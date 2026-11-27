package com.belopus.life.engine;

import java.util.Random;

public final class CellRegion {
    public final int width;
    public final int height;
    public final boolean[] cells; // row-major: y * width + x
    public final int originOffsetX;
    public final int originOffsetY;

    public CellRegion(int width, int height, boolean[] cells) {
        this(width, height, cells, 0, 0);
    }

    public CellRegion(int width, int height, boolean[] cells, int originOffsetX, int originOffsetY) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.cells = cells != null ? cells : new boolean[0];
        this.originOffsetX = originOffsetX;
        this.originOffsetY = originOffsetY;
    }

    public boolean isEmpty() {
        for (boolean c : cells) {
            if (c) return false;
        }
        return true;
    }

    public int liveCount() {
        int count = 0;
        for (boolean c : cells) {
            if (c) count++;
        }
        return count;
    }

    public boolean get(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return false;
        return cells[y * width + x];
    }

    public CellRegion rotateCW() {
        if (width == 0 || height == 0) return this;
        boolean[] out = new boolean[width * height];
        int nw = height;
        int nh = width;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (cells[y * width + x]) {
                    int nx = height - 1 - y;
                    int ny = x;
                    out[ny * nw + nx] = true;
                }
            }
        }
        return new CellRegion(nw, nh, out, originOffsetX, originOffsetY);
    }

    public CellRegion rotateCCW() {
        if (width == 0 || height == 0) return this;
        boolean[] out = new boolean[width * height];
        int nw = height;
        int nh = width;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (cells[y * width + x]) {
                    int nx = y;
                    int ny = width - 1 - x;
                    out[ny * nw + nx] = true;
                }
            }
        }
        return new CellRegion(nw, nh, out, originOffsetX, originOffsetY);
    }

    public CellRegion flipH() {
        if (width == 0 || height == 0) return this;
        boolean[] out = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out[y * width + (width - 1 - x)] = cells[y * width + x];
            }
        }
        return new CellRegion(width, height, out, originOffsetX, originOffsetY);
    }

    public CellRegion flipV() {
        if (width == 0 || height == 0) return this;
        boolean[] out = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            int ny = height - 1 - y;
            System.arraycopy(cells, y * width, out, ny * width, width);
        }
        return new CellRegion(width, height, out, originOffsetX, originOffsetY);
    }

    public CellRegion invert() {
        boolean[] out = new boolean[cells.length];
        for (int i = 0; i < cells.length; i++) {
            out[i] = !cells[i];
        }
        return new CellRegion(width, height, out, originOffsetX, originOffsetY);
    }

    public CellRegion trimmed() {
        if (width == 0 || height == 0) return new CellRegion(0, 0, new boolean[0]);
        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (cells[y * width + x]) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX) return new CellRegion(0, 0, new boolean[0]);
        int nw = maxX - minX + 1;
        int nh = maxY - minY + 1;
        boolean[] out = new boolean[nw * nh];
        for (int y = minY; y <= maxY; y++) {
            System.arraycopy(cells, y * width + minX, out, (y - minY) * nw, nw);
        }
        return new CellRegion(nw, nh, out, originOffsetX + minX, originOffsetY + minY);
    }

    public static CellRegion random(int width, int height, float density, Symmetry symmetry, Random rng) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        final boolean[] out = new boolean[w * h];
        int px2 = w - 1;
        int py2 = h - 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (symmetry != Symmetry.NONE) {
                    if ((symmetry == Symmetry.MIRROR_X || symmetry == Symmetry.FOUR_WAY || symmetry == Symmetry.EIGHT_WAY)
                            && x > w / 2) continue;
                    if ((symmetry == Symmetry.MIRROR_Y || symmetry == Symmetry.FOUR_WAY || symmetry == Symmetry.EIGHT_WAY)
                            && y > h / 2) continue;
                    if (symmetry == Symmetry.EIGHT_WAY && y > x) continue;
                }
                if (rng.nextFloat() < density) {
                    symmetry.apply(x, y, px2, py2, new Symmetry.PointConsumer() {
                        @Override
                        public void accept(int sx, int sy) {
                            if (sx >= 0 && sy >= 0 && sx < w && sy < h) {
                                out[sy * w + sx] = true;
                            }
                        }
                    });
                }
            }
        }
        return new CellRegion(w, h, out);
    }
}
