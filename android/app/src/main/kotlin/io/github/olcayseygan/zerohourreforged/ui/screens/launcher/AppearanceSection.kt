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

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import io.github.olcayseygan.zerohourreforged.R
import io.github.olcayseygan.zerohourreforged.ui.theme.AppearanceMode
import io.github.olcayseygan.zerohourreforged.ui.theme.spacing

/**
 * The appearance controls: light, dark or follow the device, and whether to use wallpaper colours.
 *
 * <p>The mode is a [SingleChoiceSegmentedButtonRow]: three mutually exclusive choices with the
 * current one visibly selected, which is exactly what a segmented button expresses. The dynamic
 * colour option is a [Switch] on a toggleable row, because it is a single on/off preference.
 *
 * <p>The dynamic-colour row is disabled below Android 12 and says why, rather than disappearing:
 * a control that vanishes between devices is more confusing than one that explains it needs a newer
 * version.
 *
 * @param onAppearanceChange called with the new mode and dynamic-colour flag together, so the store
 *   writes one consistent pair.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSection(
    appearance: AppearanceMode,
    dynamicColor: Boolean,
    onAppearanceChange: (AppearanceMode, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        AppearanceMode.SYSTEM to stringResource(R.string.appearance_system),
        AppearanceMode.LIGHT to stringResource(R.string.appearance_light),
        AppearanceMode.DARK to stringResource(R.string.appearance_dark),
    )
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = appearance == mode,
                    onClick = { onAppearanceChange(mode, dynamicColor) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                ) {
                    // Two lines, not one: at 200% font scale a three-way row cannot fit the labels
                    // on a single line of a narrow phone, and a clipped "Follow sys…" is worse than
                    // a wrapped one.
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MaterialTheme.spacing.touchTarget)
                .toggleable(
                    value = dynamicColor && dynamicAvailable,
                    enabled = dynamicAvailable,
                    role = Role.Switch,
                    onValueChange = { onAppearanceChange(appearance, it) },
                ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.appearance_dynamic_color),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!dynamicAvailable) {
                    Text(
                        text = stringResource(R.string.appearance_dynamic_color_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = dynamicColor && dynamicAvailable,
                onCheckedChange = null,
                enabled = dynamicAvailable,
            )
        }
    }
}
