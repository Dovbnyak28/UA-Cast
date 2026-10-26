#!/usr/bin/env python3
"""Deterministic legal HTML from reviewed policy data and actual Android Terms resources."""

import argparse
import html
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DATE = "2026-10-26"
LOCALES = {"en": "values", "uk": "values-uk", "ru": "values-ru", "es": "values-es"}
STYLE = """body{font-family:system-ui,sans-serif;max-width:760px;margin:auto;padding:24px 20px 64px;
line-height:1.65;color:#171717;background:#fff}h1{font-size:1.6rem}h2{font-size:1.15rem;margin-top:2em}
nav a{display:inline-block;padding:10px;margin-right:8px}.date{color:#555}section{scroll-margin-top:12px}
hr{margin:48px 0;border:0;border-top:1px solid #ddd}a{overflow-wrap:anywhere}p{overflow-wrap:break-word}"""


def android_text(value):
    return value.replace(r"\'", "'").replace(r'\"', '"').replace(r"\n", "\n")


def render(title, documents):
    links = " ".join(f'<a href="#{language}">{html.escape(name)}</a>'
                     for language, name, *_ in documents)
    blocks = []
    for language, name, heading, intro, sections in documents:
        paragraphs = "".join(f"<h2>{html.escape(label)}</h2><p>{html.escape(body)}</p>"
                             for label, body in sections)
        blocks.append(f'<section id="{language}" lang="{language}"><h1>{html.escape(heading)}</h1>'
                      f'<p class="date">{DATE}</p><p>{html.escape(intro)}</p>{paragraphs}</section>')
    return (f'<!DOCTYPE html>\n<html lang="en"><head><meta charset="UTF-8">'
            f'<meta name="viewport" content="width=device-width, initial-scale=1">'
            f'<title>{html.escape(title)}</title><style>{STYLE}</style></head><body>'
            f'<nav aria-label="Language">{links}</nav>' + "<hr>".join(blocks) + "</body></html>\n")


def generated_documents():
    policy = json.loads((ROOT / "config/legal/privacy-content.json").read_text(encoding="utf-8"))
    if policy["updated"] != DATE:
        raise ValueError("Policy date and generator date must agree")
    privacy = [(language, item["name"], item["title"], item["intro"], item["sections"])
               for language, item in policy["languages"].items()]
    names = {"en": "English", "uk": "Українська", "ru": "Русский", "es": "Español"}
    terms = []
    for language, directory in LOCALES.items():
        document = ET.parse(ROOT / f"app/src/main/res/{directory}/strings.xml")
        strings = {node.attrib["name"]: android_text("".join(node.itertext()))
                   for node in document.getroot().findall("string")}
        sections = [(strings[f"terms_s{index}_title"], strings[f"terms_s{index}_body"])
                    for index in range(1, 9)]
        sections.append(("FFmpeg / LGPL", strings["legal_ffmpeg_notice"] +
                         " https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html — " +
                         "https://github.com/Dovbnyak28/UA-Cast/releases"))
        terms.append((language, names[language], strings["terms_title"], strings["terms_intro"], sections))
    return {"privacy-policy.html": render("UA Cast Player — Privacy Policy", privacy),
            "terms-of-use.html": render("UA Cast Player — Terms of Use", terms)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Fail on drift; never rewrite files")
    args = parser.parse_args()
    failures = []
    for filename, content in generated_documents().items():
        target = ROOT / "legal" / filename
        if args.check:
            if not target.exists() or target.read_text(encoding="utf-8") != content:
                failures.append(filename)
        else:
            target.write_text(content, encoding="utf-8", newline="\n")
    if failures:
        print("Legal document drift: " + ", ".join(failures), file=sys.stderr)
        return 1
    print("Legal documents: synchronized")
    return 0


if __name__ == "__main__":
    sys.exit(main())
