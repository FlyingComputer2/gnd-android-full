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
 * rover_sample RC publisher over USB channel 2: a frame as soon as EdgeTX sends a CRSF frame to
 * its external module, and the latest one repeated so the rover gets at least 50 Hz. With External RF off (no CRSF) nothing is sent and the rover goes to
 * failsafe.
 */
public final class RoverControlBridge implements AutoCloseable {
    private static final String TAG = "RoverRc";
    private static final long PERIOD_MS = 20;
    /** A CRSF frame older than this is not sent (EdgeTX stopped, External RF off). */
    private static final long STALE_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    private final ControlSession control;
    private final RcUsbHandler rcUsb;
    private final SynchClient clock;
    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rc-publisher");
                t.setDaemon(true);
                return t;
            });
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile ScheduledFuture<?> tick;
    private volatile boolean rcOpen;
    private volatile long sent;
    private volatile long skippedBlocked;
    private long lastSendNanos; // rc-publisher thread only
    private long lastLogNanos;

    public RoverControlBridge(ControlSession control, RcUsbHandler rcUsb, SynchClient clock) {
        this.control = control;
        this.rcUsb = rcUsb;
        this.clock = clock;
        // Watchdog ticks from creation so the diagnostics line shows even if channel 2 never opens.
        tick = timer.scheduleAtFixedRate(this::watchdog, 0, PERIOD_MS, TimeUnit.MILLISECONDS);
        control.setCrsfListener(
                () -> {
                    try {
                        timer.execute(this::send);
                    } catch (java.util.concurrent.RejectedExecutionException e) {
                        // closed
                    }
                });
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

    /** 20 ms watchdog: diagnostics, and disarm when CRSF stops (no frames = nothing sent). */
    private void watchdog() {
        long now = System.nanoTime();
        if (now - lastLogNanos > TimeUnit.SECONDS.toNanos(5)) {
            lastLogNanos = now;
            CrsfRcChannels last = control.lastCrsf();
            Log.i(
                    TAG,
                    "rc_open=" + rcOpen
                            + " sent=" + sent
                            + " skipped_blocked=" + skippedBlocked
                            + " crsf_age_ms="
                            + (last == null ? -1 : (now - last.receivedNanos) / 1_000_000)
                            + " ch1-8="
                            + (last == null
                                    ? "-"
                                    : java.util.Arrays.toString(
                                            java.util.Arrays.copyOf(last.us, 8)))
                            + " status=" + (last == null ? -1 : last.status)
                            + " state=" + control.state()
                            + " " + control.crsfStats()
                            + " " + clock.stats());
        }
        CrsfRcChannels ch = control.lastCrsf();
        if (ch == null || now - ch.receivedNanos > STALE_NANOS) {
            control.disarm();
            return;
        }
        // EdgeTX's CRSF rate in the simulator is ~18 Hz: repeat the latest fresh frame so the
        // rover still gets 50 Hz (each frame carries the full state).
        // 15 ms, not 20: watchdog ticks land a little early and would skip every other repeat.
        if (now - lastSendNanos >= TimeUnit.MILLISECONDS.toNanos(PERIOD_MS - 5)) {
            send();
        }
    }

    /**
     * Sends one RC frame for the CRSF frame just drained (one per drain, ~50 Hz), so there is no
     * second timer between EdgeTX and the wire. While the app blocks driving (menu open, radio off,
     * USB failsafe) nothing is sent: the rover then holds steering centre and CH2/CH3 at their
     * failsafe positions instead of following sticks moved during setup.
     */
    private void send() {
        if (!rcOpen) {
            return;
        }
        CrsfRcChannels ch = control.lastCrsf();
        if (ch == null) {
            return;
        }
        if (!ch.armed()) {
            control.clearFailsafeOnDisarmedSwitch();
        }
        DrivingState st = control.state();
        if (st != DrivingState.READY && st != DrivingState.ARMED) {
            skippedBlocked++;
            control.disarm();
            return;
        }
        int seq = sequence.incrementAndGet() & 0xFFFF;
        // Ground clock (krono over USB), the one the rover follows, so the rover's rc_lat_ms is
        // the real phone-to-rover time; the phone's own wall clock was ~100 ms off.
        long tUs = clock.groundOrLocalNowUs();
        RoverRcFrame rc = EdgeTxOutputMapper.map(seq, tUs, ch, true);
        if ((rc.flags & RoverRcFrame.FLAG_ARM) != 0) {
            control.requestArm();
        } else {
            control.disarm();
        }
        byte[] wire = MspV2.encode(MspV2.TO_ROVER, RoverRcFrame.FUNCTION, rc.pack());
        lastSendNanos = System.nanoTime();
        if (rcUsb.sendMsp(wire)) {
            sent++;
        }
    }

    @Override
    public void close() {
        if (tick != null) {
            tick.cancel(true);
            tick = null;
        }
        control.setCrsfListener(null);
        timer.shutdownNow();
        rcOpen = false;
    }
}
