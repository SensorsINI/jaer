/*
 * USBRebindTester.java — temporary TCP CLI for multi-viewer USB rebind.
 */
package net.sf.jaer.hardwareinterface.usb;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.util.JaerTmpdir;
import net.sf.jaer.util.ViewerInterfaceBindingMap;

/**
 * Temporary localhost CLI wired from {@link JAERViewer}. Query bound/available
 * cameras and inject Interface-menu clicks on the EDT (same listeners as a
 * real menu selection).
 *
 * <p>Default port {@value #DEFAULT_PORT}. Off unless
 * {@code -Djaer.usbRebindTester=true}. Port:
 * {@code -Djaer.usbRebindTester.port=18997}.
 *
 * <p>PowerShell: {@code powershell -File scripts/usb-rebind.ps1 status}
 */
public final class USBRebindTester {

    public static final int DEFAULT_PORT = 18997;
    public static final String END = ".";

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Object LOCK = new Object();
    private static USBRebindTester instance;

    private final JAERViewer jaerViewer;
    private final int port;
    private final ServerSocket server;
    private final Thread acceptThread;

    private USBRebindTester(JAERViewer jaerViewer, int port) throws IOException {
        this.jaerViewer = jaerViewer;
        this.port = port;
        this.server = new ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"));
        this.acceptThread = new Thread(this::acceptLoop, "jaer-usb-rebind-tester");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
        log.info("USBRebindTester listening on 127.0.0.1:" + port
                + " (scripts/usb-rebind.ps1 help)");
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty("jaer.usbRebindTester", "false"));
    }

    public static int configuredPort() {
        try {
            return Integer.parseInt(System.getProperty("jaer.usbRebindTester.port",
                    Integer.toString(DEFAULT_PORT)));
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    /** Start once per JVM. No-op if disabled or already started. */
    public static USBRebindTester start(JAERViewer jaerViewer) {
        if (!isEnabled() || jaerViewer == null) {
            return null;
        }
        synchronized (LOCK) {
            if (instance != null) {
                return instance;
            }
            try {
                instance = new USBRebindTester(jaerViewer, configuredPort());
                return instance;
            } catch (IOException e) {
                log.log(Level.WARNING, "USBRebindTester could not bind 127.0.0.1:"
                        + configuredPort() + " — CLI disabled", e);
                return null;
            }
        }
    }

    public static USBRebindTester instance() {
        synchronized (LOCK) {
            return instance;
        }
    }

    public int getPort() {
        return port;
    }

    public void close() {
        try {
            server.close();
        } catch (IOException ignored) {
        }
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket s = server.accept();
                s.setSoTimeout(15_000);
                Thread t = new Thread(() -> handleClient(s), "jaer-usb-rebind-cli");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!server.isClosed()) {
                    log.log(Level.FINE, "USBRebindTester accept: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket s) {
        try (s;
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
            String line = in.readLine();
            if (line == null) {
                return;
            }
            if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }
            line = line.trim();
            UsbOpenTrace.event("tester", "cli", line);
            String reply;
            try {
                reply = dispatch(line);
            } catch (Exception e) {
                log.log(Level.WARNING, "USBRebindTester command failed: " + line, e);
                reply = "ERR " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n";
            }
            if (reply == null) {
                reply = "";
            }
            if (!reply.isEmpty() && !reply.endsWith("\n")) {
                reply = reply + "\n";
            }
            out.print(reply);
            out.print(END);
            out.print('\n');
            out.flush();
        } catch (IOException e) {
            log.log(Level.FINE, "USBRebindTester client: " + e.getMessage());
        }
    }

    String dispatch(String line) throws Exception {
        if (line.isEmpty()) {
            return help();
        }
        String[] tok = line.split("\\s+", 3);
        String cmd = tok[0].toLowerCase();
        switch (cmd) {
            case "help":
            case "?":
                return help();
            case "ping":
                return "pong port=" + port + " viewers=" + viewers().size() + "\n";
            case "status":
                return status();
            case "viewers":
                return dumpViewers();
            case "devices":
                return dumpDevices();
            case "map":
                return "binding map " + ViewerInterfaceBindingMap.file().getAbsolutePath() + "\n"
                        + ViewerInterfaceBindingMap.dump();
            case "coordinator":
                return SessionCameraOpenCoordinator.dump();
            case "trace":
                return tailFile(UsbOpenTrace.file(), tok.length > 1 ? parseInt(tok[1], 40) : 40);
            case "log":
                return tailFile(new File(JaerTmpdir.get(), "jAER-0.log"),
                        tok.length > 1 ? parseInt(tok[1], 40) : 40);
            case "scan":
                return scanUsb();
            case "menu":
                return onEdt(() -> menuList(requireViewer(tok, 1)), 8_000);
            case "none":
                return onEdt(() -> requireViewer(tok, 1).injectInterfaceMenuClick("None"), 8_000);
            case "refresh":
                return onEdt(() -> requireViewer(tok, 1).injectInterfaceMenuClick("Refresh"), 8_000);
            case "select":
                if (tok.length < 3) {
                    return "ERR usage: select <viewer> <index|substr>\n";
                }
                return onEdt(() -> requireViewer(tok, 1).injectInterfaceMenuClick(tok[2]), 8_000);
            case "select-unbound":
                return onEdt(() -> requireViewer(tok, 1).injectInterfaceMenuClick("unbound"), 8_000);
            case "select-key":
                if (tok.length < 3) {
                    return "ERR usage: select-key <viewer> <busN-addrM|vid:pid|substr>\n";
                }
                return onEdt(() -> requireViewer(tok, 1).injectInterfaceMenuClick(tok[2]), 8_000);
            case "exit-all":
            case "exit":
            case "quit-jaer":
                return exitAll();
            default:
                return "ERR unknown command '" + cmd + "' — type help\n";
        }
    }

    private static String help() {
        return """
                USBRebindTester  127.0.0.1:%d
                Query (no EDT unless noted)
                  help | ping | status | viewers | devices | map | coordinator
                  trace [n]     last n lines of usb-open-trace.log
                  log [n]       last n lines of jAER-0.log
                  scan          USB re-enumerate off EDT (no bind)
                Inject Interface clicks on the EDT (same listeners as the menu)
                  menu <v>                    rebuild and list items
                  none <v>                    Interface → None
                  refresh <v>                 Interface → Refresh
                  select <v> <idx|substr>     device radio (# or text)
                  select-unbound <v>          first unused enabled device
                  select-key <v> <key>        match busN-addrM / vid:pid / text
                Exit
                  exit-all                    File → Exit (all viewers)
                Viewer <v>: instance index (0), window #1, or AEViewer-0
                Client: powershell -File scripts/usb-rebind.ps1 status
                """.formatted(configuredPort());
    }

    private List<AEViewer> viewers() {
        ArrayList<AEViewer> copy = new ArrayList<>(jaerViewer.getViewers());
        copy.sort(Comparator.comparingInt(AEViewer::getViewerInstanceIndex));
        return copy;
    }

    private AEViewer requireViewer(String[] tok, int argIndex) {
        if (tok.length <= argIndex) {
            throw new IllegalArgumentException("missing viewer (0, #1, or AEViewer-0)");
        }
        AEViewer v = findViewer(tok[argIndex]);
        if (v == null) {
            throw new IllegalArgumentException("no viewer matching '" + tok[argIndex] + "'");
        }
        return v;
    }

    private AEViewer findViewer(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String s = spec.trim();
        int wantIndex = Integer.MIN_VALUE;
        if (s.regionMatches(true, 0, "AEViewer-", 0, 9)) {
            wantIndex = parseInt(s.substring(9), Integer.MIN_VALUE);
        } else if (s.startsWith("#")) {
            int n = parseInt(s.substring(1), Integer.MIN_VALUE);
            if (n != Integer.MIN_VALUE) {
                wantIndex = n - 1;
            }
        } else {
            wantIndex = parseInt(s, Integer.MIN_VALUE);
        }
        for (AEViewer v : viewers()) {
            if (wantIndex != Integer.MIN_VALUE && v.getViewerInstanceIndex() == wantIndex) {
                return v;
            }
            if (v.getViewerWindowLabel().equalsIgnoreCase(s)
                    || ("AEViewer-" + v.getViewerInstanceIndex()).equalsIgnoreCase(s)) {
                return v;
            }
        }
        return null;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String status() {
        StringBuilder sb = new StringBuilder();
        sb.append(SessionCameraOpenCoordinator.dump());
        sb.append('\n');
        sb.append(dumpViewers());
        sb.append('\n');
        sb.append(dumpDevices());
        sb.append('\n');
        sb.append("binding map\n").append(ViewerInterfaceBindingMap.dump());
        return sb.toString();
    }

    private String dumpViewers() {
        List<AEViewer> list = viewers();
        StringBuilder sb = new StringBuilder();
        sb.append("viewers ").append(list.size()).append('\n');
        if (list.isEmpty()) {
            sb.append("  (none yet — UI restore may still be constructing windows)\n");
            return sb.toString();
        }
        for (AEViewer v : list) {
            sb.append(v.dumpUsbRebindState());
        }
        return sb.toString();
    }

    private String dumpDevices() {
        HardwareInterfaceFactory factory = HardwareInterfaceFactory.instance();
        int n = factory.getCachedNumInterfacesAvailable();
        StringBuilder sb = new StringBuilder();
        sb.append("devices cached=").append(n).append('\n');
        for (int i = 0; i < n; i++) {
            HardwareInterface hw = factory.getInterface(i);
            if (hw == null) {
                sb.append(String.format("  [%d] (null)\n", i));
                continue;
            }
            String key = UsbIds.enumerationKey(hw);
            AEViewer owner = ownerOf(hw);
            boolean open;
            try {
                open = hw.isOpen();
            } catch (Throwable t) {
                open = false;
                key = key + " isOpen=" + t.getClass().getSimpleName();
            }
            sb.append(String.format("  [%d] %s open=%s owner=%s\n",
                    i, key, open,
                    owner == null ? "-" : owner.getViewerWindowLabel()));
        }
        if (n == 0) {
            sb.append("  (empty cache — scan or wait for first WAITING poll)\n");
        }
        return sb.toString();
    }

    private AEViewer ownerOf(HardwareInterface hw) {
        for (AEViewer v : viewers()) {
            AEChip chip = v.getChip();
            if (chip == null) {
                continue;
            }
            HardwareInterface taken = chip.getHardwareInterface();
            if (taken != null && UsbIds.samePhysicalDevice(hw, taken)) {
                return v;
            }
        }
        return null;
    }

    private String menuList(AEViewer v) {
        return v.listInterfaceMenuItems();
    }

    private String scanUsb() {
        HardwareInterfaceFactory factory = HardwareInterfaceFactory.instance();
        factory.markUsbEnumerationDirty();
        int n = factory.getNumInterfacesAvailable();
        UsbOpenTrace.event("tester", "scan", "cached=" + n);
        return "scan complete cached=" + n + "\n" + dumpDevices();
    }

    private String exitAll() {
        List<AEViewer> list = viewers();
        if (list.isEmpty()) {
            return "ERR no viewers to exit\n";
        }
        AEViewer first = list.get(0);
        SwingUtilities.invokeLater(first::requestExit);
        UsbOpenTrace.event("tester", "exit-all", first.getViewerWindowLabel());
        return "exit-all requested via " + first.getViewerWindowLabel() + " File → Exit\n";
    }

    private static String tailFile(File f, int n) {
        if (f == null || !f.isFile()) {
            return "(missing) " + (f == null ? "?" : f.getAbsolutePath()) + "\n";
        }
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - Math.max(1, n));
            StringBuilder sb = new StringBuilder();
            sb.append(f.getAbsolutePath()).append(" last ").append(lines.size() - from)
                    .append('/').append(lines.size()).append('\n');
            for (int i = from; i < lines.size(); i++) {
                sb.append(lines.get(i)).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return "ERR read " + f.getAbsolutePath() + ": " + e.getMessage() + "\n";
        }
    }

    private static String onEdt(ThrowingSupplier<String> task, long timeoutMs) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return task.get();
        }
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        SwingUtilities.invokeLater(() -> {
            try {
                out.set(task.get());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            return "ERR EDT did not return in " + timeoutMs
                    + " ms (Interface click or menu rebuild blocked the EDT)\n";
        }
        if (err.get() != null) {
            Throwable t = err.get();
            if (t instanceof Exception e) {
                throw e;
            }
            throw new RuntimeException(t);
        }
        String s = out.get();
        return s == null ? "" : s;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
