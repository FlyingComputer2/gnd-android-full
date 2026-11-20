package com.flyingcomputer.gndfull.edgetx;

/** RadioMaster TX16S stick/switch indices (hw_defs/tx16s.json). */
public final class Tx16InputMap {
    public static final int ANALOG_LH = 0;
    public static final int ANALOG_LV = 1;
    public static final int ANALOG_RV = 2;
    public static final int ANALOG_RH = 3;

    /** Pots / sliders S1 (P1), S2 (P3), LS (SL1), RS (SL2); P2 is the 6POS. */
    public static final int ANALOG_S1 = 4;
    public static final int ANALOG_S2 = 6;
    public static final int ANALOG_LS = 7;
    public static final int ANALOG_RS = 8;

    /** {@code EdgeTxInputSnapshot.pots()} order → analog index. */
    public static final int[] POT_ANALOGS = new int[] {ANALOG_S1, ANALOG_S2, ANALOG_LS, ANALOG_RS};

    /**
     * UI stick -1024..1024 (right / up positive) → EdgeTX host units, all four axes alike. The
     * {@code inverted} flags on LV / RH in tx16s.json are applied by the STM32 ADC driver only;
     * the simulator reads simuGetAnalog as is, so flipping them here reversed both axes.
     */
    public static int stickToEdgeTxPerMille(int norm) {
        return Math.max(-1024, Math.min(1024, norm));
    }

    /**
     * Switch state → {@code simuSetSwitch}, clamped to -1 (up) / 0 (mid) / 1 (down). Slot types
     * and position counts are resolved by {@code SwitchConfig}; a 3-position state on a 2POS
     * switch (SF, SH) needs that switch set to 3POS in EdgeTX hardware settings.
     */
    public static int switchToFirmware(int state) {
        return Math.max(-1, Math.min(1, state));
    }

    private Tx16InputMap() {}
}
