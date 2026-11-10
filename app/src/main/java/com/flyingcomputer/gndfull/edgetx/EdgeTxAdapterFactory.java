package com.flyingcomputer.gndfull.edgetx;

import android.content.Context;

/** Selects EdgeTX backend: WASM simulator (TX16S) or offline fake. */
public final class EdgeTxAdapterFactory {
    public enum Kind {
        WASM,
        FAKE
    }

    private EdgeTxAdapterFactory() {}

    public static EdgeTxAdapter create(Context context, Kind kind) {
        if (kind == Kind.WASM) {
            return new WasmEdgeTxAdapter(context);
        }
        return new FakeEdgeTxAdapter();
    }

    /** Prefer WASM; fall back to fake when wasm asset/build is missing. */
    public static EdgeTxAdapter createAuto(Context context) {
        try {
            return create(context, Kind.WASM);
        } catch (Exception e) {
            android.util.Log.e(
                    "EdgeTxAdapterFactory",
                    "WASM unavailable, using fake: " + e.getMessage(),
                    e);
            return create(context, Kind.FAKE);
        }
    }

    public static String describe(EdgeTxAdapter adapter) {
        return adapter instanceof WasmEdgeTxAdapter ? "wasm" : "fake";
    }
}
