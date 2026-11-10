package com.flyingcomputer.gndfull.edgetx;

import android.content.Context;
import android.util.Log;
import com.flyingcomputer.gndfull.edgetx.audio.AudioTrackEdgeTxAudioAdapter;
import com.flyingcomputer.gndfull.edgetx.audio.EdgeTxAudioAdapter;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

/** EdgeTX TX16S simulator via WAMR. */
public final class WasmEdgeTxAdapter implements EdgeTxAdapter, EdgeTxTouchSink {
    private static final String TAG = "WasmEdgeTxAdapter";
    private static final String WASM_ASSET = "edgetx/edgetx-tx16s-simulator.wasm";

    static {
        System.loadLibrary("gndfull_edgetx");
    }

    private final Context context;
    private final EdgeTxAudioAdapter audio;
    private final File sdRoot;
    private final long handle;
    private volatile boolean running;
    private volatile boolean awaitingPostBootStickModePatch;
    private volatile String startFailure;
    private byte[] lcdCache = new byte[0];
    private int lcdW;
    private int lcdH;

    public WasmEdgeTxAdapter(Context context) {
        this(context, new AudioTrackEdgeTxAudioAdapter());
    }

    WasmEdgeTxAdapter(Context context, EdgeTxAudioAdapter audio) {
        this.context = context.getApplicationContext();
        this.audio = audio;
        File wasm = ensureWasmOnDisk();
        File sdRoot;
        File sd;
        try {
            sd = EdgeTxSdSeedInstaller.ensureSdRoot(context);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("EdgeTX SD seed failed", e);
        }
        this.sdRoot = sd;
        handle =
                nativeCreate(
                        sd.getAbsolutePath(), sd.getAbsolutePath(), sd.getAbsolutePath());
        if (handle == 0) {
            throw new IllegalStateException("EdgeTX wasm load failed");
        }
        lcdW = nativeLcdWidth(handle);
        lcdH = nativeLcdHeight(handle);
        if (audio instanceof AudioTrackEdgeTxAudioAdapter player) {
            nativeBindAudioSink(handle, player);
        }
    }

    private File ensureWasmOnDisk() {
        File out = new File(context.getFilesDir(), "edgetx/edgetx-tx16s-simulator.wasm");
        File parent = out.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IllegalStateException("cannot create EdgeTX files dir");
        }
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
        startFailure = null;
        try {
            EdgeTxRadioPrefs.ApplyResult prefs = EdgeTxRadioPrefs.applyDefaults(sdRoot);
            awaitingPostBootStickModePatch = prefs == EdgeTxRadioPrefs.ApplyResult.NEEDS_POST_BOOT_PATCH;
        } catch (IOException e) {
            Log.w(TAG, "radio prefs before start: " + e.getMessage());
        }
        audio.start();
        int utcOffsetSec = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000;
        if (!nativeStart(handle, utcOffsetSec)) {
            audio.stop();
            throw new IllegalStateException("simuStart failed");
        }
        running = true;
    }

  /** Set when {@link com.flyingcomputer.gndfull.control.ControlSession} fails async start. */
    public void noteStartFailure(String message) {
        startFailure = message;
        running = false;
    }

    @Override
    public void stop() {
        nativeStop(handle);
        audio.stop();
        running = false;
    }

    @Override
    public void radioPowerOff() {
        stop();
    }

    @Override
    public void radioPowerOn() throws Exception {
        start();
    }

    @Override
    public boolean radioPowered() {
        return running;
    }

    @Override
    public void submitInput(EdgeTxInputSnapshot input) {
        if (!running) {
            return;
        }
        int[] sticks = input.sticks();
        nativeSetAnalog(handle, Tx16InputMap.ANALOG_LH, Tx16InputMap.stickToEdgeTxPerMille(sticks[0]));
        nativeSetAnalog(
                handle,
                Tx16InputMap.ANALOG_LV,
                Tx16InputMap.stickY(sticks[1], Tx16InputMap.ANALOG_LV));
        nativeSetAnalog(handle, Tx16InputMap.ANALOG_RV, Tx16InputMap.stickToEdgeTxPerMille(sticks[3]));
        nativeSetAnalog(
                handle, Tx16InputMap.ANALOG_RH, Tx16InputMap.stickY(sticks[2], Tx16InputMap.ANALOG_RH));
        int[] sw = input.switches();
        for (int i = 0; i < sw.length && i < 8; i++) {
            nativeSetSwitch(handle, i, Tx16InputMap.switchToFirmware(i, sw[i]));
        }
    }

    @Override
    public void keyDown(EdgeTxKey key) {
        if (!running) {
            return;
        }
        nativeSetKey(handle, Tx16KeyMap.toFirmware(key), true);
    }

    @Override
    public void keyUp(EdgeTxKey key) {
        if (!running) {
            return;
        }
        nativeSetKey(handle, Tx16KeyMap.toFirmware(key), false);
    }

    @Override
    public void touchDown(int lcdX, int lcdY) {
        if (!running) {
            return;
        }
        nativeTouchDown(handle, lcdX, lcdY);
    }

    @Override
    public void touchUp() {
        if (!running) {
            return;
        }
        nativeTouchUp(handle);
    }

    public void pump() {
        if (!running) {
            return;
        }
        nativePump(handle);
        if (awaitingPostBootStickModePatch && nativeBootComplete(handle)) {
            try {
                if (EdgeTxRadioPrefs.completePostBootDefaults(sdRoot)) {
                    restartRadioAfterRadioYamlPatch();
                }
            } catch (IOException e) {
                Log.w(TAG, "post-boot stick mode patch: " + e.getMessage());
            } finally {
                awaitingPostBootStickModePatch = false;
            }
        }
    }

    private void restartRadioAfterRadioYamlPatch() {
        nativeStop(handle);
        int utcOffsetSec =
                java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000;
        if (!nativeStart(handle, utcOffsetSec)) {
            running = false;
            audio.stop();
            startFailure = "stick mode restart failed";
            return;
        }
        running = true;
    }

    public boolean bootComplete() {
        return running && nativeBootComplete(handle);
    }

    @Override
    public LcdMetadata lcdMetadata() {
        return new LcdMetadata(lcdW, lcdH, lcdW * 2, 1);
    }

    @Override
    public byte[] copyLcdFrameRgb565() {
        if (!running) {
            return lcdCache;
        }
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
        if (!running) {
            return new int[16];
        }
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
        if (startFailure != null) {
            return new Status(false, "edgetx start failed: " + startFailure);
        }
        String detail =
                running ? (bootComplete() ? "edgetx-tx16s-wasm" : "edgetx booting…") : "radio off";
        return new Status(running, detail);
    }

    @Override
    public void close() {
        stop();
        nativeDestroy(handle);
    }

    private native long nativeCreate(String wasmPath, String sdPath, String settingsPath);

    private native void nativeDestroy(long handle);

    private native boolean nativeStart(long handle, int utcOffsetSec);

    private native void nativeStop(long handle);

    private native void nativePump(long handle);

    private native boolean nativeBootComplete(long handle);

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

    private native void nativeBindAudioSink(long handle, AudioTrackEdgeTxAudioAdapter sink);
}
