/*
**      Copyright 2026 İlyas Akın
**      Additional terms under GNU GPL section 7 apply: see LICENSE.md.
**
**      This program is free software: you can redistribute it and/or modify
**      it under the terms of the GNU General Public License as published by
**      the Free Software Foundation, either version 3 of the License, or
**      (at your option) any later version.
**
**      This program is distributed in the hope that it will be useful,
**      but WITHOUT ANY WARRANTY; without even the implied warranty of
**      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**      GNU General Public License for more details.
**
**      You should have received a copy of the GNU General Public License
**      along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

// Platform/StackWalk.h: walk the call stack and print it, on glibc and on Android's bionic.
//
// glibc's <execinfo.h> backtrace()/backtrace_symbols_fd() are not Android's: bionic only gained
// them at API 33, and the port's baseline is API 28 (scripts/build/android/android.toolchain.cmake),
// so an Android build that calls them does not compile.  The unwinder every Android version has is
// the C++ ABI's _Unwind_Backtrace (libgcc/compiler-rt), which walks the stack and hands each frame
// to a callback; _Unwind_GetIP(ctx) yields the frame's program counter - exactly what backtrace()
// returns and what dladdr() consumes.
//
// The public names are zh_* rather than the glibc ones, so no translation unit that includes this
// header can accidentally call backtrace() and link-fail on Android.  Both functions are
// non-allocating: they may run on a crash path, where the heap cannot be trusted.

#pragma once

#include <stddef.h>
#include <stdint.h>

// Android uses the ABI unwinder; glibc keeps execinfo's.  Android also defines __linux__, so the
// test names __ANDROID__ first - the same trap PlatformPredicates.h documents.
#if defined(__ANDROID__)
#define ZH_STACKWALK_UNWIND 1
#else
#define ZH_STACKWALK_UNWIND 0
#endif

#if ZH_STACKWALK_UNWIND

#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <unwind.h>

typedef struct ZhUnwindState
{
        void **frames;
        int max;
        int count;
} ZhUnwindState;

static inline _Unwind_Reason_Code zhUnwindCallback( struct _Unwind_Context *context, void *argument )
{
        ZhUnwindState *state = (ZhUnwindState *)argument;
        if (state->count >= state->max)
                return _URC_END_OF_STACK;
        const uintptr_t ip = (uintptr_t)_Unwind_GetIP( context );
        if (ip != 0)
                state->frames[ state->count++ ] = (void *)ip;
        return _URC_NO_REASON;
}

/** The addresses of up to `max` frames of the current stack, the caller first.  The count written,
        * as backtrace() answers it.  Its first call initialises the unwinder's tables (which allocate),
        * so a caller that must not allocate - a crash handler - primes it once at install time. */
static inline int zh_backtrace( void **frames, int max )
{
        ZhUnwindState state;
        state.frames = frames;
        state.max = max;
        state.count = 0;
        _Unwind_Backtrace( zhUnwindCallback, &state );
        return state.count;
}

/** Writes `count` frames to `fd`, one per line, symbolised where dladdr can.  bionic has no
        * backtrace_symbols_fd, and backtrace_symbols() mallocs, so each line is formatted into a stack
        * buffer and written directly.  The line shape matches StackDumpPosix.cpp's formatFrame. */
static inline void zh_backtrace_print( int fd, void *const *frames, int count )
{
        char line[ 512 ];
        for (int i = 0; i < count; ++i)
        {
                Dl_info info;
                const bool found = dladdr( frames[ i ], &info ) != 0;
                const char *path = (found && info.dli_fname) ? info.dli_fname : "?";
                const char *slash = strrchr( path, '/' );
                const char *module = slash ? slash + 1 : path;
                int written;
                if (found && info.dli_sname != NULL)
                        written = snprintf( line, sizeof( line ), "%s(%s+0x%lx) [0x%llx]\n", module, info.dli_sname,
                                (unsigned long)((char *)frames[ i ] - (char *)info.dli_saddr),
                                (unsigned long long)(uintptr_t)frames[ i ] );
                else if (found && info.dli_fbase != NULL)
                        written = snprintf( line, sizeof( line ), "%s(%s+0x%lx) [0x%llx]\n", module, module,
                                (unsigned long)((char *)frames[ i ] - (char *)info.dli_fbase),
                                (unsigned long long)(uintptr_t)frames[ i ] );
                else
                        written = snprintf( line, sizeof( line ), "[0x%llx]\n", (unsigned long long)(uintptr_t)frames[ i ] );
                if (written > 0)
                {
                        if ((size_t)written > sizeof( line ) - 1)
                                written = (int)(sizeof( line ) - 1);
                        ssize_t ignored = write( fd, line, (size_t)written );
                        (void)ignored;
                }
        }
}

#else   // glibc, macOS and the other POSIX systems: execinfo's own

#include <execinfo.h>

static inline int zh_backtrace( void **frames, int max )
{
        return backtrace( frames, max );
}

static inline void zh_backtrace_print( int fd, void *const *frames, int count )
{
        backtrace_symbols_fd( (void *const *)frames, count, fd );
}

#endif
