package com.cph.att.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;

import com.cph.att.R;

/**
 * SweetAlert style dialog. Every dialog in the app goes through here so the
 * look stays consistent: rounded glass card, tinted icon badge, two buttons.
 */
public final class SweetDialog {

    public static final int TYPE_INFO = 0;
    public static final int TYPE_SUCCESS = 1;
    public static final int TYPE_WARNING = 2;
    public static final int TYPE_ERROR = 3;
    public static final int TYPE_QUESTION = 4;
    public static final int TYPE_LOADING = 5;

    public interface OnClickListener {
        void onClick(SweetDialog dialog);
    }

    public interface OnItemClickListener {
        void onItemClick(SweetDialog dialog, int index);
    }

    private final Dialog dialog;
    private final View root;
    private final TextView titleView;
    private final TextView messageView;
    private final EditText inputView;
    private final View inputWrap;
    private final LinearLayout listWrap;
    private final ProgressBar progressBar;
    private final ProgressBar spinner;
    private final TextView progressText;
    private final AppCompatButton confirmButton;
    private final AppCompatButton cancelButton;
    private final FrameLayout iconWrap;
    private final ImageView iconView;

    private OnClickListener confirmListener;
    private OnClickListener cancelListener;

    private SweetDialog(Context context, Builder b) {
        Context themed = context;
        dialog = new Dialog(themed, R.style.SweetDialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = LayoutInflater.from(themed).inflate(R.layout.dialog_sweet, null, false);
        dialog.setContentView(root);
        dialog.setCancelable(b.cancelable);

        titleView = root.findViewById(R.id.title);
        messageView = root.findViewById(R.id.message);
        inputView = root.findViewById(R.id.input);
        inputWrap = root.findViewById(R.id.inputWrap);
        listWrap = root.findViewById(R.id.listWrap);
        progressBar = root.findViewById(R.id.progress);
        spinner = root.findViewById(R.id.spinner);
        progressText = root.findViewById(R.id.progressText);
        confirmButton = root.findViewById(R.id.btnConfirm);
        cancelButton = root.findViewById(R.id.btnCancel);
        iconWrap = root.findViewById(R.id.iconWrap);
        iconView = root.findViewById(R.id.icon);

        int accent = accentFor(context, b.type);
        iconWrap.setBackgroundTintList(ColorStateList.valueOf(withAlpha(accent, 46)));
        iconView.setColorFilter(accent);
        iconView.setImageResource(iconFor(b));
        confirmButton.setBackgroundResource(backgroundFor(b.type));

        titleView.setText(b.title == null ? "" : b.title);
        if (b.message == null || b.message.length() == 0) {
            messageView.setVisibility(View.GONE);
        } else {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText(b.message);
        }

        if (b.type == TYPE_LOADING) {
            spinner.setVisibility(View.VISIBLE);
            progressBar.setVisibility(b.showProgress ? View.VISIBLE : View.GONE);
            progressText.setVisibility(b.showProgress ? View.VISIBLE : View.GONE);
            confirmButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            dialog.setCancelable(false);
        }

        if (b.inputHint != null) {
            inputWrap.setVisibility(View.VISIBLE);
            ((com.google.android.material.textfield.TextInputLayout) inputWrap).setHint(b.inputHint);
            if (b.input != null) inputView.setText(b.input);
            if (b.inputNumber) {
                inputView.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
            if (b.selectAllOnShow) {
                inputView.setSelection(inputView.getText().length());
            }
        }

        if (b.items != null && b.items.length > 0) {
            buildList(context, b);
        }

        if (b.confirmText != null) {
            confirmButton.setText(b.confirmText);
        }
        if (b.cancelText == null) {
            cancelButton.setVisibility(View.GONE);
        } else {
            cancelButton.setText(b.cancelText);
        }

        confirmListener = b.onConfirm;
        cancelListener = b.onCancel;

        confirmButton.setOnClickListener(v -> {
            if (confirmListener != null) confirmListener.onClick(this);
            else dialog.dismiss();
        });
        cancelButton.setOnClickListener(v -> {
            if (cancelListener != null) cancelListener.onClick(this);
            else dialog.dismiss();
        });

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.62f;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void buildList(Context context, Builder b) {
        listWrap.setVisibility(View.VISIBLE);
        for (int i = 0; i < b.items.length; i++) {
            final int index = i;
            TextView row = new TextView(context);
            row.setText(b.items[i]);
            row.setTextColor(context.getResources().getColor(R.color.text));
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            row.setPadding(dp(context, 16), dp(context, 15), dp(context, 16), dp(context, 15));
            row.setBackgroundResource(R.drawable.bg_row_ripple);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(context, 8);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (b.onItem != null) b.onItem.onItemClick(this, index);
            });
            listWrap.addView(row);
        }
    }

    public void show() {
        if (dialog.isShowing()) return;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int screen = root.getContext().getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(screen - dp(root.getContext(), 48), dp(root.getContext(), 400));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        if (inputWrap.getVisibility() == View.VISIBLE) {
            inputView.requestFocus();
            window = dialog.getWindow();
            if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    public void dismiss() {
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public String inputText() {
        return inputView.getText() == null ? "" : inputView.getText().toString().trim();
    }

    public EditText input() {
        return inputView;
    }

    public void setProgress(int percent) {
        progressBar.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        progressBar.setProgress(Math.max(0, Math.min(100, percent)));
    }

    public void setProgressText(String text) {
        progressText.setText(text);
    }

    public void setTitle(String text) {
        titleView.setText(text);
    }

    public void setMessage(String text) {
        messageView.setVisibility(View.VISIBLE);
        messageView.setText(text);
    }

    public void setButtonsEnabled(boolean enabled) {
        confirmButton.setEnabled(enabled);
        cancelButton.setEnabled(enabled);
    }

    // ------------------------------------------------------------- factories

    public static void info(Context context, String title, String message) {
        new Builder(context).type(TYPE_INFO).title(title).message(message)
                .confirmText(context.getString(R.string.dlg_ok)).build().show();
    }

    public static void success(Context context, String title, String message) {
        new Builder(context).type(TYPE_SUCCESS).title(title).message(message)
                .confirmText(context.getString(R.string.dlg_ok)).build().show();
    }

    public static void error(Context context, String title, String message) {
        new Builder(context).type(TYPE_ERROR).title(title).message(message)
                .confirmText(context.getString(R.string.dlg_ok)).build().show();
    }

    public static void warning(Context context, String title, String message) {
        new Builder(context).type(TYPE_WARNING).title(title).message(message)
                .confirmText(context.getString(R.string.dlg_ok)).build().show();
    }

    public static SweetDialog loading(Context context, String title, String message) {
        SweetDialog d = new Builder(context).type(TYPE_LOADING).title(title).message(message)
                .cancelable(false).build();
        d.show();
        return d;
    }

    public static SweetDialog loadingProgress(Context context, String title, String message) {
        SweetDialog d = new Builder(context).type(TYPE_LOADING).title(title).message(message)
                .showProgress(true).cancelable(false).build();
        d.show();
        return d;
    }

    // ---------------------------------------------------------------- builder

    public static Builder builder(Context context) {
        return new Builder(context);
    }

    public static final class Builder {
        private final Context context;
        private int type = TYPE_INFO;
        private String title;
        private String message;
        private String confirmText;
        private String cancelText;
        private String inputHint;
        private String input;
        private boolean inputNumber;
        private boolean selectAllOnShow;
        private boolean cancelable = true;
        private boolean showProgress;
        private int iconRes;
        private String[] items;
        private OnClickListener onConfirm;
        private OnClickListener onCancel;
        private OnItemClickListener onItem;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder type(int type) {
            this.type = type;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder confirmText(String text) {
            this.confirmText = text;
            return this;
        }

        public Builder cancelText(String text) {
            this.cancelText = text;
            return this;
        }

        public Builder onConfirm(OnClickListener listener) {
            this.onConfirm = listener;
            return this;
        }

        public Builder onCancel(OnClickListener listener) {
            this.onCancel = listener;
            return this;
        }

        public Builder input(String hint, String preset) {
            this.inputHint = hint;
            this.input = preset;
            this.selectAllOnShow = true;
            return this;
        }

        public Builder numericInput(boolean numeric) {
            this.inputNumber = numeric;
            return this;
        }

        public Builder items(String[] items, OnItemClickListener listener) {
            this.items = items;
            this.onItem = listener;
            return this;
        }

        public Builder cancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Builder icon(int res) {
            this.iconRes = res;
            return this;
        }

        public Builder showProgress(boolean show) {
            this.showProgress = show;
            return this;
        }

        public SweetDialog build() {
            return new SweetDialog(context, this);
        }

        public SweetDialog showNow() {
            SweetDialog d = build();
            d.show();
            return d;
        }
    }

    // ------------------------------------------------------------------ theme

    private static int iconFor(Builder b) {
        if (b.iconRes != 0) return b.iconRes;
        switch (b.type) {
            case TYPE_SUCCESS:
                return R.drawable.ic_check;
            case TYPE_WARNING:
                return R.drawable.ic_warning;
            case TYPE_ERROR:
                return R.drawable.ic_close;
            case TYPE_QUESTION:
                return R.drawable.ic_info;
            case TYPE_LOADING:
                return R.drawable.ic_cloud_done;
            default:
                return R.drawable.ic_info;
        }
    }

    private static int backgroundFor(int type) {
        switch (type) {
            case TYPE_ERROR:
                return R.drawable.bg_btn_danger;
            case TYPE_SUCCESS:
                return R.drawable.bg_btn_primary;
            default:
                return R.drawable.bg_btn_primary;
        }
    }

    private static int accentFor(Context context, int type) {
        switch (type) {
            case TYPE_SUCCESS:
                return context.getResources().getColor(R.color.success);
            case TYPE_WARNING:
                return context.getResources().getColor(R.color.warn);
            case TYPE_ERROR:
                return context.getResources().getColor(R.color.danger);
            case TYPE_LOADING:
                return context.getResources().getColor(R.color.accent);
            default:
                return context.getResources().getColor(R.color.primary_light);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Convenience for activities that want the dialog tied to their lifecycle. */
    public static void show(Activity activity, SweetDialog dialog) {
        if (activity.isFinishing()) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
                && activity.isDestroyed()) return;
        dialog.show();
    }
}
