package net.sf.jaer.eventio.aedat4;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.flatbuffers.FlatBufferBuilder;
import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.stream.Stream;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Acceptance tests for rejecting hostile AEDAT-4 lengths and FlatBuffers during
 * construction. Every fixture is created locally and contains only the bytes
 * needed to reach the malformed field under test.
 */
public class Aedat4FileInputStreamBoundsTest {

    /**
     * A single encoded packet is capped at 64 MiB by the reader hardening
     * contract. Normal event, frame, and IMU packets are much smaller. Keeping
     * the fixture sparse makes the pre-allocation check practical under
     * {@code -Xmx64m} without writing or retaining a large byte array.
     */
    private static final int MAX_PACKET_PAYLOAD_BYTES = 64 * 1024 * 1024;
    private static final int REPEATED_OPEN_COUNT = 64;
    private static final Path PROC_SELF_FD = Paths.get("/proc/self/fd");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void maxIntIoHeaderPrefixIsContextualIOException() throws Exception {
        File fixture = newFixture("max-int-ioheader");
        try (DataOutputStream out = output(fixture)) {
            out.write(Aedat4FileOutputStream.VERSION_LINE);
            writeIntLittleEndian(out, Integer.MAX_VALUE);
        }

        IOException failure = expectConstructorIOException(fixture);
        assertContext(failure, "ioheader", Integer.toString(Integer.MAX_VALUE));
    }

    @Test
    public void packetPayloadLargerThanRemainingFileIsContextualIOException() throws Exception {
        final int declaredPayloadBytes = 4096;
        File fixture = newFixture("payload-beyond-eof");
        try (DataOutputStream out = output(fixture)) {
            out.write(Aedat4FileOutputStream.VERSION_LINE);
            out.write(validIoHeader());
            writePacketHeader(out, Aedat4FileOutputStream.STREAM_EVENTS, declaredPayloadBytes);
            out.write(new byte[]{1, 2, 3});
        }

        IOException failure = expectConstructorIOException(fixture);
        assertContext(failure, "payload", "remaining", Integer.toString(declaredPayloadBytes));
    }

    @Test
    public void packetPayloadAboveDocumentedMaximumIsRejectedBeforeAllocation() throws Exception {
        final int declaredPayloadBytes = MAX_PACKET_PAYLOAD_BYTES + 1;
        File fixture = newFixture("payload-over-limit");
        try (RandomAccessFile out = new RandomAccessFile(fixture, "rw")) {
            out.write(Aedat4FileOutputStream.VERSION_LINE);
            out.write(validIoHeader());
            writePacketHeader(out, Aedat4FileOutputStream.STREAM_EVENTS, declaredPayloadBytes);
            long payloadOffset = out.getFilePointer();
            out.setLength(payloadOffset + declaredPayloadBytes);
        }

        IOException failure = expectConstructorIOException(fixture);
        assertContext(failure, "payload", Integer.toString(declaredPayloadBytes));
        assertMessageContainsAny(failure, "maximum", "limit", "too large");
    }

    @Test
    public void truncatedIoHeaderFlatBufferIsContextualIOException() throws Exception {
        final int declaredHeaderBytes = 32;
        File fixture = newFixture("truncated-ioheader");
        try (DataOutputStream out = output(fixture)) {
            out.write(Aedat4FileOutputStream.VERSION_LINE);
            writeIntLittleEndian(out, declaredHeaderBytes);
            out.write(new byte[]{4, 0, 0, 0});
        }

        IOException failure = expectConstructorIOException(fixture);
        assertContext(failure, "ioheader");
        assertMessageContainsAny(failure, "truncated", "eof", "remaining");
    }

    @Test
    public void malformedEventPacketFlatBufferIsContextualIOException() throws Exception {
        byte[] malformedEventPacket = malformedSizePrefixedFlatBuffer("EVTS");
        File fixture = newFixture("malformed-event-packet");
        try (DataOutputStream out = output(fixture)) {
            out.write(Aedat4FileOutputStream.VERSION_LINE);
            out.write(validIoHeader());
            writePacketHeader(out, Aedat4FileOutputStream.STREAM_EVENTS, malformedEventPacket.length);
            out.write(malformedEventPacket);
        }

        IOException failure = expectConstructorIOException(fixture);
        assertMessageContainsAny(failure, "evts", "event packet", "flatbuffer");
        assertMessageContainsAny(failure, "malformed", "parse", "invalid");
    }

    @Test
    public void repeatedMalformedConstructorOpensKeepFileDescriptorCountStable() throws Exception {
        Assume.assumeTrue("requires readable /proc/self/fd",
                Files.isDirectory(PROC_SELF_FD) && Files.isReadable(PROC_SELF_FD));

        File fixture = newFixture("repeated-malformed-ioheader");
        try (DataOutputStream out = output(fixture)) {
            out.write(Aedat4FileOutputStream.VERSION_LINE);
            out.write(malformedSizePrefixedFlatBuffer("IOHE"));
        }

        // Initialize logging and parser classes before taking the descriptor baseline.
        openOnceIgnoringExpectedFailure(fixture);
        long before = countOpenFileDescriptorsOrSkip();

        int checkedFailures = 0;
        int uncheckedFailures = 0;
        int nonContextualFailures = 0;
        int successfulOpens = 0;
        String firstUnchecked = null;
        for (int i = 0; i < REPEATED_OPEN_COUNT; i++) {
            Aedat4FileInputStream opened = null;
            try {
                opened = new Aedat4FileInputStream(fixture, null);
                successfulOpens++;
            } catch (IOException failure) {
                checkedFailures++;
                if (!hasContext(failure, "ioheader")) {
                    nonContextualFailures++;
                }
            } catch (RuntimeException failure) {
                uncheckedFailures++;
                if (firstUnchecked == null) {
                    firstUnchecked = failure.getClass().getName() + ": " + failure.getMessage();
                }
            } finally {
                if (opened != null) {
                    opened.close();
                }
            }
        }

        long after = countOpenFileDescriptorsOrSkip();
        StringBuilder defects = new StringBuilder();
        if (after != before) {
            defects.append("file descriptors changed from ").append(before).append(" to ").append(after).append("; ");
        }
        if (checkedFailures != REPEATED_OPEN_COUNT) {
            defects.append("contextual IOException count=").append(checkedFailures)
                    .append(" expected=").append(REPEATED_OPEN_COUNT).append("; ");
        }
        if (uncheckedFailures != 0) {
            defects.append("unchecked failures=").append(uncheckedFailures)
                    .append(" first=").append(firstUnchecked).append("; ");
        }
        if (nonContextualFailures != 0) {
            defects.append("non-contextual IOExceptions=").append(nonContextualFailures).append("; ");
        }
        if (successfulOpens != 0) {
            defects.append("successful malformed opens=").append(successfulOpens).append("; ");
        }
        assertTrue("Repeated malformed constructor opens must fail as contextual IOExceptions without leaking: "
                + defects, defects.length() == 0);
    }

    private File newFixture(String prefix) throws IOException {
        return Files.createTempFile(temporaryFolder.getRoot().toPath(), prefix + "-", ".aedat4").toFile();
    }

    private static DataOutputStream output(File file) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
    }

    private static byte[] validIoHeader() {
        FlatBufferBuilder builder = new FlatBufferBuilder(64);
        int infoNode = builder.createString("");
        int root = IOHeader.createIOHeader(builder, CompressionType.NONE, -1L, infoNode);
        builder.finishSizePrefixed(root, "IOHE");
        return builder.sizedByteArray();
    }

    private static byte[] malformedSizePrefixedFlatBuffer(String identifier) {
        if (identifier.length() != 4) {
            throw new IllegalArgumentException("FlatBuffer identifier must have four characters");
        }
        ByteBuffer bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(12);
        bytes.putInt(Integer.MAX_VALUE); // root offset points far outside this buffer
        for (int i = 0; i < identifier.length(); i++) {
            bytes.put((byte) identifier.charAt(i));
        }
        bytes.putInt(0);
        return bytes.array();
    }

    private static void writePacketHeader(DataOutput out, int streamId, int payloadBytes) throws IOException {
        writeIntLittleEndian(out, streamId);
        writeIntLittleEndian(out, payloadBytes);
    }

    private static void writeIntLittleEndian(DataOutput out, int value) throws IOException {
        out.writeByte(value);
        out.writeByte(value >>> 8);
        out.writeByte(value >>> 16);
        out.writeByte(value >>> 24);
    }

    private static IOException expectConstructorIOException(File fixture) throws Exception {
        Aedat4FileInputStream opened;
        try {
            opened = new Aedat4FileInputStream(fixture, null);
        } catch (IOException failure) {
            return failure;
        } catch (RuntimeException | OutOfMemoryError unchecked) {
            AssertionError assertion = new AssertionError(
                    "Expected contextual IOException for " + fixture.getName()
                    + " but constructor threw " + unchecked.getClass().getName() + ": " + unchecked.getMessage());
            assertion.initCause(unchecked);
            throw assertion;
        }

        try {
            opened.close();
        } finally {
            deleteIndexCacheQuietly(fixture);
        }
        fail("Expected malformed AEDAT-4 constructor to fail for " + fixture.getName());
        return null;
    }

    private static void openOnceIgnoringExpectedFailure(File fixture) throws IOException {
        Aedat4FileInputStream opened = null;
        try {
            opened = new Aedat4FileInputStream(fixture, null);
        } catch (IOException | RuntimeException expected) {
            // Warm-up only; the measured loop below verifies the exact failure contract.
        } finally {
            if (opened != null) {
                opened.close();
            }
        }
    }

    private static long countOpenFileDescriptorsOrSkip() {
        try (Stream<Path> descriptors = Files.list(PROC_SELF_FD)) {
            return descriptors.count();
        } catch (IOException failure) {
            Assume.assumeTrue("cannot enumerate /proc/self/fd: " + failure, false);
            return -1L;
        }
    }

    private static void assertContext(IOException failure, String... requiredTokens) {
        assertNotNull("IOException is required", failure);
        String message = failure.getMessage();
        assertTrue("IOException must have a contextual message, got " + failure,
                message != null && !message.trim().isEmpty());
        String lower = message.toLowerCase(Locale.ROOT);
        for (String token : requiredTokens) {
            assertTrue("IOException message must contain '" + token + "': " + message,
                    lower.contains(token.toLowerCase(Locale.ROOT)));
        }
    }

    private static void assertMessageContainsAny(IOException failure, String... alternatives) {
        assertNotNull("IOException is required", failure);
        String message = failure.getMessage();
        assertTrue("IOException must have a contextual message, got " + failure,
                message != null && !message.trim().isEmpty());
        String lower = message.toLowerCase(Locale.ROOT);
        for (String alternative : alternatives) {
            if (lower.contains(alternative.toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        fail("IOException message must contain one of " + String.join(", ", alternatives) + ": " + message);
    }

    private static boolean hasContext(IOException failure, String... requiredTokens) {
        String message = failure.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String token : requiredTokens) {
            if (!lower.contains(token.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static void deleteIndexCacheQuietly(File fixture) {
        File cache = new File(System.getProperty("java.io.tmpdir"), String.format(Locale.ROOT,
                "%s.%d.%d.s0.aedat4idx", fixture.getName(), fixture.length(), fixture.lastModified()));
        try {
            Files.deleteIfExists(cache.toPath());
        } catch (IOException ignored) {
            // Best-effort cleanup after an unexpected successful malformed open.
        }
    }
}
