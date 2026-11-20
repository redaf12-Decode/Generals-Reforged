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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.olcayseygan.zerohourreforged.R
import io.github.olcayseygan.zerohourreforged.ui.theme.spacing

/**
 * The one action a screen wants the player to take.
 *
 * <p>A filled [Button] in the brand gold, full width, with a fixed minimum height. Full width
 * because the primary action is the thing the screen exists for, and a thumb reaches it anywhere
 * along the bottom of the device; the minimum height is the 48dp touch target, so the control stays
 * usable when the player's font scale is small.
 *
 * <p>While [busy] the label is replaced by a spinner and the button is disabled. The label is kept
 * in the tree's semantics rather than removed, so a screen reader still names the action while it
 * runs; the spinner carries its own description for the same reason.
 *
 * @param onClick invoked on a tap; not invoked while [busy] or [enabled] is false.
 * @param busy whether the action is in flight. Disables the button and shows the spinner.
 */
@Composable
internal fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val progressDescription = stringResource(R.string.action_in_progress)
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.spacing.touchTarget),
        enabled = enabled && !busy,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (busy) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .semantics { contentDescription = progressDescription },
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(text = text, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The supporting action: the alternative, the escape hatch, the "not now".
 *
 * <p>An [OutlinedButton] with the same geometry as [PrimaryActionButton] so a stack of the two lines
 * up, but no fill: the outline and the gold label carry it. Deliberately not a filled button in a
 * second colour, which would compete with the primary action for attention.
 */
@Composable
internal fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.spacing.touchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
