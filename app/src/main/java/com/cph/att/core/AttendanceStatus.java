package com.cph.att.core;

/** Status values stored in the attendance table. */
public final class AttendanceStatus {

    public static final String PRESENT = "present";
    public static final String LATE = "late";
    public static final String HALF_DAY = "half_day";
    public static final String INCOMPLETE = "incomplete";
    public static final String OPEN = "open";
    public static final String ABSENT = "absent";
    public static final String LEAVE = "leave";
    public static final String HOLIDAY = "holiday";

    private AttendanceStatus() {
    }

    public static String of(PunchSet p, WorkCalc.Result r, int halfDayThresholdMinutes) {
        if (p == null || p.in1 == null) return ABSENT;
        if (!r.complete) return r.working || r.onBreak ? OPEN : INCOMPLETE;
        if (r.worked < halfDayThresholdMinutes) return HALF_DAY;
        return r.late > 0 ? LATE : PRESENT;
    }

    public static String label(String status) {
        if (status == null) return "—";
        switch (status) {
            case PRESENT: return "Present";
            case LATE: return "Late";
            case HALF_DAY: return "Half day";
            case INCOMPLETE: return "Incomplete";
            case OPEN: return "In progress";
            case ABSENT: return "Absent";
            case LEAVE: return "Leave";
            case HOLIDAY: return "Holiday";
            default: return status;
        }
    }

    public static boolean isWorked(String status) {
        return PRESENT.equals(status) || LATE.equals(status)
                || HALF_DAY.equals(status) || OPEN.equals(status)
                || INCOMPLETE.equals(status);
    }
}
