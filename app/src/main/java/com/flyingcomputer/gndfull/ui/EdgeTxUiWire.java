package com.flyingcomputer.gndfull.ui;

import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import com.flyingcomputer.gndfull.R;
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

    /**
     * The TX16S keys under the LCD, one row, equal width and height:
     * SYS, RTN, PAGE up, PAGE down, TELE, roller up, roller click (ENTER), roller down, MDL.
     */
    public void wireNavKeys(LinearLayout nav, int heightPx) {
        addKey(nav, heightPx, R.drawable.ic_key_sys, "SYS", EdgeTxKey.SYS);
        addKey(nav, heightPx, R.drawable.ic_key_rtn, "RTN", EdgeTxKey.RTN);
        addKey(nav, heightPx, R.drawable.ic_key_pgup, "PAGE up", EdgeTxKey.PAGE_UP);
        addKey(nav, heightPx, R.drawable.ic_key_pgdn, "PAGE down", EdgeTxKey.PAGE_DOWN);
        addKey(nav, heightPx, R.drawable.ic_key_tele, "TELE", EdgeTxKey.TELE);
        addRoller(nav, heightPx, R.drawable.ic_key_roller_up, "Roller up", -1);
        addKey(nav, heightPx, R.drawable.ic_key_roller_click, "Roller click", EdgeTxKey.ENTER);
        addRoller(nav, heightPx, R.drawable.ic_key_roller_down, "Roller down", 1);
        addKey(nav, heightPx, R.drawable.ic_key_mdl, "MDL", EdgeTxKey.MDL);
    }

    private static final int KEY_FACE = 0xfff7f9fa;
    private static final int KEY_INK = 0xff1c2b33;
    private static final int KEY_EDGE = 0xff687984;

    private static android.graphics.drawable.Drawable keyFace(float density) {
        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, keyShape(density, KEY_INK));
        states.addState(new int[] {}, keyShape(density, KEY_FACE));
        return states;
    }

    private static android.graphics.drawable.GradientDrawable keyShape(float density, int fill) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(density * 6f);
        g.setStroke(Math.max(1, Math.round(density)), KEY_EDGE);
        return g;
    }

    private static android.widget.ImageButton iconButton(
            LinearLayout nav, int heightPx, int icon, String label) {
        android.content.Context ctx = nav.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        android.widget.ImageButton b = new android.widget.ImageButton(ctx);
        b.setImageResource(icon);
        b.setContentDescription(label);
        b.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        int pad = Math.round(density * 7f);
        b.setPadding(pad, pad, pad, pad);
        // Light key face, dark icon (as the switch tiles); inverted while pressed.
        b.setBackground(keyFace(density));
        b.setImageTintList(
                new android.content.res.ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_pressed}, new int[] {}},
                        new int[] {KEY_FACE, KEY_INK}));
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, heightPx, 1f);
        int gap = Math.round(density * 2f);
        lp.setMargins(gap, 0, gap, 0);
        nav.addView(b, lp);
        return b;
    }

    private void addKey(LinearLayout nav, int heightPx, int icon, String label, EdgeTxKey key) {
        android.widget.ImageButton b = iconButton(nav, heightPx, icon, label);
        b.setOnTouchListener(
                (v, event) -> {
                    if (!control.radioPowered()) {
                        return true;
                    }
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            v.setPressed(true);
                            control.keyEvent(key, true);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.setPressed(false);
                            control.keyEvent(key, false);
                            return true;
                        default:
                            return false;
                    }
                });
    }

    /** One roller step on press, then repeats while held. */
    private void addRoller(LinearLayout nav, int heightPx, int icon, String label, int step) {
        android.widget.ImageButton b = iconButton(nav, heightPx, icon, label);
        final Runnable[] repeat = new Runnable[1];
        repeat[0] =
                () -> {
                    control.rotaryEvent(step);
                    b.postDelayed(repeat[0], 90);
                };
        b.setOnTouchListener(
                (v, event) -> {
                    if (!control.radioPowered()) {
                        return true;
                    }
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            v.setPressed(true);
                            control.rotaryEvent(step);
                            v.postDelayed(repeat[0], 350);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.setPressed(false);
                            v.removeCallbacks(repeat[0]);
                            return true;
                        default:
                            return false;
                    }
                });
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
