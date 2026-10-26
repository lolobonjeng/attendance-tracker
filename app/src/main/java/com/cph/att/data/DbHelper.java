package com.cph.att.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class DbHelper extends SQLiteOpenHelper {

    /** SQLiteOpenHelper keeps its context private and exposes no getter. */
    private final Context context;

    public static final String DB_NAME = "attendance.db";
    public static final int DB_VERSION = 1;

    public static final String T_EMPLOYEES = "employees";
    public static final String T_ATTENDANCE = "attendance";
    public static final String T_SETTINGS = "settings";

    public static final String[] SCHEMA = {            "CREATE TABLE IF NOT EXISTS " + T_EMPLOYEES + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL,"
                    + "code TEXT,"
                    + "position TEXT,"
                    + "rate REAL NOT NULL DEFAULT 0,"
                    + "shift_start INTEGER NOT NULL DEFAULT 540,"
                    + "shift_end INTEGER NOT NULL DEFAULT 1080,"
                    + "workdays TEXT NOT NULL DEFAULT '1,2,3,4,5',"
                    + "active INTEGER NOT NULL DEFAULT 1,"
                    + "created_at INTEGER NOT NULL DEFAULT 0)",

            "CREATE TABLE IF NOT EXISTS " + T_ATTENDANCE + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "employee_id INTEGER NOT NULL,"
                    + "work_date TEXT NOT NULL,"
                    + "in1 INTEGER, out1 INTEGER, in2 INTEGER, out2 INTEGER, in3 INTEGER, out3 INTEGER,"
                    + "ot_in INTEGER, ot_out INTEGER,"
                    + "status TEXT NOT NULL DEFAULT 'absent',"
                    + "note TEXT,"
                    + "created_at INTEGER NOT NULL DEFAULT 0,"
                    + "updated_at INTEGER NOT NULL DEFAULT 0,"
                    + "UNIQUE(employee_id, work_date),"
                    + "FOREIGN KEY(employee_id) REFERENCES " + T_EMPLOYEES + "(id) ON DELETE CASCADE)",

            "CREATE TABLE IF NOT EXISTS " + T_SETTINGS + " ("
                    + "k TEXT PRIMARY KEY,"
                    + "v TEXT)",

            "CREATE INDEX IF NOT EXISTS idx_att_date ON " + T_ATTENDANCE + "(work_date)",
            "CREATE INDEX IF NOT EXISTS idx_att_emp ON " + T_ATTENDANCE + "(employee_id)"
    };

    public DbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        try {
            db.setForeignKeyConstraintsEnabled(true);
        } catch (SQLiteException ignored) {
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String sql : SCHEMA) {
            db.execSQL(sql);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the initial schema. Future migrations go here.
        for (String sql : SCHEMA) {
            db.execSQL(sql);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Keep data; the additive schema is compatible downwards.
    }

    public File dbFile() {
        return context.getDatabasePath(DB_NAME);
    }

    /** Runs PRAGMA integrity_check. False when the file is unusable. */
    public boolean integrityOk() {
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = getReadableDatabase();
            c = db.rawQuery("PRAGMA integrity_check", null);
            if (c.moveToFirst()) {
                String value = c.getString(0);
                return "ok".equalsIgnoreCase(value);
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    /** Replaces the live database file with the given snapshot. Caller must close first. */
    public static boolean swapDatabase(Context context, File snapshot) {
        File live = context.getDatabasePath(DB_NAME);
        File parent = live.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        File safety = new File(live.getAbsolutePath() + ".before-restore");
        try {
            if (live.exists()) {
                copy(live, safety);
            }
            copy(snapshot, live);
            deleteQuietly(new File(live.getAbsolutePath() + "-wal"));
            deleteQuietly(new File(live.getAbsolutePath() + "-shm"));
            deleteQuietly(new File(live.getAbsolutePath() + "-journal"));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void copy(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { }
            if (out != null) try { out.close(); } catch (IOException ignored) { }
        }
    }

    public static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
