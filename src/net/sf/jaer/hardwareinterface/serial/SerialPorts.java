package net.sf.jaer.hardwareinterface.serial;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * USB serial ports on Windows, Linux, and macOS. The {@link SerialPortInfo#device()}
 * string is what to open: {@code COMn}, {@code /dev/ttyACM*} or {@code /dev/serial/by-id/…},
 * or {@code /dev/cu.usbmodem…} (the macOS callout device, not {@code tty.}).
 */
public final class SerialPorts {

    private static final Logger LOG = Logger.getLogger(SerialPorts.class.getName());

    private SerialPorts() {
    }

    public static List<SerialPortInfo> list() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                return listWindows();
            }
            if (os.contains("mac")) {
                return listMac();
            }
            return listLinux();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "serial port scan failed", e);
            return List.of();
        }
    }

    /** True for macOS callout nodes that are USB serial adapters. Skips Bluetooth and the debug console. */
    static boolean includeMacCallout(String name) {
        if (name == null || !name.startsWith("cu.")) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("bluetooth") || lower.contains("debug-console") || lower.contains("wlan")) {
            return false;
        }
        return lower.contains("usb") || lower.contains("wch") || lower.contains("slab")
                || lower.contains("uart") || lower.contains("modem");
    }

    static List<SerialPortInfo> macPorts(Collection<String> calloutNames, String ioregText) {
        Map<String, UsbDevice> bySerial = new LinkedHashMap<>();
        for (UsbDevice device : parseUsbHostDevices(ioregText == null ? "" : ioregText)) {
            if (device.serial != null && device.serial.length() >= 4) {
                bySerial.putIfAbsent(device.serial, device);
            }
        }
        List<Draft> drafts = new ArrayList<>();
        if (calloutNames != null) {
            for (String name : calloutNames) {
                if (!includeMacCallout(name)) {
                    continue;
                }
                String path = name.startsWith("/dev/") ? name : "/dev/" + name;
                String node = path.substring("/dev/".length());
                UsbDevice usb = longestSerialMatch(node, bySerial);
                String shortName = node.startsWith("cu.") ? node.substring(3) : node;
                drafts.add(draft(path, shortName, usb, null));
            }
        }
        return finish(drafts);
    }

    static List<SerialPortInfo> linuxPorts(Collection<LinuxLink> byId, Collection<String> plainDevices,
            Map<String, UsbDevice> sysfsByTty) {
        List<Draft> drafts = new ArrayList<>();
        if (byId != null) {
            for (LinuxLink link : byId) {
                if (link == null || link.device == null) {
                    continue;
                }
                UsbDevice usb = sysfsByTty == null ? null : sysfsByTty.get(link.tty);
                String fromId = labelFromById(Path.of(link.device).getFileName().toString());
                String extra = link.tty == null ? null : "/dev/" + link.tty;
                Draft draft = draft(link.device, link.tty == null ? fromId : link.tty, usb, extra);
                if (usb == null || draft.label.equals(draft.shortName)) {
                    draft.label = fromId;
                }
                drafts.add(draft);
            }
        }
        if (plainDevices != null) {
            for (String path : plainDevices) {
                if (path == null || path.isEmpty()) {
                    continue;
                }
                String tty = path.startsWith("/dev/") ? path.substring(5) : path;
                String device = path.startsWith("/dev/") ? path : "/dev/" + path;
                UsbDevice usb = sysfsByTty == null ? null : sysfsByTty.get(tty);
                drafts.add(draft(device, tty, usb, null));
            }
        }
        return finish(drafts);
    }

    static List<SerialPortInfo> windowsPorts(Collection<String> comNames, String nameDump) {
        Map<String, String> friendly = parseWindowsNames(nameDump);
        List<String> coms = new ArrayList<>();
        if (comNames != null) {
            for (String name : comNames) {
                if (name == null) {
                    continue;
                }
                String com = name.toUpperCase(Locale.ROOT).trim();
                if (com.matches("COM\\d+") && !coms.contains(com)) {
                    coms.add(com);
                }
            }
        }
        for (String com : friendly.keySet()) {
            if (!coms.contains(com)) {
                coms.add(com);
            }
        }
        coms.sort(Comparator.comparingInt(SerialPorts::comNumber));
        List<Draft> drafts = new ArrayList<>();
        for (String com : coms) {
            String friendlyName = friendly.get(com);
            Draft draft = new Draft();
            draft.device = com;
            draft.shortName = com;
            draft.label = (friendlyName == null || friendlyName.isEmpty()) ? com : friendlyName;
            draft.tooltip = tooltip(com, null, friendlyName, null, null, null);
            drafts.add(draft);
        }
        return finish(drafts);
    }

    static Map<String, String> parseWindowsNames(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return map;
        }
        for (String line : text.split("\\R")) {
            int bar = line.indexOf('|');
            if (bar <= 0) {
                continue;
            }
            String com = line.substring(0, bar).trim().toUpperCase(Locale.ROOT);
            String name = line.substring(bar + 1).trim();
            if (com.matches("COM\\d+") && !name.isEmpty()) {
                map.putIfAbsent(com, name);
            }
        }
        return map;
    }

    static List<UsbDevice> parseUsbHostDevices(String text) {
        List<UsbDevice> found = new ArrayList<>();
        UsbDevice current = null;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.contains("<class IOUSBHostDevice")) {
                addIfIdentified(found, current);
                current = new UsbDevice();
                continue;
            }
            if (current == null) {
                continue;
            }
            if (line.contains("<class ")) {
                addIfIdentified(found, current);
                current = null;
                continue;
            }
            String vendor = quoted(line, "USB Vendor Name");
            String product = quoted(line, "USB Product Name");
            String serial = quoted(line, "USB Serial Number");
            if (vendor != null) {
                current.vendor = vendor;
            }
            if (product != null) {
                current.product = product;
            }
            if (serial != null) {
                current.serial = serial;
            }
            Integer vid = integerProp(line, "idVendor");
            Integer pid = integerProp(line, "idProduct");
            if (vid != null) {
                current.vid = vid;
            }
            if (pid != null) {
                current.pid = pid;
            }
            String hexVid = quoted(line, "idVendor");
            String hexPid = quoted(line, "idProduct");
            if (hexVid != null) {
                current.vidHex = hexVid;
            }
            if (hexPid != null) {
                current.pidHex = hexPid;
            }
        }
        addIfIdentified(found, current);
        return found;
    }

    private static List<SerialPortInfo> listMac() {
        List<String> names = devNames(SerialPorts::includeMacCallout);
        String ioreg = "";
        if (!names.isEmpty()) {
            ioreg = capture(List.of("ioreg", "-w", "0", "-l", "-r", "-c", "IOUSBHostDevice"));
        }
        return macPorts(names, ioreg);
    }

    private static List<SerialPortInfo> listLinux() {
        List<LinuxLink> links = new ArrayList<>();
        java.util.Set<String> covered = new java.util.HashSet<>();
        File[] entries = new File("/dev/serial/by-id").listFiles();
        if (entries != null) {
            Arrays.sort(entries, Comparator.comparing(File::getName));
            for (File entry : entries) {
                try {
                    String tty = Files.readSymbolicLink(entry.toPath()).getFileName().toString();
                    if (!isLinuxUsbTty(tty)) {
                        continue;
                    }
                    links.add(new LinuxLink("/dev/serial/by-id/" + entry.getName(), tty));
                    covered.add(tty);
                } catch (IOException e) {
                    LOG.log(Level.FINE, "skip by-id link {0}: {1}", new Object[]{entry, e.toString()});
                }
            }
        }
        List<String> plain = new ArrayList<>();
        for (String name : devNames(SerialPorts::isLinuxUsbTty)) {
            if (!covered.contains(name)) {
                plain.add("/dev/" + name);
            }
        }
        Map<String, UsbDevice> sysfs = new LinkedHashMap<>();
        for (LinuxLink link : links) {
            sysfs.put(link.tty, readSysfs(link.tty));
        }
        for (String path : plain) {
            String tty = path.substring("/dev/".length());
            sysfs.put(tty, readSysfs(tty));
        }
        return linuxPorts(links, plain, sysfs);
    }

    private static List<SerialPortInfo> listWindows() {
        List<String> com = net.sf.jaer.hardwareinterface.serial.witmotion.WinSerialPort.listPortNames();
        String dump = capture(List.of(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "Get-CimInstance Win32_PnPEntity | Where-Object { $_.Name -match '\\(COM\\d+\\)' } "
                        + "| ForEach-Object { if ($_.Name -match '\\((COM\\d+)\\)') { $matches[1] + '|' + $_.Name } }"));
        return windowsPorts(com, dump);
    }

    private static boolean isLinuxUsbTty(String name) {
        return name != null && (name.startsWith("ttyACM") || name.startsWith("ttyUSB"));
    }

    private static UsbDevice readSysfs(String tty) {
        UsbDevice usb = new UsbDevice();
        try {
            Path iface = Path.of("/sys/class/tty", tty, "device").toRealPath();
            Path parent = iface.getParent();
            if (parent == null) {
                return usb;
            }
            usb.vendor = readText(parent.resolve("manufacturer"));
            usb.product = readText(parent.resolve("product"));
            usb.serial = readText(parent.resolve("serial"));
            usb.vidHex = readText(parent.resolve("idVendor"));
            usb.pidHex = readText(parent.resolve("idProduct"));
        } catch (IOException e) {
            LOG.log(Level.FINE, "no sysfs identity for {0}", tty);
        }
        return usb;
    }

    private static String readText(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            String text = Files.readString(path).trim();
            return text.isEmpty() ? null : text;
        } catch (IOException e) {
            return null;
        }
    }

    private static List<String> devNames(java.util.function.Predicate<String> include) {
        String[] names = new File("/dev").list((dir, name) -> include.test(name));
        if (names == null || names.length == 0) {
            return List.of();
        }
        Arrays.sort(names);
        return Arrays.asList(names);
    }

    private static UsbDevice longestSerialMatch(String node, Map<String, UsbDevice> bySerial) {
        UsbDevice best = null;
        int bestLength = 0;
        for (Map.Entry<String, UsbDevice> entry : bySerial.entrySet()) {
            String serial = entry.getKey();
            if (serial.length() > bestLength && node.contains(serial)) {
                best = entry.getValue();
                bestLength = serial.length();
            }
        }
        return best;
    }

    private static Draft draft(String device, String shortName, UsbDevice usb, String extra) {
        Draft draft = new Draft();
        draft.device = device;
        draft.shortName = shortName == null ? device : shortName;
        String vendor = usb == null ? null : usb.vendor;
        String product = usb == null ? null : usb.product;
        if (product != null && !product.isEmpty()) {
            draft.label = (vendor == null || vendor.isEmpty()) ? product : vendor + " " + product;
        } else {
            draft.label = draft.shortName;
        }
        draft.tooltip = tooltip(device, vendor, product, usb == null ? null : usb.serial, vidPid(usb), extra);
        return draft;
    }

    private static List<SerialPortInfo> finish(List<Draft> drafts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Draft draft : drafts) {
            counts.merge(draft.label, 1, Integer::sum);
        }
        List<SerialPortInfo> ports = new ArrayList<>();
        for (Draft draft : drafts) {
            String label = draft.label;
            if (counts.getOrDefault(label, 0) > 1 && !label.contains(draft.shortName)) {
                label = label + " (" + draft.shortName + ")";
            }
            ports.add(new SerialPortInfo(draft.device, label, draft.tooltip));
        }
        ports.sort(Comparator.comparing(SerialPortInfo::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(ports);
    }

    private static String labelFromById(String fileName) {
        String label = fileName;
        if (label.startsWith("usb-")) {
            label = label.substring(4);
        }
        label = label.replaceFirst("-if\\d+$", "");
        return label.replace('_', ' ');
    }

    private static String vidPid(UsbDevice usb) {
        if (usb == null) {
            return null;
        }
        if (usb.vidHex != null && usb.pidHex != null) {
            return usb.vidHex.toUpperCase(Locale.ROOT) + ":" + usb.pidHex.toUpperCase(Locale.ROOT);
        }
        if (usb.vid != null && usb.pid != null) {
            return String.format("%04X:%04X", usb.vid, usb.pid);
        }
        return null;
    }

    private static String tooltip(String device, String vendor, String product, String serial, String vidPid, String extra) {
        StringBuilder html = new StringBuilder("<html>");
        html.append(escape(device));
        if (product != null && !product.isEmpty()) {
            String name = (vendor == null || vendor.isEmpty()) ? product : vendor + " " + product;
            html.append("<br>").append(escape(name));
        }
        if (serial != null && !serial.isEmpty()) {
            html.append("<br>serial ").append(escape(serial));
        }
        if (vidPid != null && !vidPid.isEmpty()) {
            html.append("<br>").append(escape(vidPid));
        }
        if (extra != null && !extra.isEmpty()) {
            html.append("<br>").append(escape(extra));
        }
        html.append("</html>");
        return html.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void addIfIdentified(List<UsbDevice> found, UsbDevice device) {
        if (device == null) {
            return;
        }
        if ((device.serial != null && !device.serial.isEmpty())
                || (device.product != null && !device.product.isEmpty())) {
            found.add(device);
        }
    }

    private static String quoted(String line, String key) {
        String mark = "\"" + key + "\" = \"";
        int start = line.indexOf(mark);
        if (start < 0) {
            return null;
        }
        start += mark.length();
        int end = line.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return line.substring(start, end);
    }

    private static Integer integerProp(String line, String key) {
        String mark = "\"" + key + "\" = ";
        int start = line.indexOf(mark);
        if (start < 0) {
            return null;
        }
        String rest = line.substring(start + mark.length()).trim();
        if (rest.startsWith("\"")) {
            return null;
        }
        int n = 0;
        while (n < rest.length() && Character.isDigit(rest.charAt(n))) {
            n++;
        }
        if (n == 0) {
            return null;
        }
        try {
            return Integer.parseInt(rest.substring(0, n));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int comNumber(String com) {
        int n = 0;
        for (int i = 0; i < com.length(); i++) {
            char c = com.charAt(i);
            if (c >= '0' && c <= '9') {
                n = n * 10 + (c - '0');
            }
        }
        return n == 0 ? Integer.MAX_VALUE : n;
    }

    private static String capture(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder text = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        text.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                    // process destroyed, or the pipe closed
                }
            }, "SerialPorts-io");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            reader.join(1000);
            return text.toString();
        } catch (Exception e) {
            LOG.log(Level.FINE, "command failed {0}: {1}", new Object[]{command, e.toString()});
            return "";
        }
    }

    /** Linux {@code /dev/serial/by-id} symlink and the tty it points at. */
    static final class LinuxLink {
        final String device;
        final String tty;

        LinuxLink(String device, String tty) {
            this.device = device;
            this.tty = tty;
        }
    }

    /** USB identity from ioreg or sysfs. Package-visible for tests. */
    static final class UsbDevice {
        String vendor;
        String product;
        String serial;
        Integer vid;
        Integer pid;
        String vidHex;
        String pidHex;
    }

    private static final class Draft {
        String device;
        String label;
        String shortName;
        String tooltip;
    }
}
