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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale: one 4dp rhythm for every gap, inset and padding in the application.
 *
 * <p>Values are named by role rather than by size, so a screen says what a gap is *for* and the
 * number lives here once. That is what keeps two cards, the gap between them and the inset around
 * them consistent without any screen picking its own `14.dp`.
 *
 * <p>The scale is provided through [LocalSpacing] and read as `MaterialTheme.spacing`, so it is
 * available anywhere in the tree the same way the colour scheme is, and a preview can supply a
 * different scale without touching a component.
 */
@Immutable
internal data class Spacing(
    /** 2dp: the gap between a label and its own value inside one row. */
    val hairline: Dp = 2.dp,
    /** 4dp: the gap between two lines of the same block. */
    val tight: Dp = 4.dp,
    /** 8dp: the gap between a heading and the content it introduces. */
    val small: Dp = 8.dp,
    /** 12dp: the gap between two related components, such as two buttons. */
    val medium: Dp = 12.dp,
    /** 16dp: the padding inside a card or a section container. */
    val large: Dp = 16.dp,
    /** 20dp: the horizontal inset of a screen's content. */
    val screenEdge: Dp = 20.dp,
    /** 24dp: the gap between two unrelated sections of a screen. */
    val sectionGap: Dp = 24.dp,
    /** 28dp: the vertical breathing room at the top and bottom of a scrolling screen. */
    val screenVertical: Dp = 28.dp,
    /** 48dp: the Material minimum touch target, and the height of a standalone control's row. */
    val touchTarget: Dp = 48.dp,
)

internal val ZeroHourSpacing = Spacing()

/** The spacing scale of the enclosing [ZeroHourTheme]. */
internal val LocalSpacing = staticCompositionLocalOf { ZeroHourSpacing }
