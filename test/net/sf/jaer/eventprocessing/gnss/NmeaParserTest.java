package net.sf.jaer.eventprocessing.gnss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.TreeMap;

import org.junit.Test;

public class NmeaParserTest {

    @Test
    public void ggaRmcVtg() {
        GnssFix f = new GnssFix();
        assertTrue(NmeaParser.apply(nmea("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"), f));
        assertEquals(48.1173, f.latDeg, 1e-4);
        assertEquals(11.516666, f.lonDeg, 1e-4);
        assertEquals(1, f.fixQuality);
        assertEquals(8, f.numSats);
        assertEquals(545.4, f.altM, 1e-6);
        assertTrue(NmeaParser.apply(
                nmea("GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W"), f));
        assertEquals('A', f.rmcStatus);
        assertEquals(22.4, f.sogKnots, 1e-6);
        assertEquals(84.4, f.cogTrueDeg, 1e-6);
        assertTrue(f.isValidFix());
        assertTrue(NmeaParser.apply(nmea("GNVTG,054.7,T,034.4,M,005.5,N,010.2,K"), f));
        assertEquals(54.7, f.cogTrueDeg, 1e-6);
        assertEquals(5.5, f.sogKnots, 1e-6);
    }

    @Test
    public void badChecksumRejected() {
        GnssFix f = new GnssFix();
        assertFalse(NmeaParser.apply(
                "$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*00", f));
    }

    @Test
    public void sidecarRoundtrip() throws Exception {
        File rec = File.createTempFile("FlyEye-test", ".aedat4");
        rec.deleteOnExit();
        File side = GnssSidecar.fileForRecording(rec);
        assertTrue(side.getName().endsWith(".gnss.csv"));
        GnssFix f = new GnssFix();
        NmeaParser.apply(nmea("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"), f);
        f.cameraUs = 42;
        f.receivedUnixMs = 1_725_000_000_000L;
        BufferedWriter w = GnssSidecar.tryOpen(side, rec);
        GnssSidecar.writeRow(w, f);
        GnssSidecar.close(side, w);
        TreeMap<Long, GnssFix> map = GnssSidecar.load(side);
        assertEquals(1, map.size());
        GnssFix back = map.get(f.receivedUnixMs);
        assertEquals(f.latDeg, back.latDeg, 1e-6);
        assertEquals(f.lonDeg, back.lonDeg, 1e-6);
        Files.deleteIfExists(side.toPath());
        rec.delete();
    }

    private static String nmea(String body) {
        int xor = 0;
        for (int i = 0; i < body.length(); i++) {
            xor ^= body.charAt(i);
        }
        return String.format("$%s*%02X", body, xor);
    }
}
