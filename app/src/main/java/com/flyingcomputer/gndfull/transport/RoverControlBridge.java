package com.flyingcomputer.gndfull.transport;

import android.util.Log;
import com.flyingcomputer.gndfull.control.ControlSession;
import com.flyingcomputer.gndfull.control.DrivingState;
import com.flyingcomputer.gndfull.crsf.CrsfRcChannels;
import com.flyingcomputer.gndfull.transport.msp.MspV2;
import com.flyingcomputer.gndfull.transport.rover.EdgeTxOutputMapper;
import com.flyingcomputer.gndfull.transport.rover.RoverRcFrame;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 50 Hz rover_sample RC publisher over USB channel 2, fed by the CRSF channels EdgeTX sends to its
 * external module. With External RF off (no fresh CRSF frame) nothing is sent and the rover goes
 * to failsafe.
 */
public final class RoverControlBridge implements AutoCloseable {
    private static final String TAG = "RoverRc";
    private static final long PERIOD_MS = 20;
    /** A CRSF frame older than this is not sent (EdgeTX stopped, External RF off). */
    private static final long STALE_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    private final ControlSession control;
    private final RcUsbHandler rcUsb;
    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rc-publisher");
                t.setDaemon(true);
                return t;
            });
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile ScheduledFuture<?> tick;
    private volatile boolean rcOpen;
    private long sent;
    private long skippedStale;
    private long lastLogNanos;

    public RoverControlBridge(ControlSession control, RcUsbHandler rcUsb) {
        this.control = control;
        this.rcUsb = rcUsb;
        // Ticks from creation so the diagnostics line shows even if channel 2 never opens.
        tick = timer.scheduleAtFixedRate(this::publish, 0, PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    public void onRcChannelOpen() {
        Log.i(TAG, "RC channel 2 open");
        rcOpen = true;
    }

    public void onRcChannelClose() {
        Log.i(TAG, "RC channel 2 closed");
        rcOpen = false;
        control.disarm();
    }

    private void publish() {
        long now = System.nanoTime();
        if (now - lastLogNanos > TimeUnit.SECONDS.toNanos(5)) {
            lastLogNanos = now;
            CrsfRcChannels last = control.lastCrsf();
            Log.i(
                    TAG,
                    "rc_open=" + rcOpen
                            + " sent=" + sent
                            + " skipped_stale=" + skippedStale
                            + " crsf_age_ms="
                            + (last == null ? -1 : (now - last.receivedNanos) / 1_000_000)
                            + " ch1-8="
                            + (last == null
                                    ? "-"
                                    : java.util.Arrays.toString(
                                            java.util.Arrays.copyOf(last.us, 8)))
                            + " status=" + (last == null ? -1 : last.status)
                            + " state=" + control.state()
                            + " " + control.crsfStats());
        }
        if (!rcOpen) {
            return;
        }
        CrsfRcChannels ch = control.lastCrsf();
        if (ch == null || now - ch.receivedNanos > STALE_NANOS) {
            skippedStale++;
            control.disarm();
            return;
        }
        sent++;
        if (!ch.armed()) {
            control.clearFailsafeOnDisarmedSwitch();
        }
        DrivingState st = control.state();
        boolean allowArm = st == DrivingState.READY || st == DrivingState.ARMED;
        int seq = sequence.incrementAndGet() & 0xFFFF;
        long tUs = System.currentTimeMillis() * 1000L;
        RoverRcFrame rc = EdgeTxOutputMapper.map(seq, tUs, ch, allowArm);
        if ((rc.flags & RoverRcFrame.FLAG_ARM) != 0) {
            control.requestArm();
        } else {
            control.disarm();
        }
        byte[] wire = MspV2.encode(MspV2.TO_ROVER, RoverRcFrame.FUNCTION, rc.pack());
        if (!rcUsb.sendMsp(wire)) {
            // Queue full or channel stalled; rover failsafe handles silence.
        }
    }

    @Override
    public void close() {
        if (tick != null) {
            tick.cancel(true);
            tick = null;
        }
        timer.shutdownNow();
        rcOpen = false;
    }
}
