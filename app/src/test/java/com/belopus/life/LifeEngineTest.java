package com.belopus.life;

import com.belopus.life.engine.*;

public class LifeEngineTest {

    private static int passed = 0;
    private static int failed = 0;

    private static void check(boolean cond, String msg) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.err.println("FAIL: " + msg);
        }
    }

    public static void main(String[] args) {
        testRuleParsing();
        testSymmetry();
        testRleRoundTrip();
        testPlaintextParsing();
        testTopologies();
        testUndoRedoAndGen0();
        testMethuselahMilestones();
        testAllLibraryPatterns();

        System.out.println("\n=== Engine Test Summary: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }

    private static void testRuleParsing() {
        RuleSet c = RuleSet.parse("B3/S23");
        check(c != null && c.equals(RuleSet.CONWAY), "Parse B3/S23");
        RuleSet sb = RuleSet.parse("23/3");
        check(sb != null && sb.equals(RuleSet.CONWAY), "Parse 23/3 legacy S/B notation");
        RuleSet hl = RuleSet.parse("b36/s23");
        check(hl != null && hl.equals(RuleSet.HIGH_LIFE), "Parse lowercase b36/s23");
        RuleSet seeds = RuleSet.parse("B2/S");
        check(seeds != null && seeds.equals(RuleSet.SEEDS), "Parse B2/S empty survival");
        check(RuleSet.parse("invalid") == null, "Reject invalid rule");
    }

    private static void testSymmetry() {
        final int[] count = {0};
        Symmetry.FOUR_WAY.apply(2, 3, 0, 0, (x, y) -> count[0]++);
        check(count[0] == 4, "4-way symmetry produces 4 points");
        count[0] = 0;
        Symmetry.EIGHT_WAY.apply(2, 5, 0, 0, (x, y) -> count[0]++);
        check(count[0] == 8, "8-way symmetry produces 8 points");
    }

    private static void testRleRoundTrip() {
        PatternLibrary.PatternEntry pulsar = PatternLibrary.findById("pulsar");
        check(pulsar != null, "Find pulsar");
        CellRegion r1 = pulsar.getRegion();
        String encoded = RleCodec.encodeRle(r1, RuleSet.CONWAY, "Pulsar", "Test");
        RleCodec.DecodedPattern dp = RleCodec.decodeAuto(encoded);
        check(dp.region.width == r1.width && dp.region.height == r1.height, "RLE round-trip dimensions");
        check(dp.region.liveCount() == r1.liveCount(), "RLE round-trip liveCount");
        for (int y = 0; y < r1.height; y++) {
            for (int x = 0; x < r1.width; x++) {
                if (r1.get(x, y) != dp.region.get(x, y)) {
                    check(false, "RLE round-trip cell mismatch at " + x + "," + y);
                    return;
                }
            }
        }
        check(true, "RLE round-trip exact match");
    }

    private static void testPlaintextParsing() {
        String txt = "!Name: Glider\n.O.\n..O\nOOO\n";
        RleCodec.DecodedPattern dp = RleCodec.decodeAuto(txt);
        check("Glider".equals(dp.name), "Plaintext name parsed");
        check(dp.region.width == 3 && dp.region.height == 3 && dp.region.liveCount() == 5, "Plaintext glider cells");
    }

    private static void testTopologies() {
        // Test toroidal glider wrapping around 16x16 grid (period = 16*4 = 64)
        LifeEngine e = new LifeEngine();
        e.configureTopology(Topology.TOROIDAL, 16, 16, false);
        e.pasteRegion(PatternLibrary.findById("glider").getRegion(), 14, 14, true, false);
        e.stepN(140); // 16 * 4 = 64 -> after 140 gens we have >2 cycles for exact P=64 detection!
        check(e.getStats().liveCount == 5, "Toroidal 16x16 glider survives");
        check(e.getStats().status == SimStats.Status.OSCILLATOR && e.getStats().detectedPeriod == 64,
                "Toroidal 16x16 glider period == 64 (got " + e.getStats().detectedPeriod + ")");

        // Test toroidal 12x12 glider period == 48
        LifeEngine e12 = new LifeEngine();
        e12.configureTopology(Topology.TOROIDAL, 12, 12, false);
        e12.pasteRegion(PatternLibrary.findById("glider").getRegion(), 0, 0, true, false);
        e12.stepN(100); // 12 * 4 = 48
        check(e12.getStats().status == SimStats.Status.OSCILLATOR && e12.getStats().detectedPeriod == 48,
                "Toroidal 12x12 glider period == 48 (got " + e12.getStats().detectedPeriod + ")");

        // Test bounded wall stops glider at corner -> turns into 4-cell block
        LifeEngine eb = new LifeEngine();
        eb.configureTopology(Topology.BOUNDED, 10, 10, false);
        eb.pasteRegion(PatternLibrary.findById("glider").getRegion(), 7, 7, true, false);
        eb.stepN(20);
        check(eb.getStats().liveCount == 4 && eb.getStats().status == SimStats.Status.STILL_LIFE,
                "Bounded glider hitting corner turns into 4-cell block (got pop="
                        + eb.getStats().liveCount + ", status=" + eb.getStats().status + ")");

        // Test infinite glider travels cleanly past negative & large coords
        LifeEngine ei = new LifeEngine();
        ei.configureTopology(Topology.INFINITE, 128, 128, false);
        ei.pasteRegion(PatternLibrary.findById("glider").getRegion(), -5, -5, true, false);
        ei.stepN(400); // moves +100,+100 across multiple 32x32 chunks!
        check(ei.getStats().liveCount == 5, "Infinite glider travels 400 gens across chunks");
        check(ei.getStats().minX == 95 && ei.getStats().minY == 95, "Infinite glider exact pos (95,95)");
    }

    private static void testUndoRedoAndGen0() {
        LifeEngine e = new LifeEngine();
        e.pushUndo();
        e.pasteRegion(PatternLibrary.findById("r_pentomino").getRegion(), 0, 0, true, false);
        e.markCurrentAsGen0();
        check(e.getStats().liveCount == 5 && e.getStats().generation == 0, "Initial Gen 0 setup");
        e.stepN(50);
        check(e.getStats().generation == 50 && e.getStats().liveCount > 5, "Stepped 50 gens");
        check(e.resetToGen0(), "Reset to Gen 0");
        check(e.getStats().generation == 0 && e.getStats().liveCount == 5, "Restored Gen 0 state");
        check(e.undo(), "Undo Reset to Gen 0");
        check(e.getStats().generation == 50, "Undo brought back gen 50");
        check(e.redo(), "Redo Reset to Gen 0");
        check(e.getStats().generation == 0 && e.getStats().liveCount == 5, "Redo restored Gen 0");
    }

    private static void testMethuselahMilestones() {
        // Diehard vanishes on exact gen 130
        LifeEngine diehard = new LifeEngine();
        diehard.pasteRegion(PatternLibrary.findById("diehard").getRegion(), 0, 0, true, false);
        diehard.stepN(129);
        check(diehard.getStats().liveCount > 0, "Diehard still alive at gen 129");
        diehard.step();
        check(diehard.getStats().liveCount == 0 && diehard.getStats().status == SimStats.Status.EXTINCT,
                "Diehard extinct at exact gen 130");

        // R-pentomino stabilizes at gen 1103 with 116 live cells
        LifeEngine rp = new LifeEngine();
        rp.pasteRegion(PatternLibrary.findById("r_pentomino").getRegion(), 0, 0, true, false);
        rp.stepN(1103);
        long pop1103 = rp.getStats().liveCount;
        check(pop1103 == 116, "R-pentomino stabilizes at 116 cells at gen 1103 (got " + pop1103 + ")");
    }

    private static void testAllLibraryPatterns() {
        for (PatternLibrary.PatternEntry entry : PatternLibrary.getAll()) {
            CellRegion reg = entry.getRegion();
            check(reg != null && reg.liveCount() > 0, "Pattern " + entry.id + " non-empty");

            LifeEngine e = new LifeEngine();
            e.setRule(entry.getRule());
            e.pasteRegion(reg, 0, 0, true, false);

            if (entry.expectedPeriod == 1) {
                e.stepN(3);
                check(e.getStats().status == SimStats.Status.STILL_LIFE && e.getStats().liveCount == reg.liveCount(),
                        "Still life verified: " + entry.id + " (got " + e.getStats().status + ", pop " + e.getStats().liveCount + ")");
            } else if (entry.expectedPeriod > 1) {
                int p = entry.expectedPeriod;
                e.stepN(p * 3 + 3);
                check(e.getStats().status == SimStats.Status.OSCILLATOR && e.getStats().detectedPeriod == p,
                        "Oscillator verified: " + entry.id + " expected P=" + p + ", got " + e.getStats().status + " P=" + e.getStats().detectedPeriod + " pop=" + e.getStats().liveCount);
            } else if (entry.isSpaceship) {
                long initPop = reg.liveCount();
                int initMinX = e.getStats().minX;
                int initMinY = e.getStats().minY;
                e.stepN(280); // enough for P=4,7,10 etc.
                boolean moved = (e.getStats().minX != initMinX || e.getStats().minY != initMinY);
                check(moved && e.getStats().liveCount > 0 && e.getStats().status == SimStats.Status.RUNNING,
                        "Spaceship verified: " + entry.id + " pop=" + e.getStats().liveCount + " initPop=" + initPop
                                + " pos=(" + e.getStats().minX + "," + e.getStats().minY + ") status=" + e.getStats().status);
            } else if (entry.id.equals("gosper_gun") || entry.id.equals("simkin_gun")
                    || entry.id.equals("switch_engine_glider") || entry.id.equals("switch_engine_block")
                    || entry.id.equals("line_infinite") || entry.id.equals("one_row_infinite")
                    || entry.id.equals("highlife_replicator") || entry.id.equals("max_spacefiller")) {
                long initPop = reg.liveCount();
                e.stepN(300);
                check(e.getStats().liveCount > initPop + 10,
                        "Infinite growth verified: " + entry.id + " init=" + initPop + " after300=" + e.getStats().liveCount);
            }
        }
    }
}
