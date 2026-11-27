package com.belopus.life.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.belopus.life.engine.LifeEngine;
import com.belopus.life.engine.RuleSet;
import com.belopus.life.engine.Topology;
import com.belopus.life.storage.PatternStorage;

public final class SettingsDialog {

    public interface Callback {
        void onSettingsApplied(PatternStorage.AppSettings newSettings, boolean topologyChanged, boolean preserveCells);
    }

    private SettingsDialog() {}

    public static void show(final Context ctx, final LifeEngine engine,
                            final PatternStorage.AppSettings current,
                            final Callback callback) {
        final boolean ru = current.languageRu;
        final ThemeManager.Theme theme = ThemeManager.byId(current.themeId);

        final Topology[] selTop = {engine.getTopology()};
        final int[] selW = {engine.getGridWidth()};
        final int[] selH = {engine.getGridHeight()};
        final int[] bMask = {engine.getRule().birthMask};
        final int[] sMask = {engine.getRule().survivalMask};
        final String[] selThemeId = {current.themeId};
        final int[] selShape = {current.cellShape};
        final int[] selDecay = {current.decaySteps};
        final boolean[] selLangRu = {current.languageRu};

        ScrollView sv = new ScrollView(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.hudBgColor);
        int pad = UiHelper.dp(ctx, 16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(ctx);
        title.setText(ru ? "Настройки симуляции и вида" : "Simulation & Display Settings");
        title.setTextColor(theme.hudTextColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        // === TOPOLOGY ===
        root.addView(UiHelper.makeSectionHeader(ctx, ru ? "Топология поля" : "Grid Topology", theme.hudTextColor));
        final LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(topRow);

        final LinearLayout sizeSection = new LinearLayout(ctx);
        sizeSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(sizeSection);

        final EditText etW = new EditText(ctx);
        final EditText etH = new EditText(ctx);
        etW.setInputType(InputType.TYPE_CLASS_NUMBER);
        etH.setInputType(InputType.TYPE_CLASS_NUMBER);
        etW.setText(String.valueOf(selW[0]));
        etH.setText(String.valueOf(selH[0]));
        etW.setTextColor(theme.hudTextColor);
        etH.setTextColor(theme.hudTextColor);
        etW.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        etH.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        etW.setPadding(UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 6));
        etH.setPadding(UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 6));
        etW.setBackground(UiHelper.roundedBox(theme.bgColor, theme.hudBorderColor, UiHelper.dp(ctx, 1), UiHelper.dp(ctx, 8)));
        etH.setBackground(UiHelper.roundedBox(theme.bgColor, theme.hudBorderColor, UiHelper.dp(ctx, 1), UiHelper.dp(ctx, 8)));

        final CheckBox cbPreserve = new CheckBox(ctx);
        cbPreserve.setText(ru ? "Сохранять клетки при смене размера/поля" : "Preserve cells when resizing/switching");
        cbPreserve.setTextColor(theme.hudSubTextColor);
        cbPreserve.setChecked(true);

        final Runnable[] rebuildTopUI = new Runnable[1];
        rebuildTopUI[0] = new Runnable() {
            @Override
            public void run() {
                topRow.removeAllViews();
                Topology[] vals = Topology.values();
                for (int i = 0; i < vals.length; i++) {
                    final Topology t = vals[i];
                    boolean act = selTop[0] == t;
                    TextView pill = UiHelper.makePillButton(ctx, t.getTitle(ru),
                            act ? theme.accentTextColor : theme.hudTextColor,
                            act ? theme.accentColor : theme.bgColor,
                            act ? theme.accentColor : theme.hudBorderColor, 11f, 8, 7);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    if (i < vals.length - 1) lp.rightMargin = UiHelper.dp(ctx, 5);
                    pill.setLayoutParams(lp);
                    pill.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            selTop[0] = t;
                            rebuildTopUI[0].run();
                        }
                    });
                    topRow.addView(pill);
                }

                sizeSection.removeAllViews();
                if (selTop[0] != Topology.INFINITE) {
                    LinearLayout presetsRow = new LinearLayout(ctx);
                    presetsRow.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    prlp.topMargin = UiHelper.dp(ctx, 8);
                    presetsRow.setLayoutParams(prlp);
                    int[] sizes = {32, 64, 128, 256, 512};
                    for (int i = 0; i < sizes.length; i++) {
                        final int sz = sizes[i];
                        boolean act = (selW[0] == sz && selH[0] == sz);
                        TextView p = UiHelper.makePillButton(ctx, String.valueOf(sz),
                                act ? theme.accentTextColor : theme.hudTextColor,
                                act ? theme.accentColor : theme.bgColor,
                                act ? theme.accentColor : theme.hudBorderColor, 11f, 6, 5);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                        if (i < sizes.length - 1) lp.rightMargin = UiHelper.dp(ctx, 4);
                        p.setLayoutParams(lp);
                        p.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                selW[0] = sz;
                                selH[0] = sz;
                                etW.setText(String.valueOf(sz));
                                etH.setText(String.valueOf(sz));
                                rebuildTopUI[0].run();
                            }
                        });
                        presetsRow.addView(p);
                    }
                    sizeSection.addView(presetsRow);

                    LinearLayout customRow = new LinearLayout(ctx);
                    customRow.setOrientation(LinearLayout.HORIZONTAL);
                    customRow.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams crlp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    crlp.topMargin = UiHelper.dp(ctx, 6);
                    customRow.setLayoutParams(crlp);
                    TextView lbl = new TextView(ctx);
                    lbl.setText(ru ? "Размер (Ш × В): " : "Size (W × H): ");
                    lbl.setTextColor(theme.hudSubTextColor);
                    lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    customRow.addView(lbl);
                    LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 68), LinearLayout.LayoutParams.WRAP_CONTENT);
                    etW.setLayoutParams(wlp);
                    customRow.addView(etW);
                    TextView xLbl = new TextView(ctx);
                    xLbl.setText("  ×  ");
                    xLbl.setTextColor(theme.hudSubTextColor);
                    customRow.addView(xLbl);
                    LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 68), LinearLayout.LayoutParams.WRAP_CONTENT);
                    etH.setLayoutParams(hlp);
                    customRow.addView(etH);
                    sizeSection.addView(customRow);
                    sizeSection.addView(cbPreserve);
                }
            }
        };
        rebuildTopUI[0].run();

        // === RULES ===
        root.addView(UiHelper.makeDivider(ctx, theme.hudBorderColor));
        root.addView(UiHelper.makeSectionHeader(ctx, ru ? "Правило автомата (по умолчанию B3/S23)" : "Cellular Automaton Rule (default B3/S23)", theme.hudTextColor));

        final TextView tvRuleTitle = new TextView(ctx);
        tvRuleTitle.setTextColor(theme.accentColor);
        tvRuleTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvRuleTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(tvRuleTitle);

        final TextView tvRuleDesc = new TextView(ctx);
        tvRuleDesc.setTextColor(theme.hudSubTextColor);
        tvRuleDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvRuleDesc.setPadding(0, UiHelper.dp(ctx, 2), 0, UiHelper.dp(ctx, 8));
        root.addView(tvRuleDesc);

        HorizontalScrollView ruleScroll = new HorizontalScrollView(ctx);
        ruleScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout rulePresetsRow = new LinearLayout(ctx);
        rulePresetsRow.setOrientation(LinearLayout.HORIZONTAL);
        ruleScroll.addView(rulePresetsRow);
        root.addView(ruleScroll);

        final LinearLayout bsEditor = new LinearLayout(ctx);
        bsEditor.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bslp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bslp.topMargin = UiHelper.dp(ctx, 8);
        bsEditor.setLayoutParams(bslp);
        root.addView(bsEditor);

        final Runnable[] rebuildRuleUI = new Runnable[1];
        rebuildRuleUI[0] = new Runnable() {
            @Override
            public void run() {
                RuleSet curRule = new RuleSet(bMask[0], sMask[0]);
                tvRuleTitle.setText(curRule.getName(ru));
                tvRuleDesc.setText(curRule.getDescription(ru));

                rulePresetsRow.removeAllViews();
                for (final RuleSet preset : RuleSet.PRESETS) {
                    boolean act = preset.birthMask == bMask[0] && preset.survivalMask == sMask[0];
                    TextView pill = UiHelper.makePillButton(ctx, preset.getRuleCode(),
                            act ? theme.accentTextColor : theme.hudTextColor,
                            act ? theme.accentColor : theme.bgColor,
                            act ? theme.accentColor : theme.hudBorderColor, 11f, 10, 6);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.rightMargin = UiHelper.dp(ctx, 5);
                    pill.setLayoutParams(lp);
                    pill.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            bMask[0] = preset.birthMask;
                            sMask[0] = preset.survivalMask;
                            rebuildRuleUI[0].run();
                        }
                    });
                    rulePresetsRow.addView(pill);
                }

                bsEditor.removeAllViews();
                for (int mode = 0; mode < 2; mode++) {
                    final boolean isBirth = (mode == 0);
                    LinearLayout row = new LinearLayout(ctx);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    rlp.bottomMargin = UiHelper.dp(ctx, 5);
                    row.setLayoutParams(rlp);

                    TextView lbl = new TextView(ctx);
                    lbl.setText(isBirth ? (ru ? "Рожд. B:" : "Birth B:") : (ru ? "Выжив. S:" : "Surv. S:"));
                    lbl.setTextColor(theme.hudSubTextColor);
                    lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    lbl.setLayoutParams(new LinearLayout.LayoutParams(UiHelper.dp(ctx, 64), LinearLayout.LayoutParams.WRAP_CONTENT));
                    row.addView(lbl);

                    for (int n = 0; n <= 8; n++) {
                        final int bit = 1 << n;
                        int mask = isBirth ? bMask[0] : sMask[0];
                        boolean act = (mask & bit) != 0;
                        TextView btn = UiHelper.makePillButton(ctx, String.valueOf(n),
                                act ? theme.accentTextColor : theme.hudTextColor,
                                act ? theme.accentColor : theme.bgColor,
                                act ? theme.accentColor : theme.hudBorderColor, 11f, 4, 4);
                        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                        if (n < 8) blp.rightMargin = UiHelper.dp(ctx, 3);
                        btn.setLayoutParams(blp);
                        btn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                if (isBirth) {
                                    bMask[0] ^= bit;
                                    if (bit == 1 && (bMask[0] & 1) != 0 && selTop[0] == Topology.INFINITE) {
                                        Toast.makeText(ctx, ru
                                                ? "Правило B0 переключает поле в режим Тор"
                                                : "Rule B0 switches topology to Toroidal", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    sMask[0] ^= bit;
                                }
                                rebuildRuleUI[0].run();
                            }
                        });
                        row.addView(btn);
                    }
                    bsEditor.addView(row);
                }
            }
        };
        rebuildRuleUI[0].run();

        // === VISUAL THEME ===
        root.addView(UiHelper.makeDivider(ctx, theme.hudBorderColor));
        root.addView(UiHelper.makeSectionHeader(ctx, ru ? "Цветовая тема" : "Visual Theme", theme.hudTextColor));
        HorizontalScrollView themeScroll = new HorizontalScrollView(ctx);
        themeScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout themeRow = new LinearLayout(ctx);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeScroll.addView(themeRow);
        root.addView(themeScroll);

        final Runnable[] rebuildThemes = new Runnable[1];
        rebuildThemes[0] = new Runnable() {
            @Override public void run() {
                themeRow.removeAllViews();
                for (final ThemeManager.Theme t : ThemeManager.THEMES) {
                    boolean act = t.id.equals(selThemeId[0]);
                    TextView pill = UiHelper.makePillButton(ctx, t.getName(ru),
                            act ? t.accentTextColor : theme.hudTextColor,
                            act ? t.accentColor : t.bgColor,
                            act ? t.accentColor : t.cellMatureColor, 12f, 11, 7);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.rightMargin = UiHelper.dp(ctx, 6);
                    pill.setLayoutParams(lp);
                    pill.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            selThemeId[0] = t.id;
                            rebuildThemes[0].run();
                        }
                    });
                    themeRow.addView(pill);
                }
            }
        };
        rebuildThemes[0].run();

        // === CELL SHAPE ===
        root.addView(UiHelper.makeSectionHeader(ctx, ru ? "Форма клеток" : "Cell Shape", theme.hudTextColor));
        final LinearLayout shapeRow = new LinearLayout(ctx);
        shapeRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(shapeRow);
        final String[] shapeNames = ru
                ? new String[]{"Квадрат", "Скруглённая", "Круг"}
                : new String[]{"Square", "Rounded", "Circle"};
        final Runnable[] rebuildShapes = new Runnable[1];
        rebuildShapes[0] = new Runnable() {
            @Override public void run() {
                shapeRow.removeAllViews();
                for (int i = 0; i < 3; i++) {
                    final int idx = i;
                    boolean act = selShape[0] == idx;
                    TextView p = UiHelper.makePillButton(ctx, shapeNames[i],
                            act ? theme.accentTextColor : theme.hudTextColor,
                            act ? theme.accentColor : theme.bgColor,
                            act ? theme.accentColor : theme.hudBorderColor, 12f, 8, 6);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    if (i < 2) lp.rightMargin = UiHelper.dp(ctx, 5);
                    p.setLayoutParams(lp);
                    p.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            selShape[0] = idx;
                            rebuildShapes[0].run();
                        }
                    });
                    shapeRow.addView(p);
                }
            }
        };
        rebuildShapes[0].run();

        // === DECAY TRAIL SLIDER ===
        final TextView tvDecay = new TextView(ctx);
        tvDecay.setText((ru ? "Шлейф угасания (пок.): " : "Decay trail (gens): ") + selDecay[0]);
        tvDecay.setTextColor(theme.hudTextColor);
        tvDecay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvDecay.setPadding(0, UiHelper.dp(ctx, 12), 0, UiHelper.dp(ctx, 4));
        root.addView(tvDecay);

        SeekBar sbDecay = new SeekBar(ctx);
        sbDecay.setMax(16);
        sbDecay.setProgress(Math.max(0, Math.min(16, selDecay[0])));
        sbDecay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                selDecay[0] = p;
                tvDecay.setText((ru ? "Шлейф угасания (пок.): " : "Decay trail (gens): ")
                        + (p == 0 ? (ru ? "Выкл" : "Off") : String.valueOf(p)));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(sbDecay);

        // === CHECKBOX TOGGLES ===
        final CheckBox cbAge = new CheckBox(ctx);
        cbAge.setText(ru ? "Подсветка возраста живых клеток" : "Color cells by age");
        cbAge.setTextColor(theme.hudTextColor);
        cbAge.setChecked(current.ageColoring);
        root.addView(cbAge);

        final CheckBox cbGrid = new CheckBox(ctx);
        cbGrid.setText(ru ? "Линии сетки при приближении" : "Show grid lines when zoomed in");
        cbGrid.setTextColor(theme.hudTextColor);
        cbGrid.setChecked(current.showGrid);
        root.addView(cbGrid);

        final CheckBox cbAxes = new CheckBox(ctx);
        cbAxes.setText(ru ? "Оси координат (0, 0)" : "Show origin axes (0, 0)");
        cbAxes.setTextColor(theme.hudTextColor);
        cbAxes.setChecked(current.showAxes);
        root.addView(cbAxes);

        final CheckBox cbMinimap = new CheckBox(ctx);
        cbMinimap.setText(ru ? "Мини-карта обзора" : "Show overview minimap");
        cbMinimap.setTextColor(theme.hudTextColor);
        cbMinimap.setChecked(current.showMinimap);
        root.addView(cbMinimap);

        final CheckBox cbAutoPause = new CheckBox(ctx);
        cbAutoPause.setText(ru ? "Автопауза при стабилизации / осцилляторе" : "Auto-pause on stability / oscillator");
        cbAutoPause.setTextColor(theme.hudTextColor);
        cbAutoPause.setChecked(current.autoPauseOnStable);
        root.addView(cbAutoPause);

        final CheckBox cbSound = new CheckBox(ctx);
        cbSound.setText(ru ? "Звуковой отклик (синтез)" : "Procedural sound feedback");
        cbSound.setTextColor(theme.hudTextColor);
        cbSound.setChecked(current.soundEnabled);
        root.addView(cbSound);

        // === LANGUAGE ===
        root.addView(UiHelper.makeSectionHeader(ctx, ru ? "Язык интерфейса / Language" : "Interface Language", theme.hudTextColor));
        final LinearLayout langRow = new LinearLayout(ctx);
        langRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(langRow);
        final Runnable[] rebuildLang = new Runnable[1];
        rebuildLang[0] = new Runnable() {
            @Override public void run() {
                langRow.removeAllViews();
                for (int i = 0; i < 2; i++) {
                    final boolean isRu = (i == 0);
                    boolean act = selLangRu[0] == isRu;
                    TextView p = UiHelper.makePillButton(ctx, isRu ? "Русский" : "English",
                            act ? theme.accentTextColor : theme.hudTextColor,
                            act ? theme.accentColor : theme.bgColor,
                            act ? theme.accentColor : theme.hudBorderColor, 12f, 10, 6);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    if (i == 0) lp.rightMargin = UiHelper.dp(ctx, 6);
                    p.setLayoutParams(lp);
                    p.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            selLangRu[0] = isRu;
                            rebuildLang[0].run();
                        }
                    });
                    langRow.addView(p);
                }
            }
        };
        rebuildLang[0].run();

        sv.addView(root);
        new AlertDialog.Builder(ctx)
                .setView(sv)
                .setNegativeButton(ru ? "Отмена" : "Cancel", null)
                .setPositiveButton(ru ? "Применить" : "Apply", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        int gw = selW[0];
                        int gh = selH[0];
                        try { gw = Integer.parseInt(etW.getText().toString().trim()); } catch (Exception ignored) {}
                        try { gh = Integer.parseInt(etH.getText().toString().trim()); } catch (Exception ignored) {}
                        gw = Math.max(8, Math.min(1024, gw));
                        gh = Math.max(8, Math.min(1024, gh));

                        RuleSet chosenRule = new RuleSet(bMask[0], sMask[0]);
                        Topology finalTop = selTop[0];
                        if (finalTop == Topology.INFINITE && chosenRule.hasB0()) {
                            finalTop = Topology.TOROIDAL;
                        }

                        boolean topChanged = (finalTop != engine.getTopology())
                                || (finalTop != Topology.INFINITE && (gw != engine.getGridWidth() || gh != engine.getGridHeight()));

                        PatternStorage.AppSettings ns = new PatternStorage.AppSettings();
                        ns.languageRu = selLangRu[0];
                        ns.themeId = selThemeId[0];
                        ns.cellShape = selShape[0];
                        ns.ageColoring = cbAge.isChecked();
                        ns.decaySteps = selDecay[0];
                        ns.showGrid = cbGrid.isChecked();
                        ns.showAxes = cbAxes.isChecked();
                        ns.showMinimap = cbMinimap.isChecked();
                        ns.autoPauseOnStable = cbAutoPause.isChecked();
                        ns.soundEnabled = cbSound.isChecked();
                        ns.simSpeed = current.simSpeed;
                        ns.topology = finalTop;
                        ns.gridWidth = gw;
                        ns.gridHeight = gh;
                        ns.ruleCode = chosenRule.getRuleCode();

                        callback.onSettingsApplied(ns, topChanged, cbPreserve.isChecked());
                    }
                }).show();
    }
}
