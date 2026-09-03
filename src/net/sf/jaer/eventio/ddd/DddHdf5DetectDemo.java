package net.sf.jaer.eventio.ddd;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

import eu.seebetter.ini.chips.davis.imu.IMUSample;
import io.jhdf.HdfFile;
import io.jhdf.WritableHdfFile;
import io.jhdf.api.WritableGroup;
import net.sf.jaer.eventio.dsec.DsecHdf5AEInputStream;
import net.sf.jaer.eventio.dsec.DsecHdf5AEOutputStream;

/**
 * Headless detect: synthetic DDD {@code /dvs} HDF5 vs DSEC {@code /events}.
 * Run: {@code java -cp ... net.sf.jaer.eventio.ddd.DddHdf5DetectDemo}
 */
public final class DddHdf5DetectDemo {

    public static void main(String[] args) throws Exception {
        File ddd = Files.createTempFile("ddd-detect-", ".hdf5").toFile();
        ddd.deleteOnExit();
        try (WritableHdfFile hdf = HdfFile.write(ddd.toPath())) {
            WritableGroup dvs = hdf.putGroup("dvs");
            dvs.putDataset("timestamp", new long[]{1_501_953_155_000_000L, 1_501_953_155_000_100L});
            dvs.putDataset("data", new byte[]{1, 2, 3});
            WritableGroup steer = hdf.putGroup("steering_wheel_angle");
            steer.putDataset("timestamp", new long[]{1_501_953_155_000_000L});
            steer.putDataset("data", new double[][]{{1_501_953_155_000_000L, -3.5}});
        }
        if (!DddHdf5.isDddRecording(ddd)) {
            throw new IllegalStateException("synthetic DDD file not detected: " + ddd);
        }
        if (DsecHdf5AEInputStream.isDsecEventsFile(ddd)) {
            throw new IllegalStateException("DDD file must not look like DSEC");
        }
        DddHdf5.Summary sum = DddHdf5.peek(ddd);
        if (sum == null || sum.dvsRows != 2) {
            throw new IllegalStateException("peek dvsRows expected 2, got " + sum);
        }
        if (!sum.vehicleChannels.contains("steering_wheel_angle")) {
            throw new IllegalStateException("missing vehicle channel: " + sum.vehicleChannels);
        }
        File sibling = DddHdf5.aedat4Sibling(ddd);
        if (!sibling.getName().endsWith(".aedat4")) {
            throw new IllegalStateException("sibling: " + sibling);
        }
        System.out.println("PASS DDD HDF5 detect " + ddd.getName() + " overlay=" + sum.overlayText(ddd).replace('\n', ' '));

        File dsec = Files.createTempFile("dsec-not-ddd-", ".h5").toFile();
        dsec.deleteOnExit();
        DsecHdf5AEOutputStream.writeSynthetic(dsec, 640, 480,
                new int[]{1000, 1500}, new short[]{1, 2}, new short[]{3, 4}, new byte[]{0, 1});
        if (DddHdf5.isDddRecording(dsec)) {
            throw new IllegalStateException("DSEC file must not look like DDD");
        }
        System.out.println("PASS DSEC is not DDD " + dsec.getName());
        assertImu6Slots();
        System.out.println("PASS DDD IMU6 slots (cAER ax,ay,az,gx,gy,gz,temp → IMUSample)");
    }

    /**
     * cAER IMU6 floats are accel then gyro then temp; {@link IMUSample} stores
     * temp before gyro. A short[7] copy in cAER order put °C into roll.
     */
    private static void assertImu6Slots() {
        ByteBuffer bb = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(1);
        bb.putInt(1000);
        bb.putFloat(0.10f);
        bb.putFloat(0.98f);
        bb.putFloat(-0.48f);
        bb.putFloat(1.5f);
        bb.putFloat(2.5f);
        bb.putFloat(3.5f);
        bb.putFloat(48.6f);
        IMUSample s = DddHdf5ToAedat4.decodeOneImu6Event(bb.array());
        checkClose("ax", 0.10f, s.getAccelX(), 0.02f);
        checkClose("ay", 0.98f, s.getAccelY(), 0.02f);
        checkClose("az", -0.48f, s.getAccelZ(), 0.02f);
        checkClose("tilt", 1.5f, s.getGyroTiltX(), 0.2f);
        checkClose("yaw", 2.5f, s.getGyroYawY(), 0.2f);
        checkClose("roll", 3.5f, s.getGyroRollZ(), 0.2f);
        checkClose("temp", 48.6f, s.getTemperature(), 0.5f);
        float g = (float) Math.sqrt(
                s.getAccelX() * s.getAccelX() + s.getAccelY() * s.getAccelY() + s.getAccelZ() * s.getAccelZ());
        if (g < 0.9f || g > 1.3f) {
            throw new IllegalStateException("|a| expected ~1.1g, got " + g);
        }
    }

    private static void checkClose(String name, float expected, float actual, float tol) {
        if (Math.abs(expected - actual) > tol) {
            throw new IllegalStateException(name + " expected " + expected + " got " + actual);
        }
    }
}
