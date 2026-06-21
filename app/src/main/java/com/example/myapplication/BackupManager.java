package com.example.myapplication;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class BackupManager {

    public interface SuccessCallback<T> {
        void call(T value);
    }

    public interface FailureCallback {
        void call(Throwable exception);
    }

    public static class Result<T> {
        private final T value;
        private final Throwable exception;

        private Result(T value, Throwable exception) {
            this.value = value;
            this.exception = exception;
        }

        public static <T> Result<T> success(T value) {
            return new Result<>(value, null);
        }

        public static <T> Result<T> failure(Throwable exception) {
            return new Result<>(null, exception);
        }

        public boolean isSuccess() {
            return exception == null;
        }

        public T getValue() {
            return value;
        }

        public Throwable getException() {
            return exception;
        }

        public void fold(SuccessCallback<T> onSuccess, FailureCallback onFailure) {
            if (isSuccess()) {
                onSuccess.call(value);
            } else {
                onFailure.call(exception);
            }
        }
    }

    public static Result<Integer> exportToJson(Context context, Uri uri) {
        try {
            DatabaseHelper db = DatabaseHelper.invoke(context);
            List<Transaction> all = db.getTransactions(0L, Long.MAX_VALUE);
            JSONArray array = new JSONArray();
            for (Transaction t : all) {
                JSONObject obj = new JSONObject();
                obj.put("id", t.getId());
                obj.put("title", t.getTitle());
                obj.put("type", t.getType());
                obj.put("amount", t.getAmount());
                obj.put("category", t.getCategory());
                obj.put("account", t.getAccount());
                obj.put("note", t.getNote());
                obj.put("description", t.getDescription());
                obj.put("date", t.getDate());
                obj.put("isCompleted", t.isCompleted());
                obj.put("isStarred", t.isStarred());
                array.put(obj);
            }

            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(array.toString(2).getBytes(StandardCharsets.UTF_8));
                }
            }
            return Result.success(all.size());
        } catch (Throwable e) {
            return Result.failure(e);
        }
    }

    public static Result<Integer> importFromJson(Context context, Uri uri) {
        try {
            StringBuilder sb = new StringBuilder();
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                    }
                }
            }

            JSONArray array = new JSONArray(sb.toString());
            DatabaseHelper db = DatabaseHelper.invoke(context);
            int count = 0;
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                db.addTransaction(new Transaction(
                    o.getString("title"),
                    o.getString("type"),
                    o.getDouble("amount"),
                    o.getString("category"),
                    o.optString("account", ""),
                    o.optString("note", ""),
                    o.optString("description", ""),
                    o.getLong("date"),
                    o.optBoolean("isCompleted", false),
                    o.optBoolean("isStarred", false)
                ));
                count++;
            }
            return Result.success(count);
        } catch (Throwable e) {
            return Result.failure(e);
        }
    }
}
