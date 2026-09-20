package net.sf.jaer.eventprocessing.gnss;

/**
 * One GNSS fix assembled from NMEA GGA/RMC/VTG. Missing numeric fields are
 * {@link Double#NaN} or {@code -1}.
 */
public final class GnssFix {

    public long receivedUnixMs;
    public int cameraUs;
    public String utc = "";
    public double latDeg = Double.NaN;
    public double lonDeg = Double.NaN;
    public double altM = Double.NaN;
    public double sogKnots = Double.NaN;
    public double cogTrueDeg = Double.NaN;
    public int fixQuality = -1;
    public int numSats = -1;
    public double hdop = Double.NaN;
    /** RMC status {@code A} (valid) or {@code V} (void); other if unknown. */
    public char rmcStatus = 0;
    public String talker = "";
    public String raw = "";

    public GnssFix copy() {
        GnssFix c = new GnssFix();
        c.receivedUnixMs = receivedUnixMs;
        c.cameraUs = cameraUs;
        c.utc = utc;
        c.latDeg = latDeg;
        c.lonDeg = lonDeg;
        c.altM = altM;
        c.sogKnots = sogKnots;
        c.cogTrueDeg = cogTrueDeg;
        c.fixQuality = fixQuality;
        c.numSats = numSats;
        c.hdop = hdop;
        c.rmcStatus = rmcStatus;
        c.talker = talker;
        c.raw = raw;
        return c;
    }

    public boolean hasPosition() {
        return !Double.isNaN(latDeg) && !Double.isNaN(lonDeg);
    }

    public boolean isValidFix() {
        if (rmcStatus == 'V') {
            return false;
        }
        if (fixQuality == 0) {
            return false;
        }
        return hasPosition();
    }

    public String overlayText() {
        if (!hasPosition()) {
            return "GNSS: no fix";
        }
        StringBuilder sb = new StringBuilder(96);
        sb.append(String.format("GNSS %s  %10.6f  %11.6f", isValidFix() ? "OK" : "void", latDeg, lonDeg));
        if (!Double.isNaN(altM)) {
            sb.append(String.format("  alt %.1fm", altM));
        }
        if (!Double.isNaN(sogKnots)) {
            sb.append(String.format("  SOG %.2fkn", sogKnots));
        }
        if (!Double.isNaN(cogTrueDeg)) {
            sb.append(String.format("  COG %.1f°", cogTrueDeg));
        }
        if (numSats >= 0) {
            sb.append(String.format("  n=%d", numSats));
        }
        if (!utc.isEmpty()) {
            sb.append("  ").append(utc);
        }
        return sb.toString();
    }
}
