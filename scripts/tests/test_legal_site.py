import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("legal_site", ROOT / "scripts/prepare-legal-site.py")
SITE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SITE)


class LegalSiteTests(unittest.TestCase):
    def test_only_reviewed_legal_files_are_staged(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "legal").mkdir()
            content = {"privacy-policy.html": "policy", "terms-of-use.html": "terms"}
            for name, text in content.items():
                (root / "legal" / name).write_text(text, encoding="utf-8")
            (root / "private-key.pem").write_text("private", encoding="utf-8")
            output = root / "site"
            hashes = SITE.stage(root, output, content)
            self.assertEqual(len(hashes), 6)
            self.assertFalse((output / "private-key.pem").exists())
            self.assertEqual((output / "legal/privacy-policy.html").read_text(), "policy")
            (output / "unexpected.txt").write_text("do not publish", encoding="utf-8")
            with self.assertRaises(ValueError):
                SITE.stage(root, output, content)

    def test_changed_unreviewed_document_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "legal").mkdir()
            (root / "legal/privacy-policy.html").write_text("reviewed", encoding="utf-8")
            with self.assertRaises(ValueError):
                SITE.stage(root, root / "site", {"privacy-policy.html": "changed"})
