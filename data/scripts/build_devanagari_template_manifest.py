#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Generate scripts/templates/devanagari-manifest.json from the real, already-populated
scripts/templates/hyle-all-templates.zip -- CLAUDE.md's own "Data packs ... checked in under
data/ with a generator script beside them; never hand-edit generated data" convention, the same
pattern data/learn-faces/manifest.json (data/scripts/fetch_learn_faces.py) and
data/node-economy-latin.json (data/scripts/build_node_economy_corpus.py) already use.

This is a one-time, offline listing generator: it opens the zip already checked into this repo
(no network call -- CLAUDE.md law 3) and reads svg/Devanagari/'s own real file listing, one entry
per file, exactly as scripts/templates/hyle-all-templates.zip's own HOW_TO_USE.md documents
("svg/Devanagari | 68 | vowels, consonants, matras, digits; conjuncts come later in shaping").
It does not invent glyph coverage: the Unicode name in each filename is looked up against
Python's own unicodedata database (stdlib, ships with every CPython) to get a real codepoint,
and this build's own glyph-naming convention (Devanagari glyphs end -deva; a vowel SIGN --
a matra -- is named "<vowel>Matra-deva", the one pattern docs/RESEARCH_font_quality.md's own
Devanagari section evidences directly by name: "Glyphs' workflow starts with aaMatra-deva ...
several iMatra-deva length variants") is applied mechanically from the Unicode name's own
LETTER / VOWEL SIGN / SIGN / DIGIT category, never hand-typed per glyph.

Two real duplicate pairs exist in the zip's own listing, confirmed here by content hash, not
guessed: 000_DEVANAGARI_LETTER_A.svg and 011_DEVANAGARI_LETTER_A.svg are byte-identical (same
md5), and so are 012_DEVANAGARI_SIGN_ANUSVARA.svg and 056_DEVANAGARI_SIGN_ANUSVARA.svg. The
folder genuinely holds 68 files (matching HOW_TO_USE.md's own count) but only 66 distinct
Unicode names / glyphs. Both facts are true and both are recorded here: "template_count": 68
(files in the folder) and "distinct_glyph_count": 66 (unique glyphs those files cover), with
every duplicate file's own manifest entry carrying "duplicate_of" naming the earlier index it
repeats. See docs/OPEN_QUESTIONS.md for the write-up.

Usage: python3 build_devanagari_template_manifest.py [--zip ../../scripts/templates/hyle-all-templates.zip]
                                                       [--out ../../scripts/templates/devanagari-manifest.json]
Run from anywhere; paths default relative to this file's own directory.
"""
import argparse
import hashlib
import json
import re
import unicodedata
import zipfile
from pathlib import Path

FOLDER = "Devanagari"


def glyph_name_for(unicode_name: str) -> str:
    """This build's own Devanagari naming convention, applied mechanically from the Unicode
    character name's own category word (LETTER / VOWEL SIGN / SIGN / DIGIT):
      - LETTER <X>        -> "<x>-deva"       (independent vowels and consonants alike)
      - VOWEL SIGN <X>    -> "<x>Matra-deva"  (a dependent vowel sign, i.e. a matra --
                              docs/RESEARCH_font_quality.md evidences aaMatra-deva, iMatra-deva
                              directly; every other vowel sign follows the same pattern)
      - SIGN <X>          -> "<x>-deva"       (anusvara, visarga)
      - DIGIT <X>         -> "<x>-deva"       (digit name lowercased, e.g. "zero-deva")
    <X> is lowercased and its own multi-word remainder (e.g. "VOCALIC R") is camelCased with no
    separator (e.g. "vocalicR"), matching aaMatra-deva's own camelCase shape (aa + Matra, no
    underscore) rather than the underscore-joined convention, which the research reserves for
    conjunct ligature names (e.g. "ka_ssa-deva" for the Ka+Ssa akhand ligature), not single
    glyphs.
    """
    m = re.match(r"DEVANAGARI (LETTER|VOWEL SIGN|SIGN|DIGIT) (.+)$", unicode_name)
    if not m:
        raise ValueError(f"unrecognised Devanagari Unicode name shape: {unicode_name!r}")
    category, rest = m.group(1), m.group(2)
    words = rest.split(" ")
    camel = words[0].lower() + "".join(w.capitalize() for w in words[1:])
    if category == "VOWEL SIGN":
        return f"{camel}Matra-deva"
    return f"{camel}-deva"


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    here = Path(__file__).resolve().parent  # data/scripts/
    templates_dir = here.parent.parent / "scripts" / "templates"
    ap.add_argument("--zip", default=str(templates_dir / "hyle-all-templates.zip"))
    ap.add_argument("--out", default=str(templates_dir / "devanagari-manifest.json"))
    args = ap.parse_args()

    zip_path = Path(args.zip)
    out_path = Path(args.out)

    entries = []
    hashes_seen: dict[str, int] = {}  # md5 -> first index that produced it
    with zipfile.ZipFile(zip_path) as zf:
        names = [n for n in zf.namelist() if n.startswith(f"svg/{FOLDER}/") and n.endswith(".svg")]
        for name in sorted(names):
            filename = name.rsplit("/", 1)[-1]
            m = re.match(r"(\d+)_(.+)\.svg$", filename)
            if not m:
                raise ValueError(f"unrecognised template filename shape: {filename!r}")
            index = int(m.group(1))
            unicode_name = m.group(2).replace("_", " ")
            data = zf.read(name)
            digest = hashlib.md5(data).hexdigest()

            try:
                codepoint = ord(unicodedata.lookup(unicode_name))
            except KeyError:
                codepoint = None

            duplicate_of = hashes_seen.get(digest)
            if duplicate_of is None:
                hashes_seen[digest] = index

            entries.append(
                {
                    "index": index,
                    "template_file": filename,
                    "template_folder": f"svg/{FOLDER}",
                    "unicode_name": unicode_name,
                    "codepoint": f"U+{codepoint:04X}" if codepoint is not None else None,
                    "glyph_name": glyph_name_for(unicode_name),
                    "file_size_bytes": len(data),
                    "file_md5": digest,
                    "duplicate_of_index": duplicate_of,
                },
            )

    distinct_glyphs = sorted({e["glyph_name"] for e in entries})

    manifest = {
        "source": f"{zip_path.name}'s own svg/{FOLDER}/ folder, read directly (no network), "
        "by data/scripts/build_devanagari_template_manifest.py",
        "script_folder": f"svg/{FOLDER}",
        "template_count": len(entries),
        "distinct_glyph_count": len(distinct_glyphs),
        "duplicate_pairs": [
            {"index": e["index"], "template_file": e["template_file"], "duplicate_of_index": e["duplicate_of_index"]}
            for e in entries
            if e["duplicate_of_index"] is not None
        ],
        "entries": entries,
    }
    out_path.write_text(json.dumps(manifest, indent=2, sort_keys=False) + "\n")
    print(
        f"wrote {out_path} -- {len(entries)} templates, {len(distinct_glyphs)} distinct glyphs, "
        f"{len(manifest['duplicate_pairs'])} duplicate file(s)",
    )


if __name__ == "__main__":
    main()
