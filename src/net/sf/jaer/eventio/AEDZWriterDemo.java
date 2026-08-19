package net.sf.jaer.eventio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.zip.CRC32;

import com.github.luben.zstd.Zstd;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;

/**
 * Headless production-path self-test for the AEDZ <em>writer</em> (plan Todo 4,
 * commit A).
 *
 * <p>It drives the production {@link AEDZOutputStream} through the exact event
 * counts the format must honour (0, 1, exactly one chunk, one-plus-partial
 * chunk) and verifies the closed file's own structure directly — reading, by
 * hand, the header patch, the per-chunk layout, the chunk index, the summary
 * block, the footer and the CRC — so a writer defect is caught without leaning
 * on the companion reader. It decompresses each chunk's byte-transposed planes
 * and reverses the delta-encoding to prove the exact written addresses and
 * timestamps match the input packet. It also asserts deterministic normalized
 * output (two identical writes yield byte-identical chunk/index/footer regions),
 * that {@code close()} is idempotent, and that writing after close throws a
 * controlled {@link IOException}.
 *
 * <p>Each check throws on mismatch (non-zero exit) and prints PASS lines. Run
 * headlessly after {@code ant clean compile}:
 * {@code java -cp build/classes:lib/*:jars/* net.sf.jaer.eventio.AEDZWriterDemo}
 */
public class AEDZWriterDemo {

    static final int CHUNK_EVENTS = 65536;

    public static void main(String[] args) throws Exception {
        for (int n : new int[]{0, 1, CHUNK_EVENTS, CHUNK_EVENTS + 1}) {
            writerCount(n);
        }
        partialChunk();
        deterministicOutput();
        closeIdempotenceAndWriteAfterClose();
        explicitSnapshotMetadata();
        System.out.println("ALL AEDZ WRITER TESTS PASS");
    }

    // ------------------------------------------------------------------
    // count cases + structural verification
    // ------------------------------------------------------------------

    /** Write n events, close, and verify header patch / chunks / index / summary / footer / CRC by hand. */
    private static void writerCount(int n) throws Exception {
        AEPacketRaw packet = makePacket(n, 7);
        File file = tempFile();
        AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), null);
        try {
            out.writePacket(packet);
        } finally {
            out.close();
        }
        assertTrue(out.getNumEvents() == n, "getNumEvents()==" + n);
        awaitClosed();

        byte[] b = readAll(file);
        int expectedChunks = n == 0 ? 0 : ((n - 1) / CHUNK_EVENTS + 1);

        // Header patch.
        assertTrue(leLong(b, 8) == n, "header n_events patched to " + n + " (got " + leLong(b, 8) + ")");
        assertTrue(leInt(b, 8 + 8) == expectedChunks, "header n_chunks patched to " + expectedChunks);

        // Walk chunks, decompress, and verify events reconstructed exactly.
        int idx = 0;
        long pos = firstChunkOffset(b);
        int[] addr = packet.getAddresses();
        int[] ts = packet.getTimestamps();
        for (int c = 0; c < expectedChunks; c++) {
            long chunkStart = pos;
            int chunkN = leInt(b, (int) chunkStart);
            assertTrue(chunkN > 0, "chunk " + c + " has " + chunkN + " events");
            int chunkDataSize = leInt(b, (int) chunkStart + 4);
            int[] planeSizes = new int[8];
            int sum = 0;
            for (int p = 0; p < 8; p++) {
                planeSizes[p] = leInt(b, (int) chunkStart + 8 + p * 4);
                sum += planeSizes[p];
            }
            assertTrue(sum + 8 * 4 == chunkDataSize, "chunk " + c + " plane sizes sum + 32 == chunkDataSize");
            checkPluginEOF(b, (int) chunkStart, chunkDataSize);

            // Decompress each plane and reconstruct the events.
            byte[][] planes = new byte[8][];
            int dataOff = (int) chunkStart + 8 + 8 * 4;
            for (int p = 0; p < 8; p++) {
                byte[] compressed = java.util.Arrays.copyOfRange(b, dataOff, dataOff + planeSizes[p]);
                byte[] decompressed = new byte[chunkN];
                long nDst = Zstd.decompress(decompressed, compressed);
                assertTrue(nDst == chunkN, "chunk " + c + " plane " + p + " decompresses to " + chunkN);
                planes[p] = decompressed;
                dataOff += planeSizes[p];
            }
            // Un-transpose addresses (little-endian byte planes).
            for (int i = 0; i < chunkN; i++) {
                int a = (planes[0][i] & 0xFF)
                        | ((planes[1][i] & 0xFF) << 8)
                        | ((planes[2][i] & 0xFF) << 16)
                        | ((planes[3][i] & 0xFF) << 24);
                int g = idx + i;
                assertTrue(a == addr[g], "chunk " + c + " addr mismatch at global " + g);
            }
            // Un-transpose delta-ts and reverse the delta encoding.
            int prev = 0;
            for (int i = 0; i < chunkN; i++) {
                int d = (planes[4][i] & 0xFF)
                        | ((planes[5][i] & 0xFF) << 8)
                        | ((planes[6][i] & 0xFF) << 16)
                        | ((planes[7][i] & 0xFF) << 24);
                int t = i == 0 ? d : prev + d;
                int g = idx + i;
                assertTrue(t == ts[g], "chunk " + c + " ts mismatch at global " + g + " got " + t + " expect " + ts[g]);
                prev = t;
            }

            idx += chunkN;
            pos = chunkStart + 4 + 4 + chunkDataSize;
        }
        assertTrue(idx == n, "all " + n + " events walked (" + idx + ")");

        // Index region: n_chunks x [offset(8) n_events(4)].
        long indexOffset = leLong(b, b.length - 24);
        assertTrue(indexOffset == pos, "index offset == position after last chunk");
        for (int c = 0; c < expectedChunks; c++) {
            long off = leLong(b, (int) indexOffset + c * 12);
            int nEv = leInt(b, (int) indexOffset + c * 12 + 8);
            assertTrue(off >= 0 && off < b.length, "index " + c + " offset sane");
            assertTrue(nEv > 0, "index " + c + " n_events " + nEv);
        }

        // Summary block (len 0) then footer.
        long summaryOffset = leLong(b, b.length - 16);
        assertTrue(summaryOffset == indexOffset + expectedChunks * 12L, "summary offset after index");
        assertTrue(leInt(b, (int) summaryOffset) == 0, "summary_len == 0 (streaming)");
        assertTrue(b.length == summaryOffset + 4 + 24, "file ends exactly at footer");

        // Footer CRC over big-endian addr+ts pairs.
        CRC32 expectedCrc = crcOf(packet);
        int footerCrc = leInt(b, b.length - 8);
        assertTrue((int) expectedCrc.getValue() == footerCrc, "footer CRC matches recomputed CRC");
        assertTrue(b[b.length - 4] == 'A' && b[b.length - 3] == 'E' && b[b.length - 2] == 'D' && b[b.length - 1] == 'Z',
                "footer magic == AEDZ");

        System.out.println("PASS writerCount n=" + n + " chunks=" + expectedChunks + " len=" + b.length);
        file.delete();
    }

    /** A final partial chunk (fewer than CHUNK_EVENTS) must flush as its own chunk. */
    private static void partialChunk() throws Exception {
        int n = CHUNK_EVENTS + (int) (CHUNK_EVENTS * 0.37);
        AEPacketRaw packet = makePacket(n, 11);
        File file = tempFile();
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), null)) {
            out.writePacket(packet);
        }
        awaitClosed();
        byte[] b = readAll(file);
        assertTrue(leLong(b, 8) == n, "partial: header n_events=" + n);
        assertTrue(leInt(b, 8 + 8) == 2, "partial: 2 chunks");
        System.out.println("PASS partialChunk n=" + n + " len=" + b.length);
        file.delete();
    }

    // ------------------------------------------------------------------
    // determinism / close semantics
    // ------------------------------------------------------------------

    /** Two identical writes yield byte-identical chunk/index/footer (header times differ). */
    private static void deterministicOutput() throws Exception {
        AEPacketRaw packet = makePacket(CHUNK_EVENTS + 500, 3);
        File f1 = tempFile();
        File f2 = tempFile();
        try (AEDZOutputStream o1 = new AEDZOutputStream(new FileOutputStream(f1), null)) {
            o1.writePacket(packet);
        }
        awaitClosed();
        try (AEDZOutputStream o2 = new AEDZOutputStream(new FileOutputStream(f2), null)) {
            o2.writePacket(packet);
        }
        awaitClosed();
        byte[] b1 = readAll(f1);
        byte[] b2 = readAll(f2);
        int from = firstChunkOffset(b1);
        // Header lengths are identical (same line set), so the chunk region starts at the same offset.
        assertTrue(firstChunkOffset(b2) == from, "deterministic chunk start offset");
        assertTrue(java.util.Arrays.equals(b1, from, b1.length, b2, from, b2.length),
                "chunk/index/summary/footer regions byte-identical");
        assertTrue(leLong(b1, 8) == leLong(b2, 8), "patched n_events identical");
        assertTrue(leInt(b1, 8 + 8) == leInt(b2, 8 + 8), "patched n_chunks identical");
        System.out.println("PASS deterministicOutput len=" + b1.length);
        f1.delete();
        f2.delete();
    }

    /** close() is idempotent; writePacket after close throws a controlled IOException. */
    private static void closeIdempotenceAndWriteAfterClose() throws Exception {
        File file = tempFile();
        AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), null);
        out.writePacket(makePacket(100, 5));
        out.close();
        out.close(); // duplicate close must be a no-op
        assertTrue(out.getNumEvents() == 100, "events recorded before close");

        boolean threw = false;
        try {
            out.writePacket(makePacket(1, 1));
        } catch (IOException expected) {
            threw = true;
        }
        assertTrue(threw, "writePacket after close throws IOException");

        // A stream that is closed without writing still yields a valid empty file.
        File empty = tempFile();
        AEDZOutputStream e = new AEDZOutputStream(new FileOutputStream(empty), null);
        e.close();
        e.close();
        byte[] b = readAll(empty);
        assertTrue(leLong(b, 8) == 0 && leInt(b, 8 + 8) == 0, "empty file header 0/0");
        assertTrue(b[b.length - 4] == 'A' && b[b.length - 3] == 'E' && b[b.length - 2] == 'D' && b[b.length - 1] == 'Z',
                "empty file footer magic");
        System.out.println("PASS closeIdempotenceAndWriteAfterClose");
        file.delete();
        empty.delete();
    }

    /** Explicit snapshots are serialized directly, before the AEDAT end marker, and reopen losslessly. */
    private static void explicitSnapshotMetadata() throws Exception {
        String hostile = "a<&>\"b\nc\rd";
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        chip.getPrefs().put("hostile", hostile);
        RecordingConfigurationSnapshot snapshot = RecordingConfigurationSnapshot.captureFromChip(chip);

        File file = tempFile();
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), null, snapshot)) {
            // Header construction is the behavior under test; an empty event body is sufficient.
        }
        byte[] header;
        try (AEDZInputStream in = new AEDZInputStream(file)) {
            header = in.getAedatHeader();
        }
        String text = new String(header, StandardCharsets.UTF_8);
        String start = "Start of Preferences for this AEChip";
        String end = "End of Preferences for this AEChip";
        assertTrue(countOccurrences(text, "!AER-DAT" + AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2) == 1,
                "explicit snapshot has one AEDAT-2 marker");
        assertTrue(countOccurrences(text, start) == 1 && countOccurrences(text, "#" + end + "\r\n") == 1,
                "explicit snapshot has one preference frame");
        assertTrue(text.indexOf(start) < text.indexOf(AEDataFile.DATA_START_TIME_SYSTEMCURRENT_TIME_MILLIS),
                "preference block precedes data-start line");
        assertTrue(text.indexOf(start) < text.indexOf(AEDataFile.END_OF_HEADER_STRING),
                "preference block precedes end-of-header marker");
        assertTrue(text.contains("#<entry key=\"AEChip.level\" value=\"37\"/>\r\n"),
                "level entry has exact CRLF-framed legacy bytes");
        assertTrue(text.contains("#<entry key=\"hostile\" value=\"" + SnapshotCodec.escape(hostile) + "\"/>\r\n"),
                "hostile value is escaped onto one physical line");
        RecordingConfigurationSnapshot reopened = RecordingConfigurationSnapshot.parseLegacyEntries(
                java.util.Arrays.asList(text.split("\\r\\n", -1)));
        assertTrue("37".equals(reopened.get("AEChip.level")), "explicit level reopens as 37");
        assertTrue(hostile.equals(reopened.get("hostile")), "hostile value reopens losslessly");
        file.delete();

        AEChip emptyChip = bareChip();
        RecordingConfigurationSnapshot empty = RecordingConfigurationSnapshot.captureFromChip(emptyChip);
        File emptyFile = tempFile();
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(emptyFile), null, empty)) {
            // Explicitly empty snapshots still carry the recognizable preference frame.
        }
        try (AEDZInputStream in = new AEDZInputStream(emptyFile)) {
            String emptyText = new String(in.getAedatHeader(), StandardCharsets.UTF_8);
            assertTrue(countOccurrences(emptyText, start) == 1 && countOccurrences(emptyText, "#End of Preferences for this AEChip") == 1,
                    "empty explicit snapshot keeps one preference frame");
            assertTrue(countOccurrences(emptyText, "#<entry ") == 0, "empty explicit snapshot has no entries");
        }
        emptyFile.delete();
        System.out.println("PASS explicitSnapshotMetadata level=37 hostile-roundtrip empty-frame");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static AEPacketRaw makePacket(int n, int seed) {
        AEPacketRaw p = new AEPacketRaw(Math.max(1, n));
        int[] addr = p.getAddresses();
        int[] ts = p.getTimestamps();
        Random rnd = new Random(seed * 31L + 7);
        int t = 1000;
        for (int i = 0; i < n; i++) {
            addr[i] = rnd.nextInt();
            t += rnd.nextInt(3) + 1;
            ts[i] = t;
        }
        p.setNumEvents(n);
        return p;
    }

    /** CRC32 over the big-endian addr+ts pairs (matches the writer's checksum). */
    private static CRC32 crcOf(AEPacketRaw p) {
        CRC32 crc = new CRC32();
        int[] addr = p.getAddresses();
        int[] ts = p.getTimestamps();
        byte[] buf = new byte[8];
        for (int i = 0; i < p.getNumEvents(); i++) {
            int a = addr[i];
            int t = ts[i];
            buf[0] = (byte) (a >> 24);
            buf[1] = (byte) (a >> 16);
            buf[2] = (byte) (a >> 8);
            buf[3] = (byte) a;
            buf[4] = (byte) (t >> 24);
            buf[5] = (byte) (t >> 16);
            buf[6] = (byte) (t >> 8);
            buf[7] = (byte) t;
            crc.update(buf, 0, 8);
        }
        return crc;
    }

    private static File tempFile() throws IOException {
        return File.createTempFile("jaer-aedzwriter", ".aedz");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }

    private static AEChip bareChip() {
        AEChip chip = new org.objenesis.ObjenesisStd().newInstance(AEChip.class);
        chip.setPrefs(new MapBackedPreferences(null, ""));
        return chip;
    }

    private static final class MapBackedPreferences extends AbstractPreferences {
        private final Map<String, String> store = new HashMap<>();

        MapBackedPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        @Override protected void putSpi(String key, String value) { store.put(key, value); }
        @Override protected String getSpi(String key) { return store.get(key); }
        @Override protected void removeSpi(String key) { store.remove(key); }
        @Override protected void removeNodeSpi() throws BackingStoreException { }
        @Override protected String[] keysSpi() throws BackingStoreException { return store.keySet().toArray(new String[0]); }
        @Override protected String[] childrenNamesSpi() throws BackingStoreException { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { return new MapBackedPreferences(this, name); }
        @Override protected void syncSpi() throws BackingStoreException { }
        @Override protected void flushSpi() throws BackingStoreException { }
    }

    private static byte[] readAll(File f) throws IOException {
        return java.nio.file.Files.readAllBytes(f.toPath());
    }

    private static void awaitClosed() throws InterruptedException {
        // Give the JVM time to fully flush/close the channel before we read the file.
        Thread.sleep(10);
    }

    private static void checkPluginEOF(byte[] b, int chunkStart, int chunkDataSize) {
        if (chunkStart + 4 + 4 + chunkDataSize > b.length) {
            throw new AssertionError("chunk overruns EOF");
        }
    }

    private static int firstChunkOffset(byte[] b) {
        int headerLen = leInt(b, 8 + 8 + 4 + 1);
        int trailingLen = leInt(b, 8 + 8 + 4 + 1 + 4 + headerLen);
        return 8 + 8 + 4 + 1 + 4 + headerLen + 4 + trailingLen;
    }

    private static long leLong(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (b[off + i] & 0xFFL) << (8 * i);
        }
        return v;
    }

    private static int leInt(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
