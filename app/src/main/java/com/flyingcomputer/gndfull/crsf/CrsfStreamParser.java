package com.flyingcomputer.gndfull.crsf;

import java.util.Arrays;

/**
 * Splits a CRSF byte stream (what EdgeTX sends to the external module) into frames: address, len,
 * type, payload, CRC8 over type + payload. Bad bytes are skipped one at a time, as EdgeTX does.
 * Keeps the newest RC channels frame.
 */
public final class CrsfStreamParser {
    private static final int MAX_FRAME = 64;
    private final byte[] buf = new byte[1024 + MAX_FRAME];
    private int len;
    private CrsfRcChannels lastRc;
    private long rcFrames;
    private long crcErrors;

    /** Feeds bytes; returns the newest RC channels frame seen in them, or null. */
    public CrsfRcChannels feed(byte[] data, long nowNanos) {
        if (data == null || data.length == 0) {
            return null;
        }
        if (len + data.length > buf.length) {
            len = 0; // fell behind: resync on the new bytes
        }
        int n = Math.min(data.length, buf.length);
        System.arraycopy(data, data.length - n, buf, len, n);
        len += n;
        CrsfRcChannels newest = null;
        int p = 0;
        while (len - p >= 4) {
            int addr = buf[p] & 0xFF;
            int frameLen = buf[p + 1] & 0xFF; // type + payload + crc
            if ((addr != 0xEE && addr != 0xC8 && addr != 0xEA)
                    || frameLen < 2
                    || frameLen > MAX_FRAME - 2) {
                p++;
                continue;
            }
            int total = frameLen + 2;
            if (len - p < total) {
                break;
            }
            int crc = CrsfCrc8.compute(buf, p + 2, frameLen - 1);
            if (crc != (buf[p + total - 1] & 0xFF)) {
                crcErrors++;
                p++;
                continue;
            }
            CrsfRcChannels rc = decodeRc(buf, p, frameLen, nowNanos);
            if (rc != null) {
                newest = rc;
                rcFrames++;
            }
            p += total;
        }
        if (p > 0) {
            System.arraycopy(buf, p, buf, 0, len - p);
            len -= p;
        }
        if (newest != null) {
            lastRc = newest;
        }
        return newest;
    }

    public CrsfRcChannels lastRc() {
        return lastRc;
    }

    public long rcFrames() {
        return rcFrames;
    }

    public long crcErrors() {
        return crcErrors;
    }

    /** RC channels frame at {@code off}: type 0x16, 22 packed bytes, optional EdgeTX status byte. */
    static CrsfRcChannels decodeRc(byte[] b, int off, int frameLen, long nowNanos) {
        if (b[off + 2] != CrsfChannelsFrame.CHANNELS_ID) {
            return null;
        }
        int payload = frameLen - 2;
        if (payload != 22 && payload != 23) {
            return null;
        }
        int[] us = new int[CrsfChannelsFrame.CHANNEL_COUNT];
        int bits = 0;
        int avail = 0;
        int in = off + 3;
        for (int ch = 0; ch < us.length; ch++) {
            while (avail < CrsfChannelsFrame.CHANNEL_BITS) {
                bits |= (b[in++] & 0xFF) << avail;
                avail += 8;
            }
            us[ch] = CrsfRcChannels.toMicros(bits & 0x7FF);
            bits >>>= CrsfChannelsFrame.CHANNEL_BITS;
            avail -= CrsfChannelsFrame.CHANNEL_BITS;
        }
        int status = payload == 23 ? (b[off + 3 + 22] & 0xFF) : -1;
        return new CrsfRcChannels(Arrays.copyOf(us, us.length), status, nowNanos);
    }
}
