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
import java.util.List;
import java.util.logging.Logger;
import javax.swing.ProgressMonitor;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventio.aedat4.dv.EventPacket;
import net.sf.jaer.eventio.aedat4.dv.FileDataDefinition;
import net.sf.jaer.eventio.aedat4.dv.FileDataTable;
import net.sf.jaer.eventio.aedat4.dv.Frame;
import net.sf.jaer.eventio.aedat4.dv.IMUPacket;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;

/**
 * Rewrites an AEDAT-4 file that uses DV dependent-block LZ4 into a sibling file
 * with jAER independent-block LZ4 (fast {@code lz4-java} playback path).
 * <p>
 * Packet-level recompress: preserves stream IDs, infoNode, and timestamps.
 * Does not decode through chip extractors or {@link Aedat4FileOutputStream#writeBundle}.
 */
public final class Aedat4Lz4Rerecorder {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** Suffix before {@code .aedat4}: {@code foo.aedat4} → {@code foo-rerecord.aedat4}. */
    public static final String RERECORD_INFIX = "-rerecord";
    private static final String PARTIAL_SUFFIX = ".partial";
    private static final long DATA_TABLE_POSITION_PENDING = -2L;

    /**
     * Playback open plan after the dependent-LZ4 dialog.
     * {@code rerecordFrom == null} means open {@code fileToOpen} as-is;
     * otherwise recompress {@code rerecordFrom} into {@code fileToOpen} first.
     */
    public static final class OpenPlan {
        public final File fileToOpen;
        public final File rerecordFrom;

        public OpenPlan(File fileToOpen, File rerecordFrom) {
            this.fileToOpen = fileToOpen;
            this.rerecordFrom = rerecordFrom;
        }
    }

    private Aedat4Lz4Rerecorder() {
    }

    /** True when the file name already ends with {@code -rerecord.aedat4}. */
    public static boolean isRerecordFile(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(RERECORD_INFIX + ".aedat4");
    }

    /**
     * Sibling path {@code basename-rerecord.aedat4} next to {@code source}.
     */
    public static File rerecordSibling(File source) {
        if (source == null) {
            return null;
        }
        String name = source.getName();
        String base;
        String lower = name.toLowerCase();
        if (lower.endsWith(".aedat4")) {
            base = name.substring(0, name.length() - ".aedat4".length());
        } else {
            base = name;
        }
        return new File(source.getParentFile(), base + RERECORD_INFIX + ".aedat4");
    }

    /**
     * Recompress {@code source} to {@code dest} (independent-block LZ4).
     * Writes via {@code dest.getName() + .partial} then renames.
     *
     * @return the finished {@code dest} file
     */
    public static File rerecord(File source, File dest, ProgressMonitor progressMonitor)
            throws IOException, InterruptedException {
        if (source == null || !source.isFile()) {
            throw new IOException("Source AEDAT-4 missing: " + source);
        }
        if (dest == null) {
            throw new IOException("Destination is null");
        }
        File parent = dest.getParentFile();
        if (parent != null && !parent.canWrite()) {
            throw new IOException("Directory not writable: " + parent);
        }
        File partial = new File(dest.getPath() + PARTIAL_SUFFIX);
        if (partial.exists() && !partial.delete()) {
            throw new IOException("Cannot delete leftover partial: " + partial);
        }
        long t0 = System.currentTimeMillis();
        try {
            doRerecord(source, partial, progressMonitor);
            throwIfCanceled(progressMonitor);
            if (dest.exists() && !dest.delete()) {
                throw new IOException("Cannot replace existing file: " + dest);
            }
            if (!partial.renameTo(dest)) {
                throw new IOException("Cannot rename " + partial + " to " + dest);
            }
            log.info(String.format(
                    "AEDAT-4 LZ4 re-record %s -> %s in %d ms (src %,d bytes, dest %,d bytes)",
                    source.getName(), dest.getName(), System.currentTimeMillis() - t0,
                    source.length(), dest.length()));
            return dest;
        } catch (Throwable t) {
            if (partial.exists() && !partial.delete()) {
                log.warning("Left partial re-record file: " + partial);
            }
            if (t instanceof InterruptedException) {
                throw (InterruptedException) t;
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException("AEDAT-4 re-record failed: " + t, t);
        }
    }

    private static void doRerecord(File source, File partial, ProgressMonitor progressMonitor)
            throws IOException, InterruptedException {
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
            if (compression != CompressionType.LZ4 && compression != CompressionType.LZ4_HIGH) {
                throw new IOException("Re-record only supports LZ4/LZ4_HIGH (got "
                        + Aedat4Compression.nameOf(compression) + ")");
            }
            String infoNode = header.infoNode();
            if (infoNode == null) {
                infoNode = "";
            }
            long srcTablePos = header.dataTablePosition();
            long fileSize = in.size();
            long dataEnd = (srcTablePos >= 0 && srcTablePos < fileSize) ? srcTablePos : fileSize;

            // Optional source FTAB for numElements / timestamps (packet order).
            List<FtabMeta> srcMeta = tryLoadSourceFtabMeta(in, srcTablePos, fileSize, compression);

            out.write(ByteBuffer.wrap(Aedat4FileOutputStream.VERSION_LINE));
            long headerPosition = out.position();
            byte[] outHeader = buildIOHeader(compression, DATA_TABLE_POSITION_PENDING, infoNode);
            out.write(ByteBuffer.wrap(outHeader));

            List<DataDef> defs = new ArrayList<>();
            ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            int packetIndex = 0;
            final long dataStart = in.position();
            final long dataSpan = Math.max(1L, dataEnd - dataStart);
            final long[] meta = new long[3];

            if (progressMonitor != null) {
                progressMonitor.setNote("Re-recording LZ4 (independent blocks): " + source.getName());
            }

            while (in.position() + 8 <= dataEnd) {
                throwIfCanceled(progressMonitor);
                packetHeader.clear();
                readFully(in, packetHeader);
                packetHeader.flip();
                int streamId = packetHeader.getInt();
                int payloadSize = packetHeader.getInt();
                if (payloadSize < 0 || in.position() + payloadSize > dataEnd) {
                    break;
                }
                byte[] compressed = new byte[payloadSize];
                ByteBuffer payloadBuf = ByteBuffer.wrap(compressed);
                readFully(in, payloadBuf);

                byte[] flat = Aedat4Compression.decompress(compressed, compression);
                byte[] recompressed = Aedat4Compression.compress(flat, compression);

                long numElements;
                long tStart;
                long tEnd;
                if (srcMeta != null && packetIndex < srcMeta.size()
                        && srcMeta.get(packetIndex).streamId == streamId) {
                    FtabMeta m = srcMeta.get(packetIndex);
                    numElements = m.numElements;
                    tStart = m.timestampStart;
                    tEnd = m.timestampEnd;
                } else {
                    fillMetaFromFlat(flat, meta);
                    numElements = meta[0];
                    tStart = meta[1];
                    tEnd = meta[2];
                }

                long outPacketOffset = out.position() + 8L;
                ByteBuffer outHdr = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                outHdr.putInt(streamId);
                outHdr.putInt(recompressed.length);
                outHdr.flip();
                out.write(outHdr);
                out.write(ByteBuffer.wrap(recompressed));
                defs.add(new DataDef(outPacketOffset, streamId, recompressed.length, numElements, tStart, tEnd));
                packetIndex++;

                // ProgressMonitor updates are surprisingly expensive at high packet rates.
                if (progressMonitor != null && (packetIndex & 31) == 0) {
                    progressMonitor.setProgress((int) Math.min(90,
                            ((in.position() - dataStart) * 90L) / dataSpan));
                }
            }

            throwIfCanceled(progressMonitor);
            if (progressMonitor != null) {
                progressMonitor.setNote("Writing FileDataTable");
                progressMonitor.setProgress(92);
            }

            long tablePosition = out.position();
            byte[] ftab = Aedat4Compression.compress(buildFileDataTable(defs), compression);
            out.write(ByteBuffer.wrap(ftab));
            byte[] patched = buildIOHeader(compression, tablePosition, infoNode);
            if (patched.length != outHeader.length) {
                throw new IOException(String.format(
                        "IOHeader size changed on re-record close (%d -> %d); infoNode length unstable",
                        outHeader.length, patched.length));
            }
            long end = out.position();
            out.position(headerPosition);
            out.write(ByteBuffer.wrap(patched));
            out.position(end);
            out.force(true);
            if (progressMonitor != null) {
                progressMonitor.setProgress(99);
            }
            log.info(String.format("AEDAT-4 re-record wrote %d packets, FTAB at %d", defs.size(), tablePosition));
        }
    }

    /** Fills {@code out} as {@code [numElements, tStart, tEnd]}. */
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
            // leave zeros
        }
    }

    private static List<FtabMeta> tryLoadSourceFtabMeta(FileChannel in, long dataTablePosition,
            long fileSize, int compression) {
        if (dataTablePosition < 0 || dataTablePosition >= fileSize) {
            return null;
        }
        long remaining = fileSize - dataTablePosition;
        if (remaining < 8 || remaining > 512L * 1024 * 1024) {
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
            throw new InterruptedException("AEDAT-4 re-record canceled");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("AEDAT-4 re-record interrupted");
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
}
