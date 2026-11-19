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

package io.github.olcayseygan.zerohourreforged.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * The application theme: one [MaterialTheme] with the project's colour, type and shape scales.
 *
 * <p>The game screen is SDL3's own surface and never enters this tree, so this theme only ever
 * applies to the launcher and compatibility screens. Dark is the default because the surrounding
 * game is dark; a light scheme is provided for devices that ask for it.
 *
 * <p>The content is wrapped in a [Surface] painted with the scheme's background. Both Activities
 * use a plain fullscreen window theme with no windowBackground, so without this the platform's
 * default black would show through and the light scheme's dark text would be unreadable.
 */
@Composable
fun ZeroHourTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ZeroHourDarkColors else ZeroHourLightColors,
        typography = ZeroHourTypography,
        shapes = ZeroHourShapes,
    ) {
        CompositionLocalProvider(
            LocalVerdictColors provides if (darkTheme) ZeroHourDarkVerdicts else ZeroHourLightVerdicts,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                content = content,
            )
        }
    }
}
