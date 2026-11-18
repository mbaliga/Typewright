#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Assembles the directory Clickable packages into the native Typewright click:
#   stage.sh [--app-image DIR] [--out DIR]
# --app-image: output of `./gradlew :app-desktop:createDistributable` (bin/typewright, lib/).
# --out:       stage directory (default build/ubuntu-touch/stage). Recreated on every run.
# Pure file copying; no network. Does not build the click.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
app_image="app-desktop/build/compose/binaries/main/app/typewright"
out="build/ubuntu-touch/stage"
while [ $# -gt 0 ]; do
  case "$1" in
    --app-image) [ $# -ge 2 ] || { echo "stage.sh: --app-image needs a directory" >&2; exit 2; }
                 app_image="$2"; shift 2 ;;
    --out) [ $# -ge 2 ] || { echo "stage.sh: --out needs a directory" >&2; exit 2; }
           out="$2"; shift 2 ;;
    -h|--help) sed -n '4,8p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "stage.sh: unknown argument $1" >&2; exit 2 ;;
  esac
done

[ -x "$app_image/bin/typewright" ] || { echo "stage.sh: $app_image/bin/typewright is not an executable app-image" >&2; exit 1; }
[ -d "$app_image/lib" ] || { echo "stage.sh: $app_image/lib missing" >&2; exit 1; }

rm -rf "$out"
mkdir -p "$out/assets"
cp -a "$app_image" "$out/typewright"
cp "$here/manifest.json" "$here/typewright.apparmor.json" "$here/typewright.desktop" "$out/"
cp "$here/typewright-launch.sh" "$here/clickable.yaml" "$out/"
cp "$here/assets-icon.svg" "$out/assets/icon.svg"
chmod +x "$out/typewright-launch.sh"
echo "staged $(du -sh "$out" | cut -f1) into $out"
