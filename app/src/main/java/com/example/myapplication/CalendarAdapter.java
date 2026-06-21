package com.example.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.VH> {

    public interface OnDayClickListener {
        void onDayClick(int day);
    }

    private final List<Integer> cells;
    private final Map<String, DaySummary> dayMap;
    private final int year;
    private final int month;
    private final int todayDay;
    private final OnDayClickListener onDayClickListener;

    public CalendarAdapter(List<Integer> cells, Map<String, DaySummary> dayMap, 
                           int year, int month, int todayDay, OnDayClickListener onDayClickListener) {
        this.cells = cells;
        this.dayMap = dayMap;
        this.year = year;
        this.month = month;
        this.todayDay = todayDay;
        this.onDayClickListener = onDayClickListener;
    }

    public static class VH extends RecyclerView.ViewHolder {
        public final TextView tvDay;
        public final TextView tvIncome;
        public final TextView tvExpense;
        public final TextView tvDebt;

        public VH(View view) {
            super(view);
            tvDay = view.findViewById(R.id.tvDay);
            tvIncome = view.findViewById(R.id.tvIncome);
            tvExpense = view.findViewById(R.id.tvExpense);
            tvDebt = view.findViewById(R.id.tvDebt);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new VH(view);
    }

    @Override
    public int getItemCount() {
        return cells.size();
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Integer day = cells.get(position);

        if (day == null) {
            holder.tvDay.setText("");
            holder.tvIncome.setText("");
            holder.tvExpense.setText("");
            holder.tvDebt.setText("");
            holder.itemView.setBackground(null);
            holder.itemView.setOnClickListener(null);
            return;
        }

        holder.tvDay.setText(String.valueOf(day));

        Context context = holder.itemView.getContext();
        if (day == todayDay) {
            holder.tvDay.setBackgroundResource(R.drawable.bg_toggle_selected);
            holder.tvDay.setTextColor(ContextCompat.getColor(context, R.color.white));
        } else {
            holder.tvDay.setBackground(null);
            holder.tvDay.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        }

        String key = String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
        DaySummary summary = dayMap.get(key);

        holder.tvIncome.setText(summary != null && summary.getIncome() > 0 ? "+" + (int) summary.getIncome() : "");
        holder.tvExpense.setText(summary != null && summary.getExpense() > 0 ? "-" + (int) summary.getExpense() : "");
        holder.tvDebt.setText(summary != null && summary.getDebt() > 0 ? "~" + (int) summary.getDebt() : "");

        holder.itemView.setOnClickListener(v -> {
            if (onDayClickListener != null) {
                onDayClickListener.onDayClick(day);
            }
        });
    }
}
