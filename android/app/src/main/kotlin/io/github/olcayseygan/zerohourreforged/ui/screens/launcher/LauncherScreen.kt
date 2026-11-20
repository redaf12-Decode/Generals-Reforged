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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.olcayseygan.zerohourreforged.R
import io.github.olcayseygan.zerohourreforged.ui.components.ApplicationTopBar
import io.github.olcayseygan.zerohourreforged.ui.components.DeterminateProgress
import io.github.olcayseygan.zerohourreforged.ui.components.ErrorDialog
import io.github.olcayseygan.zerohourreforged.ui.components.IndeterminateProgress
import io.github.olcayseygan.zerohourreforged.ui.components.PrimaryActionButton
import io.github.olcayseygan.zerohourreforged.ui.components.SecondaryActionButton
import io.github.olcayseygan.zerohourreforged.ui.components.SectionContainer
import io.github.olcayseygan.zerohourreforged.ui.components.StatusCard
import io.github.olcayseygan.zerohourreforged.ui.theme.AppearanceMode
import io.github.olcayseygan.zerohourreforged.ui.theme.ZeroHourTheme
import io.github.olcayseygan.zerohourreforged.ui.theme.spacing

/**
 * The launcher: pick the folder Zero Hour is installed in, stage it, start the game.
 *
 * <p>Stateless by design. [state] says what to draw and the callbacks say what a tap means; the
 * Activity owns the picker, the staging thread, the validator and the game launch, so this
 * composable cannot change any of that behaviour - it can only ask for it.
 *
 * <p>Order is deliberate: the status card first, because it is the answer to the question the
 * screen poses; then the single primary action, then the supporting ones. "Choose game folder"
 * is the primary action even after a folder is remembered, because re-choosing is how a player
 * recovers from a folder that went away.
 *
 * @param state everything the screen draws; the Activity derives it.
 * @param onSelectFolder opens the system folder picker.
 * @param onLaunchGame starts the engine with the folder already validated.
 * @param onClearFolder forgets the remembered folder.
 * @param onShowRequirements opens the compatibility screen.
 * @param appearance the scheme to draw, and the current value of the appearance control.
 * @param dynamicColor whether wallpaper colours are in use, and the current value of that switch.
 * @param onAppearanceChange called with the new pair when the player changes either control. When
 *   null the appearance section is not shown at all, which is what a host that does not persist a
 *   preference passes.
 * @param error a failure to report over the screen, or null for none.
 * @param onDismissError acknowledges [error] and clears it.
 */
@Composable
fun LauncherScreen(
    state: LauncherUiState,
    onSelectFolder: () -> Unit,
    onLaunchGame: () -> Unit,
    onClearFolder: () -> Unit,
    onShowRequirements: () -> Unit,
    modifier: Modifier = Modifier,
    appearance: AppearanceMode = AppearanceMode.DEFAULT,
    dynamicColor: Boolean = false,
    onAppearanceChange: ((AppearanceMode, Boolean) -> Unit)? = null,
    error: String? = null,
    onDismissError: () -> Unit = {},
) {
    if (error != null) {
        ErrorDialog(
            title = stringResource(R.string.setup_problem_title),
            message = error,
            onDismiss = onDismissError,
        )
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { ApplicationTopBar(title = stringResource(R.string.setup_title)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MaterialTheme.spacing.screenEdge,
                    vertical = MaterialTheme.spacing.screenVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sectionGap),
        ) {
            Text(
                text = stringResource(R.string.setup_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StatusCard(
                tone = state.tone,
                title = state.status,
                detail = state.detail.takeIf { it.isNotEmpty() },
            )

            // No label: the status card directly above already says what is being copied, and a bar
            // that repeats that sentence reads as a rendering bug.
            if (state.staging) {
                if (state.hasProgressTotal) {
                    DeterminateProgress(progress = state.progressFraction)
                } else {
                    IndeterminateProgress()
                }
            }

            SectionContainer(title = stringResource(R.string.setup_group_actions)) {
                PrimaryActionButton(
                    text = stringResource(R.string.setup_select_folder),
                    onClick = onSelectFolder,
                    enabled = !state.staging,
                )
                PrimaryActionButton(
                    text = stringResource(R.string.setup_launch),
                    onClick = onLaunchGame,
                    enabled = state.canLaunch,
                    busy = state.staging,
                )
                SecondaryActionButton(
                    text = stringResource(R.string.setup_clear_folder),
                    onClick = onClearFolder,
                    enabled = state.canClear && !state.staging,
                )
                SecondaryActionButton(
                    text = stringResource(R.string.compat_button),
                    onClick = onShowRequirements,
                )
            }

            if (onAppearanceChange != null) {
                SectionContainer(title = stringResource(R.string.appearance_title)) {
                    AppearanceSection(
                        appearance = appearance,
                        dynamicColor = dynamicColor,
                        onAppearanceChange = onAppearanceChange,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LauncherReadyPreview() {
    ZeroHourTheme {
        LauncherScreen(
            state = LauncherUiState(
                status = "Ready: Zero Hour.",
                detail = "The game reads its data from /storage/emulated/0/Android/data/.../files/game",
                canLaunch = true,
                canClear = true,
            ),
            onSelectFolder = {},
            onLaunchGame = {},
            onClearFolder = {},
            onShowRequirements = {},
            onAppearanceChange = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LauncherStagingPreview() {
    ZeroHourTheme(appearance = AppearanceMode.DARK) {
        LauncherScreen(
            state = LauncherUiState(
                status = "Copying the game data...",
                detail = "",
                staging = true,
                progressCopied = 3_400_000_000L,
                progressTotal = 8_100_000_000L,
            ),
            onSelectFolder = {},
            onLaunchGame = {},
            onClearFolder = {},
            onShowRequirements = {},
            onAppearanceChange = { _, _ -> },
        )
    }
}
