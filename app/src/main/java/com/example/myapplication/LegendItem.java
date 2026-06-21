package com.example.myapplication;

public class LegendItem {
    private final String label;
    private final double amount;
    private final int color;

    public LegendItem(String label, double amount, int color) {
        this.label = label != null ? label : "";
        this.amount = amount;
        this.color = color;
    }

    public String getLabel() { return label; }
    public double getAmount() { return amount; }
    public int getColor() { return color; }
}
