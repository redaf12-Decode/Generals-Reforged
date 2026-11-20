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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.olcayseygan.zerohourreforged.R
import io.github.olcayseygan.zerohourreforged.ui.components.ApplicationHeader
import io.github.olcayseygan.zerohourreforged.ui.components.ConfirmDialog
import io.github.olcayseygan.zerohourreforged.ui.components.DeterminateProgress
import io.github.olcayseygan.zerohourreforged.ui.components.ErrorDialog
import io.github.olcayseygan.zerohourreforged.ui.components.IndeterminateProgress
import io.github.olcayseygan.zerohourreforged.ui.components.InstallationCard
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
 * composable cannot change any of that behaviour - it can only ask for it. In particular it never
 * derives a verdict: [LauncherUiState.status] is the Activity's own reading of the backend, so a
 * wrong folder can never be drawn as ready.
 *
 * <p>The order is the order the player needs it: identity, then what is installed, then the state
 * of the installation, then the one action that resolves that state, then the supporting actions.
 * "Choose game folder" is the primary action in every state that is not ready, because re-picking
 * is the recovery path for all of them; "Start game" becomes primary once the copy validates.
 *
 * <p>The whole column scrolls, so a long validation message, a 200% font scale or a short landscape
 * window can never hide the actions. The content is capped at [CONTENT_MAX_WIDTH] so a tablet does
 * not stretch a paragraph across the screen.
 *
 * @param state everything the screen draws; the Activity derives it.
 * @param onSelectFolder opens the system folder picker.
 * @param onLaunchGame starts the engine with the folder already validated.
 * @param onClearFolder forgets the remembered folder; the screen confirms first.
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
    // The confirmation is screen-local: it is a question about one tap, not part of the
    // installation state, and the Activity never needs to know it was asked.
    var confirmingClear by remember { mutableStateOf(false) }

    if (error != null) {
        ErrorDialog(
            title = stringResource(R.string.setup_problem_title),
            message = error,
            onDismiss = onDismissError,
        )
    }

    if (confirmingClear) {
        ConfirmDialog(
            title = stringResource(R.string.setup_clear_confirm_title),
            message = stringResource(
                R.string.setup_clear_confirm_message,
                state.folderName ?: stringResource(R.string.setup_value_no_folder),
            ),
            dismissText = stringResource(R.string.setup_cancel),
            confirmText = stringResource(R.string.setup_clear_confirm_action),
            onConfirm = {
                confirmingClear = false
                onClearFolder()
            },
            onDismiss = { confirmingClear = false },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = MaterialTheme.spacing.screenEdge,
                        vertical = MaterialTheme.spacing.screenVertical,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sectionGap),
            ) {
                ApplicationHeader(
                    title = stringResource(R.string.setup_title),
                    subtitle = stringResource(R.string.setup_subtitle),
                )

                InstallationCard(
                    folderName = state.folderName,
                    enginePath = state.enginePath,
                    showCopyNote = state.status != LauncherStatus.READY,
                )

                StatusCard(
                    tone = state.tone,
                    title = state.headline,
                    detail = state.detail.takeIf { it.isNotEmpty() },
                )

                // The status card above already names what is being copied, so the bar carries the
                // byte counts instead of repeating that sentence. A total the copy has not reported
                // yet is drawn as indeterminate rather than as a bar stuck at 0%.
                if (state.staging) {
                    if (state.hasProgressTotal) {
                        DeterminateProgress(
                            progress = state.progressFraction,
                            label = state.progressBytes,
                        )
                    } else {
                        IndeterminateProgress()
                    }
                }

                SectionContainer(title = stringResource(R.string.setup_group_actions)) {
                    if (state.canLaunch) {
                        PrimaryActionButton(
                            text = stringResource(R.string.setup_launch),
                            onClick = onLaunchGame,
                            enabled = !state.staging,
                        )
                        SecondaryActionButton(
                            text = stringResource(R.string.setup_select_folder),
                            onClick = onSelectFolder,
                            enabled = !state.staging,
                        )
                    } else {
                        // Disabled rather than busy: the spinner would label "Choose game folder"
                        // as the running operation, but the folder is already chosen and it is the
                        // copy that is running. The progress bar above is the busy indicator.
                        PrimaryActionButton(
                            text = stringResource(R.string.setup_select_folder),
                            onClick = onSelectFolder,
                            enabled = !state.staging,
                        )
                    }
                    SecondaryActionButton(
                        text = stringResource(R.string.compat_button),
                        onClick = onShowRequirements,
                    )
                    if (state.canClear) {
                        SecondaryActionButton(
                            text = stringResource(R.string.setup_clear_folder),
                            onClick = { confirmingClear = true },
                            enabled = !state.staging,
                        )
                    }
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
}

/** The widest the launcher's column gets, so a tablet does not stretch a paragraph edge to edge. */
private val CONTENT_MAX_WIDTH = 560.dp

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LauncherNoFolderPreview() {
    ZeroHourTheme {
        LauncherScreen(
            state = LauncherUiState(
                status = LauncherStatus.NO_FOLDER,
                headline = "No game folder chosen yet.",
                detail = "Tap \"Choose game folder\" and pick the folder Zero Hour is installed in.",
            ),
            onSelectFolder = {},
            onLaunchGame = {},
            onClearFolder = {},
            onShowRequirements = {},
            onAppearanceChange = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LauncherReadyPreview() {
    ZeroHourTheme(appearance = AppearanceMode.DARK) {
        LauncherScreen(
            state = LauncherUiState(
                status = LauncherStatus.READY,
                headline = "Ready: \"Zero Hour\".",
                detail = "The game reads its data from /storage/emulated/0/Android/data/…/files/game",
                folderName = "Zero Hour",
                enginePath = "/storage/emulated/0/Android/data/…/files/game",
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

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LauncherStagingPreview() {
    ZeroHourTheme {
        LauncherScreen(
            state = LauncherUiState(
                status = LauncherStatus.STAGING,
                headline = "Copying the game data…",
                folderName = "Zero Hour",
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

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LauncherIncompletePreview() {
    ZeroHourTheme {
        LauncherScreen(
            state = LauncherUiState(
                status = LauncherStatus.INCOMPLETE,
                headline = "\"Zero Hour\" is not a complete Zero Hour folder.",
                detail = "Zero Hour is there, but not the original Command & Conquer Generals it needs.",
                folderName = "Zero Hour",
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

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LauncherPermissionLostPreview() {
    ZeroHourTheme {
        LauncherScreen(
            state = LauncherUiState(
                status = LauncherStatus.PERMISSION_LOST,
                headline = "Access to \"Zero Hour\" was withdrawn.",
                detail = "The system no longer grants access to the folder you picked. Choose it again.",
                folderName = "Zero Hour",
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

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LauncherInvalidPreview() {
    ZeroHourTheme {
        LauncherScreen(
            state = LauncherUiState(
                status = LauncherStatus.INVALID,
                headline = "\"Downloads\" has no Zero Hour installation in it.",
                detail = "It has no INIZH.big. Choose the folder Zero Hour is installed in.",
                folderName = "Downloads",
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

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LauncherStagingFailedPreview() {
    ZeroHourTheme(appearance = AppearanceMode.DARK) {
        LauncherScreen(
            state = LauncherUiState(
                status = LauncherStatus.STAGING_FAILED,
                headline = "Copying the game data did not finish.",
                detail = "No space left on device",
                folderName = "Zero Hour",
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
