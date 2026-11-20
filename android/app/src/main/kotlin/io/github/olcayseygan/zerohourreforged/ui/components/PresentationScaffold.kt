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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.olcayseygan.zerohourreforged.ui.theme.LayoutRules
import io.github.olcayseygan.zerohourreforged.ui.theme.spacing

/**
 * The frame every presentation screen is drawn in: the window, a centred reading column, and the
 * scroll.
 *
 * <p>One component rather than the same twenty lines in each screen, because the parts that are
 * easy to get wrong are exactly the parts that were being copied: the insets, the width cap and the
 * vertical padding. A screen that forgot one of them would look correct on a phone and clip on a
 * tablet, and nothing would fail to build.
 *
 * <p>The column is capped at [contentMaxWidth] and centred, so a tablet shows a readable column
 * rather than a paragraph stretched to 1280dp. The cap is a maximum, never a fixed width: a narrow
 * phone gets the full width minus the edge padding, which is why nothing here can overflow
 * horizontally.
 *
 * <p>Content always scrolls vertically. At a 200% font scale a launcher that fits one screen in
 * portrait needs three, and a landscape phone has a third of the height - the scroll is what makes
 * every one of those cases work without a second layout.
 *
 * @param contentMaxWidth the widest the reading column gets; see [LayoutRules].
 * @param topBar the app bar, or nothing for a screen that draws its own header.
 */
@Composable
internal fun PresentationScaffold(
    contentMaxWidth: Dp,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = topBar,
        containerColor = MaterialTheme.colorScheme.background,
        // The window is edge to edge (enableEdgeToEdgeWindow), so this padding is the only thing
        // keeping content out of the bars. safeDrawing, not the default systemBars: it also reports
        // the display cutout, which in landscape sits on a short edge under the content.
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            // The height the content actually has, after the bars and the app bar. A landscape
            // phone reports about 360dp here, which is why the vertical padding is chosen from it
            // rather than being a constant.
            val verticalPadding = if (LayoutRules.isCompactHeight(maxHeight.value)) {
                MaterialTheme.spacing.screenVerticalCompact
            } else {
                MaterialTheme.spacing.screenVertical
            }
            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = MaterialTheme.spacing.screenEdge,
                        vertical = verticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sectionGap),
                content = content,
            )
        }
    }
}
