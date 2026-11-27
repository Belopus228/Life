package com.belopus.life.engine;

import java.util.ArrayDeque;
import java.util.Deque;

public final class UndoStack {

    public static final class Snapshot {
        public final Topology topology;
        public final int gridWidth;
        public final int gridHeight;
        public final long generation;
        public final int[] xs;
        public final int[] ys;
        public final byte[] ages;

        public Snapshot(Topology topology, int gridWidth, int gridHeight, long generation,
                        int[] xs, int[] ys, byte[] ages) {
            this.topology = topology;
            this.gridWidth = gridWidth;
            this.gridHeight = gridHeight;
            this.generation = generation;
            this.xs = xs;
            this.ys = ys;
            this.ages = ages;
        }
    }

    private final int maxLevels;
    private final Deque<Snapshot> undoDeque = new ArrayDeque<>();
    private final Deque<Snapshot> redoDeque = new ArrayDeque<>();

    public UndoStack(int maxLevels) {
        this.maxLevels = Math.max(5, maxLevels);
    }

    public void pushUndo(Snapshot snap) {
        if (snap == null) return;
        undoDeque.push(snap);
        while (undoDeque.size() > maxLevels) {
            undoDeque.removeLast();
        }
        redoDeque.clear();
    }

    public boolean canUndo() {
        return !undoDeque.isEmpty();
    }

    public boolean canRedo() {
        return !redoDeque.isEmpty();
    }

    public Snapshot popUndo(Snapshot currentState) {
        if (undoDeque.isEmpty()) return null;
        if (currentState != null) {
            redoDeque.push(currentState);
            while (redoDeque.size() > maxLevels) {
                redoDeque.removeLast();
            }
        }
        return undoDeque.pop();
    }

    public Snapshot popRedo(Snapshot currentState) {
        if (redoDeque.isEmpty()) return null;
        if (currentState != null) {
            undoDeque.push(currentState);
            while (undoDeque.size() > maxLevels) {
                undoDeque.removeLast();
            }
        }
        return redoDeque.pop();
    }

    public void clear() {
        undoDeque.clear();
        redoDeque.clear();
    }
}
