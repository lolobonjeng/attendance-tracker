package com.cph.att.data;

import android.content.ContentValues;
import android.database.Cursor;

import com.cph.att.core.AttendanceRecord;
import com.cph.att.core.AttendanceStatus;

import java.util.ArrayList;
import java.util.List;

public class AttendanceDao {

    private final DbHelper helper;

    public AttendanceDao(DbHelper helper) {
        this.helper = helper;
    }

    public AttendanceRecord find(long employeeId, String date) {
        Cursor c = null;
        try {
            c = helper.getReadableDatabase().query(DbHelper.T_ATTENDANCE, null,
                    "employee_id = ? AND work_date = ?",
                    new String[]{String.valueOf(employeeId), date}, null, null, null);
            if (c.moveToFirst()) return read(c);
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    public AttendanceRecord byId(long id) {
        Cursor c = null;
        try {
            c = helper.getReadableDatabase().query(DbHelper.T_ATTENDANCE, null,
                    "id = ?", new String[]{String.valueOf(id)}, null, null, null);
            if (c.moveToFirst()) return read(c);
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    /**
     * @param employeeId 0 for every employee
     */
    public List<AttendanceRecord> range(String fromDate, String toDate, long employeeId) {
        List<AttendanceRecord> out = new ArrayList<>();
        String selection = "work_date >= ? AND work_date <= ?";
        List<String> args = new ArrayList<>();
        args.add(fromDate);
        args.add(toDate);
        if (employeeId > 0) {
            selection += " AND employee_id = ?";
            args.add(String.valueOf(employeeId));
        }
        Cursor c = null;
        try {
            c = helper.getReadableDatabase().query(DbHelper.T_ATTENDANCE, null, selection,
                    args.toArray(new String[0]), null, null, "work_date DESC, id DESC");
            while (c.moveToNext()) {
                out.add(read(c));
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public List<AttendanceRecord> recent(int limit) {
        List<AttendanceRecord> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = helper.getReadableDatabase().query(DbHelper.T_ATTENDANCE, null, null,
                    null, null, null, "work_date DESC, id DESC", String.valueOf(limit));
            while (c.moveToNext()) {
                out.add(read(c));
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public int count() {
        Cursor c = null;
        try {
            c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + DbHelper.T_ATTENDANCE, null);
            if (c.moveToFirst()) return c.getInt(0);
        } finally {
            if (c != null) c.close();
        }
        return 0;
    }

    public long save(AttendanceRecord r) {
        long now = System.currentTimeMillis();
        if (r.createdAt <= 0) r.createdAt = now;
        r.updatedAt = now;

        AttendanceRecord existing = find(r.employeeId, r.date);
        ContentValues cv = write(r);
        if (existing != null) {
            r.id = existing.id;
            cv.put("created_at", existing.createdAt);
            helper.getWritableDatabase().update(DbHelper.T_ATTENDANCE, cv,
                    "id = ?", new String[]{String.valueOf(existing.id)});
            return existing.id;
        }
        cv.put("created_at", r.createdAt);
        long id = helper.getWritableDatabase().insert(DbHelper.T_ATTENDANCE, null, cv);
        r.id = id;
        return id;
    }

    public void delete(long id) {
        helper.getWritableDatabase().delete(DbHelper.T_ATTENDANCE, "id = ?",
                new String[]{String.valueOf(id)});
    }

    public void deleteFor(long employeeId, String date) {
        helper.getWritableDatabase().delete(DbHelper.T_ATTENDANCE,
                "employee_id = ? AND work_date = ?",
                new String[]{String.valueOf(employeeId), date});
    }

    public int countLate(String fromDate, String toDate, long employeeId) {
        String sql = "SELECT COUNT(*) FROM " + DbHelper.T_ATTENDANCE
                + " WHERE work_date >= ? AND work_date <= ? AND status = 'late'";
        List<String> args = new ArrayList<>();
        args.add(fromDate);
        args.add(toDate);
        if (employeeId > 0) {
            sql += " AND employee_id = ?";
            args.add(String.valueOf(employeeId));
        }
        Cursor c = null;
        try {
            c = helper.getReadableDatabase().rawQuery(sql, args.toArray(new String[0]));
            if (c.moveToFirst()) return c.getInt(0);
        } finally {
            if (c != null) c.close();
        }
        return 0;
    }

    private ContentValues write(AttendanceRecord r) {
        ContentValues cv = new ContentValues();
        cv.put("employee_id", r.employeeId);
        cv.put("work_date", r.date);
        putInt(cv, "in1", r.in1);
        putInt(cv, "out1", r.out1);
        putInt(cv, "in2", r.in2);
        putInt(cv, "out2", r.out2);
        putInt(cv, "in3", r.in3);
        putInt(cv, "out3", r.out3);
        putInt(cv, "ot_in", r.otIn);
        putInt(cv, "ot_out", r.otOut);
        cv.put("status", r.status == null ? AttendanceStatus.ABSENT : r.status);
        cv.put("note", r.note == null ? "" : r.note);
        cv.put("updated_at", r.updatedAt);
        return cv;
    }

    private void putInt(ContentValues cv, String key, Integer value) {
        if (value == null) {
            cv.putNull(key);
        } else {
            cv.put(key, value);
        }
    }

    private AttendanceRecord read(Cursor c) {
        AttendanceRecord r = new AttendanceRecord();
        r.id = c.getLong(c.getColumnIndexOrThrow("id"));
        r.employeeId = c.getLong(c.getColumnIndexOrThrow("employee_id"));
        r.date = getString(c, "work_date");
        r.in1 = getInt(c, "in1");
        r.out1 = getInt(c, "out1");
        r.in2 = getInt(c, "in2");
        r.out2 = getInt(c, "out2");
        r.in3 = getInt(c, "in3");
        r.out3 = getInt(c, "out3");
        r.otIn = getInt(c, "ot_in");
        r.otOut = getInt(c, "ot_out");
        r.status = getString(c, "status");
        r.note = getString(c, "note");
        r.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        r.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return r;
    }

    private Integer getInt(Cursor c, String column) {
        int idx = c.getColumnIndex(column);
        if (idx < 0 || c.isNull(idx)) return null;
        return c.getInt(idx);
    }

    private String getString(Cursor c, String column) {
        int idx = c.getColumnIndex(column);
        if (idx < 0 || c.isNull(idx)) return "";
        return c.getString(idx);
    }
}
