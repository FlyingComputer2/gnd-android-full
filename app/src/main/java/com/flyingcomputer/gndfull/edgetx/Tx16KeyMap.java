package com.flyingcomputer.gndfull.edgetx;

/** TX16S key indices; matches {@code EnumKeys} in EdgeTX {@code hal/key_driver.h}. */
public final class Tx16KeyMap {
    public static final int KEY_EXIT = 1;
    public static final int KEY_ENTER = 2;
    public static final int KEY_PAGEUP = 3;
    public static final int KEY_PAGEDN = 4;
    public static final int KEY_MODEL = 11;
    public static final int KEY_TELE = 12;
    public static final int KEY_SYS = 13;

    public static int toFirmware(EdgeTxKey key) {
        return switch (key) {
            case SYS -> KEY_SYS;
            case MDL -> KEY_MODEL;
            case TELE -> KEY_TELE;
            case RTN -> KEY_EXIT;
            case ENTER -> KEY_ENTER;
            case PAGE_UP -> KEY_PAGEUP;
            case PAGE_DOWN -> KEY_PAGEDN;
        };
    }

    private Tx16KeyMap() {}
}
