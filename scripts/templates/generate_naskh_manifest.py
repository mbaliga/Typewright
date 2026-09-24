#!/usr/bin/env python3
"""Generate scripts/templates/naskh-manifest.json from the real, already-populated
scripts/templates/hyle-all-templates.zip -- CLAUDE.md's own "Data packs ... checked in under
data/ with a generator script beside them; never hand-edit generated data" convention, the same
pattern data/learn-faces/manifest.json, data/node-economy-latin.json and
scripts/templates/kana-manifest.json (scripts/templates/generate_kana_manifest.py) already use.

Named naskh-manifest.json and placed beside the templates asset itself, not under data/, for the
exact reason generate_kana_manifest.py's own docstring gives: this build's own P8 task runs
several script agents (Hiragana/Katakana, Devanagari, Arabic Naskh) in parallel, each reading the
SAME hyle-all-templates.zip but writing to its OWN new package
(scripts/.../kana/, scripts/.../devanagari/, scripts/.../arabic/) so no two agents' generated
files collide. A shared scripts/templates/manifest.json would be exactly the kind of file two
concurrent agents could each independently create or regenerate and clobber -- scoping this
generator and its own output to Naskh only removes that risk.

This is a one-time, offline listing generator: it opens the zip already checked into this repo
(no network call -- CLAUDE.md law 3) and reads svg/Naskh/'s own real file listing, one entry per
file. It does not invent glyph coverage: every entry's Unicode name and codepoint come straight
from each template's own embedded <text> label (e.g. "U+0627  ا  ARABIC_LETTER_ALEF"), cross-
checked against Python's own unicodedata database (stdlib) rather than typed from memory, and the
four real vertical-metric guide values (ascender, tooth height, baseline, descender) come from
each template's own embedded guide <text> labels, not invented -- the script fails loudly (raises)
if the real folder's file count is not 49 (scripts/templates/hyle-all-templates.zip's own
HOW_TO_USE.md: "Naskh | svg/Naskh | 49 | isolated base letters; positional forms generated at
build") or if the 49 templates do not all share identical guide values.

One real fact this generator cannot get from the zip or from Python's stdlib: which of the 39
real letters are Unicode "dual-joining" (get four positional forms) versus "right-joining" or
"non-joining" (isolated form only -- see this task's own instruction, "for each dual-joining
letter, also generate its three positional variants"). That is Unicode's own Joining_Type
property (UAX #53's ArabicShaping.txt), which ships as a separate Unicode Character Database file
that this repository does not carry a local copy of and Python's stdlib `unicodedata` module does
not expose. JOINING_TYPE_BY_CODEPOINT below is therefore a hand-entered table, cross-checked
against the real ArabicShaping.txt (Unicode 18.0.0, https://www.unicode.org/Public/UCD/latest/ucd/
ArabicShaping.txt) for exactly these 39 codepoints at the time this generator was written, not
guessed -- see docs/OPEN_QUESTIONS.md for the write-up, since a future re-run of this script has
no local file to re-check it against.

Usage: python3 generate_naskh_manifest.py [--zip ./hyle-all-templates.zip]
                                           [--out ./naskh-manifest.json]
Run from anywhere; paths default relative to this file's own directory.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import unicodedata
import zipfile
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_ZIP = HERE / "hyle-all-templates.zip"
DEFAULT_OUT = HERE / "naskh-manifest.json"

FOLDER = "Naskh"
EXPECTED_COUNT = 49

# Unicode Joining_Type, by codepoint, for exactly the 39 real ARABIC LETTER codepoints
# svg/Naskh/'s own templates cover (000-038; 039-048 are the ten ARABIC-INDIC DIGIT codepoints,
# which are
# outside the cursive-joining system entirely and are not in this table -- see this generator's
# own docstring and ArabicJoiningType.kt's own KDoc). Cross-checked against the real
# ArabicShaping.txt (Unicode 18.0.0), not guessed: "R" = right-joining only (isolated + final
# forms only), "D" = dual-joining (all four positional forms), "U" = non-joining (isolated form
# only). No codepoint in this table is "C" (join-causing) or "T" (transparent) -- neither value
# applies to any base letter.
JOINING_TYPE_BY_CODEPOINT = {
    0x0627: "R",  # ALEF
    0x0628: "D",  # BEH
    0x067E: "D",  # PEH
    0x062A: "D",  # TEH
    0x0679: "D",  # TTEH
    0x062B: "D",  # THEH
    0x062C: "D",  # JEEM
    0x0686: "D",  # TCHEH
    0x062D: "D",  # HAH
    0x062E: "D",  # KHAH
    0x062F: "R",  # DAL
    0x0688: "R",  # DDAL
    0x0630: "R",  # THAL
    0x0631: "R",  # REH
    0x0691: "R",  # RREH
    0x0632: "R",  # ZAIN
    0x0698: "R",  # JEH
    0x0633: "D",  # SEEN
    0x0634: "D",  # SHEEN
    0x0635: "D",  # SAD
    0x0636: "D",  # DAD
    0x0637: "D",  # TAH
    0x0638: "D",  # ZAH
    0x0639: "D",  # AIN
    0x063A: "D",  # GHAIN
    0x0641: "D",  # FEH
    0x0642: "D",  # QAF
    0x06A9: "D",  # KEHEH
    0x06AF: "D",  # GAF
    0x0644: "D",  # LAM
    0x0645: "D",  # MEEM
    0x0646: "D",  # NOON
    0x06BA: "D",  # NOON GHUNNA
    0x0648: "R",  # WAW
    0x06C1: "D",  # HEH GOAL
    0x06BE: "D",  # HEH DOACHASHMEE (Unicode: KNOTTED HEH joining group)
    0x0621: "U",  # HAMZA
    0x06CC: "D",  # FARSI YEH
    0x06D2: "R",  # YEH BARREE
}

TEXT_LINE_RE = re.compile(
    r'<text x="-140" y="-800" font-family="monospace" font-size="42" fill="#111">'
    r"U\+([0-9A-Fa-f]{4,6})\s+(\S+)\s+(.+?)</text>",
)
GUIDE_VALUE_RE = re.compile(r"(descender|baseline|tooth height|ascender) (-?\d+)")


def glyph_base_name_for(unicode_name: str) -> str:
    """This build's own Arabic naming convention's *base* name, before the -ar suffix
    ([ArabicGlyphInventory] and [ArabicTemplateSheet] append that mechanically): strip the
    "ARABIC LETTER " / "ARABIC INDIC DIGIT " prefix, camelCase the remainder with no separator
    (e.g. "NOON GHUNNA" -> "noonGhunna"), matching Devanagari's own camelCase multi-word pattern
    (build_devanagari_template_manifest.py's own vocalicR-deva) rather than the underscore-joined
    convention docs/RESEARCH_font_quality.md reserves for Devanagari conjunct ligature names.
    """
    m = re.match(r"ARABIC(?:-INDIC)? (LETTER|DIGIT) (.+)$", unicode_name)
    if not m:
        raise ValueError(f"unrecognised Naskh Unicode name shape: {unicode_name!r}")
    rest = m.group(2)
    words = rest.split(" ")
    return words[0].lower() + "".join(w.capitalize() for w in words[1:])


def parse_template(svg_text: str, file_name: str) -> dict:
    text_match = TEXT_LINE_RE.search(svg_text)
    if not text_match:
        raise ValueError(f"{file_name}: could not find the label <text> line")
    codepoint_hex, char, filename_style_name = text_match.groups()
    unicode_name = filename_style_name.replace("_", " ")

    real_codepoint = int(codepoint_hex, 16)
    try:
        looked_up_codepoint = ord(unicodedata.lookup(unicode_name))
    except KeyError:
        looked_up_codepoint = None
    if looked_up_codepoint != real_codepoint:
        raise ValueError(
            f"{file_name}: label says U+{codepoint_hex} for {unicode_name!r} but Python's own "
            f"unicodedata.lookup resolves that name to "
            f"{'U+%04X' % looked_up_codepoint if looked_up_codepoint else 'nothing'} -- refusing "
            "to trust an unverified codepoint.",
        )

    guide_values = dict(GUIDE_VALUE_RE.findall(svg_text))
    missing = {"descender", "baseline", "tooth height", "ascender"} - guide_values.keys()
    if missing:
        raise ValueError(f"{file_name}: missing guide line(s) {missing}")

    is_digit = unicode_name.startswith("ARABIC-INDIC DIGIT ")
    joining_type = None if is_digit else JOINING_TYPE_BY_CODEPOINT.get(real_codepoint)
    if not is_digit and joining_type is None:
        raise ValueError(
            f"{file_name}: U+{codepoint_hex} ({unicode_name}) has no entry in "
            "JOINING_TYPE_BY_CODEPOINT -- refusing to silently treat an unclassified letter as "
            "non-joining.",
        )

    return {
        "file": file_name,
        "codepoint": f"U+{codepoint_hex.upper()}",
        "char": char,
        "unicode_name": unicode_name,
        "glyph_base_name": glyph_base_name_for(unicode_name),
        "is_digit": is_digit,
        "joining_type": joining_type,
        "guides": {
            "ascender": int(guide_values["ascender"]),
            "tooth_height": int(guide_values["tooth height"]),
            "baseline": int(guide_values["baseline"]),
            "descender": int(guide_values["descender"]),
        },
    }


def build_manifest(zip_path: Path) -> dict:
    archive_bytes = zip_path.read_bytes()
    archive_sha256 = hashlib.sha256(archive_bytes).hexdigest()

    with zipfile.ZipFile(zip_path) as zf:
        names = sorted(
            n for n in zf.namelist() if n.startswith(f"svg/{FOLDER}/") and n.endswith(".svg")
        )
        if len(names) != EXPECTED_COUNT:
            raise ValueError(
                f"svg/{FOLDER} has {len(names)} real .svg templates, expected {EXPECTED_COUNT} "
                "per the zip's own HOW_TO_USE.md -- refusing to generate a manifest that "
                "disagrees with the real archive.",
            )

        templates = []
        guide_sets = set()
        for name in names:
            file_name = name.rsplit("/", 1)[-1]
            svg_text = zf.read(name).decode("utf-8")
            parsed = parse_template(svg_text, file_name)
            templates.append(parsed)
            guide_sets.add(tuple(sorted(parsed["guides"].items())))

        if len(guide_sets) != 1:
            raise ValueError(
                f"svg/{FOLDER}'s {len(templates)} templates do not all share identical guide "
                f"metrics ({len(guide_sets)} distinct guide sets found) -- this generator assumes "
                "one shared metric system for the script and refuses to silently pick one.",
            )
        (shared_guides,) = guide_sets

        base_names = [t["glyph_base_name"] for t in templates]
        if len(set(base_names)) != len(base_names):
            raise ValueError(f"svg/{FOLDER} produced duplicate derived glyph base names")
        codepoints = [t["codepoint"] for t in templates]
        if len(set(codepoints)) != len(codepoints):
            raise ValueError(f"svg/{FOLDER} has duplicate codepoints among its templates")

    dual_joining = sum(1 for t in templates if t["joining_type"] == "D")
    right_joining = sum(1 for t in templates if t["joining_type"] == "R")
    non_joining = sum(1 for t in templates if t["joining_type"] == "U")
    digits = sum(1 for t in templates if t["is_digit"])

    return {
        "source": (
            f"scripts/templates/{zip_path.name} (real, already-populated asset; see its own "
            "HOW_TO_USE.md), generated by scripts/templates/generate_naskh_manifest.py"
        ),
        "archive_sha256": archive_sha256,
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "folder": f"svg/{FOLDER}",
        "template_count": len(templates),
        "shared_guide_metrics": dict(shared_guides),
        "letter_count": len(templates) - digits,
        "digit_count": digits,
        "dual_joining_count": dual_joining,
        "right_joining_count": right_joining,
        "non_joining_count": non_joining,
        "templates": templates,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip", type=Path, default=DEFAULT_ZIP)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()

    manifest = build_manifest(args.zip)
    args.out.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(
        f"Wrote {args.out} -- {manifest['template_count']} templates "
        f"({manifest['letter_count']} letters + {manifest['digit_count']} digits), "
        f"{manifest['dual_joining_count']} dual-joining, {manifest['right_joining_count']} "
        f"right-joining, {manifest['non_joining_count']} non-joining.",
    )


if __name__ == "__main__":
    main()
