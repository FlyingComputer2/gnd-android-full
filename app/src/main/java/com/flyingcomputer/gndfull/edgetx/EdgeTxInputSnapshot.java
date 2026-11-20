package com.flyingcomputer.gndfull.edgetx;

import java.util.Arrays;

/**
 * Immutable full-input snapshot for one mixer frame: sticks (-1024..1024), the eight TX16S
 * switches as {@code simuSetSwitch} states (-1 up, 0 mid, 1 down) and the pots/sliders S1, S2,
 * LS, RS (-1024..1024).
 */
public final class EdgeTxInputSnapshot {
    public static final int STICK_AXES = 4;
    public static final int SWITCH_COUNT = 8;
    public static final int POT_COUNT = 4;

    private final int[] sticks;
    private final int[] switches;
    private final int[] pots;

    public EdgeTxInputSnapshot(int[] sticks, int[] switches) {
        this(sticks, switches, new int[POT_COUNT]);
    }

    public EdgeTxInputSnapshot(int[] sticks, int[] switches, int[] pots) {
        if (sticks.length != STICK_AXES) {
            throw new IllegalArgumentException("expected four stick axes");
        }
        if (switches.length != SWITCH_COUNT) {
            throw new IllegalArgumentException("expected eight switches");
        }
        if (pots.length != POT_COUNT) {
            throw new IllegalArgumentException("expected four pots");
        }
        this.sticks = Arrays.copyOf(sticks, STICK_AXES);
        this.switches = Arrays.copyOf(switches, SWITCH_COUNT);
        this.pots = Arrays.copyOf(pots, POT_COUNT);
    }

    public int[] sticks() {
        return Arrays.copyOf(sticks, sticks.length);
    }

    public int[] switches() {
        return Arrays.copyOf(switches, switches.length);
    }

    public int[] pots() {
        return Arrays.copyOf(pots, pots.length);
    }

    /** Sticks and pots centred, switches up (the EdgeTX boot state). */
    public static EdgeTxInputSnapshot neutral() {
        return new EdgeTxInputSnapshot(
                new int[] {0, 0, 0, 0}, new int[] {-1, -1, -1, -1, -1, -1, -1, -1}, new int[4]);
    }
}
