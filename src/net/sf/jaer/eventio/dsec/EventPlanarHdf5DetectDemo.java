package net.sf.jaer.eventio.dsec;

import java.io.File;
import java.nio.file.Files;

import io.jhdf.HdfFile;
import io.jhdf.WritableHdfFile;
import io.jhdf.api.WritableGroup;
import net.sf.jaer.eventio.ddd.DddHdf5;

/**
 * Headless detect: Event Planar {@code /events/{xs,ys,ts,ps}} vs DSEC {@code x,y,t,p}.
 * Run: {@code java -cp ... net.sf.jaer.eventio.dsec.EventPlanarHdf5DetectDemo}
 */
public final class EventPlanarHdf5DetectDemo {

    public static void main(String[] args) throws Exception {
        File planar = Files.createTempFile("event-planar-detect-", ".h5").toFile();
        planar.deleteOnExit();
        try (WritableHdfFile hdf = HdfFile.write(planar.toPath())) {
            hdf.putAttribute("t0", 100.0);
            hdf.putAttribute("duration", 0.002);
            hdf.putAttribute("sensor_resolution", new long[]{240, 180});
            WritableGroup events = hdf.putGroup("events");
            events.putDataset("xs", new short[]{10, 20, 30});
            events.putDataset("ys", new short[]{40, 50, 60});
            events.putDataset("ts", new double[]{100.0, 100.0005, 100.001});
            events.putDataset("ps", new byte[]{0, 1, 0});
        }
        if (!DsecHdf5AEInputStream.isEventPlanarEventsFile(planar)) {
            throw new IllegalStateException("synthetic Event Planar file not detected: " + planar);
        }
        if (DsecHdf5AEInputStream.isDsecEventsFile(planar)) {
            throw new IllegalStateException("Event Planar must not look like DSEC");
        }
        if (DddHdf5.isDddRecording(planar)) {
            throw new IllegalStateException("Event Planar must not look like DDD");
        }
        DsecHdf5AEInputStream.SensorSize sz = DsecHdf5AEInputStream.peekSensorSize(planar);
        if (sz == null || sz.width != 240 || sz.height != 180) {
            throw new IllegalStateException("peekSensorSize expected 240x180, got " + sz);
        }
        System.out.println("PASS Event Planar detect " + planar.getName() + " size=" + sz);

        File dsec = Files.createTempFile("dsec-not-planar-", ".h5").toFile();
        dsec.deleteOnExit();
        DsecHdf5AEOutputStream.writeSynthetic(dsec, 640, 480,
                new int[]{1000, 1500}, new short[]{1, 2}, new short[]{3, 4}, new byte[]{0, 1});
        if (DsecHdf5AEInputStream.isEventPlanarEventsFile(dsec)) {
            throw new IllegalStateException("DSEC file must not look like Event Planar");
        }
        System.out.println("PASS DSEC is not Event Planar " + dsec.getName());

        if (args.length > 0) {
            File sample = new File(args[0]);
            if (!DsecHdf5AEInputStream.isEventPlanarEventsFile(sample)) {
                throw new IllegalStateException("sample not Event Planar: " + sample);
            }
            DsecHdf5AEInputStream.SensorSize sampleSize = DsecHdf5AEInputStream.peekSensorSize(sample);
            System.out.println("PASS sample " + sample.getName() + " size=" + sampleSize);
        }
    }
}
