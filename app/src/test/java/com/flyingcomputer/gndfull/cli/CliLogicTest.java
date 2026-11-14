package com.flyingcomputer.gndfull.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CliLogicTest {
    private static List<String> inserts(List<Completions.Candidate> c) {
        List<String> out = new ArrayList<>();
        for (Completions.Candidate x : c) {
            out.add(x.insert());
        }
        return out;
    }

    private static List<String> next(Completions.Grammar g, String line) {
        return inserts(g.candidates(line));
    }

    @Test
    public void targetsUseGroundPorts() {
        assertEquals(5092, ConsoleTarget.ROV_VS.port);
        assertEquals(2410, ConsoleTarget.ROV_L3.port);
        assertEquals(2400, ConsoleTarget.GND_L3.port);
        assertEquals(Completions.VSTREAMER, ConsoleTarget.ROV_VS.grammar);
        assertEquals(Completions.L3, ConsoleTarget.GND_L3.grammar);
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
    public void vstreamerCompletesEveryNodeWordByWord() {
        Completions.Grammar g = Completions.VSTREAMER;
        assertEquals(List.of("ping", "nodes", "get ", "set "), next(g, ""));
        assertEquals(List.of("get tx ", "get src ", "get pay ", "get snd "), next(g, "get "));
        assertEquals(List.of("set snd "), next(g, "set sn"));
        List<String> snd = next(g, "get snd ");
        assertTrue(snd.contains("get snd fec_k"));
        assertTrue(snd.contains("get snd stats"));
        assertTrue(next(g, "get pay ").contains("get pay mtu"));
        assertTrue(next(g, "GET SRC ").contains("GET SRC bitrate"));
        assertEquals(List.of("set snd fec_k ", "set snd fec_n ", "set snd fec_spread_ms ",
                "set snd fec "), next(g, "set snd fec"));
        assertEquals(List.of("set snd fec none", "set snd fec block"), next(g, "set snd fec "));
        assertEquals(List.of("set tx pause 0", "set tx pause 1"), next(g, "set tx pause "));
        // A placeholder takes free input: nothing to offer.
        assertTrue(next(g, "set src bitrate ").isEmpty());
        assertTrue(next(g, "bogus ").isEmpty());
    }

    @Test
    public void labelsShowWhatFollows() {
        List<Completions.Candidate> c = Completions.VSTREAMER.candidates("set src bit");
        assertEquals(1, c.size());
        assertEquals("set src bitrate ", c.get(0).insert());
        assertEquals("set src bitrate <kbit/s>", c.get(0).label());
        assertEquals("rt tx_power=<dBm>",
                Completions.L3.candidates("rt tx").get(0).label());
    }

    @Test
    public void l3CompletesNamesArgumentsAndValues() {
        Completions.Grammar g = Completions.L3;
        assertEquals(List.of("list_upstream", "lu", "list_upstream_rx_stat", "lur",
                "list_upstream_tx_stat", "lut"), next(g, "l"));
        assertEquals(List.of("lu ids="), next(g, "lu "));
        assertEquals(List.of("rt channel=", "rt tx_power=", "rt modulation=", "rt cca="),
                next(g, "rt "));
        // Arguments already given are not offered again, in any order.
        assertEquals(List.of("rt channel=36 tx_power=10 modulation=", "rt channel=36 tx_power=10 cca="),
                next(g, "rt channel=36 tx_power=10 "));
        assertEquals(List.of("uu id=1 fec=NONE", "uu id=1 fec=BLOCK"), next(g, "uu id=1 fec="));
        assertEquals(List.of("rd fcs=SIGNAL"), next(g, "rd fcs=s"));
        assertTrue(next(g, "rmp ").isEmpty());
        assertTrue(next(g, "ri ").isEmpty());
    }

    @Test
    public void matchPutsHistoryFirst() {
        List<Completions.Candidate> m =
                Completions.match("l", List.of("lur ids=4", "ping"), Completions.L3, 4);
        assertEquals(List.of("lur ids=4", "list_upstream", "lu", "list_upstream_rx_stat"),
                inserts(m));
        assertTrue(Completions.match("zz", List.of(), Completions.L3, 8).isEmpty());
    }

    @Test
    public void tabCompletesCommonPrefix() {
        Completions.Grammar l3 = Completions.L3;
        assertEquals("list_upstream", Completions.complete("list_", l3.candidates("list_")));
        assertEquals("radio_", Completions.complete("radio", l3.candidates("radio")));
        // One word at a time, like a shell.
        assertEquals("set tx ",
                Completions.complete("set t", Completions.VSTREAMER.candidates("set t")));
        assertEquals("set tx pause ",
                Completions.complete("set tx ", Completions.VSTREAMER.candidates("set tx ")));
        assertEquals("get src ",
                Completions.complete("get sr", Completions.VSTREAMER.candidates("get sr")));
        assertEquals("x", Completions.complete("x", List.of()));
    }
}
