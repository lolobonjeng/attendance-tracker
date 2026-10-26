package com.cph.att.backup;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cph.att.core.AppConfig;
import com.cph.att.core.AttendanceRecord;
import com.cph.att.core.AttendanceStatus;
import com.cph.att.core.Employee;
import com.cph.att.core.Fmt;
import com.cph.att.core.TimeCalc;
import com.cph.att.core.WorkCalc;
import com.cph.att.data.DbHelper;
import com.cph.att.data.Repo;
import com.cph.att.storage.Storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Autosave + auto restore.
 *
 * Layout on disk (visible in Downloads):
 *   Download/AttendanceTracker/backups/attendance_latest.db      (rewritten on every change)
 *   Download/AttendanceTracker/backups/attendance_20261026_1345.db (rotated snapshots)
 */
public final class BackupManager {

    private static final String TAG = "BackupManager";
    private static final String LATEST = "attendance_latest.db";
    private static final String PREFIX = "attendance_";
    private static final String SUFFIX = ".db";

    private static BackupManager instance;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private boolean pending;
    private long lastSnapshotAt;

    private final Runnable autoSaveRunnable = new Runnable() {
        @Override
        public void run() {
            pending = false;
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        writeLatest();
                    } catch (Exception e) {
                        Log.w(TAG, "Autosave failed", e);
                    }
                }
            });
        }
    };

    private BackupManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized BackupManager get(Context context) {
        if (instance == null) {
            instance = new BackupManager(context);
        }
        return instance;
    }

    public static final class Result {
        public final boolean ok;
        public final String message;

        Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    // -------------------------------------------------------------- autosave

    /** Debounced: several quick writes collapse into one snapshot. */
    public void requestAutoSave() {
        Repo repo = Repo.get(context);
        if (!repo.config().autoBackup) return;
        if (pending) return;
        pending = true;
        handler.removeCallbacks(autoSaveRunnable);
        handler.postDelayed(autoSaveRunnable, 2500L);
    }

    private void writeLatest() throws IOException {
        File snapshot = buildSnapshot();
        try {
            byte[] bytes = readFile(snapshot);
            Storage.replaceBytes(context, Storage.BACKUPS, LATEST, Storage.MIME_DB, bytes);
            Repo repo = Repo.get(context);
            repo.config().lastBackupAt = System.currentTimeMillis();
            repo.saveConfigQuietly();
            maybeRotate(snapshot);
        } finally {
            DbHelper.deleteQuietly(snapshot);
        }
    }

    private void maybeRotate(File snapshot) {
        long now = System.currentTimeMillis();
        if (lastSnapshotAt == 0L) {
            lastSnapshotAt = now;
            return;
        }
        if (now - lastSnapshotAt < 60L * 60L * 1000L) return;
        lastSnapshotAt = now;
        try {
            byte[] bytes = readFile(snapshot);
            String name = PREFIX + Fmt.fileStamp() + SUFFIX;
            Storage.writeBytes(context, Storage.BACKUPS, name, Storage.MIME_DB, bytes);
            prune();
        } catch (Exception e) {
            Log.w(TAG, "Rotate failed", e);
        }
    }

    // ------------------------------------------------------------ manual ops

    public Result backupNow() {
        try {
            File snapshot = buildSnapshot();
            byte[] bytes = readFile(snapshot);
            Storage.replaceBytes(context, Storage.BACKUPS, LATEST, Storage.MIME_DB, bytes);
            String name = PREFIX + Fmt.fileStamp() + SUFFIX;
            Storage.writeBytes(context, Storage.BACKUPS, name, Storage.MIME_DB, bytes);
            DbHelper.deleteQuietly(snapshot);

            Repo repo = Repo.get(context);
            repo.config().lastBackupAt = System.currentTimeMillis();
            repo.saveConfigQuietly();
            lastSnapshotAt = System.currentTimeMillis();
            prune();
            return new Result(true, Storage.describeLocation() + "/" + name);
        } catch (Exception e) {
            Log.e(TAG, "Manual backup failed", e);
            return new Result(false, e.getMessage() == null ? "Backup failed" : e.getMessage());
        }
    }

    public List<Storage.StoredFile> listBackups() {
        return Storage.list(context, Storage.BACKUPS, SUFFIX);
    }

    public boolean restoreFrom(Storage.StoredFile file) {
        try {
            byte[] bytes = Storage.readBytes(context, file);
            return restoreBytes(bytes);
        } catch (Exception e) {
            Log.e(TAG, "Restore failed", e);
            return false;
        }
    }

    public boolean restoreFromUri(Uri uri) {
        InputStream in = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            in = resolver.openInputStream(uri);
            if (in == null) return false;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return restoreBytes(out.toByteArray());
        } catch (Exception e) {
            Log.e(TAG, "Restore from uri failed", e);
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { }
        }
    }

    private boolean restoreBytes(byte[] bytes) {
        File candidate = new File(context.getCacheDir(), "restore_candidate.db");
        try {
            writeFile(candidate, bytes);
        } catch (IOException e) {
            return false;
        }
        if (!validate(candidate)) {
            DbHelper.deleteQuietly(candidate);
            return false;
        }
        Repo.reset();
        boolean swapped = DbHelper.swapDatabase(context, candidate);
        DbHelper.deleteQuietly(candidate);
        if (!swapped) {
            Repo.get(context);
            return false;
        }
        Repo repo = Repo.get(context);
        repo.reloadConfig();
        repo.config().lastRestoreAt = System.currentTimeMillis();
        repo.saveConfigQuietly();
        repo.ensureSeed();
        return true;
    }

    // ----------------------------------------------------------- auto restore

    /**
     * Runs before the database is opened. Restores the newest backup when the
     * local file is missing or damaged.
     *
     * @return true when something was restored
     */
    public boolean autoRestoreIfNeeded() {
        File live = context.getDatabasePath(DbHelper.DB_NAME);
        boolean healthy = live.exists() && validate(live);
        if (healthy) return false;

        List<Storage.StoredFile> backups = listBackups();
        for (Storage.StoredFile f : backups) {
            try {
                byte[] bytes = Storage.readBytes(context, f);
                File candidate = new File(context.getCacheDir(), "auto_restore.db");
                try {
                    writeFile(candidate, bytes);
                } catch (IOException e) {
                    continue;
                }
                boolean ok = validate(candidate);
                if (!ok) {
                    DbHelper.deleteQuietly(candidate);
                    continue;
                }
                Repo.reset();
                boolean swapped = DbHelper.swapDatabase(context, candidate);
                DbHelper.deleteQuietly(candidate);
                if (swapped) {
                    Repo repo = Repo.get(context);
                    repo.reloadConfig();
                    repo.config().lastRestoreAt = System.currentTimeMillis();
                    repo.saveConfigQuietly();
                    return true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Auto restore candidate failed: " + f.name, e);
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ util

    public File buildSnapshot() throws IOException {
        Repo repo = Repo.get(context);
        SQLiteDatabase src = repo.db().getReadableDatabase();
        File out = new File(context.getCacheDir(), "snapshot_" + System.currentTimeMillis() + ".db");
        DbHelper.deleteQuietly(out);
        SQLiteDatabase dst = SQLiteDatabase.openOrCreateDatabase(out, null);
        try {
            for (String sql : DbHelper.SCHEMA) {
                dst.execSQL(sql);
            }
            dst.beginTransaction();
            copyTable(src, dst, DbHelper.T_EMPLOYEES);
            copyTable(src, dst, DbHelper.T_ATTENDANCE);
            copyTable(src, dst, DbHelper.T_SETTINGS);
            dst.setTransactionSuccessful();
            dst.endTransaction();
        } finally {
            try { dst.close(); } catch (Exception ignored) { }
        }
        return out;
    }

    private static void copyTable(SQLiteDatabase src, SQLiteDatabase dst, String table) {
        Cursor c = null;
        try {
            c = src.query(table, null, null, null, null, null, null);
            if (c == null) return;
            String[] cols = c.getColumnNames();
            while (c.moveToNext()) {
                ContentValues cv = new ContentValues();
                for (int i = 0; i < cols.length; i++) {
                    switch (c.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL:
                            cv.putNull(cols[i]);
                            break;
                        case Cursor.FIELD_TYPE_INTEGER:
                            cv.put(cols[i], c.getLong(i));
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            cv.put(cols[i], c.getDouble(i));
                            break;
                        default:
                            cv.put(cols[i], c.getString(i));
                            break;
                    }
                }
                dst.insert(table, null, cv);
            }
        } finally {
            if (c != null) c.close();
        }
    }

    public static boolean validate(File dbFile) {
        if (dbFile == null || !dbFile.exists() || dbFile.length() < 1024) return false;
        SQLiteDatabase db = null;
        Cursor c = null;
        Cursor i = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' "
                    + "AND name IN ('employees','attendance','settings')", null);
            if (!c.moveToFirst() || c.getInt(0) < 3) return false;
            i = db.rawQuery("PRAGMA integrity_check", null);
            return i.moveToFirst() && "ok".equalsIgnoreCase(i.getString(0));
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
            if (i != null) i.close();
            if (db != null) try { db.close(); } catch (Exception ignored) { }
        }
    }

    private void prune() {
        Repo repo = Repo.get(context);
        int keep = Math.max(3, repo.config().backupKeep);
        List<Storage.StoredFile> all = listBackups();
        List<Storage.StoredFile> snapshots = new ArrayList<>();
        for (Storage.StoredFile f : all) {
            if (LATEST.equals(f.name)) continue;
            snapshots.add(f);
        }
        for (int i = keep; i < snapshots.size(); i++) {
            Storage.delete(context, snapshots.get(i));
        }
    }

    // ------------------------------------------------------------------- csv

    public Result exportCsv(String fromDate, String toDate, long employeeId) {
        try {
            Repo repo = Repo.get(context);
            List<AttendanceRecord> records = repo.records(fromDate, toDate, employeeId);
            Map<Long, Employee> people = new HashMap<>();
            for (Employee e : repo.employees(true)) {
                people.put(e.id, e);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Date,Employee,Check In,Break 1 Start,Break 1 End,Break 2 Start,")
                    .append("Break 2 End,Check Out,OT In,OT Out,Worked (h),Break (min),")
                    .append("OT (h),Late (min),Status,Note\r\n");

            for (AttendanceRecord r : records) {
                Employee e = people.get(r.employeeId);
                WorkCalc.Result res = repo.compute(r, e);
                sb.append(csv(r.date)).append(',')
                        .append(csv(e != null ? e.name : "Unknown")).append(',')
                        .append(csv(time(r.in1))).append(',')
                        .append(csv(time(r.out1))).append(',')
                        .append(csv(time(r.in2))).append(',')
                        .append(csv(time(r.out2))).append(',')
                        .append(csv(time(r.in3))).append(',')
                        .append(csv(time(r.out3))).append(',')
                        .append(csv(time(r.otIn))).append(',')
                        .append(csv(time(r.otOut))).append(',')
                        .append(round1(res.worked / 60d)).append(',')
                        .append(res.breakTotal).append(',')
                        .append(round1(res.overtime / 60d)).append(',')
                        .append(res.late).append(',')
                        .append(csv(com.cph.att.core.AttendanceStatus.label(r.status))).append(',')
                        .append(csv(r.note == null ? "" : r.note))
                        .append("\r\n");
            }

            String name = "attendance_" + fromDate + "_to_" + toDate + ".csv";
            Storage.replaceBytes(context, Storage.EXPORTS, name, Storage.MIME_CSV,
                    sb.toString().getBytes("UTF-8"));
            return new Result(true, "Downloads/" + Storage.ROOT + "/exports/" + name);
        } catch (Exception e) {
            Log.e(TAG, "CSV export failed", e);
            return new Result(false, e.getMessage() == null ? "Export failed" : e.getMessage());
        }
    }

    // ----------------------------------------------------------------- excel

    public Result exportXlsx(String fromDate, String toDate, long employeeId) {
        try {
            Repo repo = Repo.get(context);
            List<AttendanceRecord> records = repo.records(fromDate, toDate, employeeId);
            Map<Long, Employee> people = new HashMap<>();
            for (Employee e : repo.employees(true)) {
                people.put(e.id, e);
            }
            AppConfig cfg = repo.config();

            XlsxWriter book = new XlsxWriter();

            XlsxWriter.Sheet days = book.addSheet("Days");
            days.widths(12, 22, 11, 13, 12, 13, 12, 11, 11, 11, 10, 11, 9, 9, 12, 26);
            days.header("Date", "Employee", "Check in", "Break 1 start", "Break 1 end",
                    "Break 2 start", "Break 2 end", "Check out", "OT in", "OT out",
                    "Worked (h)", "Break (min)", "OT (h)", "Late (min)", "Status", "Note");

            // days, worked minutes, overtime minutes, late days, estimated pay
            Map<Long, double[]> totals = new HashMap<>();

            for (AttendanceRecord r : records) {
                Employee e = people.get(r.employeeId);
                WorkCalc.Result res = repo.compute(r, e);

                days.row(r.date,
                        e != null ? e.name : "Unknown",
                        time(r.in1), time(r.out1), time(r.in2), time(r.out2),
                        time(r.in3), time(r.out3), time(r.otIn), time(r.otOut),
                        round2(res.worked / 60d), res.breakTotal, round2(res.overtime / 60d),
                        res.late, AttendanceStatus.label(r.status),
                        r.note == null ? "" : r.note);

                double[] total = totals.get(r.employeeId);
                if (total == null) {
                    total = new double[5];
                    totals.put(r.employeeId, total);
                }
                total[0] += 1;
                total[1] += res.worked;
                total[2] += res.overtime;
                if (res.late > 0) total[3] += 1;
                total[4] += cfg.payFor(res.worked, res.overtime, e == null ? 0 : e.rate);
            }

            XlsxWriter.Sheet summary = book.addSheet("Summary");
            summary.widths(24, 8, 11, 11, 10, 14);
            summary.header("Employee", "Days", "Hours", "Overtime", "Late days", "Est. pay");
            for (Employee e : repo.employees(true)) {
                double[] total = totals.get(e.id);
                if (total == null) continue;
                summary.row(e.name, (int) total[0], round2(total[1] / 60d),
                        round2(total[2] / 60d), (int) total[3], round2(total[4]));
            }

            byte[] bytes = book.toBytes();
            String name = "attendance_" + fromDate + "_to_" + toDate + ".xlsx";
            Storage.replaceBytes(context, Storage.EXPORTS, name, Storage.MIME_XLSX, bytes);
            return new Result(true, "Downloads/" + Storage.ROOT + "/exports/" + name);
        } catch (Exception e) {
            Log.e(TAG, "Excel export failed", e);
            return new Result(false, e.getMessage() == null ? "Export failed" : e.getMessage());
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private static String time(Integer minutes) {
        return minutes == null ? "" : TimeCalc.format12(minutes);
    }

    private static String round1(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    private static String csv(String raw) {
        String v = raw == null ? "" : raw;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static byte[] readFile(File f) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
        return out.toByteArray();
    }

    /** Writes bytes to a cache file, throwing instead of silently truncating. */
    private static void writeFile(File file, byte[] bytes) throws IOException {
        java.io.FileOutputStream out = new java.io.FileOutputStream(file);
        try {
            out.write(bytes);
            out.flush();
        } finally {
            try { out.close(); } catch (IOException ignored) { }
        }
    }
}
