package com.cph.att.storage;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes to Downloads/AttendanceTracker without asking for scary permissions.
 * API 29+ goes through MediaStore, older devices use plain files.
 */
public final class Storage {

    public static final String ROOT = "AttendanceTracker";
    public static final String BACKUPS = "Download/" + ROOT + "/backups";
    public static final String UPDATES = "Download/" + ROOT + "/updates";
    public static final String EXPORTS = "Download/" + ROOT + "/exports";

    public static final String MIME_DB = "application/x-sqlite3";
    public static final String MIME_CSV = "text/csv";
    public static final String MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String MIME_APK = "application/vnd.android.package-archive";

    private Storage() {
    }

    public static final class StoredFile {
        public String name = "";
        public String relDir = "";
        public Uri uri;
        public File file;
        public long size;
        public long modified;

        public boolean isMediaStore() {
            return uri != null;
        }

        public String displaySize() {
            if (size <= 0) return "—";
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return (size / 1024) + " KB";
            return String.format(java.util.Locale.US, "%.1f MB", size / (1024d * 1024d));
        }
    }

    public static boolean needsLegacyPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    public static boolean hasLegacyPermission(Context context) {
        if (!needsLegacyPermission()) return true;
        return ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static File publicDir(String relDir) {
        String sub = relDir.startsWith("Download/")
                ? relDir.substring("Download/".length())
                : relDir;
        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(base, sub);
    }

    private static boolean useMediaStore() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    // ------------------------------------------------------------------ write

    public static Uri create(Context context, String relDir, String name, String mime) throws IOException {
        if (!useMediaStore()) {
            File dir = publicDir(relDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Cannot create " + dir.getAbsolutePath());
            }
            File target = new File(dir, name);
            if (!target.exists() && !target.createNewFile()) {
                throw new IOException("Cannot create " + target.getAbsolutePath());
            }
            return Uri.fromFile(target);
        }

        ContentResolver resolver = context.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relDir);
        cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new IOException("MediaStore insert failed");
        return uri;
    }

    public static OutputStream openOut(Context context, Uri uri) throws IOException {
        if (uri.getScheme() != null && "file".equals(uri.getScheme())) {
            return new FileOutputStream(new File(uri.getPath()));
        }
        OutputStream out = context.getContentResolver().openOutputStream(uri);
        if (out == null) throw new IOException("No output stream for " + uri);
        return out;
    }

    public static void finish(Context context, Uri uri) {
        if (!useMediaStore()) return;
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
        try {
            context.getContentResolver().update(uri, cv, null, null);
        } catch (Exception ignored) {
        }
    }

    public static Uri writeBytes(Context context, String relDir, String name, String mime, byte[] data)
            throws IOException {
        Uri uri = create(context, relDir, name, mime);
        OutputStream out = null;
        try {
            out = openOut(context, uri);
            out.write(data);
            out.flush();
        } finally {
            if (out != null) try { out.close(); } catch (IOException ignored) { }
        }
        finish(context, uri);
        return uri;
    }

    /** Deletes an existing entry with the same name so autosave can overwrite it. */
    public static void deleteByName(Context context, String relDir, String name) {
        if (!useMediaStore()) {
            File f = new File(publicDir(relDir), name);
            if (f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = {name, relDir + "%"};
        try {
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection, args);
        } catch (Exception ignored) {
        }
    }

    public static Uri replaceBytes(Context context, String relDir, String name, String mime, byte[] data)
            throws IOException {
        deleteByName(context, relDir, name);
        return writeBytes(context, relDir, name, mime, data);
    }

    // ------------------------------------------------------------------- read

    public static List<StoredFile> list(Context context, String relDir, String suffix) {
        List<StoredFile> out = new ArrayList<>();
        if (!useMediaStore()) {
            File dir = publicDir(relDir);
            File[] files = dir.listFiles();
            if (files == null) return out;
            for (File f : files) {
                if (!f.isFile()) continue;
                if (suffix != null && !f.getName().toLowerCase(java.util.Locale.US)
                        .endsWith(suffix.toLowerCase(java.util.Locale.US))) continue;
                StoredFile sf = new StoredFile();
                sf.name = f.getName();
                sf.file = f;
                sf.relDir = relDir;
                sf.size = f.length();
                sf.modified = f.lastModified();
                out.add(sf);
            }
            java.util.Collections.sort(out, (a, b) -> Long.compare(b.modified, a.modified));
            return out;
        }

        ContentResolver resolver = context.getContentResolver();
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
        };
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = {relDir + "%"};
        android.database.Cursor c = null;
        try {
            c = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection,
                    selection, args, MediaStore.MediaColumns.DATE_MODIFIED + " DESC");
            if (c == null) return out;
            int idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int sizeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            int modIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);
            while (c.moveToNext()) {
                String name = c.getString(nameIdx);
                if (name == null) continue;
                if (suffix != null && !name.toLowerCase(java.util.Locale.US)
                        .endsWith(suffix.toLowerCase(java.util.Locale.US))) continue;
                StoredFile sf = new StoredFile();
                sf.name = name;
                sf.relDir = relDir;
                sf.size = c.getLong(sizeIdx);
                sf.modified = c.getLong(modIdx) * 1000L;
                sf.uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        String.valueOf(c.getLong(idIdx)));
                out.add(sf);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public static InputStream openIn(Context context, StoredFile f) throws IOException {
        if (f.file != null) {
            return new FileInputStream(f.file);
        }
        if (f.uri != null) {
            InputStream in = context.getContentResolver().openInputStream(f.uri);
            if (in == null) throw new IOException("No stream for " + f.name);
            return in;
        }
        throw new IOException("Empty handle");
    }

    public static byte[] readBytes(Context context, StoredFile f) throws IOException {
        InputStream in = null;
        try {
            in = openIn(context, f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { }
        }
    }

    public static void delete(Context context, StoredFile f) {
        if (f.file != null) {
            //noinspection ResultOfMethodCallIgnored
            f.file.delete();
            return;
        }
        if (f.uri != null) {
            try {
                context.getContentResolver().delete(f.uri, null, null);
            } catch (Exception ignored) {
            }
        }
    }

    public static String describeLocation() {
        return "Downloads/" + ROOT + "/backups";
    }
}
