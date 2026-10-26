package com.cph.att.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

/** Small shared UI helpers. */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static void tint(ImageView view, int color) {
        view.setColorFilter(color);
    }

    public static void tintBackground(View view, int color) {
        if (view.getBackground() == null) return;
        view.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    public static void roundBackground(View view, int color, float alpha) {
        view.setBackgroundResource(com.cph.att.R.drawable.bg_circle);
        view.setBackgroundTintList(ColorStateList.valueOf(withAlpha(color, (int) (alpha * 255))));
    }

    public static void squareBackground(View view, int color, float alpha) {
        view.setBackgroundResource(com.cph.att.R.drawable.bg_round_16);
        view.setBackgroundTintList(ColorStateList.valueOf(withAlpha(color, (int) (alpha * 255))));
    }

    /** Edge-to-edge with transparent system bars, falling back gracefully. */
    public static void setupWindow(Activity activity) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(0);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }

    public static void applyTopInset(View view) {
        if (view == null) return;
        final int base = view.getPaddingTop();
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars());
                top = bars.top;
            } else {
                top = insets.getSystemWindowInsetTop();
            }
            v.setPadding(v.getPaddingLeft(), base + top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    public static void applyBottomInset(View view) {
        if (view == null) return;
        final int base = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars());
                bottom = bars.bottom;
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), base + bottom);
            return insets;
        });
    }

    /** Grows a fixed-height view (bottom bar) so the system gesture area never overlaps it. */
    public static void growForBottomInset(final View view) {
        if (view == null || view.getLayoutParams() == null) return;
        final int baseHeight = view.getLayoutParams().height;
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars());
                bottom = bars.bottom;
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = baseHeight + bottom;
            v.setLayoutParams(lp);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            return insets;
        });
    }
}
