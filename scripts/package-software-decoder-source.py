#!/usr/bin/env python3
"""Whitelist-only corresponding-source bundle for the independently compiled native libraries."""
import argparse
import importlib.util
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("native_builder", ROOT / "scripts/prepare-software-decoder.py")
NATIVE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NATIVE)

README = """UA-Cast software decoder corresponding source

Scope: only the Apache-2.0 wrapper, LGPL FFmpeg, BSD libyuv and their build inputs.
This bundle does not grant a GPL licence to the application's own code. All component
licences/copyright notices are retained. No private keys or provider playlists are included.

Rebuild on Linux/Windows with Python 3.12+, Bash/GNU make/patch, the Android NDK and
Android SDK CMake revisions in ffmpegdecoder/native-sources.json. On Linux use paths
without spaces for GNU make. On Windows the build
creates temporary directory junctions so paths with spaces work even without NTFS short
names; TEMP must be a writable directory without spaces. Junctions are removed on exit.

  python scripts/prepare-software-decoder.py --offline \\
    --ndk /path/to/ndk/26.1.10909125 \\
    --cmake /path/to/cmake/3.22.1/bin/cmake \\
    --ninja /path/to/cmake/3.22.1/bin/ninja

The two complete source archives are already in build/legal-native-inputs. The script
verifies archive hashes, applies the supplied build-only FFmpeg patch, independently
compiles all three ABIs and writes hash-verified outputs in ffmpegdecoder/build/native-libraries.
No NextLib AAR/code/binary is needed. CMake JNI links dynamically to the FFmpeg .so files.

The actual per-ABI generated configurations and distributed-library hash manifest are
in distribution-evidence. Build paths differ between machines; bit-identical binaries
are not promised, but source, tool versions, configuration and native ABI are documented.

Replacing/relinking libraries for personal use:
1. Obtain the corresponding UA-Cast universal APK and back up app data from its UI.
2. Keep its compiled Java/Dex code/resources. Replace lib/<ABI>/libavcodec.so,
   libavutil.so, libswresample.so, libswscale.so and/or libffmpegJNI.so with the rebuilt
   compatible libraries; preserve names and all three ABI layouts as appropriate.
3. Repackage with Android SDK zipalign (16-KiB shared-library alignment), then sign
   using your OWN Android signing key with apksigner. Do not use or request the
   publisher's signing key. Android will reject an in-place differently signed update.
4. A differently signed build may require uninstalling the original before installing
   your build; restore your exported backup afterwards. Never uninstall without a backup.
5. The publisher's official APK signature/update checks do not apply to your modified
   build. You are responsible for the modified build's security and compatibility.

The included scripts/relink-software-decoder-apk.py automates replacement of one
compatible .so and removes the old APK signatures while checking unchanged DEX/resources:
  python scripts/relink-software-decoder-apk.py --apk original.apk \\
    --library rebuilt/libavcodec.so --abi x86_64 --output unsigned.apk
It never installs/uninstalls anything. Run zipalign -P 16 -f 4 unsigned.apk aligned.apk,
then apksigner sign --ks YOUR_OWN_KEYSTORE --out modified.apk aligned.apk.

The application terms permit modification/reverse engineering needed to exercise the
third-party component licences. This technical source bundle is not a legal opinion;
distribution must include this bundle alongside the APK and publish correct attribution.
"""


def create_bundle(root, output):
    manifest = NATIVE.verify_manifest(root)
    lock = json.loads((root / "ffmpegdecoder/native-sources.json").read_text(encoding="utf-8"))
    archives = {"ffmpeg": root / f"build/legal-native-inputs/ffmpeg-{lock['ffmpeg']['version']}.tar.xz",
                "libyuv": root / "build/legal-native-inputs/libyuv-eeb06a6.zip"}
    for name, path in archives.items():
        if not path.is_file() or NATIVE.sha256(path) != lock[name]["sha256"]:
            raise ValueError("Verified complete source archive missing: " + name)
    candidates = sorted((root / "ffmpegdecoder/build/native-bootstrap").glob("run-*"))
    run = None
    for candidate in candidates:
        try:
            matches = all(NATIVE.sha256(candidate / "ffmpeg" / abi / "install/lib" / name) ==
                          manifest["libraries"][f"{abi}/{name}"] for abi in NATIVE.ABIS for name in NATIVE.LIBRARIES
                          if name != "libffmpegJNI.so")
            matches &= all(NATIVE.sha256(candidate / ("jni-" + abi) / "libffmpegJNI.so") ==
                           manifest["libraries"][f"{abi}/libffmpegJNI.so"] for abi in NATIVE.ABIS)
        except OSError:
            continue
        if matches:
            run = candidate
            break
    if run is None:
        raise ValueError("Matching actual native build/configuration evidence missing")
    files = {name: root / name for name in NATIVE.INPUTS}
    files['scripts/relink-software-decoder-apk.py'] = root / 'scripts/relink-software-decoder-apk.py'
    for path in (root / "ffmpegdecoder/src/main").rglob("*"):
        if path.is_file():
            files[str(path.relative_to(root)).replace("\\", "/")] = path
    for name in ("LICENSE", "ORIGIN.md", "consumer-rules.pro", "build.gradle.kts"):
        files["ffmpegdecoder/" + name] = root / "ffmpegdecoder" / name
    for path in archives.values():
        files[str(path.relative_to(root)).replace("\\", "/")] = path
    for abi in NATIVE.ABIS:
        for name in ("config.h", "ffbuild/config.mak", "ffbuild/config.sh"):
            files[f"distribution-evidence/{abi}/{name}"] = run / "ffmpeg" / abi / "objects" / name
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
        bundle.writestr("README-REBUILD-RELINK.txt", README)
        bundle.writestr("distribution-evidence/native-manifest.json", json.dumps(manifest, sort_keys=True, indent=2))
        for name, source in sorted(files.items()):
            if source.is_symlink() or not source.resolve().is_relative_to(root):
                raise ValueError("Source bundle input escapes the repository")
            bundle.write(source, name)
        bundle.writestr("distribution-evidence/source-file-sha256.json",
                        json.dumps({name: NATIVE.sha256(path) for name, path in files.items()}, sort_keys=True, indent=2))
    checksum = NATIVE.sha256(output)
    output.with_suffix(output.suffix + ".sha256").write_text(checksum + "  " + output.name + "\n", encoding="utf-8")
    print(f"Corresponding-source bundle created: {output.name} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    create_bundle(root, args.output or root / "build/software-decoder-dist/uacast-software-decoder-source.zip")
