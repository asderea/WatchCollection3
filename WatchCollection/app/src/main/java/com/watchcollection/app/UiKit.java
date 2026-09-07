package com.watchcollection.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UiKit {
    public static final int NAVY = Color.rgb(22, 32, 51);
    public static final int NAVY_2 = Color.rgb(43, 61, 86);
    public static final int CREAM = Color.rgb(246, 243, 237);
    public static final int PAPER = Color.rgb(252, 251, 248);
    public static final int INK = Color.rgb(34, 40, 49);
    public static final int MUTED = Color.rgb(112, 119, 129);
    public static final int LINE = Color.rgb(224, 220, 211);
    public static final int GOLD = Color.rgb(177, 143, 74);
    public static final int GOLD_SOFT = Color.rgb(239, 231, 211);
    public static final int IMAGE_BG = Color.rgb(235, 233, 228);
    public static final int PHOTO_BG = Color.rgb(252, 251, 247); // 사용자가 지정한 시계 사진/캘린더 카드 배경
    public static final int WHITE = Color.WHITE;

    private UiKit() {}

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, String value, int sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(INK);
        t.setLineSpacing(0, 1.12f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    public static TextView muted(Context c, String value, int sp) {
        TextView t = text(c, value, sp, false);
        t.setTextColor(MUTED);
        return t;
    }

    public static TextView eyebrow(Context c, String value) {
        TextView t = text(c, value.toUpperCase(), 11, true);
        t.setTextColor(GOLD);
        t.setLetterSpacing(0.08f);
        return t;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View spacer(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, hDp)));
        return v;
    }

    public static GradientDrawable rounded(int fill, int stroke, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(c, 1), stroke);
        return d;
    }

    public static LinearLayout card(Context c) {
        LinearLayout card = column(c);
        card.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        card.setBackground(rounded(PAPER, LINE, 18, c));
        card.setElevation(dp(c, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(c, 12));
        card.setLayoutParams(p);
        return card;
    }

    public static Button primaryButton(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextColor(WHITE);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(NAVY, Color.TRANSPARENT, 13, c));
        b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        return b;
    }

    public static Button secondaryButton(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextColor(NAVY);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setBackground(rounded(PAPER, LINE, 12, c));
        b.setPadding(dp(c, 10), dp(c, 7), dp(c, 10), dp(c, 7));
        return b;
    }

    public static Button chip(Context c, String label, boolean selected) {
        Button b = secondaryButton(c, label);
        b.setTextColor(selected ? WHITE : NAVY);
        b.setBackground(rounded(selected ? NAVY : PAPER, selected ? NAVY : LINE, 22, c));
        return b;
    }

    public static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setPadding(dp(c, 12), dp(c, 11), dp(c, 12), dp(c, 11));
        e.setBackground(rounded(WHITE, LINE, 11, c));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 5), 0, dp(c, 11));
        e.setLayoutParams(p);
        return e;
    }

    public static TextView label(Context c, String label) {
        TextView t = text(c, label, 12, true);
        t.setTextColor(MUTED);
        return t;
    }
}
