/*
**      Command & Conquer Generals Zero Hour(tm)
**      Copyright 2025 Electronic Arts Inc.
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

package io.github.olcayseygan.zerohourreforged;

import android.content.Intent;
import android.os.Bundle;

import org.libsdl.app.SDLActivity;

import java.io.File;

/**
 * The game's Android Activity: a thin shell over SDL3's SDLActivity.
 *
 * <p>Everything SDL3 does for an Android game is inherited unchanged - the surface,
 * the input dispatch, the lifecycle (onPause/onResume/onDestroy), and the start-up
 * sequence that loads the native library and calls its exported {@code SDL_main} on
 * a dedicated thread. This class adds nothing to that: no lifecycle override, no
 * duplicate entry point, no rendering or input code.
 *
 * <p>The one thing it decides is which native libraries to load.
 *
 * <p>The engine is built as {@code libmain.so} (GeneralsMD/Code/CMakeLists.txt,
 * {@code add_library(generals SHARED ...)} with {@code OUTPUT_NAME main}) from
 * {@code Main/PosixMain.cpp}, whose {@code main()} SDL's {@code SDL_main.h} renames to
 * the exported {@code SDL_main}. SDLActivity's default {@link #getMainSharedObject()}
 * resolves to {@code libmain.so} and {@link #getMainFunction()} to {@code SDL_main},
 * so the entry point needs no override here.
 *
 * <p>SDL3 itself is linked <em>statically</em> into libmain.so (the build forces
 * {@code SDL_STATIC} on and {@code SDL_SHARED} off), so the APK carries no
 * {@code libSDL3.so} and SDL3's JNI {@code JNI_OnLoad} lives inside libmain.so. SDL3's
 * default {@code getLibraries()} returns {@code {"SDL3", "main"}}, which would try to
 * load a library that is not in the APK; this override returns only the one library
 * that is.
 */
public class ZeroHourActivity extends SDLActivity {

    @Override
    protected String[] getLibraries() {
        // Only libmain.so: it already contains SDL3 (linked statically) and exports
        // SDL3's JNI_OnLoad, so SDLActivity's JNI bindings are registered when this
        // library loads. There is no separate libSDL3.so to load.
        return new String[] { "main" };
    }

    /**
     * The engine's install root: {@code -root <folder>}, which {@code PosixMain.cpp}'s
     * {@code chooseInstallRoot} takes as given and {@code chdir}s into before the engine exists.
     *
     * <p>The folder is the staged copy of the one the player picked (see {@link SafTreeStager}),
     * a real filesystem path - the engine's file access is POSIX and cannot open the
     * {@code content://} URI the picker returns. SDL passes these strings to the native
     * {@code main} as argv[1..], so the engine never has to guess where its data is.
     */
    @Override
    protected String[] getArguments() {
        File root = GameFolderStore.engineRoot(this);
        return new String[] { "-root", root.getAbsolutePath() };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Reached from SetupActivity only when the staged folder validated. A direct start (a stale
        // shortcut, or a relaunch after the folder moved) sends the player to the picker instead.
        // SDLActivity starts the native SDL_main thread on the first resume, so finishing here -
        // before onResume - means the engine never runs with a folder that does not validate.
        if (!GameFolderStore.isReady(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
        }
    }
}
