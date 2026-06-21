package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.VH> {

    private final List<LegendItem> items;
    private final double total;

    public LegendAdapter(List<LegendItem> items, double total) {
        this.items = items;
        this.total = total;
    }

    public static class VH extends RecyclerView.ViewHolder {
        public final View dot;
        public final TextView tvCategory;
        public final TextView tvAmount;
        public final TextView tvPercent;

        public VH(View view) {
            super(view);
            dot = view.findViewById(R.id.dot);
            tvCategory = view.findViewById(R.id.tvCategory);
            tvAmount = view.findViewById(R.id.tvAmount);
            tvPercent = view.findViewById(R.id.tvPercent);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_legend, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        LegendItem item = items.get(position);
        holder.dot.setBackgroundColor(item.getColor());
        holder.tvCategory.setText(item.getLabel());
        holder.tvAmount.setText("₹" + (int) item.getAmount());
        double percent = (item.getAmount() / total) * 100;
        holder.tvPercent.setText(String.format(Locale.US, "%.1f%%", percent));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
