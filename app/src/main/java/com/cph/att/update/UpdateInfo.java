package com.cph.att.update;

public class UpdateInfo {

    public int versionCode;
    public String versionName = "";
    public String url = "";
    public String notes = "";
    public long size;
    public String sha256 = "";
    public boolean mandatory;

    public boolean isNewerThan(int installedCode) {
        return versionCode > installedCode;
    }

    public String sizeLabel() {
        if (size <= 0) return "";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", size / (1024d * 1024d));
    }
}
