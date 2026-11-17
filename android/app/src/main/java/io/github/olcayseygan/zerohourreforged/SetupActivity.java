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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

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
 */
public class SetupActivity extends Activity {

    private static final int REQUEST_PICK_FOLDER = 1;

    private TextView status;
    private TextView detail;
    private Button selectButton;
    private Button launchButton;
    private Button clearButton;
    private ProgressBar progress;

    private volatile boolean staging;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!staging) {
            refreshStatus();
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText(R.string.setup_title);
        title.setTextSize(22);
        root.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText(R.string.setup_explanation);
        explanation.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(explanation);

        status = new TextView(this);
        status.setPadding(0, pad / 2, 0, pad / 4);
        root.addView(status);

        detail = new TextView(this);
        detail.setPadding(0, 0, 0, pad / 2);
        root.addView(detail);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        root.addView(progress);

        selectButton = new Button(this);
        selectButton.setText(R.string.setup_select_folder);
        selectButton.setOnClickListener(v -> pickFolder());
        root.addView(selectButton);

        launchButton = new Button(this);
        launchButton.setText(R.string.setup_launch);
        launchButton.setOnClickListener(v -> launchGame());
        root.addView(launchButton);

        clearButton = new Button(this);
        clearButton.setText(R.string.setup_clear_folder);
        clearButton.setOnClickListener(v -> clearFolder());
        root.addView(clearButton);
        return scroll;
    }

    private void refreshStatus() {
        Uri tree = GameFolderStore.treeUri(this);
        if (tree == null) {
            status.setText(R.string.setup_status_none);
            detail.setText(R.string.setup_detail_none);
            launchButton.setEnabled(false);
            clearButton.setEnabled(false);
            return;
        }
        String name = GameFolderStore.sourceName(this);
        if (!GameFolderStore.hasPersistedRead(this)) {
            status.setText(getString(R.string.setup_status_permission_lost, name));
            detail.setText(R.string.setup_detail_permission_lost);
            launchButton.setEnabled(false);
            clearButton.setEnabled(true);
            return;
        }
        GameFolderValidator.Verdict verdict = GameFolderStore.stagedValidation(this);
        if (!verdict.isOk()) {
            status.setText(getString(R.string.setup_status_incomplete, name));
            detail.setText(messageFor(verdict.result));
            launchButton.setEnabled(false);
            clearButton.setEnabled(true);
            return;
        }
        status.setText(getString(R.string.setup_status_ready, name));
        detail.setText(getString(R.string.setup_detail_staging,
                GameFolderStore.engineRoot(this).getAbsolutePath()));
        launchButton.setEnabled(true);
        clearButton.setEnabled(true);
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
        if (requestCode != REQUEST_PICK_FOLDER) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            // Cancelled: keep whatever was already there.
            refreshStatus();
            return;
        }
        Uri tree = data.getData();
        String name = SafTreeStager.displayName(getContentResolver(), tree);
        GameFolderStore.remember(this, tree, name);
        stage(tree);
    }

    /**
     * Copies the picked tree into the real-path staging directory off the UI thread, then reports.
     * The first copy is gigabytes, so it runs on a background thread with a progress bar; a later
     * launch copies only what changed.
     */
    private void stage(Uri tree) {
        staging = true;
        status.setText(R.string.setup_status_staging);
        detail.setText("");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        setButtonsEnabled(false);

        final File stagingRoot = GameFolderStore.stagingRoot(this);
        new Thread(() -> {
            try {
                SafTreeStager.stage(getContentResolver(), tree, stagingRoot,
                        (copied, total) -> runOnUiThread(() -> {
                            if (total > 0) {
                                progress.setIndeterminate(false);
                                progress.setMax((int) Math.min(total, Integer.MAX_VALUE));
                                progress.setProgress((int) Math.min(copied, Integer.MAX_VALUE));
                            }
                        }));
                runOnUiThread(() -> {
                    staging = false;
                    progress.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    refreshStatus();
                    GameFolderValidator.Verdict verdict = GameFolderStore.stagedValidation(this);
                    if (!verdict.isOk()) {
                        showProblem(messageFor(verdict.result));
                    }
                });
            } catch (Exception e) {
                final String message = e.getMessage() != null ? e.getMessage()
                        : getString(R.string.setup_staging_failed);
                runOnUiThread(() -> {
                    staging = false;
                    progress.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    refreshStatus();
                    showProblem(message);
                });
            }
        }, "game-data-staging").start();
    }

    private void setButtonsEnabled(boolean enabled) {
        selectButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
        // The launch button is set only by refreshStatus, from the staged verdict, so the staged tree
        // is walked on the UI thread once per state change rather than on every button update.
        launchButton.setEnabled(false);
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

    private void showProblem(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.setup_problem_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
