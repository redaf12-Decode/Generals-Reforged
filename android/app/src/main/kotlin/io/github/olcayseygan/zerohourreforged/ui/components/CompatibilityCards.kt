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

/** One line of a [CompatibilityCheckList]: a state, a name, and the detail behind it. */
internal data class CheckRow(
    val tone: StatusTone,
    /** The state in the screen's own accuracy vocabulary, e.g. "Unable to verify". */
    val stateLabel: String,
    val title: String,
    val detail: String,
)

/**
 * The compatibility verdict: the headline result and the one line that explains it.
 *
 * <p>The screen's single most important element, so it is the only one drawn on a surface of its own
 * with an outline around it, rather than being one more section. The outline is what carries the
 * separation: `surfaceContainerLowest` is lighter than the background in the light scheme but darker
 * in the dark one, so the tonal step alone would not read the same way in both.
 *
 * <p>Inside is a [StatusCard] - the same component the launcher uses for its status - so the verdict
 * is announced and tinted exactly like every other state in the application. The individual
 * requirements are deliberately <em>not</em> here: they are [CompatibilityCheckList] further down the
 * screen, so the summary stays a summary and a long list of checks does not push the verdict off
 * the first screenful.
 *
 * @param tone the overall verdict's state.
 * @param headline a few words stating the verdict.
 * @param summary one line of explanation, shown under the headline.
 */
@Composable
internal fun CompatibilitySummaryCard(
    tone: StatusTone,
    headline: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        StatusCard(tone = tone, title = headline, detail = summary)
    }
}

/**
 * The individual compatibility checks, each stating its own result.
 *
 * <p>Separated from the summary because the two answer different questions: the summary says what
 * the verdict is, and this says why. Every row's state comes from the evaluator's own flags - this
 * component never decides a result, it only draws the ones it is given, so the list cannot disagree
 * with the verdict above it.
 *
 * <p>Rows are divided by a full-bleed hairline rather than being separate cards: a dozen outlined
 * boxes would read as a dozen unrelated verdicts. The divider is `outlineVariant`, which is a
 * decorative separation here - the rows are already told apart by their chips, so the line carries
 * no meaning that would need 3:1 contrast.
 *
 * @param rows the checks, in the order the evaluator produced them.
 */
@Composable
internal fun CompatibilityCheckList(
    rows: List<CheckRow>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = MaterialTheme.spacing.small),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            CheckRowItem(row)
        }
    }
}

/**
 * One requirement inside a [CompatibilityCheckList].
 *
 * <p>Not a Material 3 ListItem: a ListItem carries its own 16dp horizontal inset, which would double
 * up inside the section's padding. A [StatusChip] states the result in words - a glyph alone would
 * ask the player to learn an icon vocabulary - and the two lines below it say what the requirement is
 * and what the platform reported.
 *
 * <p>Stacked rather than a row of badge-then-text so the text keeps the full width: at a 200% font
 * scale a trailing chip would squeeze a long requirement name into a narrow column. The whole item
 * merges into one accessibility node, so it reads as "result. name. detail".
 */
@Composable
private fun CheckRowItem(row: CheckRow, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
    ) {
        StatusChip(tone = row.tone, label = row.stateLabel)
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
