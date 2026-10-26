package com.cph.att.ui;

import android.content.Context;
import android.content.res.ColorStateList;

import com.cph.att.R;
import com.cph.att.core.AttendanceStatus;

/** Shared colours for attendance statuses. */
public final class StatusStyle {

    private StatusStyle() {
    }

    public static int color(Context context, String status) {
        if (status == null) return context.getResources().getColor(R.color.text_mute);
        switch (status) {
            case AttendanceStatus.PRESENT:
                return context.getResources().getColor(R.color.success);
            case AttendanceStatus.LATE:
            case AttendanceStatus.HALF_DAY:
            case AttendanceStatus.INCOMPLETE:
                return context.getResources().getColor(R.color.warn);
            case AttendanceStatus.OPEN:
                return context.getResources().getColor(R.color.info);
            case AttendanceStatus.LEAVE:
            case AttendanceStatus.HOLIDAY:
                return context.getResources().getColor(R.color.primary_light);
            case AttendanceStatus.ABSENT:
                return context.getResources().getColor(R.color.danger);
            default:
                return context.getResources().getColor(R.color.text_mute);
        }
    }

    public static ColorStateList background(Context context, String status) {
        return ColorStateList.valueOf(Ui.withAlpha(color(context, status), 34));
    }
}
