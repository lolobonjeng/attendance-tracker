package com.cph.att;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cph.att.core.Employee;
import com.cph.att.core.Fmt;
import com.cph.att.core.TimeCalc;
import com.cph.att.databinding.ItemEmployeeBinding;
import com.cph.att.ui.Ui;

import java.util.ArrayList;
import java.util.List;

public class EmployeeAdapter extends RecyclerView.Adapter<EmployeeAdapter.VH> {

    public interface OnEmployeeClick {
        void onEmployee(Employee employee);
    }

    private final List<Employee> data = new ArrayList<>();
    private final OnEmployeeClick listener;
    private final String currency;

    public EmployeeAdapter(OnEmployeeClick listener, String currency) {
        this.listener = listener;
        this.currency = currency == null ? "" : currency;
    }

    public void setData(List<Employee> employees) {
        data.clear();
        if (employees != null) data.addAll(employees);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemEmployeeBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Employee e = data.get(position);
        ItemEmployeeBinding b = holder.binding;

        b.txtInitials.setText(e.initials());
        b.txtName.setText(e.name);

        String meta = e.position == null || e.position.isEmpty() ? "Team member" : e.position;
        if (e.code != null && !e.code.isEmpty()) {
            meta = meta + " · #" + e.code;
        }
        b.txtMeta.setText(meta);

        b.txtShift.setText(TimeCalc.format12(e.shiftStart) + " – " + TimeCalc.format12(e.shiftEnd)
                + " · " + Fmt.workdayLabel(e.workdays));

        b.txtBadge.setText(e.active ? R.string.team_active : R.string.team_archived);
        int color = b.getRoot().getContext().getResources()
                .getColor(e.active ? R.color.success : R.color.text_mute);
        b.txtBadge.setTextColor(color);
        b.txtBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Ui.withAlpha(color, 34)));
        b.avatarWrap.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Ui.withAlpha(e.active
                        ? b.getRoot().getContext().getResources().getColor(R.color.primary)
                        : b.getRoot().getContext().getResources().getColor(R.color.text_mute), 48)));

        b.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onEmployee(e);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemEmployeeBinding binding;

        VH(ItemEmployeeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
