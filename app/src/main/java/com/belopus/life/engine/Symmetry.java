package com.belopus.life.engine;

public enum Symmetry {
    NONE("none", "Выкл", "Off"),
    MIRROR_X("mirror_x", "Зеркало ↔", "Mirror X"),
    MIRROR_Y("mirror_y", "Зеркало ↕", "Mirror Y"),
    FOUR_WAY("four_way", "4-луч. ✛", "4-Way"),
    EIGHT_WAY("eight_way", "8-луч. ✳", "8-Way");

    public final String id;
    public final String titleRu;
    public final String titleEn;

    Symmetry(String id, String titleRu, String titleEn) {
        this.id = id;
        this.titleRu = titleRu;
        this.titleEn = titleEn;
    }

    public String getTitle(boolean ru) {
        return ru ? titleRu : titleEn;
    }

    public interface PointConsumer {
        void accept(int x, int y);
    }

    /**
     * Applies symmetry around pivot = (pivotX2 / 2.0, pivotY2 / 2.0).
     * Passing doubled pivot coordinates allows symmetric centering around both
     * cell centers (even pivot2) and cell boundaries (odd pivot2).
     */
    public void apply(int x, int y, int pivotX2, int pivotY2, PointConsumer consumer) {
        consumer.accept(x, y);
        if (this == NONE) return;

        int mx = pivotX2 - x;
        int my = pivotY2 - y;

        switch (this) {
            case MIRROR_X:
                if (mx != x) consumer.accept(mx, y);
                break;
            case MIRROR_Y:
                if (my != y) consumer.accept(x, my);
                break;
            case FOUR_WAY:
                if (mx != x) consumer.accept(mx, y);
                if (my != y) consumer.accept(x, my);
                if (mx != x && my != y) consumer.accept(mx, my);
                break;
            case EIGHT_WAY:
                // Enforce consistent parity so diagonal swap stays integer-aligned.
                int adjPy2 = ((pivotX2 & 1) == (pivotY2 & 1)) ? pivotY2 : (pivotY2 - 1);
                int adjMy = adjPy2 - y;
                consumer.accept(mx, y);
                consumer.accept(x, adjMy);
                consumer.accept(mx, adjMy);

                int dx2 = 2 * x - pivotX2;
                int dy2 = 2 * y - adjPy2;
                int sx = (pivotX2 + dy2) >> 1;
                int sy = (adjPy2 + dx2) >> 1;
                int smx = pivotX2 - sx;
                int smy = adjPy2 - sy;
                consumer.accept(sx, sy);
                consumer.accept(smx, sy);
                consumer.accept(sx, smy);
                consumer.accept(smx, smy);
                break;
            default:
                break;
        }
    }

    public static Symmetry fromId(String id) {
        if (id == null) return NONE;
        for (Symmetry s : values()) {
            if (s.id.equalsIgnoreCase(id)) return s;
        }
        return NONE;
    }

    public Symmetry next() {
        Symmetry[] vals = values();
        return vals[(ordinal() + 1) % vals.length];
    }
}
