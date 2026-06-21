package com.example.myapplication;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChartFragment extends Fragment {

    private final List<String> expensePalette = Arrays.asList(
        "#F44336", "#FF6D00", "#FFD600", "#00C853",
        "#00B0FF", "#651FFF", "#F50057", "#00BFA5",
        "#FF6F00", "#1565C0", "#6A1B9A", "#2E7D32"
    );
    private final List<String> incomePalette = Arrays.asList(
        "#00C853", "#00B0FF", "#651FFF", "#FFD600",
        "#F44336", "#FF6D00", "#00BFA5", "#F50057",
        "#1565C0", "#FF6F00", "#6A1B9A", "#2E7D32"
    );

    private String selectedType = "expense";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView btnExpense = view.findViewById(R.id.btnChartExpense);
        TextView btnIncome = view.findViewById(R.id.btnChartIncome);

        OnTypeSelect typeSelect = new OnTypeSelect() {
            @Override
            public void selectType(String type) {
                selectedType = type;
                int selColor = type.equals("expense") 
                    ? ContextCompat.getColor(requireContext(), R.color.color_expense)
                    : ContextCompat.getColor(requireContext(), R.color.color_income);
                int unselText = ContextCompat.getColor(requireContext(), R.color.text_secondary);
                int white = ContextCompat.getColor(requireContext(), android.R.color.white);

                btnExpense.setBackgroundColor(type.equals("expense") ? selColor : Color.TRANSPARENT);
                btnIncome.setBackgroundColor(type.equals("income") ? ContextCompat.getColor(requireContext(), R.color.color_income) : Color.TRANSPARENT);
                btnExpense.setTextColor(type.equals("expense") ? white : unselText);
                btnIncome.setTextColor(type.equals("income") ? white : unselText);
                load(view);
            }
        };

        btnExpense.setOnClickListener(v -> typeSelect.selectType("expense"));
        btnIncome.setOnClickListener(v -> typeSelect.selectType("income"));
        typeSelect.selectType("expense");
    }

    @Override
    public void onResume() {
        super.onResume();
        View view = getView();
        if (view != null) {
            load(view);
        }
    }

    private void load(View view) {
        Calendar cal = Calendar.getInstance();
        Calendar start = (Calendar) cal.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) cal.clone();
        end.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);

        List<String> palette = selectedType.equals("income") ? incomePalette : expensePalette;
        List<Transaction> transactions = DatabaseHelper.getInstance(requireContext())
            .getTransactions(start.getTimeInMillis(), end.getTimeInMillis());

        List<Transaction> filtered = new ArrayList<>();
        for (Transaction t : transactions) {
            if (t.getType().equalsIgnoreCase(selectedType)) {
                filtered.add(t);
            }
        }

        Map<String, Double> categorySums = new HashMap<>();
        for (Transaction t : filtered) {
            String cat = t.getCategory();
            double sum = categorySums.getOrDefault(cat, 0.0);
            categorySums.put(cat, sum + t.getAmount());
        }

        List<Pair<String, Double>> grouped = new ArrayList<>();
        double total = 0.0;
        for (Map.Entry<String, Double> entry : categorySums.entrySet()) {
            if (entry.getValue() > 0) {
                grouped.add(new Pair<>(entry.getKey(), entry.getValue()));
                total += entry.getValue();
            }
        }
        grouped.sort((p1, p2) -> Double.compare(p2.second, p1.second));

        double totalVal = total > 0 ? total : 1.0;

        List<Pair<Float, Integer>> slices = new ArrayList<>();
        List<LegendItem> legendItems = new ArrayList<>();

        for (int i = 0; i < grouped.size(); i++) {
            Pair<String, Double> entry = grouped.get(i);
            float sweep = (float) (entry.second / totalVal * 360.0);
            int color = Color.parseColor(palette.get(i % palette.size()));
            slices.add(new Pair<>(sweep, color));
            legendItems.add(new LegendItem(entry.first, entry.second, color));
        }

        PieChartView pieChart = view.findViewById(R.id.pieChart);
        pieChart.setData(slices, "₹" + (int) total);

        RecyclerView rvLegend = view.findViewById(R.id.rvLegend);
        if (rvLegend.getLayoutManager() == null) {
            rvLegend.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
        rvLegend.setAdapter(new LegendAdapter(legendItems, totalVal));
    }

    private interface OnTypeSelect {
        void selectType(String type);
    }
}
