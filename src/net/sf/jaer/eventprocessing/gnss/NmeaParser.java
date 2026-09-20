package net.sf.jaer.eventprocessing.gnss;

/**
 * NMEA 0183 GGA / RMC / VTG (any talker GP/GN/GL/GA/GB). Checksum required when
 * {@code *} is present.
 */
public final class NmeaParser {

    private NmeaParser() {
    }

    /**
     * Merge one sentence into {@code dest}. Returns true if the sentence was
     * recognized and applied.
     */
    public static boolean apply(String line, GnssFix dest) {
        if (line == null || dest == null) {
            return false;
        }
        String s = line.trim();
        if (s.isEmpty() || s.charAt(0) != '$' || s.length() < 6) {
            return false;
        }
        if (!checksumOk(s)) {
            return false;
        }
        int star = s.indexOf('*');
        String body = star < 0 ? s.substring(1) : s.substring(1, star);
        String[] f = body.split(",", -1);
        if (f.length < 1 || f[0].length() < 5) {
            return false;
        }
        String type = f[0].substring(2).toUpperCase();
        dest.talker = f[0].substring(0, 2);
        dest.raw = s;
        dest.receivedUnixMs = System.currentTimeMillis();
        switch (type) {
            case "GGA":
                return applyGga(f, dest);
            case "RMC":
                return applyRmc(f, dest);
            case "VTG":
                return applyVtg(f, dest);
            default:
                return false;
        }
    }

    static boolean checksumOk(String sentence) {
        int star = sentence.indexOf('*');
        if (star < 0) {
            return true;
        }
        if (star + 3 > sentence.length()) {
            return false;
        }
        int xor = 0;
        for (int i = 1; i < star; i++) {
            xor ^= sentence.charAt(i);
        }
        try {
            int claimed = Integer.parseInt(sentence.substring(star + 1, star + 3), 16);
            return xor == claimed;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean applyGga(String[] f, GnssFix dest) {
        if (f.length < 10) {
            return false;
        }
        if (!f[1].isEmpty()) {
            dest.utc = f[1];
        }
        Double lat = parseLatLon(f[2], f[3], true);
        Double lon = parseLatLon(f[4], f[5], false);
        if (lat != null) {
            dest.latDeg = lat;
        }
        if (lon != null) {
            dest.lonDeg = lon;
        }
        dest.fixQuality = parseInt(f[6], dest.fixQuality);
        dest.numSats = parseInt(f[7], dest.numSats);
        dest.hdop = parseDouble(f[8], dest.hdop);
        dest.altM = parseDouble(f[9], dest.altM);
        return true;
    }

    private static boolean applyRmc(String[] f, GnssFix dest) {
        if (f.length < 10) {
            return false;
        }
        if (!f[1].isEmpty()) {
            dest.utc = f[1];
        }
        if (!f[2].isEmpty()) {
            dest.rmcStatus = Character.toUpperCase(f[2].charAt(0));
        }
        Double lat = parseLatLon(f[3], f[4], true);
        Double lon = parseLatLon(f[5], f[6], false);
        if (lat != null) {
            dest.latDeg = lat;
        }
        if (lon != null) {
            dest.lonDeg = lon;
        }
        dest.sogKnots = parseDouble(f[7], dest.sogKnots);
        dest.cogTrueDeg = parseDouble(f[8], dest.cogTrueDeg);
        return true;
    }

    private static boolean applyVtg(String[] f, GnssFix dest) {
        if (f.length < 8) {
            return false;
        }
        dest.cogTrueDeg = parseDouble(f[1], dest.cogTrueDeg);
        dest.sogKnots = parseDouble(f[5], dest.sogKnots);
        return true;
    }

    /**
     * {@code ddmm.mmmm} / {@code dddmm.mmmm} plus hemisphere.
     */
    static Double parseLatLon(String raw, String hemi, boolean lat) {
        if (raw == null || raw.isEmpty() || hemi == null || hemi.isEmpty()) {
            return null;
        }
        try {
            int degDigits = lat ? 2 : 3;
            if (raw.length() < degDigits + 1) {
                return null;
            }
            double deg = Double.parseDouble(raw.substring(0, degDigits));
            double min = Double.parseDouble(raw.substring(degDigits));
            double value = deg + min / 60.0;
            char h = Character.toUpperCase(hemi.charAt(0));
            if (h == 'S' || h == 'W') {
                value = -value;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String s, double fallback) {
        if (s == null || s.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
