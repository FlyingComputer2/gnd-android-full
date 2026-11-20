package com.flyingcomputer.gndfull.transport;

import com.flyingcomputer.gndfull.cli.ScpConsole;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Binary datagrams to one port on the ground over the USB cli channel (4): the ground sends each
 * as one datagram to its 127.0.0.1:port and returns the replies tagged with that port. For udpfs,
 * port 2440 is the ground's relay to the rover's udpfs (L3 upstream 11).
 */
public final class CliUsbTransport implements ScpConsole.Link {
    /** Ground UDP 2440: the rover's udpfs. */
    public static final int UDPFS_PORT = 2440;
    private static final int MAX_QUEUED = 64;

    private final CliUsbHandler link;
    private final int port;
    private final LinkedBlockingQueue<byte[]> rx = new LinkedBlockingQueue<>(MAX_QUEUED);

    public CliUsbTransport(CliUsbHandler link, int port) {
        this.link = link;
        this.port = port;
        link.setDatagramListener(port, d -> rx.offer(d));
    }

    @Override
    public void drain() {
        rx.clear();
    }

    @Override
    public boolean isOpen() {
        return link.isOpen();
    }

    @Override
    public boolean send(byte[] datagram) {
        return link.sendBytes(port, datagram);
    }

    @Override
    public byte[] receive(long timeoutMs) throws InterruptedException {
        return rx.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void detach() {
        link.setDatagramListener(port, null);
    }
}
