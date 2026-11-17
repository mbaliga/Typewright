#!/usr/bin/env bash
# SPDX-License-Identifier: FSL-1.1-ALv2
#
# Stages the data packs into a scratch folder and checks that every path the Kotlin readers ask
# for exists there. Runs on any Linux or macOS host with bash.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

"$here/stage-resources.sh" "$scratch/res" > /dev/null

fail=0
need() {
  if [ ! -e "$scratch/res/$1" ]; then
    echo "MISSING: $1" >&2
    fail=1
  fi
}

need typewright/learn-faces/manifest.json
need campaign/workbook-latin.yaml
need scenes/craft/a1-tracing-destroys-the-drawing.yaml
ls "$root"/data/node-economy-*.json > /dev/null
for f in "$root"/data/node-economy-*.json; do need "typewright/corpus/$(basename "$f")"; done

# Every font file the learn-face manifest names must be staged under its slug.
faces_list="$scratch/faces.txt"
python3 - "$root/data/learn-faces/manifest.json" > "$faces_list" <<'PY'
import json, sys
m = json.load(open(sys.argv[1], encoding="utf-8"))
for face in m["faces"].values():
    print(face["slug"] + "/" + face["file"])
PY
while IFS= read -r rel; do
  need "typewright/learn-faces/$rel"
done < "$faces_list"

# The scenes and workbook source trees must arrive complete.
src_count=$(find "$root/learn/scenes/src/commonMain/resources" -type f | wc -l | tr -d ' ')
got_count=$(find "$scratch/res/scenes" -type f | wc -l | tr -d ' ')
if [ "$src_count" != "$got_count" ]; then
  echo "scene files: $src_count in source, $got_count staged" >&2
  fail=1
fi

if [ "$fail" -ne 0 ]; then exit 1; fi
echo "stage-resources OK"
