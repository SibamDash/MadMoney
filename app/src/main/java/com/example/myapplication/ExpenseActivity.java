package com.example.myapplication;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExpenseActivity extends AppCompatActivity {

    public static final String EXTRA_TYPES = "extra_types";
    public static final String EXTRA_TITLE = "extra_title";

    private final Calendar calendar = Calendar.getInstance();
    private Calendar selectedDayCalendar = null;
    private List<String> filterTypes;
    private String sectionTitle;
    private final List<Transaction> allTransactions = new ArrayList<>();
    private final List<Row> displayRows = new ArrayList<>();
    private ExpenseDayAdapter rowAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_expense);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        filterTypes = getIntent().getStringArrayListExtra(EXTRA_TYPES);
        if (filterTypes == null) {
            filterTypes = new ArrayList<>();
            filterTypes.add("expense");
        }
        // Normalize search types to lowercase
        for (int i = 0; i < filterTypes.size(); i++) {
            filterTypes.set(i, filterTypes.get(i).toLowerCase(Locale.US));
        }

        sectionTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (sectionTitle == null) {
            sectionTitle = "Expenses";
        }

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        View logo = findViewById(R.id.logo);
        if (logo != null) {
            logo.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }
        ((TextView) findViewById(R.id.tvSectionTitle)).setText(sectionTitle);

        rowAdapter = new ExpenseDayAdapter(displayRows);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(rowAdapter);

        setupMonthNavigation();
        setupSearch();

        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddTransactionActivity.class);
            if (selectedDayCalendar != null) {
                intent.putExtra("selected_date_millis", selectedDayCalendar.getTimeInMillis());
            }
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTransactions();
    }

    private void loadTransactions() {
        long[] range = getRange();
        List<Transaction> data = DatabaseHelper.getInstance(this).getTransactions(range[0], range[1]);
        
        List<Transaction> filtered = new ArrayList<>();
        for (Transaction t : data) {
            if (filterTypes.contains(t.getType().toLowerCase(Locale.US))) {
                filtered.add(t);
            }
        }
        filtered.sort((t1, t2) -> Long.compare(t2.getDate(), t1.getDate()));

        allTransactions.clear();
        allTransactions.addAll(filtered);
        applyFilter(null);
        updateDateLabel();
    }

    private void applyFilter(String query) {
        String q = query != null ? query.trim().toLowerCase(Locale.US) : "";
        List<Transaction> filtered = new ArrayList<>();
        if (q.isEmpty()) {
            filtered.addAll(allTransactions);
        } else {
            for (Transaction t : allTransactions) {
                if (t.getTitle().toLowerCase(Locale.US).contains(q) ||
                    t.getCategory().toLowerCase(Locale.US).contains(q) ||
                    t.getType().toLowerCase(Locale.US).contains(q) ||
                    t.getNote().toLowerCase(Locale.US).contains(q) ||
                    t.getAccount().toLowerCase(Locale.US).contains(q) ||
                    String.valueOf(t.getAmount()).contains(q)) {
                    filtered.add(t);
                }
            }
        }

        double total = 0;
        for (Transaction t : filtered) {
            total += t.getAmount();
        }
        ((TextView) findViewById(R.id.tvTotalExpense)).setText(String.format(Locale.US, "₹%.2f", total));

        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Map<String, List<Transaction>> grouped = new LinkedHashMap<>();
        for (Transaction t : filtered) {
            String key = dayFmt.format(new Date(t.getDate()));
            if (!grouped.containsKey(key)) {
                grouped.put(key, new ArrayList<>());
            }
            grouped.get(key).add(t);
        }

        displayRows.clear();
        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            double dayTotal = 0;
            for (Transaction t : entry.getValue()) {
                dayTotal += t.getAmount();
            }
            displayRows.add(new Row.Header(entry.getKey(), dayTotal));
            for (Transaction t : entry.getValue()) {
                displayRows.add(new Row.Item(t));
            }
        }
        rowAdapter.notifyDataSetChanged();
    }

    private void setupSearch() {
        SearchView searchView = findViewById(R.id.searchView);
        ImageView ivSearch = findViewById(R.id.ivSearch);

        ivSearch.setOnClickListener(v -> {
            if (searchView.getVisibility() == View.GONE) {
                searchView.setVisibility(View.VISIBLE);
                findViewById(R.id.llDateNav).setVisibility(View.GONE);
                findViewById(R.id.tvSectionTitle).setVisibility(View.GONE);
                searchView.setIconified(false);
                searchView.requestFocus();
            } else {
                searchView.setVisibility(View.GONE);
                findViewById(R.id.llDateNav).setVisibility(View.VISIBLE);
                findViewById(R.id.tvSectionTitle).setVisibility(View.VISIBLE);
                searchView.setQuery("", false);
                applyFilter(null);
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String q) {
                applyFilter(q);
                return true;
            }
        });

        searchView.setOnCloseListener(() -> {
            searchView.setVisibility(View.GONE);
            findViewById(R.id.llDateNav).setVisibility(View.VISIBLE);
            findViewById(R.id.tvSectionTitle).setVisibility(View.VISIBLE);
            applyFilter(null);
            return false;
        });
    }

    private void setupMonthNavigation() {
        findViewById(R.id.ivBack).setOnClickListener(v -> {
            if (selectedDayCalendar != null) {
                selectedDayCalendar.add(Calendar.DAY_OF_MONTH, -1);
                calendar.setTimeInMillis(selectedDayCalendar.getTimeInMillis());
            } else {
                calendar.add(Calendar.MONTH, -1);
            }
            loadTransactions();
        });

        findViewById(R.id.ivForward).setOnClickListener(v -> {
            if (selectedDayCalendar != null) {
                selectedDayCalendar.add(Calendar.DAY_OF_MONTH, 1);
                calendar.setTimeInMillis(selectedDayCalendar.getTimeInMillis());
            } else {
                calendar.add(Calendar.MONTH, 1);
            }
            loadTransactions();
        });

        TextView tvDateRange = findViewById(R.id.tvDateRange);
        tvDateRange.setOnClickListener(v -> {
            Calendar ref = selectedDayCalendar != null ? selectedDayCalendar : calendar;
            new DatePickerDialog(this, (view, year, month, day) -> {
                selectedDayCalendar = Calendar.getInstance();
                selectedDayCalendar.set(year, month, day);
                calendar.set(year, month, day);
                loadTransactions();
            }, ref.get(Calendar.YEAR), ref.get(Calendar.MONTH), ref.get(Calendar.DAY_OF_MONTH)).show();
        });

        tvDateRange.setOnLongClickListener(v -> {
            if (selectedDayCalendar != null) {
                selectedDayCalendar = null;
                loadTransactions();
                Toast.makeText(this, "Showing full month", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private void updateDateLabel() {
        SimpleDateFormat fmt = selectedDayCalendar != null ? new SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                                                           : new SimpleDateFormat("MMM yyyy", Locale.getDefault());
        Calendar ref = selectedDayCalendar != null ? selectedDayCalendar : calendar;
        ((TextView) findViewById(R.id.tvDateRange)).setText(fmt.format(ref.getTime()));
    }

    private long[] getRange() {
        Calendar day = selectedDayCalendar;
        if (day != null) {
            Calendar s = (Calendar) day.clone();
            s.set(Calendar.HOUR_OF_DAY, 0);
            s.set(Calendar.MINUTE, 0);
            s.set(Calendar.SECOND, 0);
            s.set(Calendar.MILLISECOND, 0);
            Calendar e = (Calendar) day.clone();
            e.set(Calendar.HOUR_OF_DAY, 23);
            e.set(Calendar.MINUTE, 59);
            e.set(Calendar.SECOND, 59);
            e.set(Calendar.MILLISECOND, 999);
            return new long[]{s.getTimeInMillis(), e.getTimeInMillis()};
        } else {
            Calendar s = (Calendar) calendar.clone();
            s.set(Calendar.DAY_OF_MONTH, 1);
            s.set(Calendar.HOUR_OF_DAY, 0);
            s.set(Calendar.MINUTE, 0);
            s.set(Calendar.SECOND, 0);
            s.set(Calendar.MILLISECOND, 0);
            Calendar e = (Calendar) calendar.clone();
            e.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
            e.set(Calendar.HOUR_OF_DAY, 23);
            e.set(Calendar.MINUTE, 59);
            e.set(Calendar.SECOND, 59);
            e.set(Calendar.MILLISECOND, 999);
            return new long[]{s.getTimeInMillis(), e.getTimeInMillis()};
        }
    }

    public static class ExpenseDayAdapter extends RecyclerView.Adapter<ExpenseDayAdapter.VH> {

        private final List<Row> rows;
        private final SimpleDateFormat dayLabelFmt = new SimpleDateFormat("EEEE, d MMM", Locale.getDefault());
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        public static class VH extends RecyclerView.ViewHolder {
            public VH(View view) {
                super(view);
            }
        }

        public ExpenseDayAdapter(List<Row> rows) {
            this.rows = rows;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof Row.Header ? 0 : 1;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == 0 ? R.layout.item_expense_header : R.layout.item_expense_row;
            View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Row row = rows.get(position);
            if (row instanceof Row.Header) {
                Row.Header header = (Row.Header) row;
                try {
                    Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(header.getDateKey());
                    if (date != null) {
                        ((TextView) holder.itemView.findViewById(R.id.tvDayLabel)).setText(dayLabelFmt.format(date));
                    }
                } catch (Exception ignored) {}
                ((TextView) holder.itemView.findViewById(R.id.tvDayTotal)).setText("₹" + (int) header.getTotal());
            } else if (row instanceof Row.Item) {
                Row.Item item = (Row.Item) row;
                Transaction t = item.getTransaction();
                ((TextView) holder.itemView.findViewById(R.id.tvTitle)).setText(t.getTitle());
                ((TextView) holder.itemView.findViewById(R.id.tvCategory)).setText(t.getCategory());
                ((TextView) holder.itemView.findViewById(R.id.tvTime)).setText(timeFmt.format(new Date(t.getDate())));
                ((TextView) holder.itemView.findViewById(R.id.tvAmount)).setText("₹" + (int) t.getAmount());
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
