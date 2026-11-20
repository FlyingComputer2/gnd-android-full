package com.flyingcomputer.gndfull.udpfs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * A small udpfs server over java.io for client tests: the operations the "rov fs" console uses,
 * with the reply cache for the ones that must not run twice. Paths are absolute and used as-is.
 */
public final class FakeUdpfsServer {
    private final Map<Integer, Object> handles = new HashMap<>(); // RandomAccessFile or String[]
    private final Map<Integer, String> paths = new HashMap<>();
    private final Deque<Object[]> cache = new ArrayDeque<>(); // {op, handle, reqid, response}
    private int next = 1000;

    public byte[] handle(byte[] req) {
        ByteBuffer r = ByteBuffer.wrap(req).order(ByteOrder.LITTLE_ENDIAN);
        int op = r.get() & 0xff;
        int reqid = r.getInt();
        boolean cached =
                op == UdpfsWire.OP_OPEN || op == UdpfsWire.OP_CLOSE || op == UdpfsWire.OP_READDIR
                        || op == UdpfsWire.OP_CLOSEDIR || op == UdpfsWire.OP_MKDIR || op == UdpfsWire.OP_UNLINK
                        || op == UdpfsWire.OP_OPENDIR;
        int key = r.remaining() >= 4 ? r.getInt(r.position()) : -1;
        if (op == UdpfsWire.OP_OPEN || op == UdpfsWire.OP_MKDIR || op == UdpfsWire.OP_UNLINK
                || op == UdpfsWire.OP_OPENDIR) {
            key = -1;
        }
        if (cached) {
            for (Object[] c : cache) {
                if ((int) c[0] == op && (int) c[1] == key && (int) c[2] == reqid) {
                    return (byte[]) c[3];
                }
            }
        }
        byte[] resp;
        try {
            resp = dispatch(op, reqid, r);
        } catch (IOException e) {
            resp = header(op, reqid, -1, UdpfsWire.EIO).array();
        }
        if (cached) {
            if (cache.size() >= 32) {
                cache.poll();
            }
            cache.add(new Object[] {op, key, reqid, resp});
        }
        return resp;
    }

    private static ByteBuffer header(int op, int reqid, int ret, int err) {
        ByteBuffer b = ByteBuffer.allocate(1400).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) (op | UdpfsWire.RESPONSE_BIT)).putInt(reqid).putInt(ret).putInt(err);
        return b;
    }

    private static byte[] done(ByteBuffer b) {
        return Arrays.copyOf(b.array(), b.position());
    }

    private static String rest(ByteBuffer r) {
        byte[] p = new byte[r.remaining()];
        r.get(p);
        return new String(p, StandardCharsets.UTF_8);
    }

    private byte[] fail(int op, int reqid, int err) {
        return done(header(op, reqid, -1, err));
    }

    private byte[] dispatch(int op, int reqid, ByteBuffer r) throws IOException {
        switch (op) {
            case UdpfsWire.OP_STAT: {
                File f = new File(rest(r));
                if (!f.exists()) {
                    return fail(op, reqid, UdpfsWire.ENOENT);
                }
                ByteBuffer b = header(op, reqid, 0, 0);
                b.put((byte) (f.isDirectory() ? UdpfsWire.TYPE_DIR : UdpfsWire.TYPE_FILE)).putInt(0644)
                        .putLong(f.isDirectory() ? 0 : f.length()).putLong(f.lastModified() / 1000);
                return done(b);
            }
            case UdpfsWire.OP_OPEN: {
                int flags = r.getInt();
                r.getInt();
                String p = rest(r);
                File f = new File(p);
                if ((flags & UdpfsWire.O_CREAT) == 0 && !f.exists()) {
                    return fail(op, reqid, UdpfsWire.ENOENT);
                }
                RandomAccessFile raf = new RandomAccessFile(f, (flags & 3) == UdpfsWire.O_RDONLY ? "r" : "rw");
                if ((flags & UdpfsWire.O_TRUNC) != 0) {
                    raf.setLength(0);
                }
                int h = next++;
                handles.put(h, raf);
                paths.put(h, p);
                return done(header(op, reqid, h, 0));
            }
            case UdpfsWire.OP_CLOSE:
            case UdpfsWire.OP_CLOSEDIR: {
                Object h = handles.remove(r.getInt());
                if (h == null) {
                    return fail(op, reqid, 9);
                }
                if (h instanceof RandomAccessFile raf) {
                    raf.close();
                }
                return done(header(op, reqid, 0, 0));
            }
            case UdpfsWire.OP_READ: {
                RandomAccessFile f = (RandomAccessFile) handles.get(r.getInt());
                long off = r.getLong();
                int len = Math.min(r.getInt(), 1400 - UdpfsWire.RESPONSE_HEADER);
                byte[] buf = new byte[len];
                f.seek(off);
                int n = Math.max(f.read(buf), 0);
                ByteBuffer b = header(op, reqid, n, 0);
                b.put(buf, 0, n);
                return done(b);
            }
            case UdpfsWire.OP_WRITE: {
                RandomAccessFile f = (RandomAccessFile) handles.get(r.getInt());
                long off = r.getLong();
                int len = r.getInt();
                byte[] buf = new byte[len];
                r.get(buf);
                f.seek(off);
                f.write(buf);
                return done(header(op, reqid, len, 0));
            }
            case UdpfsWire.OP_FSYNC:
                r.getInt();
                return done(header(op, reqid, 0, 0));
            case UdpfsWire.OP_CRC32: {
                RandomAccessFile f = (RandomAccessFile) handles.get(r.getInt());
                long off = r.getLong();
                long len = r.getLong();
                long seed = r.getInt() & 0xffffffffL;
                if (seed != 0) {
                    throw new IOException("the fake server takes unchained CRC32 only");
                }
                f.seek(off);
                byte[] buf = new byte[(int) Math.min(len, f.length() - off)];
                f.readFully(buf);
                CRC32 c = new CRC32();
                c.update(buf);
                ByteBuffer b = header(op, reqid, 0, 0);
                b.putInt((int) c.getValue()).putLong(buf.length);
                return done(b);
            }
            case UdpfsWire.OP_OPENDIR: {
                String p = rest(r);
                String[] names = new File(p).list();
                if (names == null) {
                    return fail(op, reqid, UdpfsWire.ENOENT);
                }
                Arrays.sort(names);
                int h = next++;
                handles.put(h, new Object[] {p, names, 0});
                paths.put(h, p);
                return done(header(op, reqid, h, 0));
            }
            case UdpfsWire.OP_READDIR: {
                Object[] d = (Object[]) handles.get(r.getInt());
                String[] names = (String[]) d[1];
                int i = (int) d[2];
                ByteBuffer body = ByteBuffer.allocate(1400).order(ByteOrder.LITTLE_ENDIAN);
                int count = 0;
                for (; i < names.length && count < 8; i++, count++) {
                    File f = new File((String) d[0], names[i]);
                    byte[] n = names[i].getBytes(StandardCharsets.UTF_8);
                    body.put((byte) (f.isDirectory() ? UdpfsWire.TYPE_DIR : UdpfsWire.TYPE_FILE))
                            .putLong(f.length()).putLong(0).put((byte) n.length).put(n);
                }
                d[2] = i;
                ByteBuffer b = header(op, reqid, count, 0);
                b.put(body.array(), 0, body.position());
                return done(b);
            }
            case UdpfsWire.OP_MKDIR: {
                r.getInt();
                File f = new File(rest(r));
                return f.mkdir() ? done(header(op, reqid, 0, 0)) : fail(op, reqid, UdpfsWire.EEXIST);
            }
            case UdpfsWire.OP_UNLINK: {
                File f = new File(rest(r));
                return f.delete() ? done(header(op, reqid, 0, 0)) : fail(op, reqid, UdpfsWire.ENOENT);
            }
            case UdpfsWire.OP_LIST_OPEN: {
                ByteBuffer b = header(op, reqid, handles.size(), 0);
                for (Map.Entry<Integer, Object> e : handles.entrySet()) {
                    byte[] p = paths.get(e.getKey()).getBytes(StandardCharsets.UTF_8);
                    b.putInt(e.getKey()).put((byte) (e.getValue() instanceof RandomAccessFile ? 1 : 2))
                            .putLong(0).putShort((short) p.length).put(p);
                }
                return done(b);
            }
            default:
                return fail(op, reqid, 38);
        }
    }

    public int openHandles() {
        return handles.size();
    }
}
