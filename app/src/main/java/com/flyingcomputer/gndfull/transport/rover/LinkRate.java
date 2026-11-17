package com.flyingcomputer.gndfull.transport.rover;

import java.util.ArrayDeque;

/**
 * Rate and loss of one air-link direction from the receiver's free-running counters (ground link
 * frame 0x4230 for rover → ground, rover telemetry for ground → rover). Each sample carries the
 * receiver's own monotonic ms, so rates do not depend on USB or radio delivery timing. Counters
 * are u32 and may wrap; a sender clock that goes back (reboot) starts over.
 */
public final class LinkRate {
    /** Shortest span the rate is taken over. */
    static final long MIN_SPAN_MS = 900;
    /** Samples older than this (sender ms) are dropped. */
    static final long KEEP_MS = 3000;
    /** No new sample for this long (local time): unknown. */
    static final long STALE_NANOS = 2_500_000_000L;

    /** Air throughput in Mbit/s and frame loss in percent over the last second or so. */
    public record Result(double mbps, double lossPercent) {}

    private record Sample(long ms, long bytes, long packets, long lost) {}

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private long lastLocalNanos;

    /**
     * Adds a sample (u32 values as read from the wire); repeats of the newest sample (the rover
     * sends the same one in every 20 Hz frame) are ignored. All zero = the sender has none.
     */
    public synchronized void add(long ms, long bytes, long packets, long lost, long localNanos) {
        if (ms == 0 && bytes == 0 && packets == 0 && lost == 0) {
            return;
        }
        Sample last = samples.peekLast();
        if (last != null) {
            long dms = u32(ms - last.ms);
            if (dms == 0) {
                return;
            }
            if (dms > 0x7FFF_FFFFL) {
                samples.clear(); // sender clock went back: restarted
            }
        }
        samples.addLast(new Sample(ms, bytes, packets, lost));
        lastLocalNanos = localNanos;
        while (samples.size() > 2 && u32(ms - samples.peekFirst().ms) > KEEP_MS) {
            samples.removeFirst();
        }
    }

    /** Null until two samples at least {@link #MIN_SPAN_MS} apart, or when stale. */
    public synchronized Result result(long localNanos) {
        Sample newest = samples.peekLast();
        if (newest == null || localNanos - lastLocalNanos > STALE_NANOS) {
            return null;
        }
        Sample base = null;
        for (Sample s : samples) {
            if (u32(newest.ms - s.ms) >= MIN_SPAN_MS) {
                base = s; // keep looking: the newest one still far enough back
            }
        }
        if (base == null) {
            return null;
        }
        long dms = u32(newest.ms - base.ms);
        long dBytes = u32(newest.bytes - base.bytes);
        long dPackets = u32(newest.packets - base.packets);
        long dLost = u32(newest.lost - base.lost);
        double mbps = dBytes * 8.0 / dms / 1000.0;
        long sent = dPackets + dLost;
        double loss = sent == 0 ? 0 : dLost * 100.0 / sent;
        return new Result(mbps, loss);
    }

    public synchronized void clear() {
        samples.clear();
    }

    private static long u32(long v) {
        return v & 0xFFFF_FFFFL;
    }
}
