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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.ComponentActivity;

import java.io.File;

import io.github.olcayseygan.zerohourreforged.ui.LauncherHost;
import io.github.olcayseygan.zerohourreforged.ui.screens.launcher.LauncherStatus;

/**
 * The launcher screen: pick the folder Zero Hour is installed in, stage it into a real path, start
 * the game.
 *
 * <p>This is the one screen that exists outside the game, and it exists because the engine's file
 * access is POSIX: it needs a real directory it can {@code chdir} into and enumerate, not a
 * {@code content://} URI. The folder picker (the system Storage Access Framework picker, reached
 * with {@code ACTION_OPEN_DOCUMENT_TREE}) is the only way to read an arbitrary folder without the
 * restricted "all files access" permission; the picked tree is then copied once into the app's own
 * external files directory, which is a real path the engine can use. {@link SafTreeStager} does the
 * copy incrementally, so only the first launch copies anything.
 *
 * <p>The game Activity is started only when the staged copy validates, so an incomplete or wrong
 * folder never reaches the engine. The reason it did not is shown here, in words, rather than
 * letting the engine fail with a missing-archive message.
 *
 * <p>The screen itself is Compose ({@code LauncherScreen}), reached through {@link LauncherHost}.
 * The screen is stateless: it renders the state published here. Every decision - the picker, the
 * staging thread, the validation, the launch - stays in this class, unchanged.
 */
public class SetupActivity extends ComponentActivity implements LauncherHost.Callbacks {

    private static final String TAG = "ZH.SetupActivity";
    private static final int REQUEST_PICK_FOLDER = 1;

    private LauncherHost host;

    // The state the Compose screen renders. Written on the UI thread only.
    private LauncherStatus status = LauncherStatus.NO_FOLDER;
    private String statusText = "";
    private String detailText = "";
    private String folderName;
    private String enginePath;
    private long progressCopied;
    private long progressTotal;
    private boolean canLaunch;
    private boolean canClear;

    private volatile boolean staging;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        host = new LauncherHost(this, this);
        host.install();
        Log.i(TAG, "created");
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!staging) {
            refreshStatus();
        }
    }

    @Override
    protected void onDestroy() {
        // The staging thread runs for minutes on a first copy; its completion callback must not touch
        // this Activity's state after the system has destroyed it.
        destroyed = true;
        super.onDestroy();
        Log.i(TAG, "destroyed");
    }

    /** Publishes the current state to the Compose tree. */
    private void publish() {
        if (destroyed) {
            return;
        }
        host.render(status, statusText, detailText, folderName, enginePath,
                staging, progressCopied, progressTotal, canLaunch, canClear);
    }

    /**
     * Reads the backend and publishes what it says.
     *
     * <p>Each branch is one of the launcher's real situations, and each reports the {@link LauncherStatus}
     * that names it. The verdict itself is never re-derived here: {@link GameFolderStore} decides
     * whether the staged copy validates, and {@link GameFolderValidator.Result} says which archive is
     * missing, so the screen can state the real reason rather than a generic failure.
     */
    private void refreshStatus() {
        Uri tree = GameFolderStore.treeUri(this);
        if (tree == null) {
            status = LauncherStatus.NO_FOLDER;
            folderName = null;
            enginePath = null;
            statusText = getString(R.string.setup_status_none);
            detailText = getString(R.string.setup_detail_none);
            canLaunch = false;
            canClear = false;
            publish();
            return;
        }
        // The remembered display name: what the SAF provider called the folder the player picked.
        String name = displayNameOf(tree);
        folderName = name;
        if (!GameFolderStore.hasPersistedRead(this)) {
            status = LauncherStatus.PERMISSION_LOST;
            enginePath = null;
            statusText = getString(R.string.setup_status_permission_lost, name);
            detailText = getString(R.string.setup_detail_permission_lost);
            canLaunch = false;
            canClear = true;
            publish();
            return;
        }
        GameFolderValidator.Verdict verdict = GameFolderStore.stagedValidation(this);
        if (!verdict.isOk()) {
            // A wrong folder and an incomplete one are different problems with different fixes, so
            // they are reported as different states rather than as one "not valid".
            boolean wrongFolder = verdict.result == GameFolderValidator.Result.NO_ZERO_HOUR;
            status = wrongFolder ? LauncherStatus.INVALID : LauncherStatus.INCOMPLETE;
            enginePath = null;
            statusText = getString(
                    wrongFolder ? R.string.setup_status_invalid : R.string.setup_status_incomplete,
                    name);
            detailText = messageFor(verdict.result);
            canLaunch = false;
            canClear = true;
            publish();
            return;
        }
        status = LauncherStatus.READY;
        enginePath = GameFolderStore.engineRoot(this).getAbsolutePath();
        statusText = getString(R.string.setup_status_ready, name);
        detailText = getString(R.string.setup_detail_staging, enginePath);
        canLaunch = true;
        canClear = true;
        publish();
    }

    /**
     * The remembered name of the picked folder, never null.
     *
     * <p>{@link GameFolderStore#sourceName} is null if the name was never stored, which a stale
     * preference can produce. The screen treats a null name as "no folder chosen", so a remembered
     * folder must still have a name: the tree's last path segment is the same fallback
     * {@link GameFolderStore#remember} already applies, so both agree.
     */
    private String displayNameOf(Uri tree) {
        String stored = GameFolderStore.sourceName(this);
        if (stored != null && !stored.isEmpty()) {
            return stored;
        }
        String segment = tree.getLastPathSegment();
        return segment != null ? segment : "";
    }

    /**
     * Publishes an interrupted copy as its own state.
     *
     * <p>Separate from {@link #refreshStatus()} on purpose: that method reports what the staged copy
     * validates to, which after a failure is "incomplete" - a verdict that blames the player's folder
     * for a copy that simply did not finish. The staged copy is kept, so choosing the same folder
     * again resumes incrementally; only the folder's access is offered for removal here.
     */
    private void publishStagingFailed(String failure) {
        status = LauncherStatus.STAGING_FAILED;
        enginePath = null;
        statusText = getString(R.string.setup_staging_failed);
        detailText = failure;
        canLaunch = false;
        canClear = GameFolderStore.treeUri(this) != null;
        publish();
    }

    private String messageFor(GameFolderValidator.Result result) {
        switch (result) {
            case NO_ZERO_HOUR:
                return getString(R.string.setup_detail_no_zero_hour);
            case NO_BASE_GAME:
                return getString(R.string.setup_detail_no_base_game);
            default:
                return "";
        }
    }

    @Override
    public void onSelectFolder() {
        pickFolder();
    }

    @Override
    public void onLaunchGame() {
        launchGame();
    }

    @Override
    public void onClearFolder() {
        clearFolder();
    }

    @Override
    public void onShowRequirements() {
        startActivity(new Intent(this, CompatibilityActivity.class));
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        // The system picker grants read access to the whole tree; the permission is taken as
        // persistable below so it survives a restart. No storage permission is declared.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_FOLDER);
        } catch (android.content.ActivityNotFoundException e) {
            showProblem(getString(R.string.setup_no_picker));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        boolean hasUri = data != null && data.getData() != null;
        SafPickerResult.Outcome outcome = SafPickerResult.classify(
                REQUEST_PICK_FOLDER, requestCode, resultCode, hasUri);
        Log.i(TAG, "picker returned: resultCode=" + resultCode + " outcome=" + outcome);
        if (outcome == SafPickerResult.Outcome.IGNORE) {
            return;
        }
        if (outcome == SafPickerResult.Outcome.CANCELLED) {
            // Cancelled or a malformed result: keep whatever was already there.
            refreshStatus();
            return;
        }
        Uri tree = data.getData();
        try {
            // The picked URI is a tree, not a document. Everything that touches the provider must cope
            // with it being refused (a revoked grant, an exotic provider); nothing here may throw out of
            // the callback, or the app dies on the UI thread. The display name is cosmetic and already
            // falls back on its own; remember() and stage() are defensive below.
            String name = SafTreeStager.displayName(getContentResolver(), tree);
            GameFolderStore.remember(this, tree, name);
            Log.i(TAG, "remembered the picked tree and its grant");
        } catch (RuntimeException e) {
            // A provider refused the tree outright: say so and let the player retry, rather than crash.
            Log.e(TAG, "could not record the picked tree", e);
            showProblem(getString(R.string.setup_folder_unreadable));
            refreshStatus();
            return;
        }
        stage(tree);
    }

    /**
     * Copies the picked tree into the real-path staging directory off the UI thread, then reports.
     * The first copy is gigabytes, so it runs on a background thread with a progress bar; a later
     * launch copies only what changed.
     */
    private void stage(Uri tree) {
        staging = true;
        status = LauncherStatus.STAGING;
        // The folder being copied, so the installation card names it while the copy runs rather than
        // staying on whatever the previous refresh left there.
        folderName = displayNameOf(tree);
        enginePath = null;
        statusText = getString(R.string.setup_status_staging);
        detailText = "";
        progressCopied = 0;
        progressTotal = 0;
        canLaunch = false;
        canClear = false;
        publish();

        final File stagingRoot = GameFolderStore.stagingRoot(this);
        Log.i(TAG, "staging started");
        new Thread(() -> {
            try {
                SafTreeStager.stage(getContentResolver(), tree, stagingRoot,
                        (copied, total) -> runOnUiThread(() -> {
                            if (destroyed) {
                                return;
                            }
                            progressCopied = copied;
                            progressTotal = total;
                            publish();
                        }));
                Log.i(TAG, "staging finished");
                runOnUiThread(() -> onStagingFinished(null));
            } catch (Exception e) {
                // A staging failure is reported to the player, never swallowed and never fatal.
                Log.e(TAG, "staging failed", e);
                final String message = e.getMessage() != null ? e.getMessage()
                        : getString(R.string.setup_staging_failed);
                runOnUiThread(() -> onStagingFinished(message));
            }
        }, "game-data-staging").start();
    }

    /** The staging thread's completion, on the UI thread: report the result or the failure. */
    private void onStagingFinished(String failure) {
        staging = false;
        if (destroyed) {
            // The Activity is gone; the staged files and the stored tree are still valid, so the next
            // launch re-reads them. There is no view to update.
            return;
        }
        progressCopied = 0;
        progressTotal = 0;
        if (failure != null) {
            // The copy stopped, so the folder's own validity is not what to report: the staged copy
            // is partial and the reason is the failure. Publishing the failed state first means the
            // screen still explains itself after the dialog is dismissed, instead of falling back to
            // a "not a complete folder" verdict the player did not cause.
            publishStagingFailed(failure);
            showProblem(failure);
            return;
        }
        // refreshStatus publishes the post-staging state, including whether the game can start.
        refreshStatus();
        GameFolderValidator.Verdict verdict = GameFolderStore.stagedValidation(this);
        if (!verdict.isOk()) {
            showProblem(messageFor(verdict.result));
        }
    }

    private void launchGame() {
        if (!GameFolderStore.stagedValidation(this).isOk()) {
            refreshStatus();
            return;
        }
        startActivity(new Intent(this, ZeroHourActivity.class));
    }

    private void clearFolder() {
        GameFolderStore.clear(this);
        refreshStatus();
    }

    /**
     * Reports a failure the player has to acknowledge.
     *
     * <p>Drawn by the design system's own dialog through the host, so it matches the screen behind it
     * and is owned by the Compose tree. Every caller is already on the UI thread - the picker result
     * and the staging thread's completion both arrive there - which is where Compose state may be
     * written.
     */
    private void showProblem(String message) {
        host.showError(message);
    }
}
