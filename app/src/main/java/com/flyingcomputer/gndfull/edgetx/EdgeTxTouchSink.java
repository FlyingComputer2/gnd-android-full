package com.flyingcomputer.gndfull.edgetx;

/** LCD touch down/move/up for color targets. */
public interface EdgeTxTouchSink {
    void touchDown(int lcdX, int lcdY);

    void touchUp();
}
