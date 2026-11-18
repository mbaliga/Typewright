#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Asserts a jpackage app-image carries Skiko's arm64 native. Usage: check-skiko-arm64.sh APP_IMAGE_DIR
# The native ships in the skiko-awt-runtime-linux-arm64 jar (entry libskiko-linux-arm64.so); depending on how the
# image was laid out it can sit in any jar directory, so every jar is searched, by name and by entry, and a loose
# .so also counts. On failure the Skiko-related files are listed so the layout is visible in the log.
set -u
app="${1:?usage: check-skiko-arm64.sh APP_IMAGE_DIR}"
hits=0
while IFS= read -r j; do
  case "$(basename "$j")" in
    *skiko*linux*arm64*) echo "jar by name:  $j"; hits=$((hits + 1)); continue ;;
  esac
  if unzip -l "$j" 2>/dev/null | grep -q 'libskiko-linux-arm64'; then echo "jar by entry: $j"; hits=$((hits + 1)); fi
done < <(find "$app" -name '*.jar')
while IFS= read -r so; do echo "loose native: $so"; hits=$((hits + 1)); done < <(find "$app" -name 'libskiko-linux-arm64*')
echo "arm64 Skiko carriers found: $hits"
if [ "$hits" -eq 0 ]; then
  echo "no arm64 Skiko native in $app; Skiko-related files:" >&2
  find "$app" -iname '*skiko*' >&2
  exit 1
fi
