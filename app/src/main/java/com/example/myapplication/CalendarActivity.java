package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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

public class CalendarActivity extends AppCompatActivity {

    private final Calendar calendar = Calendar.getInstance();
    private GestureDetector gestureDetector;
    private boolean isAnimating = false;

    private TextView tvMonthYear;
    private RecyclerView rvCalendar;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_calendar);

        tvMonthYear = findViewById(R.id.tvMonthYear);
        rvCalendar = findViewById(R.id.rvCalendar);
        View root = findViewById(R.id.root);
        View btnBack = findViewById(R.id.ivBack);
        View layoutMonthPicker = findViewById(R.id.layoutMonthPicker);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null) return false;
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                    if (diffX < 0) {
                        animateMonthChange(-1);
                    } else {
                        animateMonthChange(1);
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        root.setOnTouchListener((view, event) -> {
            if (gestureDetector.onTouchEvent(event)) return true;
            if (event.getAction() == MotionEvent.ACTION_UP) {
                view.performClick();
            }
            return true;
        });

        rvCalendar.setOnTouchListener((view, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });

        btnBack.setOnClickListener(v -> finish());
        View logo = findViewById(R.id.logo);
        if (logo != null) {
            logo.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }
        layoutMonthPicker.setOnClickListener(v -> showMonthYearPicker());

        loadCalendar();
    }

    private void animateMonthChange(int delta) {
        if (isAnimating) return;
        isAnimating = true;

        float screenWidth = getResources().getDisplayMetrics().widthPixels;
        float outTranslation = delta > 0 ? screenWidth : -screenWidth;
        float inStartTranslation = delta > 0 ? -screenWidth : screenWidth;

        tvMonthYear.animate().alpha(0f).setDuration(150).start();

        rvCalendar.animate()
            .translationX(outTranslation)
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(() -> {
                calendar.add(Calendar.MONTH, delta);
                loadCalendar();

                rvCalendar.setTranslationX(inStartTranslation);
                rvCalendar.setAlpha(0f);
                tvMonthYear.setAlpha(0f);

                rvCalendar.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(250)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> isAnimating = false)
                    .start();

                tvMonthYear.animate().alpha(1f).setDuration(250).start();
            })
            .start();
    }

    private void showMonthYearPicker() {
        String[] months = new String[]{"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        NumberPicker monthPicker = new NumberPicker(this);
        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(11);
        monthPicker.setDisplayedValues(months);
        monthPicker.setValue(calendar.get(Calendar.MONTH));
        monthPicker.setWrapSelectorWheel(true);

        NumberPicker yearPicker = new NumberPicker(this);
        yearPicker.setMinValue(2000);
        yearPicker.setMaxValue(2100);
        yearPicker.setValue(calendar.get(Calendar.YEAR));
        yearPicker.setWrapSelectorWheel(false);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(p, p / 2, p, p / 2);
        container.addView(monthPicker, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        container.addView(yearPicker, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        new AlertDialog.Builder(this)
            .setTitle("Select Month")
            .setView(container)
            .setPositiveButton("Go", (dialog, which) -> {
                calendar.set(Calendar.MONTH, monthPicker.getValue());
                calendar.set(Calendar.YEAR, yearPicker.getValue());
                loadCalendar();
            })
            .setNegativeButton("Cancel", null)
            .show();
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

        List<Transaction> transactions = DatabaseHelper.getInstance(this).getTransactions(monthStart.getTimeInMillis(), monthEnd.getTimeInMillis());

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

        int firstDayOfWeek = monthStart.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = monthEnd.get(Calendar.DAY_OF_MONTH);
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < firstDayOfWeek; i++) {
            cells.add(null);
        }
        for (int d = 1; d <= daysInMonth; d++) {
            cells.add(d);
        }

        Calendar today = Calendar.getInstance();
        boolean isCurrentMonth = today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                                 today.get(Calendar.MONTH) == calendar.get(Calendar.MONTH);
        int todayDay = isCurrentMonth ? today.get(Calendar.DAY_OF_MONTH) : -1;

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;

        if (rvCalendar.getLayoutManager() == null) {
            rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        }
        rvCalendar.setAdapter(new CalendarAdapter(cells, dayMap, year, month, todayDay, day -> {
            Calendar targetCal = Calendar.getInstance();
            targetCal.set(year, month - 1, day, 0, 0, 0);
            targetCal.set(Calendar.MILLISECOND, 0);
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("selected_date_millis", targetCal.getTimeInMillis());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }));
    }
}
