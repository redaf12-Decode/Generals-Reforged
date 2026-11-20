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
 * WCAG 2.1 relative luminance and contrast ratio, over plain ARGB values.
 *
 * <p>This is production code, not test scaffolding: the dynamic-colour path uses [bestAccent] to
 * choose a brand accent that still clears 4.5:1 against whatever surface the wallpaper produced,
 * because a dynamic scheme's neutrals are not the ones this palette was tuned against. The same
 * functions let `WcagContrastTest` assert the shipped palette on the host JVM, so the numbers the
 * test checks are the numbers the application uses.
 *
 * <p>Everything is a plain [Long]/[Double] so nothing here needs an Android or Compose class.
 */
internal object BrandContrast {

    /** The minimum ratio WCAG 2.1 SC 1.4.3 requires for normal-size text. */
    const val MIN_TEXT_CONTRAST = 4.5

    /** The minimum ratio SC 1.4.11 requires for a non-text boundary such as a focus ring. */
    const val MIN_NON_TEXT_CONTRAST = 3.0

    /** Relative luminance of an ARGB colour, per WCAG 2.1's definition. */
    fun relativeLuminance(argb: Long): Double {
        val r = linearize(((argb shr 16) and 0xFF) / 255.0)
        val g = linearize(((argb shr 8) and 0xFF) / 255.0)
        val b = linearize((argb and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Contrast ratio between two opaque ARGB colours, from 1.0 to 21.0. */
    fun contrastRatio(foreground: Long, background: Long): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /**
     * Of two candidate accents, the one with more contrast against [background].
     *
     * <p>Used for dynamic colour: the same brand accent cannot serve a wallpaper-derived surface in
     * both a light and a dark scheme, so the theme keeps both brand tones and asks for whichever
     * reads on the surface it actually got.
     */
    fun bestAccent(darkSchemeAccent: Long, lightSchemeAccent: Long, background: Long): Long =
        if (contrastRatio(darkSchemeAccent, background) >= contrastRatio(lightSchemeAccent, background)) {
            darkSchemeAccent
        } else {
            lightSchemeAccent
        }

    /** Whether [foreground] on [background] meets [MIN_TEXT_CONTRAST]. */
    fun meetsTextContrast(foreground: Long, background: Long): Boolean =
        contrastRatio(foreground, background) >= MIN_TEXT_CONTRAST

    private fun linearize(channel: Double): Double =
        if (channel <= 0.03928) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
}
