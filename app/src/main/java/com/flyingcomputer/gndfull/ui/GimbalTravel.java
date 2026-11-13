package com.flyingcomputer.gndfull.ui;

/**
 * Square gimbal throw. A 45° corner reaches max X and max Y (not a circular gate).
 */
public final class GimbalTravel {
    public static final int MAX_PER_MILLE = 1024;

    private GimbalTravel() {}

    /**
     * Clamps {@code (dx, dy)} to the axis-aligned box of half-sizes {@code rx}, {@code ry}.
     * Writes the clamped vector into {@code out} as {@code [dx, dy]}.
     */
    public static void clamp(float dx, float dy, float rx, float ry, float[] out) {
        float radiusX = Math.max(1f, rx);
        float radiusY = Math.max(1f, ry);
        out[0] = Math.max(-radiusX, Math.min(radiusX, dx));
        out[1] = Math.max(-radiusY, Math.min(radiusY, dy));
    }

    /** Maps a clamped axis offset to EdgeTX ±1024. */
    public static int toPerMille(float d, float radius) {
        return Math.round((d / Math.max(1f, radius)) * MAX_PER_MILLE);
    }
}
