package com.flyingcomputer.gndfull.switches;

/**
 * One of the eight on-screen switch slots: what it looks like (label), how it behaves
 * (type, positions) and which EdgeTX input it drives (output).
 *
 * <p>Positions are indices {@code 0..positions()-1}:
 *
 * <ul>
 *   <li>{@link Type#PUSH}: 0 released, 1 held (momentary).
 *   <li>{@link Type#TOGGLE}: 0 / 1, flips on tap.
 *   <li>{@link Type#MPOS}: 0..n-1, tap the left half to step down, the right half to step up.
 * </ul>
 *
 * A switch output (SA..SH) takes 2 or 3 positions; an analog output (S1, S2, LS, RS) takes any
 * count and behaves like an axis with n evenly spaced detents.
 */
public record SwitchSlot(String label, Type type, int count, Output output) {
    public static final int MAX_POSITIONS = 12;

    public enum Type {
        PUSH("push"),
        TOGGLE("toggle"),
        MPOS("mpos");

        public final String key;

        Type(String key) {
            this.key = key;
        }

        public static Type fromKey(String key) {
            for (Type t : values()) {
                if (t.key.equals(key)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("unknown switch type " + key);
        }
    }

    /** EdgeTX TX16S inputs a slot can drive ({@code hw_defs/tx16s.json} indices). */
    public enum Output {
        SA(false, 0),
        SB(false, 1),
        SC(false, 2),
        SD(false, 3),
        SE(false, 4),
        SF(false, 5),
        SG(false, 6),
        SH(false, 7),
        /** Pot P1. */
        S1(true, 4),
        /** Pot P3. */
        S2(true, 6),
        /** Slider SL1. */
        LS(true, 7),
        /** Slider SL2. */
        RS(true, 8);

        public final boolean analog;
        /** {@code simuSetSwitch} index for switches, {@code simuGetAnalog} index for analogs. */
        public final int firmwareIndex;

        Output(boolean analog, int firmwareIndex) {
            this.analog = analog;
            this.firmwareIndex = firmwareIndex;
        }

        /** Index into {@code EdgeTxInputSnapshot.pots()} (S1, S2, LS, RS), or -1. */
        public int potSlot() {
            return analog ? ordinal() - S1.ordinal() : -1;
        }
    }

    public SwitchSlot {
        if (label == null) {
            label = "";
        }
        label = label.replace('|', '/').replace(';', ',').replace('\n', ' ').trim();
        if (label.length() > 6) {
            label = label.substring(0, 6);
        }
        if (type == null || output == null) {
            throw new IllegalArgumentException("type and output required");
        }
        if (type != Type.MPOS) {
            count = 2;
        }
        String err = check(type, count, output);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
    }

    /** Null when valid, else a user-facing reason. */
    public static String check(Type type, int count, Output output) {
        int n = type == Type.MPOS ? count : 2;
        if (n < 2 || n > MAX_POSITIONS) {
            return "positions must be 2.." + MAX_POSITIONS;
        }
        if (!output.analog && n > 3) {
            return output + " is a switch (max 3 positions); use S1, S2, LS or RS for more";
        }
        return null;
    }

    public int positions() {
        return count;
    }

    /** Push / toggle start released (0); mpos starts in the middle (3-pos: centre). */
    public int initialPosition() {
        return type == Type.MPOS ? (count - 1) / 2 : 0;
    }

    public int clamp(int position) {
        return Math.max(0, Math.min(count - 1, position));
    }

    /**
     * {@code simuSetSwitch} state: -1 = up (SWITCH_HW_UP), 0 = mid, 1 = down. Two positions: 0 up,
     * 1 down. Three: 0 up, 1 mid, 2 down.
     */
    public int switchState(int position) {
        int p = clamp(position);
        if (count == 2) {
            return p == 0 ? -1 : 1;
        }
        return p - 1;
    }

    /** Analog value -1024..1024, evenly spaced: position 0 = -1024, last = +1024. */
    public int analogValue(int position) {
        int p = clamp(position);
        return -1024 + (p * 2048) / (count - 1);
    }

    /** Short text for the tile: "0"/"1", or "2/5" for mpos. */
    public String positionText(int position) {
        int p = clamp(position);
        if (type == Type.MPOS && count > 2) {
            return p + "/" + (count - 1);
        }
        return Integer.toString(p);
    }

    public String describe() {
        String t = type == Type.MPOS ? "mpos " + count : type.key;
        return (label.isEmpty() ? "—" : label) + ": " + t + " → " + output;
    }
}
