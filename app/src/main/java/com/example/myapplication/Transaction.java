package com.example.myapplication;

public class Transaction {
    private final long id;
    private final String title;
    private final String category;
    private final double amount;
    private final String type;
    private final String account;
    private final String note;
    private final String description;
    private final long date;
    private final boolean isCompleted;
    private final long completedAt;
    private final boolean isStarred;
    private final long folderId;

    // All-arguments constructor
    public Transaction(long id, String title, String category, double amount, String type, 
                       String account, String note, String description, long date, 
                       boolean isCompleted, long completedAt, boolean isStarred, long folderId) {
        this.id = id;
        this.title = title != null ? title : "";
        this.category = category != null ? category : "";
        this.amount = amount;
        this.type = type != null ? type : "";
        this.account = account != null ? account : "";
        this.note = note != null ? note : "";
        this.description = description != null ? description : "";
        this.date = date;
        this.isCompleted = isCompleted;
        this.completedAt = completedAt;
        this.isStarred = isStarred;
        this.folderId = folderId;
    }

    public Transaction(long id, String title, String category, double amount, String type, 
                       String account, String note, String description, long date, 
                       boolean isCompleted, long completedAt, boolean isStarred) {
        this(id, title, category, amount, type, account, note, description, date, isCompleted, completedAt, isStarred, 0L);
    }

    // Constructor with defaults for optional values (similar to kotlin default parameters)
    public Transaction(String title, String category, double amount, String type, 
                       String account, String note, String description, long date, 
                       boolean isCompleted, boolean isStarred) {
        this(0L, title, category, amount, type, account, note, description, date, isCompleted, 0L, isStarred, 0L);
    }

    // Constructor used in AddTransactionActivity (adds defaults for id, account, description, isCompleted, completedAt, isStarred)
    public Transaction(long id, String title, String category, double amount, String type, String note, long date) {
        this(id, title, category, amount, type, "", note, "", date, false, 0L, false, 0L);
    }

    public Transaction(String title, String category, double amount, String type, String note, long date) {
        this(0L, title, category, amount, type, "", note, "", date, false, 0L, false, 0L);
    }

    // Getters
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public double getAmount() { return amount; }
    public String getType() { return type; }
    public String getAccount() { return account; }
    public String getNote() { return note; }
    public String getDescription() { return description; }
    public long getDate() { return date; }
    public boolean isCompleted() { return isCompleted; }
    public long getCompletedAt() { return completedAt; }
    public boolean isStarred() { return isStarred; }
    public long getFolderId() { return folderId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        if (id != that.id) return false;
        if (Double.compare(that.amount, amount) != 0) return false;
        if (date != that.date) return false;
        if (isCompleted != that.isCompleted) return false;
        if (completedAt != that.completedAt) return false;
        if (isStarred != that.isStarred) return false;
        if (folderId != that.folderId) return false;
        if (!title.equals(that.title)) return false;
        if (!category.equals(that.category)) return false;
        if (!type.equals(that.type)) return false;
        if (!account.equals(that.account)) return false;
        if (!note.equals(that.note)) return false;
        return description.equals(that.description);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (int) (id ^ (id >>> 32));
        result = 31 * result + title.hashCode();
        result = 31 * result + category.hashCode();
        temp = Double.doubleToLongBits(amount);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + type.hashCode();
        result = 31 * result + account.hashCode();
        result = 31 * result + note.hashCode();
        result = 31 * result + description.hashCode();
        result = 31 * result + (int) (date ^ (date >>> 32));
        result = 31 * result + (isCompleted ? 1 : 0);
        result = 31 * result + (int) (completedAt ^ (completedAt >>> 32));
        result = 31 * result + (isStarred ? 1 : 0);
        result = 31 * result + (int) (folderId ^ (folderId >>> 32));
        return result;
    }
}
