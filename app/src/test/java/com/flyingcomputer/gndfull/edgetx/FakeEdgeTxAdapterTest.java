package com.flyingcomputer.gndfull.edgetx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class FakeEdgeTxAdapterTest {
    @Test
    public void mapsStickDeflectionToOutputs() throws Exception {
        FakeEdgeTxAdapter adapter = new FakeEdgeTxAdapter();
        adapter.start();
        adapter.submitInput(new EdgeTxInputSnapshot(new int[] {1024, 0, 0, 0}, new int[] {0, 0, 0, 0, 0, 0, 0, 0}));
        int[] out = adapter.copyMixedOutputs();
        assertEquals(1024, out[0]);
        assertTrue(adapter.copyLcdFrameRgb565().length > 0);
        adapter.close();
    }
}
