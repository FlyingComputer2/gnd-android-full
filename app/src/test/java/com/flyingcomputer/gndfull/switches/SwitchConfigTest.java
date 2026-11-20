package com.flyingcomputer.gndfull.switches;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.flyingcomputer.gndfull.switches.SwitchSlot.Output;
import com.flyingcomputer.gndfull.switches.SwitchSlot.Type;
import org.junit.Test;

public class SwitchConfigTest {
    @Test
    public void defaultsReproduceOldLayout() {
        SwitchConfig c = SwitchConfig.defaults();
        int[] sw = new int[8];
        int[] pots = new int[4];
        c.toInputs(c.initialPositions(), sw, pots);
        // 3POS start centred, SF/SH (toggle) start up — as the old fixed strips did.
        assertArrayEquals(new int[] {0, 0, 0, 0, 0, -1, 0, -1}, sw);
        assertArrayEquals(new int[] {0, 0, 0, 0}, pots);
        assertEquals("SE", c.slot(0).label());
        assertEquals(Output.SA, c.slot(7).output());
    }

    @Test
    public void switchStates() {
        SwitchSlot push = new SwitchSlot("ARM", Type.PUSH, 9, Output.SH);
        assertEquals(2, push.positions());
        assertEquals(-1, push.switchState(0));
        assertEquals(1, push.switchState(1));
        SwitchSlot three = new SwitchSlot("M", Type.MPOS, 3, Output.SC);
        assertEquals(-1, three.switchState(0));
        assertEquals(0, three.switchState(1));
        assertEquals(1, three.switchState(2));
        assertEquals(1, three.switchState(7));
        assertEquals(1, three.initialPosition());
    }

    @Test
    public void mposOnAxisIsEvenlySpaced() {
        SwitchSlot pot = new SwitchSlot("GAIN", Type.MPOS, 5, Output.S1);
        assertEquals(-1024, pot.analogValue(0));
        assertEquals(-512, pot.analogValue(1));
        assertEquals(0, pot.analogValue(2));
        assertEquals(1024, pot.analogValue(4));
        assertEquals(2, pot.initialPosition());
        assertEquals("2/4", pot.positionText(2));
        SwitchSlot toggle = new SwitchSlot("T", Type.TOGGLE, 2, Output.RS);
        assertEquals(-1024, toggle.analogValue(0));
        assertEquals(1024, toggle.analogValue(1));
    }

    @Test
    public void switchOutputRejectsMoreThanThree() {
        assertNotNull(SwitchSlot.check(Type.MPOS, 4, Output.SA));
        assertNull(SwitchSlot.check(Type.MPOS, 4, Output.LS));
        assertNull(SwitchSlot.check(Type.MPOS, 3, Output.SF));
        assertNotNull(SwitchSlot.check(Type.MPOS, SwitchSlot.MAX_POSITIONS + 1, Output.S2));
        assertThrows(
                IllegalArgumentException.class, () -> new SwitchSlot("x", Type.MPOS, 6, Output.SB));
    }

    @Test
    public void toInputsRoutesPotsAndLeavesUndrivenSwitchesUp() {
        SwitchConfig c =
                SwitchConfig.defaults()
                        .with(7, new SwitchSlot("S2", Type.MPOS, 3, Output.S2))
                        .with(3, new SwitchSlot("H", Type.PUSH, 2, Output.SH));
        int[] pos = c.initialPositions();
        pos[3] = 1;
        pos[7] = 2;
        int[] sw = new int[8];
        int[] pots = new int[4];
        c.toInputs(pos, sw, pots);
        assertEquals(-1, sw[0]); // SA no longer driven
        assertEquals(1, sw[7]); // SH held
        assertArrayEquals(new int[] {0, 1024, 0, 0}, pots);
    }

    @Test
    public void encodeDecodeRoundTrip() {
        SwitchConfig c =
                SwitchConfig.defaults()
                        .with(0, new SwitchSlot("Lig|ht;s", Type.MPOS, 7, Output.LS))
                        .with(4, new SwitchSlot("", Type.PUSH, 2, Output.SC));
        SwitchConfig back = SwitchConfig.decode(c.encode());
        assertEquals(c, back);
        assertEquals("Lig/ht", back.slot(0).label());
        assertEquals("SC", c.duplicateOutputs());
    }

    @Test
    public void corruptFallsBackToDefaults() {
        assertEquals(SwitchConfig.defaults(), SwitchConfig.decode(null));
        assertEquals(SwitchConfig.defaults(), SwitchConfig.decode("v9;x"));
        String bad = SwitchConfig.defaults().encode().replace("SA", "SZ");
        assertEquals(SwitchConfig.defaults(), SwitchConfig.decode(bad));
        String tooMany = SwitchConfig.defaults().encode().replace("SB|mpos|3|SB", "SB|mpos|5|SB");
        assertEquals(SwitchConfig.defaults(), SwitchConfig.decode(tooMany));
    }
}
