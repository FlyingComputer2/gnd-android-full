package com.flyingcomputer.gndfull.transport.msp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** MSP v2: {@code $X dir flag:u8 func:u16 len:u16 payload crc8-dvb-s2}. */
public final class MspV2 {
    public static final char TO_ROVER = '<';
    public static final char FROM_ROVER = '>';

    public record Frame(char direction, int function, byte[] payload) {}

    private MspV2() {}

    public static byte[] encode(char direction, int function, byte[] payload) {
        byte[] body =
                ByteBuffer.allocate(5 + payload.length)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .put((byte) 0)
                        .putShort((short) function)
                        .putShort((short) payload.length)
                        .put(payload)
                        .array();
        byte crc = crc8DvbS2(body);
        byte[] out = new byte[3 + body.length + 1];
        out[0] = (byte) '$';
        out[1] = (byte) 'X';
        out[2] = (byte) direction;
        System.arraycopy(body, 0, out, 3, body.length);
        out[out.length - 1] = crc;
        return out;
    }

    public static Frame decode(byte[] data) {
        if (data == null || data.length < 9 || data[0] != '$' || data[1] != 'X') {
            return null;
        }
        char dir = (char) data[2];
        ByteBuffer buf = ByteBuffer.wrap(data, 3, data.length - 3).order(ByteOrder.LITTLE_ENDIAN);
        int flag = buf.get() & 0xFF;
        int func = buf.getShort() & 0xFFFF;
        int len = buf.getShort() & 0xFFFF;
        if (flag != 0 || data.length != 9 + len) {
            return null;
        }
        byte[] payload = Arrays.copyOfRange(data, 8, 8 + len);
        byte expect = crc8DvbS2(Arrays.copyOfRange(data, 3, data.length - 1));
        if (expect != data[data.length - 1]) {
            return null;
        }
        return new Frame(dir, func, payload);
    }

    static byte crc8DvbS2(byte[] data) {
        int crc = 0;
        for (byte b : data) {
            crc ^= b & 0xFF;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x80) != 0) {
                    crc = ((crc << 1) ^ 0xD5) & 0xFF;
                } else {
                    crc = (crc << 1) & 0xFF;
                }
            }
        }
        return (byte) crc;
    }
}
