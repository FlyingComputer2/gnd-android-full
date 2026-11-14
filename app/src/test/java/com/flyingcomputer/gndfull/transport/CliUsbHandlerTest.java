package com.flyingcomputer.gndfull.transport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CliUsbHandlerTest {
    @Test
    public void encodesPortBigEndianThenLine() {
        assertArrayEquals(
                new byte[] {0x09, 0x60, 'l', 'u', 'r'}, CliUsbHandler.encode(2400, "lur"));
        assertArrayEquals(
                new byte[] {0x13, (byte) 0xe4, 'v'}, CliUsbHandler.encode(5092, "v"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPortZero() {
        CliUsbHandler.encode(0, "x");
    }

    @Test
    public void decodesSourcePortAndText() {
        CliUsbHandler.Reply r =
                CliUsbHandler.decode(new byte[] {0x09, 0x6a, 'O', 'K', ' ', 'v', '\n'});
        assertEquals(2410, r.port());
        assertEquals("OK v\n", r.text());
        assertEquals("", CliUsbHandler.decode(new byte[] {0x13, (byte) 0xe4}).text());
        assertNull(CliUsbHandler.decode(new byte[] {1}));
    }

    @Test
    public void notOpenUntilAUsbSessionOpensIt() {
        CliUsbHandler h = new CliUsbHandler();
        assertFalse(h.isOpen());
        assertFalse(h.send(2400, "version"));
    }
}
