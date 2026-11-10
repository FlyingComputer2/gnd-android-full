package com.flyingcomputer.gndfull.transport.rover;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** rover_sample telemetry payload (func 0x4220). */
public final class RoverTelemFrame {
    public static final int FUNCTION = 0x4220;
    public static final int PAYLOAD_BYTES = 39;

    public final int seq;
    public final int rcAgeMs;
    public final int state;
    public final int mode;
    public final int steerOut;
    public final int thrOut;
    public final int rcLossPerMille;
    public final int rcLatMs;

    public RoverTelemFrame(
            int seq,
            int rcAgeMs,
            int state,
            int mode,
            int steerOut,
            int thrOut,
            int rcLossPerMille,
            int rcLatMs) {
        this.seq = seq;
        this.rcAgeMs = rcAgeMs;
        this.state = state;
        this.mode = mode;
        this.steerOut = steerOut;
        this.thrOut = thrOut;
        this.rcLossPerMille = rcLossPerMille;
        this.rcLatMs = rcLatMs;
    }

    public static RoverTelemFrame unpack(byte[] payload) {
        if (payload == null || payload.length != PAYLOAD_BYTES) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int seq = b.getShort() & 0xFFFF;
        b.getInt(); // t_us
        b.getShort(); // rc_seq
        int rcAgeMs = b.getShort() & 0xFFFF;
        int state = b.get() & 0xFF;
        int mode = b.get() & 0xFF;
        b.getShort(); // r_cmd
        b.getShort(); // r_meas
        int steerOut = b.getShort();
        int thrOut = b.getShort();
        b.getShort(); // heading
        b.getShort(); // roll
        b.getShort(); // pitch
        b.getShort(); // ax
        b.getShort(); // ay
        b.getShort(); // v_est
        b.getShort(); // i_motor
        int rcLoss = b.getShort() & 0xFFFF;
        int rcLatMs = b.getShort() & 0xFFFF;
        return new RoverTelemFrame(seq, rcAgeMs, state, mode, steerOut, thrOut, rcLoss, rcLatMs);
    }

    public String stateLabel() {
        return switch (state) {
            case 0 -> "disarmed";
            case 1 -> "armed";
            case 2 -> "failsafe";
            case 3 -> "fault";
            default -> "state=" + state;
        };
    }
}
