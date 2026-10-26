package com.cph.att;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cph.att.backup.BackupManager;
import com.cph.att.core.AppConfig;
import com.cph.att.core.Fmt;
import com.cph.att.core.TimeCalc;
import com.cph.att.core.WorkCalc;
import com.cph.att.data.Repo;
import com.cph.att.databinding.ActivitySettingsBinding;
import com.cph.att.databinding.ItemSettingRowBinding;
import com.cph.att.databinding.ItemSettingSwitchBinding;
import com.cph.att.storage.Storage;
import com.cph.att.ui.SweetDialog;
import com.cph.att.ui.Ui;
import com.cph.att.ui.UpdateFlow;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_STORAGE = 101;
    private static final int REQ_RESTORE_FILE = 102;

    private ActivitySettingsBinding b;
    private Repo repo;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Runnable pendingStorageAction;

    private interface IntResult {
        void accept(int value);
    }

    private interface TextResult {
        void accept(String value);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        b = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.applyTopInset(b.header);

        repo = Repo.get(this);
        b.btnBack.setOnClickListener(v -> finish());

        wireShiftRows();
        wireBackupRows();
        wireUpdateRows();
        wireAboutRows();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        AppConfig c = repo.config();

        row(b.rowShiftStart, R.drawable.ic_clock, R.string.settings_shift_start,
                TimeCalc.format12(c.shiftStart));
        row(b.rowShiftEnd, R.drawable.ic_clock, R.string.settings_shift_end,
                TimeCalc.format12(c.shiftEnd));
        row(b.rowGrace, R.drawable.ic_timer, R.string.settings_grace,
                c.graceMinutes + " minutes");
        row(b.rowLunch, R.drawable.ic_coffee, R.string.settings_lunch,
                c.lunchBreak + " minutes");
        row(b.rowCoffee, R.drawable.ic_coffee, R.string.settings_coffee,
                c.coffeeBreak + " minutes");
        row(b.rowOtMode, R.drawable.ic_timer, R.string.settings_ot_mode,
                c.otMode == WorkCalc.OT_MODE_AFTER_SHIFT
                        ? "After shift (OT out only)"
                        : "Punch pair (OT in + OT out)");
        row(b.rowOtMultiplier, R.drawable.ic_chart, R.string.settings_ot_multiplier,
                String.format(java.util.Locale.US, "x%.2f", c.otMultiplier));
        row(b.rowCurrency, R.drawable.ic_database, R.string.settings_currency,
                c.currency == null || c.currency.isEmpty() ? "none" : c.currency);

        row(b.rowBackupNow, R.drawable.ic_cloud_done, R.string.settings_backup_now,
                Storage.describeLocation());
        row(b.rowRestore, R.drawable.ic_restore, R.string.settings_restore,
                "Pick from saved snapshots");
        row(b.rowRestoreFile, R.drawable.ic_upload, R.string.settings_restore_file,
                "Restore from any .db file");
        row(b.rowExport, R.drawable.ic_export, R.string.settings_export_csv,
                "Downloads/" + Storage.ROOT + "/exports");
        row(b.rowStorage, R.drawable.ic_database, R.string.settings_storage,
                "Downloads/" + Storage.ROOT);

        row(b.rowCheckUpdate, R.drawable.ic_refresh, R.string.settings_update_check,
                "Installed " + BuildConfig.VERSION_NAME);
        row(b.rowVersion, R.drawable.ic_info, R.string.settings_version,
                BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        row(b.rowPrivacy, R.drawable.ic_shield, R.string.settings_privacy_title,
                "Local SQLite · offline first");

        b.txtLastBackup.setText(getString(R.string.settings_last_backup) + ": "
                + (c.lastBackupAt > 0 ? Fmt.stamp(c.lastBackupAt) : getString(R.string.settings_never)));

        switchRow(b.switchAutoBackup, R.string.settings_autobackup,
                R.string.settings_autobackup_desc, c.autoBackup);
        switchRow(b.switchAutoUpdate, R.string.settings_update_auto,
                R.string.settings_update_auto_desc, c.autoUpdateCheck);

        b.txtBuild.setText("com.cph.att · built for Android 5.0+");
    }

    private void row(ItemSettingRowBinding binding, int icon, int titleRes, String value) {
        binding.title.setText(titleRes);
        binding.icon.setImageResource(icon);
        binding.icon.setColorFilter(ContextCompat.getColor(this, R.color.accent));
        binding.value.setText(value);
    }

    private void switchRow(ItemSettingSwitchBinding binding, int titleRes, int subtitleRes,
                           boolean checked) {
        binding.title.setText(titleRes);
        binding.subtitle.setText(subtitleRes);
        binding.switchView.setChecked(checked);
    }

    // ------------------------------------------------------------ shift rules

    private void wireShiftRows() {
        b.rowShiftStart.getRoot().setOnClickListener(v ->
                editTime(R.string.settings_shift_start, repo.config().shiftStart, value -> {
                    repo.config().shiftStart = value;
                    repo.saveConfig();
                    render();
                }));

        b.rowShiftEnd.getRoot().setOnClickListener(v ->
                editTime(R.string.settings_shift_end, repo.config().shiftEnd, value -> {
                    repo.config().shiftEnd = value;
                    repo.saveConfig();
                    render();
                }));

        b.rowGrace.getRoot().setOnClickListener(v ->
                editNumber(R.string.settings_grace, repo.config().graceMinutes, value -> {
                    repo.config().graceMinutes = value;
                    repo.saveConfig();
                    render();
                }));

        b.rowLunch.getRoot().setOnClickListener(v ->
                editNumber(R.string.settings_lunch, repo.config().lunchBreak, value -> {
                    repo.config().lunchBreak = value;
                    repo.saveConfig();
                    render();
                }));

        b.rowCoffee.getRoot().setOnClickListener(v ->
                editNumber(R.string.settings_coffee, repo.config().coffeeBreak, value -> {
                    repo.config().coffeeBreak = value;
                    repo.saveConfig();
                    render();
                }));

        b.rowOtMode.getRoot().setOnClickListener(v -> {
            String[] options = {"Punch pair (OT in + OT out)", "After shift (OT out only)"};
            SweetDialog.builder(this)
                    .type(SweetDialog.TYPE_QUESTION)
                    .title(getString(R.string.settings_ot_mode))
                    .items(options, (dialog, index) -> {
                        repo.config().otMode = index == 0
                                ? WorkCalc.OT_MODE_PAIR : WorkCalc.OT_MODE_AFTER_SHIFT;
                        repo.saveConfig();
                        render();
                    })
                    .cancelText(getString(R.string.dlg_cancel))
                    .build()
                    .show();
        });

        b.rowOtMultiplier.getRoot().setOnClickListener(v ->
                editDecimal(R.string.settings_ot_multiplier, repo.config().otMultiplier, value -> {
                    if (value < 1.0) {
                        SweetDialog.warning(this, "Too low",
                                "The OT multiplier should be at least 1.00.");
                        return;
                    }
                    repo.config().otMultiplier = value;
                    repo.saveConfig();
                    render();
                }));

        b.rowCurrency.getRoot().setOnClickListener(v ->
                SweetDialog.builder(this)
                        .type(SweetDialog.TYPE_QUESTION)
                        .title(getString(R.string.settings_currency))
                        .message("Used in reports. Leave empty for no symbol.")
                        .input("e.g. ₱ or $", repo.config().currency)
                        .confirmText(getString(R.string.dlg_save))
                        .cancelText(getString(R.string.dlg_cancel))
                        .onConfirm(dialog -> {
                            repo.config().currency = dialog.inputText();
                            repo.saveConfig();
                            dialog.dismiss();
                            render();
                        })
                        .build()
                        .show());
    }

    // -------------------------------------------------------------- data rows

    private void wireBackupRows() {
        b.switchAutoBackup.getRoot().setOnClickListener(v -> {
            repo.config().autoBackup = !repo.config().autoBackup;
            repo.saveConfig();
            render();
        });

        b.rowBackupNow.getRoot().setOnClickListener(v -> ensureStorage(this::doBackup));
        b.rowRestore.getRoot().setOnClickListener(v -> showRestorePicker());
        b.rowRestoreFile.getRoot().setOnClickListener(v -> pickRestoreFile());
        b.rowExport.getRoot().setOnClickListener(v -> ensureStorage(this::chooseExport));
        b.rowStorage.getRoot().setOnClickListener(v ->
                SweetDialog.info(this, "Storage",
                        "Backups: Downloads/" + Storage.ROOT + "/backups\n"
                                + "Updates: Downloads/" + Storage.ROOT + "/updates\n"
                                + "Exports: Downloads/" + Storage.ROOT + "/exports\n\n"
                                + "The database itself stays in the app's private folder."));
    }

    private void doBackup() {
        final SweetDialog loading = SweetDialog.loading(this,
                getString(R.string.dlg_loading), "Writing a snapshot…");
        worker.execute(() -> {
            final BackupManager.Result result = BackupManager.get(this).backupNow();
            runOnUiThread(() -> {
                loading.dismiss();
                if (result.ok) {
                    SweetDialog.success(this, getString(R.string.dlg_backup_done), result.message);
                } else {
                    SweetDialog.error(this, getString(R.string.dlg_error), result.message);
                }
                render();
            });
        });
    }

    private void chooseExport() {
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title(getString(R.string.export_title))
                .message(getString(R.string.export_message))
                .items(new String[]{getString(R.string.export_excel),
                        getString(R.string.export_csv)}, (dialog, index) -> doExport(index == 0))
                .cancelText(getString(R.string.dlg_cancel))
                .build()
                .show();
    }

    private void doExport(final boolean excel) {
        final String month = Fmt.monthKey(Fmt.todayKey());
        final SweetDialog loading = SweetDialog.loading(this, getString(R.string.dlg_loading),
                getString(excel ? R.string.export_building_excel : R.string.export_building_csv));
        worker.execute(() -> {
            final BackupManager.Result result = excel
                    ? BackupManager.get(this).exportXlsx(Fmt.firstDayOfMonth(month),
                            Fmt.lastDayOfMonth(month), 0)
                    : BackupManager.get(this).exportCsv(Fmt.firstDayOfMonth(month),
                            Fmt.lastDayOfMonth(month), 0);
            runOnUiThread(() -> {
                loading.dismiss();
                if (result.ok) {
                    SweetDialog.success(this, getString(R.string.dlg_export_done), result.message);
                } else {
                    SweetDialog.error(this, getString(R.string.dlg_error), result.message);
                }
            });
        });
    }

    private void showRestorePicker() {
        List<Storage.StoredFile> backups = BackupManager.get(this).listBackups();
        if (backups.isEmpty()) {
            SweetDialog.warning(this, getString(R.string.dlg_no_backups),
                    getString(R.string.dlg_no_backups_msg));
            return;
        }
        final List<Storage.StoredFile> files = backups;
        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            Storage.StoredFile f = files.get(i);
            labels[i] = f.name + "\n" + Fmt.stamp(f.modified) + " · " + f.displaySize();
        }
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title(getString(R.string.settings_restore))
                .items(labels, (dialog, index) -> confirmRestore(files.get(index)))
                .cancelText(getString(R.string.dlg_cancel))
                .build()
                .show();
    }

    private void confirmRestore(final Storage.StoredFile file) {
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_WARNING)
                .title(getString(R.string.dlg_restore))
                .message(getString(R.string.dlg_restore_msg) + "\n\n" + file.name)
                .confirmText(getString(R.string.dlg_restore))
                .cancelText(getString(R.string.dlg_cancel))
                .onConfirm(dialog -> {
                    dialog.dismiss();
                    runRestore(() -> BackupManager.get(this).restoreFrom(file));
                })
                .build()
                .show();
    }

    private void pickRestoreFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_RESTORE_FILE);
        } catch (Exception e) {
            SweetDialog.error(this, getString(R.string.dlg_error),
                    "No file picker available on this device.");
        }
    }

    private void runRestore(RestoreCall call) {
        final SweetDialog loading = SweetDialog.loading(this,
                getString(R.string.dlg_loading), "Restoring your data…");
        worker.execute(() -> {
            final boolean ok = call.run();
            runOnUiThread(() -> {
                loading.dismiss();
                if (ok) {
                    SweetDialog.builder(this)
                            .type(SweetDialog.TYPE_SUCCESS)
                            .title(getString(R.string.dlg_restored))
                            .message("Your records are back. The app will restart to load them.")
                            .confirmText(getString(R.string.dlg_ok))
                            .cancelable(false)
                            .onConfirm(dialog -> {
                                dialog.dismiss();
                                restartApp();
                            })
                            .build()
                            .show();
                } else {
                    SweetDialog.error(this, getString(R.string.dlg_error),
                            "That file is not a valid Attendance Tracker backup.");
                }
            });
        });
    }

    private interface RestoreCall {
        boolean run();
    }

    private void restartApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }

    // ------------------------------------------------------------- update rows

    private void wireUpdateRows() {
        b.switchAutoUpdate.getRoot().setOnClickListener(v -> {
            repo.config().autoUpdateCheck = !repo.config().autoUpdateCheck;
            repo.saveConfig();
            render();
        });

        b.rowCheckUpdate.getRoot().setOnClickListener(v -> UpdateFlow.check(this, false));
    }

    private void wireAboutRows() {
        b.rowVersion.getRoot().setOnClickListener(v ->
                SweetDialog.info(this, getString(R.string.app_name),
                        "Version " + BuildConfig.VERSION_NAME
                                + " (" + BuildConfig.VERSION_CODE + ")\n\n"
                                + "Offline attendance tracking with six punches, overtime, "
                                + "automatic backups and self updates."));

        b.rowPrivacy.getRoot().setOnClickListener(v ->
                SweetDialog.info(this, "Your data stays yours",
                        "Everything is stored in a local SQLite database on this device.\n\n"
                                + "Backups are plain .db files inside Downloads/AttendanceTracker. "
                                + "Nothing is uploaded anywhere."));
    }

    // ------------------------------------------------------------------ input

    private void editTime(int titleRes, int current, final IntResult result) {
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title(getString(titleRes))
                .message("Type a time like 9:00 AM or 18:00.")
                .input("9:00 AM", TimeCalc.format12(current))
                .confirmText(getString(R.string.dlg_save))
                .cancelText(getString(R.string.dlg_cancel))
                .onConfirm(dialog -> {
                    Integer value = TimeCalc.parseTime(dialog.inputText());
                    if (value == null) {
                        dialog.dismiss();
                        SweetDialog.error(this, "Invalid time",
                                "Use 9:00 AM, 6:00 PM or 18:00.");
                        return;
                    }
                    dialog.dismiss();
                    result.accept(value);
                })
                .build()
                .show();
    }

    private void editNumber(int titleRes, int current, final IntResult result) {
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title(getString(titleRes))
                .message("Minutes, whole numbers only.")
                .input("minutes", String.valueOf(current))
                .numericInput(true)
                .confirmText(getString(R.string.dlg_save))
                .cancelText(getString(R.string.dlg_cancel))
                .onConfirm(dialog -> {
                    int value;
                    try {
                        value = Integer.parseInt(dialog.inputText());
                    } catch (NumberFormatException e) {
                        dialog.dismiss();
                        SweetDialog.error(this, "Invalid number", "Type a whole number.");
                        return;
                    }
                    if (value < 0) {
                        dialog.dismiss();
                        SweetDialog.error(this, "Invalid number", "It cannot be negative.");
                        return;
                    }
                    dialog.dismiss();
                    result.accept(value);
                })
                .build()
                .show();
    }

    private void editDecimal(int titleRes, double current, final DoubleResult result) {
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title(getString(titleRes))
                .message("For example 1.25 means 125% of the hourly rate.")
                .input("1.25", String.format(java.util.Locale.US, "%.2f", current))
                .confirmText(getString(R.string.dlg_save))
                .cancelText(getString(R.string.dlg_cancel))
                .onConfirm(dialog -> {
                    double value;
                    try {
                        value = Double.parseDouble(dialog.inputText().replace(",", "."));
                    } catch (NumberFormatException e) {
                        dialog.dismiss();
                        SweetDialog.error(this, "Invalid number", "Type something like 1.25.");
                        return;
                    }
                    dialog.dismiss();
                    result.accept(value);
                })
                .build()
                .show();
    }

    private interface DoubleResult {
        void accept(double value);
    }

    // -------------------------------------------------------------- lifecycle

    private void ensureStorage(Runnable action) {
        if (!Storage.needsLegacyPermission() || Storage.hasLegacyPermission(this)) {
            action.run();
            return;
        }
        pendingStorageAction = action;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_STORAGE) return;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            Runnable action = pendingStorageAction;
            pendingStorageAction = null;
            if (action != null) action.run();
        } else {
            SweetDialog.warning(this, getString(R.string.permission_denied),
                    getString(R.string.permission_denied_msg));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_RESTORE_FILE && resultCode == RESULT_OK && data != null) {
            final Uri uri = data.getData();
            if (uri == null) return;
            runRestore(() -> BackupManager.get(this).restoreFromUri(uri));
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
