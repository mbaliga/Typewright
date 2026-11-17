#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
#
# Opens the bundled Kotlin/Wasm distribution in Lomiri's webapp-container from a file:// URL.
# This is the "Typewright Preview" companion, NOT the Ubuntu Touch port (that is the native click
# in packaging/ubuntu-touch). Whether webapp-container loads local content, runs WasmGC, or keeps
# storage across launches is unverified (docs/release/DEVICE_CHECKLIST_UT.md, preview section).
here="$(cd "$(dirname "$0")" && pwd)"
url="file://$here/www/index.html"
if [ "${TYPEWRIGHT_DRY_RUN:-}" = "1" ]; then
  echo "webapp-container --webapp $url"
  exit 0
fi
exec webapp-container --webapp "$url"
