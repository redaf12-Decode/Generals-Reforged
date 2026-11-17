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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Host tests for {@link GameDataStaging}: the incremental copy plan. */
public class GameDataStagingTest {

    private static GameDataStaging.FileState file(String path, long size, long modified) {
        return new GameDataStaging.FileState(path, size, modified);
    }

    @Test
    public void firstStagingCopiesEverything() {
        List<GameDataStaging.FileState> source = Arrays.asList(
                file("INIZH.big", 100, 1), file("ZH_Generals/Textures.big", 200, 2));
        GameDataStaging.Plan plan = GameDataStaging.plan(source, Collections.emptyList());
        assertEquals(2, plan.toCopy.size());
        assertEquals(300, plan.bytesToCopy());
        assertTrue(plan.toDelete.isEmpty());
        assertFalse(plan.isNoOp());
    }

    @Test
    public void anUnchangedTreeCopiesNothing() {
        List<GameDataStaging.FileState> source = Arrays.asList(
                file("INIZH.big", 100, 1), file("ZH_Generals/Textures.big", 200, 2));
        GameDataStaging.Plan plan = GameDataStaging.plan(source, source);
        assertTrue(plan.isNoOp());
        assertEquals(0, plan.bytesToCopy());
    }

    @Test
    public void onlyAChangedFileIsCopied() {
        List<GameDataStaging.FileState> source = Arrays.asList(
                file("INIZH.big", 100, 1), file("Generals.big", 300, 9));
        List<GameDataStaging.FileState> staged = Arrays.asList(
                file("INIZH.big", 100, 1), file("Generals.big", 200, 9));
        GameDataStaging.Plan plan = GameDataStaging.plan(source, staged);
        assertEquals(1, plan.toCopy.size());
        assertEquals("Generals.big", plan.toCopy.get(0).relativePath);
        assertEquals(300, plan.bytesToCopy());
    }

    @Test
    public void aReStampedFileIsCopied() {
        List<GameDataStaging.FileState> source = Collections.singletonList(file("INIZH.big", 100, 5));
        List<GameDataStaging.FileState> staged = Collections.singletonList(file("INIZH.big", 100, 4));
        assertEquals(1, GameDataStaging.plan(source, staged).toCopy.size());
    }

    @Test
    public void anUnknownModificationTimeFallsBackToSize() {
        // The provider reports no last-modified time (0): a same-size file is trusted, a
        // different-size one is copied.
        List<GameDataStaging.FileState> source = Collections.singletonList(file("INIZH.big", 100, 0));
        List<GameDataStaging.FileState> staged = Collections.singletonList(file("INIZH.big", 100, 7));
        assertTrue(GameDataStaging.plan(source, staged).isNoOp());
    }

    @Test
    public void aFileTheSourceLostIsDeleted() {
        List<GameDataStaging.FileState> source = Collections.singletonList(file("INIZH.big", 100, 1));
        List<GameDataStaging.FileState> staged = Arrays.asList(
                file("INIZH.big", 100, 1), file("Old.big", 50, 1));
        GameDataStaging.Plan plan = GameDataStaging.plan(source, staged);
        assertEquals(Collections.singletonList("Old.big"), plan.toDelete);
    }

    @Test
    public void thePlanKnowsWhichExactFilesItCopies() {
        List<GameDataStaging.FileState> source = Arrays.asList(
                file("A.big", 1, 1), file("B.big", 2, 1));
        List<GameDataStaging.FileState> staged = Collections.singletonList(file("A.big", 1, 1));
        GameDataStaging.Plan plan = GameDataStaging.plan(source, staged);
        assertFalse(plan.copies(source.get(0)));
        assertTrue(plan.copies(source.get(1)));
    }

    @Test
    public void aStateSurvivesASerializeRoundTrip() {
        List<GameDataStaging.FileState> files = Arrays.asList(
                file("INIZH.big", 100, 1234), file("ZH_Generals/Textures.big", 200, 5678));
        String text = GameDataStaging.serialize(files);
        List<GameDataStaging.FileState> read = GameDataStaging.parse(text);
        assertEquals(2, read.size());
        assertEquals("INIZH.big", read.get(0).relativePath);
        assertEquals(100, read.get(0).size);
        assertEquals(1234, read.get(0).lastModified);
        assertEquals("ZH_Generals/Textures.big", read.get(1).relativePath);
        assertEquals(5678, read.get(1).lastModified);
        // Round-tripping the parsed state plans nothing.
        assertTrue(GameDataStaging.plan(files, read).isNoOp());
    }

    @Test
    public void aCorruptManifestLineIsDroppedNotTrusted() {
        List<GameDataStaging.FileState> read = GameDataStaging.parse(
                "not-a-number\t1\tA.big\n100\t200\tB.big\nmalformed\n");
        assertEquals(1, read.size());
        assertEquals("B.big", read.get(0).relativePath);
    }

    @Test
    public void aPathTheFormatCannotCarryIsNotSerialized() {
        String text = GameDataStaging.serialize(
                Collections.singletonList(file("bad\tname.big", 1, 1)));
        assertEquals("", text);
    }
}
