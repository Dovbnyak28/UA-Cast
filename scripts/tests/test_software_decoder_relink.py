import importlib.util
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile
from test_software_decoder_build import elf

ROOT = Path(__file__).resolve().parents[2]


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / (name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


RELINK = module('relink-software-decoder-apk')
PROBE = module('prepare-software-decoder-relink-probe')


class RelinkTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.apk = self.root / 'original.apk'
        self.output = self.root / 'unsigned.apk'
        self.library = self.root / 'libavcodec.so'
        self.library.write_bytes(elf('x86_64') + b'modified')
        self.files = {'classes.dex': b'unchanged dex', 'resources.arsc': b'unchanged resources',
                      'lib/x86_64/libavcodec.so': bytes(elf('x86_64')),
                      'META-INF/ORIGINAL.SF': b'signature', 'META-INF/ORIGINAL.RSA': b'key',
                      'META-INF/MANIFEST.MF': b'digests', 'META-INF/services/provider': b'keep service',
                      'META-INF/module.kotlin_module': b'keep kotlin'}
        self.write_apk()

    def write_apk(self):
        with zipfile.ZipFile(self.apk, 'w') as archive:
            for name, value in self.files.items():
                info = zipfile.ZipInfo('placeholder')
                info.filename = name
                archive.writestr(info, value)

    def run_relink(self):
        RELINK.replace_library(self.apk, self.library, 'x86_64', self.output)

    def test_only_library_and_signatures_change(self):
        before = self.apk.read_bytes()
        self.run_relink()
        self.assertEqual(before, self.apk.read_bytes())
        with zipfile.ZipFile(self.output) as result:
            for name, value in self.files.items():
                if RELINK.SIGNATURE.fullmatch(name):
                    self.assertNotIn(name, result.namelist())
                else:
                    expected = self.library.read_bytes() if name.startswith('lib/') else value
                    self.assertEqual(expected, result.read(name))
            self.assertEqual(zipfile.ZIP_STORED, result.getinfo('lib/x86_64/libavcodec.so').compress_type)

    def test_existing_output_and_inputs_are_never_overwritten(self):
        self.output.write_bytes(b'keep')
        with self.assertRaises(ValueError):
            self.run_relink()
        self.assertEqual(b'keep', self.output.read_bytes())
        with self.assertRaises(ValueError):
            RELINK.replace_library(self.apk, self.library, 'x86_64', self.apk)

    def test_identical_or_wrong_abi_replacement_is_rejected(self):
        for contents in (elf('x86_64'), elf('arm64-v8a')):
            self.library.write_bytes(contents)
            with self.assertRaises(ValueError):
                self.run_relink()
            self.assertFalse(self.output.exists())

    def test_malformed_names_and_duplicate_entries_are_rejected(self):
        for name in ('../classes.dex', '/absolute', 'lib\\x86_64\\evil', 'C:/evil'):
            self.files[name] = b'bad'
            self.write_apk()
            with self.assertRaises(ValueError):
                self.run_relink()
            del self.files[name]
        self.write_apk()
        with warnings.catch_warnings(), zipfile.ZipFile(self.apk, 'a') as archive:
            warnings.simplefilter('ignore', UserWarning)
            archive.writestr('classes.dex', b'duplicate')
        with self.assertRaises(ValueError):
            self.run_relink()

    def test_probe_patch_preserves_licence_and_requires_exact_source(self):
        original = 'return &LICENSE_PREFIX FFMPEG_LICENSE[sizeof(LICENSE_PREFIX) - 1];'
        patched = PROBE.marked_source(original)
        self.assertIn('FFMPEG_LICENSE', patched)
        self.assertIn(PROBE.MARKER, patched)
        for invalid in ('other version', original + original, patched):
            with self.assertRaises(ValueError):
                PROBE.marked_source(invalid)
