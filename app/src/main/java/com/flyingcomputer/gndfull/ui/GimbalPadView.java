package com.flyingcomputer.gndfull.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** One touch gimbal; tracks a single active pointer ID. Fills its cell. */
public final class GimbalPadView extends View {
    public interface Listener {
        void onAxis(int normX, int normY);

        void onRelease();
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF field = new RectF();
    private final RectF ring = new RectF();
    private final PointF center = new PointF();
    private final PointF knobPt = new PointF();
    private final float[] clamped = new float[2];
    private Listener listener;
    private int activePointer = -1;
    private float radiusX;
    private float radiusY;

    public GimbalPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xffd5dde2);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(3f);
        frame.setColor(0xff4d616c);
        cross.setStyle(Paint.Style.STROKE);
        cross.setStrokeWidth(2f);
        cross.setColor(0x664d616c);
        knob.setColor(0xff2c4a5a);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layoutPlayfield(w, h);
    }

    private void layoutPlayfield(int w, int h) {
        float padL = getPaddingLeft();
        float padT = getPaddingTop();
        float padR = getPaddingRight();
        float padB = getPaddingBottom();
        field.set(padL, padT, w - padR, h - padB);
        if (field.width() < 1f || field.height() < 1f) {
            field.set(0, 0, Math.max(1, w), Math.max(1, h));
        }
        center.set(field.centerX(), field.centerY());
        radiusX = field.width() * 0.42f;
        radiusY = field.height() * 0.42f;
        ring.set(center.x - radiusX, center.y - radiusY, center.x + radiusX, center.y + radiusY);
        knobPt.set(center);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = Math.min(field.width(), field.height()) * 0.06f;
        canvas.drawRoundRect(field, r, r, fill);
        canvas.drawRoundRect(field, r, r, frame);
        canvas.drawOval(ring, frame);
        canvas.drawLine(center.x - radiusX, center.y, center.x + radiusX, center.y, cross);
        canvas.drawLine(center.x, center.y - radiusY, center.x, center.y + radiusY, cross);
        float knobR = Math.min(radiusX, radiusY) * 0.20f;
        canvas.drawCircle(knobPt.x, knobPt.y, knobR, knob);
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
        GimbalTravel.clamp(
                event.getX(index) - center.x,
                event.getY(index) - center.y,
                radiusX,
                radiusY,
                clamped);
        knobPt.set(center.x + clamped[0], center.y + clamped[1]);
        if (listener != null) {
            int normX = Math.round((clamped[0] / radiusX) * 1024f);
            int normY = Math.round((-clamped[1] / radiusY) * 1024f);
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
