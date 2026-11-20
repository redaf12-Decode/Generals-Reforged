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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
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
    // Measured, not taken from the mode: a wallpaper-derived surface is not guaranteed to sit on the
    // same side of mid-grey as the mode it was generated for.
    //
    // Both colours are consulted because the bars are transparent: the status bar sits over the app
    // bar's surface on the compatibility screen but over the background on the launcher, which has a
    // header rather than a top bar. Dark icons are only safe if *every* colour they can land on is
    // light, so the darker of the two decides.
    val lightBars = minOf(
        colorScheme.background.luminance(),
        colorScheme.surface.luminance(),
    ) > 0.5f

    LaunchedEffect(background, lightBars) {
        activity.window.applyAppearance(background, lightBars)
    }
}

/**
 * Lets the window draw behind the system bars, so a transparent bar shows the Compose background
 * rather than a second colour.
 *
 * <p>Called from the two presentation hosts in `onCreate`, before `setContent`, and never from the
 * game Activity. Both halves of that are deliberate.
 *
 * <p>Before `setContent` because this changes the window's layout: doing it after the first frame
 * would re-lay out a drawn screen, and the content would jump by the height of the bars. Compose's
 * own `enableEdgeToEdge` runs here for the same reason.
 *
 * <p>Never for the game because SDL owns that surface's geometry and decides its own fullscreen
 * insets; a second party turning `decorFitsSystemWindows` off underneath it would change how the
 * game renders, which this phase is not allowed to do.
 *
 * <p>Two code paths because the flag that does this moved: API 30 replaced the systemUiVisibility
 * layout flags with `setDecorFitsSystemWindows`. Below 30 the flags are still the only way, and all
 * three are needed - dropping LAYOUT_HIDE_NAVIGATION leaves the navigation bar reserving its strip
 * while the status bar does not, which is the half-edge-to-edge look.
 *
 * <p>Cutout mode is `SHORT_EDGES`: the window is allowed to paint into the cutout area on the short
 * edges, so the background reaches the edge of the screen instead of being letterboxed. That mode
 * does <em>not</em> keep content out of a notch - in landscape a phone's notch is on a short edge,
 * so the window may paint under it. Keeping text clear of the cutout is the insets' job, which is why
 * the screens consume [androidx.compose.foundation.layout.WindowInsets.safeDrawing] rather than only
 * the system bars: `safeDrawing` reports the cutout on every edge, `systemBars` does not.
 */
@Suppress("DEPRECATION")
internal fun Activity.enableEdgeToEdgeWindow() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
    } else {
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
    // API 28+; minSdk is 28, so no version check is needed.
    window.attributes = window.attributes.apply {
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}

/**
 * The deprecated bar colours and icon flags are used deliberately: they are the only API that works
 * down to minSdk 28, and the modern replacement only exists from API 30.
 *
 * <p>Both bars are transparent, not tinted. From API 35 the system draws every app edge to edge and
 * ignores `statusBarColor`/`navigationBarColor` outright, so a colour set here would work on 28-34
 * and silently do nothing on 35 - two different layouts from one code path. Transparent everywhere
 * means the Compose background is what shows behind the bars on every release: the app bar's surface
 * behind the status bar on the compatibility screen, the background behind it on the launcher, which
 * has a header instead of a top bar.
 *
 * <p>The icon tint still has to be set explicitly: a transparent bar does not tell the system
 * whether the content behind it is light or dark, and the player can pin either regardless of the
 * device's night mode.
 */
@Suppress("DEPRECATION")
private fun Window.applyAppearance(background: Int, lightBars: Boolean) {
    // Also set here, not only in XML: the window outlives an Activity recreation, so a scheme change
    // must repaint it rather than leave the colour the previous theme installed.
    setBackgroundDrawable(ColorDrawable(background))
    statusBarColor = Color.TRANSPARENT
    navigationBarColor = Color.TRANSPARENT

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
