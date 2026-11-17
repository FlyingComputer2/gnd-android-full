package com.flyingcomputer.gndfull.control;

import com.flyingcomputer.gndfull.edgetx.EdgeTxInputSnapshot;
import com.flyingcomputer.gndfull.edgetx.FakeEdgeTxAdapter;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public final class ControlSessionInputTest {
    private FakeEdgeTxAdapter adapter;
    private ControlSession session;

    @Before
    public void setUp() throws Exception {
        adapter = new FakeEdgeTxAdapter();
        session = new ControlSession(adapter);
        TimeUnit.MILLISECONDS.sleep(200);
    }

    @After
    public void tearDown() {
        session.close();
    }

    @Test
    public void reconnectResumesWithoutRearm() {
        session.onUsbConnected();
        assertEquals(DrivingState.READY, session.state());
        session.requestArm();
        assertEquals(DrivingState.ARMED, session.state());
        session.onUsbDisconnected();
        assertEquals(DrivingState.FAILSAFE, session.state());
        session.requestArm(); // nothing to send while USB is down
        assertEquals(DrivingState.FAILSAFE, session.state());
        session.onUsbConnected(); // arm switch still high: no cycle needed
        assertEquals(DrivingState.READY, session.state());
        session.requestArm();
        assertEquals(DrivingState.ARMED, session.state());
    }

    @Test
    public void reconnectWhileMenuOpenStaysInactive() {
        session.setMenuOpen(true);
        session.onUsbDisconnected();
        session.onUsbConnected();
        assertEquals(DrivingState.FAILSAFE, session.state());
        session.setMenuOpen(false);
        assertEquals(DrivingState.READY, session.state());
    }

    @Test
    public void submitsSticksDuringFailsafe() throws Exception {
        session.onUsbDisconnected();
        assertEquals(DrivingState.FAILSAFE, session.state());

        EdgeTxInputSnapshot moved =
                new EdgeTxInputSnapshot(
                        new int[] {512, -256, 0, 400}, new int[] {0, 0, 0, 0, 1, 0, 0, 0});
        session.publishInput(moved);
        TimeUnit.MILLISECONDS.sleep(100);

        assertEquals(moved.sticks()[0], adapter.lastInput().sticks()[0]);
        assertNotEquals(0, adapter.lastInput().sticks()[0]);
    }
}
