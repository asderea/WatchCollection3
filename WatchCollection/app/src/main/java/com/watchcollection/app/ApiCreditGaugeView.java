package com.watchcollection.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * 앱 상단에 표시하는 OpenAI API 크레딧 원형 게이지입니다.
 * 실제 결제 잔액이 아니라 Worker가 계산한 예상 잔여 비율을 표시합니다.
 */
public class ApiCreditGaugeView extends View {
    private enum State { UNCONFIGURED, LOADING, READY, ERROR }

    private final Paint ringBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private State state = State.UNCONFIGURED;
    private float ratio = 0f;
    private String valueText = "—";
    private String labelText = "API";
    private boolean blocked = false;
    private boolean limited = false;

    public ApiCreditGaugeView(Context context) {
        super(context);
        init();
    }

    public ApiCreditGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        setMinimumWidth(UiKit.dp(getContext(), 52));
        setMinimumHeight(UiKit.dp(getContext(), 52));

        ringBackgroundPaint.setStyle(Paint.Style.STROKE);
        ringBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);
        ringBackgroundPaint.setColor(Color.argb(72, 255, 255, 255));

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        valuePaint.setColor(Color.WHITE);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        labelPaint.setColor(Color.rgb(190, 197, 207));

        setContentDescription("API 크레딧 설정 필요");
    }

    public void showUnconfigured() {
        state = State.UNCONFIGURED;
        ratio = 0f;
        valueText = "—";
        labelText = "API";
        blocked = false;
        limited = false;
        setContentDescription("API 크레딧 설정 필요");
        invalidate();
    }

    public void showLoading() {
        state = State.LOADING;
        valueText = "···";
        labelText = "API";
        blocked = false;
        limited = false;
        setContentDescription("API 크레딧 조회 중");
        invalidate();
    }

    public void showError() {
        state = State.ERROR;
        ratio = 0f;
        valueText = "!";
        labelText = "API";
        blocked = false;
        limited = false;
        setContentDescription("API 크레딧 조회 실패");
        invalidate();
    }

    public void showUsage(double remainingPercent, boolean callsBlocked, boolean partiallyLimited) {
        state = State.READY;
        ratio = (float) Math.max(0d, Math.min(1d, remainingPercent / 100d));
        valueText = Math.round(Math.max(0d, Math.min(100d, remainingPercent))) + "%";
        labelText = "API";
        blocked = callsBlocked;
        limited = partiallyLimited;

        String stateText = callsBlocked ? "호출 차단" : (partiallyLimited ? "일부 호출 제한" : "사용 가능");
        setContentDescription("API 예상 잔여 " + valueText + ", " + stateText);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int preferred = UiKit.dp(getContext(), 52);
        int width = resolveSize(preferred, widthMeasureSpec);
        int height = resolveSize(preferred, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float size = Math.min(width, height);
        float stroke = Math.max(UiKit.dp(getContext(), 3), size * 0.075f);
        float pad = stroke / 2f + UiKit.dp(getContext(), 1);

        ringBackgroundPaint.setStrokeWidth(stroke);
        ringPaint.setStrokeWidth(stroke);

        int accent;
        if (state == State.ERROR || blocked) {
            accent = Color.rgb(220, 92, 92);
        } else if (limited) {
            accent = Color.rgb(224, 174, 74);
        } else if (state == State.UNCONFIGURED || state == State.LOADING) {
            accent = Color.rgb(150, 158, 170);
        } else {
            accent = UiKit.GOLD;
        }
        ringPaint.setColor(accent);

        arcBounds.set(pad, pad, width - pad, height - pad);
        canvas.drawArc(arcBounds, -90f, 360f, false, ringBackgroundPaint);

        float sweep;
        if (state == State.READY) sweep = Math.max(2f, 360f * ratio);
        else if (state == State.LOADING) sweep = 80f;
        else sweep = 0f;
        if (sweep > 0f) canvas.drawArc(arcBounds, -90f, sweep, false, ringPaint);

        valuePaint.setTextSize(size * 0.255f);
        labelPaint.setTextSize(size * 0.145f);

        float centerX = width / 2f;
        float valueBaseline = height * 0.49f - (valuePaint.ascent() + valuePaint.descent()) / 2f;
        float labelBaseline = height * 0.69f - (labelPaint.ascent() + labelPaint.descent()) / 2f;

        canvas.drawText(valueText, centerX, valueBaseline, valuePaint);
        canvas.drawText(labelText, centerX, labelBaseline, labelPaint);
    }
}
