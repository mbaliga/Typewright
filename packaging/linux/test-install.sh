#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Runs install.sh and install.sh --uninstall against a fake app-image in a scratch prefix.
# Proves the script's file handling only; it says nothing about the real app-image or a desktop.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

fake="$scratch/typewright"
mkdir -p "$fake/bin" "$fake/lib"
printf '#!/bin/sh\necho fake\n' > "$fake/bin/typewright"
chmod +x "$fake/bin/typewright"
echo x > "$fake/lib/app.jar"
prefix="$scratch/prefix"

fail() { echo "test-install: FAIL: $1" >&2; exit 1; }

"$here/install.sh" --prefix "$prefix" "$fake" > /dev/null
[ -x "$prefix/lib/typewright/bin/typewright" ] || fail "binary not copied"
[ -f "$prefix/lib/typewright/lib/app.jar" ] || fail "lib not copied"
[ -L "$prefix/bin/typewright" ] || fail "launcher link missing"
grep -q "^Exec=$prefix/lib/typewright/bin/typewright\$" "$prefix/share/applications/typewright.desktop" \
  || fail ".desktop Exec line wrong"

# A second install replaces the first.
echo stale > "$prefix/lib/typewright/stale"
"$here/install.sh" --prefix "$prefix" "$fake" > /dev/null
[ ! -e "$prefix/lib/typewright/stale" ] || fail "reinstall kept stale files"

"$here/install.sh" --uninstall --prefix "$prefix" > /dev/null
[ ! -e "$prefix/lib/typewright" ] || fail "lib dir left behind"
[ ! -e "$prefix/bin/typewright" ] && [ ! -L "$prefix/bin/typewright" ] || fail "launcher left behind"
[ ! -e "$prefix/share/applications/typewright.desktop" ] || fail ".desktop left behind"

# A missing app-image is refused.
if "$here/install.sh" --prefix "$prefix" "$scratch/nope" > /dev/null 2>&1; then fail "accepted a missing app-image"; fi

echo "test-install: ok"
