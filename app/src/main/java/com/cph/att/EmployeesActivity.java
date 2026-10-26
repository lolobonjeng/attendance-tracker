package com.cph.att;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cph.att.core.Employee;
import com.cph.att.data.Repo;
import com.cph.att.databinding.ActivityEmployeesBinding;
import com.cph.att.ui.BottomNav;
import com.cph.att.ui.Nav;
import com.cph.att.ui.Ui;

import java.util.List;

public class EmployeesActivity extends AppCompatActivity {

    public static final String EXTRA_ADD = "add_now";

    private ActivityEmployeesBinding b;
    private Repo repo;
    private EmployeeAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        b = ActivityEmployeesBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        repo = Repo.get(this);
        repo.ensureSeed();
        repo.setListener(this::refresh);

        b.nav.setCurrent(Nav.TAB_PEOPLE);
        Ui.applyTopInset(b.header);
        Ui.growForBottomInset(b.nav);

        adapter = new EmployeeAdapter(this::openEditor, repo.config().currency);
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);

        b.btnAdd.setOnClickListener(v -> openEditor(null));

        if (getIntent().getBooleanExtra(EXTRA_ADD, false)) {
            b.getRoot().post(() -> openEditor(null));
            getIntent().removeExtra(EXTRA_ADD);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        b.nav.setCurrent(Nav.TAB_PEOPLE);
        repo.setListener(this::refresh);
        refresh();
    }

    private void refresh() {
        List<Employee> people = repo.employees(true);
        adapter.setData(people);
        b.txtCount.setText(people.size() + (people.size() == 1 ? " person" : " people"));
        boolean empty = people.isEmpty();
        b.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openEditor(Employee employee) {
        Intent intent = new Intent(this, EmployeeEditActivity.class);
        if (employee != null) {
            intent.putExtra(EmployeeEditActivity.EXTRA_ID, employee.id);
        }
        startActivity(intent);
    }
}
