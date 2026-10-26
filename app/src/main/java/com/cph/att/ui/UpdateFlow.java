package com.cph.att.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;

import com.cph.att.BuildConfig;
import com.cph.att.R;
import com.cph.att.data.Repo;
import com.cph.att.storage.Storage;
import com.cph.att.update.UpdateInfo;
import com.cph.att.update.UpdateManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared update flow: check → confirm → download → install. */
public final class UpdateFlow {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private UpdateFlow() {
    }

    public static void check(final Activity activity, final boolean silent) {
        final Repo repo = Repo.get(activity);
        String url = repo.config().updateUrl;
        if (url == null || url.trim().isEmpty()) {
            if (silent) return;
            SweetDialog.builder(activity)
                    .type(SweetDialog.TYPE_INFO)
                    .title("Set an update link")
                    .message("Paste the URL of a JSON manifest. The app reads versionCode, "
                            + "versionName, url, notes, size and sha256 from it.")
                    .input("https://…/update.json", "")
                    .confirmText("Save and check")
                    .cancelText(activity.getString(R.string.dlg_cancel))
                    .onConfirm(dialog -> {
                        String value = dialog.inputText();
                        if (value.isEmpty()) {
                            dialog.dismiss();
                            return;
                        }
                        repo.config().updateUrl = value;
                        repo.saveConfigQuietly();
                        dialog.dismiss();
                        check(activity, false);
                    })
                    .build()
                    .show();
            return;
        }

        final SweetDialog loading = silent ? null
                : SweetDialog.loading(activity, activity.getString(R.string.dlg_loading),
                "Checking for a new version…");

        WORKER.execute(() -> {
            try {
                final UpdateInfo info = UpdateManager.fetch(url);
                repo.config().lastUpdateCheck = System.currentTimeMillis();
                repo.saveConfigQuietly();
                activity.runOnUiThread(() -> {
                    if (loading != null) loading.dismiss();
                    if (activity.isFinishing()) return;
                    if (info.isNewerThan(BuildConfig.VERSION_CODE)) {
                        showUpdateDialog(activity, info);
                    } else if (!silent) {
                        SweetDialog.success(activity, activity.getString(R.string.dlg_up_to_date),
                                "Version " + BuildConfig.VERSION_NAME + " is the latest build.");
                    }
                });
            } catch (final Exception e) {
                activity.runOnUiThread(() -> {
                    if (loading != null) loading.dismiss();
                    if (silent || activity.isFinishing()) return;
                    SweetDialog.error(activity, activity.getString(R.string.dlg_error),
                            e.getMessage() == null ? "Could not reach the update server."
                                    : e.getMessage());
                });
            }
        });
    }

    private static void showUpdateDialog(final Activity activity, final UpdateInfo info) {
        StringBuilder message = new StringBuilder();
        message.append("Version ").append(info.versionName)
                .append(" (").append(info.versionCode).append(") is ready.");
        if (info.size > 0) {
            message.append("  ·  ").append(info.sizeLabel());
        }
        if (info.notes != null && !info.notes.isEmpty()) {
            message.append("\n\n").append(info.notes);
        }

        SweetDialog.builder(activity)
                .type(SweetDialog.TYPE_SUCCESS)
                .icon(R.drawable.ic_download)
                .title(activity.getString(R.string.dlg_update_available))
                .message(message.toString())
                .confirmText(activity.getString(R.string.dlg_update_now))
                .cancelText(activity.getString(R.string.dlg_update_later))
                .onConfirm(dialog -> {
                    dialog.dismiss();
                    download(activity, info);
                })
                .build()
                .show();
    }

    private static void download(final Activity activity, final UpdateInfo info) {
        final SweetDialog progress = SweetDialog.loadingProgress(activity,
                activity.getString(R.string.dlg_downloading), info.versionName);
        progress.setProgress(0);

        WORKER.execute(() -> {
            try {
                final Storage.StoredFile file = UpdateManager.download(activity, info,
                        (percent, downloaded, total) -> activity.runOnUiThread(() -> {
                            progress.setProgress(percent);
                            progress.setProgressText(percent + "%  ·  "
                                    + human(downloaded) + " / " + human(total));
                        }));
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (activity.isFinishing()) return;
                    installOrExplain(activity, file);
                });
            } catch (final Exception e) {
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (activity.isFinishing()) return;
                    SweetDialog.error(activity, "Download failed",
                            e.getMessage() == null ? "Try again in a moment." : e.getMessage());
                });
            }
        });
    }

    private static void installOrExplain(final Activity activity, final Storage.StoredFile file) {
        if (!UpdateManager.canInstall(activity)) {
            SweetDialog.builder(activity)
                    .type(SweetDialog.TYPE_WARNING)
                    .title(activity.getString(R.string.dlg_install_blocked))
                    .message(activity.getString(R.string.dlg_install_blocked_msg))
                    .confirmText(activity.getString(R.string.dlg_open_settings))
                    .cancelText(activity.getString(R.string.dlg_cancel))
                    .onConfirm(dialog -> {
                        dialog.dismiss();
                        activity.startActivity(UpdateManager.installPermissionIntent(activity));
                    })
                    .build()
                    .show();
            return;
        }
        boolean started = UpdateManager.install(activity, file);
        if (!started) {
            SweetDialog.warning(activity, "Installer unavailable",
                    "The APK is saved to " + UpdateManager.updatesFolder()
                            + ". Open it from your file manager to install.");
        }
    }

    private static String human(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024d * 1024d));
    }
}
