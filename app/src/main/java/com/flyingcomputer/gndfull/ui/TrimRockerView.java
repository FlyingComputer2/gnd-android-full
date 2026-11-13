package com.flyingcomputer.gndfull.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/** Slim two-way TX16S trim switch (not a Material button). */
public final class TrimRockerView extends View {
    public interface Listener {
        void onTrim(int firmwareIndex, boolean down);
    }

    private final Paint housing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chevron = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pressedFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path triangle = new Path();
    private final boolean vertical;
    private final int minusIndex;
    private final int plusIndex;
    private Listener listener;
    private int held = 0;

    public TrimRockerView(
            Context context, boolean vertical, int minusIndex, int plusIndex) {
        super(context);
        this.vertical = vertical;
        this.minusIndex = minusIndex;
        this.plusIndex = plusIndex;
        housing.setStyle(Paint.Style.FILL);
        housing.setColor(0xff3a4a54);
        chevron.setStyle(Paint.Style.FILL);
        chevron.setColor(0xffd7e2e8);
        pressedFill.setStyle(Paint.Style.FILL);
        pressedFill.setColor(0xff2a3840);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int minusIndex() {
        return minusIndex;
    }

    public int plusIndex() {
        return plusIndex;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = Math.min(w, h) * 0.18f;
        canvas.drawRoundRect(0, 0, w, h, r, r, housing);
        if (held < 0) {
            if (vertical) {
                canvas.drawRoundRect(0, h / 2f, w, h, r, r, pressedFill);
            } else {
                canvas.drawRoundRect(0, 0, w / 2f, h, r, r, pressedFill);
            }
        } else if (held > 0) {
            if (vertical) {
                canvas.drawRoundRect(0, 0, w, h / 2f, r, r, pressedFill);
            } else {
                canvas.drawRoundRect(w / 2f, 0, w, h, r, r, pressedFill);
            }
        }
        float inset = Math.min(w, h) * 0.22f;
        if (vertical) {
            drawChevron(canvas, w / 2f, h * 0.28f, inset, true);
            drawChevron(canvas, w / 2f, h * 0.72f, inset, false);
        } else {
            drawChevron(canvas, w * 0.28f, h / 2f, inset, false);
            drawChevron(canvas, w * 0.72f, h / 2f, inset, true);
        }
    }

    private void drawChevron(Canvas canvas, float cx, float cy, float size, boolean plusEnd) {
        triangle.reset();
        if (vertical) {
            if (plusEnd) {
                triangle.moveTo(cx, cy - size);
                triangle.lineTo(cx - size, cy + size * 0.5f);
                triangle.lineTo(cx + size, cy + size * 0.5f);
            } else {
                triangle.moveTo(cx, cy + size);
                triangle.lineTo(cx - size, cy - size * 0.5f);
                triangle.lineTo(cx + size, cy - size * 0.5f);
            }
        } else {
            if (plusEnd) {
                triangle.moveTo(cx + size, cy);
                triangle.lineTo(cx - size * 0.5f, cy - size);
                triangle.lineTo(cx - size * 0.5f, cy + size);
            } else {
                triangle.moveTo(cx - size, cy);
                triangle.lineTo(cx + size * 0.5f, cy - size);
                triangle.lineTo(cx + size * 0.5f, cy + size);
            }
        }
        triangle.close();
        canvas.drawPath(triangle, chevron);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int next = heldOf(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fire(next, true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (next != held) {
                    fire(held, false);
                    fire(next, true);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                fire(held, false);
                return true;
            default:
                return false;
        }
    }

    private int heldOf(MotionEvent event) {
        if (vertical) {
            return event.getY() < getHeight() / 2f ? 1 : -1;
        }
        return event.getX() < getWidth() / 2f ? -1 : 1;
    }

    private void fire(int side, boolean down) {
        if (side == 0) {
            return;
        }
        held = down ? side : 0;
        invalidate();
        if (listener == null) {
            return;
        }
        listener.onTrim(side < 0 ? minusIndex : plusIndex, down);
    }
}
