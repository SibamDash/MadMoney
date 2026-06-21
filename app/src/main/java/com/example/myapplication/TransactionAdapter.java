package com.example.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    public interface OnCheckClickListener {
        void onCheck(Transaction transaction);
    }

    public interface OnCrossClickListener {
        void onCross(Transaction transaction);
    }

    public interface OnStarToggleListener {
        void onStarToggle(Transaction transaction, boolean starred);
    }

    public interface OnDoubleClickListener {
        void onDoubleClick(Transaction transaction);
    }

    public interface OnLongClickListener {
        void onLongClick(Transaction transaction, int actionId);
    }

    private final List<Transaction> transactions;
    private final OnCheckClickListener onCheckClickListener;
    private final OnCrossClickListener onCrossClickListener;
    private final OnStarToggleListener onStarToggleListener;
    private final OnDoubleClickListener onDoubleClickListener;
    private final OnLongClickListener onLongClickListener;

    private final SimpleDateFormat settledFmt = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    public TransactionAdapter(List<Transaction> transactions,
                              OnCheckClickListener onCheckClickListener,
                              OnCrossClickListener onCrossClickListener,
                              OnStarToggleListener onStarToggleListener) {
        this(transactions, onCheckClickListener, onCrossClickListener, onStarToggleListener, null, null);
    }

    public TransactionAdapter(List<Transaction> transactions,
                              OnCheckClickListener onCheckClickListener,
                              OnCrossClickListener onCrossClickListener,
                              OnStarToggleListener onStarToggleListener,
                              OnDoubleClickListener onDoubleClickListener) {
        this(transactions, onCheckClickListener, onCrossClickListener, onStarToggleListener, onDoubleClickListener, null);
    }

    public TransactionAdapter(List<Transaction> transactions,
                              OnCheckClickListener onCheckClickListener,
                              OnCrossClickListener onCrossClickListener,
                              OnStarToggleListener onStarToggleListener,
                              OnDoubleClickListener onDoubleClickListener,
                              OnLongClickListener onLongClickListener) {
        this.transactions = transactions;
        this.onCheckClickListener = onCheckClickListener;
        this.onCrossClickListener = onCrossClickListener;
        this.onStarToggleListener = onStarToggleListener;
        this.onDoubleClickListener = onDoubleClickListener;
        this.onLongClickListener = onLongClickListener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View iconContainer;
        public final TextView tvTitle;
        public final TextView tvSubtitle;
        public final TextView tvSettledAt;
        public final TextView tvAmount;
        public final View actionButtons;
        public final ImageView btnCheck;
        public final ImageView btnCross;
        public final ImageView ivStar;
        public final View cardView;

        public ViewHolder(View view) {
            super(view);
            iconContainer = view.findViewById(R.id.iconContainer);
            tvTitle = view.findViewById(R.id.tvTitle);
            tvSubtitle = view.findViewById(R.id.tvSubtitle);
            tvSettledAt = view.findViewById(R.id.tvSettledAt);
            tvAmount = view.findViewById(R.id.tvAmount);
            actionButtons = view.findViewById(R.id.actionButtons);
            btnCheck = view.findViewById(R.id.btnCheck);
            btnCross = view.findViewById(R.id.btnCross);
            ivStar = view.findViewById(R.id.ivStar);
            cardView = view.findViewById(R.id.cardView);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction item = transactions.get(position);
        Context ctx = holder.itemView.getContext();
        holder.tvTitle.setText(item.getTitle());
        holder.tvAmount.setText("₹" + (int) item.getAmount());

        switch (item.getType().toLowerCase(Locale.US)) {
            case "income":
                holder.tvSubtitle.setText("Income • Received");
                holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_income));
                holder.iconContainer.getBackground().setTint(ContextCompat.getColor(ctx, R.color.color_income));
                holder.actionButtons.setVisibility(View.GONE);
                holder.tvSettledAt.setVisibility(View.GONE);
                break;
            case "expense":
                holder.tvSubtitle.setText("Expense • Paid");
                holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_expense));
                holder.iconContainer.getBackground().setTint(ContextCompat.getColor(ctx, R.color.color_expense));
                holder.actionButtons.setVisibility(View.GONE);
                holder.tvSettledAt.setVisibility(View.GONE);
                break;
            case "togive":
                holder.tvSubtitle.setText("To Give • Debt");
                holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_to_give));
                holder.iconContainer.getBackground().setTint(ContextCompat.getColor(ctx, R.color.color_to_give));
                holder.actionButtons.setVisibility(item.isCompleted() ? View.GONE : View.VISIBLE);
                if (item.isCompleted() && item.getCompletedAt() > 0) {
                    holder.tvSettledAt.setText("Settled on " + settledFmt.format(new Date(item.getCompletedAt())));
                    holder.tvSettledAt.setVisibility(View.VISIBLE);
                } else {
                    holder.tvSettledAt.setVisibility(View.GONE);
                }
                break;
            case "toget":
                holder.tvSubtitle.setText("To Get • Credit");
                holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_to_get));
                holder.iconContainer.getBackground().setTint(ContextCompat.getColor(ctx, R.color.color_to_get));
                holder.actionButtons.setVisibility(item.isCompleted() ? View.GONE : View.VISIBLE);
                if (item.isCompleted() && item.getCompletedAt() > 0) {
                    holder.tvSettledAt.setText("Settled on " + settledFmt.format(new Date(item.getCompletedAt())));
                    holder.tvSettledAt.setVisibility(View.VISIBLE);
                } else {
                    holder.tvSettledAt.setVisibility(View.GONE);
                }
                break;
        }

        holder.ivStar.setVisibility(item.isStarred() ? View.VISIBLE : View.GONE);

        holder.cardView.setOnClickListener(new View.OnClickListener() {
            private long lastClick = 0L;
            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - lastClick < 300) {
                    if (onDoubleClickListener != null) {
                        onDoubleClickListener.onDoubleClick(item);
                    } else if (onStarToggleListener != null) {
                        onStarToggleListener.onStarToggle(item, !item.isStarred());
                    }
                }
                lastClick = now;
            }
        });

        holder.cardView.setOnLongClickListener(v -> {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(v.getContext(), v);
            popup.getMenu().add(0, 0, 0, "✏️ Edit");
            popup.getMenu().add(0, 1, 1, "🗑️ Delete");
            popup.getMenu().add(0, 2, 2, "⭐ Save");
            popup.setOnMenuItemClickListener(menuItem -> {
                if (onLongClickListener != null) {
                    onLongClickListener.onLongClick(item, menuItem.getItemId());
                }
                return true;
            });
            popup.show();
            return true;
        });

        holder.btnCheck.setOnClickListener(v -> {
            if (onCheckClickListener != null) {
                onCheckClickListener.onCheck(item);
            }
        });

        holder.btnCross.setOnClickListener(v -> {
            if (onCrossClickListener != null) {
                onCrossClickListener.onCross(item);
            }
        });
    }
}
