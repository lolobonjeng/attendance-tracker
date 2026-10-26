package com.cph.att.core;

/** Mutable snapshot of the app settings. Mirrors the settings table. */
public class AppConfig {

    /** Set from Settings > Update link, or by pasting it into the first check. */
    public static final String DEFAULT_UPDATE_URL = "";

    public int shiftStart = 540;
    public int shiftEnd = 1080;
    public int graceMinutes = 5;
    public int lunchBreak = 60;
    public int coffeeBreak = 20;
    public int otMode = WorkCalc.OT_MODE_PAIR;
    public double otMultiplier = 1.25;
    public int halfDayMinutes = 240;
    public String currency = "\u20B1";
    public boolean autoBackup = true;
    public int backupKeep = 20;
    public boolean autoUpdateCheck = true;
    public String updateUrl = DEFAULT_UPDATE_URL;
    public long lastBackupAt = 0L;
    public long lastRestoreAt = 0L;
    public long lastUpdateCheck = 0L;
    public long selectedEmployeeId = 0L;
    public String orgName = "";

    public int breakAllowance() {
        return Math.max(0, lunchBreak) + Math.max(0, coffeeBreak);
    }

    public int requiredMinutes() {
        int end = shiftEnd;
        if (end <= shiftStart) end += TimeCalc.MIN_PER_DAY;
        return Math.max(0, (end - shiftStart) - breakAllowance());
    }

    public String shiftLabel() {
        return TimeCalc.format12(shiftStart) + " – " + TimeCalc.format12(shiftEnd);
    }

    public double payFor(int workedMinutes, int overtimeMinutes, double hourlyRate) {
        if (hourlyRate <= 0) return 0d;
        double base = (workedMinutes / 60d) * hourlyRate;
        double ot = (overtimeMinutes / 60d) * hourlyRate * Math.max(1d, otMultiplier);
        return base + ot;
    }
}
