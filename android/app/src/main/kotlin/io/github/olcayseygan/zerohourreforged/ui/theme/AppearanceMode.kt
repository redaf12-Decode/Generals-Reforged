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

package io.github.olcayseygan.zerohourreforged.ui.theme

/**
 * What the player asked the application to look like.
 *
 * <p>[SYSTEM] is the default and follows the device's own light/dark setting, which is what the
 * rest of the phone's UI does. The other two pin the scheme regardless of the device, because a
 * game launcher is often used in a dark room where the player wants the dark scheme even if the
 * phone is in light mode (and vice versa in daylight).
 *
 * <p>Public because it is a parameter of [ZeroHourTheme], which the Activities' hosts call.
 *
 * <p>Deliberately a plain Kotlin enum with no Android types: [resolve] is the whole rule, and
 * `AppearanceAndToneTest` runs it on the host JVM.
 */
enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK;

    /**
     * Whether to draw the dark scheme.
     *
     * <p>Only [SYSTEM] consults [systemInDarkTheme]; an explicit choice always wins, which is the
     * point of offering it.
     */
    fun resolve(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    /** The value to persist: the constant's name, so a renamed constant is a visible diff. */
    fun storageValue(): String = name

    companion object {
        /** The default before the player has chosen: follow the device. */
        val DEFAULT = SYSTEM

        /**
         * Parses a stored value, falling back to [DEFAULT] for anything unrecognised.
         *
         * <p>A stored value can be stale - written by a build that had a different set of modes - so
         * an unknown string must not throw and must not leave the application without a scheme.
         */
        fun fromStorage(value: String?): AppearanceMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
