#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Fetch the fonts the style atlas measures (step 1 of 2).

The style atlas (data/style-atlas-latin.json) is the measured half of the Brief: for each
style class, the distribution of every style feature the app's own detector measures, so a
brief's targets are ranges taken from professional fonts rather than numbers someone typed
(CLAUDE.md law 5). This script only downloads; the measuring is done by the app's own Kotlin
code, so the atlas and the drift check can never disagree about what a feature means:

    python3 data/scripts/fetch_style_atlas_fonts.py            # step 1, this file
    ./gradlew :qa:corpus:jvmTest --tests "*StyleAtlasGeneratorTest*"   # step 2, writes the pack

What it fetches, all from the google/fonts commit the node-economy corpus is pinned to:

- every family in data/node-economy-latin.json (the same 30-per-class faces behind the
  Economy boxes, so a class's n is the same number in both packs);
- the genres a brief can name that the node-economy corpus does not cover yet (rounded,
  superellipse, glyphic, Venetian, Scotch, fat face, the scripts, techno, stencil, inline,
  monospace), chosen by the corpus's own rule: tag score >= 50, ranked by /Quality/Drawing,
  one face per superfamily, top --top. They get feature distributions but no Economy box;
- with --feelings (the default), the top --per-feeling families for each of Google's
  /Expressive tags in tags/all/families.csv, one face per superfamily, scored at the
  family's default location. These feed the Brief's "a feeling" door.

tags/all/families.csv is read at build time and never committed or shipped: google/fonts
states no licence for tags/ (docs/OPEN_QUESTIONS.md item 121). The atlas keeps only what the
app needs from it: which families a feeling's measurements came from, and their scores.

Output (not committed; the fonts are third-party binaries): --out-dir/fonts/<slug>/<file> and
--out-dir/manifest.json, which step 2 reads.

Usage: python3 fetch_style_atlas_fonts.py [--out-dir ../../qa/corpus/build/style-atlas-fonts]
                                          [--per-feeling 14] [--no-feelings] [--jobs 8]

Needs fontTools and skia-pathops (pip install fonttools skia-pathops): variable fonts are
measured as their Regular instance with overlaps removed.
"""
import argparse
import collections
import concurrent.futures as cf
import csv
import io
import json
import re
import sys
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build_node_economy_corpus as nec  # noqa: E402  (sibling script: fetch, slugs, METADATA parsing)

from fontTools.ttLib import TTFont  # noqa: E402
from fontTools import subset  # noqa: E402
from fontTools.varLib import instancer  # noqa: E402

LATIN_PROBE = "onageHTcsx"  # the detector's glyph set; a face missing all of them is dropped
MEASURED_GLYPHS = "onageHTcsxI0123456789"  # what a Regular instance keeps (the detector's set, plus I and figures)
FEELING_MIN_SCORE = 60  # on Google's 0-100 tag scale

# Genres the Brief offers beyond the ten node-economy classes. Keys follow the corpus's own
# naming (group-name); values are the google/fonts tags that define them.
EXTRA_CLASSES = {
    "sans-rounded": ("/Sans/Rounded",),
    "sans-superellipse": ("/Sans/Superellipse",),
    "sans-glyphic": ("/Sans/Glyphic",),
    "serif-venetian": ("/Serif/Humanist Venetian",),
    "serif-scotch": ("/Serif/Scotch",),
    "serif-fatface": ("/Serif/Fat Face",),
    "script-formal": ("/Script/Formal",),
    "script-informal": ("/Script/Informal",),
    "script-handwritten": ("/Script/Handwritten",),
    "display-techno": ("/Theme/Techno",),
    "display-stencil": ("/Theme/Stencil",),
    "display-inline": ("/Theme/Inline",),
    "monospace": ("/Monospace/Monospace",),
}


def corpus_rule(fam_tag, tags, top):
    """The node-economy corpus's selection rule (build_node_economy_corpus.main), reused verbatim:
    tag score >= 50, sorted by (drawing, tag score, name) descending, one face per superfamily."""
    cands = []
    for fam, t in fam_tag.items():
        sc = max((t.get(x, 0) for x in tags), default=0)
        if sc >= 50:
            cands.append((t.get("/Quality/Drawing", 0), sc, fam))
    cands.sort(reverse=True)
    seen, dedup = set(), []
    for c in cands:
        root = c[2].split()[0].lower()
        if root in seen:
            continue
        seen.add(root)
        dedup.append(c)
    return [{"family": c[2], "drawing": c[0]} for c in dedup[: top + 6]]


def pinned_commit(corpus_source: str) -> str:
    m = re.search(r"google/fonts@([0-9a-f]{40})", corpus_source)
    if not m:
        sys.exit("node-economy-latin.json's source field names no pinned google/fonts commit")
    return m.group(1)


def default_location_scores(text: str):
    """family -> {tag: score} from families.csv, preferring each family's default-location row.

    Rows are (family, axis-location, tag, score). A variable family can carry extra rows scored
    at other axis positions (e.g. 'wght@700'); the Brief describes a family as drawn at its
    default, so the row with an empty location wins when there is one.
    """
    default = collections.defaultdict(dict)
    located = collections.defaultdict(dict)
    for row in csv.reader(io.StringIO(text)):
        if len(row) < 4:
            continue
        family, location, tag, score = row[0], row[1].strip(), row[2], row[3]
        try:
            value = float(score)
        except ValueError:
            continue
        target = default if location == "" else located
        target[family][tag] = max(target[family].get(tag, 0.0), value)
    merged = {}
    for family in set(default) | set(located):
        tags = dict(located.get(family, {}))
        tags.update(default.get(family, {}))
        merged[family] = tags
    return merged


def find_regular(family: str):
    """(licence dir, regular file name) for a family, read from its METADATA.pb."""
    slug = nec.dir_name(family)
    for lic in ("ofl", "apache", "ufl"):
        try:
            meta = nec.fetch(f"{nec.RAW}{lic}/{slug}/METADATA.pb").decode("utf-8", "replace")
        except Exception:
            continue
        fn = nec.regular_filename(meta)
        return (lic, fn) if fn else None
    return None


def has_latin(path: Path) -> bool:
    try:
        cmap = TTFont(str(path), lazy=True).getBestCmap() or {}
    except Exception:
        return False
    return any(ord(ch) in cmap for ch in LATIN_PROBE)


def regular_instance(path: Path) -> Path:
    """A variable font's Regular: every axis pinned, wght at 400 (clamped to the axis range), the
    rest at their defaults. 65 of the faces measured here default to another weight (Montserrat's
    default master is Thin), and a style feature such as stem or contrast read off a Thin master
    says nothing about the Regular the class is known by. Static fonts are returned unchanged."""
    font = TTFont(str(path))
    if "fvar" not in font:
        return path
    out = path.with_name(path.stem + ".regular.ttf")
    if out.exists():
        return out
    # Only the detector's glyphs are measured, so subset to them first: removing overlaps from
    # every glyph of a large (say CJK-covering) variable font would take minutes per face.
    opts = subset.Options()
    opts.layout_features = []
    opts.notdef_outline = True
    opts.name_IDs = ["*"]
    sub = subset.Subsetter(opts)
    sub.populate(text=MEASURED_GLYPHS)
    sub.subset(font)
    location = {}
    for axis in font["fvar"].axes:
        if axis.axisTag == "wght":
            location[axis.axisTag] = min(max(400.0, axis.minValue), axis.maxValue)
        else:
            location[axis.axisTag] = axis.defaultValue
    # Variable fonts keep overlapping contours; the detector's probes read ink by the even-odd
    # rule, which turns an overlap into a hole. Removing overlaps (skia-pathops) makes the static
    # instance read the way it renders.
    static = instancer.instantiateVariableFont(font, location, overlap=instancer.OverlapMode.REMOVE)
    static.save(str(out))
    return out


def download(job, out_dir: Path):
    """Fetch one family's regular face into out_dir/fonts/<slug>/<file>; cached on disk."""
    family, lic, fn = job["family"], job.get("licence"), job.get("file")
    if lic is None or fn is None:
        found = find_regular(family)
        if not found:
            return None
        lic, fn = found
    slug = nec.dir_name(family)
    dest = out_dir / "fonts" / slug / fn
    if not dest.exists():
        data = None
        for candidate in ([lic] if lic else []) + [d for d in ("ofl", "apache", "ufl") if d != lic]:
            try:
                data = nec.fetch(f"{nec.RAW}{candidate}/{slug}/{fn}")
                lic = candidate
                break
            except Exception:
                continue
        if data is None:
            return None
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
    if not has_latin(dest):
        print("skip (no Latin letters):", family, file=sys.stderr)
        return None
    try:
        measured = regular_instance(dest)
    except Exception as e:  # noqa: BLE001 - an instancing failure falls back to the default master, and says so
        print("could not instance", family, e, file=sys.stderr)
        measured = dest
    return {"family": family, "file": fn, "licence": lic, "path": str(measured.relative_to(out_dir))}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--corpus", default=str(HERE.parent / "node-economy-latin.json"))
    ap.add_argument("--out-dir", default=str(HERE.parent.parent / "qa" / "corpus" / "build" / "style-atlas-fonts"))
    ap.add_argument("--per-feeling", type=int, default=14)
    ap.add_argument("--top", type=int, default=30, help="faces per extra genre, as the corpus's --top")
    ap.add_argument("--no-feelings", action="store_true")
    ap.add_argument("--tags", default=None, help="local families.csv for an offline run")
    ap.add_argument("--jobs", type=int, default=8)
    a = ap.parse_args()

    corpus = json.load(open(a.corpus))
    sha = pinned_commit(corpus["source"])
    nec.RAW = nec.RAW_TEMPLATE.format(ref=sha)
    out_dir = Path(a.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    if a.tags:
        tags_text = Path(a.tags).read_text(encoding="utf-8")
    else:
        tags_text = nec.fetch(f"{nec.RAW}tags/all/families.csv").decode("utf-8")
    scores = default_location_scores(tags_text)

    jobs = {}
    classes = {}
    in_corpus = set(corpus["styles"])
    for key, style in corpus["styles"].items():
        classes[key] = []
        for fam in style["families"]:
            classes[key].append(fam["family"])
            jobs.setdefault(fam["family"], {"family": fam["family"], "file": fam["file"], "drawing": fam.get("drawing")})

    fam_tag = nec.parse_tags(tags_text)
    for key, tags in EXTRA_CLASSES.items():
        classes[key] = []
        for c in corpus_rule(fam_tag, tags, a.top):
            classes[key].append(c["family"])
            jobs.setdefault(c["family"], {"family": c["family"], "drawing": c["drawing"]})

    # The Brief's cue faces (data/brief-cues, built by build_brief_cues.py), measured as the app
    # renders them, file for file. The Brief picks which face illustrates which answer from these
    # measurements, so it has to know what each one actually looks like.
    bundled = {}
    cue_manifest = HERE.parent / "brief-cues" / "manifest.json"
    if cue_manifest.exists():
        for key, cue in json.load(open(cue_manifest))["cues"].items():
            src = HERE.parent / "brief-cues" / cue["file"]
            dest = out_dir / "bundled" / cue["file"]
            if src.exists():
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(src.read_bytes())
                bundled[key] = {"family": cue["family"], "path": str(dest.relative_to(out_dir))}

    feelings = {}
    if not a.no_feelings:
        expressive = sorted({t for tags in scores.values() for t in tags if t.startswith("/Expressive/")})
        for tag in expressive:
            ranked = sorted(
                ((s[tag], s.get("/Quality/Drawing", 0.0), fam) for fam, s in scores.items() if s.get(tag, 0.0) >= FEELING_MIN_SCORE),
                key=lambda r: (-r[0], -r[1], r[2]),
            )
            seen, chosen = set(), []
            for score, drawing, fam in ranked:
                root = fam.split()[0].lower()
                if root in seen:
                    continue
                seen.add(root)
                chosen.append({"family": fam, "score": score})
                if len(chosen) >= a.per_feeling + 4:  # spares for faces that fail or lack Latin
                    break
            feelings[tag] = chosen
            for c in chosen:
                jobs.setdefault(c["family"], {"family": c["family"], "drawing": scores.get(c["family"], {}).get("/Quality/Drawing")})

    print(f"{len(jobs)} families to fetch from google/fonts@{sha[:12]}", file=sys.stderr)
    fetched = {}
    with cf.ThreadPoolExecutor(a.jobs) as ex:
        for job, result in zip(jobs.values(), ex.map(lambda j: download(j, out_dir), jobs.values())):
            if result:
                result["drawing"] = job.get("drawing")
                fetched[job["family"]] = result

    manifest = {
        "source": f"google/fonts@{sha} (raw.githubusercontent.com/google/fonts/{sha}/), fetched {date.today().isoformat()} "
        "by data/scripts/fetch_style_atlas_fonts.py; feelings from tags/all/families.csv at the same commit "
        "(not redistributed: docs/OPEN_QUESTIONS.md item 121)",
        "commit": sha,
        "corpusClasses": sorted(in_corpus),
        "classTags": {**{k: list(v["tags"]) for k, v in corpus["styles"].items()}, **{k: list(v) for k, v in EXTRA_CLASSES.items()}},
        "classes": {
            k: [f for f in v if f in fetched][: (len(v) if k in in_corpus else a.top)] for k, v in classes.items()
        },
        "feelings": {
            tag: [c for c in chosen if c["family"] in fetched][: a.per_feeling] for tag, chosen in feelings.items()
        },
        "families": fetched,
        "bundled": bundled,
    }
    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=1, sort_keys=True))

    # The same manifest as plain TSV, for the Kotlin generator (which has no JSON dependency):
    # kind, key, family, path, score, drawing. Feelings are keyed by the tag's last segment.
    def cell(v):
        return "" if v is None else str(v).replace("\t", " ")

    lines = ["#source\t" + manifest["source"]]
    lines += [f"corpus\t{k}\t\t\t\t" for k in manifest["corpusClasses"]]
    for key, fams in manifest["classes"].items():
        for fam in fams:
            f = fetched[fam]
            lines.append("\t".join(["class", key, fam, f["path"], "", cell(f.get("drawing"))]))
    for tag, chosen in manifest["feelings"].items():
        key = tag.rsplit("/", 1)[-1].lower()
        for c in chosen:
            f = fetched[c["family"]]
            lines.append("\t".join(["feeling", key, c["family"], f["path"], cell(c["score"]), cell(f.get("drawing"))]))
    for key, b in bundled.items():
        lines.append("\t".join(["bundled", key, b["family"], b["path"], "", ""]))
    (out_dir / "manifest.tsv").write_text("\n".join(lines) + "\n")
    missing = [f for f in jobs if f not in fetched]
    print(f"fetched {len(fetched)} families; {len(missing)} skipped: {', '.join(missing[:20])}", file=sys.stderr)
    print("wrote", out_dir / "manifest.json", file=sys.stderr)


if __name__ == "__main__":
    main()
