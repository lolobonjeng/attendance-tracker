package com.cph.att.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.cph.att.storage.Storage;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Minimal, dependency free updater.
 *
 * The manifest URL must return JSON like:
 * {
 *   "versionCode": 2,
 *   "versionName": "1.1.0",
 *   "url": "https://example.com/attendance-tracker-1.1.0.apk",
 *   "notes": "What changed",
 *   "size": 7340032,
 *   "sha256": "optional hex digest",
 *   "mandatory": false
 * }
 */
public final class UpdateManager {

    public interface Progress {
        void onProgress(int percent, long downloaded, long total);
    }

    private UpdateManager() {
    }

    public static UpdateInfo fetch(String manifestUrl) throws IOException {
        if (manifestUrl == null || manifestUrl.trim().isEmpty()) {
            throw new IOException("No update URL configured");
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(manifestUrl.trim());
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Server returned " + code);
            }
            InputStream in = conn.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            in.close();

            JSONObject json = new JSONObject(out.toString("UTF-8"));
            UpdateInfo info = new UpdateInfo();
            info.versionCode = json.optInt("versionCode", 0);
            info.versionName = json.optString("versionName", "");
            info.url = json.optString("url", "");
            info.notes = json.optString("notes", "");
            info.size = json.optLong("size", 0L);
            info.sha256 = json.optString("sha256", "");
            info.mandatory = json.optBoolean("mandatory", false);
            if (info.versionCode <= 0 || info.url.isEmpty()) {
                throw new IOException("Malformed update manifest");
            }
            return info;
        } catch (org.json.JSONException e) {
            throw new IOException("Malformed update manifest: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static Storage.StoredFile download(Context context, UpdateInfo info, Progress progress)
            throws IOException {
        String fileName = "attendance-tracker-" + (info.versionName.isEmpty()
                ? String.valueOf(info.versionCode) : info.versionName) + ".apk";
        fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");

        HttpURLConnection conn = null;
        Uri uri = null;
        OutputStream out = null;
        try {
            URL url = new URL(info.url);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Download failed (" + code + ")");
            }
            long total = info.size;
            int declared = conn.getContentLength();
            if (declared > 0) total = declared;

            Storage.deleteByName(context, Storage.UPDATES, fileName);
            uri = Storage.create(context, Storage.UPDATES, fileName, Storage.MIME_APK);
            out = Storage.openOut(context, uri);

            InputStream in = conn.getInputStream();
            MessageDigest digest = null;
            if (info.sha256 != null && !info.sha256.isEmpty()) {
                try {
                    digest = MessageDigest.getInstance("SHA-256");
                } catch (Exception ignored) {
                }
            }

            byte[] buffer = new byte[32 * 1024];
            long downloaded = 0;
            int read;
            int lastPercent = -1;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (digest != null) digest.update(buffer, 0, read);
                if (progress != null) {
                    int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        progress.onProgress(percent, downloaded, total);
                    }
                }
            }
            out.flush();
            in.close();
            out.close();
            out = null;
            Storage.finish(context, uri);

            if (digest != null) {
                String actual = hex(digest.digest());
                if (!actual.equalsIgnoreCase(info.sha256.trim())) {
                    Storage.deleteByName(context, Storage.UPDATES, fileName);
                    throw new IOException("Checksum mismatch — download again");
                }
            }

            Storage.StoredFile stored = new Storage.StoredFile();
            stored.name = fileName;
            stored.relDir = Storage.UPDATES;
            stored.uri = uri;
            stored.size = downloaded;
            stored.modified = System.currentTimeMillis();
            return stored;
        } finally {
            if (out != null) try { out.close(); } catch (IOException ignored) { }
            if (conn != null) conn.disconnect();
        }
    }

    public static boolean canInstall(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return context.getPackageManager().canRequestPackageInstalls();
    }

    public static Intent installPermissionIntent(Context context) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
        } else {
            intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
        }
        return intent;
    }

    /** Launches the system installer. Returns false when the intent cannot resolve. */
    public static boolean install(Context context, Storage.StoredFile file) {
        Uri uri = file.uri;
        if (uri == null && file.file != null) {
            uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", file.file);
        }
        if (uri == null) return false;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, Storage.MIME_APK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    /** Used by the settings screen to show a readable file location. */
    public static String updatesFolder() {
        return "Downloads/" + Storage.ROOT + "/updates";
    }

    public static File cacheDir(Context context) {
        return context.getCacheDir();
    }
}
