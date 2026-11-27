package com.belopus.life.engine;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RuleSet {
    public final int birthMask;    // bit i = birth on i neighbors (0..8)
    public final int survivalMask; // bit i = survive on i neighbors (0..8)
    public final String nameRu;
    public final String nameEn;
    public final String descriptionRu;
    public final String descriptionEn;

    public static final RuleSet CONWAY = new RuleSet(
            mask(3), mask(2, 3),
            "Классическая Жизнь (B3/S23)", "Conway's Game of Life (B3/S23)",
            "Оригинальные правила Джона Конвея: рождение при 3 соседях, выживание при 2 или 3.",
            "Original Conway rules: birth on 3 neighbors, survival on 2 or 3.");

    public static final RuleSet HIGH_LIFE = new RuleSet(
            mask(3, 6), mask(2, 3),
            "HighLife (B36/S23)", "HighLife (B36/S23)",
            "Ближайший аналог Жизни: добавлено рождение при 6 соседях, существует самокопирующийся Репликатор.",
            "Close variant with birth on 6 neighbors; supports a self-copying Replicator.");

    public static final RuleSet DAY_NIGHT = new RuleSet(
            mask(3, 6, 7, 8), mask(3, 4, 6, 7, 8),
            "День и Ночь (B3678/S34678)", "Day & Night (B3678/S34678)",
            "Симметричное правило: инверсия живых и мёртвых клеток даёт эквивалентную динамику.",
            "Time/state-symmetric rule: live and dead roles are exact duals.");

    public static final RuleSet SEEDS = new RuleSet(
            mask(2), 0,
            "Семена (B2/S)", "Seeds (B2/S)",
            "Все живые клетки умирают каждый такт, порождая взрывные фотонные потоки и космические корабли.",
            "Every live cell dies each generation, spawning chaotic ships and waves.");

    public static final RuleSet LIFE_WITHOUT_DEATH = new RuleSet(
            mask(3), mask(0, 1, 2, 3, 4, 5, 6, 7, 8),
            "Жизнь без смерти (B3/S0..8)", "Life Without Death (B3/S0..8)",
            "Живые клетки никогда не умирают; образует фрактальные, коралловые и лабиринтные структуры.",
            "Cells never die once born, growing fractal and coral-like patterns.");

    public static final RuleSet DIAMOEBA = new RuleSet(
            mask(3, 5, 6, 7, 8), mask(5, 6, 7, 8),
            "Диамеба (B35678/S5678)", "Diamoeba (B35678/S5678)",
            "Образует растущие органические ромбовидные амебы со сложными границами.",
            "Forms growing diamond-shaped organic blobs with fluctuating edges.");

    public static final RuleSet MORLEY = new RuleSet(
            mask(3, 6, 8), mask(2, 4, 5),
            "Морли (B368/S245)", "Morley (B368/S245)",
            "Поддерживает естественные паровозы, пушки и сложные космические корабли.",
            "Rich rule supporting puffers, guns, and natural spaceships.");

    public static final RuleSet ANNEAL = new RuleSet(
            mask(4, 6, 7, 8), mask(3, 5, 6, 7, 8),
            "Отжиг (B4678/S35678)", "Anneal (B4678/S35678)",
            "Моделирует кристаллическую релаксацию и сглаживание доменов.",
            "Models domain relaxation and majority-like boundary smoothing.");

    public static final RuleSet MAZE = new RuleSet(
            mask(3), mask(1, 2, 3, 4, 5),
            "Лабиринт (B3/S12345)", "Maze (B3/S12345)",
            "Самоорганизующиеся стабильные лабиринты из любого случайного супа.",
            "Crystallizes random soups into intricate static mazes.");

    public static final RuleSet REPLICATOR = new RuleSet(
            mask(1, 3, 5, 7), mask(1, 3, 5, 7),
            "Репликатор (B1357/S1357)", "Replicator (B1357/S1357)",
            "Любой узор бесконечно самовоспроизводится в симметричной решётке.",
            "Every finite pattern replicates itself infinitely across the grid.");

    public static final List<RuleSet> PRESETS = Collections.unmodifiableList(Arrays.asList(
            CONWAY, HIGH_LIFE, DAY_NIGHT, SEEDS, LIFE_WITHOUT_DEATH,
            DIAMOEBA, MORLEY, ANNEAL, MAZE, REPLICATOR
    ));

    public RuleSet(int birthMask, int survivalMask, String nameRu, String nameEn,
                   String descriptionRu, String descriptionEn) {
        this.birthMask = birthMask & 0x1FF;
        this.survivalMask = survivalMask & 0x1FF;
        this.nameRu = nameRu;
        this.nameEn = nameEn;
        this.descriptionRu = descriptionRu;
        this.descriptionEn = descriptionEn;
    }

    public RuleSet(int birthMask, int survivalMask) {
        this.birthMask = birthMask & 0x1FF;
        this.survivalMask = survivalMask & 0x1FF;
        RuleSet matched = findPreset(this.birthMask, this.survivalMask);
        if (matched != null) {
            this.nameRu = matched.nameRu;
            this.nameEn = matched.nameEn;
            this.descriptionRu = matched.descriptionRu;
            this.descriptionEn = matched.descriptionEn;
        } else {
            String code = formatMask(this.birthMask, this.survivalMask);
            this.nameRu = "Пользовательское (" + code + ")";
            this.nameEn = "Custom (" + code + ")";
            this.descriptionRu = "Пользовательская конфигурация B/S.";
            this.descriptionEn = "Custom outer-totalistic B/S configuration.";
        }
    }

    public static int mask(int... counts) {
        int m = 0;
        for (int c : counts) {
            if (c >= 0 && c <= 8) m |= (1 << c);
        }
        return m;
    }

    public boolean isBirth(int neighbors) {
        return ((birthMask >> neighbors) & 1) != 0;
    }

    public boolean isSurvival(int neighbors) {
        return ((survivalMask >> neighbors) & 1) != 0;
    }

    public boolean hasB0() {
        return (birthMask & 1) != 0;
    }

    public boolean isClassicConway() {
        return birthMask == CONWAY.birthMask && survivalMask == CONWAY.survivalMask;
    }

    public String getRuleCode() {
        return formatMask(birthMask, survivalMask);
    }

    public String getName(boolean ru) {
        return ru ? nameRu : nameEn;
    }

    public String getDescription(boolean ru) {
        return ru ? descriptionRu : descriptionEn;
    }

    public static String formatMask(int bMask, int sMask) {
        StringBuilder sb = new StringBuilder();
        sb.append('B');
        for (int i = 0; i <= 8; i++) {
            if (((bMask >> i) & 1) != 0) sb.append(i);
        }
        sb.append("/S");
        for (int i = 0; i <= 8; i++) {
            if (((sMask >> i) & 1) != 0) sb.append(i);
        }
        return sb.toString();
    }

    public static RuleSet findPreset(int bMask, int sMask) {
        for (RuleSet r : PRESETS) {
            if (r.birthMask == (bMask & 0x1FF) && r.survivalMask == (sMask & 0x1FF)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Parses both "B3/S23" and "23/3" (S/B) notation. Returns null if invalid.
     */
    public static RuleSet parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase().replace(" ", "");
        if (s.isEmpty()) return null;

        int bMask = 0;
        int sMask = 0;

        if (s.contains("B") || s.contains("S")) {
            String[] parts = s.split("/");
            for (String p : parts) {
                if (p.isEmpty()) continue;
                if (p.startsWith("B")) {
                    for (int i = 1; i < p.length(); i++) {
                        char c = p.charAt(i);
                        if (c >= '0' && c <= '8') bMask |= (1 << (c - '0'));
                        else return null;
                    }
                } else if (p.startsWith("S")) {
                    for (int i = 1; i < p.length(); i++) {
                        char c = p.charAt(i);
                        if (c >= '0' && c <= '8') sMask |= (1 << (c - '0'));
                        else return null;
                    }
                } else {
                    return null;
                }
            }
            return new RuleSet(bMask, sMask);
        }

        // Legacy S/B notation e.g. "23/3"
        String[] parts = s.split("/");
        if (parts.length == 2) {
            for (int i = 0; i < parts[0].length(); i++) {
                char c = parts[0].charAt(i);
                if (c >= '0' && c <= '8') sMask |= (1 << (c - '0'));
                else return null;
            }
            for (int i = 0; i < parts[1].length(); i++) {
                char c = parts[1].charAt(i);
                if (c >= '0' && c <= '8') bMask |= (1 << (c - '0'));
                else return null;
            }
            return new RuleSet(bMask, sMask);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuleSet)) return false;
        RuleSet other = (RuleSet) o;
        return birthMask == other.birthMask && survivalMask == other.survivalMask;
    }

    @Override
    public int hashCode() {
        return (birthMask << 9) | survivalMask;
    }
}
