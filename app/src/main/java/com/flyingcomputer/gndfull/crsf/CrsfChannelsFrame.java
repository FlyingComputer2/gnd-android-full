package com.flyingcomputer.gndfull.crsf;

/** Standard 16-channel CRSF RC frame ({@code CHANNELS_ID 0x16}), CH5 arming mode status byte. */
public final class CrsfChannelsFrame {
    public static final int CHANNEL_COUNT = 16;
    public static final int CHANNEL_BITS = 11;
    public static final int CENTER = 0x3E0;
    public static final int FRAME_BYTES = 27;
    public static final byte MODULE_ADDRESS = (byte) 0xEE;
    public static final byte CHANNELS_ID = 0x16;
    /** EdgeTX CRSF arming mode flag: channel 5 (not switch). */
    public static final byte STATUS_CH5_ARMING = 0x02;

    private CrsfChannelsFrame() {}

    public static byte[] fromPulses(int[] pulses) {
        return build(pulses, STATUS_CH5_ARMING);
    }

    /** Pulses are EdgeTX mixer units in [-1024, +1024]. */
    public static byte[] build(int[] pulses, byte statusByte) {
        byte[] frame = new byte[FRAME_BYTES];
        frame[0] = MODULE_ADDRESS;
        frame[1] = 25; // type + 22 packed channels + status + CRC
        frame[2] = CHANNELS_ID;
        int bitBuffer = 0;
        int bitsAvailable = 0;
        int out = 3;
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            int pulse = i < pulses.length ? pulses[i] : 0;
            int val =
                    clamp(
                            0,
                            CENTER + (pulse * 4) / 5,
                            2 * CENTER);
            bitBuffer |= (val & 0x7FF) << bitsAvailable;
            bitsAvailable += CHANNEL_BITS;
            while (bitsAvailable >= 8) {
                frame[out++] = (byte) (bitBuffer & 0xFF);
                bitBuffer >>>= 8;
                bitsAvailable -= 8;
            }
        }
        frame[out++] = statusByte;
        frame[out] = (byte) CrsfCrc8.compute(frame, 2, 24);
        return frame;
    }

    public static int pwmToPulse(int pwmMicroseconds) {
        return Math.max(-1024, Math.min(1024, (pwmMicroseconds - 1500) * 1024 / 500));
    }

    public static int[] pulsesFromPwm(int[] pwmMicroseconds) {
        int[] pulses = new int[CHANNEL_COUNT];
        int n = Math.min(CHANNEL_COUNT, pwmMicroseconds == null ? 0 : pwmMicroseconds.length);
        for (int i = 0; i < n; i++) {
            pulses[i] = pwmToPulse(pwmMicroseconds[i]);
        }
        return pulses;
    }

    private static int clamp(int lo, int value, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
