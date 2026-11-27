package com.belopus.life.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.belopus.life.engine.CellRegion;
import com.belopus.life.engine.LifeEngine;
import com.belopus.life.engine.RleCodec;
import com.belopus.life.engine.RuleSet;
import com.belopus.life.engine.Topology;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class PatternStorage {

    private static final String PREF_NAME = "life_game_prefs";
    public static final int SLOT_COUNT = 6;

    public static final class AppSettings {
        public boolean languageRu = true;
        public String themeId = "obsidian";
        public int cellShape = 1; // 0=square, 1=rounded, 2=circle
        public boolean ageColoring = true;
        public int decaySteps = 8;
        public boolean showGrid = true;
        public boolean showAxes = true;
        public boolean showMinimap = true;
        public boolean autoPauseOnStable = false;
        public boolean soundEnabled = false;
        public float simSpeed = 12f; // gen/s
        public Topology topology = Topology.INFINITE;
        public int gridWidth = 128;
        public int gridHeight = 128;
        public String ruleCode = "B3/S23";
    }

    public static final class SavedSlot {
        public int index;
        public String name;
        public String rle;
        public String topologyId;
        public int gridWidth;
        public int gridHeight;
        public int originX;
        public int originY;
        public long generation;
        public long liveCount;
        public String ruleCode;
        public long savedAtMs;
    }

    public static final class CustomPattern {
        public String id;
        public String name;
        public String rle;
        public long createdAtMs;

        public CellRegion decodeRegion() {
            return RleCodec.decodeAuto(rle).region;
        }
    }

    private final SharedPreferences prefs;

    public PatternStorage(Context ctx) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public AppSettings loadSettings() {
        AppSettings s = new AppSettings();
        s.languageRu = prefs.getBoolean("lang_ru", true);
        s.themeId = prefs.getString("theme_id", "obsidian");
        s.cellShape = prefs.getInt("cell_shape", 1);
        s.ageColoring = prefs.getBoolean("age_coloring", true);
        s.decaySteps = prefs.getInt("decay_steps", 8);
        s.showGrid = prefs.getBoolean("show_grid", true);
        s.showAxes = prefs.getBoolean("show_axes", true);
        s.showMinimap = prefs.getBoolean("show_minimap", true);
        s.autoPauseOnStable = prefs.getBoolean("auto_pause", false);
        s.soundEnabled = prefs.getBoolean("sound_enabled", false);
        s.simSpeed = prefs.getFloat("sim_speed", 12f);
        s.topology = Topology.fromId(prefs.getString("topology", Topology.INFINITE.id));
        s.gridWidth = prefs.getInt("grid_w", 128);
        s.gridHeight = prefs.getInt("grid_h", 128);
        s.ruleCode = prefs.getString("rule_code", "B3/S23");
        return s;
    }

    public void saveSettings(AppSettings s) {
        prefs.edit()
                .putBoolean("lang_ru", s.languageRu)
                .putString("theme_id", s.themeId)
                .putInt("cell_shape", s.cellShape)
                .putBoolean("age_coloring", s.ageColoring)
                .putInt("decay_steps", s.decaySteps)
                .putBoolean("show_grid", s.showGrid)
                .putBoolean("show_axes", s.showAxes)
                .putBoolean("show_minimap", s.showMinimap)
                .putBoolean("auto_pause", s.autoPauseOnStable)
                .putBoolean("sound_enabled", s.soundEnabled)
                .putFloat("sim_speed", s.simSpeed)
                .putString("topology", s.topology.id)
                .putInt("grid_w", s.gridWidth)
                .putInt("grid_h", s.gridHeight)
                .putString("rule_code", s.ruleCode)
                .apply();
    }

    public void saveAutosave(LifeEngine engine) {
        if (engine.getStats().liveCount > 80_000) return; // avoid bloating SharedPrefs on huge spacefillers
        CellRegion reg = engine.extractAllLiveBounds();
        String rle = RleCodec.encodeRle(reg, engine.getRule(), "Autosave", "");
        prefs.edit()
                .putBoolean("has_autosave", true)
                .putString("auto_rle", rle)
                .putInt("auto_ox", engine.getStats().hasBounds ? engine.getStats().minX : 0)
                .putInt("auto_oy", engine.getStats().hasBounds ? engine.getStats().minY : 0)
                .putString("auto_top", engine.getTopology().id)
                .putInt("auto_w", engine.getGridWidth())
                .putInt("auto_h", engine.getGridHeight())
                .putString("auto_rule", engine.getRule().getRuleCode())
                .putLong("auto_gen", engine.getStats().generation)
                .apply();
    }

    public boolean restoreAutosave(LifeEngine engine) {
        if (!prefs.getBoolean("has_autosave", false)) return false;
        String rle = prefs.getString("auto_rle", "");
        Topology top = Topology.fromId(prefs.getString("auto_top", Topology.INFINITE.id));
        int w = prefs.getInt("auto_w", 128);
        int h = prefs.getInt("auto_h", 128);
        RuleSet rule = RuleSet.parse(prefs.getString("auto_rule", "B3/S23"));
        if (rule == null) rule = RuleSet.CONWAY;
        int ox = prefs.getInt("auto_ox", 0);
        int oy = prefs.getInt("auto_oy", 0);
        long gen = prefs.getLong("auto_gen", 0L);

        engine.configureTopology(top, w, h, false);
        engine.setRule(rule);
        RleCodec.DecodedPattern dp = RleCodec.decodeAuto(rle);
        if (!dp.region.isEmpty()) {
            engine.pasteRegion(dp.region, ox, oy, true, false);
        }
        engine.getStats().generation = gen;
        return true;
    }

    public void saveSlot(int slotIndex, String name, LifeEngine engine) {
        CellRegion reg = engine.extractAllLiveBounds();
        String rle = RleCodec.encodeRle(reg, engine.getRule(), name, "");
        try {
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("rle", rle);
            obj.put("top", engine.getTopology().id);
            obj.put("w", engine.getGridWidth());
            obj.put("h", engine.getGridHeight());
            obj.put("ox", engine.getStats().hasBounds ? engine.getStats().minX : 0);
            obj.put("oy", engine.getStats().hasBounds ? engine.getStats().minY : 0);
            obj.put("gen", engine.getStats().generation);
            obj.put("pop", engine.getStats().liveCount);
            obj.put("rule", engine.getRule().getRuleCode());
            obj.put("ts", System.currentTimeMillis());
            prefs.edit().putString("slot_" + slotIndex, obj.toString()).apply();
        } catch (Exception ignored) {}
    }

    public SavedSlot loadSlot(int slotIndex) {
        String raw = prefs.getString("slot_" + slotIndex, null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(raw);
            SavedSlot slot = new SavedSlot();
            slot.index = slotIndex;
            slot.name = obj.optString("name", "Slot " + (slotIndex + 1));
            slot.rle = obj.optString("rle", "");
            slot.topologyId = obj.optString("top", Topology.INFINITE.id);
            slot.gridWidth = obj.optInt("w", 128);
            slot.gridHeight = obj.optInt("h", 128);
            slot.originX = obj.optInt("ox", 0);
            slot.originY = obj.optInt("oy", 0);
            slot.generation = obj.optLong("gen", 0);
            slot.liveCount = obj.optLong("pop", 0);
            slot.ruleCode = obj.optString("rule", "B3/S23");
            slot.savedAtMs = obj.optLong("ts", 0);
            return slot;
        } catch (Exception e) {
            return null;
        }
    }

    public void deleteSlot(int slotIndex) {
        prefs.edit().remove("slot_" + slotIndex).apply();
    }

    public List<CustomPattern> loadCustomPatterns() {
        List<CustomPattern> list = new ArrayList<>();
        String raw = prefs.getString("custom_patterns", "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                CustomPattern cp = new CustomPattern();
                cp.id = o.optString("id", "cp_" + i);
                cp.name = o.optString("name", "Pattern");
                cp.rle = o.optString("rle", "");
                cp.createdAtMs = o.optLong("ts", 0);
                if (!cp.rle.isEmpty()) list.add(cp);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void saveCustomPatterns(List<CustomPattern> list) {
        JSONArray arr = new JSONArray();
        try {
            for (CustomPattern cp : list) {
                JSONObject o = new JSONObject();
                o.put("id", cp.id);
                o.put("name", cp.name);
                o.put("rle", cp.rle);
                o.put("ts", cp.createdAtMs);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        prefs.edit().putString("custom_patterns", arr.toString()).apply();
    }

    public void addCustomPattern(String name, CellRegion region, RuleSet rule) {
        List<CustomPattern> list = loadCustomPatterns();
        CustomPattern cp = new CustomPattern();
        cp.id = "cp_" + System.currentTimeMillis();
        cp.name = name;
        cp.rle = RleCodec.encodeRle(region, rule, name, "");
        cp.createdAtMs = System.currentTimeMillis();
        list.add(0, cp);
        saveCustomPatterns(list);
    }
}
