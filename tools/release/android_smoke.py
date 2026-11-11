#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Release-build smoke test: look inside the release APK and fail on anything a release must not ship.

    python3 tools/release/android_smoke.py app-android/build/outputs/apk/release [--version-file version.properties]

Checks, on the APK it finds in the directory (or the file it is given), with aapt2 from the Android SDK
build-tools (ANDROID_HOME, or ANDROID_SDK_ROOT, or --aapt2):

  * the package, versionName and versionCode are the ones in version.properties;
  * minSdk is 31 and targetSdk is 36 (the levels docs/OPEN_QUESTIONS.md items 4 and 12 fix);
  * the manifest is not debuggable and does not allow backup;
  * the permissions are exactly the camera, and there is no network permission (the app makes no
    network calls of its own);
  * a launcher icon (and a round one) is declared and the files are in the APK;
  * R8 ran: the dex does not hold readable names for the app's own private classes, and the APK is
    inside the size bounds (a size that falls off a cliff means R8 or resource shrinking removed something);
  * whether it is signed is reported, not required: with no signing secrets the build is unsigned, and
    this script says so instead of failing.

It reads the APK; it never signs, installs or runs it. A release build that starts and works on a phone is
owner-verified only (CLAUDE.md law 4).
"""
import argparse
import glob
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

EXPECTED_PACKAGE = "com.asoc.typewright"
EXPECTED_MIN_SDK = "31"
EXPECTED_TARGET_SDK = "36"
ALLOWED_PERMISSIONS = {"android.permission.CAMERA"}
FORBIDDEN_PERMISSIONS = {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"}
# Bounds in bytes. The lower one catches an APK with the app stripped out of it; the upper one catches
# a debug-sized build or an accidental fixture. Both are loose on purpose: they are alarms, not targets.
MIN_APK_BYTES = 2 * 1024 * 1024
MAX_APK_BYTES = 60 * 1024 * 1024


def find_aapt2(explicit: str | None) -> str:
    if explicit:
        return explicit
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        home = os.environ.get(variable)
        if home:
            candidates = sorted(glob.glob(os.path.join(home, "build-tools", "*", "aapt2")))
            if candidates:
                return candidates[-1]
    sys.exit("android_smoke: aapt2 not found; set ANDROID_HOME or pass --aapt2")


def find_apk(target: str) -> Path:
    path = Path(target)
    if path.is_file():
        return path
    apks = sorted(path.glob("*.apk"))
    if not apks:
        sys.exit(f"android_smoke: no .apk in {target}; run ./gradlew :app-android:assembleRelease first")
    return apks[0]


def read_properties(path: str) -> dict:
    values = {}
    for line in Path(path).read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def aapt2(tool: str, *args: str) -> str:
    return subprocess.run([tool, *args], check=True, capture_output=True, text=True).stdout


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", help="the release APK, or the directory that holds it")
    parser.add_argument("--version-file", default=str(Path(__file__).resolve().parents[2] / "version.properties"))
    parser.add_argument("--aapt2")
    args = parser.parse_args()

    apk = find_apk(args.apk)
    tool = find_aapt2(args.aapt2)
    version = read_properties(args.version_file)
    problems: list[str] = []
    notes: list[str] = []

    badging = aapt2(tool, "dump", "badging", str(apk))
    manifest = aapt2(tool, "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk))

    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not package:
        sys.exit("android_smoke: aapt2 gave no package line")
    name, code, version_name = package.groups()
    if name != EXPECTED_PACKAGE:
        problems.append(f"package is {name}, expected {EXPECTED_PACKAGE}")
    if version_name != version.get("VERSION_NAME"):
        problems.append(f"versionName {version_name} differs from version.properties {version.get('VERSION_NAME')}")
    if code != version.get("VERSION_CODE"):
        problems.append(f"versionCode {code} differs from version.properties {version.get('VERSION_CODE')}")

    min_sdk = re.search(r"minSdkVersion:'(\d+)'", badging)
    target_sdk = re.search(r"targetSdkVersion:'(\d+)'", badging)
    if not min_sdk or min_sdk.group(1) != EXPECTED_MIN_SDK:
        problems.append(f"minSdk is {min_sdk.group(1) if min_sdk else 'missing'}, expected {EXPECTED_MIN_SDK}")
    if not target_sdk or target_sdk.group(1) != EXPECTED_TARGET_SDK:
        problems.append(f"targetSdk is {target_sdk.group(1) if target_sdk else 'missing'}, expected {EXPECTED_TARGET_SDK}")

    if re.search(r"A: android:debuggable\([^)]*\)=\(type 0x12\)0xffffffff", manifest):
        problems.append("the manifest says android:debuggable=true")
    if "debuggable" in badging.split("\n", 1)[0] or re.search(r"^application-debuggable", badging, re.M):
        problems.append("aapt2 reports the application as debuggable")
    if re.search(r"A: android:allowBackup\([^)]*\)=\(type 0x12\)0xffffffff", manifest):
        problems.append("the manifest allows backup; the project's files are the person's, not a cloud backup's")

    permissions = set(re.findall(r"uses-permission: name='([^']+)'", badging))
    for permission in sorted(permissions & FORBIDDEN_PERMISSIONS):
        problems.append(f"the manifest asks for {permission}; the app makes no network calls of its own (CLAUDE.md law 3)")
    for permission in sorted(permissions - ALLOWED_PERMISSIONS - FORBIDDEN_PERMISSIONS):
        # The AndroidX start-up and dynamic receiver permission is added by the library for every app.
        if not permission.endswith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"):
            problems.append(f"unexpected permission {permission}")

    icon = re.search(r"application: label='[^']*' icon='([^']*)'", badging)
    if not icon or not icon.group(1):
        problems.append("the application declares no launcher icon")
    launcher = re.search(r"launchable-activity: name='([^']+)'", badging)
    if not launcher:
        problems.append("no launchable activity")
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if icon and icon.group(1) and icon.group(1) not in names:
            problems.append(f"the launcher icon {icon.group(1)} is not in the APK")
        round_icon = re.search(r"roundIcon='([^']*)'", badging)
        if round_icon and round_icon.group(1) and round_icon.group(1) not in names:
            problems.append(f"the round icon {round_icon.group(1)} is not in the APK")
        dex = b"".join(archive.read(n) for n in names if re.fullmatch(r"classes\d*\.dex", n))
        if not dex:
            problems.append("the APK has no classes.dex")
        # R8 renames the app's own non-entry classes; if a private helper keeps its name, minification did not run.
        if b"Lcom/asoc/typewright/ui/settings/NoticeSection;" in dex:
            problems.append("com.asoc.typewright.ui.settings.NoticeSection is still in the dex under its own name: R8 did not run")
        signed = any(n.startswith("META-INF/") and n.endswith((".RSA", ".EC", ".DSA")) for n in names)
        v2 = None
        try:
            v2 = subprocess.run(
                [str(Path(tool).with_name("apksigner")), "verify", str(apk)], capture_output=True, text=True
            ).returncode == 0
        except OSError:
            pass
        if not (signed or v2):
            notes.append("UNSIGNED: no signing secrets were in the environment. This APK cannot be installed or uploaded; see docs/release/RELEASE_CHECKLIST.md.")

    size = apk.stat().st_size
    if size < MIN_APK_BYTES:
        problems.append(f"APK is {size} bytes, under {MIN_APK_BYTES}: the app may have been stripped out")
    if size > MAX_APK_BYTES:
        problems.append(f"APK is {size} bytes, over {MAX_APK_BYTES}")

    print(f"{apk.name}: {size / 1024 / 1024:.1f} MiB, {name} {version_name} (code {code}), minSdk {min_sdk and min_sdk.group(1)}, "
          f"targetSdk {target_sdk and target_sdk.group(1)}, permissions {sorted(permissions)}")
    for note in notes:
        print("note:", note)
    if problems:
        print("Release smoke test FAILED:")
        for problem in problems:
            print("  -", problem)
        sys.exit(1)
    print("Release smoke test passed. Whether this build starts and works on a phone is owner-verified only.")


main()
