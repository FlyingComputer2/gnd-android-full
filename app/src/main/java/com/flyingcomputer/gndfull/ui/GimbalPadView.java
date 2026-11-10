package com.flyingcomputer.gndfull.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** One touch gimbal; tracks a single active pointer ID. */
public final class GimbalPadView extends View {
    public interface Listener {
        void onAxis(int normX, int normY);

        void onRelease();
    }

    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PointF center = new PointF();
    private final PointF knobPt = new PointF();
    private Listener listener;
    private int activePointer = -1;
    private float radius;

    public GimbalPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(4f);
        frame.setColor(0xff687984);
        knob.setColor(0xff395d72);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        center.set(w / 2f, h / 2f);
        radius = Math.min(w, h) * 0.36f;
        knobPt.set(center);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom(), frame);
        canvas.drawCircle(center.x, center.y, radius, frame);
        canvas.drawCircle(knobPt.x, knobPt.y, radius * 0.18f, knob);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (activePointer < 0) {
                    activePointer = event.getPointerId(event.getActionIndex());
                    updateKnob(event);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (activePointer >= 0) {
                    updateKnob(event);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == activePointer) {
                    release();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                release();
                break;
            default:
                break;
        }
        return true;
    }

    private void updateKnob(MotionEvent event) {
        int index = event.findPointerIndex(activePointer);
        if (index < 0) {
            return;
        }
        float x = event.getX(index);
        float y = event.getY(index);
        float dx = x - center.x;
        float dy = y - center.y;
        float dist = (float) Math.hypot(dx, dy);
        if (dist > radius) {
            float scale = radius / dist;
            dx *= scale;
            dy *= scale;
        }
        knobPt.set(center.x + dx, center.y + dy);
        if (listener != null) {
            int normX = Math.round((dx / radius) * 1024f);
            int normY = Math.round((-dy / radius) * 1024f);
            listener.onAxis(normX, normY);
        }
        invalidate();
    }

    private void release() {
        activePointer = -1;
        knobPt.set(center);
        if (listener != null) {
            listener.onRelease();
        }
        invalidate();
    }
}
