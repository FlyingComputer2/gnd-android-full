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
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.flyingcomputer.gndfull.cli.CliView;
import com.flyingcomputer.gndfull.control.ControlSession;
import com.flyingcomputer.gndfull.control.DrivingMode;
import com.flyingcomputer.gndfull.edgetx.EdgeTxAdapter;
import com.flyingcomputer.gndfull.edgetx.EdgeTxAdapterFactory;
import com.flyingcomputer.gndfull.transport.UsbGroundSession;
import com.flyingcomputer.gndfull.transport.rover.GroundLinkFrame;
import com.flyingcomputer.gndfull.transport.rover.RoverTelemFrame;
import com.flyingcomputer.gndfull.ui.EdgeTxLcdView;
import com.flyingcomputer.gndfull.ui.EdgeTxUiWire;
import com.flyingcomputer.gndfull.ui.OsdPresenter;
import com.flyingcomputer.gndfull.ui.StickClusterView;
import com.flyingcomputer.gndfull.ui.SwitchConfigDialog;
import com.flyingcomputer.gndfull.ui.SwitchStripView;
import com.flyingcomputer.gndfull.video.VideoMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = "GndFull";
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
    private EdgeTxLcdView lcdView;
    private volatile boolean surfaceReady;
    private volatile int currentDevice = -1;
    // RC-only views (LCD, switches, gimbals), hidden in CLI mode.
    private final List<View> rcViews = new ArrayList<>();
    private CliView cliView;
    private FrameLayout videoPane;
    private int videoHeightRc;
    private int videoHeightCli;
    private boolean cliActive;
    /** Device id with a permission request in flight (no repeat prompts), or -1. */
    private int permissionPending = -1;
    private static final long USB_RETRY_MS = 2000;
    private final Runnable usbRetry = this::discoverUsb;

    private final Runnable lcdRefresh =
            new Runnable() {
                @Override
                public void run() {
                    if (lcdView != null) {
                        lcdView.refreshFrame();
                    }
                    ui.postDelayed(this, 100);
                }
            };

    private final Runnable osdRefresh =
            new Runnable() {
                @Override
                public void run() {
                    osd.setText(OsdPresenter.format(osdInput()));
                    ui.postDelayed(this, 500);
                }
            };

    private OsdPresenter.Input osdInput() {
        long now = System.nanoTime();
        boolean linked = usbSession != null && currentDevice >= 0;
        if (!linked) {
            return new OsdPresenter.Input(0, null, 0, null, null, -1, -1);
        }
        GroundLinkFrame ground = usbSession.groundLink(now);
        RoverTelemFrame telem = usbSession.telemetry(now);
        return new OsdPresenter.Input(
                ground == null ? 0 : ground.rssiDbm,
                usbSession.rovTx().result(now),
                telem == null ? 0 : telem.rssiDbm,
                usbSession.gndTx().result(now),
                usbSession.videoMetrics().window(now),
                telem == null ? -1 : telem.rcLatMs,
                telem == null ? -1 : telem.rcLossPerMille);
    }

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
                            showStatus("USB permission denied — replug the ground to retry");
                        }
                        permissionPending = -1;
                    } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                        permissionPending = -1;
                        ui.removeCallbacks(usbRetry);
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
        control = GndFullApp.from(this).control();
        EdgeTxAdapter edgeTx = control.adapter();
        edgeTxUi = new EdgeTxUiWire(control);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                            () -> moveTaskToBack(true));
        }
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
                                            ui.post(MainActivity.this::scheduleUsbRetry);
                                        });
                            }

                            @Override
                            public void onTelemetry(RoverTelemFrame telem) {}

                            @Override
                            public void onVideoMetrics(VideoMetrics.Snapshot metrics) {}
                        },
                        // RC + telemetry on USB channel 2, driven by EdgeTX's CRSF
                        // external module (see RoverControlBridge).
                        true);

        float density = getResources().getDisplayMetrics().density;
        int padH = Math.round(density * 6f);
        int padV = Math.round(density * 2f);
        int barH = Math.round(density * 28f);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xffeef1f3);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(padH, padV, padH, padV);
        topBar.setBackgroundColor(0xffdce5eb);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_flyingcomputer);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setContentDescription("FlyingComputer2");
        topBar.addView(logo, new LinearLayout.LayoutParams(barH, barH));
        View spacer = new View(this);
        topBar.addView(
                spacer, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

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
        mode.setPadding(0, 0, 0, 0);
        mode.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent, View view, int position, long id) {
                        if (position == DrivingMode.CLI.ordinal()) {
                            setCliMode(true);
                        } else if (position == DrivingMode.RC.ordinal()) {
                            setCliMode(false);
                        } else {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Not available")
                                    .setMessage("PLN is not implemented yet.")
                                    .setPositiveButton("OK", null)
                                    .show();
                            mode.setSelection(
                                    cliActive ? DrivingMode.CLI.ordinal() : DrivingMode.RC.ordinal());
                        }
                    }

                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
        topBar.addView(mode);

        powerButton = new Button(this);
        powerButton.setText("PWR");
        powerButton.setTextSize(11f);
        powerButton.setMinHeight(barH);
        powerButton.setPadding(padH, 0, padH, 0);
        powerButton.setOnClickListener(v -> toggleRadioPower());
        topBar.addView(
                powerButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, barH));

        Button menu = new Button(this);
        menu.setText("☰");
        menu.setTextSize(12f);
        menu.setMinHeight(barH);
        menu.setPadding(padH, 0, padH, 0);
        menu.setOnClickListener(v -> openMenu());
        topBar.addView(
                menu,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, barH));
        root.addView(
                topBar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, barH + padV * 2));

        videoPane = new FrameLayout(this);
        int videoHeight = Math.round(density * 220f);
        videoHeightRc = videoHeight;
        videoHeightCli = Math.round(density * 120f);
        videoSurface = new SurfaceView(this);
        videoSurface.getHolder().addCallback(this);
        videoPane.addView(
                videoSurface,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, videoHeight));
        osd = new TextView(this);
        // Green bold monospace on a darkened box: readable over any video, columns line up.
        osd.setTextColor(0xff39ff6a);
        osd.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        osd.setTextSize(12f);
        osd.setBackgroundColor(0xb3000000);
        osd.setPadding(
                Math.round(density * 6f), Math.round(density * 4f),
                Math.round(density * 6f), Math.round(density * 4f));
        osd.setText(OsdPresenter.format(new OsdPresenter.Input(0, null, 0, null, null, -1, -1)));
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

        LinearLayout lcdPane = new LinearLayout(this);
        lcdPane.setOrientation(LinearLayout.VERTICAL);
        lcdView = new EdgeTxLcdView(this, null);
        lcdView.bind(control.adapter());
        edgeTxUi.wireLcdTouch(lcdView);
        lcdPane.addView(
                lcdView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout lcdNav = new LinearLayout(this);
        lcdNav.setOrientation(LinearLayout.HORIZONTAL);
        lcdNav.setPadding(padH, padV, padH, padV);
        lcdNav.setBackgroundColor(0xffe7edf0);
        int navKeyH = Math.round(density * 40f);
        edgeTxUi.wireNavKeys(lcdNav, navKeyH);
        lcdPane.addView(
                lcdNav,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, navKeyH + padV * 2));
        rcViews.add(lcdPane);
        root.addView(
                lcdPane,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        SwitchStripView upper = new SwitchStripView(this, null);
        edgeTxUi.wireSwitchRow(upper, 0);
        rcViews.add(upper);
        root.addView(
                upper,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (getResources().getDisplayMetrics().density * 34)));

        SwitchStripView lower = new SwitchStripView(this, null);
        edgeTxUi.wireSwitchRow(lower, 1);
        // Saved slot layout (Menu → Switches); also publishes the initial switch state.
        edgeTxUi.setSwitchConfig(SwitchConfigDialog.load(this));
        rcViews.add(lower);
        root.addView(
                lower,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (getResources().getDisplayMetrics().density * 34)));

        LinearLayout gimbals = new LinearLayout(this);
        gimbals.setOrientation(LinearLayout.HORIZONTAL);
        gimbals.addView(
                buildStickCluster(true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        gimbals.addView(
                buildStickCluster(false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        rcViews.add(gimbals);
        root.addView(
                gimbals,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        cliView = new CliView(this);
        // Consoles over the ground USB link (channel 4), not IP.
        cliView.setLink(usbSession.cliUsb());
        cliView.setVisibility(View.GONE);
        root.addView(
                cliView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        applySystemBarInsets(root);
        Log.i(
                TAG,
                "EdgeTX ["
                        + EdgeTxAdapterFactory.describe(edgeTx)
                        + "]: "
                        + edgeTx.status().detail());
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

    private StickClusterView buildStickCluster(boolean leftStick) {
        StickClusterView cluster = new StickClusterView(this, leftStick);
        // Mode 2: left stick Y is throttle, without spring (stays where released).
        cluster.gimbal().setHoldY(leftStick);
        edgeTxUi.wireGimbal(cluster.gimbal(), leftStick);
        edgeTxUi.wireTrim(cluster.hTrim());
        edgeTxUi.wireTrim(cluster.vTrim());
        return cluster;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (control != null) {
            control.setForeground(true);
            updatePowerButton();
            if (lcdView != null) {
                lcdView.setRadioPowered(control.radioPowered());
            }
        }
    }

    @Override
    protected void onPause() {
        if (control != null) {
            control.setForeground(false);
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private void applySystemBarInsets(View root) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        root.setOnApplyWindowInsetsListener(
                (v, insets) -> {
                    int top = insets.getSystemWindowInsetTop();
                    int bottom = insets.getSystemWindowInsetBottom();
                    v.setPadding(0, top, 0, bottom);
                    return insets;
                });
        root.requestApplyInsets();
    }

    /** "All files access": the storage root in Download/FlyingComputer, visible to the user. */
    private boolean allFilesAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || android.os.Environment.isExternalStorageManager();
    }

    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        try {
            startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (android.content.ActivityNotFoundException e) {
            startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void openMenu() {
        control.setMenuOpen(true);
        boolean[] handedOff = new boolean[1];
        new AlertDialog.Builder(this)
                .setTitle("Connection & EdgeTX")
                .setMessage(
                        "USB: "
                                + (currentDevice >= 0
                                        ? "ground connected"
                                        : "waiting for the ground (connects when plugged)")
                                + "\nUSB ch0 rtp-h264-v1 video, ch2 rc-v1 MSP (rover_sample 0x4201).\n"
                                + "CLI: USB ch4 cli-v1 ("
                                + (usbSession.cliUsb().isOpen() ? "open" : "not open")
                                + "; rov vs 5092, rov l3 2410, gnd l3 2400, rov fs 2440 on the ground).\n"
                                + "EdgeTX: "
                                + control.adapter().status().detail()
                                + "\nFiles: "
                                + AppStorage.root(this).getAbsolutePath()
                                + " (edgetx/sd, scp)"
                                + (allFilesAccess() ? "" : "\nGrant Files access to see them in Files / on a PC."))
                .setNeutralButton(
                        "Switches",
                        (d, w) -> {
                            handedOff[0] = true;
                            SwitchConfigDialog.show(
                                    this,
                                    edgeTxUi.switchConfig(),
                                    edgeTxUi::setSwitchConfig,
                                    () -> control.setMenuOpen(cliActive));
                        })
                .setPositiveButton("Close", null)
                .setNegativeButton(allFilesAccess() ? null : "Files access", (d, w) -> requestAllFilesAccess())
                // Driving stays blocked while the CLI (or the switch editor) is shown.
                .setOnDismissListener(
                        d -> {
                            if (!handedOff[0]) {
                                control.setMenuOpen(cliActive);
                            }
                        })
                .show();
    }

    /**
     * CLI replaces the RC controls below the video (video stays, smaller). Like
     * the menu, it puts control in the non-driving state (disarm) while shown.
     */
    private void setCliMode(boolean on) {
        if (on == cliActive) {
            return;
        }
        cliActive = on;
        // Driving blocked, but disarmed hold frames keep the rover link and telemetry alive.
        control.setLinkHold(on);
        control.setMenuOpen(on);
        for (View v : rcViews) {
            v.setVisibility(on ? View.GONE : View.VISIBLE);
        }
        cliView.setVisibility(on ? View.VISIBLE : View.GONE);
        int h = on ? videoHeightCli : videoHeightRc;
        ViewGroup.LayoutParams lp = videoPane.getLayoutParams();
        lp.height = h;
        videoPane.setLayoutParams(lp);
        ViewGroup.LayoutParams sp = videoSurface.getLayoutParams();
        sp.height = h;
        videoSurface.setLayoutParams(sp);
        if (!on) {
            View focused = getCurrentFocus();
            if (focused != null) {
                ((android.view.inputmethod.InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(focused.getWindowToken(), 0);
                focused.clearFocus();
            }
        }
    }

    private void toggleRadioPower() {
        try {
            control.setRadioPower(!control.radioPowered());
            updatePowerButton();
            if (lcdView != null) {
                lcdView.setRadioPowered(control.radioPowered());
            }
        } catch (Exception e) {
            showStatus("Power failed: " + e.getMessage());
        }
    }

    private void updatePowerButton() {
        powerButton.setText(control.radioPowered() ? "PWR ●" : "PWR ○");
    }

    private void submit(Runnable work) {
        try {
            usbSetup.execute(work);
        } catch (RejectedExecutionException ignored) {
            // Activity teardown already owns cleanup.
        }
    }

    private void showStatus(String text) {
        Log.i(TAG, text);
    }

    /** Connects to the ground P4 whenever it is plugged in; no menu action needed. */
    private void discoverUsb() {
        ui.removeCallbacks(usbRetry);
        if (!surfaceReady) {
            return;
        }
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getVendorId() == 0x303a && device.getProductId() == 0x4001) {
                if (device.getDeviceId() == currentDevice) {
                    return;
                }
                if (usbManager.hasPermission(device)) {
                    connectUsb(device);
                } else if (permissionPending != device.getDeviceId()) {
                    permissionPending = device.getDeviceId();
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

    /** After a USB error the ground may still be plugged: try again shortly. */
    private void scheduleUsbRetry() {
        ui.removeCallbacks(usbRetry);
        ui.postDelayed(usbRetry, USB_RETRY_MS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // singleTask: a USB attach while running lands here (permission comes with the intent).
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            discoverUsb();
        }
    }

    private void connectUsb(UsbDevice device) {
        submit(
                () -> {
                    // Attach broadcast and onNewIntent can both fire for one plug-in.
                    if (device.getDeviceId() == currentDevice) {
                        return;
                    }
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
                        ui.post(this::scheduleUsbRetry);
                    }
                });
    }

    private void disconnectUsb() {
        if (usbSession != null) {
            usbSession.close();
        }
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
        ui.removeCallbacks(usbRetry);
        unregisterReceiver(usbEvents);
        if (cliView != null) {
            cliView.detach();
        }
        surfaceReady = false;
        submit(this::disconnectUsb);
        usbSetup.shutdown();
        super.onDestroy();
    }
}
