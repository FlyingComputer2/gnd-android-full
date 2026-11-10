package com.flyingcomputer.gndfull.transport.rover;

import com.flyingcomputer.gndfull.control.DrivingState;

/** Maps EdgeTX mixed outputs to rover_sample RC fields. */
public final class EdgeTxOutputMapper {
    public static final int DEFAULT_STEER_CHANNEL = 0;
    public static final int DEFAULT_THR_CHANNEL = 2;
    public static final int ARM_SWITCH_INDEX = 3; // SA on lower row

    private final int steerChannel;
    private final int thrChannel;

    public EdgeTxOutputMapper() {
        this(DEFAULT_STEER_CHANNEL, DEFAULT_THR_CHANNEL);
    }

    public EdgeTxOutputMapper(int steerChannel, int thrChannel) {
        this.steerChannel = steerChannel;
        this.thrChannel = thrChannel;
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
                0);
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
