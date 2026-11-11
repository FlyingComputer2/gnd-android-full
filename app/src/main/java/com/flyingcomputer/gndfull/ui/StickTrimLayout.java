package com.flyingcomputer.gndfull.ui;

/**
 * Largest 1:1 gimbal that fits the cell, with slim trim rockers overlaid inside:
 * ◀▶ on top, ▲▼ on the inner edge (between the two sticks).
 */
public final class StickTrimLayout {
    public static final class Box {
        public int left;
        public int top;
        public int right;
        public int bottom;

        public int width() {
            return right - left;
        }

        public int height() {
            return bottom - top;
        }
    }

    private StickTrimLayout() {}

    /**
     * @param leftStick left gimbal: vertical trim on the right (inner) edge;
     *     right gimbal: vertical trim on the left (inner) edge.
     */
    public static void place(
            int width,
            int height,
            int trimPx,
            int insetPx,
            boolean leftStick,
            Box gimbal,
            Box hTrim,
            Box vTrim) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        int slim = Math.max(1, trimPx);
        int inset = Math.max(0, insetPx);
        int size = Math.min(w, h);
        int ox = (w - size) / 2;
        int oy = (h - size) / 2;
        gimbal.left = ox;
        gimbal.top = oy;
        gimbal.right = ox + size;
        gimbal.bottom = oy + size;

        int hLen = Math.max(slim * 4, size * 3 / 10);
        hLen = Math.min(hLen, Math.max(slim, size - inset * 2));
        hTrim.left = ox + (size - hLen) / 2;
        hTrim.top = oy + inset;
        hTrim.right = hTrim.left + hLen;
        hTrim.bottom = hTrim.top + slim;

        int vLen = Math.max(slim * 4, size * 3 / 10);
        vLen = Math.min(vLen, Math.max(slim, size - inset * 2));
        int vTop = oy + (size - vLen) / 2;
        vTrim.top = vTop;
        vTrim.bottom = vTop + vLen;
        if (leftStick) {
            vTrim.right = gimbal.right - inset;
            vTrim.left = vTrim.right - slim;
        } else {
            vTrim.left = gimbal.left + inset;
            vTrim.right = vTrim.left + slim;
        }
    }
}
