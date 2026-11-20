#!/usr/bin/env python3
"""Stage only reviewed public legal documents; never copy the workspace/web root."""
import hashlib
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("legal_documents", ROOT / "scripts/generate-legal-documents.py")
DOCUMENTS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DOCUMENTS)
INDEX = """<!doctype html>
<html lang="uk"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>UA-Cast — правова інформація</title>
<style>body{font:18px/1.6 system-ui;max-width:700px;margin:auto;padding:32px}a{display:block;padding:12px}</style>
</head><body><h1>UA-Cast</h1><p>Правова інформація / Legal information</p>
<a href="legal/privacy-policy.html">Політика конфіденційності / Privacy policy</a>
<a href="legal/terms-of-use.html">Умови використання / Terms of use</a>
</body></html>
"""


def stage(root, output, documents):
    files = {".nojekyll": "", "index.html": INDEX}
    for name, content in documents.items():
        if name not in {"privacy-policy.html", "terms-of-use.html"}:
            raise ValueError("Unexpected public document")
        if (root / "legal" / name).read_text(encoding="utf-8") != content:
            raise ValueError("Generate and review legal documents before deployment")
        files[name] = content  # Keep the historical root links usable too.
        files["legal/" + name] = content
    existing = {p.relative_to(output).as_posix() for p in output.rglob("*") if p.is_file()}
    if existing - files.keys():
        raise ValueError("Staging directory contains unrelated files; refusing publication")
    for name, content in files.items():
        path = output / name
        if path.is_symlink() or not path.resolve().is_relative_to(output.resolve()):
            raise ValueError("Public staging path escapes its directory")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8", newline="\n")
    return {name: hashlib.sha256(content.encode()).hexdigest() for name, content in files.items()}


if __name__ == "__main__":
    hashes = stage(ROOT, ROOT / "build/legal-site", DOCUMENTS.generated_documents())
    for name, digest in sorted(hashes.items()):
        print(digest + "  " + name)
