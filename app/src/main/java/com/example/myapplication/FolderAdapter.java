package com.example.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {

    public interface OnFolderClickListener {
        void onFolderClick(Folder folder);
    }

    public interface OnFolderLongClickListener {
        void onFolderLongClick(Folder folder);
    }

    private final List<Folder> folders;
    private final OnFolderClickListener onClickListener;
    private final OnFolderLongClickListener onLongClickListener;

    public FolderAdapter(List<Folder> folders, OnFolderClickListener onClickListener, OnFolderLongClickListener onLongClickListener) {
        this.folders = folders;
        this.onClickListener = onClickListener;
        this.onLongClickListener = onLongClickListener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView tvFolderName;
        public final TextView tvFolderItemCount;

        public ViewHolder(View view) {
            super(view);
            tvFolderName = view.findViewById(R.id.tvFolderName);
            tvFolderItemCount = view.findViewById(R.id.tvFolderItemCount);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Folder item = folders.get(position);
        Context ctx = holder.itemView.getContext();
        
        holder.tvFolderName.setText(item.getName());
        
        int count = DatabaseHelper.getInstance(ctx).getTransactionsInFolder(item.getId()).size();
        holder.tvFolderItemCount.setText(count + " logs");

        holder.itemView.setOnClickListener(v -> {
            if (onClickListener != null) {
                onClickListener.onFolderClick(item);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (onLongClickListener != null) {
                onLongClickListener.onFolderLongClick(item);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }
}
