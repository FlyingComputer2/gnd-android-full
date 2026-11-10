package com.flyingcomputer.gndfull.edgetx;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class Tx16KeyMapTest {
    @Test
    public void mapsTx16sNavKeys() {
        assertEquals(Tx16KeyMap.KEY_EXIT, Tx16KeyMap.toFirmware(EdgeTxKey.RTN));
        assertEquals(Tx16KeyMap.KEY_ENTER, Tx16KeyMap.toFirmware(EdgeTxKey.ENTER));
        assertEquals(Tx16KeyMap.KEY_TELE, Tx16KeyMap.toFirmware(EdgeTxKey.TELE));
        assertEquals(Tx16KeyMap.KEY_PAGEUP, Tx16KeyMap.toFirmware(EdgeTxKey.PAGE_UP));
    }
}
