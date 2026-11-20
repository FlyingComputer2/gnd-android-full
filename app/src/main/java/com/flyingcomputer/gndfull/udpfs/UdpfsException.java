package com.flyingcomputer.gndfull.udpfs;

import java.io.IOException;

/** A udpfs error: the wire errno (Linux numbering) and its name. */
public final class UdpfsException extends IOException {
    public final int errno;

    public UdpfsException(int errno, String detail) {
        super(UdpfsWire.errnoName(errno) + (detail == null ? "" : " (" + detail + ")"));
        this.errno = errno;
    }
}
