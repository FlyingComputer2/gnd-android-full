package com.flyingcomputer.gndfull.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Four labeled switches in one row (SA–SH mapping supplied by parent). */
public final class SwitchStripView extends View {
    public interface Listener {
        void onSwitch(int index, int position);
    }

    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] positions = new int[4];
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
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int[] positions() {
        return positions.clone();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cell = getWidth() / 4f;
        float h = getHeight();
        for (int i = 0; i < 4; i++) {
            float left = i * cell;
            canvas.drawRect(left + 4, 4, left + cell - 4, h - 4, box);
            canvas.drawText(labels[i], left + cell / 2f, h * 0.55f, text);
            if (positions[i] > 0) {
                canvas.drawCircle(left + cell / 2f, h * 0.78f, 10f, box);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int index = (int) (event.getX() / (getWidth() / 4f));
            if (index >= 0 && index < 4) {
                positions[index] = positions[index] <= 0 ? 1 : 0;
                if (listener != null) {
                    listener.onSwitch(index, positions[index]);
                }
                invalidate();
            }
        }
        return true;
    }
}
