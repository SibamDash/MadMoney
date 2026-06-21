package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarFragment extends Fragment {

    private final Calendar calendar = Calendar.getInstance();
    private TextView tvMonthYear;
    private RecyclerView rvCalendar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvMonthYear = view.findViewById(R.id.tvMonthYear);
        rvCalendar = view.findViewById(R.id.rvCalendar);

        view.findViewById(R.id.ivPrevMonth).setOnClickListener(v -> {
            calendar.add(Calendar.MONTH, -1);
            loadCalendar();
        });

        view.findViewById(R.id.ivNextMonth).setOnClickListener(v -> {
            calendar.add(Calendar.MONTH, 1);
            loadCalendar();
        });

        view.findViewById(R.id.layoutMonthPicker).setOnClickListener(v -> showMonthYearPicker());
        loadCalendar();
    }

    private void showMonthYearPicker() {
        String[] months = new String[]{"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        NumberPicker mp = new NumberPicker(requireContext());
        mp.setMinValue(0);
        mp.setMaxValue(11);
        mp.setDisplayedValues(months);
        mp.setValue(calendar.get(Calendar.MONTH));
        mp.setWrapSelectorWheel(true);

        NumberPicker yp = new NumberPicker(requireContext());
        yp.setMinValue(2000);
        yp.setMaxValue(2100);
        yp.setValue(calendar.get(Calendar.YEAR));
        yp.setWrapSelectorWheel(false);

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(32, 16, 32, 16);
        container.addView(mp, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        container.addView(yp, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        new AlertDialog.Builder(requireContext())
            .setTitle("Select Month")
            .setView(container)
            .setPositiveButton("Go", (dialog, which) -> {
                calendar.set(Calendar.MONTH, mp.getValue());
                calendar.set(Calendar.YEAR, yp.getValue());
                loadCalendar();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isAdded()) {
            loadCalendar();
        }
    }

    public void reload() {
        if (isAdded()) {
            loadCalendar();
        }
    }

    private void loadCalendar() {
        SimpleDateFormat fmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(fmt.format(calendar.getTime()));

        Calendar monthStart = (Calendar) calendar.clone();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        monthStart.set(Calendar.HOUR_OF_DAY, 0);
        monthStart.set(Calendar.MINUTE, 0);
        monthStart.set(Calendar.SECOND, 0);
        monthStart.set(Calendar.MILLISECOND, 0);

        Calendar monthEnd = (Calendar) calendar.clone();
        monthEnd.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        monthEnd.set(Calendar.HOUR_OF_DAY, 23);
        monthEnd.set(Calendar.MINUTE, 59);
        monthEnd.set(Calendar.SECOND, 59);
        monthEnd.set(Calendar.MILLISECOND, 999);

        List<Transaction> transactions = DatabaseHelper.getInstance(requireContext())
            .getTransactions(monthStart.getTimeInMillis(), monthEnd.getTimeInMillis());

        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Map<String, DaySummary> dayMap = new HashMap<>();

        for (Transaction t : transactions) {
            String key = dayFmt.format(new Date(t.getDate()));
            DaySummary current = dayMap.get(key);
            if (current == null) {
                current = new DaySummary();
            }
            switch (t.getType().toLowerCase(Locale.US)) {
                case "income":
                    current.setIncome(current.getIncome() + t.getAmount());
                    break;
                case "expense":
                    current.setExpense(current.getExpense() + t.getAmount());
                    break;
                case "togive":
                case "toget":
                    current.setDebt(current.getDebt() + t.getAmount());
                    break;
            }
            dayMap.put(key, current);
        }

        int firstDow = monthStart.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = monthEnd.get(Calendar.DAY_OF_MONTH);
        
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < firstDow; i++) {
            cells.add(null);
        }
        for (int d = 1; d <= daysInMonth; d++) {
            cells.add(d);
        }

        Calendar today = Calendar.getInstance();
        int todayDay = -1;
        if (today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
            today.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)) {
            todayDay = today.get(Calendar.DAY_OF_MONTH);
        }

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;

        rvCalendar.setLayoutManager(new GridLayoutManager(requireContext(), 7));
        rvCalendar.setAdapter(new CalendarAdapter(cells, dayMap, year, month, todayDay, day -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToDailyForDate(year, month - 1, day);
            }
        }));
    }
}
