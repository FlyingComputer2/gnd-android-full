package com.flyingcomputer.gndfull.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class CliLogicTest {
    @Test
    public void targetsUseGroundPorts() {
        assertEquals(5092, ConsoleTarget.ROV_VS.port);
        assertEquals(2410, ConsoleTarget.ROV_L3.port);
        assertEquals(2400, ConsoleTarget.GND_L3.port);
        for (ConsoleTarget t : ConsoleTarget.values()) {
            assertEquals("fast grid is 3 columns", 0, t.fastKeys.size() % 3);
        }
    }

    @Test
    public void fastKeysSendCompleteCommandsOnly() {
        assertTrue(FastKeys.sendsImmediately("lur"));
        assertTrue(FastKeys.sendsImmediately("set tx pause 1"));
        assertFalse(FastKeys.sendsImmediately("rmp "));
        assertFalse(FastKeys.sendsImmediately("rt tx_power="));
        assertFalse(FastKeys.sendsImmediately(""));
    }

    @Test
    public void fastKeyInsertReplacesOrAppends() {
        assertEquals("rmp ", FastKeys.insert("", "rmp "));
        assertEquals("rmp ", FastKeys.insert("lur", "rmp "));
        assertEquals("rmp ri", FastKeys.insert("rmp ", "ri"));
        assertEquals("gm keys=x", FastKeys.insert("gm keys=", "x"));
    }

    @Test
    public void lineBufferSplitsAndCaps() {
        LineBuffer b = new LineBuffer(3);
        b.append("ok a\nok b\n");
        assertEquals(2, b.size());
        b.append("c\r\nd");
        assertEquals("ok b\nc\nd", b.toString());
        b.clear();
        assertEquals("", b.toString());
    }

    @Test
    public void historyBrowses() {
        CommandHistory h = new CommandHistory(10);
        assertNull(h.previous());
        h.add("a");
        h.add("b");
        h.add("b");
        assertEquals("b", h.previous());
        assertEquals("a", h.previous());
        assertEquals("a", h.previous());
        assertEquals("b", h.next());
        assertEquals("", h.next());
    }

    @Test
    public void completionHistoryFirstThenVocab() {
        List<String> m = Completions.match("l", List.of("lur ids=4", "ping"), Completions.L3, 4);
        assertEquals(List.of("lur ids=4", "list_upstream", "list_upstream ids=", "lu"), m);
        assertTrue(Completions.match("lu", List.of(), List.of("lu", "lur"), 8).equals(List.of("lur")));
        assertTrue(Completions.match("GET SRC", List.of(), Completions.VSTREAMER, 8).contains("get src fps"));
        assertTrue(Completions.match("zz", List.of(), Completions.L3, 8).isEmpty());
    }

    @Test
    public void tabCompletesCommonPrefix() {
        assertEquals("list_upstream",
                Completions.complete("list_", Completions.match("list_", List.of(), Completions.L3, 99)));
        assertEquals("radio_", Completions.complete("rad", Completions.match("rad", List.of(), Completions.L3, 99)));
        assertEquals("set tx pause ",
                Completions.complete("set t", Completions.match("set t", List.of(), Completions.VSTREAMER, 99)));
        assertEquals("x", Completions.complete("x", List.of()));
    }
}
