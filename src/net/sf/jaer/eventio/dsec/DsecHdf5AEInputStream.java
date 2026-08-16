package net.sf.jaer.eventio.dsec;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.swing.ProgressMonitor;

import io.jhdf.HdfFile;
import io.jhdf.api.Attribute;
import io.jhdf.api.Dataset;
import io.jhdf.api.Node;
import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Plays a single-camera
 * <a href="https://dsec.ifi.uzh.ch/data-format/">DSEC</a>-layout event recording
 * ({@code events.h5} / {@code .h5} / {@code .hdf5}).
 * <p>
 * DSEC stores <em>cooked</em> polarity streams (column, row, polarity, time) —
 * not a vendor raw address encoding. Events are packed into
 * {@link AEPacketRaw} via the selected chip's
 * {@link TypedEventExtractor#getAddressFromCell(int, int, int)}.
 * <p>
 * Expected layout:
 * <pre>
 * /events/p  polarity
 * /events/t  timestamps (µs)
 * /events/x  column
 * /events/y  row
 * /ms_to_idx millisecond → event index
 * /t_offset  add to {@code t} for image-aligned clock
 * </pre>
 * Left and right cameras are separate files; open one at a time. Blosc+ZSTD
 * compression is handled by {@link BloscHdf5Filter}.
 * <p>
 * Resolution is <em>not</em> assumed VGA: use {@link #peekSensorSize(File)}
 * (HDF5 attributes when present, else max {@code x}/{@code y} sample). Prefer
 * {@link ch.unizh.ini.jaer.chip.retina.DVS640} for 640×480 or
 * {@link ch.unizh.ini.jaer.chip.retina.DVS1280x720SD} for 1280×720
 * (e.g. Prophesee EVK4 exports in this layout).
 *
 * @see <a href="https://dsec.ifi.uzh.ch/data-format/">DSEC Data Format</a>
 */
public class DsecHdf5AEInputStream implements AEFileInputStreamInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    public static final String DATA_FILE_EXTENSION_H5 = "h5";
    public static final String DATA_FILE_EXTENSION_HDF5 = "hdf5";

    /** Classic DSEC Gen3.1 VGA size (default when size cannot be peeked). */
    public static final int DEFAULT_WIDTH = 640;
    public static final int DEFAULT_HEIGHT = 480;
    /** @deprecated use {@link #DEFAULT_WIDTH} */
    @Deprecated
    public static final int WIDTH = DEFAULT_WIDTH;
    /** @deprecated use {@link #DEFAULT_HEIGHT} */
    @Deprecated
    public static final int HEIGHT = DEFAULT_HEIGHT;

    private static final int MAX_EVENTS_PER_READ = 1_000_000;
    private static final int WINDOW_EVENTS = 1 << 20; // 1M-event sliding window
    /** Events per slice when inferring size from max x/y. */
    private static final int PEEK_SAMPLE_EVENTS = 256_000;

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private final AEPacketRaw packet = new AEPacketRaw();

    private File file;
    private AEChip chip;
    private HdfFile hdf;
    private Dataset dsP;
    private Dataset dsT;
    private Dataset dsX;
    private Dataset dsY;
    private long[] msToIdx = new long[0];
    private long tOffset;
    private long eventCount;
    /** Sensor size from HDF5 attrs or max x/y sample (not assumed VGA). */
    private int sensorWidth = DEFAULT_WIDTH;
    private int sensorHeight = DEFAULT_HEIGHT;
    /** Raw {@code t[0]} from the file (before remapping to player timeline). */
    private int firstRawT;
    private int firstTimestamp;
    private int lastTimestamp;
    private long absoluteStartingTimeMs;

    private long position;
    private int mostRecentTimestamp;
    private int currentStartTimestamp;
    private boolean nonMonotonicTimeExceptionsChecked = true;
    private int timestampResetBitmask;
    private long markIn;
    private long markOut;
    private boolean markInSet;
    private boolean markOutSet;
    private boolean repeat = true;
    private final NavigableSet<Long> markers = new TreeSet<>();
    private long lastJumpTimeMs;

    /** Sliding window of decoded raw events. */
    private long windowStart = -1;
    private int windowLen;
    private int[] windowAddresses = new int[0];
    private int[] windowTimestamps = new int[0];
    private int[] windowTRel = new int[0]; // t[i] relative to first event (for seek by time)

    private TypedEventExtractor typedExtractor;

    public DsecHdf5AEInputStream(File file, AEChip chip, ProgressMonitor progressMonitor)
            throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("not a file: " + file);
        }
        if (chip == null) {
            throw new NullPointerException("AEChip is null");
        }
        this.file = file;
        this.chip = chip;
        EventExtractor2D ex = chip.getEventExtractor();
        if (!(ex instanceof TypedEventExtractor)) {
            throw new IOException("DSEC HDF5 needs a TypedEventExtractor on chip "
                    + chip.getClass().getSimpleName() + " (cooked x,y,p → address)");
        }
        this.typedExtractor = (TypedEventExtractor) ex;
        BloscHdf5Filter.ensureRegistered();
        try {
            if (progressMonitor != null) {
                progressMonitor.setNote("Opening DSEC HDF5 " + file.getName());
                progressMonitor.setMaximum(100);
                progressMonitor.setProgress(1);
            }
            hdf = new HdfFile(file.toPath());
            openDatasets();
            resolveSensorSizeFromOpenFile();
            loadIndexAndBounds(progressMonitor);
            clearMarks();
            seekToEvent(0);
            EngineeringFormat eng = new EngineeringFormat();
            eng.setPrecision(3);
            log.info(String.format(
                    "Opened DSEC HDF5 %s: %,d events, %dx%d, duration=%ss, t_offset=%d, chip=%s",
                    file.getName(),
                    eventCount,
                    sensorWidth,
                    sensorHeight,
                    eng.format(getDurationUs() * 1e-6).trim(),
                    tOffset,
                    chip.getClass().getSimpleName()));
            support.firePropertyChange(AEInputStream.EVENT_INIT, null, this);
        } catch (IOException e) {
            closeQuietly();
            throw e;
        } catch (RuntimeException e) {
            closeQuietly();
            throw new IOException("Failed to open DSEC HDF5 " + file + ": " + e.getMessage(), e);
        }
    }

    /** True if {@code file} looks like a DSEC {@code events.h5} recording. */
    public static boolean isDsecEventsFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".h5") && !name.endsWith(".hdf5")) {
            return false;
        }
        // Avoid treating TensorFlow .h5 models as DSEC (usually not named events.h5)
        if (!name.equals("events.h5") && !name.equals("events.hdf5")
                && !name.contains("events") && !name.contains("dsec")) {
            // Still allow peek for other .h5 names that have the DSEC layout
        }
        try {
            BloscHdf5Filter.ensureRegistered();
            try (HdfFile h = new HdfFile(file.toPath())) {
                return h.getByPath("/events/x") instanceof Dataset
                        && h.getByPath("/events/y") instanceof Dataset
                        && h.getByPath("/events/t") instanceof Dataset
                        && h.getByPath("/events/p") instanceof Dataset;
            }
        } catch (Exception e) {
            log.fine("Not a DSEC events HDF5 (" + file.getName() + "): " + e);
            return false;
        }
    }

    public static boolean isHdf5Extension(File file) {
        if (file == null) {
            return false;
        }
        String n = file.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".h5") || n.endsWith(".hdf5");
    }

    /**
     * Sensor geometry from a DSEC-layout HDF5 file.
     */
    public static final class SensorSize {
        public final int width;
        public final int height;
        /** {@code hdf5-attr} or {@code xy-sample}. */
        public final String origin;

        public SensorSize(int width, int height, String origin) {
            this.width = width;
            this.height = height;
            this.origin = origin;
        }

        @Override
        public String toString() {
            return width + "x" + height + " (" + origin + ")";
        }
    }

    /**
     * Peek sensor width/height without full playback open. Prefers HDF5
     * attributes ({@code width}/{@code height}, {@code sizeX}/{@code sizeY}, …)
     * on the root or {@code /events} group; otherwise samples max {@code x}/{@code y}.
     *
     * @return size, or {@code null} if the file is not readable as DSEC events
     */
    public static SensorSize peekSensorSize(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        BloscHdf5Filter.ensureRegistered();
        try (HdfFile h = new HdfFile(file.toPath())) {
            if (!(h.getByPath("/events/x") instanceof Dataset)
                    || !(h.getByPath("/events/y") instanceof Dataset)) {
                return null;
            }
            SensorSize fromAttr = sensorSizeFromAttributes(h);
            if (fromAttr != null) {
                return fromAttr;
            }
            return sensorSizeFromXySample(h);
        } catch (Exception e) {
            log.fine("Could not peek DSEC sensor size from " + file.getName() + ": " + e);
            return null;
        }
    }

    private void resolveSensorSizeFromOpenFile() {
        SensorSize fromAttr = sensorSizeFromAttributes(hdf);
        if (fromAttr != null) {
            sensorWidth = fromAttr.width;
            sensorHeight = fromAttr.height;
            log.fine("DSEC sensor size from attributes: " + fromAttr);
            return;
        }
        try {
            SensorSize sampled = sensorSizeFromXySample(hdf);
            if (sampled != null) {
                sensorWidth = sampled.width;
                sensorHeight = sampled.height;
                log.fine("DSEC sensor size from x/y sample: " + sampled);
                return;
            }
        } catch (Exception e) {
            log.warning("Could not sample DSEC x/y for sensor size: " + e);
        }
        sensorWidth = DEFAULT_WIDTH;
        sensorHeight = DEFAULT_HEIGHT;
        log.warning("Assuming default DSEC size " + sensorWidth + "x" + sensorHeight
                + " (could not peek geometry)");
    }

    private static SensorSize sensorSizeFromAttributes(HdfFile h) {
        Integer w = firstIntAttr(h, "width", "sizeX", "sensor_width", "WIDTH", "geometry_width");
        Integer ht = firstIntAttr(h, "height", "sizeY", "sensor_height", "HEIGHT", "geometry_height");
        Node events = h.getByPath("/events");
        if (events != null) {
            if (w == null) {
                w = firstIntAttr(events, "width", "sizeX", "sensor_width", "WIDTH");
            }
            if (ht == null) {
                ht = firstIntAttr(events, "height", "sizeY", "sensor_height", "HEIGHT");
            }
        }
        // Single "geometry" / "sensor_size" array attribute [w,h] or [h,w]
        if (w == null || ht == null) {
            int[] pair = firstIntPairAttr(h, "geometry", "sensor_size", "size");
            if (pair == null && events != null) {
                pair = firstIntPairAttr(events, "geometry", "sensor_size", "size");
            }
            if (pair != null) {
                // Prefer landscape (w >= h) as width×height
                if (pair[0] >= pair[1]) {
                    w = pair[0];
                    ht = pair[1];
                } else {
                    w = pair[1];
                    ht = pair[0];
                }
            }
        }
        if (w != null && ht != null && w > 0 && ht > 0 && w <= 8192 && ht <= 8192) {
            return new SensorSize(w, ht, "hdf5-attr");
        }
        return null;
    }

    private static SensorSize sensorSizeFromXySample(HdfFile h) {
        Dataset dsX = (Dataset) h.getByPath("/events/x");
        Dataset dsY = (Dataset) h.getByPath("/events/y");
        if (dsX == null || dsY == null) {
            return null;
        }
        long ntot = dsX.getDimensions()[0];
        if (ntot <= 0) {
            return null;
        }
        int n = (int) Math.min(PEEK_SAMPLE_EVENTS, ntot);
        long mid = Math.max(0L, (ntot / 2) - (n / 2L));
        long end = Math.max(0L, ntot - n);
        int xmax = -1;
        int ymax = -1;
        for (long off : new long[]{0L, mid, end}) {
            int[] xs = toIntArray(dsX.getData(new long[]{off}, new int[]{n}));
            int[] ys = toIntArray(dsY.getData(new long[]{off}, new int[]{n}));
            for (int i = 0; i < xs.length; i++) {
                int xv = xs[i] & 0xffff;
                int yv = ys[i] & 0xffff;
                if (xv > xmax) {
                    xmax = xv;
                }
                if (yv > ymax) {
                    ymax = yv;
                }
            }
        }
        if (xmax < 0 || ymax < 0) {
            return null;
        }
        return new SensorSize(xmax + 1, ymax + 1, "xy-sample");
    }

    private static Integer firstIntAttr(Node node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        Map<String, Attribute> attrs;
        try {
            attrs = node.getAttributes();
        } catch (Exception e) {
            return null;
        }
        if (attrs == null || attrs.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            for (Map.Entry<String, Attribute> e : attrs.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                    Integer v = attributeToInt(e.getValue());
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    private static int[] firstIntPairAttr(Node node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        Map<String, Attribute> attrs;
        try {
            attrs = node.getAttributes();
        } catch (Exception e) {
            return null;
        }
        if (attrs == null || attrs.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            for (Map.Entry<String, Attribute> e : attrs.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                    int[] pair = attributeToIntPair(e.getValue());
                    if (pair != null) {
                        return pair;
                    }
                }
            }
        }
        return null;
    }

    private static Integer attributeToInt(Attribute a) {
        if (a == null) {
            return null;
        }
        Object d = a.getData();
        if (d == null) {
            return null;
        }
        if (d instanceof Number) {
            return ((Number) d).intValue();
        }
        if (d instanceof int[] && ((int[]) d).length >= 1) {
            return ((int[]) d)[0];
        }
        if (d instanceof long[] && ((long[]) d).length >= 1) {
            return (int) ((long[]) d)[0];
        }
        if (d instanceof short[] && ((short[]) d).length >= 1) {
            return ((short[]) d)[0] & 0xffff;
        }
        if (d instanceof String) {
            try {
                return Integer.parseInt(((String) d).trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private static int[] attributeToIntPair(Attribute a) {
        if (a == null) {
            return null;
        }
        Object d = a.getData();
        if (d instanceof int[] && ((int[]) d).length >= 2) {
            return new int[]{((int[]) d)[0], ((int[]) d)[1]};
        }
        if (d instanceof long[] && ((long[]) d).length >= 2) {
            return new int[]{(int) ((long[]) d)[0], (int) ((long[]) d)[1]};
        }
        if (d instanceof short[] && ((short[]) d).length >= 2) {
            return new int[]{((short[]) d)[0] & 0xffff, ((short[]) d)[1] & 0xffff};
        }
        return null;
    }

    public int getSensorWidth() {
        return sensorWidth;
    }

    public int getSensorHeight() {
        return sensorHeight;
    }

    private void openDatasets() throws IOException {
        dsX = requireDataset("/events/x");
        dsY = requireDataset("/events/y");
        dsT = requireDataset("/events/t");
        dsP = requireDataset("/events/p");
        long nx = firstDim(dsX);
        long ny = firstDim(dsY);
        long nt = firstDim(dsT);
        long np = firstDim(dsP);
        if (nx != ny || nx != nt || nx != np) {
            throw new IOException(String.format(
                    "DSEC dataset length mismatch x=%d y=%d t=%d p=%d", nx, ny, nt, np));
        }
        eventCount = nx;
        if (eventCount == 0) {
            throw new IOException("DSEC file has zero events: " + file);
        }
    }

    private Dataset requireDataset(String path) throws IOException {
        Node n = hdf.getByPath(path);
        if (!(n instanceof Dataset)) {
            throw new IOException("Missing DSEC dataset " + path + " in " + file.getName());
        }
        return (Dataset) n;
    }

    private static long firstDim(Dataset ds) {
        long[] dims = ds.getDimensionsAsLong();
        return dims.length == 0 ? 1 : dims[0];
    }

    private void loadIndexAndBounds(ProgressMonitor progressMonitor) throws IOException {
        if (progressMonitor != null) {
            progressMonitor.setNote("Reading DSEC index / bounds");
            progressMonitor.setProgress(10);
        }
        Node offNode = hdf.getByPath("/t_offset");
        if (offNode instanceof Dataset) {
            tOffset = toLongScalar(((Dataset) offNode).getData());
        } else {
            tOffset = 0;
        }
        Node msNode = hdf.getByPath("/ms_to_idx");
        if (msNode instanceof Dataset) {
            msToIdx = toLongArray(((Dataset) msNode).getDataFlat());
        } else {
            msToIdx = new long[0];
            log.warning("DSEC file missing /ms_to_idx; time seek will scan");
        }

        // First / last relative timestamps (int32 domain for player)
        int[] t0 = readIntSlice(dsT, 0, 1);
        int[] t1 = readIntSlice(dsT, eventCount - 1, 1);
        firstRawT = t0[0];
        firstTimestamp = 0;
        lastTimestamp = (int) ((t1[0] & 0xffffffffL) - (firstRawT & 0xffffffffL));
        // Absolute start for UI: treat t_offset+t0 as µs epoch if plausible
        long absUs = tOffset + (firstRawT & 0xffffffffL);
        if (absUs > 1_000_000_000_000L) { // > ~2001 in µs
            absoluteStartingTimeMs = absUs / 1000L;
        } else {
            absoluteStartingTimeMs = file.lastModified();
        }
        if (progressMonitor != null) {
            progressMonitor.setProgress(40);
        }
    }

    private void ensureWindow(long eventIndex) throws IOException {
        if (eventIndex < 0 || eventIndex >= eventCount) {
            return;
        }
        if (windowStart >= 0
                && eventIndex >= windowStart
                && eventIndex < windowStart + windowLen) {
            return;
        }
        long start = Math.max(0, eventIndex - (WINDOW_EVENTS / 8));
        int len = (int) Math.min(WINDOW_EVENTS, eventCount - start);
        loadWindow(start, len);
    }

    private void loadWindow(long start, int len) throws IOException {
        if (len <= 0) {
            windowStart = start;
            windowLen = 0;
            return;
        }
        int[] x = readIntSlice(dsX, start, len);
        int[] y = readIntSlice(dsY, start, len);
        int[] t = readIntSlice(dsT, start, len);
        int[] p = readIntSlice(dsP, start, len);
        if (windowAddresses.length < len) {
            windowAddresses = new int[len];
            windowTimestamps = new int[len];
            windowTRel = new int[len];
        }
        final int sizeY = sensorHeight > 1 ? sensorHeight
                : (chip.getSizeY() > 0 ? chip.getSizeY() : 1);
        for (int i = 0; i < len; i++) {
            int xi = x[i] & 0xffff;
            int yi = y[i] & 0xffff;
            // DSEC row 0 is top (image coords); jAER y=0 is bottom
            yi = (sizeY - 1) - yi;
            int pol = p[i] != 0 ? 1 : 0;
            windowAddresses[i] = packCookedAddress(xi, yi, pol);
            int rel = (int) ((t[i] & 0xffffffffL) - (firstRawT & 0xffffffffL));
            windowTRel[i] = rel;
            windowTimestamps[i] = rel;
        }
        windowStart = start;
        windowLen = len;
    }

    /**
     * Pack DSEC cooked (jAER-display) x/y/p into a raw address the chip extractor
     * will decode. Davis extractors ignore {@code typeshift} and read polarity at
     * bit 11, and they unflip X ({@code e.x = sizeX-1-addrX}), same as AEDAT-4.
     */
    private int packCookedAddress(int x, int y, int pol01) {
        if (chip instanceof DavisChip) {
            int sx1 = chip.getSizeX() - 1;
            int px = sx1 - x;
            return DavisChip.ADDRESS_TYPE_DVS
                    | ((px & 0x3ff) << DavisChip.XSHIFT)
                    | ((y & 0x1ff) << DavisChip.YSHIFT)
                    | ((pol01 & 1) << DavisChip.POLSHIFT);
        }
        return typedExtractor.getAddressFromCell(x, y, pol01);
    }

    private int[] readIntSlice(Dataset ds, long offset, int len) throws IOException {
        try {
            Object data = ds.getData(new long[]{offset}, new int[]{len});
            return toIntArray(data);
        } catch (RuntimeException e) {
            throw new IOException("Failed reading " + ds.getName() + " @ " + offset + " len=" + len + ": " + e.getMessage(), e);
        }
    }

    private static int[] toIntArray(Object data) {
        if (data == null) {
            return new int[0];
        }
        if (data instanceof int[]) {
            return (int[]) data;
        }
        if (data instanceof short[]) {
            short[] s = (short[]) data;
            int[] out = new int[s.length];
            for (int i = 0; i < s.length; i++) {
                out[i] = s[i] & 0xffff;
            }
            return out;
        }
        if (data instanceof byte[]) {
            byte[] b = (byte[]) data;
            int[] out = new int[b.length];
            for (int i = 0; i < b.length; i++) {
                out[i] = b[i] & 0xff;
            }
            return out;
        }
        if (data instanceof long[]) {
            long[] l = (long[]) data;
            int[] out = new int[l.length];
            for (int i = 0; i < l.length; i++) {
                out[i] = (int) l[i];
            }
            return out;
        }
        // nested array from getData (not flat)
        if (data.getClass().isArray()) {
            int n = Array.getLength(data);
            if (n > 0 && Array.get(data, 0) != null && Array.get(data, 0).getClass().isArray()) {
                // flatten one level
                Object flat = flatten(data);
                return toIntArray(flat);
            }
            int[] out = new int[n];
            for (int i = 0; i < n; i++) {
                Object el = Array.get(data, i);
                if (el instanceof Number) {
                    out[i] = ((Number) el).intValue();
                } else {
                    out[i] = 0;
                }
            }
            return out;
        }
        throw new IllegalArgumentException("Unsupported dataset Java type: " + data.getClass());
    }

    private static Object flatten(Object data) {
        // jHDF getData returns 1D for 1D datasets; keep simple
        return data;
    }

    private static long[] toLongArray(Object data) {
        if (data == null) {
            return new long[0];
        }
        if (data instanceof long[]) {
            return (long[]) data;
        }
        if (data instanceof int[]) {
            int[] a = (int[]) data;
            long[] out = new long[a.length];
            for (int i = 0; i < a.length; i++) {
                out[i] = a[i] & 0xffffffffL;
            }
            return out;
        }
        if (data.getClass().isArray()) {
            int n = Array.getLength(data);
            long[] out = new long[n];
            for (int i = 0; i < n; i++) {
                Object el = Array.get(data, i);
                out[i] = el instanceof Number ? ((Number) el).longValue() : 0L;
            }
            return out;
        }
        throw new IllegalArgumentException("Unsupported long dataset type: " + data.getClass());
    }

    private static long toLongScalar(Object data) {
        if (data instanceof Number) {
            return ((Number) data).longValue();
        }
        if (data != null && data.getClass().isArray() && Array.getLength(data) > 0) {
            Object el = Array.get(data, 0);
            if (el instanceof Number) {
                return ((Number) el).longValue();
            }
        }
        return 0L;
    }

    private void seekToEvent(long eventIndex) throws IOException {
        long target = Math.max(0, Math.min(eventIndex, eventCount));
        ensureWindow(target == eventCount && target > 0 ? target - 1 : target);
        position = target;
        if (target <= 0) {
            mostRecentTimestamp = firstTimestamp;
            currentStartTimestamp = firstTimestamp;
        } else {
            int idx = (int) (target - 1 - windowStart);
            mostRecentTimestamp = windowTimestamps[Math.max(0, Math.min(idx, windowLen - 1))];
            currentStartTimestamp = mostRecentTimestamp;
        }
    }

    /** Map a relative timestamp (µs from first event) to an event index via ms_to_idx. */
    private long indexForRelativeTimeUs(int relUs) {
        if (msToIdx.length == 0) {
            return position;
        }
        int ms = Math.max(0, relUs / 1000);
        if (ms >= msToIdx.length) {
            return eventCount;
        }
        long idx = msToIdx[ms];
        if (idx < 0) {
            idx = 0;
        }
        if (idx > eventCount) {
            idx = eventCount;
        }
        return idx;
    }

    private void ensureReadableOrThrow(boolean forwards) throws EOFException {
        if (eventCount == 0) {
            throw new EOFException("DSEC HDF5 has no events");
        }
        if (forwards) {
            if (position < effectiveMarkOut()) {
                return;
            }
            if (repeat) {
                try {
                    rewind();
                } catch (IOException e) {
                    throw new EOFException(e.toString());
                }
                if (position >= effectiveMarkOut()) {
                    throw new EOFException();
                }
                return;
            }
            throw new EOFException();
        }
        if (position > effectiveMarkIn()) {
            return;
        }
        throw new EOFException("reached start of file");
    }

    private long effectiveMarkOut() {
        return Math.min(markOut, eventCount);
    }

    private long effectiveMarkIn() {
        return Math.max(0, markIn);
    }

    private void firePosition() {
        support.firePropertyChange(AEInputStream.EVENT_POSITION, null, position);
    }

    /** Timestamp of event at {@code index}, or {@link #firstTimestamp} if index &lt; 0. */
    private int timestampAt(long index) throws IOException {
        if (index < 0) {
            return firstTimestamp;
        }
        if (index >= eventCount) {
            index = eventCount - 1;
        }
        ensureWindow(index);
        return windowTimestamps[(int) (index - windowStart)];
    }

    /**
     * First index in [{@code limitIn}, {@code end}) with timestamp &gt;= {@code target}.
     * Uses {@code ms_to_idx} then refines by scanning.
     */
    private long findStartIndexByTime(long end, int target, long limitIn) throws IOException {
        long hint = indexForRelativeTimeUs(Math.max(0, target));
        long i = Math.max(limitIn, Math.min(hint, end));
        // Walk back while previous event is still >= target
        while (i > limitIn) {
            if (end - i >= MAX_EVENTS_PER_READ) {
                i = Math.max(limitIn, end - MAX_EVENTS_PER_READ);
                break;
            }
            ensureWindow(i - 1);
            if (windowTimestamps[(int) (i - 1 - windowStart)] < target) {
                break;
            }
            i--;
        }
        // Walk forward while current event is still < target
        while (i < end) {
            ensureWindow(i);
            if (windowTimestamps[(int) (i - windowStart)] >= target) {
                break;
            }
            i++;
        }
        return i;
    }

    /** Copy chronological events in [{@code start}, {@code end}) and set {@link #position} to {@code start}. */
    private AEPacketRaw copyRange(long start, long end) throws IOException {
        if (start >= end) {
            throw new EOFException();
        }
        long nLong = end - start;
        if (nLong > MAX_EVENTS_PER_READ) {
            start = end - MAX_EVENTS_PER_READ;
            nLong = MAX_EVENTS_PER_READ;
        }
        int n = (int) nLong;
        packet.setNumEvents(0);
        packet.ensureCapacity(n);
        int collected = 0;
        long pos = start;
        while (collected < n && pos < end) {
            ensureWindow(pos);
            int local = (int) (pos - windowStart);
            int avail = Math.min(windowLen - local, n - collected);
            avail = (int) Math.min(avail, end - pos);
            if (avail <= 0) {
                break;
            }
            System.arraycopy(windowAddresses, local, packet.getAddresses(), collected, avail);
            System.arraycopy(windowTimestamps, local, packet.getTimestamps(), collected, avail);
            collected += avail;
            pos += avail;
        }
        if (collected == 0) {
            throw new EOFException();
        }
        packet.setNumEvents(collected);
        position = start;
        mostRecentTimestamp = packet.getTimestamps()[0];
        currentStartTimestamp = mostRecentTimestamp;
        firePosition();
        return packet;
    }

    private AEPacketRaw readEventsForwards(int n) throws IOException {
        if (n <= 0) {
            throw new EOFException();
        }
        currentStartTimestamp = mostRecentTimestamp;
        packet.setNumEvents(0);
        packet.ensureCapacity(n);
        int collected = 0;
        while (collected < n && position < effectiveMarkOut()) {
            ensureWindow(position);
            int local = (int) (position - windowStart);
            int avail = Math.min(windowLen - local, n - collected);
            avail = (int) Math.min(avail, effectiveMarkOut() - position);
            if (avail <= 0) {
                break;
            }
            System.arraycopy(windowAddresses, local, packet.getAddresses(), collected, avail);
            System.arraycopy(windowTimestamps, local, packet.getTimestamps(), collected, avail);
            collected += avail;
            position += avail;
            mostRecentTimestamp = packet.getTimestamps()[collected - 1];
        }
        if (collected == 0) {
            throw new EOFException();
        }
        packet.setNumEvents(collected);
        firePosition();
        return packet;
    }

    @Override
    public synchronized AEPacketRaw readPacketByNumber(int n) throws IOException {
        if (n == 0) {
            n = 1;
        }
        boolean forwards = n > 0;
        ensureReadableOrThrow(forwards);
        if (forwards) {
            int capped = Math.min(n, MAX_EVENTS_PER_READ);
            long end = Math.min(effectiveMarkOut(), position + capped);
            return readEventsForwards((int) (end - position));
        }
        long end = position;
        long start = Math.max(effectiveMarkIn(), end - Math.min(-n, MAX_EVENTS_PER_READ));
        return copyRange(start, end);
    }

    @Override
    public synchronized AEPacketRaw readPacketByTime(int dt) throws IOException {
        if (dt == 0) {
            dt = 1;
        }
        boolean forwards = dt > 0;
        ensureReadableOrThrow(forwards);
        if (forwards) {
            int tStart = mostRecentTimestamp;
            currentStartTimestamp = tStart;
            int target = tStart + dt;
            long hint = indexForRelativeTimeUs(target);
            if (hint > position + MAX_EVENTS_PER_READ) {
                hint = position + MAX_EVENTS_PER_READ;
            }
            int n = (int) Math.max(1, Math.min(MAX_EVENTS_PER_READ, hint - position + 4096));
            AEPacketRaw pkt = readEventsForwards(n);
            int[] ts = pkt.getTimestamps();
            int num = pkt.getNumEvents();
            int cut = num;
            for (int i = 0; i < num; i++) {
                if (ts[i] >= target && i > 0) {
                    cut = i + 1;
                    break;
                }
            }
            if (cut < num) {
                position -= (num - cut);
                pkt.setNumEvents(cut);
                mostRecentTimestamp = pkt.getTimestamps()[cut - 1];
                firePosition();
            }
            return pkt;
        }
        // Backwards jog: exclusive end is current position; find start with ts >= target
        long end = position;
        long limitIn = effectiveMarkIn();
        if (end <= limitIn) {
            throw new EOFException("reached start of file");
        }
        int tEnd = timestampAt(end - 1);
        int target = tEnd + dt; // dt < 0
        long start = findStartIndexByTime(end, target, limitIn);
        return copyRange(start, end);
    }

    @Override
    public boolean isNonMonotonicTimeExceptionsChecked() {
        return nonMonotonicTimeExceptionsChecked;
    }

    @Override
    public void setNonMonotonicTimeExceptionsChecked(boolean yes) {
        nonMonotonicTimeExceptionsChecked = yes;
    }

    @Override
    public long getAbsoluteStartingTimeMs() {
        return absoluteStartingTimeMs;
    }

    @Override
    public ZoneId getZoneId() {
        return ZoneId.systemDefault();
    }

    @Override
    public int getDurationUs() {
        return getLastTimestamp() - getFirstTimestamp();
    }

    @Override
    public int getFirstTimestamp() {
        return firstTimestamp;
    }

    @Override
    public PropertyChangeSupport getSupport() {
        return support;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public int getLastTimestamp() {
        return lastTimestamp;
    }

    @Override
    public int getMostRecentTimestamp() {
        return mostRecentTimestamp;
    }

    @Override
    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public int getTimestampResetBitmask() {
        return timestampResetBitmask;
    }

    @Override
    public void setTimestampResetBitmask(int timestampResetBitmask) {
        this.timestampResetBitmask = timestampResetBitmask;
    }

    @Override
    public synchronized void close() throws IOException {
        closeQuietly();
    }

    private void closeQuietly() {
        if (hdf != null) {
            try {
                hdf.close();
            } catch (Exception e) {
                log.fine("DSEC HDF5 close: " + e);
            }
            hdf = null;
        }
    }

    @Override
    public int getCurrentStartTimestamp() {
        return currentStartTimestamp;
    }

    @Override
    public void setCurrentStartTimestamp(int currentStartTimestamp) {
        this.currentStartTimestamp = currentStartTimestamp;
    }

    @Override
    public boolean toggleMarker() {
        Long here = position;
        boolean added = markers.add(here);
        if (!added) {
            markers.remove(here);
        }
        support.firePropertyChange(AEInputStream.EVENT_MARK_TOGGLED, added ? null : here, added ? here : null);
        return added;
    }

    @Override
    public synchronized boolean jumpToNextMarker() {
        lastJumpTimeMs = System.currentTimeMillis();
        Long next = markers.higher(position);
        if (next == null) {
            return false;
        }
        position(next);
        return true;
    }

    @Override
    public synchronized boolean jumpToPrevMarker() {
        Long prev = markers.lower(position);
        if (prev == null) {
            return false;
        }
        if (System.currentTimeMillis() - lastJumpTimeMs <= 2000) {
            Long earlier = markers.lower(prev);
            if (earlier != null) {
                prev = earlier;
            }
        }
        lastJumpTimeMs = System.currentTimeMillis();
        position(prev);
        return true;
    }

    @Override
    public float getFractionalPosition() {
        return eventCount == 0 ? 0 : (float) position / eventCount;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public synchronized void position(long n) {
        long old = position;
        long target = Math.max(markIn, Math.min(n, effectiveMarkOut()));
        try {
            seekToEvent(target);
        } catch (IOException e) {
            log.warning("DSEC HDF5 seek failed: " + e);
        }
        currentStartTimestamp = mostRecentTimestamp;
        support.firePropertyChange(AEInputStream.EVENT_REPOSITIONED, old, position);
        if (old != position) {
            firePosition();
        }
    }

    @Override
    public synchronized void rewind() throws IOException {
        long old = position;
        seekToEvent(markIn);
        support.firePropertyChange(AEInputStream.EVENT_REWOUND, old, position);
    }

    @Override
    public void setFractionalPosition(float frac) {
        position((long) (Math.max(0, Math.min(1, frac)) * eventCount));
    }

    @Override
    public long size() {
        return eventCount;
    }

    @Override
    public void clearMarks() {
        long[] oldMarks = new long[]{markIn, markOut};
        markIn = 0;
        markOut = eventCount;
        markInSet = false;
        markOutSet = false;
        markers.clear();
        support.firePropertyChange(AEInputStream.EVENT_MARKS_CLEARED, oldMarks, new long[]{markIn, markOut});
    }

    @Override
    public long setMarkIn() {
        if (position <= markOut) {
            long old = markIn;
            markIn = position;
            markInSet = true;
            support.firePropertyChange(AEInputStream.EVENT_MARK_IN_SET, old, markIn);
        }
        return markIn;
    }

    @Override
    public long setMarkOut() {
        if (position > markIn) {
            long old = markOut;
            markOut = position;
            markOutSet = true;
            support.firePropertyChange(AEInputStream.EVENT_MARK_OUT_SET, old, markOut);
        }
        return markOut;
    }

    @Override
    public long getMarkInPosition() {
        return markIn;
    }

    @Override
    public long getMarkOutPosition() {
        return markOut;
    }

    @Override
    public boolean isMarkInSet() {
        return markInSet;
    }

    @Override
    public boolean isMarkOutSet() {
        return markOutSet;
    }

    @Override
    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    @Override
    public boolean isRepeat() {
        return repeat;
    }

    /** For tests / tools. */
    public Path getPath() {
        return file != null ? file.toPath() : null;
    }

    public long getTOffset() {
        return tOffset;
    }
}
