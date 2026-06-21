package com.example.myapplication;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrendsFragment extends Fragment {

    private enum Mode { CONSECUTIVE_MONTHS, SIX_MONTHS, CONSECUTIVE_YEARS, CUSTOM }

    private Mode mode = Mode.SIX_MONTHS;
    private Calendar customFrom = null;
    private Calendar customTo = null;
    private int consecutiveCount = 6; // months or years

    private final SimpleDateFormat displayFmt = new SimpleDateFormat("MMM yy", Locale.getDefault());
    private final SimpleDateFormat yearFmt = new SimpleDateFormat("yyyy", Locale.getDefault());
    private final SimpleDateFormat pickFmt = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trends, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Spinner spinner = view.findViewById(R.id.spinnerRange);
        List<String> options = Arrays.asList("3 Months", "6 Months", "12 Months", "3 Years", "5 Years", "Year-over-Year", "Custom");
        spinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, options));
        spinner.setSelection(1); // default: 6 months

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                LinearLayout customLayout = view.findViewById(R.id.layoutCustomRange);
                switch (pos) {
                    case 0:
                        mode = Mode.CONSECUTIVE_MONTHS;
                        consecutiveCount = 3;
                        customLayout.setVisibility(View.GONE);
                        load(view);
                        break;
                    case 1:
                        mode = Mode.CONSECUTIVE_MONTHS;
                        consecutiveCount = 6;
                        customLayout.setVisibility(View.GONE);
                        load(view);
                        break;
                    case 2:
                        mode = Mode.CONSECUTIVE_MONTHS;
                        consecutiveCount = 12;
                        customLayout.setVisibility(View.GONE);
                        load(view);
                        break;
                    case 3:
                        mode = Mode.CONSECUTIVE_YEARS;
                        consecutiveCount = 3;
                        customLayout.setVisibility(View.GONE);
                        load(view);
                        break;
                    case 4:
                        mode = Mode.CONSECUTIVE_YEARS;
                        consecutiveCount = 5;
                        customLayout.setVisibility(View.GONE);
                        load(view);
                        break;
                    case 5:
                        mode = Mode.SIX_MONTHS;
                        customLayout.setVisibility(View.GONE);
                        load(view);
                        break;
                    case 6:
                        mode = Mode.CUSTOM;
                        customLayout.setVisibility(View.VISIBLE);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Custom date pickers
        view.findViewById(R.id.tvCustomFrom).setOnClickListener(v -> pickDate(view, true));
        view.findViewById(R.id.tvCustomTo).setOnClickListener(v -> pickDate(view, false));
        view.findViewById(R.id.btnApplyCustom).setOnClickListener(v -> {
            if (customFrom != null && customTo != null) {
                load(view);
            } else {
                Toast.makeText(requireContext(), "Select both dates", Toast.LENGTH_SHORT).show();
            }
        });

        load(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        View view = getView();
        if (view != null) {
            load(view);
        }
    }

    public void reload() {
        if (isAdded()) {
            View view = getView();
            if (view != null) {
                load(view);
            }
        }
    }

    private void pickDate(View view, boolean isFrom) {
        if (!isVisible()) return;
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view2, y, m, d) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(y, m, d);
            if (isFrom) {
                customFrom = picked;
                ((TextView) view.findViewById(R.id.tvCustomFrom)).setText("From: " + pickFmt.format(picked.getTime()));
            } else {
                customTo = picked;
                ((TextView) view.findViewById(R.id.tvCustomTo)).setText("To: " + pickFmt.format(picked.getTime()));
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void load(View view) {
        DatabaseHelper db = DatabaseHelper.getInstance(requireContext());
        List<BarChartView.MonthData> months = new ArrayList<>();
        String title = "";

        Calendar cal = Calendar.getInstance();

        if (mode == Mode.CONSECUTIVE_MONTHS) {
            title = consecutiveCount + "-Month Trends";
            for (int offset = consecutiveCount - 1; offset >= 0; offset--) {
                Calendar c = (Calendar) cal.clone();
                c.add(Calendar.MONTH, -offset);
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
                long start = c.getTimeInMillis();
                
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
                c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999);
                long end = c.getTimeInMillis();

                List<Transaction> txns = db.getTransactions(start, end);
                float income = 0;
                float expense = 0;
                for (Transaction t : txns) {
                    if ("income".equalsIgnoreCase(t.getType())) income += t.getAmount();
                    else if ("expense".equalsIgnoreCase(t.getType())) expense += t.getAmount();
                }
                months.add(new BarChartView.MonthData(displayFmt.format(new Date(start)), income, expense));
            }
        } else if (mode == Mode.CONSECUTIVE_YEARS) {
            title = consecutiveCount + "-Year Trends";
            for (int offset = consecutiveCount - 1; offset >= 0; offset--) {
                Calendar c = (Calendar) cal.clone();
                c.add(Calendar.YEAR, -offset);
                c.set(Calendar.DAY_OF_YEAR, 1);
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
                long start = c.getTimeInMillis();

                c.set(Calendar.MONTH, 11);
                c.set(Calendar.DAY_OF_MONTH, 31);
                c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999);
                long end = c.getTimeInMillis();

                List<Transaction> txns = db.getTransactions(start, end);
                float income = 0;
                float expense = 0;
                for (Transaction t : txns) {
                    if ("income".equalsIgnoreCase(t.getType())) income += t.getAmount();
                    else if ("expense".equalsIgnoreCase(t.getType())) expense += t.getAmount();
                }
                months.add(new BarChartView.MonthData(yearFmt.format(new Date(start)), income, expense));
            }
        } else if (mode == Mode.SIX_MONTHS) {
            title = "Year-over-Year (6 mo)";
            int thisYear = cal.get(Calendar.YEAR);
            for (int offset = 5; offset >= 0; offset--) {
                for (int yr : new int[]{thisYear - 1, thisYear}) {
                    Calendar c = (Calendar) cal.clone();
                    c.set(Calendar.YEAR, yr);
                    c.add(Calendar.MONTH, -offset);
                    c.set(Calendar.DAY_OF_MONTH, 1);
                    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
                    long start = c.getTimeInMillis();

                    c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
                    c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999);
                    long end = c.getTimeInMillis();

                    List<Transaction> txns = db.getTransactions(start, end);
                    float income = 0;
                    float expense = 0;
                    for (Transaction t : txns) {
                        if ("income".equalsIgnoreCase(t.getType())) income += t.getAmount();
                        else if ("expense".equalsIgnoreCase(t.getType())) expense += t.getAmount();
                    }
                    String yrLabel = SimpleDateFormat.getDateInstance().format(new Date(start)); // fallback
                    String labelStr = new SimpleDateFormat("MMM", Locale.getDefault()).format(new Date(start)) + " '" + String.valueOf(yr).substring(2);
                    months.add(new BarChartView.MonthData(labelStr, income, expense));
                }
            }
        } else if (mode == Mode.CUSTOM) {
            if (customFrom == null || customTo == null) return;
            title = pickFmt.format(customFrom.getTime()) + " – " + pickFmt.format(customTo.getTime());

            Calendar cur = (Calendar) customFrom.clone();
            cur.set(Calendar.DAY_OF_MONTH, 1);
            cur.set(Calendar.HOUR_OF_DAY, 0); cur.set(Calendar.MINUTE, 0); cur.set(Calendar.SECOND, 0); cur.set(Calendar.MILLISECOND, 0);

            Calendar toMonth = (Calendar) customTo.clone();
            toMonth.set(Calendar.DAY_OF_MONTH, 1);
            toMonth.set(Calendar.HOUR_OF_DAY, 0); toMonth.set(Calendar.MINUTE, 0); toMonth.set(Calendar.SECOND, 0); toMonth.set(Calendar.MILLISECOND, 0);

            while (!cur.after(toMonth)) {
                long start = cur.getTimeInMillis();
                Calendar endCal = (Calendar) cur.clone();
                endCal.set(Calendar.DAY_OF_MONTH, cur.getActualMaximum(Calendar.DAY_OF_MONTH));
                endCal.set(Calendar.HOUR_OF_DAY, 23); endCal.set(Calendar.MINUTE, 59); endCal.set(Calendar.SECOND, 59); endCal.set(Calendar.MILLISECOND, 999);
                long end = endCal.getTimeInMillis();

                List<Transaction> txns = db.getTransactions(start, end);
                float income = 0;
                float expense = 0;
                for (Transaction t : txns) {
                    if ("income".equalsIgnoreCase(t.getType())) income += t.getAmount();
                    else if ("expense".equalsIgnoreCase(t.getType())) expense += t.getAmount();
                }
                months.add(new BarChartView.MonthData(displayFmt.format(new Date(start)), income, expense));
                cur.add(Calendar.MONTH, 1);
            }
        }

        ((TextView) view.findViewById(R.id.tvTrendsTitle)).setText(title);
        BarChartView chart = view.findViewById(R.id.barChart);
        chart.setData(months);

        float monthlyLimit = requireContext().getSharedPreferences("budget", Context.MODE_PRIVATE).getFloat("monthly_limit", 0f);
        chart.setBudgetLimit(
            (mode == Mode.CONSECUTIVE_MONTHS || mode == Mode.CUSTOM || mode == Mode.SIX_MONTHS) ? monthlyLimit : 0f
        );

        LinearLayout ll = view.findViewById(R.id.llMonthRows);
        ll.removeAllViews();
        ll.addView(makeRow("Period", "Income", "Expense", "Net", true, 0f));
        ll.addView(divider());
        for (BarChartView.MonthData m : months) {
            float net = m.getIncome() - m.getExpense();
            ll.addView(makeRow(
                m.getLabel(),
                "₹" + (int) m.getIncome(),
                "₹" + (int) m.getExpense(),
                (net >= 0 ? "+" : "") + "₹" + (int) net,
                false,
                net
            ));
        }
    }

    private LinearLayout makeRow(String c1, String c2, String c3, String c4, boolean isHeader, float net) {
        Context ctx = requireContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 10, 0, 10);

        row.addView(createTextView(ctx, c1, 1.6f, Gravity.START, isHeader, null, net));
        row.addView(createTextView(ctx, c2, 1.0f, Gravity.END, isHeader, ContextCompat.getColor(ctx, R.color.color_income), net));
        row.addView(createTextView(ctx, c3, 1.0f, Gravity.END, isHeader, ContextCompat.getColor(ctx, R.color.color_expense), net));
        row.addView(createTextView(ctx, c4, 1.0f, Gravity.END, isHeader, ContextCompat.getColor(ctx, net >= 0 ? R.color.color_income : R.color.color_expense), net));

        return row;
    }

    private TextView createTextView(Context ctx, String text, float weight, int align, boolean isHeader, Integer customColor, float net) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setGravity(align);
        if (customColor != null && !isHeader) {
            tv.setTextColor(customColor);
        } else {
            tv.setTextColor(ContextCompat.getColor(ctx, isHeader ? R.color.text_primary : R.color.text_secondary));
        }
        if (isHeader) {
            tv.setTypeface(null, Typeface.BOLD);
        }
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        return tv;
    }

    private View divider() {
        View v = new View(requireContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        v.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider));
        return v;
    }
}
