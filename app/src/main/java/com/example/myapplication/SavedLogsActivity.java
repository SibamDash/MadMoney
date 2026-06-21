package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class SavedLogsActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private FolderAdapter folderAdapter;
    private TransactionAdapter transactionAdapter;
    private final List<Folder> folders = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private TextView tvNoFolders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_saved_logs);

        db = DatabaseHelper.getInstance(this);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        View logo = findViewById(R.id.logo);
        if (logo != null) {
            logo.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }
        tvNoFolders = findViewById(R.id.tvNoFolders);

        findViewById(R.id.btnCreateFolder).setOnClickListener(v -> showCreateFolderDialog());

        // Set up Folders list
        RecyclerView rvFolders = findViewById(R.id.rvFolders);
        rvFolders.setLayoutManager(new LinearLayoutManager(this));
        folderAdapter = new FolderAdapter(
            folders,
            folder -> {
                Intent intent = new Intent(this, FolderDetailActivity.class);
                intent.putExtra("folder_id", folder.getId());
                intent.putExtra("folder_name", folder.getName());
                startActivity(intent);
            },
            folder -> showFolderOptionsPopup(rvFolders.findViewById(R.id.tvFolderName), folder)
        );
        rvFolders.setAdapter(folderAdapter);

        // Set up Starred transactions list
        transactionAdapter = new TransactionAdapter(
            transactions,
            t -> {
                db.markCompleted(t.getId());
                loadStarredAndFolders();
            },
            t -> {
                db.deleteTransaction(t.getId());
                loadStarredAndFolders();
                Toast.makeText(this, t.getTitle() + " deleted", Toast.LENGTH_SHORT).show();
            },
            (t, starred) -> {
                db.toggleStar(t.getId(), starred);
                loadStarredAndFolders();
            },
            t -> {
                final long oldFolderId = t.getFolderId();
                final boolean oldStarred = t.isStarred();
                db.setTransactionFolder(t.getId(), 0L);
                db.toggleStar(t.getId(), false);
                loadStarredAndFolders();
                com.google.android.material.snackbar.Snackbar.make(findViewById(R.id.root), "Transaction unsaved", 5000)
                    .setAction("Undo", v -> {
                        db.setTransactionFolder(t.getId(), oldFolderId);
                        db.toggleStar(t.getId(), oldStarred);
                        loadStarredAndFolders();
                    }).show();
            },
            this::handleTransactionLongClick
        );

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(transactionAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStarredAndFolders();
    }

    private void loadStarredAndFolders() {
        folders.clear();
        folders.addAll(db.getFolders());
        folderAdapter.notifyDataSetChanged();
        tvNoFolders.setVisibility(folders.isEmpty() ? View.VISIBLE : View.GONE);

        transactions.clear();
        transactions.addAll(db.getUncategorizedStarredTransactions());
        transactionAdapter.notifyDataSetChanged();
    }

    private void showCreateFolderDialog() {
        EditText input = new EditText(this);
        input.setHint("Folder Name");
        input.setSingleLine();

        new AlertDialog.Builder(this)
            .setTitle("Create Folder")
            .setView(input)
            .setPositiveButton("Create", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(this, "Folder name cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                long id = db.createFolder(name);
                if (id == -1) {
                    Toast.makeText(this, "Folder with this name already exists", Toast.LENGTH_SHORT).show();
                } else {
                    loadStarredAndFolders();
                    Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showFolderOptionsPopup(View anchor, Folder folder) {
        PopupMenu popup = new PopupMenu(this, anchor != null ? anchor : findViewById(R.id.btnCreateFolder));
        popup.getMenu().add("Delete Folder");
        popup.setOnMenuItemClickListener(item -> {
            if ("Delete Folder".equals(item.getTitle().toString())) {
                new AlertDialog.Builder(this)
                    .setTitle("Delete Folder")
                    .setMessage("Delete folder \"" + folder.getName() + "\"? Transactions inside will be kept but uncategorized.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        db.deleteFolder(folder.getId());
                        loadStarredAndFolders();
                        Toast.makeText(this, "Folder deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
            return true;
        });
        popup.show();
    }

    private void showMoveTransactionDialog(Transaction t) {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", Context.MODE_PRIVATE);
        boolean remember = prefs.getBoolean("pref_remember_folder", false);
        long rememberedFolderId = prefs.getLong("pref_remembered_folder_id", 0L);

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

        CheckBox checkBox = new CheckBox(this);
        checkBox.setText("Remember my folder choice for future double-taps");
        checkBox.setPadding(0, 24, 0, 0);
        checkBox.setChecked(remember && rememberedFolderId == t.getFolderId());
        layout.addView(checkBox);

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
                        db.toggleStar(t.getId(), true); // keep it starred but uncategorized
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
                    loadStarredAndFolders();
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
                    loadStarredAndFolders();
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
                    loadStarredAndFolders();
                    Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show();
                }
            })
            .setNeutralButton("Unsave", (dialog, which) -> {
                db.setTransactionFolder(t.getId(), 0L);
                db.toggleStar(t.getId(), false);
                loadStarredAndFolders();
                Toast.makeText(this, "Transaction unsaved", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
