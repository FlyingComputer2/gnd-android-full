package com.flyingcomputer.gndfull.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Switch row; supports 2- or 3-position toggles. */
public final class SwitchStripView extends View {
    public interface Listener {
        void onSwitch(int index, int position);
    }

    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] positions = new int[4];
    private final int[] maxPositions = new int[] {3, 3, 3, 3};
    private String[] labels = new String[] {"", "", "", ""};
    private Listener listener;

    public SwitchStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        box.setStyle(Paint.Style.STROKE);
        box.setStrokeWidth(2f);
        box.setColor(0xff687984);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(36f);
        text.setColor(0xff26333d);
    }

    public void setLabels(String[] labels) {
        this.labels = labels;
        for (int i = 0; i < maxPositions.length && i < labels.length; i++) {
            maxPositions[i] =
                    com.flyingcomputer.gndfull.edgetx.Tx16InputMap.isTwoPosition(labels[i]) ? 2 : 3;
        }
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cell = getWidth() / 4f;
        float h = getHeight();
        for (int i = 0; i < 4; i++) {
            float left = i * cell;
            canvas.drawRect(left + 4, 4, left + cell - 4, h - 4, box);
            String label = i < labels.length ? labels[i] : "";
            canvas.drawText(label, left + cell / 2f, h * 0.45f, text);
            String pos = positionLabel(positions[i], maxPositions[i]);
            canvas.drawText(pos, left + cell / 2f, h * 0.78f, text);
        }
    }

    private static String positionLabel(int pos, int max) {
        if (max == 2) {
            return pos > 0 ? "↑" : "↓";
        }
        return switch (pos) {
            case -1 -> "↓";
            case 1 -> "↑";
            default -> "—";
        };
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int index = (int) (event.getX() / (getWidth() / 4f));
            if (index >= 0 && index < 4) {
                positions[index] = nextPosition(positions[index], maxPositions[index]);
                if (listener != null) {
                    listener.onSwitch(index, positions[index]);
                }
                invalidate();
            }
        }
        return true;
    }

    private static int nextPosition(int current, int max) {
        if (max == 2) {
            return current > 0 ? 0 : 1;
        }
        if (current <= -1) {
            return 0;
        }
        if (current == 0) {
            return 1;
        }
        return -1;
    }
}
