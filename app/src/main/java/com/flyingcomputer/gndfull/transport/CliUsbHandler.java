package com.flyingcomputer.gndfull.transport;

import com.flyingcomputer.gndfull.UsbProxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * USB channel 4 {@code cli-v1}: the ground P4's text consoles over USB (the phone has no IP route
 * to the board). A request is [u16 port, big-endian][one console line]; the ground sends the line
 * as one datagram to its own 127.0.0.1:port (l3 2400, sys 5130, the rover relays 2410 / 5092 /
 * 5132, ...). Each reply datagram comes back as [u16 source port][text], split at the channel MTU.
 * Served by gnd-esp32p4 register_usb_cli(). Optional: a ground without it still connects.
 */
public final class CliUsbHandler {
    public static final int CHANNEL = 4;
    public static final String FORMAT = "cli-v1";

    public interface Listener {
        /** On the USB channel worker thread. */
        void onReply(int port, String text);
    }

    /** One decoded reply record. */
    public record Reply(int port, String text) {}

    private final AtomicReference<UsbProxy.Channel> channel = new AtomicReference<>();
    private volatile Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isOpen() {
        return channel.get() != null;
    }

    /** Sends one line to the ground's console on port; false if the channel is not open or full. */
    public boolean send(int port, String line) {
        UsbProxy.Channel c = channel.get();
        return c != null && c.trySend(encode(port, line));
    }

    public static byte[] encode(int port, String line) {
        if (port <= 0 || port > 0xffff) {
            throw new IllegalArgumentException("port");
        }
        byte[] text = line.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[2 + text.length];
        out[0] = (byte) (port >> 8);
        out[1] = (byte) port;
        System.arraycopy(text, 0, out, 2, text.length);
        return out;
    }

    /** Null for a record too short to carry a port. */
    public static Reply decode(byte[] payload) {
        if (payload == null || payload.length < 2) {
            return null;
        }
        int port = ((payload[0] & 0xff) << 8) | (payload[1] & 0xff);
        return new Reply(port, new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8));
    }

    /**
     * A handler for one USB session's channel. This object outlives sessions; a closing session
     * clears only its own channel, so a late onClose cannot detach the next session.
     */
    public UsbProxy.Handler sessionHandler() {
        return new UsbProxy.Handler() {
            private volatile UsbProxy.Channel mine;

            @Override
            public String format() {
                return FORMAT;
            }

            @Override
            public void onOpen(UsbProxy.Channel sender) {
                mine = sender;
                channel.set(sender);
            }

            @Override
            public void onPacket(UsbProxy.Lease packet, UsbProxy.Channel sender) {
                try {
                    Reply r = decode(packet.payload);
                    Listener l = listener;
                    if (r != null && l != null) {
                        l.onReply(r.port(), r.text());
                    }
                } finally {
                    packet.close();
                }
            }

            @Override
            public void onClose() {
                UsbProxy.Channel m = mine;
                if (m != null) {
                    channel.compareAndSet(m, null);
                }
            }
        };
    }
}
