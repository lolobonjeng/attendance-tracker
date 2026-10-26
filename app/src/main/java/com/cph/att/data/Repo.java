package com.cph.att.data;

import android.content.Context;

import com.cph.att.core.AppConfig;
import com.cph.att.core.AttendanceRecord;
import com.cph.att.core.AttendanceStatus;
import com.cph.att.core.Employee;
import com.cph.att.core.Fmt;
import com.cph.att.core.WorkCalc;

import java.util.List;

/** Single entry point to the local database. */
public final class Repo {

    public interface Listener {
        void onDataChanged();
    }

    private static Repo instance;

    private final Context appContext;
    private final DbHelper helper;
    private final EmployeeDao employeeDao;
    private final AttendanceDao attendanceDao;
    private final SettingsDao settingsDao;

    private AppConfig config;
    /** UI hook. Static so it survives a restore (which recreates the singleton). */
    private static Listener listener;
    /** Autosave hook, registered once by the Application class. */
    private static Runnable changeHook;

    private Repo(Context context) {
        this.appContext = context.getApplicationContext();
        this.helper = new DbHelper(this.appContext);
        this.employeeDao = new EmployeeDao(helper);
        this.attendanceDao = new AttendanceDao(helper);
        this.settingsDao = new SettingsDao(helper);
        this.config = settingsDao.read();
    }

    public static synchronized Repo get(Context context) {
        if (instance == null) {
            instance = new Repo(context);
        }
        return instance;
    }

    /** Drops the cached instance so the next call reopens the (possibly restored) file. */
    public static synchronized void reset() {
        if (instance != null) {
            try {
                instance.helper.close();
            } catch (Exception ignored) {
            }
            instance = null;
        }
    }

    public static synchronized boolean isOpen() {
        return instance != null;
    }

    public DbHelper db() {
        return helper;
    }

    public Context context() {
        return appContext;
    }

    public void setListener(Listener listener) {
        Repo.listener = listener;
    }

    public static void setChangeHook(Runnable hook) {
        Repo.changeHook = hook;
    }

    public void notifyChanged() {
        if (listener != null) listener.onDataChanged();
        if (changeHook != null) changeHook.run();
    }

    public AppConfig config() {
        return config;
    }

    public void reloadConfig() {
        config = settingsDao.read();
    }

    public void saveConfig() {
        settingsDao.write(config);
        notifyChanged();
    }

    public void saveConfigQuietly() {
        settingsDao.write(config);
    }

    // ---------------------------------------------------------------- people

    public List<Employee> employees(boolean includeArchived) {
        return employeeDao.all(includeArchived);
    }

    public Employee employee(long id) {
        return employeeDao.byId(id);
    }

    public int employeeCount() {
        return employeeDao.count();
    }

    public long saveEmployee(Employee e) {
        long id;
        if (e.id > 0) {
            employeeDao.update(e);
            id = e.id;
        } else {
            id = employeeDao.insert(e);
            e.id = id;
        }
        notifyChanged();
        return id;
    }

    public void deleteEmployee(long id) {
        employeeDao.delete(id);
        if (config.selectedEmployeeId == id) {
            config.selectedEmployeeId = 0;
            saveConfigQuietly();
        }
        notifyChanged();
    }

    public void archiveEmployee(long id, boolean archived) {
        employeeDao.archive(id, archived);
        notifyChanged();
    }

    /** The employee currently shown on the dashboard. */
    public Employee currentEmployee() {
        Employee e = null;
        if (config.selectedEmployeeId > 0) {
            e = employeeDao.byId(config.selectedEmployeeId);
        }
        if (e == null || !e.active) {
            List<Employee> active = employeeDao.all(false);
            if (!active.isEmpty()) {
                e = active.get(0);
                config.selectedEmployeeId = e.id;
                saveConfigQuietly();
            } else {
                e = null;
            }
        }
        return e;
    }

    public void selectEmployee(long id) {
        config.selectedEmployeeId = id;
        saveConfigQuietly();
    }

    // ------------------------------------------------------------ attendance

    public AttendanceRecord record(long employeeId, String date) {
        return attendanceDao.find(employeeId, date);
    }

    public AttendanceRecord todayRecord(long employeeId) {
        return attendanceDao.find(employeeId, Fmt.todayKey());
    }

    public AttendanceRecord recordById(long id) {
        return attendanceDao.byId(id);
    }

    public List<AttendanceRecord> records(String fromDate, String toDate, long employeeId) {
        return attendanceDao.range(fromDate, toDate, employeeId);
    }

    public List<AttendanceRecord> recent(int limit) {
        return attendanceDao.recent(limit);
    }

    public int attendanceCount() {
        return attendanceDao.count();
    }

    public int lateCount(String fromDate, String toDate, long employeeId) {
        return attendanceDao.countLate(fromDate, toDate, employeeId);
    }

    public WorkCalc.Result compute(AttendanceRecord r, Employee e) {
        int shiftStart = e != null ? e.shiftStart : config.shiftStart;
        int shiftEnd = e != null ? e.shiftEnd : config.shiftEnd;
        return WorkCalc.compute(r, shiftStart, shiftEnd,
                config.breakAllowance(), config.graceMinutes, config.otMode);
    }

    public void applyStatus(AttendanceRecord r, Employee e) {
        if (r.in1 == null) {
            if (AttendanceStatus.LEAVE.equals(r.status)
                    || AttendanceStatus.HOLIDAY.equals(r.status)
                    || AttendanceStatus.ABSENT.equals(r.status)) {
                return;
            }
            r.status = AttendanceStatus.ABSENT;
            return;
        }
        WorkCalc.Result res = compute(r, e);
        r.status = AttendanceStatus.of(r, res, config.halfDayMinutes);
    }

    public AttendanceRecord saveRecord(AttendanceRecord r, Employee e) {
        applyStatus(r, e);
        attendanceDao.save(r);
        notifyChanged();
        return r;
    }

    public void deleteRecord(long id) {
        attendanceDao.delete(id);
        notifyChanged();
    }

    public void deleteRecord(long employeeId, String date) {
        attendanceDao.deleteFor(employeeId, date);
        notifyChanged();
    }

    /**
     * Writes a single punch into today's record.
     *
     * @return the updated record, or null when the employee is missing
     */
    public AttendanceRecord punchToday(long employeeId, int slot, int minutes) {
        Employee e = employeeDao.byId(employeeId);
        if (e == null) return null;
        String date = Fmt.todayKey();
        AttendanceRecord r = attendanceDao.find(employeeId, date);
        if (r == null) {
            r = new AttendanceRecord();
            r.employeeId = employeeId;
            r.date = date;
            r.status = AttendanceStatus.ABSENT;
        }
        r.set(slot, minutes);
        applyStatus(r, e);
        attendanceDao.save(r);
        notifyChanged();
        return r;
    }

    public AttendanceRecord setOvertime(long employeeId, String date, Integer otIn, Integer otOut) {
        Employee e = employeeDao.byId(employeeId);
        AttendanceRecord r = attendanceDao.find(employeeId, date);
        if (r == null) {
            r = new AttendanceRecord();
            r.employeeId = employeeId;
            r.date = date;
            r.status = AttendanceStatus.ABSENT;
        }
        r.otIn = otIn;
        r.otOut = otOut;
        if (e != null) applyStatus(r, e);
        attendanceDao.save(r);
        notifyChanged();
        return r;
    }

    /** Clears the last filled punch of today's record. */
    public AttendanceRecord undoLastPunch(long employeeId) {
        String date = Fmt.todayKey();
        AttendanceRecord r = attendanceDao.find(employeeId, date);
        if (r == null) return null;
        int last = r.lastFilled();
        if (last < 0) {
            if (r.otOut != null) {
                r.otOut = null;
            } else if (r.otIn != null) {
                r.otIn = null;
            }
        } else {
            r.set(last, null);
        }
        Employee e = employeeDao.byId(employeeId);
        applyStatus(r, e);
        if (r.isEmpty()) {
            attendanceDao.delete(r.id);
            notifyChanged();
            return null;
        }
        attendanceDao.save(r);
        notifyChanged();
        return r;
    }

    // ------------------------------------------------------------------ seed

    /** Creates a starter profile the very first time the app runs. */
    public void ensureSeed() {
        if (employeeDao.count() > 0) return;
        Employee e = new Employee();
        e.name = "My Attendance";
        e.code = "";
        e.position = "";
        e.rate = 0;
        e.shiftStart = config.shiftStart;
        e.shiftEnd = config.shiftEnd;
        e.workdays = "1,2,3,4,5";
        e.active = true;
        e.createdAt = System.currentTimeMillis();
        long id = employeeDao.insert(e);
        config.selectedEmployeeId = id;
        saveConfigQuietly();
    }
}
