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

import org.junit.Test;

/**
 * Host tests for {@link SafPickerResult}: how a folder-picker result is classified.
 *
 * <p>Pure-logic tests. They pin the rule {@link SetupActivity#onActivityResult} follows - a foreign
 * request is ignored, a cancel or a null URI keeps the previous choice, and only a real OK result with
 * a URI stages - which is the "cancelled or invalid SAF result" case the crash brief calls out.
 */
public class SafPickerResultTest {

    private static final int PICK = 1;
    private static final int OK = SafPickerResult.RESULT_OK;
    private static final int CANCELLED = 0;

    private static SafPickerResult.Outcome classify(int requestCode, int resultCode, boolean hasUri) {
        return SafPickerResult.classify(PICK, requestCode, resultCode, hasUri);
    }

    @Test
    public void aResultForAnotherRequestIsIgnored() {
        assertEquals(SafPickerResult.Outcome.IGNORE, classify(99, OK, true));
    }

    @Test
    public void aCancelledPickKeepsThePreviousChoice() {
        assertEquals(SafPickerResult.Outcome.CANCELLED, classify(PICK, CANCELLED, false));
    }

    @Test
    public void anOkResultWithNoUriIsTreatedAsCancelled() {
        // A provider that answers RESULT_OK but puts no URI in the Intent must not crash or wipe state.
        assertEquals(SafPickerResult.Outcome.CANCELLED, classify(PICK, OK, false));
    }

    @Test
    public void anOkResultWithAUriStages() {
        assertEquals(SafPickerResult.Outcome.STAGE, classify(PICK, OK, true));
    }

    @Test
    public void aCancelledResultThatStillCarriesAUriIsNotStaged() {
        // The result code is authoritative: a cancel is a cancel even if a URI is present.
        assertEquals(SafPickerResult.Outcome.CANCELLED, classify(PICK, CANCELLED, true));
    }
}
