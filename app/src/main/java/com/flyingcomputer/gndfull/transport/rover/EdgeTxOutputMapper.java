package com.flyingcomputer.gndfull.transport.rover;

import com.flyingcomputer.gndfull.control.DrivingState;
import com.flyingcomputer.gndfull.crsf.CrsfChannelsFrame;

/** Maps EdgeTX mixed outputs to CRSF channels and rover_sample RC fields. */
public final class EdgeTxOutputMapper {
    public static final int DEFAULT_STEER_CHANNEL = 0;
    public static final int DEFAULT_THR_CHANNEL = 2;
    public static final int ARM_SWITCH_INDEX = 3; // SA on lower row
    /** CRSF channel 5 (1-based), explicit arming in CH5 arming mode. */
    public static final int ARM_CHANNEL_INDEX = 4;
    public static final int ARM_PWM_DISARMED = 1000;
    public static final int ARM_PWM_ARMED = 2000;

    private final int steerChannel;
    private final int thrChannel;

    public EdgeTxOutputMapper() {
        this(DEFAULT_STEER_CHANNEL, DEFAULT_THR_CHANNEL);
    }

    public EdgeTxOutputMapper(int steerChannel, int thrChannel) {
        this.steerChannel = steerChannel;
        this.thrChannel = thrChannel;
    }

    public byte[] buildCrsfFrame(
            int[] outputs,
            int[] switches,
            DrivingState driving,
            boolean sendArmFlag) {
        int[] channels = preparedPwmChannels(outputs, switches, driving, sendArmFlag);
        return CrsfChannelsFrame.fromPulses(CrsfChannelsFrame.pulsesFromPwm(channels));
    }

    public RoverRcFrame map(
            int seq,
            long timeUsLow32,
            int[] outputs,
            int[] switches,
            DrivingState driving,
            boolean sendArmFlag) {
        int[] channels = preparedPwmChannels(outputs, switches, driving, sendArmFlag);
        int steer = pwmToPerMille(channel(channels, steerChannel));
        int thr = pwmToPerMille(channel(channels, thrChannel));
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

    /** Applies CH5 arm level; rover safety still requires neutral throttle and link timing. */
    int[] preparedPwmChannels(
            int[] outputs,
            int[] switches,
            DrivingState driving,
            boolean sendArmFlag) {
        int[] channels = new int[CrsfChannelsFrame.CHANNEL_COUNT];
        if (outputs != null) {
            System.arraycopy(
                    outputs,
                    0,
                    channels,
                    0,
                    Math.min(channels.length, outputs.length));
        }
        boolean saUp = switches.length > ARM_SWITCH_INDEX && switches[ARM_SWITCH_INDEX] > 0;
        boolean armHigh =
                saUp && sendArmFlag && driving == DrivingState.ARMED;
        channels[ARM_CHANNEL_INDEX] = armHigh ? ARM_PWM_ARMED : ARM_PWM_DISARMED;
        if (!saUp) {
            channels[steerChannel] = 1500;
            channels[thrChannel] = 1500;
        }
        return channels;
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
