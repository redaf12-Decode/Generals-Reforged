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
import androidx.compose.ui.res.stringResource
import io.github.olcayseygan.zerohourreforged.R

/**
 * The dialog that reports a failure the player has to acknowledge.
 *
 * <p>A Material 3 [AlertDialog] with a single dismiss action: an error here is a statement, not a
 * choice, so offering a second button would be a decision the application cannot act on. The
 * dialog's title and text carry the message; the tone's glyph is deliberately absent, because the
 * dialog is itself the signal and an icon would only repeat the word "error".
 *
 * <p>[message] is arbitrary text from the failure - a validator message, an exception's description -
 * so it wraps and the dialog grows. It is never truncated: the one thing a player needs from an
 * error dialog is the whole reason.
 *
 * <p>Dismissing by tapping outside the dialog is allowed, which is the platform's convention and
 * matches the single button.
 */
@Composable
internal fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = stringResource(R.string.action_dismiss),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = confirmText, color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.error,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
