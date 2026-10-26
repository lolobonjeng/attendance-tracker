package com.cph.att.core;

import java.util.Locale;

/**
 * Time helpers. Pure Java on purpose: no Android imports so the logic can be
 * compiled and unit tested outside of the device.
 */
public final class TimeCalc {

    public static final int MIN_PER_DAY = 1440;

    private TimeCalc() {
    }

    /** Parses "9:42", "09:42", "9:42 PM", "21:42", "9 PM". Returns null when invalid. */
    public static Integer parseTime(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase(Locale.US).replace(".", "").replace(" ", "");
        if (s.isEmpty()) return null;

        boolean pm = s.endsWith("PM");
        boolean am = s.endsWith("AM");
        if (pm || am) {
            s = s.substring(0, s.length() - 2);
        }
        if (s.isEmpty()) return null;

        String[] parts = s.split(":");
        if (parts.length > 2) return null;

        int hour;
        int minute = 0;
        try {
            hour = Integer.parseInt(parts[0]);
            if (parts.length == 2) {
                String mm = parts[1];
                if (mm.isEmpty()) return null;
                minute = Integer.parseInt(mm);
            }
        } catch (NumberFormatException e) {
            return null;
        }

        if (minute < 0 || minute > 59) return null;

        if (pm || am) {
            if (hour < 1 || hour > 12) return null;
            if (pm && hour != 12) hour += 12;
            if (am && hour == 12) hour = 0;
        } else if (hour < 0 || hour > 23) {
            return null;
        }
        return hour * 60 + minute;
    }

    /** "9:42 AM" */
    public static String format12(int minutes) {
        int m = ((minutes % MIN_PER_DAY) + MIN_PER_DAY) % MIN_PER_DAY;
        int h24 = m / 60;
        int mm = m % 60;
        int h12 = h24 % 12;
        if (h12 == 0) h12 = 12;
        String suffix = h24 < 12 ? "AM" : "PM";
        return String.format(Locale.US, "%d:%02d %s", h12, mm, suffix);
    }

    /** "09:42" */
    public static String format24(int minutes) {
        int m = ((minutes % MIN_PER_DAY) + MIN_PER_DAY) % MIN_PER_DAY;
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    /** "7h 46m", "46m", "8h" */
    public static String formatDuration(int minutes) {
        int m = Math.max(0, minutes);
        int h = m / 60;
        int mm = m % 60;
        if (h == 0) return mm + "m";
        if (mm == 0) return h + "h";
        return h + "h " + mm + "m";
    }

    /** "07:46" — compact, used inside cards */
    public static String formatHhMm(int minutes) {
        int m = Math.max(0, minutes);
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    /** "07:46:09" — live stopwatch */
    public static String formatStopwatch(int totalSeconds) {
        int s = Math.max(0, totalSeconds);
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int sec = s % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, sec);
    }

    /** Minutes since midnight for the device clock. */
    public static int nowMinutes() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
    }

    public static int nowSecondsOfDay() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 3600
                + c.get(java.util.Calendar.MINUTE) * 60
                + c.get(java.util.Calendar.SECOND);
    }

    /** Handles shifts that run past midnight. */
    public static int duration(int from, int to) {
        int d = to - from;
        if (d < 0) d += MIN_PER_DAY;
        return d;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
