package com.flyingcomputer.gndfull.ui;

import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import com.flyingcomputer.gndfull.control.ControlSession;
import com.flyingcomputer.gndfull.edgetx.EdgeTxKey;
import com.flyingcomputer.gndfull.edgetx.EdgeTxInputSnapshot;
import com.flyingcomputer.gndfull.edgetx.Tx16InputMap;

/** Binds sketch UI controls to {@link ControlSession}. */
public final class EdgeTxUiWire {
    private final ControlSession control;
    private final int[] sticks = new int[4];
    private final int[] switches = new int[8];
    private Runnable onInputChanged;

    public EdgeTxUiWire(ControlSession control) {
        this.control = control;
    }

    public void setOnInputChanged(Runnable onInputChanged) {
        this.onInputChanged = onInputChanged;
    }

    public boolean isInputActive() {
        return control.radioPowered() && !control.menuBlocksInput();
    }

    public void wireGimbal(GimbalPadView view, boolean leftStick) {
        view.setListener(
                new GimbalPadView.Listener() {
                    @Override
                    public void onAxis(int normX, int normY) {
                        if (!isInputActive()) {
                            return;
                        }
                        if (leftStick) {
                            sticks[0] = normX;
                            sticks[1] = normY;
                        } else {
                            sticks[2] = normX;
                            sticks[3] = normY;
                        }
                        publish();
                    }

                    @Override
                    public void onRelease() {
                        if (!isInputActive()) {
                            return;
                        }
                        if (leftStick) {
                            sticks[0] = 0;
                            sticks[1] = 0;
                        } else {
                            sticks[2] = 0;
                            sticks[3] = 0;
                        }
                        publish();
                    }
                });
    }

    public void wireSwitchRow(SwitchStripView row, int[] indexMap) {
        row.setListener(
                (index, position) -> {
                    if (!isInputActive()) {
                        return;
                    }
                    switches[indexMap[index]] = position;
                    publish();
                });
    }

    public Button createKeyButton(android.content.Context context, EdgeTxKey edgeKey, String label) {
        float density = context.getResources().getDisplayMetrics().density;
        int h = Math.round(density * 36f);
        int w = Math.round(density * 52f);
        Button key = new Button(context);
        key.setText(label);
        key.setAllCaps(false);
        key.setTextSize(12f);
        key.setMinHeight(h);
        key.setMinWidth(w);
        key.setPadding(4, 0, 4, 0);
        key.setOnTouchListener(
                (v, event) -> {
                    if (!control.radioPowered()) {
                        return true;
                    }
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            control.keyEvent(edgeKey, true);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            control.keyEvent(edgeKey, false);
                            return true;
                        default:
                            return false;
                    }
                });
        return key;
    }

    public void wireNavRow(LinearLayout nav, EdgeTxKey[] keys, String[] labels) {
        float density = nav.getResources().getDisplayMetrics().density;
        int h = Math.round(density * 36f);
        for (int i = 0; i < labels.length; i++) {
            Button key = createKeyButton(nav.getContext(), keys[i], labels[i]);
            nav.addView(key, new LinearLayout.LayoutParams(0, h, 1f));
        }
    }

    public void wireTrim(TrimRockerView rocker) {
        rocker.setListener(
                (index, down) -> {
                    if (!control.radioPowered()) {
                        return;
                    }
                    control.trimEvent(index, down);
                });
    }

    public void wireLcdTouch(EdgeTxLcdView lcd) {
        lcd.setTouchMapper(
                (lcdX, lcdY, up) -> {
                    if (!control.radioPowered()) {
                        return;
                    }
                    if (up) {
                        control.touchUp();
                    } else {
                        control.touchDown(lcdX, lcdY);
                    }
                });
    }

    private void publish() {
        control.publishInput(new EdgeTxInputSnapshot(sticks, switches));
        if (onInputChanged != null) {
            onInputChanged.run();
        }
    }
}
