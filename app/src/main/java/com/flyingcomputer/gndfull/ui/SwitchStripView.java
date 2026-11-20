package com.flyingcomputer.gndfull.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.flyingcomputer.gndfull.switches.SwitchSlot;

/**
 * Row of four switch tiles. Per slot type: push = held while touched (multi-touch), toggle =
 * flips on tap, mpos = tap the left half to step down, the right half to step up.
 */
public final class SwitchStripView extends View {
    public interface Listener {
        /** Return false to reject the change (input not active); the tile keeps its position. */
        boolean onSwitch(int index, int position);
    }

    private static final int TILES = 4;
    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] positions = new int[TILES];
    /** Pointer id holding each push tile, or -1. */
    private final int[] pushPointer = new int[] {-1, -1, -1, -1};
    private SwitchSlot[] slots = new SwitchSlot[TILES];
    private Listener listener;

    public SwitchStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        box.setStyle(Paint.Style.STROKE);
        box.setStrokeWidth(2f);
        box.setColor(0xff687984);
        fill.setStyle(Paint.Style.FILL);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(36f);
        text.setColor(0xff26333d);
    }

    /** Sets the four slots and their current positions. */
    public void setSlots(SwitchSlot[] slots, int[] positions) {
        this.slots = slots.clone();
        System.arraycopy(positions, 0, this.positions, 0, TILES);
        java.util.Arrays.fill(pushPointer, -1);
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cell = getWidth() / (float) TILES;
        float h = getHeight();
        text.setTextSize(h * 0.42f);
        float baseline = h / 2f - (text.descent() + text.ascent()) / 2f;
        for (int i = 0; i < TILES; i++) {
            SwitchSlot s = slots[i];
            if (s == null) {
                continue;
            }
            float left = i * cell;
            boolean active = positions[i] > 0 && s.type() != SwitchSlot.Type.MPOS;
            if (active) {
                fill.setColor(0xffb8d4e3);
                canvas.drawRect(left + 4, 4, left + cell - 4, h - 4, fill);
            }
            if (s.type() == SwitchSlot.Type.MPOS && s.positions() > 1) {
                // Thin level bar along the bottom: how far along the n positions.
                fill.setColor(0xff7fa7bd);
                float frac = positions[i] / (float) (s.positions() - 1);
                canvas.drawRect(left + 6, h - 9, left + 6 + (cell - 12) * frac, h - 5, fill);
            }
            canvas.drawRect(left + 4, 4, left + cell - 4, h - 4, box);
            canvas.drawText(
                    s.label() + "  " + s.positionText(positions[i]), left + cell / 2f, baseline, text);
        }
    }

    private int tileAt(float x) {
        int i = (int) (x / (getWidth() / (float) TILES));
        return i >= 0 && i < TILES && slots[i] != null ? i : -1;
    }

    private void set(int i, int position) {
        int p = slots[i].clamp(position);
        if (p == positions[i]) {
            return;
        }
        if (listener == null || listener.onSwitch(i, p)) {
            positions[i] = p;
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int idx = event.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                {
                    int i = tileAt(event.getX(idx));
                    if (i >= 0 && slots[i].type() == SwitchSlot.Type.PUSH && pushPointer[i] < 0) {
                        pushPointer[i] = event.getPointerId(idx);
                        set(i, 1);
                    }
                    return true;
                }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                {
                    int id = event.getPointerId(idx);
                    if (releasePush(id)) {
                        return true;
                    }
                    int i = tileAt(event.getX(idx));
                    if (i < 0) {
                        return true;
                    }
                    SwitchSlot s = slots[i];
                    if (s.type() == SwitchSlot.Type.TOGGLE) {
                        set(i, positions[i] == 0 ? 1 : 0);
                    } else if (s.type() == SwitchSlot.Type.MPOS) {
                        float cell = getWidth() / (float) TILES;
                        boolean up = event.getX(idx) - i * cell >= cell / 2f;
                        set(i, positions[i] + (up ? 1 : -1));
                    }
                    return true;
                }
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < TILES; i++) {
                    if (pushPointer[i] >= 0) {
                        pushPointer[i] = -1;
                        forceRelease(i);
                    }
                }
                return true;
            default:
                return true;
        }
    }

    private boolean releasePush(int pointerId) {
        for (int i = 0; i < TILES; i++) {
            if (pushPointer[i] == pointerId) {
                pushPointer[i] = -1;
                forceRelease(i);
                return true;
            }
        }
        return false;
    }

    /** A released push button always goes back to 0, even if the listener is not active. */
    private void forceRelease(int i) {
        if (positions[i] != 0) {
            positions[i] = 0;
            if (listener != null) {
                listener.onSwitch(i, 0);
            }
            invalidate();
        }
    }
}
