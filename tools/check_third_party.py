#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Fail when a dependency has no entry in THIRD_PARTY.md.

Every library in gradle/libs.versions.toml's [libraries] table must be named in THIRD_PARTY.md,
by its artifact or its group (a family such as androidx.compose is credited once). The app's About
screen shows THIRD_PARTY.md, so a dependency that is missing here is missing there too
(SCREENS_V1 S23).
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
catalog = (ROOT / "gradle" / "libs.versions.toml").read_text()
notices = (ROOT / "THIRD_PARTY.md").read_text().lower().replace("-", "").replace("_", "").replace(" ", "")


def squash(text: str) -> str:
    return text.lower().replace("-", "").replace("_", "").replace(" ", "")


section = None
missing = []
for line in catalog.splitlines():
    stripped = line.strip()
    if stripped.startswith("["):
        section = stripped
        continue
    if section != "[libraries]" or "=" not in stripped or stripped.startswith("#"):
        continue
    alias = stripped.split("=", 1)[0].strip()
    module = re.search(r'module\s*=\s*"([^"]+)"', stripped)
    group = re.search(r'group\s*=\s*"([^"]+)"', stripped)
    name = re.search(r'name\s*=\s*"([^"]+)"', stripped)
    if module:
        g, a = module.group(1).split(":", 1)
    elif group and name:
        g, a = group.group(1), name.group(1)
    else:
        continue
    candidates = {squash(a), squash(g), squash(g.split(".")[-1]), squash(alias)}
    if not any(c and c in notices for c in candidates):
        missing.append(f"{alias} ({g}:{a})")

if missing:
    print("THIRD_PARTY.md has no entry for:")
    for m in missing:
        print("  -", m)
    sys.exit(1)
print("Third-party notices complete: every dependency in gradle/libs.versions.toml is named in THIRD_PARTY.md.")
