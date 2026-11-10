package com.flyingcomputer.gndfull.edgetx;

/** TX16S key indices; matches Companion {@code EnumKeys} / {@code hal/key_driver.h}. */
public final class Tx16KeyMap {
    public static final int KEY_ENTER = 2;
    public static final int KEY_PAGEUP = 3;
    public static final int KEY_PAGEDN = 4;
    public static final int KEY_UP = 5;
    public static final int KEY_DOWN = 6;
    public static final int KEY_LEFT = 7;
    public static final int KEY_RIGHT = 8;
    public static final int KEY_MODEL = 11;
    public static final int KEY_SYS = 13;

    public static int toFirmware(EdgeTxKey key) {
        return switch (key) {
            case SYS -> KEY_SYS;
            case MDL -> KEY_MODEL;
            case UP -> KEY_UP;
            case DOWN -> KEY_DOWN;
            case LEFT -> KEY_LEFT;
            case RIGHT -> KEY_RIGHT;
            case ENTER -> KEY_ENTER;
        };
    }

    private Tx16KeyMap() {}
}
