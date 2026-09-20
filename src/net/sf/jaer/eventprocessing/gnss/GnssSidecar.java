package net.sf.jaer.eventprocessing.gnss;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.jaer.eventio.AEDataFile;

/**
 * CSV sidecar next to an AEDAT recording ({@code name.gnss.csv}).
 */
public final class GnssSidecar {

    public static final String EXTENSION = ".gnss.csv";
    private static final ConcurrentHashMap<String, Boolean> OPEN = new ConcurrentHashMap<>();

    private GnssSidecar() {
    }

    public static File fileForRecording(File recording) {
        if (recording == null) {
            return null;
        }
        String name = recording.getName();
        String base = stripKnownExtension(name);
        File parent = recording.getParentFile();
        return new File(parent == null ? new File(".") : parent, base + EXTENSION);
    }

    static String stripKnownExtension(String name) {
        String[] ext = {
            AEDataFile.DATA_FILE_EXTENSION_AEDAT4,
            AEDataFile.DATA_FILE_EXTENSION_AEDAT2,
            AEDataFile.DATA_FILE_EXTENSION_AEDZ,
            AEDataFile.DATA_FILE_EXTENSION,
            AEDataFile.OLD_DATA_FILE_EXTENSION
        };
        for (String e : ext) {
            if (name.toLowerCase(Locale.ROOT).endsWith(e)) {
                return name.substring(0, name.length() - e.length());
            }
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * @return writer, or {@code null} if another filter already owns this path
     */
    public static BufferedWriter tryOpen(File sidecar, File recording) throws IOException {
        if (sidecar == null) {
            return null;
        }
        String key = sidecar.getAbsolutePath();
        if (OPEN.putIfAbsent(key, Boolean.TRUE) != null) {
            return null;
        }
        try {
            BufferedWriter w = Files.newBufferedWriter(sidecar.toPath(), StandardCharsets.UTF_8);
            w.write("# jAER GNSS sidecar (NMEA GGA/RMC/VTG)");
            w.newLine();
            w.write("# recording=" + (recording == null ? "" : recording.getAbsolutePath()));
            w.newLine();
            w.write("unix_ms,camera_us,utc,lat_deg,lon_deg,alt_m,sog_kn,cog_true_deg,fix_quality,num_sats,hdop,rmc_status,talker,raw");
            w.newLine();
            w.flush();
            return w;
        } catch (IOException e) {
            OPEN.remove(key);
            throw e;
        }
    }

    public static void close(File sidecar, BufferedWriter w) throws IOException {
        if (w != null) {
            w.close();
        }
        if (sidecar != null) {
            OPEN.remove(sidecar.getAbsolutePath());
        }
    }

    public static void writeRow(BufferedWriter w, GnssFix fix) throws IOException {
        if (w == null || fix == null) {
            return;
        }
        w.write(Long.toString(fix.receivedUnixMs));
        w.write(',');
        w.write(Integer.toString(fix.cameraUs));
        w.write(',');
        w.write(csv(fix.utc));
        w.write(',');
        w.write(num(fix.latDeg));
        w.write(',');
        w.write(num(fix.lonDeg));
        w.write(',');
        w.write(num(fix.altM));
        w.write(',');
        w.write(num(fix.sogKnots));
        w.write(',');
        w.write(num(fix.cogTrueDeg));
        w.write(',');
        w.write(Integer.toString(fix.fixQuality));
        w.write(',');
        w.write(Integer.toString(fix.numSats));
        w.write(',');
        w.write(num(fix.hdop));
        w.write(',');
        w.write(fix.rmcStatus == 0 ? "" : Character.toString(fix.rmcStatus));
        w.write(',');
        w.write(csv(fix.talker));
        w.write(',');
        w.write(csv(fix.raw));
        w.newLine();
        w.flush();
    }

    /**
     * Keyed by camera timestamp (µs).
     */
    public static TreeMap<Integer, GnssFix> load(File sidecar) throws IOException {
        TreeMap<Integer, GnssFix> map = new TreeMap<>();
        if (sidecar == null || !sidecar.isFile()) {
            return map;
        }
        try (BufferedReader r = Files.newBufferedReader(sidecar.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.startsWith("unix_ms")) {
                    continue;
                }
                GnssFix f = parseRow(line);
                if (f != null) {
                    map.put(f.cameraUs, f);
                }
            }
        }
        return map;
    }

    static GnssFix parseRow(String line) {
        String[] p = splitCsv(line, 14);
        if (p == null) {
            return null;
        }
        try {
            GnssFix f = new GnssFix();
            f.receivedUnixMs = Long.parseLong(p[0]);
            f.cameraUs = Integer.parseInt(p[1]);
            f.utc = p[2];
            f.latDeg = parseD(p[3]);
            f.lonDeg = parseD(p[4]);
            f.altM = parseD(p[5]);
            f.sogKnots = parseD(p[6]);
            f.cogTrueDeg = parseD(p[7]);
            f.fixQuality = Integer.parseInt(p[8]);
            f.numSats = Integer.parseInt(p[9]);
            f.hdop = parseD(p[10]);
            f.rmcStatus = p[11].isEmpty() ? 0 : p[11].charAt(0);
            f.talker = p[12];
            f.raw = p[13];
            return f;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String[] splitCsv(String line, int n) {
        String[] out = new String[n];
        int start = 0;
        int idx = 0;
        for (int i = 0; i < line.length() && idx < n - 1; i++) {
            if (line.charAt(i) == ',') {
                out[idx++] = uncsv(line.substring(start, i));
                start = i + 1;
            }
        }
        if (idx != n - 1) {
            return null;
        }
        out[n - 1] = uncsv(line.substring(start));
        return out;
    }

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\n', ' ').replace('\r', ' ').replace(',', ' ');
    }

    private static String uncsv(String s) {
        return s == null ? "" : s;
    }

    private static String num(double v) {
        if (Double.isNaN(v)) {
            return "";
        }
        return String.format(Locale.US, "%.8f", v);
    }

    private static double parseD(String s) {
        if (s == null || s.isEmpty()) {
            return Double.NaN;
        }
        return Double.parseDouble(s);
    }
}
