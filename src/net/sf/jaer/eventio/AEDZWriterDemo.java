package net.sf.jaer.eventio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
 * Headless production-path self-test for the AEDZ writer. It drives
 * {@link AEDZOutputStream} directly and parses/decompresses the closed file
 * without depending on the companion reader.
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
        legacyMetadataBehavior();
        System.out.println("ALL AEDZ WRITER TESTS PASS");
    }

    /** Write n events and verify header, chunks, index, summary, footer and CRC by hand. */
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
        assertTrue(leLong(b, 8) == n, "header n_events patched to " + n);
        assertTrue(leInt(b, 16) == expectedChunks, "header n_chunks patched to " + expectedChunks);

        int idx = 0;
        long pos = firstChunkOffset(b);
        int[] addr = packet.getAddresses();
        int[] ts = packet.getTimestamps();
        for (int c = 0; c < expectedChunks; c++) {
            long chunkStart = pos;
            int chunkN = leInt(b, (int) chunkStart);
            assertTrue(chunkN > 0, "chunk " + c + " has events");
            int chunkDataSize = leInt(b, (int) chunkStart + 4);
            int[] planeSizes = new int[8];
            int sum = 0;
            for (int p = 0; p < 8; p++) {
                planeSizes[p] = leInt(b, (int) chunkStart + 8 + p * 4);
                sum += planeSizes[p];
            }
            assertTrue(sum + 32 == chunkDataSize, "chunk plane sizes match compressed size");
            assertTrue(chunkStart + 8 + chunkDataSize <= b.length, "chunk stays before EOF");

            byte[][] planes = new byte[8][];
            int dataOff = (int) chunkStart + 40;
            for (int p = 0; p < 8; p++) {
                byte[] compressed = Arrays.copyOfRange(b, dataOff, dataOff + planeSizes[p]);
                byte[] decompressed = new byte[chunkN];
                long nDst = Zstd.decompress(decompressed, compressed);
                assertTrue(nDst == chunkN, "chunk " + c + " plane " + p + " exact decompressed length");
                planes[p] = decompressed;
                dataOff += planeSizes[p];
            }
            for (int i = 0; i < chunkN; i++) {
                int a = (planes[0][i] & 0xff)
                        | ((planes[1][i] & 0xff) << 8)
                        | ((planes[2][i] & 0xff) << 16)
                        | ((planes[3][i] & 0xff) << 24);
                assertTrue(a == addr[idx + i], "address exact at " + (idx + i));
            }
            int prev = 0;
            for (int i = 0; i < chunkN; i++) {
                int d = (planes[4][i] & 0xff)
                        | ((planes[5][i] & 0xff) << 8)
                        | ((planes[6][i] & 0xff) << 16)
                        | ((planes[7][i] & 0xff) << 24);
                int t = i == 0 ? d : prev + d;
                assertTrue(t == ts[idx + i], "timestamp exact at " + (idx + i));
                prev = t;
            }
            idx += chunkN;
            pos = chunkStart + 8 + chunkDataSize;
        }
        assertTrue(idx == n, "all events walked");

        long indexOffset = leLong(b, b.length - 24);
        assertTrue(indexOffset == pos, "index begins after final chunk");
        for (int c = 0; c < expectedChunks; c++) {
            long off = leLong(b, (int) indexOffset + c * 12);
            int nEv = leInt(b, (int) indexOffset + c * 12 + 8);
            assertTrue(off >= firstChunkOffset(b) && off < indexOffset, "index offset sane");
            assertTrue(nEv > 0, "index event count positive");
        }

        long summaryOffset = leLong(b, b.length - 16);
        assertTrue(summaryOffset == indexOffset + expectedChunks * 12L, "summary follows 12-byte index");
        assertTrue(leInt(b, (int) summaryOffset) == 0, "summary_len is zero");
        assertTrue(b.length == summaryOffset + 4 + 24, "footer ends file exactly");
        assertTrue((int) crcOf(packet).getValue() == leInt(b, b.length - 8), "footer CRC exact");
        assertFooterMagic(b);

        System.out.println("PASS writerCount n=" + n + " chunks=" + expectedChunks + " len=" + b.length);
        file.delete();
    }

    private static void partialChunk() throws Exception {
        int n = CHUNK_EVENTS + (int) (CHUNK_EVENTS * 0.37);
        AEPacketRaw packet = makePacket(n, 11);
        File file = tempFile();
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), null)) {
            out.writePacket(packet);
        }
        byte[] b = readAll(file);
        assertTrue(leLong(b, 8) == n, "partial header event count");
        assertTrue(leInt(b, 16) == 2, "partial creates two chunks");
        System.out.println("PASS partialChunk n=" + n + " len=" + b.length);
        file.delete();
    }

    /** Header timestamps vary, but chunk/index/footer bytes are deterministic. */
    private static void deterministicOutput() throws Exception {
        AEPacketRaw packet = makePacket(CHUNK_EVENTS + 500, 3);
        File f1 = tempFile();
        File f2 = tempFile();
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(f1), null)) {
            out.writePacket(packet);
        }
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(f2), null)) {
            out.writePacket(packet);
        }
        byte[] b1 = readAll(f1);
        byte[] b2 = readAll(f2);
        int from = firstChunkOffset(b1);
        assertTrue(firstChunkOffset(b2) == from, "deterministic chunk start");
        assertTrue(Arrays.equals(b1, from, b1.length, b2, from, b2.length),
                "chunk/index/summary/footer deterministic");
        System.out.println("PASS deterministicOutput len=" + b1.length);
        f1.delete();
        f2.delete();
    }

    private static void closeIdempotenceAndWriteAfterClose() throws Exception {
        File file = tempFile();
        AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), null);
        out.writePacket(makePacket(100, 5));
        out.close();
        out.close();
        assertTrue(out.getNumEvents() == 100, "events retained after duplicate close");
        boolean threw = false;
        try {
            out.writePacket(makePacket(1, 1));
        } catch (IOException expected) {
            threw = true;
        }
        assertTrue(threw, "write after close throws IOException");

        File empty = tempFile();
        AEDZOutputStream emptyOut = new AEDZOutputStream(new FileOutputStream(empty), null);
        emptyOut.close();
        emptyOut.close();
        byte[] b = readAll(empty);
        assertTrue(leLong(b, 8) == 0 && leInt(b, 16) == 0, "empty header is 0/0");
        assertFooterMagic(b);
        System.out.println("PASS closeIdempotenceAndWriteAfterClose");
        file.delete();
        empty.delete();
    }

    /** The compatibility constructor retains the existing chip-backed legacy metadata path. */
    private static void legacyMetadataBehavior() throws Exception {
        String hostile = "a<&>\"b\nc\rd";
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        chip.getPrefs().put("hostile", hostile);
        File file = tempFile();
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), chip)) {
            // Header capture is the behavior under test.
        }
        byte[] bytes = readAll(file);
        int headerLen = leInt(bytes, 21);
        String text = new String(bytes, 25, headerLen, StandardCharsets.UTF_8);
        assertTrue(text.contains("#Start of Preferences for this AEChip"), "legacy preference frame starts");
        assertTrue(text.contains("#End of Preferences for this AEChip\r\n"), "legacy preference frame ends");
        assertTrue(text.indexOf("Start of Preferences") < text.indexOf(AEDataFile.END_OF_HEADER_STRING),
                "legacy preferences precede end marker");
        RecordingConfigurationSnapshot reopened = RecordingConfigurationSnapshot.parseLegacyEntries(
                Arrays.asList(text.split("\\r\\n", -1)));
        assertTrue("37".equals(reopened.get("AEChip.level")), "legacy level reopens as 37");
        assertTrue(hostile.equals(reopened.get("hostile")), "legacy hostile value round-trips");
        File sidecar = new File(file.getPath() + ".xml");
        assertTrue(!sidecar.exists(), "legacy metadata remains embedded with no sidecar");
        file.delete();
        System.out.println("PASS legacyMetadataBehavior level=37 hostile-roundtrip no-sidecar");
    }

    private static AEPacketRaw makePacket(int n, int seed) {
        AEPacketRaw packet = new AEPacketRaw(Math.max(1, n));
        int[] addr = packet.getAddresses();
        int[] ts = packet.getTimestamps();
        Random random = new Random(seed * 31L + 7);
        int t = 1000;
        for (int i = 0; i < n; i++) {
            addr[i] = random.nextInt();
            t += random.nextInt(3) + 1;
            ts[i] = t;
        }
        packet.setNumEvents(n);
        return packet;
    }

    private static CRC32 crcOf(AEPacketRaw packet) {
        CRC32 crc = new CRC32();
        byte[] buf = new byte[8];
        for (int i = 0; i < packet.getNumEvents(); i++) {
            int a = packet.getAddresses()[i];
            int t = packet.getTimestamps()[i];
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

    private static File tempFile() throws IOException {
        return File.createTempFile("jaer-aedzwriter", ".aedz");
    }

    private static byte[] readAll(File file) throws IOException {
        return java.nio.file.Files.readAllBytes(file.toPath());
    }

    private static void awaitClosed() throws InterruptedException {
        Thread.sleep(10);
    }

    private static int firstChunkOffset(byte[] b) {
        int headerLen = leInt(b, 21);
        int trailingLen = leInt(b, 25 + headerLen);
        return 29 + headerLen + trailingLen;
    }

    private static long leLong(byte[] b, int off) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= (b[off + i] & 0xffL) << (8 * i);
        }
        return value;
    }

    private static int leInt(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    private static void assertFooterMagic(byte[] b) {
        assertTrue(b[b.length - 4] == 'A' && b[b.length - 3] == 'E'
                && b[b.length - 2] == 'D' && b[b.length - 1] == 'Z', "footer magic AEDZ");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
