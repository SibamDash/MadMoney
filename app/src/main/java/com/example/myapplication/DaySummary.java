package com.example.myapplication;

public class DaySummary {
    private double income;
    private double expense;
    private double debt;

    public DaySummary() {
        this.income = 0.0;
        this.expense = 0.0;
        this.debt = 0.0;
    }

    public DaySummary(double income, double expense, double debt) {
        this.income = income;
        this.expense = expense;
        this.debt = debt;
    }

    public double getIncome() { return income; }
    public void setIncome(double income) { this.income = income; }

    public double getExpense() { return expense; }
    public void setExpense(double expense) { this.expense = expense; }

    public double getDebt() { return debt; }
    public void setDebt(double debt) { this.debt = debt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DaySummary that = (DaySummary) o;
        if (Double.compare(that.income, income) != 0) return false;
        if (Double.compare(that.expense, expense) != 0) return false;
        return Double.compare(that.debt, debt) == 0;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(income);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(expense);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(debt);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
