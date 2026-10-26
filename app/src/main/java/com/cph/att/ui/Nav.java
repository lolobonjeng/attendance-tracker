package com.cph.att.ui;

import android.content.Context;
import android.content.Intent;

import com.cph.att.EmployeesActivity;
import com.cph.att.HistoryActivity;
import com.cph.att.MainActivity;
import com.cph.att.ReportsActivity;

/** Bottom navigation destinations. */
public final class Nav {

    public static final int TAB_TODAY = 0;
    public static final int TAB_HISTORY = 1;
    public static final int TAB_PEOPLE = 2;
    public static final int TAB_REPORTS = 3;

    private Nav() {
    }

    public static Class<?> target(int tab) {
        switch (tab) {
            case TAB_HISTORY:
                return HistoryActivity.class;
            case TAB_PEOPLE:
                return EmployeesActivity.class;
            case TAB_REPORTS:
                return ReportsActivity.class;
            default:
                return MainActivity.class;
        }
    }

    public static void go(Context context, int tab) {
        Intent intent = new Intent(context, target(tab));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
        if (context instanceof android.app.Activity) {
            // Tabs must swap instantly: a slide here makes the bar and the screen
            // disagree for a few frames, which reads as a glitch.
            ((android.app.Activity) context).overridePendingTransition(0, 0);
        }
    }
}
