package com.flyingcomputer.gndfull.edgetx;

/** Selects fake vs native EdgeTX adapter implementations. */
public final class EdgeTxAdapterFactory {
    public enum Kind {
        FAKE,
        NATIVE_STUB
    }

    private EdgeTxAdapterFactory() {}

    public static EdgeTxAdapter create(Kind kind) {
        return switch (kind) {
            case FAKE -> new FakeEdgeTxAdapter();
            case NATIVE_STUB -> new NativeEdgeTxAdapter();
        };
    }
}
