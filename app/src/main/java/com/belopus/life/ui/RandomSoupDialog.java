package com.belopus.life.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.belopus.life.engine.Symmetry;
import com.belopus.life.engine.Topology;

public final class RandomSoupDialog {

    public interface Callback {
        void onGenerateSoup(int width, int height, float density, Symmetry symmetry, boolean clearFirst);
    }

    private RandomSoupDialog() {}

    public static void show(Context ctx, final ThemeManager.Theme theme,
                            final Topology topology, final int gridW, final int gridH,
                            final boolean ru, final Callback callback) {
        final int[] densityPct = {32};
        final int[] sizeChoice = {topology == Topology.INFINITE ? 1 : 3}; // 0=32,1=64,2=128,3=full/200
        final Symmetry[] symChoice = {Symmetry.NONE};

        ScrollView sv = new ScrollView(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.hudBgColor);
        int pad = UiHelper.dp(ctx, 16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(ctx);
        title.setText(ru ? "Генератор случайного супа" : "Random Soup Generator");
        title.setTextColor(theme.hudTextColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        final TextView tvDensity = new TextView(ctx);
        tvDensity.setText((ru ? "Плотность: " : "Density: ") + densityPct[0] + "%");
        tvDensity.setTextColor(theme.accentColor);
        tvDensity.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvDensity.setPadding(0, UiHelper.dp(ctx, 12), 0, UiHelper.dp(ctx, 4));
        root.addView(tvDensity);

        SeekBar sb = new SeekBar(ctx);
        sb.setMax(85); // 5..90
        sb.setProgress(densityPct[0] - 5);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                densityPct[0] = p + 5;
                tvDensity.setText((ru ? "Плотность: " : "Density: ") + densityPct[0] + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(sb);

        root.addView(UiHelper.makeSectionHeader(ctx, ru ? "Размер области" : "Region Size", theme.hudTextColor));
        final LinearLayout sizeRow = new LinearLayout(ctx);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] sizeLabels = topology == Topology.INFINITE
                ? new String[]{"32×32", "64×64", "128×128", "200×200"}
                : new String[]{"32×32", "64×64", "128×128", ru ? "Всё поле" : "Full Grid"};
        final Runnable[] rebuildSizes = new Runnable[1];
        rebuildSizes[0] = new Runnable() {
            @Override public void run() {
                sizeRow.removeAllViews();
                for (int i = 0; i < sizeLabels.length; i++) {
                    final int idx = i;
                    boolean act = sizeChoice[0] == idx;
                    TextView pill = UiHelper.makePillButton(ctx, sizeLabels[i],
                            act ? theme.accentTextColor : theme.hudTextColor,
                            act ? theme.accentColor : theme.bgColor,
                            act ? theme.accentColor : theme.hudBorderColor, 12f, 10, 6);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    if (i < sizeLabels.length - 1) lp.rightMargin = UiHelper.dp(ctx, 6);
                    pill.setLayoutParams(lp);
                    pill.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            sizeChoice[0] = idx;
                            rebuildSizes[0].run();
                        }
                    });
                    sizeRow.addView(pill);
                }
            }
        };
        rebuildSizes[0].run();
        root.addView(sizeRow);

        root.addView(UiHelper.makeSectionHeader(ctx, ru ? "Симметрия" : "Symmetry", theme.hudTextColor));
        final LinearLayout symRow = new LinearLayout(ctx);
        symRow.setOrientation(LinearLayout.HORIZONTAL);
        final Runnable[] rebuildSyms = new Runnable[1];
        rebuildSyms[0] = new Runnable() {
            @Override public void run() {
                symRow.removeAllViews();
                Symmetry[] vals = Symmetry.values();
                for (int i = 0; i < vals.length; i++) {
                    final Symmetry s = vals[i];
                    boolean act = symChoice[0] == s;
                    TextView pill = UiHelper.makePillButton(ctx, s.getTitle(ru),
                            act ? theme.accentTextColor : theme.hudTextColor,
                            act ? theme.accentColor : theme.bgColor,
                            act ? theme.accentColor : theme.hudBorderColor, 11f, 6, 6);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    if (i < vals.length - 1) lp.rightMargin = UiHelper.dp(ctx, 4);
                    pill.setLayoutParams(lp);
                    pill.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            symChoice[0] = s;
                            rebuildSyms[0].run();
                        }
                    });
                    symRow.addView(pill);
                }
            }
        };
        rebuildSyms[0].run();
        root.addView(symRow);

        final CheckBox cbClear = new CheckBox(ctx);
        cbClear.setText(ru ? "Очистить поле перед заполнением" : "Clear grid before generating");
        cbClear.setTextColor(theme.hudTextColor);
        cbClear.setChecked(true);
        cbClear.setPadding(0, UiHelper.dp(ctx, 12), 0, 0);
        root.addView(cbClear);

        sv.addView(root);
        new AlertDialog.Builder(ctx)
                .setView(sv)
                .setNegativeButton(ru ? "Отмена" : "Cancel", null)
                .setPositiveButton(ru ? "Создать" : "Generate", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        int rw, rh;
                        if (sizeChoice[0] == 0) { rw = rh = 32; }
                        else if (sizeChoice[0] == 1) { rw = rh = 64; }
                        else if (sizeChoice[0] == 2) { rw = rh = 128; }
                        else if (topology == Topology.INFINITE) { rw = rh = 200; }
                        else { rw = gridW; rh = gridH; }
                        callback.onGenerateSoup(rw, rh, densityPct[0] / 100f, symChoice[0], cbClear.isChecked());
                    }
                }).show();
    }
}
