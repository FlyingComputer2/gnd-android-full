package com.flyingcomputer.gndfull.transport;

import com.flyingcomputer.gndfull.transport.msp.MspV2;
import com.flyingcomputer.gndfull.transport.rover.RoverRcFrame;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class MspV2Test {
    @Test
    public void roundTripRcPayloadMatchesPythonFixture() {
        byte[] payload =
                new RoverRcFrame(1, 0x12345678, 2, 0, 255, 300, -200, 0)
                        .pack();
        byte[] wire = MspV2.encode(MspV2.TO_ROVER, RoverRcFrame.FUNCTION, payload);
        MspV2.Frame decoded = MspV2.decode(wire);
        assertNotNull(decoded);
        assertEquals(MspV2.TO_ROVER, decoded.direction());
        assertEquals(RoverRcFrame.FUNCTION, decoded.function());
        assertEquals(15, decoded.payload().length);
    }
}
