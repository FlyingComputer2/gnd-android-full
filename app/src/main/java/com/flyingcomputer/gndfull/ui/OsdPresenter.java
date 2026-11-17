package com.flyingcomputer.gndfull.ui;

import com.flyingcomputer.gndfull.transport.rover.LinkRate;
import com.flyingcomputer.gndfull.video.VideoMetrics;
import java.util.Locale;

/**
 * OSD text, four lines:
 *
 * <pre>
 * ROVTX: -52 dbm 6.123 mbps 0.8%     rover → ground air link, as the ground receives it
 * GNDTX: -48 dbm 0.041 mbps 1.5%     ground → rover air link, as the rover receives it
 * VIDEO: 29.8 fps 2.1 ms 74 ms       decoded fps, RFC 3550 frame jitter, camera-to-decode latency
 * RC: 8 ms 1.0%                      phone → rover RC latency (p50) and loss, rover side
 * </pre>
 *
 * RSSI is the receiver's (dBm), rate the received air throughput, loss the air frames missing
 * from the upstream sequences, each over about the last second. {@code ---} = unknown or stale.
 */
public final class OsdPresenter {
    static final String NA = "---";

    private OsdPresenter() {}

    /** Inputs; null / 0 / negative fields are unknown. */
    public record Input(
            int rovTxRssiDbm,
            LinkRate.Result rovTx,
            int gndTxRssiDbm,
            LinkRate.Result gndTx,
            VideoMetrics.Window video,
            int rcLatencyMs,
            int rcLossPerMille) {}

    public static String format(Input in) {
        StringBuilder sb = new StringBuilder(128);
        link(sb, "ROVTX: ", in.rovTxRssiDbm(), in.rovTx());
        sb.append('\n');
        link(sb, "GNDTX: ", in.gndTxRssiDbm(), in.gndTx());
        sb.append('\n');
        VideoMetrics.Window v = in.video();
        sb.append("VIDEO: ")
                .append(v == null || v.fps() < 0 ? NA : fmt("%.1f", v.fps()))
                .append(" fps ")
                .append(v == null || v.jitterMs() < 0 ? NA : fmt("%.1f", v.jitterMs()))
                .append(" ms ")
                .append(v == null || v.latencyMs() < 0 ? NA : fmt("%.0f", v.latencyMs()))
                .append(" ms\n");
        sb.append("RC: ")
                .append(in.rcLatencyMs() < 0 ? NA : Integer.toString(in.rcLatencyMs()))
                .append(" ms ")
                .append(in.rcLossPerMille() < 0 ? NA : fmt("%.1f", in.rcLossPerMille() / 10.0))
                .append('%');
        return sb.toString();
    }

    private static void link(StringBuilder sb, String label, int rssiDbm, LinkRate.Result r) {
        sb.append(label)
                .append(rssiDbm == 0 ? NA : Integer.toString(rssiDbm))
                .append(" dbm ")
                .append(r == null ? NA : fmt("%.3f", r.mbps()))
                .append(" mbps ")
                .append(r == null ? NA : fmt("%.1f", r.lossPercent()))
                .append('%');
    }

    private static String fmt(String f, double v) {
        return String.format(Locale.ROOT, f, v);
    }
}
