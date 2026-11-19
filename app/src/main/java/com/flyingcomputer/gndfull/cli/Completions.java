package com.flyingcomputer.gndfull.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command completion for the CLI input, word by word like a shell: the next word (or {@code
 * key=} argument, or argument value) of every command form that fits what is typed so far. The
 * dropdown also offers prefix matches from the history (newest first).
 *
 * <p>A form is space-separated tokens. Leading tokens are positional words: a literal, {@code
 * a|b|c} alternatives, or a {@code <placeholder>} for free input. Tokens with {@code =} after them
 * are optional {@code key=value} arguments in any order; the value is a {@code <placeholder>} or
 * {@code A|B} alternatives.
 */
public final class Completions {
    private Completions() {}

    /** One choice: {@code insert} is the input line after picking it, {@code label} adds hints. */
    public record Candidate(String insert, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** A console's command forms. */
    public static final class Grammar {
        private final List<Form> forms = new ArrayList<>();

        public Grammar(String... forms) {
            for (String f : forms) {
                this.forms.add(new Form(f));
            }
        }

        /** Completions of the last (partial) word of {@code line}, without duplicates. */
        public List<Candidate> candidates(String line) {
            String s = line.stripLeading();
            int cut = s.lastIndexOf(' ');
            String partial = s.substring(cut + 1);
            String head = cut < 0 ? "" : s.substring(0, cut + 1);
            List<String> done =
                    head.isBlank() ? List.of() : Arrays.asList(head.trim().split(" +"));
            Map<String, String> out = new LinkedHashMap<>();
            for (Form f : forms) {
                f.complete(done, head, partial, out);
            }
            List<Candidate> list = new ArrayList<>(out.size());
            out.forEach((insert, label) -> list.add(new Candidate(insert, label)));
            return list;
        }
    }

    private static final class Form {
        final List<String> words = new ArrayList<>();
        final List<String> args = new ArrayList<>();

        Form(String form) {
            for (String t : form.trim().split(" +")) {
                if (t.indexOf('=') > 0) {
                    args.add(t);
                } else {
                    words.add(t);
                }
            }
        }

        void complete(List<String> done, String head, String partial, Map<String, String> out) {
            if (done.size() > words.size() && args.isEmpty()) {
                return;
            }
            for (int i = 0; i < done.size(); i++) {
                String t = done.get(i);
                if (i < words.size() ? !wordMatches(words.get(i), t) : argFor(t) == null) {
                    return;
                }
            }
            int pos = done.size();
            if (pos < words.size()) {
                String w = words.get(pos);
                if (isPlaceholder(w)) {
                    return;
                }
                boolean more = pos + 1 < words.size();
                String rest = hint(pos + 1);
                for (String alt : w.split("\\|")) {
                    if (startsWith(alt, partial)) {
                        String insert = head + alt + (more ? " " : "");
                        add(out, insert, (head + alt + rest).trim());
                    }
                }
                return;
            }
            int eq = partial.indexOf('=');
            if (eq > 0) {
                // A value for key=: its alternatives.
                String arg = argFor(partial);
                if (arg == null) {
                    return;
                }
                String key = arg.substring(0, arg.indexOf('=') + 1);
                String value = arg.substring(key.length());
                if (isPlaceholder(value)) {
                    return;
                }
                String typed = partial.substring(eq + 1);
                for (String alt : value.split("\\|")) {
                    if (startsWith(alt, typed)) {
                        add(out, head + key + alt, head + key + alt);
                    }
                }
                return;
            }
            for (String arg : args) {
                String key = arg.substring(0, arg.indexOf('=') + 1);
                if (startsWith(key, partial) && !used(done, key)) {
                    String value = arg.substring(key.length());
                    int alts = value.split("\\|").length;
                    // Long enums (modulation=, channel=) are listed once the key is picked.
                    add(out, head + key, head + key + (alts > 3 ? "<" + alts + " values>" : value));
                }
            }
        }

        /** Hint for the words from {@code from} on, and the optional arguments. */
        private String hint(int from) {
            StringBuilder b = new StringBuilder();
            for (int i = from; i < words.size(); i++) {
                String w = words.get(i);
                b.append(' ').append(w.split("\\|").length > 3 ? "<key>" : w);
            }
            if (args.size() > 2) {
                b.append(" [key=…]");
            } else if (!args.isEmpty()) {
                b.append(" [").append(String.join(" ", args)).append(']');
            }
            return b.toString();
        }

        private String argFor(String token) {
            int eq = token.indexOf('=');
            if (eq <= 0) {
                return null;
            }
            String key = token.substring(0, eq + 1).toLowerCase(Locale.ROOT);
            for (String a : args) {
                if (a.toLowerCase(Locale.ROOT).startsWith(key)) {
                    return a;
                }
            }
            return null;
        }

        private boolean used(List<String> done, String key) {
            for (int i = words.size(); i < done.size(); i++) {
                if (startsWith(done.get(i), key)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean wordMatches(String word, String typed) {
            if (isPlaceholder(word)) {
                return true;
            }
            for (String alt : word.split("\\|")) {
                if (alt.equalsIgnoreCase(typed)) {
                    return true;
                }
            }
            return false;
        }

        private static void add(Map<String, String> out, String insert, String label) {
            String had = out.putIfAbsent(insert, label);
            if (had != null && !had.equals(label)) {
                out.put(insert, insert.trim()); // several forms share it: no single hint fits
            }
        }
    }

    private static boolean isPlaceholder(String w) {
        return w.startsWith("<");
    }

    private static boolean startsWith(String s, String prefix) {
        return s.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /** l3 PHY names (winject-l3 PhyAirtime k_phys), for modulation=. */
    static final String MODULATIONS =
            "DSS_1M_L|DSS_2M_S|DSS_2M_L|CCK_5M_L|CCK_5M_S|CCK_11M_L|CCK_11M_S|OFDM_6M|OFDM_9M|OFDM_12M|OFDM_18M|OFDM_24M|OFDM_36M|OFDM_48M|OFDM_54M|OFDM_MCS0_LGI|OFDM_MCS1_LGI|OFDM_MCS2_LGI|OFDM_MCS3_LGI|OFDM_MCS4_LGI|OFDM_MCS5_LGI|OFDM_MCS6_LGI|OFDM_MCS7_LGI|OFDM_MCS0_SGI|OFDM_MCS1_SGI|OFDM_MCS2_SGI|OFDM_MCS3_SGI|OFDM_MCS4_SGI|OFDM_MCS5_SGI|OFDM_MCS6_SGI|OFDM_MCS7_SGI";

    /** vstreamer camera image controls (esp32p4_csi_device live keys). */
    static final String IMAGE_KEYS =
            "exposure_us|gain|gain_ceiling|ae_target|awb|wb_r|wb_b|brightness|contrast"
                    + "|saturation|hue|gamma|sharpness";

    /** 2.4 GHz channels (the C6 radios are 2.4 GHz only). */
    static final String CHANNELS = "1|2|3|4|5|6|7|8|9|10|11|12|13|14";

    /** winject-l3 console (ConsoleCommands k_help_text), long and short command names. */
    public static final Grammar L3 =
            new Grammar(
                    "help|?|h",
                    "ping|p",
                    "version|ver",
                    "add_upstream|au id=<u8> txbus=<u8> rxbus=<u8> type=UDP rx=<address:port>"
                            + " tx=<interface:port> fec=NONE|BLOCK k=<n> n=<n> fec_timeout=<ms>"
                            + " quanta=<bytes>",
                    "list_upstream|lu ids=<u8,...>",
                    "update_upstream|uu id=<u8> fec=NONE|BLOCK k=<n> n=<n> fec_timeout=<ms>"
                            + " quanta=<bytes>",
                    "remove_upstream|ru id=<u8>",
                    "list_upstream_rx_stat|lur ids=<u8,...>",
                    "list_upstream_tx_stat|lut ids=<u8,...>",
                    "get_metrics|gm keys=<key,...>",
                    "radio_info|ri",
                    "radio_caps_info|rci",
                    "radio_stats|rs",
                    "radio_tx|rt channel=" + CHANNELS + " tx_power=<dBm> modulation=" + MODULATIONS
                            + " cca=0|1",
                    "radio_mplane|rmp <radio_command_line>",
                    "reset|r",
                    "config slot=<u8>",
                    "radio_device|rd id=<u8> mplane=<ip:port> dplane=<ip:port>"
                            + " fcs=AUTO|SIGNAL|ACTUAL",
                    "radio_domain id=<u8>");

    /**
     * vstreamer live m-plane (live_mplane: ping, nodes, get/set node key) on the rover's live TX:
     * nodes src (esp32_h264_isp_ng_src), pay (rtp_h264_pay), snd (stream_sender) and tx pause.
     * Settings are the ones accepted while streaming; the rest answer EBUSY until reopen.
     */
    public static final Grammar VSTREAMER =
            new Grammar(
                    "ping",
                    "nodes",
                    "get tx pause",
                    "set tx pause 0|1",
                    "get src state|status|backend|stats|size|fps|buffers|light_freq|rotate|driver"
                            + "|dq_stats|bitrate|gop|qp_min|qp_max|rc|codec|enc_frames|encode_ms"
                            + "|latency_ms|node_latency_ms|encode_cpu|encode_prio"
                            + "|sensor_state|isp_state|" + IMAGE_KEYS,
                    "set src bitrate <kbit/s>",
                    "set src gop <frames>",
                    "set src idr 1",
                    "set src reset_stats 1",
                    // Camera image controls (OV5647 sensor, P4 ISP), applied while streaming.
                    "set src exposure_us <us,0:auto>",
                    "set src gain <16-1023x16,0:auto>",
                    "set src gain_ceiling <16-1023x16>",
                    "set src ae_target <2-235>",
                    "set src awb 0|1",
                    "set src wb_r <1-3999x1000>",
                    "set src wb_b <1-3999x1000>",
                    "set src brightness <-128-127>",
                    "set src contrast <0-255,128:1x>",
                    "set src saturation <0-255,128:1x>",
                    "set src hue <0-360deg>",
                    "set src gamma <20-400x100,0:off>",
                    "set src sharpness <0-400%>",
                    "get pay mtu|fps|datagrams_copied|datagrams_dropped|datagrams_in_place"
                            + "|pool_misses",
                    "set pay mtu <bytes>",
                    "set pay fps <1-120>",
                    "get snd stats|fec_mode|fec_k|fec_n|fec_spread_ms|pkt_sent|byt_sent"
                            + "|peer_udp_pkt_recv|peer_fec_pkt_recv|peer_byt_recv|send_late_max_ms"
                            + "|reactor|queue_transport_tx|queue_transport_rx|udp_transport"
                            + "|send_cpu|send_prio",
                    "set snd fec_k <n>",
                    "set snd fec_n <n>",
                    "set snd fec_spread_ms <ms>",
                    "set snd fec none|block");

    /**
     * Up to {@code limit} candidates for {@code line}: history lines that extend it (newest
     * first), then the grammar's next words, without duplicates.
     */
    public static List<Candidate> match(
            String line, List<String> historyNewestFirst, Grammar grammar, int limit) {
        Map<String, Candidate> out = new LinkedHashMap<>();
        for (String h : historyNewestFirst) {
            if (h.length() > line.length() && startsWith(h, line)) {
                out.putIfAbsent(h, new Candidate(h, h));
            }
        }
        for (Candidate c : grammar.candidates(line)) {
            out.putIfAbsent(c.insert(), c);
        }
        List<Candidate> list = new ArrayList<>(out.values());
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    /**
     * Tab completion: the longest common prefix of the candidates, or {@code line} itself when
     * there are none or it is already the longest.
     */
    public static String complete(String line, List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return line;
        }
        String common = candidates.get(0).insert();
        for (Candidate c : candidates) {
            String m = c.insert();
            int n = 0;
            int max = Math.min(common.length(), m.length());
            while (n < max
                    && Character.toLowerCase(common.charAt(n))
                            == Character.toLowerCase(m.charAt(n))) {
                n++;
            }
            common = common.substring(0, n);
        }
        return common.length() > line.length() ? common : line;
    }
}
