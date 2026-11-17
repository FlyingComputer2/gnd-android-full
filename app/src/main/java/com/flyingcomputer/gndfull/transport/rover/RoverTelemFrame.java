package com.flyingcomputer.gndfull.transport.rover;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * rover_sample telemetry payload (func 0x4220): 58 bytes; older rovers send 42 (no link counters)
 * or 39 (no vbus, rssi either).
 */
public final class RoverTelemFrame {
    public static final int FUNCTION = 0x4220;
    public static final int PAYLOAD_BYTES = 58;
    public static final int PAYLOAD_BYTES_V2 = 42;
    public static final int PAYLOAD_BYTES_V1 = 39;

    public final int seq;
    public final int rcAgeMs;
    public final int state;
    public final int mode;
    public final int steerOut;
    public final int thrOut;
    public final int rcLossPerMille;
    public final int rcLatMs;
    /** 0.01 deg, 0..35999. */
    public final int headingCentiDeg;
    /** 0.01 deg. */
    public final int rollCentiDeg;
    /** 0.01 deg. */
    public final int pitchCentiDeg;
    /** Motor bridge current, 10 mA. */
    public final int iMotorCentiAmps;
    /** VBUS, 10 mV; 0 = no sense. */
    public final int vbusCentiVolts;
    /** dBm of the ground's frames at the rover radio; 0 = unknown. */
    public final int rssiDbm;
    /**
     * Ground → rover air link as the rover's L3 receives it (free-running u32, summed over all
     * upstreams), sampled at {@link #linkMs} rover monotonic ms. All 0 = unknown (older rover).
     */
    public final long linkMs;
    public final long linkRxBytes;
    public final long linkRxPackets;
    public final long linkRxLost;

    public RoverTelemFrame(
            int seq,
            int rcAgeMs,
            int state,
            int mode,
            int steerOut,
            int thrOut,
            int rcLossPerMille,
            int rcLatMs) {
        this(seq, rcAgeMs, state, mode, steerOut, thrOut, rcLossPerMille, rcLatMs, 0, 0, 0, 0, 0, 0);
    }

    public RoverTelemFrame(
            int seq,
            int rcAgeMs,
            int state,
            int mode,
            int steerOut,
            int thrOut,
            int rcLossPerMille,
            int rcLatMs,
            int headingCentiDeg,
            int rollCentiDeg,
            int pitchCentiDeg,
            int iMotorCentiAmps,
            int vbusCentiVolts,
            int rssiDbm) {
        this(seq, rcAgeMs, state, mode, steerOut, thrOut, rcLossPerMille, rcLatMs, headingCentiDeg,
                rollCentiDeg, pitchCentiDeg, iMotorCentiAmps, vbusCentiVolts, rssiDbm, 0, 0, 0, 0);
    }

    public RoverTelemFrame(
            int seq,
            int rcAgeMs,
            int state,
            int mode,
            int steerOut,
            int thrOut,
            int rcLossPerMille,
            int rcLatMs,
            int headingCentiDeg,
            int rollCentiDeg,
            int pitchCentiDeg,
            int iMotorCentiAmps,
            int vbusCentiVolts,
            int rssiDbm,
            long linkMs,
            long linkRxBytes,
            long linkRxPackets,
            long linkRxLost) {
        this.seq = seq;
        this.rcAgeMs = rcAgeMs;
        this.state = state;
        this.mode = mode;
        this.steerOut = steerOut;
        this.thrOut = thrOut;
        this.rcLossPerMille = rcLossPerMille;
        this.rcLatMs = rcLatMs;
        this.headingCentiDeg = headingCentiDeg;
        this.rollCentiDeg = rollCentiDeg;
        this.pitchCentiDeg = pitchCentiDeg;
        this.iMotorCentiAmps = iMotorCentiAmps;
        this.vbusCentiVolts = vbusCentiVolts;
        this.rssiDbm = rssiDbm;
        this.linkMs = linkMs;
        this.linkRxBytes = linkRxBytes;
        this.linkRxPackets = linkRxPackets;
        this.linkRxLost = linkRxLost;
    }

    public static RoverTelemFrame unpack(byte[] payload) {
        if (payload == null
                || (payload.length != PAYLOAD_BYTES
                        && payload.length != PAYLOAD_BYTES_V2
                        && payload.length != PAYLOAD_BYTES_V1)) {
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
        int heading = b.getShort() & 0xFFFF;
        int roll = b.getShort();
        int pitch = b.getShort();
        b.getShort(); // ax
        b.getShort(); // ay
        b.getShort(); // v_est
        int iMotor = b.getShort() & 0xFFFF;
        int rcLoss = b.getShort() & 0xFFFF;
        int rcLatMs = b.getShort() & 0xFFFF;
        b.get(); // flags
        int vbus = 0;
        int rssi = 0;
        if (payload.length >= PAYLOAD_BYTES_V2) {
            vbus = b.getShort() & 0xFFFF;
            rssi = b.get();
        }
        long[] link = new long[4];
        if (payload.length == PAYLOAD_BYTES) {
            for (int i = 0; i < link.length; i++) {
                link[i] = b.getInt() & 0xFFFF_FFFFL; // link_ms, rx bytes, rx packets, rx lost
            }
        }
        return new RoverTelemFrame(
                seq, rcAgeMs, state, mode, steerOut, thrOut, rcLoss, rcLatMs, heading, roll, pitch,
                iMotor, vbus, rssi, link[0], link[1], link[2], link[3]);
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
