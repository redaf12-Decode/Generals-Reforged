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

import java.util.Arrays;
import java.util.List;

/**
 * The Storage Access Framework's URI shapes, as pure functions over a URI's path segments.
 *
 * <p>They are pure (no Android types) so the rule that a picked tree must be addressed through its
 * <em>root document</em> is unit-tested on the host. That rule is what the "Use this folder" crash
 * turned on: {@code ACTION_OPEN_DOCUMENT_TREE} hands back a tree URI
 * ({@code content://<authority>/tree/<treeId>}), and a document provider answers only document URIs -
 * {@code .../tree/<treeId>/document/<documentId>} (or {@code .../document/<documentId>}). A bare tree
 * URI matches none of a provider's registered patterns, so its {@code query} throws
 * {@code UnsupportedOperationException("Unsupported Uri")} and
 * {@code DocumentsContract.getDocumentId(treeUri)} throws {@code IllegalArgumentException}.
 *
 * <p>The shapes mirror AOSP {@code DocumentsContract} ({@code getTreeDocumentId},
 * {@code getDocumentId}, {@code buildDocumentUriUsingTree}) exactly, and are what
 * {@link SafTreeStager} builds the root document URI with.
 */
final class SafTreeUri {

    private SafTreeUri() {
    }

    /**
     * The path segments of the root document URI of the tree whose path segments are given:
     * {@code tree/<treeId>/document/<treeId>}. A tree's root document id is the tree id itself, which
     * is what {@code DocumentFile.fromTreeUri} builds and what a provider resolves as its root.
     */
    static List<String> rootDocumentSegments(List<String> treeSegments) {
        String treeId = treeId(treeSegments);
        return Arrays.asList("tree", treeId, "document", treeId);
    }

    /** The tree id of a tree URI's path segments (AOSP {@code DocumentsContract.getTreeDocumentId}). */
    static String treeId(List<String> segments) {
        if (segments.size() >= 2 && "tree".equals(segments.get(0))) {
            return segments.get(1);
        }
        throw new IllegalArgumentException("Not a tree URI: " + segments);
    }

    /** The document id of a document URI's path segments (AOSP {@code DocumentsContract.getDocumentId}). */
    static String documentId(List<String> segments) {
        if (segments.size() >= 2 && "document".equals(segments.get(0))) {
            return segments.get(1);
        }
        if (segments.size() >= 4 && "tree".equals(segments.get(0)) && "document".equals(segments.get(2))) {
            return segments.get(3);
        }
        throw new IllegalArgumentException("Not a document URI: " + segments);
    }
}
