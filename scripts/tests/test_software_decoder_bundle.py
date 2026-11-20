import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("source_bundle", ROOT / "scripts/verify-software-decoder-bundle.py")
BUNDLE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUNDLE)


class SourceBundleTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name) / "source.zip"
        self.source = b"pinned build input"
        self.files = {"scripts/build.sh": self.source,
                      "ffmpegdecoder/native-sources.json": json.dumps({"ffmpeg": {"version": "6.1.6"}}).encode(),
                      "ffmpegdecoder/src/main/java/Wrapper.java": b"wrapper source",
                      "build/legal-native-inputs/ffmpeg-6.1.6.tar.xz": b"ffmpeg archive",
                      "build/legal-native-inputs/libyuv-eeb06a6.zip": b"libyuv archive"}
        self.hashes = {name: hashlib.sha256(value).hexdigest() for name, value in self.files.items()}
        self.manifest = {"inputs": {name: self.hashes[name] for name in
                                   ("scripts/build.sh", "ffmpegdecoder/native-sources.json")},
                         "archives": {"ffmpeg": self.hashes["build/legal-native-inputs/ffmpeg-6.1.6.tar.xz"],
                                      "libyuv": self.hashes["build/legal-native-inputs/libyuv-eeb06a6.zip"]},
                         "libraries": {"arm64-v8a/decoder.so": "a" * 64}}

    def write_bundle(self, **overrides):
        files = {**self.files,
                 BUNDLE.HASH_INDEX: json.dumps(self.hashes),
                 BUNDLE.NATIVE_INDEX: json.dumps(self.manifest), BUNDLE.README: "Rebuild and relink instructions"}
        files.update(overrides)
        with zipfile.ZipFile(self.path, "w") as archive:
            for name, data in files.items():
                info = zipfile.ZipInfo("placeholder")
                info.filename = name  # Keep hostile backslashes verbatim on Windows too.
                archive.writestr(info, data)
        self.checksum()

    def checksum(self):
        digest = hashlib.sha256(self.path.read_bytes()).hexdigest()
        self.path.with_suffix(".zip.sha256").write_text(digest + "  source.zip\n", encoding="utf-8")

    def test_matching_source_is_accepted(self):
        self.write_bundle()
        BUNDLE.verify_bundle(self.path, self.manifest)

    def test_wrong_archive_checksum_is_rejected(self):
        self.write_bundle()
        self.path.write_bytes(self.path.read_bytes() + b"changed")
        with self.assertRaisesRegex(ValueError, "archive checksum"):
            BUNDLE.verify_bundle(self.path, self.manifest)

    def test_other_build_and_modified_source_are_rejected(self):
        for replacement, message in (({BUNDLE.NATIVE_INDEX: "{}"}, "different native"),
                                     ({"scripts/build.sh": b"changed"}, "file checksum"),
                                     ({BUNDLE.HASH_INDEX: "{}"}, "inventory"),
                                     ({BUNDLE.README: ""}, "instructions")):
            with self.subTest(message=message):
                self.write_bundle(**replacement)
                with self.assertRaisesRegex(ValueError, message):
                    BUNDLE.verify_bundle(self.path, self.manifest)

    def test_missing_build_input_is_rejected(self):
        self.write_bundle()
        manifest = {**self.manifest, "inputs": {**self.manifest["inputs"], "missing.c": "0" * 64}}
        with zipfile.ZipFile(self.path, "r") as archive:
            files = {item: archive.read(item) for item in archive.namelist()}
        files[BUNDLE.NATIVE_INDEX] = json.dumps(manifest)
        with zipfile.ZipFile(self.path, "w") as archive:
            for name, value in files.items():
                archive.writestr(name, value)
        self.checksum()
        with self.assertRaisesRegex(ValueError, "native inputs"):
            BUNDLE.verify_bundle(self.path, manifest)

    def test_wrong_upstream_archive_cannot_hide_behind_consistent_internal_checksums(self):
        name = "build/legal-native-inputs/ffmpeg-6.1.6.tar.xz"
        replacement = b"different source archive"
        hashes = {**self.hashes, name: hashlib.sha256(replacement).hexdigest()}
        self.write_bundle(**{name: replacement, BUNDLE.HASH_INDEX: json.dumps(hashes)})
        with self.assertRaisesRegex(ValueError, "Upstream source"):
            BUNDLE.verify_bundle(self.path, self.manifest)

    def test_bundle_wrapper_must_match_the_app_checkout_too(self):
        self.write_bundle()
        checkout = Path(self.temporary.name) / "checkout"
        for name, value in self.files.items():
            if name.startswith(("ffmpegdecoder/", "scripts/")):
                path = checkout / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(value)
        BUNDLE.verify_bundle(self.path, self.manifest, checkout)
        (checkout / "ffmpegdecoder/src/main/java/Wrapper.java").write_bytes(b"different wrapper")
        with self.assertRaisesRegex(ValueError, "differs from checkout"):
            BUNDLE.verify_bundle(self.path, self.manifest, checkout)

    def test_unsafe_paths_and_duplicate_members_are_rejected(self):
        for name in ("../escape", "/absolute", "C:/drive", "folder\\escape"):
            self.write_bundle(**{name: "unsafe"})
            with self.assertRaises(ValueError):
                BUNDLE.verify_bundle(self.path, self.manifest)
        self.write_bundle()
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(self.path, "a") as archive:
                archive.writestr(BUNDLE.README, "duplicate")
        self.checksum()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            BUNDLE.verify_bundle(self.path, self.manifest)


if __name__ == "__main__":
    unittest.main()
