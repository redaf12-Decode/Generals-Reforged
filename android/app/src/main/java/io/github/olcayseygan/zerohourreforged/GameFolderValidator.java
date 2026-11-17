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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds the Zero Hour installation inside a folder the player picked, and says whether it is
 * complete.
 *
 * <p>The rule is the engine's own: {@code PosixInstallRoot.cpp}'s {@code PosixCheckInstallFolder}
 * requires Zero Hour's {@code INIZH.big} at the install folder's top level and the base game's
 * {@code Textures.big} in one of the places {@code Win32BIGFileSystem::init} looks for it (the
 * folder itself, {@code ZH_Generals}, or a sibling Generals folder reached with {@code ../}). This
 * mirrors that over a list of paths rather than a real directory, so it is unit-tested on the host
 * with no Android types and no files on disk.
 *
 * <p>The player may pick the Zero Hour folder itself, or a folder that contains it (Steam installs
 * the two games as siblings). The verdict names the sub-path of the picked folder the engine should
 * be rooted at, so a staged copy keeps the same shape and the {@code ../} lookups still resolve.
 */
public final class GameFolderValidator {

    private GameFolderValidator() {
    }

    /** Zero Hour's archive; the engine's {@code ZERO_HOUR_ARCHIVE} ({@code PosixInstallRoot.cpp}). */
    public static final String ZERO_HOUR_ARCHIVE = "inizh.big";

    /** The base game's archive; {@code Win32BIGFileSystem}'s {@code BASE_GAME_ARCHIVE}. */
    public static final String BASE_GAME_ARCHIVE = "textures.big";

    /**
     * Where the base game's archives may sit, relative to the Zero Hour folder, lowercased: the
     * engine's {@code BASE_GAME_DIRECTORIES} and {@code FIRST_DECADE_GENERALS_FOLDER}. The
     * {@code ../} entries are resolved inside the picked tree, so a player who picked the folder
     * that holds both games is accepted.
     */
    private static final String[] BASE_GAME_FOLDERS = {
        "",
        "zh_generals/",
        "../command & conquer generals/",
        "../command and conquer generals/",
        "../command & conquer(tm) generals/",
    };

    /** The First Decade keeps the base game in a subfolder of its own folder. */
    private static final String FIRST_DECADE_GENERALS_FOLDER = "command & conquer(tm) generals/";

    public enum Result {
        /** The folder holds Zero Hour and the base game the engine looks for. */
        OK,
        /** No {@code INIZH.big} anywhere in the picked folder. */
        NO_ZERO_HOUR,
        /** Zero Hour is there, but not the base game's {@code Textures.big} where the engine looks. */
        NO_BASE_GAME
    }

    /** What the picked folder is, and where in it the engine should be rooted. */
    public static final class Verdict {
        public final Result result;
        /** {@code ""} when the Zero Hour folder is the picked folder, else its sub-path ending in
         * {@code /}; null when no Zero Hour folder was found. */
        public final String rootPrefix;

        Verdict(Result result, String rootPrefix) {
            this.result = result;
            this.rootPrefix = rootPrefix;
        }

        public boolean isOk() {
            return result == Result.OK;
        }
    }

    /**
     * Classifies a picked tree. {@code relativePaths} are the tree's files, '/'-separated, relative
     * to the tree root (a file at the root has no '/'); case is ignored, as the engine's
     * {@code zh_access} resolves each path component without regard to case.
     */
    public static Verdict validate(Collection<String> relativePaths) {
        Set<String> files = new HashSet<>();
        // The Zero Hour folder is reported back in its original case: the staged copy keeps the
        // picked names, and the filesystem is case-sensitive, so a lowercased prefix would name a
        // directory that does not exist. Matching is done on the lowercased form, as the engine's
        // zh_access resolves each path component without regard to case.
        Map<String, String> directories = new LinkedHashMap<>();
        directories.put("", "");
        for (String path : relativePaths) {
            files.add(path.toLowerCase(Locale.ROOT));
            for (String ancestor : ancestors(path)) {
                directories.putIfAbsent(ancestor.toLowerCase(Locale.ROOT), ancestor);
            }
        }

        // The Zero Hour folder: the shallowest directory with INIZH.big directly in it, so a player
        // who picked a parent gets the game folder inside it, and one who picked the game folder
        // gets that.
        List<String> candidates = new ArrayList<>();
        for (Map.Entry<String, String> directory : directories.entrySet()) {
            if (files.contains(directory.getKey() + ZERO_HOUR_ARCHIVE)) {
                candidates.add(directory.getKey());
            }
        }
        if (candidates.isEmpty()) {
            return new Verdict(Result.NO_ZERO_HOUR, null);
        }
        Collections.sort(candidates, (a, b) -> {
            int byDepth = Integer.compare(depth(a), depth(b));
            return byDepth != 0 ? byDepth : a.compareTo(b);
        });
        String zeroHourLower = candidates.get(0);
        String zeroHour = directories.get(zeroHourLower);

        for (String folder : BASE_GAME_FOLDERS) {
            String base = normalize(zeroHourLower + folder);
            if (files.contains(base + BASE_GAME_ARCHIVE)
                    || files.contains(base + FIRST_DECADE_GENERALS_FOLDER + BASE_GAME_ARCHIVE)) {
                return new Verdict(Result.OK, zeroHour);
            }
        }
        return new Verdict(Result.NO_BASE_GAME, zeroHour);
    }

    /** The '/' ancestors of a file path, excluding the path itself; a file at the root adds none. */
    private static List<String> ancestors(String path) {
        List<String> ancestors = new ArrayList<>();
        int at = path.indexOf('/');
        while (at >= 0) {
            ancestors.add(path.substring(0, at + 1));
            at = path.indexOf('/', at + 1);
        }
        return ancestors;
    }

    private static int depth(String directory) {
        int depth = 0;
        for (int i = 0; i < directory.length(); i++) {
            if (directory.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    /** Resolves {@code ../} and {@code ./} segments; a leading {@code ../} that escapes is kept. */
    private static String normalize(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!parts.isEmpty() && !parts.get(parts.size() - 1).equals("..")) {
                    parts.remove(parts.size() - 1);
                } else {
                    parts.add(part);
                }
            } else {
                parts.add(part);
            }
        }
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            out.append(part).append('/');
        }
        return out.toString();
    }
}
