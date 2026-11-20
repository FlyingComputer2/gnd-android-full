package com.flyingcomputer.gndfull.files;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.flyingcomputer.gndfull.AppStorage;
import com.flyingcomputer.gndfull.cli.ScpConsole;
import com.flyingcomputer.gndfull.udpfs.UdpfsClient;
import com.flyingcomputer.gndfull.udpfs.UdpfsException;
import com.flyingcomputer.gndfull.udpfs.UdpfsWire;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * FILES mode: the rover's files over udpfs (ground 2440, the same console as the CLI's "rov fs").
 * Toolbar [^] parent, [<] back, path, [go], [upload]; columns file / type / modified, tap a header
 * to sort; tap a directory to open it, long-press an entry for download (to scp/), crc32, rename,
 * move, delete. One operation at a time, shared with "rov fs". UI-thread only; the work runs on
 * the console's worker.
 */
public final class FilesView extends LinearLayout {
    /** The activity's side: a document picker for upload, answered with {@link #onUploadPicked}. */
    public interface Host {
        void pickUpload();
    }

    private static final int BG = 0xff10161a;
    private static final int FG = 0xffc8d6df;
    private static final int DIM = 0xff708090;
    private static final int DIR = 0xff4fc3f7;
    private static final int HEADER_BG = 0xff1b252b;
    private static final int ERR = 0xffff8a80;
    private static final String START = "/sdcard";
    /** The picker backgrounds the app, which drops the USB link until it resumes. */
    private static final long UPLOAD_AWAIT_MS = 15000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float density;
    private final EditText pathInput;
    private final TextView status;
    private final TextView[] headers = new TextView[FileListing.Column.values().length];
    private final Rows adapter = new Rows();
    private final Deque<String> back = new ArrayDeque<>();
    private final List<FileListing.Entry> entries = new ArrayList<>();
    private ScpConsole fs;
    private Host host;
    private String cwd = START;
    private boolean loaded;
    private FileListing.Column sortBy = FileListing.Column.NAME;
    private boolean ascending = true;

    public FilesView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        density = getResources().getDisplayMetrics().density;

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(2), dp(2), dp(2), dp(2));
        bar.addView(button(context, "^", v -> open(FileListing.parent(cwd), true)));
        bar.addView(button(context, "<", v -> goBack()));
        pathInput = new EditText(context);
        pathInput.setSingleLine(true);
        pathInput.setTextSize(13f);
        pathInput.setTypeface(Typeface.MONOSPACE);
        pathInput.setTextColor(FG);
        pathInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        pathInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        pathInput.setText(cwd);
        pathInput.setOnEditorActionListener(
                (v, actionId, event) -> {
                    goTyped();
                    return true;
                });
        bar.addView(pathInput, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(button(context, "go", v -> goTyped()));
        bar.addView(button(context, "upload", v -> upload()));
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(HORIZONTAL);
        head.setBackgroundColor(HEADER_BG);
        head.setPadding(dp(8), dp(6), dp(8), dp(6));
        for (FileListing.Column c : FileListing.Column.values()) {
            TextView h = cell(context, "", DIM);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            h.setOnClickListener(v -> sortBy(c));
            headers[c.ordinal()] = h;
            head.addView(h, columnParams(c));
        }
        addView(head, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView list = new ListView(context);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setOnItemClickListener((p, v, pos, id) -> onTap(pos));
        list.setOnItemLongClickListener((p, v, pos, id) -> onLongPress(pos));
        addView(list, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(context);
        status.setTextSize(11f);
        status.setTypeface(Typeface.MONOSPACE);
        status.setTextColor(DIM);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.START);
        status.setPadding(dp(8), dp(3), dp(8), dp(3));
        addView(status, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateHeaders();
    }

    /** The "rov fs" console (CliView.fs()): its link, worker and transfer code. */
    public void setConsole(ScpConsole fs, Host host) {
        this.fs = fs;
        this.host = host;
    }

    /** Called when FILES mode is shown: the first time lists the start directory. */
    public void onShown() {
        if (!loaded) {
            open(cwd, false);
        }
    }

    // ---- navigation ----

    private void goTyped() {
        hideKeyboard();
        open(FileListing.normalize(pathInput.getText().toString()), true);
    }

    private void goBack() {
        if (back.isEmpty()) {
            setStatus("no earlier directory", false);
            return;
        }
        open(back.peek(), false);
    }

    /** Lists path; on success it becomes the current directory (history: push the old one). */
    private void open(String path, boolean pushHistory) {
        String p = FileListing.normalize(path);
        setStatus("listing " + p + " …", false);
        run(
                c -> {
                    List<FileListing.Entry> got = list(c, p);
                    ui.post(() -> show(p, got, pushHistory));
                });
    }

    private void show(String path, List<FileListing.Entry> got, boolean pushHistory) {
        if (pushHistory && !path.equals(cwd)) {
            back.push(cwd);
        } else if (!pushHistory && !back.isEmpty() && back.peek().equals(path)) {
            back.pop();
        }
        loaded = true;
        cwd = path;
        pathInput.setText(path);
        entries.clear();
        entries.addAll(got);
        FileListing.sort(entries, sortBy, ascending);
        adapter.notifyDataSetChanged();
        setStatus(path + "  " + got.size() + " entries", false);
    }

    /** Worker: the directory with stat; "/" falls back to the known filesystems. */
    private static List<FileListing.Entry> list(UdpfsClient c, String path) throws IOException {
        List<FileListing.Entry> out = new ArrayList<>();
        try {
            for (UdpfsWire.DirEntry e : c.list(path, true)) {
                if (!e.name().equals(".") && !e.name().equals("..")) {
                    out.add(new FileListing.Entry(e.name(), e.type(), e.size(), e.mtime()));
                }
            }
        } catch (UdpfsException e) {
            if (!path.equals("/")) {
                throw e;
            }
        }
        if (path.equals("/") && out.isEmpty()) {
            for (String r : FileListing.ROOTS) {
                out.add(new FileListing.Entry(r, UdpfsWire.TYPE_DIR, 0, 0));
            }
        }
        return out;
    }

    private void sortBy(FileListing.Column c) {
        if (c == sortBy) {
            ascending = !ascending;
        } else {
            sortBy = c;
            ascending = c != FileListing.Column.MODIFIED; // newest first
        }
        FileListing.sort(entries, sortBy, ascending);
        adapter.notifyDataSetChanged();
        updateHeaders();
    }

    private void updateHeaders() {
        String[] names = {"file", "type", "modified"};
        for (FileListing.Column c : FileListing.Column.values()) {
            headers[c.ordinal()].setText(
                    names[c.ordinal()] + (c == sortBy ? (ascending ? " ▲" : " ▼") : ""));
        }
    }

    // ---- rows: [..], [.], then the entries ----

    private static final int ROW_UP = 0, ROW_SELF = 1, FIRST_ENTRY = 2;

    private void onTap(int pos) {
        if (pos == ROW_UP) {
            open(FileListing.parent(cwd), true);
        } else if (pos == ROW_SELF) {
            open(cwd, false);
        } else {
            FileListing.Entry e = entries.get(pos - FIRST_ENTRY);
            if (e.dir()) {
                open(FileListing.child(cwd, e.name()), true);
            } else {
                setStatus(e.name() + "  " + FileListing.size(e.size()) + " — long-press for actions", false);
            }
        }
    }

    private boolean onLongPress(int pos) {
        if (pos < FIRST_ENTRY) {
            return false;
        }
        FileListing.Entry e = entries.get(pos - FIRST_ENTRY);
        String path = FileListing.child(cwd, e.name());
        boolean top = cwd.equals("/");
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(e.dir() ? "download (all)" : "download");
        actions.add(() -> download(path));
        if (!e.dir()) {
            labels.add("crc32");
            actions.add(() -> crc(path, e.size()));
        }
        if (!top) { // the filesystems themselves stay put
            labels.add("rename");
            actions.add(() -> rename(e, path));
            labels.add("move");
            actions.add(() -> move(e, path));
            labels.add("delete");
            actions.add(() -> delete(e, path));
        }
        new AlertDialog.Builder(getContext())
                .setTitle(e.name() + (e.dir() ? "/" : "  (" + FileListing.size(e.size()) + ")"))
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
        return true;
    }

    // ---- file menu ----

    private void download(String path) {
        setStatus("download " + path + " …", false);
        run(
                c -> {
                    File dir = new File(fs.storageRoot(), AppStorage.SCP);
                    long t0 = System.nanoTime();
                    long n = fs.getInto(c, path, dir, line -> ui.post(() -> setStatus(line, false)));
                    double s = Math.max((System.nanoTime() - t0) / 1e9, 0.001);
                    String msg = String.format(Locale.US, "downloaded %s to %s/ (%s, %.1f KB/s, CRC ok)",
                            ScpConsole.baseName(path), AppStorage.SCP, FileListing.size(n), n / 1024.0 / s);
                    ui.post(() -> setStatus(msg, false));
                });
    }

    private void crc(String path, long size) {
        setStatus("crc32 " + path + " …", false);
        run(
                c -> {
                    long crc = ScpConsole.crc(c, path);
                    String hex = String.format(Locale.US, "%08x", crc);
                    ui.post(
                            () -> {
                                setStatus("crc32 " + hex + "  " + path, false);
                                new AlertDialog.Builder(getContext())
                                        .setTitle("crc32")
                                        .setMessage(hex + "\n" + size + " B\n" + path)
                                        .setPositiveButton("OK", null)
                                        .show();
                            });
                });
    }

    private void rename(FileListing.Entry e, String path) {
        prompt("rename", e.name(), name -> {
            if (name.isEmpty() || name.contains("/") || name.equals(e.name())) {
                setStatus(name.contains("/") ? "rename: a name, not a path (use move)" : "rename: unchanged", !name.equals(e.name()));
                return;
            }
            String to = FileListing.child(cwd, name);
            mutate("rename " + path + " → " + to, c -> c.rename(path, to));
        });
    }

    private void move(FileListing.Entry e, String path) {
        prompt("move to (directory or new path)", cwd + "/", typed -> {
            String dst = FileListing.normalize(typed);
            mutate("move " + path + " → " + dst, c -> {
                String to = dst;
                try {
                    if (c.stat(dst).type() == UdpfsWire.TYPE_DIR) {
                        to = FileListing.child(dst, e.name());
                    }
                } catch (UdpfsException x) {
                    if (x.errno != UdpfsWire.ENOENT) {
                        throw x;
                    }
                }
                if (to.equals(path)) {
                    return;
                }
                c.rename(path, to);
            });
        });
    }

    private void delete(FileListing.Entry e, String path) {
        new AlertDialog.Builder(getContext())
                .setTitle("delete")
                .setMessage("Delete " + path + (e.dir() ? "/ ? (directory must be empty)" : " ?"))
                .setPositiveButton("delete", (d, w) -> mutate("delete " + path, c -> {
                    if (e.dir()) {
                        c.rmdir(path);
                    } else {
                        c.unlink(path);
                    }
                }))
                .setNegativeButton("cancel", null)
                .show();
    }

    // ---- upload ----

    private void upload() {
        if (cwd.equals("/")) {
            setStatus("upload: open /sdcard or /winject first", true);
            return;
        }
        if (host != null) {
            host.pickUpload();
        }
    }

    /** The picked document goes into the current directory under its own name, CRC-checked. */
    public void onUploadPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        String dir = cwd;
        Context app = getContext().getApplicationContext();
        setStatus("upload to " + dir + " … (waiting for the ground link)", false);
        run(
                UPLOAD_AWAIT_MS,
                c -> {
                    String name = displayName(app, uri);
                    File tmpDir = new File(app.getCacheDir(), "upload");
                    if (!tmpDir.isDirectory() && !tmpDir.mkdirs()) {
                        throw new IOException("cannot create " + tmpDir);
                    }
                    File tmp = new File(tmpDir, name);
                    try {
                        try (InputStream in = app.getContentResolver().openInputStream(uri);
                                OutputStream out = new FileOutputStream(tmp)) {
                            if (in == null) {
                                throw new IOException("cannot read " + uri);
                            }
                            byte[] buf = new byte[64 * 1024];
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                out.write(buf, 0, n);
                            }
                        }
                        long t0 = System.nanoTime();
                        long n = fs.put(c, tmp, dir, line -> ui.post(() -> setStatus(line, false)));
                        double s = Math.max((System.nanoTime() - t0) / 1e9, 0.001);
                        List<FileListing.Entry> got = list(c, dir);
                        String msg = String.format(Locale.US, "uploaded %s (%s, %.1f KB/s, CRC ok)",
                                name, FileListing.size(n), n / 1024.0 / s);
                        ui.post(() -> {
                            if (dir.equals(cwd)) {
                                show(dir, got, false);
                            }
                            setStatus(msg, false);
                        });
                    } finally {
                        tmp.delete();
                    }
                });
    }

    private static String displayName(Context context, Uri uri) {
        String name = null;
        try (Cursor cur = context.getContentResolver()
                .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                name = cur.getString(0);
            }
        } catch (RuntimeException ignored) {
            // fall back to the path's last part
        }
        if (name == null || name.isEmpty()) {
            name = uri.getLastPathSegment();
        }
        if (name == null || name.isEmpty()) {
            name = "upload.bin";
        }
        return name.replace('/', '_');
    }

    // ---- plumbing ----

    /** Runs a change, then re-lists the current directory. */
    private void mutate(String what, ScpConsole.Job change) {
        String dir = cwd;
        setStatus(what + " …", false);
        run(
                c -> {
                    change.run(c);
                    List<FileListing.Entry> got = list(c, dir);
                    ui.post(() -> {
                        if (dir.equals(cwd)) {
                            show(dir, got, false);
                        }
                        setStatus(what + ": ok", false);
                    });
                });
    }

    private void run(ScpConsole.Job job) {
        run(0, job);
    }

    private void run(long awaitOpenMs, ScpConsole.Job job) {
        ScpConsole c = fs;
        if (c == null) {
            setStatus("not connected", true);
            return;
        }
        if (!c.submitJob(job, e -> ui.post(() -> setStatus("! " + e.getMessage(), true)), awaitOpenMs)) {
            setStatus("busy: one operation at a time (also rov fs in the CLI)", true);
        }
    }

    private interface Text {
        void accept(String s);
    }

    private void prompt(String title, String initial, Text done) {
        EditText in = new EditText(getContext());
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setText(initial);
        in.setSelection(initial.length());
        new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setView(in)
                .setPositiveButton("OK", (d, w) -> done.accept(in.getText().toString().trim()))
                .setNegativeButton("cancel", null)
                .show();
    }

    private void setStatus(String s, boolean error) {
        status.setText(s);
        status.setTextColor(error ? ERR : DIM);
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(pathInput.getWindowToken(), 0);
        pathInput.clearFocus();
    }

    private Button button(Context context, String label, View.OnClickListener l) {
        Button b = new Button(context);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13f);
        b.setMinWidth(0);
        b.setMinimumWidth(dp(40));
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setOnClickListener(l);
        return b;
    }

    private TextView cell(Context context, String text, int color) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(13f);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        return t;
    }

    private LayoutParams columnParams(FileListing.Column c) {
        return switch (c) {
            case NAME -> new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            case TYPE -> new LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT);
            case MODIFIED -> new LayoutParams(dp(124), ViewGroup.LayoutParams.WRAP_CONTENT);
        };
    }

    private final class Rows extends BaseAdapter {
        @Override
        public int getCount() {
            return FIRST_ENTRY + entries.size();
        }

        @Override
        public Object getItem(int pos) {
            return pos < FIRST_ENTRY ? null : entries.get(pos - FIRST_ENTRY);
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout row = (LinearLayout) convert;
            if (row == null) {
                Context ctx = parent.getContext();
                row = new LinearLayout(ctx);
                row.setOrientation(HORIZONTAL);
                row.setPadding(dp(8), dp(9), dp(8), dp(9));
                for (FileListing.Column c : FileListing.Column.values()) {
                    TextView t = cell(ctx, "", FG);
                    if (c != FileListing.Column.NAME) {
                        t.setTextSize(12f);
                    }
                    row.addView(t, columnParams(c));
                }
            }
            TextView name = (TextView) row.getChildAt(0);
            TextView type = (TextView) row.getChildAt(1);
            TextView mod = (TextView) row.getChildAt(2);
            if (pos < FIRST_ENTRY) {
                name.setText(pos == ROW_UP ? ".." : ".");
                name.setTextColor(DIR);
                type.setText("dir");
                type.setTextColor(DIM);
                mod.setText("");
            } else {
                FileListing.Entry e = entries.get(pos - FIRST_ENTRY);
                name.setText(e.dir() ? e.name() + "/" : e.name());
                name.setTextColor(e.dir() ? DIR : FG);
                type.setText(FileListing.typeLabel(e));
                type.setTextColor(DIM);
                mod.setText(FileListing.modified(e.mtime()));
                mod.setTextColor(DIM);
            }
            return row;
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
