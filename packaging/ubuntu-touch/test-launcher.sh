#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Exercises typewright-launch.sh and stage.sh against a fake app-image. Proves the scripts' own
# logic (scale mapping, display check, log, staging layout); says nothing about Lomiri or the JVM.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
fail() { echo "test-launcher: FAIL: $1" >&2; exit 1; }

fake="$scratch/typewright"
mkdir -p "$fake/bin" "$fake/lib/runtime/lib"
printf '#!/bin/sh\necho "ran $*"\n' > "$fake/bin/typewright"
chmod +x "$fake/bin/typewright"
export HOME="$scratch/home" XDG_CACHE_HOME="$scratch/cache"
mkdir -p "$HOME"

run() { env -i PATH="$PATH" HOME="$HOME" XDG_CACHE_HOME="$XDG_CACHE_HOME" TYPEWRIGHT_APP_ROOT="$fake" "$@" sh "$here/typewright-launch.sh"; }

out="$(run DISPLAY=:0 GRID_UNIT_PX=21 TYPEWRIGHT_DRY_RUN=1)"
echo "$out" | grep -q "uiScale=2.62" || fail "GRID_UNIT_PX=21 should give scale 2.62: $out"
out="$(run DISPLAY=:0 GRID_UNIT_PX=4 TYPEWRIGHT_DRY_RUN=1)"
echo "$out" | grep -q "uiScale=1.00" || fail "scale must clamp to 1.00: $out"
out="$(run DISPLAY=:0 GRID_UNIT_PX=80 TYPEWRIGHT_DRY_RUN=1)"
echo "$out" | grep -q "uiScale=4.00" || fail "scale must clamp to 4.00: $out"
out="$(run DISPLAY=:0 GRID_UNIT_PX=21 TYPEWRIGHT_UI_SCALE=1.5 TYPEWRIGHT_DRY_RUN=1)"
echo "$out" | grep -q "uiScale=1.5\$" || fail "explicit TYPEWRIGHT_UI_SCALE must win: $out"
out="$(run DISPLAY=:0 TYPEWRIGHT_DRY_RUN=1)"
echo "$out" | grep -q "uiScale" && fail "no scale flag expected without GRID_UNIT_PX: $out"
out="$(run DISPLAY=:0 TYPEWRIGHT_RENDER=software TYPEWRIGHT_DRY_RUN=1)"
echo "$out" | grep -q "SKIKO_RENDER_API=SOFTWARE_FAST" || fail "software safe mode not applied: $out"
out="$(run WAYLAND_DISPLAY=w0 DISPLAY= TYPEWRIGHT_DRY_RUN=1)"
echo "$out" | grep -q "^exec " || fail "WAYLAND_DISPLAY alone should be accepted: $out"

# No display server: refuse, and say why in the log.
if run TYPEWRIGHT_DRY_RUN=1 > /dev/null 2>&1; then fail "launched without any display"; fi
grep -q "neither DISPLAY nor WAYLAND_DISPLAY" "$XDG_CACHE_HOME/typewright/launch.log" || fail "log missing the no-display reason"

# Missing binary: refuse.
rm "$fake/bin/typewright"
if run DISPLAY=:0 TYPEWRIGHT_DRY_RUN=1 > /dev/null 2>&1; then fail "launched without a binary"; fi
printf '#!/bin/sh\necho "ran $*"\n' > "$fake/bin/typewright"; chmod +x "$fake/bin/typewright"

# A real exec passes arguments through.
out="$(env -i PATH="$PATH" HOME="$HOME" XDG_CACHE_HOME="$XDG_CACHE_HOME" TYPEWRIGHT_APP_ROOT="$fake" DISPLAY=:0 sh "$here/typewright-launch.sh" a b)"
[ "$out" = "ran a b" ] || fail "arguments not passed to the app: $out"

# Staging layout.
"$here/stage.sh" --app-image "$fake" --out "$scratch/stage" > /dev/null
for f in manifest.json typewright.apparmor.json typewright.desktop typewright-launch.sh clickable.yaml assets/icon.svg typewright/bin/typewright; do
  [ -e "$scratch/stage/$f" ] || fail "stage missing $f"
done
if "$here/stage.sh" --app-image "$scratch/nope" --out "$scratch/s2" > /dev/null 2>&1; then fail "stage accepted a missing app-image"; fi

echo "test-launcher: ok"
