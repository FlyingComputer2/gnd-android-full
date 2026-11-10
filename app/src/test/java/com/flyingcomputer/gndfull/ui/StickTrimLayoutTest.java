package com.flyingcomputer.gndfull.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StickTrimLayoutTest {
    @Test
    public void leftStick_verticalTrimIsOnInnerRight() {
        StickTrimLayout.Box gimbal = new StickTrimLayout.Box();
        StickTrimLayout.Box h = new StickTrimLayout.Box();
        StickTrimLayout.Box v = new StickTrimLayout.Box();
        StickTrimLayout.place(0, 0, 200, 20, true, gimbal, h, v);

        assertEquals(0, h.top);
        assertEquals(20, h.bottom);
        assertEquals(180, v.left);
        assertEquals(200, v.right);
        assertTrue(v.left >= gimbal.right - 1);
        assertEquals(0, gimbal.left);
        assertEquals(20, gimbal.top);
    }

    @Test
    public void rightStick_verticalTrimIsOnInnerLeft() {
        StickTrimLayout.Box gimbal = new StickTrimLayout.Box();
        StickTrimLayout.Box h = new StickTrimLayout.Box();
        StickTrimLayout.Box v = new StickTrimLayout.Box();
        StickTrimLayout.place(0, 0, 200, 20, false, gimbal, h, v);

        assertEquals(0, v.left);
        assertEquals(20, v.right);
        assertEquals(20, gimbal.left);
        assertEquals(200, gimbal.right);
        assertTrue(v.right <= gimbal.left + 1);
        assertEquals(0, h.top);
        assertEquals(20, h.left);
    }
}
