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

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// A muted military palette: olive and khaki surfaces and a brass accent for actions. The
// compatibility verdict has its own per-scheme tones further down.
private val Brass = Color(0xFFC8A24A)
private val BrassLight = Color(0xFFE6C77A)
private val Olive = Color(0xFF4A5D3A)
private val OliveDeep = Color(0xFF232B1C)
private val Khaki = Color(0xFFB8AE93)

private val Failure = Color(0xFFC4553F)

internal val ZeroHourDarkColors = darkColorScheme(
    primary = BrassLight,
    onPrimary = Color(0xFF2A2110),
    primaryContainer = Olive,
    onPrimaryContainer = Khaki,
    secondary = Khaki,
    onSecondary = OliveDeep,
    secondaryContainer = Color(0xFF333C28),
    onSecondaryContainer = Khaki,
    background = Color(0xFF15180F),
    onBackground = Color(0xFFE4DFD0),
    surface = Color(0xFF1D2116),
    onSurface = Color(0xFFE4DFD0),
    surfaceVariant = Color(0xFF2A3020),
    onSurfaceVariant = Color(0xFFC4BDA9),
    outline = Color(0xFF6B6A56),
    error = Failure,
    onError = Color(0xFF2A100C),
)

internal val ZeroHourLightColors = lightColorScheme(
    primary = Olive,
    onPrimary = Color.White,
    primaryContainer = BrassLight,
    onPrimaryContainer = Color(0xFF2A2110),
    secondary = Color(0xFF5C5A46),
    onSecondary = Color.White,
    secondaryContainer = Khaki,
    onSecondaryContainer = OliveDeep,
    background = Color(0xFFF4F1E6),
    onBackground = Color(0xFF1F2216),
    surface = Color(0xFFFAF8F0),
    onSurface = Color(0xFF1F2216),
    surfaceVariant = Color(0xFFE2DCCB),
    onSurfaceVariant = Color(0xFF4A4C3C),
    outline = Color(0xFF7B7A66),
    error = Failure,
    onError = Color.White,
)

/**
 * The colours of a compatibility verdict, one set per scheme.
 *
 * <p>A verdict is a semantic state, not a themed accent, so these are not derived from the primary
 * colour. They do have to differ per scheme: the verdict is drawn as a glyph and as a tinted
 * container, and a single set of tones cannot reach the 4.5:1 contrast minimum against both a near
 * black and a near white surface. Each value below meets it against its own scheme's surface.
 */
internal data class VerdictColors(
    val satisfied: Color,
    val failed: Color,
    val unverified: Color,
    val unknown: Color,
)

internal val ZeroHourDarkVerdicts = VerdictColors(
    satisfied = Color(0xFF9BD97A),
    failed = Color(0xFFEC8E78),
    unverified = Color(0xFFF0C46E),
    unknown = Color(0xFFB5AFA3),
)

internal val ZeroHourLightVerdicts = VerdictColors(
    satisfied = Color(0xFF3E6B22),
    failed = Color(0xFF9C2F1C),
    unverified = Color(0xFF8A5A0E),
    unknown = Color(0xFF5E5B4E),
)

/** The verdict colours of the enclosing [ZeroHourTheme], so the screens never re-derive them. */
internal val LocalVerdictColors = staticCompositionLocalOf { ZeroHourDarkVerdicts }
