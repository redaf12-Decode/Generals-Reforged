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

@file:JvmName("CompatibilityUi")

package io.github.olcayseygan.zerohourreforged.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.olcayseygan.zerohourreforged.CompatibilityEvaluator
import io.github.olcayseygan.zerohourreforged.DeviceCapabilities
import io.github.olcayseygan.zerohourreforged.ui.screens.compatibility.CompatibilityScreen
import io.github.olcayseygan.zerohourreforged.ui.theme.AppearanceStore
import io.github.olcayseygan.zerohourreforged.ui.theme.ZeroHourTheme

/**
 * Installs the compatibility screen on [activity]. The screen is a pure renderer of the values it
 * is given, so the caller keeps reading the device and evaluating the verdict exactly as before;
 * this only replaces how the result is drawn.
 *
 * <p>The appearance is read from [AppearanceStore] rather than passed in, so this screen matches the
 * launcher the player came from even though it is a separate Activity with no shared state.
 *
 * @param onNavigateBack called by the top bar's back arrow. Null leaves the arrow off, which is
 *   correct only if this screen is ever shown as the task root.
 */
@JvmOverloads
fun installCompatibilityContent(
    activity: ComponentActivity,
    device: DeviceCapabilities,
    report: CompatibilityEvaluator.Report,
    onNavigateBack: (() -> Unit)? = null,
) {
    activity.setContent {
        ZeroHourTheme(
            appearance = AppearanceStore.mode(activity),
            dynamicColor = AppearanceStore.dynamicColor(activity),
        ) {
            CompatibilityScreen(
                device = device,
                report = report,
                onNavigateBack = onNavigateBack,
            )
        }
    }
}
