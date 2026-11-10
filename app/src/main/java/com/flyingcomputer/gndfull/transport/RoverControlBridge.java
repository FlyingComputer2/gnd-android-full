package com.flyingcomputer.gndfull.transport;

import com.flyingcomputer.gndfull.control.ControlSession;
import com.flyingcomputer.gndfull.control.DrivingState;
import com.flyingcomputer.gndfull.transport.msp.MspV2;
import com.flyingcomputer.gndfull.transport.rover.EdgeTxOutputMapper;
import com.flyingcomputer.gndfull.transport.rover.RoverRcFrame;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 50 Hz rover_sample RC publisher over USB channel 2. */
public final class RoverControlBridge implements AutoCloseable {
    private static final long PERIOD_MS = 20;

    private final ControlSession control;
    private final RcUsbHandler rcUsb;
    private final EdgeTxOutputMapper mapper = new EdgeTxOutputMapper();
    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rc-publisher");
                t.setDaemon(true);
                return t;
            });
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile ScheduledFuture<?> tick;
    private volatile int[] lastSwitches = new int[8];
    private volatile boolean rcOpen;

    public RoverControlBridge(ControlSession control, RcUsbHandler rcUsb) {
        this.control = control;
        this.rcUsb = rcUsb;
    }

    public void onRcChannelOpen() {
        rcOpen = true;
        if (tick == null) {
            tick = timer.scheduleAtFixedRate(this::publish, 0, PERIOD_MS, TimeUnit.MILLISECONDS);
        }
    }

    public void onRcChannelClose() {
        rcOpen = false;
        control.disarm();
    }

    public void updateSwitches(int[] switches) {
        lastSwitches = switches.clone();
        if (mapper.armIntent(switches)
                && control.state() == DrivingState.READY
                && mapper.throttleNeutral(control.lastOutputs())) {
            control.requestArm();
        } else if (!mapper.armIntent(switches)) {
            control.disarm();
        }
    }

    private void publish() {
        if (!rcOpen) {
            return;
        }
        int seq = sequence.incrementAndGet() & 0xFFFF;
        long tUs = System.currentTimeMillis() * 1000L;
        RoverRcFrame rc =
                mapper.map(
                        seq,
                        tUs,
                        control.lastOutputs(),
                        lastSwitches,
                        control.state(),
                        control.state() == DrivingState.ARMED);
        byte[] wire =
                MspV2.encode(MspV2.TO_ROVER, RoverRcFrame.FUNCTION, rc.pack());
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
