#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Validates the UFO 3 sample projects `UfoLibValidationSamplesTest` writes (docs/PROJECT_MODEL.md
§11) against fontTools' own reference `ufoLib` -- proof that what `core-font`/`:project` write
really opens in real Python UFO tooling, not just this repo's own Kotlin round-trip tests.

For every `.ufo` under the given directory:
  - `UFOReader(path, validate=True)`: `readMetaInfo`, `readInfo`, `readGroups`, `readKerning`,
    `readLib`, `readFeatures`;
  - `getGlyphSet(validateRead=True)`, then every glyph read once through a recording point pen
    and once through `PointToSegmentPen(RecordingPen())` (validate=True on both -- a `<point>`'s
    `type` is only checked when a real point pen consumes it, not when a caller passes none);
  - each glyph's unicodes, advance width and per-contour point-"type" sequence compared against
    the `expectations.json` the Kotlin writer left beside its `.ufo`.

It also, for every sample directory that has one:
  - loads `typewright.json`, `scrapbook/manifest.json` and `lessons/*.json` and checks their
    `format`/`format_version`, and that every path they name (a master's `.ufo`, a lock's
    approved snapshot and diffs, a workbook's lessons file, a scrapbook pin's image) exists;
  - parses every `locks/**/*.glif` with `glifLib.readGlyphFromString(validate=True)`, through a
    real point pen so point types are actually checked;
  - for every still-open unlock episode, applies its diff to the approved snapshot (GNU `patch`,
    the same tool `UnifiedDiffGnuCrossCheckTest` cross-checks this repo's own differ against) and
    checks the result is byte-identical to the glyph's current `.glif` on disk.

Exits 1 and prints every problem found, never stopping at the first one. Exits 0 and prints a
one-line summary when every sample is clean.

Run from the repository root, after `:project:jvmTest` has written the samples (its
`UfoLibValidationSamplesTest`; see project/build.gradle.kts for the two system properties that
place them):
    python3 tools/validate_ufo.py project/build/ufo-validation
"""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import tempfile
import types
from pathlib import Path

from fontTools.pens.pointPen import PointToSegmentPen
from fontTools.pens.recordingPen import RecordingPen, RecordingPointPen
from fontTools.ufoLib import UFOReader

FORMAT_KEY = "format"
FORMAT_VERSION_KEY = "format_version"


# ---- Glyph-level: a .ufo's glyphs against its expectations.json -----------------------------


def contours_from_point_pen_recording(recording: list) -> list[list[str | None]]:
    """[recording] (a `RecordingPointPen.value`) split into one list of `addPoint` types per `beginPath`/`endPath` span."""
    contours: list[list[str | None]] = []
    current: list[str | None] | None = None
    for operator, args, _kwargs in recording:
        if operator == "beginPath":
            current = []
        elif operator == "addPoint":
            assert current is not None, "addPoint outside beginPath/endPath"
            current.append(args[1])
        elif operator == "endPath":
            assert current is not None, "endPath without a matching beginPath"
            contours.append(current)
            current = None
    return contours


def validate_glyph(
    ufo_path: Path,
    glyph_set,
    name: str,
    expected: dict,
    problems: list[str],
) -> None:
    where = f"{ufo_path}/{name}"

    point_glyph = types.SimpleNamespace()
    point_pen = RecordingPointPen()
    try:
        glyph_set.readGlyph(name, point_glyph, point_pen, validate=True)
    except Exception as error:  # noqa: BLE001 -- report every problem, never stop at the first
        problems.append(f"{where}: point pen read failed: {error}")
        return

    # The spec also asks every glyph to be readable through a segment pen (a second real entry
    # point into the same file, exercising PointToSegmentPen's own point-to-segment conversion).
    segment_glyph = types.SimpleNamespace()
    segment_recording = RecordingPen()
    try:
        glyph_set.readGlyph(name, segment_glyph, PointToSegmentPen(segment_recording), validate=True)
    except Exception as error:  # noqa: BLE001
        problems.append(f"{where}: segment pen read failed: {error}")

    actual_unicodes = list(getattr(point_glyph, "unicodes", None) or [])
    expected_unicodes = expected.get("unicodes", [])
    if actual_unicodes != expected_unicodes:
        problems.append(f"{where}: unicodes {actual_unicodes!r} != expected {expected_unicodes!r}")

    actual_advance = getattr(point_glyph, "width", None)
    expected_advance = expected.get("advance")
    if actual_advance != expected_advance:
        problems.append(f"{where}: advance {actual_advance!r} != expected {expected_advance!r}")

    actual_contours = contours_from_point_pen_recording(point_pen.value)
    expected_contours = expected.get("contours", [])
    if actual_contours != expected_contours:
        problems.append(f"{where}: point-type sequence {actual_contours!r} != expected {expected_contours!r}")


def validate_ufo(ufo_path: Path, problems: list[str]) -> None:
    try:
        reader = UFOReader(str(ufo_path), validate=True)
    except Exception as error:  # noqa: BLE001
        problems.append(f"{ufo_path}: UFOReader failed: {error}")
        return

    for reader_call in ("readMetaInfo", "readInfo", "readGroups", "readKerning", "readLib", "readFeatures"):
        try:
            if reader_call == "readInfo":
                reader.readInfo(types.SimpleNamespace(), validate=True)
            elif reader_call == "readFeatures":
                reader.readFeatures()
            else:
                getattr(reader, reader_call)(validate=True)
        except Exception as error:  # noqa: BLE001
            problems.append(f"{ufo_path}: {reader_call} failed: {error}")

    expectations_path = ufo_path.parent / "expectations.json"
    expectations = load_json(expectations_path, problems)
    if expectations is None:
        return
    expected_glyphs = expectations.get("glyphs", {})

    try:
        glyph_set = reader.getGlyphSet(validateRead=True)
    except Exception as error:  # noqa: BLE001
        problems.append(f"{ufo_path}: getGlyphSet failed: {error}")
        return

    actual_names = set(glyph_set.keys())
    expected_names = set(expected_glyphs.keys())
    if actual_names != expected_names:
        problems.append(
            f"{ufo_path}: glyph set {sorted(actual_names)} != expectations.json's {sorted(expected_names)}"
        )

    for name in sorted(actual_names & expected_names):
        validate_glyph(ufo_path, glyph_set, name, expected_glyphs[name], problems)


# ---- Project-level: typewright.json, scrapbook, lessons, locks ------------------------------


def load_json(path: Path, problems: list[str]) -> dict | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        problems.append(f"{path}: does not exist")
    except (OSError, json.JSONDecodeError) as error:
        problems.append(f"{path}: {error}")
    return None


def check_format(obj: dict, path: Path, expected_format: str, problems: list[str]) -> None:
    if obj.get(FORMAT_KEY) != expected_format:
        problems.append(f"{path}: \"{FORMAT_KEY}\" is {obj.get(FORMAT_KEY)!r}, expected {expected_format!r}")
    check_format_version(obj, path, problems)


def check_format_version(obj: dict, path: Path, problems: list[str]) -> None:
    version = obj.get(FORMAT_VERSION_KEY)
    if not isinstance(version, int) or version < 1:
        problems.append(f"{path}: \"{FORMAT_VERSION_KEY}\" is {version!r}, expected a positive integer")


def require_path(sample_dir: Path, relative: str, owner: str, problems: list[str]) -> Path | None:
    target = sample_dir / relative
    if not target.exists():
        problems.append(f"{owner} names \"{relative}\", which does not exist under {sample_dir}")
        return None
    return target


PATCH_AVAILABLE = shutil.which("patch") is not None


def apply_unified_diff(diff_text: str, old_text: str) -> str:
    """[old_text] with [diff_text] (a `diff -u` hunk set, as `UnifiedDiff.kt` writes) applied, via GNU `patch -o` -- never touching a real file, and cross-checked against this repo's own differ by `UnifiedDiffGnuCrossCheckTest`."""
    with tempfile.TemporaryDirectory() as raw_dir:
        tmp = Path(raw_dir)
        old_file = tmp / "old"
        diff_file = tmp / "change.diff"
        out_file = tmp / "new"
        old_file.write_text(old_text, encoding="utf-8")
        diff_file.write_text(diff_text, encoding="utf-8")
        result = subprocess.run(
            ["patch", "--quiet", "-u", "-o", str(out_file), str(old_file), str(diff_file)],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode != 0:
            raise RuntimeError(f"patch exited {result.returncode}: {(result.stdout + result.stderr).strip()}")
        return out_file.read_text(encoding="utf-8")


def glyph_file_path(ufo_dir: Path, glyph_name: str, problems: list[str]) -> Path | None:
    """[glyph_name]'s current `.glif` file, resolved from `glyphs/contents.plist` (an Apple plist, not JSON; a small regex is enough for its flat `<key>name</key><string>file</string>` pairs)."""
    plist_text = (ufo_dir / "glyphs" / "contents.plist").read_text(encoding="utf-8")
    match = re.search(
        rf"<key>{re.escape(glyph_name)}</key>\s*<string>([^<]+)</string>",
        plist_text,
    )
    if match is None:
        problems.append(f"{ufo_dir}/glyphs/contents.plist has no entry for glyph \"{glyph_name}\"")
        return None
    return ufo_dir / "glyphs" / match.group(1)


def validate_open_diff(
    sample_dir: Path,
    manifest: dict,
    master_id: str,
    glyph_name: str,
    approved_path: Path,
    diff_path: Path,
    problems: list[str],
) -> None:
    if not PATCH_AVAILABLE:
        problems.append("GNU 'patch' is not on PATH; cannot check that an open diff reproduces the current glif")
        return
    master = next((m for m in manifest.get("masters", []) if m.get("id") == master_id), None)
    if master is None:
        problems.append(f"{sample_dir}/typewright.json: lock names master \"{master_id}\", which is not in masters[]")
        return
    ufo_dir = sample_dir / master["path"]
    current_path = glyph_file_path(ufo_dir, glyph_name, problems)
    if current_path is None or not current_path.is_file():
        return
    approved_text = approved_path.read_text(encoding="utf-8")
    diff_text = diff_path.read_text(encoding="utf-8")
    current_text = current_path.read_text(encoding="utf-8")
    try:
        patched = apply_unified_diff(diff_text, approved_text)
    except (RuntimeError, OSError) as error:
        problems.append(f"{diff_path}: applying to {approved_path} failed: {error}")
        return
    if patched != current_text:
        problems.append(
            f"{diff_path}: applying it to {approved_path} does not reproduce {current_path} "
            f"(the still-open episode's own claim -- docs/PROJECT_MODEL.md §7.3)"
        )


def validate_locks_glifs(sample_dir: Path, problems: list[str]) -> None:
    locks_dir = sample_dir / "locks"
    if not locks_dir.is_dir():
        return
    from fontTools.ufoLib.glifLib import readGlyphFromString

    for glif_path in sorted(locks_dir.glob("**/*.glif")):
        glyph = types.SimpleNamespace()
        pen = RecordingPointPen()
        try:
            readGlyphFromString(glif_path.read_text(encoding="utf-8"), glyph, pen, validate=True)
        except Exception as error:  # noqa: BLE001
            problems.append(f"{glif_path}: readGlyphFromString failed: {error}")


def validate_project(sample_dir: Path, problems: list[str]) -> None:
    manifest_path = sample_dir / "typewright.json"
    manifest = load_json(manifest_path, problems)
    if manifest is None:
        return
    check_format(manifest, manifest_path, "typewright-project", problems)

    for master in manifest.get("masters", []):
        require_path(sample_dir, master.get("path", ""), f"{manifest_path}'s masters[]", problems)

    for master_id, glyphs in manifest.get("locks", {}).items():
        for glyph_name, lock in glyphs.items():
            owner = f"{manifest_path}'s locks.{master_id}.{glyph_name}"
            approved_path = require_path(sample_dir, lock.get("approved", ""), f"{owner}.approved", problems)
            for episode in lock.get("unlocks", []):
                diff_path = require_path(sample_dir, episode.get("diff", ""), f"{owner}.unlocks[].diff", problems)
                if approved_path is None or diff_path is None:
                    continue
                if episode.get("relocked_at") is None:
                    validate_open_diff(sample_dir, manifest, master_id, glyph_name, approved_path, diff_path, problems)

    for key, record in manifest.get("workbook", {}).items():
        lessons_relative = record.get("lessons")
        if not lessons_relative:
            continue
        lessons_path = require_path(sample_dir, lessons_relative, f"{manifest_path}'s workbook.{key}", problems)
        if lessons_path is None:
            continue
        lessons = load_json(lessons_path, problems)
        if lessons is not None:
            check_format(lessons, lessons_path, "typewright-lessons", problems)

    scrapbook_path = sample_dir / "scrapbook" / "manifest.json"
    if scrapbook_path.is_file():
        scrapbook = load_json(scrapbook_path, problems)
        if scrapbook is not None:
            check_format_version(scrapbook, scrapbook_path, problems)
            for pin in scrapbook.get("pins", []):
                image_relative = pin.get("image_path")
                if image_relative:
                    require_path(sample_dir, image_relative, f"{scrapbook_path}'s pins[]", problems)

    validate_locks_glifs(sample_dir, problems)


# ---- Driver -----------------------------------------------------------------------------------


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {argv[0]} <ufo-validation-dir>", file=sys.stderr)
        return 2
    root = Path(argv[1])
    problems: list[str] = []

    # A Gradle test task's outputs aren't declared (this directory is a plain system-property
    # side effect, not a registered @OutputDirectory), so a cache hit on :project:jvmTest leaves
    # it absent rather than restoring it: report that plainly instead of silently validating
    # nothing.
    sample_dirs = sorted(p for p in root.iterdir() if p.is_dir()) if root.is_dir() else []
    if not sample_dirs:
        print(
            f"{root} has no sample directories -- UfoLibValidationSamplesTest may not have run this build "
            "(or was skipped as up to date/from the Gradle build cache; its samples are a system-property "
            "side effect, not a declared task output, so a cache hit leaves this directory absent). Run "
            "'./gradlew :project:jvmTest --tests \"*UfoLibValidationSamplesTest*\"' first.",
            file=sys.stderr,
        )
        return 1

    ufo_count = 0
    for ufo_path in sorted(root.glob("**/*.ufo")):
        ufo_count += 1
        validate_ufo(ufo_path, problems)

    project_count = 0
    for sample_dir in sample_dirs:
        if (sample_dir / "typewright.json").is_file():
            project_count += 1
            validate_project(sample_dir, problems)

    if problems:
        for problem in problems:
            print(problem, file=sys.stderr)
        print(f"\n{len(problems)} ufoLib validation problem(s).", file=sys.stderr)
        return 1

    print(
        f"ufoLib validation OK: {len(sample_dirs)} sample(s), {ufo_count} .ufo(s), "
        f"{project_count} full project(s) (typewright.json/scrapbook/lessons/locks)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
