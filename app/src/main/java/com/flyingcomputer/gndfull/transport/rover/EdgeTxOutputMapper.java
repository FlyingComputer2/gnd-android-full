package com.flyingcomputer.gndfull.transport.rover;

import com.flyingcomputer.gndfull.control.DrivingState;

/** Maps EdgeTX mixed outputs to rover_sample RC fields. */
public final class EdgeTxOutputMapper {
    public static final int DEFAULT_STEER_CHANNEL = 0;
    public static final int DEFAULT_THR_CHANNEL = 2;
    public static final int ARM_SWITCH_INDEX = 3; // SA on lower row
    /** EdgeTX CH6 / CH7 (0-based 5 / 6) drive the rover's CH2 / CH3 servos (aux / aux2). */
    public static final int DEFAULT_AUX_CHANNEL = 5;
    public static final int DEFAULT_AUX2_CHANNEL = 6;

    private final int steerChannel;
    private final int thrChannel;
    private final int auxChannel;
    private final int aux2Channel;

    public EdgeTxOutputMapper() {
        this(DEFAULT_STEER_CHANNEL, DEFAULT_THR_CHANNEL, DEFAULT_AUX_CHANNEL, DEFAULT_AUX2_CHANNEL);
    }

    public EdgeTxOutputMapper(int steerChannel, int thrChannel, int auxChannel, int aux2Channel) {
        this.steerChannel = steerChannel;
        this.thrChannel = thrChannel;
        this.auxChannel = auxChannel;
        this.aux2Channel = aux2Channel;
    }

    public RoverRcFrame map(
            int seq,
            long timeUsLow32,
            int[] outputs,
            int[] switches,
            DrivingState driving,
            boolean sendArmFlag) {
        int steer = pwmToPerMille(channel(outputs, steerChannel));
        int thr = pwmToPerMille(channel(outputs, thrChannel));
        // Aux servos follow their channels whatever SA says; the rover holds them at
        // ch2_failsafe / ch3_failsafe when RC stops.
        int aux = pwmToPerMille(channel(outputs, auxChannel));
        int aux2 = pwmToPerMille(channel(outputs, aux2Channel));
        int flags = 0;
        if (sendArmFlag && driving == DrivingState.ARMED) {
            flags |= RoverRcFrame.FLAG_ARM;
        }
        boolean saUp = switches.length > ARM_SWITCH_INDEX && switches[ARM_SWITCH_INDEX] > 0;
        if (!saUp) {
            thr = 0;
            steer = 0;
            flags = 0;
        }
        return new RoverRcFrame(
                seq,
                timeUsLow32,
                RoverRcFrame.SRC_PHONE,
                flags,
                RoverRcFrame.MODE_NONE,
                steer,
                thr,
                aux,
                aux2);
    }

    public boolean armIntent(int[] switches) {
        return switches.length > ARM_SWITCH_INDEX && switches[ARM_SWITCH_INDEX] > 0;
    }

    public boolean throttleNeutral(int[] outputs) {
        return Math.abs(pwmToPerMille(channel(outputs, thrChannel))) <= 50;
    }

    private static int channel(int[] outputs, int index) {
        if (outputs == null || index < 0 || index >= outputs.length) {
            return 1500;
        }
        return outputs[index];
    }

    public static int pwmToPerMille(int pwmMicroseconds) {
        return Math.max(-1000, Math.min(1000, (pwmMicroseconds - 1500) * 1000 / 500));
    }
}
