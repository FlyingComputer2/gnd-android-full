package com.flyingcomputer.gndfull.edgetx.audio;

/** Host audio output for EdgeTX simulator waveforms ({@code simuQueueAudio}). */
public interface EdgeTxAudioAdapter {
    /** EdgeTX {@code AUDIO_SAMPLE_RATE} (32 kHz, mono PCM16LE). */
    int SAMPLE_RATE_HZ = 32000;

    void start();

    void stop();

    void queuePcm16Le(byte[] interleavedMono, int byteLength);
}
