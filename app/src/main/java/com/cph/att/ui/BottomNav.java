package com.cph.att.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cph.att.R;

/** Custom bottom bar: icon + label, filled pill on the active tab. */
public class BottomNav extends LinearLayout {

    private static final int[] ICONS = {
            R.drawable.ic_dashboard,
            R.drawable.ic_history,
            R.drawable.ic_people,
            R.drawable.ic_chart
    };

    private static final int[] LABELS = {
            R.string.nav_dashboard,
            R.string.nav_history,
            R.string.nav_team,
            R.string.nav_reports
    };

    private final View[] items = new View[4];
    private final ImageView[] icons = new ImageView[4];
    private final TextView[] labels = new TextView[4];

    private int current = -1;

    public BottomNav(Context context) {
        super(context);
        init();
    }

    public BottomNav(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BottomNav(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private int dp(int value) {
        return Ui.dp(getContext(), value);
    }

    private void init() {
        setOrientation(HORIZONTAL);        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.bg_nav);
        setPadding(dp(10), dp(6), dp(10), dp(6));

        for (int i = 0; i < 4; i++) {
            final int index = i;

            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(6), dp(8), dp(6), dp(8));
            item.setClickable(true);
            item.setFocusable(true);

            ImageView icon = new ImageView(getContext());
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(21), dp(21));
            icon.setLayoutParams(iconLp);
            icon.setImageResource(ICONS[i]);
            icon.setColorFilter(getResources().getColor(R.color.text_mute));

            TextView label = new TextView(getContext());
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(3);
            label.setLayoutParams(labelLp);
            label.setText(LABELS[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            label.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                    android.graphics.Typeface.NORMAL));
            label.setTextColor(getResources().getColor(R.color.text_mute));

            item.addView(icon);
            item.addView(label);

            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            item.setLayoutParams(lp);

            item.setOnClickListener(v -> {
                if (index == current) return;
                // The destination marks itself in onResume. Marking it here too would
                // leave this bar showing the wrong tab when the activity is resumed
                // again through FLAG_ACTIVITY_CLEAR_TOP.
                Nav.go(getContext(), index);
            });

            items[i] = item;
            icons[i] = icon;
            labels[i] = label;
            addView(item);
        }
    }

    public void setCurrent(int tab) {
        current = tab;
        int active = getResources().getColor(R.color.accent);
        int idle = getResources().getColor(R.color.text_mute);
        for (int i = 0; i < 4; i++) {
            boolean isActive = i == tab;
            icons[i].setColorFilter(isActive ? active : idle);
            labels[i].setTextColor(isActive ? active : idle);
            if (isActive) {
                items[i].setBackgroundResource(R.drawable.bg_round_20);
                items[i].setBackgroundTintList(ColorStateList.valueOf(
                        Ui.withAlpha(getResources().getColor(R.color.primary), 40)));
                items[i].setAlpha(1f);
            } else {
                items[i].setBackground(null);
                items[i].setAlpha(0.86f);
            }
        }
    }
}
