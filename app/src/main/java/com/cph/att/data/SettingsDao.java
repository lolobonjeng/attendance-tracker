package com.cph.att.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.cph.att.core.AppConfig;
import com.cph.att.core.WorkCalc;

import java.util.HashMap;
import java.util.Map;

public class SettingsDao {

    private final DbHelper helper;

    public SettingsDao(DbHelper helper) {
        this.helper = helper;
    }

    public Map<String, String> loadAll() {
        Map<String, String> map = new HashMap<>();
        Cursor c = null;
        try {
            c = helper.getReadableDatabase().query(DbHelper.T_SETTINGS, null, null, null, null, null, null);
            while (c.moveToNext()) {
                map.put(c.getString(0), c.getString(1));
            }
        } finally {
            if (c != null) c.close();
        }
        return map;
    }

    public void put(String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("k", key);
        cv.put("v", value);
        helper.getWritableDatabase().insertWithOnConflict(DbHelper.T_SETTINGS, null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public AppConfig read() {
        Map<String, String> m = loadAll();
        AppConfig c = new AppConfig();
        c.shiftStart = intOf(m, "shift_start", c.shiftStart);
        c.shiftEnd = intOf(m, "shift_end", c.shiftEnd);
        c.graceMinutes = intOf(m, "grace", c.graceMinutes);
        c.lunchBreak = intOf(m, "lunch_break", c.lunchBreak);
        c.coffeeBreak = intOf(m, "coffee_break", c.coffeeBreak);
        c.otMode = intOf(m, "ot_mode", c.otMode);
        c.otMultiplier = doubleOf(m, "ot_multiplier", c.otMultiplier);
        c.halfDayMinutes = intOf(m, "half_day", c.halfDayMinutes);
        c.currency = m.containsKey("currency") ? m.get("currency") : c.currency;
        c.autoBackup = boolOf(m, "auto_backup", c.autoBackup);
        c.backupKeep = intOf(m, "backup_keep", c.backupKeep);
        c.autoUpdateCheck = boolOf(m, "auto_update", c.autoUpdateCheck);
        c.updateUrl = m.containsKey("update_url") ? m.get("update_url") : c.updateUrl;
        c.lastBackupAt = longOf(m, "last_backup", c.lastBackupAt);
        c.lastRestoreAt = longOf(m, "last_restore", c.lastRestoreAt);
        c.lastUpdateCheck = longOf(m, "last_update_check", c.lastUpdateCheck);
        c.selectedEmployeeId = longOf(m, "selected_employee", c.selectedEmployeeId);
        c.orgName = m.containsKey("org_name") ? m.get("org_name") : c.orgName;
        if (c.backupKeep < 3) c.backupKeep = 3;
        return c;
    }

    public void write(AppConfig c) {
        put("shift_start", String.valueOf(c.shiftStart));
        put("shift_end", String.valueOf(c.shiftEnd));
        put("grace", String.valueOf(c.graceMinutes));
        put("lunch_break", String.valueOf(c.lunchBreak));
        put("coffee_break", String.valueOf(c.coffeeBreak));
        put("ot_mode", String.valueOf(c.otMode));
        put("ot_multiplier", String.valueOf(c.otMultiplier));
        put("half_day", String.valueOf(c.halfDayMinutes));
        put("currency", c.currency == null ? "" : c.currency);
        put("auto_backup", c.autoBackup ? "1" : "0");
        put("backup_keep", String.valueOf(c.backupKeep));
        put("auto_update", c.autoUpdateCheck ? "1" : "0");
        put("update_url", c.updateUrl == null ? "" : c.updateUrl);
        put("last_backup", String.valueOf(c.lastBackupAt));
        put("last_restore", String.valueOf(c.lastRestoreAt));
        put("last_update_check", String.valueOf(c.lastUpdateCheck));
        put("selected_employee", String.valueOf(c.selectedEmployeeId));
        put("org_name", c.orgName == null ? "" : c.orgName);
    }

    public void putOvertimeMode(int mode) {
        put("ot_mode", String.valueOf(mode == WorkCalc.OT_MODE_AFTER_SHIFT
                ? WorkCalc.OT_MODE_AFTER_SHIFT : WorkCalc.OT_MODE_PAIR));
    }

    private int intOf(Map<String, String> m, String key, int fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private long longOf(Map<String, String> m, String key, long fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double doubleOf(Map<String, String> m, String key, double fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean boolOf(Map<String, String> m, String key, boolean fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }
}
