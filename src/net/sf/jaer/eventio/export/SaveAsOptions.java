package net.sf.jaer.eventio.export;

import java.io.File;
import net.sf.jaer.util.textio.DavisTextEventFormatter;

/**
 * Options for File → Save As offline export.
 */
public final class SaveAsOptions {

    public enum Format {
        AEDAT4("AEDAT-4", "aedat4"),
        AEDZ("AEDZ compressed AEDAT-2", "aedz"),
        CSV("CSV / text", "csv"),
        DSEC_H5("DSEC HDF5", "h5");

        public final String label;
        public final String extension;

        Format(String label, String extension) {
            this.label = label;
            this.extension = extension;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public File outputFile;
    public Format format = Format.AEDAT4;
    public boolean useInOutMarkers = true;
    public boolean applyEventFilters = true;
    /** DV {@link net.sf.jaer.eventio.aedat4.dv.CompressionType} for AEDAT-4. */
    public int aedat4Compression = net.sf.jaer.eventio.aedat4.dv.CompressionType.LZ4;
    public DavisTextEventFormatter csvFormatter = DavisTextEventFormatter.rpg();
    /** HVS sidecar APS frames as compressed PNG. */
    public boolean writeFrames = false;
    /** HVS sidecar IMU CSV. */
    public boolean writeImu = false;
    public int sensorWidth;
    public int sensorHeight;

    public String basename() {
        if (outputFile == null) {
            return "export";
        }
        String name = outputFile.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }

    public File parentDir() {
        File p = outputFile != null ? outputFile.getParentFile() : null;
        return p != null ? p : new File(".");
    }

    public File framesDir() {
        return new File(parentDir(), basename() + "-frames");
    }

    public File imuFile() {
        return new File(parentDir(), basename() + "-imu.csv");
    }
}
