package com.example.myapplication;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.EditText;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class FolderDetailActivity extends AppCompatActivity {

    private long folderId;
    private String folderName;
    private DatabaseHelper db;
    private TransactionAdapter adapter;
    private final List<Transaction> transactions = new ArrayList<>();
    private TextView tvEmptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_folder_detail);

        View root = findViewById(R.id.root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        folderId = getIntent().getLongExtra("folder_id", -1);
        folderName = getIntent().getStringExtra("folder_name");
        db = DatabaseHelper.getInstance(this);

        if (folderId == -1 || folderName == null) {
            Toast.makeText(this, "Error: Folder not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView tvFolderName = findViewById(R.id.tvFolderName);
        tvFolderName.setText(folderName);

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        View logo = findViewById(R.id.logo);
        if (logo != null) {
            logo.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }

        findViewById(R.id.ivMoreOptions).setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add("Delete Folder");
            popup.setOnMenuItemClickListener(item -> {
                if ("Delete Folder".equals(item.getTitle().toString())) {
                    new AlertDialog.Builder(this)
                        .setTitle("Delete Folder")
                        .setMessage("Delete folder \"" + folderName + "\"? Transactions inside will be kept but uncategorized.")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            db.deleteFolder(folderId);
                            Toast.makeText(this, "Folder deleted", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                }
                return true;
            });
            popup.show();
        });

        tvEmptyState = findViewById(R.id.tvEmptyState);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TransactionAdapter(
            transactions,
            t -> {
                db.markCompleted(t.getId());
                loadTransactions();
            },
            t -> {
                // Remove transaction from folder instead of deleting it entirely from database,
                // or ask if they want to delete it from DB or remove from folder.
                // Standard behavior in folder: remove from folder.
                new AlertDialog.Builder(this)
                    .setTitle("Remove from Folder")
                    .setMessage("Remove \"" + t.getTitle() + "\" from this folder?")
                    .setPositiveButton("Remove", (dialog, which) -> {
                        db.setTransactionFolder(t.getId(), 0L);
                        loadTransactions();
                        Toast.makeText(this, "Removed from folder", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            },
            (t, starred) -> {
                db.toggleStar(t.getId(), starred);
                loadTransactions();
            },
            t -> {
                final long oldFolderId = t.getFolderId();
                final boolean oldStarred = t.isStarred();
                db.setTransactionFolder(t.getId(), 0L);
                db.toggleStar(t.getId(), false);
                loadTransactions();
                com.google.android.material.snackbar.Snackbar.make(findViewById(R.id.root), "Transaction unsaved", 5000)
                    .setAction("Undo", v -> {
                        db.setTransactionFolder(t.getId(), oldFolderId);
                        db.toggleStar(t.getId(), oldStarred);
                        loadTransactions();
                    }).show();
            },
            this::handleTransactionLongClick
        );

        recyclerView.setAdapter(adapter);

        View.OnClickListener launchMultiSelect = v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("multi_select_mode", true);
            intent.putExtra("target_folder_id", folderId);
            startActivity(intent);
        };
        findViewById(R.id.fabAdd).setOnClickListener(launchMultiSelect);
        findViewById(R.id.btnCreateNewTransaction).setOnClickListener(launchMultiSelect);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTransactions();
    }

    private void loadTransactions() {
        transactions.clear();
        transactions.addAll(db.getTransactionsInFolder(folderId));
        adapter.notifyDataSetChanged();
        tvEmptyState.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddTransactionDialog() {
        List<Transaction> all = db.getAllTransactions();
        List<Transaction> eligible = new ArrayList<>();
        for (Transaction t : all) {
            if (t.getFolderId() != folderId) {
                eligible.add(t);
            }
        }

        if (eligible.isEmpty()) {
            Toast.makeText(this, "No other transactions available to add", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] itemTexts = new String[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) {
            Transaction t = eligible.get(i);
            String title = t.getNote() != null && !t.getNote().trim().isEmpty() ? t.getNote() : t.getTitle();
            itemTexts[i] = title + " (₹" + (int) t.getAmount() + ")";
        }

        new AlertDialog.Builder(this)
            .setTitle("Add Transaction to Folder")
            .setItems(itemTexts, (dialog, which) -> {
                Transaction selected = eligible.get(which);
                db.setTransactionFolder(selected.getId(), folderId);
                loadTransactions();
                Toast.makeText(this, "Added to folder", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showMoveTransactionDialog(Transaction t) {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", Context.MODE_PRIVATE);
        boolean remember = prefs.getBoolean("pref_remember_folder", false);
        long rememberedFolderId = prefs.getLong("pref_remembered_folder_id", 0L);

        List<Folder> folders = db.getFolders();
        
        // Add a "None / Remove from folder" option at the beginning of the list
        List<Folder> spinnerItems = new ArrayList<>();
        spinnerItems.add(new Folder(0L, "None (Uncategorized)"));
        spinnerItems.addAll(folders);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        LinearLayout spinnerRow = new LinearLayout(this);
        spinnerRow.setOrientation(LinearLayout.HORIZONTAL);
        spinnerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        Spinner spinner = new Spinner(this);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        spinner.setLayoutParams(spinnerLp);
        spinnerRow.addView(spinner);

        android.widget.Button btnNewFolder = new android.widget.Button(this);
        btnNewFolder.setText("+ Folder");
        btnNewFolder.setTextSize(12);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(16, 0, 0, 0);
        btnNewFolder.setLayoutParams(btnLp);
        spinnerRow.addView(btnNewFolder);

        layout.addView(spinnerRow);

        CheckBox checkBox = new CheckBox(this);
        checkBox.setText("Remember my folder choice for future double-taps");
        checkBox.setPadding(0, 24, 0, 0);
        checkBox.setChecked(remember && rememberedFolderId == t.getFolderId());
        layout.addView(checkBox);

        ArrayAdapter<Folder> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinnerItems);
        spinner.setAdapter(spinnerAdapter);

        // Select current folder in spinner
        int selectionIndex = 0;
        for (int i = 0; i < spinnerItems.size(); i++) {
            if (spinnerItems.get(i).getId() == t.getFolderId()) {
                selectionIndex = i;
                break;
            }
        }
        spinner.setSelection(selectionIndex);

        btnNewFolder.setOnClickListener(v -> {
            android.widget.EditText input = new android.widget.EditText(this);
            input.setHint("Folder Name");
            input.setSingleLine();

            new AlertDialog.Builder(this)
                .setTitle("Create Folder")
                .setView(input)
                .setPositiveButton("Create", (dialog1, which1) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Folder name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long id = db.createFolder(name);
                    if (id == -1) {
                        Toast.makeText(this, "Folder with this name already exists", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        new AlertDialog.Builder(this)
            .setTitle("Change Folder")
            .setView(layout)
            .setPositiveButton("Save", (dialog, which) -> {
                Folder selectedFolder = (Folder) spinner.getSelectedItem();
                if (selectedFolder != null) {
                    db.setTransactionFolder(t.getId(), selectedFolder.getId());
                    if (selectedFolder.getId() == 0L) {
                        db.toggleStar(t.getId(), false); // Unstar if removed from folder
                    }
                    if (checkBox.isChecked() && selectedFolder.getId() > 0) {
                        prefs.edit()
                            .putBoolean("pref_remember_folder", true)
                            .putLong("pref_remembered_folder_id", selectedFolder.getId())
                            .apply();
                    } else if (!checkBox.isChecked() && remember && rememberedFolderId == selectedFolder.getId()) {
                        prefs.edit()
                            .putBoolean("pref_remember_folder", false)
                            .putLong("pref_remembered_folder_id", 0L)
                            .apply();
                    }
                    loadTransactions();
                    Toast.makeText(this, "Folder configuration updated", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void handleTransactionLongClick(Transaction t, int actionId) {
        if (actionId == 0) {
            Intent intent = new Intent(this, AddTransactionActivity.class);
            intent.putExtra("edit_id", t.getId());
            intent.putExtra("edit_title", t.getTitle());
            intent.putExtra("edit_category", t.getCategory());
            intent.putExtra("edit_amount", t.getAmount());
            intent.putExtra("edit_type", t.getType());
            intent.putExtra("edit_note", t.getNote());
            intent.putExtra("edit_date", t.getDate());
            startActivity(intent);
        } else if (actionId == 1) {
            new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Delete \"" + t.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialog, whichButton) -> {
                    db.deleteTransaction(t.getId());
                    loadTransactions();
                    Toast.makeText(this, t.getTitle() + " deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null).show();
        } else if (actionId == 2) {
            showSaveDialog(t);
        }
    }

    private void showSaveDialog(Transaction t) {
        List<Folder> spinnerItems = new ArrayList<>();
        spinnerItems.add(new Folder(0L, "None (Uncategorized)"));
        spinnerItems.addAll(db.getFolders());

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        LinearLayout spinnerRow = new LinearLayout(this);
        spinnerRow.setOrientation(LinearLayout.HORIZONTAL);
        spinnerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        Spinner spinner = new Spinner(this);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        spinner.setLayoutParams(spinnerLp);
        spinnerRow.addView(spinner);

        android.widget.Button btnNewFolder = new android.widget.Button(this);
        btnNewFolder.setText("+ Folder");
        btnNewFolder.setTextSize(12);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(16, 0, 0, 0);
        btnNewFolder.setLayoutParams(btnLp);
        spinnerRow.addView(btnNewFolder);

        layout.addView(spinnerRow);

        ArrayAdapter<Folder> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinnerItems);
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
            EditText input = new EditText(this);
            input.setHint("Folder Name");
            input.setSingleLine();

            new AlertDialog.Builder(this)
                .setTitle("Create Folder")
                .setView(input)
                .setPositiveButton("Create", (dialog1, which1) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Folder name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long id = db.createFolder(name);
                    if (id == -1) {
                        Toast.makeText(this, "Folder with this name already exists", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        new AlertDialog.Builder(this)
            .setTitle("Save Options")
            .setView(layout)
            .setPositiveButton("Save", (dialog, which) -> {
                Folder selectedFolder = (Folder) spinner.getSelectedItem();
                if (selectedFolder != null) {
                    db.setTransactionFolder(t.getId(), selectedFolder.getId());
                    db.toggleStar(t.getId(), true);
                    loadTransactions();
                    Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show();
                }
            })
            .setNeutralButton("Unsave", (dialog, which) -> {
                db.setTransactionFolder(t.getId(), 0L);
                db.toggleStar(t.getId(), false);
                loadTransactions();
                Toast.makeText(this, "Transaction unsaved", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
