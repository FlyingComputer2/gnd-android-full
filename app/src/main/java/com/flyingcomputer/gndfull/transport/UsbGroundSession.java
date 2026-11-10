package com.flyingcomputer.gndfull.transport;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.view.Surface;
import com.flyingcomputer.gndfull.UsbProxy;
import com.flyingcomputer.gndfull.VideoReceiver;
import com.flyingcomputer.gndfull.control.ControlSession;
import com.flyingcomputer.gndfull.transport.rover.RoverTelemFrame;
import com.flyingcomputer.gndfull.video.VideoMetrics;

/** One USB connection: video ch0 + RC ch2 + metrics. */
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
    private final RcUsbHandler rcUsb = new RcUsbHandler();
    private RoverControlBridge bridge;

    public UsbGroundSession(ControlSession control, Listener listener, boolean enableRc) {
        this.control = control;
        this.listener = listener;
        this.enableRc = enableRc;
        rcUsb.setListener(
                telem -> {
                    if (listener != null) {
                        listener.onTelemetry(telem);
                    }
                });
    }

    public synchronized void connect(UsbManager manager, UsbDevice device, Surface surface)
            throws Exception {
        closeInternals();
        video = new VideoReceiver(surface, videoMetrics);
        proxy =
                new UsbProxy(
                        error -> {
                            if (listener != null) {
                                listener.onFailure(error);
                            }
                        });
        UsbProxy.Channel videoChannel = proxy.registerHandler(0, video);
        if (enableRc) {
            bridge = new RoverControlBridge(control, rcUsb);
        }
        if (!enableRc) {
            proxy.connect(manager, device);
            videoChannel.open(8);
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
                        bridge.onRcChannelOpen();
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
                        bridge.onRcChannelClose();
                    }
                };
        UsbProxy.Channel rcChannel = proxy.registerHandler(2, rcHandler);
        proxy.connect(manager, device);
        videoChannel.open(8);
        rcChannel.open(8);
        control.onUsbConnected();
        if (listener != null) {
            listener.onStatus("Video channel 0 open (rtp-h264-v1)");
            listener.onVideoMetrics(videoMetrics.snapshot());
        }
    }

    public VideoMetrics videoMetrics() {
        return videoMetrics;
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
    }
}
