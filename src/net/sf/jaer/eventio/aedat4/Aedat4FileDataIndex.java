package net.sf.jaer.eventio.aedat4;

import com.google.flatbuffers.FlatBufferBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventio.aedat4.dv.FileDataDefinition;
import net.sf.jaer.eventio.aedat4.dv.FileDataTable;

/**
 * AEDAT-4 packet index at the end of the file. A table that fits in one
 * FlatBuffer is a single compressed {@code FTAB} (DV layout). A larger table
 * is several of those chunks plus a small uncompressed {@code FTCH} directory
 * at {@code IOHeader.dataTablePosition}. Java cannot hold one FlatBuffer past
 * 2 GiB, so the directory exists so a long merge can still be opened.
 */
public final class Aedat4FileDataIndex {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /**
     * Rows in one {@code FTAB} chunk. Eight million rows is about 400 MiB, under
     * the FlatBuffer 2 GiB ceiling. Tests may lower {@link #rowsPerChunk}.
     */
    public static final int MAX_ROWS_PER_CHUNK = 8_000_000;
    /** Uncompressed directory magic. Chunks after it are ordinary compressed FTABs. */
    public static final byte[] CHUNK_MAGIC = {'F', 'T', 'C', 'H'};
    /** Directory entries. A corrupt count above this is not a chunk index. */
    private static final int MAX_CHUNKS = 8_192;
    /**
     * Production chunk size. Demos set a smaller value and restore it.
     */
    static int rowsPerChunk = MAX_ROWS_PER_CHUNK;

    private Aedat4FileDataIndex() {
    }

    /** Random access to index rows without copying them into another list. */
    public interface Rows {
        int size();

        long byteOffset(int index);

        int streamId(int index);

        int payloadSize(int index);

        long numElements(int index);

        long timestampStart(int index);

        long timestampEnd(int index);
    }

    /** One decompressed, size-prefixed {@code FTAB}. Called once per chunk, in order. */
    public interface TableConsumer {
        void accept(ByteBuffer sizePrefixedLittleEndian) throws IOException;

        /** {@code index} is 0-based. A single-table file does not call this. */
        default void beginChunk(int index, int count) {
        }
    }

    /**
     * Append the index at the channel position (end of packets).
     *
     * @return file offset to store in {@code IOHeader.dataTablePosition}
     */
    public static long write(FileChannel channel, int compression, Rows rows) throws IOException {
        if (channel == null || rows == null) {
            throw new IOException("AEDAT-4 index write missing channel or rows");
        }
        int chunkRows = rowsPerChunk < 1 ? MAX_ROWS_PER_CHUNK : rowsPerChunk;
        int n = rows.size();
        long tablePosition = channel.position();
        if (n <= chunkRows) {
            writeFully(channel, Aedat4Compression.compress(buildChunk(rows, 0, n), compression));
            return tablePosition;
        }
        int chunks = (n + chunkRows - 1) / chunkRows;
        int dirSize = 8 + chunks * 12;
        writeFully(channel, new byte[dirSize]);
        long[] offset = new long[chunks];
        int[] length = new int[chunks];
        for (int c = 0, base = 0; c < chunks; c++) {
            int count = Math.min(chunkRows, n - base);
            offset[c] = channel.position();
            byte[] stored = Aedat4Compression.compress(buildChunk(rows, base, count), compression);
            length[c] = stored.length;
            writeFully(channel, stored);
            base += count;
        }
        ByteBuffer dir = ByteBuffer.allocate(dirSize).order(ByteOrder.LITTLE_ENDIAN);
        dir.put(CHUNK_MAGIC);
        dir.putInt(chunks);
        for (int c = 0; c < chunks; c++) {
            dir.putLong(offset[c]);
            dir.putInt(length[c]);
        }
        dir.flip();
        long end = channel.position();
        channel.position(tablePosition);
        while (dir.hasRemaining()) {
            channel.write(dir);
        }
        channel.position(end);
        log.info(String.format("AEDAT-4 FileDataTable split into %d chunks (%,d rows)", chunks, n));
        return tablePosition;
    }

    /**
     * Deliver every {@code FTAB} in order. A single-table file calls the consumer
     * once. Returns false when the trailer is missing or not an index; the caller
     * scans packets. A chunk that fails after an earlier chunk was delivered throws,
     * so the caller must drop a partial index.
     */
    public static boolean forEachTable(FileChannel channel, long dataTablePosition, long fileSize,
            int compression, TableConsumer consumer) throws IOException {
        if (channel == null || consumer == null || dataTablePosition < 0 || dataTablePosition >= fileSize) {
            return false;
        }
        long remaining = fileSize - dataTablePosition;
        if (remaining < 8) {
            return false;
        }
        ByteBuffer magic = ByteBuffer.allocate(4);
        channel.position(dataTablePosition);
        readFully(channel, magic);
        magic.flip();
        if (magic.get(0) == CHUNK_MAGIC[0] && magic.get(1) == CHUNK_MAGIC[1]
                && magic.get(2) == CHUNK_MAGIC[2] && magic.get(3) == CHUNK_MAGIC[3]) {
            return readChunks(channel, dataTablePosition, fileSize, compression, consumer);
        }
        if (remaining > Aedat4FileInputStream.MAX_FILE_DATA_TABLE_BYTES) {
            log.warning("AEDAT-4 FileDataTable remaining bytes implausible (" + remaining + ")");
            return false;
        }
        channel.position(dataTablePosition);
        ByteBuffer raw = ByteBuffer.allocate((int) remaining).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, raw);
        raw.flip();
        ByteBuffer table = decodeTable(raw, compression);
        if (table == null) {
            return false;
        }
        consumer.accept(table);
        return true;
    }

    private static boolean readChunks(FileChannel channel, long dataTablePosition, long fileSize,
            int compression, TableConsumer consumer) throws IOException {
        ByteBuffer countBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        channel.position(dataTablePosition + 4);
        readFully(channel, countBuf);
        countBuf.flip();
        int count = countBuf.getInt();
        if (count <= 0 || count > MAX_CHUNKS) {
            log.warning("AEDAT-4 FTCH chunk count " + count + " unusable");
            return false;
        }
        int dirSize = 8 + count * 12;
        if (dataTablePosition + (long) dirSize > fileSize) {
            log.warning("AEDAT-4 FTCH directory overruns the file");
            return false;
        }
        ByteBuffer dir = ByteBuffer.allocate(dirSize).order(ByteOrder.LITTLE_ENDIAN);
        channel.position(dataTablePosition);
        readFully(channel, dir);
        dir.flip();
        dir.position(8);
        long dirEnd = dataTablePosition + dirSize;
        long[] offset = new long[count];
        int[] length = new int[count];
        for (int i = 0; i < count; i++) {
            offset[i] = dir.getLong();
            length[i] = dir.getInt();
            if (length[i] <= 0 || length[i] > Aedat4FileInputStream.MAX_FILE_DATA_TABLE_BYTES
                    || offset[i] < dirEnd || offset[i] + (long) length[i] > fileSize) {
                log.warning("AEDAT-4 FTCH chunk " + i + " offset=" + offset[i] + " length=" + length[i]);
                return false;
            }
        }
        log.info("AEDAT-4 FileDataTable is " + count + " chunks");
        for (int i = 0; i < count; i++) {
            consumer.beginChunk(i, count);
            ByteBuffer raw = ByteBuffer.allocate(length[i]).order(ByteOrder.LITTLE_ENDIAN);
            channel.position(offset[i]);
            readFully(channel, raw);
            raw.flip();
            ByteBuffer table = decodeTable(raw, compression);
            if (table == null) {
                throw new IOException("AEDAT-4 FTCH chunk " + i + " is not a FileDataTable");
            }
            consumer.accept(table);
        }
        return true;
    }

    private static ByteBuffer decodeTable(ByteBuffer raw, int compression) {
        try {
            if (compression == CompressionType.NONE || looksLikeFtab(raw)) {
                return raw;
            }
            byte[] compressed = new byte[raw.remaining()];
            raw.get(compressed);
            byte[] flat = Aedat4Compression.decompress(compressed, compression);
            ByteBuffer table = ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN);
            if (!looksLikeFtab(table)) {
                log.warning("AEDAT-4 FileDataTable after decompress is not FTAB");
                return null;
            }
            return table;
        } catch (IOException e) {
            log.warning("AEDAT-4 FileDataTable decompress failed: " + e.getMessage());
            return null;
        }
    }

    private static byte[] buildChunk(Rows rows, int from, int count) {
        int cap = builderCapacity(count);
        FlatBufferBuilder builder = new FlatBufferBuilder(cap);
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            int row = from + i;
            offsets[i] = FileDataDefinition.createFileDataDefinition(builder, rows.byteOffset(row),
                    rows.streamId(row), rows.payloadSize(row), rows.numElements(row),
                    rows.timestampStart(row), rows.timestampEnd(row));
        }
        int vector = FileDataTable.createTableVector(builder, offsets);
        int root = FileDataTable.createFileDataTable(builder, vector);
        builder.finishSizePrefixed(root, "FTAB");
        return builder.sizedByteArray();
    }

    /** {@code rows * 64} as a positive int. The chunk cap keeps this under 2 GiB. */
    private static int builderCapacity(int rows) {
        long hint = 1024L + (long) Math.max(0, rows) * 64L;
        if (hint > Integer.MAX_VALUE - 8L) {
            return Integer.MAX_VALUE - 8;
        }
        return (int) Math.max(1024L, hint);
    }

    static boolean looksLikeFtab(ByteBuffer payload) {
        int p = payload.position();
        int n = payload.remaining();
        if (n >= 12) {
            return payload.get(p + 8) == 'F' && payload.get(p + 9) == 'T'
                    && payload.get(p + 10) == 'A' && payload.get(p + 11) == 'B';
        }
        return false;
    }

    private static void writeFully(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new IOException("Unexpected EOF");
            }
        }
    }
}
