#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
#
# Lomiri launcher for the Typewright click. Runs the bundled Compose Desktop app-image under
# XMir/Xwayland. Plain POSIX sh: the click rootfs is not guaranteed to have bash.
#
# Everything here is a guess about Lomiri until an owner runs it on a device (see
# docs/release/DEVICE_CHECKLIST_UT.md). Knobs, all optional:
#   TYPEWRIGHT_UI_SCALE   force the AWT scale factor (e.g. 2.5); else derived from GRID_UNIT_PX
#   TYPEWRIGHT_RENDER     "software" sets SKIKO_RENDER_API=SOFTWARE_FAST (safe mode if the window
#                         is blank because OpenGL is unavailable under Xwayland)
#   TYPEWRIGHT_DRY_RUN=1  print the command instead of exec'ing it (used by the test)
# A launch log goes to $XDG_CACHE_HOME/typewright/launch.log; nothing leaves the device.

here="$(cd "$(dirname "$0")" && pwd)"
app_root="${TYPEWRIGHT_APP_ROOT:-$here/typewright}"
bin="$app_root/bin/typewright"

# Lomiri's grid unit is 8 px at scale 1.0 (Qt pixel ratio = GRID_UNIT_PX / 8). Clamp to a sane band.
ui_scale() {
  if [ -n "${TYPEWRIGHT_UI_SCALE:-}" ]; then
    echo "$TYPEWRIGHT_UI_SCALE"
  elif [ -n "${GRID_UNIT_PX:-}" ]; then
    awk -v g="$GRID_UNIT_PX" 'BEGIN { s = g / 8; if (s < 1) s = 1; if (s > 4) s = 4; printf "%.2f\n", s }'
  fi
}

cache="${XDG_CACHE_HOME:-${HOME:-/tmp}/.cache}/typewright"
mkdir -p "$cache" 2>/dev/null
log="$cache/launch.log"
note() { printf '%s %s\n' "$(date -u +%FT%TZ 2>/dev/null)" "$*" >> "$log" 2>/dev/null; }

if [ ! -x "$bin" ]; then
  note "FAIL no executable at $bin"
  echo "typewright-launch: no executable at $bin" >&2
  exit 1
fi
if [ -z "${DISPLAY:-}" ] && [ -z "${WAYLAND_DISPLAY:-}" ]; then
  note "FAIL neither DISPLAY nor WAYLAND_DISPLAY is set (no X server for AWT)"
  echo "typewright-launch: no display server in the environment" >&2
  exit 1
fi

# Config and project defaults stay inside the app's own writable dirs when Lomiri provides them.
[ -n "${XDG_CONFIG_HOME:-}" ] || XDG_CONFIG_HOME="${HOME:-/tmp}/.config"
export XDG_CONFIG_HOME
export _JAVA_AWT_WM_NONREPARENTING=1
export GDK_BACKEND=x11

opts="${JAVA_TOOL_OPTIONS:-}"
scale="$(ui_scale)"
if [ -n "$scale" ]; then
  opts="$opts -Dsun.java2d.uiScale=$scale"
fi
JAVA_TOOL_OPTIONS="$opts"
export JAVA_TOOL_OPTIONS
if [ "${TYPEWRIGHT_RENDER:-}" = "software" ]; then
  SKIKO_RENDER_API=SOFTWARE_FAST
  export SKIKO_RENDER_API
fi

# Preflight (non-fatal): a missing X11 library in the click rootfs is the likeliest silent failure.
awt="$(find "$app_root/lib/runtime/lib" -name libawt_xawt.so 2>/dev/null | head -n 1)"
if [ -n "$awt" ] && command -v ldd >/dev/null 2>&1; then
  missing="$(ldd "$awt" 2>/dev/null | grep 'not found' | tr -d '\t' | tr '\n' ';')"
  [ -z "$missing" ] || note "WARN libawt_xawt.so has unresolved libraries: $missing"
fi

note "launch scale=${scale:-default} render=${TYPEWRIGHT_RENDER:-default} display=${DISPLAY:-} wayland=${WAYLAND_DISPLAY:-}"
if [ "${TYPEWRIGHT_DRY_RUN:-}" = "1" ]; then
  echo "exec $bin"
  echo "JAVA_TOOL_OPTIONS=$JAVA_TOOL_OPTIONS"
  echo "SKIKO_RENDER_API=${SKIKO_RENDER_API:-}"
  exit 0
fi
exec "$bin" "$@"
