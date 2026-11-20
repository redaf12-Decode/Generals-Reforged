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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The Material 3 shape scale, tightened for a dense, information-heavy layout.
 *
 * <p>The library defaults (16dp on large, 28dp on extra-large) read as playful at the size of a
 * full-width card on a phone. These radii are smaller and increase monotonically, so a container
 * always nests inside a larger one without its corners poking out:
 *
 * <ul>
 *   <li>extraSmall - a 6dp chip radius;</li>
 *   <li>small - 8dp, for the inline pieces inside a card;</li>
 *   <li>medium - 12dp, the card and dialog default;</li>
 *   <li>large - 16dp, the section container;</li>
 *   <li>extraLarge - 24dp, reserved for full-bleed sheets.</li>
 * </ul>
 *
 * <p>Buttons take [Shapes.medium] from the theme rather than the M3 pill, which keeps an action the
 * same shape as the card it sits under.
 */
internal val ZeroHourShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
