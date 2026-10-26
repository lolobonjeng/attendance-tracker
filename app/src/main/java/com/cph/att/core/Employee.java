package com.cph.att.core;

public class Employee {

    public long id;
    public String name = "";
    public String code = "";
    public String position = "";
    public double rate;
    public int shiftStart = 540;   // 9:00
    public int shiftEnd = 1080;    // 18:00
    public String workdays = "1,2,3,4,5";
    public boolean active = true;
    public long createdAt;

    public String initials() {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "?";
        String[] parts = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    public String shiftLabel() {
        return TimeCalc.format12(shiftStart) + " – " + TimeCalc.format12(shiftEnd);
    }
}
