package com.flyingcomputer.gndfull.crsf;

/** One decoded CRSF RC channels frame ({@code 0x16}): 16 channels in microseconds + status. */
public final class CrsfRcChannels {
    /** EdgeTX status byte bit 1: arming mode is CH5 (else Switch, armed state in bit 0). */
    public static final int STATUS_ARMING_MODE_CH5 = 0x02;
    public static final int STATUS_SWITCH_ARMED = 0x01;

    public final int[] us;
    /** EdgeTX extra status byte, or -1 when the frame had none (22-byte payload). */
    public final int status;
    public final long receivedNanos;

    public CrsfRcChannels(int[] us, int status, long receivedNanos) {
        this.us = us;
        this.status = status;
        this.receivedNanos = receivedNanos;
    }

    /** Channel n (1-based) in microseconds; 1500 if absent. */
    public int channel(int oneBased) {
        int i = oneBased - 1;
        return i >= 0 && i < us.length ? us[i] : 1500;
    }

    /**
     * Arm request as EdgeTX's CRSF arming mode defines it: CH5 at or above 1500 us in CH5 mode, or
     * the status byte's armed bit in Switch mode. Frames without a status byte use CH5.
     */
    public boolean armed() {
        if (status >= 0 && (status & STATUS_ARMING_MODE_CH5) == 0) {
            return (status & STATUS_SWITCH_ARMED) != 0;
        }
        return channel(5) >= 1500;
    }

    /** CRSF 11-bit value to microseconds (172..1811 -> 988..2012, 992 = 1500). */
    public static int toMicros(int crsf) {
        return 1500 + Math.round((crsf - CrsfChannelsFrame.CENTER) * 5f / 8f);
    }
}
