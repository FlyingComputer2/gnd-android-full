package com.flyingcomputer.gndfull.ui;

/**
 * Places TX16S-style trims on a square gimbal: horizontal rocker on top, vertical
 * rocker on the inner edge (between the two sticks).
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
     * @param leftStick left gimbal gets the vertical trim on its right (inner) edge;
     *     right gimbal gets it on the left (inner) edge.
     */
    public static void place(
            int originX,
            int originY,
            int size,
            int trimPx,
            boolean leftStick,
            Box gimbal,
            Box hTrim,
            Box vTrim) {
        int inner = Math.max(1, trimPx);
        int side = Math.max(inner * 3, size);
        int right = originX + side;
        int bottom = originY + side;

        hTrim.left = originX + (leftStick ? 0 : inner);
        hTrim.top = originY;
        hTrim.right = right - (leftStick ? inner : 0);
        hTrim.bottom = originY + inner;

        if (leftStick) {
            vTrim.left = right - inner;
            vTrim.top = originY + inner;
            vTrim.right = right;
            vTrim.bottom = bottom;
        } else {
            vTrim.left = originX;
            vTrim.top = originY + inner;
            vTrim.right = originX + inner;
            vTrim.bottom = bottom;
        }

        gimbal.left = originX + (leftStick ? 0 : inner);
        gimbal.top = originY + inner;
        gimbal.right = right - (leftStick ? inner : 0);
        gimbal.bottom = bottom;
    }
}
