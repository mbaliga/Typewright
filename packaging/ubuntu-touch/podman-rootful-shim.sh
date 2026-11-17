#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Podman shim for the CI Clickable lanes: run Podman as root, and drop the --uidmap/--gidmap
# pairs Clickable adds.
#
# Why root: rootless Podman cannot unpack the 24.04 CI image on the runner (uid 32011 has no
# host mapping).  Why drop the maps: Clickable writes "--uidmap <uid>:0:1 --uidmap 0:1:<uid>"
# for *rootless* Podman, where container uid <uid> maps to the invoking user (host 0 inside
# the user namespace).  Under real root the same flags make the container user host-root and
# the runner-owned build tree look foreign, so `click build` died with
# "PermissionError: ./<app>_arm64.click".  Without the maps, `--user <uid>` is the runner's
# own uid and the mounted tree is writable.
real=/usr/bin/podman
[ -x "$real" ] || real=$(command -v podman)
args=
skip=0
for a in "$@"; do
  if [ "$skip" = 1 ]; then skip=0; continue; fi
  case "$a" in
    --uidmap|--gidmap) skip=1; continue ;;
    --uidmap=*|--gidmap=*) continue ;;
  esac
  args="$args '$(printf %s "$a" | sed "s/'/'\\\\''/g")'"
done
eval "set -- $args"
exec sudo -E "$real" "$@"
