#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Checks Typewright's licence split (docs/LICENSING.md).

1. Every source file names its licence in an SPDX line: the first line, or the second after a
   shebang. App directories carry FSL-1.1-ALv2; engine, build and tooling directories carry
   Apache-2.0.
2. No Apache-2.0 module depends on an FSL-1.1-ALv2 module, in any source set, tests included.

Every Gradle module and every source file must fall under a directory listed below, so a new
module can't silently inherit a licence.

Run from the repository root:
    python3 tools/check_licences.py          # check; exit 1 on any problem
    python3 tools/check_licences.py --fix    # add missing SPDX lines, then check
"""
import re
import subprocess
import sys
from pathlib import Path

FSL = "FSL-1.1-ALv2"
APACHE = "Apache-2.0"

# Directory -> licence. The longest matching directory wins.
LICENCE_BY_DIR = {
    "ui": FSL,
    "learn": FSL,  # includes learn/scenes
    "campaign": FSL,
    "app-android": FSL,
    "app-desktop": FSL,
    "app-web": FSL,
    "core-geometry": APACHE,
    "core-font": APACHE,
    "engine-trace": APACHE,
    "engine-construct": APACHE,
    "qa": APACHE,  # includes qa/corpus
    "scripts": APACHE,  # the templates themselves are CC0 data, not source files
    "shape-preview": APACHE,
    "compile": APACHE,
    "build-logic": APACHE,
    "tools": APACHE,
    "data/scripts": APACHE,
}
ROOT_BUILD_FILES = {"settings.gradle.kts": APACHE, "build.gradle.kts": APACHE}

COMMENT_BY_SUFFIX = {".kt": "//", ".kts": "//", ".mjs": "//", ".js": "//", ".py": "#", ".sh": "#"}


def spdx_line(comment: str, licence: str) -> str:
    return f"{comment} SPDX-License-Identifier: {licence}"


def licence_for(path: str) -> str | None:
    if path in ROOT_BUILD_FILES:
        return ROOT_BUILD_FILES[path]
    best = None
    for directory, licence in LICENCE_BY_DIR.items():
        if path.startswith(directory + "/") and (best is None or len(directory) > len(best[0])):
            best = (directory, licence)
    return best[1] if best else None


def repo_files() -> list[str]:
    out = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        check=True, capture_output=True, text=True,
    ).stdout
    return sorted({line for line in out.splitlines() if line and Path(line).is_file()})


def check_headers(files: list[str], fix: bool) -> list[str]:
    problems = []
    for path in files:
        comment = COMMENT_BY_SUFFIX.get(Path(path).suffix)
        if comment is None:
            continue
        licence = licence_for(path)
        if licence is None:
            problems.append(f"{path}: not under any directory in LICENCE_BY_DIR; classify it")
            continue
        expected = spdx_line(comment, licence)
        text = Path(path).read_text(encoding="utf-8")
        lines = text.split("\n")
        header_index = 1 if lines and lines[0].startswith("#!") else 0
        actual = lines[header_index] if len(lines) > header_index else ""
        if actual == expected:
            continue
        if "SPDX-License-Identifier" in actual:
            problems.append(f"{path}: has '{actual.strip()}', expected '{expected}'")
            continue
        if fix:
            lines[header_index:header_index] = [expected, ""]
            Path(path).write_text("\n".join(lines), encoding="utf-8")
        else:
            problems.append(f"{path}: missing '{expected}' (run with --fix)")
    return problems


def gradle_modules() -> list[str]:
    """Module directories from settings.gradle.kts, e.g. ':qa:corpus' -> 'qa/corpus'."""
    settings = Path("settings.gradle.kts").read_text(encoding="utf-8")
    return sorted({m.replace(":", "/").strip("/") for m in re.findall(r'"(:[\w:-]+)"', settings)})


def check_dependency_direction() -> list[str]:
    problems = []
    for module in gradle_modules():
        licence = licence_for(module + "/build.gradle.kts")
        if licence is None:
            problems.append(f"module :{module.replace('/', ':')} is not classified in LICENCE_BY_DIR")
            continue
        if licence != APACHE:
            continue
        build_file = Path(module) / "build.gradle.kts"
        if not build_file.is_file():
            continue
        for dep in re.findall(r'project\("(:[\w:-]+)"\)', build_file.read_text(encoding="utf-8")):
            dep_dir = dep.replace(":", "/").strip("/")
            if licence_for(dep_dir + "/build.gradle.kts") == FSL:
                problems.append(
                    f":{module.replace('/', ':')} is Apache-2.0 but depends on FSL module {dep} "
                    f"(docs/LICENSING.md: an Apache module may never depend on an FSL module)"
                )
    return problems


def main() -> int:
    fix = "--fix" in sys.argv[1:]
    problems = check_headers(repo_files(), fix) + check_dependency_direction()
    for problem in problems:
        print(problem, file=sys.stderr)
    if problems:
        print(f"\n{len(problems)} licence problem(s).", file=sys.stderr)
        return 1
    print("Licence split OK: every source file has its SPDX line, and no Apache module depends on FSL.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
