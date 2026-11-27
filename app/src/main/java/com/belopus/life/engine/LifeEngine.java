package com.belopus.life.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class LifeEngine {

    public static final int CHUNK_SHIFT = 5;
    public static final int CHUNK_SIZE = 32;
    public static final int CHUNK_MASK = 31;
    public static final int CHUNK_AREA = 1024;
    private static final int SCRATCH_STRIDE = 34;

    public interface CellVisitor {
        void visit(int x, int y, byte state);
    }

    public static final class Chunk {
        public int cx;
        public int cy;
        public final byte[] cells = new byte[CHUNK_AREA];
        public int liveCount;
        public int decayCount;

        void init(int cx, int cy) {
            this.cx = cx;
            this.cy = cy;
            Arrays.fill(cells, (byte) 0);
            this.liveCount = 0;
            this.decayCount = 0;
        }
    }

    private static final class ChunkPool {
        private final ArrayList<Chunk> pool = new ArrayList<>(64);

        Chunk obtain(int cx, int cy) {
            Chunk c;
            int n = pool.size();
            if (n > 0) {
                c = pool.remove(n - 1);
            } else {
                c = new Chunk();
            }
            c.init(cx, cy);
            return c;
        }

        void recycle(Chunk c) {
            if (c != null && pool.size() < 512) {
                pool.add(c);
            }
        }
    }

    private static final class LongChunkMap {
        private long[] keys;
        private Chunk[] vals;
        private boolean[] used;
        private int mask;
        private int size;
        private int threshold;
        final ArrayList<Chunk> list = new ArrayList<>(64);

        LongChunkMap(int initialCap) {
            int cap = 1;
            while (cap < initialCap) cap <<= 1;
            initTable(cap);
        }

        private void initTable(int cap) {
            keys = new long[cap];
            vals = new Chunk[cap];
            used = new boolean[cap];
            mask = cap - 1;
            size = 0;
            threshold = (cap * 3) / 4;
            list.clear();
        }

        private static long pack(int cx, int cy) {
            return (((long) cx) << 32) | (cy & 0xFFFFFFFFL);
        }

        private static int hash(long k) {
            long h = k * 0x9E3779B97F4A7C15L;
            h ^= (h >>> 32);
            return (int) (h ^ (h >>> 16));
        }

        Chunk get(int cx, int cy) {
            long k = pack(cx, cy);
            int idx = hash(k) & mask;
            while (used[idx]) {
                if (keys[idx] == k) return vals[idx];
                idx = (idx + 1) & mask;
            }
            return null;
        }

        void put(int cx, int cy, Chunk c) {
            if (size >= threshold) rehash();
            long k = pack(cx, cy);
            int idx = hash(k) & mask;
            while (used[idx]) {
                if (keys[idx] == k) {
                    Chunk old = vals[idx];
                    if (old != c) {
                        int li = list.indexOf(old);
                        if (li >= 0) list.set(li, c);
                        else list.add(c);
                    }
                    vals[idx] = c;
                    return;
                }
                idx = (idx + 1) & mask;
            }
            used[idx] = true;
            keys[idx] = k;
            vals[idx] = c;
            size++;
            list.add(c);
        }

        Chunk remove(int cx, int cy) {
            long k = pack(cx, cy);
            int idx = hash(k) & mask;
            while (used[idx]) {
                if (keys[idx] == k) {
                    Chunk removed = vals[idx];
                    used[idx] = false;
                    vals[idx] = null;
                    size--;
                    int li = list.lastIndexOf(removed);
                    if (li >= 0) {
                        int last = list.size() - 1;
                        if (li != last) list.set(li, list.get(last));
                        list.remove(last);
                    }
                    // Shift back subsequent entries in probe chain
                    int next = (idx + 1) & mask;
                    while (used[next]) {
                        long nk = keys[next];
                        Chunk nv = vals[next];
                        used[next] = false;
                        vals[next] = null;
                        int slot = hash(nk) & mask;
                        while (used[slot]) slot = (slot + 1) & mask;
                        used[slot] = true;
                        keys[slot] = nk;
                        vals[slot] = nv;
                        next = (next + 1) & mask;
                    }
                    return removed;
                }
                idx = (idx + 1) & mask;
            }
            return null;
        }

        void clear(ChunkPool pool) {
            if (pool != null) {
                for (int i = 0; i < list.size(); i++) {
                    pool.recycle(list.get(i));
                }
            }
            Arrays.fill(used, false);
            Arrays.fill(vals, null);
            size = 0;
            list.clear();
        }

        private void rehash() {
            int newCap = keys.length << 1;
            long[] oldKeys = keys;
            Chunk[] oldVals = vals;
            boolean[] oldUsed = used;

            keys = new long[newCap];
            vals = new Chunk[newCap];
            used = new boolean[newCap];
            mask = newCap - 1;
            threshold = (newCap * 3) / 4;
            size = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldUsed[i]) {
                    long k = oldKeys[i];
                    int idx = hash(k) & mask;
                    while (used[idx]) idx = (idx + 1) & mask;
                    used[idx] = true;
                    keys[idx] = k;
                    vals[idx] = oldVals[i];
                    size++;
                }
            }
        }
    }

    private static final class LongCoordSet {
        private long[] keys;
        private boolean[] used;
        private int mask;
        private int size;
        private int threshold;

        LongCoordSet(int cap) {
            int c = 1;
            while (c < cap) c <<= 1;
            keys = new long[c];
            used = new boolean[c];
            mask = c - 1;
            threshold = (c * 3) / 4;
        }

        void clear() {
            Arrays.fill(used, false);
            size = 0;
        }

        void add(int cx, int cy) {
            if (size >= threshold) rehash();
            long k = (((long) cx) << 32) | (cy & 0xFFFFFFFFL);
            long h = k * 0x9E3779B97F4A7C15L;
            int idx = (int) ((h ^ (h >>> 32)) >>> 16) & mask;
            while (used[idx]) {
                if (keys[idx] == k) return;
                idx = (idx + 1) & mask;
            }
            used[idx] = true;
            keys[idx] = k;
            size++;
        }

        private void rehash() {
            long[] oldK = keys;
            boolean[] oldU = used;
            int nc = oldK.length << 1;
            keys = new long[nc];
            used = new boolean[nc];
            mask = nc - 1;
            threshold = (nc * 3) / 4;
            size = 0;
            for (int i = 0; i < oldK.length; i++) {
                if (oldU[i]) {
                    long k = oldK[i];
                    long h = k * 0x9E3779B97F4A7C15L;
                    int idx = (int) ((h ^ (h >>> 32)) >>> 16) & mask;
                    while (used[idx]) idx = (idx + 1) & mask;
                    used[idx] = true;
                    keys[idx] = k;
                    size++;
                }
            }
        }
    }

    private Topology topology = Topology.INFINITE;
    private int gridWidth = 128;
    private int gridHeight = 128;
    private RuleSet rule = RuleSet.CONWAY;
    private int decaySteps = 8; // 0 disables decay trail

    // Finite buffers
    private byte[] finiteGrid;
    private byte[] finiteNext;

    // Infinite sparse buffers
    private final ChunkPool chunkPool = new ChunkPool();
    private LongChunkMap chunks = new LongChunkMap(128);
    private LongChunkMap nextChunks = new LongChunkMap(128);
    private final LongCoordSet wakeSet = new LongCoordSet(256);
    private final byte[] scratch = new byte[SCRATCH_STRIDE * SCRATCH_STRIDE];

    private final SimStats stats = new SimStats();
    private final UndoStack undoStack = new UndoStack(40);
    private UndoStack.Snapshot gen0Snapshot;

    public LifeEngine() {
        recomputeStatsAfterEdit(true);
    }

    public synchronized Topology getTopology() {
        return topology;
    }

    public synchronized int getGridWidth() {
        return gridWidth;
    }

    public synchronized int getGridHeight() {
        return gridHeight;
    }

    public synchronized RuleSet getRule() {
        return rule;
    }

    public synchronized int getDecaySteps() {
        return decaySteps;
    }

    public synchronized SimStats getStats() {
        return stats;
    }

    public synchronized void setRule(RuleSet newRule) {
        if (newRule == null) return;
        this.rule = newRule;
        if (this.topology == Topology.INFINITE && newRule.hasB0()) {
            // B0 flips infinite void every generation; switch automatically to TOROIDAL
            configureTopology(Topology.TOROIDAL, gridWidth, gridHeight, true);
        }
    }

    public synchronized void setDecaySteps(int steps) {
        int clamped = Math.max(0, Math.min(20, steps));
        if (clamped == this.decaySteps) return;
        this.decaySteps = clamped;
        if (clamped == 0) {
            clearDecayOnly();
        }
    }

    private void clearDecayOnly() {
        if (topology == Topology.INFINITE) {
            List<Chunk> list = chunks.list;
            for (int i = list.size() - 1; i >= 0; i--) {
                Chunk c = list.get(i);
                if (c.decayCount > 0) {
                    for (int k = 0; k < CHUNK_AREA; k++) {
                        if (c.cells[k] < 0) c.cells[k] = 0;
                    }
                    c.decayCount = 0;
                    if (c.liveCount == 0) {
                        Chunk rem = chunks.remove(c.cx, c.cy);
                        chunkPool.recycle(rem);
                    }
                }
            }
        } else if (finiteGrid != null) {
            for (int i = 0; i < finiteGrid.length; i++) {
                if (finiteGrid[i] < 0) finiteGrid[i] = 0;
            }
        }
    }

    public synchronized void configureTopology(Topology newTopology, int newW, int newH, boolean preserveCells) {
        int w = Math.max(8, Math.min(1024, newW));
        int h = Math.max(8, Math.min(1024, newH));

        UndoStack.Snapshot saved = preserveCells ? captureSnapshotInternal() : null;

        this.topology = newTopology;
        this.gridWidth = w;
        this.gridHeight = h;

        chunks.clear(chunkPool);
        nextChunks.clear(chunkPool);

        if (newTopology == Topology.INFINITE) {
            finiteGrid = null;
            finiteNext = null;
            if (rule.hasB0()) {
                rule = RuleSet.CONWAY;
            }
        } else {
            finiteGrid = new byte[w * h];
            finiteNext = new byte[w * h];
        }

        if (saved != null && saved.xs.length > 0) {
            // Re-center or preserve coordinates cleanly
            if (saved.topology == Topology.INFINITE && newTopology != Topology.INFINITE) {
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                for (int i = 0; i < saved.xs.length; i++) {
                    if (saved.xs[i] < minX) minX = saved.xs[i];
                    if (saved.xs[i] > maxX) maxX = saved.xs[i];
                    if (saved.ys[i] < minY) minY = saved.ys[i];
                    if (saved.ys[i] > maxY) maxY = saved.ys[i];
                }
                int cx = (minX + maxX) / 2;
                int cy = (minY + maxY) / 2;
                int offX = w / 2 - cx;
                int offY = h / 2 - cy;
                for (int i = 0; i < saved.xs.length; i++) {
                    setCellRaw(saved.xs[i] + offX, saved.ys[i] + offY, saved.ages[i]);
                }
            } else if (saved.topology != Topology.INFINITE && newTopology == Topology.INFINITE) {
                int offX = -saved.gridWidth / 2;
                int offY = -saved.gridHeight / 2;
                for (int i = 0; i < saved.xs.length; i++) {
                    setCellRaw(saved.xs[i] + offX, saved.ys[i] + offY, saved.ages[i]);
                }
            } else {
                int offX = (newTopology == Topology.INFINITE) ? 0 : (w - saved.gridWidth) / 2;
                int offY = (newTopology == Topology.INFINITE) ? 0 : (h - saved.gridHeight) / 2;
                for (int i = 0; i < saved.xs.length; i++) {
                    setCellRaw(saved.xs[i] + offX, saved.ys[i] + offY, saved.ages[i]);
                }
            }
        }
        recomputeStatsAfterEdit(true);
        gen0Snapshot = captureSnapshotInternal();
    }

    public synchronized void clear(boolean recordUndo) {
        if (recordUndo) pushUndoInternal();
        if (topology == Topology.INFINITE) {
            chunks.clear(chunkPool);
        } else if (finiteGrid != null) {
            Arrays.fill(finiteGrid, (byte) 0);
        }
        recomputeStatsAfterEdit(true);
        gen0Snapshot = captureSnapshotInternal();
    }

    public synchronized byte getCell(int x, int y) {
        if (topology == Topology.INFINITE) {
            int cx = x >> CHUNK_SHIFT;
            int cy = y >> CHUNK_SHIFT;
            Chunk c = chunks.get(cx, cy);
            if (c == null) return 0;
            return c.cells[((y & CHUNK_MASK) << CHUNK_SHIFT) | (x & CHUNK_MASK)];
        } else {
            if (topology == Topology.TOROIDAL) {
                x = wrap(x, gridWidth);
                y = wrap(y, gridHeight);
            } else if (x < 0 || y < 0 || x >= gridWidth || y >= gridHeight) {
                return 0;
            }
            return finiteGrid[y * gridWidth + x];
        }
    }

    public synchronized boolean isAlive(int x, int y) {
        return getCell(x, y) > 0;
    }

    public synchronized void setCell(int x, int y, boolean alive) {
        setCellRaw(x, y, alive ? (byte) 1 : (byte) 0);
    }

    private void setCellRaw(int x, int y, byte age) {
        if (topology == Topology.INFINITE) {
            int cx = x >> CHUNK_SHIFT;
            int cy = y >> CHUNK_SHIFT;
            int lx = x & CHUNK_MASK;
            int ly = y & CHUNK_MASK;
            int idx = (ly << CHUNK_SHIFT) | lx;
            Chunk c = chunks.get(cx, cy);
            if (c == null) {
                if (age == 0) return;
                c = chunkPool.obtain(cx, cy);
                chunks.put(cx, cy, c);
            }
            byte prev = c.cells[idx];
            if (prev == age) return;
            if (prev > 0) c.liveCount--;
            else if (prev < 0) c.decayCount--;
            c.cells[idx] = age;
            if (age > 0) c.liveCount++;
            else if (age < 0) c.decayCount++;
            if (c.liveCount == 0 && c.decayCount == 0) {
                Chunk rem = chunks.remove(cx, cy);
                chunkPool.recycle(rem);
            }
        } else {
            if (topology == Topology.TOROIDAL) {
                x = wrap(x, gridWidth);
                y = wrap(y, gridHeight);
            } else if (x < 0 || y < 0 || x >= gridWidth || y >= gridHeight) {
                return;
            }
            finiteGrid[y * gridWidth + x] = age;
        }
    }

    public synchronized void notifyManualEditComplete(boolean updateGen0IfZero) {
        recomputeStatsAfterEdit(false);
        if (updateGen0IfZero && stats.generation == 0) {
            gen0Snapshot = captureSnapshotInternal();
        }
    }

    public synchronized void pushUndo() {
        pushUndoInternal();
    }

    private void pushUndoInternal() {
        undoStack.pushUndo(captureSnapshotInternal());
    }

    public synchronized boolean canUndo() {
        return undoStack.canUndo();
    }

    public synchronized boolean canRedo() {
        return undoStack.canRedo();
    }

    public synchronized boolean undo() {
        UndoStack.Snapshot cur = captureSnapshotInternal();
        UndoStack.Snapshot prev = undoStack.popUndo(cur);
        if (prev == null) return false;
        restoreSnapshotInternal(prev);
        return true;
    }

    public synchronized boolean redo() {
        UndoStack.Snapshot cur = captureSnapshotInternal();
        UndoStack.Snapshot next = undoStack.popRedo(cur);
        if (next == null) return false;
        restoreSnapshotInternal(next);
        return true;
    }

    public synchronized boolean hasGen0Snapshot() {
        return gen0Snapshot != null;
    }

    public synchronized void markCurrentAsGen0() {
        stats.generation = 0;
        recomputeStatsAfterEdit(true);
        gen0Snapshot = captureSnapshotInternal();
    }

    public synchronized boolean resetToGen0() {
        if (gen0Snapshot == null) return false;
        pushUndoInternal();
        restoreSnapshotInternal(gen0Snapshot);
        return true;
    }

    public synchronized void getSymmetryPivot2(int[] outPivot2) {
        if (topology == Topology.INFINITE) {
            outPivot2[0] = 0;
            outPivot2[1] = 0;
        } else {
            outPivot2[0] = gridWidth - 1;
            outPivot2[1] = gridHeight - 1;
        }
    }

    public synchronized void drawCellWithSymmetry(int x, int y, final boolean alive, Symmetry sym) {
        if (sym == null || sym == Symmetry.NONE) {
            setCell(x, y, alive);
            return;
        }
        int px2 = (topology == Topology.INFINITE) ? 0 : (gridWidth - 1);
        int py2 = (topology == Topology.INFINITE) ? 0 : (gridHeight - 1);
        sym.apply(x, y, px2, py2, new Symmetry.PointConsumer() {
            @Override
            public void accept(int sx, int sy) {
                setCell(sx, sy, alive);
            }
        });
    }

    public synchronized void drawLineWithSymmetry(int x0, int y0, int x1, int y1, boolean alive, Symmetry sym) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int cx = x0, cy = y0;
        while (true) {
            drawCellWithSymmetry(cx, cy, alive, sym);
            if (cx == x1 && cy == y1) break;
            int e2 = err << 1;
            if (e2 > -dy) {
                err -= dy;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cy += sy;
            }
        }
    }

    public synchronized void drawRectWithSymmetry(int x0, int y0, int x1, int y1, boolean filled, boolean alive, Symmetry sym) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);
        if ((long) (maxX - minX + 1) * (maxY - minY + 1) > 400_000L) return;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (filled || x == minX || x == maxX || y == minY || y == maxY) {
                    drawCellWithSymmetry(x, y, alive, sym);
                }
            }
        }
    }

    public synchronized void drawCircleWithSymmetry(int cx, int cy, int edgeX, int edgeY, boolean filled, boolean alive, Symmetry sym) {
        long dx = (long) edgeX - cx;
        long dy = (long) edgeY - cy;
        int r = (int) Math.round(Math.sqrt(dx * dx + dy * dy));
        r = Math.min(r, 256);
        if (r == 0) {
            drawCellWithSymmetry(cx, cy, alive, sym);
            return;
        }
        if (filled) {
            int r2 = r * r + r;
            for (int y = -r; y <= r; y++) {
                for (int x = -r; x <= r; x++) {
                    if (x * x + y * y <= r2) {
                        drawCellWithSymmetry(cx + x, cy + y, alive, sym);
                    }
                }
            }
        } else {
            int x = r, y = 0;
            int err = 1 - r;
            while (x >= y) {
                drawCellWithSymmetry(cx + x, cy + y, alive, sym);
                drawCellWithSymmetry(cx + y, cy + x, alive, sym);
                drawCellWithSymmetry(cx - y, cy + x, alive, sym);
                drawCellWithSymmetry(cx - x, cy + y, alive, sym);
                drawCellWithSymmetry(cx - x, cy - y, alive, sym);
                drawCellWithSymmetry(cx - y, cy - x, alive, sym);
                drawCellWithSymmetry(cx + y, cy - x, alive, sym);
                drawCellWithSymmetry(cx + x, cy - y, alive, sym);
                y++;
                if (err < 0) {
                    err += 2 * y + 1;
                } else {
                    x--;
                    err += 2 * (y - x) + 1;
                }
            }
        }
    }

    public synchronized void pasteRegion(CellRegion region, int destX, int destY, boolean merge, boolean recordUndo) {
        if (region == null || region.width <= 0 || region.height <= 0) return;
        if (recordUndo) pushUndoInternal();
        for (int y = 0; y < region.height; y++) {
            for (int x = 0; x < region.width; x++) {
                boolean cell = region.get(x, y);
                if (cell) {
                    setCell(destX + x, destY + y, true);
                } else if (!merge) {
                    setCell(destX + x, destY + y, false);
                }
            }
        }
        notifyManualEditComplete(true);
    }

    public synchronized CellRegion extractRegion(int x0, int y0, int x1, int y1) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        if (w <= 0 || h <= 0 || (long) w * h > 2_000_000L) {
            return new CellRegion(0, 0, new boolean[0]);
        }
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y * w + x] = isAlive(minX + x, minY + y);
            }
        }
        return new CellRegion(w, h, out, minX, minY);
    }

    public synchronized CellRegion extractAllLiveBounds() {
        if (!stats.hasBounds) return new CellRegion(0, 0, new boolean[0]);
        return extractRegion(stats.minX, stats.minY, stats.maxX, stats.maxY);
    }

    public synchronized void clearRegion(int x0, int y0, int x1, int y1, boolean recordUndo) {
        if (recordUndo) pushUndoInternal();
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);
        if ((long) (maxX - minX + 1) * (maxY - minY + 1) > 2_000_000L) return;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                setCell(x, y, false);
            }
        }
        notifyManualEditComplete(true);
    }

    public synchronized void invertRegion(int x0, int y0, int x1, int y1, boolean recordUndo) {
        if (recordUndo) pushUndoInternal();
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);
        if ((long) (maxX - minX + 1) * (maxY - minY + 1) > 2_000_000L) return;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                setCell(x, y, !isAlive(x, y));
            }
        }
        notifyManualEditComplete(true);
    }

    public synchronized void fillRandomRegion(int destX, int destY, int width, int height,
                                              float density, Symmetry symmetry, boolean clearFirst, boolean recordUndo) {
        if (recordUndo) pushUndoInternal();
        if (clearFirst && topology != Topology.INFINITE && destX == 0 && destY == 0
                && width == gridWidth && height == gridHeight) {
            Arrays.fill(finiteGrid, (byte) 0);
        }
        CellRegion soup = CellRegion.random(width, height, density, symmetry, new Random());
        for (int y = 0; y < soup.height; y++) {
            for (int x = 0; x < soup.width; x++) {
                boolean v = soup.get(x, y);
                if (clearFirst || v) {
                    setCell(destX + x, destY + y, v);
                }
            }
        }
        notifyManualEditComplete(true);
    }

    public synchronized void step() {
        if (stats.generation == 0 && gen0Snapshot == null) {
            gen0Snapshot = captureSnapshotInternal();
        }
        if (topology == Topology.INFINITE) {
            stepInfinite();
        } else {
            stepFinite();
        }
    }

    public synchronized void stepN(int count) {
        for (int i = 0; i < count; i++) {
            step();
        }
    }

    private void stepInfinite() {
        wakeSet.clear();
        List<Chunk> list = chunks.list;
        for (int i = 0; i < list.size(); i++) {
            Chunk c = list.get(i);
            if (c.liveCount > 0) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        wakeSet.add(c.cx + dx, c.cy + dy);
                    }
                }
            } else if (c.decayCount > 0) {
                wakeSet.add(c.cx, c.cy);
            }
        }

        nextChunks.clear(chunkPool);

        long liveCount = 0;
        long births = 0;
        long deaths = 0;
        long stateHash = 0;
        boolean hasBounds = false;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        final int bMask = rule.birthMask;
        final int sMask = rule.survivalMask;
        final int decay = decaySteps;

        for (int i = 0; i < wakeSet.keys.length; i++) {
            if (!wakeSet.used[i]) continue;
            long packed = wakeSet.keys[i];
            int cx = (int) (packed >> 32);
            int cy = (int) packed;

            Chunk center = chunks.get(cx, cy);
            Chunk nw = chunks.get(cx - 1, cy - 1);
            Chunk nn = chunks.get(cx, cy - 1);
            Chunk ne = chunks.get(cx + 1, cy - 1);
            Chunk ww = chunks.get(cx - 1, cy);
            Chunk ee = chunks.get(cx + 1, cy);
            Chunk sw = chunks.get(cx - 1, cy + 1);
            Chunk ss = chunks.get(cx, cy + 1);
            Chunk se = chunks.get(cx + 1, cy + 1);

            boolean anyNeighborLive = (nw != null && nw.liveCount > 0)
                    || (nn != null && nn.liveCount > 0)
                    || (ne != null && ne.liveCount > 0)
                    || (ww != null && ww.liveCount > 0)
                    || (ee != null && ee.liveCount > 0)
                    || (sw != null && sw.liveCount > 0)
                    || (ss != null && ss.liveCount > 0)
                    || (se != null && se.liveCount > 0);

            if (!anyNeighborLive && (center == null || (center.liveCount == 0 && center.decayCount == 0))) {
                continue;
            }

            // Fast fade-only path when center and all neighbors have zero live cells
            if (!anyNeighborLive && center != null && center.liveCount == 0) {
                if (decay <= 0) continue;
                Chunk out = chunkPool.obtain(cx, cy);
                int outDecay = 0;
                for (int k = 0; k < CHUNK_AREA; k++) {
                    byte v = center.cells[k];
                    if (v < 0) {
                        int nv = v + 1;
                        if (nv < 0) {
                            out.cells[k] = (byte) nv;
                            outDecay++;
                        }
                    }
                }
                out.decayCount = outDecay;
                if (outDecay > 0) {
                    nextChunks.put(cx, cy, out);
                } else {
                    chunkPool.recycle(out);
                }
                continue;
            }

            // Fill 34x34 scratch array with 0/1 alive state
            Arrays.fill(scratch, (byte) 0);
            if (center != null && center.liveCount > 0) {
                byte[] cc = center.cells;
                for (int ly = 0; ly < CHUNK_SIZE; ly++) {
                    int srcOff = ly << CHUNK_SHIFT;
                    int dstOff = (ly + 1) * SCRATCH_STRIDE + 1;
                    for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                        if (cc[srcOff + lx] > 0) scratch[dstOff + lx] = 1;
                    }
                }
            }
            if (nn != null && nn.liveCount > 0) {
                int srcOff = 31 << CHUNK_SHIFT;
                for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                    if (nn.cells[srcOff + lx] > 0) scratch[1 + lx] = 1;
                }
            }
            if (ss != null && ss.liveCount > 0) {
                int dstOff = 33 * SCRATCH_STRIDE + 1;
                for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                    if (ss.cells[lx] > 0) scratch[dstOff + lx] = 1;
                }
            }
            if (ww != null && ww.liveCount > 0) {
                for (int ly = 0; ly < CHUNK_SIZE; ly++) {
                    if (ww.cells[(ly << CHUNK_SHIFT) + 31] > 0) {
                        scratch[(ly + 1) * SCRATCH_STRIDE] = 1;
                    }
                }
            }
            if (ee != null && ee.liveCount > 0) {
                for (int ly = 0; ly < CHUNK_SIZE; ly++) {
                    if (ee.cells[ly << CHUNK_SHIFT] > 0) {
                        scratch[(ly + 1) * SCRATCH_STRIDE + 33] = 1;
                    }
                }
            }
            if (nw != null && nw.liveCount > 0 && nw.cells[1023] > 0) scratch[0] = 1;
            if (ne != null && ne.liveCount > 0 && ne.cells[31 << CHUNK_SHIFT] > 0) scratch[33] = 1;
            if (sw != null && sw.liveCount > 0 && sw.cells[31] > 0) scratch[33 * SCRATCH_STRIDE] = 1;
            if (se != null && se.liveCount > 0 && se.cells[0] > 0) scratch[33 * SCRATCH_STRIDE + 33] = 1;

            Chunk out = chunkPool.obtain(cx, cy);
            byte[] outCells = out.cells;
            byte[] oldCells = center != null ? center.cells : null;
            int outLive = 0;
            int outDecay = 0;
            int baseX = cx << CHUNK_SHIFT;
            int baseY = cy << CHUNK_SHIFT;

            for (int ly = 0; ly < CHUNK_SIZE; ly++) {
                int sRow = (ly + 1) * SCRATCH_STRIDE + 1;
                int cRow = ly << CHUNK_SHIFT;
                int wy = baseY + ly;
                for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                    int sIdx = sRow + lx;
                    int n = scratch[sIdx - SCRATCH_STRIDE - 1]
                            + scratch[sIdx - SCRATCH_STRIDE]
                            + scratch[sIdx - SCRATCH_STRIDE + 1]
                            + scratch[sIdx - 1]
                            + scratch[sIdx + 1]
                            + scratch[sIdx + SCRATCH_STRIDE - 1]
                            + scratch[sIdx + SCRATCH_STRIDE]
                            + scratch[sIdx + SCRATCH_STRIDE + 1];

                    boolean wasAlive = scratch[sIdx] != 0;
                    boolean nextAlive = wasAlive
                            ? (((sMask >> n) & 1) != 0)
                            : (((bMask >> n) & 1) != 0);

                    int cIdx = cRow + lx;
                    if (nextAlive) {
                        byte prevAge = oldCells != null ? oldCells[cIdx] : 0;
                        byte nextAge = prevAge > 0 ? (byte) (prevAge < 127 ? prevAge + 1 : 127) : 1;
                        outCells[cIdx] = nextAge;
                        outLive++;
                        if (!wasAlive) births++;
                        int wx = baseX + lx;
                        stateHash ^= coordHash(wx, wy);
                        if (!hasBounds) {
                            hasBounds = true;
                            minX = maxX = wx;
                            minY = maxY = wy;
                        } else {
                            if (wx < minX) minX = wx;
                            if (wx > maxX) maxX = wx;
                            if (wy < minY) minY = wy;
                            if (wy > maxY) maxY = wy;
                        }
                    } else if (wasAlive) {
                        deaths++;
                        if (decay > 0) {
                            outCells[cIdx] = (byte) (-decay);
                            outDecay++;
                        }
                    } else if (decay > 0 && oldCells != null) {
                        byte oldVal = oldCells[cIdx];
                        if (oldVal < 0) {
                            int nv = oldVal + 1;
                            if (nv < 0) {
                                outCells[cIdx] = (byte) nv;
                                outDecay++;
                            }
                        }
                    }
                }
            }

            out.liveCount = outLive;
            out.decayCount = outDecay;
            if (outLive > 0 || outDecay > 0) {
                nextChunks.put(cx, cy, out);
                liveCount += outLive;
            } else {
                chunkPool.recycle(out);
            }
        }

        LongChunkMap tmp = chunks;
        chunks = nextChunks;
        nextChunks = tmp;
        nextChunks.clear(chunkPool);

        stats.onStep(liveCount, births, deaths, stateHash, hasBounds, minX, minY, maxX, maxY);
    }

    private void stepFinite() {
        final int w = gridWidth;
        final int h = gridHeight;
        final byte[] src = finiteGrid;
        final byte[] dst = finiteNext;
        final boolean toroidal = (topology == Topology.TOROIDAL);
        final int bMask = rule.birthMask;
        final int sMask = rule.survivalMask;
        final int decay = decaySteps;

        long liveCount = 0;
        long births = 0;
        long deaths = 0;
        long stateHash = 0;
        boolean hasBounds = false;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        // Fast branchless interior loop
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int idx = row + x;
                int n = (src[idx - w - 1] > 0 ? 1 : 0)
                        + (src[idx - w] > 0 ? 1 : 0)
                        + (src[idx - w + 1] > 0 ? 1 : 0)
                        + (src[idx - 1] > 0 ? 1 : 0)
                        + (src[idx + 1] > 0 ? 1 : 0)
                        + (src[idx + w - 1] > 0 ? 1 : 0)
                        + (src[idx + w] > 0 ? 1 : 0)
                        + (src[idx + w + 1] > 0 ? 1 : 0);

                byte oldVal = src[idx];
                boolean wasAlive = oldVal > 0;
                boolean nextAlive = wasAlive
                        ? (((sMask >> n) & 1) != 0)
                        : (((bMask >> n) & 1) != 0);

                if (nextAlive) {
                    dst[idx] = wasAlive ? (byte) (oldVal < 127 ? oldVal + 1 : 127) : 1;
                    liveCount++;
                    if (!wasAlive) births++;
                    stateHash ^= coordHash(x, y);
                    if (!hasBounds) {
                        hasBounds = true;
                        minX = maxX = x;
                        minY = maxY = y;
                    } else {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                } else if (wasAlive) {
                    deaths++;
                    dst[idx] = decay > 0 ? (byte) (-decay) : 0;
                } else if (oldVal < 0 && decay > 0) {
                    int nv = oldVal + 1;
                    dst[idx] = nv < 0 ? (byte) nv : 0;
                } else {
                    dst[idx] = 0;
                }
            }
        }

        // Border rows & columns
        for (int y = 0; y < h; y++) {
            int row = y * w;
            boolean isBorderRow = (y == 0 || y == h - 1);
            for (int x = 0; x < w; x++) {
                if (!isBorderRow && x > 0 && x < w - 1) {
                    x = w - 2; // skip interior, next x++ lands on w - 1
                    continue;
                }
                int idx = row + x;
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (toroidal) {
                        if (ny < 0) ny += h;
                        else if (ny >= h) ny -= h;
                    } else if (ny < 0 || ny >= h) {
                        continue;
                    }
                    int nrow = ny * w;
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        if (toroidal) {
                            if (nx < 0) nx += w;
                            else if (nx >= w) nx -= w;
                        } else if (nx < 0 || nx >= w) {
                            continue;
                        }
                        if (src[nrow + nx] > 0) n++;
                    }
                }

                byte oldVal = src[idx];
                boolean wasAlive = oldVal > 0;
                boolean nextAlive = wasAlive
                        ? (((sMask >> n) & 1) != 0)
                        : (((bMask >> n) & 1) != 0);

                if (nextAlive) {
                    dst[idx] = wasAlive ? (byte) (oldVal < 127 ? oldVal + 1 : 127) : 1;
                    liveCount++;
                    if (!wasAlive) births++;
                    stateHash ^= coordHash(x, y);
                    if (!hasBounds) {
                        hasBounds = true;
                        minX = maxX = x;
                        minY = maxY = y;
                    } else {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                } else if (wasAlive) {
                    deaths++;
                    dst[idx] = decay > 0 ? (byte) (-decay) : 0;
                } else if (oldVal < 0 && decay > 0) {
                    int nv = oldVal + 1;
                    dst[idx] = nv < 0 ? (byte) nv : 0;
                } else {
                    dst[idx] = 0;
                }
            }
        }

        finiteGrid = dst;
        finiteNext = src;

        stats.onStep(liveCount, births, deaths, stateHash, hasBounds, minX, minY, maxX, maxY);
    }

    public synchronized void visitCellsInRect(int minX, int minY, int maxX, int maxY,
                                              boolean includeDecay, CellVisitor visitor) {
        if (topology == Topology.INFINITE) {
            int minCx = minX >> CHUNK_SHIFT;
            int maxCx = maxX >> CHUNK_SHIFT;
            int minCy = minY >> CHUNK_SHIFT;
            int maxCy = maxY >> CHUNK_SHIFT;
            List<Chunk> list = chunks.list;
            for (int i = 0; i < list.size(); i++) {
                Chunk c = list.get(i);
                if (c.cx < minCx || c.cx > maxCx || c.cy < minCy || c.cy > maxCy) continue;
                if (c.liveCount == 0 && (!includeDecay || c.decayCount == 0)) continue;

                int baseX = c.cx << CHUNK_SHIFT;
                int baseY = c.cy << CHUNK_SHIFT;
                int lxStart = Math.max(0, minX - baseX);
                int lxEnd = Math.min(CHUNK_SIZE - 1, maxX - baseX);
                int lyStart = Math.max(0, minY - baseY);
                int lyEnd = Math.min(CHUNK_SIZE - 1, maxY - baseY);

                byte[] cells = c.cells;
                for (int ly = lyStart; ly <= lyEnd; ly++) {
                    int row = ly << CHUNK_SHIFT;
                    int wy = baseY + ly;
                    for (int lx = lxStart; lx <= lxEnd; lx++) {
                        byte v = cells[row + lx];
                        if (v > 0 || (includeDecay && v < 0)) {
                            visitor.visit(baseX + lx, wy, v);
                        }
                    }
                }
            }
        } else {
            int x0 = Math.max(0, minX);
            int y0 = Math.max(0, minY);
            int x1 = Math.min(gridWidth - 1, maxX);
            int y1 = Math.min(gridHeight - 1, maxY);
            for (int y = y0; y <= y1; y++) {
                int row = y * gridWidth;
                for (int x = x0; x <= x1; x++) {
                    byte v = finiteGrid[row + x];
                    if (v > 0 || (includeDecay && v < 0)) {
                        visitor.visit(x, y, v);
                    }
                }
            }
        }
    }

    private UndoStack.Snapshot captureSnapshotInternal() {
        int count = (int) Math.min(stats.liveCount, 500_000L);
        int[] xs = new int[count];
        int[] ys = new int[count];
        byte[] ages = new byte[count];
        final int[] idx = new int[1];

        if (topology == Topology.INFINITE) {
            List<Chunk> list = chunks.list;
            for (int i = 0; i < list.size() && idx[0] < count; i++) {
                Chunk c = list.get(i);
                if (c.liveCount == 0) continue;
                int baseX = c.cx << CHUNK_SHIFT;
                int baseY = c.cy << CHUNK_SHIFT;
                for (int ly = 0; ly < CHUNK_SIZE && idx[0] < count; ly++) {
                    int row = ly << CHUNK_SHIFT;
                    for (int lx = 0; lx < CHUNK_SIZE && idx[0] < count; lx++) {
                        byte v = c.cells[row + lx];
                        if (v > 0) {
                            xs[idx[0]] = baseX + lx;
                            ys[idx[0]] = baseY + ly;
                            ages[idx[0]] = v;
                            idx[0]++;
                        }
                    }
                }
            }
        } else if (finiteGrid != null) {
            for (int y = 0; y < gridHeight && idx[0] < count; y++) {
                int row = y * gridWidth;
                for (int x = 0; x < gridWidth && idx[0] < count; x++) {
                    byte v = finiteGrid[row + x];
                    if (v > 0) {
                        xs[idx[0]] = x;
                        ys[idx[0]] = y;
                        ages[idx[0]] = v;
                        idx[0]++;
                    }
                }
            }
        }

        if (idx[0] != count) {
            xs = Arrays.copyOf(xs, idx[0]);
            ys = Arrays.copyOf(ys, idx[0]);
            ages = Arrays.copyOf(ages, idx[0]);
        }
        return new UndoStack.Snapshot(topology, gridWidth, gridHeight, stats.generation, xs, ys, ages);
    }

    private void restoreSnapshotInternal(UndoStack.Snapshot snap) {
        if (snap.topology != topology || snap.gridWidth != gridWidth || snap.gridHeight != gridHeight) {
            topology = snap.topology;
            gridWidth = snap.gridWidth;
            gridHeight = snap.gridHeight;
            if (topology == Topology.INFINITE) {
                finiteGrid = null;
                finiteNext = null;
            } else {
                finiteGrid = new byte[gridWidth * gridHeight];
                finiteNext = new byte[gridWidth * gridHeight];
            }
        }
        chunks.clear(chunkPool);
        if (finiteGrid != null) Arrays.fill(finiteGrid, (byte) 0);

        for (int i = 0; i < snap.xs.length; i++) {
            setCellRaw(snap.xs[i], snap.ys[i], snap.ages[i]);
        }
        stats.generation = snap.generation;
        recomputeStatsAfterEdit(snap.generation == 0);
    }

    private void recomputeStatsAfterEdit(boolean fullReset) {
        long liveCount = 0;
        long stateHash = 0;
        boolean hasBounds = false;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        if (topology == Topology.INFINITE) {
            List<Chunk> list = chunks.list;
            for (int i = 0; i < list.size(); i++) {
                Chunk c = list.get(i);
                if (c.liveCount == 0) continue;
                int baseX = c.cx << CHUNK_SHIFT;
                int baseY = c.cy << CHUNK_SHIFT;
                for (int ly = 0; ly < CHUNK_SIZE; ly++) {
                    int row = ly << CHUNK_SHIFT;
                    int wy = baseY + ly;
                    for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                        if (c.cells[row + lx] > 0) {
                            liveCount++;
                            int wx = baseX + lx;
                            stateHash ^= coordHash(wx, wy);
                            if (!hasBounds) {
                                hasBounds = true;
                                minX = maxX = wx;
                                minY = maxY = wy;
                            } else {
                                if (wx < minX) minX = wx;
                                if (wx > maxX) maxX = wx;
                                if (wy < minY) minY = wy;
                                if (wy > maxY) maxY = wy;
                            }
                        }
                    }
                }
            }
        } else if (finiteGrid != null) {
            for (int y = 0; y < gridHeight; y++) {
                int row = y * gridWidth;
                for (int x = 0; x < gridWidth; x++) {
                    if (finiteGrid[row + x] > 0) {
                        liveCount++;
                        stateHash ^= coordHash(x, y);
                        if (!hasBounds) {
                            hasBounds = true;
                            minX = maxX = x;
                            minY = maxY = y;
                        } else {
                            if (x < minX) minX = x;
                            if (x > maxX) maxX = x;
                            if (y < minY) minY = y;
                            if (y > maxY) maxY = y;
                        }
                    }
                }
            }
        }

        if (fullReset) {
            stats.resetAll(liveCount, stateHash, hasBounds, minX, minY, maxX, maxY);
        } else {
            stats.onEdited(liveCount, stateHash, hasBounds, minX, minY, maxX, maxY);
        }
    }

    private static int wrap(int v, int mod) {
        int r = v % mod;
        return r < 0 ? r + mod : r;
    }

    private static long coordHash(int x, int y) {
        long z = (((long) x) << 32) | (y & 0xFFFFFFFFL);
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
