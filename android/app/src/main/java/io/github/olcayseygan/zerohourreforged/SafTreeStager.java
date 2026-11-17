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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a folder the player granted through the system folder picker ({@code content://} tree) and
 * stages it into a real filesystem directory the engine can open.
 *
 * <p>The Storage Access Framework is the only way to read an arbitrary folder without the
 * restricted "all files access" permission, and it returns {@code content://} URIs. The engine
 * cannot open those - its file layer is POSIX {@code open}/{@code opendir}/{@code stat}/{@code chdir}
 * - so the files are copied, once, into the app's own external files directory
 * ({@code getExternalFilesDir()}), which is a real path the app may always write with no permission.
 * The copy is incremental (see {@link GameDataStaging}), so it happens once and later launches
 * copy nothing unless the picked folder changed.
 *
 * <p>The tree walk and the copy use the platform's own {@link DocumentsContract} and
 * {@link ContentResolver} - no third-party dependency - so the app still declares no storage
 * permission and adds no library.
 */
public final class SafTreeStager {

    private SafTreeStager() {
    }

    /** Raised when the picked tree cannot be read or staged; the message is shown to the player. */
    public static final class StagingException extends IOException {
        public StagingException(String message, Throwable cause) {
            super(message, cause);
        }

        public StagingException(String message) {
            super(message);
        }
    }

    /** A file's state while it is being staged, and the plan {@link GameDataStaging} works out. */
    public static final class Scan {
        public final List<GameDataStaging.FileState> files;
        /** Document URIs, in the same order as {@code files}. */
        public final List<Uri> uris;

        Scan(List<GameDataStaging.FileState> files, List<Uri> uris) {
            this.files = files;
            this.uris = uris;
        }
    }

    /**
     * The files below a tree URI, recursively. A directory the provider refuses to list is skipped
     * (the engine treats a missing directory as missing data, not a crash); a file the provider
     * cannot stat is left out, so staging copies it again rather than trusting a size of zero.
     */
    public static Scan scan(ContentResolver resolver, Uri treeUri) throws IOException {
        List<GameDataStaging.FileState> files = new ArrayList<>();
        List<Uri> uris = new ArrayList<>();
        walk(resolver, treeUri, "", files, uris);
        return new Scan(files, uris);
    }

    private static void walk(ContentResolver resolver, Uri directory, String prefix,
            List<GameDataStaging.FileState> files, List<Uri> uris) throws IOException {
        List<Document> children = listChildren(resolver, directory);
        for (Document child : children) {
            String path = prefix + child.name;
            if (child.isDirectory) {
                walk(resolver, child.uri, path + "/", files, uris);
            } else if (child.size >= 0) {
                files.add(new GameDataStaging.FileState(path, child.size, child.lastModified));
                uris.add(child.uri);
            }
        }
    }

    private static final class Document {
        final Uri uri;
        final String name;
        final boolean isDirectory;
        final long size;
        final long lastModified;

        Document(Uri uri, String name, boolean isDirectory, long size, long lastModified) {
            this.uri = uri;
            this.name = name;
            this.isDirectory = isDirectory;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    private static List<Document> listChildren(ContentResolver resolver, Uri directory) throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                directory, DocumentsContract.getDocumentId(directory));
        String[] columns = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        };
        List<Document> documents = new ArrayList<>();
        try (Cursor cursor = resolver.query(children, columns, null, null, null)) {
            if (cursor == null) {
                return documents;
            }
            // A provider is free to omit a column; the required ones (id, name, mime) are looked up
            // by index and a row without them is skipped, and the optional size/time are absent for
            // some providers (size -1 means "unknown", so the file is copied rather than trusted).
            int idAt = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameAt = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeAt = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeAt = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
            int modifiedAt = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            while (cursor.moveToNext()) {
                String id = idAt >= 0 ? cursor.getString(idAt) : null;
                String name = nameAt >= 0 ? cursor.getString(nameAt) : null;
                String mime = mimeAt >= 0 ? cursor.getString(mimeAt) : null;
                if (id == null || name == null) {
                    continue;
                }
                boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                long size = sizeAt < 0 || cursor.isNull(sizeAt) ? -1 : cursor.getLong(sizeAt);
                long modified = modifiedAt < 0 || cursor.isNull(modifiedAt)
                        ? 0 : cursor.getLong(modifiedAt);
                documents.add(new Document(
                        DocumentsContract.buildDocumentUriUsingTree(directory, id),
                        name, isDirectory, size, modified));
            }
        } catch (IllegalArgumentException | SecurityException e) {
            // The provider refused this directory (a permission revoked mid-walk, or an exotic
            // provider): the files under it are simply absent from the scan, which the validator
            // and the engine both treat as missing data.
        }
        return documents;
    }

    /**
     * Brings {@code stagingRoot} up to date with the tree at {@code treeUri}: copies what changed,
     * deletes what the source no longer has, and records the result in a manifest beside it.
     * {@code onProgress} is called with the number of bytes copied so far, from the copying thread.
     */
    public static void stage(ContentResolver resolver, Uri treeUri, File stagingRoot,
            GameDataStaging.ProgressListener onProgress) throws IOException {
        Scan scan = scan(resolver, treeUri);
        // A copy interrupted before its rename leaves a ".part" beside the real file; it is not in
        // the manifest, so it is not the engine's and is cleared before this run plans anything.
        deleteInterruptedCopies(stagingRoot);
        // The recorded state is trusted only for files that are still there at the recorded size: a
        // file the engine deleted from the root (it removes Data/INI/INIZH.big at start-up, as on
        // desktop) or a copy a crash truncated is re-copied rather than assumed present.
        List<GameDataStaging.FileState> recorded = new ArrayList<>();
        for (GameDataStaging.FileState file : GameDataStaging.parse(readManifest(stagingRoot))) {
            File staged = new File(stagingRoot, file.relativePath);
            if (staged.isFile() && staged.length() == file.size) {
                recorded.add(file);
            }
        }
        GameDataStaging.Plan plan = GameDataStaging.plan(scan.files, recorded);

        if (!stagingRoot.isDirectory() && !stagingRoot.mkdirs()) {
            throw new StagingException("Could not make the game data folder " + stagingRoot);
        }

        // The copy is gigabytes on the first run. Refuse clearly rather than filling the volume.
        long needed = plan.bytesToCopy();
        long available = stagingRoot.getUsableSpace();
        if (needed > available) {
            throw new StagingException("Not enough space: the game data needs "
                    + (needed / (1024 * 1024)) + " MB but only " + (available / (1024 * 1024))
                    + " MB is free.");
        }

        // Deletions first, so a rename does not need room for both copies at once.
        for (String relative : plan.toDelete) {
            File stale = new File(stagingRoot, relative);
            if (stale.exists() && !stale.delete()) {
                throw new StagingException("Could not remove the old file " + relative);
            }
        }

        long copied = 0;
        List<GameDataStaging.FileState> done = new ArrayList<>();
        for (int i = 0; i < scan.files.size(); i++) {
            GameDataStaging.FileState file = scan.files.get(i);
            File target = new File(stagingRoot, file.relativePath);
            boolean copy = plan.copies(file);
            if (copy) {
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new StagingException("Could not make the folder for " + file.relativePath);
                }
                copyFile(resolver, scan.uris.get(i), target);
                copied += file.size;
                if (onProgress != null) {
                    onProgress.onProgress(copied, plan.bytesToCopy());
                }
            }
            // Re-stat the file we actually wrote: its size is what the next launch compares against.
            done.add(new GameDataStaging.FileState(file.relativePath, target.length(),
                    file.lastModified));
        }

        writeManifest(stagingRoot, GameDataStaging.serialize(done));
    }

    private static void copyFile(ContentResolver resolver, Uri source, File target) throws IOException {
        // Written to a ".part" and renamed into place, so a copy the player interrupts or the OS
        // kills never leaves a half file the engine would open as if it were whole.
        File temporary = new File(target.getPath() + ".part");
        try {
            try (InputStream in = resolver.openInputStream(source)) {
                if (in == null) {
                    throw new StagingException("Could not read " + source);
                }
                try (OutputStream out = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[1 << 16];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
            }
        } catch (SecurityException e) {
            temporary.delete();
            throw new StagingException("Access to the game folder was refused; pick it again.", e);
        } catch (IOException e) {
            temporary.delete();
            throw e;
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new StagingException("Could not finish writing " + target.getName());
        }
    }

    private static String readManifest(File stagingRoot) {
        File manifest = manifestFile(stagingRoot);
        if (!manifest.isFile()) {
            return "";
        }
        try {
            byte[] bytes = new byte[(int) manifest.length()];
            try (InputStream in = new java.io.FileInputStream(manifest)) {
                int read = in.read(bytes);
                return read > 0 ? new String(bytes, 0, read, java.nio.charset.StandardCharsets.UTF_8) : "";
            }
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeManifest(File stagingRoot, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(manifestFile(stagingRoot))) {
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static File manifestFile(File stagingRoot) {
        return new File(stagingRoot, ".staged-manifest");
    }

    private static void deleteInterruptedCopies(File directory) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                deleteInterruptedCopies(child);
            } else if (child.getName().endsWith(".part")) {
                child.delete();
            }
        }
    }

    /** The app's staging directory: a real path under the app's own external files directory. */
    public static File stagingRoot(Context context) {
        File external = context.getExternalFilesDir(null);
        // getExternalFilesDir answers null only when external storage is unavailable; the internal
        // files directory is the fallback, and is always a real writable path.
        File base = external != null ? external : context.getFilesDir();
        return new File(base, "gamedata");
    }

    /** The picked tree's display name, for the setup screen; null when the provider does not say. */
    public static String displayName(ContentResolver resolver, Uri treeUri) {
        String[] columns = { DocumentsContract.Document.COLUMN_DISPLAY_NAME };
        try (Cursor cursor = resolver.query(treeUri, columns, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (IllegalArgumentException | SecurityException ignored) {
            // fall through to the URI's own last segment
        }
        return treeUri.getLastPathSegment();
    }
}
