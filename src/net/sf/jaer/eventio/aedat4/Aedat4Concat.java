package net.sf.jaer.eventio.aedat4;

import com.google.flatbuffers.FlatBufferBuilder;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.swing.ProgressMonitor;
import net.sf.jaer.eventio.RecordingChipDetector;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventio.aedat4.dv.EventPacket;
import net.sf.jaer.eventio.aedat4.dv.FileDataDefinition;
import net.sf.jaer.eventio.aedat4.dv.FileDataTable;
import net.sf.jaer.eventio.aedat4.dv.Frame;
import net.sf.jaer.eventio.aedat4.dv.IMU;
import net.sf.jaer.eventio.aedat4.dv.IMUPacket;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;
import net.sf.jaer.graphics.RecordingVcrSession;
import net.sf.jaer.util.RecordingDiskSpace;

/**
 * Concatenate AEDAT-4 cassettes (or an explicit file list) into one file.
 * Copies packets in record order and does not delete sources. Cassette
 * boundaries that jumped because each file used a new Unix origin (VCR roll)
 * are stitched so playback time is continuous; gaps inside a cassette (e.g.
 * denoising) are left as-is. A stitched cassette is decompressed, shifted,
 * and recompressed on several cores; a cassette that needs no time shift is
 * copied as compressed bytes.
 */
public final class Aedat4Concat {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    public static final String PARTIAL_SUFFIX = ".partial";
    static final long DATA_TABLE_POSITION_PENDING = -2L;
    /** Trailer/index overhead: 1% of sources or 64 MiB, whichever is larger. */
    static final long OVERHEAD_FLOOR_BYTES = 64L << 20;
    /** Close a cassette-boundary jump larger than this (VCR double-counted baseUs). */
    static final long STITCH_GAP_US = 50_000L;
    /** Packets rewritten together so the stitch can use more than one core. */
    private static final int WAVE_PACKETS = 512;
    /** Compressed bytes per rewrite wave. Caps memory when packets are large. */
    private static final int WAVE_BYTES = 8 << 20;

    private Aedat4Concat() {
    }

    public static final class Result {
        public final File output;
        public final int packets;
        public final List<File> sources;

        Result(File output, int packets, List<File> sources) {
            this.output = output;
            this.packets = packets;
            this.sources = List.copyOf(sources);
        }
    }

    public static final class SpaceCheck {
        public final long sourceBytes;
        public final long estimateBytes;
        public final long requiredBytes;
        public final long usableBytes;
        public final boolean enough;

        SpaceCheck(long sourceBytes, long estimateBytes, long requiredBytes, long usableBytes) {
            this.sourceBytes = sourceBytes;
            this.estimateBytes = estimateBytes;
            this.requiredBytes = requiredBytes;
            this.usableBytes = usableBytes;
            this.enough = usableBytes >= requiredBytes;
        }
    }

    public static List<File> sourcesFromDeck(File deckDir) throws IOException {
        File dir = RecordingVcrSession.deckFolder(deckDir);
        if (dir == null) {
            throw new IOException("not a VCR deck: " + deckDir);
        }
        List<File> sources = RecordingVcrSession.cassetteFilesForConcat(dir);
        if (sources.isEmpty()) {
            throw new IOException("no cassette AEDAT-4 files in " + dir);
        }
        return sources;
    }

    public static File defaultOutput(File deckDir) {
        File dir = RecordingVcrSession.deckFolder(deckDir);
        return RecordingVcrSession.defaultConcatOutput(dir != null ? dir : deckDir);
    }

    public static SpaceCheck checkSpace(List<File> sources, File dest) {
        return checkSpace(sources, dest, RecordingDiskSpace.MIN_FREE_BYTES);
    }

    static SpaceCheck checkSpace(List<File> sources, File dest, long minFreeBytes) {
        long sum = 0L;
        if (sources != null) {
            for (File f : sources) {
                if (f != null && f.isFile()) {
                    sum += Math.max(0L, f.length());
                }
            }
        }
        long overhead = Math.max(sum / 100L, OVERHEAD_FLOOR_BYTES);
        long estimate = sum + overhead;
        long required = estimate + Math.max(0L, minFreeBytes);
        long usable = RecordingDiskSpace.usableBytes(dest);
        return new SpaceCheck(sum, estimate, required, usable);
    }

    public static Result merge(File deckDir, File outFile) throws IOException, InterruptedException {
        return merge(deckDir, outFile, null);
    }

    public static Result merge(File deckDir, File outFile, ProgressMonitor progress)
            throws IOException, InterruptedException {
        return mergeFiles(sourcesFromDeck(deckDir), outFile, progress);
    }

    public static Result mergeFiles(List<File> sources, File dest)
            throws IOException, InterruptedException {
        return mergeFiles(sources, dest, null);
    }

    public static Result mergeFiles(List<File> sources, File dest, ProgressMonitor progress)
            throws IOException, InterruptedException {
        if (sources == null || sources.isEmpty()) {
            throw new IOException("no source AEDAT-4 files");
        }
        for (File f : sources) {
            if (f == null || !f.isFile()) {
                throw new IOException("source AEDAT-4 missing: " + f);
            }
        }
        if (dest == null) {
            throw new IOException("destination is null");
        }
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create output folder: " + parent);
        }
        SpaceCheck space = checkSpace(sources, dest);
        if (!space.enough) {
            throw new IOException("not enough free space for concat: need "
                    + RecordingDiskSpace.formatBytes(space.requiredBytes)
                    + ", have " + RecordingDiskSpace.formatBytes(space.usableBytes)
                    + " on " + RecordingDiskSpace.directoryToProbe(dest));
        }
        File partial = new File(dest.getPath() + PARTIAL_SUFFIX);
        if (partial.exists() && !partial.delete()) {
            throw new IOException("cannot delete leftover partial: " + partial);
        }
        Thread hook = new Thread(() -> {
            if (partial.exists() && !partial.delete()) {
                log.warning("left concat partial after abort: " + partial);
            }
        }, "aedat4-concat-partial-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);
        long started = System.nanoTime();
        try {
            int packets = doMerge(sources, partial, progress);
            throwIfCanceled(progress);
            if (dest.exists() && !dest.delete()) {
                throw new IOException("cannot replace existing file: " + dest);
            }
            if (!partial.renameTo(dest)) {
                throw new IOException("cannot rename " + partial + " to " + dest);
            }
            log.info(String.format("AEDAT-4 concat %d files -> %s packets=%d in %.1fs",
                    sources.size(), dest.getName(), packets, (System.nanoTime() - started) / 1e9));
            return new Result(dest, packets, sources);
        } catch (Throwable t) {
            if (partial.exists() && !partial.delete()) {
                log.warning("left concat partial: " + partial);
            }
            if (t instanceof InterruptedException) {
                throw (InterruptedException) t;
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException("AEDAT-4 concat failed: " + t, t);
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignore) {
            }
        }
    }

    private static int doMerge(List<File> sources, File partial, ProgressMonitor progress)
            throws IOException, InterruptedException {
        Header first = readHeader(sources.get(0));
        RewritePool pool = new RewritePool();
        try (RandomAccessFile outRaf = new RandomAccessFile(partial, "rw");
                FileChannel out = outRaf.getChannel()) {
            PacketWriter writer = new PacketWriter(out);
            writer.write(Aedat4FileOutputStream.VERSION_LINE);
            long headerPosition = writer.position();
            byte[] outHeader = buildIOHeader(first.compression, DATA_TABLE_POSITION_PENDING, first.infoNode);
            writer.write(outHeader);
            List<DataDef> defs = new ArrayList<>();
            long[] lastEndUs = { 0L };
            int fileIndex = 0;
            for (File source : sources) {
                throwIfCanceled(progress);
                Header h = fileIndex == 0 ? first : readHeader(source);
                if (h.compression != first.compression) {
                    throw new IOException(source.getName() + " compression "
                            + Aedat4Compression.nameOf(h.compression) + " != "
                            + Aedat4Compression.nameOf(first.compression));
                }
                if (!sameFormat(first.infoNode, h.infoNode)) {
                    throw new IOException(source.getName()
                            + " chip/streams disagree with " + sources.get(0).getName());
                }
                if (progress != null) {
                    progress.setNote("Concat " + (fileIndex + 1) + "/" + sources.size()
                            + ": " + source.getName());
                }
                copyPackets(source, h, writer, defs, progress, fileIndex, sources.size(), lastEndUs, pool);
                fileIndex++;
            }
            throwIfCanceled(progress);
            if (progress != null) {
                progress.setNote("Writing FileDataTable");
                progress.setProgress(95);
            }
            long tablePosition = writer.position();
            byte[] ftab = Aedat4Compression.compress(buildFileDataTable(defs), first.compression);
            writer.write(ftab);
            writer.flush();
            byte[] patched = buildIOHeader(first.compression, tablePosition, first.infoNode);
            if (patched.length != outHeader.length) {
                throw new IOException(String.format(
                        "IOHeader size changed on concat close (%d -> %d)",
                        outHeader.length, patched.length));
            }
            long end = writer.position();
            out.position(headerPosition);
            out.write(ByteBuffer.wrap(patched));
            out.position(end);
            out.force(true);
            if (progress != null) {
                progress.setProgress(99);
            }
            return defs.size();
        } finally {
            pool.close();
        }
    }

    private static void copyPackets(File source, Header header, PacketWriter out, List<DataDef> defs,
            ProgressMonitor progress, int fileIndex, int fileCount, long[] lastEndUs, RewritePool pool)
            throws IOException, InterruptedException {
        try (RandomAccessFile inRaf = new RandomAccessFile(source, "r");
                FileChannel in = inRaf.getChannel()) {
            ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
            readFully(in, version);
            readSizePrefixed(in);
            long fileSize = in.size();
            long dataEnd = (header.dataTablePosition >= 0 && header.dataTablePosition < fileSize)
                    ? header.dataTablePosition : fileSize;
            List<FtabMeta> srcMeta = tryLoadSourceFtabMeta(in, header.dataTablePosition, fileSize,
                    header.compression);
            long delta = 0L;
            int shiftFrom = Integer.MAX_VALUE;
            boolean deltaResolved = fileIndex == 0 || lastEndUs[0] <= 0L;
            if (!deltaResolved && srcMeta != null) {
                for (int i = 0; i < srcMeta.size(); i++) {
                    FtabMeta m = srcMeta.get(i);
                    if (m.numElements > 0 || m.timestampStart != 0L || m.timestampEnd != 0L) {
                        long gap = m.timestampStart - lastEndUs[0];
                        if (gap > STITCH_GAP_US || gap < 0L) {
                            delta = lastEndUs[0] + 1L - m.timestampStart;
                            log.info(String.format(
                                    "VCR concat stitch %s: closed %.3fs gap at cassette boundary",
                                    source.getName(), gap / 1e6));
                        }
                        shiftFrom = i;
                        deltaResolved = true;
                        break;
                    }
                }
            }
            if (delta != 0L) {
                copyPacketsStitched(in, dataEnd, header, srcMeta, shiftFrom, delta, out, defs,
                        progress, fileIndex, fileCount, lastEndUs, pool);
                return;
            }
            ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            byte[] outHdr = new byte[8];
            int packetIndex = 0;
            final long dataStart = in.position();
            final long dataSpan = Math.max(1L, dataEnd - dataStart);
            final long[] meta = new long[3];
            while (in.position() + 8 <= dataEnd) {
                throwIfCanceled(progress);
                packetHeader.clear();
                readFully(in, packetHeader);
                packetHeader.flip();
                int streamId = packetHeader.getInt();
                int payloadSize = packetHeader.getInt();
                if (payloadSize < 0 || in.position() + payloadSize > dataEnd) {
                    break;
                }
                byte[] compressed = new byte[payloadSize];
                readFully(in, ByteBuffer.wrap(compressed));
                long numElements;
                long tStart;
                long tEnd;
                byte[] flat = null;
                if (srcMeta != null && packetIndex < srcMeta.size()
                        && srcMeta.get(packetIndex).streamId == streamId) {
                    FtabMeta m = srcMeta.get(packetIndex);
                    numElements = m.numElements;
                    tStart = m.timestampStart;
                    tEnd = m.timestampEnd;
                } else {
                    flat = Aedat4Compression.decompress(compressed, header.compression);
                    fillMetaFromFlat(flat, meta);
                    numElements = meta[0];
                    tStart = meta[1];
                    tEnd = meta[2];
                }
                if (!deltaResolved && (numElements > 0 || tStart != 0L || tEnd != 0L)) {
                    long gap = tStart - lastEndUs[0];
                    if (gap > STITCH_GAP_US || gap < 0L) {
                        delta = lastEndUs[0] + 1L - tStart;
                        log.info(String.format(
                                "VCR concat stitch %s: closed %.3fs gap at cassette boundary",
                                source.getName(), gap / 1e6));
                    }
                    deltaResolved = true;
                }
                byte[] toWrite = compressed;
                if (delta != 0L) {
                    if (flat == null) {
                        flat = Aedat4Compression.decompress(compressed, header.compression);
                    }
                    shiftFlatTimestamps(flat, delta);
                    tStart += delta;
                    tEnd += delta;
                    toWrite = Aedat4Compression.compress(flat, header.compression);
                }
                writePacket(out, outHdr, streamId, toWrite, defs, numElements, tStart, tEnd, lastEndUs);
                packetIndex++;
                if (progress != null && (packetIndex & 255) == 0) {
                    reportProgress(progress, fileIndex, fileCount, in.position() - dataStart, dataSpan);
                }
            }
        }
    }

    /**
     * Cassette-boundary stitch. Every packet from {@code shiftFrom} is
     * decompressed, shifted by the same {@code delta}, and recompressed.
     * Packets in a wave are rewritten on {@code pool}; the output order matches
     * the source.
     */
    private static void copyPacketsStitched(FileChannel in, long dataEnd, Header header, List<FtabMeta> srcMeta,
            int shiftFrom, long delta, PacketWriter out, List<DataDef> defs, ProgressMonitor progress,
            int fileIndex, int fileCount, long[] lastEndUs, RewritePool pool)
            throws IOException, InterruptedException {
        final long dataStart = in.position();
        final long dataSpan = Math.max(1L, dataEnd - dataStart);
        byte[] packetHeader = new byte[8];
        byte[] outHdr = new byte[8];
        int packetIndex = 0;
        List<InPacket> wave = new ArrayList<>(WAVE_PACKETS);
        int waveBytes = 0;
        while (in.position() + 8 <= dataEnd) {
            throwIfCanceled(progress);
            readFully(in, ByteBuffer.wrap(packetHeader));
            int streamId = getIntLE(packetHeader, 0);
            int payloadSize = getIntLE(packetHeader, 4);
            if (payloadSize < 0 || in.position() + payloadSize > dataEnd) {
                break;
            }
            byte[] compressed = new byte[payloadSize];
            readFully(in, ByteBuffer.wrap(compressed));
            InPacket packet = new InPacket();
            packet.streamId = streamId;
            packet.compressed = compressed;
            packet.shift = packetIndex >= shiftFrom;
            if (srcMeta != null && packetIndex < srcMeta.size()
                    && srcMeta.get(packetIndex).streamId == streamId) {
                FtabMeta m = srcMeta.get(packetIndex);
                packet.numElements = m.numElements;
                packet.tStart = m.timestampStart;
                packet.tEnd = m.timestampEnd;
                packet.metaKnown = true;
            }
            wave.add(packet);
            waveBytes += payloadSize;
            packetIndex++;
            if (wave.size() >= WAVE_PACKETS || waveBytes >= WAVE_BYTES) {
                flushWave(wave, header.compression, delta, pool, out, outHdr, defs, lastEndUs);
                waveBytes = 0;
                reportProgress(progress, fileIndex, fileCount, in.position() - dataStart, dataSpan);
            }
        }
        flushWave(wave, header.compression, delta, pool, out, outHdr, defs, lastEndUs);
    }

    private static void flushWave(List<InPacket> wave, int compression, long delta, RewritePool pool,
            PacketWriter out, byte[] outHdr, List<DataDef> defs, long[] lastEndUs)
            throws IOException, InterruptedException {
        if (wave.isEmpty()) {
            return;
        }
        rewriteWave(wave, compression, delta, pool);
        for (InPacket p : wave) {
            long tStart = p.tStart;
            long tEnd = p.tEnd;
            if (p.shift && delta != 0L) {
                tStart += delta;
                tEnd += delta;
            }
            writePacket(out, outHdr, p.streamId, p.rewritten, defs, p.numElements, tStart, tEnd, lastEndUs);
        }
        wave.clear();
    }

    private static void rewriteWave(List<InPacket> wave, int compression, long delta, RewritePool pool)
            throws IOException, InterruptedException {
        int n = wave.size();
        ExecutorService exec = pool.executor();
        int workers = exec == null ? 1 : Math.min(pool.threads, n);
        if (workers <= 1) {
            Aedat4Compression.Reusable codec = pool.codec();
            for (int i = 0; i < n; i++) {
                rewritePacket(wave.get(i), codec, compression, delta);
            }
            return;
        }
        int chunk = (n + workers - 1) / workers;
        List<Future<Void>> futures = new ArrayList<>(workers);
        for (int t = 0; t < workers; t++) {
            int from = t * chunk;
            int to = Math.min(n, from + chunk);
            if (from >= to) {
                break;
            }
            futures.add(exec.submit(() -> {
                try {
                    Aedat4Compression.Reusable codec = pool.codec();
                    for (int i = from; i < to; i++) {
                        rewritePacket(wave.get(i), codec, compression, delta);
                    }
                    return null;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));
        }
        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                Throwable c = e.getCause();
                if (c instanceof UncheckedIOException uio && uio.getCause() instanceof IOException io) {
                    throw io;
                }
                if (c instanceof IOException io) {
                    throw io;
                }
                if (c instanceof RuntimeException re) {
                    throw re;
                }
                throw new IOException("AEDAT-4 concat rewrite failed", c);
            }
        }
    }

    private static void rewritePacket(InPacket packet, Aedat4Compression.Reusable codec, int compression, long delta)
            throws IOException {
        byte[] flat = null;
        if (!packet.metaKnown) {
            flat = codec.decompress(packet.compressed, compression);
            long[] meta = new long[3];
            fillMetaFromFlat(flat, meta);
            packet.numElements = meta[0];
            packet.tStart = meta[1];
            packet.tEnd = meta[2];
        }
        if (!packet.shift || delta == 0L) {
            packet.rewritten = packet.compressed;
            return;
        }
        if (flat == null) {
            flat = codec.decompress(packet.compressed, compression);
        }
        shiftFlatTimestamps(flat, delta);
        packet.rewritten = codec.compress(flat, compression);
    }

    private static void writePacket(PacketWriter out, byte[] outHdr, int streamId, byte[] payload, List<DataDef> defs,
            long numElements, long tStart, long tEnd, long[] lastEndUs) throws IOException {
        long outPacketOffset = out.position() + 8L;
        putIntLE(outHdr, 0, streamId);
        putIntLE(outHdr, 4, payload.length);
        out.write(outHdr);
        out.write(payload);
        defs.add(new DataDef(outPacketOffset, streamId, payload.length, numElements, tStart, tEnd));
        if (tEnd > lastEndUs[0]) {
            lastEndUs[0] = tEnd;
        }
    }

    private static void reportProgress(ProgressMonitor progress, int fileIndex, int fileCount,
            long done, long span) {
        if (progress == null) {
            return;
        }
        int base = (fileIndex * 90) / Math.max(1, fileCount);
        int width = 90 / Math.max(1, fileCount);
        progress.setProgress((int) Math.min(90, base + (done * width) / Math.max(1L, span)));
    }

    private static int getIntLE(byte[] b, int off) {
        return (b[off] & 0xff)
                | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16)
                | ((b[off + 3] & 0xff) << 24);
    }

    private static void putIntLE(byte[] b, int off, int value) {
        b[off] = (byte) value;
        b[off + 1] = (byte) (value >>> 8);
        b[off + 2] = (byte) (value >>> 16);
        b[off + 3] = (byte) (value >>> 24);
    }

    static void shiftFlatTimestamps(byte[] flat, long deltaUs) {
        if (flat == null || flat.length < 12 || deltaUs == 0L) {
            return;
        }
        ByteBuffer bb = ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN);
        char c0 = (char) (bb.get(8) & 0xff);
        char c1 = (char) (bb.get(9) & 0xff);
        char c2 = (char) (bb.get(10) & 0xff);
        char c3 = (char) (bb.get(11) & 0xff);
        try {
            if (c0 == 'E' && c1 == 'V' && c2 == 'T' && c3 == 'S') {
                EventPacket p = EventPacket.getSizePrefixedRootAsEventPacket(bb);
                int n = p.elementsLength();
                int base = n > 0 ? p.elementsVectorStart() : -1;
                if (base >= 0) {
                    for (int i = 0; i < n; i++) {
                        int pos = base + (i << 4);
                        bb.putLong(pos, bb.getLong(pos) + deltaUs);
                    }
                }
            } else if (c0 == 'F' && c1 == 'R' && c2 == 'M' && c3 == 'E') {
                Frame.getSizePrefixedRootAsFrame(bb).addToTimestamps(deltaUs);
            } else if (c0 == 'I' && c1 == 'M' && c2 == 'U' && c3 == 'S') {
                IMUPacket p = IMUPacket.getSizePrefixedRootAsIMUPacket(bb);
                IMU imu = new IMU();
                int n = p.elementsLength();
                for (int i = 0; i < n; i++) {
                    p.elements(imu, i);
                    imu.addToTimestamp(deltaUs);
                }
            }
        } catch (RuntimeException ignore) {
        }
    }

    /** FTAB {@code [timestampStart, timestampEnd]} per packet; demo/tests. */
    static List<long[]> packetTimeRanges(File file) throws IOException {
        Header h = readHeader(file);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                FileChannel in = raf.getChannel()) {
            ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
            readFully(in, version);
            readSizePrefixed(in);
            List<FtabMeta> meta = tryLoadSourceFtabMeta(in, h.dataTablePosition, in.size(), h.compression);
            if (meta == null || meta.isEmpty()) {
                throw new IOException("no FileDataTable in " + file);
            }
            List<long[]> ranges = new ArrayList<>(meta.size());
            for (FtabMeta m : meta) {
                ranges.add(new long[] { m.timestampStart, m.timestampEnd });
            }
            return ranges;
        }
    }

    private static boolean sameFormat(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.equals(b)) {
            return true;
        }
        List<RecordingChipDetector.StreamHint> sa = RecordingChipDetector.streamsFromInfoNodeXml(a);
        List<RecordingChipDetector.StreamHint> sb = RecordingChipDetector.streamsFromInfoNodeXml(b);
        if (sa.size() != sb.size()) {
            return false;
        }
        for (int i = 0; i < sa.size(); i++) {
            RecordingChipDetector.StreamHint x = sa.get(i);
            RecordingChipDetector.StreamHint y = sb.get(i);
            if (x.streamId != y.streamId
                    || !String.valueOf(x.typeIdentifier).equals(String.valueOf(y.typeIdentifier))
                    || !String.valueOf(x.source).equals(String.valueOf(y.source))
                    || !java.util.Objects.equals(x.sizeX, y.sizeX)
                    || !java.util.Objects.equals(x.sizeY, y.sizeY)) {
                return false;
            }
        }
        return true;
    }

    private static Header readHeader(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                FileChannel in = raf.getChannel()) {
            ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
            readFully(in, version);
            if (!Arrays.equals(version.array(), Aedat4FileOutputStream.VERSION_LINE)) {
                throw new IOException(file + " is not an AEDAT-4 file");
            }
            ByteBuffer headerBytes = readSizePrefixed(in);
            IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
            int compression = Aedat4Compression.clamp(header.compression());
            String infoNode = header.infoNode();
            if (infoNode == null) {
                infoNode = "";
            }
            return new Header(compression, header.dataTablePosition(), infoNode);
        }
    }

    private static void fillMetaFromFlat(byte[] flat, long[] out) {
        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        if (flat == null || flat.length < 12) {
            return;
        }
        ByteBuffer bb = ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN);
        char c0 = (char) (bb.get(8) & 0xff);
        char c1 = (char) (bb.get(9) & 0xff);
        char c2 = (char) (bb.get(10) & 0xff);
        char c3 = (char) (bb.get(11) & 0xff);
        try {
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
        } catch (RuntimeException ignore) {
        }
    }

    private static List<FtabMeta> tryLoadSourceFtabMeta(FileChannel in, long dataTablePosition,
            long fileSize, int compression) {
        if (dataTablePosition < 0 || dataTablePosition >= fileSize) {
            return null;
        }
        long remaining = fileSize - dataTablePosition;
        if (remaining < 8 || remaining > Aedat4FileInputStream.MAX_FILE_DATA_TABLE_BYTES) {
            return null;
        }
        long saved;
        try {
            saved = in.position();
        } catch (IOException e) {
            return null;
        }
        try {
            in.position(dataTablePosition);
            ByteBuffer raw = ByteBuffer.allocate((int) remaining).order(ByteOrder.LITTLE_ENDIAN);
            readFully(in, raw);
            raw.flip();
            ByteBuffer tableBytes;
            if (compression == CompressionType.NONE || looksLikeFtab(raw)) {
                tableBytes = raw;
            } else {
                byte[] compressed = new byte[raw.remaining()];
                raw.get(compressed);
                byte[] flat = Aedat4Compression.decompress(compressed, compression);
                tableBytes = ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN);
            }
            if (!looksLikeFtab(tableBytes)) {
                return null;
            }
            FileDataTable table = FileDataTable.getSizePrefixedRootAsFileDataTable(tableBytes);
            int n = table.tableLength();
            if (n <= 0 || n > 10_000_000) {
                return null;
            }
            List<FtabMeta> list = new ArrayList<>(n);
            FileDataDefinition def = new FileDataDefinition();
            for (int i = 0; i < n; i++) {
                FileDataDefinition d = table.table(def, i);
                if (d == null) {
                    return null;
                }
                list.add(new FtabMeta(d.packetInfoStreamID(), d.numElements(),
                        d.timestampStart(), d.timestampEnd()));
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

    private static boolean looksLikeFtab(ByteBuffer payload) {
        int p = payload.position();
        int n = payload.remaining();
        if (n >= 12) {
            return payload.get(p + 8) == 'F' && payload.get(p + 9) == 'T'
                    && payload.get(p + 10) == 'A' && payload.get(p + 11) == 'B';
        }
        return false;
    }

    private static byte[] buildIOHeader(int compression, long dataTablePosition, String infoNode) {
        FlatBufferBuilder builder = new FlatBufferBuilder(Math.max(1024, infoNode.length() + 64));
        int info = builder.createString(infoNode);
        int root = IOHeader.createIOHeader(builder, compression, dataTablePosition, info);
        builder.finishSizePrefixed(root, "IOHE");
        return builder.sizedByteArray();
    }

    private static byte[] buildFileDataTable(List<DataDef> defs) {
        FlatBufferBuilder builder = new FlatBufferBuilder(Math.max(1024, defs.size() * 64));
        int[] offsets = new int[defs.size()];
        for (int i = 0; i < defs.size(); i++) {
            DataDef d = defs.get(i);
            offsets[i] = FileDataDefinition.createFileDataDefinition(builder, d.byteOffset, d.streamId, d.size,
                    d.numElements, d.timestampStart, d.timestampEnd);
        }
        int vector = FileDataTable.createTableVector(builder, offsets);
        int root = FileDataTable.createFileDataTable(builder, vector);
        builder.finishSizePrefixed(root, "FTAB");
        return builder.sizedByteArray();
    }

    private static void throwIfCanceled(ProgressMonitor progressMonitor) throws InterruptedException {
        if (progressMonitor != null && progressMonitor.isCanceled()) {
            throw new InterruptedException("AEDAT-4 concat canceled");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("AEDAT-4 concat interrupted");
        }
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
                throw new IOException("Unexpected EOF");
            }
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length < 1 || "-h".equals(args[0]) || "--help".equals(args[0])) {
            System.err.println("Usage: Aedat4Concat <deckDir> [outFile]");
            System.exit(2);
            return;
        }
        File deck = new File(args[0]);
        File out = args.length > 1 ? new File(args[1]) : defaultOutput(deck);
        try {
            Result r = merge(deck, out);
            System.out.println("Wrote " + r.output.getAbsolutePath()
                    + " packets=" + r.packets + " sources=" + r.sources.size());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private static final class RewritePool implements AutoCloseable {
        final int threads;
        private ExecutorService executor;
        private final ConcurrentLinkedQueue<Aedat4Compression.Reusable> codecs = new ConcurrentLinkedQueue<>();
        private final ThreadLocal<Aedat4Compression.Reusable> local = ThreadLocal.withInitial(() -> {
            Aedat4Compression.Reusable codec = new Aedat4Compression.Reusable();
            codecs.add(codec);
            return codec;
        });

        RewritePool() {
            int n = Runtime.getRuntime().availableProcessors();
            threads = n <= 1 ? 1 : Math.min(n, 16);
        }

        ExecutorService executor() {
            if (threads <= 1) {
                return null;
            }
            if (executor == null) {
                log.info("AEDAT-4 concat timestamp stitch uses " + threads + " threads");
                executor = Executors.newFixedThreadPool(threads, r -> {
                    Thread t = new Thread(r, "aedat4-concat");
                    t.setDaemon(true);
                    return t;
                });
            }
            return executor;
        }

        Aedat4Compression.Reusable codec() {
            return local.get();
        }

        @Override
        public void close() {
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                        executor.awaitTermination(30, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            Aedat4Compression.Reusable codec;
            while ((codec = codecs.poll()) != null) {
                codec.close();
            }
        }
    }

    /** Buffered writes. Packet offsets use {@link #position()}, not the channel. */
    private static final class PacketWriter {
        private final FileChannel out;
        private final ByteBuffer buf = ByteBuffer.allocate(1 << 20);
        private long pos;

        PacketWriter(FileChannel out) throws IOException {
            this.out = out;
            this.pos = out.position();
        }

        long position() {
            return pos;
        }

        void write(byte[] b) throws IOException {
            int off = 0;
            int n = b.length;
            while (n > 0) {
                if (!buf.hasRemaining()) {
                    flush();
                }
                int take = Math.min(buf.remaining(), n);
                buf.put(b, off, take);
                off += take;
                n -= take;
                pos += take;
            }
        }

        void flush() throws IOException {
            buf.flip();
            while (buf.hasRemaining()) {
                out.write(buf);
            }
            buf.clear();
        }
    }

    private static final class InPacket {
        int streamId;
        byte[] compressed;
        byte[] rewritten;
        long numElements;
        long tStart;
        long tEnd;
        boolean shift;
        boolean metaKnown;
    }

    private static final class Header {
        final int compression;
        final long dataTablePosition;
        final String infoNode;

        Header(int compression, long dataTablePosition, String infoNode) {
            this.compression = compression;
            this.dataTablePosition = dataTablePosition;
            this.infoNode = infoNode;
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
}
