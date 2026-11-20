package com.flyingcomputer.gndfull.files;

import com.flyingcomputer.gndfull.udpfs.UdpfsWire;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The FILES explorer's model without Android: rover paths, the type column and sorting. */
public final class FileListing {
    /** The rover's udpfs filesystems; ESP-IDF's VFS cannot list "/" itself. */
    public static final List<String> ROOTS = List.of("sdcard", "winject");

    public enum Column {
        NAME,
        TYPE,
        MODIFIED
    }

    public record Entry(String name, int type, long size, long mtime) {
        public boolean dir() {
            return type == UdpfsWire.TYPE_DIR;
        }
    }

    private FileListing() {}

    /** An absolute path without "." / ".." / empty parts or a trailing '/'; "/" for the root. */
    public static String normalize(String path) {
        String p = path == null ? "" : path.trim();
        List<String> parts = new ArrayList<>();
        for (String s : p.split("/")) {
            if (s.isEmpty() || s.equals(".")) {
                continue;
            }
            if (s.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
                continue;
            }
            parts.add(s);
        }
        return "/" + String.join("/", parts);
    }

    public static String parent(String path) {
        String p = normalize(path);
        int i = p.lastIndexOf('/');
        return i <= 0 ? "/" : p.substring(0, i);
    }

    public static String child(String dir, String name) {
        return normalize(dir + "/" + name);
    }

    /** "dir", the extension in lower case, or "file". */
    public static String typeLabel(Entry e) {
        if (e.dir()) {
            return "dir";
        }
        if (e.type() != UdpfsWire.TYPE_FILE) {
            return "other";
        }
        int i = e.name().lastIndexOf('.');
        return i > 0 && i < e.name().length() - 1 ? e.name().substring(i + 1).toLowerCase(Locale.US) : "file";
    }

    public static String modified(long mtime) {
        return mtime <= 0 ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(mtime * 1000L));
    }

    public static String size(long n) {
        if (n < 1024) {
            return n + " B";
        }
        if (n < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", n / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", n / 1024.0 / 1024.0);
    }

    /** Directories first, then by column (ties by name); ascending or not. */
    public static void sort(List<Entry> list, Column by, boolean ascending) {
        Comparator<Entry> name = Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
        Comparator<Entry> c =
                switch (by) {
                    case NAME -> name;
                    case TYPE -> Comparator.comparing(FileListing::typeLabel).thenComparing(name);
                    case MODIFIED -> Comparator.comparingLong(Entry::mtime).thenComparing(name);
                };
        if (!ascending) {
            c = c.reversed();
        }
        list.sort(Comparator.comparing((Entry e) -> !e.dir()).thenComparing(c));
    }
}
