#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Fail when a library the apps actually resolve has no mention in THIRD_PARTY.md.

tools/check_third_party.py checks the dependency catalogue. This checks the other end: what Gradle resolves
onto the three apps' runtime classpaths (app-desktop, app-android's release variant, app-web's Wasm
executable), transitive libraries included, which is what ships and what About must credit.

    python3 tools/check_resolved_dependencies.py            # runs Gradle (offline first, then online)
    python3 tools/check_resolved_dependencies.py --from FILE   # parse saved `gradle dependencies` output

A resolved `group:artifact` is covered when THIRD_PARTY.md names its artifact, its group, or the last words of
its group (a family such as `androidx.compose` is credited once, as the file's rows do). It needs a Gradle that
can resolve the apps' dependencies; the Android one needs an Android SDK, and is skipped with a message when
neither ANDROID_HOME nor ANDROID_SDK_ROOT is set.
"""
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TASKS = [
    (":app-desktop:dependencies", "runtimeClasspath", False),
    (":app-android:dependencies", "releaseRuntimeClasspath", True),
    (":app-web:dependencies", "wasmJsRuntimeClasspath", False),
]
LINE = re.compile(r"^[|+\\ -]*[+\\]--- (\S+)")


def squash(text: str) -> str:
    return re.sub(r"[^a-z0-9]", "", text.lower())


def resolved(output: str) -> set[str]:
    found = set()
    for line in output.splitlines():
        match = LINE.match(line)
        if not match:
            continue
        coordinate = match.group(1)
        if coordinate.startswith("project") or coordinate.count(":") < 1:
            continue
        group, artifact = coordinate.split(":")[0:2]
        found.add(f"{group}:{artifact}")
    return found


def gradle(task: str, configuration: str) -> str:
    base = [str(ROOT / "gradlew"), "-q", task, "--configuration", configuration]
    for extra in (["--offline"], []):
        run = subprocess.run(base[:1] + extra + base[1:], cwd=ROOT, capture_output=True, text=True)
        if run.returncode == 0:
            return run.stdout
    sys.exit(f"Gradle could not resolve {task} {configuration}:\n{run.stderr[-2000:]}")


def main() -> None:
    notices = squash((ROOT / "THIRD_PARTY.md").read_text())
    libraries: set[str] = set()
    if "--from" in sys.argv:
        libraries |= resolved(Path(sys.argv[sys.argv.index("--from") + 1]).read_text())
    else:
        for task, configuration, needs_sdk in TASKS:
            if needs_sdk and not (os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")):
                print(f"skipping {task}: no Android SDK (set ANDROID_HOME)")
                continue
            libraries |= resolved(gradle(task, configuration))
    if not libraries:
        sys.exit("No resolved libraries found; the check would pass on nothing.")
    missing = []
    for coordinate in sorted(libraries):
        group, artifact = coordinate.split(":")
        names = {squash(artifact), squash(group), squash(group.split(".")[-1])}
        # `kotlin-stdlib-wasm-js` is credited as `kotlin-stdlib`; try the artifact without a platform suffix too.
        names.add(squash(re.sub(r"-(jvm|desktop|android|wasm-js|js|linux-x64|awt)$", "", artifact)))
        if not any(n and n in notices for n in names):
            missing.append(coordinate)
    if missing:
        print("THIRD_PARTY.md does not mention these resolved libraries:")
        for coordinate in missing:
            print("  -", coordinate)
        sys.exit(1)
    print(f"Every one of the {len(libraries)} resolved libraries is covered by THIRD_PARTY.md.")


main()
