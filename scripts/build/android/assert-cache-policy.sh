#!/usr/bin/env bash
#       Copyright 2026 İlyas Akın
#       Additional terms under GNU GPL section 7 apply: see LICENSE.md.
#
#       This program is free software: you can redistribute it and/or modify
#       it under the terms of the GNU General Public License as published by
#       the Free Software Foundation, either version 3 of the License, or
#       (at your option) any later version.
#
#       This program is distributed in the hope that it will be useful,
#       but WITHOUT ANY WARRANTY; without even the implied warranty of
#       MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#       GNU General Public License for more details.
#
#       You should have received a copy of the GNU General Public License
#       along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
# Enforce the Android CI cache policy: a cache may hold only build infrastructure that is reusable
# independently of the application's changing source, and must never hold an application output.
#
#   scripts/build/android/assert-cache-policy.sh <workflow.yml>...
#
# Allowed cache paths (the complete list; extend it here when a new piece of *infrastructure* is
# cached, never in the workflow alone):
#
#   ${{ env.ANDROID_SDK_ROOT }}   the pinned SDK and NDK
#   ${{ runner.temp }}/zhr-vendor immutable third-party source downloads (vendor.sh)
#   ${{ env.CCACHE_DIR }}         ccache's compiler objects and metadata
#
# Forbidden: libmain.so, any other application-built shared library, the CMake/Ninja build tree,
# object files outside ccache, generated APKs/AABs, and anything else the current source produces.
# A restored application binary would ship stale code, so the engine must be linked afresh every run.
#
# The guard reads the workflow files themselves rather than trusting a convention, so a future cache
# step pointed at an output - or at a new, unrecognised directory - fails the run here.

set -euo pipefail

usage() { echo "usage: $0 <workflow.yml>..." >&2; exit 2; }
[ $# -ge 1 ] || usage
fail() { echo "::error::$1" >&2; exit 1; }

allowed_paths=(
  '${{ env.ANDROID_SDK_ROOT }}'
  '${{ runner.temp }}/zhr-vendor'
  '${{ env.CCACHE_DIR }}'
)

is_allowed() {
  local p="$1" a
  for a in "${allowed_paths[@]}"; do [ "$p" = "$a" ] && return 0; done
  return 1
}

trim() {
  local s="$1"
  s="${s#"${s%%[![:space:]]*}"}"
  s="${s%"${s##*[![:space:]]}"}"
  printf '%s' "$s"
}

seen=0
for wf in "$@"; do
  [ -f "$wf" ] || fail "no such workflow file: $wf"

  # Every `path:` value inside a step that uses an actions/cache action. A step starts at a line
  # matching `- ` (the same indentation the step list uses), so the state resets there; the block
  # scalar forms `path: |` and `path: >` are captured as `|`/`>` and rejected below, so a
  # multi-directory cache cannot hide behind a folded value.
  while IFS= read -r raw; do
    p="$(trim "$raw")"
    [ -n "$p" ] || continue
    case "$p" in
      '|'|'>') fail "a multi-path cache block ($p) in $wf is not allowed: list each infrastructure path explicitly" ;;
    esac
    is_allowed "$p" \
      || fail "cache path '$p' in $wf is not an allowed infrastructure path; the engine libmain.so, the build tree and the APK must never be cached"
    seen=$((seen + 1))
  done < <(awk '
    /^[[:space:]]*- / { incache=0 }
    /uses:[[:space:]]*actions\/cache/ { incache=1 }
    incache && /^[[:space:]]*path:/ { sub(/^[[:space:]]*path:[[:space:]]*/, ""); print }
  ' "$wf")

  echo "cache policy OK: $wf"
done

[ "$seen" -gt 0 ] || fail "no cache paths were found at all; the policy check is not inspecting the workflows"

echo "cache policy OK: $seen cache path(s), every one build infrastructure"
