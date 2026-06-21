package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class BarChartView extends View {

    public static class MonthData {
        private final String label;
        private final float income;
        private final float expense;

        public MonthData(String label, float income, float expense) {
            this.label = label;
            this.income = income;
            this.expense = expense;
        }

        public String getLabel() { return label; }
        public float getIncome() { return income; }
        public float getExpense() { return expense; }
    }

    private List<MonthData> data = new ArrayList<>();
    private float budgetLimit = 0f;

    private final Paint paintIncome = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintExpense = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintYLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLimit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLimitLabel = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarChartView(Context context) {
        this(context, null);
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paintIncome.setStyle(Paint.Style.FILL);
        paintExpense.setStyle(Paint.Style.FILL);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setTextSize(26f);
        paintYLabel.setTextAlign(Paint.Align.RIGHT);
        paintYLabel.setTextSize(24f);
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(1f);
        paintLimit.setStyle(Paint.Style.STROKE);
        paintLimit.setStrokeWidth(3f);
        paintLimit.setPathEffect(new DashPathEffect(new float[]{20f, 10f}, 0f));
        paintLimitLabel.setTextSize(24f);
        paintLimitLabel.setTextAlign(Paint.Align.LEFT);
    }

    public void setData(List<MonthData> months) {
        if (months != null) {
            this.data = months;
        } else {
            this.data = new ArrayList<>();
        }
        invalidate();
    }

    public void setBudgetLimit(float limit) {
        this.budgetLimit = limit;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null || data.isEmpty()) return;

        paintIncome.setColor(ContextCompat.getColor(getContext(), R.color.color_income));
        paintExpense.setColor(ContextCompat.getColor(getContext(), R.color.color_expense));
        paintText.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        paintYLabel.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        paintGrid.setColor(ContextCompat.getColor(getContext(), R.color.divider));
        paintLimit.setColor(ContextCompat.getColor(getContext(), R.color.color_expense));
        paintLimitLabel.setColor(ContextCompat.getColor(getContext(), R.color.color_expense));

        float yAxisWidth = 72f;
        float paddingRight = 16f;
        float paddingTop = 40f;
        float labelHeight = 40f;
        float chartBottom = getHeight() - labelHeight;
        float chartHeight = chartBottom - paddingTop;

        float maxVal = 1f;
        float tempMax = 0f;
        for (MonthData m : data) {
            float val = Math.max(m.getIncome(), m.getExpense());
            if (val > tempMax) tempMax = val;
        }
        if (tempMax > 0f) {
            maxVal = tempMax;
        }
        if (budgetLimit > 0f) {
            maxVal = Math.max(maxVal, budgetLimit * 1.2f);
        }

        // Y-axis grid lines + labels (4 lines)
        int steps = 4;
        for (int i = 1; i <= steps; i++) {
            float fraction = (float) i / steps;
            float y = paddingTop + chartHeight * (1f - fraction);
            canvas.drawLine(yAxisWidth, y, getWidth() - paddingRight, y, paintGrid);
            int value = (int) (maxVal * fraction);
            String label = value >= 1000 ? "₹" + (value / 1000) + "k" : "₹" + value;
            canvas.drawText(label, yAxisWidth - 6f, y + paintYLabel.getTextSize() / 3f, paintYLabel);
        }

        float totalWidth = getWidth() - yAxisWidth - paddingRight;
        float groupWidth = totalWidth / data.size();
        float barWidth = groupWidth * 0.3f;
        float gap = groupWidth * 0.05f;

        for (int i = 0; i < data.size(); i++) {
            MonthData month = data.get(i);
            float groupLeft = yAxisWidth + i * groupWidth;
            float centerX = groupLeft + groupWidth / 2f;

            float incomeH = (month.getIncome() / maxVal) * chartHeight;
            float incomeLeft = centerX - barWidth - gap / 2f;
            canvas.drawRoundRect(
                new RectF(incomeLeft, chartBottom - incomeH, incomeLeft + barWidth, chartBottom),
                6f, 6f, paintIncome
            );

            float expenseH = (month.getExpense() / maxVal) * chartHeight;
            float expenseLeft = centerX + gap / 2f;
            canvas.drawRoundRect(
                new RectF(expenseLeft, chartBottom - expenseH, expenseLeft + barWidth, chartBottom),
                6f, 6f, paintExpense
            );

            canvas.drawText(month.getLabel(), centerX, getHeight() - 8f, paintText);
        }

        // Budget limit line
        if (budgetLimit > 0f) {
            float limitY = paddingTop + chartHeight * (1f - budgetLimit / maxVal);
            canvas.drawLine(yAxisWidth, limitY, getWidth() - paddingRight, limitY, paintLimit);
            String limitLabel = "Max Budget " + (budgetLimit >= 1000 ? "₹" + ((int) (budgetLimit / 1000)) + "k" : "₹" + ((int) budgetLimit));
            canvas.drawText(limitLabel, yAxisWidth + 4f, limitY - 8f, paintLimitLabel);
        }
    }
}
