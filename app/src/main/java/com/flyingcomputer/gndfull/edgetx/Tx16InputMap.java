package com.flyingcomputer.gndfull.edgetx;

/** RadioMaster TX16S stick/switch indices (radios.json / wasm sim). */
public final class Tx16InputMap {
    public static final int ANALOG_LH = 0;
    public static final int ANALOG_LV = 1;
    public static final int ANALOG_RV = 2;
    public static final int ANALOG_RH = 3;

    public static final int SWITCH_SA = 0;
    public static final int SWITCH_SB = 1;
    public static final int SWITCH_SC = 2;
    public static final int SWITCH_SD = 3;
    public static final int SWITCH_SE = 4;
    public static final int SWITCH_SF = 5;
    public static final int SWITCH_SG = 6;
    public static final int SWITCH_SH = 7;

    /** UI upper row SE..SH → firmware indices. */
    public static final int[] UPPER_SWITCH_INDICES =
            new int[] {SWITCH_SE, SWITCH_SF, SWITCH_SG, SWITCH_SH};

    /** UI lower row SD..SA labels left-to-right map to SA..SD in sketch order. */
    public static final int[] LOWER_SWITCH_INDICES =
            new int[] {SWITCH_SD, SWITCH_SC, SWITCH_SB, SWITCH_SA};

    /** 2-position (SF only on TX16S upper row). */
    public static boolean isTwoPosition(String label) {
        return "SF".equals(label);
    }

    public static int stickToEdgeTxPerMille(int norm) {
        return Math.max(-1024, Math.min(1024, norm));
    }

    private Tx16InputMap() {}
}
