package com.flyingcomputer.gndfull.cli;

import java.util.ArrayList;
import java.util.List;

/** Shell-style command history with an up/down cursor. */
public final class CommandHistory {
    private final int max;
    private final ArrayList<String> items = new ArrayList<>();
    private int cursor; // == items.size() when not browsing

    public CommandHistory(int max) {
        this.max = max;
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
