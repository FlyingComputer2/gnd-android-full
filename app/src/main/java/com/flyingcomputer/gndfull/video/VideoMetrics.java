package com.flyingcomputer.gndfull.video;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Counters from the USB video path (not glass-to-glass latency). */
public final class VideoMetrics {
    private final AtomicInteger rtpPackets = new AtomicInteger();
    private final AtomicInteger accessUnits = new AtomicInteger();
    private final AtomicInteger keyframes = new AtomicInteger();
    private final AtomicLong lastRtpNanos = new AtomicLong();

    public void onRtpPacket() {
        rtpPackets.incrementAndGet();
        lastRtpNanos.set(System.nanoTime());
    }

    public void onAccessUnit(boolean key) {
        accessUnits.incrementAndGet();
        if (key) {
            keyframes.incrementAndGet();
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(
                rtpPackets.get(),
                accessUnits.get(),
                keyframes.get(),
                ageMs(lastRtpNanos.get()));
    }

    private static long ageMs(long nano) {
        if (nano == 0) {
            return -1;
        }
        return Math.max(0, (System.nanoTime() - nano) / 1_000_000L);
    }

    public record Snapshot(int rtpPackets, int accessUnits, int keyframes, long lastRtpAgeMs) {}
}
