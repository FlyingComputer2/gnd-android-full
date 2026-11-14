package com.flyingcomputer.gndfull.cli;

import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * UDP text console client: one request per datagram, replies come back to this
 * socket. Owns one socket, a receive thread and a send executor; the listener is
 * called on the receive thread.
 */
public final class UdpConsole implements AutoCloseable {
    public interface Listener {
        void onReply(String text);

        void onError(String message);
    }

    private static final String TAG = "GndCli";
    // l3: at most 511 bytes per request; replies (listings) can be larger.
    private static final int MAX_DATAGRAM = 8192;

    private final ConsoleTarget target;
    private final Listener listener;
    private final ExecutorService sender;
    private volatile String host;
    private volatile DatagramSocket socket;
    private volatile boolean closed;
    private Thread receiver;

    public UdpConsole(ConsoleTarget target, String host, Listener listener) {
        this.target = target;
        this.host = host;
        this.listener = listener;
        this.sender = Executors.newSingleThreadExecutor(r -> new Thread(r, "cli-tx-" + target.port));
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String endpoint() {
        return host + ":" + target.port;
    }

    /** Sends one command line; opens the socket on first use. */
    public void send(String line) {
        try {
            sender.execute(() -> sendNow(line));
        } catch (RejectedExecutionException ignored) {
            // closed
        }
    }

    private void sendNow(String line) {
        try {
            DatagramSocket s = ensureSocket();
            byte[] data = line.getBytes(StandardCharsets.UTF_8);
            s.send(new DatagramPacket(data, data.length, InetAddress.getByName(host), target.port));
        } catch (IOException e) {
            if (!closed) {
                listener.onError("send " + endpoint() + ": " + e.getMessage());
            }
        }
    }

    private synchronized DatagramSocket ensureSocket() throws SocketException {
        if (closed) {
            throw new SocketException("closed");
        }
        if (socket == null) {
            socket = new DatagramSocket();
            DatagramSocket s = socket;
            receiver = new Thread(() -> receiveLoop(s), "cli-rx-" + target.port);
            receiver.setDaemon(true);
            receiver.start();
        }
        return socket;
    }

    private void receiveLoop(DatagramSocket s) {
        byte[] buf = new byte[MAX_DATAGRAM];
        while (!closed && !s.isClosed()) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                s.receive(p);
            } catch (IOException e) {
                if (!closed) {
                    Log.w(TAG, target.label + " receive: " + e.getMessage());
                }
                return;
            }
            listener.onReply(new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8));
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        sender.shutdownNow();
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }
}
