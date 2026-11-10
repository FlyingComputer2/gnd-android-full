package com.flyingcomputer.gndfull.edgetx;

import java.util.Arrays;

/** Immutable full-input snapshot for one mixer frame. */
public final class EdgeTxInputSnapshot {
    public static final int STICK_AXES = 4;
    public static final int SWITCH_COUNT = 8;

    private final int[] sticks;
    private final int[] switches;

    public EdgeTxInputSnapshot(int[] sticks, int[] switches) {
        if (sticks.length != STICK_AXES) {
            throw new IllegalArgumentException("expected four stick axes");
        }
        if (switches.length != SWITCH_COUNT) {
            throw new IllegalArgumentException("expected eight switches");
        }
        this.sticks = Arrays.copyOf(sticks, STICK_AXES);
        this.switches = Arrays.copyOf(switches, SWITCH_COUNT);
    }

    public int[] sticks() {
        return Arrays.copyOf(sticks, sticks.length);
    }

    public int[] switches() {
        return Arrays.copyOf(switches, switches.length);
    }

    public static EdgeTxInputSnapshot neutral() {
        return new EdgeTxInputSnapshot(new int[] {0, 0, 0, 0}, new int[] {0, 0, 0, 0, 0, 0, 0, 0});
    }
}
