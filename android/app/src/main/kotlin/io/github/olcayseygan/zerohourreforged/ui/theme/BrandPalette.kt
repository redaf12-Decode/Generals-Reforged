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

/**
 * The brand palette, as ARGB values.
 *
 * <p>Deliberately plain [Long]s rather than Compose [androidx.compose.ui.graphics.Color]s: this is
 * the single source of truth for every colour the application draws, and keeping it free of Compose
 * types lets `WcagContrastTest` assert the contrast of the exact values that ship, on the host JVM,
 * with no Android or Compose class loading involved.
 *
 * <p>Two coherent schemes, not one palette used twice. A tone that clears the 4.5:1 text minimum
 * against a near-black surface cannot clear it against a near-white one, so every role that carries
 * text is tuned per scheme. `WcagContrastTest` pins the ratios.
 *
 * <p>The identity: deep graphite for the dark scheme, warm grey paper for the light one, a muted
 * military green as the secondary accent, warm metallic gold as the primary, and a warm off-white
 * (or near-black) for text. Gold is used for headings and the primary action, green for supporting
 * structure, never the reverse.
 */
internal object BrandPalette {

    // ---- Dark scheme: deep graphite, gold accent -------------------------------------------
    const val DarkBackground = 0xFF101215L
    const val DarkOnBackground = 0xFFEDEAE2L
    const val DarkSurface = 0xFF16181CL
    const val DarkOnSurface = 0xFFEDEAE2L
    const val DarkSurfaceDim = 0xFF101215L
    const val DarkSurfaceBright = 0xFF33383EL
    const val DarkSurfaceContainerLowest = 0xFF0B0D10L
    const val DarkSurfaceContainerLow = 0xFF1C1F24L
    const val DarkSurfaceContainer = 0xFF212429L
    const val DarkSurfaceContainerHigh = 0xFF272B31L
    const val DarkSurfaceContainerHighest = 0xFF2E3238L
    const val DarkSurfaceVariant = 0xFF31353BL
    const val DarkOnSurfaceVariant = 0xFFC6C0B1L
    const val DarkOutline = 0xFF8F8C81L
    const val DarkOutlineVariant = 0xFF3F454CL

    const val DarkPrimary = 0xFFE8C87CL
    const val DarkOnPrimary = 0xFF241B05L
    const val DarkPrimaryContainer = 0xFF4F3E14L
    const val DarkOnPrimaryContainer = 0xFFF6E4BAL
    const val DarkInversePrimary = 0xFF6E5210L
    const val DarkSecondary = 0xFFB5C6A4L
    const val DarkOnSecondary = 0xFF1E2A15L
    const val DarkSecondaryContainer = 0xFF36432CL
    const val DarkOnSecondaryContainer = 0xFFD7E2CBL
    const val DarkTertiary = 0xFFD6C7A7L
    const val DarkOnTertiary = 0xFF2A2312L
    const val DarkTertiaryContainer = 0xFF463D29L
    const val DarkOnTertiaryContainer = 0xFFEEE3CAL

    const val DarkError = 0xFFF0A18DL
    const val DarkOnError = 0xFF3C0F06L
    const val DarkErrorContainer = 0xFF5E2418L
    const val DarkOnErrorContainer = 0xFFFFDAD1L
    const val DarkInverseSurface = 0xFFEDEAE2L
    const val DarkInverseOnSurface = 0xFF23262BL

    // ---- Light scheme: warm paper, deep gold accent ----------------------------------------
    const val LightBackground = 0xFFF4F1E9L
    const val LightOnBackground = 0xFF191C1FL
    const val LightSurface = 0xFFFBF9F4L
    const val LightOnSurface = 0xFF191C1FL
    const val LightSurfaceDim = 0xFFDCD8CDL
    const val LightSurfaceBright = 0xFFFBF9F4L
    const val LightSurfaceContainerLowest = 0xFFFFFFFFL
    const val LightSurfaceContainerLow = 0xFFF7F4ECL
    const val LightSurfaceContainer = 0xFFF1EEE5L
    const val LightSurfaceContainerHigh = 0xFFEBE8DEL
    const val LightSurfaceContainerHighest = 0xFFE4E1D6L
    const val LightSurfaceVariant = 0xFFE2DDD0L
    const val LightOnSurfaceVariant = 0xFF494A41L
    const val LightOutline = 0xFF6E6C62L
    const val LightOutlineVariant = 0xFFC7C2B4L

    const val LightPrimary = 0xFF6E5210L
    const val LightOnPrimary = 0xFFFFFFFFL
    const val LightPrimaryContainer = 0xFFF2E0B2L
    const val LightOnPrimaryContainer = 0xFF2A2110L
    const val LightInversePrimary = 0xFFE8C87CL
    const val LightSecondary = 0xFF41552FL
    const val LightOnSecondary = 0xFFFFFFFFL
    const val LightSecondaryContainer = 0xFFD9E5C9L
    const val LightOnSecondaryContainer = 0xFF1D2A13L
    const val LightTertiary = 0xFF5A4A2AL
    const val LightOnTertiary = 0xFFFFFFFFL
    const val LightTertiaryContainer = 0xFFEDE2C9L
    const val LightOnTertiaryContainer = 0xFF2A2312L

    const val LightError = 0xFFA0361FL
    const val LightOnError = 0xFFFFFFFFL
    const val LightErrorContainer = 0xFFFBDDD5L
    const val LightOnErrorContainer = 0xFF3C0F06L
    const val LightInverseSurface = 0xFF2E3238L
    const val LightInverseOnSurface = 0xFFF3F0E7L

    // ---- Semantic status tones, dark scheme ------------------------------------------------
    const val DarkSuccess = 0xFFA9D98DL
    const val DarkSuccessContainer = 0xFF2A3D20L
    const val DarkOnSuccessContainer = 0xFFCFE9BAL
    const val DarkWarning = 0xFFEDC77EL
    const val DarkWarningContainer = 0xFF4B3B13L
    const val DarkOnWarningContainer = 0xFFF8E5B8L
    const val DarkDanger = 0xFFF2A48FL
    const val DarkDangerContainer = 0xFF4C2017L
    const val DarkOnDangerContainer = 0xFFFFD9CFL
    const val DarkInfo = 0xFFA8C6DEL
    const val DarkInfoContainer = 0xFF22333FL
    const val DarkOnInfoContainer = 0xFFD2E4F1L
    const val DarkNeutral = 0xFFC3BDAEL
    const val DarkNeutralContainer = 0xFF2E3238L
    const val DarkOnNeutralContainer = 0xFFE5E1D7L

    // ---- Semantic status tones, light scheme -----------------------------------------------
    const val LightSuccess = 0xFF35651FL
    const val LightSuccessContainer = 0xFFDDEBCEL
    const val LightOnSuccessContainer = 0xFF1B3A10L
    const val LightWarning = 0xFF7A5410L
    const val LightWarningContainer = 0xFFF7E6BFL
    const val LightOnWarningContainer = 0xFF3A2A05L
    const val LightDanger = 0xFF9C2F1CL
    const val LightDangerContainer = 0xFFFBDDD5L
    const val LightOnDangerContainer = 0xFF3C0F06L
    const val LightInfo = 0xFF28546FL
    const val LightInfoContainer = 0xFFD8E7F2L
    const val LightOnInfoContainer = 0xFF0F2836L
    const val LightNeutral = 0xFF5A574CL
    const val LightNeutralContainer = 0xFFE2DDD0L
    const val LightOnNeutralContainer = 0xFF2E2C25L
}
