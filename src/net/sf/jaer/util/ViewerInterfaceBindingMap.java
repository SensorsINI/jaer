package net.sf.jaer.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.UsbIds;

/**
 * Machine-local map from {@code AEViewer-N} windows to last-bound USB cameras.
 * Lives under {@link JaerTmpdir} ({@code ${java.io.tmpdir}/jaer/}) so restart
 * can rebind the same cameras to the same windows without Preferences keys.
 * Matching uses VID:PID and bus/addr from enumeration; never opens USB.
 * In-memory cache is the source of truth; disk flush is best-effort (Windows
 * often locks the dest file during replace).
 */
public final class ViewerInterfaceBindingMap {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    public static final String FILE_NAME = "aeviewer-interface-map.properties";
    private static final Pattern VID_PID = Pattern.compile("(?i)\\b([0-9a-f]{4}):([0-9a-f]{4})\\b");
    private static final Pattern BUS_ADDR = Pattern.compile("bus(\\d+)-addr(\\d+)");
    private static final int STORE_RETRIES = 5;
    private static final long STORE_RETRY_MS = 30;

    private static Properties cache;
    private static boolean cacheLoaded;

    private ViewerInterfaceBindingMap() {
    }

    /** {@code ${java.io.tmpdir}/jaer/aeviewer-interface-map.properties}. */
    public static File file() {
        return JaerTmpdir.file(FILE_NAME);
    }

    public static final class Binding {
        public final String label;
        public final String serial;
        public final String chipClass;

        public Binding(String label, String serial, String chipClass) {
            this.label = label == null ? "" : label;
            this.serial = serial == null ? "" : serial;
            this.chipClass = chipClass == null ? "" : chipClass;
        }

        public boolean isEmpty() {
            return label.isBlank() && serial.isBlank();
        }

        /**
         * True when this enumerated interface is the remembered camera. Does
         * not open USB or read string descriptors.
         */
        public boolean matches(HardwareInterface hw) {
            if (hw == null || isEmpty()) {
                return false;
            }
            String key = UsbIds.enumerationKey(hw);
            if (!label.isBlank() && label.equals(key)) {
                return true;
            }
            String storedVidPid = ViewerInterfaceBindingMap.vidPid(label);
            String nowVidPid = ViewerInterfaceBindingMap.vidPid(key);
            String storedTopo = ViewerInterfaceBindingMap.busAddr(label);
            String nowTopo = ViewerInterfaceBindingMap.busAddr(key);
            return storedVidPid != null && storedVidPid.equals(nowVidPid)
                    && storedTopo != null && storedTopo.equals(nowTopo);
        }

        public String vidPid() {
            return ViewerInterfaceBindingMap.vidPid(label);
        }

        public boolean hasTopology() {
            return ViewerInterfaceBindingMap.busAddr(label) != null;
        }
    }

    /** Sorted {@code viewer.N.key=value} dump for {@link net.sf.jaer.hardwareinterface.usb.USBRebindTester}. */
    public static synchronized String dump() {
        Properties p = cache();
        if (p.isEmpty()) {
            return "(empty)\n";
        }
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (Object k : p.keySet()) {
            keys.add(k.toString());
        }
        java.util.Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            sb.append(k).append('=').append(p.getProperty(k, "")).append('\n');
        }
        return sb.toString();
    }

    public static synchronized Binding get(int viewerIndex) {
        Properties p = cache();
        String prefix = prefix(viewerIndex);
        Binding b = new Binding(
                p.getProperty(prefix + "label", ""),
                p.getProperty(prefix + "serial", ""),
                p.getProperty(prefix + "chip", ""));
        return b.isEmpty() ? null : b;
    }

    public static synchronized void put(int viewerIndex, String label, String serial, String chipClass) {
        Properties p = cache();
        String prefix = prefix(viewerIndex);
        if (label != null && !label.isBlank()) {
            p.setProperty(prefix + "label", label);
        } else {
            p.remove(prefix + "label");
        }
        if (serial != null && !serial.isBlank()) {
            p.setProperty(prefix + "serial", serial);
        } else {
            p.remove(prefix + "serial");
        }
        if (chipClass != null && !chipClass.isBlank()) {
            p.setProperty(prefix + "chip", chipClass);
        } else {
            p.remove(prefix + "chip");
        }
        store(p);
    }

    public static synchronized void remove(int viewerIndex) {
        Properties p = cache();
        String prefix = prefix(viewerIndex);
        p.remove(prefix + "label");
        p.remove(prefix + "serial");
        p.remove(prefix + "chip");
        store(p);
    }

    private static String prefix(int viewerIndex) {
        return "viewer." + viewerIndex + ".";
    }

    public static String vidPid(String label) {
        if (label == null) {
            return null;
        }
        Matcher m = VID_PID.matcher(label);
        return m.find() ? m.group(1).toLowerCase() + ":" + m.group(2).toLowerCase() : null;
    }

    public static String busAddr(String label) {
        if (label == null) {
            return null;
        }
        Matcher m = BUS_ADDR.matcher(label);
        return m.find() ? "bus" + m.group(1) + "-addr" + m.group(2) : null;
    }

    private static Properties cache() {
        if (!cacheLoaded) {
            cache = loadFromDisk();
            cacheLoaded = true;
        }
        return cache;
    }

    private static Properties loadFromDisk() {
        Properties p = new Properties();
        File f = file();
        if (!f.isFile()) {
            return p;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            log.log(Level.WARNING, "could not read " + f.getAbsolutePath(), e);
        }
        return p;
    }

    /**
     * Write cache to disk. Prefer overwrite of the dest file; Windows often
     * rejects {@code Files.move} replace with "used by another process".
     */
    private static void store(Properties p) {
        File f = file();
        File tmp = JaerTmpdir.file(FILE_NAME + ".tmp");
        IOException last = null;
        for (int attempt = 1; attempt <= STORE_RETRIES; attempt++) {
            try {
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    p.store(out, "jAER AEViewer-N last USB cameras; machine-local under java.io.tmpdir/jaer");
                }
                try (FileOutputStream dest = new FileOutputStream(f)) {
                    p.store(dest, "jAER AEViewer-N last USB cameras; machine-local under java.io.tmpdir/jaer");
                }
                Files.deleteIfExists(tmp.toPath());
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Files.copy(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(tmp.toPath());
                    return;
                } catch (IOException copy) {
                    last = copy;
                }
                try {
                    Thread.sleep(STORE_RETRY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.log(Level.WARNING, "could not write " + f.getAbsolutePath()
                + " after " + STORE_RETRIES + " tries (in-memory map still used)", last);
        try {
            Files.deleteIfExists(tmp.toPath());
        } catch (IOException ignored) {
        }
    }
}
