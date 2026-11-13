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

    private static RoverTelemFrame telem(int state, int mode) {
        // loss 12.5 %, heading 270 deg, roll -10 deg, pitch 5 deg, 1.23 A, 11.10 V, -67 dBm
        return new RoverTelemFrame(1, 0, state, mode, 0, 0, 125, 0, 27000, -1000, 500, 123, 1110, -67);
    }

    @Test
    public void linkStatisticsCarriesRoverRssiAndLq() {
        byte[] f = RoverCrossfireTelemetry.linkStatistics(telem(0, 0));
        assertEquals(14, f.length);
        assertEquals(RoverCrossfireTelemetry.SYNC, f[0]);
        assertEquals(12, f[1]); // type + 10 payload + crc
        assertEquals(RoverCrossfireTelemetry.LINK_STATISTICS, f[2]);
        assertEquals(-67, f[3]); // uplink RSSI ant 1, signed dBm
        assertEquals(-67, f[4]);
        assertEquals(88, f[5]); // uplink LQ: 12.5 % loss -> 87.5 -> 88
        assertEquals(88, f[10]); // downlink LQ
        assertEquals(CrsfCrc8.compute(f, 2, 11), f[13] & 0xFF);
    }

    @Test
    public void batteryAndAttitudeUnits() {
        byte[] b = RoverCrossfireTelemetry.battery(telem(0, 0));
        assertEquals(RoverCrossfireTelemetry.BATTERY, b[2]);
        assertEquals(111, ((b[3] & 0xFF) << 8) | (b[4] & 0xFF)); // 11.1 V in 0.1 V
        assertEquals(12, ((b[5] & 0xFF) << 8) | (b[6] & 0xFF)); // 1.23 A -> 1.2 A
        byte[] a = RoverCrossfireTelemetry.attitude(telem(0, 0));
        assertEquals(RoverCrossfireTelemetry.ATTITUDE, a[2]);
        assertEquals(873, (short) (((a[3] & 0xFF) << 8) | (a[4] & 0xFF))); // pitch 5 deg
        assertEquals(-1745, (short) (((a[5] & 0xFF) << 8) | (a[6] & 0xFF))); // roll -10 deg
        assertEquals(-15708, (short) (((a[7] & 0xFF) << 8) | (a[8] & 0xFF))); // 270 -> -90 deg
    }

    @Test
    public void flightModeText() {
        assertEquals("RATE*", text(RoverCrossfireTelemetry.flightMode(telem(0, 1))));
        assertEquals("MAN", text(RoverCrossfireTelemetry.flightMode(telem(1, 0))));
        assertEquals("FS", text(RoverCrossfireTelemetry.flightMode(telem(2, 0))));
        assertNull(RoverCrossfireTelemetry.frames(null));
        // Every frame in the bundle parses with a good CRC.
        byte[] all = RoverCrossfireTelemetry.frames(telem(1, 1));
        int p = 0;
        int n = 0;
        while (p < all.length) {
            int len = all[p + 1] & 0xFF;
            assertEquals(CrsfCrc8.compute(all, p + 2, len - 1), all[p + len + 1] & 0xFF);
            p += len + 2;
            n++;
        }
        assertEquals(4, n);
    }

    private static String text(byte[] f) {
        int end = 3;
        while (f[end] != 0) {
            end++;
        }
        return new String(f, 3, end - 3, java.nio.charset.StandardCharsets.US_ASCII);
    }

    @Test
    public void parserSkipsGarbageAndBadCrc() {
        int[] pulses = new int[CrsfChannelsFrame.CHANNEL_COUNT];
        pulses[2] = 1024;
        byte[] good = CrsfChannelsFrame.fromPulses(pulses);
        byte[] bad = good.clone();
        bad[10] ^= 0x55;
        byte[] stream = new byte[3 + bad.length + good.length];
        stream[0] = 0x12;
        stream[1] = (byte) 0xEE;
        stream[2] = 0x7F;
        System.arraycopy(bad, 0, stream, 3, bad.length);
        System.arraycopy(good, 0, stream, 3 + bad.length, good.length);
        CrsfStreamParser parser = new CrsfStreamParser();
        // Split across two feeds, as 20 ms drains cut frames anywhere.
        assertNull(parser.feed(java.util.Arrays.copyOf(stream, 20), 1));
        CrsfRcChannels rc =
                parser.feed(java.util.Arrays.copyOfRange(stream, 20, stream.length), 2);
        assertEquals(1, parser.rcFrames());
        assertEquals(2012, rc.channel(3), 1);
        assertEquals(1500, rc.channel(4), 1);
        assertEquals(CrsfChannelsFrame.STATUS_CH5_ARMING, rc.status);
    }

    @Test
    public void deviceInfoMatchesEdgeTxOffsets() {
        byte[] f = RoverCrossfireTelemetry.deviceInfo();
        int len = f[1] & 0xFF;
        assertEquals(f.length, len + 2);
        assertEquals(RoverCrossfireTelemetry.DEVICE_INFO, f[2]);
        assertEquals(RoverCrossfireTelemetry.MODULE_ADDRESS, f[4]); // EdgeTX checks rxBuffer[4]
        int nameSize = len - 18; // EdgeTX: rxBuffer[1] - 18, name incl. NUL
        assertEquals(RoverCrossfireTelemetry.MODULE_NAME.length() + 1, nameSize);
        assertEquals(0, f[5 + nameSize - 1]);
        assertEquals(1, f[14 + nameSize]); // major
        assertEquals(CrsfCrc8.compute(f, 2, len - 1), f[len + 1] & 0xFF);
    }

    @Test
    public void parserFlagsPing() {
        byte[] ping = {(byte) 0xC8, 4, 0x28, 0x00, (byte) 0xEA, 0};
        ping[5] = (byte) CrsfCrc8.compute(ping, 2, 3);
        CrsfStreamParser parser = new CrsfStreamParser();
        assertNull(parser.feed(ping, 1));
        assertEquals(true, parser.takePing());
        assertEquals(false, parser.takePing());
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
