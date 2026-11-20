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
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * The application theme: one [MaterialTheme] carrying the project's colour, type, shape and spacing
 * scales, plus the two semantic layers Material 3 does not define (status tones and spacing).
 *
 * <p>The game screen is SDL3's own surface and never enters this tree, so this theme only ever
 * applies to the launcher and compatibility screens.
 *
 * @param appearance what the player asked for; [AppearanceMode.SYSTEM] follows the device.
 * @param dynamicColor whether to derive the palette from the device wallpaper. Honoured only on
 *   Android 12 and later, where the platform exposes that palette; below that the brand scheme is
 *   used, which is the platform's own boundary rather than a degraded mode. Off by default - the
 *   brand palette is the identity, and dynamic colour is offered as a preference, not imposed.
 */
@Composable
fun ZeroHourTheme(
    appearance: AppearanceMode = AppearanceMode.DEFAULT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = appearance.resolve(isSystemInDarkTheme())
    val context = LocalContext.current
    val dynamicAvailable = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = remember(dark, dynamicAvailable, context) {
        when {
            dynamicAvailable -> dynamicScheme(context, dark)
            dark -> ZeroHourDarkColors
            else -> ZeroHourLightColors
        }
    }
    val statusColors = remember(colorScheme) { statusColorsFor(colorScheme) }

    ApplyWindowAppearance(colorScheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZeroHourTypography,
        shapes = ZeroHourShapes,
    ) {
        CompositionLocalProvider(
            LocalStatusColors provides statusColors,
            LocalSpacing provides ZeroHourSpacing,
        ) {
            // Both Activities use a plain fullscreen window theme with no windowBackground, so
            // without this Surface the platform's default black would show through and the light
            // scheme's dark text would be unreadable.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                content = content,
            )
        }
    }
}

/**
 * A wallpaper-derived scheme that keeps the brand identity.
 *
 * <p>The platform palette supplies every neutral role - the surfaces the wallpaper was generated
 * for - but the accent roles are overwritten with the brand gold and green, so the application
 * still looks like itself rather than like the wallpaper. Which gold (the dark scheme's bright one
 * or the light scheme's deep one) is decided by measuring contrast against the surface the platform
 * actually produced, since a dynamic light surface is not necessarily this project's light surface.
 */
private fun dynamicScheme(context: Context, dark: Boolean): ColorScheme {
    val base = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val surface = base.surface.argb()
    val primary = BrandContrast.bestAccent(
        darkSchemeAccent = BrandPalette.DarkPrimary,
        lightSchemeAccent = BrandPalette.LightPrimary,
        background = surface,
    )
    val secondary = BrandContrast.bestAccent(
        darkSchemeAccent = BrandPalette.DarkSecondary,
        lightSchemeAccent = BrandPalette.LightSecondary,
        background = surface,
    )
    val onPrimary = if (primary == BrandPalette.DarkPrimary) {
        BrandPalette.DarkOnPrimary
    } else {
        BrandPalette.LightOnPrimary
    }
    val onSecondary = if (secondary == BrandPalette.DarkSecondary) {
        BrandPalette.DarkOnSecondary
    } else {
        BrandPalette.LightOnSecondary
    }
    return base.copy(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
    )
}

/**
 * The status tones for [scheme].
 *
 * <p>A wallpaper-derived scheme has this project's surfaces replaced by the platform's, so the
 * hand-tuned per-scheme tones are not guaranteed to clear 4.5:1 on it. The dark or light tone set is
 * therefore picked by measuring the success tone against the surface that is actually in use, rather
 * than by assuming which of the two fits.
 */
private fun statusColorsFor(scheme: ColorScheme): StatusColors {
    val dark = scheme.surface.luminance() < 0.5f
    val preferred = if (dark) ZeroHourDarkStatusColors else ZeroHourLightStatusColors
    val alternate = if (dark) ZeroHourLightStatusColors else ZeroHourDarkStatusColors
    return if (BrandContrast.meetsTextContrast(preferred.success.argb(), scheme.surface.argb())) {
        preferred
    } else {
        alternate
    }
}

/** The opaque 0xAARRGGBB form of a Compose colour, for the pure contrast maths. */
private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

/**
 * The spacing scale of the enclosing [ZeroHourTheme], reached as `MaterialTheme.spacing`.
 *
 * <p>Material 3's [MaterialTheme] has no spacing slot, so this is the extension that gives it one;
 * the value comes from [LocalSpacing], which [ZeroHourTheme] provides.
 */
internal val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
