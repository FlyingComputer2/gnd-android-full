package com.flyingcomputer.gndfull.cli;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.flyingcomputer.gndfull.transport.CliUsbHandler;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI mode: three terminal columns (rover vstreamer, rover l3, ground l3), tap a
 * column to focus it (it widens), one input line for the focused console and a
 * 3-column fast-type grid of that console's common commands. Long-press a column
 * to clear it. Lines go over the ground USB link (channel 4, {@link CliUsbHandler}); replies
 * are routed to the pane whose console port they came from. UI-thread only, except the link's
 * listener, which posts here.
 */
public final class CliView extends LinearLayout {
    private static final int MAX_LINES = 400;
    private static final long REPLY_TIMEOUT_MS = 2000;
    private static final float FOCUSED_WEIGHT = 1.8f;
    private static final int BG = 0xff10161a;
    private static final int FG = 0xffc8d6df;
    private static final int BORDER = 0xff2a3a44;
    private static final int BORDER_FOCUS = 0xff4fc3f7;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ConsoleTarget[] targets = ConsoleTarget.values();
    private final Pane[] panes = new Pane[targets.length];
    private static final int MAX_COMPLETIONS = 8;
    private final AutoCompleteTextView input;
    private final GridLayout fastGrid;
    private final float density;
    private volatile int focus; // read by the completion filter thread
    private CliUsbHandler link;

    private final class Pane {
        final ConsoleTarget target;
        final LineBuffer buffer = new LineBuffer(MAX_LINES);
        final CommandHistory history = new CommandHistory(100);
        final ScrollView scroll;
        final TextView text;
        final TextView title;
        final LinearLayout box;
        final Runnable timeout;

        Pane(ConsoleTarget target, LinearLayout box, TextView title, ScrollView scroll, TextView text) {
            this.target = target;
            this.box = box;
            this.title = title;
            this.scroll = scroll;
            this.text = text;
            this.timeout = () -> print("… no reply (" + REPLY_TIMEOUT_MS / 1000 + " s)");
        }

        void print(String s) {
            buffer.append(s);
            text.setText(buffer.toString());
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    public CliView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        density = getResources().getDisplayMetrics().density;
        int pad = dp(3);

        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(HORIZONTAL);
        for (int i = 0; i < targets.length; i++) {
            panes[i] = buildPane(context, i);
            columns.addView(panes[i].box, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        addView(columns, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(pad, pad, pad, pad);
        input = new AutoCompleteTextView(context);
        input.setSingleLine(true);
        // Dropdown of prefix matches (history first, then the console's commands).
        input.setThreshold(1);
        input.setAdapter(new CompletionAdapter(context));
        input.setOnItemClickListener((parent, view, position, id) ->
                input.setSelection(input.getText().length()));
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(13f);
        input.setTextColor(FG);
        input.setHintTextColor(0xff607080);
        // Plain text: the keyboard shows its typing suggestions. Set explicitly so
        // AutoCompleteTextView's FLAG_AUTO_COMPLETE does not hide them.
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                sendInput();
                return true;
            }
            return false;
        });
        inputRow.addView(input, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(smallButton(context, "⇥", v -> tabComplete()));
        inputRow.addView(smallButton(context, "↑", v -> recall(panes[focus].history.previous())));
        inputRow.addView(smallButton(context, "↓", v -> recall(panes[focus].history.next())));
        inputRow.addView(smallButton(context, "⏎", v -> sendInput()));
        addView(inputRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fastGrid = new GridLayout(context);
        fastGrid.setColumnCount(3);
        fastGrid.setPadding(pad, 0, pad, pad);
        addView(fastGrid, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setFocus(0);
    }

    private Pane buildPane(Context context, int index) {
        ConsoleTarget target = targets[index];
        // Any touch in the column focuses it, whatever child is under the finger.
        LinearLayout box = new LinearLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && focus != index) {
                    setFocus(index);
                }
                return false;
            }
        };
        box.setOrientation(VERTICAL);
        int m = dp(2);
        box.setPadding(m, m, m, m);

        TextView title = new TextView(context);
        title.setTextSize(11f);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        // The text fills the column, so long-press-to-clear works on empty space too.
        scroll.setFillViewport(true);
        TextView text = new TextView(context);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(9f);
        text.setTextColor(FG);
        text.setTextIsSelectable(false);
        scroll.addView(text);
        box.addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Pane pane = new Pane(target, box, title, scroll, text);
        View.OnLongClickListener clearIt = v -> {
            pane.buffer.clear();
            pane.text.setText("");
            setFocus(index);
            return true;
        };
        for (View v : new View[] {box, title, text}) {
            v.setOnLongClickListener(clearIt);
        }
        return pane;
    }

    private Button smallButton(Context context, String label, View.OnClickListener l) {
        Button b = new Button(context);
        b.setText(label);
        b.setTextSize(13f);
        b.setMinWidth(0);
        b.setMinimumWidth(dp(40));
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setOnClickListener(l);
        return b;
    }

    /** The ground USB console link; replies are routed by console port. */
    public void setLink(CliUsbHandler link) {
        this.link = link;
        link.setListener((port, text) -> ui.post(() -> onReply(port, text)));
    }

    private void onReply(int port, String text) {
        for (Pane p : panes) {
            if (p.target.port == port) {
                ui.removeCallbacks(p.timeout);
                p.print(text);
                return;
            }
        }
        panes[focus].print(":" + port + " " + text);
    }

    private void setFocus(int index) {
        focus = index;
        for (int i = 0; i < panes.length; i++) {
            Pane p = panes[i];
            boolean on = i == index;
            GradientDrawable border = new GradientDrawable();
            border.setColor(BG);
            border.setStroke(dp(on ? 2 : 1), on ? BORDER_FOCUS : BORDER);
            p.box.setBackground(border);
            p.title.setTextColor(on ? BORDER_FOCUS : 0xff708090);
            p.title.setText(p.target.label + " :" + p.target.port);
            LayoutParams lp = (LayoutParams) p.box.getLayoutParams();
            if (lp != null) {
                lp.weight = on ? FOCUSED_WEIGHT : 1f;
                p.box.setLayoutParams(lp);
            }
        }
        input.setHint(panes[index].target.label + "> ");
        buildFastKeys();
    }

    private void buildFastKeys() {
        fastGrid.removeAllViews();
        int h = dp(34);
        for (String key : panes[focus].target.fastKeys) {
            Button b = new Button(getContext());
            b.setAllCaps(false);
            b.setText(key);
            b.setTextSize(11f);
            b.setTypeface(Typeface.MONOSPACE);
            b.setSingleLine(true);
            b.setPadding(dp(2), 0, dp(2), 0);
            b.setMinHeight(0);
            b.setMinimumHeight(h);
            // Tap: complete commands go out at once; the rest wait for an argument.
            b.setOnClickListener(v -> {
                if (FastKeys.sendsImmediately(key)) {
                    send(key);
                } else {
                    recall(FastKeys.insert(input.getText().toString(), key));
                    input.requestFocus();
                }
            });
            // Long press: always just insert, to edit before sending.
            b.setOnLongClickListener(v -> {
                recall(FastKeys.insert(input.getText().toString(), key));
                input.requestFocus();
                return true;
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = h;
            fastGrid.addView(b, lp);
        }
    }

    private void recall(String line) {
        if (line == null) {
            return;
        }
        // No dropdown for text set programmatically (history, fast keys).
        input.setText(line, false);
        input.setSelection(line.length());
    }

    /** Matches for {@code prefix} on the focused console (any thread). */
    private List<String> matches(String prefix) {
        Pane p = panes[focus];
        return Completions.match(prefix, p.history.newestFirst(), p.target.completions, MAX_COMPLETIONS);
    }

    /** Shell Tab: extend to the common prefix of all matches, else show them. */
    private void tabComplete() {
        String line = input.getText().toString();
        String done = Completions.complete(line,
                Completions.match(line, panes[focus].history.newestFirst(), panes[focus].target.completions,
                        Integer.MAX_VALUE));
        if (!done.equals(line)) {
            recall(done);
        } else if (input.getAdapter() != null) {
            input.showDropDown();
        }
        input.requestFocus();
    }

    /** Adapter whose filter is Completions.match on the whole line. */
    private final class CompletionAdapter extends ArrayAdapter<String> {
        private final Filter filter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults r = new FilterResults();
                List<String> m = constraint == null ? new ArrayList<>() : matches(constraint.toString());
                r.values = m;
                r.count = m.size();
                return r;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                clear();
                if (results.values != null) {
                    addAll((List<String>) results.values);
                }
                notifyDataSetChanged();
            }
        };

        CompletionAdapter(Context context) {
            super(context, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        }

        @Override
        public Filter getFilter() {
            return filter;
        }
    }

    private void sendInput() {
        String line = input.getText().toString().trim();
        if (line.isEmpty()) {
            return;
        }
        input.setText("", false);
        input.dismissDropDown();
        send(line);
    }

    private void send(String line) {
        Pane p = panes[focus];
        p.history.add(line);
        p.print("> " + line);
        CliUsbHandler l = link;
        if (l == null || !l.isOpen()) {
            p.print("! USB CLI not open: connect the ground (needs gnd-esp32p4 with USB channel 4)");
            return;
        }
        if (!l.send(p.target.port, line)) {
            p.print("! USB CLI busy, not sent");
            return;
        }
        ui.removeCallbacks(p.timeout);
        ui.postDelayed(p.timeout, REPLY_TIMEOUT_MS);
    }

    /** Stops routing replies here (the link outlives this view's activity). */
    public void detach() {
        for (Pane p : panes) {
            ui.removeCallbacks(p.timeout);
        }
        if (link != null) {
            link.setListener(null);
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
