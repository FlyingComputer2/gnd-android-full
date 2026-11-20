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
    public void binaryPortGoesToItsDatagramListenerOthersAsText() {
        CliUsbHandler h = new CliUsbHandler();
        java.util.List<byte[]> bin = new java.util.ArrayList<>();
        java.util.List<String> text = new java.util.ArrayList<>();
        h.setListener((port, t) -> text.add(port + ":" + t));
        h.setDatagramListener(2440, bin::add);
        h.dispatch(new byte[] {0x09, (byte) 0x88, (byte) 0x93, 0, (byte) 0xff});
        h.dispatch(new byte[] {0x13, (byte) 0xe4, 'o', 'k'});
        assertEquals(1, bin.size());
        assertArrayEquals(new byte[] {(byte) 0x93, 0, (byte) 0xff}, bin.get(0));
        assertEquals(java.util.List.of("5092:ok"), text);
        h.setDatagramListener(2440, null);
        h.dispatch(new byte[] {0x09, (byte) 0x88, 'x'});
        assertEquals("2440:x", text.get(1));
        assertArrayEquals(new byte[] {0x09, (byte) 0x88, 1, 2}, CliUsbHandler.encodeBytes(2440, new byte[] {1, 2}));
        assertFalse(h.sendBytes(2440, new byte[] {1}));
    }

    @Test
    public void notOpenUntilAUsbSessionOpensIt() {
        CliUsbHandler h = new CliUsbHandler();
        assertFalse(h.isOpen());
        assertFalse(h.send(2400, "version"));
    }
}
