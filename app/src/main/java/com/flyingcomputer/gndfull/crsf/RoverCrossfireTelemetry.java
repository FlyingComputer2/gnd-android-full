package com.flyingcomputer.gndfull.crsf;

import com.flyingcomputer.gndfull.transport.rover.RoverTelemFrame;

/** Builds CRSF telemetry frames for EdgeTX {@code simuSendTelemetry} injection. */
public final class RoverCrossfireTelemetry {
  /** {@code simuSendTelemetry} protocol id for Crossfire (EdgeTX simulib.cpp). */
  public static final int PROTOCOL_CROSSFIRE = 2;
  /** External module index in the simulator. */
  public static final int MODULE_EXTERNAL = 1;

  public static final byte SYNC = (byte) 0xC8;
  public static final byte LINK_STATISTICS = 0x14;
  /** Link statistics payload: 10 one-byte fields. */
  public static final int LINK_STATISTICS_PAYLOAD = 10;
  /** sync + len + type + payload + crc. */
  public static final int LINK_STATISTICS_BYTES = 3 + LINK_STATISTICS_PAYLOAD + 1;

  private RoverCrossfireTelemetry() {}

  /**
   * Link statistics from rover_sample telemetry. Only link quality is real: uplink and downlink LQ
   * both come from the rover's RC loss over the last second. The rover reports no RSSI or SNR, so
   * those fields are 0 rather than made up; EdgeTX's RSSI alarm uses LQ for CRSF.
   */
  public static byte[] linkStatistics(RoverTelemFrame telem) {
    if (telem == null) {
      return null;
    }
    int lossPerMille = Math.max(0, Math.min(1000, telem.rcLossPerMille));
    int lq = (1000 - lossPerMille + 5) / 10; // percent, rounded
    byte[] f = new byte[LINK_STATISTICS_BYTES];
    f[0] = SYNC;
    f[1] = (byte) (LINK_STATISTICS_PAYLOAD + 2); // type + payload + crc
    f[2] = LINK_STATISTICS;
    // f[3..4] uplink RSSI ant 1/2, f[6] uplink SNR, f[7] antenna, f[8] RF mode,
    // f[9] TX power index, f[10] downlink RSSI, f[12] downlink SNR: all 0.
    f[5] = (byte) lq; // uplink LQ
    f[11] = (byte) lq; // downlink LQ
    f[LINK_STATISTICS_BYTES - 1] =
        (byte) CrsfCrc8.compute(f, 2, LINK_STATISTICS_PAYLOAD + 1);
    return f;
  }
}
