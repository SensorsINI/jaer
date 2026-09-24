package net.sf.jaer.eventprocessing.witmotion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.TreeMap;

import org.junit.Test;

import net.sf.jaer.hardwareinterface.serial.witmotion.WitMotionIMU;

public class WitMotionSidecarTest {

    @Test
    public void roundTripKeepsAedat4Key() throws Exception {
        File dir = Files.createTempDirectory("witmotion-sidecar").toFile();
        File recording = new File(dir, "take.aedat4");
        File side = WitMotionSidecar.fileForRecording(recording);
        WitMotionSample s = new WitMotionSample();
        s.receivedUnixMs = 1_700_000_000_000L;
        s.cameraUs = 123456;
        s.aedat4UnixUs = 1_700_000_000_000_000L + 123456L;
        s.tempC = 24.14;
        s.ax = 0.1;
        s.ay = -1.47;
        s.az = 9.70;
        s.wx = 0;
        s.wy = 0;
        s.wz = 0;
        s.roll = -8.62;
        s.pitch = -0.60;
        s.yaw = -119.66;
        s.hx = -2930;
        s.hy = -671;
        s.hz = -6258;
        BufferedWriter w = WitMotionSidecar.tryOpen(side, recording);
        assertTrue(w != null);
        WitMotionSidecar.writeRow(w, s);
        WitMotionSidecar.close(side, w);

        TreeMap<Long, WitMotionSample> map = WitMotionSidecar.load(side);
        assertEquals(1, map.size());
        assertEquals(Long.valueOf(s.aedat4UnixUs), map.firstKey());
        WitMotionSample back = map.firstEntry().getValue();
        assertEquals(s.cameraUs, back.cameraUs);
        assertEquals(s.aedat4UnixUs, back.aedat4UnixUs);
        assertEquals(s.roll, back.roll, 1e-5);
        assertEquals(s.az, back.az, 1e-5);
        assertTrue(Double.isNaN(back.q0));
        assertTrue(Double.isNaN(back.deviceUnixS));
    }

    @Test
    public void cycleEndsWhenMessageCodeWraps() {
        WitMotionSample cycle = null;
        int last = -1;
        int commits = 0;
        int[] order = {
            WitMotionIMU.AccelerationMessage.CODE,
            WitMotionIMU.AngularVelocityMessage.CODE,
            WitMotionIMU.AngleMessage.CODE,
            WitMotionIMU.MagneticMessage.CODE
        };
        for (int n = 0; n < 3; n++) {
            for (int code : order) {
                if (cycle != null && code <= last) {
                    commits++;
                    cycle = null;
                }
                if (cycle == null) {
                    cycle = new WitMotionSample();
                }
                last = code;
            }
        }
        assertEquals(2, commits);
    }
}
