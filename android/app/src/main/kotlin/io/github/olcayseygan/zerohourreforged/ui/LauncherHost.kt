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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.olcayseygan.zerohourreforged.ui.screens.launcher.LauncherScreen
import io.github.olcayseygan.zerohourreforged.ui.screens.launcher.LauncherUiState
import io.github.olcayseygan.zerohourreforged.ui.theme.AppearanceMode
import io.github.olcayseygan.zerohourreforged.ui.theme.AppearanceStore
import io.github.olcayseygan.zerohourreforged.ui.theme.ZeroHourTheme

/**
 * The bridge between the Java [android.app.Activity] that owns the launcher's logic and the
 * Compose screen that draws it.
 *
 * <p>It exists because `setContent` takes a `@Composable` lambda, which Java cannot construct. The
 * Activity creates one host, calls [install] once in `onCreate`, then calls [render] whenever its
 * own state changes. The Activity keeps every decision; this class only forwards taps and holds
 * the values Compose observes.
 *
 * <p>Appearance is the one piece of state this class owns rather than the Activity. It is read from
 * [AppearanceStore] when the tree is installed and written back when the player changes it; both
 * values are observed, so a change repaints the whole tree without the Activity recreating itself.
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

    private var state by mutableStateOf(LauncherUiState(status = ""))
    private var appearance by mutableStateOf(AppearanceStore.mode(activity))
    private var dynamicColor by mutableStateOf(AppearanceStore.dynamicColor(activity))

    /**
     * The failure the player has to acknowledge, or null.
     *
     * <p>Held here rather than raised with the platform's `AlertDialog` so the error is drawn by the
     * same design system as the screen behind it, and so it cannot outlive the Activity: it is a
     * Compose state value, not a window the platform owns.
     */
    private var errorMessage by mutableStateOf<String?>(null)

    fun install() {
        activity.setContent {
            ZeroHourTheme(appearance = appearance, dynamicColor = dynamicColor) {
                LauncherScreen(
                    state = state,
                    onSelectFolder = callbacks::onSelectFolder,
                    onLaunchGame = callbacks::onLaunchGame,
                    onClearFolder = callbacks::onClearFolder,
                    onShowRequirements = callbacks::onShowRequirements,
                    appearance = appearance,
                    dynamicColor = dynamicColor,
                    onAppearanceChange = ::onAppearanceChange,
                    error = errorMessage,
                    onDismissError = { errorMessage = null },
                )
            }
        }
    }

    /**
     * Shows a failure to the player. Called from the Activity's UI-thread callbacks only, which is
     * where Compose state may be written.
     */
    fun showError(message: String) {
        errorMessage = message
    }

    private fun onAppearanceChange(mode: AppearanceMode, useDynamicColor: Boolean) {
        appearance = mode
        dynamicColor = useDynamicColor
        AppearanceStore.save(activity, mode, useDynamicColor)
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
        state = LauncherUiState(
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
