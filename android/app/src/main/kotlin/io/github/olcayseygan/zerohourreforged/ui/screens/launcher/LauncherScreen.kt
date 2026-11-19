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

package io.github.olcayseygan.zerohourreforged.ui.screens.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.olcayseygan.zerohourreforged.R
import io.github.olcayseygan.zerohourreforged.ui.theme.ZeroHourTheme

/**
 * The launcher screen as Compose: pick the folder Zero Hour is installed in, stage it, start the
 * game.
 *
 * <p>Stateless by design. [state] says what to draw and the callbacks say what a tap means; the
 * Activity owns the picker, the staging thread, the validator and the game launch. This composable
 * therefore cannot change any of that behaviour - it can only ask for it.
 */
@Composable
fun LauncherScreen(
    state: LauncherUiState,
    onSelectFolder: () -> Unit,
    onLaunchGame: () -> Unit,
    onClearFolder: () -> Unit,
    onShowRequirements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.setup_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        StatusCard(state)

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSelectFolder,
            enabled = !state.staging,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_select_folder))
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onLaunchGame,
            enabled = state.canLaunch && !state.staging,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_launch))
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onClearFolder,
            enabled = state.canClear && !state.staging,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_clear_folder))
        }

        Spacer(Modifier.height(4.dp))
        // Always available: the requirements and this device's compatibility, independent of
        // whether a game folder has been chosen.
        TextButton(
            onClick = onShowRequirements,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.compat_button))
        }
    }
}

@Composable
private fun StatusCard(state: LauncherUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = state.status,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.detail.isNotEmpty()) {
                Text(
                    text = state.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.staging) {
                Spacer(Modifier.height(4.dp))
                if (state.progressTotal > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (state.progressCopied.toDouble() / state.progressTotal.toDouble())
                                .toFloat()
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LauncherScreenPreview() {
    ZeroHourTheme {
        LauncherScreen(
            state = LauncherUiState(
                status = "Ready: \"Zero Hour\".",
                detail = "The game reads its data from /storage/emulated/0/Android/data/.../files/game",
                canLaunch = true,
                canClear = true,
            ),
            onSelectFolder = {},
            onLaunchGame = {},
            onClearFolder = {},
            onShowRequirements = {},
        )
    }
}
