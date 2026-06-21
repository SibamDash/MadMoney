package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static volatile DatabaseHelper instance = null;

    private DatabaseHelper(Context context) {
        super(context.getApplicationContext(), "transactions.db", null, 5);
    }

    public static DatabaseHelper invoke(Context context) {
        if (instance == null) {
            synchronized (DatabaseHelper.class) {
                if (instance == null) {
                    instance = new DatabaseHelper(context);
                }
            }
        }
        return instance;
    }

    public static DatabaseHelper getInstance(Context context) {
        return invoke(context);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE transactions (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    title TEXT NOT NULL," +
            "    type TEXT NOT NULL," +
            "    amount REAL NOT NULL," +
            "    category TEXT NOT NULL," +
            "    account TEXT NOT NULL," +
            "    note TEXT," +
            "    description TEXT," +
            "    date INTEGER NOT NULL," +
            "    isCompleted INTEGER DEFAULT 0," +
            "    completedAt INTEGER DEFAULT 0," +
            "    isStarred INTEGER DEFAULT 0," +
            "    folder_id INTEGER DEFAULT 0" +
            ")"
        );
        db.execSQL(
            "CREATE TABLE folders (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    name TEXT NOT NULL UNIQUE" +
            ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN completedAt INTEGER DEFAULT 0");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN isStarred INTEGER DEFAULT 0");
        }
        if (oldVersion < 5) {
            db.execSQL("CREATE TABLE folders (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)");
            db.execSQL("ALTER TABLE transactions ADD COLUMN folder_id INTEGER DEFAULT 0");
        }
    }

    public long addTransaction(Transaction transaction) {
        ContentValues values = new ContentValues();
        values.put("title", transaction.getTitle());
        values.put("type", transaction.getType());
        values.put("amount", transaction.getAmount());
        values.put("category", transaction.getCategory());
        values.put("account", transaction.getAccount());
        values.put("note", transaction.getNote());
        values.put("description", transaction.getDescription());
        values.put("date", transaction.getDate());
        values.put("isCompleted", transaction.isCompleted() ? 1 : 0);
        values.put("completedAt", transaction.getCompletedAt());
        values.put("isStarred", transaction.isStarred() ? 1 : 0);
        values.put("folder_id", transaction.getFolderId());
        return getWritableDatabase().insert("transactions", null, values);
    }

    private static Transaction cursorToTransaction(Cursor cursor) {
        return new Transaction(
            cursor.getLong(0),
            cursor.getString(1),
            cursor.getString(4),
            cursor.getDouble(3),
            cursor.getString(2),
            cursor.getString(5),
            cursor.getString(6) != null ? cursor.getString(6) : "",
            cursor.getString(7) != null ? cursor.getString(7) : "",
            cursor.getLong(8),
            cursor.getInt(9) == 1,
            cursor.getLong(10),
            cursor.getInt(11) == 1,
            cursor.getColumnCount() > 12 ? cursor.getLong(12) : 0L
        );
    }

    public List<Transaction> getTransactions(long startDate, long endDate) {
        List<Transaction> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "transactions", null,
                "date BETWEEN ? AND ?",
                new String[]{String.valueOf(startDate), String.valueOf(endDate)},
                null, null, "date DESC"
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(cursorToTransaction(cursor));
                }
            }
        }
        return list;
    }

    public void markCompleted(long id) {
        ContentValues values = new ContentValues();
        values.put("isCompleted", 1);
        values.put("completedAt", System.currentTimeMillis());
        getWritableDatabase().update("transactions", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public List<String> getDebtPersonsSorted() {
        List<String> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT title, COUNT(*) as cnt, MAX(date) as last FROM transactions WHERE type IN ('togive','toget') GROUP BY title ORDER BY cnt DESC, last DESC",
                null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(cursor.getString(0));
                }
            }
        }
        return list;
    }

    public void deleteTransaction(long id) {
        getWritableDatabase().delete("transactions", "id = ?", new String[]{String.valueOf(id)});
    }

    public void updateTransaction(Transaction transaction) {
        ContentValues values = new ContentValues();
        values.put("title", transaction.getTitle());
        values.put("type", transaction.getType());
        values.put("amount", transaction.getAmount());
        values.put("category", transaction.getCategory());
        values.put("account", transaction.getAccount());
        values.put("note", transaction.getNote());
        values.put("description", transaction.getDescription());
        values.put("date", transaction.getDate());
        getWritableDatabase().update("transactions", values, "id = ?", new String[]{String.valueOf(transaction.getId())});
    }

    public void toggleStar(long id, boolean starred) {
        ContentValues values = new ContentValues();
        values.put("isStarred", starred ? 1 : 0);
        getWritableDatabase().update("transactions", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public List<Transaction> getStarredTransactions() {
        List<Transaction> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "transactions", null, "isStarred = 1", null, null, null, "date DESC"
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(cursorToTransaction(cursor));
                }
            }
        }
        return list;
    }

    // Folder helper operations
    public long createFolder(String name) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        try {
            return getWritableDatabase().insert("folders", null, values);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public List<Folder> getFolders() {
        List<Folder> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("folders", null, null, null, null, null, "name ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(new Folder(cursor.getLong(0), cursor.getString(1)));
                }
            }
        }
        return list;
    }

    public Folder getFolderById(long id) {
        try (Cursor cursor = getReadableDatabase().query("folders", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return new Folder(cursor.getLong(0), cursor.getString(1));
            }
        }
        return null;
    }

    public void deleteFolder(long folderId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("folder_id", 0);
            db.update("transactions", values, "folder_id = ?", new String[]{String.valueOf(folderId)});
            db.delete("folders", "id = ?", new String[]{String.valueOf(folderId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void setTransactionFolder(long transactionId, long folderId) {
        ContentValues values = new ContentValues();
        values.put("folder_id", folderId);
        if (folderId != 0) {
            values.put("isStarred", 1); // Automatically star if assigned to folder
        }
        getWritableDatabase().update("transactions", values, "id = ?", new String[]{String.valueOf(transactionId)});
    }

    public List<Transaction> getTransactionsInFolder(long folderId) {
        List<Transaction> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "transactions", null, "folder_id = ?", new String[]{String.valueOf(folderId)}, null, null, "date DESC"
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(cursorToTransaction(cursor));
                }
            }
        }
        return list;
    }

    public List<Transaction> getUncategorizedStarredTransactions() {
        List<Transaction> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "transactions", null, "isStarred = 1 AND folder_id = 0", null, null, null, "date DESC"
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(cursorToTransaction(cursor));
                }
            }
        }
        return list;
    }

    public List<Transaction> getAllTransactions() {
        List<Transaction> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "transactions", null, null, null, null, null, "date DESC"
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(cursorToTransaction(cursor));
                }
            }
        }
        return list;
    }
}
