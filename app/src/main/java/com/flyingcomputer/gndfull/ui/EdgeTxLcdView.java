package com.flyingcomputer.gndfull.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import com.flyingcomputer.gndfull.edgetx.EdgeTxAdapter;

/** Displays the latest EdgeTX RGB565 framebuffer with letterboxing. */
public final class EdgeTxLcdView extends View {
    private final Paint background = new Paint();
    private final Rect dst = new Rect();
    private EdgeTxAdapter adapter;
    private Bitmap frame;

    public EdgeTxLcdView(Context context, AttributeSet attrs) {
        super(context, attrs);
        background.setColor(0xffe8f0e8);
    }

    public void bind(EdgeTxAdapter adapter) {
        this.adapter = adapter;
    }

    public void refreshFrame() {
        if (adapter == null) {
            return;
        }
        EdgeTxAdapter.LcdMetadata meta = adapter.lcdMetadata();
        byte[] rgb565 = adapter.copyLcdFrameRgb565();
        if (frame == null || frame.getWidth() != meta.width() || frame.getHeight() != meta.height()) {
            frame = Bitmap.createBitmap(meta.width(), meta.height(), Bitmap.Config.RGB_565);
        }
        frame.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(rgb565));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), background);
        if (frame == null) {
            return;
        }
        float scale = Math.min((float) getWidth() / frame.getWidth(),
                (float) getHeight() / frame.getHeight());
        int dw = Math.round(frame.getWidth() * scale);
        int dh = Math.round(frame.getHeight() * scale);
        int left = (getWidth() - dw) / 2;
        int top = (getHeight() - dh) / 2;
        dst.set(left, top, left + dw, top + dh);
        canvas.drawBitmap(frame, null, dst, null);
    }
}
