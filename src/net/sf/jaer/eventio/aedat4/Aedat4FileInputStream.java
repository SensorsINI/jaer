package net.sf.jaer.eventio.aedat4;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Logger;
import javax.swing.ProgressMonitor;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventio.aedat4.dv.Event;
import net.sf.jaer.eventio.aedat4.dv.EventPacket;
import net.sf.jaer.eventio.aedat4.dv.Frame;
import net.sf.jaer.eventio.aedat4.dv.FrameFormat;
import net.sf.jaer.eventio.aedat4.dv.IMU;
import net.sf.jaer.eventio.aedat4.dv.IMUPacket;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;
import net.sf.jaer.util.EngineeringFormat;

/**
 * AEDAT-4 reader: indexes polarity for raw AE playback, and indexes FRME/IMUS
 * packet offsets for typed {@link FramePacket}/{@link ImuPacket} injection.
 * Polarity (+ stream indexes) can be cached under {@code java.io.tmpdir} like
 * {@link net.sf.jaer.eventio.ros.RosbagFileInputStream}.
 */
public class Aedat4FileInputStream implements AEFileInputStreamInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int INDEX_CACHE_VERSION = 7; // v7: chip class name (addresses are chip-packed)
    private static final String INDEX_CACHE_MAGIC = "JAER4IDX";
    /** Sanity cap for cached polarity events (guards corrupt headers / OOM). */
    private static final int INDEX_CACHE_MAX_EVENTS = 50_000_000;

    private final AEChip chip;
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private final TreeSet<Long> markers = new TreeSet<>();
    private File file;
    private RandomAccessFile randomAccessFile;
    private FileChannel channel;
    private int compression = CompressionType.NONE;

    private int[] addresses = new int[0];
    private int[] timestamps = new int[0];
    private PacketRef[] frameRefs = new PacketRef[0];
    private PacketRef[] imuRefs = new PacketRef[0];
    private long baseUnixUs;
    private long eventCount;
    private long frameCount;
    private long imuSampleCount;

    private long position;
    private long markIn;
    private long markOut = Long.MAX_VALUE;
    private boolean repeat;
    private boolean nonMonotonicTimeExceptionsChecked = true;
    private int currentStartTimestamp;
    private int timestampResetBitmask;

    /** Typed packets for the most recent readPacketBy* time window. */
    private final List<FramePacket> pendingFrames = new ArrayList<>();
    private final List<ImuPacket> pendingImu = new ArrayList<>();
    private int lastReadT0;
    private int lastReadT1;
    private int frameCursor;
    private int imuCursor;

    public Aedat4FileInputStream(File file, AEChip chip) throws IOException {
        this(file, chip, null);
    }

    public Aedat4FileInputStream(File file, AEChip chip, ProgressMonitor progressMonitor) throws IOException {
        this.file = file;
        this.chip = chip;
        this.randomAccessFile = new RandomAccessFile(file, "r");
        this.channel = randomAccessFile.getChannel();
        try {
            log.fine("Aedat4FileInputStream open begin: " + file);
            readCompressionFromHeader();
            log.fine("header compression=" + Aedat4Compression.nameOf(compression));
            if (!maybeLoadCachedIndex(progressMonitor)) {
                log.fine("no usable cache; indexing " + file.getName());
                indexFile(progressMonitor);
                log.fine("index complete; writing cache");
                cacheIndex(progressMonitor);
                log.fine("cache write complete");
            } else {
                log.fine("loaded index from cache");
            }
            throwIfCanceled(progressMonitor, "AEDAT-4 open");
            if (progressMonitor != null) {
                progressMonitor.setNote("Finishing open " + file.getName());
                // Do not set 100 here — ProgressMonitor closes at max before EDT playback setup.
                progressMonitor.setProgress(99);
            }
            log.fine("clearMarks / EVENT_INIT");
        } catch (IOException e) {
            log.fine("open failed: " + e);
            try {
                close();
            } catch (IOException ignore) {
                // already failing open
            }
            throw e;
        }
        clearMarks();
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(3);
        log.info(String.format(
                "Opened AEDAT-4 %s (%s): %s events, %s frames, %s IMU samples, duration=%ss",
                file.getName(),
                Aedat4Compression.nameOf(compression),
                eng.format((double) eventCount).trim(),
                eng.format((double) frameCount).trim(),
                eng.format((double) imuSampleCount).trim(),
                eng.format(getDurationUs() * 1e-6).trim()));
        support.firePropertyChange(AEInputStream.EVENT_INIT, null, this);
        log.fine("Aedat4FileInputStream constructor returning");
    }

    private static void throwIfCanceled(ProgressMonitor progressMonitor, String what) throws IOException {
        if (Thread.currentThread().isInterrupted()
                || (progressMonitor != null && progressMonitor.isCanceled())) {
            throw new IOException(what + " canceled");
        }
    }

    /**
     * Appends FRME/IMUS packets that fall in the last {@code readPacketBy*}
     * time window into {@code bundle}, and updates {@link DavisBaseCamera}'s
     * latest IMU sample for the overlay.
     */
    public synchronized void appendTypedPackets(PacketBundle bundle) {
        if (bundle == null) {
            return;
        }
        for (FramePacket frame : pendingFrames) {
            bundle.add(frame);
        }
        for (ImuPacket imu : pendingImu) {
            bundle.add(imu);
            if (chip instanceof DavisBaseCamera && imu.getSize() > 0) {
                ((DavisBaseCamera) chip).setImuSample(imu.get(imu.getSize() - 1));
            }
        }
        pendingFrames.clear();
        pendingImu.clear();
    }

    /** Reads only the IOHeader compression field (also used when loading a disk index cache). */
    private void readCompressionFromHeader() throws IOException {
        channel.position(0);
        ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
        readFully(channel, version);
        if (!Arrays.equals(version.array(), Aedat4FileOutputStream.VERSION_LINE)) {
            throw new IOException(file + " is not an AEDAT-4 file");
        }
        ByteBuffer headerBytes = readSizePrefixed(channel);
        IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
        compression = Aedat4Compression.clamp(header.compression());
    }

    private void indexFile(ProgressMonitor progressMonitor) throws IOException {
        // Primitive growable buffers avoid boxing ~millions of Integer/Long (GC hangs on large files).
        IntGrow addressList = new IntGrow();
        LongGrow unixTimestampList = new LongGrow();
        ArrayList<PacketRef> frames = new ArrayList<>();
        ArrayList<PacketRef> imus = new ArrayList<>();
        long t0 = System.currentTimeMillis();

        channel.position(0);
        ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
        readFully(channel, version);
        if (!Arrays.equals(version.array(), Aedat4FileOutputStream.VERSION_LINE)) {
            throw new IOException(file + " is not an AEDAT-4 file");
        }

        ByteBuffer headerBytes = readSizePrefixed(channel);
        IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
        compression = Aedat4Compression.clamp(header.compression());
        long dataTablePosition = header.dataTablePosition();
        long fileSize = channel.size();
        ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        long scannedPackets = 0;
        if (progressMonitor != null) {
            progressMonitor.setNote("Indexing AEDAT-4 packets");
        }
        while (channel.position() + 8 <= fileSize) {
            throwIfCanceled(progressMonitor, "AEDAT-4 indexing");
            if (dataTablePosition >= 0 && channel.position() >= dataTablePosition) {
                break;
            }
            long packetOffset = channel.position();
            packetHeader.clear();
            readFully(channel, packetHeader);
            packetHeader.flip();
            int streamId = packetHeader.getInt();
            int payloadSize = packetHeader.getInt();
            long remaining = fileSize - channel.position();
            if (payloadSize < 0 || payloadSize > remaining) {
                break;
            }
            // dataTablePosition may be unset (-1) or pending (-2) on older/broken writes; stop before FTAB.
            if (dataTablePosition < 0
                    && streamId != Aedat4FileOutputStream.STREAM_EVENTS
                    && streamId != Aedat4FileOutputStream.STREAM_FRAMES
                    && streamId != Aedat4FileOutputStream.STREAM_IMU) {
                log.info(String.format(
                        "Stopping AEDAT-4 index at offset %d (streamId=%d); FileDataTable follows and IOHeader dataTablePosition=%d",
                        packetOffset, streamId, dataTablePosition));
                break;
            }
            long payloadOffset = channel.position();
            ByteBuffer payload = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, payload);
            payload.flip();
            scannedPackets++;
            ByteBuffer flat;
            try {
                flat = maybeDecompress(payload);
            } catch (IOException ex) {
                if (dataTablePosition < 0 && looksLikeFileDataTable(payload)) {
                    log.info("Stopping AEDAT-4 index before FileDataTable (dataTablePosition unset)");
                    break;
                }
                throw ex;
            }
            if (streamId == Aedat4FileOutputStream.STREAM_EVENTS) {
                decodeEventPacket(flat, addressList, unixTimestampList);
            } else if (streamId == Aedat4FileOutputStream.STREAM_FRAMES) {
                Frame frame = Frame.getSizePrefixedRootAsFrame(flat);
                long start = frame.timestampStartOfFrame() != 0 ? frame.timestampStartOfFrame() : frame.timestamp();
                long end = frame.timestampEndOfFrame() != 0 ? frame.timestampEndOfFrame() : start;
                frames.add(new PacketRef(payloadOffset, payloadSize, start, end, 1));
            } else if (streamId == Aedat4FileOutputStream.STREAM_IMU) {
                IMUPacket packet = IMUPacket.getSizePrefixedRootAsIMUPacket(flat);
                int n = packet.elementsLength();
                long start = 0;
                long end = 0;
                if (n > 0) {
                    start = packet.elements(0).timestamp();
                    end = packet.elements(n - 1).timestamp();
                }
                imus.add(new PacketRef(payloadOffset, payloadSize, start, end, n));
                imuSampleCount += n;
            }
            if (progressMonitor != null && fileSize > 0) {
                // Leave headroom for cache write (90–99).
                long denom = dataTablePosition > 0 ? dataTablePosition : fileSize;
                progressMonitor.setProgress((int) Math.min(89, (packetOffset * 89) / Math.max(1, denom)));
            }
        }

        frameCount = frames.size();
        eventCount = addressList.size;
        if (imuSampleCount == 0) {
            for (PacketRef r : imus) {
                imuSampleCount += r.numElements;
            }
        }

        if (unixTimestampList.size > 0) {
            baseUnixUs = unixTimestampList.get(0);
        } else if (!frames.isEmpty()) {
            baseUnixUs = frames.get(0).unixStart;
        } else if (!imus.isEmpty()) {
            baseUnixUs = imus.get(0).unixStart;
        }

        addresses = addressList.toArray();
        timestamps = new int[unixTimestampList.size];
        for (int i = 0; i < timestamps.length; i++) {
            timestamps[i] = (int) (unixTimestampList.get(i) - baseUnixUs);
        }
        // Frames-only / IMU-only: clock from typed streams; polarity arrays stay empty.
        if (addresses.length == 0 && (!frames.isEmpty() || !imus.isEmpty())) {
            synthesizeTimelineFromTypedStreams(frames, imus);
        }
        frameRefs = toRelativeRefs(frames);
        imuRefs = toRelativeRefs(imus);
        markOut = Math.max(0, Math.max(addresses.length, timestamps.length));
        log.info(String.format(
                "Indexed AEDAT-4 %s (%s) in %d ms (%d packets scanned): %,d events, %,d frames, %,d IMU samples",
                file.getName(), Aedat4Compression.nameOf(compression), System.currentTimeMillis() - t0, scannedPackets,
                eventCount, frameCount, imuSampleCount));
    }

    private void synthesizeTimelineFromTypedStreams(List<PacketRef> frames, List<PacketRef> imus) {
        ArrayList<Long> marks = new ArrayList<>();
        for (PacketRef f : frames) {
            marks.add(f.unixStart + ((f.unixEnd - f.unixStart) / 2));
        }
        for (PacketRef i : imus) {
            marks.add(i.unixStart);
        }
        marks.sort(Long::compareTo);
        if (marks.isEmpty()) {
            return;
        }
        if (baseUnixUs == 0) {
            baseUnixUs = marks.get(0);
        }
        // Empty addresses → readPacket returns AEPacketRaw(0); timestamps drive the clock.
        addresses = new int[0];
        timestamps = new int[marks.size()];
        for (int i = 0; i < marks.size(); i++) {
            timestamps[i] = (int) (marks.get(i) - baseUnixUs);
        }
    }

    private PacketRef[] toRelativeRefs(List<PacketRef> src) {
        PacketRef[] out = new PacketRef[src.size()];
        for (int i = 0; i < src.size(); i++) {
            PacketRef s = src.get(i);
            out[i] = new PacketRef(s.payloadOffset, s.payloadSize,
                    s.unixStart - baseUnixUs, s.unixEnd - baseUnixUs, s.numElements);
        }
        return out;
    }

    private void decodeEventPacket(ByteBuffer payload, IntGrow addressList, LongGrow unixTimestampList) {
        EventPacket packet = EventPacket.getSizePrefixedRootAsEventPacket(payload);
        EventExtractor2D extractor = chip != null ? chip.getEventExtractor() : null;
        final boolean useDavisPacking = chip instanceof DavisChip;
        int sx1 = chip == null ? 0 : chip.getSizeX() - 1;
        for (int i = 0; i < packet.elementsLength(); i++) {
            Event event = packet.elements(i);
            int x = event.x() & 0xffff;
            int y = event.y() & 0xffff;
            int type = event.polarity() ? 1 : 0; // On=1 / Off=0 (RetinaExtractor / PolarityEvent)
            int address;
            if (useDavisPacking) {
                // Davis extract hard-codes sx1-x and DavisChip bitfields; extractor
                // getAddressFromCell is not reliable (truncated masks, no flipx).
                if (x > sx1 || y >= (chip == null ? 0 : chip.getSizeY())) {
                    continue; // skip corrupt / out-of-range FB events
                }
                address = DavisChip.ADDRESS_TYPE_DVS
                        | (((sx1 - x) & 0x3ff) << DavisChip.XSHIFT)
                        | ((y & 0x1ff) << DavisChip.YSHIFT)
                        | ((type & 1) << DavisChip.POLSHIFT);
            } else if (extractor != null) {
                // NRV and other RetinaExtractor chips: use chip x/y/type shifts & flips.
                address = extractor.getAddressFromCell(x, y, type);
            } else {
                address = (x & 0xffff) | ((y & 0xffff) << 16) | (type << 31);
            }
            addressList.add(address);
            unixTimestampList.add(event.timestamp());
        }
    }

    private void collectTypedForWindow(int t0, int t1) throws IOException {
        pendingFrames.clear();
        pendingImu.clear();
        lastReadT0 = t0;
        lastReadT1 = t1;
        while (frameCursor < frameRefs.length && frameRefs[frameCursor].unixEnd < t0) {
            frameCursor++;
        }
        int fi = frameCursor;
        while (fi < frameRefs.length && frameRefs[fi].unixStart <= t1) {
            if (frameRefs[fi].unixEnd >= t0) {
                pendingFrames.add(decodeFrame(frameRefs[fi]));
            }
            fi++;
        }
        while (imuCursor < imuRefs.length && imuRefs[imuCursor].unixEnd < t0) {
            imuCursor++;
        }
        int ii = imuCursor;
        while (ii < imuRefs.length && imuRefs[ii].unixStart <= t1) {
            if (imuRefs[ii].unixEnd >= t0) {
                pendingImu.add(decodeImu(imuRefs[ii]));
            }
            ii++;
        }
    }

    private FramePacket decodeFrame(PacketRef ref) throws IOException {
        ByteBuffer payload = readPayload(ref);
        Frame frame = Frame.getSizePrefixedRootAsFrame(payload);
        int w = frame.sizeX() & 0xffff;
        int h = frame.sizeY() & 0xffff;
        if (w <= 0 || h <= 0) {
            w = chip != null ? chip.getSizeX() : 0;
            h = chip != null ? chip.getSizeY() : 0;
        }
        FramePacket out = new FramePacket(w, h, FramePacket.ColorMode.GRAYSCALE);
        out.setTimestampStartUs((int) ref.unixStart);
        out.setTimestampEndUs((int) ref.unixEnd);
        out.setExposureUs((int) Math.min(Integer.MAX_VALUE, Math.max(0, frame.exposure())));
        out.setSource(frame.source());
        short[] pixels = out.getPixels();
        int nbytes = frame.pixelsLength();
        boolean u16 = frame.format() == FrameFormat.OPENCV_16U_C1 || nbytes >= pixels.length * 2;
        if (u16) {
            int n = Math.min(pixels.length, nbytes / 2);
            for (int i = 0; i < n; i++) {
                int lo = frame.pixels(i * 2) & 0xff;
                int hi = frame.pixels(i * 2 + 1) & 0xff;
                pixels[i] = (short) (lo | (hi << 8));
            }
        } else {
            int n = Math.min(pixels.length, nbytes);
            for (int i = 0; i < n; i++) {
                pixels[i] = (short) ((frame.pixels(i) & 0xff) << 8);
            }
        }
        return out;
    }

    private ImuPacket decodeImu(PacketRef ref) throws IOException {
        ByteBuffer payload = readPayload(ref);
        IMUPacket packet = IMUPacket.getSizePrefixedRootAsIMUPacket(payload);
        int n = packet.elementsLength();
        ImuPacket out = new ImuPacket(Math.max(ImuPacket.DEFAULT_CAPACITY, n));
        for (int i = 0; i < n; i++) {
            IMU imu = packet.elements(i);
            int ts = (int) (imu.timestamp() - baseUnixUs);
            IMUSample sample = out.nextOutput();
            sample.setFromPhysicalUnits(ts,
                    imu.accelerometerX(), imu.accelerometerY(), imu.accelerometerZ(),
                    imu.gyroscopeX(), imu.gyroscopeY(), imu.gyroscopeZ(),
                    imu.temperature());
        }
        return out;
    }

    private ByteBuffer maybeDecompress(ByteBuffer payload) throws IOException {
        if (compression == CompressionType.NONE) {
            return payload;
        }
        byte[] raw = new byte[payload.remaining()];
        int pos = payload.position();
        payload.get(raw);
        payload.position(pos);
        byte[] flat = Aedat4Compression.decompress(raw, compression);
        return ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** True if buffer looks like a size-prefixed or bare FileDataTable ("FTAB") FlatBuffer. */
    private static boolean looksLikeFileDataTable(ByteBuffer payload) {
        int p = payload.position();
        int n = payload.remaining();
        if (n >= 4) {
            // Mis-framed FTAB payload often starts at the file_identifier.
            if (payload.get(p) == 'F' && payload.get(p + 1) == 'T'
                    && payload.get(p + 2) == 'A' && payload.get(p + 3) == 'B') {
                return true;
            }
        }
        if (n >= 12) {
            // Size-prefixed FlatBuffer: identifier at offset 8 after size+soffset.
            if (payload.get(p + 8) == 'F' && payload.get(p + 9) == 'T'
                    && payload.get(p + 10) == 'A' && payload.get(p + 11) == 'B') {
                return true;
            }
        }
        return false;
    }

    private ByteBuffer readPayload(PacketRef ref) throws IOException {
        ensureChannelOpen();
        ByteBuffer payload = ByteBuffer.allocate(ref.payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        try {
            channel.position(ref.payloadOffset);
            readFully(channel, payload);
        } catch (ClosedChannelException e) {
            ensureChannelOpen();
            channel.position(ref.payloadOffset);
            readFully(channel, payload);
        }
        payload.flip();
        return maybeDecompress(payload);
    }

    /**
     * Reopen FileChannel if a ViewLoop interrupt closed it
     * ({@link ClosedByInterruptException}). Index arrays remain valid.
     */
    private synchronized void ensureChannelOpen() throws IOException {
        if (channel != null && channel.isOpen()) {
            return;
        }
        if (file == null) {
            throw new IOException("AEDAT-4 channel closed and file is null");
        }
        log.info("Reopening AEDAT-4 FileChannel after close/interrupt: " + file.getName());
        if (randomAccessFile != null) {
            try {
                randomAccessFile.close();
            } catch (IOException ignore) {
            }
        }
        randomAccessFile = new RandomAccessFile(file, "r");
        channel = randomAccessFile.getChannel();
    }

    private static ByteBuffer readSizePrefixed(FileChannel channel) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, sizeBuffer);
        sizeBuffer.flip();
        int size = sizeBuffer.getInt();
        if (size < 0) {
            throw new IOException("Negative FlatBuffer size prefix " + size);
        }
        ByteBuffer payload = ByteBuffer.allocate(size + 4).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(size);
        readFully(channel, payload);
        payload.flip();
        return payload;
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new EOFException();
            }
        }
    }

    private String chipClassName() {
        return chip == null ? "null" : chip.getClass().getName();
    }

    private File indexCacheFile() {
        String name = String.format("%s.%d.%d.aedat4idx",
                file.getName(), file.length(), file.lastModified());
        return new File(System.getProperty("java.io.tmpdir"), name);
    }

    private boolean maybeLoadCachedIndex(ProgressMonitor progressMonitor) throws IOException {
        File cache = indexCacheFile();
        if (!cache.isFile() || !cache.canRead() || cache.length() == 0) {
            return false;
        }
        long t0 = System.currentTimeMillis();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cache), 1 << 20))) {
            if (!INDEX_CACHE_MAGIC.equals(in.readUTF()) || in.readInt() != INDEX_CACHE_VERSION) {
                log.info("AEDAT-4 index cache version mismatch, rebuilding: " + cache);
                return false;
            }
            if (in.readLong() != file.length() || in.readLong() != file.lastModified()) {
                log.info("AEDAT-4 index cache stale, rebuilding: " + cache);
                return false;
            }
            String cachedChip = in.readUTF();
            String currentChip = chipClassName();
            if (!cachedChip.equals(currentChip)) {
                log.info(String.format(
                        "AEDAT-4 index cache chip mismatch (cache=%s, viewer=%s), rebuilding: %s",
                        cachedChip, currentChip, cache.getName()));
                return false;
            }
            if (progressMonitor != null) {
                progressMonitor.setNote("Reading cached AEDAT-4 index");
            }
            baseUnixUs = in.readLong();
            eventCount = in.readLong();
            frameCount = in.readLong();
            imuSampleCount = in.readLong();
            int nEvents = in.readInt();
            if (nEvents < 0 || nEvents > INDEX_CACHE_MAX_EVENTS) {
                log.warning("AEDAT-4 index cache has absurd nEvents=" + nEvents + ", rebuilding");
                return false;
            }
            long remainingHint = cache.length(); // rough; exact remaining harder mid-stream
            // Each event is 4 bytes address; timestamps same count typically.
            if (nEvents > 0 && remainingHint > 0 && (nEvents * 4L) > remainingHint) {
                log.warning(String.format(
                        "AEDAT-4 index cache truncated/corrupt (nEvents=%d needs >%d bytes, file=%d), rebuilding",
                        nEvents, nEvents * 4L, remainingHint));
                return false;
            }
            if (progressMonitor != null) {
                progressMonitor.setNote(String.format("Reading cached index (%,d events)", nEvents));
                progressMonitor.setProgress(1);
            }
            log.info(String.format("Reading AEDAT-4 index cache %s (%,d events, %.1f MB)",
                    cache.getName(), nEvents, cache.length() / (1024.0 * 1024.0)));
            addresses = new int[nEvents];
            for (int i = 0; i < nEvents; i++) {
                addresses[i] = in.readInt();
                if ((i & 0xfffff) == 0) { // every ~1M
                    throwIfCanceled(progressMonitor, "AEDAT-4 cache load");
                    if (progressMonitor != null && nEvents > 0) {
                        progressMonitor.setProgress(Math.min(95, 1 + (i * 94) / nEvents));
                    }
                }
            }
            int nTimes = in.readInt();
            if (nTimes < 0 || nTimes > INDEX_CACHE_MAX_EVENTS) {
                log.warning("AEDAT-4 index cache has absurd nTimes=" + nTimes + ", rebuilding");
                return false;
            }
            timestamps = new int[nTimes];
            for (int i = 0; i < nTimes; i++) {
                timestamps[i] = in.readInt();
                if ((i & 0xfffff) == 0) {
                    throwIfCanceled(progressMonitor, "AEDAT-4 cache load");
                }
            }
            frameRefs = readRefs(in);
            imuRefs = readRefs(in);
            markOut = Math.max(0, timestamps.length);
            log.info(String.format(
                    "Loaded cached AEDAT-4 index from %s in %d ms (%,d events, %,d frames, %,d IMU samples)",
                    cache.getName(), System.currentTimeMillis() - t0,
                    eventCount, frameCount, imuSampleCount));
            return true;
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("canceled")) {
                throw e;
            }
            log.warning("Could not load AEDAT-4 index cache, rebuilding: " + e);
            return false;
        } catch (Exception e) {
            log.warning("Could not load AEDAT-4 index cache, rebuilding: " + e);
            return false;
        }
    }

    private void cacheIndex(ProgressMonitor progressMonitor) throws IOException {
        File cache = indexCacheFile();
        if (progressMonitor != null) {
            progressMonitor.setNote("Writing AEDAT-4 index cache");
            progressMonitor.setProgress(90);
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cache), 1 << 20))) {
            out.writeUTF(INDEX_CACHE_MAGIC);
            out.writeInt(INDEX_CACHE_VERSION);
            out.writeLong(file.length());
            out.writeLong(file.lastModified());
            out.writeUTF(chipClassName());
            out.writeLong(baseUnixUs);
            out.writeLong(eventCount);
            out.writeLong(frameCount);
            out.writeLong(imuSampleCount);
            out.writeInt(addresses.length);
            for (int i = 0; i < addresses.length; i++) {
                out.writeInt(addresses[i]);
                if ((i & 0xfffff) == 0) {
                    throwIfCanceled(progressMonitor, "AEDAT-4 cache write");
                    if (progressMonitor != null && addresses.length > 0) {
                        progressMonitor.setProgress(90 + Math.min(9, (i * 9) / addresses.length));
                    }
                }
            }
            out.writeInt(timestamps.length);
            for (int i = 0; i < timestamps.length; i++) {
                out.writeInt(timestamps[i]);
                if ((i & 0xfffff) == 0) {
                    throwIfCanceled(progressMonitor, "AEDAT-4 cache write");
                }
            }
            writeRefs(out, frameRefs);
            writeRefs(out, imuRefs);
            out.flush();
            log.info(String.format("Cached AEDAT-4 index (%s) to %s", file.getName(), cache.getAbsolutePath()));
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("canceled")) {
                // Remove partial cache so next open rebuilds cleanly.
                if (!cache.delete() && cache.exists()) {
                    log.warning("Could not delete partial AEDAT-4 index cache " + cache);
                }
                throw e;
            }
            log.warning("Could not cache AEDAT-4 index: " + e);
        } catch (Exception e) {
            log.warning("Could not cache AEDAT-4 index: " + e);
        }
    }

    /** Growable int buffer used while indexing (avoids boxing). */
    private static final class IntGrow {
        int[] a = new int[4096];
        int size;

        void add(int v) {
            if (size == a.length) {
                a = Arrays.copyOf(a, a.length * 2);
            }
            a[size++] = v;
        }

        int[] toArray() {
            return size == a.length ? a : Arrays.copyOf(a, size);
        }
    }

    /** Growable long buffer used while indexing (avoids boxing). */
    private static final class LongGrow {
        long[] a = new long[4096];
        int size;

        void add(long v) {
            if (size == a.length) {
                a = Arrays.copyOf(a, a.length * 2);
            }
            a[size++] = v;
        }

        long get(int i) {
            return a[i];
        }
    }

    private static PacketRef[] readRefs(DataInputStream in) throws IOException {
        int n = in.readInt();
        PacketRef[] refs = new PacketRef[n];
        for (int i = 0; i < n; i++) {
            refs[i] = new PacketRef(in.readLong(), in.readInt(), in.readLong(), in.readLong(), in.readInt());
        }
        return refs;
    }

    private static void writeRefs(DataOutputStream out, PacketRef[] refs) throws IOException {
        out.writeInt(refs.length);
        for (PacketRef r : refs) {
            out.writeLong(r.payloadOffset);
            out.writeInt(r.payloadSize);
            out.writeLong(r.unixStart);
            out.writeLong(r.unixEnd);
            out.writeInt((int) Math.min(Integer.MAX_VALUE, r.numElements));
        }
    }

    @Override
    public synchronized AEPacketRaw readPacketByNumber(int n) throws IOException {
        ensureReadableOrThrow();
        ensureChannelOpen();
        int start = (int) position;
        int end = (int) Math.min(effectiveMarkOut(), position + Math.max(1, n));
        position = end;
        currentStartTimestamp = timestamps[start];
        collectTypedForWindow(timestamps[start], timestamps[end - 1]);
        firePosition();
        return polaritySlice(start, end);
    }

    @Override
    public synchronized AEPacketRaw readPacketByTime(int dt) throws IOException {
        ensureReadableOrThrow();
        ensureChannelOpen();
        int start = (int) position;
        int target = timestamps[start] + dt;
        int end = start + 1;
        while (end < effectiveMarkOut() && timestamps[end - 1] <= target) {
            end++;
        }
        position = end;
        currentStartTimestamp = timestamps[start];
        collectTypedForWindow(timestamps[start], timestamps[end - 1]);
        firePosition();
        return polaritySlice(start, end);
    }

    private AEPacketRaw polaritySlice(int start, int end) {
        if (addresses.length == 0) {
            return new AEPacketRaw(0);
        }
        int aEnd = Math.min(end, addresses.length);
        int aStart = Math.min(start, aEnd);
        return new AEPacketRaw(Arrays.copyOfRange(addresses, aStart, aEnd), Arrays.copyOfRange(timestamps, aStart, aEnd));
    }

    private void ensureReadableOrThrow() throws EOFException {
        if (timestamps.length == 0) {
            throw new EOFException("AEDAT-4 file has no playable timeline (no events/frames/IMU)");
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
        return Math.min(markOut, timestamps.length);
    }

    private void firePosition() {
        support.firePropertyChange(AEInputStream.EVENT_POSITION, null, position);
    }

    @Override
    public boolean isNonMonotonicTimeExceptionsChecked() { return nonMonotonicTimeExceptionsChecked; }

    @Override
    public void setNonMonotonicTimeExceptionsChecked(boolean yes) { nonMonotonicTimeExceptionsChecked = yes; }

    @Override
    public long getAbsoluteStartingTimeMs() { return baseUnixUs / 1000L; }

    @Override
    public ZoneId getZoneId() { return ZoneId.systemDefault(); }

    @Override
    public int getDurationUs() { return getLastTimestamp() - getFirstTimestamp(); }

    @Override
    public int getFirstTimestamp() { return timestamps.length == 0 ? 0 : timestamps[0]; }

    @Override
    public PropertyChangeSupport getSupport() { return support; }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) { support.addPropertyChangeListener(listener); }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) { support.removePropertyChangeListener(listener); }

    @Override
    public File getFile() { return file; }

    @Override
    public int getLastTimestamp() { return timestamps.length == 0 ? 0 : timestamps[timestamps.length - 1]; }

    @Override
    public int getMostRecentTimestamp() {
        int index = (int) Math.max(0, Math.min(position - 1, timestamps.length - 1));
        return timestamps.length == 0 ? 0 : timestamps[index];
    }

    @Override
    public void setFile(File file) { this.file = file; }

    @Override
    public int getTimestampResetBitmask() { return timestampResetBitmask; }

    @Override
    public void setTimestampResetBitmask(int timestampResetBitmask) { this.timestampResetBitmask = timestampResetBitmask; }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
        }
        if (randomAccessFile != null) {
            randomAccessFile.close();
        }
    }

    @Override
    public int getCurrentStartTimestamp() { return currentStartTimestamp; }

    @Override
    public void setCurrentStartTimestamp(int currentStartTimestamp) { this.currentStartTimestamp = currentStartTimestamp; }

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
    public float getFractionalPosition() { return timestamps.length == 0 ? 0 : (float) position / timestamps.length; }

    @Override
    public long position() { return position; }

    @Override
    public void position(long n) {
        long old = position;
        position = Math.max(markIn, Math.min(n, effectiveMarkOut()));
        int t = timestamps.length == 0 ? 0 : timestamps[(int) Math.min(Math.max(0, position), timestamps.length - 1)];
        frameCursor = 0;
        imuCursor = 0;
        while (frameCursor < frameRefs.length && frameRefs[frameCursor].unixEnd < t) {
            frameCursor++;
        }
        while (imuCursor < imuRefs.length && imuRefs[imuCursor].unixEnd < t) {
            imuCursor++;
        }
        support.firePropertyChange(AEInputStream.EVENT_REPOSITIONED, old, position);
    }

    @Override
    public void rewind() throws IOException {
        long old = position;
        position = markIn;
        frameCursor = 0;
        imuCursor = 0;
        support.firePropertyChange(AEInputStream.EVENT_REWOUND, old, position);
    }

    @Override
    public void setFractionalPosition(float frac) { position((long) (Math.max(0, Math.min(1, frac)) * timestamps.length)); }

    @Override
    public long size() { return timestamps.length; }

    @Override
    public void clearMarks() {
        long[] oldMarks = new long[]{markIn, markOut};
        markIn = 0;
        markOut = timestamps.length;
        markers.clear();
        support.firePropertyChange(AEInputStream.EVENT_MARKS_CLEARED, oldMarks, new long[]{markIn, markOut});
    }

    @Override
    public long setMarkIn() {
        if (position <= markOut) {
            long old = markIn;
            markIn = position;
            support.firePropertyChange(AEInputStream.EVENT_MARK_IN_SET, old, markIn);
        }
        return markIn;
    }

    @Override
    public long setMarkOut() {
        if (position > markIn) {
            long old = markOut;
            markOut = position;
            support.firePropertyChange(AEInputStream.EVENT_MARK_OUT_SET, old, markOut);
        }
        return markOut;
    }

    @Override
    public long getMarkInPosition() { return markIn; }

    @Override
    public long getMarkOutPosition() { return markOut; }

    @Override
    public boolean isMarkInSet() { return markIn != 0; }

    @Override
    public boolean isMarkOutSet() { return markOut != timestamps.length; }

    @Override
    public void setRepeat(boolean repeat) { this.repeat = repeat; }

    @Override
    public boolean isRepeat() { return repeat; }

    /** Packet payload location; unixStart/End are relative µs after indexing. */
    private static final class PacketRef {
        final long payloadOffset;
        final int payloadSize;
        final long unixStart;
        final long unixEnd;
        final long numElements;

        PacketRef(long payloadOffset, int payloadSize, long unixStart, long unixEnd, long numElements) {
            this.payloadOffset = payloadOffset;
            this.payloadSize = payloadSize;
            this.unixStart = unixStart;
            this.unixEnd = unixEnd;
            this.numElements = numElements;
        }
    }
}
