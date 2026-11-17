package com.flyingcomputer.gndfull.cli;

import java.util.ArrayList;
import java.util.List;

/** Shell-style command history with an up/down cursor, optionally kept across restarts. */
public final class CommandHistory {
    /** Where the history is kept between app runs (oldest first). */
    public interface Store {
        List<String> load();

        void save(List<String> oldestFirst);
    }

    private final int max;
    private final ArrayList<String> items = new ArrayList<>();
    private final Store store;
    private int cursor; // == items.size() when not browsing

    public CommandHistory(int max) {
        this(max, null);
    }

    /** Loads the stored history (the newest {@code max}); every add saves it back. */
    public CommandHistory(int max, Store store) {
        this.max = max;
        this.store = store;
        if (store != null) {
            for (String s : store.load()) {
                if (s != null && !s.isEmpty()) {
                    items.add(s);
                }
            }
            while (items.size() > max) {
                items.remove(0);
            }
        }
        cursor = items.size();
    }

    /** Snapshot, newest first (for completion; safe from any thread). */
    public synchronized List<String> newestFirst() {
        ArrayList<String> out = new ArrayList<>(items.size());
        for (int i = items.size() - 1; i >= 0; i--) {
            out.add(items.get(i));
        }
        return out;
    }

    /** Records a sent command (skips repeats of the newest) and resets the cursor. */
    public synchronized void add(String cmd) {
        if (!cmd.isEmpty() && (items.isEmpty() || !items.get(items.size() - 1).equals(cmd))) {
            items.add(cmd);
            if (items.size() > max) {
                items.remove(0);
            }
            if (store != null) {
                store.save(new ArrayList<>(items));
            }
        }
        cursor = items.size();
    }

    /** Older entry, or null at the oldest / when empty. */
    public synchronized String previous() {
        if (cursor == 0) {
            return items.isEmpty() ? null : items.get(0);
        }
        return items.get(--cursor);
    }

    /** Newer entry, or "" past the newest. */
    public synchronized String next() {
        if (cursor >= items.size() - 1) {
            cursor = items.size();
            return "";
        }
        return items.get(++cursor);
    }
}
