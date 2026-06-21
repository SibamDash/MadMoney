package com.example.myapplication;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GroupedTransactionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnCheckClickListener {
        void onCheck(Transaction transaction);
    }

    public interface OnCrossClickListener {
        void onCross(Transaction transaction);
    }

    public interface OnStarToggleListener {
        void onStarToggle(Transaction transaction, boolean starred);
    }

    public interface OnLongClickListener {
        void onLongClick(Transaction transaction, int actionId);
    }

    public interface OnDoubleClickListener {
        void onDoubleClick(Transaction transaction);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(Set<Long> selectedIds);
    }

    private final List<GroupedRow> rows = new ArrayList<>();
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat headerFmt = new SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat settledFmt = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    private final Map<String, String> defaultEmojis = new HashMap<>();
    private final Map<String, String> defaultIncomeEmojis = new HashMap<>();

    private boolean multiSelectMode = false;
    private final Set<Long> selectedIds = new HashSet<>();
    private OnSelectionChangedListener onSelectionChangedListener;

    private final OnCheckClickListener onCheckClickListener;
    private final OnCrossClickListener onCrossClickListener;
    private final OnStarToggleListener onStarToggleListener;
    private final OnLongClickListener onLongClickListener;
    private final OnDoubleClickListener onDoubleClickListener;

    public GroupedTransactionAdapter(OnCheckClickListener onCheckClickListener,
                                     OnCrossClickListener onCrossClickListener,
                                     OnStarToggleListener onStarToggleListener,
                                     OnLongClickListener onLongClickListener) {
        this(onCheckClickListener, onCrossClickListener, onStarToggleListener, onLongClickListener, null);
    }

    public GroupedTransactionAdapter(OnCheckClickListener onCheckClickListener,
                                     OnCrossClickListener onCrossClickListener,
                                     OnStarToggleListener onStarToggleListener,
                                     OnLongClickListener onLongClickListener,
                                     OnDoubleClickListener onDoubleClickListener) {
        this.onCheckClickListener = onCheckClickListener;
        this.onCrossClickListener = onCrossClickListener;
        this.onStarToggleListener = onStarToggleListener;
        this.onLongClickListener = onLongClickListener;
        this.onDoubleClickListener = onDoubleClickListener;

        // Populate default emojis
        defaultEmojis.put("Food", "🍔");
        defaultEmojis.put("Social Life", "🎉");
        defaultEmojis.put("Pets", "🐾");
        defaultEmojis.put("Transport", "🚗");
        defaultEmojis.put("Health", "💊");
        defaultEmojis.put("Education", "📚");
        defaultEmojis.put("Gift", "🎁");
        defaultEmojis.put("Apparel", "👗");

        defaultIncomeEmojis.put("Allowance", "💰");
        defaultIncomeEmojis.put("Salary", "💼");
        defaultIncomeEmojis.put("Cash", "💵");
        defaultIncomeEmojis.put("Bonus", "🎯");
    }

    public void enableMultiSelect(OnSelectionChangedListener listener) {
        this.multiSelectMode = true;
        this.selectedIds.clear();
        this.onSelectionChangedListener = listener;
        notifyDataSetChanged();
    }

    public void disableMultiSelect() {
        this.multiSelectMode = false;
        this.selectedIds.clear();
        this.onSelectionChangedListener = null;
        notifyDataSetChanged();
    }

    public Set<Long> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    public void submitList(List<Transaction> transactions) {
        List<GroupedRow> newRows = new ArrayList<>();
        
        // Sort transactions descending by date
        List<Transaction> sorted = new ArrayList<>(transactions);
        sorted.sort((t1, t2) -> Long.compare(t2.getDate(), t1.getDate()));

        // Group by day label
        Map<String, List<Transaction>> grouped = new LinkedHashMap<>();
        for (Transaction t : sorted) {
            String key = dayFmt.format(new Date(t.getDate()));
            if (!grouped.containsKey(key)) {
                grouped.put(key, new ArrayList<>());
            }
            grouped.get(key).add(t);
        }

        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            List<Transaction> items = entry.getValue();
            double income = 0;
            double expense = 0;
            List<String> types = new ArrayList<>();
            for (Transaction t : items) {
                if ("income".equalsIgnoreCase(t.getType())) {
                    income += t.getAmount();
                } else if ("expense".equalsIgnoreCase(t.getType())) {
                    expense += t.getAmount();
                }
                types.add(t.getType());
            }
            String label = headerFmt.format(new Date(items.get(0).getDate()));
            newRows.add(new GroupedRow.Header(label, income - expense, types));
            for (Transaction t : items) {
                newRows.add(new GroupedRow.Item(t));
            }
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return rows.size();
            }

            @Override
            public int getNewListSize() {
                return newRows.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                GroupedRow oldRow = rows.get(oldItemPosition);
                GroupedRow newRow = newRows.get(newItemPosition);
                if (oldRow instanceof GroupedRow.Header && newRow instanceof GroupedRow.Header) {
                    return ((GroupedRow.Header) oldRow).getDateLabel().equals(((GroupedRow.Header) newRow).getDateLabel());
                }
                if (oldRow instanceof GroupedRow.Item && newRow instanceof GroupedRow.Item) {
                    return ((GroupedRow.Item) oldRow).getTransaction().getId() == ((GroupedRow.Item) newRow).getTransaction().getId();
                }
                return false;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return rows.get(oldItemPosition).equals(newRows.get(newItemPosition));
            }
        });

        rows.clear();
        rows.addAll(newRows);
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof GroupedRow.Header ? 0 : 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == 0) {
            return new HeaderVH(inflater.inflate(R.layout.item_expense_header, parent, false));
        } else {
            return new ItemVH(inflater.inflate(R.layout.item_transaction, parent, false));
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private void loadCategoryIcon(Context ctx, ItemVH holder, String categoryName, boolean isIncome) {
        String prefsKey = isIncome ? "income_cat_icons" : "cat_icons";
        String catPrefsKey = isIncome ? "income_categories" : "categories";
        Map<String, String> defaultEmojiMap = isIncome ? defaultIncomeEmojis : defaultEmojis;

        android.content.SharedPreferences catPrefs = ctx.getSharedPreferences(catPrefsKey, Context.MODE_PRIVATE);
        android.content.SharedPreferences iconPrefs = ctx.getSharedPreferences(prefsKey, Context.MODE_PRIVATE);

        Set<String> listSet = catPrefs.getStringSet("list", null);
        int count = listSet != null ? listSet.size() : 0;
        Integer index = null;
        for (int i = 0; i < count; i++) {
            if (categoryName.equals(catPrefs.getString("cat_" + i, null))) {
                index = i;
                break;
            }
        }

        if (index != null) {
            String imagePath = iconPrefs.getString("icon_" + index, null);
            if (imagePath != null && new File(imagePath).exists()) {
                holder.ivCategoryIcon.setImageBitmap(BitmapFactory.decodeFile(imagePath));
                holder.ivCategoryIcon.clearColorFilter();
                holder.tvCategoryEmoji.setVisibility(View.GONE);
                holder.ivCategoryIcon.setVisibility(View.VISIBLE);
                return;
            }
            String emoji = iconPrefs.getString("emoji_" + index, defaultEmojiMap.get(categoryName));
            if (emoji != null) {
                holder.tvCategoryEmoji.setText(emoji);
                holder.tvCategoryEmoji.setVisibility(View.VISIBLE);
                holder.ivCategoryIcon.setVisibility(View.GONE);
                return;
            }
        }

        // Fallback
        String emoji = defaultEmojiMap.get(categoryName);
        if (emoji != null) {
            holder.tvCategoryEmoji.setText(emoji);
            holder.tvCategoryEmoji.setVisibility(View.VISIBLE);
            holder.ivCategoryIcon.setVisibility(View.GONE);
        } else {
            holder.tvCategoryEmoji.setVisibility(View.GONE);
            holder.ivCategoryIcon.setImageResource(R.drawable.ic_list);
            holder.ivCategoryIcon.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GroupedRow row = rows.get(position);
        if (row instanceof GroupedRow.Header) {
            HeaderVH h = (HeaderVH) holder;
            GroupedRow.Header header = (GroupedRow.Header) row;
            h.tvDayLabel.setText(header.getDateLabel());
            double net = header.getDayTotal();
            h.tvDayTotal.setText(net >= 0 ? "+₹" + (int) net : "-₹" + (int) (-net));
            h.tvDayTotal.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                net >= 0 ? R.color.color_income : R.color.color_expense));
        } else if (row instanceof GroupedRow.Item) {
            ItemVH h = (ItemVH) holder;
            Transaction t = ((GroupedRow.Item) row).getTransaction();
            Context ctx = h.itemView.getContext();

            h.tvTitle.setText(t.getNote() != null && !t.getNote().trim().isEmpty() ? t.getNote() : t.getTitle());
            h.tvAmount.setText("₹" + (int) t.getAmount());
            h.tvSubtitle.setText(timeFmt.format(new Date(t.getDate())));

            switch (t.getType().toLowerCase(Locale.US)) {
                case "income":
                    h.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_income));
                    h.iconContainer.getBackground().setTint(ContextCompat.getColor(ctx, R.color.color_income));
                    h.actionButtons.setVisibility(View.GONE);
                    h.tvSettledAt.setVisibility(View.GONE);
                    loadCategoryIcon(ctx, h, t.getCategory(), true);
                    break;
                case "expense":
                    h.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_expense));
                    h.iconContainer.getBackground().setTint(ContextCompat.getColor(ctx, R.color.color_expense));
                    h.actionButtons.setVisibility(View.GONE);
                    h.tvSettledAt.setVisibility(View.GONE);
                    loadCategoryIcon(ctx, h, t.getCategory(), false);
                    break;
                case "togive":
                    h.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_to_give));
                    h.iconContainer.getBackground().setTint(ContextCompat.getColor(ctx, R.color.color_to_give));
                    h.actionButtons.setVisibility(t.isCompleted() ? View.GONE : View.VISIBLE);
                    if (t.isCompleted() && t.getCompletedAt() > 0) {
                        h.tvSettledAt.setText("Settled on " + settledFmt.format(new Date(t.getCompletedAt())));
                        h.tvSettledAt.setVisibility(View.VISIBLE);
                    } else {
                        h.tvSettledAt.setVisibility(View.GONE);
                    }
                    h.tvCategoryEmoji.setVisibility(View.GONE);
                    h.ivCategoryIcon.setImageResource(R.drawable.ic_list);
                    h.ivCategoryIcon.setVisibility(View.VISIBLE);
                    break;
                case "toget":
                    h.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_to_get));
                    h.iconContainer.getBackground().setTint(ContextCompat.getColor(ctx, R.color.color_to_get));
                    h.actionButtons.setVisibility(t.isCompleted() ? View.GONE : View.VISIBLE);
                    if (t.isCompleted() && t.getCompletedAt() > 0) {
                        h.tvSettledAt.setText("Settled on " + settledFmt.format(new Date(t.getCompletedAt())));
                        h.tvSettledAt.setVisibility(View.VISIBLE);
                    } else {
                        h.tvSettledAt.setVisibility(View.GONE);
                    }
                    h.tvCategoryEmoji.setVisibility(View.GONE);
                    h.ivCategoryIcon.setImageResource(R.drawable.ic_list);
                    h.ivCategoryIcon.setVisibility(View.VISIBLE);
                    break;
            }

            if (t.isStarred()) {
                h.ivStar.setImageResource(R.drawable.ic_star_filled);
                h.ivStar.setColorFilter(ContextCompat.getColor(ctx, R.color.text_primary));
                h.ivStar.setVisibility(View.VISIBLE);
            } else {
                h.ivStar.setVisibility(View.GONE);
            }

            // Multi-select mode handling
            if (multiSelectMode) {
                h.cbSelect.setVisibility(View.VISIBLE);
                h.cbSelect.setChecked(selectedIds.contains(t.getId()));
                h.cardView.setOnClickListener(v -> {
                    if (selectedIds.contains(t.getId())) {
                        selectedIds.remove(t.getId());
                    } else {
                        selectedIds.add(t.getId());
                    }
                    h.cbSelect.setChecked(selectedIds.contains(t.getId()));
                    if (onSelectionChangedListener != null) {
                        onSelectionChangedListener.onSelectionChanged(new HashSet<>(selectedIds));
                    }
                });
                h.cardView.setOnLongClickListener(v -> true); // suppress in multi-select
            } else {
                h.cbSelect.setVisibility(View.GONE);
                h.cardView.setOnClickListener(new View.OnClickListener() {
                    private long lastClick = 0L;
                    @Override
                    public void onClick(View v) {
                        long now = System.currentTimeMillis();
                        if (now - lastClick < 300) {
                            if (onDoubleClickListener != null) {
                                onDoubleClickListener.onDoubleClick(t);
                            } else if (onStarToggleListener != null) {
                                onStarToggleListener.onStarToggle(t, !t.isStarred());
                            }
                        }
                        lastClick = now;
                    }
                });

                h.cardView.setOnLongClickListener(v -> {
                    PopupMenu popup = new PopupMenu(v.getContext(), v);
                    popup.getMenu().add(0, 0, 0, "✏️ Edit");
                    popup.getMenu().add(0, 1, 1, "🗑️ Delete");
                    popup.getMenu().add(0, 2, 2, "⭐ Save");
                    popup.setOnMenuItemClickListener(item -> {
                        if (onLongClickListener != null) {
                            onLongClickListener.onLongClick(t, item.getItemId());
                        }
                        return true;
                    });
                    popup.show();
                    return true;
                });
            }

            h.btnCheck.setOnClickListener(v -> {
                if (onCheckClickListener != null) {
                    onCheckClickListener.onCheck(t);
                }
            });

            h.btnCross.setOnClickListener(v -> {
                if (onCrossClickListener != null) {
                    onCrossClickListener.onCross(t);
                }
            });
        }
    }

    public static class HeaderVH extends RecyclerView.ViewHolder {
        public final TextView tvDayLabel;
        public final TextView tvDayTotal;

        public HeaderVH(View view) {
            super(view);
            tvDayLabel = view.findViewById(R.id.tvDayLabel);
            tvDayTotal = view.findViewById(R.id.tvDayTotal);
        }
    }

    public static class ItemVH extends RecyclerView.ViewHolder {
        public final TextView tvTitle;
        public final TextView tvSubtitle;
        public final TextView tvSettledAt;
        public final TextView tvAmount;
        public final View iconContainer;
        public final ImageView ivCategoryIcon;
        public final TextView tvCategoryEmoji;
        public final View actionButtons;
        public final ImageView btnCheck;
        public final ImageView btnCross;
        public final ImageView ivStar;
        public final View cardView;
        public final CheckBox cbSelect;

        public ItemVH(View view) {
            super(view);
            tvTitle = view.findViewById(R.id.tvTitle);
            tvSubtitle = view.findViewById(R.id.tvSubtitle);
            tvSettledAt = view.findViewById(R.id.tvSettledAt);
            tvAmount = view.findViewById(R.id.tvAmount);
            iconContainer = view.findViewById(R.id.iconContainer);
            ivCategoryIcon = view.findViewById(R.id.ivCategoryIcon);
            tvCategoryEmoji = view.findViewById(R.id.tvCategoryEmoji);
            actionButtons = view.findViewById(R.id.actionButtons);
            btnCheck = view.findViewById(R.id.btnCheck);
            btnCross = view.findViewById(R.id.btnCross);
            ivStar = view.findViewById(R.id.ivStar);
            cardView = view.findViewById(R.id.cardView);
            cbSelect = view.findViewById(R.id.cbSelect);
        }
    }
}
