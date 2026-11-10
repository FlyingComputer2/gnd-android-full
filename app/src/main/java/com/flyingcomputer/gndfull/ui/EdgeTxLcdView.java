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
    public interface TouchMapper {
        void onLcdTouch(int lcdX, int lcdY, boolean up);
    }

    private final Paint background = new Paint();
    private final Rect dst = new Rect();
    private EdgeTxAdapter adapter;
    private Bitmap frame;
    private boolean radioPowered = true;
    private final Paint powerOff = new Paint();
    private TouchMapper touchMapper;

    public EdgeTxLcdView(Context context, AttributeSet attrs) {
        super(context, attrs);
        background.setColor(0xffe8f0e8);
        powerOff.setColor(0xcc000000);
    }

    public void setRadioPowered(boolean on) {
        radioPowered = on;
        invalidate();
    }

    public void bind(EdgeTxAdapter adapter) {
        this.adapter = adapter;
    }

    public void setTouchMapper(TouchMapper touchMapper) {
        this.touchMapper = touchMapper;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (touchMapper == null || frame == null || adapter == null) {
            return false;
        }
        float scale = Math.min((float) getWidth() / frame.getWidth(),
                (float) getHeight() / frame.getHeight());
        int dw = Math.round(frame.getWidth() * scale);
        int dh = Math.round(frame.getHeight() * scale);
        int left = (getWidth() - dw) / 2;
        int top = (getHeight() - dh) / 2;
        float x = event.getX();
        float y = event.getY();
        if (x < left || y < top || x > left + dw || y > top + dh) {
            return false;
        }
        int lcdX = Math.round((x - left) * frame.getWidth() / (float) dw);
        int lcdY = Math.round((y - top) * frame.getHeight() / (float) dh);
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
            case android.view.MotionEvent.ACTION_MOVE:
                touchMapper.onLcdTouch(lcdX, lcdY, false);
                return true;
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                touchMapper.onLcdTouch(lcdX, lcdY, true);
                return true;
            default:
                return false;
        }
    }

    public void refreshFrame() {
        if (adapter == null) {
            return;
        }
        EdgeTxAdapter.LcdMetadata meta = adapter.lcdMetadata();
        byte[] rgb565 = adapter.copyLcdFrameRgb565();
        final int expected = meta.width() * meta.height() * 2;
        if (rgb565 == null || rgb565.length < expected) {
            if (frame == null || frame.getWidth() != meta.width() || frame.getHeight() != meta.height()) {
                frame = Bitmap.createBitmap(meta.width(), meta.height(), Bitmap.Config.RGB_565);
            }
            invalidate();
            return;
        }
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
        if (!radioPowered) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), powerOff);
        }
    }
}
