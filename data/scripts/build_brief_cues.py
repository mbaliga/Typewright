#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Build the Brief's cue faces: the real fonts its questions are asked in.

Every option on a Brief question is shown in a real typeface, never a drawing of one (brief
principle 11: lessons are live geometry from real fonts). This script fetches those typefaces
from google/fonts at the commit the node-economy corpus is pinned to, takes the instance the
cue needs (Regular unless the cue is about weight), removes overlaps, subsets it to Basic Latin
and renames it, then writes:

- data/brief-cues/<key>.ttf, each face's subset, and data/brief-cues/<slug>-OFL.txt, its licence;
- data/brief-cues/manifest.json, what each file is and where it came from;
- ui/src/commonMain/kotlin/com/asoc/typewright/ui/brief/BriefCueFontData.kt, the same bytes
  embedded as base64 so the cues render on every target, the browser included, with no resource
  loading (the gap docs/OPEN_QUESTIONS.md item 8 describes for the data packs).

Renaming: an instanced, subset font is a Modified Version under the OFL, which may not carry a
Reserved Font Name. Every cue is renamed "Typewright Cue <Key>"; its copyright and licence name
records are kept, and THIRD_PARTY.md credits each family.

Then rerun data/scripts/fetch_style_atlas_fonts.py and the atlas generator, so the atlas's
"bundled" section measures these exact files.

Usage: python3 build_brief_cues.py   (needs fontTools and skia-pathops; network to
raw.githubusercontent.com)
"""
import base64
import hashlib
import io
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))
import build_node_economy_corpus as nec  # noqa: E402

from fontTools import subset  # noqa: E402
from fontTools.ttLib import TTFont  # noqa: E402
from fontTools.varLib import instancer  # noqa: E402

OUT_DIR = ROOT / "data" / "brief-cues"
KOTLIN_OUT = ROOT / "ui" / "src" / "commonMain" / "kotlin" / "com" / "asoc" / "typewright" / "ui" / "brief" / "BriefCueFontData.kt"

# key, family, wght (None = the file's own default for a static font), what the cue shows.
CUES = [
    ("geometric", "Poppins", 400, "geometric sans: one-storey a, circular o, even stroke"),
    ("weightLight", "Jost", 300, "one family at three weights: light"),
    ("weightRegular", "Jost", 400, "one family at three weights: regular"),
    ("weightBold", "Jost", 700, "one family at three weights: bold"),
    ("grotesque", "Work Sans", 400, "grotesque: two-storey a, flat terminals"),
    ("neogrotesque", "Inter", 400, "neo-grotesque: large x-height, closed apertures"),
    ("humanist", "Open Sans", 400, "humanist sans: open apertures, two-storey g"),
    ("garalde", "EB Garamond", 400, "garalde: diagonal stress, bracketed serifs, small x-height"),
    ("transitional", "Libre Baskerville", 400, "transitional serif"),
    ("didone", "Libre Bodoni", 400, "didone: vertical stress, hairline serifs, ball terminals"),
    ("slab", "Zilla Slab", 400, "slab serif"),
    ("deco", "Limelight", None, "art deco, heavy with hairlines, high-waisted"),
    ("decoAiry", "Poiret One", None, "art deco, airy and monoline"),
    ("superellipse", "Michroma", None, "squarish rounds, wide"),
    ("techno", "Orbitron", 400, "square rounds, techno"),
    ("condensed", "League Gothic", None, "narrow, condensed"),
    ("rounded", "Varela Round", None, "rounded terminals"),
    ("lowWaist", "Voltaire", None, "a low crossbar, angled terminals"),
    ("script", "Dancing Script", 400, "script"),
    ("blackletter", "UnifrakturMaguntia", None, "blackletter"),
    ("fatface", "Abril Fatface", None, "fat face"),
    ("mono", "Space Mono", None, "monospace"),
]

# Basic Latin letters, digits and the punctuation a cue or a watch-face proof uses.
CHARSET = (
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    " .,:;!?'\"-()/&@%#*+=·–—’°"
)
LAYOUT_FEATURES = ["kern", "liga", "calt", "tnum", "lnum", "pnum", "onum", "case"]


def fetch_regular(family):
    slug = nec.dir_name(family)
    for lic in ("ofl", "apache", "ufl"):
        try:
            meta = nec.fetch(f"{nec.RAW}{lic}/{slug}/METADATA.pb").decode("utf-8", "replace")
        except Exception:
            continue
        fn = nec.regular_filename(meta)
        if not fn:
            break
        data = nec.fetch(f"{nec.RAW}{lic}/{slug}/{fn}")
        licence_name = "OFL.txt" if lic == "ofl" else ("LICENSE.txt" if lic == "apache" else "UFL.txt")
        licence = nec.fetch(f"{nec.RAW}{lic}/{slug}/{licence_name}")
        return slug, lic, fn, data, licence
    raise RuntimeError(f"no METADATA.pb for {family}")


def build(key, family, wght):
    slug, lic, fn, data, licence = fetch_regular(family)
    if lic != "ofl":
        raise RuntimeError(f"{family} is {lic}, not OFL; the cue set is OFL only")
    font = TTFont(io.BytesIO(data))
    if "fvar" in font:
        location = {}
        for axis in font["fvar"].axes:
            if axis.axisTag == "wght" and wght is not None:
                location["wght"] = min(max(float(wght), axis.minValue), axis.maxValue)
            else:
                location[axis.axisTag] = axis.defaultValue
        font = instancer.instantiateVariableFont(font, location, overlap=instancer.OverlapMode.REMOVE)
    opts = subset.Options()
    opts.layout_features = LAYOUT_FEATURES
    opts.name_IDs = ["*"]
    opts.name_languages = ["*"]
    opts.notdef_outline = True
    opts.hinting = False
    opts.drop_tables += ["DSIG", "STAT", "fvar", "gvar", "avar", "HVAR", "MVAR"]
    subsetter = subset.Subsetter(opts)
    subsetter.populate(text=CHARSET)
    subsetter.subset(font)

    copyright_line = font["name"].getDebugName(0) or ""
    cue_family = "Typewright Cue " + key[0].upper() + key[1:]
    ps_name = "TypewrightCue-" + key[0].upper() + key[1:]
    for record in list(font["name"].names):
        if record.nameID in (1, 3, 4, 6, 16, 17, 21, 22, 25):
            font["name"].removeNames(nameID=record.nameID)
    font["name"].setName(cue_family, 1, 3, 1, 0x409)
    font["name"].setName("Regular", 2, 3, 1, 0x409)
    font["name"].setName(cue_family, 4, 3, 1, 0x409)
    font["name"].setName(ps_name, 6, 3, 1, 0x409)
    font["name"].setName(f"{ps_name};{family};google/fonts@{SHA[:12]}", 3, 3, 1, 0x409)

    buf = io.BytesIO()
    font.save(buf)
    out = buf.getvalue()
    (OUT_DIR / f"{key}.ttf").write_bytes(out)
    licence_file = f"{slug}-OFL.txt"
    (OUT_DIR / licence_file).write_bytes(licence)
    return {
        "key": key,
        "family": family,
        "instance": {"wght": wght} if wght is not None else "default",
        "file": f"{key}.ttf",
        "renamed": cue_family,
        "licence": "OFL-1.1",
        "licence_file": licence_file,
        "copyright": copyright_line,
        "source_font_url": f"{nec.RAW}{lic}/{slug}/{fn}",
        "file_size_bytes": len(out),
        "file_sha256": hashlib.sha256(out).hexdigest(),
    }


CHUNK = 16000


def kotlin_source(entries, blobs):
    lines = [
        "// SPDX-License-Identifier: OFL-1.1",
        "",
        "// Generated by data/scripts/build_brief_cues.py. Do not edit: rerun the script.",
        "//",
        "// The Brief's cue faces, subset to Basic Latin, instanced and renamed \"Typewright Cue <Key>\"",
        "// (a Modified Version under the SIL Open Font License 1.1, which keeps any Reserved Font Name",
        "// off it). Each face's copyright and licence are in its own name table, in",
        "// data/brief-cues/manifest.json and in THIRD_PARTY.md. The licence text is in",
        "// data/brief-cues/<family>-OFL.txt and at https://openfontlicense.org.",
        "",
        "package com.asoc.typewright.ui.brief",
        "",
        "/** Base64 of each cue face's TTF, keyed by cue key. Each string stays below the JVM's 64 KB constant limit. */",
        "internal val BRIEF_CUE_FONT_BASE64: Map<String, String> by lazy {",
        "    mapOf(",
    ]
    for e in entries:
        lines.append(f'        "{e["key"]}" to {e["key"].upper()}_CUE,')
    lines += ["    )", "}", ""]
    for e in entries:
        b64 = blobs[e["key"]]
        if len(b64) >= 65000:
            raise RuntimeError(f"{e['key']}: base64 is {len(b64)} bytes, over the JVM constant limit")
        copyright = " ".join(e["copyright"].split())[:120]
        comment = f"// {e['family']} ({e['instance']}), {copyright}"
        lines.append(comment if len(comment) <= 140 else comment[:137].rstrip() + "...")
        chunks = [b64[i : i + CHUNK] for i in range(0, len(b64), CHUNK)]
        name = f"{e['key'].upper()}_CUE"
        for i, c in enumerate(chunks):
            lines.append(f'private const val {name}_{i}: String =')
            lines.append(f'    "{c}"')
            lines.append("")
        lines.append(f"private const val {name}: String = " + " + ".join(f"{name}_{i}" for i in range(len(chunks))))
        lines.append("")
    return "\n".join(lines)


def main():
    global SHA
    corpus = json.load(open(ROOT / "data" / "node-economy-latin.json"))
    m = re.search(r"google/fonts@([0-9a-f]{40})", corpus["source"])
    SHA = m.group(1)
    nec.RAW = nec.RAW_TEMPLATE.format(ref=SHA)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for old in OUT_DIR.glob("*.ttf"):
        old.unlink()
    entries, blobs = [], {}
    for key, family, wght, what in CUES:
        e = build(key, family, wght)
        e["shows"] = what
        entries.append(e)
        blobs[key] = base64.b64encode((OUT_DIR / e["file"]).read_bytes()).decode("ascii")
        print(f"{key:16s} {family:20s} {e['file_size_bytes']:7,d} bytes", file=sys.stderr)
    manifest = {
        "source": f"google/fonts@{SHA} (raw.githubusercontent.com/google/fonts/{SHA}/), built by data/scripts/build_brief_cues.py",
        "commit": SHA,
        "charset": CHARSET,
        "cues": {e["key"]: e for e in entries},
    }
    (OUT_DIR / "manifest.json").write_text(json.dumps(manifest, indent=1, ensure_ascii=False) + "\n")
    KOTLIN_OUT.parent.mkdir(parents=True, exist_ok=True)
    KOTLIN_OUT.write_text(kotlin_source(entries, blobs) + "\n")
    total = sum(e["file_size_bytes"] for e in entries)
    print(f"wrote {len(entries)} cue faces, {total:,} bytes; {KOTLIN_OUT.relative_to(ROOT)}", file=sys.stderr)


SHA = ""

if __name__ == "__main__":
    main()
