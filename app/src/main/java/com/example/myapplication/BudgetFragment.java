package com.example.myapplication;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BudgetFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_budget, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("budget", Context.MODE_PRIVATE);

        // Weekly
        EditText etWeekly = view.findViewById(R.id.etWeeklyBudget);
        float weeklyBudget = prefs.getFloat("weekly_limit", 0f);
        if (weeklyBudget > 0) {
            etWeekly.setText(String.valueOf((int) weeklyBudget));
        }
        view.findViewById(R.id.btnSaveWeekly).setOnClickListener(v -> {
            String txt = etWeekly.getText().toString();
            try {
                float val = Float.parseFloat(txt);
                prefs.edit().putFloat("weekly_limit", val).apply();
                refreshWeekly(view, prefs);
                if (getActivity() instanceof MainActivity) {
                    MainActivity activity = (MainActivity) getActivity();
                    activity.updateSummary(activity.lastSummaryData);
                }
            } catch (Exception ignored) {}
        });
        setupToggle(view, R.id.tvWeeklyDetailsToggle, R.id.layoutWeeklyDetails);
        refreshWeekly(view, prefs);

        // Monthly
        EditText etMonthly = view.findViewById(R.id.etMonthlyBudget);
        float monthlyBudget = prefs.getFloat("monthly_limit", 0f);
        if (monthlyBudget > 0) {
            etMonthly.setText(String.valueOf((int) monthlyBudget));
        }
        view.findViewById(R.id.btnSaveMonthly).setOnClickListener(v -> {
            String txt = etMonthly.getText().toString();
            try {
                float val = Float.parseFloat(txt);
                prefs.edit().putFloat("monthly_limit", val).apply();
                refreshMonthly(view, prefs);
                if (getActivity() instanceof MainActivity) {
                    MainActivity activity = (MainActivity) getActivity();
                    activity.updateSummary(activity.lastSummaryData);
                }
            } catch (Exception ignored) {}
        });
        setupToggle(view, R.id.tvMonthlyDetailsToggle, R.id.layoutMonthlyDetails);
        refreshMonthly(view, prefs);

        // Custom
        android.content.SharedPreferences settingsPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        applySettingsVisibility(view, settingsPrefs);

        EditText etCustom = view.findViewById(R.id.etCustomBudget);
        float customBudget = prefs.getFloat("custom_limit", 0f);
        if (customBudget > 0) {
            etCustom.setText(String.valueOf((int) customBudget));
        }
        view.findViewById(R.id.btnSaveCustom).setOnClickListener(v -> {
            String txt = etCustom.getText().toString();
            try {
                float val = Float.parseFloat(txt);
                prefs.edit().putFloat("custom_limit", val).apply();
                refreshCustom(view, prefs, settingsPrefs);
                if (getActivity() instanceof MainActivity) {
                    MainActivity activity = (MainActivity) getActivity();
                    activity.updateSummary(activity.lastSummaryData);
                }
            } catch (Exception ignored) {}
        });
        setupToggle(view, R.id.tvCustomDetailsToggle, R.id.layoutCustomDetails);
        refreshCustom(view, prefs, settingsPrefs);
    }

    @Override
    public void onResume() {
        super.onResume();
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("budget", Context.MODE_PRIVATE);
        android.content.SharedPreferences settingsPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        View view = getView();
        if (view != null) {
            refreshWeekly(view, prefs);
            refreshMonthly(view, prefs);
            refreshCustom(view, prefs, settingsPrefs);
            applySettingsVisibility(view, settingsPrefs);
        }
    }

    public void applySettingsVisibility(View view, android.content.SharedPreferences settingsPrefs) {
        view.findViewById(R.id.cardMonthly).setVisibility(
            settingsPrefs.getBoolean("budget_monthly_enabled", true) ? View.VISIBLE : View.GONE
        );
        view.findViewById(R.id.cardCustom).setVisibility(
            settingsPrefs.getBoolean("budget_custom_enabled", false) ? View.VISIBLE : View.GONE
        );
        String name = settingsPrefs.getString("budget_custom_name", "");
        if (name == null || name.isEmpty()) {
            name = "Custom Budget";
        }
        ((TextView) view.findViewById(R.id.tvCustomTitle)).setText(name);
    }

    public void refreshCustomFromSettings(View view) {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("budget", Context.MODE_PRIVATE);
        android.content.SharedPreferences settingsPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        refreshCustom(view, prefs, settingsPrefs);
    }

    private void setupToggle(View view, int toggleId, int contentId) {
        TextView toggle = view.findViewById(toggleId);
        LinearLayout content = view.findViewById(contentId);
        toggle.setOnClickListener(v -> {
            boolean open = content.getVisibility() == View.VISIBLE;
            content.setVisibility(open ? View.GONE : View.VISIBLE);
            toggle.setText(open ? "▶ Details" : "▼ Details");
        });
    }

    private void refreshWeekly(View view, android.content.SharedPreferences prefs) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long weekStart = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_WEEK, 7);
        long weekEnd = cal.getTimeInMillis();

        SimpleDateFormat fmt = new SimpleDateFormat("d MMM", Locale.getDefault());
        ((TextView) view.findViewById(R.id.tvWeekLabel)).setText(
            fmt.format(new Date(weekStart)) + " – " + fmt.format(new Date(weekEnd - 1))
        );

        List<Transaction> allTxns = DatabaseHelper.getInstance(requireContext()).getTransactions(weekStart, weekEnd);
        List<Transaction> txns = new ArrayList<>();
        float spent = 0;
        for (Transaction t : allTxns) {
            if ("expense".equalsIgnoreCase(t.getType())) {
                txns.add(t);
                spent += t.getAmount();
            }
        }
        float limit = prefs.getFloat("weekly_limit", 0f);
        bindCard(spent, limit,
            view.findViewById(R.id.progressWeekly),
            view.findViewById(R.id.tvWeeklySpent),
            view.findViewById(R.id.tvWeeklyRemaining));
        bindDetails(view, txns, spent, limit, 7,
            view.findViewById(R.id.tvWeeklyStats),
            view.findViewById(R.id.llWeeklyLogs));
    }

    private void refreshMonthly(View view, android.content.SharedPreferences prefs) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        cal.add(Calendar.MONTH, 1);
        long monthEnd = cal.getTimeInMillis();

        SimpleDateFormat fmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        ((TextView) view.findViewById(R.id.tvMonthLabel)).setText(fmt.format(new Date(monthStart)));

        List<Transaction> allTxns = DatabaseHelper.getInstance(requireContext()).getTransactions(monthStart, monthEnd);
        List<Transaction> txns = new ArrayList<>();
        float spent = 0;
        for (Transaction t : allTxns) {
            if ("expense".equalsIgnoreCase(t.getType())) {
                txns.add(t);
                spent += t.getAmount();
            }
        }
        float limit = prefs.getFloat("monthly_limit", 0f);
        bindCard(spent, limit,
            view.findViewById(R.id.progressMonthly),
            view.findViewById(R.id.tvMonthlySpent),
            view.findViewById(R.id.tvMonthlyRemaining));
        bindDetails(view, txns, spent, limit, daysInMonth,
            view.findViewById(R.id.tvMonthlyStats),
            view.findViewById(R.id.llMonthlyLogs));
    }

    private void refreshCustom(View view, android.content.SharedPreferences prefs, android.content.SharedPreferences settingsPrefs) {
        int years = settingsPrefs.getInt("budget_custom_years", 0);
        int months = settingsPrefs.getInt("budget_custom_months", 0);
        int weeks = settingsPrefs.getInt("budget_custom_weeks", 0);
        int days = settingsPrefs.getInt("budget_custom_days", 0);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999);
        long end = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.YEAR, -years);
        cal.add(Calendar.MONTH, -months);
        cal.add(Calendar.WEEK_OF_YEAR, -weeks);
        cal.add(Calendar.DAY_OF_YEAR, -days);
        long start = cal.getTimeInMillis();

        int totalDays = Math.max(1, (int) ((end - start) / 86400000));

        List<String> parts = new ArrayList<>();
        if (years > 0) parts.add(years + " yr");
        if (months > 0) parts.add(months + " mo");
        if (weeks > 0) parts.add(weeks + " wk");
        if (days > 0) parts.add(days + " d");
        
        StringBuilder customLabel = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            customLabel.append(parts.get(i));
            if (i < parts.size() - 1) customLabel.append(" ");
        }
        ((TextView) view.findViewById(R.id.tvCustomLabel)).setText(parts.isEmpty() ? "No period set" : customLabel.toString());

        List<Transaction> txns = new ArrayList<>();
        float spent = 0;
        if (start < end) {
            List<Transaction> allTxns = DatabaseHelper.getInstance(requireContext()).getTransactions(start, end);
            for (Transaction t : allTxns) {
                if ("expense".equalsIgnoreCase(t.getType())) {
                    txns.add(t);
                    spent += t.getAmount();
                }
            }
        }
        float limit = prefs.getFloat("custom_limit", 0f);
        bindCard(spent, limit,
            view.findViewById(R.id.progressCustom),
            view.findViewById(R.id.tvCustomSpent),
            view.findViewById(R.id.tvCustomRemaining));
        bindDetails(view, txns, spent, limit, totalDays,
            view.findViewById(R.id.tvCustomStats),
            view.findViewById(R.id.llCustomLogs));
    }

    private void bindCard(float spent, float limit, ProgressBar progress, TextView tvSpent, TextView tvRemaining) {
        tvSpent.setText("Spent: ₹" + (int) spent);
        if (limit <= 0f) {
            progress.setProgress(0);
            tvRemaining.setText("No limit set");
            tvRemaining.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            return;
        }
        int pct = Math.max(0, Math.min(100, (int) ((spent / limit) * 100)));
        progress.setProgress(pct);
        float remaining = limit - spent;
        if (remaining >= 0) {
            tvRemaining.setText("Left: ₹" + (int) remaining);
            tvRemaining.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_income));
            progress.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.color_income)));
        } else {
            tvRemaining.setText("Over by ₹" + (int) (-remaining));
            tvRemaining.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_expense));
            progress.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.color_expense)));
        }
    }

    private void bindDetails(View view, List<Transaction> txns, float spent, float limit, int periodDays, TextView tvStats, LinearLayout llLogs) {
        Context ctx = requireContext();
        float dailyAvg = txns.isEmpty() ? 0f : spent / periodDays;
        int pct = limit > 0 ? (int) (spent / limit * 100) : 0;

        StringBuilder sb = new StringBuilder();
        if (limit > 0) {
            sb.append("Budget: ₹").append((int) limit).append("  •  Used: ").append(pct).append("%\n");
            float remaining = limit - spent;
            if (remaining >= 0) {
                float dailyLeft = remaining / Math.max(1, periodDays);
                sb.append("Remaining: ₹").append((int) remaining).append("  •  Daily budget left: ₹").append(String.format(Locale.US, "%.0f", dailyLeft)).append("\n");
            } else {
                sb.append("Overspent by: ₹").append((int) (-remaining)).append("\n");
            }
        }
        sb.append("Daily avg spend: ₹").append(String.format(Locale.US, "%.0f", dailyAvg)).append("  •  Transactions: ").append(txns.size());
        tvStats.setText(sb.toString());
        tvStats.setTextSize(13f);

        llLogs.removeAllViews();
        if (txns.isEmpty()) {
            llLogs.addView(makeRow(ctx, "No expenses in this period", "", false, true));
            return;
        }

        // Header row
        llLogs.addView(makeRow(ctx, "Date  ·  Title", "Amount", true, false));

        SimpleDateFormat dateFmt = new SimpleDateFormat("d MMM", Locale.getDefault());
        for (int i = 0; i < txns.size(); i++) {
            Transaction t = txns.get(i);
            String noteVal = t.getNote() != null && !t.getNote().isEmpty() ? t.getNote() : t.getCategory();
            String label = dateFmt.format(new Date(t.getDate())) + "  ·  " + noteVal;
            llLogs.addView(makeRow(ctx, label, "₹" + (int) t.getAmount(), false, i % 2 == 0));
        }
    }

    private LinearLayout makeRow(Context ctx, String left, String right, boolean isHeader, boolean isEven) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 6, 0, 6);
        if (!isHeader) {
            row.setBackgroundColor(ContextCompat.getColor(ctx, isEven ? R.color.background : R.color.surface));
        }

        TextView tvLeft = new TextView(ctx);
        tvLeft.setText(left);
        tvLeft.setTextSize(13f);
        tvLeft.setTextColor(ContextCompat.getColor(ctx, isHeader ? R.color.text_primary : R.color.text_secondary));
        if (isHeader) tvLeft.setTypeface(null, Typeface.BOLD);
        row.addView(tvLeft, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvRight = new TextView(ctx);
        tvRight.setText(right);
        tvRight.setTextSize(13f);
        tvRight.setTextColor(ContextCompat.getColor(ctx, isHeader ? R.color.text_primary : R.color.color_expense));
        if (isHeader) tvRight.setTypeface(null, Typeface.BOLD);
        tvRight.setGravity(Gravity.END);
        row.addView(tvRight, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return row;
    }
}
