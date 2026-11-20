package com.flyingcomputer.gndfull.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;
import com.flyingcomputer.gndfull.switches.SwitchConfig;
import com.flyingcomputer.gndfull.switches.SwitchSlot;
import com.flyingcomputer.gndfull.switches.SwitchSlot.Output;
import com.flyingcomputer.gndfull.switches.SwitchSlot.Type;
import java.util.function.Consumer;

/** Menu → Switches: list of the eight slots; tap one to edit label, type, positions, output. */
public final class SwitchConfigDialog {
    private static final String PREFS = "switches";
    private static final String KEY = "slots";

    private SwitchConfigDialog() {}

    public static SwitchConfig load(Context ctx) {
        return SwitchConfig.decode(prefs(ctx).getString(KEY, null));
    }

    public static void save(Context ctx, SwitchConfig config) {
        prefs(ctx).edit().putString(KEY, config.encode()).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Shows the slot list. {@code onChange} gets every saved change (already persisted);
     * {@code onDismiss} runs when the list closes.
     */
    public static void show(
            Context ctx, SwitchConfig current, Consumer<SwitchConfig> onChange, Runnable onDismiss) {
        String[] items = new String[SwitchConfig.SLOTS];
        for (int i = 0; i < SwitchConfig.SLOTS; i++) {
            String row = i < SwitchConfig.PER_ROW ? "upper " : "lower ";
            items[i] =
                    (i + 1) + " (" + row + (i % SwitchConfig.PER_ROW + 1) + ")  "
                            + current.slot(i).describe();
        }
        String dup = current.duplicateOutputs();
        AlertDialog.Builder b =
                new AlertDialog.Builder(ctx)
                        .setTitle(dup.isEmpty() ? "Switches" : "Switches — shared output: " + dup)
                        .setItems(
                                items,
                                (d, which) ->
                                        editSlot(
                                                ctx,
                                                current,
                                                which,
                                                next -> {
                                                    save(ctx, next);
                                                    onChange.accept(next);
                                                    show(ctx, next, onChange, onDismiss);
                                                },
                                                () -> show(ctx, current, onChange, onDismiss)))
                        .setNeutralButton(
                                "Defaults",
                                (d, w) -> {
                                    SwitchConfig def = SwitchConfig.defaults();
                                    save(ctx, def);
                                    onChange.accept(def);
                                    onDismiss.run();
                                })
                        .setPositiveButton("Close", (d, w) -> onDismiss.run())
                        .setOnCancelListener(d -> onDismiss.run());
        b.show();
    }

    private static void editSlot(
            Context ctx,
            SwitchConfig config,
            int index,
            Consumer<SwitchConfig> onSave,
            Runnable onBack) {
        SwitchSlot slot = config.slot(index);
        float density = ctx.getResources().getDisplayMetrics().density;
        int pad = Math.round(density * 16f);
        LinearLayout form = new LinearLayout(ctx);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(pad, pad / 2, pad, 0);

        form.addView(caption(ctx, "Label (max 6)"));
        EditText label = new EditText(ctx);
        label.setSingleLine(true);
        label.setFilters(new InputFilter[] {new InputFilter.LengthFilter(6)});
        label.setText(slot.label());
        form.addView(label);

        form.addView(caption(ctx, "Type"));
        Spinner type = new Spinner(ctx);
        String[] typeLabels = {"push (0|1, momentary)", "toggle (0,1)", "mpos (0..n-1)"};
        type.setAdapter(
                new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, typeLabels));
        type.setSelection(slot.type().ordinal());
        form.addView(type);

        TextView countCaption = caption(ctx, "Positions (n)");
        form.addView(countCaption);
        NumberPicker count = new NumberPicker(ctx);
        count.setMinValue(2);
        count.setMaxValue(SwitchSlot.MAX_POSITIONS);
        count.setValue(slot.type() == Type.MPOS ? slot.positions() : 3);
        count.setWrapSelectorWheel(false);
        form.addView(count);

        form.addView(caption(ctx, "EdgeTX input (SA..SH switch, S1 S2 LS RS axis)"));
        Spinner output = new Spinner(ctx);
        String[] outLabels = new String[Output.values().length];
        for (int i = 0; i < outLabels.length; i++) {
            Output o = Output.values()[i];
            outLabels[i] = o.name() + (o.analog ? " (axis)" : " (switch)");
        }
        output.setAdapter(
                new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, outLabels));
        output.setSelection(slot.output().ordinal());
        form.addView(output);

        TextView error = caption(ctx, "");
        error.setTextColor(0xffc62828);
        form.addView(error);

        Runnable refresh =
                () -> {
                    boolean mpos = type.getSelectedItemPosition() == Type.MPOS.ordinal();
                    countCaption.setVisibility(mpos ? View.VISIBLE : View.GONE);
                    count.setVisibility(mpos ? View.VISIBLE : View.GONE);
                    String err =
                            SwitchSlot.check(
                                    Type.values()[type.getSelectedItemPosition()],
                                    count.getValue(),
                                    Output.values()[output.getSelectedItemPosition()]);
                    error.setText(err == null ? "" : err);
                };
        AdapterView.OnItemSelectedListener onSel =
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        refresh.run();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> p) {}
                };
        type.setOnItemSelectedListener(onSel);
        output.setOnItemSelectedListener(onSel);
        count.setOnValueChangedListener((p, o, n) -> refresh.run());
        refresh.run();

        android.widget.ScrollView scroll = new android.widget.ScrollView(ctx);
        scroll.addView(form);
        AlertDialog dialog =
                new AlertDialog.Builder(ctx)
                        .setTitle("Slot " + (index + 1))
                        .setView(scroll)
                        .setPositiveButton("Save", null)
                        .setNegativeButton("Back", (d, w) -> onBack.run())
                        .setOnCancelListener(d -> onBack.run())
                        .create();
        dialog.setOnShowListener(
                d ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            Type t = Type.values()[type.getSelectedItemPosition()];
                                            Output o =
                                                    Output.values()[
                                                            output.getSelectedItemPosition()];
                                            String err = SwitchSlot.check(t, count.getValue(), o);
                                            if (err != null) {
                                                error.setText(err);
                                                return;
                                            }
                                            SwitchSlot next =
                                                    new SwitchSlot(
                                                            label.getText().toString(),
                                                            t,
                                                            count.getValue(),
                                                            o);
                                            dialog.dismiss();
                                            onSave.accept(config.with(index, next));
                                        }));
        dialog.show();
    }

    private static TextView caption(Context ctx, String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(12f);
        return t;
    }
}
