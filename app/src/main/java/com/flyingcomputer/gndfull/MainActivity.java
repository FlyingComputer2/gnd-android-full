package com.flyingcomputer.gndfull;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.flyingcomputer.gndfull.control.ControlSession;
import com.flyingcomputer.gndfull.control.DrivingMode;
import com.flyingcomputer.gndfull.control.DrivingState;
import com.flyingcomputer.gndfull.edgetx.EdgeTxAdapter;
import com.flyingcomputer.gndfull.edgetx.EdgeTxAdapterFactory;
import com.flyingcomputer.gndfull.edgetx.EdgeTxKey;
import com.flyingcomputer.gndfull.edgetx.Tx16InputMap;
import com.flyingcomputer.gndfull.transport.UsbGroundSession;
import com.flyingcomputer.gndfull.transport.rover.RoverTelemFrame;
import com.flyingcomputer.gndfull.ui.EdgeTxLcdView;
import com.flyingcomputer.gndfull.ui.EdgeTxUiWire;
import com.flyingcomputer.gndfull.ui.GimbalPadView;
import com.flyingcomputer.gndfull.ui.OsdPresenter;
import com.flyingcomputer.gndfull.ui.SwitchStripView;
import com.flyingcomputer.gndfull.video.VideoMetrics;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static final String USB_PERMISSION = "com.flyingcomputer.gndfull.USB_PERMISSION";

    private final ExecutorService usbSetup = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private EdgeTxUiWire edgeTxUi;
    private Button powerButton;

    private UsbManager usbManager;
    private ControlSession control;
    private UsbGroundSession usbSession;
    private SurfaceView videoSurface;
    private TextView osd;
    private TextView statusBar;
    private EdgeTxLcdView lcdView;
    private volatile boolean surfaceReady;
    private volatile int currentDevice = -1;
    private volatile RoverTelemFrame lastTelem;

    private final Runnable lcdRefresh =
            new Runnable() {
                @Override
                public void run() {
                    if (lcdView != null) {
                        lcdView.refreshFrame();
                    }
                    refreshControlStatus();
                    ui.postDelayed(this, 100);
                }
            };

    private final Runnable osdRefresh =
            new Runnable() {
                @Override
                public void run() {
                    VideoMetrics.Snapshot video =
                            usbSession == null
                                    ? null
                                    : usbSession.videoMetrics().snapshot();
                    osd.setText(
                            OsdPresenter.format(
                                    usbSession != null && currentDevice >= 0,
                                    video,
                                    lastTelem,
                                    control.state(),
                                    0));
                    ui.postDelayed(this, 500);
                }
            };

    private final BroadcastReceiver usbEvents =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (USB_PERMISSION.equals(intent.getAction())) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                                && device != null) {
                            connectUsb(device);
                        } else {
                            showStatus("USB permission denied");
                        }
                    } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                        if (device != null && device.getDeviceId() == currentDevice) {
                            submit(
                                    () -> {
                                        disconnectUsb();
                                        showStatus("USB disconnected — failsafe");
                                    });
                        }
                    } else {
                        discoverUsb();
                    }
                }
            };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        usbManager = (UsbManager) getSystemService(USB_SERVICE);
        EdgeTxAdapter edgeTx = EdgeTxAdapterFactory.createAuto(this);
        control = new ControlSession(edgeTx);
        edgeTxUi = new EdgeTxUiWire(control);
        edgeTxUi.setOnInputChanged(this::refreshControlStatus);
        usbSession =
                new UsbGroundSession(
                        control,
                        new UsbGroundSession.Listener() {
                            @Override
                            public void onStatus(String message) {
                                showStatus(message);
                            }

                            @Override
                            public void onFailure(Exception error) {
                                submit(
                                        () -> {
                                            disconnectUsb();
                                            showStatus("USB error: " + error.getMessage());
                                        });
                            }

                            @Override
                            public void onTelemetry(RoverTelemFrame telem) {
                                lastTelem = telem;
                            }

                            @Override
                            public void onVideoMetrics(VideoMetrics.Snapshot metrics) {}
                        },
                        false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xffeef1f3);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(24, 16, 24, 16);
        topBar.setBackgroundColor(0xffdce5eb);
        TextView title = new TextView(this);
        title.setText("FLYING COMPUTER II");
        title.setTextSize(16f);
        topBar.addView(
                title,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Spinner mode = new Spinner(this);
        String[] modeLabels = new String[DrivingMode.values().length];
        for (int i = 0; i < modeLabels.length; i++) {
            modeLabels[i] = DrivingMode.values()[i].label;
        }
        mode.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        modeLabels));
        mode.setSelection(DrivingMode.RC.ordinal());
        mode.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent, View view, int position, long id) {
                        if (position != DrivingMode.RC.ordinal()) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Not available")
                                    .setMessage("Only RC mode is implemented in this baseline.")
                                    .setPositiveButton("OK", null)
                                    .show();
                            mode.setSelection(DrivingMode.RC.ordinal());
                        }
                    }

                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
        topBar.addView(mode);

        powerButton = new Button(this);
        powerButton.setText("PWR");
        powerButton.setOnClickListener(v -> toggleRadioPower());
        topBar.addView(powerButton);

        Button menu = new Button(this);
        menu.setText("☰");
        menu.setOnClickListener(v -> openMenu());
        topBar.addView(menu);
        root.addView(topBar);

        FrameLayout videoPane = new FrameLayout(this);
        int videoHeight = (int) (getResources().getDisplayMetrics().density * 220);
        videoSurface = new SurfaceView(this);
        videoSurface.getHolder().addCallback(this);
        videoPane.addView(
                videoSurface,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, videoHeight));
        osd = new TextView(this);
        osd.setTextColor(0xffc8d6df);
        osd.setTextSize(12f);
        osd.setPadding(16, 12, 16, 12);
        osd.setText("LINK / FPS / LATENCY — unavailable");
        videoPane.addView(
                osd,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.START | Gravity.TOP));
        root.addView(
                videoPane,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, videoHeight));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(8, 8, 8, 8);
        nav.setBackgroundColor(0xffe7edf0);
        edgeTxUi.wireNavRow(
                nav,
                new EdgeTxKey[] {
                    EdgeTxKey.SYS,
                    EdgeTxKey.LEFT,
                    EdgeTxKey.UP,
                    EdgeTxKey.ENTER,
                    EdgeTxKey.DOWN,
                    EdgeTxKey.RIGHT,
                    EdgeTxKey.MDL
                },
                new String[] {"SYS", "◀", "▲", "●", "▼", "▶", "MDL"});
        root.addView(nav);

        lcdView = new EdgeTxLcdView(this, null);
        lcdView.bind(control.adapter());
        edgeTxUi.wireLcdTouch(lcdView);
        int lcdHeight = (int) (getResources().getDisplayMetrics().density * 180);
        root.addView(
                lcdView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, lcdHeight));

        SwitchStripView upper = new SwitchStripView(this, null);
        upper.setLabels(new String[] {"SE", "SF", "SG", "SH"});
        edgeTxUi.wireSwitchRow(upper, Tx16InputMap.UPPER_SWITCH_INDICES);
        root.addView(
                upper,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (getResources().getDisplayMetrics().density * 52)));

        SwitchStripView lower = new SwitchStripView(this, null);
        lower.setLabels(new String[] {"SD", "SC", "SB", "SA"});
        edgeTxUi.wireSwitchRow(lower, Tx16InputMap.LOWER_SWITCH_INDICES);
        root.addView(
                lower,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (getResources().getDisplayMetrics().density * 52)));

        LinearLayout gimbals = new LinearLayout(this);
        gimbals.setOrientation(LinearLayout.HORIZONTAL);
        GimbalPadView left = new GimbalPadView(this, null);
        edgeTxUi.wireGimbal(left, true);
        GimbalPadView right = new GimbalPadView(this, null);
        edgeTxUi.wireGimbal(right, false);
        gimbals.addView(
                left,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        gimbals.addView(
                right,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(
                gimbals,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        statusBar = new TextView(this);
        statusBar.setPadding(20, 12, 20, 12);
        statusBar.setBackgroundColor(0x99000000);
        statusBar.setTextColor(0xffffffff);
        root.addView(statusBar);

        setContentView(root);
        showStatus(
                "EdgeTX ["
                        + EdgeTxAdapterFactory.describe(edgeTx)
                        + "]: "
                        + edgeTx.status().detail()
                        + " — splash may take ~30s");
        ui.post(lcdRefresh);
        ui.post(osdRefresh);
        updatePowerButton();

        IntentFilter filter = new IntentFilter(USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbEvents, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbEvents, filter);
        }
    }

    private void openMenu() {
        control.setMenuOpen(true);
        new AlertDialog.Builder(this)
                .setTitle("Connection & EdgeTX")
                .setMessage(
                        "USB ch0 rtp-h264-v1 video, ch2 rc-v1 MSP (rover_sample 0x4201).\n"
                                + "EdgeTX: "
                                + control.adapter().status().detail()
                                + "\nRover RC / S.Port adapters are not enabled in this build.")
                .setNeutralButton("Connect USB", (d, w) -> discoverUsb())
                .setPositiveButton("Close", (d, w) -> control.setMenuOpen(false))
                .setOnDismissListener(d -> control.setMenuOpen(false))
                .show();
    }

    private void toggleRadioPower() {
        try {
            control.setRadioPower(!control.radioPowered());
            updatePowerButton();
            if (lcdView != null) {
                lcdView.setRadioPowered(control.radioPowered());
            }
            refreshControlStatus();
        } catch (Exception e) {
            showStatus("Power failed: " + e.getMessage());
        }
    }

    private void updatePowerButton() {
        powerButton.setText(control.radioPowered() ? "PWR ●" : "PWR ○");
    }

    private void refreshControlStatus() {
        showStatus(
                control.adapter().status().detail()
                        + "  ch1="
                        + control.lastOutputs()[0]
                        + "  ch3="
                        + control.lastOutputs()[2]);
    }

    private void submit(Runnable work) {
        try {
            usbSetup.execute(work);
        } catch (RejectedExecutionException ignored) {
            // Activity teardown already owns cleanup.
        }
    }

    private void showStatus(String text) {
        runOnUiThread(() -> statusBar.setText(text));
    }

    private void discoverUsb() {
        if (!surfaceReady) {
            return;
        }
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getVendorId() == 0x303a && device.getProductId() == 0x4001) {
                if (usbManager.hasPermission(device)) {
                    connectUsb(device);
                } else {
                    usbManager.requestPermission(
                            device,
                            android.app.PendingIntent.getBroadcast(
                                    this,
                                    0,
                                    new Intent(USB_PERMISSION).setPackage(getPackageName()),
                                    android.app.PendingIntent.FLAG_IMMUTABLE));
                }
                return;
            }
        }
        showStatus("P4 not found — use High Speed USB, not CH340");
    }

    private void connectUsb(UsbDevice device) {
        submit(
                () -> {
                    disconnectUsb();
                    if (!surfaceReady) {
                        return;
                    }
                    showStatus("Connecting USB…");
                    currentDevice = device.getDeviceId();
                    try {
                        usbSession.connect(usbManager, device, videoSurface.getHolder().getSurface());
                    } catch (Exception e) {
                        disconnectUsb();
                        showStatus("Connection failed: " + e.getMessage());
                    }
                });
    }

    private void disconnectUsb() {
        if (usbSession != null) {
            usbSession.close();
        }
        lastTelem = null;
        currentDevice = -1;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        discoverUsb();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        submit(this::disconnectUsb);
    }

    @Override
    public void onDestroy() {
        ui.removeCallbacks(lcdRefresh);
        ui.removeCallbacks(osdRefresh);
        unregisterReceiver(usbEvents);
        surfaceReady = false;
        submit(this::disconnectUsb);
        usbSetup.shutdown();
        if (control != null) {
            control.close();
        }
        super.onDestroy();
    }
}
