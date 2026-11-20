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

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private fun Long.toColor(): Color = Color(this)

/**
 * The dark scheme: deep graphite surfaces with a warm metallic gold accent.
 *
 * <p>Every surface role is set explicitly, including the `surfaceContainer*` ramp Material 3 uses
 * for cards, menus and dialogs. Leaving them unset would fall back to the library's neutral grey
 * tokens, which are a different temperature from this palette and would break the single-hue ramp
 * the design relies on for depth.
 */
internal val ZeroHourDarkColors: ColorScheme = darkColorScheme(
    primary = BrandPalette.DarkPrimary.toColor(),
    onPrimary = BrandPalette.DarkOnPrimary.toColor(),
    primaryContainer = BrandPalette.DarkPrimaryContainer.toColor(),
    onPrimaryContainer = BrandPalette.DarkOnPrimaryContainer.toColor(),
    inversePrimary = BrandPalette.DarkInversePrimary.toColor(),
    secondary = BrandPalette.DarkSecondary.toColor(),
    onSecondary = BrandPalette.DarkOnSecondary.toColor(),
    secondaryContainer = BrandPalette.DarkSecondaryContainer.toColor(),
    onSecondaryContainer = BrandPalette.DarkOnSecondaryContainer.toColor(),
    tertiary = BrandPalette.DarkTertiary.toColor(),
    onTertiary = BrandPalette.DarkOnTertiary.toColor(),
    tertiaryContainer = BrandPalette.DarkTertiaryContainer.toColor(),
    onTertiaryContainer = BrandPalette.DarkOnTertiaryContainer.toColor(),
    background = BrandPalette.DarkBackground.toColor(),
    onBackground = BrandPalette.DarkOnBackground.toColor(),
    surface = BrandPalette.DarkSurface.toColor(),
    onSurface = BrandPalette.DarkOnSurface.toColor(),
    surfaceVariant = BrandPalette.DarkSurfaceVariant.toColor(),
    onSurfaceVariant = BrandPalette.DarkOnSurfaceVariant.toColor(),
    surfaceDim = BrandPalette.DarkSurfaceDim.toColor(),
    surfaceBright = BrandPalette.DarkSurfaceBright.toColor(),
    surfaceContainerLowest = BrandPalette.DarkSurfaceContainerLowest.toColor(),
    surfaceContainerLow = BrandPalette.DarkSurfaceContainerLow.toColor(),
    surfaceContainer = BrandPalette.DarkSurfaceContainer.toColor(),
    surfaceContainerHigh = BrandPalette.DarkSurfaceContainerHigh.toColor(),
    surfaceContainerHighest = BrandPalette.DarkSurfaceContainerHighest.toColor(),
    error = BrandPalette.DarkError.toColor(),
    onError = BrandPalette.DarkOnError.toColor(),
    errorContainer = BrandPalette.DarkErrorContainer.toColor(),
    onErrorContainer = BrandPalette.DarkOnErrorContainer.toColor(),
    outline = BrandPalette.DarkOutline.toColor(),
    outlineVariant = BrandPalette.DarkOutlineVariant.toColor(),
    inverseSurface = BrandPalette.DarkInverseSurface.toColor(),
    inverseOnSurface = BrandPalette.DarkInverseOnSurface.toColor(),
)

/** The light scheme: warm paper surfaces with a deep gold accent that still reads as gold. */
internal val ZeroHourLightColors: ColorScheme = lightColorScheme(
    primary = BrandPalette.LightPrimary.toColor(),
    onPrimary = BrandPalette.LightOnPrimary.toColor(),
    primaryContainer = BrandPalette.LightPrimaryContainer.toColor(),
    onPrimaryContainer = BrandPalette.LightOnPrimaryContainer.toColor(),
    inversePrimary = BrandPalette.LightInversePrimary.toColor(),
    secondary = BrandPalette.LightSecondary.toColor(),
    onSecondary = BrandPalette.LightOnSecondary.toColor(),
    secondaryContainer = BrandPalette.LightSecondaryContainer.toColor(),
    onSecondaryContainer = BrandPalette.LightOnSecondaryContainer.toColor(),
    tertiary = BrandPalette.LightTertiary.toColor(),
    onTertiary = BrandPalette.LightOnTertiary.toColor(),
    tertiaryContainer = BrandPalette.LightTertiaryContainer.toColor(),
    onTertiaryContainer = BrandPalette.LightOnTertiaryContainer.toColor(),
    background = BrandPalette.LightBackground.toColor(),
    onBackground = BrandPalette.LightOnBackground.toColor(),
    surface = BrandPalette.LightSurface.toColor(),
    onSurface = BrandPalette.LightOnSurface.toColor(),
    surfaceVariant = BrandPalette.LightSurfaceVariant.toColor(),
    onSurfaceVariant = BrandPalette.LightOnSurfaceVariant.toColor(),
    surfaceDim = BrandPalette.LightSurfaceDim.toColor(),
    surfaceBright = BrandPalette.LightSurfaceBright.toColor(),
    surfaceContainerLowest = BrandPalette.LightSurfaceContainerLowest.toColor(),
    surfaceContainerLow = BrandPalette.LightSurfaceContainerLow.toColor(),
    surfaceContainer = BrandPalette.LightSurfaceContainer.toColor(),
    surfaceContainerHigh = BrandPalette.LightSurfaceContainerHigh.toColor(),
    surfaceContainerHighest = BrandPalette.LightSurfaceContainerHighest.toColor(),
    error = BrandPalette.LightError.toColor(),
    onError = BrandPalette.LightOnError.toColor(),
    errorContainer = BrandPalette.LightErrorContainer.toColor(),
    onErrorContainer = BrandPalette.LightOnErrorContainer.toColor(),
    outline = BrandPalette.LightOutline.toColor(),
    outlineVariant = BrandPalette.LightOutlineVariant.toColor(),
    inverseSurface = BrandPalette.LightInverseSurface.toColor(),
    inverseOnSurface = BrandPalette.LightInverseOnSurface.toColor(),
)

/**
 * The status tones: a semantic layer Material 3 does not have.
 *
 * <p>Material's `error` role covers one state. A status card and a status chip need four, and they
 * need them to mean the same thing in both schemes. Each tone therefore comes as a triple:
 *
 * <ul>
 *   <li>[content] draws a glyph, an icon or a short label on a normal surface;</li>
 *   <li>[container] is a tinted fill, used for the status card and the chip;</li>
 *   <li>[onContainer] is the text drawn inside that fill.</li>
 * </ul>
 *
 * <p>All three are tuned per scheme and audited by `WcagContrastTest`: content clears 4.5:1 on every
 * surface role it may be drawn on, and onContainer clears 4.5:1 on its own container.
 */
@Immutable
internal data class StatusColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
)

internal val ZeroHourDarkStatusColors = StatusColors(
    success = BrandPalette.DarkSuccess.toColor(),
    successContainer = BrandPalette.DarkSuccessContainer.toColor(),
    onSuccessContainer = BrandPalette.DarkOnSuccessContainer.toColor(),
    warning = BrandPalette.DarkWarning.toColor(),
    warningContainer = BrandPalette.DarkWarningContainer.toColor(),
    onWarningContainer = BrandPalette.DarkOnWarningContainer.toColor(),
    danger = BrandPalette.DarkDanger.toColor(),
    dangerContainer = BrandPalette.DarkDangerContainer.toColor(),
    onDangerContainer = BrandPalette.DarkOnDangerContainer.toColor(),
    info = BrandPalette.DarkInfo.toColor(),
    infoContainer = BrandPalette.DarkInfoContainer.toColor(),
    onInfoContainer = BrandPalette.DarkOnInfoContainer.toColor(),
    neutral = BrandPalette.DarkNeutral.toColor(),
    neutralContainer = BrandPalette.DarkNeutralContainer.toColor(),
    onNeutralContainer = BrandPalette.DarkOnNeutralContainer.toColor(),
)

internal val ZeroHourLightStatusColors = StatusColors(
    success = BrandPalette.LightSuccess.toColor(),
    successContainer = BrandPalette.LightSuccessContainer.toColor(),
    onSuccessContainer = BrandPalette.LightOnSuccessContainer.toColor(),
    warning = BrandPalette.LightWarning.toColor(),
    warningContainer = BrandPalette.LightWarningContainer.toColor(),
    onWarningContainer = BrandPalette.LightOnWarningContainer.toColor(),
    danger = BrandPalette.LightDanger.toColor(),
    dangerContainer = BrandPalette.LightDangerContainer.toColor(),
    onDangerContainer = BrandPalette.LightOnDangerContainer.toColor(),
    info = BrandPalette.LightInfo.toColor(),
    infoContainer = BrandPalette.LightInfoContainer.toColor(),
    onInfoContainer = BrandPalette.LightOnInfoContainer.toColor(),
    neutral = BrandPalette.LightNeutral.toColor(),
    neutralContainer = BrandPalette.LightNeutralContainer.toColor(),
    onNeutralContainer = BrandPalette.LightOnNeutralContainer.toColor(),
)

/** The status tones of the enclosing [ZeroHourTheme], so no component re-derives them. */
internal val LocalStatusColors = staticCompositionLocalOf { ZeroHourDarkStatusColors }
