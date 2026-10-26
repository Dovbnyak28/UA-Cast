#!/usr/bin/env python3
"""Verify the corresponding-source artifact against the native inputs used by this build."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import stat
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MAX_SOURCE_BYTES = 64 * 1024 * 1024
HASH_INDEX = "distribution-evidence/source-file-sha256.json"
NATIVE_INDEX = "distribution-evidence/native-manifest.json"
README = "README-REBUILD-RELINK.txt"


def verify_bundle(path, manifest, source_root=None):
    expected_checksum = path.with_suffix(path.suffix + ".sha256").read_text(encoding="utf-8").split()
    with path.open("rb") as stream:
        checksum = hashlib.file_digest(stream, "sha256").hexdigest()
    if expected_checksum != [checksum, path.name]:
        raise ValueError("Source archive checksum mismatch")
    with zipfile.ZipFile(path) as archive:
        members = archive.infolist()
        names = [item.filename for item in members]
        if len(names) != len(set(names)) or sum(item.file_size for item in members) > MAX_SOURCE_BYTES:
            raise ValueError("Duplicate or oversized source archive")
        for item in members:
            member = PurePosixPath(item.filename)
            if (member.is_absolute() or ".." in member.parts or "\\" in item.filename
                    or ":" in item.filename or stat.S_ISLNK(item.external_attr >> 16)):
                raise ValueError("Unsafe source archive member")
        if json.loads(archive.read(NATIVE_INDEX)) != manifest:
            raise ValueError("Source archive belongs to a different native build")
        hashes = json.loads(archive.read(HASH_INDEX))
        if not isinstance(hashes, dict) or set(names) != set(hashes) | {HASH_INDEX, NATIVE_INDEX, README}:
            raise ValueError("Incomplete source file inventory")
        for name, expected in hashes.items():
            with archive.open(name) as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                    raise ValueError("Source file checksum mismatch: " + name)
        if not manifest["inputs"] or any(hashes.get(name) != digest for name, digest in manifest["inputs"].items()):
            raise ValueError("Source archive does not cover the verified native inputs")
        lock = json.loads(archive.read("ffmpegdecoder/native-sources.json"))
        sources = {f"build/legal-native-inputs/ffmpeg-{lock['ffmpeg']['version']}.tar.xz": "ffmpeg",
                   "build/legal-native-inputs/libyuv-eeb06a6.zip": "libyuv"}
        if any(hashes.get(name) != manifest["archives"][component] for name, component in sources.items()):
            raise ValueError("Upstream source archive differs from the compiled input")
        if source_root is not None:
            for name, digest in hashes.items():
                if name.startswith(("ffmpegdecoder/", "scripts/")):
                    with (source_root / name).open("rb") as stream:
                        if hashlib.file_digest(stream, "sha256").hexdigest() != digest:
                            raise ValueError("Bundled wrapper/build source differs from checkout: " + name)
        if not archive.read(README).strip():
            raise ValueError("Rebuild/relink instructions missing")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--bundle", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    spec = importlib.util.spec_from_file_location("native_builder", root / "scripts/prepare-software-decoder.py")
    native = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(native)
    verify_bundle(args.bundle or root / "build/software-decoder-dist/uacast-software-decoder-source.zip",
                  native.verify_manifest(root), root)
    print("Corresponding-source archive matches this verified native build")


if __name__ == "__main__":
    main()
