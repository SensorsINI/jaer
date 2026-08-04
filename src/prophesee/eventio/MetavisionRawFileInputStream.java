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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.swing.ProgressMonitor;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.util.EngineeringFormat;
import prophesee.usb.evt3.Evt3Parser;

/**
 * Plays Prophesee / Metavision native {@code .raw} EVT3 recordings.
 * <p>
 * ASCII {@code % key value} header, then little-endian EVT3 words decoded with
 * {@link Evt3Parser} (same path as live EVK4 USB).
 *
 * @see <a href="https://docs.prophesee.ai/stable/data/file_formats/raw.html">RAW File Format</a>
 */
public class MetavisionRawFileInputStream implements AEFileInputStreamInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    public static final String DATA_FILE_EXTENSION = "raw";

    private static final int READ_CHUNK_BYTES = 64 * 1024;
    /** Leave enough words so Vect12 triples are not split across chunks. */
    private static final int HOLDBACK_BYTES = 6;
    private static final int INDEX_STRIDE_EVENTS = 100_000;
    private static final int MAX_EVENTS_PER_READ = 1_000_000;
    /** Must cover worst-case CD expansion for one {@link #READ_CHUNK_BYTES} chunk. */
    private static final int DISCARD_CAPACITY = 1 << 18;
    private static final DateTimeFormatter HEADER_DATE
            = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private final Evt3Parser parser = new Evt3Parser();
    private final AEPacketRaw packet = new AEPacketRaw();
    private final int[] discardAddresses = new int[DISCARD_CAPACITY];
    private final int[] discardTimestamps = new int[DISCARD_CAPACITY];
    private final byte[] readBuf = new byte[READ_CHUNK_BYTES];
    private final byte[] carry = new byte[HOLDBACK_BYTES + 2];

    private File file;
    private AEChip chip;
    private RandomAccessFile raf;
    private long dataStart;
    private long fileLength;
    private Map<String, String> header = new LinkedHashMap<>();
    private int width = Evt3Parser.WIDTH;
    private int height = Evt3Parser.HEIGHT;
    private boolean evt3 = true;

    private long eventCount;
    private int firstTimestamp;
    private int lastTimestamp;
    private long absoluteStartingTimeMs;
    private final List<Checkpoint> checkpoints = new ArrayList<>();

    private long position;
    private long fileBytePos;
    private int carryLen;
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

    private static final class Checkpoint {
        final long eventIndex;
        final long fileByteOffset;
        final Evt3Parser.State parserState;

        Checkpoint(long eventIndex, long fileByteOffset, Evt3Parser.State parserState) {
            this.eventIndex = eventIndex;
            this.fileByteOffset = fileByteOffset;
            this.parserState = parserState;
        }
    }

    public MetavisionRawFileInputStream(File file, AEChip chip, ProgressMonitor progressMonitor)
            throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("not a file: " + file);
        }
        if (chip == null) {
            throw new NullPointerException("AEChip is null");
        }
        this.file = file;
        this.chip = chip;
        this.raf = new RandomAccessFile(file, "r");
        this.fileLength = raf.length();
        try {
            HeaderInfo hi = parseHeader(raf);
            this.header = hi.fields;
            this.dataStart = hi.dataStart;
            this.width = hi.width;
            this.height = hi.height;
            this.evt3 = hi.evt3;
            if (!evt3) {
                throw new IOException(file.getName()
                        + " is not EVT3 (only Metavision RAW EVT3 is supported yet)");
            }
            absoluteStartingTimeMs = parseAbsoluteTimeMs(header.get("date"));
            if (absoluteStartingTimeMs == 0) {
                absoluteStartingTimeMs = parseAbsoluteTimeMs(header.get("Date"));
            }
            indexFile(progressMonitor);
            clearMarks();
            seekToEvent(0);
            EngineeringFormat eng = new EngineeringFormat();
            eng.setPrecision(3);
            log.info(String.format(
                    "Opened Metavision RAW EVT3 %s: %,d events, %dx%d, duration=%ss, data@%d",
                    file.getName(),
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

    /** True if {@code file} looks like a Metavision EVT3 {@code .raw} recording. */
    public static boolean isMetavisionEvt3RawFile(File file) {
        HeaderInfo hi = peekHeader(file);
        return hi != null && hi.evt3;
    }

    public static HeaderInfo peekHeader(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".raw")) {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return parseHeader(raf);
        } catch (IOException e) {
            log.fine("Could not peek Metavision RAW header from " + file.getName() + ": " + e);
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

    private void indexFile(ProgressMonitor progressMonitor) throws IOException {
        if (progressMonitor != null) {
            progressMonitor.setNote("Indexing Metavision RAW " + file.getName());
            progressMonitor.setMaximum(100);
            progressMonitor.setProgress(0);
        }
        parser.reset();
        checkpoints.clear();
        checkpoints.add(new Checkpoint(0, dataStart, parser.snapshot()));
        eventCount = 0;
        firstTimestamp = 0;
        lastTimestamp = 0;
        boolean haveFirst = false;
        long bytesRead = 0;
        final long dataBytes = Math.max(1L, fileLength - dataStart);
        raf.seek(dataStart);
        fileBytePos = dataStart;
        carryLen = 0;

        final int[] addrs = new int[DISCARD_CAPACITY];
        final int[] ts = new int[DISCARD_CAPACITY];
        long nextCheckpoint = INDEX_STRIDE_EVENTS;
        long lastProgressMs = System.currentTimeMillis();

        while (true) {
            throwIfCanceled(progressMonitor);
            int n = fillAndParse(addrs, ts, 0, DISCARD_CAPACITY);
            if (n == 0 && fileBytePos >= fileLength && carryLen < 2) {
                break;
            }
            if (n == 0) {
                // no CD events in this chunk; continue reading
                if (fileBytePos >= fileLength && carryLen < 2) {
                    break;
                }
                continue;
            }
            for (int i = 0; i < n; i++) {
                if (!haveFirst) {
                    firstTimestamp = ts[i];
                    haveFirst = true;
                }
                lastTimestamp = ts[i];
                eventCount++;
                if (eventCount >= nextCheckpoint) {
                    checkpoints.add(new Checkpoint(eventCount, fileBytePos - carryLen, parser.snapshot()));
                    nextCheckpoint += INDEX_STRIDE_EVENTS;
                }
            }
            long now = System.currentTimeMillis();
            if (progressMonitor != null && now - lastProgressMs > 100) {
                bytesRead = Math.min(dataBytes, Math.max(0, fileBytePos - dataStart));
                int pct = (int) Math.min(99, (bytesRead * 100L) / dataBytes);
                progressMonitor.setProgress(pct);
                progressMonitor.setNote(String.format("Indexing %,d events…", eventCount));
                lastProgressMs = now;
            }
        }
        if (progressMonitor != null) {
            progressMonitor.setProgress(99);
            progressMonitor.setNote("Finishing open " + file.getName());
        }
        if (!haveFirst) {
            throw new IOException(file.getName() + " contains no EVT3 CD events");
        }
    }

    private static void throwIfCanceled(ProgressMonitor progressMonitor) throws IOException {
        if (Thread.currentThread().isInterrupted()
                || (progressMonitor != null && progressMonitor.isCanceled())) {
            throw new IOException("Metavision RAW open canceled");
        }
    }

    /**
     * Read from {@link #raf} at {@link #fileBytePos}, prepend {@link #carry},
     * parse with holdback, update carry/fileBytePos.
     *
     * @return events written into addresses/timestamps, or -1 on overrun
     */
    private int fillAndParse(int[] addresses, int[] timestamps, int eventOffset, int maxEvents)
            throws IOException {
        if (maxEvents <= eventOffset) {
            return 0;
        }
        final int oldCarry = carryLen;
        System.arraycopy(carry, 0, readBuf, 0, oldCarry);
        int space = readBuf.length - oldCarry;
        int got = 0;
        if (space > 0 && fileBytePos < fileLength) {
            raf.seek(fileBytePos);
            got = raf.read(readBuf, oldCarry, space);
            if (got < 0) {
                got = 0;
            }
            fileBytePos += got;
        }
        int total = oldCarry + got;
        if (total < 2) {
            carryLen = total;
            if (total > 0) {
                System.arraycopy(readBuf, 0, carry, 0, total);
            }
            return 0;
        }
        final boolean eof = fileBytePos >= fileLength;
        int parseLen = total;
        if (!eof && total > HOLDBACK_BYTES) {
            parseLen = total - HOLDBACK_BYTES;
            parseLen -= (parseLen & 1); // even
        } else {
            parseLen -= (parseLen & 1);
        }
        if (parseLen < 2) {
            // Push file bytes back; keep only prior carry (plus nothing new).
            fileBytePos -= got;
            carryLen = oldCarry;
            if (oldCarry > 0) {
                System.arraycopy(readBuf, 0, carry, 0, oldCarry);
            }
            return 0;
        }
        int written = parser.parse(readBuf, parseLen, addresses, timestamps, eventOffset, maxEvents);
        int consumed = Math.max(0, Math.min(parseLen, parser.getLastBytesConsumed()));
        // Unparsed suffix: rewind file pointer (holdback / partial parse). Drop a final odd byte at EOF.
        int unparsed = total - consumed;
        if (consumed >= oldCarry) {
            if (unparsed >= 2 || (!eof && unparsed > 0)) {
                fileBytePos -= unparsed;
            }
            carryLen = 0;
        } else {
            fileBytePos -= got;
            carryLen = oldCarry - consumed;
            System.arraycopy(readBuf, consumed, carry, 0, carryLen);
        }
        if (written < 0) {
            return maxEvents - eventOffset;
        }
        return written;
    }

    private void seekToEvent(long eventIndex) throws IOException {
        long target = Math.max(0, Math.min(eventIndex, eventCount));
        Checkpoint cp = checkpoints.get(0);
        for (int i = checkpoints.size() - 1; i >= 0; i--) {
            if (checkpoints.get(i).eventIndex <= target) {
                cp = checkpoints.get(i);
                break;
            }
        }
        parser.restore(cp.parserState);
        fileBytePos = cp.fileByteOffset;
        carryLen = 0;
        long at = cp.eventIndex;
        while (at < target) {
            int want = (int) Math.min(DISCARD_CAPACITY, target - at);
            int n = fillAndParse(discardAddresses, discardTimestamps, 0, want);
            if (n < 0) {
                throw new IOException("EVT3 overrun while seeking");
            }
            if (n == 0) {
                if (fileBytePos >= fileLength && carryLen < 2) {
                    break;
                }
                continue;
            }
            at += n;
            mostRecentTimestamp = discardTimestamps[n - 1];
        }
        position = at;
        if (at > 0) {
            currentStartTimestamp = mostRecentTimestamp;
        } else {
            currentStartTimestamp = firstTimestamp;
            mostRecentTimestamp = firstTimestamp;
        }
    }

    private void ensureReadableOrThrow() throws EOFException {
        if (eventCount == 0) {
            throw new EOFException("Metavision RAW has no events");
        }
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

    private long effectiveMarkOut() {
        return Math.min(markOut, eventCount);
    }

    private void firePosition() {
        support.firePropertyChange(AEInputStream.EVENT_POSITION, null, position);
    }

    @Override
    public synchronized AEPacketRaw readPacketByNumber(int n) throws IOException {
        ensureReadableOrThrow();
        int capped = Math.min(Math.max(1, n), MAX_EVENTS_PER_READ);
        long end = Math.min(effectiveMarkOut(), position + capped);
        return readEvents((int) (end - position));
    }

    @Override
    public synchronized AEPacketRaw readPacketByTime(int dt) throws IOException {
        ensureReadableOrThrow();
        int tStart = mostRecentTimestamp;
        if (position == markIn || position == 0) {
            tStart = mostRecentTimestamp;
        }
        currentStartTimestamp = tStart;
        int target = tStart + Math.max(0, dt);
        packet.setNumEvents(0);
        packet.ensureCapacity(Math.min(65536, MAX_EVENTS_PER_READ));
        int collected = 0;
        while (position < effectiveMarkOut() && collected < MAX_EVENTS_PER_READ) {
            int room = Math.min(DISCARD_CAPACITY, MAX_EVENTS_PER_READ - collected);
            int n = fillAndParse(discardAddresses, discardTimestamps, 0, room);
            if (n < 0) {
                // flush what fits
                int fit = Math.min(room, packet.getAddresses().length - collected);
                if (fit <= 0) {
                    packet.ensureCapacity(collected + room);
                    fit = room;
                }
                // treat as full room of events from last successful parse path — shouldn't happen often
                throw new IOException("EVT3 parse overrun in readPacketByTime");
            }
            if (n == 0) {
                if (fileBytePos >= fileLength && carryLen < 2) {
                    break;
                }
                continue;
            }
            packet.ensureCapacity(collected + n);
            System.arraycopy(discardAddresses, 0, packet.getAddresses(), collected, n);
            System.arraycopy(discardTimestamps, 0, packet.getTimestamps(), collected, n);
            collected += n;
            position += n;
            mostRecentTimestamp = discardTimestamps[n - 1];
            if (mostRecentTimestamp >= target && collected >= 1) {
                break;
            }
        }
        if (collected == 0) {
            throw new EOFException();
        }
        packet.setNumEvents(collected);
        firePosition();
        return packet;
    }

    private AEPacketRaw readEvents(int n) throws IOException {
        if (n <= 0) {
            throw new EOFException();
        }
        currentStartTimestamp = mostRecentTimestamp;
        packet.setNumEvents(0);
        packet.ensureCapacity(n);
        int collected = 0;
        while (collected < n && position < effectiveMarkOut()) {
            int room = n - collected;
            int got = fillAndParse(packet.getAddresses(), packet.getTimestamps(), collected, collected + room);
            if (got < 0) {
                throw new IOException("EVT3 parse overrun in readPacketByNumber");
            }
            if (got == 0) {
                if (fileBytePos >= fileLength && carryLen < 2) {
                    break;
                }
                continue;
            }
            collected += got;
            position += got;
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
    public boolean jumpToNextMarker() {
        Long next = markers.higher(position);
        if (next == null) {
            return false;
        }
        position(next);
        return true;
    }

    @Override
    public boolean jumpToPrevMarker() {
        Long prev = markers.lower(position);
        if (prev == null) {
            return false;
        }
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
            log.warning("Metavision RAW seek failed: " + e);
        }
        support.firePropertyChange(AEInputStream.EVENT_REPOSITIONED, old, position);
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
        public final boolean evt3;

        HeaderInfo(Map<String, String> fields, long dataStart, int width, int height, boolean evt3) {
            this.fields = fields;
            this.dataStart = dataStart;
            this.width = width;
            this.height = height;
            this.evt3 = evt3;
        }
    }

    static HeaderInfo parseHeader(RandomAccessFile raf) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        long pos = 0;
        raf.seek(0);
        final long maxHeader = Math.min(raf.length(), 64 * 1024L);
        while (pos < maxHeader) {
            raf.seek(pos);
            String line = readAsciiLine(raf);
            if (line == null) {
                break;
            }
            long next = raf.getFilePointer();
            if (!line.startsWith("%")) {
                return buildHeaderInfo(fields, pos);
            }
            if (line.matches("(?i)%\\s*end\\s*")) {
                return buildHeaderInfo(fields, next);
            }
            // "% key value" or "%key value"
            String body = line.substring(1).trim();
            int sp = body.indexOf(' ');
            if (sp > 0) {
                fields.put(body.substring(0, sp).trim(), body.substring(sp + 1).trim());
            } else if (!body.isEmpty()) {
                fields.put(body, "");
            }
            pos = next;
        }
        return buildHeaderInfo(fields, pos);
    }

    private static HeaderInfo buildHeaderInfo(Map<String, String> fields, long dataStart) {
        int width = Evt3Parser.WIDTH;
        int height = Evt3Parser.HEIGHT;
        boolean evt3 = false;
        String format = first(fields, "format", "Format");
        if (format != null) {
            String f = format.toUpperCase(Locale.ROOT);
            if (f.contains("EVT3")) {
                evt3 = true;
            }
            Integer w = parseKeyedInt(format, "width");
            Integer h = parseKeyedInt(format, "height");
            if (w != null) {
                width = w;
            }
            if (h != null) {
                height = h;
            }
        }
        String evt = first(fields, "evt", "EVT");
        if (evt != null && evt.trim().startsWith("3")) {
            evt3 = true;
        }
        String geometry = first(fields, "geometry", "Geometry");
        if (geometry != null && geometry.contains("x")) {
            String[] wh = geometry.toLowerCase(Locale.ROOT).split("x");
            if (wh.length == 2) {
                try {
                    width = Integer.parseInt(wh[0].trim());
                    height = Integer.parseInt(wh[1].trim());
                } catch (NumberFormatException ignore) {
                    // keep defaults
                }
            }
        }
        String plugin = first(fields, "plugin_name", "plugin");
        if (!evt3 && plugin != null) {
            String p = plugin.toLowerCase(Locale.ROOT);
            if (p.contains("imx636") || p.contains("gen41") || p.contains("gen4")) {
                // HD Prophesee plugins of this era are EVT3 by default
                evt3 = true;
            }
        }
        // Older headers: "% evt 3.0" alone
        if (!evt3) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (e.getKey().equalsIgnoreCase("evt") && e.getValue().trim().startsWith("3")) {
                    evt3 = true;
                }
            }
        }
        return new HeaderInfo(fields, dataStart, width, height, evt3);
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

    private static Integer parseKeyedInt(String format, String key) {
        int i = format.toLowerCase(Locale.ROOT).indexOf(key.toLowerCase(Locale.ROOT) + "=");
        if (i < 0) {
            return null;
        }
        int start = i + key.length() + 1;
        int end = start;
        while (end < format.length() && Character.isDigit(format.charAt(end))) {
            end++;
        }
        if (end == start) {
            return null;
        }
        try {
            return Integer.parseInt(format.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
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
                    // binary — push back by seeking one byte back
                    raf.seek(raf.getFilePointer() - 1);
                    return sb.length() == 0 ? null : sb.toString();
                }
                sb.append((char) b);
            }
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
