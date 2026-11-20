#!/usr/bin/env python3
"""Verified cross-platform native build/bootstrap. Never imports binaries from a decoder AAR."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
import re
from pathlib import Path, PurePosixPath
import shutil
import struct
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ABIS = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183), "x86_64": (2, 62)}
LIBRARIES = ("libavcodec.so", "libavutil.so", "libswresample.so", "libswscale.so", "libffmpegJNI.so")
INPUTS = ("ffmpegdecoder/native-sources.json", "ffmpegdecoder/src/main/jni/CMakeLists.txt",
          "ffmpegdecoder/src/main/jni/ffmpeg_jni.cc", "scripts/build-ffmpeg-android.sh",
          "scripts/patches/ffmpeg-6.1.6-windows-response.patch", "scripts/prepare-software-decoder.py")


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def input_hashes(root):
    return {name: sha256(root / name) for name in INPUTS}


def validate_elf(path, abi):
    """Reject wrong ABI, malformed tables and non-16-KiB load alignment before publishing outputs."""
    data = path.read_bytes()
    expected_class, expected_machine = ABIS[abi]
    if len(data) < 64 or data[:4] != b"\x7fELF" or data[4] != expected_class or data[5] != 1:
        raise ValueError(f"Invalid ELF class/encoding: {abi}/{path.name}")
    kind, machine = struct.unpack_from("<HH", data, 16)
    if kind != 3 or machine != expected_machine:
        raise ValueError(f"Invalid ELF type/machine: {abi}/{path.name}")
    if expected_class == 2:
        offset = struct.unpack_from("<Q", data, 32)[0]
        size, count = struct.unpack_from("<HH", data, 54)
        minimum, align_offset = 56, 48
    else:
        offset = struct.unpack_from("<I", data, 28)[0]
        size, count = struct.unpack_from("<HH", data, 42)
        minimum, align_offset = 32, 28
    if size < minimum or count < 1 or count > 128 or offset < 52 or offset + size * count > len(data):
        raise ValueError(f"Invalid ELF program headers: {abi}/{path.name}")
    loads = 0
    for index in range(count):
        header = offset + size * index
        if struct.unpack_from("<I", data, header)[0] == 1:
            loads += 1
            alignment = struct.unpack_from("<Q" if expected_class == 2 else "<I", data, header + align_offset)[0]
            if alignment < 16384 or alignment & (alignment - 1):
                raise ValueError(f"ELF is not 16-KiB compatible: {abi}/{path.name}")
    if not loads:
        raise ValueError(f"ELF has no load segments: {abi}/{path.name}")


def verify_manifest(root, directory=None):
    directory = directory or root / "ffmpegdecoder/build/native-libraries"
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    lock = json.loads((root / "ffmpegdecoder/native-sources.json").read_text(encoding="utf-8"))
    if not isinstance(manifest, dict) or manifest.get("schemaVersion") != 1:
        raise ValueError("Unsupported native manifest")
    if manifest.get("inputs") != input_hashes(root) or manifest.get("toolchain") != lock["toolchain"]:
        raise ValueError("Native binaries are stale for the checked-out sources/toolchain")
    if manifest.get("archives") != {name: lock[name]["sha256"] for name in ("ffmpeg", "libyuv")}:
        raise ValueError("Native archive provenance mismatch")
    expected_paths = {f"{abi}/{name}" for abi in ABIS for name in LIBRARIES}
    hashes = manifest.get("libraries")
    if not isinstance(hashes, dict) or set(hashes) != expected_paths:
        raise ValueError("Native manifest must cover all five libraries in all three ABIs")
    actual_paths = {str(path.relative_to(directory)).replace("\\", "/") for path in directory.rglob("*.so")}
    if actual_paths != expected_paths:
        raise ValueError("Unexpected or missing native libraries")
    for name in sorted(expected_paths):
        path = directory / name
        if path.is_symlink() or sha256(path) != hashes[name]:
            raise ValueError(f"Native library hash mismatch: {name}")
        validate_elf(path, name.split("/", 1)[0])
    return manifest


def verified_download(url, expected, path, offline):
    if path.exists():
        if sha256(path) != expected:
            raise ValueError(f"Cached source archive hash mismatch: {path.name}")
        return
    if offline:
        raise ValueError(f"Offline source archive missing: {path.name}")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, suffix=".download", delete=False) as temporary:
            temporary_path = Path(temporary.name)
            with urllib.request.urlopen(url, timeout=60) as response:
                shutil.copyfileobj(response, temporary)
            temporary.flush()
            os.fsync(temporary.fileno())
        if sha256(temporary_path) != expected:
            raise ValueError(f"Downloaded source archive hash mismatch: {path.name}")
        os.replace(temporary_path, path)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def archive_member(name, prefix):
    path = PurePosixPath(name)
    if ("\\" in name or ":" in name or path.is_absolute() or ".." in path.parts or
            not path.parts or path.parts[0] != prefix):
        raise ValueError("Archive member escapes its pinned source directory")


def extract_verified(archive, prefix, destination, is_zip=False):
    destination.mkdir(parents=True, exist_ok=True)
    # Fresh generated directory; interrupted/modified extraction is never silently reused.
    staging = Path(tempfile.mkdtemp(prefix="unpack-", dir=destination))
    if is_zip:
        with zipfile.ZipFile(archive) as source:
            for member in source.infolist():
                archive_member(member.filename, prefix)
                if (member.external_attr >> 16) & 0o170000 == 0o120000:
                    raise ValueError("Symlink source archive member")
            source.extractall(staging)
    else:
        with tarfile.open(archive) as source:
            for member in source.getmembers():
                archive_member(member.name, prefix)
                if not (member.isfile() or member.isdir()):
                    raise ValueError("Non-regular source archive member")
            source.extractall(staging, filter="data")
    return staging / prefix


def command(arguments, log, env=None, cwd=None):
    with log.open("a", encoding="utf-8") as output:
        subprocess.run([str(item) for item in arguments], cwd=cwd, env=env,
                       stdout=output, stderr=subprocess.STDOUT, check=True)


def build(root, ndk, cmake, ninja, archives, jobs, offline):
    # New Windows volumes often have 8.3 names disabled. Git Bash's cygpath -ms
    # then leaves spaces intact, which GNU make cannot handle. Use temporary
    # junction aliases for make only; provenance and published paths stay canonical.
    if os.name != "nt":
        return _build(root, ndk, cmake, ninja, archives, jobs, offline, root, ndk)
    with tempfile.TemporaryDirectory(prefix="uacast-native-") as temporary:
        alias_root = Path(temporary)
        if any(character.isspace() for character in temporary):
            raise ValueError("Set TEMP to a whitespace-free directory for the native build")
        aliases = []
        try:
            for name, target in (("workspace", root), ("ndk", ndk)):
                alias = alias_root / name
                subprocess.run(["cmd.exe", "/d", "/c", "mklink", "/J", str(alias), str(target)],
                               check=True, capture_output=True, text=True)
                aliases.append(alias)
            return _build(root, ndk, cmake, ninja, archives, jobs, offline,
                          alias_root / "workspace", alias_root / "ndk")
        finally:
            # Remove only the junction itself, never recursively traverse its target.
            for alias in reversed(aliases):
                if alias.parent != alias_root or not alias.is_junction():
                    raise ValueError("Unexpected native build alias; refusing cleanup")
                alias.rmdir()


def _build(root, ndk, cmake, ninja, archives, jobs, offline, make_root, make_ndk):
    lock = json.loads((root / "ffmpegdecoder/native-sources.json").read_text(encoding="utf-8"))
    revision = [line.split("=", 1)[1].strip() for line in (ndk / "source.properties").read_text().splitlines()
                if line.strip().startswith("Pkg.Revision")]
    if revision != [lock["toolchain"]["ndk"]]:
        raise ValueError("Unexpected Android NDK revision")
    version = subprocess.run([str(cmake), "--version"], check=True, capture_output=True, text=True).stdout
    cmake_revision = lock["toolchain"]["cmake"]
    if not re.fullmatch("cmake version " + re.escape(cmake_revision) + r"(?:-[\w-]+)?", version.splitlines()[0]):
        raise ValueError("Unexpected CMake version")
    sdk_cmake = cmake.parent.parent / "source.properties"
    if (not sdk_cmake.is_file() or not re.search(r"^Pkg.Revision\s*=\s*" + re.escape(cmake_revision) + r"\s*$",
            sdk_cmake.read_text(), re.MULTILINE)):
        raise ValueError("Expected the pinned Android SDK CMake package")
    if not ninja.is_file() or jobs < 1 or jobs > 16:
        raise ValueError("Invalid Ninja path or worker count")
    cache = root / "ffmpegdecoder/build/native-bootstrap"
    cache.mkdir(parents=True, exist_ok=True)
    try:
        verify_manifest(root)
    except (OSError, ValueError, KeyError, TypeError):
        pass
    else:
        print("Existing native outputs match all verified inputs", flush=True)
        return
    # Never reuse a Makefile which could still include an earlier extraction's source path.
    run_directory = Path(tempfile.mkdtemp(prefix="run-", dir=cache))
    inputs = input_hashes(root)
    ffmpeg_archive = archives / f"ffmpeg-{lock['ffmpeg']['version']}.tar.xz"
    yuv_archive = archives / "libyuv-eeb06a6.zip"
    for name, path in (("ffmpeg", ffmpeg_archive), ("libyuv", yuv_archive)):
        verified_download(lock[name]["url"], lock[name]["sha256"], path, offline)
    ffmpeg = extract_verified(ffmpeg_archive, "ffmpeg-" + lock["ffmpeg"]["version"], run_directory / "sources")
    libyuv = extract_verified(yuv_archive, "libyuv-" + lock["libyuv"]["commit"], run_directory / "sources", True)
    bash = shutil.which("bash")
    if os.name == "nt":
        git_bash = Path("C:/Program Files/Git/bin/bash.exe")
        if git_bash.is_file():
            bash = str(git_bash)
    if not bash:
        raise ValueError("Bash with make/patch is required")
    environment = dict(os.environ, ANDROID_NDK_HOME=str(make_ndk),
                       FFMPEG_SOURCE=str(make_root / ffmpeg.relative_to(root)),
                       FFMPEG_BUILD_ROOT=str(make_root / run_directory.relative_to(root) / "ffmpeg"),
                       NATIVE_BUILD_JOBS=str(jobs))
    if os.name == "nt":
        environment["PATH"] = str(Path(bash).parent.parent / "usr/bin") + os.pathsep + environment["PATH"]
    # Patch once before parallel configure/build processes share the source tree.
    command([bash, "-c", 'patch -d "$FFMPEG_SOURCE" -p1 --forward < scripts/patches/ffmpeg-6.1.6-windows-response.patch'],
            run_directory / "source-patch.log", environment, root)
    staging = Path(tempfile.mkdtemp(prefix="native-staging-", dir=cache))

    def compile_abi(abi):
        log = run_directory / (abi + ".log")
        print("Building independent software decoder: " + abi, flush=True)
        command([bash, root / "scripts/build-ffmpeg-android.sh", abi], log, environment, root)
        install = run_directory / "ffmpeg" / abi / "install"
        jni = run_directory / ("jni-" + abi)
        command([cmake, "-S", root / "ffmpegdecoder/src/main/jni", "-B", jni, "-G", "Ninja",
                 "-DCMAKE_TOOLCHAIN_FILE=" + str(ndk / "build/cmake/android.toolchain.cmake"),
                 "-DANDROID_ABI=" + abi, "-DANDROID_PLATFORM=android-24", "-DANDROID_STL=c++_static",
                 "-DFFMPEG_INSTALL=" + str(install), "-DLIBYUV_SOURCE=" + str(libyuv),
                 "-DCMAKE_MAKE_PROGRAM=" + str(ninja), "-DCMAKE_BUILD_TYPE=Release"], log, cwd=root)
        command([cmake, "--build", jni, "--target", "ffmpegJNI", "-j", str(jobs)], log, cwd=root)
        output = staging / abi
        output.mkdir()
        for name in LIBRARIES:
            source = jni / name if name == "libffmpegJNI.so" else install / "lib" / name
            validate_elf(source, abi)
            shutil.copy2(source, output / name)
        print("Validated all five native libraries: " + abi, flush=True)

    # Keep CPU/RAM bounded; three unconstrained native builds can overwhelm a developer laptop.
    with ThreadPoolExecutor(max_workers=2) as executor:
        list(executor.map(compile_abi, ABIS))
    if input_hashes(root) != inputs:
        raise ValueError("Decoder sources changed during native compilation; refusing stale outputs")
    manifest = {"schemaVersion": 1, "inputs": inputs, "toolchain": lock["toolchain"],
                "archives": {name: lock[name]["sha256"] for name in ("ffmpeg", "libyuv")},
                "libraries": {f"{abi}/{name}": sha256(staging / abi / name) for abi in ABIS for name in LIBRARIES}}
    (staging / "manifest.json").write_text(json.dumps(manifest, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    verify_manifest(root, staging)
    target = root / "ffmpegdecoder/build/native-libraries"
    if not target.resolve().is_relative_to(root):
        raise ValueError("Native output directory escapes the workspace")
    # An interrupted publication leaves an old/missing manifest whose hashes fail closed.
    for abi in ABIS:
        (target / abi).mkdir(parents=True, exist_ok=True)
        for name in LIBRARIES:
            os.replace(staging / abi / name, target / abi / name)
    os.replace(staging / "manifest.json", target / "manifest.json")
    verify_manifest(root)
    print("Verified native decoder published to generated build inputs", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--ndk", type=Path)
    parser.add_argument("--cmake", type=Path)
    parser.add_argument("--ninja", type=Path)
    parser.add_argument("--archives", type=Path)
    parser.add_argument("--jobs", type=int, default=3)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        if args.verify_only:
            verify_manifest(root)
        else:
            if not args.ndk or not args.cmake or not args.ninja:
                parser.error("--ndk, --cmake and --ninja are required to build")
            build(root, args.ndk.resolve(), args.cmake.resolve(), args.ninja.resolve(),
                  (args.archives or root / "build/legal-native-inputs").resolve(), args.jobs, args.offline)
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        print(f"Software decoder verification/build failed: {error}", file=sys.stderr)
        return 1
    print("Native software decoder verification passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
