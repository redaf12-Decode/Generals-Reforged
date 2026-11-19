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

package io.github.olcayseygan.zerohourreforged.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import io.github.olcayseygan.zerohourreforged.ui.screens.launcher.LauncherScreen
import io.github.olcayseygan.zerohourreforged.ui.screens.launcher.LauncherUiState
import io.github.olcayseygan.zerohourreforged.ui.theme.ZeroHourTheme

/**
 * The bridge between the Java [android.app.Activity] that owns the launcher's logic and the
 * Compose screen that draws it.
 *
 * <p>It exists because `setContent` takes a `@Composable` lambda, which Java cannot construct. The
 * Activity creates one host, calls [install] once in `onCreate`, then calls [render] whenever its
 * own state changes. The Activity keeps every decision; this class only forwards taps and holds
 * the value Compose observes.
 */
class LauncherHost(
    private val activity: ComponentActivity,
    private val callbacks: Callbacks,
) {

    /** What a tap on the launcher means; implemented by the Activity. */
    interface Callbacks {
        fun onSelectFolder()
        fun onLaunchGame()
        fun onClearFolder()
        fun onShowRequirements()
    }

    private val state = mutableStateOf(LauncherUiState(status = ""))

    fun install() {
        activity.setContent {
            ZeroHourTheme {
                LauncherScreen(
                    state = state.value,
                    onSelectFolder = callbacks::onSelectFolder,
                    onLaunchGame = callbacks::onLaunchGame,
                    onClearFolder = callbacks::onClearFolder,
                    onShowRequirements = callbacks::onShowRequirements,
                )
            }
        }
    }

    fun render(
        status: String,
        detail: String,
        staging: Boolean,
        progressCopied: Long,
        progressTotal: Long,
        canLaunch: Boolean,
        canClear: Boolean,
    ) {
        state.value = LauncherUiState(
            status = status,
            detail = detail,
            staging = staging,
            progressCopied = progressCopied,
            progressTotal = progressTotal,
            canLaunch = canLaunch,
            canClear = canClear,
        )
    }
}
