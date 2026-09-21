package com.watchcollection.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class AccuracyChartView extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Double> values = new ArrayList<>();

    public AccuracyChartView(Context context) {
        super(context);
        grid.setColor(UiKit.LINE);
        grid.setStrokeWidth(UiKit.dp(context, 1));
        line.setColor(UiKit.NAVY);
        line.setStrokeWidth(UiKit.dp(context, 2));
        line.setStyle(Paint.Style.STROKE);
        point.setColor(UiKit.GOLD);
        point.setStyle(Paint.Style.FILL);
    }

    public void setValues(List<Double> newValues) {
        values = new ArrayList<>(newValues);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        int pad = UiKit.dp(getContext(), 16);
        float mid = h / 2f;
        canvas.drawLine(pad, mid, w - pad, mid, grid);
        if (values.size() < 2) return;

        double max = 1.0;
        for (double v : values) max = Math.max(max, Math.abs(v));
        float usableW = w - pad * 2f;
        float usableH = h - pad * 2f;
        float lastX = 0, lastY = 0;
        for (int i = 0; i < values.size(); i++) {
            float x = pad + usableW * i / (values.size() - 1f);
            float y = mid - (float) (values.get(i) / max) * (usableH / 2f);
            if (i > 0) canvas.drawLine(lastX, lastY, x, y, line);
            canvas.drawCircle(x, y, UiKit.dp(getContext(), 3), point);
            lastX = x;
            lastY = y;
        }
    }
}
