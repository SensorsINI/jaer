package net.sf.jaer.eventio.aedat4;

import com.google.flatbuffers.FlatBufferBuilder;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.sf.jaer.eventio.RecordingChipDetector;
import net.sf.jaer.eventio.RecordingChipDetector.StreamHint;
import net.sf.jaer.eventio.aedat4.MulticameraClockDrift.CameraFit;
import net.sf.jaer.eventio.aedat4.MulticameraClockDrift.Progress;
import net.sf.jaer.eventio.aedat4.MulticameraClockDrift.Result;
import net.sf.jaer.eventio.aedat4.dv.Event;
import net.sf.jaer.eventio.aedat4.dv.EventPacket;
import net.sf.jaer.eventio.aedat4.dv.FileDataDefinition;
import net.sf.jaer.eventio.aedat4.dv.FileDataTable;
import net.sf.jaer.eventio.aedat4.dv.Frame;
import net.sf.jaer.eventio.aedat4.dv.IMU;
import net.sf.jaer.eventio.aedat4.dv.IMUPacket;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;

/**
 * Writes a new AEDAT-4 file whose non-reference cameras lie on the reference
 * camera's clock. Packet-index times and the timestamps inside event, frame,
 * IMU, and lossy-bin packets are mapped with
 * {@code referenceFirst + (timestamp - referenceFirst - offset) / scale}.
 * The reference camera is copied still compressed. The source file is not modified.
 */
public final class Aedat4ClockCorrection {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    /** Suffix before {@code .aedat4}: {@code foo.aedat4} becomes {@code foo-clocksync.aedat4}. */
    public static final String CLOCKSYNC_INFIX = "-clocksync";
    private static final String PARTIAL_SUFFIX = ".partial";
    private static final long DATA_TABLE_POSITION_PENDING = -2L;

    private Aedat4ClockCorrection() {
    }

    /** Sibling path {@code basename-clocksync.aedat4} next to {@code source}. */
    public static File sibling(File source) {
        if (source == null) {
            return null;
        }
        String name = source.getName();
        String lower = name.toLowerCase();
        String base = lower.endsWith(".aedat4")
                ? name.substring(0, name.length() - ".aedat4".length())
                : name;
        File parent = source.getParentFile();
        return new File(parent, base + CLOCKSYNC_INFIX + ".aedat4");
    }

    /**
     * Map one timestamp onto the reference clock. Zero stays zero so an absent
     * frame or IMU field is not turned into a real time.
     * Scale is this camera's elapsed time divided by the reference elapsed time.
     */
    public static long mapTimestamp(long timestampUs, long referenceFirstUs, double offsetUs, double scale) {
        if (timestampUs == 0L || !(scale > 0.0) || Double.isNaN(scale) || Double.isNaN(offsetUs)) {
            return timestampUs;
        }
        return Math.round(referenceFirstUs + (timestampUs - referenceFirstUs - offsetUs) / scale);
    }

    /**
     * Copy {@code source} to {@code dest}, warping every fitted camera onto the
     * reference clock. Writes {@code dest.partial} and renames it when the index is in place.
     *
     * @param progress optional; {@code false} from {@link Progress#update} stops the copy
     * @return the finished {@code dest} file
     */
    public static File writeCorrectedCopy(File source, File dest, Result result, Progress progress)
            throws IOException {
        if (source == null || !source.isFile()) {
            throw new IOException("Source AEDAT-4 missing: " + source);
        }
        if (dest == null) {
            throw new IOException("Destination is null");
        }
        if (result == null || !result.crossCorrelationOk()) {
            throw new IOException("No camera had a fitted clock scale");
        }
        if (source.getCanonicalFile().equals(dest.getCanonicalFile())) {
            throw new IOException("Corrected copy must be a different file");
        }
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            throw new IOException("Destination directory is missing: " + parent);
        }
        File partial = new File(dest.getPath() + PARTIAL_SUFFIX);
        if (partial.exists() && !partial.delete()) {
            throw new IOException("Cannot delete leftover partial: " + partial);
        }
        long t0 = System.currentTimeMillis();
        try {
            doWrite(source, partial, result, progress);
            if (dest.exists() && !dest.delete()) {
                throw new IOException("Cannot replace existing file: " + dest);
            }
            if (!partial.renameTo(dest)) {
                throw new IOException("Cannot rename " + partial + " to " + dest);
            }
            log.info(String.format(
                    "Clock-corrected AEDAT-4 %s -> %s in %d ms (src %,d bytes, dest %,d bytes)",
                    source.getName(), dest.getName(), System.currentTimeMillis() - t0,
                    source.length(), dest.length()));
            return dest;
        } catch (Throwable t) {
            if (partial.exists() && !partial.delete()) {
                log.warning("Left partial clock-correction file: " + partial);
            }
            if (t instanceof MulticameraClockDrift.Cancelled) {
                throw (MulticameraClockDrift.Cancelled) t;
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException("Clock correction failed: " + t, t);
        }
    }

    private static void doWrite(File source, File partial, Result result, Progress progress) throws IOException {
        try (RandomAccessFile inRaf = new RandomAccessFile(source, "r");
                FileChannel in = inRaf.getChannel();
                RandomAccessFile outRaf = new RandomAccessFile(partial, "rw");
                FileChannel out = outRaf.getChannel()) {

            ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
            readFully(in, version);
            if (!Arrays.equals(version.array(), Aedat4FileOutputStream.VERSION_LINE)) {
                throw new IOException(source + " is not an AEDAT-4 file");
            }
            ByteBuffer headerBytes = readSizePrefixed(in);
            IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
            int compression = Aedat4Compression.clamp(header.compression());
            String infoNode = header.infoNode();
            if (infoNode == null) {
                infoNode = "";
            }
            Map<Integer, Warp> warps = warpsFor(result, infoNode);
            if (warps.isEmpty()) {
                throw new IOException("No camera had a usable clock scale");
            }
            long srcTablePos = header.dataTablePosition();
            long fileSize = in.size();
            long dataEnd = (srcTablePos >= 0 && srcTablePos < fileSize) ? srcTablePos : fileSize;
            List<FtabMeta> srcMeta = tryLoadSourceFtabMeta(in, srcTablePos, fileSize, compression);

            out.write(ByteBuffer.wrap(Aedat4FileOutputStream.VERSION_LINE));
            long headerPosition = out.position();
            byte[] outHeader = buildIOHeader(compression, DATA_TABLE_POSITION_PENDING, infoNode);
            out.write(ByteBuffer.wrap(outHeader));

            List<DataDef> defs = new ArrayList<>();
            ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            int packetIndex = 0;
            int correctedPackets = 0;
            final long dataStart = in.position();
            final long dataSpan = Math.max(1L, dataEnd - dataStart);
            final long[] meta = new long[3];
            int lastPercent = -1;
            Set<Integer> warned = new HashSet<>();

            while (in.position() + 8 <= dataEnd) {
                packetHeader.clear();
                readFully(in, packetHeader);
                packetHeader.flip();
                int streamId = packetHeader.getInt();
                int payloadSize = packetHeader.getInt();
                if (payloadSize < 0 || in.position() + (long) payloadSize > dataEnd) {
                    break;
                }
                byte[] compressed = new byte[payloadSize];
                readFully(in, ByteBuffer.wrap(compressed));

                long numElements;
                long tStart;
                long tEnd;
                FtabMeta row = null;
                if (srcMeta != null && packetIndex < srcMeta.size()
                        && srcMeta.get(packetIndex).streamId == streamId) {
                    row = srcMeta.get(packetIndex);
                    numElements = row.numElements;
                    tStart = row.timestampStart;
                    tEnd = row.timestampEnd;
                } else {
                    byte[] flat = Aedat4Compression.decompress(compressed, compression);
                    fillMetaFromFlat(flat, meta);
                    numElements = meta[0];
                    tStart = meta[1];
                    tEnd = meta[2];
                }

                byte[] toWrite = compressed;
                Warp warp = warps.get(streamId);
                if (warp != null) {
                    byte[] flat = Aedat4Compression.decompress(compressed, compression);
                    // NONE returns the same array. Mutate a copy so a rejected packet
                    // is still written with its original bytes.
                    if (flat == compressed) {
                        flat = Arrays.copyOf(compressed, compressed.length);
                    }
                    if (!warpFlat(flat, warp)) {
                        if (Math.floorMod(streamId, Aedat4CameraTrack.STREAMS_PER_CAMERA) == 0) {
                            throw new IOException("Could not correct event packet on stream " + streamId
                                    + " at packet " + packetIndex);
                        }
                        if (warned.add(streamId)) {
                            log.warning("Left stream " + streamId
                                    + " unchanged; payload was not EVTS, FRME, IMUS, or LBEV");
                        }
                    } else {
                        toWrite = Aedat4Compression.compress(flat, compression);
                        if (row != null) {
                            tStart = warp.map(tStart);
                            tEnd = warp.map(tEnd);
                        } else {
                            fillMetaFromFlat(flat, meta);
                            numElements = meta[0];
                            tStart = meta[1];
                            tEnd = meta[2];
                        }
                        correctedPackets++;
                    }
                }

                long outPacketOffset = out.position() + 8L;
                ByteBuffer outHdr = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                outHdr.putInt(streamId);
                outHdr.putInt(toWrite.length);
                outHdr.flip();
                out.write(outHdr);
                out.write(ByteBuffer.wrap(toWrite));
                defs.add(new DataDef(outPacketOffset, streamId, toWrite.length, numElements, tStart, tEnd));
                packetIndex++;

                if ((packetIndex & 4095) == 0 || percent(in.position(), dataStart, dataSpan) != lastPercent) {
                    lastPercent = percent(in.position(), dataStart, dataSpan);
                    if (progress != null && !progress.update("Writing corrected copy", lastPercent)) {
                        throw new MulticameraClockDrift.Cancelled();
                    }
                }
            }

            if (progress != null && !progress.update("Writing packet index", 96)) {
                throw new MulticameraClockDrift.Cancelled();
            }
            long tablePosition = Aedat4FileDataIndex.write(out, compression, rowsOf(defs));
            byte[] patched = buildIOHeader(compression, tablePosition, infoNode);
            if (patched.length != outHeader.length) {
                throw new IOException(String.format(
                        "IOHeader size changed on clock correction (%d -> %d); infoNode length unstable",
                        outHeader.length, patched.length));
            }
            long end = out.position();
            out.position(headerPosition);
            out.write(ByteBuffer.wrap(patched));
            out.position(end);
            out.force(true);
            log.info(String.format(
                    "Clock correction wrote %d packets (%d corrected) on streams %s, FTAB at %d",
                    defs.size(), correctedPackets, warps.keySet(), tablePosition));
        }
    }

    private static int percent(long position, long dataStart, long dataSpan) {
        long pct = ((position - dataStart) * 95L) / dataSpan;
        if (pct < 0L) {
            return 0;
        }
        return (int) Math.min(95L, pct);
    }

    /**
     * One warp per stream that shares a fitted camera. The reference camera,
     * including its frame and IMU streams, is left out.
     */
    private static Map<Integer, Warp> warpsFor(Result result, String infoNode) throws IOException {
        long origin = 0L;
        CameraFit reference = null;
        for (CameraFit camera : result.cameras) {
            if (camera.reference) {
                reference = camera;
                origin = camera.firstUs;
            }
        }
        if (reference == null) {
            throw new IOException("No reference camera");
        }
        List<StreamHint> hints = RecordingChipDetector.streamsFromInfoNodeXml(infoNode);
        Map<Integer, String> sourceOf = new HashMap<>();
        for (StreamHint hint : hints) {
            sourceOf.put(hint.streamId, hint.source == null ? "" : hint.source);
        }
        Set<Integer> frozen = new HashSet<>();
        frozen.add(reference.streamId);
        addSiblings(frozen, reference.streamId, sourceOf, hints);

        Map<Integer, Warp> warps = new HashMap<>();
        for (CameraFit fit : result.cameras) {
            if (!usable(fit)) {
                continue;
            }
            warps.put(fit.streamId, new Warp(origin, fit.offsetAtStartUs, fit.scaleVsReference));
        }
        List<Integer> fitted = new ArrayList<>(warps.keySet());
        for (int streamId : fitted) {
            Warp warp = warps.get(streamId);
            String source = sourceOf.getOrDefault(streamId, "");
            if (!source.isEmpty()) {
                for (StreamHint hint : hints) {
                    if (source.equals(hint.source) && !frozen.contains(hint.streamId)) {
                        warps.putIfAbsent(hint.streamId, warp);
                    }
                }
            }
            int group = Math.floorDiv(streamId, Aedat4CameraTrack.STREAMS_PER_CAMERA);
            for (int slot = 0; slot < Aedat4CameraTrack.STREAMS_PER_CAMERA; slot++) {
                int sibling = group * Aedat4CameraTrack.STREAMS_PER_CAMERA + slot;
                if (!frozen.contains(sibling)) {
                    warps.putIfAbsent(sibling, warp);
                }
            }
        }
        frozen.forEach(warps::remove);
        return warps;
    }

    private static void addSiblings(Set<Integer> into, int streamId, Map<Integer, String> sourceOf,
            List<StreamHint> hints) {
        String source = sourceOf.getOrDefault(streamId, "");
        if (!source.isEmpty()) {
            for (StreamHint hint : hints) {
                if (source.equals(hint.source)) {
                    into.add(hint.streamId);
                }
            }
        }
        int group = Math.floorDiv(streamId, Aedat4CameraTrack.STREAMS_PER_CAMERA);
        for (int slot = 0; slot < Aedat4CameraTrack.STREAMS_PER_CAMERA; slot++) {
            into.add(group * Aedat4CameraTrack.STREAMS_PER_CAMERA + slot);
        }
    }

    private static boolean usable(CameraFit fit) {
        return fit.rateWasFit() && fit.scaleVsReference > 0.0 && !Double.isNaN(fit.offsetAtStartUs);
    }

    /** @return false when the payload is not a timestamped packet this writer knows */
    private static boolean warpFlat(byte[] flat, Warp warp) {
        if (flat == null || flat.length < 4) {
            return false;
        }
        ByteBuffer wrapped = ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN);
        if (Aedat4LossyTimeBins.isLossyPayload(wrapped)) {
            return Aedat4LossyTimeBins.mapBinTimestamps(wrapped, warp::map);
        }
        if (flat.length < 12) {
            return false;
        }
        char c0 = (char) (flat[8] & 0xff);
        char c1 = (char) (flat[9] & 0xff);
        char c2 = (char) (flat[10] & 0xff);
        char c3 = (char) (flat[11] & 0xff);
        try {
            if (c0 == 'E' && c1 == 'V' && c2 == 'T' && c3 == 'S') {
                EventPacket packet = EventPacket.getSizePrefixedRootAsEventPacket(wrapped);
                int n = packet.elementsLength();
                int start = packet.elementsVectorStart();
                if (n > 0 && start < 0) {
                    return false;
                }
                Event event = new Event();
                for (int i = 0; i < n; i++) {
                    event.__assign(start + i * 16, wrapped);
                    long timestamp = event.timestamp();
                    if (timestamp != 0L) {
                        event.mutateTimestamp(warp.map(timestamp));
                    }
                }
                return true;
            }
            if (c0 == 'F' && c1 == 'R' && c2 == 'M' && c3 == 'E') {
                Frame frame = Frame.getSizePrefixedRootAsFrame(wrapped);
                frame.mapTimestamps(warp::map);
                return true;
            }
            if (c0 == 'I' && c1 == 'M' && c2 == 'U' && c3 == 'S') {
                IMUPacket packet = IMUPacket.getSizePrefixedRootAsIMUPacket(wrapped);
                int n = packet.elementsLength();
                IMU sample = new IMU();
                for (int i = 0; i < n; i++) {
                    IMU one = packet.elements(sample, i);
                    if (one == null) {
                        return false;
                    }
                    one.mapTimestamp(warp::map);
                }
                return true;
            }
        } catch (RuntimeException ex) {
            return false;
        }
        return false;
    }

    /** Fills {@code out} as {@code [numElements, tStart, tEnd]}. */
    private static void fillMetaFromFlat(byte[] flat, long[] out) {
        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        if (flat == null || flat.length < 4) {
            return;
        }
        ByteBuffer bb = ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN);
        try {
            if (Aedat4LossyTimeBins.isLossyPayload(bb)) {
                Aedat4LossyTimeBins.Summary summary = Aedat4LossyTimeBins.summarize(bb);
                out[0] = summary.expanded;
                out[1] = summary.tMin;
                out[2] = summary.tMax;
                return;
            }
            if (flat.length < 12) {
                return;
            }
            char c0 = (char) (bb.get(8) & 0xff);
            char c1 = (char) (bb.get(9) & 0xff);
            char c2 = (char) (bb.get(10) & 0xff);
            char c3 = (char) (bb.get(11) & 0xff);
            if (c0 == 'E' && c1 == 'V' && c2 == 'T' && c3 == 'S') {
                EventPacket p = EventPacket.getSizePrefixedRootAsEventPacket(bb);
                int n = p.elementsLength();
                out[0] = n;
                if (n > 0) {
                    out[1] = p.elements(0).timestamp();
                    out[2] = p.elements(n - 1).timestamp();
                }
            } else if (c0 == 'F' && c1 == 'R' && c2 == 'M' && c3 == 'E') {
                Frame f = Frame.getSizePrefixedRootAsFrame(bb);
                out[0] = 1;
                long start = f.timestampStartOfFrame() != 0 ? f.timestampStartOfFrame() : f.timestamp();
                long end = f.timestampEndOfFrame() != 0 ? f.timestampEndOfFrame() : start;
                out[1] = start;
                out[2] = end;
            } else if (c0 == 'I' && c1 == 'M' && c2 == 'U' && c3 == 'S') {
                IMUPacket p = IMUPacket.getSizePrefixedRootAsIMUPacket(bb);
                int n = p.elementsLength();
                out[0] = n;
                if (n > 0) {
                    out[1] = p.elements(0).timestamp();
                    out[2] = p.elements(n - 1).timestamp();
                }
            }
        } catch (RuntimeException | IOException ignore) {
            // leave zeros
        }
    }

    private static List<FtabMeta> tryLoadSourceFtabMeta(FileChannel in, long dataTablePosition,
            long fileSize, int compression) {
        if (dataTablePosition < 0 || dataTablePosition >= fileSize) {
            return null;
        }
        long saved;
        try {
            saved = in.position();
        } catch (IOException e) {
            return null;
        }
        List<FtabMeta> list = new ArrayList<>();
        try {
            boolean ok = Aedat4FileDataIndex.forEachTable(in, dataTablePosition, fileSize, compression, tableBytes -> {
                if (!Aedat4FileDataIndex.looksLikeFtab(tableBytes)) {
                    throw new IOException("not FTAB");
                }
                FileDataTable table = FileDataTable.getSizePrefixedRootAsFileDataTable(tableBytes);
                int n = table.tableLength();
                if (n < 0) {
                    throw new IOException("bad FileDataTable length " + n);
                }
                FileDataDefinition def = new FileDataDefinition();
                for (int i = 0; i < n; i++) {
                    FileDataDefinition d = table.table(def, i);
                    if (d == null) {
                        throw new IOException("null FileDataTable row");
                    }
                    list.add(new FtabMeta(d.packetInfoStreamID(), d.numElements(),
                            d.timestampStart(), d.timestampEnd()));
                    if (list.size() > Aedat4FileInputStream.INDEX_CACHE_MAX_PACKETS) {
                        throw new IOException("FileDataTable longer than the index cap");
                    }
                }
            });
            if (!ok || list.isEmpty()) {
                return null;
            }
            return list;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                in.position(saved);
            } catch (IOException ignore) {
            }
        }
    }

    private static byte[] buildIOHeader(int compression, long dataTablePosition, String infoNode) {
        FlatBufferBuilder builder = new FlatBufferBuilder(Math.max(1024, infoNode.length() + 64));
        int info = builder.createString(infoNode);
        int root = IOHeader.createIOHeader(builder, compression, dataTablePosition, info);
        builder.finishSizePrefixed(root, "IOHE");
        return builder.sizedByteArray();
    }

    private static Aedat4FileDataIndex.Rows rowsOf(List<DataDef> defs) {
        return new Aedat4FileDataIndex.Rows() {
            @Override
            public int size() {
                return defs.size();
            }

            @Override
            public long byteOffset(int index) {
                return defs.get(index).byteOffset;
            }

            @Override
            public int streamId(int index) {
                return defs.get(index).streamId;
            }

            @Override
            public int payloadSize(int index) {
                return defs.get(index).size;
            }

            @Override
            public long numElements(int index) {
                return defs.get(index).numElements;
            }

            @Override
            public long timestampStart(int index) {
                return defs.get(index).timestampStart;
            }

            @Override
            public long timestampEnd(int index) {
                return defs.get(index).timestampEnd;
            }
        };
    }

    private static ByteBuffer readSizePrefixed(FileChannel channel) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, sizeBuffer);
        sizeBuffer.flip();
        int size = sizeBuffer.getInt();
        if (size < 0 || size > 64 * 1024 * 1024) {
            throw new IOException("AEDAT-4 header size " + size);
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
                throw new IOException("Unexpected EOF");
            }
        }
    }

    private static final class Warp {
        final long originUs;
        final double offsetUs;
        final double scale;

        Warp(long originUs, double offsetUs, double scale) {
            this.originUs = originUs;
            this.offsetUs = offsetUs;
            this.scale = scale;
        }

        long map(long timestampUs) {
            return mapTimestamp(timestampUs, originUs, offsetUs, scale);
        }
    }

    private static final class FtabMeta {
        final int streamId;
        final long numElements;
        final long timestampStart;
        final long timestampEnd;

        FtabMeta(int streamId, long numElements, long timestampStart, long timestampEnd) {
            this.streamId = streamId;
            this.numElements = numElements;
            this.timestampStart = timestampStart;
            this.timestampEnd = timestampEnd;
        }
    }

    private static final class DataDef {
        final long byteOffset;
        final int streamId;
        final int size;
        final long numElements;
        final long timestampStart;
        final long timestampEnd;

        DataDef(long byteOffset, int streamId, int size, long numElements, long timestampStart, long timestampEnd) {
            this.byteOffset = byteOffset;
            this.streamId = streamId;
            this.size = size;
            this.numElements = numElements;
            this.timestampStart = timestampStart;
            this.timestampEnd = timestampEnd;
        }
    }

    /** Synthetic file: reference stream unchanged, stream 3 and its frame/IMU siblings warped. */
    static void selfCheck() {
        long base = 1_700_000_000_000_000L;
        if (mapTimestamp(base + 1000L, base, 1000.0, 2.0) != base) {
            throw new AssertionError("origin+offset must land on the reference start");
        }
        if (mapTimestamp(base + 1_000_000L, base, 1000.0, 2.0) != base + 499_500L) {
            throw new AssertionError("midpoint map");
        }
        if (mapTimestamp(base + 10_000_000L, base, 1000.0, 2.0) != base + 4_999_500L) {
            throw new AssertionError("later map");
        }
        if (mapTimestamp(0L, base, 1000.0, 2.0) != 0L) {
            throw new AssertionError("absent timestamp must stay zero");
        }

        File src = null;
        File dest = null;
        try {
            src = File.createTempFile("jaer-clocksync", ".aedat4");
            dest = File.createTempFile("jaer-clocksync-out", ".aedat4");
            if (!dest.delete()) {
                throw new IOException("Cannot clear " + dest);
            }
            writeSample(src, base);
            CameraFit ref = new CameraFit("ref", 0, true, 2, 1, base + 100L, base + 200L,
                    1.0, 0.0, 0.0, Double.NaN, 0, 0, 0.0, 0.0, false, new double[0]);
            CameraFit cam = new CameraFit("cam", 3, false, 4, 2, base + 1000L, base + 1_000_000L,
                    2.0, 1000.0, 0.0, Double.NaN, 3, 3, 0.9, 0.99, false, new double[0]);
            Result result = new Result(src, "ref", 0L, "", List.of(ref, cam), "");
            writeCorrectedCopy(src, dest, result, null);
            assertSample(src, dest, base);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        } finally {
            if (src != null) {
                src.delete();
            }
            if (dest != null) {
                dest.delete();
            }
        }
    }

    private static void writeSample(File file, long base) throws IOException {
        ch.unizh.ini.jaer.chip.retina.DVS128 chip = new ch.unizh.ini.jaer.chip.retina.DVS128();
        try (Aedat4FileOutputStream output = new Aedat4FileOutputStream(file, chip,
                net.sf.jaer.eventio.aedat4.dv.CompressionType.LZ4, base)) {
            output.writeCopiedPacket(0, events(new long[]{base + 100L, base + 200L}, (short) 3, (short) 4),
                    2, base + 100L, base + 200L);
            output.writeCopiedPacket(3, events(new long[]{base + 1000L, base + 1_000_000L}, (short) 7, (short) 8),
                    2, base + 1000L, base + 1_000_000L);
            Aedat4LossyTimeBins.Accumulator bins = new Aedat4LossyTimeBins.Accumulator(0);
            bins.add(base + 1_000_000L, 1, 2, true);
            Aedat4LossyTimeBins.Encoded encoded = bins.finish();
            output.writeCopiedPacket(3, encoded.payload, encoded.expanded, encoded.tMin, encoded.tMax);
            output.writeCopiedPacket(4, frame(base + 5000L, base + 5100L), 1, base + 5000L, base + 5100L);
            output.writeCopiedPacket(5, imu(base + 8000L), 1, base + 8000L, base + 8000L);
            output.writeCopiedPacket(6, events(new long[]{base + 42L}, (short) 1, (short) 1),
                    1, base + 42L, base + 42L);
        }
    }

    private static byte[] events(long[] timestamps, short x, short y) {
        short[] xs = new short[timestamps.length];
        short[] ys = new short[timestamps.length];
        boolean[] on = new boolean[timestamps.length];
        Arrays.fill(xs, x);
        Arrays.fill(ys, y);
        Arrays.fill(on, true);
        FlatBufferBuilder builder = new FlatBufferBuilder(timestamps.length * 16 + 64);
        int vector = EventPacket.createElementsVector(builder, timestamps, xs, ys, on);
        int root = EventPacket.createEventPacket(builder, vector);
        builder.finishSizePrefixed(root, "EVTS");
        return builder.sizedByteArray();
    }

    private static byte[] frame(long startUs, long endUs) {
        FlatBufferBuilder builder = new FlatBufferBuilder(128);
        int pixels = Frame.createPixelsVector(builder, new byte[]{9, 8, 7, 6});
        int root = Frame.createFrame(builder, startUs, startUs, endUs, 0L, 0L,
                net.sf.jaer.eventio.aedat4.dv.FrameFormat.OPENCV_8U_C1,
                (short) 2, (short) 2, (short) 0, (short) 0, pixels, 100L,
                net.sf.jaer.eventio.aedat4.dv.FrameSource.UNDEFINED);
        builder.finishSizePrefixed(root, "FRME");
        return builder.sizedByteArray();
    }

    private static byte[] imu(long timestampUs) {
        FlatBufferBuilder builder = new FlatBufferBuilder(128);
        int sample = IMU.createIMU(builder, timestampUs, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        int vector = IMUPacket.createElementsVector(builder, new int[]{sample});
        int root = IMUPacket.createIMUPacket(builder, vector);
        builder.finishSizePrefixed(root, "IMUS");
        return builder.sizedByteArray();
    }

    private static void assertSample(File source, File dest, long base) throws IOException {
        List<Seen> original = readPackets(source);
        List<Seen> corrected = readPackets(dest);
        if (original.size() != corrected.size()) {
            throw new AssertionError("packet count " + original.size() + " vs " + corrected.size());
        }
        int eventsOn3 = 0;
        int lossyOn3 = 0;
        for (int i = 0; i < corrected.size(); i++) {
            Seen before = original.get(i);
            Seen after = corrected.get(i);
            if (before.streamId != after.streamId) {
                throw new AssertionError("stream id changed at packet " + i);
            }
            if (before.streamId == 0) {
                expectTimes(after, base + 100L, base + 200L, "reference events");
                expectIndex(after, base + 100L, base + 200L, "reference index");
                if (after.x != 3 || after.y != 4) {
                    throw new AssertionError("reference address changed");
                }
            } else if (before.streamId == 3 && !before.lossy) {
                eventsOn3++;
                expectTimes(before, base + 1000L, base + 1_000_000L, "source events were rewritten");
                expectTimes(after, base + 50L, base + 499_550L, "warped events");
                expectIndex(after, base + 50L, base + 499_550L, "warped event index");
                if (after.x != 7 || after.y != 8) {
                    throw new AssertionError("event address changed");
                }
            } else if (before.streamId == 3) {
                lossyOn3++;
                expectTimes(after, base + 499_550L, base + 499_550L, "warped bins");
                expectIndex(after, base + 499_550L, base + 499_550L, "warped bin index");
            } else if (before.streamId == 4) {
                expectTimes(after, base + 2050L, base + 2100L, "warped frame");
                expectIndex(after, base + 2050L, base + 2100L, "warped frame index");
                if (after.exposureUs != 100L || after.absentUs != 0L) {
                    throw new AssertionError("frame exposure " + after.exposureUs + " absent " + after.absentUs);
                }
            } else if (before.streamId == 5) {
                expectTimes(after, base + 3550L, base + 3550L, "warped imu");
                expectIndex(after, base + 3550L, base + 3550L, "warped imu index");
            } else if (before.streamId == 6) {
                expectTimes(after, base + 42L, base + 42L, "other camera");
                expectIndex(after, base + 42L, base + 42L, "other camera index");
            } else {
                throw new AssertionError("unexpected stream " + before.streamId);
            }
            if (before.streamId == 0 || before.streamId == 6) {
                expectTimes(before, after.firstUs, after.lastUs, "source was rewritten");
            }
        }
        if (eventsOn3 != 1 || lossyOn3 != 1) {
            throw new AssertionError("stream 3 packets events=" + eventsOn3 + " lossy=" + lossyOn3);
        }
    }

    private static void expectTimes(Seen seen, long first, long last, String label) {
        if (seen.firstUs != first || seen.lastUs != last) {
            throw new AssertionError(label + " times " + seen.firstUs + ".." + seen.lastUs
                    + " expected " + first + ".." + last);
        }
    }

    private static void expectIndex(Seen seen, long start, long end, String label) {
        if (seen.indexStart != start || seen.indexEnd != end) {
            throw new AssertionError(label + " " + seen.indexStart + ".." + seen.indexEnd
                    + " expected " + start + ".." + end);
        }
    }

    private static List<Seen> readPackets(File file) throws IOException {
        List<Seen> out = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel channel = raf.getChannel()) {
            ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
            readFully(channel, version);
            ByteBuffer headerBytes = readSizePrefixed(channel);
            IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
            int compression = Aedat4Compression.clamp(header.compression());
            long tablePos = header.dataTablePosition();
            long fileSize = channel.size();
            long dataEnd = (tablePos >= 0 && tablePos < fileSize) ? tablePos : fileSize;
            List<FtabMeta> meta = tryLoadSourceFtabMeta(channel, tablePos, fileSize, compression);
            ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            int packetIndex = 0;
            while (channel.position() + 8 <= dataEnd) {
                packetHeader.clear();
                readFully(channel, packetHeader);
                packetHeader.flip();
                int streamId = packetHeader.getInt();
                int payloadSize = packetHeader.getInt();
                if (payloadSize < 0 || channel.position() + (long) payloadSize > dataEnd) {
                    break;
                }
                byte[] compressed = new byte[payloadSize];
                readFully(channel, ByteBuffer.wrap(compressed));
                byte[] flat = Aedat4Compression.decompress(compressed, compression);
                Seen seen = inspect(streamId, flat);
                if (meta != null && packetIndex < meta.size()) {
                    seen.indexStart = meta.get(packetIndex).timestampStart;
                    seen.indexEnd = meta.get(packetIndex).timestampEnd;
                }
                out.add(seen);
                packetIndex++;
            }
        }
        return out;
    }

    private static Seen inspect(int streamId, byte[] flat) throws IOException {
        Seen seen = new Seen();
        seen.streamId = streamId;
        ByteBuffer bb = ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN);
        if (Aedat4LossyTimeBins.isLossyPayload(bb)) {
            Aedat4LossyTimeBins.Summary summary = Aedat4LossyTimeBins.summarize(bb);
            seen.lossy = true;
            seen.firstUs = summary.tMin;
            seen.lastUs = summary.tMax;
            return seen;
        }
        char c0 = (char) (flat[8] & 0xff);
        char c1 = (char) (flat[9] & 0xff);
        char c2 = (char) (flat[10] & 0xff);
        char c3 = (char) (flat[11] & 0xff);
        if (c0 == 'E' && c1 == 'V' && c2 == 'T' && c3 == 'S') {
            EventPacket packet = EventPacket.getSizePrefixedRootAsEventPacket(bb);
            int n = packet.elementsLength();
            seen.firstUs = packet.elements(0).timestamp();
            seen.lastUs = packet.elements(n - 1).timestamp();
            seen.x = packet.elements(0).x();
            seen.y = packet.elements(0).y();
        } else if (c0 == 'F' && c1 == 'R' && c2 == 'M' && c3 == 'E') {
            Frame frame = Frame.getSizePrefixedRootAsFrame(bb);
            seen.firstUs = frame.timestamp();
            seen.lastUs = frame.timestampEndOfFrame();
            seen.exposureUs = frame.exposure();
            seen.absentUs = frame.timestampStartOfExposure();
        } else if (c0 == 'I' && c1 == 'M' && c2 == 'U' && c3 == 'S') {
            IMUPacket packet = IMUPacket.getSizePrefixedRootAsIMUPacket(bb);
            seen.firstUs = packet.elements(0).timestamp();
            seen.lastUs = seen.firstUs;
        } else {
            throw new IOException("unread packet on stream " + streamId);
        }
        return seen;
    }

    private static final class Seen {
        int streamId;
        boolean lossy;
        long firstUs;
        long lastUs;
        long indexStart;
        long indexEnd;
        int x;
        int y;
        long exposureUs;
        long absentUs;
    }
}
