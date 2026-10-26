package com.cph.att.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cph.att.R;
import com.cph.att.core.Fmt;

/**
 * Calendar that picks a start and an end day. Every dialog in the app goes
 * through the SweetDialog look, so this one reuses the same card, theme and
 * dimmed backdrop.
 */
public final class RangePickerDialog {

    public interface OnRangePicked {
        void onPicked(String from, String to);
    }

    private static final int ROWS = 6;
    private static final int COLS = 7;

    private final Dialog dialog;
    private final Context context;
    private final OnRangePicked listener;

    private final TextView monthLabel;
    private final TextView pickedLabel;
    private final LinearLayout grid;
    private final View showButton;

    private final FrameLayout[][] cells = new FrameLayout[ROWS][COLS];
    private final TextView[][] numbers = new TextView[ROWS][COLS];
    private final LinearLayout[] rows = new LinearLayout[ROWS];
    private final String[] dayKeys = new String[ROWS * COLS];

    private String viewMonth;
    private String from;
    private String to;

    private RangePickerDialog(Context context, String initialFrom, String initialTo,
                              OnRangePicked listener) {
        this.context = context;
        this.listener = listener;
        this.from = initialFrom;
        this.to = initialTo;

        String anchor = initialFrom != null ? initialFrom : Fmt.todayKey();
        this.viewMonth = Fmt.monthKey(anchor);

        dialog = new Dialog(context, R.style.SweetDialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_range, null, false);
        dialog.setContentView(root);

        monthLabel = root.findViewById(R.id.txtMonth);
        pickedLabel = root.findViewById(R.id.txtPicked);
        grid = root.findViewById(R.id.rangeGrid);
        showButton = root.findViewById(R.id.btnShowRange);

        buildWeekHeader(root.findViewById(R.id.weekHeader));
        buildGrid();

        root.findViewById(R.id.btnRangeClose).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.btnCancelRange).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.btnPrevMonth).setOnClickListener(v -> shiftMonth(-1));
        root.findViewById(R.id.btnNextMonth).setOnClickListener(v -> shiftMonth(1));
        root.findViewById(R.id.btnClearRange).setOnClickListener(v -> {
            from = null;
            to = null;
            render();
        });
        showButton.setOnClickListener(v -> {
            if (from == null || to == null) return;
            dialog.dismiss();
            listener.onPicked(from, to);
        });

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.62f;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }

        render();
    }

    public static void show(AppCompatActivity activity, String from, String to,
                            OnRangePicked listener) {
        if (activity.isFinishing()) return;
        RangePickerDialog picker = new RangePickerDialog(activity, from, to, listener);
        picker.dialog.show();
        Window window = picker.dialog.getWindow();
        if (window != null) {
            int screen = activity.getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(screen - Ui.dp(activity, 24f), Ui.dp(activity, 380f));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // ------------------------------------------------------------------ build

    private void buildWeekHeader(LinearLayout header) {
        String[] names = {"S", "M", "T", "W", "T", "F", "S"};
        for (int i = 0; i < COLS; i++) {
            TextView label = new TextView(context);
            label.setText(names[i]);
            label.setGravity(Gravity.CENTER);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setTextColor(ContextCompat.getColor(context, R.color.text_mute));
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(lp);
            header.addView(label);
        }
    }

    private void buildGrid() {
        for (int row = 0; row < ROWS; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 38f));
            line.setLayoutParams(rowLp);
            grid.addView(line);
            rows[row] = line;

            for (int col = 0; col < COLS; col++) {
                final int slot = row * COLS + col;

                FrameLayout cell = new FrameLayout(context);
                LinearLayout.LayoutParams cellLp =
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                cell.setLayoutParams(cellLp);
                cell.setClickable(true);
                cell.setFocusable(true);
                cell.setBackgroundResource(R.drawable.bg_day_cell);
                cell.setOnClickListener(v -> onDayTapped(slot));

                TextView number = new TextView(context);
                FrameLayout.LayoutParams numberLp = new FrameLayout.LayoutParams(
                        Ui.dp(context, 32f), Ui.dp(context, 32f), Gravity.CENTER);
                number.setLayoutParams(numberLp);
                number.setGravity(Gravity.CENTER);
                number.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                number.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                cell.addView(number);

                line.addView(cell);
                cells[row][col] = cell;
                numbers[row][col] = number;
            }
        }
    }

    // ------------------------------------------------------------------ state

    private void shiftMonth(int delta) {
        viewMonth = Fmt.addMonths(viewMonth, delta);
        render();
    }

    private void onDayTapped(int slot) {
        String key = dayKeys[slot];
        if (key == null) return;

        if (from == null || to != null) {
            from = key;
            to = null;
        } else if (key.compareTo(from) < 0) {
            // Tapping earlier than the start means the user meant the other way round.
            to = from;
            from = key;
        } else {
            to = key;
        }
        render();
    }

    private void render() {
        monthLabel.setText(Fmt.monthLabel(viewMonth));

        String first = Fmt.firstDayOfMonth(viewMonth);
        String last = Fmt.lastDayOfMonth(viewMonth);
        int offset = Fmt.dayOfWeek(first) - 1; // 0 = Sunday, matching the header row
        int total = Fmt.daysBetween(first, last);
        int year = Integer.parseInt(viewMonth.substring(0, 4));
        int month = Integer.parseInt(viewMonth.substring(5, 7)) - 1;

        for (int i = 0; i < dayKeys.length; i++) {
            dayKeys[i] = null;
        }
        for (int day = 1; day <= total; day++) {
            dayKeys[offset + day - 1] = Fmt.keyOf(year, month, day);
        }

        int inRange = ContextCompat.getColor(context, R.color.text);
        int outside = ContextCompat.getColor(context, R.color.text_mute);
        int edge = ContextCompat.getColor(context, R.color.accent);

        for (int row = 0; row < ROWS; row++) {
            boolean rowUsed = false;
            for (int col = 0; col < COLS; col++) {
                int slot = row * COLS + col;
                String key = dayKeys[slot];
                TextView number = numbers[row][col];
                FrameLayout cell = cells[row][col];

                if (key == null) {
                    number.setText("");
                    number.setBackground(null);
                    cell.setClickable(false);
                    cell.setVisibility(View.INVISIBLE);
                    continue;
                }

                rowUsed = true;
                cell.setVisibility(View.VISIBLE);
                cell.setClickable(true);
                number.setText(String.valueOf(slot - offset + 1));

                boolean isStart = key.equals(from);
                boolean isEnd = key.equals(to);
                boolean inside = from != null && to != null
                        && key.compareTo(from) >= 0 && key.compareTo(to) <= 0;

                if (isStart || isEnd) {
                    number.setBackgroundResource(R.drawable.bg_day_edge);
                    number.setTextColor(edge);
                    number.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                } else if (inside) {
                    number.setBackgroundResource(R.drawable.bg_day_range);
                    number.setTextColor(inRange);
                    number.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                } else {
                    number.setBackground(null);
                    number.setTextColor(outside);
                    number.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                }
            }
            // Months that fit in five weeks must not leave an empty row behind.
            rows[row].setVisibility(rowUsed ? View.VISIBLE : View.GONE);
        }

        if (from == null) {
            pickedLabel.setText(R.string.range_none);
        } else if (to == null) {
            pickedLabel.setText(Fmt.dateLabel(from) + "  ·  "
                    + context.getString(R.string.range_pick_end));
        } else {
            int days = Fmt.daysBetween(from, to);
            pickedLabel.setText(Fmt.dateLabel(from) + "  –  " + Fmt.dateLabel(to)
                    + "  ·  " + days + (days == 1
                    ? context.getString(R.string.range_day)
                    : context.getString(R.string.range_days)));
        }

        boolean ready = from != null && to != null;
        showButton.setEnabled(ready);
        showButton.setAlpha(ready ? 1f : 0.45f);
    }
}
