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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

/**
 * Paints the platform window - its background, its system bar colours and their icon tint - from the
 * Compose scheme that draws on it.
 *
 * <p>The XML themes give the window a colour correct for the *device's* night mode, which is all a
 * resource qualifier can express. This application also lets the player pin light or dark regardless
 * of the device, and lets Android 12 derive the palette from the wallpaper; both make the resolved
 * scheme differ from what `values-night` selected. The window underneath Compose would then be the
 * wrong colour - a flash between Activity recreation and the first frame, and status bar icons tinted
 * for a scheme that is not on screen.
 *
 * <p>Driven from the theme rather than from each Activity so one place owns the mapping and it
 * cannot drift from the scheme the content is drawn with. The XML themes stay as the first-frame
 * fallback, which is what makes the Android 9-11 path correct before Compose has drawn anything.
 *
 * <p>No-ops outside a real Activity, which is what makes it safe in a `@Preview`.
 */
@Composable
internal fun ApplyWindowAppearance(colorScheme: ColorScheme) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context.findActivity() ?: return
    val background = colorScheme.background.toArgb()
    val surface = colorScheme.surface.toArgb()
    // Measured, not taken from the mode: a wallpaper-derived surface is not guaranteed to sit on the
    // same side of mid-grey as the mode it was generated for.
    val lightBars = colorScheme.surface.luminance() > 0.5f

    LaunchedEffect(background, surface, lightBars) {
        activity.window.applyAppearance(background, surface, lightBars)
    }
}

/**
 * The deprecated bar colours and icon flags are used deliberately: they are the only API that works
 * down to minSdk 28, and the modern replacement only exists from API 30.
 */
@Suppress("DEPRECATION")
private fun Window.applyAppearance(background: Int, surface: Int, lightBars: Boolean) {
    // Also set here, not only in XML: the window outlives an Activity recreation, so a scheme change
    // must repaint it rather than leave the colour the previous theme installed.
    setBackgroundDrawable(ColorDrawable(background))
    statusBarColor = surface
    navigationBarColor = background

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        insetsController?.setSystemBarsAppearance(if (lightBars) light else 0, light)
    } else {
        decorView.systemUiVisibility = if (lightBars) {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            0
        }
    }
}

/** The Activity hosting this context, or null if there is none (a preview, a plain view tree). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
