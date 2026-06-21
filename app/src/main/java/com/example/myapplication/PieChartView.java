package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class PieChartView extends View {

    private final List<Pair<Float, Integer>> slices = new ArrayList<>();
    private String centerLabel = "";

    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PieChartView(Context context) {
        this(context, null);
    }

    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        slicePaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(3f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(42f);
        labelPaint.setFakeBoldText(true);
        subLabelPaint.setTextAlign(Paint.Align.CENTER);
        subLabelPaint.setTextSize(28f);
    }

    public void setData(List<Pair<Float, Integer>> data, String center) {
        slices.clear();
        if (data != null) {
            slices.addAll(data);
        }
        centerLabel = center != null ? center : "";
        invalidate();
    }

    public void setData(List<Pair<Float, Integer>> data) {
        setData(data, "");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (slices.isEmpty()) return;

        holePaint.setColor(ContextCompat.getColor(getContext(), R.color.background));
        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        subLabelPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        strokePaint.setColor(ContextCompat.getColor(getContext(), R.color.background));

        float size = Math.min(getWidth(), getHeight());
        float margin = size * 0.04f;
        RectF oval = new RectF(margin, margin, size - margin, size - margin);
        float cx = size / 2f;
        float cy = size / 2f;
        float radius = size / 2f - margin;
        float holeR = radius * 0.52f; // donut hole

        float startAngle = -90f;
        for (Pair<Float, Integer> slice : slices) {
            slicePaint.setColor(slice.second);
            canvas.drawArc(oval, startAngle, slice.first, true, slicePaint);
            canvas.drawArc(oval, startAngle, slice.first, true, strokePaint);
            startAngle += slice.first;
        }

        // punch out center hole
        canvas.drawCircle(cx, cy, holeR, holePaint);

        // center text
        if (centerLabel != null && !centerLabel.isEmpty()) {
            float textY = cy - (labelPaint.descent() + labelPaint.ascent()) / 2f;
            canvas.drawText(centerLabel, cx, textY - 10f, labelPaint);
            canvas.drawText("total", cx, textY + 30f, subLabelPaint);
        }
    }
}
