package com.flyingcomputer.gndfull.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.flyingcomputer.gndfull.transport.msp.MspV2;
import com.flyingcomputer.gndfull.transport.rover.GroundLinkFrame;
import com.flyingcomputer.gndfull.transport.rover.LinkRate;
import com.flyingcomputer.gndfull.transport.rover.RoverTelemFrame;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

public final class LinkStatsTest {
    private static final long S = 1_000_000_000L;

    private static byte[] hex(String h) {
        byte[] b = new byte[h.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }

    /** Wire bytes as gnd-esp32p4 main.cpp msp_v2_frame() builds them. */
    @Test
    public void decodesGroundLinkFrame() {
        MspV2.Frame f = MspV2.decode(hex("24583e0030421100c300ffffff15cd5b0700286bee4d00000091"));
        assertNotNull(f);
        assertEquals(GroundLinkFrame.FUNCTION, f.function());
        GroundLinkFrame g = GroundLinkFrame.unpack(f.payload());
        assertNotNull(g);
        assertEquals(-61, g.rssiDbm);
        assertEquals(0xFFFFFF00L, g.linkMs);
        assertEquals(123456789L, g.rxBytes);
        assertEquals(4000000000L, g.rxPackets);
        assertEquals(77L, g.rxLost);
        assertNull(GroundLinkFrame.unpack(new byte[16]));
    }

    @Test
    public void telemetryCarriesLinkCountersAndKeepsOlderSizes() {
        ByteBuffer b = ByteBuffer.allocate(RoverTelemFrame.PAYLOAD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        b.position(39); // after flags
        b.putShort((short) 1234); // vbus
        b.put((byte) -67); // rssi
        b.putInt(0x80000001).putInt(123456789).putInt((int) 4000000000L).putInt(77);
        RoverTelemFrame t = RoverTelemFrame.unpack(b.array());
        assertNotNull(t);
        assertEquals(-67, t.rssiDbm);
        assertEquals(1234, t.vbusCentiVolts);
        assertEquals(0x80000001L, t.linkMs);
        assertEquals(123456789L, t.linkRxBytes);
        assertEquals(4000000000L, t.linkRxPackets);
        assertEquals(77L, t.linkRxLost);
        RoverTelemFrame v2 = RoverTelemFrame.unpack(java.util.Arrays.copyOf(b.array(), 42));
        assertNotNull(v2);
        assertEquals(-67, v2.rssiDbm);
        assertEquals(0, v2.linkRxBytes);
        assertNotNull(RoverTelemFrame.unpack(new byte[39]));
        assertNull(RoverTelemFrame.unpack(new byte[50]));
    }

    @Test
    public void rateAndLossOverOneSecond() {
        LinkRate r = new LinkRate();
        r.add(1000, 0, 0, 0, 0);
        assertNull(r.result(0)); // one sample
        r.add(1500, 375_000, 300, 0, S / 2);
        assertNull(r.result(S / 2)); // 500 ms span
        r.add(1500, 375_000, 300, 0, S / 2 + 1); // repeat (rover telem at 20 Hz)
        r.add(2000, 750_000, 590, 10, S);
        LinkRate.Result x = r.result(S);
        assertNotNull(x);
        assertEquals(6.0, x.mbps(), 1e-9); // 750 kB in 1 s
        assertEquals(10 * 100.0 / 600, x.lossPercent(), 1e-9);
        assertNull(r.result(S + 3 * S)); // stale
    }

    @Test
    public void countersAndClockWrap() {
        LinkRate r = new LinkRate();
        r.add(0xFFFF_FE00L, 0xFFFF_FF00L, 0xFFFF_FFF0L, 5, 0);
        r.add(0x0000_0200L, 0x0000_FF00L, 0x0000_0010L, 5, S); // +1024 ms, +65536 B, +32
        LinkRate.Result x = r.result(S);
        assertNotNull(x);
        assertEquals(65536 * 8.0 / 1024 / 1000, x.mbps(), 1e-9);
        assertEquals(0, x.lossPercent(), 1e-9);
    }

    @Test
    public void senderRestartStartsOver() {
        LinkRate r = new LinkRate();
        r.add(50_000, 1_000_000, 1000, 0, 0);
        r.add(51_000, 2_000_000, 2000, 0, S);
        assertNotNull(r.result(S));
        r.add(500, 10, 1, 0, 2 * S); // rebooted
        assertNull(r.result(2 * S));
    }

    @Test
    public void unknownWhenAllZero() {
        LinkRate r = new LinkRate();
        r.add(0, 0, 0, 0, 0);
        r.add(0, 0, 0, 0, S);
        assertNull(r.result(S));
    }
}
