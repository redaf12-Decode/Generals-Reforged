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

package io.github.olcayseygan.zerohourreforged.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.olcayseygan.zerohourreforged.ui.theme.spacing

/**
 * The state of something, as a tinted panel: a glyph, a headline, and an optional line of detail.
 *
 * <p>The panel carries the tone's own container colour rather than a generic card colour, so a
 * screen with several states reads at a glance: the danger card is the one that is red. The text on
 * it uses the tone's `onContainer`, which the palette guarantees clears 4.5:1 against that fill in
 * both schemes.
 *
 * <p>The whole panel is one accessibility node: a screen reader announces the tone's name, the
 * headline and the detail together instead of three disconnected fragments.
 *
 * @param tone what state the panel reports.
 * @param title the headline, a few words.
 * @param detail an optional second line, wrapped as needed. It may be long; the panel grows.
 */
@Composable
internal fun StatusCard(
    tone: StatusTone,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val colors = tone.colors()
    val spokenTone = tone.label()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(spokenTone)
                    append(". ")
                    append(title)
                    if (!detail.isNullOrBlank()) {
                        append(". ")
                        append(detail)
                    }
                }
            },
        shape = MaterialTheme.shapes.medium,
        color = colors.container,
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.Top,
        ) {
            StatusIcon(tone = tone, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onContainer,
                )
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onContainer,
                    )
                }
            }
        }
    }
}

/**
 * The state of something, as a compact pill: a glyph and a short label.
 *
 * <p>For a state that belongs to a row of information rather than to a panel of its own - a
 * requirement's result inside a list. A real [androidx.compose.material3.AssistChip] is not used
 * because a chip there is a control the player can press; this is a label, so it is a plain
 * [Surface] with the tone's fill and no interaction or touch target.
 */
@Composable
internal fun StatusChip(
    tone: StatusTone,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = tone.colors()
    val spokenTone = tone.label()
    Surface(
        // A label, not a control: cleared and replaced so it is announced once, as the tone and
        // then the words, instead of as a glyph and a fragment of text.
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "$spokenTone. $label"
        },
        shape = MaterialTheme.shapes.extraSmall,
        color = colors.container,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.tight,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIcon(tone = tone, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onContainer,
            )
        }
    }
}
