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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.olcayseygan.zerohourreforged.ui.theme.spacing

/** One line of a [CompatibilityResultCard]: a state, a name, and the detail behind it. */
internal data class ResultRow(
    val tone: StatusTone,
    val title: String,
    val detail: String,
)

/**
 * The compatibility verdict: the headline result, followed by the individual requirements.
 *
 * <p>The screen's single most important element, so it is the only one drawn on a surface of its own
 * with an outline around it, rather than being one more section. The outline is what carries the
 * separation: `surfaceContainerLowest` is lighter than the background in the light scheme but darker
 * in the dark one, so the tonal step alone would not read the same way in both. Inside, the verdict
 * is a [StatusCard] - the same component the launcher uses for its status - and each requirement is
 * a [ResultRow] stating its own result as a [StatusChip], so the overall verdict and the reasons
 * behind it always agree.
 *
 * <p>Every row is one accessibility node, and a row's colour is never the only carrier of its state:
 * the chip states the result in words, and the row's own text says what the requirement is.
 *
 * @param tone the overall verdict's state.
 * @param headline a few words stating the verdict.
 * @param summary one line of explanation, shown under the headline.
 * @param rows the individual requirements, in the order they should be read.
 */
@Composable
internal fun CompatibilityResultCard(
    tone: StatusTone,
    headline: String,
    summary: String,
    rows: List<ResultRow>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            StatusCard(tone = tone, title = headline, detail = summary)
            if (rows.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                rows.forEach { row ->
                    ResultRowItem(row)
                }
            }
        }
    }
}

/**
 * One requirement inside a [CompatibilityResultCard].
 *
 * <p>Not a Material 3 ListItem: a ListItem carries its own 16dp horizontal inset, which would double
 * up inside the card's padding. A [StatusChip] states the result in words - a glyph alone would ask
 * the player to learn an icon vocabulary - and the two lines below it say what the requirement is
 * and what the platform reported.
 *
 * <p>Stacked rather than a row of badge-then-text so the text keeps the full width: at a 200% font
 * scale a trailing chip would squeeze a long requirement name into a narrow column. The whole item
 * merges into one accessibility node, so it reads as "result. name. detail".
 */
@Composable
private fun ResultRowItem(row: ResultRow, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.tight)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
    ) {
        StatusChip(tone = row.tone, label = row.tone.label())
        Text(
            text = row.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (row.detail.isNotBlank()) {
            Text(
                text = row.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
