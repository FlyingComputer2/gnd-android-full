package com.flyingcomputer.gndfull.edgetx;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

/** EdgeTX TX16S simulator via WAMR (real LCD + mixer when wasm asset is present). */
public final class WasmEdgeTxAdapter implements EdgeTxAdapter {
    private static final String WASM_ASSET = "edgetx/edgetx-tx16s-simulator.wasm";

    static {
        System.loadLibrary("gndfull_edgetx");
    }

    private final Context context;
    private final long handle;
    private volatile boolean running;
    private byte[] lcdCache = new byte[0];
    private int lcdW;
    private int lcdH;

    public WasmEdgeTxAdapter(Context context) {
        this.context = context.getApplicationContext();
        File wasm = ensureWasmOnDisk();
        File sdRoot = new File(context.getFilesDir(), "edgetx/sd");
        if (!sdRoot.mkdirs() && !sdRoot.isDirectory()) {
            throw new IllegalStateException("cannot create EdgeTX sd dir");
        }
        handle = nativeCreate(wasm.getAbsolutePath(), sdRoot.getAbsolutePath(), sdRoot.getAbsolutePath());
        if (handle == 0) {
            throw new IllegalStateException("EdgeTX wasm load failed — run scripts/build-edgetx-wasm.sh");
        }
        lcdW = nativeLcdWidth(handle);
        lcdH = nativeLcdHeight(handle);
    }

    private File ensureWasmOnDisk() {
        File out = new File(context.getFilesDir(), "edgetx/edgetx-tx16s-simulator.wasm");
        if (out.exists() && out.length() > 0) {
            return out;
        }
        try (InputStream in = context.getAssets().open(WASM_ASSET);
                FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        } catch (Exception e) {
            throw new IllegalStateException("missing wasm asset: " + WASM_ASSET, e);
        }
        return out;
    }

    @Override
    public void start() {
        if (!nativeStart(handle)) {
            throw new IllegalStateException(nativeLastError(handle));
        }
        running = true;
    }

    @Override
    public void stop() {
        nativeStop(handle);
        running = false;
    }

    @Override
    public void submitInput(EdgeTxInputSnapshot input) {
        int[] sticks = input.sticks();
        nativeSetAnalog(handle, Tx16InputMap.ANALOG_LH, Tx16InputMap.stickToEdgeTxPerMille(sticks[0]));
        nativeSetAnalog(handle, Tx16InputMap.ANALOG_LV, Tx16InputMap.stickToEdgeTxPerMille(sticks[1]));
        nativeSetAnalog(handle, Tx16InputMap.ANALOG_RH, Tx16InputMap.stickToEdgeTxPerMille(sticks[2]));
        nativeSetAnalog(handle, Tx16InputMap.ANALOG_RV, Tx16InputMap.stickToEdgeTxPerMille(sticks[3]));
        int[] sw = input.switches();
        for (int i = 0; i < sw.length && i < 8; i++) {
            nativeSetSwitch(handle, i, sw[i]);
        }
    }

    @Override
    public void keyDown(EdgeTxKey key) {
        nativeSetKey(handle, mapKey(key), true);
    }

    @Override
    public void keyUp(EdgeTxKey key) {
        nativeSetKey(handle, mapKey(key), false);
    }

    @Override
    public LcdMetadata lcdMetadata() {
        return new LcdMetadata(lcdW, lcdH, lcdW * 2, 1);
    }

    @Override
    public byte[] copyLcdFrameRgb565() {
        byte[] frame = nativeCopyLcdRgb565(handle);
        if (frame == null || frame.length == 0) {
            return lcdCache;
        }
        lcdCache = frame;
        nativeLcdFlushed(handle);
        return frame;
    }

    @Override
    public int[] copyMixedOutputs() {
        int[] raw = nativeCopyChannelOutputs(handle);
        if (raw == null) {
            return new int[16];
        }
        int[] pwm = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            pwm[i] = 1500 + (raw[i] * 500) / 1024;
        }
        return pwm.length >= 16 ? Arrays.copyOf(pwm, 16) : pwm;
    }

    @Override
    public Status status() {
        return new Status(running, "edgetx-tx16s-wasm");
    }

    @Override
    public void close() {
        stop();
        nativeDestroy(handle);
    }

    public void touchDown(int x, int y) {
        nativeTouchDown(handle, x, y);
    }

    public void touchUp() {
        nativeTouchUp(handle);
    }

    public void pump() {
        nativePump(handle);
    }

    private static int mapKey(EdgeTxKey key) {
        return switch (key) {
            case SYS -> 0;
            case MDL -> 1;
            case UP -> 2;
            case DOWN -> 3;
            case LEFT -> 4;
            case RIGHT -> 5;
            case ENTER -> 6;
        };
    }

    private native long nativeCreate(String wasmPath, String sdPath, String settingsPath);

    private native void nativeDestroy(long handle);

    private native boolean nativeStart(long handle);

    private native void nativeStop(long handle);

    private native void nativePump(long handle);

    private native void nativeSetAnalog(long handle, int index, int value);

    private native void nativeSetSwitch(long handle, int index, int state);

    private native void nativeSetKey(long handle, int key, boolean down);

    private native void nativeTouchDown(long handle, int x, int y);

    private native void nativeTouchUp(long handle);

    private native void nativeLcdFlushed(long handle);

    private native int nativeLcdWidth(long handle);

    private native int nativeLcdHeight(long handle);

    private native byte[] nativeCopyLcdRgb565(long handle);

    private native int[] nativeCopyChannelOutputs(long handle);

    private native String nativeLastError(long handle);
}
