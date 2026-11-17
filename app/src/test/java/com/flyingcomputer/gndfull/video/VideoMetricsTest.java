package com.flyingcomputer.gndfull.video;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class VideoMetricsTest {
    private static final long MS = 1_000_000L;

    @Test
    public void fpsLatencyAndJitter() {
        VideoMetrics m = new VideoMetrics();
        long t0 = 10_000 * MS;
        assertEquals(-1, m.window(t0).fps(), 0);
        long capture0 = 1_700_000_000_000_000_000L;
        long ground0Us = capture0 / 1000;
        // 30 frames at 33 ms; decode alternates 70 / 74 ms after capture: |D| = 4 ms each time.
        for (int i = 1; i <= 30; i++) {
            long cap = capture0 + i * 33 * MS;
            long delay = (i % 2 == 0 ? 70 : 74) * MS;
            long local = t0 + i * 33 * MS + delay;
            m.onFrameDecoded(cap, local, ground0Us + (i * 33 * MS + delay) / 1000);
        }
        VideoMetrics.Window w = m.window(t0 + 1000 * MS);
        assertEquals(30.0, w.fps(), 1e-9);
        assertEquals(72.0, w.latencyMs(), 1e-6);
        // 29 differences of 4 ms: J = 4 * (1 - (15/16)^29).
        assertEquals(4.0 * (1 - Math.pow(15.0 / 16, 29)), w.jitterMs(), 1e-3);
        // Called again within the second: same window.
        assertEquals(w, m.window(t0 + 1500 * MS));
    }

    @Test
    public void rtpLossAfterFec() {
        VideoMetrics m = new VideoMetrics();
        m.window(0);
        int seq = 65530;
        for (int i = 0; i < 100; i++, seq++) {
            if (i == 10 || i == 11 || i == 50) {
                continue; // 3 lost, one across the 16-bit wrap region
            }
            m.onRtpSequence(seq);
        }
        m.onRtpSequence(seq - 5); // late: ignored
        m.onRtpSequence(seq - 1); // duplicate: ignored
        VideoMetrics.Window w = m.window(1000 * MS);
        assertEquals(3 * 100.0 / 100, w.lossPercent(), 1e-9);
        m.onRtpSequence(seq + 20000); // rover restart: resync, not loss
        m.onRtpSequence(seq + 20001);
        assertEquals(0, m.window(2000 * MS).lossPercent(), 1e-9);
        assertEquals(-1, m.window(3000 * MS).lossPercent(), 0); // nothing arrived
    }

    @Test
    public void unknownWithoutStampsOrClock() {
        VideoMetrics m = new VideoMetrics();
        m.window(0);
        for (int i = 1; i <= 10; i++) {
            m.onFrameDecoded(0, i * 100 * MS, Long.MIN_VALUE);
        }
        VideoMetrics.Window w = m.window(1000 * MS);
        assertEquals(10.0, w.fps(), 1e-9);
        assertEquals(-1, w.latencyMs(), 0);
        assertEquals(-1, w.jitterMs(), 0);
        // Stamped frames but no ground clock yet: jitter known, latency not.
        for (int i = 1; i <= 5; i++) {
            m.onFrameDecoded(i * 33 * MS, 1000 * MS + i * 33 * MS, Long.MIN_VALUE);
        }
        w = m.window(2000 * MS);
        assertEquals(-1, w.latencyMs(), 0);
        assertEquals(0, w.jitterMs(), 1e-9);
    }
}
