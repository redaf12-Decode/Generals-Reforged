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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Asks before doing something the player cannot undo with one tap.
 *
 * <p>A Material 3 [AlertDialog] with two actions, which is what separates it from [ErrorDialog]: an
 * error is a statement the player only acknowledges, so it gets one button; a confirmation is a
 * decision, so it gets two. Merging them would mean an error dialog that could be "confirmed",
 * which is a worse trap than a duplicated `AlertDialog` call.
 *
 * <p>The confirming action is the one tinted `error`, not the cancelling one: the colour marks what
 * the player is about to lose, and a neutral "Forget folder" would read as routine. The cancel
 * action comes first in reading order, so a screen reader hears the safe choice before the
 * destructive one, and it is the one a thumb reaches first.
 *
 * <p>Dismissing by tapping outside is allowed and means cancel - the platform's convention, and the
 * only reading of a tap outside a "forget this?" question that cannot lose data.
 *
 * @param dismissText the label of the safe action.
 * @param confirmText the label of the destructive action.
 * @param onConfirm invoked only when the player explicitly confirms.
 * @param onDismiss invoked for the safe action, a tap outside, and the back gesture.
 */
@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    dismissText: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
