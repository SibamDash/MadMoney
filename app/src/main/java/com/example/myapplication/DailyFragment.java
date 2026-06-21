package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DailyFragment extends Fragment {

    private GroupedTransactionAdapter adapter;
    private String activeFilter = "all";
    private String searchQuery = "";
    private String searchField = "all";
    private int[] selectedDate = null; // year, month, day
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
    private boolean inMultiSelectMode = false;
    private long multiSelectTargetFolderId = -1L;
    private long pendingMultiSelectFolderId = -1L; // queued before adapter is ready

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setId(R.id.recyclerView);
        return rv;
    }

    private void handleTransactionDoubleClick(Transaction t) {
        if (t.isStarred() || t.getFolderId() > 0) {
            final long oldFolderId = t.getFolderId();
            final boolean oldStarred = t.isStarred();
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(requireContext());
            dbHelper.setTransactionFolder(t.getId(), 0L);
            dbHelper.toggleStar(t.getId(), false);
            load();
            Snackbar.make(requireActivity().findViewById(R.id.main), "Transaction unsaved", 5000)
                .setAction("Undo", v -> {
                    dbHelper.setTransactionFolder(t.getId(), oldFolderId);
                    dbHelper.toggleStar(t.getId(), oldStarred);
                    load();
                }).show();
            return;
        }

        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
        boolean remember = prefs.getBoolean("pref_remember_folder", false);
        long rememberedFolderId = prefs.getLong("pref_remembered_folder_id", 0L);

        DatabaseHelper db = DatabaseHelper.getInstance(requireContext());
        List<Folder> folders = db.getFolders();

        if (remember) {
            boolean exists = false;
            String folderName = "Uncategorized";
            if (rememberedFolderId == 0L) {
                exists = true;
            } else {
                for (Folder f : folders) {
                    if (f.getId() == rememberedFolderId) {
                        exists = true;
                        folderName = f.getName();
                        break;
                    }
                }
            }
            if (exists) {
                db.setTransactionFolder(t.getId(), rememberedFolderId);
                db.toggleStar(t.getId(), true);
                android.widget.Toast.makeText(requireContext(), "Saved to \"" + folderName + "\"", android.widget.Toast.LENGTH_SHORT).show();
                load();
                return;
            }
        }

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        android.widget.LinearLayout spinnerRow = new android.widget.LinearLayout(requireContext());
        spinnerRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        spinnerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        android.widget.Spinner spinner = new android.widget.Spinner(requireContext());
        android.widget.LinearLayout.LayoutParams spinnerLp = new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        spinner.setLayoutParams(spinnerLp);
        spinnerRow.addView(spinner);

        android.widget.Button btnNewFolder = new android.widget.Button(requireContext());
        btnNewFolder.setText("+ Folder");
        btnNewFolder.setTextSize(12);
        android.widget.LinearLayout.LayoutParams btnLp = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(16, 0, 0, 0);
        btnNewFolder.setLayoutParams(btnLp);
        spinnerRow.addView(btnNewFolder);

        layout.addView(spinnerRow);

        android.widget.CheckBox checkBox = new android.widget.CheckBox(requireContext());
        checkBox.setText("Remember my folder choice");
        checkBox.setPadding(0, 24, 0, 0);
        layout.addView(checkBox);

        java.util.List<Folder> spinnerItems = new java.util.ArrayList<>();
        spinnerItems.add(new Folder(0L, "None (Uncategorized)"));
        spinnerItems.addAll(folders);

        android.widget.ArrayAdapter<Folder> spinnerAdapter = new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, spinnerItems);
        spinner.setAdapter(spinnerAdapter);
        spinner.setSelection(0);

        btnNewFolder.setOnClickListener(v -> {
            android.widget.EditText input = new android.widget.EditText(requireContext());
            input.setHint("Folder Name");
            input.setSingleLine();

            new AlertDialog.Builder(requireContext())
                .setTitle("Create Folder")
                .setView(input)
                .setPositiveButton("Create", (dialog1, which1) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        android.widget.Toast.makeText(requireContext(), "Folder name cannot be empty", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long id = db.createFolder(name);
                    if (id == -1) {
                        android.widget.Toast.makeText(requireContext(), "Folder with this name already exists", android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        List<Folder> updated = db.getFolders();
                        spinnerItems.clear();
                        spinnerItems.add(new Folder(0L, "None (Uncategorized)"));
                        spinnerItems.addAll(updated);
                        spinnerAdapter.notifyDataSetChanged();
                        // Select the newly created folder
                        for (int i = 0; i < spinnerItems.size(); i++) {
                            if (spinnerItems.get(i).getId() == id) {
                                spinner.setSelection(i);
                                break;
                            }
                        }
                        android.widget.Toast.makeText(requireContext(), "Folder created", android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        new AlertDialog.Builder(requireContext())
            .setTitle("Save to Folder")
            .setView(layout)
            .setPositiveButton("Save", (dialog, which) -> {
                Folder selectedFolder = (Folder) spinner.getSelectedItem();
                if (selectedFolder != null) {
                    db.setTransactionFolder(t.getId(), selectedFolder.getId());
                    db.toggleStar(t.getId(), true);
                    if (checkBox.isChecked()) {
                        prefs.edit()
                            .putBoolean("pref_remember_folder", true)
                            .putLong("pref_remembered_folder_id", selectedFolder.getId())
                            .apply();
                    }
                    android.widget.Toast.makeText(requireContext(), "Saved to \"" + selectedFolder.getName() + "\"", android.widget.Toast.LENGTH_SHORT).show();
                    load();
                } else {
                    android.widget.Toast.makeText(requireContext(), "Please select or create a folder first", android.widget.Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showSaveDialog(Transaction t) {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
        DatabaseHelper db = DatabaseHelper.getInstance(requireContext());
        List<Folder> folders = db.getFolders();

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        android.widget.LinearLayout spinnerRow = new android.widget.LinearLayout(requireContext());
        spinnerRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        spinnerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        android.widget.Spinner spinner = new android.widget.Spinner(requireContext());
        android.widget.LinearLayout.LayoutParams spinnerLp = new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        spinner.setLayoutParams(spinnerLp);
        spinnerRow.addView(spinner);

        android.widget.Button btnNewFolder = new android.widget.Button(requireContext());
        btnNewFolder.setText("+ Folder");
        btnNewFolder.setTextSize(12);
        android.widget.LinearLayout.LayoutParams btnLp = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(16, 0, 0, 0);
        btnNewFolder.setLayoutParams(btnLp);
        spinnerRow.addView(btnNewFolder);

        layout.addView(spinnerRow);

        java.util.List<Folder> spinnerItems = new java.util.ArrayList<>();
        spinnerItems.add(new Folder(0L, "None (Uncategorized)"));
        spinnerItems.addAll(folders);

        android.widget.ArrayAdapter<Folder> spinnerAdapter = new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, spinnerItems);
        spinner.setAdapter(spinnerAdapter);

        int selectionIndex = 0;
        for (int i = 0; i < spinnerItems.size(); i++) {
            if (spinnerItems.get(i).getId() == t.getFolderId()) {
                selectionIndex = i;
                break;
            }
        }
        spinner.setSelection(selectionIndex);

        btnNewFolder.setOnClickListener(v -> {
            android.widget.EditText input = new android.widget.EditText(requireContext());
            input.setHint("Folder Name");
            input.setSingleLine();

            new AlertDialog.Builder(requireContext())
                .setTitle("Create Folder")
                .setView(input)
                .setPositiveButton("Create", (dialog1, which1) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        android.widget.Toast.makeText(requireContext(), "Folder name cannot be empty", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long id = db.createFolder(name);
                    if (id == -1) {
                        android.widget.Toast.makeText(requireContext(), "Folder with this name already exists", android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        List<Folder> updated = db.getFolders();
                        spinnerItems.clear();
                        spinnerItems.add(new Folder(0L, "None (Uncategorized)"));
                        spinnerItems.addAll(updated);
                        spinnerAdapter.notifyDataSetChanged();
                        for (int i = 0; i < spinnerItems.size(); i++) {
                            if (spinnerItems.get(i).getId() == id) {
                                spinner.setSelection(i);
                                break;
                            }
                        }
                        android.widget.Toast.makeText(requireContext(), "Folder created", android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        new AlertDialog.Builder(requireContext())
            .setTitle("Save Options")
            .setView(layout)
            .setPositiveButton("Save", (dialog, which) -> {
                Folder selectedFolder = (Folder) spinner.getSelectedItem();
                if (selectedFolder != null) {
                    db.setTransactionFolder(t.getId(), selectedFolder.getId());
                    db.toggleStar(t.getId(), true);
                    load();
                    android.widget.Toast.makeText(requireContext(), "Transaction saved", android.widget.Toast.LENGTH_SHORT).show();
                }
            })
            .setNeutralButton("Unsave", (dialog, which) -> {
                db.setTransactionFolder(t.getId(), 0L);
                db.toggleStar(t.getId(), false);
                load();
                android.widget.Toast.makeText(requireContext(), "Transaction unsaved", android.widget.Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new GroupedTransactionAdapter(
            t -> {
                DatabaseHelper.getInstance(requireContext()).markCompleted(t.getId());
                load();
            },
            t -> {
                new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Transaction")
                    .setMessage("Delete \"" + t.getTitle() + "\"?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        DatabaseHelper.getInstance(requireContext()).deleteTransaction(t.getId());
                        load();
                        Snackbar.make(requireActivity().findViewById(R.id.main), t.getTitle() + " deleted", 5000)
                            .setAction("Undo", v -> {
                                DatabaseHelper.getInstance(requireContext()).addTransaction(t);
                                load();
                            }).show();
                    })
                    .setNegativeButton("Cancel", null).show();
            },
            (t, starred) -> {
                DatabaseHelper.getInstance(requireContext()).toggleStar(t.getId(), starred);
                load();
            },
            (t, which) -> {
                if (which == 0) {
                    if (getActivity() instanceof MainActivity) {
                        Intent intent = new Intent(requireContext(), AddTransactionActivity.class);
                        intent.putExtra("edit_id", t.getId());
                        intent.putExtra("edit_title", t.getTitle());
                        intent.putExtra("edit_category", t.getCategory());
                        intent.putExtra("edit_amount", t.getAmount());
                        intent.putExtra("edit_type", t.getType());
                        intent.putExtra("edit_note", t.getNote());
                        intent.putExtra("edit_date", t.getDate());
                        ((MainActivity) getActivity()).launchAddTransaction(intent);
                    }
                } else if (which == 1) {
                    new AlertDialog.Builder(requireContext())
                        .setTitle("Delete Transaction")
                        .setMessage("Delete \"" + t.getTitle() + "\"?")
                        .setPositiveButton("Delete", (dialog, whichButton) -> {
                            DatabaseHelper.getInstance(requireContext()).deleteTransaction(t.getId());
                            load();
                            Snackbar.make(requireActivity().findViewById(R.id.main), t.getTitle() + " deleted", 5000)
                                .setAction("Undo", v -> {
                                    DatabaseHelper.getInstance(requireContext()).addTransaction(t);
                                    load();
                                }).show();
                        })
                        .setNegativeButton("Cancel", null).show();
                } else if (which == 2) {
                    showSaveDialog(t);
                }
            },
            t -> {
                handleTransactionDoubleClick(t);
            }
        );
        ((RecyclerView) view).setAdapter(adapter);

        // Apply any pending multi-select mode that was requested before adapter was ready
        if (pendingMultiSelectFolderId >= 0) {
            long folderId = pendingMultiSelectFolderId;
            pendingMultiSelectFolderId = -1L;
            enterMultiSelectMode(folderId);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    public void setFilter(String filter) {
        this.activeFilter = filter;
        if (isAdded()) {
            load();
        }
    }

    public void setSearch(String query) {
        this.searchQuery = query;
        if (isAdded()) {
            load();
        }
    }

    public void setSearchField(String field) {
        this.searchField = field;
        if (isAdded()) {
            load();
        }
    }

    public void setDateFilter(int year, int month, int day) {
        this.selectedDate = new int[]{year, month, day};
        if (isAdded()) {
            load();
        }
    }

    public void clearDateFilter() {
        this.selectedDate = null;
        if (isAdded()) {
            load();
        }
    }

    /** Called from MainActivity when the folder-plus button sends us here in multi-select mode */
    public void enterMultiSelectMode(long folderId) {
        if (adapter == null) {
            // Fragment not yet attached — queue it
            pendingMultiSelectFolderId = folderId;
            return;
        }
        inMultiSelectMode = true;
        multiSelectTargetFolderId = folderId;
        adapter.enableMultiSelect(selectedIds -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateMultiSelectCount(selectedIds.size());
            }
        });
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showMultiSelectBar(folderId);
        }
    }

    public void exitMultiSelectMode() {
        if (adapter == null) return;
        inMultiSelectMode = false;
        multiSelectTargetFolderId = -1L;
        adapter.disableMultiSelect();
    }

    /** Commits all selected transactions to the target folder. Called by MainActivity on Done. */
    public void commitMultiSelect() {
        if (adapter == null || multiSelectTargetFolderId < 0) return;
        java.util.Set<Long> ids = adapter.getSelectedIds();
        DatabaseHelper db = DatabaseHelper.getInstance(requireContext());
        for (long id : ids) {
            db.setTransactionFolder(id, multiSelectTargetFolderId);
            db.toggleStar(id, true);
        }
        int count = ids.size();
        exitMultiSelectMode();
        load();
        android.widget.Toast.makeText(requireContext(),
            count + " transaction" + (count == 1 ? "" : "s") + " saved to folder",
            android.widget.Toast.LENGTH_SHORT).show();
    }

    public void load() {
        if (!isAdded() || adapter == null) return;
        
        long start;
        long end;
        if (selectedDate != null) {
            Calendar s = Calendar.getInstance();
            s.set(selectedDate[0], selectedDate[1], selectedDate[2], 0, 0, 0);
            s.set(Calendar.MILLISECOND, 0);
            Calendar e = Calendar.getInstance();
            e.set(selectedDate[0], selectedDate[1], selectedDate[2], 23, 59, 59);
            e.set(Calendar.MILLISECOND, 999);
            start = s.getTimeInMillis();
            end = e.getTimeInMillis();
        } else {
            Calendar s = Calendar.getInstance();
            s.set(2000, 0, 1, 0, 0, 0);
            s.set(Calendar.MILLISECOND, 0);
            Calendar e = Calendar.getInstance();
            e.set(2100, 0, 1, 0, 0, 0);
            e.set(Calendar.MILLISECOND, 0);
            start = s.getTimeInMillis();
            end = e.getTimeInMillis();
        }

        List<Transaction> all = DatabaseHelper.getInstance(requireContext()).getTransactions(start, end);

        List<Transaction> filteredByFilter = new ArrayList<>();
        if ("expense".equals(activeFilter)) {
            for (Transaction t : all) {
                if ("expense".equalsIgnoreCase(t.getType())) filteredByFilter.add(t);
            }
        } else if ("income".equals(activeFilter)) {
            for (Transaction t : all) {
                if ("income".equalsIgnoreCase(t.getType())) filteredByFilter.add(t);
            }
        } else if ("debts".equals(activeFilter)) {
            for (Transaction t : all) {
                if ("togive".equalsIgnoreCase(t.getType()) || "toget".equalsIgnoreCase(t.getType())) {
                    filteredByFilter.add(t);
                }
            }
        } else {
            filteredByFilter.addAll(all);
        }

        List<Transaction> filtered = new ArrayList<>();
        String q = searchQuery.trim().toLowerCase(Locale.US);
        if (!q.isEmpty()) {
            for (Transaction t : filteredByFilter) {
                boolean matches = false;
                switch (searchField) {
                    case "name":
                        matches = t.getTitle().toLowerCase(Locale.US).contains(q);
                        break;
                    case "category":
                        matches = t.getCategory().toLowerCase(Locale.US).contains(q);
                        break;
                    case "amount":
                        matches = String.valueOf(t.getAmount()).contains(q);
                        break;
                    case "date":
                        matches = dateFmt.format(new Date(t.getDate())).toLowerCase(Locale.US).contains(q);
                        break;
                    default:
                        matches = t.getTitle().toLowerCase(Locale.US).contains(q) ||
                                  t.getCategory().toLowerCase(Locale.US).contains(q) ||
                                  t.getNote().toLowerCase(Locale.US).contains(q) ||
                                  String.valueOf(t.getAmount()).contains(q) ||
                                  dateFmt.format(new Date(t.getDate())).toLowerCase(Locale.US).contains(q);
                        break;
                }
                if (matches) {
                    filtered.add(t);
                }
            }
        } else {
            filtered.addAll(filteredByFilter);
        }

        adapter.submitList(filtered);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateSummary(all);
        }
    }
}
