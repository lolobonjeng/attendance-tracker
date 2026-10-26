package com.cph.att;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.cph.att.core.AttendanceRecord;
import com.cph.att.core.AttendanceStatus;
import com.cph.att.core.Employee;
import com.cph.att.core.Fmt;
import com.cph.att.core.TimeCalc;
import com.cph.att.core.WorkCalc;
import com.cph.att.data.Repo;
import com.cph.att.databinding.ActivityRecordEditBinding;
import com.cph.att.databinding.ItemTimeFieldBinding;
import com.cph.att.ui.SweetDialog;
import com.cph.att.ui.Ui;

public class RecordEditActivity extends AppCompatActivity {

    public static final String EXTRA_EMPLOYEE_ID = "employee_id";
    public static final String EXTRA_DATE = "date";

    private ActivityRecordEditBinding b;
    private Repo repo;
    private Employee employee;
    private AttendanceRecord record;
    private String date;
    private String statusOverride;

    private final String[] statuses = {
            AttendanceStatus.PRESENT,
            AttendanceStatus.LATE,
            AttendanceStatus.HALF_DAY,
            AttendanceStatus.ABSENT,
            AttendanceStatus.LEAVE,
            AttendanceStatus.HOLIDAY
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        b = ActivityRecordEditBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.applyTopInset(b.header);

        repo = Repo.get(this);
        long employeeId = getIntent().getLongExtra(EXTRA_EMPLOYEE_ID, 0L);
        date = getIntent().getStringExtra(EXTRA_DATE);
        if (date == null || date.isEmpty()) date = Fmt.todayKey();

        employee = employeeId > 0 ? repo.employee(employeeId) : repo.currentEmployee();
        if (employee == null) {
            SweetDialog.error(this, getString(R.string.dlg_error), "No person selected.");
            finish();
            return;
        }

        record = repo.record(employee.id, date);
        if (record == null) {
            record = new AttendanceRecord();
            record.employeeId = employee.id;
            record.date = date;
            record.status = AttendanceStatus.ABSENT;
        }
        statusOverride = record.status;

        b.txtDate.setText(Fmt.prettyLong(date));
        setHints();
        fillFields();
        updateStatusPill();
        updatePreview();

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updatePreview();
            }
        };
        allFields()[0].edit.addTextChangedListener(watcher);
        allFields()[1].edit.addTextChangedListener(watcher);
        allFields()[2].edit.addTextChangedListener(watcher);
        allFields()[3].edit.addTextChangedListener(watcher);
        allFields()[4].edit.addTextChangedListener(watcher);
        allFields()[5].edit.addTextChangedListener(watcher);
        allFields()[6].edit.addTextChangedListener(watcher);
        allFields()[7].edit.addTextChangedListener(watcher);

        b.btnBack.setOnClickListener(v -> finish());
        b.btnSave.setOnClickListener(v -> save());
        b.btnDelete.setOnClickListener(v -> confirmDelete());
        b.rowStatus.setOnClickListener(v -> pickStatus());
    }

    private ItemTimeFieldBinding[] allFields() {
        return new ItemTimeFieldBinding[]{
                b.fieldIn1, b.fieldOut1, b.fieldIn2, b.fieldOut2,
                b.fieldIn3, b.fieldOut3, b.fieldOtIn, b.fieldOtOut
        };
    }

    private void setHints() {
        b.fieldIn1.getRoot().setHint(getString(R.string.punch_check_in));
        b.fieldOut1.getRoot().setHint(getString(R.string.punch_break_out_1));
        b.fieldIn2.getRoot().setHint(getString(R.string.punch_break_in_1));
        b.fieldOut2.getRoot().setHint(getString(R.string.punch_break_out_2));
        b.fieldIn3.getRoot().setHint(getString(R.string.punch_break_in_2));
        b.fieldOut3.getRoot().setHint(getString(R.string.punch_check_out));
        b.fieldOtIn.getRoot().setHint(getString(R.string.punch_ot_in));
        b.fieldOtOut.getRoot().setHint(getString(R.string.punch_ot_out));
    }

    private void fillFields() {
        set(b.fieldIn1, record.in1);
        set(b.fieldOut1, record.out1);
        set(b.fieldIn2, record.in2);
        set(b.fieldOut2, record.out2);
        set(b.fieldIn3, record.in3);
        set(b.fieldOut3, record.out3);
        set(b.fieldOtIn, record.otIn);
        set(b.fieldOtOut, record.otOut);
        b.editNote.setText(record.note == null ? "" : record.note);
    }

    private void set(ItemTimeFieldBinding field, Integer minutes) {
        field.edit.setText(minutes == null ? "" : TimeCalc.format12(minutes));
    }

    private AttendanceRecord collect() {
        AttendanceRecord r = new AttendanceRecord();
        r.id = record.id;
        r.employeeId = employee.id;
        r.date = date;
        r.createdAt = record.createdAt;
        r.note = b.editNote.getText() == null ? "" : b.editNote.getText().toString().trim();
        r.status = statusOverride == null ? AttendanceStatus.ABSENT : statusOverride;
        r.in1 = read(b.fieldIn1);
        r.out1 = read(b.fieldOut1);
        r.in2 = read(b.fieldIn2);
        r.out2 = read(b.fieldOut2);
        r.in3 = read(b.fieldIn3);
        r.out3 = read(b.fieldOut3);
        r.otIn = read(b.fieldOtIn);
        r.otOut = read(b.fieldOtOut);
        return r;
    }

    private Integer read(ItemTimeFieldBinding field) {
        String raw = field.edit.getText() == null ? "" : field.edit.getText().toString().trim();
        if (raw.isEmpty()) return null;
        return TimeCalc.parseTime(raw);
    }

    private void updatePreview() {
        AttendanceRecord preview = collect();
        WorkCalc.Result res = repo.compute(preview, employee);
        StringBuilder sb = new StringBuilder();
        sb.append(TimeCalc.formatDuration(res.worked)).append(" worked · ")
                .append(TimeCalc.formatDuration(res.breakTotal)).append(" break");
        if (res.overtime > 0) {
            sb.append(" · ").append(TimeCalc.formatDuration(res.overtime)).append(" OT");
        }
        if (res.late > 0) {
            sb.append(" · ").append(res.late).append("m late");
        }
        b.txtPreview.setText(sb.toString());
    }

    private void updateStatusPill() {
        String label = AttendanceStatus.label(statusOverride);
        int color = com.cph.att.ui.StatusStyle.color(this, statusOverride);
        b.txtStatus.setText(label);
        b.txtStatus.setTextColor(color);
        b.txtStatus.setBackgroundTintList(
                com.cph.att.ui.StatusStyle.background(this, statusOverride));
    }

    private void pickStatus() {
        String[] labels = new String[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            labels[i] = AttendanceStatus.label(statuses[i]);
        }
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title("Status")
                .items(labels, (dialog, index) -> {
                    statusOverride = statuses[index];
                    updateStatusPill();
                })
                .cancelText(getString(R.string.dlg_cancel))
                .build()
                .show();
    }

    private void save() {
        ItemTimeFieldBinding[] fields = allFields();
        String[] labels = {"Check In", "Break Out", "Break In", "Break Out 2",
                "Break In 2", "Check Out", "OT In", "OT Out"};
        for (int i = 0; i < fields.length; i++) {
            String raw = fields[i].edit.getText() == null ? ""
                    : fields[i].edit.getText().toString().trim();
            if (raw.isEmpty()) continue;
            if (TimeCalc.parseTime(raw) == null) {
                SweetDialog.error(this, "Invalid time",
                        labels[i] + ": \"" + raw + "\" is not a valid time.\n"
                                + "Use 9:42 AM, 9:42 PM or 21:42.");
                return;
            }
        }

        AttendanceRecord edited = collect();
        boolean autoStatus = statusOverride == null
                || AttendanceStatus.ABSENT.equals(statusOverride);
        if (autoStatus) {
            repo.applyStatus(edited, employee);
        }
        repo.saveRecord(edited, employee);
        finish();
    }

    private void confirmDelete() {
        if (record.id <= 0) {
            finish();
            return;
        }
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_WARNING)
                .title(getString(R.string.dlg_delete_record))
                .message(getString(R.string.dlg_delete_record_msg))
                .confirmText(getString(R.string.dlg_delete))
                .cancelText(getString(R.string.dlg_cancel))
                .onConfirm(dialog -> {
                    dialog.dismiss();
                    repo.deleteRecord(record.id);
                    finish();
                })
                .build()
                .show();
    }
}
