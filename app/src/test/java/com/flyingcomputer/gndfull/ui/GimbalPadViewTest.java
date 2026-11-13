package com.flyingcomputer.gndfull.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class GimbalPadViewTest {
    @Test
    public void heldThrottleSnapsToCentreOnlyNearIt() {
        assertEquals(0, GimbalPadView.snapHeld(39));
        assertEquals(0, GimbalPadView.snapHeld(-39));
        assertEquals(40, GimbalPadView.snapHeld(40));
        assertEquals(-700, GimbalPadView.snapHeld(-700));
    }
}
