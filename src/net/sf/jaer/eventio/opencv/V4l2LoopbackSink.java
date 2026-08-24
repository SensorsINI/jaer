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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

/**
 * Linux v4l2loopback writer: {@code VIDIOC_S_FMT} then {@code write()} of YUYV.
 * No-op on other OSes. Falls back to {@code v4l2-ctl} + FileOutputStream if JNA
 * ioctl fails.
 */
public class V4l2LoopbackSink {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int O_RDWR = 2;
    private static final int V4L2_BUF_TYPE_VIDEO_OUTPUT = 2;
    private static final int V4L2_BUF_TYPE_VIDEO_CAPTURE = 1;
    private static final int V4L2_FIELD_NONE = 1;
    private static final int V4L2_PIX_FMT_YUYV = fourcc('Y', 'U', 'Y', 'V');
    /** Linux x86_64 / aarch64 {@code sizeof(v4l2_format)==208}. */
    private static final int VIDIOC_S_FMT = 0xc0d05605;
    private static final int VIDIOC_QUERYCAP = 0x80685600;

    private final String device;
    private int fd = -1;
    private FileOutputStream fos;
    private int fmtW;
    private int fmtH;
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
        return fd >= 0 || fos != null;
    }

    public synchronized void close() {
        if (fd >= 0) {
            try {
                libc().close(fd);
            } catch (Throwable ignore) {
            }
            fd = -1;
        }
        if (fos != null) {
            try {
                fos.close();
            } catch (IOException ignore) {
            }
            fos = null;
        }
        fmtW = 0;
        fmtH = 0;
    }

    public synchronized void write(OpenCvRawFrame frame, int outW, int outH) {
        if (!isLinux()) {
            lastError = "v4l2loopback is Linux-only";
            return;
        }
        if (frame == null) {
            return;
        }
        OpenCvRawFrame scaled = (outW > 0 && outH > 0) ? frame.scaled(outW, outH) : frame;
        int w = scaled.yuyvWidth();
        int h = scaled.height;
        byte[] yuyv = scaled.toYuyv();
        try {
            ensureOpen(w, h);
            if (fd >= 0) {
                LibC libc = libc();
                NativeLong n = libc.write(fd, yuyv, new NativeLong(yuyv.length));
                if (n.longValue() < 0) {
                    lastError = "write errno=" + Native.getLastError();
                } else {
                    lastError = null;
                }
                return;
            }
            if (fos != null) {
                fos.write(yuyv);
                fos.flush();
                lastError = null;
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

    private void ensureOpen(int w, int h) throws IOException {
        if ((fd >= 0 || fos != null) && fmtW == w && fmtH == h) {
            return;
        }
        close();
        File node = new File(device);
        if (!node.exists()) {
            lastError = device + " missing (modprobe v4l2loopback …)";
            throw new IOException(lastError);
        }
        if (tryJnaOpen(w, h)) {
            fmtW = w;
            fmtH = h;
            lastError = null;
            return;
        }
        setFmtWithV4l2Ctl(w, h);
        fos = new FileOutputStream(device);
        fmtW = w;
        fmtH = h;
        lastError = null;
        log.info("v4l2loopback via v4l2-ctl + write: " + device + " " + w + "x" + h + " YUYV");
    }

    private boolean tryJnaOpen(int w, int h) {
        try {
            LibC libc = libc();
            int opened = libc.open(device, O_RDWR);
            if (opened < 0) {
                lastError = "open " + device + " errno=" + Native.getLastError();
                return false;
            }
            fd = opened;
            Memory cap = new Memory(128);
            libc.ioctl(fd, nativeIoctl(VIDIOC_QUERYCAP), cap);
            if (setFmtIoctl(libc, w, h, V4L2_BUF_TYPE_VIDEO_OUTPUT)
                    || setFmtIoctl(libc, w, h, V4L2_BUF_TYPE_VIDEO_CAPTURE)) {
                log.info("v4l2loopback JNA ioctl: " + device + " " + w + "x" + h + " YUYV");
                return true;
            }
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

    private boolean setFmtIoctl(LibC libc, int w, int h, int bufType) {
        Memory fmt = new Memory(208);
        fmt.clear();
        fmt.setInt(0, bufType);
        fmt.setInt(8, w);
        fmt.setInt(12, h);
        fmt.setInt(16, V4L2_PIX_FMT_YUYV);
        fmt.setInt(20, V4L2_FIELD_NONE);
        fmt.setInt(24, w * 2);
        fmt.setInt(28, w * h * 2);
        return libc.ioctl(fd, nativeIoctl(VIDIOC_S_FMT), fmt) == 0;
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

    private void setFmtWithV4l2Ctl(int w, int h) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("v4l2-ctl", "-d", device,
                "--set-fmt-video=width=" + w + ",height=" + h + ",pixelformat=YUYV");
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            int rc = p.waitFor();
            if (rc != 0) {
                throw new IOException("v4l2-ctl exited " + rc + " (install v4l2-utils or use JNA)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("v4l2-ctl interrupted", e);
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
