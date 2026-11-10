package com.flyingcomputer.gndfull.ui;

import com.flyingcomputer.gndfull.control.DrivingState;
import com.flyingcomputer.gndfull.transport.rover.RoverTelemFrame;
import com.flyingcomputer.gndfull.video.VideoMetrics;

/** Builds OSD text from labeled measurement points. */
public final class OsdPresenter {
    private OsdPresenter() {}

    public static String format(
            boolean usbLinked,
            VideoMetrics.Snapshot video,
            RoverTelemFrame telem,
            DrivingState driving,
            int rcSeq) {
        StringBuilder sb = new StringBuilder();
        sb.append(usbLinked ? "LINK: USB" : "LINK: down");
        if (video != null) {
            sb.append("  RTP=").append(video.rtpPackets());
            sb.append("  AU=").append(video.accessUnits());
            if (video.lastRtpAgeMs() >= 0) {
                sb.append("  rtp_age_ms=").append(video.lastRtpAgeMs());
            }
        }
        sb.append("  ctl=").append(driving);
        sb.append("  rc_seq=").append(rcSeq);
        if (telem != null) {
            sb.append("  rover=").append(telem.stateLabel());
            sb.append("  rc_age_ms=").append(telem.rcAgeMs);
            sb.append("  rc_loss=").append(telem.rcLossPerMille / 10.0).append('%');
            sb.append("  rc_lat_ms=").append(telem.rcLatMs);
            sb.append(" (USB telem)");
        }
        return sb.toString();
    }
}
