package com.cph.att.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.cph.att.R;

/** Small in-app banner used instead of the default toast. */
public final class Banner {

    private Banner() {
    }

    public static void show(Activity activity, String message) {
        show(activity, message, activity.getResources().getColor(R.color.surface_2),
                activity.getResources().getColor(R.color.text));
    }

    public static void success(Activity activity, String message) {
        show(activity, message,
                activity.getResources().getColor(R.color.success),
                Color.parseColor("#06231A"));
    }

    public static void error(Activity activity, String message) {
        show(activity, message,
                activity.getResources().getColor(R.color.danger),
                Color.parseColor("#2A0810"));
    }

    private static void show(Activity activity, String message, int background, int textColor) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        ViewGroup root;
        if (decor instanceof ViewGroup) {
            View content = decor.findViewById(android.R.id.content);
            root = content instanceof ViewGroup ? (ViewGroup) content : (ViewGroup) decor;
        } else {
            return;
        }

        final TextView view = new TextView(activity);
        view.setText(message);
        view.setTextColor(textColor);
        view.setTextSize(13.5f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(activity, 20), dp(activity, 14), dp(activity, 20), dp(activity, 14));
        view.setBackgroundResource(R.drawable.bg_round_16);
        view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(background));
        view.setElevation(dp(activity, 8));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(activity, 54);
        lp.leftMargin = dp(activity, 24);
        lp.rightMargin = dp(activity, 24);

        root.addView(view, lp);
        view.setAlpha(0f);
        view.setTranslationY(-dp(activity, 24));
        view.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            view.animate().alpha(0f).translationY(-dp(activity, 18)).setDuration(200)
                    .withEndAction(() -> {
                        ViewGroup parent = (ViewGroup) view.getParent();
                        if (parent != null) parent.removeView(view);
                    }).start();
        }, 2300L);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
