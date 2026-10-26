package com.cph.att;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cph.att.core.Employee;
import com.cph.att.core.TimeCalc;
import com.cph.att.data.Repo;
import com.cph.att.databinding.ActivityEmployeeEditBinding;
import com.cph.att.ui.SweetDialog;
import com.cph.att.ui.Ui;

public class EmployeeEditActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "employee_id";

    private ActivityEmployeeEditBinding b;
    private Repo repo;
    private Employee employee;
    private boolean isNew;

    private final boolean[] days = new boolean[7];
    private TextView[] chips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        b = ActivityEmployeeEditBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.applyTopInset(b.header);

        repo = Repo.get(this);
        long id = getIntent().getLongExtra(EXTRA_ID, 0L);
        employee = id > 0 ? repo.employee(id) : null;
        isNew = employee == null;
        if (employee == null) employee = new Employee();

        chips = new TextView[]{b.day1, b.day2, b.day3, b.day4, b.day5, b.day6, b.day7};

        b.txtTitle.setText(isNew ? getString(R.string.team_add) : getString(R.string.team_edit));
        b.btnDelete.setVisibility(isNew ? View.GONE : View.VISIBLE);

        b.fieldShiftStart.getRoot().setHint(getString(R.string.field_shift_start));
        b.fieldShiftEnd.getRoot().setHint(getString(R.string.field_shift_end));

        fill();
        renderDays();
        renderActive();

        for (int i = 0; i < chips.length; i++) {
            final int index = i;
            chips[i].setOnClickListener(v -> {
                days[index] = !days[index];
                renderDays();
            });
        }

        b.btnBack.setOnClickListener(v -> finish());
        b.btnSave.setOnClickListener(v -> save());
        b.btnDelete.setOnClickListener(v -> confirmDelete());
        b.rowActive.setOnClickListener(v -> {
            employee.active = !employee.active;
            renderActive();
        });
    }

    private void fill() {
        b.editName.setText(employee.name);
        b.editPosition.setText(employee.position);
        b.editCode.setText(employee.code);
        b.editRate.setText(employee.rate > 0
                ? String.format(java.util.Locale.US, "%.2f", employee.rate) : "");
        b.fieldShiftStart.edit.setText(TimeCalc.format12(employee.shiftStart));
        b.fieldShiftEnd.edit.setText(TimeCalc.format12(employee.shiftEnd));

        for (int i = 0; i < 7; i++) days[i] = false;
        String raw = employee.workdays == null ? "" : employee.workdays;
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            try {
                int d = Integer.parseInt(p);
                if (d >= 1 && d <= 7) days[d - 1] = true;
            } catch (NumberFormatException ignored) {
            }
        }
        if (raw.isEmpty()) {
            days[0] = true;
            days[1] = true;
            days[2] = true;
            days[3] = true;
            days[4] = true;
        }
    }

    private void renderDays() {
        int on = ContextCompat.getColor(this, R.color.primary);
        int off = ContextCompat.getColor(this, R.color.surface_2);
        for (int i = 0; i < chips.length; i++) {
            chips[i].setBackgroundResource(R.drawable.bg_round_12);
            chips[i].setBackgroundTintList(ColorStateList.valueOf(days[i] ? on : off));
            chips[i].setTextColor(days[i] ? ContextCompat.getColor(this, R.color.on_primary)
                    : ContextCompat.getColor(this, R.color.text_mute));
        }
    }

    private void renderActive() {
        int color = ContextCompat.getColor(this,
                employee.active ? R.color.success : R.color.text_mute);
        b.txtActive.setText(employee.active ? R.string.team_active : R.string.team_archived);
        b.txtActive.setTextColor(color);
        b.txtActive.setBackgroundTintList(ColorStateList.valueOf(Ui.withAlpha(color, 34)));
    }

    private void save() {
        String name = text(b.editName);
        if (name.isEmpty()) {
            SweetDialog.warning(this, "Name required", "Give this person a name first.");
            return;
        }

        Integer start = TimeCalc.parseTime(text(b.fieldShiftStart.edit));
        Integer end = TimeCalc.parseTime(text(b.fieldShiftEnd.edit));
        if (start == null || end == null) {
            SweetDialog.error(this, "Invalid shift",
                    "Use times like 9:00 AM and 6:00 PM.");
            return;
        }

        StringBuilder workdays = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (!days[i]) continue;
            if (workdays.length() > 0) workdays.append(',');
            workdays.append(i + 1);
        }
        if (workdays.length() == 0) {
            SweetDialog.warning(this, "Pick work days",
                    "Select at least one working day.");
            return;
        }

        double rate = 0;
        String rateRaw = text(b.editRate);
        if (!rateRaw.isEmpty()) {
            try {
                rate = Double.parseDouble(rateRaw.replace(",", "."));
            } catch (NumberFormatException e) {
                SweetDialog.error(this, "Invalid rate", "Type a number like 120.50.");
                return;
            }
        }

        employee.name = name;
        employee.position = text(b.editPosition);
        employee.code = text(b.editCode);
        employee.rate = rate;
        employee.shiftStart = start;
        employee.shiftEnd = end;
        employee.workdays = workdays.toString();

        repo.saveEmployee(employee);
        if (isNew) {
            repo.selectEmployee(employee.id);
        }
        finish();
    }

    private void confirmDelete() {
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_WARNING)
                .title(getString(R.string.dlg_delete_person))
                .message(getString(R.string.dlg_delete_person_msg))
                .confirmText(getString(R.string.dlg_delete))
                .cancelText(getString(R.string.dlg_cancel))
                .onConfirm(dialog -> {
                    dialog.dismiss();
                    repo.deleteEmployee(employee.id);
                    finish();
                })
                .build()
                .show();
    }

    private String text(android.widget.EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }
}
