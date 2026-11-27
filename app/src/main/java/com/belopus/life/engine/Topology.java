package com.belopus.life.engine;

public enum Topology {
    INFINITE("infinite", "Бесконечное", "Infinite"),
    TOROIDAL("toroidal", "Тор (замкнутое)", "Toroidal"),
    BOUNDED("bounded", "Стены (огранич.)", "Bounded");

    public final String id;
    public final String titleRu;
    public final String titleEn;

    Topology(String id, String titleRu, String titleEn) {
        this.id = id;
        this.titleRu = titleRu;
        this.titleEn = titleEn;
    }

    public String getTitle(boolean ru) {
        return ru ? titleRu : titleEn;
    }

    public static Topology fromId(String id) {
        if (id == null) return INFINITE;
        for (Topology t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return INFINITE;
    }
}
