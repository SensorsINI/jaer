package net.sf.jaer.eventio.export;

import java.io.File;
import java.util.Locale;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.util.textio.DavisTextEventFormatter;

/**
 * Options for File → Save As offline export.
 */
public final class SaveAsOptions {

    public enum Format {
        AEDAT4("AEDAT-4", "aedat4"),
        CSV("CSV / text", "csv"),
        DSEC_H5("DSEC HDF5", "h5");

        public final String label;
        public final String extension;

        Format(String label, String extension) {
            this.label = label;
            this.extension = extension;
        }

        /**
         * Suffixes this format may write (no leading dot), longest first so
         * glued names like {@code filehdf5} match {@code hdf5} before {@code h5}.
         */
        public String[] acceptedExtensions() {
            switch (this) {
                case CSV:
                    return new String[]{"csv", "txt"};
                case DSEC_H5:
                    return new String[]{"hdf5", "h5"};
                case AEDAT4:
                default:
                    return new String[]{extension};
            }
        }

        public boolean acceptsExtension(String ext) {
            if (ext == null || ext.isEmpty()) {
                return false;
            }
            String e = ext.charAt(0) == '.' ? ext.substring(1) : ext;
            for (String a : acceptedExtensions()) {
                if (a.equalsIgnoreCase(e)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Force {@code file} to a suffix this format writes. Adds a missing
     * extension, inserts a forgotten dot ({@code 1aedat4} → {@code 1.aedat4}),
     * and replaces any other suffix (including valid jAER types that are not
     * this format, e.g. {@code .aedat} when writing AEDAT-4).
     */
    public static File ensureFormatExtension(File file, Format format) {
        if (format == null) {
            format = Format.AEDAT4;
        }
        if (file == null) {
            return new File("jAER-export." + format.extension);
        }
        String name = file.getName();
        if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            name = "jAER-export." + format.extension;
            File parent = file.getParentFile();
            return parent != null ? new File(parent, name) : new File(name);
        }
        String fixed = ensureFormatExtensionName(name, format);
        if (fixed.equals(name)) {
            return file;
        }
        File parent = file.getParentFile();
        return parent != null ? new File(parent, fixed) : new File(fixed);
    }

    static String ensureFormatExtensionName(String name, Format format) {
        if (format == null) {
            format = Format.AEDAT4;
        }
        String current = name;
        while (true) {
            int dot = current.lastIndexOf('.');
            if (dot > 0 && dot < current.length() - 1) {
                String ext = current.substring(dot + 1);
                if (format.acceptsExtension(ext)) {
                    return current.substring(0, dot) + "." + ext.toLowerCase(Locale.ROOT);
                }
                current = current.substring(0, dot);
                continue;
            }
            String glued = gluedAcceptedExtension(current, format);
            if (glued != null) {
                return current.substring(0, current.length() - glued.length()) + "." + glued;
            }
            while (current.endsWith(".") && current.length() > 1) {
                current = current.substring(0, current.length() - 1);
            }
            return current + "." + format.extension;
        }
    }

    /** {@code 1aedat4} → {@code aedat4} when that is an accepted suffix. */
    private static String gluedAcceptedExtension(String name, Format format) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : format.acceptedExtensions()) {
            if (ext.isEmpty() || lower.length() <= ext.length()) {
                continue;
            }
            if (lower.endsWith(ext) && name.charAt(name.length() - ext.length() - 1) != '.') {
                return ext;
            }
        }
        return null;
    }

    public File outputFile;
    /**
     * Recording to scan. Captured when Save starts so playback can close or
     * switch files without affecting the export.
     */
    public File sourceFile;
    public long sourceFileBytes = -1;
    public String sourceFileInfo = "";
    public long rangeStart;
    public long rangeEnd = Long.MAX_VALUE;
    public Integer aedat4EventStreamId;
    public Class<? extends AEChip> chipClass;
    public boolean filterChainGloballyEnabled = true;
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
