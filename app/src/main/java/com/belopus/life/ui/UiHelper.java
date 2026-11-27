package com.belopus.life.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.belopus.life.engine.CellRegion;

public final class UiHelper {

    private UiHelper() {}

    public static int dp(Context ctx, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }

    public static Drawable roundedBox(int fillColor, int strokeColor, float strokePx, float radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        if (strokePx > 0) {
            gd.setStroke(Math.round(strokePx), strokeColor);
        }
        return gd;
    }

    public static Drawable ripplePill(int fillColor, int strokeColor, float strokePx, float radiusPx, int rippleColor) {
        GradientDrawable content = new GradientDrawable();
        content.setColor(fillColor);
        content.setCornerRadius(radiusPx);
        if (strokePx > 0) {
            content.setStroke(Math.round(strokePx), strokeColor);
        }
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    public static TextView makePillButton(Context ctx, String label, int textColor, int bgColor,
                                          int borderColor, float textSp, int padHdp, int padVdp) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(textColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        int ph = dp(ctx, padHdp);
        int pv = dp(ctx, padVdp);
        tv.setPadding(ph, pv, ph, pv);
        tv.setBackground(ripplePill(bgColor, borderColor, dp(ctx, 1), dp(ctx, 18), 0x33FFFFFF));
        return tv;
    }

    public static TextView makeSectionHeader(Context ctx, String title, int textColor) {
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextColor(textColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, dp(ctx, 12), 0, dp(ctx, 6));
        return tv;
    }

    public static View makeDivider(Context ctx, int color) {
        View v = new View(ctx);
        v.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(ctx, 1)));
        lp.topMargin = dp(ctx, 8);
        lp.bottomMargin = dp(ctx, 8);
        v.setLayoutParams(lp);
        return v;
    }

    public static Bitmap renderRegionThumbnail(CellRegion region, ThemeManager.Theme theme, int sizePx) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(theme.bgColor);
        if (region == null || region.width <= 0 || region.height <= 0) return bmp;

        float pad = sizePx * 0.12f;
        float avail = sizePx - 2 * pad;
        float cell = Math.min(avail / region.width, avail / region.height);
        float drawW = cell * region.width;
        float drawH = cell * region.height;
        float ox = (sizePx - drawW) * 0.5f;
        float oy = (sizePx - drawH) * 0.5f;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(theme.cellMatureColor);
        float gap = cell >= 3.5f ? cell * 0.14f : 0f;
        float radius = cell >= 4f ? cell * 0.22f : 0f;

        for (int y = 0; y < region.height; y++) {
            for (int x = 0; x < region.width; x++) {
                if (region.get(x, y)) {
                    float l = ox + x * cell + gap * 0.5f;
                    float t = oy + y * cell + gap * 0.5f;
                    float r = ox + (x + 1) * cell - gap * 0.5f;
                    float b = oy + (y + 1) * cell - gap * 0.5f;
                    if (r <= l) r = l + 1f;
                    if (b <= t) b = t + 1f;
                    if (radius > 0f) {
                        c.drawRoundRect(l, t, r, b, radius, radius, p);
                    } else {
                        c.drawRect(l, t, r, b, p);
                    }
                }
            }
        }
        return bmp;
    }
}
