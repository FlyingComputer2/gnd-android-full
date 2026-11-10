package com.flyingcomputer.gndfull.edgetx;

/** JNI stub matching the future real simulator adapter API. */
public final class NativeEdgeTxAdapter implements EdgeTxAdapter {
    static {
        System.loadLibrary("gndfull_receiver");
    }

    private static final int LCD_W = 480;
    private static final int LCD_H = 272;

    private long handle;

    public NativeEdgeTxAdapter() {
        handle = nativeCreate();
        if (handle == 0) {
            throw new IllegalStateException("native EdgeTX allocation failed");
        }
    }

    @Override
    public void start() {
        if (!nativeStart(handle)) {
            throw new IllegalStateException("native EdgeTX start failed");
        }
    }

    @Override
    public void stop() {
        nativeStop(handle);
    }

    @Override
    public void submitInput(EdgeTxInputSnapshot snapshot) {
        nativeSubmitInput(handle, snapshot.sticks(), snapshot.switches());
    }

    @Override
    public void keyDown(EdgeTxKey key) {
        // Keys are routed once the real target mapping is pinned.
    }

    @Override
    public void keyUp(EdgeTxKey key) {
        // Keys are routed once the real target mapping is pinned.
    }

    @Override
    public LcdMetadata lcdMetadata() {
        return new LcdMetadata(LCD_W, LCD_H, LCD_W * 2, 1);
    }

    @Override
    public byte[] copyLcdFrameRgb565() {
        byte[] frame = nativeCopyLcdRgb565(handle);
        if (frame == null) {
            throw new IllegalStateException("LCD copy failed");
        }
        return frame;
    }

    @Override
    public int[] copyMixedOutputs() {
        int[] out = nativeCopyOutputs(handle);
        if (out == null) {
            throw new IllegalStateException("output copy failed");
        }
        return out;
    }

    @Override
    public Status status() {
        return new Status(true, "native-stub");
    }

    @Override
    public void close() {
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
    }

    private native long nativeCreate();

    private native void nativeDestroy(long handle);

    private native boolean nativeStart(long handle);

    private native void nativeStop(long handle);

    private native void nativeSubmitInput(long handle, int[] sticks, int[] switches);

    private native byte[] nativeCopyLcdRgb565(long handle);

    private native int[] nativeCopyOutputs(long handle);
}
