package com.cph.att;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cph.att.core.AttendanceRecord;
import com.cph.att.core.AttendanceStatus;
import com.cph.att.core.Employee;
import com.cph.att.core.Fmt;
import com.cph.att.core.PunchSet;
import com.cph.att.core.TimeCalc;
import com.cph.att.core.WorkCalc;
import com.cph.att.data.Repo;
import com.cph.att.databinding.ActivityMainBinding;
import com.cph.att.databinding.ItemPunchRowBinding;
import com.cph.att.databinding.ItemStatBinding;
import com.cph.att.ui.Banner;
import com.cph.att.ui.BottomNav;
import com.cph.att.ui.Nav;
import com.cph.att.ui.SweetDialog;
import com.cph.att.ui.Ui;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int SLOT_OT_IN = 6;
    private static final int SLOT_OT_OUT = 7;

    private ActivityMainBinding b;
    private Repo repo;
    private Employee employee;
    private AttendanceRecord record;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final List<ItemPunchRowBinding> rows = new ArrayList<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateLive();
            ticker.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        repo = Repo.get(this);
        repo.ensureSeed();
        repo.setListener(this::onDataChanged);

        b.nav.setCurrent(Nav.TAB_TODAY);
        Ui.applyTopInset(b.scroll);
        Ui.growForBottomInset(b.nav);

        buildTimeline();
        wireListeners();
        styleStats();
        maybeCheckUpdates();
    }

    private void maybeCheckUpdates() {
        com.cph.att.core.AppConfig cfg = repo.config();
        if (!cfg.autoUpdateCheck) return;
        if (cfg.updateUrl == null || cfg.updateUrl.trim().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - cfg.lastUpdateCheck < 24L * 60L * 60L * 1000L) return;
        com.cph.att.ui.UpdateFlow.check(this, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        b.nav.setCurrent(Nav.TAB_TODAY);
        repo.setListener(this::onDataChanged);
        refreshAll();
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    @Override
    protected void onPause() {
        ticker.removeCallbacks(tick);
        super.onPause();
    }

    private void wireListeners() {
        b.btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        b.chipEmployee.setOnClickListener(v -> showEmployeePicker());

        b.btnAction.setOnClickListener(v -> {
            b.btnAction.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            punchNext();
        });

        b.btnUndo.setOnClickListener(v -> confirmUndo());
        b.btnEditToday.setOnClickListener(v -> editToday());

        b.btnOtIn.setOnClickListener(v -> handleOvertime(SLOT_OT_IN));
        b.btnOtOut.setOnClickListener(v -> handleOvertime(SLOT_OT_OUT));

        b.btnCreateProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, EmployeesActivity.class);
            intent.putExtra(EmployeesActivity.EXTRA_ADD, true);
            startActivity(intent);
        });
    }

    private void styleStats() {
        ItemStatBinding worked = b.statWorked;
        worked.label.setText(R.string.dash_worked);
        worked.icon.setImageResource(R.drawable.ic_clock);
        worked.icon.setColorFilter(ContextCompat.getColor(this, R.color.accent));

        ItemStatBinding brk = b.statBreak;
        brk.label.setText(R.string.dash_break);
        brk.icon.setImageResource(R.drawable.ic_coffee);
        brk.icon.setColorFilter(ContextCompat.getColor(this, R.color.warn));

        ItemStatBinding ot = b.statOt;
        ot.label.setText(R.string.dash_ot);
        ot.icon.setImageResource(R.drawable.ic_timer);
        ot.icon.setColorFilter(ContextCompat.getColor(this, R.color.punch_ot));

        ItemStatBinding late = b.statLate;
        late.label.setText(R.string.dash_late);
        late.icon.setImageResource(R.drawable.ic_warning);
        late.icon.setColorFilter(ContextCompat.getColor(this, R.color.danger));
    }

    private void buildTimeline() {
        b.timeline.removeAllViews();
        rows.clear();
        for (int i = 0; i < PunchSet.COUNT; i++) {
            ItemPunchRowBinding row = ItemPunchRowBinding.inflate(getLayoutInflater(),
                    b.timeline, true);
            final int slot = i;
            row.getRoot().setOnClickListener(v -> editSlot(slot));
            rows.add(row);
        }
    }

    private void onDataChanged() {
        refreshAll();
    }

    private void refreshAll() {
        employee = repo.currentEmployee();
        if (employee == null) {
            showEmpty(true);
            return;
        }
        showEmpty(false);

        b.txtEmployee.setText(employee.name);
        b.txtInitials.setText(employee.initials());
        b.txtShift.setText(employee.shiftLabel());
        b.txtSubtitle.setText(Fmt.prettyLong(Fmt.todayKey()));

        record = repo.todayRecord(employee.id);
        if (record == null) {
            record = new AttendanceRecord();
            record.employeeId = employee.id;
            record.date = Fmt.todayKey();
            record.status = AttendanceStatus.ABSENT;
        }

        renderTimeline();
        updateLive();
        renderOvertime();
        renderAction();
        renderStatus();
    }

    private void showEmpty(boolean empty) {
        b.heroCard.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.emptyCard.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.chipEmployee.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.statWorked.getRoot().setVisibility(empty ? View.GONE : View.VISIBLE);
        b.statBreak.getRoot().setVisibility(empty ? View.GONE : View.VISIBLE);
        b.statOt.getRoot().setVisibility(empty ? View.GONE : View.VISIBLE);
        b.statLate.getRoot().setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void renderTimeline() {
        int slot = record.nextSlot();
        int filled = record.filledCount();
        b.txtTimelineCount.setText(filled + " / " + PunchSet.COUNT);

        for (int i = 0; i < rows.size(); i++) {
            ItemPunchRowBinding row = rows.get(i);
            row.label.setText(PunchSet.label(i));
            row.hint.setText(PunchSet.hint(i));

            Integer value = record.get(i);
            boolean done = value != null;
            boolean next = i == slot && slot < PunchSet.COUNT;

            row.time.setText(done ? TimeCalc.format12(value) : (next ? "Punch now" : "—"));

            int color;
            int icon;
            if (done) {
                color = ContextCompat.getColor(this, PunchSet.isIn(i) ? R.color.success : R.color.warn);
                icon = R.drawable.ic_check;
                row.time.setTextColor(ContextCompat.getColor(this, R.color.text));
                row.label.setTextColor(ContextCompat.getColor(this, R.color.text));
                row.hint.setVisibility(View.GONE);
            } else if (next) {
                color = ContextCompat.getColor(this, R.color.accent);
                icon = PunchSet.isIn(i) ? R.drawable.ic_play : R.drawable.ic_pause;
                row.time.setTextColor(ContextCompat.getColor(this, R.color.accent));
                row.label.setTextColor(ContextCompat.getColor(this, R.color.text));
                row.hint.setVisibility(View.VISIBLE);
            } else {
                color = ContextCompat.getColor(this, R.color.text_mute);
                icon = PunchSet.isIn(i) ? R.drawable.ic_play : R.drawable.ic_pause;
                row.time.setTextColor(ContextCompat.getColor(this, R.color.text_mute));
                row.label.setTextColor(ContextCompat.getColor(this, R.color.text_dim));
                row.hint.setVisibility(View.VISIBLE);
            }

            row.dotWrap.setBackgroundResource(R.drawable.bg_circle);
            row.dotWrap.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Ui.withAlpha(color, done ? 46 : next ? 34 : 18)));
            row.dotIcon.setImageResource(icon);
            row.dotIcon.setColorFilter(color);
            row.chevron.setAlpha(done || next ? 0.8f : 0.35f);
        }
    }

    private void renderAction() {
        int slot = record.nextSlot();
        if (slot >= PunchSet.COUNT) {
            b.btnAction.setEnabled(false);
            b.btnAction.setAlpha(0.55f);
            b.btnAction.setText("DAY COMPLETE");
        } else {
            b.btnAction.setEnabled(true);
            b.btnAction.setAlpha(1f);
            b.btnAction.setText(PunchSet.label(slot).toUpperCase(java.util.Locale.US));
        }
        boolean hasAny = !record.isEmpty();
        b.btnUndo.setEnabled(hasAny);
        b.btnUndo.setAlpha(hasAny ? 1f : 0.4f);
    }

    private void renderOvertime() {
        WorkCalc.Result res = repo.compute(record, employee);
        String otText;
        if (record.otIn == null && record.otOut == null) {
            otText = "Not started";
        } else if (res.overtime > 0) {
            otText = TimeCalc.formatDuration(res.overtime);
        } else {
            otText = "Pending";
        }
        b.txtOtValue.setText(otText);

        b.btnOtIn.setText(record.otIn == null ? getString(R.string.punch_ot_in)
                : getString(R.string.punch_ot_in) + " · " + TimeCalc.format12(record.otIn));
        b.btnOtOut.setText(record.otOut == null ? getString(R.string.punch_ot_out)
                : getString(R.string.punch_ot_out) + " · " + TimeCalc.format12(record.otOut));
    }

    private void renderStatus() {
        String status = record.in1 == null ? "not_started" : record.status;
        String label;
        int color;
        String key = status == null ? "" : status;
        switch (key) {
            case AttendanceStatus.PRESENT:
                label = AttendanceStatus.label(key);
                color = ContextCompat.getColor(this, R.color.success);
                break;
            case AttendanceStatus.LATE:
            case AttendanceStatus.HALF_DAY:
            case AttendanceStatus.INCOMPLETE:
                label = AttendanceStatus.label(key);
                color = ContextCompat.getColor(this, R.color.warn);
                break;
            case AttendanceStatus.OPEN:
                label = AttendanceStatus.label(key);
                color = ContextCompat.getColor(this, R.color.info);
                break;
            case AttendanceStatus.LEAVE:
            case AttendanceStatus.HOLIDAY:
                label = AttendanceStatus.label(key);
                color = ContextCompat.getColor(this, R.color.primary_light);
                break;
            default:
                label = "Not started";
                color = ContextCompat.getColor(this, R.color.text_mute);
                break;
        }
        b.pillStatus.setText(label.toUpperCase(java.util.Locale.US));
        b.pillStatus.setTextColor(color);
        b.pillStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Ui.withAlpha(color, 30)));
    }

    private void updateLive() {
        if (employee == null || record == null) return;
        int now = TimeCalc.nowMinutes();
        WorkCalc.Result res = repo.compute(record, employee);

        int worked = res.liveWorked(now);
        int breakMinutes = res.liveBreak(now);

        float progress = res.required > 0 ? Math.min(1f, worked / (float) res.required) : 0f;
        b.ring.setProgress(progress);
        b.ring.setCenterText(TimeCalc.formatHhMm(worked));
        b.ring.setSubText(res.working ? "WORKING" : res.onBreak ? "ON BREAK" : "WORKED");

        b.statWorked.value.setText(TimeCalc.formatDuration(worked));
        b.statBreak.value.setText(TimeCalc.formatDuration(breakMinutes));
        b.statOt.value.setText(res.overtime > 0 ? TimeCalc.formatDuration(res.overtime) : "—");
        b.statLate.value.setText(res.late > 0 ? res.late + "m" : "—");

        b.txtRequired.setText("Required " + TimeCalc.formatDuration(res.required)
                + " · " + employee.shiftLabel());
    }

    // ---------------------------------------------------------------- actions

    private void punchNext() {
        if (employee == null) return;
        int slot = record.nextSlot();
        if (slot >= PunchSet.COUNT) {
            SweetDialog.info(this, "Day complete",
                    "All six punches are recorded. Use Edit today if something needs a fix.");
            return;
        }
        int now = TimeCalc.nowMinutes();
        AttendanceRecord updated = repo.punchToday(employee.id, slot, now);
        if (updated == null) {
            SweetDialog.error(this, getString(R.string.dlg_error), "Could not save the punch.");
            return;
        }
        record = updated;
        renderTimeline();
        renderAction();
        renderStatus();
        renderOvertime();
        updateLive();
        Banner.success(this, PunchSet.label(slot) + " · " + TimeCalc.format12(now));
    }

    private void confirmUndo() {
        if (record == null || record.isEmpty()) return;
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_WARNING)
                .title(getString(R.string.dlg_undo))
                .message(getString(R.string.dlg_undo_msg))
                .confirmText(getString(R.string.dlg_delete))
                .cancelText(getString(R.string.dlg_cancel))
                .onConfirm(dialog -> {
                    dialog.dismiss();
                    repo.undoLastPunch(employee.id);
                    Banner.show(this, "Last punch cleared");
                })
                .build()
                .show();
    }

    private void editToday() {
        if (employee == null) return;
        String[] options = {
                "Check In",
                "Break Out",
                "Break In",
                "Break Out 2",
                "Break In 2",
                "Check Out",
                "OT In",
                "OT Out"
        };
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title(getString(R.string.dash_edit_today))
                .message("Pick the punch you want to change.")
                .items(options, (dialog, index) -> editSlot(index))
                .cancelText(getString(R.string.dlg_cancel))
                .build()
                .show();
    }

    private void editSlot(int slot) {
        if (employee == null || record == null) return;

        final Integer current;
        final String title;
        if (slot == SLOT_OT_IN) {
            current = record.otIn;
            title = getString(R.string.punch_ot_in);
        } else if (slot == SLOT_OT_OUT) {
            current = record.otOut;
            title = getString(R.string.punch_ot_out);
        } else {
            current = record.get(slot);
            title = PunchSet.label(slot);
        }

        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title(title)
                .message("Type a time like 9:42 AM or 21:42. Leave it empty to clear this punch.")
                .input("e.g. 9:42 AM", current == null ? "" : TimeCalc.format12(current))
                .confirmText(getString(R.string.dlg_save))
                .cancelText(getString(R.string.dlg_cancel))
                .onConfirm(dialog -> {
                    String raw = dialog.inputText();
                    if (raw.isEmpty()) {
                        dialog.dismiss();
                        applyPunch(slot, null);
                        return;
                    }
                    Integer minutes = TimeCalc.parseTime(raw);
                    if (minutes == null) {
                        dialog.dismiss();
                        SweetDialog.error(this, "Invalid time",
                                "Use a format like 9:42 AM or 21:42.");
                        return;
                    }
                    dialog.dismiss();
                    applyPunch(slot, minutes);
                })
                .build()
                .show();
    }

    private void applyPunch(int slot, Integer minutes) {
        if (slot == SLOT_OT_IN) {
            record = repo.setOvertime(employee.id, record.date, minutes, record.otOut);
        } else if (slot == SLOT_OT_OUT) {
            record = repo.setOvertime(employee.id, record.date, record.otIn, minutes);
        } else {
            record.set(slot, minutes);
            record = repo.saveRecord(record, employee);
        }
        refreshAll();
    }

    private void handleOvertime(int slot) {
        if (employee == null || record == null) return;
        boolean alreadySet = slot == SLOT_OT_IN ? record.otIn != null : record.otOut != null;
        if (alreadySet) {
            editSlot(slot);
            return;
        }
        int now = TimeCalc.nowMinutes();
        if (slot == SLOT_OT_IN) {
            record = repo.setOvertime(employee.id, record.date, now, record.otOut);
            Banner.success(this, "OT In · " + TimeCalc.format12(now));
        } else {
            record = repo.setOvertime(employee.id, record.date, record.otIn, now);
            Banner.success(this, "OT Out · " + TimeCalc.format12(now));
        }
        refreshAll();
    }

    private void showEmployeePicker() {
        final List<Employee> people = repo.employees(false);
        if (people.isEmpty()) {
            startActivity(new Intent(this, EmployeesActivity.class));
            return;
        }
        String[] labels = new String[people.size() + 1];
        for (int i = 0; i < people.size(); i++) {
            Employee e = people.get(i);
            labels[i] = e.name + (e.id == employee.id ? "  ·  current" : "");
        }
        labels[people.size()] = "Manage people…";

        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title("Switch person")
                .items(labels, (dialog, index) -> {
                    if (index < people.size()) {
                        repo.selectEmployee(people.get(index).id);
                        refreshAll();
                    } else {
                        startActivity(new Intent(this, EmployeesActivity.class));
                    }
                })
                .cancelText(getString(R.string.dlg_cancel))
                .build()
                .show();
    }
}
