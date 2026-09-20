package net.sf.jaer.eventprocessing.gnss;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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
            w.write("unix_ms,camera_us,aedat4_unix_us,utc,lat_deg,lon_deg,alt_m,sog_kn,cog_true_deg,fix_quality,num_sats,hdop,rmc_status,talker,raw");
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
        w.write(Long.toString(fix.aedat4UnixUs));
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
     * Keyed by AEDAT-4 packet Unix µs when that column is present and nonzero,
     * otherwise by host {@code unix_ms}. Duplicate keys keep the later row.
     */
    public static TreeMap<Long, GnssFix> load(File sidecar) throws IOException {
        TreeMap<Long, GnssFix> map = new TreeMap<>();
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
                if (f == null) {
                    continue;
                }
                long key = f.aedat4UnixUs > 0 ? f.aedat4UnixUs : f.receivedUnixMs;
                if (key > 0) {
                    map.put(key, f);
                }
            }
        }
        return map;
    }

    static GnssFix parseRow(String line) {
        String[] p = splitCsv(line);
        if (p == null || p.length < 14) {
            return null;
        }
        try {
            boolean v2 = p.length >= 15;
            int i = 0;
            GnssFix f = new GnssFix();
            f.receivedUnixMs = Long.parseLong(p[i++]);
            f.cameraUs = Integer.parseInt(p[i++]);
            if (v2) {
                f.aedat4UnixUs = p[i].isEmpty() ? 0L : Long.parseLong(p[i]);
                i++;
            }
            f.utc = p[i++];
            f.latDeg = parseD(p[i++]);
            f.lonDeg = parseD(p[i++]);
            f.altM = parseD(p[i++]);
            f.sogKnots = parseD(p[i++]);
            f.cogTrueDeg = parseD(p[i++]);
            f.fixQuality = Integer.parseInt(p[i++]);
            f.numSats = Integer.parseInt(p[i++]);
            f.hdop = parseD(p[i++]);
            f.rmcStatus = p[i].isEmpty() ? 0 : p[i].charAt(0);
            i++;
            f.talker = p[i++];
            f.raw = p[i];
            return f;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Split into fields; last field is the remainder (NMEA). */
    private static String[] splitCsv(String line) {
        if (line == null) {
            return null;
        }
        ArrayList<String> out = new ArrayList<>(16);
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ',') {
                out.add(uncsv(line.substring(start, i)));
                start = i + 1;
            }
        }
        out.add(uncsv(line.substring(start)));
        return out.toArray(new String[0]);
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
