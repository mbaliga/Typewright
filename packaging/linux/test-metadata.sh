#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Static checks of the Linux packaging metadata: the .desktop entry, the AppStream metainfo and the
# Flatpak manifest. Each tool is used only if installed; a missing tool is reported, never faked.
# (desktop-file-utils, appstream and flatpak-builder are apt packages.)
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fp="$here/flatpak"
fail() { echo "test-metadata: FAIL: $1" >&2; exit 1; }
ran=0

# The three files share the app id; a rename that misses one is the failure this guards.
id="$(sed -n 's/^app-id: *//p' "$fp/com.asoc.Typewright.yml")"
[ -n "$id" ] || fail "manifest has no app-id"
for f in "$id.desktop" "$id.metainfo.xml" "$id.yml"; do [ -f "$fp/$f" ] || fail "missing $f for app id $id"; done
grep -q "<id>$id</id>" "$fp/$id.metainfo.xml" || fail "metainfo id differs from $id"
grep -q "^Icon=$id\$" "$fp/$id.desktop" || fail ".desktop Icon differs from $id"
grep -q "<launchable type=\"desktop-id\">$id.desktop</launchable>" "$fp/$id.metainfo.xml" || fail "launchable differs"
# Law 4 and the no-network rule: the sandbox must not be granted the network.
if grep -q -- '--share=network' "$fp/$id.yml"; then fail "manifest grants the network"; fi

if command -v desktop-file-validate >/dev/null; then desktop-file-validate "$fp/$id.desktop"; ran=$((ran+1)); else echo "test-metadata: desktop-file-validate not installed, skipped"; fi
if command -v appstreamcli >/dev/null; then appstreamcli validate --no-net "$fp/$id.metainfo.xml"; ran=$((ran+1)); else echo "test-metadata: appstreamcli not installed, skipped"; fi
if command -v flatpak-builder >/dev/null; then flatpak-builder --show-manifest "$fp/$id.yml" > /dev/null; ran=$((ran+1)); else echo "test-metadata: flatpak-builder not installed, skipped"; fi
echo "test-metadata: ok (id $id, $ran of 3 tool checks ran)"
