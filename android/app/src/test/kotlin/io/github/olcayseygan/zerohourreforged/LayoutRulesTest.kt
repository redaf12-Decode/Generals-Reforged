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

import io.github.olcayseygan.zerohourreforged.ui.theme.LayoutRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The responsive decisions, checked at the boundaries.
 *
 * <p>Every rule here is arithmetic on the space a layout is given, so it can be checked without a
 * device - which matters because a breakpoint is wrong exactly at its boundary, and a boundary is
 * the one case a screenshot never shows. The form factors below are the ones the requirement names:
 * a small phone, a standard phone, a large phone, a landscape phone and a tablet.
 */
class LayoutRulesTest {

    /** The width a section's content column has, given a window width. */
    private fun contentWidth(windowDp: Float): Float =
        windowDp - 2 * SCREEN_EDGE_DP - 2 * SECTION_PADDING_DP

    private fun stacks(windowDp: Float, fontScale: Float): Boolean =
        LayoutRules.stackSegmentControls(contentWidth(windowDp), fontScale)

    @Test
    fun aPhoneAtTheDefaultFontScaleKeepsTheSegmentedRow() {
        // 320dp is the narrowest phone still in use, and the requirement names small phones
        // explicitly. At 1.0x the three labels fit wrapped, so the segmented control survives.
        assertFalse(stacks(320f, 1.0f))
        assertFalse(stacks(360f, 1.0f))
        assertFalse(stacks(412f, 1.0f))
    }

    @Test
    fun aPhoneStacksOnceTheFontGrowsPastTheWrappingLimit() {
        // The old layout wrapped to two lines and clipped them against the fixed 40dp height. Above
        // 1.25x a phone's column cannot hold three single-line labels either, so the answer there is
        // the stacked list - the case that used to clip.
        assertTrue(stacks(320f, 1.3f))
        assertTrue(stacks(360f, 1.3f))
        assertTrue(stacks(412f, 1.3f))
        assertTrue(stacks(360f, 2.0f))
    }

    @Test
    fun aWideWindowKeepsTheSegmentedRowWhileSingleLineLabelsFit() {
        // Not every large font scale forces the stacked list: a tablet at 1.3x still has room for
        // three single-line labels, so the segmented control - the better control when it fits -
        // survives. At 2.0x it no longer does, which is where the width runs out.
        assertFalse(stacks(600f, 1.0f))
        assertFalse(stacks(600f, 1.3f))
        assertFalse(stacks(800f, 1.3f))
        assertTrue(stacks(600f, 2.0f))
    }

    @Test
    fun theFontScaleBoundaryIsWhereItIsDocumented() {
        // Exactly at the limit the label may still wrap, so a phone keeps the row; a hair over it,
        // wrapping is no longer allowed and the single-line width does not fit. Asserting both sides
        // is the point - a `<=` written as `<` would pass a one-sided test.
        assertFalse(stacks(360f, LayoutRules.MAX_WRAPPED_FONT_SCALE))
        assertTrue(stacks(360f, LayoutRules.MAX_WRAPPED_FONT_SCALE + 0.01f))
    }

    @Test
    fun aFontScaleBelowOneIsTreatedAsOne() {
        // A device can report a scale below 1.0, but nothing shrinks below the design size; treating
        // 0.85 as a reason to keep a row that 1.0 would stack would be the wrong way round.
        assertEquals(stacks(360f, 1.0f), stacks(360f, 0.85f))
    }

    @Test
    fun shortWindowsAreCompact() {
        // A landscape phone: 360dp of height before the bars. 28dp of breathing room twice is a
        // tenth of the visible area there, so it drops to the compact padding.
        assertTrue(LayoutRules.isCompactHeight(360f))
        assertFalse(LayoutRules.isCompactHeight(900f))
    }

    @Test
    fun thePaddingBoundaryFavoursTheComfortableValue() {
        // At exactly the threshold the window is not "compact": the comparison is strict, so a
        // window of 480dp keeps the roomier padding rather than losing it to a rounding case.
        assertFalse(LayoutRules.isCompactHeight(LayoutRules.COMPACT_HEIGHT_DP))
        assertTrue(LayoutRules.isCompactHeight(LayoutRules.COMPACT_HEIGHT_DP - 1f))
    }

    @Test
    fun progressAnnouncementsAreStepped() {
        // The copy reports once per file, so the announced value must not move on every report.
        assertEquals(0, LayoutRules.announcedPercent(0))
        assertEquals(0, LayoutRules.announcedPercent(9))
        assertEquals(10, LayoutRules.announcedPercent(10))
        assertEquals(10, LayoutRules.announcedPercent(19))
        assertEquals(50, LayoutRules.announcedPercent(59))
        assertEquals(100, LayoutRules.announcedPercent(100))
    }

    @Test
    fun progressAnnouncementsClampAnOvershoot() {
        // A reporter can briefly exceed the total it announced; the announcement must not read
        // "110% complete" and must not throw.
        assertEquals(100, LayoutRules.announcedPercent(110))
        assertEquals(0, LayoutRules.announcedPercent(-5))
    }

    @Test
    fun theAnnouncementStepMatchesTheSpeechItProduces() {
        // Ten announcements over a whole copy is the intended cadence, so the step and the range
        // have to agree. Written as an assertion rather than a comment because changing the step to
        // 5 would silently double the interruptions.
        assertEquals(100 / LayoutRules.PROGRESS_ANNOUNCEMENT_STEP, 10)
    }

    private companion object {
        /** Mirrors `Spacing.screenEdge`; the rule is given the content width, so the test subtracts. */
        const val SCREEN_EDGE_DP = 20f

        /** Mirrors the `SectionContainer` padding. */
        const val SECTION_PADDING_DP = 16f
    }
}
