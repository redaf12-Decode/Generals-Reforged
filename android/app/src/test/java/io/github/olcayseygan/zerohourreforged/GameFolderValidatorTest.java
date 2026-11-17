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

package io.github.olcayseygan.zerohourreforged;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Host tests for {@link GameFolderValidator}: the engine's own install-folder rule. */
public class GameFolderValidatorTest {

    private static GameFolderValidator.Verdict validate(String... paths) {
        return GameFolderValidator.validate(Arrays.asList(paths));
    }

    @Test
    public void acceptsZeroHourWithTheBaseGameBesideIt() {
        GameFolderValidator.Verdict verdict = validate("INIZH.big", "Textures.big", "Generals.big");
        assertTrue(verdict.isOk());
        assertEquals("", verdict.rootPrefix);
    }

    @Test
    public void acceptsTheBaseGameInAZhGeneralsSubfolder() {
        GameFolderValidator.Verdict verdict = validate("INIZH.big", "ZH_Generals/Textures.big");
        assertTrue(verdict.isOk());
        assertEquals("", verdict.rootPrefix);
    }

    @Test
    public void acceptsASteamLikePairOfSiblings() {
        // The player picked the folder that holds both games.
        GameFolderValidator.Verdict verdict = validate(
                "Command & Conquer Generals Zero Hour/INIZH.big",
                "Command and Conquer Generals/Textures.big");
        assertTrue(verdict.isOk());
        assertEquals("Command & Conquer Generals Zero Hour/", verdict.rootPrefix);
    }

    @Test
    public void acceptsTheBaseGameInsideAZhGeneralsSubfolderOfAPickedParent() {
        GameFolderValidator.Verdict verdict = validate(
                "Zero Hour/INIZH.big", "Zero Hour/ZH_Generals/Textures.big");
        assertTrue(verdict.isOk());
        assertEquals("Zero Hour/", verdict.rootPrefix);
    }

    @Test
    public void ignoresCaseAsTheEngineDoes() {
        assertTrue(validate("inizh.big", "zh_generals/textures.big").isOk());
        assertTrue(validate("IniZH.BIG", "TEXTURES.BIG").isOk());
    }

    @Test
    public void rejectsAFolderWithoutZeroHour() {
        GameFolderValidator.Verdict verdict = validate("Textures.big", "Generals.big");
        assertEquals(GameFolderValidator.Result.NO_ZERO_HOUR, verdict.result);
        assertNull(verdict.rootPrefix);
        assertEquals(GameFolderValidator.Result.NO_ZERO_HOUR,
                GameFolderValidator.validate(new ArrayList<>()).result);
    }

    @Test
    public void rejectsZeroHourWithoutTheBaseGame() {
        GameFolderValidator.Verdict verdict = validate("INIZH.big", "Generals.big");
        assertEquals(GameFolderValidator.Result.NO_BASE_GAME, verdict.result);
        assertEquals("", verdict.rootPrefix);
    }

    @Test
    public void rejectsZeroHourWithOnlyTheZeroHourArchive() {
        assertEquals(GameFolderValidator.Result.NO_BASE_GAME, validate("INIZH.big").result);
    }

    @Test
    public void doesNotMistakeANestedArchiveForTheBaseGame() {
        // A Textures.big somewhere unrelated is not where the engine looks.
        assertEquals(GameFolderValidator.Result.NO_BASE_GAME,
                validate("INIZH.big", "Data/Textures.big").result);
    }

    @Test
    public void aRealisticZeroHourTreeIsAccepted() {
        List<String> tree = Arrays.asList(
                "INIZH.big", "PatchZH.big", "TexturesZH.big", "Generals.big", "W3DZH.big",
                "AudioZH.big", "SpeechZH.big", "MusicZH.big", "MapsZH.big", "TerrainZH.big",
                "ZH_Generals/Textures.big", "ZH_Generals/Generals.big",
                "data/INI/Default/Weather.ini");
        assertTrue(GameFolderValidator.validate(tree).isOk());
    }

    @Test
    public void picksTheShallowestZeroHourFolder() {
        // A copy nested inside another copy: the shallowest is the install the player meant.
        GameFolderValidator.Verdict verdict = validate(
                "INIZH.big", "Textures.big",
                "backup/INIZH.big", "backup/Textures.big");
        assertTrue(verdict.isOk());
        assertEquals("", verdict.rootPrefix);
    }
}
