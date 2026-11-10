package com.flyingcomputer.gndfull.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;

/** Two-button trim (DEC / INC) for one TX16S stick axis. */
public final class TrimPairView extends LinearLayout {
    public interface Listener {
        void onTrim(int firmwareIndex, boolean down);
    }

    private final int decIndex;
    private final int incIndex;
    private Listener listener;

    public TrimPairView(
            Context context, boolean vertical, String decLabel, String incLabel, int decIndex, int incIndex) {
        super(context);
        this.decIndex = decIndex;
        this.incIndex = incIndex;
        setOrientation(vertical ? VERTICAL : HORIZONTAL);
        setGravity(Gravity.CENTER);
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(density * 36f);
        addView(makeButton(decLabel, decIndex, size, vertical));
        addView(makeButton(incLabel, incIndex, size, vertical));
    }

    public int decIndex() {
        return decIndex;
    }

    public int incIndex() {
        return incIndex;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private Button makeButton(String label, int firmwareIndex, int size, boolean vertical) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12f);
        button.setMinWidth(size);
        button.setMinHeight(size);
        button.setPadding(0, 0, 0, 0);
        button.setOnTouchListener(
                (v, event) -> {
                    if (listener == null) {
                        return true;
                    }
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            listener.onTrim(firmwareIndex, true);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            listener.onTrim(firmwareIndex, false);
                            return true;
                        default:
                            return false;
                    }
                });
        LayoutParams params =
                vertical
                        ? new LayoutParams(size, 0, 1f)
                        : new LayoutParams(0, size, 1f);
        button.setLayoutParams(params);
        return button;
    }
}
