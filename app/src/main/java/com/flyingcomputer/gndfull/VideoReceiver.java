package com.flyingcomputer.gndfull;

import android.media.*;
import android.os.Build;
import android.view.Surface;
import com.flyingcomputer.gndfull.transport.SynchClient;
import com.flyingcomputer.gndfull.video.VideoMetrics;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Recovered RTP/H.264 receiver -> Android H.264 decoder -> Surface.
 *
 * For the OSD it also hands each decoded frame's capture stamp to {@link VideoMetrics}: the
 * rover's packer stamps each frame's capture instant as CLOCK_REALTIME (RTP header extension id
 * 2), kept here by RTP timestamp until the AU is queued, then by PTS until the decoder outputs it
 * (ported from gnd-android 5ccf3b6).
 */
public final class VideoReceiver implements UsbProxy.Handler, AutoCloseable {
    static { System.loadLibrary("gndfull_receiver"); }
    private final MediaCodec decoder;
    private final Thread output;
    private volatile boolean running=true;
    private long nativeHandle;
    private boolean needKey=true;
    private long lastPts;
    private final VideoMetrics metrics;
    private final SynchClient clock;
    // Capture CLOCK_REALTIME ns by RTP timestamp, for the AUs being assembled (feed path only).
    private final LinkedHashMap<Long,Long> captureByRtpTs=new LinkedHashMap<>(64,0.75f,false) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,Long> e) { return size()>64; }
    };
    private long packetTs=-1, previousTs=-1;
    private boolean packetMarker;
    // Capture ns by the PTS the AU was queued with, until the decoder outputs it.
    private final ConcurrentHashMap<Long,Long> captureByPts=new ConcurrentHashMap<>();
    public VideoReceiver(Surface surface) throws Exception {
        this(surface, null, null);
    }
    public VideoReceiver(Surface surface, VideoMetrics metrics, SynchClient clock) throws Exception {
        this.metrics = metrics;
        this.clock = clock;
        MediaFormat format=MediaFormat.createVideoFormat("video/avc",1920,1080);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,4*1024*1024);
        if(Build.VERSION.SDK_INT>=30) format.setInteger(MediaFormat.KEY_LOW_LATENCY,1);
        decoder=MediaCodec.createDecoderByType("video/avc");
        decoder.configure(format,surface,null,0); decoder.start();
        nativeHandle=nativeCreate();
        if(nativeHandle==0) throw new IllegalStateException("native receiver allocation failed");
        output=new Thread(()->{
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(running) {
                try {
                    int index=decoder.dequeueOutputBuffer(info,10000);
                    if(index>=0) { onDecoded(info.presentationTimeUs); decoder.releaseOutputBuffer(index,true); }
                }
                catch(IllegalStateException e) { if(running) android.util.Log.e("RoverVideo","decoder output",e); break; }
            }
        },"video-display");
        output.start();
    }
    @Override public String format() { return "rtp-h264-v1"; }
    @Override public synchronized void onPacket(UsbProxy.Lease packet,UsbProxy.Channel sender) {
        try {
            if(metrics!=null) metrics.onRtpPacket();
            if(running && nativeHandle!=0) {
                noteRtp(packet.payload);
                nativeFeed(nativeHandle,packet.payload);
            }
        }
        finally { packet.close(); }
    }
    // RTP timestamp, marker and capture extension (one-byte header 0xBEDE, id 2, 8-byte BE ns).
    private void noteRtp(byte[] p) {
        if(p.length<12) return;
        if(metrics!=null) metrics.onRtpSequence(((p[2]&0xff)<<8)|(p[3]&0xff));
        long ts=((p[4]&0xffL)<<24)|((p[5]&0xffL)<<16)|((p[6]&0xffL)<<8)|(p[7]&0xffL);
        if(ts!=packetTs) { previousTs=packetTs; packetTs=ts; }
        packetMarker=(p[1]&0x80)!=0;
        int off=12+4*(p[0]&0x0f);
        if((p[0]&0x10)==0 || p.length<off+13) return;
        if((p[off]&0xff)!=0xbe || (p[off+1]&0xff)!=0xde) return;
        int id=(p[off+4]&0xff)>>4, len=(p[off+4]&0x0f)+1;
        if(id!=2 || len!=8) return;
        long ns=0;
        for(int i=0;i<8;i++) ns=(ns<<8)|(p[off+5+i]&0xff);
        if(ns>0) captureByRtpTs.put(ts,ns);
    }
    // The depayloader finishes an AU on its marker packet, or on the first packet of the next
    // AU when the marker was lost.
    private long completedAuTs() { return packetMarker ? packetTs : previousTs; }
    // Decoder output thread.
    private void onDecoded(long pts) {
        if(metrics==null) return;
        Long capture=captureByPts.remove(pts);
        long ground=clock==null ? Long.MIN_VALUE : clock.serverNowUs();
        metrics.onFrameDecoded(capture==null ? 0 : capture,System.nanoTime(),ground);
    }
    // Called on the serialized native feed path. Never on the USB reader.
    private void onAccessUnit(byte[] annexB,long pts,boolean key) {
        Long capture=captureByRtpTs.remove(completedAuTs());
        if(needKey && !key) return;
        int index=decoder.dequeueInputBuffer(10000);
        if(index<0) { needKey=true; return; }
        ByteBuffer input=decoder.getInputBuffer(index);
        if(input==null || input.capacity()<annexB.length) {
            decoder.queueInputBuffer(index,0,0,0,0); needKey=true; return;
        }
        input.clear(); input.put(annexB);
        lastPts=Math.max(lastPts+1,pts);
        if(capture!=null) {
            if(captureByPts.size()>64) captureByPts.clear(); // decoder dropped frames
            captureByPts.put(lastPts,capture);
        }
        decoder.queueInputBuffer(index,0,annexB.length,lastPts,0); needKey=false;
        if(metrics!=null) metrics.onAccessUnit(key);
    }
    @Override public void close() {
        synchronized(this) {
            if(!running) return; running=false;
            if(nativeHandle!=0) { nativeDestroy(nativeHandle); nativeHandle=0; }
        }
        try { output.join(1000); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        decoder.stop(); decoder.release();
    }
    private native long nativeCreate();
    private native void nativeFeed(long handle,byte[] bytes);
    private native void nativeDestroy(long handle);
}
