package com.flyingcomputer.gndfull.crsf;

import com.flyingcomputer.gndfull.transport.rover.RoverTelemFrame;

/** Builds minimal CRSF telemetry for EdgeTX {@code simuSendTelemetry} injection. */
public final class RoverCrossfireTelemetry {
  public static final int PROTOCOL_CROSSFIRE = 2;
  /** External module index in the simulator. */
  public static final int MODULE_EXTERNAL = 1;

  private static final byte SYNC = (byte) 0xC8;
  private static final byte LINK_STATISTICS = 0x14;

  private RoverCrossfireTelemetry() {}

  /** Link statistics derived from rover_sample telemetry (bench-appropriate, not RF-accurate). */
  public static byte[] linkStatistics(RoverTelemFrame telem) {
    if (telem == null) {
      return null;
    }
    int rqly = Math.max(0, Math.min(100, 100 - telem.rcLossPerMille / 10));
    int rssi = Math.max(0, Math.min(130, 130 - telem.rcLossPerMille / 8));
    byte[] packet = new byte[16];
    packet[0] = SYNC;
    packet[1] = 13;
    packet[2] = LINK_STATISTICS;
    packet[3] = (byte) rssi;
    packet[4] = (byte) rssi;
    packet[5] = (byte) rqly;
    packet[6] = 0;
    packet[7] = 0;
    packet[8] = 0;
    packet[9] = 0;
    packet[10] = (byte) rssi;
    packet[11] = (byte) rqly;
    packet[12] = 0;
    return packet;
  }
}
