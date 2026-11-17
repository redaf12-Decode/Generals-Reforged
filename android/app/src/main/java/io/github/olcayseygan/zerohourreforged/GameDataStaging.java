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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The staging plan: which files to copy from the folder the player picked into the app's real-path
 * staging directory, and which staged files to remove.
 *
 * <p>The engine's file access is C++ POSIX - {@code chdir}, {@code opendir}/{@code readdir},
 * {@code stat}, {@code open}, {@code realpath} (see {@code PosixLocalFileSystem.cpp},
 * {@code Win32BIGFileSystem.cpp}) - and the Storage Access Framework hands back {@code content://}
 * URIs, which no POSIX call can open. The game data therefore has to exist at a real path the app
 * can open, and this class decides the smallest set of files that gets it there.
 *
 * <p>The decision is a pure function of the source tree and the staged tree's recorded state, so it
 * is unit-tested on the host with no Android types. The first staging copies everything; every
 * later one copies only files whose size or modification time changed and deletes files the source
 * no longer has, so a launch after the first copies nothing when the game data has not moved.
 */
public final class GameDataStaging {

    private GameDataStaging() {
    }

    /** Staging's progress, for a first-run screen that copies gigabytes. */
    public interface ProgressListener {
        void onProgress(long bytesCopied, long bytesTotal);
    }

    /** One file in the source tree (the picked folder) or the staged tree. */
    public static final class FileState {
        /** Path below the tree root, '/'-separated; a file at the root has no '/'. */
        public final String relativePath;
        public final long size;
        /** Milliseconds since the epoch, or 0 when the provider does not report one. */
        public final long lastModified;

        public FileState(String relativePath, long size, long lastModified) {
            this.relativePath = relativePath;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    /** What staging must do to make the staged tree match the source tree. */
    public static final class Plan {
        public final List<FileState> toCopy;
        public final List<String> toDelete;
        private final Set<String> toCopyPaths;

        Plan(List<FileState> toCopy, List<String> toDelete) {
            this.toCopy = Collections.unmodifiableList(toCopy);
            this.toDelete = Collections.unmodifiableList(toDelete);
            Set<String> paths = new HashSet<>();
            for (FileState file : toCopy) {
                paths.add(file.relativePath);
            }
            this.toCopyPaths = Collections.unmodifiableSet(paths);
        }

        /** Whether this exact source file has to be copied (the plan's {@code toCopy} membership). */
        public boolean copies(FileState file) {
            return toCopyPaths.contains(file.relativePath);
        }

        /** The bytes the copy will read, for the free-space check before it starts. */
        public long bytesToCopy() {
            long total = 0;
            for (FileState file : toCopy) {
                total += file.size;
            }
            return total;
        }

        public boolean isNoOp() {
            return toCopy.isEmpty() && toDelete.isEmpty();
        }
    }

    /**
     * The plan that turns {@code staged} into {@code source}. A file is copied when the staged tree
     * does not have it, when its size differs, or when both sizes are known and the modification
     * time differs (a re-stamped file with an older time is still a different file). A staged file
     * the source no longer has is deleted.
     */
    public static Plan plan(List<FileState> source, List<FileState> staged) {
        Map<String, FileState> stagedByPath = new HashMap<>();
        for (FileState file : staged) {
            stagedByPath.put(file.relativePath, file);
        }
        List<FileState> toCopy = new ArrayList<>();
        for (FileState file : source) {
            FileState have = stagedByPath.remove(file.relativePath);
            boolean changed = have == null || have.size != file.size
                    || (file.lastModified != 0 && have.lastModified != 0
                        && have.lastModified != file.lastModified);
            if (changed) {
                toCopy.add(file);
            }
        }
        // Whatever the source did not claim is stale.
        List<String> toDelete = new ArrayList<>(stagedByPath.keySet());
        Collections.sort(toDelete);
        return new Plan(toCopy, toDelete);
    }

    /**
     * The staged tree's state, one file per line: {@code <size>\t<lastModified>\t<relativePath>}.
     * A path the line format cannot carry is left out, so that file is re-copied rather than
     * silently trusted from a line that would parse back wrong.
     */
    public static String serialize(List<FileState> files) {
        StringBuilder text = new StringBuilder();
        for (FileState file : files) {
            if (file.relativePath.indexOf('\n') >= 0 || file.relativePath.indexOf('\t') >= 0) {
                continue;
            }
            text.append(file.size).append('\t').append(file.lastModified).append('\t')
                    .append(file.relativePath).append('\n');
        }
        return text.toString();
    }

    /** {@link #serialize}'s reader. A corrupt line is dropped, so its file is re-copied. */
    public static List<FileState> parse(String text) {
        List<FileState> files = new ArrayList<>();
        if (text == null) {
            return files;
        }
        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int first = line.indexOf('\t');
            int second = first < 0 ? -1 : line.indexOf('\t', first + 1);
            if (first < 0 || second < 0) {
                continue;
            }
            try {
                long size = Long.parseLong(line.substring(0, first));
                long modified = Long.parseLong(line.substring(first + 1, second));
                files.add(new FileState(line.substring(second + 1), size, modified));
            } catch (NumberFormatException ignored) {
                // dropped: the file is re-copied rather than trusted from a line that did not parse
            }
        }
        return files;
    }
}
