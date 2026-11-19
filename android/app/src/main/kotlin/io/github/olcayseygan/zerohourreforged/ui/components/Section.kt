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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.olcayseygan.zerohourreforged.ui.theme.VerdictFailed
import io.github.olcayseygan.zerohourreforged.ui.theme.VerdictSatisfied
import io.github.olcayseygan.zerohourreforged.ui.theme.VerdictUnknown
import io.github.olcayseygan.zerohourreforged.ui.theme.VerdictUnverified

/** How a single requirement stands, matching {@code CompatibilityEvaluator.Check}'s three states. */
enum class CheckState { SATISFIED, FAILED, UNVERIFIED, UNKNOWN }

/**
 * A heading for one group on the compatibility screen, so the sections are separated the same way
 * throughout.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One requirement or device fact: a status mark, the name, and the detail beneath it.
 *
 * <p>The mark is textual (✓ / ✗ / ?) rather than a coloured dot alone, so the state survives a
 * monochrome display and a screen reader; [state] only colours it. The colour is never the sole
 * carrier of meaning.
 */
@Composable
fun CheckRow(
    state: CheckState,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val (mark, color, label) = when (state) {
        CheckState.SATISFIED -> Triple("✓", VerdictSatisfied, "satisfied")
        CheckState.FAILED -> Triple("✗", VerdictFailed, "failed")
        CheckState.UNVERIFIED -> Triple("?", VerdictUnverified, "could not be verified")
        CheckState.UNKNOWN -> Triple("?", VerdictUnknown, "not reported")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "$title: $label" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = mark, color = color, style = MaterialTheme.typography.bodyLarge)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A plain labelled fact, for the device group: these are reported values, not pass/fail checks, so
 * they carry no status mark.
 */
@Composable
fun FactRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A short inline note, for statements that apply to a whole group rather than one check. */
@Composable
fun NoteText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The verdict banner: the one element on the compatibility screen that states the overall result,
 * so it is given the result's colour as a container rather than as text.
 */
@Composable
fun VerdictBanner(text: String, state: CheckState, modifier: Modifier = Modifier) {
    val color: Color = when (state) {
        CheckState.SATISFIED -> VerdictSatisfied
        CheckState.FAILED -> VerdictFailed
        CheckState.UNVERIFIED -> VerdictUnverified
        CheckState.UNKNOWN -> VerdictUnknown
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.16f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
