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
 * The decisions a layout makes from the space it is given, as arithmetic rather than as Compose.
 *
 * <p>Deliberately free of `androidx.compose` types: every function takes and returns plain numbers
 * (dp as `Float`, a font scale as `Float`), so the rules can be tested on the JVM. A breakpoint is
 * the kind of thing that is wrong exactly at its boundary, and a boundary is the one case a
 * screenshot of a real device never shows you.
 *
 * <p>Widths are compared against the *content* width - the space left after the screen edge and the
 * section padding - because that is what a control actually has to fit into. A rule written against
 * the display width would pass on a 360dp phone and overflow inside a card.
 */
internal object LayoutRules {

    /**
     * The widest a reading column gets on a small screen, in dp.
     *
     * <p>Measured in characters, not in aesthetics: at the body size this application uses, roughly
     * 60-70 characters fit in 560dp, which is the range long-form text stays readable in. Wider than
     * that and the eye loses the line on the way back.
     */
    const val READABLE_WIDTH_DP = 560f

    /**
     * The same cap for the compatibility screen, which is wider because its content is a table of
     * technical facts rather than prose - a Vulkan extension list wraps badly at 560dp.
     */
    const val WIDE_READABLE_WIDTH_DP = 640f

    /**
     * Below this window height [isCompactHeight] asks for the compact vertical padding.
     *
     * <p>480dp is a landscape phone: a 360x800 phone turned on its side has about 360dp of height
     * before the system bars. The 28dp of breathing room that reads as generous in portrait is 8% of
     * the visible area there, and it is spent twice - top and bottom - on a screen that has to
     * scroll anyway.
     */
    const val COMPACT_HEIGHT_DP = 480f

    /**
     * The horizontal padding a segment spends on its own inset, in dp.
     *
     * <p>Material 3's segmented button insets its label by 12dp on each side, and that inset does not
     * scale with the font.
     */
    const val SEGMENT_PADDING_DP = 24f

    /**
     * The width of the longest *word* in the control's labels, in dp at the default font scale.
     *
     * <p>"Follow system" wraps between its two words, so what has to fit on one line is "system" -
     * about 6 characters at the 12sp label size, so roughly 45dp.
     */
    const val LONGEST_WORD_WIDTH_DP = 45f

    /**
     * The width of the longest label on a single line, in dp at the default font scale.
     *
     * <p>"Follow system", 13 characters at the 12sp label size, is roughly 85dp.
     */
    const val LONGEST_LABEL_WIDTH_DP = 85f

    /**
     * The largest font scale at which a two-line segment label still fits the control's height.
     *
     * <p>A segmented button is 40dp tall in Material 3 and its height is not negotiable. Two lines
     * of the 12sp label are about 32dp at the default scale and grow with the scale, so they clear
     * 40dp with a little room up to 1.25x and stop clearing it above that. This is the boundary the
     * old layout ignored: it wrapped to two lines and clipped the second one once the player turned
     * the font up.
     */
    const val MAX_WRAPPED_FONT_SCALE = 1.25f

    /** How many segments the appearance control has: follow the system, light, dark. */
    const val SEGMENT_COUNT = 3

    /**
     * Whether a segmented control has to fall back to a stacked list.
     *
     * <p>Segmented buttons share their row's width equally and have a fixed 40dp height, so they
     * degrade in two ways: too narrow and the label is clipped horizontally, too large a font and
     * even a wrapped label no longer fits the fixed height. Wrapping buys room in the first case but
     * not the second, so the width a segment needs depends on whether wrapping is available - and
     * the text itself grows with the font scale in both cases, which is why the scale multiplies the
     * glyph width rather than the whole segment.
     *
     * <p>At 1.3x and above the answer is "stack" on a phone whatever the width, because a phone's
     * content column cannot hold three single-line labels at that size. That is exactly the case the
     * old always-segmented layout clipped.
     *
     * @param contentWidthDp the width the control has to fit into, after screen and card padding.
     * @param fontScale the device's font scale, where 1.0 is the default setting.
     */
    fun stackSegmentControls(contentWidthDp: Float, fontScale: Float): Boolean {
        val scale = fontScale.coerceAtLeast(1f)
        val glyphs = if (scale <= MAX_WRAPPED_FONT_SCALE) {
            LONGEST_WORD_WIDTH_DP
        } else {
            LONGEST_LABEL_WIDTH_DP
        }
        val perSegment = glyphs * scale + SEGMENT_PADDING_DP
        return contentWidthDp < perSegment * SEGMENT_COUNT
    }

    /**
     * Whether a window of the given height is short enough to need the compact vertical padding.
     *
     * <p>Returns a decision, not a measurement: the two padding values live in [Spacing] with every
     * other dimension, so there is one place to change a spacing token and one place to change when
     * it applies. A rule that also carried the values would drift from the design system the first
     * time either was retuned.
     *
     * @param windowHeightDp the height available to the content, after the system bars.
     */
    fun isCompactHeight(windowHeightDp: Float): Boolean = windowHeightDp < COMPACT_HEIGHT_DP

    /**
     * The completion percentage worth announcing, rounded down to a step.
     *
     * <p>A copy reports progress once per file, which for a few thousand small files is several
     * announcements a second. A polite live region would read every one of them and bury whatever
     * the player was listening to, so the announced value only moves in [PROGRESS_ANNOUNCEMENT_STEP]
     * increments - the bar still moves smoothly, because it is drawn from the exact fraction.
     *
     * @param percent completion from 0 to 100; clamped, since a reporter may briefly overshoot.
     */
    fun announcedPercent(percent: Int): Int {
        val clamped = percent.coerceIn(0, 100)
        return clamped - clamped % PROGRESS_ANNOUNCEMENT_STEP
    }

    /** The increment, in percent, between two progress announcements. */
    const val PROGRESS_ANNOUNCEMENT_STEP = 10
}
