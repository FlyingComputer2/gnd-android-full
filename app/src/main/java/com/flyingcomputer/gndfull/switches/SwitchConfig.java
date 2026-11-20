package com.flyingcomputer.gndfull.switches;

import com.flyingcomputer.gndfull.switches.SwitchSlot.Output;
import com.flyingcomputer.gndfull.switches.SwitchSlot.Type;
import java.util.Arrays;

/**
 * The eight switch slots: 0..3 the upper strip, 4..7 the lower strip. Defaults match the
 * previous fixed TX16S layout (SE SF SG SH / SD SC SB SA).
 *
 * <p>Stored as one string: {@code v1;label|type|positions|output;...} (eight entries).
 */
public final class SwitchConfig {
    public static final int SLOTS = 8;
    public static final int PER_ROW = 4;
    private static final String VERSION = "v1";

    private final SwitchSlot[] slots;

    public SwitchConfig(SwitchSlot[] slots) {
        if (slots.length != SLOTS) {
            throw new IllegalArgumentException("expected eight slots");
        }
        this.slots = slots.clone();
    }

    public static SwitchConfig defaults() {
        return new SwitchConfig(
                new SwitchSlot[] {
                    new SwitchSlot("SE", Type.MPOS, 3, Output.SE),
                    new SwitchSlot("SF", Type.TOGGLE, 2, Output.SF),
                    new SwitchSlot("SG", Type.MPOS, 3, Output.SG),
                    new SwitchSlot("SH", Type.TOGGLE, 2, Output.SH),
                    new SwitchSlot("SD", Type.MPOS, 3, Output.SD),
                    new SwitchSlot("SC", Type.MPOS, 3, Output.SC),
                    new SwitchSlot("SB", Type.MPOS, 3, Output.SB),
                    new SwitchSlot("SA", Type.MPOS, 3, Output.SA),
                });
    }

    public SwitchSlot slot(int index) {
        return slots[index];
    }

    public SwitchSlot[] row(int row) {
        return Arrays.copyOfRange(slots, row * PER_ROW, row * PER_ROW + PER_ROW);
    }

    public SwitchConfig with(int index, SwitchSlot slot) {
        SwitchSlot[] next = slots.clone();
        next[index] = slot;
        return new SwitchConfig(next);
    }

    /** Slot indices that drive the same output as an earlier slot (the later one wins). */
    public String duplicateOutputs() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SLOTS; i++) {
            for (int j = 0; j < i; j++) {
                if (slots[i].output() == slots[j].output()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(slots[i].output());
                    break;
                }
            }
        }
        return sb.toString();
    }

    public int[] initialPositions() {
        int[] p = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            p[i] = slots[i].initialPosition();
        }
        return p;
    }

    /**
     * EdgeTX switch states (8, {@code simuSetSwitch} -1/0/1) and pot values (S1, S2, LS, RS,
     * -1024..1024) for the given slot positions. Undriven switches stay up (-1, the EdgeTX boot
     * state), undriven pots centred.
     */
    public void toInputs(int[] positions, int[] switchesOut, int[] potsOut) {
        Arrays.fill(switchesOut, -1);
        Arrays.fill(potsOut, 0);
        for (int i = 0; i < SLOTS; i++) {
            SwitchSlot s = slots[i];
            if (s.output().analog) {
                potsOut[s.output().potSlot()] = s.analogValue(positions[i]);
            } else {
                switchesOut[s.output().firmwareIndex] = s.switchState(positions[i]);
            }
        }
    }

    public String encode() {
        StringBuilder sb = new StringBuilder(VERSION);
        for (SwitchSlot s : slots) {
            sb.append(';')
                    .append(s.label())
                    .append('|')
                    .append(s.type().key)
                    .append('|')
                    .append(s.positions())
                    .append('|')
                    .append(s.output().name());
        }
        return sb.toString();
    }

    /** Falls back to {@link #defaults()} on null / unknown / corrupt input. */
    public static SwitchConfig decode(String text) {
        if (text == null) {
            return defaults();
        }
        try {
            String[] parts = text.split(";", -1);
            if (parts.length != SLOTS + 1 || !VERSION.equals(parts[0])) {
                return defaults();
            }
            SwitchSlot[] slots = new SwitchSlot[SLOTS];
            for (int i = 0; i < SLOTS; i++) {
                String[] f = parts[i + 1].split("\\|", -1);
                if (f.length != 4) {
                    return defaults();
                }
                slots[i] =
                        new SwitchSlot(
                                f[0],
                                Type.fromKey(f[1]),
                                Integer.parseInt(f[2]),
                                Output.valueOf(f[3]));
            }
            return new SwitchConfig(slots);
        } catch (RuntimeException e) {
            return defaults();
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SwitchConfig other && Arrays.equals(slots, other.slots);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(slots);
    }
}
