# SPDX-License-Identifier: Apache-2.0
"""Static checks for the Ubuntu Touch click sources under packaging/ubuntu-touch.

Machine-checkable only: JSON shape, cross-file references, the version pin against
app-desktop/build.gradle.kts, and the honesty rules (the Preview click is labelled a preview and
the native click never is; the Preview click gets no `unconfined` template and no `networking`
group). It cannot say a click installs or runs; that is click-review in CI and a device.

    python3 tools/check_ubuntu_touch.py [--root DIR]
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

NAME_RE = re.compile(r"^[a-z0-9][a-z0-9.-]*\.[a-z0-9][a-z0-9-]*$")
FRAMEWORKS = {"ubuntu-touch-24.04-1.x", "ubuntu-touch-24.04-2.x"}


def read_desktop(path: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("["):
            key, value = line.split("=", 1)
            entries[key.strip()] = value.strip()
    return entries


def gradle_package_version(root: Path) -> str | None:
    text = (root / "app-desktop/build.gradle.kts").read_text(encoding="utf-8")
    match = re.search(r'packageVersion\s*=\s*"([^"]+)"', text)
    return match.group(1) if match else None


def simple_yaml(path: Path) -> dict[str, object]:
    """Top-level scalars and one-level string lists of clickable.yaml; no PyYAML needed."""
    data: dict[str, object] = {}
    key = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        if raw.startswith("  - ") and key:
            data.setdefault(key, []).append(raw[4:].strip())  # type: ignore[union-attr]
        elif ":" in raw and not raw.startswith(" "):
            key, _, value = raw.partition(":")
            key = key.strip()
            value = value.strip()
            if value:
                data[key] = value
                key = None
            else:
                data[key] = []
    return data


def check_click(directory: Path, preview: bool, version: str | None) -> list[str]:
    problems: list[str] = []
    label = str(directory)
    manifest_path = directory / "manifest.json"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        return [f"{manifest_path}: unreadable ({exc})"]

    for key in ("name", "title", "description", "version", "framework", "architecture", "maintainer", "hooks"):
        if key not in manifest:
            problems.append(f"{label}/manifest.json: missing '{key}'")
    if problems:
        return problems
    if not NAME_RE.match(manifest["name"]):
        problems.append(f"{label}/manifest.json: name '{manifest['name']}' is not appname.developer")
    if manifest["framework"] != "@CLICK_FRAMEWORK@":
        problems.append(f"{label}/manifest.json: framework must stay @CLICK_FRAMEWORK@ (Clickable fills it)")
    if manifest["architecture"] != "@CLICK_ARCH@":
        problems.append(f"{label}/manifest.json: architecture must stay @CLICK_ARCH@ (Clickable fills it)")
    if version is not None and manifest["version"] != version:
        problems.append(f"{label}/manifest.json: version {manifest['version']} != packageVersion {version}")
    title_text = (manifest["title"] + " " + manifest["description"]).lower()
    if preview and "preview" not in manifest["title"].lower():
        problems.append(f"{label}/manifest.json: the companion click's title must say Preview")
    if preview and "preview" not in manifest["description"].lower():
        problems.append(f"{label}/manifest.json: the companion's description must say it is a preview")
    if not preview and "preview" in title_text:
        problems.append(f"{label}/manifest.json: the native click must not call itself a preview")

    hooks = manifest["hooks"]
    if len(hooks) != 1:
        problems.append(f"{label}/manifest.json: expected exactly one hook")
    for hook_name, hook in hooks.items():
        for kind in ("apparmor", "desktop"):
            target = directory / hook.get(kind, "")
            if not hook.get(kind) or not target.is_file():
                problems.append(f"{label}/manifest.json: hook {hook_name}.{kind} -> missing file")
        apparmor_path = directory / hook.get("apparmor", "")
        if apparmor_path.is_file():
            try:
                policy = json.loads(apparmor_path.read_text(encoding="utf-8"))
            except ValueError as exc:
                problems.append(f"{apparmor_path}: invalid JSON ({exc})")
                policy = {}
            if policy.get("policy_version") != "@APPARMOR_POLICY@":
                problems.append(f"{apparmor_path}: policy_version must stay @APPARMOR_POLICY@")
            groups = policy.get("policy_groups", [])
            if "networking" in groups:
                problems.append(f"{apparmor_path}: no 'networking' group (law 3: only user-asked network calls)")
            if preview and policy.get("template") == "unconfined":
                problems.append(f"{apparmor_path}: the Preview must not be unconfined")
            if not preview and policy.get("template") != "unconfined":
                problems.append(f"{apparmor_path}: the native JVM click needs the unconfined template")
        desktop_path = directory / hook.get("desktop", "")
        if desktop_path.is_file():
            entries = read_desktop(desktop_path)
            if entries.get("Type") != "Application":
                problems.append(f"{desktop_path}: Type must be Application")
            exec_line = entries.get("Exec", "")
            script = exec_line.split()[-1] if exec_line.startswith("sh ") else ""
            if not script or not (directory / script).is_file():
                problems.append(f"{desktop_path}: Exec '{exec_line}' must be 'sh <script in the click>'")
            if "Waydroid" in desktop_path.read_text(encoding="utf-8"):
                problems.append(f"{desktop_path}: Waydroid is not a port (owner ruling)")

    clickable = directory / "clickable.yaml"
    if not clickable.is_file():
        problems.append(f"{label}/clickable.yaml: missing")
    else:
        cfg = simple_yaml(clickable)
        if cfg.get("framework") not in FRAMEWORKS:
            problems.append(f"{clickable}: framework {cfg.get('framework')} not in {sorted(FRAMEWORKS)}")
        expected = "pure" if preview else "precompiled"
        if cfg.get("builder") != expected:
            problems.append(f"{clickable}: builder must be {expected}")
        if not preview and cfg.get("restrict_arch") != "arm64":
            problems.append(f"{clickable}: the JVM click is arm64 only (restrict_arch: arm64)")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", default=".", help="repository root (default: cwd)")
    root = Path(parser.parse_args().root)
    base = root / "packaging/ubuntu-touch"
    version = gradle_package_version(root)
    problems: list[str] = []
    if version is None:
        problems.append("app-desktop/build.gradle.kts: packageVersion not found")
    problems += check_click(base, preview=False, version=version)
    problems += check_click(base / "preview", preview=True, version=version)
    native = json.loads((base / "manifest.json").read_text(encoding="utf-8"))["name"]
    preview = json.loads((base / "preview/manifest.json").read_text(encoding="utf-8"))["name"]
    if native == preview:
        problems.append("native and preview clicks share a name")
    for problem in problems:
        print(f"check_ubuntu_touch: {problem}", file=sys.stderr)
    if problems:
        return 1
    print(f"check_ubuntu_touch: ok ({native}, {preview}, version {version})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
