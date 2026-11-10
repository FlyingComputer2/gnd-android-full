package com.flyingcomputer.gndfull.edgetx;

/**
 * TX16S trim-switch indices for {@code simuSetTrim}. Matches Companion {@code Board::TrimSwitches}
 * (LH, LV, RV, RH — each DEC then INC).
 */
public final class Tx16TrimMap {
    public static final int LH_DEC = 0;
    public static final int LH_INC = 1;
    public static final int LV_DEC = 2;
    public static final int LV_INC = 3;
    public static final int RV_DEC = 4;
    public static final int RV_INC = 5;
    public static final int RH_DEC = 6;
    public static final int RH_INC = 7;

    private Tx16TrimMap() {}
}
