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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Remembers the folder the player picked and whether it is still usable, so the picker runs once.
 *
 * <p>What is persisted is the storage-access-framework tree URI and the read permission the system
 * granted for it, taken as a persistable grant so it survives a restart (the storage guide's
 * "Persist permissions"). The staged copy lives at a real path under the app's own external files
 * directory ({@link SafTreeStager#stagingRoot}), which the app may always write with no permission;
 * that path is what the engine is rooted at.
 *
 * <p>{@link #isReady} is the start-up check: a URI that is still granted, and staged data that still
 * validates. A revoked permission, a deleted or moved source folder, or an incomplete staging all
 * make it false, and the setup screen asks for the folder again.
 */
public final class GameFolderStore {

    private static final String TAG = "ZH.GameFolderStore";
    private static final String PREFERENCES = "game_folder";
    private static final String KEY_TREE_URI = "tree_uri";
    private static final String KEY_SOURCE_NAME = "source_name";

    private GameFolderStore() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /** Records a freshly picked tree and takes the read permission so it survives a restart. */
    public static void remember(Context context, Uri treeUri, String sourceName) {
        try {
            context.getContentResolver().takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // The grant was not persistable (an older provider): the permission still holds for this
            // run, and the next launch will ask again. Not fatal.
            Log.w(TAG, "the picked tree's read grant is not persistable", e);
        } catch (RuntimeException e) {
            // A provider that rejects the persist request must not abort the pick: the run still has
            // the grant, and the next launch asks again. Recorded, not swallowed silently.
            Log.w(TAG, "could not persist the picked tree's read grant", e);
        }
        preferences(context).edit()
                .putString(KEY_TREE_URI, treeUri.toString())
                .putString(KEY_SOURCE_NAME, sourceName != null ? sourceName : treeUri.getLastPathSegment())
                .apply();
    }

    /** The picked tree URI, or null when no folder has been chosen. */
    public static Uri treeUri(Context context) {
        String value = preferences(context).getString(KEY_TREE_URI, null);
        return value != null ? Uri.parse(value) : null;
    }

    /** The picked folder's display name, for the setup screen. */
    public static String sourceName(Context context) {
        return preferences(context).getString(KEY_SOURCE_NAME, null);
    }

    /** The real filesystem directory the staged copy lives in (the picked tree's shape, unchanged). */
    public static File stagingRoot(Context context) {
        return SafTreeStager.stagingRoot(context);
    }

    /** Whether the staged copy still holds Zero Hour and the base game the engine needs. */
    public static GameFolderValidator.Verdict stagedValidation(Context context) {
        return GameFolderValidator.validate(relativeFiles(stagingRoot(context)));
    }

    /**
     * The real filesystem path the engine is rooted at: the Zero Hour folder inside the staged copy.
     * A player who picked the Zero Hour folder itself is rooted at the staging root; one who picked
     * a folder that holds it (Steam's two siblings) is rooted one level down, so the base game the
     * engine looks for with {@code ../} is still beside it.
     */
    public static File engineRoot(Context context) {
        GameFolderValidator.Verdict verdict = stagedValidation(context);
        String prefix = verdict.rootPrefix;
        if (prefix == null || prefix.isEmpty()) {
            return stagingRoot(context);
        }
        return new File(stagingRoot(context), prefix.substring(0, prefix.length() - 1));
    }

    /**
     * Whether the game can start now: a folder was picked, its read permission is still held, and
     * the staged copy still validates. Anything else sends the player back to the setup screen.
     */
    public static boolean isReady(Context context) {
        if (treeUri(context) == null) {
            return false;
        }
        if (!hasPersistedRead(context)) {
            return false;
        }
        return stagedValidation(context).isOk();
    }

    /** Whether a persisted read permission for the picked tree is still held. */
    public static boolean hasPersistedRead(Context context) {
        Uri uri = treeUri(context);
        if (uri == null) {
            return false;
        }
        for (android.content.UriPermission permission
                : context.getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(uri) && permission.isReadPermission()) {
                return true;
            }
        }
        return false;
    }

    /** Forgets the picked folder and its permission; the staged copy is left for a re-pick to reuse. */
    public static void clear(Context context) {
        Uri uri = treeUri(context);
        if (uri != null) {
            try {
                context.getContentResolver().releasePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // not held any more: nothing to release
            }
        }
        preferences(context).edit().remove(KEY_TREE_URI).remove(KEY_SOURCE_NAME).apply();
    }

    /** Every file below {@code root}, '/'-separated, for the validator. */
    private static List<String> relativeFiles(File root) {
        List<String> files = new ArrayList<>();
        collect(root, "", files);
        return files;
    }

    private static void collect(File directory, String prefix, List<String> files) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.getName().equals(".staged-manifest")) {
                continue;
            }
            String path = prefix + child.getName();
            if (child.isDirectory()) {
                collect(child, path + "/", files);
            } else {
                files.add(path);
            }
        }
    }
}
