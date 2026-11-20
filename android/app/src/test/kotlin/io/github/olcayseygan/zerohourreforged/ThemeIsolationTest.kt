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

package io.github.olcayseygan.zerohourreforged

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The theme/manifest contract this application depends on, asserted against the real files.
 *
 * <p>These are not unit tests of a rule object: they read `AndroidManifest.xml`, the theme resources
 * and `ZeroHourActivity.java` from the module and check the properties that a compile cannot. A
 * theme is a string reference resolved at runtime, so nothing fails to build if an Activity names a
 * theme that does not exist, if the game surface silently inherits a presentation theme, or if a
 * presentation Activity loses its edge-to-edge call - each of those is a green build and a broken
 * screen. This test is the only thing that fails instead.
 */
class ThemeIsolationTest {

    private val module: File = findModuleRoot()
    private val manifest: String by lazy { module.resolve("src/main/AndroidManifest.xml").readText() }

    private fun resFile(vararg path: String) = module.resolve("src/main/res/" + path.joinToString("/"))

    private fun themes(): String = listOf(
        resFile("values", "themes.xml"),
        resFile("values-night", "themes.xml"),
        resFile("values-v31", "themes.xml"),
        resFile("values-night-v31", "themes.xml"),
        resFile("values", "styles.xml"),
    ).joinToString("\n") { it.readText() }

    /** The `<activity>` element for [name], as text, from the application manifest. */
    private fun activity(name: String): String {
        val start = manifest.indexOf("android:name=\".$name\"")
        assertTrue("no <activity> for $name", start >= 0)
        val open = manifest.lastIndexOf("<activity", start)
        val close = manifest.indexOf("/>", start)
        return manifest.substring(open, close)
    }

    @Test
    fun everyActivityNamesItsOwnTheme() {
        // A named theme per screen is the point of this phase: the game surface must not be able to
        // pick up a presentation theme by inheriting the <application> default.
        assertTrue(
            "SetupActivity must use the Setup theme",
            activity("SetupActivity").contains("android:theme=\"@style/Theme.ZeroHour.Setup\""),
        )
        assertTrue(
            "CompatibilityActivity must use the Compatibility theme",
            activity("CompatibilityActivity")
                .contains("android:theme=\"@style/Theme.ZeroHour.Compatibility\""),
        )
        assertTrue(
            "ZeroHourActivity must name the game theme explicitly, not inherit one",
            activity("ZeroHourActivity").contains("android:theme=\"@style/Theme.ZeroHour.Game\""),
        )
    }

    @Test
    fun theGameThemeIsNotAPresentationTheme() {
        // The failure this guards: someone repoints the game Activity at the presentation theme to
        // "fix" a status bar, and the fullscreen surface gains system bars and insets.
        val game = activity("ZeroHourActivity")
        assertFalse(
            "the game Activity must not use a presentation theme",
            game.contains("Theme.ZeroHour.Setup") || game.contains("Theme.ZeroHour.Compatibility"),
        )
        assertFalse("the game Activity must not be edge to edge", game.contains("edgeToEdge"))
    }

    @Test
    fun theThreeConcreteThemesExist() {
        val all = themes()
        for (name in listOf(
            "Theme.ZeroHour.Setup",
            "Theme.ZeroHour.Compatibility",
            "Theme.ZeroHour.Game",
        )) {
            assertTrue("$name is not defined in any theme file", all.contains("name=\"$name\""))
        }
    }

    @Test
    fun theGameThemeIsStillFullscreen() {
        // AppTheme is the fullscreen parent the game has always had; Theme.ZeroHour.Game must only
        // rename it, never restyle it.
        val styles = resFile("values", "styles.xml").readText()
        assertTrue(
            "AppTheme must stay the fullscreen platform theme",
            styles.contains(
                "<style name=\"AppTheme\" parent=\"android:Theme.NoTitleBar.Fullscreen\" />",
            ),
        )
        assertTrue(
            "Theme.ZeroHour.Game must inherit AppTheme and add nothing",
            styles.contains("<style name=\"Theme.ZeroHour.Game\" parent=\"AppTheme\" />"),
        )
    }

    @Test
    fun theSplashIsDeclaredForEveryPresentationThemeOnApi31() {
        // A style resolved on a dark-mode API 31+ device comes from values-night-v31, so a splash
        // declared only in values-v31 is missing exactly there.
        for (dir in listOf("values-v31", "values-night-v31")) {
            val xml = resFile(dir, "themes.xml").readText()
            for (name in listOf("Theme.ZeroHour.Setup", "Theme.ZeroHour.Compatibility")) {
                val body = xml.substringAfter("name=\"$name\"", "")
                assertNotEquals("$name has no splash in $dir", "", body)
                assertTrue(
                    "$name in $dir does not set the splash background",
                    body.substringBefore("/>").contains("windowSplashScreenBackground"),
                )
            }
        }
    }

    @Test
    fun theGameActivityStillHonoursItsSdlContract() {
        // The functional contract this phase must not disturb. Each line is something SDLActivity
        // needs in order to load the engine and run it; a refactor that drops one compiles fine.
        val game = module.resolve(
            "src/main/java/io/github/olcayseygan/zerohourreforged/ZeroHourActivity.java",
        ).readText()
        assertTrue("must extend SDLActivity", game.contains("extends SDLActivity"))
        assertTrue("must load libmain.so", game.contains("return new String[] { \"main\" }"))
        assertTrue("must pass -root", game.contains("\"-root\""))
        // Checked as declarations, not names: the class javadoc discusses both methods by name,
        // so a bare `contains` would fail on the documentation rather than on an override.
        assertTrue("must not override the entry point", !game.contains("String getMainFunction("))
        assertTrue(
            "must not override getMainSharedObject",
            !game.contains("String getMainSharedObject("),
        )
        // SDL owns the lifecycle; an override here would be a second lifecycle authority.
        for (hook in listOf("onPause", "onResume", "onStart", "onStop")) {
            assertFalse("must not override $hook", game.contains("void $hook("))
        }
    }

    @Test
    fun bothPresentationScreensOptIntoEdgeToEdge() {
        // enableEdgeToEdgeWindow must be called before setContent in both hosts: after the first
        // frame it re-lays out a drawn screen and the content jumps by the bar height.
        for (host in listOf("ui/LauncherHost.kt", "ui/CompatibilityHost.kt")) {
            val src = module.resolve("src/main/kotlin/io/github/olcayseygan/zerohourreforged/$host")
                .readText()
            val call = src.indexOf("enableEdgeToEdgeWindow()")
            val content = src.indexOf("setContent {")
            assertTrue("$host never opts into edge to edge", call >= 0)
            assertTrue("$host enables edge to edge after setContent", call < content)
        }
    }

    @Test
    fun thePresentationScreensReserveTheCutout() {
        // Edge to edge means the Scaffold's insets are the only thing keeping content out of the
        // bars and the cutout. The M3 default (systemBars) does not cover the cutout.
        for (screen in listOf(
            "screens/launcher/LauncherScreen.kt",
            "screens/compatibility/CompatibilityScreen.kt",
        )) {
            val src = module.resolve("src/main/kotlin/io/github/olcayseygan/zerohourreforged/ui/$screen")
                .readText()
            assertTrue("$screen does not consume safeDrawing", src.contains("WindowInsets.safeDrawing"))
        }
    }

    private companion object {
        /**
         * The Gradle test task runs with the working directory inside the module, but which
         * directory that is has changed between Gradle versions, so walk up until the module's
         * manifest is found rather than trusting one level.
         */
        fun findModuleRoot(): File {
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            while (dir != null) {
                if (File(dir, "src/main/AndroidManifest.xml").isFile) return dir
                if (File(dir, "app/src/main/AndroidManifest.xml").isFile) {
                    return File(dir, "app")
                }
                dir = dir.parentFile
            }
            throw IllegalStateException("AndroidManifest.xml not found above the test working directory")
        }
    }
}
