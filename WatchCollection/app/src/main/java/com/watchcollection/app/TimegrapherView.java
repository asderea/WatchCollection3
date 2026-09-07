package com.watchcollection.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Watch Accuracy Meter / 종이식 timegrapher 느낌을 참고한 자체 측정 패널.
 * 특정 앱 화면을 복제하지 않고, 동일한 핵심 정보 구조를 Watch Collection 테마로 재구성합니다.
 */
public class TimegrapherView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TimegrapherEngine.Snapshot snapshot;
    private double liftAngle = 52.0;

    public TimegrapherView(Context context) {
        super(context);
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.NORMAL));
        setBackgroundColor(UiKit.PAPER);
    }

    public void setSnapshot(TimegrapherEngine.Snapshot snapshot) {
        this.snapshot = snapshot;
        invalidate();
    }

    public void setLiftAngle(double liftAngle) {
        this.liftAngle = liftAngle;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        drawDotPaper(c, w, h);
        drawRateScale(c, w, h);
        drawReadings(c, w, h);
        drawCenterMarker(c, w, h);
        drawTrace(c, w, h);
        drawFooter(c, w, h);
    }

    private void drawDotPaper(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(205, 198, 181));
        float gap = dp(13);
        float r = Math.max(1f, dp(0.7f));
        for (float y = gap; y < h; y += gap) {
            for (float x = gap; x < w; x += gap) c.drawCircle(x, y, r, paint);
        }
    }

    private void drawRateScale(Canvas c, int w, int h) {
        float left = dp(24);
        float right = w - dp(24);
        float yEdge = dp(108);
        float yCenter = dp(62);
        Path arc = new Path();
        arc.moveTo(left, yEdge);
        arc.quadTo(w / 2f, yCenter, right, yEdge);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(UiKit.INK);
        c.drawPath(arc, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(sp(11));
        for (int i = 0; i <= 10; i++) {
            float t = i / 10f;
            float x = left + (right - left) * t;
            float y = quadraticY(t, yEdge, yCenter);
            float tick = i % 2 == 0 ? dp(13) : dp(8);
            c.drawLine(x, y, x, y + tick, paint);
            if (i % 2 == 0) {
                int value = -50 + i * 10;
                String text = value > 0 ? "+" + value : String.valueOf(value);
                c.drawText(text, x, y - dp(8), paint);
            }
        }
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
    }

    private float quadraticY(float t, float edge, float center) {
        // quadratic bezier: edge -> center control -> edge
        return (1 - t) * (1 - t) * edge + 2 * (1 - t) * t * center + t * t * edge;
    }

    private void drawReadings(Canvas c, int w, int h) {
        TimegrapherEngine.Snapshot s = snapshot;
        int bph = s == null ? 0 : s.bph;
        double rate = s == null ? Double.NaN : s.rateSecDay;
        double be = s == null ? Double.NaN : s.beatErrorMs;
        double amp = s == null ? Double.NaN : s.amplitudeDeg;

        float labelX = dp(28);
        float valueX = w * 0.68f;
        float y = dp(154);
        float step = dp(34);

        paint.setColor(UiKit.INK);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(14));
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        c.drawText("BPH", labelX, y, paint);
        c.drawText("Rate", labelX, y + step, paint);
        c.drawText("Beat Error", labelX, y + step * 2, paint);
        c.drawText(String.format(Locale.KOREA, "Ampl.(%.0f°)", liftAngle), labelX, y + step * 3, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD));
        String bphText = bph > 0 ? String.format(Locale.ROOT, "%,d", bph) : "- AUTO";
        if (s != null && s.bphLocked && bph > 0) bphText += "  LOCK";
        c.drawText(bphText, valueX, y, paint);
        c.drawText(Double.isNaN(rate) ? "- s/d" : String.format(Locale.KOREA, "%+.1f s/d", rate), valueX, y + step, paint);
        c.drawText(Double.isNaN(be) ? "- ms" : String.format(Locale.KOREA, "%.2f ms", be), valueX, y + step * 2, paint);
        c.drawText(Double.isNaN(amp) ? "- °" : String.format(Locale.KOREA, "%.0f°", amp), valueX, y + step * 3, paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
    }

    private void drawCenterMarker(Canvas c, int w, int h) {
        TimegrapherEngine.Snapshot s = snapshot;
        double rate = s == null ? Double.NaN : s.rateSecDay;
        float center = w / 2f;
        float top = dp(292);
        float bottom = h - dp(82);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.3f));
        paint.setColor(UiKit.NAVY_2);
        c.drawLine(center, top, center, bottom, paint);

        float markerX = center;
        if (!Double.isNaN(rate)) {
            double clipped = Math.max(-50, Math.min(50, rate));
            markerX = center + (float) (clipped / 50.0 * (w * 0.36));
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(UiKit.GOLD);
        Path tri = new Path();
        tri.moveTo(markerX, top + dp(8));
        tri.lineTo(markerX - dp(8), top + dp(24));
        tri.lineTo(markerX + dp(8), top + dp(24));
        tri.close();
        c.drawPath(tri, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(UiKit.NAVY);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(sp(11));
        c.drawText("Δ", markerX, top + dp(44), paint);
        paint.setTextSize(sp(9));
        c.drawText("s/d", markerX, top + dp(59), paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
    }

    private void drawTrace(Canvas c, int w, int h) {
        List<Double> trace = snapshot == null ? Collections.emptyList() : snapshot.traceMs;
        if (trace == null || trace.isEmpty()) return;
        float top = dp(365);
        float bottom = h - dp(38);
        if (bottom <= top) return;
        float center = w / 2f;
        float xScale = w * 0.36f / 25f;
        int start = Math.max(0, trace.size() - 120);
        int count = trace.size() - start;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(128, 68, 58));
        for (int i = start; i < trace.size(); i++) {
            float y = top + (bottom - top) * ((i - start) / (float) Math.max(1, count - 1));
            double ms = Math.max(-25, Math.min(25, trace.get(i)));
            float x = center + (float) ms * xScale;
            c.drawCircle(x, y, dp(2.1f), paint);
        }
    }

    private void drawFooter(Canvas c, int w, int h) {
        TimegrapherEngine.Snapshot s = snapshot;
        String left = s == null ? "대기 중" : s.status;
        String right = s == null ? "" : String.format(Locale.KOREA, "%s · %.0f%% · %.0fs",
                s.qualityLabel(), s.signalQuality, s.elapsedSeconds);
        paint.setTextSize(sp(9));
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(UiKit.MUTED);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(left, dp(10), h - dp(13), paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        c.drawText(right, w - dp(10), h - dp(13), paint);
        paint.setColor(Color.rgb(170, 65, 61));
        paint.setStrokeWidth(dp(2));
        c.drawLine(dp(4), h - dp(3), w - dp(4), h - dp(3), paint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
