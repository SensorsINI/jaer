package net.sf.jaer.eventio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * Headless production-path self-test for the streaming AEDZ compressed
 * recording format (phase3-aedz, plan Todo 4).
 *
 * <p>It writes real files through the production {@link AEDZOutputStream},
 * reopens them through the production {@link AEDZInputStream}, and asserts the
 * exact reconstructed addresses and timestamps for event counts {@code 0},
 * {@code 1}, {@code 65536} and {@code 65537}, plus cross-chunk seeks, both the
 * legacy 12-byte and extended 20-byte chunk-index encodings, partial final
 * chunks, and a battery of independently corrupted files that must be rejected
 * with a controlled {@link IOException} (never an unchecked array/buffer
 * exception). It reads the encoded fixture by hand so corruption tests do not
 * rely on the writer agreeing with itself.
 *
 * <p>Each check throws on mismatch (non-zero exit) and prints PASS lines.
 * Run headlessly after {@code ant clean compile}:
 * {@code java -cp build/classes:lib/*:jars/* net.sf.jaer.eventio.AEDZRoundtripDemo}
 */
public class AEDZRoundtripDemo {

    static final int CHUNK_EVENTS = 65536;
    static final int[] COUNTS = {0, 1, 65536, 65537};

    public static void main(String[] args) throws Exception {
        for (int n : COUNTS) {
            roundtripCount(n);
        }
        markOutZeroBackwardRegression();
        backwardNumberAndMarkSemantics();
        backwardAcrossChunkBoundary();
        timeWindowSemantics();
        timeWrapAndNonMonotonicSemantics();
        roundtripPartialChunk();
        seekAcrossChunkBoundary();
        extendedIndexFixture();
        corruptionTests();
        System.out.println("ALL AEDZ ROUNDTRIP TESTS PASS");
    }

    // ------------------------------------------------------------------
    // happy-path round trips
    // ------------------------------------------------------------------

    /** Write n deterministic events, reopen, assert every address/timestamp recovered exactly. */
    private static void roundtripCount(int n) throws IOException {
        AEPacketRaw packet = makePacket(n, 0);
        File file = tempFile(".aedz");
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), null)) {
            out.writePacket(packet);
        }
        long written = file.length();
        assertRecovered(file, n, packet, "count=" + n + " len=" + written);
        try (AEDZInputStream in = new AEDZInputStream(file)) {
            assertTrue(in.size() == n, "size() == " + n + " for count " + n);
            assertTrue(in.getFile().equals(file), "getFile identity for count " + n);
        }
        if (n == 0) {
            System.out.println("PASS roundtripCount n=0 len=" + written);
        } else {
            System.out.println("PASS roundtripCount n=" + n + " len=" + written);
        }
        file.delete();
    }

    /** A final partial chunk (fewer than CHUNK_EVENTS in the last chunk) must survive. */
    private static void roundtripPartialChunk() throws IOException {
        int n = CHUNK_EVENTS + (int) (CHUNK_EVENTS * 0.37); // crosses into a partial last chunk
        AEPacketRaw packet = makePacket(n, 0);
        File file = tempFile(".aedz");
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), null)) {
            out.writePacket(packet);
        }
        long written = file.length();
        assertRecovered(file, n, packet, "partial-chunk n=" + n + " len=" + written);
        System.out.println("PASS roundtripPartialChunk n=" + n + " len=" + written);
        file.delete();
    }

    /** Seek to positions around the 65535/65536 chunk boundary and read single events. */
    private static void seekAcrossChunkBoundary() throws IOException {
        int n = CHUNK_EVENTS + 5;
        AEPacketRaw packet = makePacket(n, 0);
        File file = tempFile(".aedz");
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), null)) {
            out.writePacket(packet);
        }
        try (AEDZInputStream in = new AEDZInputStream(file)) {
            int[] addr = packet.getAddresses();
            int[] ts = packet.getTimestamps();
            for (long pos : new long[]{65533, 65534, 65535, 65536, 65537, 65538, n - 1}) {
                in.position(pos);
                AEPacketRaw r = in.readPacketByNumber(1);
                assertTrue(r.getNumEvents() == 1, "seek read 1 event at pos " + pos);
                int a = r.getAddresses()[0];
                int t = r.getTimestamps()[0];
                assertTrue(a == addr[(int) pos], "seek addr at pos " + pos);
                assertTrue(t == ts[(int) pos], "seek ts at pos " + pos);
            }
        }
        System.out.println("PASS seekAcrossChunkBoundary");
        file.delete();
    }

    /** Exact reviewer trace: OUT cannot be set at IN=0, and a repeated backward read never reaches index -1. */
    private static void markOutZeroBackwardRegression() throws IOException {
        AEPacketRaw source = makePacket(3, 41);
        File file = writeFixture(source);
        try (AEDZInputStream in = new AEDZInputStream(file)) {
            in.setRepeat(true);
            in.position(0);
            long mark = in.setMarkOut();
            AEPacketRaw packet = in.readPacketByNumber(-1);
            assertTrue(!in.isMarkOutSet() && mark == in.size(),
                    "position(0) setMarkOut is rejected like AEFileInputStream");
            assertPacketIndices(packet, source, 2);
            assertTrue(in.position() == 2, "repeated backward read from zero wraps without negative position");
        }
        file.delete();
        System.out.println("PASS markOutZeroBackwardRegression markOut=unmodified index>=0");
    }

    /** Backward count, accumulation, mark boundaries, and repeat true/false semantics on empty/single/small files. */
    private static void backwardNumberAndMarkSemantics() throws IOException {
        File empty = writeFixture(makePacket(0, 2));
        try (AEDZInputStream in = new AEDZInputStream(empty)) {
            assertTrue(in.readPacketByNumber(-3).getNumEvents() == 0, "empty backward read is empty");
        }
        empty.delete();

        AEPacketRaw singleSource = makePacket(1, 3);
        File single = writeFixture(singleSource);
        try (AEDZInputStream in = new AEDZInputStream(single)) {
            in.setRepeat(false);
            in.position(1);
            AEPacketRaw accumulated = in.readPacketByNumber(-2);
            assertPacketIndices(accumulated, singleSource, 0);
            assertTrue(in.position() == 0, "repeat=false returns accumulated packet at BOF");
            assertTrue(in.readPacketByNumber(-1).getNumEvents() == 0,
                    "repeat=false backward at BOF returns empty without unchecked failure");
        }
        single.delete();

        AEPacketRaw source = makePacket(6, 5);
        File file = writeFixture(source);
        try (AEDZInputStream in = new AEDZInputStream(file)) {
            in.setRepeat(false);
            in.position(6);
            assertPacketIndices(in.readPacketByNumber(-3), source, 5, 4, 3);
            assertTrue(in.position() == 3, "backward count leaves next-forward position at 3");

            in.clearMarks();
            in.position(1);
            assertTrue(in.setMarkIn() == 1 && in.isMarkInSet(), "markIn set at 1");
            in.position(4);
            assertTrue(in.setMarkOut() == 4 && in.isMarkOutSet(), "markOut set at exclusive 4");

            in.setRepeat(true);
            in.position(4);
            assertPacketIndices(in.readPacketByNumber(1), source, 1);
            assertTrue(in.position() == 2, "forward repeat wraps OUT to IN");
            in.position(1);
            assertPacketIndices(in.readPacketByNumber(-1), source, 3);
            assertTrue(in.position() == 3, "backward repeat wraps IN to exclusive OUT");

            in.setRepeat(false);
            in.position(4);
            assertTrue(in.readPacketByNumber(1).getNumEvents() == 0 && in.position() == 4,
                    "repeat=false forward stops at OUT");
            in.position(1);
            assertTrue(in.readPacketByNumber(-1).getNumEvents() == 0 && in.position() == 1,
                    "repeat=false backward stops at IN");

            in.clearMarks();
            in.position(4);
            in.setMarkOut();
            in.position(5);
            assertTrue(in.setMarkIn() == 0 && !in.isMarkInSet(),
                    "markIn beyond OUT is rejected");
        }
        file.delete();
        System.out.println("PASS backwardNumberAndMarkSemantics empty/single/marks/repeat");
    }

    /** A negative count crosses the 65536 chunk boundary without changing order or indexing below zero. */
    private static void backwardAcrossChunkBoundary() throws IOException {
        AEPacketRaw source = makePacket(CHUNK_EVENTS + 2, 9);
        File file = writeFixture(source);
        try (AEDZInputStream in = new AEDZInputStream(file)) {
            in.setRepeat(false);
            in.position(source.getNumEvents());
            assertPacketIndices(in.readPacketByNumber(-4), source,
                    CHUNK_EVENTS + 1, CHUNK_EVENTS, CHUNK_EVENTS - 1, CHUNK_EVENTS - 2);
            assertTrue(in.position() == CHUNK_EVENTS - 2,
                    "backward multichunk position remains nonnegative and exact");
        }
        file.delete();
        System.out.println("PASS backwardAcrossChunkBoundary");
    }

    /** Forward/backward inclusive time windows and mark/repeat boundaries use the same position contract as count reads. */
    private static void timeWindowSemantics() throws IOException {
        AEPacketRaw source = packetWithTimestamps(100, 110, 120, 130, 140);
        File file = writeFixture(source);
        try (AEDZInputStream in = new AEDZInputStream(file)) {
            in.setRepeat(false);
            assertPacketIndices(in.readPacketByTime(20), source, 0, 1, 2);
            assertTrue(in.position() == 3, "forward time window includes endpoint");
            assertPacketIndices(in.readPacketByTime(10), source, 3);

            in.position(5);
            assertPacketIndices(in.readPacketByTime(-20), source, 4, 3, 2);
            assertTrue(in.position() == 2, "backward time window includes endpoint");

            in.clearMarks();
            in.position(1);
            in.setMarkIn();
            in.position(4);
            in.setMarkOut();
            in.setRepeat(true);
            in.position(4);
            assertTrue(in.readPacketByTime(10).getNumEvents() == 0 && in.position() == 1,
                    "forward time read at OUT rewinds to IN without mixing windows");
            in.position(1);
            assertTrue(in.readPacketByTime(-10).getNumEvents() == 0 && in.position() == 4,
                    "backward time read at IN rewinds to OUT without negative indexing");

            in.setRepeat(false);
            in.position(1);
            assertTrue(in.readPacketByTime(-10).getNumEvents() == 0 && in.position() == 1,
                    "repeat=false backward time stops at IN");
        }
        file.delete();
        System.out.println("PASS timeWindowSemantics forward/backward/marks/repeat");
    }

    /** Time reads cross signed int timestamp wrap and deliberately stop at checked non-monotonic input. */
    private static void timeWrapAndNonMonotonicSemantics() throws IOException {
        AEPacketRaw wrappedSource = packetWithTimestamps(
                Integer.MAX_VALUE - 5, Integer.MAX_VALUE - 1, Integer.MIN_VALUE + 2, Integer.MIN_VALUE + 8);
        File wrapped = writeFixture(wrappedSource);
        try (AEDZInputStream in = new AEDZInputStream(wrapped)) {
            in.setRepeat(false);
            final int[] wraps = new int[1];
            in.getSupport().addPropertyChangeListener(AEInputStream.EVENT_WRAPPED_TIME, evt -> wraps[0]++);
            assertPacketIndices(in.readPacketByTime(20), wrappedSource, 0, 1, 2, 3);
            in.position(4);
            assertPacketIndices(in.readPacketByTime(-20), wrappedSource, 3, 2, 1, 0);
            assertTrue(wraps[0] == 2, "forward and backward time reads each report one timestamp wrap");
        }
        wrapped.delete();

        AEPacketRaw nonMonotonicSource = packetWithTimestamps(100, 110, 105, 115);
        File nonMonotonic = writeFixture(nonMonotonicSource);
        try (AEDZInputStream in = new AEDZInputStream(nonMonotonic)) {
            in.setRepeat(false);
            final int[] notices = new int[1];
            in.getSupport().addPropertyChangeListener(AEInputStream.EVENT_NON_MONOTONIC_TIMESTAMP,
                    evt -> notices[0]++);
            assertPacketIndices(in.readPacketByTime(20), nonMonotonicSource, 0, 1);
            assertTrue(in.position() == 2 && notices[0] == 1,
                    "checked non-monotonic time returns intentional partial packet at offending event");

            in.setNonMonotonicTimeExceptionsChecked(false);
            in.position(0);
            in.setCurrentStartTimestamp(100);
            assertPacketIndices(in.readPacketByTime(20), nonMonotonicSource, 0, 1, 2, 3);
        }
        nonMonotonic.delete();
        System.out.println("PASS timeWrapAndNonMonotonicSemantics wraps=2 checked-partial/unchecked-all");
    }

    /**
     * Build a file whose chunk index is the extended 20-byte form
     * (offset + n_events + first_ts + last_ts) and prove the reader resolves it.
     * The extended record is produced by hand-patching a legitimately written
     * legacy 12-byte file so all other fields (chunk layout, footer, crc) are
     * writer-true and only the index stride differs.
     */
    private static void extendedIndexFixture() throws IOException {
        int n = CHUNK_EVENTS + 3;
        AEPacketRaw packet = makePacket(n, 0);
        File legacy = tempFile(".aedz");
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(legacy), null)) {
            out.writePacket(packet);
        }

        byte[] bytes = java.nio.file.Files.readAllBytes(legacy.toPath());
        int indexOffset = indexOffsetOf(bytes); // footer index_offset == position after the last chunk
        int nChunks = nChunksOf(bytes);
        int nChunksAtHeader = nChunksOf(bytes); // n_chunks right after n_events in header

        // Rebuild the index region as 20-byte entries: offset(8)+n_events(4)+first_ts(4)+last_ts(4).
        int entryBytes = nChunks * 20;
        byte[] idx = new byte[entryBytes];
        int p = 0;
        for (int c = 0; c < nChunks; c++) {
            int off = leInt(bytes, indexOffset + c * 12);
            int nEv = leInt(bytes, indexOffset + c * 12 + 8);
            putLE(idx, p, (long) off);
            putLE4(idx, p + 8, nEv);
            putLE4(idx, p + 12, 1000000 + c); // first_ts
            putLE4(idx, p + 16, 2000000 + c); // last_ts
            p += 20;
        }
        // New layout: ... header .. chunks .. [index(new)] .. summary .. footer.
        int newIndexOffset = indexOffset;      // index starts right after the last chunk
        int newSummaryOffset = newIndexOffset + entryBytes;
        //
        // Reassemble: bytes[0:indexOffset] + new index + summary(4=0) + footer.
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        baos.write(bytes, 0, indexOffset); // everything up to (not incl) old index
        baos.write(idx);
        baos.write(0); baos.write(0); baos.write(0); baos.write(0); // summary_len=0
        byte[] footer = new byte[24];
        putLE(footer, 0, (long) newIndexOffset);
        putLE(footer, 8, (long) newSummaryOffset);
        putLE4(footer, 16, leInt(bytes, footerCrcOf(bytes)));
        footer[20] = 'A'; footer[21] = 'E'; footer[22] = 'D'; footer[23] = 'Z';
        baos.write(footer);
        byte[] extendedBytes = baos.toByteArray();

        File extended = tempFile(".aedz");
        java.nio.file.Files.write(extended.toPath(), extendedBytes);

        assertTrue(extendedBytes.length == bytes.length - (nChunks * 12) + entryBytes,
                "extended file size sanity");
        assertRecovered(extended, n, packet, "extended-index fixture");
        System.out.println("PASS extendedIndexFixture n=" + n + " nChunks=" + nChunks + " nChunksAtHeader=" + nChunksAtHeader);
        legacy.delete();
        extended.delete();
    }

    // ------------------------------------------------------------------
    // corruption battery — every mutation must surface as controlled IOException
    // ------------------------------------------------------------------

    /** Mutate independent structural fields and require controlled IOException. */
    private static void corruptionTests() throws IOException {
        int n = CHUNK_EVENTS + 5;
        AEPacketRaw packet = makePacket(n, 0);
        File good = tempFile(".aedz");
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(good), null)) {
            out.writePacket(packet);
        }
        byte[] base = java.nio.file.Files.readAllBytes(good.toPath());
        good.delete();

        // Each entry: [name, mutated bytes, mustReject true]
        List<CorruptCase> cases = new ArrayList<>();

        // footer magic
        cases.add(mutateBytes(base, base.length - 1, (byte) 'X'));
        // truncation just past header
        cases.add(truncate(base, 8 + 8 + 4 + 1 + 4 + 4));
        // truncation inside the first chunk
        cases.add(truncateAfter(base, firstChunkOffset(base) + 8 + 4 + 4 + 4 + 1));
        // index_offset pointing past EOF
        cases.add(footerLong(base, 0, (long) base.length + 100));
        // summary_offset before index_offset (ordering violated)
        cases.add(footerLong(base, 8, footerIndexOffset(base) - 1));
        // total events inconsistent with index sum (n_events in header)
        cases.add(headerLong(base, 8, 123456789L));
        // negative n_chunks in header
        cases.add(headerInt(base, 8 + 8, -3));
        // compressed_size too large (overruns index region)
        cases.add(firstChunkInt(base, 4, 1 << 30));
        // compressed_size negative
        cases.add(firstChunkInt(base, 4, -1));
        // plane size sum != compressed_size (bump one plane size)
        cases.add(firstPlaneSizeBump(base, 2));
        // plane size negative
        cases.add(firstPlaneNegative(base, 5));
        // chunk events corrupted to a larger value (decompress mismatch / bound viol)
        cases.add(firstChunkInt(base, 0, n + 100));

        for (CorruptCase cc : cases) {
            File bad = tempFile(".aedz");
            java.nio.file.Files.write(bad.toPath(), cc.bytes);
            expectReject(bad, cc.tag);
            bad.delete();
        }
        System.out.println("PASS corruptionTests (" + cases.size() + " cases)");
    }

    private static void expectReject(File bad, String tag) {
        String failure = null;
        try {
            AEDZInputStream in = new AEDZInputStream(bad);
            in.close();
            failure = "constructor accepted corrupted file";
        } catch (IOException e) {
            // good: controlled rejection
            String msg = String.valueOf(e.getMessage());
            if (msg == null || msg.isEmpty()) {
                failure = "IOException with empty message";
            }
        } catch (RuntimeException e) {
            failure = "unchecked " + e.getClass().getSimpleName() + " thrown instead of IOException: " + e.getMessage();
        }
        if (failure != null) {
            throw new AssertionError("corruption case [" + tag + "] " + failure);
        }
        System.out.println("PASS corruption rejected: " + tag);
    }

    // ------------------------------------------------------------------
    // recovery helpers
    // ------------------------------------------------------------------

    /** Reopen and compare every event against the written packet. */
    private static void assertRecovered(File file, int n, AEPacketRaw packet, String tag) throws IOException {
        int[] addr = packet.getAddresses();
        int[] ts = packet.getTimestamps();
        try (AEDZInputStream in = new AEDZInputStream(file)) {
            assertTrue(in.size() == n, tag + " size");
            if (n == 0) {
                AEPacketRaw r = in.readPacketByNumber(10);
                assertTrue(r.getNumEvents() == 0, tag + " empty read yields 0 events");
                return;
            }
            int idx = 0;
            while (idx < n) {
                int take = Math.min(10000, n - idx);
                AEPacketRaw r = in.readPacketByNumber(take);
                int got = r.getNumEvents();
                assertTrue(got == take, tag + " read " + got + " expected " + take + " at idx " + idx);
                int[] a = r.getAddresses();
                int[] t = r.getTimestamps();
                for (int k = 0; k < got; k++) {
                    int g = idx + k;
                    if (a[k] != addr[g] || t[k] != ts[g]) {
                        throw new AssertionError(tag + " event mismatch at global " + g
                                + " got addr=" + Integer.toHexString(a[k]) + " ts=" + t[k]
                                + " expect addr=" + Integer.toHexString(addr[g]) + " ts=" + ts[g]);
                    }
                }
                idx += got;
            }
            assertTrue(idx == n, tag + " read to end, idx=" + idx + " n=" + n);
            // Repeat mode rewinds to the start on reading past end instead of hanging.
            in.position(n - 1);
            AEPacketRaw tail = in.readPacketByNumber(1);
            assertTrue(tail.getNumEvents() == 1, tag + " read last event");
            AEPacketRaw rewound = in.readPacketByNumber(1);
            assertTrue(rewound.getNumEvents() == 1, tag + " repeat rewound to start");
            assertTrue(rewound.getAddresses()[0] == addr[0] && rewound.getTimestamps()[0] == ts[0],
                    tag + " repeat rewind returns first event");
            // A negative read is a no-op (no events consumed).
            long posBefore = in.position();
            AEPacketRaw neg = in.readPacketByNumber(-1);
            assertTrue(neg.getNumEvents() == 0 || posBefore > in.position(), tag + " negative read safe");
        }
    }

    /** Deterministic packet of n events (address/timestamps), sitting on the 65536 chunk boundary as needed. */
    private static AEPacketRaw makePacket(int n, int seed) throws IOException {
        AEPacketRaw p = new AEPacketRaw(Math.max(1, n));
        int[] addr = p.getAddresses();
        int[] ts = p.getTimestamps();
        Random rnd = new Random(seed * 31L + 7);
        int t = 1000;
        for (int i = 0; i < n; i++) {
            addr[i] = rnd.nextInt();
            if (i > 0 && (i % 17) == 0) {
                t += 5; // occasional gap
            }
            ts[i] = t;
            t += rnd.nextInt(3);
        }
        p.setNumEvents(n);
        // While we are here, sanity-check the packet plumbing.
        if (p.getNumEvents() != n) {
            throw new IOException("AEPacketRaw setNumEvents did not hold " + n);
        }
        return p;
    }

    private static AEPacketRaw packetWithTimestamps(int... timestamps) {
        AEPacketRaw packet = new AEPacketRaw(Math.max(1, timestamps.length));
        for (int i = 0; i < timestamps.length; i++) {
            packet.getAddresses()[i] = 0x5000 + i;
            packet.getTimestamps()[i] = timestamps[i];
        }
        packet.setNumEvents(timestamps.length);
        return packet;
    }

    private static File writeFixture(AEPacketRaw source) throws IOException {
        File file = tempFile(".aedz");
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), null)) {
            out.writePacket(source);
        }
        return file;
    }

    private static void assertPacketIndices(AEPacketRaw actual, AEPacketRaw source, int... indices) {
        assertTrue(actual.getNumEvents() == indices.length,
                "packet count " + actual.getNumEvents() + " expected " + indices.length);
        for (int i = 0; i < indices.length; i++) {
            int sourceIndex = indices[i];
            assertTrue(actual.getAddresses()[i] == source.getAddresses()[sourceIndex],
                    "packet address " + i + " matches source index " + sourceIndex);
            assertTrue(actual.getTimestamps()[i] == source.getTimestamps()[sourceIndex],
                    "packet timestamp " + i + " matches source index " + sourceIndex);
        }
    }

    private static File tempFile(String ext) throws IOException {
        return File.createTempFile("jaer-aedz", ext);
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    // ------------------------------------------------------------------
    // low-level file-field readers/editors for the corruption battery
    // ------------------------------------------------------------------

    static int leInt(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    static long leLong(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (b[off + i] & 0xFFL) << (8 * i);
        }
        return v;
    }

    static void putLE(byte[] b, int off, long v) {
        for (int i = 0; i < 8; i++) {
            b[off + i] = (byte) (v >>> (8 * i));
        }
    }

    static void putLE4(byte[] b, int off, int v) {
        for (int i = 0; i < 4; i++) {
            b[off + i] = (byte) (v >>> (8 * i));
        }
    }

    static int footerIndexOffset(byte[] b) {
        return (int) leLong(b, b.length - 24);
    }

    static int footerSummaryOffset(byte[] b) {
        return (int) leLong(b, b.length - 16);
    }

    static int footerCrcOf(byte[] b) {
        return b.length - 8;
    }

    static int indexOffsetOf(byte[] b) {
        return footerIndexOffset(b);
    }

    static int summaryOffsetOf(byte[] b) {
        return footerSummaryOffset(b);
    }

    static int nChunksOf(byte[] b) {
        return leInt(b, 8 + 8); // n_chunks in header
    }

    static int firstChunkOffset(byte[] b) {
        int headerLen = leInt(b, 8 + 8 + 4 + 1);
        int trailingLen = leInt(b, 8 + 8 + 4 + 1 + 4 + headerLen);
        return 8 + 8 + 4 + 1 + 4 + headerLen + 4 + trailingLen;
    }

    static final class CorruptCase {
        final byte[] bytes;
        final String tag;

        CorruptCase(byte[] bytes, String tag) {
            this.bytes = bytes;
            this.tag = tag;
        }
    }

    private static CorruptCase mutateBytes(byte[] base, int offset, byte value) {
        byte[] b = base.clone();
        b[offset] = value;
        return new CorruptCase(b, "footerMagic@" + offset);
    }

    private static CorruptCase truncate(byte[] base, int len) {
        byte[] b = java.util.Arrays.copyOf(base, len);
        return new CorruptCase(b, "truncate len=" + len);
    }

    private static CorruptCase truncateAfter(byte[] base, int len) {
        byte[] b = java.util.Arrays.copyOf(base, len);
        return new CorruptCase(b, "truncate-in-chunk len=" + len);
    }

    private static CorruptCase footerLong(byte[] base, int relOffset, long value) {
        byte[] b = base.clone();
        putLE(b, b.length - 24 + relOffset, value);
        return new CorruptCase(b, "footerLong+" + relOffset + "=" + value);
    }

    private static CorruptCase headerLong(byte[] base, int relOffset, long value) {
        byte[] b = base.clone();
        putLE(b, relOffset, value);
        return new CorruptCase(b, "headerLong+" + relOffset + "=" + value);
    }

    private static CorruptCase headerInt(byte[] base, int relOffset, int value) {
        byte[] b = base.clone();
        putLE4(b, relOffset, value);
        return new CorruptCase(b, "headerInt+" + relOffset + "=" + value);
    }

    private static CorruptCase firstChunkInt(byte[] base, int relOffset, int value) {
        byte[] b = base.clone();
        putLE4(b, firstChunkOffset(base) + relOffset, value);
        return new CorruptCase(b, "chunkInt+" + relOffset + "=" + value);
    }

    private static CorruptCase firstPlaneSizeBump(byte[] base, int planeIndex) {
        byte[] b = base.clone();
        int ch = firstChunkOffset(base);
        int planeSize = leInt(b, ch + 8 + planeIndex * 4);
        putLE4(b, ch + 8 + planeIndex * 4, planeSize + 7); // break plane-size sum == compressed payload
        return new CorruptCase(b, "planeSize+" + planeIndex + " bump");
    }

    private static CorruptCase firstPlaneNegative(byte[] base, int planeIndex) {
        byte[] b = base.clone();
        int ch = firstChunkOffset(base);
        putLE4(b, ch + 8 + planeIndex * 4, -1);
        return new CorruptCase(b, "planeSize+" + planeIndex + " negative");
    }
}
