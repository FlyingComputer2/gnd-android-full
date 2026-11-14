package com.flyingcomputer.gndfull.cli;

import java.util.List;

/**
 * Fast-type keys shown in the 3-column grid under the terminals, per focused
 * console. A complete command is sent on tap; one that ends in a space or '='
 * still needs an argument, so it is put in the input line instead.
 */
public final class FastKeys {
    private FastKeys() {}

    /** vstreamer live m-plane (docs/vstreamer.md, live_mplane). */
    public static final List<String> VSTREAMER =
            List.of(
                    "help",
                    "ping",
                    "nodes",
                    "state",
                    "get src bitrate",
                    "set src bitrate ",
                    "get src fps",
                    "get src gop",
                    "set src gop ",
                    "set tx pause 1",
                    "set tx pause 0",
                    "get ");

    /** winject-l3 console (winject-l3 docs/mplane.md). */
    public static final List<String> L3 =
            List.of(
                    "help",
                    "ping",
                    "ver",
                    "ri",
                    "rs",
                    "lu",
                    "lur",
                    "lut",
                    "gm keys=",
                    "rt tx_power=",
                    "rt channel=",
                    "rmp ");

    /** True when tapping the key sends it as is; false when it needs more typing. */
    public static boolean sendsImmediately(String key) {
        return !key.isEmpty() && !key.endsWith(" ") && !key.endsWith("=");
    }

    /**
     * The input line after inserting {@code key}: a key that starts a command
     * replaces the line, unless the line already ends in a space or '=' (then the
     * key is an argument and is appended).
     */
    public static String insert(String line, String key) {
        if (line.isEmpty() || line.endsWith(" ") || line.endsWith("=")) {
            return line + key;
        }
        return key;
    }
}
