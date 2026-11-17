#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Checks preview-stage.sh and preview-launch.sh against a fake web distribution.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
fail() { echo "test-preview: FAIL: $1" >&2; exit 1; }
mkdir -p "$scratch/www"
echo '<!doctype html>' > "$scratch/www/index.html"
"$here/preview-stage.sh" --www "$scratch/www" --out "$scratch/stage" > /dev/null
for f in manifest.json typewright-preview.apparmor.json typewright-preview.desktop preview-launch.sh clickable.yaml assets/icon.svg www/index.html; do
  [ -e "$scratch/stage/$f" ] || fail "stage missing $f"
done
out="$(TYPEWRIGHT_DRY_RUN=1 sh "$scratch/stage/preview-launch.sh")"
[ "$out" = "webapp-container --webapp file://$scratch/stage/www/index.html" ] || fail "unexpected launch line: $out"
if "$here/preview-stage.sh" --www "$scratch/none" --out "$scratch/s2" > /dev/null 2>&1; then fail "accepted a missing dist"; fi
echo "test-preview: ok"
