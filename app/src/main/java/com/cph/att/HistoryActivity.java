package com.cph.att;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cph.att.core.AttendanceRecord;
import com.cph.att.core.AttendanceStatus;
import com.cph.att.core.Employee;
import com.cph.att.core.Fmt;
import com.cph.att.core.TimeCalc;
import com.cph.att.core.WorkCalc;
import com.cph.att.data.Repo;
import com.cph.att.databinding.ActivityHistoryBinding;
import com.cph.att.ui.BottomNav;
import com.cph.att.ui.Nav;
import com.cph.att.ui.SweetDialog;
import com.cph.att.ui.Ui;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding b;
    private Repo repo;
    private HistoryAdapter adapter;

    private String monthKey;
    private long filterId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        b = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        repo = Repo.get(this);
        repo.ensureSeed();
        repo.setListener(this::refresh);

        monthKey = Fmt.monthKey(Fmt.todayKey());
        b.nav.setCurrent(Nav.TAB_HISTORY);
        Ui.applyTopInset(b.header);
        Ui.growForBottomInset(b.nav);

        adapter = new HistoryAdapter(repo, this::showDetail);
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);

        b.btnPrevMonth.setOnClickListener(v -> {
            monthKey = Fmt.addMonths(monthKey, -1);
            refresh();
        });
        b.btnNextMonth.setOnClickListener(v -> {
            monthKey = Fmt.addMonths(monthKey, 1);
            refresh();
        });
        b.txtMonth.setOnClickListener(v -> showMonthPicker());
        b.btnSearch.setOnClickListener(v -> showMonthPicker());
        b.chipFilter.setOnClickListener(v -> showFilterPicker());
    }

    @Override
    protected void onResume() {
        super.onResume();
        b.nav.setCurrent(Nav.TAB_HISTORY);
        repo.setListener(this::refresh);
        refresh();
    }

    private void refresh() {
        String from = Fmt.firstDayOfMonth(monthKey);
        String to = Fmt.lastDayOfMonth(monthKey);
        List<AttendanceRecord> records = repo.records(from, to, filterId);
        List<Employee> people = repo.employees(true);

        adapter.setData(records, people);
        b.txtMonth.setText(Fmt.monthLabel(monthKey));

        boolean empty = records.isEmpty();
        b.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.list.setVisibility(empty ? View.GONE : View.VISIBLE);

        int totalWorked = 0;
        for (AttendanceRecord r : records) {
            Employee e = null;
            for (Employee candidate : people) {
                if (candidate.id == r.employeeId) {
                    e = candidate;
                    break;
                }
            }
            WorkCalc.Result res = repo.compute(r, e);
            totalWorked += res.worked;
        }

        String summary = records.size() + (records.size() == 1 ? " record" : " records")
                + " · " + TimeCalc.formatDuration(totalWorked) + " worked";
        b.txtSummary.setText(summary);

        if (filterId == 0) {
            b.txtFilter.setText(getString(R.string.history_filter_all));
        } else {
            Employee selected = repo.employee(filterId);
            b.txtFilter.setText(selected != null ? selected.name : getString(R.string.history_filter_all));
        }
    }

    private void showMonthPicker() {
        final String now = Fmt.monthKey(Fmt.todayKey());
        String[] labels = new String[13];
        for (int i = 0; i < 13; i++) {
            labels[i] = Fmt.monthLabel(Fmt.addMonths(now, i - 11));
        }
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title(getString(R.string.history_month))
                .items(labels, (dialog, index) -> {
                    monthKey = Fmt.addMonths(now, index - 11);
                    refresh();
                })
                .cancelText(getString(R.string.dlg_cancel))
                .build()
                .show();
    }

    private void showFilterPicker() {
        final List<Employee> people = repo.employees(true);
        String[] labels = new String[people.size() + 1];
        labels[0] = getString(R.string.history_filter_all);
        for (int i = 0; i < people.size(); i++) {
            labels[i + 1] = people.get(i).name;
        }
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title("Filter by person")
                .items(labels, (dialog, index) -> {
                    filterId = index == 0 ? 0 : people.get(index - 1).id;
                    refresh();
                })
                .cancelText(getString(R.string.dlg_cancel))
                .build()
                .show();
    }

    private void showDetail(AttendanceRecord r) {
        Employee e = repo.employee(r.employeeId);
        WorkCalc.Result res = repo.compute(r, e);

        StringBuilder sb = new StringBuilder();
        appendLine(sb, getString(R.string.punch_check_in), r.in1);
        appendLine(sb, getString(R.string.punch_break_out_1), r.out1);
        appendLine(sb, getString(R.string.punch_break_in_1), r.in2);
        appendLine(sb, getString(R.string.punch_break_out_2), r.out2);
        appendLine(sb, getString(R.string.punch_break_in_2), r.in3);
        appendLine(sb, getString(R.string.punch_check_out), r.out3);
        if (r.otIn != null || r.otOut != null) {
            appendLine(sb, getString(R.string.punch_ot_in), r.otIn);
            appendLine(sb, getString(R.string.punch_ot_out), r.otOut);
        }
        sb.append("\n");
        sb.append(TimeCalc.formatDuration(res.worked)).append(" worked · ")
                .append(TimeCalc.formatDuration(res.breakTotal)).append(" break");
        if (res.overtime > 0) {
            sb.append(" · ").append(TimeCalc.formatDuration(res.overtime)).append(" OT");
        }
        if (r.note != null && !r.note.isEmpty()) {
            sb.append("\n").append(r.note);
        }

        final String[] actions = {"Edit record", "Delete record", "Mark as leave", "Mark as holiday"};
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_INFO)
                .title(Fmt.prettyLong(r.date))
                .message(sb.toString())
                .items(actions, (dialog, index) -> {
                    if (index == 0) {
                        openEditor(r);
                    } else if (index == 1) {
                        confirmDelete(r);
                    } else if (index == 2) {
                        markStatus(r, AttendanceStatus.LEAVE);
                    } else {
                        markStatus(r, AttendanceStatus.HOLIDAY);
                    }
                })
                .cancelText(getString(R.string.dlg_cancel))
                .build()
                .show();
    }

    private void appendLine(StringBuilder sb, String label, Integer minutes) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(label).append("  ·  ")
                .append(minutes == null ? "—" : TimeCalc.format12(minutes));
    }

    private void openEditor(AttendanceRecord r) {
        Intent intent = new Intent(this, RecordEditActivity.class);
        intent.putExtra(RecordEditActivity.EXTRA_EMPLOYEE_ID, r.employeeId);
        intent.putExtra(RecordEditActivity.EXTRA_DATE, r.date);
        startActivity(intent);
    }

    private void confirmDelete(AttendanceRecord r) {
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_WARNING)
                .title(getString(R.string.dlg_delete_record))
                .message(getString(R.string.dlg_delete_record_msg))
                .confirmText(getString(R.string.dlg_delete))
                .cancelText(getString(R.string.dlg_cancel))
                .onConfirm(dialog -> {
                    dialog.dismiss();
                    repo.deleteRecord(r.id);
                    refresh();
                })
                .build()
                .show();
    }

    private void markStatus(AttendanceRecord r, String status) {
        if (r.in1 != null) {
            SweetDialog.warning(this, "Punches exist",
                    "Clear the punches in the editor first, then mark this day as "
                            + AttendanceStatus.label(status).toLowerCase(java.util.Locale.US) + ".");
            return;
        }
        r.status = status;
        repo.saveRecord(r, repo.employee(r.employeeId));
        refresh();
    }
}
