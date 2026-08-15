package prophesee.eventio;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.swing.ProgressMonitor;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Plays Prophesee / Metavision decoded {@code .dat} recordings (CD / Event2d).
 * <p>
 * These share the {@code .dat} extension with pre-2010 jAER / DVS128 files, but
 * the formats are distinct: Metavision DAT starts with {@code % } ASCII header
 * lines, then a 2-byte event type/size, then little-endian 8-byte events
 * ({@code t} + packed {@code x}/{@code y}/{@code p}). Legacy jAER {@code .dat}
 * uses {@code #} comments or raw AEDAT-1 addresses.
 *
 * @see <a href="https://docs.prophesee.ai/stable/data/file_formats/dat.html">DAT File Format</a>
 */
public class MetavisionDatFileInputStream implements AEFileInputStreamInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    public static final String DATA_FILE_EXTENSION = "dat";

    /** Event2d (generic 2D). */
    public static final int EV_TYPE_EVENT2D = 0x00;
    /** Contrast-detection CD event. */
    public static final int EV_TYPE_CD = 0x0C;
    /** External trigger (not played). */
    public static final int EV_TYPE_EXT_TRIGGER = 0x0E;

    private static final int X_MASK = (1 << 14) - 1;
    private static final int Y_MASK = (1 << 14) - 1;
    private static final int DEFAULT_EVENT_SIZE = 8;
    private static final int MAX_EVENTS_PER_READ = 1_000_000;
    private static final DateTimeFormatter HEADER_DATE = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE)
            .toFormatter();

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private final AEPacketRaw packet = new AEPacketRaw();

    private File file;
    private AEChip chip;
    private EventExtractor2D<?> extractor;
    private RandomAccessFile raf;
    private long dataStart;
    private long fileLength;
    private Map<String, String> header = new LinkedHashMap<>();
    private int width;
    private int height;
    private int eventType;
    private int eventSize = DEFAULT_EVENT_SIZE;

    private long eventCount;
    private long firstRawT;
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
    private byte[] readBuf = new byte[64 * 1024];

    public MetavisionDatFileInputStream(File file, AEChip chip, ProgressMonitor progressMonitor)
            throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("not a file: " + file);
        }
        if (chip == null) {
            throw new NullPointerException("AEChip is null");
        }
        this.file = file;
        this.chip = chip;
        this.extractor = chip.getEventExtractor();
        this.raf = new RandomAccessFile(file, "r");
        this.fileLength = raf.length();
        if (progressMonitor != null) {
            progressMonitor.setNote("Opening Metavision DAT");
            progressMonitor.setProgress(1);
        }
        try {
            HeaderInfo hi = parseHeader(raf);
            if (hi == null || hi.headerLineCount == 0) {
                throw new IOException(file.getName() + " is not a Metavision DAT file (missing % header)");
            }
            this.header = hi.fields;
            this.dataStart = hi.dataStart;
            this.width = hi.width;
            this.height = hi.height;
            this.eventType = hi.eventType;
            this.eventSize = hi.eventSize > 0 ? hi.eventSize : DEFAULT_EVENT_SIZE;
            if (!hi.cdEvents) {
                throw new IOException(file.getName()
                        + " Metavision DAT event type " + eventType
                        + " is not CD/Event2d (only types 0 and 12 are supported)");
            }
            if (eventSize != DEFAULT_EVENT_SIZE) {
                throw new IOException(file.getName()
                        + " Metavision DAT event size " + eventSize + " is not 8");
            }
            long payload = fileLength - dataStart;
            if (payload < 0 || payload % eventSize != 0) {
                throw new IOException(file.getName()
                        + " Metavision DAT payload length " + payload
                        + " is not a multiple of event size " + eventSize);
            }
            eventCount = payload / eventSize;
            if (eventCount > 0) {
                firstRawT = rawTimestampAt(0);
                long lastRaw = rawTimestampAt(eventCount - 1);
                firstTimestamp = 0;
                lastTimestamp = (int) (lastRaw - firstRawT);
            } else {
                firstRawT = 0;
                firstTimestamp = 0;
                lastTimestamp = 0;
            }
            absoluteStartingTimeMs = parseAbsoluteTimeMs(first(header, "Date", "date"));
            if (absoluteStartingTimeMs == 0) {
                absoluteStartingTimeMs = file.lastModified();
            }
            clearMarks();
            seekToEvent(0);
            EngineeringFormat eng = new EngineeringFormat();
            eng.setPrecision(3);
            log.info(String.format(
                    "Opened Metavision DAT %s: type=%d (%s), %,d events, %dx%d, duration=%ss, data@%d",
                    file.getName(),
                    eventType,
                    eventTypeName(eventType),
                    eventCount,
                    width,
                    height,
                    eng.format(getDurationUs() * 1e-6).trim(),
                    dataStart));
            support.firePropertyChange(AEInputStream.EVENT_INIT, null, this);
        } catch (IOException e) {
            try {
                close();
            } catch (IOException ignore) {
                // already failing open
            }
            throw e;
        }
    }

    /**
     * True if {@code file} looks like a Prophesee / Metavision DAT recording
     * ({@code % } ASCII header), not a legacy jAER / DVS128 {@code .dat}.
     */
    public static boolean isMetavisionDatFile(File file) {
        HeaderInfo hi = peekHeader(file);
        return hi != null && hi.headerLineCount > 0;
    }

    public static HeaderInfo peekHeader(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith("." + DATA_FILE_EXTENSION)) {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            HeaderInfo hi = parseHeader(raf);
            if (hi == null || hi.headerLineCount == 0) {
                return null;
            }
            return hi;
        } catch (IOException e) {
            log.fine("Could not peek Metavision DAT header from " + file.getName() + ": " + e);
            return null;
        }
    }

    public Map<String, String> getHeader() {
        return header;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getEventType() {
        return eventType;
    }

    private void seekToEvent(long eventIndex) throws IOException {
        long target = Math.max(0, Math.min(eventIndex, eventCount));
        position = target;
        if (target <= 0) {
            mostRecentTimestamp = firstTimestamp;
            currentStartTimestamp = firstTimestamp;
        } else {
            mostRecentTimestamp = relTimestamp(rawTimestampAt(target - 1));
            currentStartTimestamp = mostRecentTimestamp;
        }
    }

    private void ensureReadableOrThrow(boolean forwards) throws EOFException {
        if (eventCount == 0) {
            throw new EOFException("Metavision DAT has no events");
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
            long from = position;
            long to = effectiveMarkOut();
            long end = indexForTimestamp(target, from, to);
            if (end <= from) {
                end = Math.min(to, from + 1);
            }
            if (end - from > MAX_EVENTS_PER_READ) {
                end = from + MAX_EVENTS_PER_READ;
            }
            return readEventsForwards((int) (end - from));
        }
        // Backwards jog: exclusive end is current position; find start with ts >= target
        long end = position;
        long limitIn = effectiveMarkIn();
        if (end <= limitIn) {
            throw new EOFException("reached start of file");
        }
        int tEnd = timestampAt(end - 1);
        int target = tEnd + dt; // dt < 0
        long start = indexForTimestamp(target, limitIn, end);
        if (end - start > MAX_EVENTS_PER_READ) {
            start = end - MAX_EVENTS_PER_READ;
        }
        return copyRange(start, end);
    }

    private int timestampAt(long index) throws IOException {
        if (index < 0) {
            return firstTimestamp;
        }
        if (index >= eventCount) {
            index = eventCount - 1;
        }
        return relTimestamp(rawTimestampAt(index));
    }

    /** Chronological events in [{@code start}, {@code end}); leave {@link #position} at {@code start}. */
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
        decodeRange(start, n, packet.getAddresses(), packet.getTimestamps(), 0);
        packet.setNumEvents(n);
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
        decodeRange(position, n, packet.getAddresses(), packet.getTimestamps(), 0);
        position += n;
        mostRecentTimestamp = packet.getTimestamps()[n - 1];
        packet.setNumEvents(n);
        firePosition();
        return packet;
    }

    private void decodeRange(long startIndex, int n, int[] addresses, int[] timestamps, int destOff)
            throws IOException {
        long byteOff = dataStart + startIndex * eventSize;
        raf.seek(byteOff);
        int need = n * eventSize;
        if (readBuf.length < need) {
            readBuf = new byte[need];
        }
        raf.readFully(readBuf, 0, need);
        final int sizeY = flipHeight();
        for (int i = 0; i < n; i++) {
            int o = i * eventSize;
            long rawT = u32(readBuf, o);
            int packed = (int) u32(readBuf, o + 4);
            int x = packed & X_MASK;
            int y = (packed >>> 14) & Y_MASK;
            int p = (packed >>> 28) & 1;
            int yJaer = sizeY > 0 ? (sizeY - 1) - y : y;
            addresses[destOff + i] = packAddress(x, yJaer, p);
            timestamps[destOff + i] = (int) (rawT - firstRawT);
        }
    }

    private int flipHeight() {
        if (chip != null && chip.getSizeY() > 0) {
            return chip.getSizeY();
        }
        return height > 0 ? height : 0;
    }

    private int packAddress(int x, int y, int type) {
        if (extractor != null) {
            return extractor.getAddressFromCell(x, y, type);
        }
        return x | (y << 11) | (type != 0 ? (1 << 22) : 0);
    }

    private long indexForTimestamp(int targetRel, long from, long to) throws IOException {
        long lo = from;
        long hi = to;
        while (lo < hi) {
            long mid = (lo + hi) >>> 1;
            if (relTimestamp(rawTimestampAt(mid)) >= targetRel) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    private long rawTimestampAt(long index) throws IOException {
        if (index < 0 || index >= eventCount) {
            throw new EOFException("DAT event index " + index + " out of range " + eventCount);
        }
        raf.seek(dataStart + index * eventSize);
        return readUnsignedIntLE(raf);
    }

    private int relTimestamp(long rawTs) {
        return (int) (rawTs - firstRawT);
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
        if (raf != null) {
            raf.close();
            raf = null;
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
            log.warning("Metavision DAT seek failed: " + e);
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

    public static final class HeaderInfo {
        public final Map<String, String> fields;
        public final long dataStart;
        public final int width;
        public final int height;
        public final int eventType;
        public final int eventSize;
        public final int headerLineCount;
        public final boolean cdEvents;

        HeaderInfo(Map<String, String> fields, long dataStart, int width, int height,
                int eventType, int eventSize, int headerLineCount) {
            this.fields = fields;
            this.dataStart = dataStart;
            this.width = width;
            this.height = height;
            this.eventType = eventType;
            this.eventSize = eventSize;
            this.headerLineCount = headerLineCount;
            this.cdEvents = eventType == EV_TYPE_EVENT2D || eventType == EV_TYPE_CD;
        }
    }

    static HeaderInfo parseHeader(RandomAccessFile raf) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        long pos = 0;
        int lines = 0;
        raf.seek(0);
        final long maxHeader = Math.min(raf.length(), 64 * 1024L);
        while (pos < maxHeader) {
            raf.seek(pos);
            String line = readAsciiLine(raf);
            if (line == null) {
                break;
            }
            long next = raf.getFilePointer();
            if (!line.startsWith("% ")) {
                break;
            }
            String body = line.substring(2).trim();
            int sp = body.indexOf(' ');
            if (sp > 0) {
                fields.put(body.substring(0, sp).trim(), body.substring(sp + 1).trim());
            } else if (!body.isEmpty()) {
                fields.put(body, "");
            }
            lines++;
            pos = next;
        }
        if (lines == 0) {
            return new HeaderInfo(fields, 0, 0, 0, -1, 0, 0);
        }
        raf.seek(pos);
        int eventType = -1;
        int eventSize = 0;
        if (raf.getFilePointer() + 2 <= raf.length()) {
            eventType = raf.readUnsignedByte();
            eventSize = raf.readUnsignedByte();
            pos = raf.getFilePointer();
        }
        int width = parseIntField(fields, "Width", "width");
        int height = parseIntField(fields, "Height", "height");
        return new HeaderInfo(fields, pos, width, height, eventType, eventSize, lines);
    }

    private static int parseIntField(Map<String, String> fields, String... keys) {
        String v = first(fields, keys);
        if (v == null || v.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String first(Map<String, String> fields, String... keys) {
        for (String k : keys) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (e.getKey().equalsIgnoreCase(k)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static String readAsciiLine(RandomAccessFile raf) throws IOException {
        StringBuilder sb = new StringBuilder(128);
        while (true) {
            int b = raf.read();
            if (b < 0) {
                return sb.length() == 0 ? null : sb.toString();
            }
            if (b == '\n') {
                return sb.toString();
            }
            if (b != '\r') {
                if (b < 0x20 && b != '\t') {
                    raf.seek(raf.getFilePointer() - 1);
                    return sb.length() == 0 ? null : sb.toString();
                }
                sb.append((char) b);
            }
        }
    }

    private static long readUnsignedIntLE(RandomAccessFile raf) throws IOException {
        int b0 = raf.read();
        int b1 = raf.read();
        int b2 = raf.read();
        int b3 = raf.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            throw new EOFException();
        }
        return (b0 | (b1 << 8) | (b2 << 16) | (b3 << 24)) & 0xffffffffL;
    }

    private static long u32(byte[] buf, int off) {
        return ((buf[off] & 0xff)
                | ((buf[off + 1] & 0xff) << 8)
                | ((buf[off + 2] & 0xff) << 16)
                | ((buf[off + 3] & 0xff) << 24)) & 0xffffffffL;
    }

    private static String eventTypeName(int type) {
        switch (type) {
            case EV_TYPE_EVENT2D:
                return "Event2d";
            case EV_TYPE_CD:
                return "EventCD";
            case EV_TYPE_EXT_TRIGGER:
                return "EventExtTrigger";
            default:
                return "unknown";
        }
    }

    private static long parseAbsoluteTimeMs(String date) {
        if (date == null || date.isEmpty()) {
            return 0;
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(date.trim(), HEADER_DATE);
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }
}
