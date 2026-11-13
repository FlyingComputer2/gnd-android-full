package com.flyingcomputer.gndfull.transport.rover;

import com.flyingcomputer.gndfull.crsf.CrsfRcChannels;

/**
 * Maps the CRSF channels EdgeTX sends to its external module to rover_sample RC fields.
 *
 * <pre>
 * CH1, CH2  unused
 * CH3       throttle
 * CH4       servo 1 (rover CH1): steering
 * CH5       arm (EdgeTX CRSF arming mode CH5: >= 1750 us, see CrsfRcChannels.CH5_ARM_US;
 *           Switch mode: status byte)
 * CH6       mode: <= 1250 us manual steering (rover mode 0), >= 1750 us rate steering (mode 1),
 *           in between (unmixed = 1500 us) the rover's own `mode` parameter
 * CH7       servo 2 (rover CH2, aux)
 * CH8       servo 3 (rover CH3, aux2)
 * </pre>
 */
public final class EdgeTxOutputMapper {
    public static final int CH_THROTTLE = 3;
    public static final int CH_STEER = 4;
    public static final int CH_ARM = 5;
    public static final int CH_MODE = 6;
    public static final int CH_SERVO2 = 7;
    public static final int CH_SERVO3 = 8;

    /** rover_sample steering modes (param `mode`). */
    public static final int MODE_MANUAL = 0;
    public static final int MODE_RATE = 1;

    private EdgeTxOutputMapper() {}

    /**
     * @param allowArm false while the app blocks driving (menu open, radio off, USB failsafe):
     *     the arm flag is never sent and throttle is 0.
     */
    public static RoverRcFrame map(int seq, long timeUsLow32, CrsfRcChannels ch, boolean allowArm) {
        int flags = allowArm && ch.armed() ? RoverRcFrame.FLAG_ARM : 0;
        int ch6 = ch.channel(CH_MODE);
        int mode = ch6 <= 1250 ? MODE_MANUAL : ch6 >= 1750 ? MODE_RATE : RoverRcFrame.MODE_NONE;
        return new RoverRcFrame(
                seq,
                timeUsLow32,
                RoverRcFrame.SRC_PHONE,
                flags,
                mode,
                pwmToPerMille(ch.channel(CH_STEER)),
                allowArm ? pwmToPerMille(ch.channel(CH_THROTTLE)) : 0,
                pwmToPerMille(ch.channel(CH_SERVO2)),
                pwmToPerMille(ch.channel(CH_SERVO3)));
    }

    /** EdgeTX +-100 % = 1500 +- 512 us = +-1000. */
    public static int pwmToPerMille(int pwmMicroseconds) {
        return Math.max(-1000, Math.min(1000, Math.round((pwmMicroseconds - 1500) * 1000f / 512f)));
    }
}
