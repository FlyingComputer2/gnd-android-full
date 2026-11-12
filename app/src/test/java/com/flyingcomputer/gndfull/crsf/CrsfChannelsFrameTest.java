package com.flyingcomputer.gndfull.crsf;

import com.flyingcomputer.gndfull.control.DrivingState;
import com.flyingcomputer.gndfull.transport.rover.EdgeTxOutputMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CrsfChannelsFrameTest {
    @Test
    public void neutralPulsesProduceStandardFrameSize() {
        int[] pulses = new int[CrsfChannelsFrame.CHANNEL_COUNT];
        byte[] frame = CrsfChannelsFrame.fromPulses(pulses);
        assertEquals(CrsfChannelsFrame.FRAME_BYTES, frame.length);
        assertEquals(CrsfChannelsFrame.MODULE_ADDRESS, frame[0]);
        assertEquals(CrsfChannelsFrame.CHANNELS_ID, frame[2]);
        assertEquals(CrsfChannelsFrame.STATUS_CH5_ARMING, frame[25]);
    }

    @Test
    public void ch5ArmSetsHighPulseInCrsfBuilder() {
        EdgeTxOutputMapper mapper = new EdgeTxOutputMapper();
        int[] outputs = new int[16];
        java.util.Arrays.fill(outputs, 1500);
        outputs[2] = 1500;
        int[] switches = new int[8];
        switches[3] = 1;
        byte[] disarmed =
                mapper.buildCrsfFrame(outputs, switches, DrivingState.READY, false);
        byte[] armed =
                mapper.buildCrsfFrame(outputs, switches, DrivingState.ARMED, true);
        assertEquals(CrsfChannelsFrame.FRAME_BYTES, armed.length);
        // Packed channel data differs when CH5 arm pulse changes.
        boolean differs = false;
        for (int i = 3; i < 25; i++) {
            if (disarmed[i] != armed[i]) {
                differs = true;
                break;
            }
        }
        org.junit.Assert.assertTrue(differs);
    }
}
