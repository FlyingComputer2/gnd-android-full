package com.flyingcomputer.gndfull.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import com.flyingcomputer.gndfull.edgetx.Tx16TrimMap;

/** Full-cell gimbal with slim inner trim rockers overlaid on the stick. */
public final class StickClusterView extends ViewGroup {
    private final boolean leftStick;
    private final int trimPx;
    private final int insetPx;
    private final GimbalPadView gimbal;
    private final TrimRockerView hTrim;
    private final TrimRockerView vTrim;

    public StickClusterView(Context context, boolean leftStick) {
        super(context);
        this.leftStick = leftStick;
        float density = getResources().getDisplayMetrics().density;
        trimPx = Math.round(density * 14f);
        insetPx = Math.round(density * 8f);
        gimbal = new GimbalPadView(context, null);
        int hMinus = leftStick ? Tx16TrimMap.LH_DEC : Tx16TrimMap.RH_DEC;
        int hPlus = leftStick ? Tx16TrimMap.LH_INC : Tx16TrimMap.RH_INC;
        int vMinus = leftStick ? Tx16TrimMap.LV_DEC : Tx16TrimMap.RV_DEC;
        int vPlus = leftStick ? Tx16TrimMap.LV_INC : Tx16TrimMap.RV_INC;
        hTrim = new TrimRockerView(context, false, hMinus, hPlus);
        vTrim = new TrimRockerView(context, true, vMinus, vPlus);
        addView(gimbal);
        addView(hTrim);
        addView(vTrim);
    }

    public GimbalPadView gimbal() {
        return gimbal;
    }

    public TrimRockerView hTrim() {
        return hTrim;
    }

    public TrimRockerView vTrim() {
        return vTrim;
    }

    public boolean leftStick() {
        return leftStick;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        StickTrimLayout.Box gimbalBox = new StickTrimLayout.Box();
        StickTrimLayout.Box hBox = new StickTrimLayout.Box();
        StickTrimLayout.Box vBox = new StickTrimLayout.Box();
        StickTrimLayout.place(r - l, b - t, trimPx, insetPx, leftStick, gimbalBox, hBox, vBox);
        layoutChild(gimbal, gimbalBox);
        layoutChild(hTrim, hBox);
        layoutChild(vTrim, vBox);
    }

    private static void layoutChild(View child, StickTrimLayout.Box box) {
        int width = Math.max(1, box.width());
        int height = Math.max(1, box.height());
        child.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        child.layout(box.left, box.top, box.right, box.bottom);
    }
}
