package com.flyingcomputer.gndfull.edgetx;

/** RadioMaster TX16S stick/switch indices (hw_defs/tx16s.json). */
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

    public static final int[] UPPER_SWITCH_INDICES =
            new int[] {SWITCH_SE, SWITCH_SF, SWITCH_SG, SWITCH_SH};

    public static final int[] LOWER_SWITCH_INDICES =
            new int[] {SWITCH_SD, SWITCH_SC, SWITCH_SB, SWITCH_SA};

    public static boolean isTwoPositionSwitch(int firmwareIndex) {
        return firmwareIndex == SWITCH_SF || firmwareIndex == SWITCH_SH;
    }

    public static boolean isTwoPositionLabel(String label) {
        return "SF".equals(label) || "SH".equals(label);
    }

    /** UI stick -1024..1024 → EdgeTX host units (Companion convention). */
    public static int stickToEdgeTxPerMille(int norm) {
        return Math.max(-1024, Math.min(1024, norm));
    }

    /** LV and RH are inverted in tx16s.json ADC inputs. */
    public static int stickY(int normY, int analogIndex) {
        int v = stickToEdgeTxPerMille(normY);
        if (analogIndex == ANALOG_LV || analogIndex == ANALOG_RH) {
            v = -v;
        }
        return v;
    }

    /** UI switch state → {@code simuSetSwitch} (-1 / 0 / 1). */
    public static int switchToFirmware(int firmwareIndex, int uiState) {
        if (isTwoPositionSwitch(firmwareIndex)) {
            return uiState > 0 ? 1 : -1;
        }
        if (uiState < -1) {
            return -1;
        }
        if (uiState > 1) {
            return 1;
        }
        return uiState;
    }

    private Tx16InputMap() {}
}
