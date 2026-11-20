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

package io.github.olcayseygan.zerohourreforged.ui.screens.launcher

/**
 * Which of the launcher's situations the screen is in.
 *
 * <p>Declared by the Activity, never inferred by the screen. The Activity is the only place that
 * knows why a folder is unusable - it asks [io.github.olcayseygan.zerohourreforged.GameFolderStore]
 * whether a tree is remembered, whether its grant survived, and what
 * [io.github.olcayseygan.zerohourreforged.GameFolderValidator] says about the staged copy - and this
 * enum is how it says so without the screen having to re-derive a verdict from prose.
 *
 * <p>Each constant corresponds to one real branch of the Activity's status refresh. There is
 * deliberately no "unknown" member: the screen must always be told, so a state the Activity cannot
 * name would be a compile error rather than a plausible-looking guess.
 */
enum class LauncherStatus {
    /** No folder has been chosen yet. */
    NO_FOLDER,

    /** A copy into the staging directory is running. */
    STAGING,

    /** The staged copy validates, so the game can start. */
    READY,

    /** A folder is remembered but the system no longer grants read access to it. */
    PERMISSION_LOST,

    /** The picked folder has no Zero Hour installation in it at all. */
    INVALID,

    /** Zero Hour is present but incomplete - the base game's archives are missing. */
    INCOMPLETE,

    /** The last copy did not finish; nothing was staged from it. */
    STAGING_FAILED,
    ;

    /**
     * Whether the player can fix this by choosing a folder again.
     *
     * <p>True for every state that leaves the game unplayable, which is what makes "Choose game
     * folder" the primary action in all of them: re-picking is the recovery path, and the screen
     * should not make the player find it.
     */
    val needsFolder: Boolean
        get() = this != READY && this != STAGING
}
