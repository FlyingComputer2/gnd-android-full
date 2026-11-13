package com.flyingcomputer.gndfull.crsf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.flyingcomputer.gndfull.transport.rover.RoverTelemFrame;
import org.junit.Test;

public final class CrsfChannelsFrameTest {
    @Test
    public void crc8MatchesCrsfCheckValue() {
        // CRC-8/DVB-S2 (poly 0xD5, init 0) check value over "123456789".
        byte[] check = "123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals(0xBC, CrsfCrc8.compute(check, 0, check.length));
    }

    @Test
    public void neutralFramePacksCenterIntoEveryChannel() {
        byte[] frame = CrsfChannelsFrame.fromPulses(new int[CrsfChannelsFrame.CHANNEL_COUNT]);
        assertEquals(CrsfChannelsFrame.FRAME_BYTES, frame.length);
        assertEquals(CrsfChannelsFrame.MODULE_ADDRESS, frame[0]);
        assertEquals(25, frame[1]);
        assertEquals(CrsfChannelsFrame.CHANNELS_ID, frame[2]);
        for (int ch = 0; ch < CrsfChannelsFrame.CHANNEL_COUNT; ch++) {
            assertEquals(CrsfChannelsFrame.CENTER, unpack(frame, ch));
        }
        assertEquals(CrsfChannelsFrame.STATUS_CH5_ARMING, frame[25]);
        assertEquals(CrsfCrc8.compute(frame, 2, 24), frame[26] & 0xFF);
    }

    @Test
    public void pulsesScaleLikeEdgeTx() {
        int[] pulses = new int[CrsfChannelsFrame.CHANNEL_COUNT];
        pulses[0] = 1024;
        pulses[1] = -1024;
        pulses[4] = 500;
        pulses[15] = 2000; // beyond full scale: clamps
        byte[] frame = CrsfChannelsFrame.fromPulses(pulses);
        // EdgeTX: CROSSFIRE_CENTER + pulse * 4 / 5, limited to [0, 2 * center].
        assertEquals(992 + 1024 * 4 / 5, unpack(frame, 0));
        assertEquals(992 - 1024 * 4 / 5, unpack(frame, 1));
        assertEquals(992 + 500 * 4 / 5, unpack(frame, 4));
        assertEquals(1984, unpack(frame, 15));
    }

    @Test
    public void linkStatisticsIsWellFormed() {
        RoverTelemFrame telem = new RoverTelemFrame(1, 0, 0, 0, 0, 0, 125, 0);
        byte[] f = RoverCrossfireTelemetry.linkStatistics(telem);
        assertEquals(14, f.length);
        assertEquals(RoverCrossfireTelemetry.SYNC, f[0]);
        assertEquals(12, f[1]); // type + 10 payload + crc
        assertEquals(RoverCrossfireTelemetry.LINK_STATISTICS, f[2]);
        assertEquals(0, f[3]); // no RSSI from the rover
        assertEquals(88, f[5]); // uplink LQ: 12.5 % loss -> 87.5 -> 88
        assertEquals(88, f[11]); // downlink LQ
        assertEquals(CrsfCrc8.compute(f, 2, 11), f[13] & 0xFF);
        assertNull(RoverCrossfireTelemetry.linkStatistics(null));
    }

    private static int unpack(byte[] frame, int ch) {
        int bit = ch * CrsfChannelsFrame.CHANNEL_BITS;
        int v = 0;
        for (int i = 0; i < CrsfChannelsFrame.CHANNEL_BITS; i++, bit++) {
            if ((frame[3 + bit / 8] & (1 << (bit % 8))) != 0) {
                v |= 1 << i;
            }
        }
        return v;
    }
}
