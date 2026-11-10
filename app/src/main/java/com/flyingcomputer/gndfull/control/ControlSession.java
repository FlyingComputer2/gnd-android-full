package com.flyingcomputer.gndfull.control;

import com.flyingcomputer.gndfull.edgetx.EdgeTxAdapter;
import com.flyingcomputer.gndfull.edgetx.EdgeTxInputSnapshot;
import com.flyingcomputer.gndfull.edgetx.FakeEdgeTxAdapter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** Owns EdgeTX lifecycle and publishes the latest output snapshot. */
public final class ControlSession implements AutoCloseable {
    private final ExecutorService loop = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edgetx-owner");
        t.setDaemon(true);
        return t;
    });
    private final EdgeTxAdapter adapter;
    private final AtomicReference<EdgeTxInputSnapshot> pending =
            new AtomicReference<>(EdgeTxInputSnapshot.neutral());
    private volatile DrivingState state = DrivingState.INACTIVE;
    private volatile int[] lastOutputs = new int[16];
    private volatile boolean menuBlocksDriving;

    public ControlSession(boolean useNativeStub) throws Exception {
        adapter = useNativeStub ? new com.flyingcomputer.gndfull.edgetx.NativeEdgeTxAdapter()
                : new FakeEdgeTxAdapter();
        loop.execute(
                () -> {
                    try {
                        adapter.start();
                    } catch (Exception e) {
                        throw new IllegalStateException("EdgeTX start failed", e);
                    }
                });
    }

    public DrivingState state() {
        return state;
    }

    public void setMenuOpen(boolean open) {
        menuBlocksDriving = open;
        if (open) {
            state = DrivingState.INACTIVE;
            publishNeutral();
        } else if (state == DrivingState.INACTIVE) {
            state = DrivingState.READY;
        }
    }

    public void publishInput(EdgeTxInputSnapshot snapshot) {
        pending.set(snapshot);
        if (menuBlocksDriving || state == DrivingState.FAILSAFE) {
            publishNeutral();
            return;
        }
        loop.execute(
                () -> {
                    adapter.submitInput(snapshot);
                    lastOutputs = adapter.copyMixedOutputs();
                });
    }

    public int[] lastOutputs() {
        return lastOutputs.clone();
    }

    public EdgeTxAdapter adapter() {
        return adapter;
    }

    public void onUsbConnected() {
        if (!menuBlocksDriving) {
            state = DrivingState.READY;
        }
    }

    public void onUsbDisconnected() {
        state = DrivingState.FAILSAFE;
        publishNeutral();
    }

    private void publishNeutral() {
        loop.execute(
                () -> {
                    adapter.submitInput(EdgeTxInputSnapshot.neutral());
                    lastOutputs = adapter.copyMixedOutputs();
                });
    }

    @Override
    public void close() {
        loop.execute(
                () -> {
                    adapter.stop();
                    adapter.close();
                });
        loop.shutdownNow();
    }
}
