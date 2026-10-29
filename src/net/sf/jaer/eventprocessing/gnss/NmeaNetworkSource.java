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
import java.util.Locale;
import java.util.function.BooleanSupplier;
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
    /** False once {@link NmeaGnssFilter} is disabled, even if {@link #stop} has not joined yet. */
    private final BooleanSupplier filterEnabled;
    /** TCP_CLIENT: dial the phone hotspot gateway instead of {@link #host}. */
    private final boolean hotspotGateway;

    private volatile boolean run = true;
    /** Address of the current TCP attempt, for status text. */
    private volatile String dialed = "";
    private boolean loggedLine;
    private Thread thread;
    private Socket client;
    private ServerSocket server;
    private DatagramSocket udp;

    NmeaNetworkSource(Transport transport, String host, int port,
            Consumer<String> lines, Consumer<String> status, BooleanSupplier filterEnabled,
            boolean hotspotGateway) {
        this.transport = transport;
        this.host = host == null ? "" : host.trim();
        this.port = port;
        this.lines = lines;
        this.status = status;
        this.filterEnabled = filterEnabled == null ? () -> false : filterEnabled;
        this.hotspotGateway = hotspotGateway;
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
        while (run && filterEnabled.getAsBoolean()) {
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
                if (run && filterEnabled.getAsBoolean()) {
                    status.accept(failureStatus(e));
                    log.warning("GNSS " + transport + " " + dialedHost() + ":" + port + " failed: " + e);
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
        if (!filterEnabled.getAsBoolean()) {
            return;
        }
        String dial = resolveTcpHost();
        dialed = dial;
        if (dial.isEmpty()) {
            status.accept("GNSS: join the phone hotspot (gpsdRelay listens on 0.0.0.0)");
            log.warning("GNSS TCP_CLIENT: no phone hotspot and host is empty");
            Thread.sleep(2000);
            return;
        }
        status.accept("GNSS: connecting " + dial + ":" + port);
        log.info("GNSS connecting TCP " + dial + ":" + port);
        Socket s = new Socket();
        client = s;
        s.connect(new InetSocketAddress(dial, port), CONNECT_TIMEOUT_MS);
        s.setSoTimeout(SO_TIMEOUT_MS);
        status.accept("GNSS: TCP " + dial + ":" + port);
        log.info("GNSS connected TCP " + dial + ":" + port + " local=" + s.getLocalSocketAddress());
        readSocket(s);
    }

    /**
     * Phone hotspot gateway when {@link #hotspotGateway} is on, otherwise
     * {@link #host}. {@code 0.0.0.0} is the phone's listen address, not a dial target.
     */
    private String resolveTcpHost() {
        if (hotspotGateway) {
            String gw = HotspotGateway.phoneAddress();
            if (gw != null && !gw.isBlank()) {
                return gw;
            }
        }
        if (host.isEmpty() || "0.0.0.0".equals(host)) {
            return "";
        }
        return host;
    }

    private String dialedHost() {
        return dialed == null || dialed.isEmpty() ? host : dialed;
    }

    private String failureStatus(Exception e) {
        String msg = e.getMessage() == null ? e.toString() : e.getMessage();
        String where = dialedHost();
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("refused")) {
            return "GNSS: " + where + ":" + port
                    + " refused. Start gpsdRelay (listen 0.0.0.0:" + port + ") after the hotspot is on.";
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "GNSS: " + where + ":" + port + " timed out";
        }
        return "GNSS net: " + msg;
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
            int quietTicks = 0;
            boolean sawLine = false;
            boolean warnedQuiet = false;
            while (run && !s.isClosed()) {
                int c;
                try {
                    c = r.read();
                } catch (SocketTimeoutException e) {
                    if (!sawLine && !warnedQuiet && ++quietTicks >= 16) {
                        warnedQuiet = true;
                        status.accept("GNSS: connected, no NMEA. Phone: NMEA generation on, then open Maps until it has a fix.");
                        log.warning("GNSS TCP up but no NMEA from " + dialedHost() + ":" + port);
                    }
                    continue;
                }
                quietTicks = 0;
                if (c < 0) {
                    status.accept("GNSS: peer closed");
                    log.warning("GNSS TCP peer closed " + host + ":" + port);
                    return;
                }
                if (c == '\n' || c == '\r') {
                    if (emitLine(acc) && !sawLine) {
                        sawLine = true;
                        status.accept("GNSS: TCP " + dialedHost() + ":" + port);
                    }
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

    /** @return true when a non-empty line was delivered */
    private boolean emitLine(StringBuilder acc) {
        if (acc.length() == 0) {
            return false;
        }
        String line = acc.toString();
        acc.setLength(0);
        if (!loggedLine) {
            loggedLine = true;
            log.info("GNSS NMEA " + line);
        }
        lines.accept(line);
        return true;
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
