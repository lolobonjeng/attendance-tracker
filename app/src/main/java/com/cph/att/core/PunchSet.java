package com.cph.att.core;

/**
 * The six daily punches plus the overtime pair.
 *
 * Order matters and is the canonical sequence used across the app:
 *   0 CHECK_IN      1 BREAK_OUT_1    2 BREAK_IN_1
 *   3 BREAK_OUT_2   4 BREAK_IN_2     5 CHECK_OUT
 */
public class PunchSet {

    public static final int CHECK_IN = 0;
    public static final int BREAK_OUT_1 = 1;
    public static final int BREAK_IN_1 = 2;
    public static final int BREAK_OUT_2 = 3;
    public static final int BREAK_IN_2 = 4;
    public static final int CHECK_OUT = 5;

    public static final int COUNT = 6;

    /** Minutes since midnight, null when not punched. */
    public Integer in1;
    public Integer out1;
    public Integer in2;
    public Integer out2;
    public Integer in3;
    public Integer out3;

    public Integer otIn;
    public Integer otOut;

    public Integer get(int index) {
        switch (index) {
            case 0: return in1;
            case 1: return out1;
            case 2: return in2;
            case 3: return out2;
            case 4: return in3;
            case 5: return out3;
            default: return null;
        }
    }

    public void set(int index, Integer value) {
        switch (index) {
            case 0: in1 = value; break;
            case 1: out1 = value; break;
            case 2: in2 = value; break;
            case 3: out2 = value; break;
            case 4: in3 = value; break;
            case 5: out3 = value; break;
            default: break;
        }
    }

    public void clear() {
        in1 = null;
        out1 = null;
        in2 = null;
        out2 = null;
        in3 = null;
        out3 = null;
        otIn = null;
        otOut = null;
    }

    public boolean isEmpty() {
        for (int i = 0; i < COUNT; i++) {
            if (get(i) != null) return false;
        }
        return otIn == null && otOut == null;
    }

    public int filledCount() {
        int n = 0;
        for (int i = 0; i < COUNT; i++) {
            if (get(i) != null) n++;
        }
        return n;
    }

    /** First empty slot in the canonical sequence, or COUNT when the day is done. */
    public int nextSlot() {
        if (out3 != null) return COUNT;
        for (int i = 0; i < COUNT; i++) {
            if (get(i) == null) return i;
        }
        return COUNT;
    }

    /** Index of the last filled slot, or -1. */
    public int lastFilled() {
        for (int i = COUNT - 1; i >= 0; i--) {
            if (get(i) != null) return i;
        }
        return -1;
    }

    public static boolean isIn(int index) {
        return index == CHECK_IN || index == BREAK_IN_1 || index == BREAK_IN_2;
    }

    public static String label(int index) {
        switch (index) {
            case CHECK_IN: return "Check In";
            case BREAK_OUT_1: return "Break Out";
            case BREAK_IN_1: return "Break In";
            case BREAK_OUT_2: return "Break Out 2";
            case BREAK_IN_2: return "Break In 2";
            case CHECK_OUT: return "Check Out";
            default: return "";
        }
    }

    public static String hint(int index) {
        switch (index) {
            case CHECK_IN: return "Start your shift";
            case BREAK_OUT_1: return "Lunch break starts";
            case BREAK_IN_1: return "Back from lunch";
            case BREAK_OUT_2: return "Coffee break starts";
            case BREAK_IN_2: return "Back from coffee";
            case CHECK_OUT: return "End your shift";
            default: return "";
        }
    }

    public PunchSet copy() {
        PunchSet p = new PunchSet();
        p.in1 = in1;
        p.out1 = out1;
        p.in2 = in2;
        p.out2 = out2;
        p.in3 = in3;
        p.out3 = out3;
        p.otIn = otIn;
        p.otOut = otOut;
        return p;
    }
}
