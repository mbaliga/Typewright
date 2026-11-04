#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Installs Typewright's Linux app-image directory (the output of
# `./gradlew :app-desktop:createDistributable`) under a prefix and writes a .desktop entry.
# Local copies only: no network, no root unless the prefix needs it.
#
#   install.sh [--prefix DIR] [APP_IMAGE_DIR]
#   install.sh --uninstall [--prefix DIR]
#
# APP_IMAGE_DIR is the directory holding bin/typewright and lib/ (default:
# app-desktop/build/compose/binaries/main/app/typewright). Default prefix: ~/.local.
set -euo pipefail

prefix="${HOME}/.local"
app_image="app-desktop/build/compose/binaries/main/app/typewright"
uninstall=0

while [ $# -gt 0 ]; do
  case "$1" in
    --prefix) [ $# -ge 2 ] || { echo "install.sh: --prefix needs a directory" >&2; exit 2; }
              prefix="$2"; shift 2 ;;
    --uninstall) uninstall=1; shift ;;
    -h|--help) sed -n '4,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "install.sh: unknown option $1" >&2; exit 2 ;;
    *) app_image="$1"; shift ;;
  esac
done

dest="${prefix}/lib/typewright"
link="${prefix}/bin/typewright"
entry="${prefix}/share/applications/typewright.desktop"

if [ "$uninstall" -eq 1 ]; then
  rm -rf "$dest"
  rm -f "$link" "$entry"
  echo "Removed Typewright from ${prefix}"
  exit 0
fi

if [ ! -x "${app_image}/bin/typewright" ]; then
  echo "install.sh: ${app_image}/bin/typewright not found or not executable." >&2
  echo "Pass the app-image directory, or build it with ./gradlew :app-desktop:createDistributable" >&2
  exit 1
fi

mkdir -p "${prefix}/lib" "${prefix}/bin" "${prefix}/share/applications"
rm -rf "$dest"
cp -a "$app_image" "$dest"
ln -sfn "${dest}/bin/typewright" "$link"

# No application icon exists in the repository yet, so the entry names none.
cat > "$entry" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Typewright
Comment=Hand-made letterforms to fonts of Google Fonts quality
Exec=${dest}/bin/typewright
Terminal=false
Categories=Graphics;Development;
DESKTOP

echo "Installed Typewright to ${dest}"
echo "Launcher: ${link} (ensure ${prefix}/bin is on PATH)"
