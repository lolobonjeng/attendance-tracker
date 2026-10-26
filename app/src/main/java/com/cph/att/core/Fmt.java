package com.cph.att.core;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Date / number formatting. Pure Java, no Android. */
public final class Fmt {

    private static final String[] MONTHS = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };
    private static final String[] DAYS_SHORT = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] DAYS_LONG = {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    };

    private Fmt() {
    }

    public static String key(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static String todayKey() {
        return key(Calendar.getInstance());
    }

    public static String keyOf(int year, int monthZeroBased, int day) {
        return String.format(Locale.US, "%04d-%02d-%02d", year, monthZeroBased + 1, day);
    }

    public static Calendar parse(String dateKey) {
        Calendar c = Calendar.getInstance();
        c.clear();
        if (dateKey == null || dateKey.length() < 10) {
            return c;
        }
        try {
            int y = Integer.parseInt(dateKey.substring(0, 4));
            int m = Integer.parseInt(dateKey.substring(5, 7)) - 1;
            int d = Integer.parseInt(dateKey.substring(8, 10));
            c.set(y, m, d);
        } catch (NumberFormatException ignored) {
        }
        return c;
    }

    /** "Mon, Oct 26" */
    public static String pretty(String dateKey) {
        Calendar c = parse(dateKey);
        if (c.getTimeInMillis() == 0) return dateKey;
        return DAYS_SHORT[c.get(Calendar.DAY_OF_WEEK) - 1] + ", "
                + MONTHS[c.get(Calendar.MONTH)].substring(0, 3) + " "
                + c.get(Calendar.DAY_OF_MONTH);
    }

    /** "Monday, October 26, 2026" */
    public static String prettyLong(String dateKey) {
        Calendar c = parse(dateKey);
        if (c.getTimeInMillis() == 0) return dateKey;
        return DAYS_LONG[c.get(Calendar.DAY_OF_WEEK) - 1] + ", "
                + MONTHS[c.get(Calendar.MONTH)] + " "
                + c.get(Calendar.DAY_OF_MONTH) + ", " + c.get(Calendar.YEAR);
    }

    /** "October 2026" */
    public static String monthLabel(String monthKey) {
        if (monthKey == null || monthKey.length() < 7) return monthKey;
        try {
            int m = Integer.parseInt(monthKey.substring(5, 7)) - 1;
            int y = Integer.parseInt(monthKey.substring(0, 4));
            if (m < 0 || m > 11) return monthKey;
            return MONTHS[m] + " " + y;
        } catch (NumberFormatException e) {
            return monthKey;
        }
    }

    public static String monthKey(String dateKey) {
        if (dateKey == null || dateKey.length() < 7) return "";
        return dateKey.substring(0, 7);
    }

    /** "9 Oct 2026" — short month, always with the year. */
    public static String dateLabel(String dateKey) {
        Calendar c = parse(dateKey);
        if (c.getTimeInMillis() == 0) return dateKey;
        return c.get(Calendar.DAY_OF_MONTH) + " "
                + MONTHS[c.get(Calendar.MONTH)].substring(0, 3) + " " + c.get(Calendar.YEAR);
    }

    /**
     * Inclusive day count from {@code from} to {@code to}. Returns 0 when either
     * key is unreadable or the range runs backwards, so callers can treat 0 as
     * "nothing selected" without a second check.
     */
    public static int daysBetween(String from, String to) {
        Calendar a = parse(from);
        Calendar b = parse(to);
        if (a.getTimeInMillis() == 0 || b.getTimeInMillis() == 0) return 0;
        // Round instead of floor: a DST change makes one day 23 or 25 hours long,
        // and floor would lose a day on the 23-hour side.
        long days = Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000d) + 1;
        if (days < 0 || days > Integer.MAX_VALUE) return 0;
        return (int) days;
    }

    public static String addDays(String dateKey, int days) {
        Calendar c = parse(dateKey);
        c.add(Calendar.DAY_OF_MONTH, days);
        return key(c);
    }

    public static String addMonths(String monthKey, int months) {
        Calendar c = parse(monthKey + "-01");
        c.add(Calendar.MONTH, months);
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    public static String firstDayOfMonth(String monthKey) {
        return monthKey + "-01";
    }

    public static String lastDayOfMonth(String monthKey) {
        Calendar c = parse(monthKey + "-01");
        return key(new CalendarBuilder(c).lastDay().build());
    }

    public static int dayOfWeek(String dateKey) {
        return parse(dateKey).get(Calendar.DAY_OF_WEEK);
    }

    /** 1 = Monday ... 7 = Sunday */
    public static int isoDay(String dateKey) {
        int dow = parse(dateKey).get(Calendar.DAY_OF_WEEK);
        return dow == Calendar.SUNDAY ? 7 : dow - 1;
    }

    public static String weekdayShort(String dateKey) {
        return DAYS_SHORT[parse(dateKey).get(Calendar.DAY_OF_WEEK) - 1];
    }

    public static String dayNumber(String dateKey) {
        return String.valueOf(parse(dateKey).get(Calendar.DAY_OF_MONTH));
    }

    public static String money(double amount, String symbol) {
        String s = String.format(Locale.US, "%,.2f", amount);
        return (symbol == null ? "" : symbol) + s;
    }

    public static String stamp(long millis) {
        if (millis <= 0) return "—";
        SimpleDateFormat f = new SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US);
        return f.format(new Date(millis));
    }

    public static String fileStamp() {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        return f.format(new Date());
    }

    public static boolean isWeekend(String dateKey) {
        int dow = parse(dateKey).get(Calendar.DAY_OF_WEEK);
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
    }

    public static boolean worksOn(String workdays, String dateKey) {
        if (workdays == null || workdays.isEmpty()) return true;
        String needle = String.valueOf(isoDay(dateKey));
        String[] parts = workdays.split(",");
        for (String part : parts) {
            if (part.trim().equals(needle)) return true;
        }
        return false;
    }

    public static String workdayLabel(String workdays) {
        if (workdays == null || workdays.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        String[] parts = workdays.split(",");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            try {
                int d = Integer.parseInt(p);
                if (d >= 1 && d <= 7) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(DAYS_SHORT[d % 7].charAt(0));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return sb.toString();
    }

    /** Small helper so lastDayOfMonth stays readable. */
    private static final class CalendarBuilder {
        private final Calendar c;

        CalendarBuilder(Calendar c) {
            this.c = (Calendar) c.clone();
        }

        CalendarBuilder lastDay() {
            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
            return this;
        }

        Calendar build() {
            return c;
        }
    }
}
