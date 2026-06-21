package com.example.myapplication;

import java.util.List;
import java.util.Objects;

public abstract class GroupedRow {
    private GroupedRow() {}

    public static class Header extends GroupedRow {
        private final String dateLabel;
        private final double dayTotal;
        private final List<String> types;

        public Header(String dateLabel, double dayTotal, List<String> types) {
            this.dateLabel = dateLabel;
            this.dayTotal = dayTotal;
            this.types = types;
        }

        public String getDateLabel() { return dateLabel; }
        public double getDayTotal() { return dayTotal; }
        public List<String> getTypes() { return types; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Header header = (Header) o;
            return Double.compare(header.dayTotal, dayTotal) == 0 &&
                    Objects.equals(dateLabel, header.dateLabel) &&
                    Objects.equals(types, header.types);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dateLabel, dayTotal, types);
        }
    }

    public static class Item extends GroupedRow {
        private final Transaction transaction;

        public Item(Transaction transaction) {
            this.transaction = transaction;
        }

        public Transaction getTransaction() { return transaction; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return Objects.equals(transaction, item.transaction);
        }

        @Override
        public int hashCode() {
            return Objects.hash(transaction);
        }
    }
}
