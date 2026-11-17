package com.flyingcomputer.gndfull.cli;

import java.util.ArrayDeque;

/** Bounded scrollback for one terminal pane: keeps the newest {@code maxLines}. */
public final class LineBuffer {
    private final int maxLines;
    private final ArrayDeque<String> lines = new ArrayDeque<>();

    public LineBuffer(int maxLines) {
        if (maxLines <= 0) {
            throw new IllegalArgumentException("maxLines");
        }
        this.maxLines = maxLines;
    }

    /** Appends {@code text}, one line per '\n'; a trailing newline adds no empty line. */
    public void append(String text) {
        String[] parts = text.split("\n", -1);
        int n = parts.length;
        if (n > 1 && parts[n - 1].isEmpty()) {
            n--;
        }
        for (int i = 0; i < n; i++) {
            lines.addLast(parts[i].replace("\r", ""));
            if (lines.size() > maxLines) {
                lines.removeFirst();
            }
        }
    }

    public void clear() {
        lines.clear();
    }

    public int size() {
        return lines.size();
    }

    @Override
    public String toString() {
        return String.join("\n", lines);
    }
}
