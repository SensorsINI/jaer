package net.sf.jaer.eventprocessing.gnss;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phone Wi-Fi hotspot address: the IPv4 default gateway of this PC's Wi-Fi
 * adapter, when the access point uses a locally administered BSSID (Android
 * and iPhone hotspots do). gpsdRelay listens on {@code 0.0.0.0}; this is the
 * address jAER dials.
 */
final class HotspotGateway {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final long CACHE_MS = 5000;
    private static final Pattern DEFAULT_ROUTE = Pattern.compile(
            "(?m)^\\s*0\\.0\\.0\\.0\\s+0\\.0\\.0\\.0\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+(\\d+)\\s*$");
    private static final Pattern BSSID = Pattern.compile(
            "(?i)BSSID\\s*:\\s*([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})");

    private static volatile String cached;
    private static volatile long cachedAt;
    private static String logged;

    private HotspotGateway() {
    }

    /** Gateway of the joined phone hotspot, or null. */
    static String phoneAddress() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MS) {
            return cached;
        }
        String found = null;
        try {
            if (isWindows()) {
                Set<String> wifi = wifiIpv4();
                if (!wifi.isEmpty()) {
                    found = phoneGateway(run("route", "print", "-4"), run("netsh", "wlan", "show", "interfaces"), wifi);
                }
            }
        } catch (Exception e) {
            log.fine("GNSS hotspot lookup failed: " + e);
        }
        cached = found;
        cachedAt = System.currentTimeMillis();
        if (found != null && !found.equals(logged)) {
            logged = found;
            log.info("GNSS phone hotspot gateway " + found);
        }
        return found;
    }

    /**
     * @param routePrint {@code route print -4}
     * @param netshWlan {@code netsh wlan show interfaces}
     * @param wifiIps IPv4 addresses of this PC's Wi-Fi adapters
     */
    static String phoneGateway(String routePrint, String netshWlan, Set<String> wifiIps) {
        if (routePrint == null || wifiIps == null || wifiIps.isEmpty()) {
            return null;
        }
        String bssid = connectedBssid(netshWlan);
        if (bssid == null || !locallyAdministeredMac(bssid)) {
            return null;
        }
        String gw = null;
        int metric = Integer.MAX_VALUE;
        Matcher m = DEFAULT_ROUTE.matcher(routePrint);
        while (m.find()) {
            String hop = m.group(1);
            String iface = m.group(2);
            int rowMetric = Integer.parseInt(m.group(3));
            if (!wifiIps.contains(iface) || !isPrivateLan(hop)) {
                continue;
            }
            if (rowMetric < metric) {
                metric = rowMetric;
                gw = hop;
            }
        }
        return gw;
    }

    static String connectedBssid(String netshWlan) {
        if (netshWlan == null) {
            return null;
        }
        Matcher m = BSSID.matcher(netshWlan);
        if (!m.find()) {
            return null;
        }
        return m.group(1);
    }

    static boolean locallyAdministeredMac(String mac) {
        if (mac == null || mac.length() < 2) {
            return false;
        }
        int first;
        try {
            first = Integer.parseInt(mac.substring(0, 2), 16);
        } catch (NumberFormatException e) {
            return false;
        }
        return (first & 0x02) != 0;
    }

    static boolean isPrivateLan(String ip) {
        String[] p = ip.split("\\.");
        if (p.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(p[0]);
            int b = Integer.parseInt(p[1]);
            if (a == 10) {
                return true;
            }
            if (a == 192 && b == 168) {
                return true;
            }
            return a == 172 && b >= 16 && b <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("win");
    }

    private static Set<String> wifiIpv4() throws Exception {
        Set<String> ips = new HashSet<>();
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nif.isUp() || nif.isLoopback() || !isWifi(nif)) {
                continue;
            }
            for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    ips.add(addr.getHostAddress());
                }
            }
        }
        return ips;
    }

    private static boolean isWifi(NetworkInterface nif) {
        String d = (nif.getDisplayName() == null ? "" : nif.getDisplayName()).toLowerCase(Locale.ROOT);
        String n = (nif.getName() == null ? "" : nif.getName()).toLowerCase(Locale.ROOT);
        if (d.contains("virtual") || d.contains("zerotier") || d.contains("hyper-v")
                || d.contains("wsl") || d.contains("bluetooth") || d.contains("vpn")
                || d.contains("tap") || d.contains("tun") || d.contains("tailscale")) {
            return false;
        }
        return d.contains("wi-fi") || d.contains("wifi") || d.contains("wireless")
                || n.startsWith("wlan") || n.startsWith("wlp") || n.startsWith("wl");
    }

    private static String run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(5, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return "";
        }
        return new String(out, Charset.defaultCharset());
    }
}
