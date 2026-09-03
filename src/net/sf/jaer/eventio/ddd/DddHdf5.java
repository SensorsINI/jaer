package net.sf.jaer.eventio.ddd;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import io.jhdf.api.Node;
import net.sf.jaer.eventio.dsec.DsecHdf5AEInputStream;

/**
 * Detects DDD17 / DDD20 cAER+OpenXC HDF5 recordings
 * ({@code rec&lt;unix&gt;.hdf5} from
 * <a href="https://github.com/SensorsINI/ddd20-utils">ddd20-utils</a>).
 * Layout is {@code /dvs/{data,timestamp}} plus optional OpenXC vehicle groups
 * ({@code steering_wheel_angle}, {@code vehicle_speed}, …), not DSEC
 * {@code /events/{x,y,t,p}}.
 */
public final class DddHdf5 {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** DAVIS346 used for DDD17/DDD20 (cAER {@code DVS_SHAPE}). */
    public static final int WIDTH = 346;
    public static final int HEIGHT = 260;

    private DddHdf5() {
    }

    public static boolean isHdf5Extension(File file) {
        return DsecHdf5AEInputStream.isHdf5Extension(file);
    }

    /**
     * True when {@code file} is a DDD17/DDD20-style recording (has {@code /dvs}
     * packets, is not DSEC events.h5).
     */
    public static boolean isDddRecording(File file) {
        if (file == null || !file.isFile() || !isHdf5Extension(file)) {
            return false;
        }
        if (DsecHdf5AEInputStream.isDsecEventsFile(file)) {
            return false;
        }
        try (HdfFile h = new HdfFile(file.toPath())) {
            return h.getByPath("/dvs/data") instanceof Dataset
                    && h.getByPath("/dvs/timestamp") instanceof Dataset;
        } catch (Exception e) {
            log.fine("Not a DDD HDF5 (" + file.getName() + "): " + e);
            return false;
        }
    }

    /** Sibling {@code rec….aedat4} next to the HDF5. */
    public static File aedat4Sibling(File source) {
        if (source == null) {
            return null;
        }
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        File parent = source.getParentFile();
        return parent == null ? new File(base + ".aedat4") : new File(parent, base + ".aedat4");
    }

    public static Summary peek(File file) {
        if (!isDddRecording(file)) {
            return null;
        }
        try (HdfFile h = new HdfFile(file.toPath())) {
            Dataset ts = (Dataset) h.getByPath("/dvs/timestamp");
            long n = 0;
            if (ts != null && ts.getDimensions() != null && ts.getDimensions().length > 0) {
                n = ts.getDimensions()[0];
            }
            List<String> vehicle = listVehicleChannels(h);
            return new Summary(n, vehicle);
        } catch (Exception e) {
            log.fine("DDD HDF5 peek failed: " + e);
            return new Summary(0, Collections.emptyList());
        }
    }

    static List<String> listVehicleChannels(HdfFile h) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Node> e : h.getChildren().entrySet()) {
            if (!(e.getValue() instanceof Group)) {
                continue;
            }
            String name = e.getKey();
            if ("dvs".equals(name)) {
                continue;
            }
            if (h.getByPath("/" + name + "/data") instanceof Dataset
                    && h.getByPath("/" + name + "/timestamp") instanceof Dataset) {
                names.add(name);
            }
        }
        Collections.sort(names);
        return names;
    }

    /** Open-dialog result: play this file; convert HDF5 first when {@code convertFrom} is set. */
    public static final class OpenPlan {
        public final File fileToOpen;
        public final File convertFrom;

        public OpenPlan(File fileToOpen, File convertFrom) {
            this.fileToOpen = fileToOpen;
            this.convertFrom = convertFrom;
        }
    }

    public static final class Summary {
        public final long dvsRows;
        public final List<String> vehicleChannels;

        public Summary(long dvsRows, List<String> vehicleChannels) {
            this.dvsRows = dvsRows;
            this.vehicleChannels = vehicleChannels == null
                    ? Collections.emptyList() : vehicleChannels;
        }

        public String vehiclePreview(int maxNames) {
            if (vehicleChannels.isEmpty()) {
                return "(none found)";
            }
            int n = Math.min(maxNames, vehicleChannels.size());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(vehicleChannels.get(i));
            }
            if (vehicleChannels.size() > n) {
                sb.append(", … (").append(vehicleChannels.size()).append(" channels)");
            }
            return sb.toString();
        }

        public String overlayText(File file) {
            String name = file == null ? "" : file.getName();
            return String.format(Locale.ROOT,
                    "DDD17/DDD20 HDF5 (cAER+OpenXC)%n%s%nDAVIS346 %dx%d, %,d DVS packets%n"
                            + "OpenXC: %s%nOpen to convert events+frames to AEDAT-4",
                    name, WIDTH, HEIGHT, dvsRows, vehiclePreview(4));
        }
    }
}
