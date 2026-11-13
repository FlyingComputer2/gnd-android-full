package com.flyingcomputer.gndfull.edgetx.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

/** Streams EdgeTX simulator PCM to the device speaker via {@link AudioTrack}. */
public final class AudioTrackEdgeTxAudioAdapter implements EdgeTxAudioAdapter {
    private static final String TAG = "EdgeTxAudio";

    private final Object lock = new Object();
    private AudioTrack track;
    private int chunksDelivered;
    private boolean foreground = true;

    @Override
    public void start() {
        synchronized (lock) {
            stopLocked();
            int min =
                    AudioTrack.getMinBufferSize(
                            SAMPLE_RATE_HZ,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) {
                Log.w(TAG, "AudioTrack min buffer size invalid: " + min);
                return;
            }
            AudioAttributes attrs =
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
            AudioFormat format =
                    new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE_HZ)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build();
            int bufferSize = Math.max(min, SAMPLE_RATE_HZ / 10 * 2);
            track =
                    new AudioTrack.Builder()
                            .setAudioAttributes(attrs)
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(bufferSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                            .build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack init failed");
                track.release();
                track = null;
                return;
            }
            if (foreground) {
                track.play();
            }
        }
    }

    @Override
    public void setForeground(boolean foreground) {
        synchronized (lock) {
            this.foreground = foreground;
            if (track == null) {
                return;
            }
            try {
                if (foreground) {
                    track.play();
                } else {
                    track.pause();
                }
            } catch (IllegalStateException ignored) {
                // Track already released.
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            stopLocked();
        }
    }

    private void stopLocked() {
        if (track == null) {
            return;
        }
        try {
            track.stop();
        } catch (IllegalStateException ignored) {
            // Already halted.
        }
        track.release();
        track = null;
    }

    @Override
    public void queuePcm16Le(byte[] interleavedMono, int byteLength) {
        if (interleavedMono == null || byteLength <= 0) {
            return;
        }
        final int len = Math.min(byteLength, interleavedMono.length);
        synchronized (lock) {
            if (track == null || !foreground) {
                return;
            }
            int written = 0;
            while (written < len) {
                int n = track.write(interleavedMono, written, len - written, AudioTrack.WRITE_BLOCKING);
                if (n <= 0) {
                    Log.w(TAG, "AudioTrack write stalled: " + n);
                    break;
                }
                written += n;
            }
        }
    }

    /** Called from JNI on the wasm worker thread. */
    @SuppressWarnings("unused")
    public void deliverNativeChunk(byte[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return;
        }
        queuePcm16Le(pcm, pcm.length);
        int n = ++chunksDelivered;
        if (n == 1 || n % 200 == 0) {
            Log.d(TAG, "PCM chunks delivered: " + n + " (last " + pcm.length + " bytes)");
        }
    }
}
