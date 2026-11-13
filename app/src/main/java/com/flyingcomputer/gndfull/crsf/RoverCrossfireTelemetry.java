package com.flyingcomputer.gndfull.crsf;

import com.flyingcomputer.gndfull.transport.rover.RoverTelemFrame;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * CRSF telemetry frames built from rover_sample telemetry, fed to EdgeTX as if from the external
 * CRSF module: link statistics, battery, attitude and flight mode.
 */
public final class RoverCrossfireTelemetry {
    public static final byte SYNC = (byte) 0xC8;
    public static final byte BATTERY = 0x08;
    public static final byte LINK_STATISTICS = 0x14;
    public static final byte ATTITUDE = 0x1E;
    public static final byte FLIGHT_MODE = 0x21;

    private RoverCrossfireTelemetry() {}

    /** All frames for one rover telemetry message, concatenated. */
    public static byte[] frames(RoverTelemFrame t) {
        if (t == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(64);
        write(out, linkStatistics(t));
        if (t.vbusCentiVolts > 0) {
            write(out, battery(t));
        }
        write(out, attitude(t));
        write(out, flightMode(t));
        return out.toByteArray();
    }

    /**
     * Uplink RSSI = the ground's frames at the rover C6 (dBm, signed byte as ELRS sends it; 0 when
     * the rover has none). Uplink and downlink LQ come from the rover's RC loss over the last
     * second. SNR, antenna, RF mode and power are 0.
     */
    public static byte[] linkStatistics(RoverTelemFrame t) {
        int lossPerMille = Math.max(0, Math.min(1000, t.rcLossPerMille));
        int lq = (1000 - lossPerMille + 5) / 10;
        byte[] p = new byte[10];
        p[0] = (byte) t.rssiDbm; // uplink RSSI ant 1
        p[1] = (byte) t.rssiDbm; // uplink RSSI ant 2
        p[2] = (byte) lq; // uplink LQ
        p[7] = (byte) lq; // downlink LQ
        return frame(LINK_STATISTICS, p);
    }

    /** Voltage 0.1 V, current 0.1 A (motor bridge), capacity and remaining unknown (0). */
    public static byte[] battery(RoverTelemFrame t) {
        int dv = Math.round(t.vbusCentiVolts / 10f);
        int da = Math.round(t.iMotorCentiAmps / 10f);
        byte[] p = new byte[8];
        p[0] = (byte) (dv >> 8);
        p[1] = (byte) dv;
        p[2] = (byte) (da >> 8);
        p[3] = (byte) da;
        return frame(BATTERY, p);
    }

    /** Pitch, roll, yaw in rad * 10000, big-endian (yaw from heading, -pi..pi). */
    public static byte[] attitude(RoverTelemFrame t) {
        double yawDeg = t.headingCentiDeg / 100.0;
        if (yawDeg > 180) {
            yawDeg -= 360;
        }
        int pitch = rad10k(t.pitchCentiDeg / 100.0);
        int roll = rad10k(t.rollCentiDeg / 100.0);
        int yaw = rad10k(yawDeg);
        byte[] p = {
            (byte) (pitch >> 8), (byte) pitch,
            (byte) (roll >> 8), (byte) roll,
            (byte) (yaw >> 8), (byte) yaw,
        };
        return frame(ATTITUDE, p);
    }

    /** "MAN" / "RATE" / "HOLD" by rover mode, "*" appended while disarmed; "FS" / "FAULT". */
    public static byte[] flightMode(RoverTelemFrame t) {
        String text = switch (t.state) {
            case 2 -> "FS";
            case 3 -> "FAULT";
            default -> {
                String m = switch (t.mode) {
                    case 0 -> "MAN";
                    case 1 -> "RATE";
                    case 2 -> "HOLD";
                    default -> "M" + t.mode;
                };
                yield t.state == 1 ? m : m + "*";
            }
        };
        byte[] s = text.getBytes(StandardCharsets.US_ASCII);
        byte[] p = new byte[s.length + 1]; // NUL-terminated
        System.arraycopy(s, 0, p, 0, s.length);
        return frame(FLIGHT_MODE, p);
    }

    static byte[] frame(byte type, byte[] payload) {
        byte[] f = new byte[payload.length + 4];
        f[0] = SYNC;
        f[1] = (byte) (payload.length + 2); // type + payload + crc
        f[2] = type;
        System.arraycopy(payload, 0, f, 3, payload.length);
        f[f.length - 1] = (byte) CrsfCrc8.compute(f, 2, payload.length + 1);
        return f;
    }

    private static void write(ByteArrayOutputStream out, byte[] b) {
        out.write(b, 0, b.length);
    }

    private static int rad10k(double deg) {
        return (int) Math.round(Math.toRadians(deg) * 10000.0);
    }
}
