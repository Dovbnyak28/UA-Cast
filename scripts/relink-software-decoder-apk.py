#!/usr/bin/env python3
"""Replace one compatible native library without changing application DEX/resources.

Produces an UNSIGNED APK. zipalign and apksigner with the recipient's own key are
required afterwards. Never installs, uninstalls or accesses a device.
"""
import argparse
import copy
import hashlib
import importlib.util
from pathlib import Path, PurePosixPath
import re
import zipfile

SPEC = importlib.util.spec_from_file_location("native_build", Path(__file__).with_name("prepare-software-decoder.py"))
NATIVE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NATIVE)
SIGNATURE = re.compile(r"META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))", re.IGNORECASE)


def replace_library(apk, library, abi, output):
    if abi not in NATIVE.ABIS or library.name not in NATIVE.LIBRARIES:
        raise ValueError("Unknown native ABI/library")
    if output.exists() or output.resolve() in (apk.resolve(), library.resolve()):
        raise ValueError("Output must be a new file, never overwrite an input")
    NATIVE.validate_elf(library, abi)
    target = f"lib/{abi}/{library.name}"
    with zipfile.ZipFile(apk) as original:
        entries = original.infolist()
        names = [entry.filename for entry in entries]
        if len(entries) > 50000 or len(set(names)) != len(names) or target not in names:
            raise ValueError("APK has duplicate/excessive entries or lacks the selected library")
        if sum(entry.file_size for entry in entries) > 512 * 1024 * 1024:
            raise ValueError("APK exceeds the supported uncompressed size")
        # ZipInfo normalizes backslashes on Windows; inspect the stored spelling too.
        for name in [entry.orig_filename for entry in entries]:
            path = PurePosixPath(name)
            if path.is_absolute() or '..' in path.parts or '\\' in name or ':' in name:
                raise ValueError("Unsafe APK entry")
        replacement = library.read_bytes()
        if hashlib.sha256(original.read(target)).digest() == hashlib.sha256(replacement).digest():
            raise ValueError("Replacement is identical; this does not demonstrate relinking")
        output.parent.mkdir(parents=True, exist_ok=True)
        destination = output.open('xb')
        try:
            with destination, zipfile.ZipFile(destination, 'w') as result:
                for entry in entries:
                    if SIGNATURE.fullmatch(entry.filename):
                        continue
                    info = copy.copy(entry)
                    # zipalign -P 16 runs after this step; keep the .so uncompressed.
                    if entry.filename == target:
                        info.compress_type = zipfile.ZIP_STORED
                    result.writestr(info, replacement if entry.filename == target else original.read(entry))
            with zipfile.ZipFile(output) as result:
                for entry in entries:
                    if entry.filename != target and not SIGNATURE.fullmatch(entry.filename):
                        if result.read(entry.filename) != original.read(entry):
                            raise ValueError("Application content changed during repack")
        except BaseException:
            destination.close()
            output.unlink(missing_ok=True)
            raise
    print(f"Unsigned APK prepared; only {target} and old signatures changed")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--library', type=Path, required=True)
    parser.add_argument('--abi', choices=NATIVE.ABIS, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    replace_library(args.apk, args.library, args.abi, args.output)
