#!/usr/bin/env python3
"""Linux CI only: compile a modified LGPL library into a separate test-only output.

The production native inputs and corresponding-source ZIP stay unchanged. A
runtime-visible marker in the licence query proves Android loaded the replacement.
Copyright/licence headers and decoder behaviour are preserved.
"""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('builder', ROOT / 'scripts/prepare-software-decoder.py')
NATIVE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NATIVE)
MARKER = ' (UA-Cast relink probe)'


def marked_source(source):
    old = 'return &LICENSE_PREFIX FFMPEG_LICENSE[sizeof(LICENSE_PREFIX) - 1];'
    if source.count(old) != 1:
        raise ValueError('Unexpected FFmpeg licence-query source; refusing an ambiguous patch')
    return source.replace(old, f'return &LICENSE_PREFIX FFMPEG_LICENSE "{MARKER}"[sizeof(LICENSE_PREFIX) - 1];')


def main():
    if os.name == 'nt':
        raise ValueError('Run this incremental CI probe on Linux; production rebuild supports Windows too')
    manifest = NATIVE.verify_manifest(ROOT)
    candidates = []
    for run in (ROOT / 'ffmpegdecoder/build/native-bootstrap').glob('run-*'):
        library = run / 'ffmpeg/x86_64/install/lib/libavcodec.so'
        if library.is_file() and NATIVE.sha256(library) == manifest['libraries']['x86_64/libavcodec.so']:
            candidates.append(run)
    if not candidates:
        raise ValueError('Matching completed native build required')
    run = sorted(candidates)[-1]
    sources = list((run / 'sources').glob('*/ffmpeg-*/libavcodec/version.c'))
    if len(sources) != 1:
        raise ValueError('Expected one verified FFmpeg source directory')
    source = sources[0]
    original = source.read_bytes()
    output = ROOT / 'build/software-decoder-relink-probe'
    output.mkdir(parents=True, exist_ok=True)
    try:
        source.write_text(marked_source(original.decode('utf-8')), encoding='utf-8')
        objects = run / 'ffmpeg/x86_64/objects'
        with (output / 'compile.log').open('w') as log:
            subprocess.run(['make', '-C', str(objects), '-j2', 'libavcodec/libavcodec.so'],
                           check=True, stdout=log, stderr=subprocess.STDOUT, timeout=300)
        replacement = objects / 'libavcodec/libavcodec.so'
        NATIVE.validate_elf(replacement, 'x86_64')
        if NATIVE.sha256(replacement) == manifest['libraries']['x86_64/libavcodec.so']:
            raise ValueError('Modified source did not change the library')
        shutil.copyfile(replacement, output / 'libavcodec.so')
        (output / 'evidence.json').write_text(json.dumps({
            'testOnly': True, 'abi': 'x86_64', 'marker': MARKER,
            'originalSha256': manifest['libraries']['x86_64/libavcodec.so'],
            'modifiedSha256': NATIVE.sha256(replacement),
        }, indent=2), encoding='utf-8')
    finally:
        source.write_bytes(original)
    NATIVE.verify_manifest(ROOT)
    print('Modified test-only FFmpeg library built; production native hashes unchanged')


if __name__ == '__main__':
    main()
