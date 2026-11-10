package com.flyingcomputer.gndfull.edgetx;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class Tx16TrimMapTest {
    @Test
    public void companionOrder_lhLvRvRh() {
        assertEquals(0, Tx16TrimMap.LH_DEC);
        assertEquals(1, Tx16TrimMap.LH_INC);
        assertEquals(2, Tx16TrimMap.LV_DEC);
        assertEquals(3, Tx16TrimMap.LV_INC);
        assertEquals(4, Tx16TrimMap.RV_DEC);
        assertEquals(5, Tx16TrimMap.RV_INC);
        assertEquals(6, Tx16TrimMap.RH_DEC);
        assertEquals(7, Tx16TrimMap.RH_INC);
    }
}
