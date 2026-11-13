package com.flyingcomputer.gndfull.transport.rover;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * rover_sample RC payload inside MSP func 0x4201: 17 bytes, aux = rover CH2 servo, aux2 = CH3.
 * Rovers before aux2 accept only the 15-byte frame, so update the rover first.
 */
public final class RoverRcFrame {
    public static final int FUNCTION = 0x4201;
    public static final int PAYLOAD_BYTES = 17;
    public static final int SRC_PHONE = 1;
    public static final int FLAG_ARM = 0x01;
    public static final int MODE_NONE = 0xFF;

    public final int seq;
    public final long timeUsLow32;
    public final int src;
    public final int flags;
    public final int mode;
    public final int steer;
    public final int thr;
    public final int aux;
    public final int aux2;

    public RoverRcFrame(
            int seq,
            long timeUsLow32,
            int src,
            int flags,
            int mode,
            int steer,
            int thr,
            int aux) {
        this(seq, timeUsLow32, src, flags, mode, steer, thr, aux, 0);
    }

    public RoverRcFrame(
            int seq,
            long timeUsLow32,
            int src,
            int flags,
            int mode,
            int steer,
            int thr,
            int aux,
            int aux2) {
        this.seq = seq & 0xFFFF;
        this.timeUsLow32 = timeUsLow32 & 0xFFFFFFFFL;
        this.src = src & 0xFF;
        this.flags = flags & 0xFF;
        this.mode = mode & 0xFF;
        this.steer = clamp(steer);
        this.thr = clamp(thr);
        this.aux = clamp(aux);
        this.aux2 = clamp(aux2);
    }

    public byte[] pack() {
        return ByteBuffer.allocate(PAYLOAD_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) seq)
                .putInt((int) timeUsLow32)
                .put((byte) src)
                .put((byte) flags)
                .put((byte) mode)
                .putShort((short) steer)
                .putShort((short) thr)
                .putShort((short) aux)
                .putShort((short) aux2)
                .array();
    }

    private static int clamp(int v) {
        return Math.max(-1000, Math.min(1000, v));
    }
}
