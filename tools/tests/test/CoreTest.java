package test;

import com.cph.att.core.AppConfig;
import com.cph.att.core.AttendanceRecord;
import com.cph.att.core.AttendanceStatus;
import com.cph.att.core.Fmt;
import com.cph.att.core.PunchSet;
import com.cph.att.core.TimeCalc;
import com.cph.att.core.WorkCalc;

/** Plain-Java test harness for the attendance math. Run with tools/run_tests.sh */
public final class CoreTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        timeParsing();
        timeFormatting();
        fullDayExample();
        openDay();
        onBreak();
        overnight();
        overtimePair();
        overtimeAfterShift();
        undertimeAndHalfDay();
        lateStatus();
        dateHelpers();
        dateLabels();
        rangeMath();
        payMath();

        System.out.println();
        System.out.println("passed: " + passed + "   failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void timeParsing() {
        eq("parse 9:42 AM", 582, TimeCalc.parseTime("9:42 AM"));
        eq("parse 09:42", 582, TimeCalc.parseTime("09:42"));
        eq("parse 21:42", 1302, TimeCalc.parseTime("21:42"));
        eq("parse 12:00 AM", 0, TimeCalc.parseTime("12:00 AM"));
        eq("parse 12:00 PM", 720, TimeCalc.parseTime("12:00 PM"));
        eq("parse 9 PM", 1260, TimeCalc.parseTime("9 PM"));
        eq("parse 0:30", 30, TimeCalc.parseTime("0:30"));
        eq("parse padded", 1302, TimeCalc.parseTime(" 9:42 pm "));
        eq("reject 25:00", null, TimeCalc.parseTime("25:00"));
        eq("reject 9:75", null, TimeCalc.parseTime("9:75"));
        eq("reject abc", null, TimeCalc.parseTime("abc"));
        eq("reject empty", null, TimeCalc.parseTime(""));
        eq("reject null", null, TimeCalc.parseTime(null));
    }

    private static void timeFormatting() {
        eq("fmt 582", "9:42 AM", TimeCalc.format12(582));
        eq("fmt 0", "12:00 AM", TimeCalc.format12(0));
        eq("fmt 720", "12:00 PM", TimeCalc.format12(720));
        eq("fmt 1302", "9:42 PM", TimeCalc.format12(1302));
        eq("fmt24 582", "09:42", TimeCalc.format24(582));
        eq("duration 466", "7h 46m", TimeCalc.formatDuration(466));
        eq("duration 60", "1h", TimeCalc.formatDuration(60));
        eq("duration 45", "45m", TimeCalc.formatDuration(45));
        eq("hhmm 466", "07:46", TimeCalc.formatHhMm(466));
        eq("stopwatch", "01:46:09", TimeCalc.formatStopwatch(3600 + 46 * 60 + 9));
        eq("duration 79", "1h 19m", TimeCalc.formatDuration(79));
    }

    /** The exact day from the user's screenshot. */
    private static void fullDayExample() {
        PunchSet p = new PunchSet();
        p.in1 = 9 * 60 + 42;    // 9:42
        p.out1 = 12 * 60 + 42;  // 12:42
        p.in2 = 13 * 60 + 42;   // 1:42
        p.out2 = 16 * 60 + 42;  // 4:42
        p.in3 = 17 * 60 + 1;    // 5:01
        p.out3 = 18 * 60 + 47;  // 6:47

        WorkCalc.Result r = WorkCalc.compute(p, 540, 1080, 80, 5, WorkCalc.OT_MODE_PAIR);

        eq("worked", 466, r.worked);
        eq("break", 79, r.breakTotal);
        eq("span", 545, r.span);
        eq("late", 37, r.late);
        eq("required", 460, r.required);
        eq("undertime", 0, r.undertime);
        eq("overtime", 0, r.overtime);
        eq("complete", true, r.complete);
        eq("not working", false, r.working);
        eq("progress", 1000, r.progressPermille);
        eq("status", AttendanceStatus.LATE,
                AttendanceStatus.of(p, r, 240));
        eq("duration text", "7h 46m", TimeCalc.formatDuration(r.worked));
    }

    private static void openDay() {
        PunchSet p = new PunchSet();
        p.in1 = 540;
        WorkCalc.Result r = WorkCalc.compute(p, 540, 1080, 80, 5, WorkCalc.OT_MODE_PAIR);

        eq("open working", true, r.working);
        eq("open since", 540, r.openSince);
        eq("open worked", 0, r.worked);
        eq("open complete", false, r.complete);
        eq("open status", AttendanceStatus.OPEN, AttendanceStatus.of(p, r, 240));
        eq("live worked", 60, r.liveWorked(600));
    }

    private static void onBreak() {
        PunchSet p = new PunchSet();
        p.in1 = 540;
        p.out1 = 720;
        WorkCalc.Result r = WorkCalc.compute(p, 540, 1080, 80, 5, WorkCalc.OT_MODE_PAIR);

        eq("break state", true, r.onBreak);
        eq("break since", 720, r.breakSince);
        eq("break worked", 180, r.worked);
        eq("break total", 0, r.breakTotal);
        eq("live break", 10, r.liveBreak(730));
    }

    private static void overnight() {
        PunchSet p = new PunchSet();
        p.in1 = 22 * 60;        // 10:00 PM
        p.out1 = 2 * 60;        // 2:00 AM next day
        p.in2 = 3 * 60;         // 3:00 AM
        p.out2 = 6 * 60;        // 6:00 AM
        WorkCalc.Result r = WorkCalc.compute(p, 1320, 360, 60, 0, WorkCalc.OT_MODE_PAIR);

        eq("overnight worked", 420, r.worked);
        eq("overnight break", 60, r.breakTotal);
    }

    private static void overtimePair() {
        PunchSet p = new PunchSet();
        p.in1 = 540;
        p.out3 = 1080;
        p.otIn = 11 * 60;       // 11:00 AM
        p.otOut = 21 * 60;      // 9:00 PM

        WorkCalc.Result r = WorkCalc.compute(p, 540, 1080, 80, 5, WorkCalc.OT_MODE_PAIR);
        eq("ot pair", 600, r.overtime);
        eq("ot pending", false, r.pendingOt);
    }

    private static void overtimeAfterShift() {
        PunchSet p = new PunchSet();
        p.in1 = 540;
        p.out3 = 18 * 60 + 47;  // 6:47 PM
        p.otOut = 20 * 60;      // 8:00 PM

        WorkCalc.Result r = WorkCalc.compute(p, 540, 1080, 80, 5, WorkCalc.OT_MODE_AFTER_SHIFT);
        eq("ot after shift", 73, r.overtime);
    }

    private static void undertimeAndHalfDay() {
        PunchSet p = new PunchSet();
        p.in1 = 600;
        p.out3 = 660;

        WorkCalc.Result r = WorkCalc.compute(p, 540, 1080, 80, 5, WorkCalc.OT_MODE_PAIR);
        eq("short worked", 60, r.worked);
        eq("undertime", 400, r.undertime);
        eq("half day", AttendanceStatus.HALF_DAY, AttendanceStatus.of(p, r, 240));
    }

    private static void lateStatus() {
        PunchSet p = new PunchSet();
        p.in1 = 600;
        p.out1 = 720;
        p.in2 = 780;
        p.out3 = 1080;

        WorkCalc.Result r = WorkCalc.compute(p, 540, 1080, 80, 5, WorkCalc.OT_MODE_PAIR);
        eq("late minutes", 55, r.late);
        eq("late status", AttendanceStatus.LATE, AttendanceStatus.of(p, r, 240));
    }

    private static void dateHelpers() {
        eq("feb 2026", "2026-02-28", Fmt.lastDayOfMonth("2026-02"));
        eq("feb 2024", "2024-02-29", Fmt.lastDayOfMonth("2024-02"));
        eq("oct 2026", "2026-10-31", Fmt.lastDayOfMonth("2026-10"));
        eq("add months", "2025-12", Fmt.addMonths("2026-01", -1));
        eq("add days", "2026-11-01", Fmt.addDays("2026-10-31", 1));
        eq("iso monday", 1, Fmt.isoDay("2026-10-26"));
        eq("month label", "October 2026", Fmt.monthLabel("2026-10"));
        eq("workday label", "M T W T F", Fmt.workdayLabel("1,2,3,4,5"));
        eq("works on monday", true, Fmt.worksOn("1,2,3,4,5", "2026-10-26"));
        eq("off on sunday", false, Fmt.worksOn("1,2,3,4,5", "2026-11-01"));
    }

    /**
     * Calendar.DAY_OF_WEEK is 1..7 while the label arrays are 0-based. Getting
     * that off by one crashed the Reports tab on any Saturday, so every weekday
     * is exercised explicitly.
     */
    private static void dateLabels() {
        String[] days = {
                "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29",
                "2026-10-30", "2026-10-31", "2026-11-01"
        };
        String[] shortNames = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        String[] longNames = {
                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        };

        for (int i = 0; i < days.length; i++) {
            try {
                eq("weekday short " + shortNames[i], shortNames[i], Fmt.weekdayShort(days[i]));
                eq("pretty " + shortNames[i], shortNames[i] + ", ", prefixOf(Fmt.pretty(days[i])));
                eq("pretty long " + shortNames[i], longNames[i] + ", ",
                        prefixOf(Fmt.prettyLong(days[i])));
            } catch (RuntimeException e) {
                report("date labels " + days[i], false, "no exception", e.toString());
            }
        }

        eq("saturday pretty", "Sat, Oct 31", Fmt.pretty("2026-10-31"));
        eq("sunday pretty long", "Sunday, November 1, 2026", Fmt.prettyLong("2026-11-01"));
        eq("last day oct 2026", "2026-10-31", Fmt.lastDayOfMonth("2026-10"));
        eq("workday label", "M T W T F", Fmt.workdayLabel("1,2,3,4,5"));
    }

    private static void rangeMath() {
        eq("date label", "9 Oct 2026", Fmt.dateLabel("2026-10-09"));
        eq("date label single digit", "1 Jan 2027", Fmt.dateLabel("2027-01-01"));

        eq("same day", 1, Fmt.daysBetween("2026-10-09", "2026-10-09"));
        eq("nine to thirty", 22, Fmt.daysBetween("2026-10-09", "2026-10-30"));
        eq("full month", 31, Fmt.daysBetween("2026-10-01", "2026-10-31"));
        eq("across months", 32, Fmt.daysBetween("2026-10-25", "2026-11-25"));
        eq("leap february", 29, Fmt.daysBetween("2028-02-01", "2028-02-29"));
        eq("non leap february", 28, Fmt.daysBetween("2026-02-01", "2026-02-28"));
        eq("reversed range", 0, Fmt.daysBetween("2026-10-30", "2026-10-09"));
        eq("bad input", 0, Fmt.daysBetween("", "2026-10-09"));

        // The picker places day 1 under the right weekday column.
        eq("oct 2026 starts thursday", 5, Fmt.dayOfWeek("2026-10-01"));
        eq("nov 2026 starts sunday", 1, Fmt.dayOfWeek("2026-11-01"));
    }

    /** Returns the part of {@code value} that {@code prefix} covers. */
    private static String prefixOf(String value) {
        if (value == null) return "";
        int cut = value.indexOf(", ");
        return cut < 0 ? value : value.substring(0, cut + 2);
    }

    private static void payMath() {
        AppConfig c = new AppConfig();
        c.otMultiplier = 1.25;
        near("base pay", 776.67, c.payFor(466, 0, 100));
        near("pay with ot", 901.67, c.payFor(466, 60, 100));
        near("no rate", 0, c.payFor(466, 60, 0));
        eq("required minutes", 460, c.requiredMinutes());
        eq("break allowance", 80, c.breakAllowance());
    }

    // ------------------------------------------------------------- assertions

    private static void eq(String label, Object expected, Object actual) {
        boolean ok;
        if (expected == null) {
            ok = actual == null;
        } else {
            ok = expected.equals(actual);
        }
        report(label, ok, expected, actual);
    }

    private static void near(String label, double expected, double actual) {
        boolean ok = Math.abs(expected - actual) < 0.01;
        report(label, ok, expected, actual);
    }

    private static void report(String label, boolean ok, Object expected, Object actual) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + label);
        } else {
            failed++;
            System.out.println("  FAIL " + label + "  expected=" + expected + " actual=" + actual);
        }
    }
}
