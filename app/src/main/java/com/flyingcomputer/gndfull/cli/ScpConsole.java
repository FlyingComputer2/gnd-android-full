package com.flyingcomputer.gndfull.cli;

import com.flyingcomputer.gndfull.udpfs.UdpfsClient;
import com.flyingcomputer.gndfull.udpfs.UdpfsException;
import com.flyingcomputer.gndfull.udpfs.UdpfsWire;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.CRC32;

/**
 * The "rov fs" console: the rover's files over udpfs (ground UDP 2440, L3 upstream 11), as root on
 * its /sdcard and /winject. Commands run on a worker thread, one at a time; local paths are
 * relative to the app's storage root (Download/FlyingComputer: edgetx/, scp/).
 */
public final class ScpConsole {
    static final String HELP =
            "ls [-l] REMOTE | stat REMOTE | get [-r] REMOTE [LOCAL] | put [-r] LOCAL REMOTE\n"
                    + "rm REMOTE.. | mkdir REMOTE | rmdir REMOTE | mv REMOTE NEWREMOTE | crc REMOTE\n"
                    + "cat REMOTE | info | list_open | lls [LOCAL] | help\n"
                    + "REMOTE: absolute rover path (/sdcard/photo/rov_0001.jpg). LOCAL: under the\n"
                    + "storage root (scp/, edgetx/sd/...); get defaults to scp/.";
    static final int CAT_MAX = 8 * 1024;

    public interface Link extends UdpfsClient.Transport {
        boolean isOpen();

        /** Drops replies left over from an earlier command. */
        void drain();
    }

    private final Link link;
    private final Supplier<File> root;
    private final UdpfsClient.Config cfg;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "rov-fs"));
    private final AtomicBoolean busy = new AtomicBoolean();
    /** The storage root for the command running (worker thread only); resolved once per command,
     * since resolving it probes the directory with a write. */
    private File cur;

    public ScpConsole(Link link, Supplier<File> root, UdpfsClient.Config cfg) {
        this.link = link;
        this.root = root;
        this.cfg = cfg;
    }

    /** Runs line on the worker; out gets each output line (on the worker thread). */
    public void submit(String line, Consumer<String> out) {
        if (!busy.compareAndSet(false, true)) {
            out.accept("! busy: one command at a time");
            return;
        }
        worker.execute(
                () -> {
                    try {
                        run(line, out);
                    } finally {
                        busy.set(false);
                    }
                });
    }

    public void shutdown() {
        worker.shutdownNow();
    }

    /** Runs line on the calling thread. */
    public void run(String line, Consumer<String> out) {
        cur = root.get();
        List<String> a = new ArrayList<>(Arrays.asList(line.trim().split("\\s+")));
        if (a.isEmpty() || a.get(0).isEmpty()) {
            return;
        }
        String cmd = a.remove(0);
        boolean flagR = a.remove("-r");
        boolean flagL = a.remove("-l");
        try {
            if (cmd.equals("help") || cmd.equals("?")) {
                for (String l : HELP.split("\n")) {
                    out.accept(l);
                }
                return;
            }
            if (cmd.equals("lls")) {
                File d = local(a.isEmpty() ? "" : a.get(0));
                out.accept(d.getAbsolutePath());
                File[] list = d.listFiles();
                if (list != null) {
                    Arrays.sort(list);
                    for (File f : list) {
                        out.accept(f.isDirectory() ? f.getName() + "/" : f.getName() + "  " + f.length());
                    }
                }
                return;
            }
            if (!link.isOpen()) {
                out.accept("! USB CLI not open: connect the ground");
                return;
            }
            link.drain();
            UdpfsClient c = new UdpfsClient(link, cfg);
            switch (cmd) {
                case "ls": {
                    need(a, 1);
                    String p = a.get(0);
                    UdpfsWire.Stat st = c.stat(p);
                    if (st.type() != UdpfsWire.TYPE_DIR) {
                        out.accept(fmt(st.type(), st.size(), st.mtime(), p));
                        return;
                    }
                    for (UdpfsWire.DirEntry e : c.list(p, flagL)) {
                        out.accept(
                                flagL
                                        ? fmt(e.type(), e.size(), e.mtime(), e.name())
                                        : e.name() + (e.type() == UdpfsWire.TYPE_DIR ? "/" : ""));
                    }
                    return;
                }
                case "stat": {
                    need(a, 1);
                    UdpfsWire.Stat st = c.stat(a.get(0));
                    out.accept(
                            String.format(
                                    Locale.US,
                                    "%s type=%s mode=%03o size=%d mtime=%s",
                                    a.get(0),
                                    typeName(st.type()),
                                    st.mode(),
                                    st.size(),
                                    time(st.mtime())));
                    return;
                }
                case "get": {
                    need(a, 1);
                    String remote = a.get(0);
                    // The default (scp/) and a LOCAL ending in '/' are directories, created.
                    String l = a.size() > 1 ? a.get(1) : "scp/";
                    File dst = local(l);
                    if (l.endsWith("/") && !dst.isDirectory() && !dst.mkdirs()) {
                        throw new IOException("cannot create " + rel(dst));
                    }
                    long t0 = System.nanoTime();
                    long n = download(c, remote, dst, flagR, out);
                    out.accept(done("got", n, t0));
                    return;
                }
                case "put": {
                    need(a, 2);
                    long t0 = System.nanoTime();
                    long n = upload(c, local(a.get(0)), a.get(1), flagR, out);
                    out.accept(done("put", n, t0));
                    return;
                }
                case "rm":
                    need(a, 1);
                    for (String p : a) {
                        c.unlink(p);
                    }
                    out.accept("ok");
                    return;
                case "mkdir":
                    need(a, 1);
                    c.mkdir(a.get(0), 0755);
                    out.accept("ok");
                    return;
                case "rmdir":
                    need(a, 1);
                    c.rmdir(a.get(0));
                    out.accept("ok");
                    return;
                case "mv":
                    need(a, 2);
                    c.rename(a.get(0), a.get(1));
                    out.accept("ok");
                    return;
                case "crc": {
                    need(a, 1);
                    UdpfsWire.Stat st = c.stat(a.get(0));
                    int fd = c.open(a.get(0), UdpfsWire.O_RDONLY, 0);
                    try {
                        out.accept(String.format(Locale.US, "%08x %d %s", c.crcAll(fd, st.size()), st.size(), a.get(0)));
                    } finally {
                        c.close(fd);
                    }
                    return;
                }
                case "cat": {
                    need(a, 1);
                    UdpfsWire.Stat st = c.stat(a.get(0));
                    if (st.size() > CAT_MAX) {
                        out.accept("! " + st.size() + " B: cat shows up to " + CAT_MAX + " B; use get");
                        return;
                    }
                    int fd = c.open(a.get(0), UdpfsWire.O_RDONLY, 0);
                    StringBuilder b = new StringBuilder();
                    try {
                        c.readAll(fd, st.size(), (buf, off, len) -> b.append(new String(buf, off, len, StandardCharsets.UTF_8)), null);
                    } finally {
                        c.close(fd);
                    }
                    for (String l : b.toString().split("\n", -1)) {
                        out.accept(l);
                    }
                    return;
                }
                case "info": {
                    UdpfsWire.Info i = c.info();
                    out.accept(
                            "max_datagram=" + i.maxDatagram() + " max_handles=" + i.maxHandles()
                                    + " idle_timeout_ms=" + i.idleTimeoutMs() + " crc_max=" + i.crcMax()
                                    + " open_handles=" + i.openHandles());
                    return;
                }
                case "list_open": {
                    List<UdpfsWire.OpenEntry> open = c.listOpen();
                    if (open.isEmpty()) {
                        out.accept("(none)");
                    }
                    for (UdpfsWire.OpenEntry e : open) {
                        out.accept(e.handle() + " " + (e.type() == UdpfsWire.TYPE_DIR ? "dir " : "file") + " pos=" + e.pos() + " " + e.path());
                    }
                    return;
                }
                default:
                    out.accept("! unknown command " + cmd + " (help)");
            }
        } catch (IllegalArgumentException e) {
            out.accept("! " + cmd + ": " + e.getMessage());
        } catch (IOException e) {
            out.accept("! " + cmd + (a.isEmpty() ? "" : " " + a.get(0)) + ": " + e.getMessage());
        }
    }

    // ---- transfers ----

    private long download(UdpfsClient c, String remote, File dst, boolean recursive, Consumer<String> out)
            throws IOException {
        UdpfsWire.Stat st = c.stat(remote);
        File target = dst.isDirectory() ? new File(dst, baseName(remote)) : dst;
        if (st.type() == UdpfsWire.TYPE_DIR) {
            if (!recursive) {
                throw new UdpfsException(UdpfsWire.EISDIR, remote + ": use get -r");
            }
            if (!target.isDirectory() && !target.mkdirs()) {
                throw new IOException("cannot create " + target);
            }
            long total = 0;
            for (UdpfsWire.DirEntry e : c.list(remote, false)) {
                total += download(c, join(remote, e.name()), target, true, out);
            }
            return total;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        File part = new File(target.getPath() + ".part");
        int fd = c.open(remote, UdpfsWire.O_RDONLY, 0);
        CRC32 crc = new CRC32();
        boolean ok = false;
        try (FileOutputStream f = new FileOutputStream(part)) {
            Ticker tick = new Ticker(out, remote);
            c.readAll(
                    fd,
                    st.size(),
                    (b, off, n) -> {
                        crc.update(b, off, n);
                        f.write(b, off, n);
                    },
                    tick::update);
            f.getFD().sync();
            long rcrc = c.crcAll(fd, st.size());
            if (rcrc != crc.getValue()) {
                throw new UdpfsException(UdpfsWire.EIO, String.format(Locale.US, "CRC mismatch: local %08x remote %08x", crc.getValue(), rcrc));
            }
            ok = true;
        } finally {
            try {
                c.close(fd);
            } catch (IOException ignored) {
                // the server's idle timeout closes it
            }
            if (!ok) {
                part.delete();
            }
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("cannot replace " + target);
        }
        if (!part.renameTo(target)) {
            throw new IOException("cannot rename to " + target);
        }
        out.accept(remote + " -> " + rel(target) + " (" + st.size() + " B)");
        return st.size();
    }

    private long upload(UdpfsClient c, File src, String remote, boolean recursive, Consumer<String> out)
            throws IOException {
        if (!src.exists()) {
            throw new IOException(rel(src) + ": not found");
        }
        String target = remote;
        try {
            UdpfsWire.Stat st = c.stat(remote);
            if (st.type() == UdpfsWire.TYPE_DIR) {
                target = join(remote, src.getName());
            }
        } catch (UdpfsException e) {
            if (e.errno != UdpfsWire.ENOENT) {
                throw e;
            }
        }
        if (src.isDirectory()) {
            if (!recursive) {
                throw new UdpfsException(UdpfsWire.EISDIR, rel(src) + ": use put -r");
            }
            try {
                c.mkdir(target, 0755);
            } catch (UdpfsException e) {
                if (e.errno != UdpfsWire.EEXIST) {
                    throw e;
                }
            }
            long total = 0;
            File[] list = src.listFiles();
            if (list != null) {
                Arrays.sort(list);
                for (File f : list) {
                    total += upload(c, f, join(target, f.getName()), true, out);
                }
            }
            return total;
        }
        long size = src.length();
        CRC32 crc = new CRC32();
        try (RandomAccessFile f = new RandomAccessFile(src, "r")) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = f.read(buf)) > 0) {
                crc.update(buf, 0, n);
            }
            int fd = c.open(target, UdpfsWire.O_WRONLY | UdpfsWire.O_CREAT | UdpfsWire.O_TRUNC, 0644);
            try {
                Ticker tick = new Ticker(out, rel(src));
                c.writeAll(
                        fd,
                        size,
                        (off, b, len) -> {
                            f.seek(off);
                            f.readFully(b, 0, len);
                        },
                        tick::update);
                c.fsync(fd);
            } finally {
                c.close(fd);
            }
        }
        int rfd = c.open(target, UdpfsWire.O_RDONLY, 0);
        try {
            long rcrc = c.crcAll(rfd, size);
            if (rcrc != crc.getValue()) {
                throw new UdpfsException(UdpfsWire.EIO, String.format(Locale.US, "CRC mismatch: local %08x remote %08x", crc.getValue(), rcrc));
            }
        } finally {
            c.close(rfd);
        }
        out.accept(rel(src) + " -> " + target + " (" + size + " B)");
        return size;
    }

    /** Progress lines at most once a second. */
    private static final class Ticker {
        private final Consumer<String> out;
        private final String name;
        private final long t0 = System.nanoTime();
        private long last = t0;

        Ticker(Consumer<String> out, String name) {
            this.out = out;
            this.name = name;
        }

        void update(long done, long total) {
            long now = System.nanoTime();
            if (now - last < 1_000_000_000L || done == total) {
                return;
            }
            last = now;
            double s = (now - t0) / 1e9;
            out.accept(String.format(Locale.US, "  %s %d/%d B %.1f KB/s", baseName(name), done, total, done / 1024.0 / s));
        }
    }

    // ---- helpers ----

    /** A local path under the storage root; ".." is refused. */
    File local(String rel) throws IOException {
        File r = cur != null ? cur : root.get();
        if (rel.isEmpty() || rel.equals(".")) {
            return r;
        }
        for (String part : rel.split("/")) {
            if (part.equals("..")) {
                throw new IllegalArgumentException(rel + ": '..' not allowed (paths are under the storage root)");
            }
        }
        return new File(r, rel.startsWith("/") ? rel.substring(1) : rel);
    }

    private String rel(File f) {
        String r = (cur != null ? cur : root.get()).getAbsolutePath();
        String p = f.getAbsolutePath();
        return p.startsWith(r + "/") ? p.substring(r.length() + 1) : p;
    }

    private static void need(List<String> a, int n) {
        if (a.size() < n) {
            throw new IllegalArgumentException("missing arguments (help)");
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).isEmpty()) {
                throw new IllegalArgumentException("missing arguments (help)");
            }
        }
    }

    static String baseName(String p) {
        String s = p;
        while (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        int i = s.lastIndexOf('/');
        return i < 0 ? s : s.substring(i + 1);
    }

    static String join(String dir, String name) {
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    private static String typeName(int t) {
        return t == UdpfsWire.TYPE_DIR ? "dir" : t == UdpfsWire.TYPE_FILE ? "file" : "other";
    }

    private static String fmt(int type, long size, long mtime, String name) {
        return String.format(Locale.US, "%s %10d %s %s", type == UdpfsWire.TYPE_DIR ? "d" : "-", size, time(mtime), name);
    }

    private static String time(long t) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(t * 1000L));
    }

    private static String done(String what, long n, long t0) {
        double s = Math.max((System.nanoTime() - t0) / 1e9, 0.001);
        return String.format(Locale.US, "%s %d B in %.1f s (%.1f KB/s)", what, n, s, n / 1024.0 / s);
    }
}
