package com.flyingcomputer.gndfull.transport;

import com.flyingcomputer.gndfull.UsbProxy;
import com.flyingcomputer.gndfull.transport.msp.MspV2;
import com.flyingcomputer.gndfull.transport.rover.RoverTelemFrame;

/** USB channel 2: bidirectional {@code rc-v1} MSP v2 frames. */
public final class RcUsbHandler implements UsbProxy.Handler {
    public interface Listener {
        void onTelemetry(RoverTelemFrame frame);
    }

    private volatile UsbProxy.Channel channel;
    private volatile Listener listener;
    private volatile RoverTelemFrame lastTelemetry;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public RoverTelemFrame lastTelemetry() {
        return lastTelemetry;
    }

    @Override
    public String format() {
        return "rc-v1";
    }

    @Override
    public void onOpen(UsbProxy.Channel sender) {
        channel = sender;
    }

    @Override
    public void onPacket(UsbProxy.Lease packet, UsbProxy.Channel sender) {
        try {
            MspV2.Frame frame = MspV2.decode(packet.payload);
            if (frame == null || frame.function() != RoverTelemFrame.FUNCTION) {
                return;
            }
            RoverTelemFrame telem = RoverTelemFrame.unpack(frame.payload());
            if (telem == null) {
                return;
            }
            lastTelemetry = telem;
            Listener l = listener;
            if (l != null) {
                l.onTelemetry(telem);
            }
        } finally {
            packet.close();
        }
    }

    @Override
    public void onClose() {
        channel = null;
        lastTelemetry = null;
    }

    public boolean sendMsp(byte[] frame) {
        UsbProxy.Channel c = channel;
        return c != null && c.trySend(frame);
    }
}
