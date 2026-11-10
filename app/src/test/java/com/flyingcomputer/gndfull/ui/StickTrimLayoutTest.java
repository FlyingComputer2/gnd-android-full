package com.flyingcomputer.gndfull.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StickTrimLayoutTest {
    @Test
    public void gimbalFillsTheCell() {
        StickTrimLayout.Box gimbal = new StickTrimLayout.Box();
        StickTrimLayout.Box h = new StickTrimLayout.Box();
        StickTrimLayout.Box v = new StickTrimLayout.Box();
        StickTrimLayout.place(220, 160, 12, 8, true, gimbal, h, v);

        assertEquals(0, gimbal.left);
        assertEquals(0, gimbal.top);
        assertEquals(220, gimbal.right);
        assertEquals(160, gimbal.bottom);
    }

    @Test
    public void leftStick_slimVerticalTrimIsInsideOnTheRight() {
        StickTrimLayout.Box gimbal = new StickTrimLayout.Box();
        StickTrimLayout.Box h = new StickTrimLayout.Box();
        StickTrimLayout.Box v = new StickTrimLayout.Box();
        StickTrimLayout.place(220, 160, 12, 8, true, gimbal, h, v);

        assertEquals(12, v.width());
        assertTrue(v.left > gimbal.width() / 2);
        assertEquals(220 - 8, v.right);
        assertTrue(v.top >= gimbal.top);
        assertTrue(v.bottom <= gimbal.bottom);
        assertTrue(v.height() < gimbal.height());
        assertTrue(h.height() == 12);
        assertEquals(8, h.top);
        assertTrue(h.width() < gimbal.width());
        assertTrue(h.left > gimbal.left);
        assertTrue(h.right < gimbal.right);
    }

    @Test
    public void rightStick_slimVerticalTrimIsInsideOnTheLeft() {
        StickTrimLayout.Box gimbal = new StickTrimLayout.Box();
        StickTrimLayout.Box h = new StickTrimLayout.Box();
        StickTrimLayout.Box v = new StickTrimLayout.Box();
        StickTrimLayout.place(220, 160, 12, 8, false, gimbal, h, v);

        assertEquals(8, v.left);
        assertEquals(12, v.width());
        assertTrue(v.right < gimbal.width() / 2);
        assertTrue(h.top >= gimbal.top);
        assertTrue(h.bottom < gimbal.bottom);
    }
}
