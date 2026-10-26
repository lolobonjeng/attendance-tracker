package com.cph.att.core;

public class AttendanceRecord extends PunchSet {

    public long id;
    public long employeeId;
    /** yyyy-MM-dd */
    public String date = "";
    public String status = AttendanceStatus.ABSENT;
    public String note = "";
    public long createdAt;
    public long updatedAt;

    public String employeeName = "";

    public boolean sameAs(AttendanceRecord other) {
        if (other == null) return false;
        return eq(in1, other.in1) && eq(out1, other.out1)
                && eq(in2, other.in2) && eq(out2, other.out2)
                && eq(in3, other.in3) && eq(out3, other.out3)
                && eq(otIn, other.otIn) && eq(otOut, other.otOut)
                && eqStr(status, other.status) && eqStr(note, other.note);
    }

    private static boolean eq(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean eqStr(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
