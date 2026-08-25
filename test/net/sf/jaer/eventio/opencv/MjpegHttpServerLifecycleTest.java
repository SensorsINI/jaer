package net.sf.jaer.eventio.opencv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Test;

/** Real loopback-socket acceptance tests for the MJPEG server lifecycle. */
public class MjpegHttpServerLifecycleTest {

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static final String WORKER_THREAD_PREFIX = "OpenCV-MJPEG-client";
    private static final int MAX_WORKERS = 4;
    private static final int MAX_ACCEPTED_CLIENTS = 12;
    private static final long SHORT_BOUND_MS = 3000;

    private final List<MjpegHttpServer> servers = new ArrayList<>();
    private final List<Socket> sockets = new ArrayList<>();

    /** Keep the NetBeans Ant runner on its JUnit-4 adapter path. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(MjpegHttpServerLifecycleTest.class);
    }

    @After
    public void tearDown() throws Exception {
        for (Socket socket : sockets) {
            closeQuietly(socket);
        }
        for (MjpegHttpServer server : servers) {
            server.stop();
        }
        assertTrue("MJPEG client worker leaked after test cleanup",
                await(() -> workerThreadCount() == 0, SHORT_BOUND_MS));
    }

    @Test(timeout = 10000)
    public void occupiedPortFailureLeavesSameInstanceReusable() throws Exception {
        int port;
        MjpegHttpServer server;
        try (ServerSocket occupied = new ServerSocket()) {
            occupied.setReuseAddress(false);
            occupied.bind(new InetSocketAddress(LOOPBACK, 0));
            port = occupied.getLocalPort();
            server = server(port);

            try {
                server.start();
                fail("start unexpectedly succeeded on an occupied port");
            } catch (IOException expected) {
                // The failed start must leave no partially published server state.
            }
        }

        server.start();
        String response = request(port, "/");
        assertTrue(response.startsWith("HTTP/1.0 200 OK\r\n"));
        assertTrue(response.contains("<img src=\"/video.mjpg\""));
    }

    @Test(timeout = 12000)
    public void slowClientsUseFixedWorkersAndOverflowSocketsClose() throws Exception {
        int port = reservePort();
        MjpegHttpServer server = server(port);
        server.start();

        for (int i = 0; i < 40; i++) {
            Socket socket = connect(port);
            sockets.add(socket);
            socket.getOutputStream().write(
                    "GET / HTTP/1.0\r\nX-Stalled: ".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
        }

        await(() -> workerThreadCount() > MAX_WORKERS || closedPeerCount() > 0, 1000);
        List<String> violations = new ArrayList<>();
        int workers = workerThreadCount();
        if (workers > MAX_WORKERS) {
            violations.add("live client workers=" + workers + " (limit " + MAX_WORKERS + ")");
        }
        int accepted = server.getClientCount();
        if (accepted > MAX_ACCEPTED_CLIENTS) {
            violations.add("retained clients=" + accepted + " (limit " + MAX_ACCEPTED_CLIENTS + ")");
        }
        if (!await(() -> closedPeerCount() > 0, SHORT_BOUND_MS)) {
            violations.add("no overflow client socket was closed");
        }
        assertTrue(String.join("; ", violations), violations.isEmpty());
    }

    @Test(timeout = 12000)
    public void stopClosesClientsAndWorkersThenAllowsRestartAndRebind() throws Exception {
        int port = reservePort();
        MjpegHttpServer server = server(port);
        server.start();

        for (int i = 0; i < MAX_ACCEPTED_CLIENTS; i++) {
            Socket socket = connect(port);
            sockets.add(socket);
            socket.getOutputStream().write(
                    "GET /video.mjpg HTTP/1.0\r\nX-Stalled: ".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
        }
        assertTrue("server did not retain the expected bounded client set",
                await(() -> server.getClientCount() == MAX_ACCEPTED_CLIENTS, SHORT_BOUND_MS));

        ExecutorService stopper = Executors.newSingleThreadExecutor();
        try {
            Future<?> stopped = stopper.submit(server::stop);
            stopped.get(SHORT_BOUND_MS, TimeUnit.MILLISECONDS);
        } finally {
            stopper.shutdownNow();
            stopper.awaitTermination(SHORT_BOUND_MS, TimeUnit.MILLISECONDS);
        }

        assertTrue("stop returned before all client sockets closed",
                await(() -> closedPeerCount() == sockets.size(), SHORT_BOUND_MS));
        assertTrue("stop returned before all client workers exited",
                workerThreadCount() == 0);

        server.start();
        String response = request(port, "/index.html");
        assertTrue(response.startsWith("HTTP/1.0 200 OK\r\n"));
        server.stop();

        try (ServerSocket rebound = new ServerSocket()) {
            rebound.setReuseAddress(true);
            rebound.bind(new InetSocketAddress(LOOPBACK, port));
        }
    }

    @Test(timeout = 10000)
    public void htmlAndSnapshotResponsesRemainValid() throws Exception {
        int port = reservePort();
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, 1, 2, 3, (byte) 0xff, (byte) 0xd9};
        MjpegHttpServer server = server(port);
        server.setLatestJpeg(jpeg);
        server.start();

        String html = request(port, "/");
        assertTrue(html.startsWith("HTTP/1.0 200 OK\r\n"));
        assertTrue(html.contains("Content-Type: text/html; charset=utf-8\r\n"));
        assertTrue(html.contains("<img src=\"/video.mjpg\""));

        byte[] snapshot = requestBytes(port, "/snapshot.jpg");
        int bodyOffset = headerEnd(snapshot);
        String headers = new String(snapshot, 0, bodyOffset, StandardCharsets.US_ASCII);
        assertTrue(headers.startsWith("HTTP/1.0 200 OK\r\n"));
        assertTrue(headers.contains("Content-Type: image/jpeg\r\n"));
        assertTrue(headers.contains("Content-Length: " + jpeg.length + "\r\n"));
        byte[] body = new byte[snapshot.length - bodyOffset];
        System.arraycopy(snapshot, bodyOffset, body, 0, body.length);
        assertArrayEquals(jpeg, body);
    }

    private MjpegHttpServer server(int port) {
        MjpegHttpServer server = new MjpegHttpServer(LOOPBACK.getHostAddress(), port);
        servers.add(server);
        return server;
    }

    private Socket connect(int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(LOOPBACK, port), 1000);
        socket.setSoTimeout(20);
        return socket;
    }

    private String request(int port, String path) throws IOException {
        return new String(requestBytes(port, path), StandardCharsets.UTF_8);
    }

    private byte[] requestBytes(int port, String path) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(LOOPBACK, port), 1000);
            socket.setSoTimeout((int) SHORT_BOUND_MS);
            socket.getOutputStream().write(
                    ("GET " + path + " HTTP/1.0\r\nHost: localhost\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            socket.getInputStream().transferTo(response);
            return response.toByteArray();
        }
    }

    private int closedPeerCount() {
        int closed = 0;
        for (Socket socket : sockets) {
            try {
                if (socket.getInputStream().read() < 0) {
                    closed++;
                }
            } catch (SocketTimeoutException open) {
                // No response and no EOF means this intentionally stalled peer is open.
            } catch (IOException closedOrReset) {
                closed++;
            }
        }
        return closed;
    }

    private static int workerThreadCount() {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith(WORKER_THREAD_PREFIX)) {
                count++;
            }
        }
        return count;
    }

    private static int reservePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(LOOPBACK, 0));
            return socket.getLocalPort();
        }
    }

    private static int headerEnd(byte[] response) {
        for (int i = 0; i <= response.length - 4; i++) {
            if (response[i] == '\r' && response[i + 1] == '\n'
                    && response[i + 2] == '\r' && response[i + 3] == '\n') {
                return i + 4;
            }
        }
        fail("HTTP response did not contain a header terminator");
        return -1;
    }

    private static boolean await(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        do {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        return condition.getAsBoolean();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
