package com.flyingcomputer.gndfull.edgetx;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic stand-in until the pinned EdgeTX simulator is linked. */
public final class FakeEdgeTxAdapter implements EdgeTxAdapter {
    private static final int LCD_W = 480;
    private static final int LCD_H = 272;
    private static final int CHANNELS = 16;

    private final AtomicReference<EdgeTxInputSnapshot> input =
            new AtomicReference<>(EdgeTxInputSnapshot.neutral());
    private volatile boolean running;
    private final int[] outputs = new int[CHANNELS];

    @Override
    public void start() {
        running = true;
        tick();
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public void submitInput(EdgeTxInputSnapshot snapshot) {
        input.set(snapshot);
        if (running) {
            tick();
        }
    }

    @Override
    public void keyDown(EdgeTxKey key) {
        // Navigation keys are no-ops in the fake LCD for now.
    }

    @Override
    public void keyUp(EdgeTxKey key) {
        // Navigation keys are no-ops in the fake LCD for now.
    }

    @Override
    public LcdMetadata lcdMetadata() {
        return new LcdMetadata(LCD_W, LCD_H, LCD_W * 2, 1);
    }

    @Override
    public byte[] copyLcdFrameRgb565() {
        EdgeTxInputSnapshot snap = input.get();
        int[] sticks = snap.sticks();
        byte[] out = new byte[LCD_W * LCD_H * 2];
        for (int y = 0; y < LCD_H; y++) {
            for (int x = 0; x < LCD_W; x++) {
                boolean edge = x < 2 || y < 2 || x >= LCD_W - 2 || y >= LCD_H - 2;
                int rgb565 = edge ? 0x4a69 : 0x2d16;
                int cx = LCD_W / 2 + (sticks[0] * LCD_W) / 2048;
                int cy = LCD_H / 2 - (sticks[1] * LCD_H) / 2048;
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= 100) {
                    rgb565 = 0xf800;
                }
                int at = (y * LCD_W + x) * 2;
                out[at] = (byte) (rgb565 & 0xff);
                out[at + 1] = (byte) ((rgb565 >> 8) & 0xff);
            }
        }
        return out;
    }

    @Override
    public int[] copyMixedOutputs() {
        return Arrays.copyOf(outputs, outputs.length);
    }

    @Override
    public Status status() {
        return new Status(running, "fake-edgetx");
    }

    @Override
    public void close() {
        stop();
    }

    private void tick() {
        EdgeTxInputSnapshot snap = input.get();
        int[] sticks = snap.sticks();
        int[] switches = snap.switches();
        for (int i = 0; i < 4; i++) {
            outputs[i] = 512 + (sticks[i] * 512) / 1024;
        }
        for (int i = 0; i < 8; i++) {
            outputs[4 + i] = switches[i] < 0 ? 988 : (switches[i] > 0 ? 2012 : 1500);
        }
        for (int i = 12; i < CHANNELS; i++) {
            outputs[i] = 1500;
        }
    }
}
