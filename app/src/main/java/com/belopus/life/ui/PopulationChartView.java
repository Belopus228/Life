package com.belopus.life.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.belopus.life.engine.LifeEngine;
import com.belopus.life.engine.SimStats;
import com.belopus.life.engine.Topology;

public class PopulationChartView extends View {

    private SimStats stats;
    private ThemeManager.Theme theme = ThemeManager.OBSIDIAN;
    private boolean showDeltas = false;

    private final Paint gridPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint birthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint deathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    public PopulationChartView(Context ctx) {
        super(ctx);
        gridPaint.setStrokeWidth(1f);
        textPaint.setTextSize(UiHelper.dp(ctx, 10));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(UiHelper.dp(ctx, 2.2f));
        fillPaint.setStyle(Paint.Style.FILL);

        birthPaint.setStyle(Paint.Style.STROKE);
        birthPaint.setStrokeWidth(UiHelper.dp(ctx, 1.6f));
        birthPaint.setColor(0xFF22C55E);

        deathPaint.setStyle(Paint.Style.STROKE);
        deathPaint.setStrokeWidth(UiHelper.dp(ctx, 1.6f));
        deathPaint.setColor(0xFFEF4444);
    }

    public void setData(SimStats stats, ThemeManager.Theme theme, boolean showDeltas) {
        this.stats = stats;
        this.theme = theme;
        this.showDeltas = showDeltas;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || stats == null) return;

        float padL = UiHelper.dp(getContext(), 42);
        float padR = UiHelper.dp(getContext(), 10);
        float padT = UiHelper.dp(getContext(), 12);
        float padB = UiHelper.dp(getContext(), 18);
        float cw = Math.max(10f, w - padL - padR);
        float ch = Math.max(10f, h - padT - padB);

        gridPaint.setColor(theme.hudBorderColor);
        textPaint.setColor(theme.hudSubTextColor);

        int n = stats.getHistorySize();
        long maxVal = 10;
        for (int i = 0; i < n; i++) {
            long v = showDeltas
                    ? Math.max(stats.getBirthHistoryAt(i), stats.getDeathHistoryAt(i))
                    : stats.getPopHistoryAt(i);
            if (v > maxVal) maxVal = v;
        }

        // Horizontal grid & labels
        for (int i = 0; i <= 4; i++) {
            float y = padT + (ch * i) / 4f;
            canvas.drawLine(padL, y, padL + cw, y, gridPaint);
            long val = Math.round(maxVal * (4 - i) / 4.0);
            canvas.drawText(String.valueOf(val), UiHelper.dp(getContext(), 4), y + UiHelper.dp(getContext(), 3), textPaint);
        }

        if (n < 2) {
            textPaint.setColor(theme.hudSubTextColor);
            canvas.drawText("—", padL + cw * 0.5f, padT + ch * 0.5f, textPaint);
            return;
        }

        if (!showDeltas) {
            linePath.reset();
            fillPath.reset();
            for (int i = 0; i < n; i++) {
                float x = padL + (cw * i) / (n - 1);
                float y = padT + ch - (ch * stats.getPopHistoryAt(i)) / (float) maxVal;
                if (i == 0) {
                    linePath.moveTo(x, y);
                    fillPath.moveTo(x, padT + ch);
                    fillPath.lineTo(x, y);
                } else {
                    linePath.lineTo(x, y);
                    fillPath.lineTo(x, y);
                }
            }
            fillPath.lineTo(padL + cw, padT + ch);
            fillPath.close();

            int cTop = Color.argb(110, Color.red(theme.accentColor), Color.green(theme.accentColor), Color.blue(theme.accentColor));
            int cBot = Color.argb(8, Color.red(theme.accentColor), Color.green(theme.accentColor), Color.blue(theme.accentColor));
            fillPaint.setShader(new LinearGradient(0, padT, 0, padT + ch, cTop, cBot, Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, fillPaint);

            linePaint.setColor(theme.accentColor);
            canvas.drawPath(linePath, linePaint);
        } else {
            // Births (green) and Deaths (red)
            linePath.reset();
            for (int i = 0; i < n; i++) {
                float x = padL + (cw * i) / (n - 1);
                float y = padT + ch - (ch * stats.getBirthHistoryAt(i)) / (float) maxVal;
                if (i == 0) linePath.moveTo(x, y);
                else linePath.lineTo(x, y);
            }
            canvas.drawPath(linePath, birthPaint);

            linePath.reset();
            for (int i = 0; i < n; i++) {
                float x = padL + (cw * i) / (n - 1);
                float y = padT + ch - (ch * stats.getDeathHistoryAt(i)) / (float) maxVal;
                if (i == 0) linePath.moveTo(x, y);
                else linePath.lineTo(x, y);
            }
            canvas.drawPath(linePath, deathPaint);
        }
    }

    public static void showStatsDialog(Context ctx, final LifeEngine engine, final ThemeManager.Theme theme, final boolean ru) {
        final SimStats st = engine.getStats();
        ScrollView sv = new ScrollView(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = UiHelper.dp(ctx, 16);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(theme.hudBgColor);

        TextView title = new TextView(ctx);
        title.setText(ru ? "Статистика и аналитика" : "Simulation Analytics");
        title.setTextColor(theme.hudTextColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(ctx);
        sub.setText(engine.getRule().getRuleCode() + " · " + engine.getTopology().getTitle(ru)
                + (engine.getTopology() != Topology.INFINITE ? (" " + engine.getGridWidth() + "×" + engine.getGridHeight()) : ""));
        sub.setTextColor(theme.accentColor);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sub.setPadding(0, UiHelper.dp(ctx, 2), 0, UiHelper.dp(ctx, 10));
        root.addView(sub);

        final String[] keys = {
                ru ? "Поколение" : "Generation",
                ru ? "Живых клеток" : "Live cells",
                ru ? "Состояние" : "State",
                ru ? "За последний шаг (+ / −)" : "Last step (+ / −)",
                ru ? "Пик популяции" : "Peak population",
                ru ? "Всего рождений / смертей" : "Total births / deaths",
                ru ? "Габариты колонии" : "Bounding box"
        };

        final TextView[] valViews = new TextView[keys.length];
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 10));
        card.setBackground(UiHelper.roundedBox(theme.bgColor, theme.hudBorderColor, UiHelper.dp(ctx, 1), UiHelper.dp(ctx, 12)));
        for (int i = 0; i < keys.length; i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, UiHelper.dp(ctx, 3), 0, UiHelper.dp(ctx, 3));
            TextView k = new TextView(ctx);
            k.setText(keys[i]);
            k.setTextColor(theme.hudSubTextColor);
            k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            k.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView v = new TextView(ctx);
            v.setTextColor(theme.hudTextColor);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            v.setTypeface(null, android.graphics.Typeface.BOLD);
            valViews[i] = v;
            row.addView(k);
            row.addView(v);
            card.addView(row);
        }
        root.addView(card);

        final TextView popHdr = UiHelper.makeSectionHeader(ctx, "", theme.hudTextColor);
        root.addView(popHdr);
        final PopulationChartView popChart = new PopulationChartView(ctx);
        popChart.setBackground(UiHelper.roundedBox(theme.bgColor, theme.hudBorderColor, UiHelper.dp(ctx, 1), UiHelper.dp(ctx, 12)));
        popChart.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 135)));
        root.addView(popChart);

        root.addView(UiHelper.makeSectionHeader(ctx,
                ru ? "Рождения (зелёный) и Смерти (красный)"
                        : "Births (green) vs Deaths (red)",
                theme.hudTextColor));
        final PopulationChartView deltaChart = new PopulationChartView(ctx);
        deltaChart.setBackground(UiHelper.roundedBox(theme.bgColor, theme.hudBorderColor, UiHelper.dp(ctx, 1), UiHelper.dp(ctx, 12)));
        deltaChart.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 115)));
        root.addView(deltaChart);

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                String boxStr = st.hasBounds
                        ? ((st.maxX - st.minX + 1) + " × " + (st.maxY - st.minY + 1))
                        : "0 × 0";
                valViews[0].setText(String.valueOf(st.generation));
                valViews[1].setText(String.valueOf(st.liveCount));
                valViews[2].setText(st.getStatusText(ru));
                valViews[3].setText("+" + st.lastBirths + " / -" + st.lastDeaths);
                valViews[4].setText(st.peakPopulation + " (" + (ru ? "пок. " : "gen ") + st.peakGeneration + ")");
                valViews[5].setText(st.totalBirths + " / " + st.totalDeaths);
                valViews[6].setText(boxStr);
                popHdr.setText(ru ? "График популяции (последние " + st.getHistorySize() + " пок.)"
                        : "Population Curve (last " + st.getHistorySize() + " gens)");
                popChart.setData(st, theme, false);
                deltaChart.setData(st, theme, true);
            }
        };
        refresh.run();

        sv.addView(root);
        final AlertDialog dlg = new AlertDialog.Builder(ctx)
                .setView(sv)
                .setPositiveButton(ru ? "Закрыть" : "Close", null)
                .create();

        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable ticker = new Runnable() {
            @Override public void run() {
                if (!dlg.isShowing()) return;
                refresh.run();
                h.postDelayed(this, 250L);
            }
        };
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) {
                h.removeCallbacks(ticker);
            }
        });
        dlg.show();
        h.postDelayed(ticker, 250L);
    }
}
