package com.example.myapplication;

public abstract class Row {
    private Row() {}

    public static class Header extends Row {
        private final String dateKey;
        private final double total;

        public Header(String dateKey, double total) {
            this.dateKey = dateKey;
            this.total = total;
        }

        public String getDateKey() { return dateKey; }
        public double getTotal() { return total; }
    }

    public static class Item extends Row {
        private final Transaction transaction;

        public Item(Transaction transaction) {
            this.transaction = transaction;
        }

        public Transaction getTransaction() { return transaction; }
    }
}
