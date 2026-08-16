package net.sf.jaer.eventio.export;

import java.io.File;
import net.sf.jaer.util.textio.DavisTextEventFormatter;

/**
 * Options for File → Save As offline export.
 */
public final class SaveAsOptions {

    public enum Format {
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
    public Format format = Format.CSV;
    public boolean useInOutMarkers = true;
    public boolean applyEventFilters = true;
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
