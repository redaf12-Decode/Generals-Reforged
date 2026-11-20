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

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.olcayseygan.zerohourreforged.R

/**
 * What the launcher has actually installed: the folder the player picked and the path the engine
 * reads from.
 *
 * <p>Both values are the backend's, passed straight through - the folder name is the display name
 * the SAF provider returned, the path is [io.github.olcayseygan.zerohourreforged.GameFolderStore]'s
 * engine root. Nothing is summarised or rounded: a player diagnosing a wrong folder needs the name
 * they chose, and a player reporting a bug needs the path the game really uses.
 *
 * <p>When [folderName] is null nothing has been picked, so the card says so in words rather than
 * showing empty rows. The data path is only shown when it exists, because before the first copy
 * there is no staged path to name - and inventing a placeholder would suggest the game already has
 * somewhere to read from.
 *
 * @param folderName the picked folder's display name, or null/blank when none is remembered.
 * @param enginePath the real path the engine reads, or null before a copy has validated.
 * @param showCopyNote whether to explain the one-time copy. Off once the copy is done, where the
 *   note would describe something that already happened.
 */
@Composable
internal fun InstallationCard(
    folderName: String?,
    enginePath: String?,
    modifier: Modifier = Modifier,
    showCopyNote: Boolean = true,
) {
    SectionContainer(
        title = stringResource(R.string.setup_install_title),
        modifier = modifier,
    ) {
        if (folderName.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.setup_value_no_folder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Said here, next to the action that acts on it, rather than as a footnote at the
            // bottom of the screen.
            Text(
                text = stringResource(R.string.setup_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionContainer
        }
        InformationRow(
            label = stringResource(R.string.setup_label_folder),
            value = folderName,
        )
        if (enginePath != null) {
            InformationRow(
                label = stringResource(R.string.setup_label_data_path),
                value = enginePath,
                stacked = true,
            )
        }
        if (showCopyNote) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                text = stringResource(R.string.setup_copy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
