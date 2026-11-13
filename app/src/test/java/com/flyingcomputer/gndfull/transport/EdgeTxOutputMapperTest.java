package com.flyingcomputer.gndfull.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.flyingcomputer.gndfull.crsf.CrsfChannelsFrame;
import com.flyingcomputer.gndfull.crsf.CrsfRcChannels;
import com.flyingcomputer.gndfull.crsf.CrsfStreamParser;
import com.flyingcomputer.gndfull.transport.rover.EdgeTxOutputMapper;
import com.flyingcomputer.gndfull.transport.rover.RoverRcFrame;
import org.junit.Test;

public final class EdgeTxOutputMapperTest {
    /** EdgeTX channel outputs (+-1024) -> CRSF frame -> parser, like the external module path. */
    private static CrsfRcChannels viaCrsf(int[] pulses, byte status) {
        byte[] frame = CrsfChannelsFrame.build(pulses, status);
        CrsfRcChannels ch = new CrsfStreamParser().feed(frame, 1);
        assertNotNull(ch);
        return ch;
    }

    private static int[] pulses() {
        return new int[CrsfChannelsFrame.CHANNEL_COUNT];
    }

    @Test
    public void mapsPwmToPerMille() {
        assertEquals(0, EdgeTxOutputMapper.pwmToPerMille(1500));
        assertEquals(1000, EdgeTxOutputMapper.pwmToPerMille(2012));
        assertEquals(-1000, EdgeTxOutputMapper.pwmToPerMille(988));
        assertEquals(500, EdgeTxOutputMapper.pwmToPerMille(1756));
    }

    @Test
    public void channelMapMatchesTable() {
        int[] p = pulses();
        p[0] = 1024; // CH1 unused
        p[1] = -1024; // CH2 unused
        p[2] = 512; // CH3 throttle
        p[3] = -1024; // CH4 steering
        p[4] = 1024; // CH5 arm
        p[5] = 1024; // CH6 rate
        p[6] = 1024; // CH7 servo 2
        p[7] = -512; // CH8 servo 3
        RoverRcFrame rc =
                EdgeTxOutputMapper.map(1, 0, viaCrsf(p, CrsfChannelsFrame.STATUS_CH5_ARMING), true);
        assertEquals(500, rc.thr, 4);
        assertEquals(-1000, rc.steer);
        assertEquals(RoverRcFrame.FLAG_ARM, rc.flags);
        assertEquals(EdgeTxOutputMapper.MODE_RATE, rc.mode);
        assertEquals(1000, rc.aux);
        assertEquals(-500, rc.aux2, 4);
    }

    @Test
    public void unmixedCh6KeepsRoverMode() {
        RoverRcFrame rc =
                EdgeTxOutputMapper.map(
                        1, 0, viaCrsf(pulses(), CrsfChannelsFrame.STATUS_CH5_ARMING), true);
        assertEquals(RoverRcFrame.MODE_NONE, rc.mode);
    }

    @Test
    public void unmixedCh5DoesNotArm() {
        int[] p = pulses(); // CH5 = 0 = 1500 us, as with nothing mixed on it
        assertEquals(
                0,
                EdgeTxOutputMapper.map(1, 0, viaCrsf(p, CrsfChannelsFrame.STATUS_CH5_ARMING), true)
                        .flags);
    }

    @Test
    public void ch5LowOrArmNotAllowedDisarms() {
        int[] p = pulses();
        p[4] = -1024;
        p[5] = -1024;
        RoverRcFrame low =
                EdgeTxOutputMapper.map(1, 0, viaCrsf(p, CrsfChannelsFrame.STATUS_CH5_ARMING), true);
        assertEquals(0, low.flags);
        assertEquals(EdgeTxOutputMapper.MODE_MANUAL, low.mode);
        p[4] = 1024;
        RoverRcFrame blocked =
                EdgeTxOutputMapper.map(1, 0, viaCrsf(p, CrsfChannelsFrame.STATUS_CH5_ARMING), false);
        assertEquals(0, blocked.flags);
        p[2] = 1024;
        assertEquals(
                0,
                EdgeTxOutputMapper.map(1, 0, viaCrsf(p, CrsfChannelsFrame.STATUS_CH5_ARMING), false)
                        .thr);
    }

    @Test
    public void switchArmingModeUsesStatusByte() {
        int[] p = pulses();
        p[4] = -1024; // CH5 low, but Switch mode says armed
        assertEquals(
                RoverRcFrame.FLAG_ARM, EdgeTxOutputMapper.map(1, 0, viaCrsf(p, (byte) 0x01), true).flags);
        p[4] = 1024; // CH5 high, Switch mode says disarmed
        assertEquals(0, EdgeTxOutputMapper.map(1, 0, viaCrsf(p, (byte) 0x00), true).flags);
    }
}
