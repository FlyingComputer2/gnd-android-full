package com.flyingcomputer.gndfull.edgetx;

/** Versioned seam for the EdgeTX simulator core (real or fake). */
public interface EdgeTxAdapter extends AutoCloseable {
    record LcdMetadata(int width, int height, int strideBytes, int pixelFormatRgb565) {}

    record Status(boolean running, String detail) {}

    void start() throws Exception;

    void stop();

    /** Full stick and switch snapshot; values are adapter-defined units. */
    void submitInput(EdgeTxInputSnapshot input);

    void keyDown(EdgeTxKey key);

    void keyUp(EdgeTxKey key);

    LcdMetadata lcdMetadata();

    /** Owned rgb565 little-endian buffer; length width*height*2. */
    byte[] copyLcdFrameRgb565();

    int[] copyMixedOutputs();

    Status status();

    @Override
    void close();
}
