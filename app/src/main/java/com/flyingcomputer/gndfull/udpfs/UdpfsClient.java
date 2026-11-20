package com.flyingcomputer.gndfull.udpfs;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * udpfs client (FlyingComputer2/udpfs): the protocol's operations over a datagram transport, with
 * retransmission and a window of requests in flight for bulk reads and writes. Every request is
 * safe to resend (the server answers repeats of state-changing ones from its reply cache). One
 * thread per client; errors are {@link UdpfsException} with the wire errno.
 */
public final class UdpfsClient {
    /** One datagram per call. */
    public interface Transport {
        /** False when it could not be sent (treated as lost). */
        boolean send(byte[] datagram);

        /** One datagram, or null when none arrived within timeoutMs. */
        byte[] receive(long timeoutMs) throws InterruptedException;
    }

    public static final class Config {
        public int maxDatagram = 1400;
        /** Wait for a response before resending (USB + ground + air round trip ~100-150 ms). */
        public long retransmitMs = 600;
        /** Sends of one request before ETIMEDOUT. */
        public int tries = 12;
        /** Bulk requests in flight. */
        public int window = 8;
    }

    public interface Progress {
        void update(long done, long total);
    }

    public interface Sink {
        void write(byte[] b, int off, int n) throws IOException;
    }

    public interface Source {
        /** Fills exactly n bytes at off. */
        void read(long off, byte[] buf, int n) throws IOException;
    }

    /** Requests the protocol takes from CRC32 at most per call, and its longer resend time. */
    static final long CRC_PIECE = 256 * 1024;
    static final long CRC_RETRANSMIT_MS = 3000;

    private final Transport t;
    private final Config cfg;
    private int nextReqid;
    public long requests, sends, retransmits, stray;

    public UdpfsClient(Transport t, Config cfg) {
        this.t = t;
        this.cfg = cfg;
        this.nextReqid = new SecureRandom().nextInt();
    }

    public int readChunk() {
        return cfg.maxDatagram - UdpfsWire.RESPONSE_HEADER;
    }

    public int writeChunk() {
        return cfg.maxDatagram - UdpfsWire.REQUEST_HEADER - UdpfsWire.WRITE_FIXED;
    }

    private UdpfsWire.Request begin(int op) {
        return new UdpfsWire.Request(op, nextReqid++);
    }

    // ---- the request engine ----

    private interface Next {
        /** The next request, or null when there are no more for now. */
        Pending next() throws IOException;
    }

    private interface Done {
        void done(Pending p, UdpfsWire.Response r) throws IOException;
    }

    private static final class Pending {
        final int op;
        final int reqid;
        final byte[] bytes;
        final long tag;
        long sent;
        int tries;

        Pending(UdpfsWire.Request r, long tag) {
            this.op = r.op;
            this.reqid = r.reqid;
            this.bytes = r.toBytes();
            this.tag = tag;
        }
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    private void send(Pending p) {
        sends++;
        t.send(p.bytes);
        p.sent = now();
        p.tries++;
    }

    private void pipeline(Next next, Done done, int window, long retransmitMs) throws IOException {
        Deque<Pending> inflight = new ArrayDeque<>();
        boolean more = true;
        try {
            for (;;) {
                while (more && inflight.size() < window) {
                    Pending p = next.next();
                    if (p == null) {
                        more = false;
                        break;
                    }
                    if (p.bytes.length > cfg.maxDatagram) {
                        throw new UdpfsException(UdpfsWire.EMSGSIZE, "request too large");
                    }
                    requests++;
                    inflight.add(p);
                    send(p);
                }
                if (inflight.isEmpty()) {
                    return;
                }
                long wait = Long.MAX_VALUE;
                long n = now();
                for (Pending p : inflight) {
                    wait = Math.min(wait, p.sent + retransmitMs - n);
                }
                byte[] d = t.receive(Math.max(wait, 0));
                if (d != null) {
                    UdpfsWire.Response r = UdpfsWire.Response.parse(d, d.length);
                    Pending match = null;
                    if (r != null) {
                        for (Iterator<Pending> it = inflight.iterator(); it.hasNext(); ) {
                            Pending p = it.next();
                            if (p.reqid == r.reqid && (p.op | UdpfsWire.RESPONSE_BIT) == r.op) {
                                match = p;
                                it.remove();
                                break;
                            }
                        }
                    }
                    if (match == null) {
                        stray++;
                    } else {
                        done.done(match, r);
                    }
                }
                long t2 = now();
                for (Pending p : inflight) {
                    if (t2 - p.sent >= retransmitMs) {
                        if (p.tries >= cfg.tries) {
                            throw new UdpfsException(UdpfsWire.ETIMEDOUT, "no reply");
                        }
                        retransmits++;
                        send(p);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private UdpfsWire.Response call(UdpfsWire.Request req, long retransmitMs) throws IOException {
        UdpfsWire.Response[] out = new UdpfsWire.Response[1];
        Pending only = new Pending(req, 0);
        boolean[] sent = {false};
        pipeline(
                () -> {
                    if (sent[0]) {
                        return null;
                    }
                    sent[0] = true;
                    return only;
                },
                (p, r) -> out[0] = r,
                1,
                retransmitMs);
        UdpfsWire.Response r = out[0];
        if (r.ret < 0) {
            throw new UdpfsException(r.err == 0 ? UdpfsWire.EIO : r.err, null);
        }
        return r;
    }

    private UdpfsWire.Response call(UdpfsWire.Request req) throws IOException {
        return call(req, cfg.retransmitMs);
    }

    // ---- operations ----

    public int open(String path, int wireFlags, int mode) throws IOException {
        return call(begin(UdpfsWire.OP_OPEN).u32(wireFlags).u32(mode).str(path)).ret;
    }

    public void close(int fd) throws IOException {
        call(begin(UdpfsWire.OP_CLOSE).i32(fd));
    }

    public long lseek(int fd, long off, int whence) throws IOException {
        return call(begin(UdpfsWire.OP_LSEEK).i32(fd).u64(off).u8(whence)).i64();
    }

    public UdpfsWire.Stat fstat(int fd) throws IOException {
        return UdpfsWire.Stat.read(call(begin(UdpfsWire.OP_FSTAT).i32(fd)));
    }

    public UdpfsWire.Stat stat(String path) throws IOException {
        return UdpfsWire.Stat.read(call(begin(UdpfsWire.OP_STAT).str(path)));
    }

    public void unlink(String path) throws IOException {
        call(begin(UdpfsWire.OP_UNLINK).str(path));
    }

    public void mkdir(String path, int mode) throws IOException {
        call(begin(UdpfsWire.OP_MKDIR).u32(mode).str(path));
    }

    public void rmdir(String path) throws IOException {
        call(begin(UdpfsWire.OP_RMDIR).str(path));
    }

    public void rename(String from, String to) throws IOException {
        byte[] f = from.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        call(begin(UdpfsWire.OP_RENAME).u16(f.length).bytes(f, 0, f.length).str(to));
    }

    public void fsync(int fd) throws IOException {
        call(begin(UdpfsWire.OP_FSYNC).i32(fd));
    }

    public void ftruncate(int fd, long len) throws IOException {
        call(begin(UdpfsWire.OP_FTRUNCATE).i32(fd).u64(len));
    }

    public UdpfsWire.Info info() throws IOException {
        UdpfsWire.Response r = call(begin(UdpfsWire.OP_INFO));
        return new UdpfsWire.Info(r.u32(), r.u32(), r.u32(), r.u32(), r.u32(), r.u32(), r.u32());
    }

    public List<UdpfsWire.OpenEntry> listOpen() throws IOException {
        UdpfsWire.Response r = call(begin(UdpfsWire.OP_LIST_OPEN));
        List<UdpfsWire.OpenEntry> out = new ArrayList<>();
        for (int i = 0; i < r.ret; i++) {
            int h = r.i32();
            int type = r.u8();
            long pos = r.i64();
            out.add(new UdpfsWire.OpenEntry(h, type, pos, r.str(r.u16())));
        }
        return out;
    }

    /** The whole directory (OPENDIR / READDIR / CLOSEDIR). */
    public List<UdpfsWire.DirEntry> list(String path, boolean withStat) throws IOException {
        int dir = call(begin(UdpfsWire.OP_OPENDIR).str(path)).ret;
        List<UdpfsWire.DirEntry> out = new ArrayList<>();
        try {
            for (;;) {
                UdpfsWire.Response r =
                        call(begin(UdpfsWire.OP_READDIR).i32(dir).u8(withStat ? UdpfsWire.READDIR_STAT : 0));
                if (r.ret == 0) {
                    break;
                }
                for (int i = 0; i < r.ret; i++) {
                    int type = r.u8();
                    long size = r.i64();
                    long mtime = r.i64();
                    out.add(new UdpfsWire.DirEntry(type, size, mtime, r.str(r.u8())));
                }
            }
        } finally {
            call(begin(UdpfsWire.OP_CLOSEDIR).i32(dir));
        }
        return out;
    }

    /** CRC32 (zlib) of [0, size) on the server, chained over pieces. */
    public long crcAll(int fd, long size) throws IOException {
        long crc = 0;
        long off = 0;
        while (off < size) {
            long len = Math.min(CRC_PIECE, size - off);
            UdpfsWire.Response r =
                    call(
                            begin(UdpfsWire.OP_CRC32).i32(fd).u64(off).u64(len).u32(crc),
                            Math.max(cfg.retransmitMs, CRC_RETRANSMIT_MS));
            crc = r.u32();
            long n = r.i64();
            if (n == 0) {
                throw new UdpfsException(UdpfsWire.EIO, "file shorter than " + size);
            }
            off += n;
        }
        return crc;
    }

    /** Reads [0, size) with the window in flight; sink gets the bytes in order. */
    public void readAll(int fd, long size, Sink sink, Progress progress) throws IOException {
        final int chunk = readChunk();
        final long[] state = {0, 0, size}; // next offset, delivered, end
        Deque<long[]> redo = new ArrayDeque<>();
        TreeMap<Long, byte[]> ready = new TreeMap<>();
        TreeMap<Long, Integer> asked = new TreeMap<>();
        pipeline(
                () -> {
                    long off;
                    int len;
                    if (!redo.isEmpty()) {
                        long[] r = redo.poll();
                        off = r[0];
                        len = (int) r[1];
                    } else if (state[0] < state[2]) {
                        off = state[0];
                        len = (int) Math.min(chunk, state[2] - off);
                        state[0] += len;
                    } else {
                        return null;
                    }
                    asked.put(off, len);
                    return new Pending(begin(UdpfsWire.OP_READ).i32(fd).u64(off).u32(len), off);
                },
                (p, r) -> {
                    if (r.ret < 0) {
                        throw new UdpfsException(r.err == 0 ? UdpfsWire.EIO : r.err, null);
                    }
                    long off = p.tag;
                    int len = asked.remove(off);
                    int got = Math.min(r.ret, r.remaining());
                    if (got == 0) {
                        state[2] = Math.min(state[2], off); // ended early
                        return;
                    }
                    ready.put(off, r.bytes(got));
                    if (got < len) {
                        redo.add(new long[] {off + got, len - got});
                    }
                    while (!ready.isEmpty() && ready.firstKey() == state[1]) {
                        byte[] b = ready.pollFirstEntry().getValue();
                        sink.write(b, 0, b.length);
                        state[1] += b.length;
                        if (progress != null) {
                            progress.update(state[1], size);
                        }
                    }
                },
                cfg.window,
                cfg.retransmitMs);
        if (state[1] != size) {
            throw new UdpfsException(UdpfsWire.EIO, "file shorter than " + size);
        }
    }

    /** Writes [0, size) from source with the window in flight. */
    public void writeAll(int fd, long size, Source source, Progress progress) throws IOException {
        final int chunk = writeChunk();
        final long[] state = {0, 0}; // next offset, done
        Deque<long[]> redo = new ArrayDeque<>();
        TreeMap<Long, Integer> asked = new TreeMap<>();
        byte[] buf = new byte[chunk];
        pipeline(
                () -> {
                    long off;
                    int len;
                    if (!redo.isEmpty()) {
                        long[] r = redo.poll();
                        off = r[0];
                        len = (int) r[1];
                    } else if (state[0] < size) {
                        off = state[0];
                        len = (int) Math.min(chunk, size - off);
                        state[0] += len;
                    } else {
                        return null;
                    }
                    source.read(off, buf, len);
                    asked.put(off, len);
                    return new Pending(
                            begin(UdpfsWire.OP_WRITE).i32(fd).u64(off).u32(len).bytes(buf, 0, len), off);
                },
                (p, r) -> {
                    if (r.ret < 0) {
                        throw new UdpfsException(r.err == 0 ? UdpfsWire.EIO : r.err, null);
                    }
                    int len = asked.remove(p.tag);
                    int put = r.ret;
                    if (put == 0 && len > 0) {
                        throw new UdpfsException(UdpfsWire.ENOSPC, null);
                    }
                    if (put < len) {
                        redo.add(new long[] {p.tag + put, len - put});
                    }
                    state[1] += Math.min(put, len);
                    if (progress != null) {
                        progress.update(state[1], size);
                    }
                },
                cfg.window,
                cfg.retransmitMs);
        if (state[1] != size) {
            throw new UdpfsException(UdpfsWire.EIO, "short write");
        }
    }
}
