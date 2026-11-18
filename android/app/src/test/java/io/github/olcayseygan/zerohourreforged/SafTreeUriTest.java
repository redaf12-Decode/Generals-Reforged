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
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Host tests for {@link SafTreeUri}: the Storage Access Framework URI shapes the "Use this folder"
 * crash turned on.
 *
 * <p>These are pure-logic tests, not proof of compatibility with any real document provider. They fix
 * the rule that a picked tree must be addressed through its root document URI: the shapes asserted
 * here are exactly what AOSP {@code DocumentsContract.getDocumentId} accepts and what a provider's
 * {@code UriMatcher} answers, and {@link SafTreeStager} builds the root document URI from
 * {@link SafTreeUri#rootDocumentSegments}.
 */
public class SafTreeUriTest {

    /** A tree URI as {@code ACTION_OPEN_DOCUMENT_TREE} returns it: content://.../tree/<treeId>. */
    private static List<String> tree(String treeId) {
        return Arrays.asList("tree", treeId);
    }

    @Test
    public void aTreeIdIsExtractedFromATreeUri() {
        assertEquals("primary:Games", SafTreeUri.treeId(tree("primary:Games")));
    }

    @Test
    public void aTreesRootDocumentIsTheTreeIdItself() {
        // DocumentFile.fromTreeUri builds exactly this: .../tree/<id>/document/<id>.
        assertEquals(Arrays.asList("tree", "primary:Games", "document", "primary:Games"),
                SafTreeUri.rootDocumentSegments(tree("primary:Games")));
    }

    @Test
    public void theRootDocumentIdIsTheTreeId() {
        List<String> rootDocument = SafTreeUri.rootDocumentSegments(tree("primary:Games"));
        assertEquals("primary:Games", SafTreeUri.documentId(rootDocument));
    }

    @Test
    public void aChildDocumentsIdIsReadFromADocumentUriUnderATree() {
        // What a provider returns per child: .../tree/<treeId>/document/<treeId>/<child>.
        List<String> child = Arrays.asList(
                "tree", "primary:Games", "document", "primary:Games/INIZH.big");
        assertEquals("primary:Games/INIZH.big", SafTreeUri.documentId(child));
    }

    @Test
    public void aBareTreeUriIsNotADocumentUri() {
        // The defect: DocumentsContract.getDocumentId(treeUri) throws IllegalArgumentException for a
        // bare tree URI. The old walker passed the picked tree URI straight in, so every directory
        // list threw and staging copied nothing; the setup screen never got its folder.
        assertThrows(IllegalArgumentException.class,
                () -> SafTreeUri.documentId(tree("primary:Games")));
    }

    @Test
    public void aDocumentUriIsNotATreeUri() {
        assertThrows(IllegalArgumentException.class,
                () -> SafTreeUri.treeId(Arrays.asList("document", "primary:Games")));
    }

    @Test
    public void anEmptyOrMalformedUriIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SafTreeUri.treeId(Arrays.asList()));
        assertThrows(IllegalArgumentException.class, () -> SafTreeUri.documentId(Arrays.asList("tree")));
    }
}
