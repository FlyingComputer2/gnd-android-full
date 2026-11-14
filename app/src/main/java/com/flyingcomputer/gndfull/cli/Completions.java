package com.flyingcomputer.gndfull.cli;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Command completion for the CLI input: prefix matches from the history (newest
 * first), then from the focused console's command vocabulary.
 */
public final class Completions {
    private Completions() {}

    /** winject-l3 console commands (ConsoleCommands k_help_text), long and short forms. */
    public static final List<String> L3 =
            List.of(
                    "help",
                    "ping",
                    "version",
                    "ver",
                    "add_upstream id= txbus= rxbus= type=UDP rx= tx= fec= k= n= fec_timeout= quanta=",
                    "list_upstream",
                    "list_upstream ids=",
                    "lu",
                    "lu ids=",
                    "update_upstream id=",
                    "uu id=",
                    "remove_upstream id=",
                    "ru id=",
                    "list_upstream_rx_stat",
                    "list_upstream_rx_stat ids=",
                    "lur",
                    "lur ids=",
                    "list_upstream_tx_stat",
                    "list_upstream_tx_stat ids=",
                    "lut",
                    "lut ids=",
                    "get_metrics",
                    "get_metrics keys=",
                    "gm",
                    "gm keys=",
                    "radio_info",
                    "ri",
                    "radio_caps_info",
                    "rci",
                    "radio_stats",
                    "rs",
                    "radio_tx",
                    "rt",
                    "rt channel=",
                    "rt tx_power=",
                    "rt modulation=",
                    "rt cca=",
                    "radio_mplane ",
                    "rmp ",
                    "reset",
                    "config slot=",
                    "radio_device id=",
                    "radio_domain",
                    "radio_domain id=");

    /** vstreamer live m-plane verbs and the rover's known node keys. */
    public static final List<String> VSTREAMER =
            List.of(
                    "help",
                    "ping",
                    "nodes",
                    "state",
                    "start",
                    "pause",
                    "stop",
                    "get src bitrate",
                    "get src fps",
                    "get src gop",
                    "get src qp_min",
                    "get src qp_max",
                    "get src buffers",
                    "set src bitrate ",
                    "set src fps ",
                    "set src gop ",
                    "set src qp_min ",
                    "set src qp_max ",
                    "get pay ",
                    "set pay ",
                    "get snd ",
                    "set snd ",
                    "get tx pause",
                    "set tx pause 1",
                    "set tx pause 0");

    /**
     * Up to {@code limit} candidates starting with {@code prefix} (case-insensitive),
     * history first (newest first), without duplicates or the prefix itself.
     */
    public static List<String> match(
            String prefix, List<String> historyNewestFirst, List<String> vocab, int limit) {
        String p = prefix.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (List<String> source : List.of(historyNewestFirst, vocab)) {
            for (String c : source) {
                if (out.size() >= limit) {
                    return new ArrayList<>(out);
                }
                if (c.length() > prefix.length() && c.toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(c);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Tab completion: the longest common prefix of all matches, or {@code prefix}
     * itself when there is none or it is already the longest.
     */
    public static String complete(String prefix, List<String> matches) {
        if (matches.isEmpty()) {
            return prefix;
        }
        String common = matches.get(0);
        for (String m : matches) {
            int n = 0;
            int max = Math.min(common.length(), m.length());
            while (n < max
                    && Character.toLowerCase(common.charAt(n)) == Character.toLowerCase(m.charAt(n))) {
                n++;
            }
            common = common.substring(0, n);
        }
        return common.length() > prefix.length() ? common : prefix;
    }
}
