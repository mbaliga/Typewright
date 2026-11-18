#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Assembles the Preview click directory from the :app-web browser distribution:
#   preview-stage.sh [--www DIR] [--out DIR]
# --www: output of `./gradlew :app-web:wasmJsBrowserDistribution`
#        (default app-web/build/dist/wasmJs/productionExecutable).
# --out: default build/ubuntu-touch/preview-stage. Pure file copying; no network.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
parent="$(cd "$here/.." && pwd)"
www="app-web/build/dist/wasmJs/productionExecutable"
out="build/ubuntu-touch/preview-stage"
while [ $# -gt 0 ]; do
  case "$1" in
    --www) [ $# -ge 2 ] || { echo "preview-stage.sh: --www needs a directory" >&2; exit 2; }
           www="$2"; shift 2 ;;
    --out) [ $# -ge 2 ] || { echo "preview-stage.sh: --out needs a directory" >&2; exit 2; }
           out="$2"; shift 2 ;;
    -h|--help) sed -n '4,8p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "preview-stage.sh: unknown argument $1" >&2; exit 2 ;;
  esac
done

[ -f "$www/index.html" ] || { echo "preview-stage.sh: $www/index.html missing (run :app-web:wasmJsBrowserDistribution)" >&2; exit 1; }

rm -rf "$out"
mkdir -p "$out/assets"
cp -a "$www" "$out/www"
cp "$here/manifest.json" "$here/typewright-preview.apparmor.json" "$here/typewright-preview.desktop" "$out/"
cp "$here/preview-launch.sh" "$here/clickable.yaml" "$out/"
cp "$parent/assets-icon.svg" "$out/assets/icon.svg"
chmod +x "$out/preview-launch.sh"
echo "staged $(du -sh "$out" | cut -f1) into $out"
