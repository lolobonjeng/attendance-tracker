package com.cph.att.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.cph.att.core.Employee;

import java.util.ArrayList;
import java.util.List;

public class EmployeeDao {

    private final DbHelper helper;

    public EmployeeDao(DbHelper helper) {
        this.helper = helper;
    }

    public List<Employee> all(boolean includeArchived) {
        List<Employee> out = new ArrayList<>();
        String where = includeArchived ? null : "active = 1";
        Cursor c = null;
        try {
            c = helper.getReadableDatabase().query(DbHelper.T_EMPLOYEES, null, where,
                    null, null, null, "active DESC, name COLLATE NOCASE ASC");
            while (c.moveToNext()) {
                out.add(read(c));
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public Employee byId(long id) {
        Cursor c = null;
        try {
            c = helper.getReadableDatabase().query(DbHelper.T_EMPLOYEES, null, "id = ?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (c.moveToFirst()) return read(c);
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    public int count() {
        Cursor c = null;
        try {
            c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + DbHelper.T_EMPLOYEES, null);
            if (c.moveToFirst()) return c.getInt(0);
        } finally {
            if (c != null) c.close();
        }
        return 0;
    }

    public long insert(Employee e) {
        ContentValues cv = write(e);
        cv.put("created_at", e.createdAt > 0 ? e.createdAt : System.currentTimeMillis());
        return helper.getWritableDatabase().insert(DbHelper.T_EMPLOYEES, null, cv);
    }

    public int update(Employee e) {
        ContentValues cv = write(e);
        return helper.getWritableDatabase().update(DbHelper.T_EMPLOYEES, cv,
                "id = ?", new String[]{String.valueOf(e.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(DbHelper.T_ATTENDANCE, "employee_id = ?", new String[]{String.valueOf(id)});
        db.delete(DbHelper.T_EMPLOYEES, "id = ?", new String[]{String.valueOf(id)});
    }

    public void archive(long id, boolean archived) {
        ContentValues cv = new ContentValues();
        cv.put("active", archived ? 0 : 1);
        helper.getWritableDatabase().update(DbHelper.T_EMPLOYEES, cv,
                "id = ?", new String[]{String.valueOf(id)});
    }

    private ContentValues write(Employee e) {
        ContentValues cv = new ContentValues();
        cv.put("name", e.name);
        cv.put("code", e.code);
        cv.put("position", e.position);
        cv.put("rate", e.rate);
        cv.put("shift_start", e.shiftStart);
        cv.put("shift_end", e.shiftEnd);
        cv.put("workdays", e.workdays);
        cv.put("active", e.active ? 1 : 0);
        return cv;
    }

    private Employee read(Cursor c) {
        Employee e = new Employee();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.name = safe(c, "name");
        e.code = safe(c, "code");
        e.position = safe(c, "position");
        e.rate = c.getDouble(c.getColumnIndexOrThrow("rate"));
        e.shiftStart = c.getInt(c.getColumnIndexOrThrow("shift_start"));
        e.shiftEnd = c.getInt(c.getColumnIndexOrThrow("shift_end"));
        e.workdays = safe(c, "workdays");
        e.active = c.getInt(c.getColumnIndexOrThrow("active")) == 1;
        e.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return e;
    }

    private String safe(Cursor c, String column) {
        int idx = c.getColumnIndex(column);
        if (idx < 0 || c.isNull(idx)) return "";
        return c.getString(idx);
    }
}
