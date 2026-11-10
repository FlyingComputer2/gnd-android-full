package com.flyingcomputer.gndfull.edgetx;

/** TX16S key indices; matches {@code EnumKeys} in EdgeTX {@code hal/key_driver.h}. */
public final class Tx16KeyMap {
    public static final int KEY_EXIT = 1;
    public static final int KEY_ENTER = 2;
    public static final int KEY_PAGEUP = 3;
    public static final int KEY_TELE = 12;

    public static int toFirmware(EdgeTxKey key) {
        return switch (key) {
            case RTN -> KEY_EXIT;
            case ENTER -> KEY_ENTER;
            case TELE -> KEY_TELE;
            case PAGE_UP -> KEY_PAGEUP;
        };
    }

    private Tx16KeyMap() {}
}
