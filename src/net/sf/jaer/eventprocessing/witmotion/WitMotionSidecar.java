package net.sf.jaer.eventprocessing.witmotion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.jaer.eventprocessing.SidecarFiles;

/**
 * CSV sidecar next to an AEDAT recording ({@code name.witmotion.csv}).
 * Column {@code aedat4_unix_us} matches {@code name.gnss.csv}.
 */
public final class WitMotionSidecar {

    public static final String EXTENSION = ".witmotion.csv";
    static final String HEADER = "unix_ms,camera_us,aedat4_unix_us,device_unix_s,temp_c,"
            + "ax,ay,az,wx,wy,wz,roll_deg,pitch_deg,yaw_deg,hx,hy,hz,q0,q1,q2,q3";
    private static final int COLUMNS = 21;
    private static final ConcurrentHashMap<String, Boolean> OPEN = new ConcurrentHashMap<>();

    private WitMotionSidecar() {
    }

    public static File fileForRecording(File recording) {
        return SidecarFiles.fileBeside(recording, EXTENSION);
    }

    /**
     * @return writer, or {@code null} if another filter already owns this path
     */
    public static BufferedWriter tryOpen(File sidecar, File recording) throws IOException {
        String rec = recording == null ? "" : recording.getAbsolutePath();
        return SidecarFiles.tryOpen(OPEN, sidecar,
                "# jAER WitMotion HWT906 sidecar",
                "# aedat4_unix_us = cameraTimestampToUnixUs(camera_us), same clock as AEDAT-4 packets and .gnss.csv",
                "# recording=" + rec,
                HEADER);
    }

    public static void close(File sidecar, BufferedWriter w) throws IOException {
        SidecarFiles.close(OPEN, sidecar, w);
    }

    /**
     * Move a sidecar to sit beside {@code newRecording}.
     *
     * @return destination sidecar, or {@code sidecar} if not moved
     */
    public static File relocate(File sidecar, File newRecording) throws IOException {
        return SidecarFiles.moveTo(sidecar, fileForRecording(newRecording));
    }

    public static void writeRow(BufferedWriter w, WitMotionSample s) throws IOException {
        if (w == null || s == null) {
            return;
        }
        w.write(Long.toString(s.receivedUnixMs));
        w.write(',');
        w.write(Integer.toString(s.cameraUs));
        w.write(',');
        w.write(Long.toString(s.aedat4UnixUs));
        w.write(',');
        w.write(num(s.deviceUnixS));
        w.write(',');
        w.write(num(s.tempC));
        w.write(',');
        w.write(num(s.ax));
        w.write(',');
        w.write(num(s.ay));
        w.write(',');
        w.write(num(s.az));
        w.write(',');
        w.write(num(s.wx));
        w.write(',');
        w.write(num(s.wy));
        w.write(',');
        w.write(num(s.wz));
        w.write(',');
        w.write(num(s.roll));
        w.write(',');
        w.write(num(s.pitch));
        w.write(',');
        w.write(num(s.yaw));
        w.write(',');
        w.write(num(s.hx));
        w.write(',');
        w.write(num(s.hy));
        w.write(',');
        w.write(num(s.hz));
        w.write(',');
        w.write(num(s.q0));
        w.write(',');
        w.write(num(s.q1));
        w.write(',');
        w.write(num(s.q2));
        w.write(',');
        w.write(num(s.q3));
        w.newLine();
        w.flush();
    }

    /**
     * Keyed by AEDAT-4 packet Unix µs when that column is nonzero, otherwise
     * by host {@code unix_ms}.
     */
    public static TreeMap<Long, WitMotionSample> load(File sidecar) throws IOException {
        TreeMap<Long, WitMotionSample> map = new TreeMap<>();
        if (sidecar == null || !sidecar.isFile()) {
            return map;
        }
        try (BufferedReader r = Files.newBufferedReader(sidecar.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.startsWith("unix_ms")) {
                    continue;
                }
                WitMotionSample s = parseRow(line);
                if (s == null) {
                    continue;
                }
                long key = s.aedat4UnixUs > 0 ? s.aedat4UnixUs : s.receivedUnixMs;
                if (key > 0) {
                    map.put(key, s);
                }
            }
        }
        return map;
    }

    static WitMotionSample parseRow(String line) {
        String[] p = line.split(",", -1);
        if (p.length < COLUMNS) {
            return null;
        }
        try {
            WitMotionSample s = new WitMotionSample();
            int i = 0;
            s.receivedUnixMs = Long.parseLong(p[i++]);
            s.cameraUs = Integer.parseInt(p[i++]);
            s.aedat4UnixUs = p[i].isEmpty() ? 0L : Long.parseLong(p[i]);
            i++;
            s.deviceUnixS = parseD(p[i++]);
            s.tempC = parseD(p[i++]);
            s.ax = parseD(p[i++]);
            s.ay = parseD(p[i++]);
            s.az = parseD(p[i++]);
            s.wx = parseD(p[i++]);
            s.wy = parseD(p[i++]);
            s.wz = parseD(p[i++]);
            s.roll = parseD(p[i++]);
            s.pitch = parseD(p[i++]);
            s.yaw = parseD(p[i++]);
            s.hx = parseD(p[i++]);
            s.hy = parseD(p[i++]);
            s.hz = parseD(p[i++]);
            s.q0 = parseD(p[i++]);
            s.q1 = parseD(p[i++]);
            s.q2 = parseD(p[i++]);
            s.q3 = parseD(p[i]);
            return s;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String num(double v) {
        if (Double.isNaN(v)) {
            return "";
        }
        return String.format(Locale.US, "%.6f", v);
    }

    private static double parseD(String s) {
        if (s == null || s.isEmpty()) {
            return Double.NaN;
        }
        return Double.parseDouble(s);
    }
}
