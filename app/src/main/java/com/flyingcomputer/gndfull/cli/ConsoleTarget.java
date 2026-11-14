package com.flyingcomputer.gndfull.cli;

/**
 * One text console on the ground P4, reached over the ground USB link (channel 4,
 * cli-v1: the ground sends each line as one datagram to its own 127.0.0.1:port).
 * The rover's consoles are relayed by the ground, so targets differ by port only.
 */
public enum ConsoleTarget {
    // Rover vstreamer m-plane, ground relay upstream 6.
    ROV_VS("rov vs", 5092, Completions.VSTREAMER),
    // Rover winject-l3 console, ground relay upstream 4.
    ROV_L3("rov l3", 2410, Completions.L3),
    // Ground winject-l3 console (cfg.console_in).
    GND_L3("gnd l3", 2400, Completions.L3);

    public final String label;
    public final int port;
    public final Completions.Grammar grammar;

    ConsoleTarget(String label, int port, Completions.Grammar grammar) {
        this.label = label;
        this.port = port;
        this.grammar = grammar;
    }
}
