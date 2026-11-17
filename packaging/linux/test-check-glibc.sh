#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Runs check-glibc.sh against this host's own libc-linked binaries: proves the script's logic only.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
scratch="$(mktemp -d)"; trap 'rm -rf "$scratch"' EXIT
mkdir -p "$scratch/img/bin"; cp /bin/ls "$scratch/img/bin/typewright"
"$here/check-glibc.sh" --max 99.0 "$scratch/img" > /dev/null || { echo "test-check-glibc: FAIL: rejected under a huge limit" >&2; exit 1; }
if "$here/check-glibc.sh" --max 2.0 "$scratch/img" > /dev/null 2>&1; then echo "test-check-glibc: FAIL: accepted under glibc 2.0" >&2; exit 1; fi
if "$here/check-glibc.sh" "$scratch/nope" > /dev/null 2>&1; then echo "test-check-glibc: FAIL: accepted a missing dir" >&2; exit 1; fi
echo "test-check-glibc: ok"
