#!/usr/bin/env python3
"""Build the node-economy reference corpus for Typewright.

For each style class in the Google Fonts taxonomy, take the top-N families by
Google's own /Quality/Drawing score (tie-break: tag score), download the Regular
face from the google/fonts repository, count on-curve and off-curve points per
glyph, and write per-style, per-glyph distributions (min, q1, median, q3, max)
plus the raw per-family counts so the app can list the 30 fonts behind a box.

Counts are format-aware: TrueType quadratic off-curve counts are reported as
measured and labelled "quadratic"; on-curve counts are comparable across formats
because cu2qu keeps the on-curve points of the cubic source.

Usage: python3 build_node_economy_corpus.py [--top 30] [--out node-economy-latin.json]
"""
import csv, io, json, re, sys, argparse, collections, concurrent.futures as cf
import urllib.request
from fontTools.ttLib import TTFont
from fontTools.pens.recordingPen import RecordingPen

RAW = "https://raw.githubusercontent.com/google/fonts/main/"
STYLES = {
    "sans-geometric":   "/Sans/Geometric",
    "sans-grotesque":   "/Sans/Grotesque",
    "sans-neogrotesque":"/Sans/Neo Grotesque",
    "sans-humanist":    "/Sans/Humanist",
    "serif-garalde":    "/Serif/Old Style Garalde",
    "serif-transitional":"/Serif/Transitional",
    "serif-didone":     "/Serif/Didone",
    "slab":             ("/Slab/Humanist", "/Slab/Geometric", "/Slab/Clarendon"),
    "display-artdeco":  "/Theme/Art Deco",
    "blackletter":      "/Theme/Blackletter",
}
GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

def fetch(url, timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": "typewright-corpus/0.1"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()

def load_tags(path):
    fam_tag = collections.defaultdict(dict)   # family -> {tag: score}
    with open(path, newline="") as f:
        for row in csv.reader(f):
            if len(row) < 4: continue
            fam, _, tag, score = row[0], row[1], row[2], row[3]
            try: fam_tag[fam][tag] = max(fam_tag[fam].get(tag, 0), int(score))
            except ValueError: pass
    return fam_tag

def dir_name(family):
    return re.sub(r"[^a-z0-9]", "", family.lower())

def regular_filename(metadata_pb):
    fonts = re.findall(r"fonts \{(.*?)\}", metadata_pb, re.S)
    best = None
    for blk in fonts:
        style = re.search(r'style: "(.*?)"', blk); weight = re.search(r"weight: (\d+)", blk)
        fn = re.search(r'filename: "(.*?)"', blk)
        if not fn: continue
        s = style.group(1) if style else "normal"; w = int(weight.group(1)) if weight else 400
        cand = (0 if s == "normal" else 1, abs(w - 400), fn.group(1))
        if best is None or cand < best: best = cand
    return best[2] if best else None

def count_points(font):
    gs = font.getGlyphSet(); cmap = font.getBestCmap(); out = {}
    fmt = "quadratic" if "glyf" in font else "cubic"
    for ch in GLYPHS:
        g = cmap.get(ord(ch))
        if not g: continue
        pen = RecordingPen(); gs[g].draw(pen); on = off = 0; contours = 0
        for op, args in pen.value:
            if op == "moveTo": on += 1; contours += 1
            elif op == "lineTo": on += 1
            elif op == "qCurveTo":
                if args[-1] is None:      # all-off-curve contour: every off-curve implies an on-curve
                    off += len(args) - 1; on += len(args) - 1
                else:
                    off += len(args) - 1; on += 1
            elif op == "curveTo": off += 2; on += 1
        out[ch] = [on, off, contours]
    return fmt, out

def process(family):
    d = dir_name(family)
    for lic in ("ofl", "apache", "ufl"):
        try:
            meta = fetch(f"{RAW}{lic}/{d}/METADATA.pb").decode("utf-8", "replace")
        except Exception:
            continue
        fn = regular_filename(meta)
        if not fn: return None
        try:
            data = fetch(f"{RAW}{lic}/{d}/{fn}")
        except Exception:
            return None
        try:
            font = TTFont(io.BytesIO(data))
            fmt, counts = count_points(font)
            upm = font["head"].unitsPerEm
            return {"family": family, "file": fn, "licence": lic, "format": fmt, "upm": upm, "counts": counts}
        except Exception as e:
            print("skip", family, e, file=sys.stderr); return None
    return None

def quartiles(vals):
    v = sorted(vals); n = len(v)
    if n == 0: return None
    def q(p):
        k = (n - 1) * p; f = int(k); c = min(f + 1, n - 1)
        return v[f] + (v[c] - v[f]) * (k - f)
    return {"min": v[0], "q1": q(.25), "med": q(.5), "q3": q(.75), "max": v[-1], "n": n}

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--top", type=int, default=30)
    ap.add_argument("--tags", default="families.csv"); ap.add_argument("--out", default="node-economy-latin.json")
    a = ap.parse_args()
    fam_tag = load_tags(a.tags)
    pack = {"source": "google/fonts tags/all/families.csv + repository TTFs", "glyphs": GLYPHS, "styles": {}}
    for key, tags in STYLES.items():
        tags = tags if isinstance(tags, tuple) else (tags,)
        cands = []
        for fam, t in fam_tag.items():
            sc = max((t.get(x, 0) for x in tags), default=0)
            if sc >= 50:
                cands.append((t.get("/Quality/Drawing", 0), sc, fam))
        cands.sort(reverse=True)
        seen = set(); dedup = []                       # one face per superfamily (first word)
        for c in cands:
            root = c[2].split()[0].lower()
            if root in seen: continue
            seen.add(root); dedup.append(c)
        cands = dedup
        chosen = [c[2] for c in cands[: a.top + 8]]   # a few spares for failed downloads
        with cf.ThreadPoolExecutor(12) as ex:
            results = [r for r in ex.map(process, chosen) if r]
        results = results[: a.top]
        dist = {}
        for ch in GLYPHS:
            on = [r["counts"][ch][0] for r in results if ch in r["counts"]]
            off = [r["counts"][ch][1] for r in results if ch in r["counts"]]
            if on: dist[ch] = {"on": quartiles(on), "off": quartiles(off)}
        pack["styles"][key] = {"tags": list(tags), "families": [{"family": r["family"], "file": r["file"], "format": r["format"], "drawing": next((c[0] for c in cands if c[2] == r["family"]), None), "counts": r["counts"]} for r in results], "dist": dist}
        print(f"{key}: {len(results)} fonts", file=sys.stderr)
    json.dump(pack, open(a.out, "w"), separators=(",", ":"))
    print("wrote", a.out, file=sys.stderr)

if __name__ == "__main__":
    main()
