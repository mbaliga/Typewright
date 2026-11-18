#!/usr/bin/env bash
# SPDX-License-Identifier: FSL-1.1-ALv2
#
# Copies the data packs the app reads at run time into one folder, under the same relative paths
# the JVM classpath and the Wasm build use ("typewright/corpus/...", "scenes/...", ...). The Xcode
# project runs this into the app bundle; CI points TYPEWRIGHT_RESOURCES at the same folder for the
# simulator tests. The include lists mirror syncCorpusData (qa/corpus) and syncLearnFaceData
# (learn/scenes); keep them in step, test-stage-resources.sh checks the result.
#
# Usage: stage-resources.sh DEST_DIR
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 DEST_DIR" >&2
  exit 2
fi
dest="$1"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

rm -rf "$dest"
mkdir -p "$dest/typewright/corpus" "$dest/typewright/learn-faces"

# qa/corpus: generated node-economy packs.
for f in "$root"/data/node-economy-*.json; do
  [ -e "$f" ] && cp "$f" "$dest/typewright/corpus/"
done

# learn/scenes: manifest, each face's font files and its licence file.
cp "$root/data/learn-faces/manifest.json" "$dest/typewright/learn-faces/"
for dir in "$root"/data/learn-faces/*/; do
  [ -d "$dir" ] || continue
  slug="$(basename "$dir")"
  mkdir -p "$dest/typewright/learn-faces/$slug"
  for f in "$dir"*.ttf "$dir"OFL.txt "$dir"LICENSE.txt; do
    [ -e "$f" ] && cp "$f" "$dest/typewright/learn-faces/$slug/"
  done
done

# Hand-written resources, already laid out as scenes/... and campaign/...
cp -R "$root/learn/scenes/src/commonMain/resources/." "$dest/"
cp -R "$root/campaign/src/commonMain/resources/." "$dest/"

echo "staged $(find "$dest" -type f | wc -l | tr -d ' ') files into $dest"
