#!/usr/bin/env python3
"""Generate scripts/templates/kana-manifest.json from the real, already-populated
scripts/templates/hyle-all-templates.zip -- CLAUDE.md's "Data packs ... checked in
under data/ with a generator script beside them; never hand-edit generated data"
convention, applied here beside the templates asset itself rather than under data/,
since the asset this generator reads lives here (data/scripts/ generators all read
or write into data/; this one's own input is scripts/templates/, so its own output
manifest and this generator sit beside it instead, per this task's own instructions:
"consider writing scripts/templates/manifest.json ... your own judgement on the
exact mechanism").

Named kana-manifest.json rather than the plain manifest.json the task's prompt
suggested, on purpose: this build's own P8 task runs several script agents
(Hiragana/Katakana, Devanagari, Arabic Naskh) in parallel, each reading the SAME
hyle-all-templates.zip but writing to its OWN new package
(scripts/.../kana/, scripts/.../devanagari/, ...) so no two agents' files collide.
A single shared scripts/templates/manifest.json would be exactly the kind of file
two concurrent agents could each independently create or regenerate and clobber
each other's -- the identical risk CLAUDE.md's docs/OPEN_QUESTIONS.md append
protocol exists to avoid for that file. Scoping this generator and its output to
kana only removes that risk entirely; a future Devanagari/Arabic task can do the
same under its own <script>-manifest.json name.

This script only reads Hiragana's and Katakana's own SVG templates and writes
Hiragana's and Katakana's own guide metrics and glyph list. It does not invent
glyph coverage: every entry comes from a real file in the zip's own svg/Hiragana/
and svg/Katakana/ folders, and the script fails loudly (raises) rather than
silently guessing if a template's own guide values are not identical to every
other template's in the same script, or if the real folder counts do not match
the zip's own HOW_TO_USE.md ("Hiragana svg/Hiragana 55 ... Katakana svg/Katakana
57").

Usage: python3 generate_kana_manifest.py [--zip ./hyle-all-templates.zip]
                                          [--out ./kana-manifest.json]
Run from anywhere; paths default relative to this file's own directory.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_ZIP = HERE / "hyle-all-templates.zip"
DEFAULT_OUT = HERE / "kana-manifest.json"

EXPECTED_COUNTS = {"Hiragana": 55, "Katakana": 57}

# Mirrors dev.aarso.typewright.scripts.kana.deriveKanaGlyphName exactly (Kotlin is the
# source of truth for the running app; this copy exists only so the checked-in manifest
# can carry the same glyphName for humans reading the JSON, and is cross-checked against
# the Kotlin function's own unit tests by hand, not by running Kotlin from this script).
KANA_NAME_PREFIXES = {
    "Hiragana": [("HIRAGANA LETTER ", "-hira")],
    "Katakana": [
        ("KATAKANA LETTER ", "-kata"),
        ("KATAKANA-HIRAGANA ", "-kata"),
        ("KATAKANA ", "-kata"),
    ],
}


def derive_glyph_name(unicode_name: str, folder: str) -> str:
    for prefix, suffix in KANA_NAME_PREFIXES[folder]:
        if unicode_name.startswith(prefix):
            remainder = unicode_name[len(prefix):]
            slug = remainder.lower().replace(" ", "-")
            return f"{slug}{suffix}"
    raise ValueError(f"'{unicode_name}' matched no known {folder} Unicode-name prefix")


TEXT_LINE_RE = re.compile(
    r'<text x="-140" y="-960" font-family="monospace" font-size="42" fill="#111">'
    r"U\+([0-9A-Fa-f]{4,6})\s+(\S+)\s+(.+?)</text>",
)
GUIDE_VALUE_RE = re.compile(
    r"(body bottom|baseline|centre|body top) (-?\d+)",
)
ADVANCE_LINE_RE = re.compile(
    r'<line x1="(-?\d+)" y1="-1000" x2="-?\d+" y2="240" stroke="#2980b9"',
)


def parse_template(svg_text: str, file_name: str) -> dict:
    text_match = TEXT_LINE_RE.search(svg_text)
    if not text_match:
        raise ValueError(f"{file_name}: could not find the label <text> line")
    codepoint_hex, char, filename_style_name = text_match.groups()
    # The label's own third field uses underscores (e.g. "HIRAGANA_LETTER_A"); the real
    # Unicode character name uses spaces and, for one glyph, an internal hyphen
    # ("KATAKANA-HIRAGANA_PROLONGED_SOUND_MARK" -> "KATAKANA-HIRAGANA PROLONGED SOUND MARK").
    unicode_name = filename_style_name.replace("_", " ")

    guide_values = dict(GUIDE_VALUE_RE.findall(svg_text))
    missing = {"body bottom", "baseline", "centre", "body top"} - guide_values.keys()
    if missing:
        raise ValueError(f"{file_name}: missing guide line(s) {missing}")

    advance_xs = ADVANCE_LINE_RE.findall(svg_text)
    if len(advance_xs) != 2:
        raise ValueError(f"{file_name}: expected exactly 2 advance-width guide lines, found {len(advance_xs)}")
    left_sidebearing_x, advance_width_x = sorted(int(x) for x in advance_xs)

    return {
        "file": file_name,
        "codepoint": f"U+{codepoint_hex.upper()}",
        "char": char,
        "unicode_name": unicode_name,
        "guides": {
            "body_bottom": int(guide_values["body bottom"]),
            "baseline": int(guide_values["baseline"]),
            "virtual_body_centre": int(guide_values["centre"]),
            "body_top": int(guide_values["body top"]),
            "left_sidebearing": left_sidebearing_x,
            "advance_width": advance_width_x,
        },
    }


def build_manifest(zip_path: Path) -> dict:
    archive_bytes = zip_path.read_bytes()
    archive_sha256 = hashlib.sha256(archive_bytes).hexdigest()

    scripts: dict[str, dict] = {}
    with zipfile.ZipFile(zip_path) as zf:
        names = zf.namelist()
        for folder, expected_count in EXPECTED_COUNTS.items():
            svg_names = sorted(
                n for n in names if n.startswith(f"svg/{folder}/") and n.endswith(".svg")
            )
            if len(svg_names) != expected_count:
                raise ValueError(
                    f"svg/{folder} has {len(svg_names)} real .svg templates, expected "
                    f"{expected_count} per the zip's own HOW_TO_USE.md -- refusing to "
                    "generate a manifest that disagrees with the real archive.",
                )

            templates = []
            guide_sets = set()
            for name in svg_names:
                file_name = name.rsplit("/", 1)[-1]
                svg_text = zf.read(name).decode("utf-8")
                parsed = parse_template(svg_text, file_name)
                parsed["glyph_name"] = derive_glyph_name(parsed["unicode_name"], folder)
                templates.append(parsed)
                guide_sets.add(tuple(sorted(parsed["guides"].items())))

            if len(guide_sets) != 1:
                raise ValueError(
                    f"svg/{folder}'s {len(templates)} templates do not all share identical "
                    f"guide metrics ({len(guide_sets)} distinct guide sets found) -- this "
                    "generator assumes one shared metric system per script (this task's own "
                    "instruction) and refuses to silently pick one.",
                )
            (shared_guides,) = guide_sets

            codepoints = [t["codepoint"] for t in templates]
            if len(set(codepoints)) != len(codepoints):
                raise ValueError(f"svg/{folder} has duplicate codepoints among its templates")
            glyph_names = [t["glyph_name"] for t in templates]
            if len(set(glyph_names)) != len(glyph_names):
                raise ValueError(f"svg/{folder} produced duplicate derived glyph names")

            scripts[folder] = {
                "folder": f"svg/{folder}",
                "template_count": len(templates),
                "shared_guide_metrics": dict(shared_guides),
                "templates": templates,
            }

    return {
        "source": (
            f"scripts/templates/{zip_path.name} (real, already-populated asset; see its own "
            "HOW_TO_USE.md), generated by scripts/templates/generate_kana_manifest.py"
        ),
        "archive_sha256": archive_sha256,
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "scripts": scripts,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip", type=Path, default=DEFAULT_ZIP)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()

    manifest = build_manifest(args.zip)
    args.out.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    hira = manifest["scripts"]["Hiragana"]["template_count"]
    kata = manifest["scripts"]["Katakana"]["template_count"]
    print(f"Wrote {args.out} -- Hiragana {hira} templates, Katakana {kata} templates.")


if __name__ == "__main__":
    main()
