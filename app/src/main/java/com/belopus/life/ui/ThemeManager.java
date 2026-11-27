package com.belopus.life.ui;

import android.graphics.Color;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ThemeManager {

    public static final class Theme {
        public final String id;
        public final String nameRu;
        public final String nameEn;
        public final boolean isLight;
        public final int bgColor;
        public final int outOfBoundsColor;
        public final int gridLineColor;
        public final int majorGridLineColor;
        public final int axisColor;
        public final int borderColor;
        public final int cellYoungColor;
        public final int cellMatureColor;
        public final int cellAncientColor;
        public final int decayColor;
        public final int hudBgColor;
        public final int hudBorderColor;
        public final int hudTextColor;
        public final int hudSubTextColor;
        public final int accentColor;
        public final int accentTextColor;
        public final int selectionColor;

        public Theme(String id, String nameRu, String nameEn, boolean isLight,
                     int bgColor, int outOfBoundsColor, int gridLineColor, int majorGridLineColor,
                     int axisColor, int borderColor,
                     int cellYoungColor, int cellMatureColor, int cellAncientColor, int decayColor,
                     int hudBgColor, int hudBorderColor, int hudTextColor, int hudSubTextColor,
                     int accentColor, int accentTextColor, int selectionColor) {
            this.id = id;
            this.nameRu = nameRu;
            this.nameEn = nameEn;
            this.isLight = isLight;
            this.bgColor = bgColor;
            this.outOfBoundsColor = outOfBoundsColor;
            this.gridLineColor = gridLineColor;
            this.majorGridLineColor = majorGridLineColor;
            this.axisColor = axisColor;
            this.borderColor = borderColor;
            this.cellYoungColor = cellYoungColor;
            this.cellMatureColor = cellMatureColor;
            this.cellAncientColor = cellAncientColor;
            this.decayColor = decayColor;
            this.hudBgColor = hudBgColor;
            this.hudBorderColor = hudBorderColor;
            this.hudTextColor = hudTextColor;
            this.hudSubTextColor = hudSubTextColor;
            this.accentColor = accentColor;
            this.accentTextColor = accentTextColor;
            this.selectionColor = selectionColor;
        }

        public String getName(boolean ru) {
            return ru ? nameRu : nameEn;
        }

        public int colorForCell(byte state, int decayMax, boolean ageColoring) {
            if (state > 0) {
                if (!ageColoring || state == 1) {
                    return cellYoungColor;
                }
                if (state <= 10) {
                    float t = (state - 1) / 9f;
                    return lerpColor(cellYoungColor, cellMatureColor, t);
                } else {
                    float t = Math.min(1f, (state - 10) / 40f);
                    return lerpColor(cellMatureColor, cellAncientColor, t);
                }
            } else if (state < 0 && decayMax > 0) {
                float frac = Math.max(0.08f, Math.min(1f, (-state) / (float) decayMax));
                int a = Math.round(Color.alpha(decayColor) * frac);
                return Color.argb(a, Color.red(decayColor), Color.green(decayColor), Color.blue(decayColor));
            }
            return Color.TRANSPARENT;
        }
    }

    public static final Theme OBSIDIAN = new Theme(
            "obsidian", "Обсидиан Неон", "Obsidian Neon", false,
            0xFF0A0E14, 0xFF05070B, 0xFF151E2B, 0xFF202E42,
            0x444FC3F7, 0xFF00E676,
            0xFF7CFFB2, 0xFF00E676, 0xFF00B0FF, 0x7000A86B,
            0xE6121822, 0xFF253244, 0xFFF0F6FC, 0xFF8B9CB3,
            0xFF00E676, 0xFF06160E, 0xFF38BDF8
    );

    public static final Theme MATRIX = new Theme(
            "matrix", "Матрица CRT", "Matrix CRT", false,
            0xFF040905, 0xFF020402, 0xFF0E2112, 0xFF17361E,
            0x4439FF14, 0xFF39FF14,
            0xFFB9FF9E, 0xFF39FF14, 0xFF009E2A, 0x66168027,
            0xE609140B, 0xFF1D4023, 0xFFDDFCE0, 0xFF72B07B,
            0xFF39FF14, 0xFF031704, 0xFF80FF72
    );

    public static final Theme CLASSIC_PAPER = new Theme(
            "paper", "Бумага (Светлая)", "Classic Paper", true,
            0xFFF7F5F0, 0xFFE5E1D8, 0xFFE3DFD6, 0xFFC9C4B8,
            0x552563EB, 0xFF1E293B,
            0xFF0F172A, 0xFF1E293B, 0xFF334155, 0x453B82F6,
            0xEBFFFFFF, 0xFFD4CFC4, 0xFF0F172A, 0xFF64748B,
            0xFF2563EB, 0xFFFFFFFF, 0xFFEA580C
    );

    public static final Theme SOLAR_AMBER = new Theme(
            "amber", "Солнечный янтарь", "Solar Amber", false,
            0xFF120F0B, 0xFF090705, 0xFF241D14, 0xFF352A1D,
            0x44FFB300, 0xFFFFB300,
            0xFFFFE57F, 0xFFFFB300, 0xFFFF6D00, 0x66FF8F00,
            0xE61C1711, 0xFF3D3122, 0xFFFFF8E7, 0xFFB8A386,
            0xFFFFB300, 0xFF1F1400, 0xFFFF7043
    );

    public static final Theme BLUEPRINT = new Theme(
            "blueprint", "Чертёж (Blueprint)", "Blueprint", false,
            0xFF0B1E3D, 0xFF071328, 0xFF15325E, 0xFF204885,
            0x5593C5FD, 0xFF60A5FA,
            0xFFFFFFFF, 0xFFBAE6FD, 0xFF38BDF8, 0x6038BDF8,
            0xEB0E264D, 0xFF285294, 0xFFF0F9FF, 0xFF93C5FD,
            0xFF38BDF8, 0xFF041C2C, 0xFFFBBF24
    );

    public static final Theme AGE_SPECTRUM = new Theme(
            "spectrum", "Спектр возраста", "Age Spectrum", false,
            0xFF0B0A14, 0xFF06050B, 0xFF1B1830, 0xFF2A254A,
            0x44C084FC, 0xFFA855F7,
            0xFF67E8F9, 0xFF34D399, 0xFFF472B6, 0x66818CF8,
            0xE6141224, 0xFF2F2A52, 0xFFF5F3FF, 0xFFA79EC9,
            0xFFA855F7, 0xFFFFFFFF, 0xFF38BDF8
    );

    public static final List<Theme> THEMES = Collections.unmodifiableList(Arrays.asList(
            OBSIDIAN, MATRIX, CLASSIC_PAPER, SOLAR_AMBER, BLUEPRINT, AGE_SPECTRUM
    ));

    private ThemeManager() {}

    public static Theme byId(String id) {
        if (id != null) {
            for (Theme t : THEMES) {
                if (t.id.equalsIgnoreCase(id)) return t;
            }
        }
        return OBSIDIAN;
    }

    public static int lerpColor(int c0, int c1, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int a = Math.round(Color.alpha(c0) + (Color.alpha(c1) - Color.alpha(c0)) * k);
        int r = Math.round(Color.red(c0) + (Color.red(c1) - Color.red(c0)) * k);
        int g = Math.round(Color.green(c0) + (Color.green(c1) - Color.green(c0)) * k);
        int b = Math.round(Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * k);
        return Color.argb(a, r, g, b);
    }
}
