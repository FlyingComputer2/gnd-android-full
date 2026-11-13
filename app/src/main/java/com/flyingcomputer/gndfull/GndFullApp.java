package com.flyingcomputer.gndfull;

import android.app.Application;
import android.content.Context;
import com.flyingcomputer.gndfull.control.ControlSession;
import com.flyingcomputer.gndfull.edgetx.EdgeTxAdapter;
import com.flyingcomputer.gndfull.edgetx.EdgeTxAdapterFactory;

/** Process-scoped EdgeTX session so leaving the activity does not reboot the radio. */
public final class GndFullApp extends Application {
    private ControlSession control;

    public static GndFullApp from(Context context) {
        return (GndFullApp) context.getApplicationContext();
    }

    public synchronized ControlSession control() {
        if (control == null) {
            EdgeTxAdapter adapter = EdgeTxAdapterFactory.createAuto(this);
            control = new ControlSession(adapter);
        }
        return control;
    }
}
