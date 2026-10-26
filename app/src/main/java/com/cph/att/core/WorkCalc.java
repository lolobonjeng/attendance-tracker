package com.cph.att.core;

/**
 * All attendance math lives here so it can be unit tested without Android.
 */
public final class WorkCalc {

    public static final int OT_MODE_PAIR = 0;
    public static final int OT_MODE_AFTER_SHIFT = 1;

    private WorkCalc() {
    }

    public static final class Result {
        /** Minutes actually worked (breaks excluded). */
        public int worked;
        /** Total minutes spent on break. */
        public int breakTotal;
        /** First punch to last punch, breaks included. */
        public int span;
        /** Overtime minutes. */
        public int overtime;
        /** Minutes late versus shift start + grace. */
        public int late;
        /** Minutes short of the required time, only once checked out. */
        public int undertime;
        /** Minutes required for a full day. */
        public int required;

        public int openSince = -1;
        public int breakSince = -1;
        public boolean working;
        public boolean onBreak;
        public boolean complete;
        public boolean pendingOt;
        public int progressPermille;

        public int liveWorked(int nowMinutes) {
            if (!working || openSince < 0) return worked;
            int now = nowMinutes;
            while (now < openSince) now += TimeCalc.MIN_PER_DAY;
            return worked + Math.max(0, now - openSince);
        }

        public int liveBreak(int nowMinutes) {
            if (!onBreak || breakSince < 0) return breakTotal;
            int now = nowMinutes;
            while (now < breakSince) now += TimeCalc.MIN_PER_DAY;
            return breakTotal + Math.max(0, now - breakSince);
        }

        public boolean isOvertime() {
            return overtime > 0;
        }
    }

    public static Result compute(PunchSet p,
                                 int shiftStart,
                                 int shiftEnd,
                                 int breakAllowance,
                                 int grace,
                                 int otMode) {
        Result r = new Result();

        int[] t = new int[PunchSet.COUNT];
        boolean[] has = new boolean[PunchSet.COUNT];

        int offset = 0;
        int prev = -1;
        for (int i = 0; i < PunchSet.COUNT; i++) {
            Integer v = p.get(i);
            if (v == null) continue;
            int abs = v + offset;
            if (prev >= 0 && abs < prev) {
                offset += TimeCalc.MIN_PER_DAY;
                abs += TimeCalc.MIN_PER_DAY;
            }
            t[i] = abs;
            has[i] = true;
            prev = abs;
        }

        // Walk the punches in order so days with skipped breaks still add up:
        // an "in" opens a work segment, an "out" closes it and opens a break.
        int lastIn = -1;
        int lastOut = -1;
        int lastOutIndex = -1;
        for (int i = 0; i < PunchSet.COUNT; i++) {
            if (!has[i]) continue;
            if (PunchSet.isIn(i)) {
                if (lastOut >= 0) {
                    r.breakTotal += Math.max(0, t[i] - lastOut);
                    lastOut = -1;
                    lastOutIndex = -1;
                }
                lastIn = t[i];
            } else {
                if (lastIn >= 0) {
                    r.worked += Math.max(0, t[i] - lastIn);
                    lastIn = -1;
                }
                lastOut = t[i];
                lastOutIndex = i;
            }
        }

        if (has[0]) {
            int last = -1;
            for (int i = PunchSet.COUNT - 1; i >= 0; i--) {
                if (has[i]) {
                    last = i;
                    break;
                }
            }
            if (last > 0) r.span = t[last] - t[0];
        }

        r.complete = has[PunchSet.CHECK_OUT];
        if (!r.complete) {
            if (lastIn >= 0) {
                r.working = true;
                r.openSince = lastIn;
            } else if (lastOut >= 0 && lastOutIndex != PunchSet.CHECK_OUT) {
                r.onBreak = true;
                r.breakSince = lastOut;
            }
        }

        int end = shiftEnd;
        if (end <= shiftStart) end += TimeCalc.MIN_PER_DAY;
        r.required = Math.max(0, (end - shiftStart) - Math.max(0, breakAllowance));

        if (has[0]) {
            r.late = Math.max(0, t[0] - (shiftStart + Math.max(0, grace)));
        }

        if (r.complete) {
            r.undertime = Math.max(0, r.required - r.worked);
        }

        if (p.otIn != null && p.otOut != null) {
            int a = p.otIn;
            int b = p.otOut;
            if (b < a) b += TimeCalc.MIN_PER_DAY;
            r.overtime = Math.max(0, b - a);
        } else if (p.otOut != null) {
            int base = end;
            if (has[5] && t[5] > base) base = t[5];
            int b = p.otOut;
            if (b < base) b += TimeCalc.MIN_PER_DAY;
            r.overtime = Math.max(0, b - base);
        } else if (p.otIn != null) {
            r.pendingOt = true;
        }

        if (r.required > 0) {
            r.progressPermille = TimeCalc.clamp(r.worked * 1000 / r.required, 0, 1000);
        } else {
            r.progressPermille = r.worked > 0 ? 1000 : 0;
        }

        return r;
    }
}
