package com.flyingcomputer.gndfull.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GimbalTravelTest {
    @Test
    public void insideEllipse_unchanged() {
        float[] out = new float[2];
        GimbalTravel.clamp(30f, 10f, 100f, 50f, out);
        assertEquals(30f, out[0], 0.01f);
        assertEquals(10f, out[1], 0.01f);
    }

    @Test
    public void outside_clampedToEllipse() {
        float[] out = new float[2];
        GimbalTravel.clamp(200f, 0f, 100f, 50f, out);
        assertEquals(100f, out[0], 0.01f);
        assertEquals(0f, out[1], 0.01f);
        GimbalTravel.clamp(0f, 200f, 100f, 50f, out);
        assertEquals(0f, out[0], 0.01f);
        assertEquals(50f, out[1], 0.01f);
    }
}
