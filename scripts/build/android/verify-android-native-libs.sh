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
# Verify the native payload an APK carries for arm64-v8a, from the ELF objects themselves. This is
# the static gate between "the link produced some files" and "the device can load them":
#
#   scripts/build/android/verify-android-native-libs.sh --libs android/app/src/main/jniLibs/arm64-v8a
#   scripts/build/android/verify-android-native-libs.sh --libs DIR --ndk "$ANDROID_NDK_HOME" \
#       --glue android/app/src/main/java-sdl --reference android/app/src/main/jniLibs/arm64-v8a
#
# --libs DIR       the directory to audit: a staged jniLibs/arm64-v8a or an extracted APK lib/arm64-v8a.
#                  It must hold exactly the three libraries below and nothing else (an unused library
#                  in the APK is a packaging defect, not spare capacity).
# --ndk DIR        the NDK whose llvm-readelf/llvm-nm to use (default: the same, or PATH tools).
# --glue DIR       SDL3's staged Java glue (java-sdl). Enables the JNI contract check: every native
#                  method the glue declares must be registered by libSDL3.so.
# --reference DIR  a second directory (the staging dir) to compare identities against: each library
#                  must be the same object, so a stale library from an earlier run cannot be packaged.
#
# What is checked, per library and then across the set:
#   * ELF class ELF64, machine AArch64, type DYN. A desktop Linux, x86, x86_64 or 32-bit object is
#     rejected by name, not by a generic mismatch.
#   * SONAME equals the file name (the loader keys on it; libmain.so's DT_NEEDED names libSDL3.so).
#   * every DT_NEEDED is either an Android system library or one of the packaged three, and every
#     packaged DT_NEEDED is a real file in --libs (shared-library resolution). A desktop library is
#     rejected.
#   * the exported symbols each object must provide: SDL_main in libmain.so; JNI_OnLoad and the SDL3
#     entry points the engine and the Java glue call in libSDL3.so.
#   * exactly one SDL3 implementation: one libSDL3*.so, one object defining SDL_Init, one defining
#     JNI_OnLoad. libmain.so may define no SDL_* symbol but its own SDL_main.
#   * libmain.so's undefined C++ symbols are defined by the packaged libc++_shared.so.
#   * with --glue: the JNI method names the staged glue declares are registered in libSDL3.so.
#
# Nothing is repaired or skipped: the first violation exits non-zero and names the object and the
# property, because each of these is a device-side crash that a green build would otherwise hide.

set -euo pipefail
export LC_ALL=C

EXPECTED=(libmain.so libSDL3.so libc++_shared.so)

# Libraries the device provides through the NDK's stable ABI. They are never packaged and are the
# only DT_NEEDED entries allowed to be absent from --libs.
SYSTEM_LIBS=" libc.so libm.so libdl.so liblog.so libandroid.so libz.so libGLESv1_CM.so \
libGLESv2.so libGLESv3.so libEGL.so libvulkan.so libOpenSLES.so libaaudio.so libmediandk.so \
libcamera2ndk.so libjnigraphics.so libnativewindow.so libsync.so libbinder_ndk.so "

# Tokens that may only appear in a desktop Linux or Windows object.
DESKTOP_TOKENS="libX11 libXext libfontconfig libfreetype.so libwayland libxkbcommon libGL.so \
libdxvk libwine libbox64 libasound libpulse libcurl libgtk"

# The entry points each object must export (defined, dynamic, default visibility).
MAIN_REQUIRED="SDL_main"
SDL3_REQUIRED="JNI_OnLoad SDL_Init SDL_CreateGPUDevice SDL_GetAndroidActivity SDL_GetAndroidJNIEnv \
SDL_GetAndroidInternalStoragePath SDL_GetVersion"
CXX_REQUIRED="_Znwm _ZdlPv"

LIBS=
NDK=
GLUE=
REFERENCE=
READELF=
NM=

fail() { printf '[verify] ERROR: %s\n' "$*" >&2; exit 1; }
step() { printf '[verify] %s\n' "$*"; }

usage() {
  sed -n '/^#   scripts\/build\/android\/verify-android-native-libs.sh/,/^#$/p' "$0" | sed 's/^# \{0,1\}//'
  cat <<'EOF'

  --libs DIR       directory of arm64-v8a libraries to audit (required)
  --ndk DIR        Android NDK root, for llvm-readelf/llvm-nm
  --glue DIR       SDL3's staged Java glue (java-sdl), for the JNI contract check
  --reference DIR  staging directory to compare each library's identity against
  -h, --help       this text
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --libs) LIBS="$2"; shift 2 ;;
    --libs=*) LIBS="${1#*=}"; shift ;;
    --ndk) NDK="$2"; shift 2 ;;
    --ndk=*) NDK="${1#*=}"; shift ;;
    --glue) GLUE="$2"; shift 2 ;;
    --glue=*) GLUE="${1#*=}"; shift ;;
    --reference) REFERENCE="$2"; shift 2 ;;
    --reference=*) REFERENCE="${1#*=}"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) fail "unknown argument '$1' (try --help)" ;;
  esac
done

[ -n "$LIBS" ] || fail "no --libs directory given (try --help)"
[ -d "$LIBS" ] || fail "--libs $LIBS is not a directory"

# --- the ELF tools -------------------------------------------------------------------------------
# The NDK's own LLVM tools are preferred: they are the binutils that produced and will strip these
# objects. A host readelf/nm of the same generation reads them identically, so it is the fallback.
if [ -n "$NDK" ]; then
  for dir in "$NDK"/toolchains/llvm/prebuilt/*/bin; do
    if [ -x "$dir/llvm-readelf" ]; then READELF="$dir/llvm-readelf"; fi
    if [ -x "$dir/llvm-nm" ]; then NM="$dir/llvm-nm"; fi
  done
fi
[ -n "$READELF" ] || READELF=$(command -v llvm-readelf 2>/dev/null || command -v readelf 2>/dev/null || true)
[ -n "$NM" ] || NM=$(command -v llvm-nm 2>/dev/null || command -v nm 2>/dev/null || true)
[ -n "$READELF" ] || fail "no ELF reader found: pass --ndk DIR (llvm-readelf), or install readelf"
[ -n "$NM" ] || fail "no symbol reader found: pass --ndk DIR (llvm-nm), or install nm"
step "readelf: $READELF"
step "nm:      $NM"

elf_header() { "$READELF" -h "$1"; }
elf_field() { elf_header "$1" | sed -n "s/^ *$2: *//p" | head -n 1; }
soname() { "$READELF" -d "$1" | sed -n 's/.*SONAME.*\[\(.*\)\].*/\1/p' | head -n 1; }
needed() { "$READELF" -d "$1" | sed -n 's/.*NEEDED.*\[\(.*\)\].*/\1/p'; }
defined_syms() { "$NM" -D --defined-only "$1" 2>/dev/null | awk 'NF>0 {print $NF}'; }
undefined_syms() { "$NM" -D --undefined-only "$1" 2>/dev/null | awk 'NF>0 {print $NF}'; }

# The build id survives the symbol strip AGP applies, so it identifies the object that was packaged.
build_id() {
  local id
  id=$("$READELF" -n "$1" 2>/dev/null | sed -n 's/.*Build ID: *//p' | head -n 1)
  if [ -z "$id" ] && command -v readelf >/dev/null 2>&1; then
    id=$(readelf -n "$1" 2>/dev/null | sed -n 's/.*Build ID: *//p' | head -n 1)
  fi
  printf '%s' "$id"
}

is_system_lib() { case "$SYSTEM_LIBS" in *" $1 "*) return 0 ;; esac; return 1; }

# --- the payload set -----------------------------------------------------------------------------
step "auditing $LIBS"
found=()
while IFS= read -r f; do
  found+=("$(basename "$f")")
done < <(find "$LIBS" -maxdepth 1 -type f \( -name '*.so' -o -name '*.so.*' \) | sort)

[ "${#found[@]}" -gt 0 ] || fail "$LIBS holds no shared library: the APK would carry no native payload"
for lib in "${EXPECTED[@]}"; do
  printf '%s\n' "${found[@]}" | grep -qx "$lib" \
    || fail "$LIBS is missing $lib, which the APK must carry"
done
for lib in "${found[@]}"; do
  case " ${EXPECTED[*]} " in
    *" $lib "*) ;;
    *) fail "$LIBS carries '$lib', which nothing in the engine needs. Do not package unused libraries." ;;
  esac
done

# --- per-library checks --------------------------------------------------------------------------
declare -A SONAMES=()
for lib in "${EXPECTED[@]}"; do
  f="$LIBS/$lib"
  [ -s "$f" ] || fail "$lib is empty"
  step "--- $lib ($(stat -c %s "$f") bytes)"

  # ELF class, machine and type, with the specific wrong platform named.
  class=$(elf_field "$f" Class)
  machine=$(elf_field "$f" Machine)
  type=$(elf_field "$f" Type)
  step "    Class:   $class"
  step "    Machine: $machine"
  step "    Type:    $type"
  case "$class" in
    ELF64) ;;
    *) fail "$lib is '$class', not ELF64: a 32-bit object cannot be loaded by the arm64-v8a process" ;;
  esac
  case "$machine" in
    AArch64) ;;
    *X86-64*|*x86-64*|*x86_64*|*AMD*x86*)
      fail "$lib is an x86_64 object ($machine): a desktop Linux library was staged, not the arm64-v8a build" ;;
    *80386*|*Intel*|*i386*)
      fail "$lib is an x86 object ($machine): a 32-bit desktop library was staged" ;;
    *) fail "$lib is '$machine', not AArch64: the APK's only ABI is arm64-v8a" ;;
  esac
  case "$type" in
    DYN*) ;;
    EXEC*) fail "$lib is an executable (EXEC), not a shared object (DYN): System.loadLibrary cannot load it" ;;
    *) fail "$lib is '$type', not a shared object (DYN)" ;;
  esac

  # SONAME: the loader resolves DT_NEEDED entries by it, so it must be the packaged name.
  so=$(soname "$f")
  step "    SONAME:  ${so:-<none>}"
  [ -n "$so" ] || fail "$lib has no SONAME; libmain.so's DT_NEEDED cannot resolve against it"
  [ "$so" = "$lib" ] || fail "$lib has SONAME '$so': the loader would look for $so, which the APK does not carry"
  [ -z "${SONAMES[$so]:-}" ] || fail "$lib and ${SONAMES[$so]} share the SONAME $so: two objects cannot answer one name"
  SONAMES[$so]=$lib

  # DT_NEEDED: classified, then resolved inside the payload.
  needs=$(needed "$f")
  for token in $DESKTOP_TOKENS; do
    if printf '%s\n' $needs | grep -q -- "$token"; then
      fail "$lib needs the desktop library '$token': an Android build cannot link it"
    fi
  done
  step "    DT_NEEDED:"
  for dep in $needs; do
    if is_system_lib "$dep"; then
      step "      system   $dep"
    else
      step "      packaged $dep"
      [ -f "$LIBS/$dep" ] \
        || fail "$lib needs '$dep' at runtime, but $LIBS does not carry it: the device would fail to load it"
    fi
  done

  syms=$(defined_syms "$f")
  case "$lib" in
    libmain.so)
      printf '%s\n' "$syms" | grep -qx "SDL_main" \
        || fail "libmain.so does not export SDL_main: SDLActivity has no entry point to call"
      if printf '%s\n' "$syms" | grep -qx "JNI_OnLoad"; then
        fail "libmain.so exports JNI_OnLoad: SDL3's JNI registration belongs to libSDL3.so alone"
      fi
      # SDL_ShaderCross_* is the engine's static SDL_shadercross, not SDL3 core, so the SDL_ prefix
      # alone proves nothing: the duplicate check against libSDL3.so's exports is done on the set.
      ;;
    libSDL3.so)
      for sym in $SDL3_REQUIRED; do
        printf '%s\n' "$syms" | grep -qx "$sym" \
          || fail "libSDL3.so does not export $sym, which the engine or the Java glue calls"
      done
      ;;
    libc++_shared.so)
      for sym in $CXX_REQUIRED; do
        printf '%s\n' "$syms" | grep -qx "$sym" \
          || fail "libc++_shared.so does not export $sym: it is not the NDK's C++ runtime"
      done
      ;;
  esac
done

# --- the set as a whole --------------------------------------------------------------------------
# One SDL3, one registration authority, one C++ runtime. Duplicates are the failure mode that a
# per-file check cannot see: two objects that both answer a name disagree about SDL's state.
sdl3_objects=()
for lib in "${found[@]}"; do
  case "$lib" in libSDL3*) sdl3_objects+=("$lib") ;; esac
done
[ "${#sdl3_objects[@]}" -eq 1 ] \
  || fail "the payload carries ${#sdl3_objects[@]} SDL3 libraries (${sdl3_objects[*]}); exactly one libSDL3.so is allowed"

sdl_init_definers=$(for lib in "${EXPECTED[@]}"; do
  if defined_syms "$LIBS/$lib" | grep -qx 'SDL_Init'; then echo "$lib"; fi
done | sort)
[ "$(printf '%s\n' "$sdl_init_definers" | grep -c .)" -eq 1 ] \
  || fail "SDL_Init is defined by [${sdl_init_definers//$'\n'/ }]: exactly one SDL3 implementation is allowed"

jni_definers=$(for lib in "${EXPECTED[@]}"; do
  if defined_syms "$LIBS/$lib" | grep -qx 'JNI_OnLoad'; then echo "$lib"; fi
done | sort)
[ "$(printf '%s\n' "$jni_definers" | grep -c .)" -eq 1 ] \
  || fail "JNI_OnLoad is defined by [${jni_definers//$'\n'/ }]: exactly one library may register the JNI methods"
[ "$jni_definers" = "libSDL3.so" ] \
  || fail "JNI_OnLoad is defined by $jni_definers, not libSDL3.so: SDLActivity's native methods are registered by SDL3"

# A second SDL3 does not have to define SDL_Init: a partially linked subset - one subsystem, or a
# copy whose version script hides the rest - is enough to give the process two SDL3 states. Compare
# identities instead: no symbol libSDL3.so exports may also be defined by libmain.so. The one
# exception is the app's own entry point. (SDL_shadercross is static in the engine and defines
# SDL_ShaderCross_*, which libSDL3.so does not export - a prefix test would reject it wrongly.)
overlap=$(comm -12   <(defined_syms "$LIBS/libmain.so" | sort -u)   <(defined_syms "$LIBS/libSDL3.so" | sort -u) | grep -vx 'SDL_main' || true)
if [ -n "$overlap" ]; then
  printf '  %s\n' $overlap >&2
  fail "libmain.so defines $(printf '%s\n' $overlap | grep -c .) symbol(s) that libSDL3.so also exports: a second SDL3 implementation is inside the engine"
fi
step "one SDL3 implementation (libSDL3.so), one JNI registration (libSDL3.so)"

# libmain.so's C++ symbols come from the packaged runtime, not from a static libc++ that would give
# the process two C++ runtimes.
cxx_imports=$(undefined_syms "$LIBS/libmain.so" | grep -E '^_Z' || true)
if [ -n "$cxx_imports" ]; then
  needed "$LIBS/libmain.so" | grep -qx 'libc++_shared.so' \
    || fail "libmain.so imports C++ symbols but does not name libc++_shared.so in DT_NEEDED: no C++ runtime would be loaded"
fi
cxx_providers=$(for lib in libSDL3.so libc++_shared.so; do defined_syms "$LIBS/$lib"; done)
unresolved=()
for sym in $cxx_imports; do
  printf '%s\n' "$cxx_providers" | grep -qx "$sym" || unresolved+=("$sym")
done
if [ "${#unresolved[@]}" -gt 0 ]; then
  printf '  %s\n' "${unresolved[@]}" >&2
  fail "${#unresolved[@]} C++ symbol(s) libmain.so imports are defined by neither libSDL3.so nor libc++_shared.so"
fi
step "every C++ symbol libmain.so imports is defined by the packaged runtime"

# --- the JNI contract with SDL3's Java glue ------------------------------------------------------
if [ -n "$GLUE" ]; then
  glue_dir="$GLUE"
  if [ -d "$GLUE/org/libsdl/app" ]; then glue_dir="$GLUE/org/libsdl/app"; fi
  [ -d "$glue_dir" ] || fail "--glue $GLUE holds neither org/libsdl/app nor the staged glue files"
  mapfile -t glue_files < <(find "$glue_dir" -maxdepth 1 -name '*.java' | sort)
  [ "${#glue_files[@]}" -gt 0 ] || fail "--glue $glue_dir holds no .java file: SDL3's glue was not staged"

  # The native methods the glue declares. Each is registered by libSDL3.so through its
  # JNINativeMethod tables, so its name is a string in the library; a name that is not there is a
  # method that would throw UnsatisfiedLinkError the first time the activity calls it.
  mapfile -t glue_natives < <(
    awk '
      {
        line = $0
        sub(/\/\/.*/, "", line)
        if (match(line, /native[ \t]+[^()]*\(/)) {
          s = substr(line, RSTART, RLENGTH)
          sub(/\($/, "", s)
          n = split(s, part, /[ \t]+/)
          name = part[n]
          if (name ~ /^(if|for|while|switch|return|new|else)$/) next
          if (name ~ /^[A-Za-z_][A-Za-z0-9_]*$/) print name
        }
      }' "${glue_files[@]}" | sort -u
  )
  [ "${#glue_natives[@]}" -gt 20 ] \
    || fail "only ${#glue_natives[@]} native methods were read from $glue_dir: that is not SDL3's glue"

  missing_jni=()
  for name in "${glue_natives[@]}"; do
    grep -qa -- "$name" "$LIBS/libSDL3.so" || missing_jni+=("$name")
  done
  if [ "${#missing_jni[@]}" -gt 0 ]; then
    printf '  %s\n' "${missing_jni[@]}" >&2
    fail "${#missing_jni[@]} native method(s) SDL3's Java glue declares are not registered in libSDL3.so: the glue and the native SDL3 are not the same version"
  fi
  step "SDL3's Java glue (${#glue_files[@]} files, ${#glue_natives[@]} native methods) matches libSDL3.so"
fi

# --- the same objects as the ones that were staged -----------------------------------------------
# AGP strips symbols but keeps the object's build id, so a packaged library that is not the staged
# one - a leftover from an earlier run, or a library from another build tree - is visible here.
if [ -n "$REFERENCE" ]; then
  [ -d "$REFERENCE" ] || fail "--reference $REFERENCE is not a directory"
  for lib in "${EXPECTED[@]}"; do
    ref="$REFERENCE/$lib"
    [ -f "$ref" ] || fail "--reference $REFERENCE does not hold $lib, so $LIBS/$lib cannot be identified"
    want=$(build_id "$ref")
    got=$(build_id "$LIBS/$lib")
    if [ -n "$want" ] && [ -n "$got" ]; then
      [ "$want" = "$got" ] \
        || fail "$lib has build id $got but the staged object's is $want: the APK carries a stale library"
      step "$lib: build id $got matches the staged object"
    else
      # No build id (a linker without --build-id): fall back to the strip-invariant identity.
      [ "$(soname "$LIBS/$lib")" = "$(soname "$ref")" ] \
        || fail "$lib's SONAME differs from the staged object's"
      [ "$(needed "$LIBS/$lib" | sort)" = "$(needed "$ref" | sort)" ] \
        || fail "$lib's DT_NEEDED differs from the staged object's: the APK carries a different library"
      [ "$(defined_syms "$LIBS/$lib" | sort)" = "$(defined_syms "$ref" | sort)" ] \
        || fail "$lib's exported symbols differ from the staged object's: the APK carries a different library"
      step "$lib: SONAME, DT_NEEDED and exports match the staged object (no build id to compare)"
    fi
  done
fi

step "OK: ${EXPECTED[*]} are the arm64-v8a objects the engine needs, with no duplicates and no unresolved dependency"
