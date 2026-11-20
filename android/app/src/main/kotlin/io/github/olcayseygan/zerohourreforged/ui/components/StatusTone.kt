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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.olcayseygan.zerohourreforged.R
import io.github.olcayseygan.zerohourreforged.ui.theme.LocalStatusColors

/**
 * The five states anything in this application can be in, and the tones that express them.
 *
 * <p>This is the semantic vocabulary the screens share: a compatibility verdict, a single
 * requirement's check, a file-copy outcome and a folder's validity are all one of these. Naming them
 * once is what stops the launcher and the compatibility screen from drifting into two different
 * greens.
 *
 * <p>Every state carries an [icon] and a [label], never colour alone: a player with a monochrome
 * display or a colour-vision deficiency reads the glyph and the words, and a screen reader announces
 * the label.
 *
 * <p>Four glyphs serve five states: [INFO] and [NEUTRAL] share the informational one, because the
 * only thing that separates them is whether a value was reported, which the label states and the
 * surrounding text always shows. Material's icons-core set has no "unknown" glyph, and adding an
 * icons-extended dependency for one pictogram is not worth it.
 */
internal enum class StatusTone {
    /** Everything is in order; the action can proceed. */
    SUCCESS,

    /** Something the player should notice that does not by itself block them. */
    WARNING,

    /** A hard failure: a mandatory requirement or an operation is missing. */
    DANGER,

    /** Neutral information, neither good nor bad. */
    INFO,

    /** Nothing is known yet - no value reported, nothing chosen. */
    NEUTRAL,
}

/** The tone's glyph. Distinct for success, warning and danger; INFO and NEUTRAL share one. */
internal val StatusTone.icon: ImageVector
    get() = when (this) {
        StatusTone.SUCCESS -> Icons.Filled.CheckCircle
        StatusTone.WARNING -> Icons.Filled.Warning
        StatusTone.DANGER -> Icons.Filled.Close
        StatusTone.INFO -> Icons.Filled.Info
        StatusTone.NEUTRAL -> Icons.Filled.Info
    }

/** A short spoken name for the tone, for a screen reader. */
@Composable
internal fun StatusTone.label(): String = stringResource(
    when (this) {
        StatusTone.SUCCESS -> R.string.status_success
        StatusTone.WARNING -> R.string.status_warning
        StatusTone.DANGER -> R.string.status_danger
        StatusTone.INFO -> R.string.status_info
        StatusTone.NEUTRAL -> R.string.status_neutral
    },
)

/** The three colours of a tone: glyph, tinted fill, and text drawn on that fill. */
internal data class ToneColors(val content: Color, val container: Color, val onContainer: Color)

/** This tone's colours from the enclosing theme's status palette. */
@Composable
internal fun StatusTone.colors(): ToneColors {
    val status = LocalStatusColors.current
    return when (this) {
        StatusTone.SUCCESS -> ToneColors(status.success, status.successContainer, status.onSuccessContainer)
        StatusTone.WARNING -> ToneColors(status.warning, status.warningContainer, status.onWarningContainer)
        StatusTone.DANGER -> ToneColors(status.danger, status.dangerContainer, status.onDangerContainer)
        StatusTone.INFO -> ToneColors(status.info, status.infoContainer, status.onInfoContainer)
        StatusTone.NEUTRAL -> ToneColors(status.neutral, status.neutralContainer, status.onNeutralContainer)
    }
}

/**
 * The tone's glyph, sized for a row of text.
 *
 * <p>[contentDescription] is null by default because a status card or chip that shows the glyph
 * already announces its label; only a bare glyph needs its own description.
 */
@Composable
internal fun StatusIcon(
    tone: StatusTone,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = tone.icon,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tone.colors().content,
    )
}
