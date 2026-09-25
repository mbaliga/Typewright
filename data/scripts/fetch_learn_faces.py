#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Fetch the real OFL/Apache webfonts the Learn strand's scenes need, from the
google/fonts GitHub repository, at build time -- CLAUDE.md law 3: "The only
network calls are the ones the user asks for: fetching a Google Fonts family
for comparison..." This is that fetch, run once per family by a build-time
script (never at runtime inside the shipped app), producing checked-in data
under data/learn-faces/, the same convention data/node-economy-latin.json and
this file's sibling data/scripts/build_node_economy_corpus.py already use.

What this fetches and why (TYPEWRIGHT_BUILD_BRIEF.md 9, docs/LESSONS_SCAFFOLD.md
1-2): the ten Lineages on-stage faces (one per era, crossfaded live on the
scrubber) plus the seven "identify-it" exercise-bank faces the same section 2
names, which must NOT be faces the learner has already seen on stage. For each
family: the Regular-weight (normal style, weight closest to 400) static or
variable font file METADATA.pb lists -- a variable font is fine, nothing here
needs static instances -- and the family's OFL/Apache licence text, both from
raw.githubusercontent.com (no api.github.com call, no token: see this task's
own confirmed-working fetch mechanism).

Slugging: a family's directory under ofl/, apache/ or ufl/ is its name
lowercased with everything but [a-z0-9] stripped (dir_name(), identical to
build_node_economy_corpus.py's own). All seventeen families below resolved on
the first try when this script was written (2026-09-24); FALLBACK_SLUGS exists
as a documented escape hatch for the day one doesn't, rather than an
assumption this never needs revisiting.

Usage: python3 fetch_learn_faces.py [--out-dir ../learn-faces] [--commit SHA]
Run from anywhere; paths default relative to this file's own directory, i.e.
data/learn-faces/ and data/learn-faces/manifest.json in this repository.
"""
import argparse
import hashlib
import io
import json
import re
import subprocess
import sys
import urllib.request
import urllib.error
from datetime import date, datetime, timezone
from pathlib import Path

GOOGLE_FONTS_GIT = "https://github.com/google/fonts.git"
RAW_TEMPLATE = "https://raw.githubusercontent.com/google/fonts/{ref}/"
LICENSE_DIRS = ("ofl", "apache", "ufl")
# METADATA.pb's own `license:` field -> (human licence name, the licence text's own filename
# in that family's directory). Confirmed against apache/robotoslab (license: "APACHE2", file
# LICENSE.txt, no OFL.txt there -- see this script's commit message) and every ofl/* family
# fetched here (license: "OFL", file OFL.txt).
LICENSE_FIELD_MAP = {
    "OFL": ("OFL-1.1", "OFL.txt"),
    "APACHE2": ("Apache-2.0", "LICENSE.txt"),
    "UFL": ("UFL-1.0", "UFL.txt"),
}

# The ten Lineages on-stage faces (docs/LESSONS_SCAFFOLD.md section 2's era table), keyed the
# way data/exemplars.json already keys them (era-semantic names, reused rather than invented
# here so scene YAML, the style-detector validation test and this manifest all agree on what
# "garalde" etc. means) -- plus the seven exercise-bank faces the same section's own
# "Identify-it bank" paragraph names, none of which appear on stage. Exercise-bank keys are
# the family's own slug (no era name is given for them in the scaffold, so the family name is
# the only unambiguous handle a scene YAML author has).
FACES = [
    # key, family, role, era label (None for exercise-bank faces)
    ("blackletter", "UnifrakturMaguntia", "lineages-onstage", "Blackletter"),
    ("garalde", "EB Garamond", "lineages-onstage", "Garalde"),
    ("transitional", "Libre Baskerville", "lineages-onstage", "Transitional"),
    ("didone", "Playfair Display", "lineages-onstage", "Didone"),
    ("slab", "Zilla Slab", "lineages-onstage", "Slab"),
    ("grotesque", "Work Sans", "lineages-onstage", "Grotesque"),
    ("artdeco", "Limelight", "lineages-onstage", "Art Deco"),
    ("geometric", "Jost", "lineages-onstage", "Geometric"),
    ("humanist", "Source Sans 3", "lineages-onstage", "Humanist sans"),
    ("neogrotesque", "Inter", "lineages-onstage", "Neo-grotesque"),
    ("librebodoni", "Libre Bodoni", "exercise-bank", "Didone"),
    ("poppins", "Poppins", "exercise-bank", "Geometric"),
    ("librefranklin", "Libre Franklin", "exercise-bank", "Grotesque"),
    ("robotoslab", "Roboto Slab", "exercise-bank", "Slab"),
    ("cormorant", "Cormorant", "exercise-bank", "Garalde"),
    ("opensans", "Open Sans", "exercise-bank", "Humanist sans"),
    ("josefinsans", "Josefin Sans", "exercise-bank", "Art Deco"),
]

# Documented escape hatch, not exercised as of 2026-09-24 (every family above resolved via
# dir_name() on the first try -- see this script's fetch log in the P6 task report). Add an
# entry keyed by family name if a future google/fonts rename breaks the simple rule; the
# script tries dir_name(family) first and only falls back to entries here.
FALLBACK_SLUGS: dict[str, list[str]] = {}


def dir_name(family: str) -> str:
    return re.sub(r"[^a-z0-9]", "", family.lower())


def fetch(url: str, timeout: int = 60) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "typewright-learn-faces/0.1"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def fetch_status(url: str, timeout: int = 30):
    """Returns (ok, body_or_None, http_status_or_error_str)."""
    try:
        body = fetch(url, timeout=timeout)
        return True, body, 200
    except urllib.error.HTTPError as e:
        return False, None, e.code
    except Exception as e:  # noqa: BLE001 - reported to the caller, not swallowed
        return False, None, str(e)


def resolve_source_commit(repo: str = GOOGLE_FONTS_GIT, ref: str = "refs/heads/main"):
    """Same method as build_node_economy_corpus.py's resolve_source_commit: `git ls-remote`
    talks git's smart-HTTP protocol directly, reachable where api.github.com is not. Returns
    None (caller falls back to the unpinned "main" ref) on any failure; never hard-fails the
    fetch over network flakiness.
    """
    try:
        out = subprocess.run(
            ["git", "ls-remote", repo, ref],
            capture_output=True, text=True, timeout=30, check=True,
        )
        line = out.stdout.strip().splitlines()[0]
        sha = line.split()[0]
        if len(sha) == 40 and all(c in "0123456789abcdef" for c in sha):
            return sha
    except Exception as e:  # noqa: BLE001
        print("could not resolve google/fonts commit SHA:", e, file=sys.stderr)
    return None


def parse_metadata(text: str) -> dict:
    """Minimal METADATA.pb text-protobuf parser -- only the top-level scalar fields and the
    repeated `fonts { ... }` blocks this script needs (the same regex approach
    build_node_economy_corpus.py's regular_filename() already uses, extended to also pull the
    scalar fields it doesn't need).

    String values use `(?:[^"\\]|\\.)*` rather than `.*?`: several families' `copyright` field
    contains an escaped quote (e.g. Playfair Display's `with Reserved Font Name \\"Playfair
    Display\\".`), and a naive non-greedy `.*?` stops at that first `\"`, silently truncating
    the string. Caught by comparing this script's own manifest output against curl'd
    METADATA.pb text by hand (P6 task report) -- fixed here, not worked around at the call site.
    """
    PSTR = r'"((?:[^"\\]|\\.)*)"'

    def unescape(s):
        # protobuf text-format escapes seen in METADATA.pb in practice: \" (Playfair Display,
        # Josefin Sans's "Reserved Font Name" copyright lines) and \' (UnifrakturMaguntia's
        # "j. \'mach\' wust", over-escaped but valid); \\ last so it does not eat the others.
        if not s:
            return s
        return s.replace('\\"', '"').replace("\\'", "'").replace("\\\\", "\\")

    def top_field(name):
        m = re.search(rf"^{name}: {PSTR}$", text, re.M)
        return unescape(m.group(1)) if m else None

    fonts = []
    for blk in re.findall(r"fonts \{(.*?)\}", text, re.S):
        style = re.search(rf"style: {PSTR}", blk)
        weight = re.search(r"weight: (\d+)", blk)
        fn = re.search(rf"filename: {PSTR}", blk)
        copyright_ = re.search(rf"copyright: {PSTR}", blk)
        if not fn:
            continue
        fonts.append(
            {
                "style": style.group(1) if style else "normal",
                "weight": int(weight.group(1)) if weight else 400,
                "filename": fn.group(1),
                "copyright": unescape(copyright_.group(1)) if copyright_ else None,
            },
        )
    return {
        "name": top_field("name"),
        "designer": top_field("designer"),
        "license": top_field("license"),
        "category": top_field("category"),
        "fonts": fonts,
    }


def pick_regular(fonts: list[dict]):
    """Regular = normal style, weight closest to 400 (build_node_economy_corpus.py's
    regular_filename, same tie-break: (is-not-normal, |weight-400|, filename) ascending)."""
    best = None
    for f in fonts:
        cand = (0 if f["style"] == "normal" else 1, abs(f["weight"] - 400), f["filename"])
        if best is None or cand < (best[0], best[1], best[2]):
            best = (*cand[:2], f["filename"], f)
    return best[3] if best else None


SFNT_MAGICS = (b"\x00\x01\x00\x00", b"OTTO", b"true", b"ttcf")


def looks_like_font(data: bytes) -> tuple[bool, str]:
    """Real font, not an HTML error page or a truncated download: magic bytes + a sane size.
    A real webfont from google/fonts is tens of KB to low single-digit MB (variable fonts with
    many axes run largest); this repo has none anywhere near a "handful of bytes" or the
    kilobyte-scale an HTML error page would be, so the floor is set well above either failure
    mode and the ceiling is generous rather than exact.
    """
    if len(data) < 8:
        return False, f"too small ({len(data)} bytes)"
    if data[:4] not in SFNT_MAGICS:
        return False, f"bad magic {data[:4]!r} (not an sfnt: TrueType/OTTO/true/ttcf)"
    if len(data) < 3_000:
        return False, f"suspiciously small for a webfont ({len(data)} bytes)"
    if len(data) > 8_000_000:
        return False, f"suspiciously large for a webfont ({len(data)} bytes)"
    return True, "ok"


def resolve_family(raw_base: str, family: str):
    """Try dir_name(family) first, then FALLBACK_SLUGS entries, across ofl/apache/ufl. Returns
    (slug, license_dir, metadata_text) or raises with every attempt's status for an honest
    failure report.
    """
    attempts = []
    candidates = [dir_name(family)] + FALLBACK_SLUGS.get(family, [])
    for slug in candidates:
        for lic_dir in LICENSE_DIRS:
            url = f"{raw_base}{lic_dir}/{slug}/METADATA.pb"
            ok, body, status = fetch_status(url)
            attempts.append((slug, lic_dir, status))
            if ok:
                return slug, lic_dir, body.decode("utf-8", "replace")
    raise RuntimeError(
        f"no METADATA.pb found for {family!r}; tried "
        + ", ".join(f"{s}/{d} -> {st}" for s, d, st in attempts),
    )


def fetch_face(raw_base: str, key: str, family: str, role: str, era: str | None, out_dir: Path):
    slug, lic_dir, meta_text = resolve_family(raw_base, family)
    meta = parse_metadata(meta_text)
    regular = pick_regular(meta["fonts"])
    if regular is None:
        raise RuntimeError(f"{family!r} ({slug}): METADATA.pb lists no usable font file")

    filename = regular["filename"]
    font_url = f"{raw_base}{lic_dir}/{slug}/{filename}"
    font_bytes = fetch(font_url)

    ok, why = looks_like_font(font_bytes)
    if not ok:
        raise RuntimeError(f"{family!r} ({slug}) {filename}: failed validation -- {why}")

    licence_field = meta["license"] or "UNKNOWN"
    licence_name, licence_filename = LICENSE_FIELD_MAP.get(licence_field, (licence_field, "OFL.txt"))
    licence_url = f"{raw_base}{lic_dir}/{slug}/{licence_filename}"
    licence_ok, licence_body, licence_status = fetch_status(licence_url)
    if not licence_ok:
        # Never silently drop this -- record the miss in the manifest so the report is honest.
        licence_body = None

    face_dir = out_dir / slug
    face_dir.mkdir(parents=True, exist_ok=True)
    (face_dir / filename).write_bytes(font_bytes)
    if licence_body is not None:
        (face_dir / licence_filename).write_bytes(licence_body)

    sha256 = hashlib.sha256(font_bytes).hexdigest()

    return {
        "key": key,
        "family": family,
        "role": role,
        "lineages_era": era,
        "slug": slug,
        "license_dir": lic_dir,
        "file": filename,
        "path": f"data/learn-faces/{slug}/{filename}",
        "file_size_bytes": len(font_bytes),
        "file_sha256": sha256,
        "licence": licence_name,
        "licence_field_raw": licence_field,
        "licence_file": f"data/learn-faces/{slug}/{licence_filename}" if licence_body is not None else None,
        "licence_fetch_status": "ok" if licence_ok else str(licence_status),
        "copyright": regular["copyright"] or meta["fonts"][0]["copyright"] if meta["fonts"] else None,
        "designer": meta["designer"],
        "category": meta["category"],
        "source_metadata_url": f"{raw_base}{lic_dir}/{slug}/METADATA.pb",
        "source_font_url": font_url,
        "source_licence_url": licence_url if licence_ok else None,
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    here = Path(__file__).resolve().parent  # data/scripts/
    ap.add_argument("--out-dir", default=str(here.parent / "learn-faces"), help="default: data/learn-faces")
    ap.add_argument("--manifest", default=None, help="default: <out-dir>/manifest.json")
    ap.add_argument("--commit", default=None, help="pin to this google/fonts commit SHA instead of resolving main")
    args = ap.parse_args()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = Path(args.manifest) if args.manifest else out_dir / "manifest.json"

    sha = args.commit or resolve_source_commit()
    if sha:
        raw_base = RAW_TEMPLATE.format(ref=sha)
        pinned = True
    else:
        raw_base = RAW_TEMPLATE.format(ref="main")
        pinned = False
        print("WARNING: source commit not pinned; git ls-remote failed. Fetching from "
              "unpinned main -- this run is not reproducible.", file=sys.stderr)

    fetched_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    faces_out: dict[str, dict] = {}
    ok_keys, retried_keys, failed = [], [], []

    for key, family, role, era in FACES:
        try:
            entry = fetch_face(raw_base, key, family, role, era, out_dir)
            faces_out[key] = entry
            ok_keys.append(key)
            print(f"OK   {key:16s} {family:20s} -> {entry['slug']}/{entry['file']} "
                  f"({entry['file_size_bytes']:,} bytes, {entry['licence']})", file=sys.stderr)
        except Exception as e:  # noqa: BLE001 - every failure is named in the manifest and report
            failed.append((key, family, str(e)))
            print(f"FAIL {key:16s} {family:20s} -> {e}", file=sys.stderr)

    manifest = {
        "source": (
            f"google/fonts@{sha} (raw.githubusercontent.com/google/fonts/{sha}/"
            f"{{ofl,apache,ufl}}/<slug>/METADATA.pb + Regular font file + licence text), "
            f"fetched {fetched_at} by data/scripts/fetch_learn_faces.py"
            if pinned else
            f"google/fonts@main (commit SHA NOT resolved -- git ls-remote failed; UNPINNED, "
            f"not reproducible), fetched {fetched_at} by data/scripts/fetch_learn_faces.py"
        ),
        "commit": sha,
        "commit_pinned": pinned,
        "fetched_at": fetched_at,
        "faces": faces_out,
        "failed": [{"key": k, "family": f, "error": e} for k, f, e in failed],
    }
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=False) + "\n")

    print(f"\nwrote {manifest_path} -- {len(ok_keys)} fetched, {len(failed)} failed", file=sys.stderr)
    if failed:
        print("FAILURES (see manifest.failed for detail):", file=sys.stderr)
        for k, f, e in failed:
            print(f"  {k} ({f}): {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
