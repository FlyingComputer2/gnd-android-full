package com.flyingcomputer.gndfull.transport;

import com.flyingcomputer.gndfull.control.DrivingState;
import com.flyingcomputer.gndfull.transport.rover.EdgeTxOutputMapper;
import com.flyingcomputer.gndfull.transport.rover.RoverRcFrame;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class EdgeTxOutputMapperTest {
    @Test
    public void mapsPwmToPerMille() {
        assertEquals(0, EdgeTxOutputMapper.pwmToPerMille(1500));
        assertEquals(1000, EdgeTxOutputMapper.pwmToPerMille(2000));
        assertEquals(-1000, EdgeTxOutputMapper.pwmToPerMille(1000));
    }

    @Test
    public void disarmsWhenSaSwitchLow() {
        EdgeTxOutputMapper mapper = new EdgeTxOutputMapper();
        int[] outputs = new int[] {2000, 1500, 2000, 1500, 1500, 1500, 1500, 1500, 1500, 1500, 1500, 1500, 1500, 1500, 1500, 1500};
        int[] switches = new int[8];
        RoverRcFrame rc =
                mapper.map(1, 0, outputs, switches, DrivingState.ARMED, true);
        assertEquals(0, rc.steer);
        assertEquals(0, rc.thr);
        assertEquals(0, rc.flags);
    }

    @Test
    public void ch6AndCh7DriveAuxEvenWithSaLow() {
        EdgeTxOutputMapper mapper = new EdgeTxOutputMapper();
        int[] outputs = new int[16];
        java.util.Arrays.fill(outputs, 1500);
        outputs[5] = 2000;
        outputs[6] = 1250;
        RoverRcFrame rc =
                mapper.map(1, 0, outputs, new int[8], DrivingState.READY, false);
        assertEquals(1000, rc.aux);
        assertEquals(-500, rc.aux2);
        byte[] p = rc.pack();
        assertEquals(17, p.length);
        assertEquals((short) 1000, (short) ((p[13] & 0xFF) | (p[14] << 8)));
        assertEquals((short) -500, (short) ((p[15] & 0xFF) | (p[16] << 8)));
    }
}
