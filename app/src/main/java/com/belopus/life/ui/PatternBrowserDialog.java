package com.belopus.life.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.belopus.life.engine.CellRegion;
import com.belopus.life.engine.PatternLibrary;
import com.belopus.life.engine.RleCodec;
import com.belopus.life.engine.RuleSet;
import com.belopus.life.storage.PatternStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PatternBrowserDialog {

    public interface Callback {
        void onPlaceAsStamp(CellRegion region, String name, RuleSet recommendedRule);
        void onLoadCentered(CellRegion region, String name, RuleSet recommendedRule);
    }

    private PatternBrowserDialog() {}

    public static void show(final Context ctx, final ThemeManager.Theme theme,
                            final PatternStorage storage, final boolean ru,
                            final Callback callback) {
        final String[] selectedCat = {"all"};
        final String[] searchQuery = {""};
        final Map<String, Bitmap> thumbCache = new HashMap<>();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.hudBgColor);
        int pad = UiHelper.dp(ctx, 14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(ctx);
        title.setText(ru ? "Энциклопедия фигур (50+)" : "Pattern Encyclopedia (50+)");
        title.setTextColor(theme.hudTextColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        EditText search = new EditText(ctx);
        search.setHint(ru ? "Поиск (глайдер, ружьё, пульсар...)" : "Search patterns...");
        search.setTextColor(theme.hudTextColor);
        search.setHintTextColor(theme.hudSubTextColor);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        search.setSingleLine(true);
        search.setPadding(UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 8));
        search.setBackground(UiHelper.roundedBox(theme.bgColor, theme.hudBorderColor, UiHelper.dp(ctx, 1), UiHelper.dp(ctx, 10)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = UiHelper.dp(ctx, 8);
        slp.bottomMargin = UiHelper.dp(ctx, 8);
        search.setLayoutParams(slp);
        root.addView(search);

        HorizontalScrollView chipScroll = new HorizontalScrollView(ctx);
        chipScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout chipRow = new LinearLayout(ctx);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chipRow);
        root.addView(chipScroll);

        ScrollView listScroll = new ScrollView(ctx);
        LinearLayout.LayoutParams lslp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 360));
        lslp.topMargin = UiHelper.dp(ctx, 8);
        listScroll.setLayoutParams(lslp);
        final LinearLayout listContainer = new LinearLayout(ctx);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(listContainer);
        root.addView(listScroll);

        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(root)
                .setNegativeButton(ru ? "Закрыть" : "Close", null)
                .create();

        final Runnable[] rebuildChips = new Runnable[1];
        final Runnable[] rebuildListRef = new Runnable[1];
        final Runnable rebuildList = new Runnable() {
            @Override
            public void run() {
                listContainer.removeAllViews();
                String q = searchQuery[0].trim().toLowerCase();
                String cat = selectedCat[0];

                if ("all".equals(cat) || "custom".equals(cat)) {
                    final List<PatternStorage.CustomPattern> customs = storage.loadCustomPatterns();
                    for (int i = 0; i < customs.size(); i++) {
                        final PatternStorage.CustomPattern cp = customs.get(i);
                        if (!q.isEmpty() && !cp.name.toLowerCase().contains(q)) continue;
                        RleCodec.DecodedPattern dp = RleCodec.decodeAuto(cp.rle);
                        final CellRegion reg = dp.region;
                        final RuleSet rule = dp.rule != null ? dp.rule : RuleSet.CONWAY;
                        addPatternRow(ctx, listContainer, theme, ru, thumbCache, cp.id,
                                cp.name,
                                (ru ? "Мои шаблоны · " : "Custom · ") + reg.width + "×" + reg.height + " · " + reg.liveCount() + (ru ? " кл." : " cells"),
                                reg, cp.rle, rule, true,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        dialog.dismiss();
                                        callback.onPlaceAsStamp(reg, cp.name, rule);
                                    }
                                },
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        dialog.dismiss();
                                        callback.onLoadCentered(reg, cp.name, rule);
                                    }
                                },
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        List<PatternStorage.CustomPattern> cur = storage.loadCustomPatterns();
                                        for (int k = 0; k < cur.size(); k++) {
                                            if (cur.get(k).id.equals(cp.id)) {
                                                cur.remove(k);
                                                break;
                                            }
                                        }
                                        storage.saveCustomPatterns(cur);
                                        rebuildListRef[0].run();
                                    }
                                });
                    }
                    if ("custom".equals(cat) && customs.isEmpty()) {
                        TextView empty = new TextView(ctx);
                        empty.setText(ru ? "Пока нет сохранённых шаблонов. Выделите область на поле и нажмите «Сохранить»."
                                : "No custom patterns yet. Select an area on the grid and tap Save.");
                        empty.setTextColor(theme.hudSubTextColor);
                        empty.setPadding(UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 20));
                        listContainer.addView(empty);
                    }
                }

                if (!"custom".equals(cat)) {
                    for (final PatternLibrary.PatternEntry entry : PatternLibrary.getAll()) {
                        if (!"all".equals(cat) && !entry.category.id.equals(cat)) continue;
                        String nm = entry.getName(ru);
                        String sub = entry.getSubtitle(ru);
                        if (!q.isEmpty() && !nm.toLowerCase().contains(q)
                                && !sub.toLowerCase().contains(q)
                                && !entry.nameEn.toLowerCase().contains(q)
                                && !entry.nameRu.toLowerCase().contains(q)) {
                            continue;
                        }
                        final CellRegion reg = entry.getRegion();
                        final RuleSet rule = entry.getRule();
                        String meta = reg.width + "×" + reg.height + " · " + reg.liveCount()
                                + (ru ? " кл. — " : " cells — ") + sub;
                        addPatternRow(ctx, listContainer, theme, ru, thumbCache, entry.id, nm, meta, reg, entry.rle, rule, false,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        dialog.dismiss();
                                        callback.onPlaceAsStamp(reg, entry.getName(ru), rule);
                                    }
                                },
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        dialog.dismiss();
                                        callback.onLoadCentered(reg, entry.getName(ru), rule);
                                    }
                                },
                                null);
                    }
                }
            }
        };
        rebuildListRef[0] = rebuildList;

        rebuildChips[0] = new Runnable() {
            @Override
            public void run() {
                chipRow.removeAllViews();
                addCatChip(ctx, chipRow, theme, "all", ru ? "Все" : "All", selectedCat, rebuildChips[0], rebuildList);
                addCatChip(ctx, chipRow, theme, "custom", ru ? "Мои" : "Custom", selectedCat, rebuildChips[0], rebuildList);
                for (PatternLibrary.Category c : PatternLibrary.Category.values()) {
                    addCatChip(ctx, chipRow, theme, c.id, c.getTitle(ru), selectedCat, rebuildChips[0], rebuildList);
                }
            }
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery[0] = s != null ? s.toString() : "";
                rebuildList.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        rebuildChips[0].run();
        rebuildList.run();
        dialog.show();
    }

    private static void addCatChip(Context ctx, LinearLayout row, ThemeManager.Theme theme,
                                   final String id, String label, final String[] selectedCat,
                                   final Runnable rebuildChips, final Runnable rebuildList) {
        boolean active = id.equals(selectedCat[0]);
        TextView chip = UiHelper.makePillButton(
                ctx, label,
                active ? theme.accentTextColor : theme.hudTextColor,
                active ? theme.accentColor : theme.bgColor,
                active ? theme.accentColor : theme.hudBorderColor,
                12f, 12, 6);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UiHelper.dp(ctx, 6);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedCat[0] = id;
                rebuildChips.run();
                rebuildList.run();
            }
        });
        row.addView(chip);
    }

    private static void addPatternRow(final Context ctx, LinearLayout container, final ThemeManager.Theme theme,
                                      final boolean ru, final Map<String, Bitmap> thumbCache, final String cacheKey,
                                      final String name, String subtitle,
                                      final CellRegion region, final String rle, final RuleSet rule,
                                      final boolean isCustom,
                                      final Runnable onStamp, final Runnable onCenter, final Runnable onDelete) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = UiHelper.dp(ctx, 8);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(UiHelper.ripplePill(theme.bgColor, theme.hudBorderColor, UiHelper.dp(ctx, 1), UiHelper.dp(ctx, 12), 0x22FFFFFF));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = UiHelper.dp(ctx, 7);
        card.setLayoutParams(clp);

        ImageView thumb = new ImageView(ctx);
        int thumbSz = UiHelper.dp(ctx, 54);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(thumbSz, thumbSz);
        tlp.rightMargin = UiHelper.dp(ctx, 10);
        thumb.setLayoutParams(tlp);
        Bitmap bmp = thumbCache.get(cacheKey);
        if (bmp == null) {
            bmp = UiHelper.renderRegionThumbnail(region, theme, thumbSz);
            thumbCache.put(cacheKey, bmp);
        }
        thumb.setImageBitmap(bmp);
        card.addView(thumb);

        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(ctx);
        tvName.setText(name);
        tvName.setTextColor(theme.hudTextColor);
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        info.addView(tvName);

        TextView tvSub = new TextView(ctx);
        tvSub.setText(subtitle);
        tvSub.setTextColor(theme.hudSubTextColor);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        info.addView(tvSub);

        card.addView(info);

        TextView btnStamp = UiHelper.makePillButton(ctx, ru ? "Штамп" : "Stamp",
                theme.accentTextColor, theme.accentColor, theme.accentColor, 11f, 10, 6);
        btnStamp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStamp.run();
            }
        });
        card.addView(btnStamp);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] opts = isCustom
                        ? new String[]{
                        ru ? "Разместить штампом" : "Place as Stamp",
                        ru ? "Очистить и поставить по центру" : "Clear & Load Centered",
                        ru ? "Копировать RLE" : "Copy RLE",
                        ru ? "Удалить шаблон" : "Delete"}
                        : new String[]{
                        ru ? "Разместить штампом" : "Place as Stamp",
                        ru ? "Очистить и поставить по центру" : "Clear & Load Centered",
                        ru ? "Копировать RLE" : "Copy RLE"};
                new AlertDialog.Builder(ctx)
                        .setTitle(name)
                        .setItems(opts, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (which == 0) onStamp.run();
                                else if (which == 1) onCenter.run();
                                else if (which == 2) {
                                    ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                                    if (cm != null) {
                                        cm.setPrimaryClip(ClipData.newPlainText("GameOfLife RLE", rle));
                                        Toast.makeText(ctx, ru ? "RLE скопирован" : "RLE copied", Toast.LENGTH_SHORT).show();
                                    }
                                } else if (which == 3 && onDelete != null) {
                                    onDelete.run();
                                }
                            }
                        }).show();
            }
        });

        container.addView(card);
    }
}
