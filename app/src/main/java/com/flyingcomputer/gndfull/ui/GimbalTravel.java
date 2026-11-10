package com.flyingcomputer.gndfull.ui;

/** Elliptical throw so a gimbal uses the full cell, not a centered square. */
public final class GimbalTravel {
    private GimbalTravel() {}

    /**
     * Clamps {@code (dx, dy)} to the ellipse of radii {@code rx}, {@code ry}.
     * Writes the clamped vector into {@code out} as {@code [dx, dy]}.
     */
    public static void clamp(float dx, float dy, float rx, float ry, float[] out) {
        float radiusX = Math.max(1f, rx);
        float radiusY = Math.max(1f, ry);
        float nx = dx / radiusX;
        float ny = dy / radiusY;
        float mag2 = nx * nx + ny * ny;
        if (mag2 > 1f) {
            float scale = (float) (1.0 / Math.sqrt(mag2));
            nx *= scale;
            ny *= scale;
        }
        out[0] = nx * radiusX;
        out[1] = ny * radiusY;
    }
}
