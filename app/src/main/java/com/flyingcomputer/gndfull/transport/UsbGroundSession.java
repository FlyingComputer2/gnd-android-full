package com.flyingcomputer.gndfull.transport;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.view.Surface;
import com.flyingcomputer.gndfull.UsbProxy;
import com.flyingcomputer.gndfull.VideoReceiver;
import com.flyingcomputer.gndfull.control.ControlSession;
import com.flyingcomputer.gndfull.crsf.RoverCrossfireTelemetry;
import com.flyingcomputer.gndfull.transport.rover.GroundLinkFrame;
import com.flyingcomputer.gndfull.transport.rover.LinkRate;
import com.flyingcomputer.gndfull.transport.rover.RoverTelemFrame;
import com.flyingcomputer.gndfull.video.VideoMetrics;

/** One USB connection: video ch0 + RC ch2 + sync ch3 + CLI ch4 (optional) + metrics. */
public final class UsbGroundSession implements AutoCloseable {
    public interface Listener {
        void onStatus(String message);

        void onFailure(Exception error);

        void onTelemetry(RoverTelemFrame telem);

        void onVideoMetrics(VideoMetrics.Snapshot metrics);
    }

    private final ControlSession control;
    private final Listener listener;
    private final boolean enableRc;
    private UsbProxy proxy;
    private VideoReceiver video;
    private final VideoMetrics videoMetrics = new VideoMetrics();
    /** Rover → ground air link, from the ground's link frames (receiver side). */
    private final LinkRate rovTx = new LinkRate();
    /** Ground → rover air link, from the rover's telemetry (receiver side). */
    private final LinkRate gndTx = new LinkRate();
    private volatile GroundLinkFrame lastGroundLink;
    private volatile long lastGroundLinkNanos;
    private volatile RoverTelemFrame lastTelem;
    private volatile long lastTelemNanos;
    /** Older than this, telemetry and ground link frames are not shown. */
    private static final long FRESH_NANOS = 2_500_000_000L;
    /**
     * krono rtt client on USB channel 3 (the ground P4's clock, which the rover follows). One per
     * connection: close() ends its thread, and MainActivity closes the session before reconnecting.
     */
    private SynchClient clock;
    private final RcUsbHandler rcUsb = new RcUsbHandler();
    /** The ground's text consoles on USB channel 4; lives across connections. */
    private final CliUsbHandler cliUsb = new CliUsbHandler();
    private RoverControlBridge bridge;

    public UsbGroundSession(ControlSession control, Listener listener, boolean enableRc) {
        this.control = control;
        this.listener = listener;
        this.enableRc = enableRc;
        rcUsb.setListener(
                new RcUsbHandler.Listener() {
                    @Override
                    public void onTelemetry(RoverTelemFrame telem) {
                        // As CRSF telemetry from EdgeTX's external module: link stats
                        // (gnd->rov RSSI, LQ), battery (VBUS), attitude, flight mode.
                        byte[] crsf = RoverCrossfireTelemetry.frames(telem);
                        if (crsf != null) {
                            control.pushExternalModuleRx(crsf);
                        }
                        lastTelem = telem;
                        lastTelemNanos = System.nanoTime();
                        gndTx.add(
                                telem.linkMs,
                                telem.linkRxBytes,
                                telem.linkRxPackets,
                                telem.linkRxLost,
                                System.nanoTime());
                        if (listener != null) {
                            listener.onTelemetry(telem);
                        }
                    }

                    @Override
                    public void onGroundLink(GroundLinkFrame link) {
                        lastGroundLink = link;
                        lastGroundLinkNanos = System.nanoTime();
                        rovTx.add(
                                link.linkMs,
                                link.rxBytes,
                                link.rxPackets,
                                link.rxLost,
                                System.nanoTime());
                    }
                });
    }

    public synchronized void connect(UsbManager manager, UsbDevice device, Surface surface)
            throws Exception {
        closeInternals();
        videoMetrics.reset();
        rovTx.clear();
        gndTx.clear();
        lastGroundLink = null;
        lastTelem = null;
        // Before the video receiver: it maps capture stamps onto the ground clock.
        clock = new SynchClient();
        video = new VideoReceiver(surface, videoMetrics, clock);
        proxy =
                new UsbProxy(
                        error -> {
                            if (listener != null) {
                                listener.onFailure(error);
                            }
                        });
        UsbProxy.Channel videoChannel = proxy.registerHandler(0, video);
        UsbProxy.Channel syncChannel = proxy.registerHandler(SynchClient.CHANNEL, clock);
        UsbProxy.Channel cliChannel =
                proxy.registerOptionalHandler(CliUsbHandler.CHANNEL, cliUsb.sessionHandler());
        // Handlers keep their own bridge: closeInternals() clears the field before the proxy's
        // channel workers deliver onClose.
        final RoverControlBridge rcBridge =
                enableRc ? new RoverControlBridge(control, rcUsb, clock) : null;
        bridge = rcBridge;
        if (!enableRc) {
            proxy.connect(manager, device);
            videoChannel.open(8);
            syncChannel.open(2);
            openCli(cliChannel);
            control.onUsbConnected();
            if (listener != null) {
                listener.onStatus("Video channel 0 open (EdgeTX-only mode)");
            }
            return;
        }
        UsbProxy.Handler rcHandler =
                new UsbProxy.Handler() {
                    @Override
                    public String format() {
                        return rcUsb.format();
                    }

                    @Override
                    public void onOpen(UsbProxy.Channel sender) {
                        rcUsb.onOpen(sender);
                        rcBridge.onRcChannelOpen();
                        if (listener != null) {
                            listener.onStatus("RC channel 2 open (rc-v1)");
                        }
                    }

                    @Override
                    public void onPacket(UsbProxy.Lease packet, UsbProxy.Channel sender) {
                        rcUsb.onPacket(packet, sender);
                    }

                    @Override
                    public void onClose() {
                        rcUsb.onClose();
                        rcBridge.onRcChannelClose();
                    }
                };
        UsbProxy.Channel rcChannel = proxy.registerHandler(2, rcHandler);
        proxy.connect(manager, device);
        videoChannel.open(8);
        rcChannel.open(8);
        syncChannel.open(2);
        openCli(cliChannel);
        control.onUsbConnected();
        if (listener != null) {
            listener.onStatus("Video channel 0 open (rtp-h264-v1)");
            listener.onVideoMetrics(videoMetrics.snapshot());
        }
    }

    private void openCli(UsbProxy.Channel cliChannel) throws Exception {
        if (cliChannel.available()) {
            cliChannel.open(4);
        } else if (listener != null) {
            listener.onStatus("Ground has no USB CLI channel 4; update gnd-esp32p4");
        }
    }

    /** The ground consoles over USB channel 4 (open while connected to a ground that has it). */
    public CliUsbHandler cliUsb() {
        return cliUsb;
    }

    public VideoMetrics videoMetrics() {
        return videoMetrics;
    }

    /** Rover → ground air link (measured by the ground). */
    public LinkRate rovTx() {
        return rovTx;
    }

    /** Ground → rover air link (measured by the rover). */
    public LinkRate gndTx() {
        return gndTx;
    }

    /** Newest ground link frame (its RSSI = the rover's frames at the ground C6), null if stale. */
    public GroundLinkFrame groundLink(long nowNanos) {
        GroundLinkFrame f = lastGroundLink;
        return f != null && nowNanos - lastGroundLinkNanos < FRESH_NANOS ? f : null;
    }

    /** Newest rover telemetry, null if stale (radio link down) or none yet. */
    public RoverTelemFrame telemetry(long nowNanos) {
        RoverTelemFrame t = lastTelem;
        return t != null && nowNanos - lastTelemNanos < FRESH_NANOS ? t : null;
    }

    /** Ground-clock time source (USB channel 3), or null when not connected. */
    public SynchClient clock() {
        return clock;
    }

    public RoverControlBridge controlBridge() {
        return bridge;
    }

    public synchronized void disconnect() {
        closeInternals();
        control.onUsbDisconnected();
    }

    @Override
    public synchronized void close() {
        disconnect();
    }

    private void closeInternals() {
        if (bridge != null) {
            bridge.close();
            bridge = null;
        }
        if (proxy != null) {
            proxy.close();
            proxy = null;
        }
        if (video != null) {
            video.close();
            video = null;
        }
        if (clock != null) {
            clock.close();
            clock = null;
        }
    }
}
