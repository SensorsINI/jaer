/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.opencv;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

/**
 * Linux v4l2loopback writer: {@code VIDIOC_S_FMT} then {@code write()} of
 * YUYV or MJPEG.
 * No-op on other OSes.
 * <p>
 * Do not {@code VIDIOC_QUERYCAP} (or {@code v4l2-ctl --all} / {@code --list-devices})
 * before the first {@code S_FMT}. With {@code exclusive_caps=1} those calls strip
 * Output/Capture from {@code device_caps}, and the V4L2 core then rejects
 * {@code S_FMT} with {@code EINVAL} until the module is reloaded.
 * After a successful {@code S_FMT}, this class sets {@code keep_format=1} so
 * the format survives jAER exit and the next open is not EINVAL.
 */
public class V4l2LoopbackSink {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int O_RDWR = 2;
    private static final int V4L2_BUF_TYPE_VIDEO_OUTPUT = 2;
    private static final int V4L2_FIELD_NONE = 1;
    private static final int V4L2_PIX_FMT_YUYV = fourcc('Y', 'U', 'Y', 'V');
    private static final int V4L2_PIX_FMT_MJPEG = fourcc('M', 'J', 'P', 'G');
    /** Linux x86_64 / aarch64 {@code sizeof(v4l2_format)==208}. */
    private static final int VIDIOC_S_FMT = 0xc0d05605;
    /** {@code _IOW('V', 28, struct v4l2_control)}. */
    private static final int VIDIOC_S_CTRL = 0x4008561c;
    /** v4l2loopback {@code keep_format}: keep pixelformat after the last writer closes. */
    private static final int CID_KEEP_FORMAT = 0x0098f900;

    private final String device;
    private int fd = -1;
    private int fmtW;
    private int fmtH;
    private boolean fmtMjpeg;
    private int fmtSizeimage;
    private volatile String lastError;
    private boolean openFailedLogged;

    public V4l2LoopbackSink(String device) {
        this.device = device == null || device.isBlank() ? "/dev/video10" : device;
    }

    public static boolean isLinux() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("linux");
    }

    public String getDevice() {
        return device;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isOpen() {
        return fd >= 0;
    }

    public synchronized void close() {
        if (fd >= 0) {
            try {
                libc().close(fd);
            } catch (Throwable ignore) {
            }
            fd = -1;
        }
        fmtW = 0;
        fmtH = 0;
        fmtMjpeg = false;
        fmtSizeimage = 0;
    }

    public synchronized void write(OpenCvRawFrame frame, int outW, int outH, boolean mjpeg,
            float jpegQuality) {
        if (!isLinux()) {
            lastError = "v4l2loopback is Linux-only";
            return;
        }
        if (frame == null) {
            return;
        }
        OpenCvRawFrame scaled = (outW > 0 && outH > 0) ? frame.scaled(outW, outH) : frame;
        int w = mjpeg ? scaled.width : scaled.yuyvWidth();
        int h = scaled.height;
        try {
            byte[] payload = mjpeg ? scaled.toJpeg(jpegQuality) : scaled.toYuyv();
            int sizeimage = mjpeg ? Math.max(Math.max(65536, w * h), payload.length) : w * h * 2;
            ensureOpen(w, h, mjpeg, sizeimage);
            if (fd >= 0) {
                if (mjpeg && payload.length > fmtSizeimage) {
                    close();
                    ensureOpen(w, h, true, payload.length);
                }
                LibC libc = libc();
                NativeLong n = libc.write(fd, payload, new NativeLong(payload.length));
                if (n.longValue() < 0) {
                    lastError = "write errno=" + Native.getLastError();
                } else {
                    lastError = null;
                    openFailedLogged = false;
                }
            }
        } catch (Throwable e) {
            lastError = e.toString();
            if (!openFailedLogged) {
                log.log(Level.WARNING, "v4l2 write " + device + ": " + e, e);
                openFailedLogged = true;
            }
            close();
        }
    }

    private void ensureOpen(int w, int h, boolean mjpeg, int sizeimage) throws IOException {
        if (fd >= 0 && fmtW == w && fmtH == h && fmtMjpeg == mjpeg && fmtSizeimage >= sizeimage) {
            return;
        }
        close();
        File node = new File(device);
        if (!node.exists()) {
            lastError = device + " missing (modprobe v4l2loopback …)";
            throw new IOException(lastError);
        }
        if (!tryJnaOpen(w, h, mjpeg, sizeimage)) {
            throw new IOException(lastError != null ? lastError
                    : "VIDIOC_S_FMT failed on " + device);
        }
        fmtW = w;
        fmtH = h;
        fmtMjpeg = mjpeg;
        fmtSizeimage = sizeimage;
        lastError = null;
    }

    private boolean tryJnaOpen(int w, int h, boolean mjpeg, int sizeimage) {
        try {
            LibC libc = libc();
            int opened = libc.open(device, O_RDWR);
            if (opened < 0) {
                lastError = "open " + device + " errno=" + Native.getLastError();
                return false;
            }
            fd = opened;
            // QUERYCAP first would clear Output on exclusive_caps=1 idle nodes.
            if (setFmtIoctl(libc, w, h, mjpeg, sizeimage, V4L2_BUF_TYPE_VIDEO_OUTPUT)) {
                setKeepFormat(libc);
                log.info("v4l2loopback JNA ioctl: " + device + " " + w + "x" + h
                        + (mjpeg ? " MJPG" : " YUYV"));
                return true;
            }
            int err = Native.getLastError();
            lastError = "S_FMT failed on " + device
                    + (err != 0 ? " errno=" + err : "")
                    + " — reload v4l2loopback; do not run v4l2-ctl/gst until overlay shows open";
            libc.close(fd);
            fd = -1;
            return false;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            lastError = "JNA libc not available: " + e;
            fd = -1;
            return false;
        } catch (Throwable e) {
            lastError = e.toString();
            if (fd >= 0) {
                try {
                    libc().close(fd);
                } catch (Throwable ignore) {
                }
                fd = -1;
            }
            return false;
        }
    }

    private boolean setFmtIoctl(LibC libc, int w, int h, boolean mjpeg, int sizeimage, int bufType) {
        Memory fmt = new Memory(208);
        fmt.clear();
        fmt.setInt(0, bufType);
        fmt.setInt(8, w);
        fmt.setInt(12, h);
        fmt.setInt(16, mjpeg ? V4L2_PIX_FMT_MJPEG : V4L2_PIX_FMT_YUYV);
        fmt.setInt(20, V4L2_FIELD_NONE);
        fmt.setInt(24, mjpeg ? 0 : w * 2);
        fmt.setInt(28, sizeimage);
        return libc.ioctl(fd, nativeIoctl(VIDIOC_S_FMT), fmt) == 0;
    }

    /**
     * Prevents exclusive_caps idle nodes from losing Output/Capture after jAER
     * exits, so the next S_FMT is not EINVAL after PipeWire QUERYCAP.
     */
    private void setKeepFormat(LibC libc) {
        Memory ctrl = new Memory(8);
        ctrl.clear();
        ctrl.setInt(0, CID_KEEP_FORMAT);
        ctrl.setInt(4, 1);
        if (libc.ioctl(fd, nativeIoctl(VIDIOC_S_CTRL), ctrl) != 0) {
            log.fine("v4l2 keep_format ioctl ignored errno=" + Native.getLastError());
        }
    }

    private static NativeLong nativeIoctl(int request) {
        return new NativeLong(request & 0xffffffffL);
    }

    @SuppressWarnings("unchecked")
    private static LibC libc() {
        try {
            return (LibC) Native.class.getMethod("load", String.class, Class.class)
                    .invoke(null, "c", LibC.class);
        } catch (ReflectiveOperationException e) {
            return (LibC) Native.loadLibrary("c", LibC.class);
        }
    }

    private static int fourcc(char a, char b, char c, char d) {
        return (a & 0xff) | ((b & 0xff) << 8) | ((c & 0xff) << 16) | ((d & 0xff) << 24);
    }

    public interface LibC extends Library {
        int open(String pathname, int flags);

        int close(int fd);

        int ioctl(int fd, NativeLong request, Pointer argp);

        NativeLong write(int fd, byte[] buf, NativeLong count);
    }
}
