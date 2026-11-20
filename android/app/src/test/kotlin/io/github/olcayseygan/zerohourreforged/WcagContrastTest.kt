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

package io.github.olcayseygan.zerohourreforged

import io.github.olcayseygan.zerohourreforged.ui.theme.BrandContrast
import io.github.olcayseygan.zerohourreforged.ui.theme.BrandPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts the shipped palette against WCAG 2.1, on the host JVM.
 *
 * <p>The design system's claim is that every text pair clears 4.5:1 and every boundary clears 3:1 in
 * both schemes. A claim in a comment decays; this test does not. It reads the same
 * [BrandPalette] constants and the same [BrandContrast] maths the theme uses at runtime, so a
 * colour edited in the palette fails here rather than shipping unreadable.
 *
 * <p>Pairs are listed by role rather than by literal hex, so a failure names the pair that broke.
 */
class WcagContrastTest {

    private fun assertTextPair(name: String, foreground: Long, background: Long) {
        val ratio = BrandContrast.contrastRatio(foreground, background)
        assertTrue(
            "$name is %.2f:1, below the 4.5:1 text minimum".format(ratio),
            ratio >= BrandContrast.MIN_TEXT_CONTRAST,
        )
    }

    private fun assertBoundaryPair(name: String, foreground: Long, background: Long) {
        val ratio = BrandContrast.contrastRatio(foreground, background)
        assertTrue(
            "$name is %.2f:1, below the 3:1 non-text minimum".format(ratio),
            ratio >= BrandContrast.MIN_NON_TEXT_CONTRAST,
        )
    }

    @Test
    fun darkSchemeTextClearsWcagAa() {
        assertTextPair("onBackground/background", BrandPalette.DarkOnBackground, BrandPalette.DarkBackground)
        assertTextPair("onSurface/surface", BrandPalette.DarkOnSurface, BrandPalette.DarkSurface)
        assertTextPair(
            "onSurfaceVariant/surfaceVariant",
            BrandPalette.DarkOnSurfaceVariant,
            BrandPalette.DarkSurfaceVariant,
        )
        assertTextPair("primary/background", BrandPalette.DarkPrimary, BrandPalette.DarkBackground)
        assertTextPair("primary/surface", BrandPalette.DarkPrimary, BrandPalette.DarkSurface)
        assertTextPair("onPrimary/primary", BrandPalette.DarkOnPrimary, BrandPalette.DarkPrimary)
        assertTextPair(
            "onPrimaryContainer/primaryContainer",
            BrandPalette.DarkOnPrimaryContainer,
            BrandPalette.DarkPrimaryContainer,
        )
        assertTextPair("onSecondary/secondary", BrandPalette.DarkOnSecondary, BrandPalette.DarkSecondary)
        assertTextPair("error/surface", BrandPalette.DarkError, BrandPalette.DarkSurface)
        assertTextPair("onError/error", BrandPalette.DarkOnError, BrandPalette.DarkError)
    }

    @Test
    fun lightSchemeTextClearsWcagAa() {
        assertTextPair(
            "onBackground/background",
            BrandPalette.LightOnBackground,
            BrandPalette.LightBackground,
        )
        assertTextPair("onSurface/surface", BrandPalette.LightOnSurface, BrandPalette.LightSurface)
        assertTextPair(
            "onSurfaceVariant/surfaceVariant",
            BrandPalette.LightOnSurfaceVariant,
            BrandPalette.LightSurfaceVariant,
        )
        assertTextPair("primary/background", BrandPalette.LightPrimary, BrandPalette.LightBackground)
        assertTextPair("primary/surface", BrandPalette.LightPrimary, BrandPalette.LightSurface)
        assertTextPair("onPrimary/primary", BrandPalette.LightOnPrimary, BrandPalette.LightPrimary)
        assertTextPair(
            "onPrimaryContainer/primaryContainer",
            BrandPalette.LightOnPrimaryContainer,
            BrandPalette.LightPrimaryContainer,
        )
        assertTextPair(
            "onSecondary/secondary",
            BrandPalette.LightOnSecondary,
            BrandPalette.LightSecondary,
        )
        assertTextPair("error/surface", BrandPalette.LightError, BrandPalette.LightSurface)
        assertTextPair("onError/error", BrandPalette.LightOnError, BrandPalette.LightError)
    }

    /**
     * The text a status card or chip draws on its own fill.
     *
     * <p>These are the pairs the components rely on: [io.github.olcayseygan.zerohourreforged.ui.components.StatusCard]
     * draws `onContainer` on `container`, so a tone whose pair failed would be unreadable inside a
     * card even though the scheme's own roles were fine.
     */
    @Test
    fun darkStatusTonesAreReadableOnTheirContainers() {
        assertTextPair(
            "success",
            BrandPalette.DarkOnSuccessContainer,
            BrandPalette.DarkSuccessContainer,
        )
        assertTextPair(
            "warning",
            BrandPalette.DarkOnWarningContainer,
            BrandPalette.DarkWarningContainer,
        )
        assertTextPair(
            "danger",
            BrandPalette.DarkOnDangerContainer,
            BrandPalette.DarkDangerContainer,
        )
        assertTextPair("info", BrandPalette.DarkOnInfoContainer, BrandPalette.DarkInfoContainer)
        assertTextPair(
            "neutral",
            BrandPalette.DarkOnNeutralContainer,
            BrandPalette.DarkNeutralContainer,
        )
    }

    @Test
    fun lightStatusTonesAreReadableOnTheirContainers() {
        assertTextPair(
            "success",
            BrandPalette.LightOnSuccessContainer,
            BrandPalette.LightSuccessContainer,
        )
        assertTextPair(
            "warning",
            BrandPalette.LightOnWarningContainer,
            BrandPalette.LightWarningContainer,
        )
        assertTextPair(
            "danger",
            BrandPalette.LightOnDangerContainer,
            BrandPalette.LightDangerContainer,
        )
        assertTextPair("info", BrandPalette.LightOnInfoContainer, BrandPalette.LightInfoContainer)
        assertTextPair(
            "neutral",
            BrandPalette.LightOnNeutralContainer,
            BrandPalette.LightNeutralContainer,
        )
    }

    /**
     * The status glyph on the screen background.
     *
     * <p>A status icon is a non-text element, so it needs 3:1, not 4.5:1. It is checked separately
     * because [io.github.olcayseygan.zerohourreforged.ui.components.StatusIcon] tints itself with the
     * tone's `content` colour and can appear outside a card.
     */
    @Test
    fun statusGlyphsClearTheNonTextMinimum() {
        assertBoundaryPair("success/dark", BrandPalette.DarkSuccess, BrandPalette.DarkBackground)
        assertBoundaryPair("warning/dark", BrandPalette.DarkWarning, BrandPalette.DarkBackground)
        assertBoundaryPair("danger/dark", BrandPalette.DarkDanger, BrandPalette.DarkBackground)
        assertBoundaryPair("info/dark", BrandPalette.DarkInfo, BrandPalette.DarkBackground)
        assertBoundaryPair("neutral/dark", BrandPalette.DarkNeutral, BrandPalette.DarkBackground)

        assertBoundaryPair("success/light", BrandPalette.LightSuccess, BrandPalette.LightBackground)
        assertBoundaryPair("warning/light", BrandPalette.LightWarning, BrandPalette.LightBackground)
        assertBoundaryPair("danger/light", BrandPalette.LightDanger, BrandPalette.LightBackground)
        assertBoundaryPair("info/light", BrandPalette.LightInfo, BrandPalette.LightBackground)
        assertBoundaryPair("neutral/light", BrandPalette.LightNeutral, BrandPalette.LightBackground)
    }

    /**
     * The glyph inside a status card, drawn on the card's own fill.
     *
     * <p>This is the composition the components actually produce: [StatusCard] paints `container`
     * and tints the glyph with `content`, so that pair - not the glyph against the screen
     * background - is what a player sees.
     */
    @Test
    fun statusGlyphsAreVisibleOnTheirContainers() {
        assertBoundaryPair("success/dark", BrandPalette.DarkSuccess, BrandPalette.DarkSuccessContainer)
        assertBoundaryPair("warning/dark", BrandPalette.DarkWarning, BrandPalette.DarkWarningContainer)
        assertBoundaryPair("danger/dark", BrandPalette.DarkDanger, BrandPalette.DarkDangerContainer)
        assertBoundaryPair("info/dark", BrandPalette.DarkInfo, BrandPalette.DarkInfoContainer)
        assertBoundaryPair("neutral/dark", BrandPalette.DarkNeutral, BrandPalette.DarkNeutralContainer)

        assertBoundaryPair("success/light", BrandPalette.LightSuccess, BrandPalette.LightSuccessContainer)
        assertBoundaryPair("warning/light", BrandPalette.LightWarning, BrandPalette.LightWarningContainer)
        assertBoundaryPair("danger/light", BrandPalette.LightDanger, BrandPalette.LightDangerContainer)
        assertBoundaryPair("info/light", BrandPalette.LightInfo, BrandPalette.LightInfoContainer)
        assertBoundaryPair("neutral/light", BrandPalette.LightNeutral, BrandPalette.LightNeutralContainer)
    }

    /**
     * The pairs the components compose, as opposed to the scheme's own role pairs.
     *
     * <p>A component draws a role on a surface the scheme defines separately - the section heading is
     * `primary` on `surfaceContainerLow`, an error dialog's title is `error` on
     * `surfaceContainerHigh` - and those combinations are what a player actually reads. The role
     * pairs above cannot catch a bad one, because each half is individually correct.
     */
    @Test
    fun componentCompositionsClearWcagAa() {
        assertTextPair("heading/dark", BrandPalette.DarkPrimary, BrandPalette.DarkSurfaceContainerLow)
        assertTextPair("heading/light", BrandPalette.LightPrimary, BrandPalette.LightSurfaceContainerLow)
        assertTextPair("appbar/dark", BrandPalette.DarkPrimary, BrandPalette.DarkSurface)
        assertTextPair("appbar/light", BrandPalette.LightPrimary, BrandPalette.LightSurface)

        assertTextPair("body/dark", BrandPalette.DarkOnSurfaceVariant, BrandPalette.DarkBackground)
        assertTextPair("body/light", BrandPalette.LightOnSurfaceVariant, BrandPalette.LightBackground)
        assertTextPair(
            "detail/dark",
            BrandPalette.DarkOnSurfaceVariant,
            BrandPalette.DarkSurfaceContainerLowest,
        )
        assertTextPair(
            "detail/light",
            BrandPalette.LightOnSurfaceVariant,
            BrandPalette.LightSurfaceContainerLowest,
        )

        // The error dialog is the one surface a failure is read on, so both its lines are checked.
        assertTextPair("dialogTitle/dark", BrandPalette.DarkError, BrandPalette.DarkSurfaceContainerHigh)
        assertTextPair(
            "dialogTitle/light",
            BrandPalette.LightError,
            BrandPalette.LightSurfaceContainerHigh,
        )
        assertTextPair(
            "dialogBody/dark",
            BrandPalette.DarkOnSurfaceVariant,
            BrandPalette.DarkSurfaceContainerHigh,
        )
        assertTextPair(
            "dialogBody/light",
            BrandPalette.LightOnSurfaceVariant,
            BrandPalette.LightSurfaceContainerHigh,
        )

        // The launcher's identity header: the title sits on the page background, not on a surface.
        assertTextPair("header/dark", BrandPalette.DarkOnBackground, BrandPalette.DarkBackground)
        assertTextPair("header/light", BrandPalette.LightOnBackground, BrandPalette.LightBackground)
        // Its subtitle is the quiet caption beneath the title.
        assertTextPair("subtitle/dark", BrandPalette.DarkOnSurfaceVariant, BrandPalette.DarkBackground)
        assertTextPair("subtitle/light", BrandPalette.LightOnSurfaceVariant, BrandPalette.LightBackground)

        // The confirmation dialog: its title is a statement (onSurface), and the confirming button
        // carries the destructive tint (error) on the same container.
        assertTextPair("confirmTitle/dark", BrandPalette.DarkOnSurface, BrandPalette.DarkSurfaceContainerHigh)
        assertTextPair("confirmTitle/light", BrandPalette.LightOnSurface, BrandPalette.LightSurfaceContainerHigh)
        assertTextPair("confirmAction/dark", BrandPalette.DarkError, BrandPalette.DarkSurfaceContainerHigh)
        assertTextPair("confirmAction/light", BrandPalette.LightError, BrandPalette.LightSurfaceContainerHigh)
    }

    /**
     * The divider that separates the installation rows from the copy note must be visible.
     *
     * <p>It uses `outline`, not `outlineVariant`: `outlineVariant` is Material 3's *decorative*
     * hairline and measures about 1.7:1 here, which is too faint for the one line that tells the
     * player the note below is a different kind of statement. A divider is not text, so it is held to
     * the 3:1 non-text minimum - the same rule as an outline.
     */
    @Test
    fun installationDividerIsVisible() {
        assertBoundaryPair(
            "divider/dark",
            BrandPalette.DarkOutline,
            BrandPalette.DarkSurfaceContainerLow,
        )
        assertBoundaryPair(
            "divider/light",
            BrandPalette.LightOutline,
            BrandPalette.LightSurfaceContainerLow,
        )
    }

    /** Outlines and dividers must be visible against the surfaces they separate. */
    @Test
    fun outlinesClearTheNonTextMinimum() {
        assertBoundaryPair("outline/background/dark", BrandPalette.DarkOutline, BrandPalette.DarkBackground)
        assertBoundaryPair("outline/surface/dark", BrandPalette.DarkOutline, BrandPalette.DarkSurface)
        assertBoundaryPair("outline/background/light", BrandPalette.LightOutline, BrandPalette.LightBackground)
        assertBoundaryPair("outline/surface/light", BrandPalette.LightOutline, BrandPalette.LightSurface)
    }

    /** Contrast is symmetric: the ratio cannot depend on argument order. */
    @Test
    fun contrastRatioIsSymmetricAndBounded() {
        val a = BrandPalette.DarkOnSurface
        val b = BrandPalette.DarkSurface
        assertTrue(BrandContrast.contrastRatio(a, b) == BrandContrast.contrastRatio(b, a))
        assertTrue(BrandContrast.contrastRatio(a, a) == 1.0)
        assertTrue(BrandContrast.contrastRatio(0xFFFFFFFFL, 0xFF000000L) > 20.0)
    }

    /** A light surface must actually be lighter than a dark one, or the two schemes are swapped. */
    @Test
    fun schemesAreOrderedByLuminance() {
        assertTrue(
            BrandContrast.relativeLuminance(BrandPalette.LightSurface) >
                BrandContrast.relativeLuminance(BrandPalette.DarkSurface),
        )
    }
}
