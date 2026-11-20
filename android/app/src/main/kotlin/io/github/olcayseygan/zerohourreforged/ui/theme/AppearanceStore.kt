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

import android.content.Context

/**
 * Persists the player's appearance choices.
 *
 * <p>A tiny store rather than a preferences screen: the launcher's appearance controls write here,
 * and both Activities read it when they install their Compose tree. Values are read once per
 * Activity creation, which is enough - changing the appearance recreates the content, and a change
 * made on one screen is seen by the other the next time it is opened.
 *
 * <p>Uses its own preferences file so it never collides with the game-folder store's keys, and
 * `MODE_PRIVATE` so another application cannot read or write the player's choice.
 */
internal object AppearanceStore {

    private const val PREFERENCES = "appearance"
    private const val KEY_MODE = "mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    fun mode(context: Context): AppearanceMode =
        AppearanceMode.fromStorage(preferences(context).getString(KEY_MODE, null))

    fun dynamicColor(context: Context): Boolean =
        preferences(context).getBoolean(KEY_DYNAMIC_COLOR, false)

    fun save(context: Context, mode: AppearanceMode, dynamicColor: Boolean) {
        preferences(context).edit()
            .putString(KEY_MODE, mode.storageValue())
            .putBoolean(KEY_DYNAMIC_COLOR, dynamicColor)
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
