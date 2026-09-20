package net.sf.jaer.eventprocessing.gnss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Background NMEA reader: TCP client (phone is server), TCP server (phone
 * connects), or UDP.
 */
final class NmeaNetworkSource implements Runnable {

    enum Transport {
        TCP_CLIENT, TCP_SERVER, UDP
    }

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int SO_TIMEOUT_MS = 500;
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int MAX_LINE = 512;

    private final Transport transport;
    private final String host;
    private final int port;
    private final Consumer<String> lines;
    private final Consumer<String> status;

    private volatile boolean run = true;
    private Thread thread;
    private Socket client;
    private ServerSocket server;
    private DatagramSocket udp;

    NmeaNetworkSource(Transport transport, String host, int port,
            Consumer<String> lines, Consumer<String> status) {
        this.transport = transport;
        this.host = host == null ? "" : host.trim();
        this.port = port;
        this.lines = lines;
        this.status = status;
    }

    void start() {
        run = true;
        thread = new Thread(this, "jAER-NMEA-" + transport);
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        boolean hadClient = client != null && !client.isClosed();
        boolean hadServer = server != null && !server.isClosed();
        boolean hadUdp = udp != null && !udp.isClosed();
        run = false;
        closeQuietly();
        Thread t = thread;
        if (t != null) {
            t.interrupt();
            try {
                t.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (hadClient || hadServer || hadUdp) {
            log.info("GNSS closed " + transport + " " + host + ":" + port
                    + (hadServer ? " (listen)" : "")
                    + (hadUdp ? " (UDP)" : "")
                    + (hadClient ? " (socket)" : ""));
        }
    }

    boolean isAlive() {
        return thread != null && thread.isAlive();
    }

    @Override
    public void run() {
        while (run) {
            try {
                switch (transport) {
                    case TCP_CLIENT:
                        runTcpClient();
                        break;
                    case TCP_SERVER:
                        runTcpServer();
                        break;
                    case UDP:
                        runUdp();
                        break;
                    default:
                        return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (run) {
                    status.accept("GNSS net: " + e.getMessage());
                    log.warning("GNSS " + transport + " " + host + ":" + port + " failed: " + e);
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private void runTcpClient() throws IOException, InterruptedException {
        if (host.isEmpty()) {
            status.accept("GNSS: set host (phone IP)");
            log.warning("GNSS TCP_CLIENT: host is empty");
            Thread.sleep(2000);
            return;
        }
        status.accept("GNSS: connecting " + host + ":" + port);
        log.info("GNSS connecting TCP " + host + ":" + port);
        Socket s = new Socket();
        client = s;
        s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        s.setSoTimeout(SO_TIMEOUT_MS);
        status.accept("GNSS: TCP " + host + ":" + port);
        log.info("GNSS connected TCP " + host + ":" + port + " local=" + s.getLocalSocketAddress());
        readSocket(s);
    }

    private void runTcpServer() throws IOException {
        status.accept("GNSS: listen TCP " + port);
        log.info("GNSS listening TCP *:" + port);
        ServerSocket ss = new ServerSocket(port);
        ss.setSoTimeout(SO_TIMEOUT_MS);
        server = ss;
        while (run) {
            try {
                Socket s = ss.accept();
                client = s;
                s.setSoTimeout(SO_TIMEOUT_MS);
                status.accept("GNSS: phone " + s.getRemoteSocketAddress());
                log.info("GNSS accepted TCP from " + s.getRemoteSocketAddress());
                readSocket(s);
            } catch (SocketTimeoutException e) {
                // poll run flag
            }
        }
    }

    private void readSocket(Socket s) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII))) {
            StringBuilder acc = new StringBuilder(128);
            while (run && !s.isClosed()) {
                int c;
                try {
                    c = r.read();
                } catch (SocketTimeoutException e) {
                    continue;
                }
                if (c < 0) {
                    status.accept("GNSS: peer closed");
                    log.warning("GNSS TCP peer closed " + host + ":" + port);
                    return;
                }
                if (c == '\n' || c == '\r') {
                    emitLine(acc);
                } else if (acc.length() < MAX_LINE) {
                    acc.append((char) c);
                } else {
                    acc.setLength(0);
                }
            }
        } finally {
            closeQuietly(s);
            if (client == s) {
                client = null;
            }
        }
    }

    private void runUdp() throws IOException {
        status.accept("GNSS: UDP " + port);
        log.info("GNSS listening UDP *:" + port);
        DatagramSocket ds = new DatagramSocket(port);
        ds.setSoTimeout(SO_TIMEOUT_MS);
        udp = ds;
        byte[] buf = new byte[2048];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        while (run) {
            try {
                ds.receive(p);
                String text = new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.US_ASCII);
                for (String line : text.split("\\r?\\n")) {
                    if (!line.isEmpty()) {
                        lines.accept(line);
                    }
                }
            } catch (SocketTimeoutException e) {
                // poll
            }
        }
    }

    private void emitLine(StringBuilder acc) {
        if (acc.length() == 0) {
            return;
        }
        String line = acc.toString();
        acc.setLength(0);
        lines.accept(line);
    }

    private void closeQuietly() {
        closeQuietly(client);
        client = null;
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                // ignore
            }
            server = null;
        }
        if (udp != null) {
            udp.close();
            udp = null;
        }
    }

    private static void closeQuietly(Socket s) {
        if (s == null) {
            return;
        }
        try {
            s.close();
        } catch (IOException e) {
            // ignore
        }
    }

}
