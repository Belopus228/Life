package com.belopus.life.ui;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

public final class SoundEngine {

    private static final int SAMPLE_RATE = 22050;
    private volatile boolean enabled = false;
    private long lastTickMs = 0L;
    private long lastDrawMs = 0L;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void playDrawPop(boolean alive) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        if (now - lastDrawMs < 35) return;
        lastDrawMs = now;
        playToneAsync(alive ? 660.0 : 390.0, 18, 0.12f);
    }

    public void playStepPulse(long births, long deaths) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        if (now - lastTickMs < 85) return;
        lastTickMs = now;
        double baseFreq = 240.0 + Math.min(600.0, (births + deaths) * 3.5);
        playToneAsync(baseFreq, 24, 0.08f);
    }

    public void playMilestone() {
        if (!enabled) return;
        playToneAsync(880.0, 75, 0.14f);
    }

    private void playToneAsync(final double freqHz, final int durationMs, final float volume) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                AudioTrack track = null;
                try {
                    int numSamples = Math.max(64, (SAMPLE_RATE * durationMs) / 1000);
                    short[] samples = new short[numSamples];
                    for (int i = 0; i < numSamples; i++) {
                        double env = Math.sin(Math.PI * i / numSamples);
                        double s = Math.sin(2.0 * Math.PI * freqHz * i / SAMPLE_RATE) * env * volume;
                        samples[i] = (short) (s * Short.MAX_VALUE);
                    }
                    int minBuf = AudioTrack.getMinBufferSize(
                            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    int bufBytes = Math.max(minBuf, numSamples * 2);

                    track = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build())
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setSampleRate(SAMPLE_RATE)
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build())
                            .setBufferSizeInBytes(bufBytes)
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build();

                    track.write(samples, 0, numSamples);
                    track.play();
                    Thread.sleep(durationMs + 25L);
                } catch (Throwable ignored) {
                } finally {
                    if (track != null) {
                        try {
                            track.stop();
                        } catch (Throwable ignored) {}
                        try {
                            track.release();
                        } catch (Throwable ignored) {}
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
