package com.flyingcomputer.gndfull.ui;

/**
 * Full-cell gimbal with slim trim rockers overlaid inside: ◀▶ on top, ▲▼ on the
 * inner edge (between the two sticks).
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
        gimbal.left = 0;
        gimbal.top = 0;
        gimbal.right = w;
        gimbal.bottom = h;

        int hLen = Math.max(slim * 4, w * 3 / 10);
        hLen = Math.min(hLen, Math.max(slim, w - inset * 2));
        hTrim.left = (w - hLen) / 2;
        hTrim.top = inset;
        hTrim.right = hTrim.left + hLen;
        hTrim.bottom = inset + slim;

        int vLen = Math.max(slim * 4, h * 3 / 10);
        vLen = Math.min(vLen, Math.max(slim, h - inset * 2));
        int vTop = (h - vLen) / 2;
        vTrim.top = vTop;
        vTrim.bottom = vTop + vLen;
        if (leftStick) {
            vTrim.right = w - inset;
            vTrim.left = vTrim.right - slim;
        } else {
            vTrim.left = inset;
            vTrim.right = inset + slim;
        }
    }
}
