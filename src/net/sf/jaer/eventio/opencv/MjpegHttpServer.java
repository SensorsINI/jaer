/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.opencv;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal HTTP server: MJPEG at {@code /video.mjpg}, last JPEG at
 * {@code /snapshot.jpg}, HTML preview at {@code /}.
 */
public class MjpegHttpServer {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final String BOUNDARY = "jaerframe";
    private static final byte[] CRLF = {'\r', '\n'};

    private final String bindAddress;
    private final int port;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean stop;
    private final AtomicReference<byte[]> latestJpeg = new AtomicReference<>();
    private final AtomicLong jpegSeq = new AtomicLong();
    private final Object jpegLock = new Object();
    private final CopyOnWriteArrayList<Socket> clients = new CopyOnWriteArrayList<>();

    public MjpegHttpServer(String bindAddress, int port) {
        this.bindAddress = bindAddress == null || bindAddress.isBlank() ? "127.0.0.1" : bindAddress;
        this.port = port;
    }

    public synchronized void start() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return;
        }
        stop = false;
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(bindAddress, port));
        acceptThread = new Thread(this::acceptLoop, "OpenCV-MJPEG-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        log.info("OpenCV MJPEG at " + getStreamUrl());
    }

    public synchronized void stop() {
        stop = true;
        synchronized (jpegLock) {
            jpegLock.notifyAll();
        }
        for (Socket s : clients) {
            try {
                s.close();
            } catch (IOException ignore) {
            }
        }
        clients.clear();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignore) {
            }
            serverSocket = null;
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
    }

    public void setLatestJpeg(byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) {
            return;
        }
        latestJpeg.set(jpeg);
        jpegSeq.incrementAndGet();
        synchronized (jpegLock) {
            jpegLock.notifyAll();
        }
    }

    public byte[] getLatestJpeg() {
        return latestJpeg.get();
    }

    public int getClientCount() {
        return clients.size();
    }

    public String getStreamUrl() {
        String host = clientHost(bindAddress);
        return "http://" + host + ":" + port + "/video.mjpg";
    }

    public String getPageUrl() {
        String host = clientHost(bindAddress);
        return "http://" + host + ":" + port + "/";
    }

    static String clientHost(String bind) {
        if (bind == null || bind.isBlank()
                || "0.0.0.0".equals(bind)
                || "::".equals(bind)
                || "127.0.0.1".equals(bind)
                || "::1".equals(bind)) {
            return "localhost";
        }
        return bind;
    }

    private void acceptLoop() {
        while (!stop) {
            try {
                Socket sock = serverSocket.accept();
                sock.setTcpNoDelay(true);
                Thread t = new Thread(() -> handle(sock), "OpenCV-MJPEG-client");
                t.setDaemon(true);
                t.start();
            } catch (SocketException e) {
                if (!stop) {
                    log.log(Level.FINE, e.toString(), e);
                }
                break;
            } catch (IOException e) {
                if (!stop) {
                    log.log(Level.WARNING, "MJPEG accept: " + e, e);
                }
            }
        }
    }

    private void handle(Socket sock) {
        clients.add(sock);
        try {
            sock.setSoTimeout(15000);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII));
            String line = in.readLine();
            if (line == null) {
                return;
            }
            String path = parsePath(line);
            while (true) {
                String h = in.readLine();
                if (h == null || h.isEmpty()) {
                    break;
                }
            }
            sock.setSoTimeout(0);
            OutputStream raw = new BufferedOutputStream(sock.getOutputStream(), 8192);
            if ("/video.mjpg".equals(path) || "/video.mjpeg".equals(path)) {
                streamMjpeg(raw);
            } else if ("/snapshot.jpg".equals(path) || "/snapshot.jpeg".equals(path)) {
                sendSnapshot(raw);
            } else if ("/".equals(path) || "/index.html".equals(path)) {
                sendHtml(raw);
            } else {
                byte[] body = "Not found\n".getBytes(StandardCharsets.US_ASCII);
                raw.write(("HTTP/1.0 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: "
                        + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                raw.write(body);
                raw.flush();
            }
        } catch (IOException e) {
            log.log(Level.FINE, "MJPEG client: {0}", e.toString());
        } finally {
            clients.remove(sock);
            try {
                sock.close();
            } catch (IOException ignore) {
            }
        }
    }

    private void streamMjpeg(OutputStream out) throws IOException {
        String header = "HTTP/1.0 200 OK\r\n"
                + "Cache-Control: no-cache, no-store, must-revalidate\r\n"
                + "Pragma: no-cache\r\n"
                + "Connection: close\r\n"
                + "Content-Type: multipart/x-mixed-replace;boundary=" + BOUNDARY + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        long lastSeq = 0;
        while (!stop && !Thread.currentThread().isInterrupted()) {
            byte[] jpeg = waitForJpeg(lastSeq);
            if (jpeg == null) {
                continue;
            }
            lastSeq = jpegSeq.get();
            out.write(("--" + BOUNDARY).getBytes(StandardCharsets.US_ASCII));
            out.write(CRLF);
            out.write("Content-Type: image/jpeg\r\n".getBytes(StandardCharsets.US_ASCII));
            out.write(("Content-Length: " + jpeg.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(CRLF);
            out.write(jpeg);
            out.write(CRLF);
            out.flush();
        }
    }

    private void sendSnapshot(OutputStream out) throws IOException {
        byte[] jpeg = waitForJpeg(0);
        if (jpeg == null) {
            jpeg = new byte[0];
        }
        out.write(("HTTP/1.0 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: "
                + jpeg.length + "\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(jpeg);
        out.flush();
    }

    private void sendHtml(OutputStream out) throws IOException {
        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>jAER OpenCV</title></head>"
                + "<body style=\"margin:0;background:#111;color:#eee;font-family:sans-serif\">"
                + "<p style=\"margin:8px\">jAER OpenCV MJPEG — "
                + "<code>cv2.VideoCapture(\"" + getStreamUrl() + "\", cv2.CAP_FFMPEG)</code></p>"
                + "<img src=\"/video.mjpg\" alt=\"stream\" style=\"max-width:100%\"/>"
                + "</body></html>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.0 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private byte[] waitForJpeg(long afterSeq) {
        synchronized (jpegLock) {
            long deadline = System.currentTimeMillis() + 2000;
            while (!stop) {
                byte[] jpeg = latestJpeg.get();
                if (jpeg != null && jpegSeq.get() > afterSeq) {
                    return jpeg;
                }
                long wait = deadline - System.currentTimeMillis();
                if (wait <= 0) {
                    return latestJpeg.get();
                }
                try {
                    jpegLock.wait(Math.min(200, wait));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return latestJpeg.get();
                }
            }
        }
        return latestJpeg.get();
    }

    private static String parsePath(String requestLine) {
        String[] p = requestLine.split(" ");
        if (p.length < 2) {
            return "/";
        }
        String path = p[1];
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        if (path.isEmpty()) {
            return "/";
        }
        return path;
    }
}
