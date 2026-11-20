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

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The Material 3 type scale, on the platform's own font family.
 *
 * <p>No font file is bundled. The device's system font is what the surrounding Android UI uses and
 * the only family guaranteed to cover every script the player's locale needs; shipping a display
 * face for three short screens would add megabytes and a licence for no legibility gain. The
 * identity comes from weight and colour, not from a typeface.
 *
 * <p>Every size is in `sp`, so the scale follows the user's font-size setting up to the 200% the
 * accessibility setting allows. Line heights are generous (about 1.4x the size for body text) and no
 * component in this design sets a text container's height - only its padding - so a scaled-up font
 * grows its container instead of being clipped.
 *
 * <p>[PlatformTextStyle.includeFontPadding] is off so a line box is exactly its line height. With it
 * on, Android adds a few pixels above and below every line, enough at large font scales to make two
 * otherwise identical cards different heights.
 */
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private val EvenLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun scale(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    platformStyle = NoFontPadding,
    lineHeightStyle = EvenLineHeight,
)

internal val ZeroHourTypography = Typography(
    displaySmall = scale(FontWeight.Bold, 34, 44, 0.0),
    headlineLarge = scale(FontWeight.Bold, 30, 38, 0.0),
    headlineMedium = scale(FontWeight.Bold, 26, 34, 0.1),
    headlineSmall = scale(FontWeight.SemiBold, 22, 30, 0.0),
    titleLarge = scale(FontWeight.SemiBold, 20, 28, 0.1),
    titleMedium = scale(FontWeight.SemiBold, 16, 24, 0.15),
    titleSmall = scale(FontWeight.Medium, 14, 20, 0.1),
    bodyLarge = scale(FontWeight.Normal, 16, 24, 0.4),
    bodyMedium = scale(FontWeight.Normal, 14, 20, 0.25),
    bodySmall = scale(FontWeight.Normal, 12, 18, 0.4),
    labelLarge = scale(FontWeight.Medium, 14, 20, 0.1),
    labelMedium = scale(FontWeight.Medium, 12, 16, 0.5),
    labelSmall = scale(FontWeight.Medium, 11, 16, 0.5),
)
