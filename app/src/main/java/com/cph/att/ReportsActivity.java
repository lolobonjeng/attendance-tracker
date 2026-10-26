package com.cph.att;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cph.att.backup.BackupManager;
import com.cph.att.core.AppConfig;
import com.cph.att.core.AttendanceRecord;
import com.cph.att.core.Employee;
import com.cph.att.core.Fmt;
import com.cph.att.core.TimeCalc;
import com.cph.att.core.WorkCalc;
import com.cph.att.data.Repo;
import com.cph.att.databinding.ActivityReportsBinding;
import com.cph.att.databinding.ItemReportBinding;
import com.cph.att.ui.BottomNav;
import com.cph.att.ui.Nav;
import com.cph.att.ui.RangePickerDialog;
import com.cph.att.ui.SweetDialog;
import com.cph.att.ui.Ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportsActivity extends AppCompatActivity {

    private static final int PERIOD_WEEK = 0;
    private static final int PERIOD_MONTH = 1;
    private static final int PERIOD_LAST = 2;
    private static final int PERIOD_CUSTOM = 3;

    private static final String STATE_PERIOD = "period";
    private static final String STATE_FROM = "rangeFrom";
    private static final String STATE_TO = "rangeTo";

    private ActivityReportsBinding b;
    private Repo repo;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private int period = PERIOD_MONTH;
    private String rangeFrom;
    private String rangeTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        b = ActivityReportsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        repo = Repo.get(this);
        repo.ensureSeed();
        repo.setListener(this::load);

        if (savedInstanceState != null) {
            period = savedInstanceState.getInt(STATE_PERIOD, PERIOD_MONTH);
            rangeFrom = savedInstanceState.getString(STATE_FROM);
            rangeTo = savedInstanceState.getString(STATE_TO);
        }

        b.nav.setCurrent(Nav.TAB_REPORTS);
        Ui.applyTopInset(b.content);
        Ui.growForBottomInset(b.nav);

        b.segWeek.setOnClickListener(v -> {
            period = PERIOD_WEEK;
            load();
        });
        b.segMonth.setOnClickListener(v -> {
            period = PERIOD_MONTH;
            load();
        });
        b.segLast.setOnClickListener(v -> {
            period = PERIOD_LAST;
            load();
        });
        b.segCustom.setOnClickListener(v -> openRangePicker());

        b.btnExport.setOnClickListener(v -> chooseExport());
        b.btnExportCsv.setOnClickListener(v -> chooseExport());

        styleStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        b.nav.setCurrent(Nav.TAB_REPORTS);
        repo.setListener(this::load);
        load();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_PERIOD, period);
        out.putString(STATE_FROM, rangeFrom);
        out.putString(STATE_TO, rangeTo);
    }

    private void styleStats() {
        b.statHours.label.setText(R.string.reports_total_hours);
        b.statHours.icon.setImageResource(R.drawable.ic_clock);
        b.statHours.icon.setColorFilter(ContextCompat.getColor(this, R.color.accent));

        b.statOt.label.setText(R.string.reports_ot_hours);
        b.statOt.icon.setImageResource(R.drawable.ic_timer);
        b.statOt.icon.setColorFilter(ContextCompat.getColor(this, R.color.punch_ot));

        b.statLate.label.setText(R.string.reports_late_count);
        b.statLate.icon.setImageResource(R.drawable.ic_warning);
        b.statLate.icon.setColorFilter(ContextCompat.getColor(this, R.color.warn));

        b.statPay.label.setText(R.string.reports_est_pay);
        b.statPay.icon.setImageResource(R.drawable.ic_chart);
        b.statPay.icon.setColorFilter(ContextCompat.getColor(this, R.color.success));
    }

    private void load() {
        String[] range = computeRange();
        String from = range[0];
        String to = range[1];

        List<AttendanceRecord> records = repo.records(from, to, 0);
        List<Employee> people = repo.employees(true);
        Map<Long, Employee> byId = new HashMap<>();
        for (Employee e : people) byId.put(e.id, e);

        AppConfig cfg = repo.config();
        int totalWorked = 0;
        int totalOt = 0;
        int lateDays = 0;
        double totalPay = 0;
        Set<String> dates = new HashSet<>();

        Map<Long, int[]> perPerson = new HashMap<>();
        Map<Long, Double> payPerPerson = new HashMap<>();

        for (AttendanceRecord r : records) {
            Employee e = byId.get(r.employeeId);
            WorkCalc.Result res = repo.compute(r, e);
            totalWorked += res.worked;
            totalOt += res.overtime;
            if (res.late > 0) lateDays++;
            dates.add(r.date);

            double pay = cfg.payFor(res.worked, res.overtime, e == null ? 0 : e.rate);
            totalPay += pay;

            int[] bucket = perPerson.get(r.employeeId);
            if (bucket == null) {
                bucket = new int[4]; // worked, ot, late, days
                perPerson.put(r.employeeId, bucket);
            }
            bucket[0] += res.worked;
            bucket[1] += res.overtime;
            if (res.late > 0) bucket[2]++;
            bucket[3]++;
            Double prev = payPerPerson.get(r.employeeId);
            payPerPerson.put(r.employeeId, (prev == null ? 0 : prev) + pay);
        }

        b.txtRange.setText(Fmt.pretty(from) + "  –  " + Fmt.pretty(to));
        b.statHours.value.setText(TimeCalc.formatDuration(totalWorked));
        b.statOt.value.setText(totalOt > 0 ? TimeCalc.formatDuration(totalOt) : "—");
        b.statLate.value.setText(lateDays > 0 ? String.valueOf(lateDays) : "—");
        b.statPay.value.setText(totalPay > 0 ? Fmt.money(totalPay, cfg.currency) : "—");
        b.txtDays.setText(dates.size() + (dates.size() == 1 ? " day" : " days"));

        b.breakdown.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (Employee e : people) {
            int[] bucket = perPerson.get(e.id);
            if (bucket == null) continue;

            ItemReportBinding row = ItemReportBinding.inflate(inflater, b.breakdown, false);
            row.txtInitials.setText(e.initials());
            row.txtName.setText(e.name);
            row.txtDetail.setText(bucket[3] + " days · "
                    + TimeCalc.formatDuration(bucket[1]) + " OT · "
                    + bucket[2] + " late");
            row.txtHours.setText(TimeCalc.formatDuration(bucket[0]));

            Double pay = payPerPerson.get(e.id);
            if (pay != null && pay > 0) {
                row.txtPay.setText(Fmt.money(pay, cfg.currency));
            } else {
                row.txtPay.setText("No rate set");
            }
            b.breakdown.addView(row.getRoot());
        }

        boolean empty = b.breakdown.getChildCount() == 0;
        b.txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

        renderSegments();
    }

    private void renderSegments() {
        TextView[] segs = {b.segWeek, b.segMonth, b.segLast, b.segCustom};
        int active = ContextCompat.getColor(this, R.color.primary);
        int idle = ContextCompat.getColor(this, R.color.surface_2);
        for (int i = 0; i < segs.length; i++) {
            boolean on = i == period;
            segs[i].setBackgroundResource(R.drawable.bg_round_12);
            segs[i].setBackgroundTintList(ColorStateList.valueOf(on ? active : idle));
            segs[i].setTextColor(on
                    ? ContextCompat.getColor(this, R.color.on_primary)
                    : ContextCompat.getColor(this, R.color.text_mute));
        }
    }

    private String[] computeRange() {
        String today = Fmt.todayKey();
        switch (period) {
            case PERIOD_WEEK: {
                int iso = Fmt.isoDay(today);
                String start = Fmt.addDays(today, -(iso - 1));
                return new String[]{start, Fmt.addDays(start, 6)};
            }
            case PERIOD_LAST: {
                String month = Fmt.addMonths(Fmt.monthKey(today), -1);
                return new String[]{Fmt.firstDayOfMonth(month), Fmt.lastDayOfMonth(month)};
            }
            case PERIOD_CUSTOM: {
                if (rangeFrom != null && rangeTo != null) {
                    return new String[]{rangeFrom, rangeTo};
                }
                String month = Fmt.monthKey(today);
                return new String[]{Fmt.firstDayOfMonth(month), Fmt.lastDayOfMonth(month)};
            }
            default: {
                String month = Fmt.monthKey(today);
                return new String[]{Fmt.firstDayOfMonth(month), Fmt.lastDayOfMonth(month)};
            }
        }
    }

    private void openRangePicker() {
        String from = rangeFrom;
        String to = rangeTo;
        if (from == null || to == null) {
            String[] current = computeRange();
            from = current[0];
            to = current[1];
        }
        RangePickerDialog.show(this, from, to, (start, end) -> {
            rangeFrom = start;
            rangeTo = end;
            period = PERIOD_CUSTOM;
            load();
        });
    }

    private void chooseExport() {
        SweetDialog.builder(this)
                .type(SweetDialog.TYPE_QUESTION)
                .title(getString(R.string.export_title))
                .message(getString(R.string.export_message))
                .items(new String[]{getString(R.string.export_excel),
                        getString(R.string.export_csv)}, (dialog, index) -> export(index == 0))
                .cancelText(getString(R.string.dlg_cancel))
                .build()
                .show();
    }

    private void export(final boolean excel) {
        final String[] range = computeRange();
        final SweetDialog loading = SweetDialog.loading(this, getString(R.string.dlg_loading),
                getString(excel ? R.string.export_building_excel : R.string.export_building_csv));

        worker.execute(() -> {
            BackupManager.Result result = excel
                    ? BackupManager.get(this).exportXlsx(range[0], range[1], 0)
                    : BackupManager.get(this).exportCsv(range[0], range[1], 0);
            runOnUiThread(() -> {
                loading.dismiss();
                if (result.ok) {
                    SweetDialog.success(this, getString(R.string.dlg_export_done), result.message);
                } else {
                    SweetDialog.error(this, getString(R.string.dlg_error), result.message);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
