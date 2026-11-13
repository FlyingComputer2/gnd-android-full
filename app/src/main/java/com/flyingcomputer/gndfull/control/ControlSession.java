package com.flyingcomputer.gndfull.control;

import com.flyingcomputer.gndfull.crsf.CrsfRcChannels;
import com.flyingcomputer.gndfull.crsf.CrsfStreamParser;
import com.flyingcomputer.gndfull.edgetx.EdgeTxAdapter;
import com.flyingcomputer.gndfull.edgetx.EdgeTxInputSnapshot;
import com.flyingcomputer.gndfull.edgetx.EdgeTxKey;
import android.util.Log;
import com.flyingcomputer.gndfull.edgetx.WasmEdgeTxAdapter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Owns EdgeTX lifecycle and publishes the latest output snapshot. */
public final class ControlSession implements AutoCloseable {
    private final ExecutorService loop = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edgetx-owner");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService tick =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "edgetx-tick");
                t.setDaemon(true);
                return t;
            });
    private final EdgeTxAdapter adapter;
    private final AtomicReference<EdgeTxInputSnapshot> pending =
            new AtomicReference<>(EdgeTxInputSnapshot.neutral());
    private volatile DrivingState state = DrivingState.INACTIVE;
    private volatile int[] lastOutputs = new int[16];
    private volatile boolean menuBlocksDriving;
    private volatile boolean radioPowered = true;
    private volatile ScheduledFuture<?> refresh;
    /** EdgeTX external module (CRSF) output; owner thread only. */
    private final CrsfStreamParser crsfOut = new CrsfStreamParser();
    private volatile CrsfRcChannels lastCrsf;
    private volatile long extTxBytes;
    private volatile long extRxPushes;
    private volatile long extRxRejected;

    public ControlSession(EdgeTxAdapter adapter) {
        this.adapter = adapter;
        loop.execute(
                () -> {
                    try {
                        adapter.start();
                        refreshOutputs();
                    } catch (Exception e) {
                        Log.e("ControlSession", "EdgeTX start failed", e);
                        if (adapter instanceof WasmEdgeTxAdapter wasm) {
                            wasm.noteStartFailure(e.getMessage());
                        }
                    }
                });
        refresh =
                tick.scheduleAtFixedRate(
                        () -> {
                            loop.execute(
                                    () -> {
                                        if (adapter instanceof WasmEdgeTxAdapter wasm) {
                                            wasm.pump();
                                            drainExternalModule(wasm);
                                        }
                                        refreshOutputs();
                                    });
                        },
                        20,
                        20,
                        TimeUnit.MILLISECONDS);
    }

    public DrivingState state() {
        return state;
    }

    public boolean radioPowered() {
        return radioPowered;
    }

    /** Pause host audio when the UI is in the background; do not reboot EdgeTX. */
    public void setForeground(boolean foreground) {
        if (adapter instanceof WasmEdgeTxAdapter wasm) {
            wasm.setForeground(foreground);
        }
    }

    public boolean menuBlocksInput() {
        return menuBlocksDriving;
    }

    public void setRadioPower(boolean on) throws Exception {
        if (on == radioPowered) {
            return;
        }
        radioPowered = on;
        loop.execute(
                () -> {
                    try {
                        if (on) {
                            adapter.radioPowerOn();
                        } else {
                            adapter.radioPowerOff();
                            publishNeutral();
                            state = DrivingState.INACTIVE;
                        }
                    } catch (Exception e) {
                        Log.e("ControlSession", "radio power failed", e);
                        radioPowered = !on;
                        if (adapter instanceof WasmEdgeTxAdapter wasm) {
                            wasm.noteStartFailure(e.getMessage());
                        }
                    }
                });
    }

    public void touchDown(int x, int y) {
        if (!radioPowered || menuBlocksDriving) {
            return;
        }
        loop.execute(
                () -> {
                    if (adapter instanceof com.flyingcomputer.gndfull.edgetx.EdgeTxTouchSink touch) {
                        touch.touchDown(x, y);
                    }
                });
    }

    public void touchUp() {
        if (!radioPowered) {
            return;
        }
        loop.execute(
                () -> {
                    if (adapter instanceof com.flyingcomputer.gndfull.edgetx.EdgeTxTouchSink touch) {
                        touch.touchUp();
                    }
                });
    }

    public void setMenuOpen(boolean open) {
        menuBlocksDriving = open;
        if (open) {
            state = DrivingState.INACTIVE;
            disarm();
        } else if (state == DrivingState.INACTIVE || state == DrivingState.FAILSAFE) {
            state = DrivingState.READY;
        }
        syncHardwareInput();
    }

    public void requestArm() {
        if (menuBlocksDriving || state == DrivingState.FAILSAFE) {
            return;
        }
        state = DrivingState.ARMED;
    }

    public void disarm() {
        if (state == DrivingState.ARMED) {
            state = DrivingState.READY;
        }
    }

  /**
   * Pushes stick/switch state into EdgeTX. FAILSAFE and the app hamburger gate rover output only,
   * not the virtual radio used for model setup and mixer previews.
   */
    public void publishInput(EdgeTxInputSnapshot snapshot) {
        pending.set(snapshot);
        if (!radioPowered) {
            publishNeutral();
            return;
        }
        if (menuBlocksDriving) {
            return;
        }
        syncHardwareInput(snapshot);
    }

    public void keyEvent(EdgeTxKey key, boolean down) {
        loop.execute(
                () -> {
                    if (down) {
                        adapter.keyDown(key);
                    } else {
                        adapter.keyUp(key);
                    }
                    refreshOutputs();
                });
    }

    public void trimEvent(int trimIndex, boolean down) {
        loop.execute(
                () -> {
                    if (down) {
                        adapter.trimDown(trimIndex);
                    } else {
                        adapter.trimUp(trimIndex);
                    }
                    refreshOutputs();
                });
    }

    public int[] lastOutputs() {
        return lastOutputs.clone();
    }

    public EdgeTxAdapter adapter() {
        return adapter;
    }

    public void onUsbConnected() {
        if (!menuBlocksDriving && state != DrivingState.FAILSAFE) {
            state = DrivingState.READY;
        }
    }

    public void onUsbDisconnected() {
        state = DrivingState.FAILSAFE;
    }

    private void syncHardwareInput() {
        syncHardwareInput(pending.get());
    }

    private void syncHardwareInput(EdgeTxInputSnapshot snapshot) {
        if (!radioPowered || menuBlocksDriving) {
            return;
        }
        loop.execute(
                () -> {
                    adapter.submitInput(snapshot);
                    refreshOutputs();
                });
    }

    /**
     * Newest CRSF RC channels frame EdgeTX sent to its external module (External RF = CRSF), or
     * null if none yet. Check {@link CrsfRcChannels#receivedNanos} for staleness.
     */
    public CrsfRcChannels lastCrsf() {
        return lastCrsf;
    }

    /** Feeds CRSF telemetry bytes to EdgeTX's external module, as if from the module. */
    public void pushExternalModuleRx(byte[] frames) {
        if (frames == null || frames.length == 0 || !radioPowered) {
            return;
        }
        loop.execute(
                () -> {
                    if (adapter instanceof WasmEdgeTxAdapter wasm) {
                        if (wasm.pushExternalModuleRx(frames)) {
                            extRxPushes++;
                        } else {
                            extRxRejected++;
                        }
                    }
                });
    }

    /** One line for logcat: external module bytes, CRSF frames and telemetry pushes. */
    public String crsfStats() {
        return "ext_tx_bytes=" + extTxBytes
                + " crsf_rc=" + crsfOut.rcFrames()
                + " crsf_crc_err=" + crsfOut.crcErrors()
                + " ext_rx_push=" + extRxPushes
                + " ext_rx_rejected=" + extRxRejected;
    }

    private void drainExternalModule(WasmEdgeTxAdapter wasm) {
        byte[] out = wasm.readExternalModuleTx();
        extTxBytes += out.length;
        CrsfRcChannels rc = crsfOut.feed(out, System.nanoTime());
        if (rc != null) {
            lastCrsf = rc;
        }
    }

    private void publishNeutral() {
        loop.execute(
                () -> {
                    adapter.submitInput(EdgeTxInputSnapshot.neutral());
                    refreshOutputs();
                });
    }

    private void refreshOutputs() {
        lastOutputs = adapter.copyMixedOutputs();
    }

    @Override
    public void close() {
        if (refresh != null) {
            refresh.cancel(true);
        }
        tick.shutdownNow();
        loop.execute(
                () -> {
                    adapter.stop();
                    adapter.close();
                });
        loop.shutdownNow();
    }
}
