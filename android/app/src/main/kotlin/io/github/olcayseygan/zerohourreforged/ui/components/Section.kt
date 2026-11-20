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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.olcayseygan.zerohourreforged.ui.theme.spacing

/**
 * A section of a screen: a heading and the content it introduces, grouped so every screen is
 * divided the same way.
 *
 * <p>The container is a filled surface at `surfaceContainerLow` with a [MaterialTheme.shapes.large]
 * corner, so it separates from the background by tone rather than by a shadow - the only separation
 * that survives a dark scheme, where shadows are invisible. The palette defines the whole surface
 * ramp for the same reason: a card nested inside a section steps again, by tone.
 *
 * <p>The heading is inside the container rather than above it, so a long section that scrolls is
 * still identifiable when its heading scrolls off.
 */
@Composable
internal fun SectionContainer(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            SectionHeading(title)
            content()
        }
    }
}

/**
 * A section heading: the title of the group below it.
 *
 * <p>Gold (the primary colour) and `titleMedium`, with a heading semantic so a screen reader can
 * jump between sections. Not a larger type size than the body: at 200% font scale a heading two
 * steps larger than the body overflows a narrow phone, and weight plus colour already separate it.
 */
@Composable
internal fun SectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
