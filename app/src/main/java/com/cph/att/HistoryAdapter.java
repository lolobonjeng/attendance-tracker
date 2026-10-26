package com.cph.att;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cph.att.core.AttendanceRecord;
import com.cph.att.core.AttendanceStatus;
import com.cph.att.core.Employee;
import com.cph.att.core.Fmt;
import com.cph.att.core.TimeCalc;
import com.cph.att.core.WorkCalc;
import com.cph.att.data.Repo;
import com.cph.att.databinding.ItemHistoryBinding;
import com.cph.att.ui.StatusStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    public interface OnRecordClick {
        void onRecord(AttendanceRecord record);
    }

    private final Repo repo;
    private final OnRecordClick listener;
    private final List<AttendanceRecord> data = new ArrayList<>();
    private final Map<Long, Employee> people = new HashMap<>();

    public HistoryAdapter(Repo repo, OnRecordClick listener) {
        this.repo = repo;
        this.listener = listener;
    }

    public void setData(List<AttendanceRecord> records, List<Employee> employees) {
        data.clear();
        if (records != null) data.addAll(records);
        people.clear();
        if (employees != null) {
            for (Employee e : employees) people.put(e.id, e);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHistoryBinding binding = ItemHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AttendanceRecord r = data.get(position);
        ItemHistoryBinding b = holder.binding;
        Employee e = people.get(r.employeeId);

        b.txtDay.setText(Fmt.dayNumber(r.date));
        b.txtDow.setText(Fmt.weekdayShort(r.date));
        b.txtName.setText(e != null ? e.name : "Unknown");

        String in = r.in1 == null ? "—" : TimeCalc.format12(r.in1);
        String out = r.out3 == null ? "—" : TimeCalc.format12(r.out3);
        b.txtTimes.setText(in + "  →  " + out);

        WorkCalc.Result res = repo.compute(r, e);
        b.txtWorked.setText(TimeCalc.formatDuration(res.worked));
        b.txtBreak.setText(TimeCalc.formatDuration(res.breakTotal) + " break");
        if (res.overtime > 0) {
            b.txtOt.setVisibility(android.view.View.VISIBLE);
            b.txtOt.setText("+" + TimeCalc.formatDuration(res.overtime) + " OT");
        } else {
            b.txtOt.setVisibility(android.view.View.GONE);
        }

        b.txtBadge.setText(AttendanceStatus.label(r.status));
        b.txtBadge.setTextColor(StatusStyle.color(b.getRoot().getContext(), r.status));
        b.txtBadge.setBackgroundTintList(StatusStyle.background(b.getRoot().getContext(), r.status));

        int accent = StatusStyle.color(b.getRoot().getContext(), r.status);
        b.dayWrap.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                com.cph.att.ui.Ui.withAlpha(accent, 34)));

        b.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onRecord(r);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemHistoryBinding binding;

        VH(ItemHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
