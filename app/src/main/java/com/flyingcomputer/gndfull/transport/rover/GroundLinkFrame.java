package com.flyingcomputer.gndfull.transport.rover;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The ground P4's own frame on USB channel 2 (MSP v2 0x4230, every 500 ms): the rover → ground air
 * link as the ground's L3 receives it. 17 bytes, little-endian: {@code rssi:i8 link_ms:u32
 * rx_bytes:u32 rx_packets:u32 rx_lost:u32}. Counters are free-running and summed over all
 * upstreams (gnd-esp32p4 README, USB channel 2).
 */
public final class GroundLinkFrame {
    public static final int FUNCTION = 0x4230;
    public static final int PAYLOAD_BYTES = 17;

    /** dBm of the rover's frames at the ground C6; 0 = unknown. */
    public final int rssiDbm;
    /** Ground monotonic ms at the sample (u32). */
    public final long linkMs;
    public final long rxBytes;
    public final long rxPackets;
    public final long rxLost;

    public GroundLinkFrame(int rssiDbm, long linkMs, long rxBytes, long rxPackets, long rxLost) {
        this.rssiDbm = rssiDbm;
        this.linkMs = linkMs;
        this.rxBytes = rxBytes;
        this.rxPackets = rxPackets;
        this.rxLost = rxLost;
    }

    public static GroundLinkFrame unpack(byte[] payload) {
        if (payload == null || payload.length != PAYLOAD_BYTES) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int rssi = b.get();
        long ms = b.getInt() & 0xFFFF_FFFFL;
        long bytes = b.getInt() & 0xFFFF_FFFFL;
        long packets = b.getInt() & 0xFFFF_FFFFL;
        long lost = b.getInt() & 0xFFFF_FFFFL;
        return new GroundLinkFrame(rssi, ms, bytes, packets, lost);
    }
}
