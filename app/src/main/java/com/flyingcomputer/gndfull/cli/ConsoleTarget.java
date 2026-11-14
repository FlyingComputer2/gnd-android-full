package com.flyingcomputer.gndfull.cli;

import java.util.List;

/**
 * One text console reached over UDP on the ground P4 (one request per datagram,
 * replies to the sender). The rover's consoles are relayed by the ground, so all
 * three targets share the ground's address and differ by port.
 */
public enum ConsoleTarget {
    // Rover vstreamer m-plane, ground relay upstream 6.
    ROV_VS("rov vs", 5092, FastKeys.VSTREAMER, Completions.VSTREAMER),
    // Rover winject-l3 console, ground relay upstream 4.
    ROV_L3("rov l3", 2410, FastKeys.L3, Completions.L3),
    // Ground winject-l3 console (cfg.console_in).
    GND_L3("gnd l3", 2400, FastKeys.L3, Completions.L3);

    public static final String DEFAULT_HOST = "192.168.253.19";

    public final String label;
    public final int port;
    public final List<String> fastKeys;
    public final List<String> completions;

    ConsoleTarget(String label, int port, List<String> fastKeys, List<String> completions) {
        this.label = label;
        this.port = port;
        this.fastKeys = fastKeys;
        this.completions = completions;
    }
}
