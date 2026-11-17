package com.flyingcomputer.gndfull.video;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters from the USB video path, plus per-frame timing at the decoder output for the OSD:
 *
 * <ul>
 *   <li>fps: frames the decoder output per second;
 *   <li>latency: camera-to-decode, the ground P4's clock (krono over USB, {@code SynchClient})
 *       when the decoder hands out a frame minus the rover's capture stamp (CLOCK_REALTIME, RTP
 *       header extension id 2);
 *   <li>jitter: RFC 3550 interarrival jitter of the decoded frames against their capture stamps
 *       (|ΔD| smoothed by 1/16). It takes only differences, so it needs no clock sync;
 *   <li>loss: RTP packets missing from the sequence as they arrive over USB, i.e. what the
 *       ground's FEC did not recover (plus any USB drop), over the window.
 * </ul>
 */
public final class VideoMetrics {
    /** Window the OSD's fps and mean latency are taken over. */
    static final long WINDOW_NANOS = 1_000_000_000L;

    private final AtomicInteger rtpPackets = new AtomicInteger();
    private final AtomicInteger accessUnits = new AtomicInteger();
    private final AtomicInteger keyframes = new AtomicInteger();
    private final AtomicLong lastRtpNanos = new AtomicLong();

    // Decoder-output timing, guarded by this.
    private long frames;
    private long latencySumUs;
    private long latencyCount;
    private long stampedFrames;
    private boolean havePrevTransit;
    private long prevTransitUs;
    private double jitterUs;
    private boolean windowOpen;
    private long windowStartNanos;
    private long windowFrames;
    private long windowStamped;
    private Window last = new Window(-1, -1, -1, -1);
    // RTP sequence (feed path), guarded by this.
    private int lastSeq = -1;
    private long rtpReceived;
    private long rtpLost;
    /** A jump larger than this is a new stream (rover restart), not loss. */
    static final int SEQ_RESYNC = 3000;

    /**
     * fps, mean camera-to-decode latency (ms), jitter (ms) and post-FEC RTP loss (%) of one
     * window; -1 = unknown.
     */
    public record Window(double fps, double latencyMs, double jitterMs, double lossPercent) {}

    public void onRtpPacket() {
        rtpPackets.incrementAndGet();
        lastRtpNanos.set(System.nanoTime());
    }

    /** One RTP packet's sequence number (16 bit), in arrival order. */
    public synchronized void onRtpSequence(int seq) {
        seq &= 0xFFFF;
        if (lastSeq >= 0) {
            int d = (seq - lastSeq) & 0xFFFF;
            if (d == 0 || d > 0x8000) {
                return; // duplicate or late
            }
            if (d <= SEQ_RESYNC) {
                rtpLost += d - 1;
            }
        }
        lastSeq = seq;
        rtpReceived++;
    }

    public void onAccessUnit(boolean key) {
        accessUnits.incrementAndGet();
        if (key) {
            keyframes.incrementAndGet();
        }
    }

    /**
     * One frame out of the decoder.
     *
     * @param captureNs the rover's capture stamp (CLOCK_REALTIME ns), 0 when the frame had none
     * @param localNanos this phone's monotonic time now ({@code System.nanoTime()})
     * @param groundNowUs the ground P4's CLOCK_REALTIME µs now, or {@code Long.MIN_VALUE} before
     *     clock sync
     */
    public synchronized void onFrameDecoded(long captureNs, long localNanos, long groundNowUs) {
        frames++;
        if (captureNs <= 0) {
            return;
        }
        stampedFrames++;
        long transitUs = localNanos / 1000 - captureNs / 1000;
        if (havePrevTransit) {
            double d = Math.abs(transitUs - prevTransitUs);
            jitterUs += (d - jitterUs) / 16.0;
        }
        prevTransitUs = transitUs;
        havePrevTransit = true;
        if (groundNowUs == Long.MIN_VALUE) {
            return;
        }
        long us = groundNowUs - captureNs / 1000;
        if (us >= 0 && us < 10_000_000) {
            latencySumUs += us;
            latencyCount++;
        }
    }

    /**
     * The last closed window; closes a new one once {@link #WINDOW_NANOS} has passed, so the OSD
     * may call this more often than once a second.
     */
    public synchronized Window window(long nowNanos) {
        if (!windowOpen) {
            windowOpen = true;
            windowStartNanos = nowNanos;
            rtpReceived = 0;
            rtpLost = 0;
            windowFrames = frames;
            windowStamped = stampedFrames;
            return last;
        }
        long span = nowNanos - windowStartNanos;
        if (span < WINDOW_NANOS) {
            return last;
        }
        double fps = (frames - windowFrames) * 1e9 / span;
        boolean stamped = stampedFrames - windowStamped >= 2 && havePrevTransit;
        double latency = latencyCount > 0 ? latencySumUs / 1000.0 / latencyCount : -1;
        long sent = rtpReceived + rtpLost;
        double loss = sent > 0 ? rtpLost * 100.0 / sent : -1;
        last = new Window(fps, latency, stamped ? jitterUs / 1000.0 : -1, loss);
        rtpReceived = 0;
        rtpLost = 0;
        windowStartNanos = nowNanos;
        windowFrames = frames;
        windowStamped = stampedFrames;
        latencySumUs = 0;
        latencyCount = 0;
        return last;
    }

    /** A new stream (reconnect): forget the previous frame and window. */
    public synchronized void reset() {
        havePrevTransit = false;
        jitterUs = 0;
        windowOpen = false;
        lastSeq = -1;
        rtpReceived = 0;
        rtpLost = 0;
        latencySumUs = 0;
        latencyCount = 0;
        last = new Window(-1, -1, -1, -1);
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
