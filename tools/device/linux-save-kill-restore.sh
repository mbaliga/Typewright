#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Linux analogue of tools/device/p11-save-kill-restore.sh, for docs/release/DEVICE_CHECKLIST_LINUX.md.
# Owner-run only, at a real desktop session: this container has no display and no Linux desktop
# (CLAUDE.md law 4), so this script is untested here -- read it before the first run.
#
# It starts the app, waits while you make and save an edit in a project folder, sends SIGKILL (a
# real process death, not a window close), records what is on disk, starts the app again and
# records it again. The edit and the judgement stay yours: the script only snapshots files and
# prints a diff. It never decides that anything worked.
#
# Usage: tools/device/linux-save-kill-restore.sh PROJECT_DIR [APP_COMMAND...]
#   PROJECT_DIR   the project folder you will open and edit in the app (a UFO 3 + JSON directory)
#   APP_COMMAND   how to start the app (default: ~/.local/bin/typewright, the install.sh launcher)

set -euo pipefail

if [[ $# -lt 1 ]]; then
  sed -n '13,15p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
fi
project_dir="$1"
shift
if [[ $# -gt 0 ]]; then
  app=("$@")
else
  app=("${HOME}/.local/bin/typewright")
fi

if [[ ! -d "$project_dir" ]]; then
  echo "linux-save-kill-restore: $project_dir is not a directory" >&2
  exit 1
fi

scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

snapshot() {
  # path, size, mtime and sha256 of every file; the lock file is listed too, deliberately.
  (cd "$project_dir" && find . -type f -print0 | sort -z | xargs -0 -r sha256sum \
    && echo "--- sizes and mtimes ---" \
    && find . -type f -printf '%p %s %TY-%Tm-%Td %TH:%TM:%TS\n' | sort)
}

echo "==> Snapshot before anything runs"
snapshot > "$scratch/before.txt"
wc -l < "$scratch/before.txt"

echo "==> Starting: ${app[*]}"
"${app[@]}" &
pid=$!
echo "pid $pid"
echo
echo "In the app: open $project_dir, make one edit, and wait for it to save"
echo "(autosave is debounced). Then press Enter here. Do NOT close the window."
read -r _

if ! kill -0 "$pid" 2>/dev/null; then
  echo "The app is not running any more (pid $pid); nothing to kill. Stopping." >&2
  exit 1
fi
echo "==> Snapshot while the app is still running"
snapshot > "$scratch/running.txt"
if [[ -e "$project_dir/build/.session-lock" ]]; then
  echo "build/.session-lock is present while open"
else
  echo "build/.session-lock is NOT present while open (record this in the checklist)"
fi

echo "==> kill -9 $pid"
kill -9 "$pid"
wait "$pid" 2>/dev/null || true

echo "==> Snapshot after SIGKILL"
snapshot > "$scratch/killed.txt"
if [[ -e "$project_dir/build/.session-lock" ]]; then
  echo "build/.session-lock is still present (stale lock; the reopen below shows how the app treats it)"
else
  echo "build/.session-lock is gone"
fi
echo "--- changes between 'before' and 'after SIGKILL' ---"
diff "$scratch/before.txt" "$scratch/killed.txt" || true

echo "==> Starting again: ${app[*]}"
"${app[@]}" &
pid2=$!
echo "pid $pid2"
echo "In the app: open $project_dir again and look at your edit. Press Enter when done."
read -r _
echo "==> Snapshot after reopen"
snapshot > "$scratch/reopened.txt"
echo "--- changes between 'after SIGKILL' and 'after reopen' ---"
diff "$scratch/killed.txt" "$scratch/reopened.txt" || true

kill "$pid2" 2>/dev/null || true
echo
echo "Done. The script only recorded files. Whether the edit survived, and whether the app"
echo "behaved sensibly about the lock, is for you to judge and write in the checklist."
