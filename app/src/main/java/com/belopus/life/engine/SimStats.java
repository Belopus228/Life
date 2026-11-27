package com.belopus.life.engine;

public final class SimStats {

    public enum Status {
        IDLE,
        RUNNING,
        EXTINCT,
        STILL_LIFE,
        OSCILLATOR
    }

    public static final int HISTORY_CAP = 320;
    private static final int HASH_RING_CAP = 260;

    public long generation;
    public long liveCount;
    public long lastBirths;
    public long lastDeaths;
    public long totalBirths;
    public long totalDeaths;
    public long peakPopulation;
    public long peakGeneration;

    public boolean hasBounds;
    public int minX, minY, maxX, maxY;

    public Status status = Status.IDLE;
    public int detectedPeriod = 0;

    // Population & delta history ring buffer for charting
    private final long[] popHistory = new long[HISTORY_CAP];
    private final long[] birthHistory = new long[HISTORY_CAP];
    private final long[] deathHistory = new long[HISTORY_CAP];
    private int historySize = 0;
    private int historyHead = 0; // next write index

    // Hash ring for exact period detection up to period 60
    private final long[] hashRing = new long[HASH_RING_CAP];
    private final long[] popRing = new long[HASH_RING_CAP];
    private int hashCount = 0;
    private int hashHead = 0;

    public void resetAll(long liveCount, long stateHash, boolean hasBounds,
                         int minX, int minY, int maxX, int maxY) {
        this.generation = 0;
        this.liveCount = liveCount;
        this.lastBirths = 0;
        this.lastDeaths = 0;
        this.totalBirths = 0;
        this.totalDeaths = 0;
        this.peakPopulation = liveCount;
        this.peakGeneration = 0;
        this.hasBounds = hasBounds;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;

        this.historySize = 0;
        this.historyHead = 0;
        this.hashCount = 0;
        this.hashHead = 0;

        pushHistory(liveCount, 0, 0);
        pushHash(stateHash, liveCount);
        this.status = liveCount == 0 ? Status.EXTINCT : Status.IDLE;
        this.detectedPeriod = 0;
    }

    public void onEdited(long liveCount, long stateHash, boolean hasBounds,
                         int minX, int minY, int maxX, int maxY) {
        this.liveCount = liveCount;
        if (liveCount > peakPopulation) {
            peakPopulation = liveCount;
            peakGeneration = generation;
        }
        this.hasBounds = hasBounds;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        // Clear period history after manual edit so we don't match pre-edit hashes
        this.hashCount = 0;
        this.hashHead = 0;
        pushHash(stateHash, liveCount);
        if (historySize == 0) {
            pushHistory(liveCount, 0, 0);
        } else {
            int lastIdx = (historyHead - 1 + HISTORY_CAP) % HISTORY_CAP;
            popHistory[lastIdx] = liveCount;
        }
        this.status = liveCount == 0 ? Status.EXTINCT : Status.IDLE;
        this.detectedPeriod = 0;
    }

    public void onStep(long liveCount, long births, long deaths, long stateHash,
                       boolean hasBounds, int minX, int minY, int maxX, int maxY) {
        this.generation++;
        this.liveCount = liveCount;
        this.lastBirths = births;
        this.lastDeaths = deaths;
        this.totalBirths += births;
        this.totalDeaths += deaths;
        if (liveCount > peakPopulation) {
            peakPopulation = liveCount;
            peakGeneration = generation;
        }
        this.hasBounds = hasBounds;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;

        pushHistory(liveCount, births, deaths);
        pushHash(stateHash, liveCount);

        if (liveCount == 0) {
            status = Status.EXTINCT;
            detectedPeriod = 0;
        } else if (births == 0 && deaths == 0) {
            status = Status.STILL_LIFE;
            detectedPeriod = 1;
        } else {
            int period = detectOscillatorPeriod();
            if (period > 1) {
                status = Status.OSCILLATOR;
                detectedPeriod = period;
            } else {
                status = Status.RUNNING;
                detectedPeriod = 0;
            }
        }
    }

    private void pushHistory(long pop, long births, long deaths) {
        popHistory[historyHead] = pop;
        birthHistory[historyHead] = births;
        deathHistory[historyHead] = deaths;
        historyHead = (historyHead + 1) % HISTORY_CAP;
        if (historySize < HISTORY_CAP) historySize++;
    }

    private void pushHash(long hash, long pop) {
        hashRing[hashHead] = hash;
        popRing[hashHead] = pop;
        hashHead = (hashHead + 1) % HASH_RING_CAP;
        if (hashCount < HASH_RING_CAP) hashCount++;
    }

    private int detectOscillatorPeriod() {
        int maxPeriod = Math.min(120, hashCount - 3);
        int cur = (hashHead - 1 + HASH_RING_CAP) % HASH_RING_CAP;
        for (int p = 2; p <= maxPeriod; p++) {
            int prev1 = (cur - p + HASH_RING_CAP) % HASH_RING_CAP;
            if (hashRing[cur] != hashRing[prev1] || popRing[cur] != popRing[prev1]) continue;

            int curM1 = (cur - 1 + HASH_RING_CAP) % HASH_RING_CAP;
            int prevM1 = (curM1 - p + HASH_RING_CAP) % HASH_RING_CAP;
            if (hashRing[curM1] != hashRing[prevM1] || popRing[curM1] != popRing[prevM1]) continue;

            int curM2 = (cur - 2 + HASH_RING_CAP) % HASH_RING_CAP;
            int prevM2 = (curM2 - p + HASH_RING_CAP) % HASH_RING_CAP;
            if (hashRing[curM2] == hashRing[prevM2] && popRing[curM2] == popRing[prevM2]) {
                return p;
            }
        }
        return 0;
    }

    public int getHistorySize() {
        return historySize;
    }

    public long getPopHistoryAt(int index) {
        int start = (historyHead - historySize + HISTORY_CAP) % HISTORY_CAP;
        return popHistory[(start + index) % HISTORY_CAP];
    }

    public long getBirthHistoryAt(int index) {
        int start = (historyHead - historySize + HISTORY_CAP) % HISTORY_CAP;
        return birthHistory[(start + index) % HISTORY_CAP];
    }

    public long getDeathHistoryAt(int index) {
        int start = (historyHead - historySize + HISTORY_CAP) % HISTORY_CAP;
        return deathHistory[(start + index) % HISTORY_CAP];
    }

    public String getStatusText(boolean ru) {
        switch (status) {
            case EXTINCT:
                return ru ? "Вымирание" : "Extinct";
            case STILL_LIFE:
                return ru ? "Стабильно (натюрморт)" : "Still life";
            case OSCILLATOR:
                return (ru ? "Осциллятор P=" : "Oscillator P=") + detectedPeriod;
            case RUNNING:
                return ru ? "Симуляция" : "Active";
            case IDLE:
            default:
                return ru ? "Пауза" : "Paused";
        }
    }
}
