import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import os

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("software_native", ROOT / "scripts/prepare-software-decoder.py")
NATIVE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NATIVE)


def elf(abi, alignment=16384):
    kind, machine = NATIVE.ABIS[abi]
    header = bytearray(120)
    header[:6] = b"\x7fELF" + bytes([kind, 1])
    struct.pack_into("<HH", header, 16, 3, machine)
    if kind == 2:
        struct.pack_into("<Q", header, 32, 64)
        struct.pack_into("<HH", header, 54, 56, 1)
        struct.pack_into("<I", header, 64, 1)
        struct.pack_into("<Q", header, 112, alignment)
    else:
        struct.pack_into("<I", header, 28, 64)
        struct.pack_into("<HH", header, 42, 32, 1)
        struct.pack_into("<I", header, 64, 1)
        struct.pack_into("<I", header, 92, alignment)
    return header


class NativeBuildTests(unittest.TestCase):
    @unittest.skipUnless(os.name == "nt", "Windows junction handling")
    def test_space_free_aliases_are_removed_without_touching_targets_even_on_failure(self):
        with tempfile.TemporaryDirectory(prefix="decoder target ") as temporary:
            root = Path(temporary)
            ndk = root / "android ndk"
            ndk.mkdir()
            sentinel = ndk / "keep.txt"
            sentinel.write_text("keep", encoding="utf-8")
            for fail in (False, True):
                aliases = []
                def fake_build(*args):
                    aliases.extend(args[-2:])
                    for alias in aliases:
                        self.assertFalse(any(c.isspace() for c in str(alias)))
                        self.assertTrue(alias.is_junction())
                    self.assertEqual((aliases[-1] / "keep.txt").read_text(), "keep")
                    if fail:
                        raise ValueError("simulated compiler failure")
                with patch.object(NATIVE, "_build", side_effect=fake_build):
                    if fail:
                        with self.assertRaisesRegex(ValueError, "compiler failure"):
                            NATIVE.build(root, ndk, root, root, root, 1, True)
                    else:
                        NATIVE.build(root, ndk, root, root, root, 1, True)
                self.assertTrue(all(not alias.exists() for alias in aliases))
                self.assertEqual(sentinel.read_text(), "keep")

    def test_source_archive_cannot_escape_its_pinned_directory(self):
        for name in ("../ffmpeg-6.1.6/a", "/ffmpeg-6.1.6/a", "ffmpeg-6.1.6/../../a",
                     "ffmpeg-6.1.6\\a", "ffmpeg-6.1.6/C:/a", "other/a"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                NATIVE.archive_member(name, "ffmpeg-6.1.6")
        NATIVE.archive_member("ffmpeg-6.1.6/libavcodec/avcodec.h", "ffmpeg-6.1.6")

    def test_wrong_machine_and_4k_alignment_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "decoder.so"
            for abi in NATIVE.ABIS:
                path.write_bytes(elf(abi))
                NATIVE.validate_elf(path, abi)
                wrong = "x86_64" if abi != "x86_64" else "arm64-v8a"
                with self.assertRaises(ValueError):
                    NATIVE.validate_elf(path, wrong)
                path.write_bytes(elf(abi, 4096))
                with self.assertRaises(ValueError):
                    NATIVE.validate_elf(path, abi)

    def test_truncated_and_overflowing_program_header_tables_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "decoder.so"
            for data in (b"", b"\x7fELF", elf("arm64-v8a")[:100]):
                path.write_bytes(data)
                with self.assertRaises(ValueError):
                    NATIVE.validate_elf(path, "arm64-v8a")
            data = elf("arm64-v8a")
            struct.pack_into("<Q", data, 32, 2**64 - 1)
            path.write_bytes(data)
            with self.assertRaises(ValueError):
                NATIVE.validate_elf(path, "arm64-v8a")

    def test_bad_cached_archive_fails_closed_even_in_offline_mode(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "source.zip"
            path.write_bytes(b"corrupted")
            with self.assertRaises(ValueError):
                NATIVE.verified_download("https://example.invalid/source.zip", "0" * 64, path, True)
            with self.assertRaises(ValueError):
                NATIVE.verified_download("https://example.invalid/source.zip", "0" * 64, path.with_name("missing"), True)

    def fixture(self, root):
        for name in NATIVE.INPUTS:
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("fixture", encoding="utf-8")
        lock = {"toolchain": {"ndk": "fixture"}, "ffmpeg": {"sha256": "f" * 64}, "libyuv": {"sha256": "a" * 64}}
        (root / "ffmpegdecoder/native-sources.json").write_text(json.dumps(lock))
        target = root / "ffmpegdecoder/build/native-libraries"
        libraries = {}
        for abi in NATIVE.ABIS:
            (target / abi).mkdir(parents=True)
            for name in NATIVE.LIBRARIES:
                path = target / abi / name
                path.write_bytes(elf(abi))
                libraries[f"{abi}/{name}"] = NATIVE.sha256(path)
        manifest = {"schemaVersion": 1, "toolchain": lock["toolchain"], "inputs": NATIVE.input_hashes(root),
                    "archives": {name: lock[name]["sha256"] for name in ("ffmpeg", "libyuv")}, "libraries": libraries}
        (target / "manifest.json").write_text(json.dumps(manifest))
        return target

    def test_source_changes_and_binary_corruption_invalidate_outputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = self.fixture(root)
            NATIVE.verify_manifest(root)
            binary = target / "arm64-v8a/libavcodec.so"
            binary.write_bytes(binary.read_bytes() + b"modified")
            with self.assertRaises(ValueError):
                NATIVE.verify_manifest(root)
            binary.write_bytes(elf("arm64-v8a"))
            (root / NATIVE.INPUTS[-1]).write_text("changed")
            with self.assertRaises(ValueError):
                NATIVE.verify_manifest(root)

    def test_unexpected_gpl_wrapper_library_cannot_be_packaged(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = self.fixture(root)
            (target / "arm64-v8a/libmedia3ext.so").write_bytes(elf("arm64-v8a"))
            with self.assertRaises(ValueError):
                NATIVE.verify_manifest(root)

    def test_partial_abi_and_missing_manifest_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = self.fixture(root)
            (target / "x86_64/libavcodec.so").unlink()
            with self.assertRaises(ValueError):
                NATIVE.verify_manifest(root)
            (target / "manifest.json").unlink()
            with self.assertRaises(OSError):
                NATIVE.verify_manifest(root)


if __name__ == "__main__":
    unittest.main()
