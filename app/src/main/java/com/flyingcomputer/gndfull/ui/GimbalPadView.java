package com.flyingcomputer.gndfull.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** One touch gimbal; tracks a single active pointer ID. */
public final class GimbalPadView extends View {
    public interface Listener {
        void onAxis(int normX, int normY);

        void onRelease();
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF square = new RectF();
    private final PointF center = new PointF();
    private final PointF knobPt = new PointF();
    private Listener listener;
    private int activePointer = -1;
    private float radius;

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
        float availW = w - padL - padR;
        float availH = h - padT - padB;
        float side = Math.max(1f, Math.min(availW, availH));
        float left = padL + (availW - side) / 2f;
        float top = padT + (availH - side) / 2f;
        square.set(left, top, left + side, top + side);
        center.set(square.centerX(), square.centerY());
        radius = side * 0.40f;
        knobPt.set(center);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = Math.min(square.width(), square.height()) * 0.08f;
        canvas.drawRoundRect(square, r, r, fill);
        canvas.drawRoundRect(square, r, r, frame);
        canvas.drawCircle(center.x, center.y, radius, frame);
        canvas.drawLine(center.x - radius, center.y, center.x + radius, center.y, cross);
        canvas.drawLine(center.x, center.y - radius, center.x, center.y + radius, cross);
        canvas.drawCircle(knobPt.x, knobPt.y, radius * 0.22f, knob);
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
