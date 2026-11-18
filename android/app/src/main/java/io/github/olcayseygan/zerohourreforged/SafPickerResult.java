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

/**
 * What a folder-picker result means, as a pure decision (no Android types) so it is unit-tested on
 * the host.
 *
 * <p>The rule is small but easy to get wrong: a result for a different request is ignored, a cancel
 * or a result with no URI is treated as "keep what was there", and only {@code RESULT_OK} with a URI
 * proceeds to staging. Getting the null/URI case wrong is what makes a cancelled pick crash or wipe a
 * previous choice, so the Activity uses this decision rather than repeating it inline.
 */
final class SafPickerResult {

    /** {@code android.app.Activity.RESULT_OK}; repeated here so this class has no Android dependency. */
    static final int RESULT_OK = -1;

    enum Outcome {
        /** A result for another request: nothing to do. */
        IGNORE,
        /** A cancel, or a result with no URI: keep the previous selection. */
        CANCELLED,
        /** A usable tree URI: record it and stage it. */
        STAGE
    }

    private SafPickerResult() {
    }

    static Outcome classify(int expectedRequestCode, int requestCode, int resultCode, boolean hasUri) {
        if (requestCode != expectedRequestCode) {
            return Outcome.IGNORE;
        }
        if (resultCode != RESULT_OK || !hasUri) {
            return Outcome.CANCELLED;
        }
        return Outcome.STAGE;
    }
}
