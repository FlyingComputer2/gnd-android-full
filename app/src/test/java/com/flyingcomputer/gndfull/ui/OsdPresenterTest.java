package com.flyingcomputer.gndfull.ui;

import static org.junit.Assert.assertEquals;

import com.flyingcomputer.gndfull.transport.rover.LinkRate;
import com.flyingcomputer.gndfull.video.VideoMetrics;
import org.junit.Test;

public final class OsdPresenterTest {
    @Test
    public void fourLines() {
        String s =
                OsdPresenter.format(
                        new OsdPresenter.Input(
                                -52,
                                new LinkRate.Result(6.12345, 0.84),
                                -48,
                                new LinkRate.Result(0.0412, 1.5),
                                new VideoMetrics.Window(29.84, 73.6, 2.14, 0.34),
                                8,
                                10));
        assertEquals(
                "ROVTX: -52 dbm 6.123 mbps 0.8%\n"
                        + "GNDTX: -48 dbm 0.041 mbps 1.5%\n"
                        + "VIDEO: 29.8 fps 2.1 ms 74 ms 0.3%\n"
                        + "RC: 8 ms 1.0%",
                s);
    }

    @Test
    public void unknownValues() {
        String s = OsdPresenter.format(new OsdPresenter.Input(0, null, 0, null, null, -1, -1));
        assertEquals(
                "ROVTX: --- dbm --- mbps ---%\n"
                        + "GNDTX: --- dbm --- mbps ---%\n"
                        + "VIDEO: --- fps --- ms --- ms ---%\n"
                        + "RC: --- ms ---%",
                s);
    }
}
