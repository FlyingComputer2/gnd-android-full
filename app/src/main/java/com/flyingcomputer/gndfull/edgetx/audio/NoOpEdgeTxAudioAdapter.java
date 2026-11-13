package com.flyingcomputer.gndfull.edgetx.audio;

/** Discards audio (fake adapter / headless tests). */
public final class NoOpEdgeTxAudioAdapter implements EdgeTxAudioAdapter {
    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public void queuePcm16Le(byte[] interleavedMono, int byteLength) {}
}
