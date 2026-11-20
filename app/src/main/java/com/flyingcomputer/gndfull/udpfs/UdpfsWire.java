package com.flyingcomputer.gndfull.udpfs;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * udpfs wire format (FlyingComputer2/udpfs docs/protocol.md): op codes, Linux errno and open
 * flags, little-endian request building and response reading.
 */
public final class UdpfsWire {
    private UdpfsWire() {}

    public static final int OP_OPEN = 0x01;
    public static final int OP_CLOSE = 0x02;
    public static final int OP_READ = 0x03;
    public static final int OP_WRITE = 0x04;
    public static final int OP_LSEEK = 0x05;
    public static final int OP_FSTAT = 0x06;
    public static final int OP_STAT = 0x07;
    public static final int OP_UNLINK = 0x08;
    public static final int OP_MKDIR = 0x09;
    public static final int OP_RMDIR = 0x0a;
    public static final int OP_OPENDIR = 0x0b;
    public static final int OP_READDIR = 0x0c;
    public static final int OP_CLOSEDIR = 0x0d;
    public static final int OP_RENAME = 0x0e;
    public static final int OP_FSYNC = 0x0f;
    public static final int OP_FTRUNCATE = 0x10;
    public static final int OP_INFO = 0x11;
    public static final int OP_CRC32 = 0x12;
    public static final int OP_LIST_OPEN = 0x13;
    public static final int RESPONSE_BIT = 0x80;

    public static final int REQUEST_HEADER = 5;
    public static final int RESPONSE_HEADER = 13;
    public static final int WRITE_FIXED = 4 + 8 + 4;

    public static final int O_RDONLY = 0x0, O_WRONLY = 0x1, O_RDWR = 0x2;
    public static final int O_CREAT = 0x40, O_EXCL = 0x80, O_TRUNC = 0x200;

    public static final int SEEK_SET = 0, SEEK_CUR = 1, SEEK_END = 2;
    public static final int TYPE_FILE = 1, TYPE_DIR = 2, TYPE_OTHER = 3;
    public static final int READDIR_STAT = 0x01;

    /** Wire errno (Linux numbering) to its name; "E<n>" for others. */
    public static String errnoName(int e) {
        switch (e) {
            case 1: return "EPERM";
            case 2: return "ENOENT";
            case 5: return "EIO";
            case 9: return "EBADF";
            case 11: return "EAGAIN";
            case 12: return "ENOMEM";
            case 13: return "EACCES";
            case 16: return "EBUSY";
            case 17: return "EEXIST";
            case 18: return "EXDEV";
            case 20: return "ENOTDIR";
            case 21: return "EISDIR";
            case 22: return "EINVAL";
            case 23: return "ENFILE";
            case 24: return "EMFILE";
            case 27: return "EFBIG";
            case 28: return "ENOSPC";
            case 30: return "EROFS";
            case 36: return "ENAMETOOLONG";
            case 38: return "ENOSYS";
            case 39: return "ENOTEMPTY";
            case 71: return "EPROTO";
            case 90: return "EMSGSIZE";
            case 95: return "ENOTSUP";
            case 110: return "ETIMEDOUT";
            default: return "E" + e;
        }
    }

    public static final int ENOENT = 2, EIO = 5, EEXIST = 17, EISDIR = 21, EINVAL = 22;
    public static final int ENOSPC = 28, EMSGSIZE = 90, ETIMEDOUT = 110;

    /** A request under construction. */
    public static final class Request {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(64);
        public final int op;
        public final int reqid;

        public Request(int op, int reqid) {
            this.op = op;
            this.reqid = reqid;
            u8(op);
            u32(reqid);
        }

        public Request u8(int v) {
            out.write(v & 0xff);
            return this;
        }

        public Request u16(int v) {
            return put(v, 2);
        }

        public Request u32(long v) {
            return put(v, 4);
        }

        public Request i32(int v) {
            return put(v, 4);
        }

        public Request u64(long v) {
            return put(v, 8);
        }

        public Request bytes(byte[] b, int off, int n) {
            out.write(b, off, n);
            return this;
        }

        public Request str(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            return bytes(b, 0, b.length);
        }

        private Request put(long v, int n) {
            for (int i = 0; i < n; i++) {
                out.write((int) (v >>> (8 * i)) & 0xff);
            }
            return this;
        }

        public byte[] toBytes() {
            return out.toByteArray();
        }
    }

    /** A parsed response: header fields and a little-endian reader over the payload. */
    public static final class Response {
        public final int op;
        public final int reqid;
        public final int ret;
        public final int err;
        private final ByteBuffer payload;

        private Response(int op, int reqid, int ret, int err, ByteBuffer payload) {
            this.op = op;
            this.reqid = reqid;
            this.ret = ret;
            this.err = err;
            this.payload = payload;
        }

        /** Null when shorter than a response header. */
        public static Response parse(byte[] d, int n) {
            if (d == null || n < RESPONSE_HEADER) {
                return null;
            }
            ByteBuffer b = ByteBuffer.wrap(d, 0, n).order(ByteOrder.LITTLE_ENDIAN);
            int op = b.get() & 0xff;
            int reqid = b.getInt();
            int ret = b.getInt();
            int err = b.getInt();
            return new Response(op, reqid, ret, err, b.slice().order(ByteOrder.LITTLE_ENDIAN));
        }

        public int remaining() {
            return payload.remaining();
        }

        public int u8() {
            need(1);
            return payload.get() & 0xff;
        }

        public int u16() {
            need(2);
            return payload.getShort() & 0xffff;
        }

        public long u32() {
            need(4);
            return payload.getInt() & 0xffffffffL;
        }

        public int i32() {
            need(4);
            return payload.getInt();
        }

        public long i64() {
            need(8);
            return payload.getLong();
        }

        public byte[] bytes(int n) {
            need(n);
            byte[] b = new byte[n];
            payload.get(b);
            return b;
        }

        public String str(int n) {
            return new String(bytes(n), StandardCharsets.UTF_8);
        }

        private void need(int n) {
            if (payload.remaining() < n) {
                throw new IllegalStateException("short udpfs response");
            }
        }
    }

    /** A stat block (STAT, FSTAT). */
    public record Stat(int type, int mode, long size, long mtime) {
        static Stat read(Response r) {
            return new Stat(r.u8(), (int) r.u32(), r.i64(), r.i64());
        }
    }

    /** A READDIR entry. */
    public record DirEntry(int type, long size, long mtime, String name) {}

    /** A LIST_OPEN entry. */
    public record OpenEntry(int handle, int type, long pos, String path) {}

    /** INFO. */
    public record Info(
            long maxDatagram,
            long maxPath,
            long maxHandles,
            long replyCache,
            long idleTimeoutMs,
            long crcMax,
            long openHandles) {}
}
