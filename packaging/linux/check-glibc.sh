#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Reports the highest GLIBC symbol version the app-image's native files need, and fails when it is
# above MAX (default 2.35, Ubuntu 22.04). The launcher, the bundled JRE and Skiko's libskiko are what
# decide which distributions can run the image.
#   check-glibc.sh [--max X.Y] APP_IMAGE_DIR
set -euo pipefail
max="2.35"
while [ $# -gt 0 ]; do
  case "$1" in
    --max) [ $# -ge 2 ] || { echo "check-glibc: --max needs a version" >&2; exit 2; }; max="$2"; shift 2 ;;
    -*) echo "check-glibc: unknown option $1" >&2; exit 2 ;;
    *) dir="$1"; shift ;;
  esac
done
[ -n "${dir:-}" ] && [ -d "$dir" ] || { echo "check-glibc: pass an app-image directory" >&2; exit 2; }
command -v objdump >/dev/null || { echo "check-glibc: objdump (binutils) not installed" >&2; exit 2; }

worst="0.0"; worst_file=""; count=0
while IFS= read -r f; do
  head -c4 "$f" 2>/dev/null | grep -q 'ELF' || continue
  count=$((count + 1))
  v="$(objdump -T "$f" 2>/dev/null | grep -o 'GLIBC_[0-9.]*' | sed 's/GLIBC_//' | sort -V | tail -1 || true)"
  [ -n "$v" ] || continue
  if [ "$(printf '%s\n%s\n' "$worst" "$v" | sort -V | tail -1)" = "$v" ] && [ "$v" != "$worst" ]; then worst="$v"; worst_file="$f"; fi
done < <(find "$dir" -type f \( -name '*.so' -o -name '*.so.*' -o -perm -u+x \))

echo "check-glibc: $count ELF files, highest GLIBC_$worst (${worst_file:-none})"
if [ "$(printf '%s\n%s\n' "$worst" "$max" | sort -V | tail -1)" != "$max" ]; then
  echo "check-glibc: FAIL: needs glibc $worst, above the limit $max" >&2
  exit 1
fi
