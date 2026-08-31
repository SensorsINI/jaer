package net.sf.jaer.eventio.aedat4;

import com.google.flatbuffers.FlatBufferBuilder;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedDataPacket;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventio.aedat4.dv.FileDataDefinition;
import net.sf.jaer.eventio.aedat4.dv.FileDataTable;
import net.sf.jaer.eventio.aedat4.dv.Frame;
import net.sf.jaer.eventio.aedat4.dv.FrameFormat;
import net.sf.jaer.eventio.aedat4.dv.FrameSource;
import net.sf.jaer.eventio.aedat4.dv.IMU;
import net.sf.jaer.eventio.aedat4.dv.IMUPacket;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;
import net.sf.jaer.util.EngineeringFormat;

/** Writes AEDAT-4 files with DV-compatible FlatBuffers packets and optional LZ4/ZSTD compression. */
public class Aedat4FileOutputStream implements Closeable {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    public static final byte[] VERSION_LINE = new byte[]{'#', '!', 'A', 'E', 'R', '-', 'D', 'A', 'T', '4', '.', '0', '\r', '\n'};
    public static final int STREAM_EVENTS = 0;
    public static final int STREAM_FRAMES = 1;
    public static final int STREAM_IMU = 2;

    private final FileOutputStream outputStream;
    private final FileChannel channel;
    private final AEChip chip;
    private final int compression;
    private final RecordingConfigurationSnapshot snapshot;
    private final List<Aedat4CameraTrack> tracks;
    private Aedat4CameraTrack currentTrack;
    private final long baseUs;
    private final List<DataDefinition> dataDefinitions = new ArrayList<>();
    private final long headerPosition;
    private byte[] headerBytes;
    private boolean closed;
    /**
     * Camera timestamps are 32-bit µs; add 2^32 on wrap so AEDAT-4 Unix times
     * stay monotonic (12 h recordings). Per-camera unwrap lives on each
     * {@link Aedat4CameraTrack}.
     */
    /** Uncompressed FlatBuffer packet payload bytes (before LZ4/ZSTD). */
    private long uncompressedPayloadBytes;
    /** Compressed packet payload bytes written to the file (same as uncompressed if NONE). */
    private long compressedPayloadBytes;
    private long[] evTimestamps;
    private short[] evXs;
    private short[] evYs;
    private boolean[] evPolarities;
    private FlatBufferBuilder eventBuilder;
    private final ByteBuffer packetHeader;

    public Aedat4FileOutputStream(File file, AEChip chip) throws IOException {
        this(new FileOutputStream(file), chip, CompressionType.LZ4,
                System.currentTimeMillis() * 1000L, null, true);
    }

    public Aedat4FileOutputStream(File file, AEChip chip, int compression) throws IOException {
        this(new FileOutputStream(file), chip, compression,
                System.currentTimeMillis() * 1000L, null, true);
    }

    public Aedat4FileOutputStream(FileOutputStream outputStream, AEChip chip) throws IOException {
        this(outputStream, chip, CompressionType.LZ4);
    }

    public Aedat4FileOutputStream(FileOutputStream outputStream, AEChip chip, int compression) throws IOException {
        this(outputStream, chip, compression, System.currentTimeMillis() * 1000L, null, false);
    }

    /**
     * @param baseUnixUs AEDAT-4 packet timestamps are Unix µs; playback stores
     *                   relative µs. Pass the source file's
     *                   {@code getAbsoluteStartingTimeMs() * 1000} to keep the
     *                   original timeline, or {@code <= 0} for wall-clock now.
     */
    public Aedat4FileOutputStream(File file, AEChip chip, int compression, long baseUnixUs) throws IOException {
        this(new FileOutputStream(file), chip, compression, baseUnixUs, null, true);
    }

    public Aedat4FileOutputStream(FileOutputStream outputStream, AEChip chip, int compression, long baseUnixUs)
            throws IOException {
        this(outputStream, chip, compression, baseUnixUs, null, false);
    }

    /**
     * Explicit-snapshot constructor. The snapshot is reused verbatim for the open
     * and close IOHeader rebuild so the serialized header size stays stable even
     * if live preferences change after recording begins, and so the recorded
     * configuration reflects the immutable recording-start values.
     *
     * @param outputStream the file to write to
     * @param chip the chip (geometry/source metadata)
     * @param compression desired compatibility compression
     * @param snapshot the frozen recording-start configuration; if {@code null}
     *                 it is captured once here from the chip, never reread later
     * @throws IOException if the file cannot be written
     */
    public Aedat4FileOutputStream(FileOutputStream outputStream, AEChip chip, int compression,
            RecordingConfigurationSnapshot snapshot) throws IOException {
        this(outputStream, chip, compression, System.currentTimeMillis() * 1000L, snapshot, false, null);
    }

    /**
     * Muxed cameras sharing one file. Tracks must be frozen before this call
     * (snapshots, source labels, stream bases). Shared {@code baseUnixUs}.
     */
    public Aedat4FileOutputStream(FileOutputStream outputStream, List<Aedat4CameraTrack> tracks, int compression,
            long baseUnixUs) throws IOException {
        this(outputStream, tracks == null || tracks.isEmpty() ? null : tracks.get(0).chip, compression,
                baseUnixUs, firstSnapshot(tracks), false, tracks);
    }

    private static RecordingConfigurationSnapshot firstSnapshot(List<Aedat4CameraTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return null;
        }
        return tracks.get(0).snapshot;
    }

    private Aedat4FileOutputStream(FileOutputStream outputStream, AEChip chip, int compression,
            long baseUnixUs, RecordingConfigurationSnapshot snapshot, boolean closeOnInitializationFailure)
            throws IOException {
        this(outputStream, chip, compression, baseUnixUs, snapshot, closeOnInitializationFailure, null);
    }

    private Aedat4FileOutputStream(FileOutputStream outputStream, AEChip chip, int compression,
            long baseUnixUs, RecordingConfigurationSnapshot snapshot, boolean closeOnInitializationFailure,
            List<Aedat4CameraTrack> suppliedTracks)
            throws IOException {
        this.outputStream = outputStream;
        this.channel = outputStream.getChannel();
        this.chip = chip;
        this.compression = Aedat4Compression.clamp(compression);
        this.baseUs = baseUnixUs > 0 ? baseUnixUs : System.currentTimeMillis() * 1000L;
        ByteBuffer initializedPacketHeader;
        long initializedHeaderPosition;
        RecordingConfigurationSnapshot initializedSnapshot;
        List<Aedat4CameraTrack> initializedTracks;
        try {
            initializedSnapshot =
                    snapshot != null ? snapshot : RecordingConfigurationSnapshot.captureFromChip(chip);
            if (suppliedTracks != null && !suppliedTracks.isEmpty()) {
                initializedTracks = Collections.unmodifiableList(new ArrayList<>(suppliedTracks));
            } else {
                initializedTracks = Collections.singletonList(
                        new Aedat4CameraTrack(chip,
                                chip == null ? "jAER" : chip.getClass().getSimpleName(),
                                initializedSnapshot, 0));
            }
            this.tracks = initializedTracks;
            this.currentTrack = initializedTracks.get(0);
            this.snapshot = initializedSnapshot;
            initializedPacketHeader = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN);
            channel.write(ByteBuffer.wrap(VERSION_LINE));
            initializedHeaderPosition = channel.position();
            // FlatBuffers omits dataTablePosition when it equals the default (-1). Use a
            // non-default sentinel so the field is always present and close() can patch
            // the same-sized IOHeader with the real FileDataTable offset.
            headerBytes = buildIOHeader(DATA_TABLE_POSITION_PENDING, initializedSnapshot);
            channel.write(ByteBuffer.wrap(headerBytes));
        } catch (IOException | RuntimeException failure) {
            if (closeOnInitializationFailure) {
                try {
                    outputStream.close();
                } catch (IOException | RuntimeException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
        this.packetHeader = initializedPacketHeader;
        this.headerPosition = initializedHeaderPosition;
    }

    /** Sentinel written at open; replaced on {@link #close()} with the real table offset. */
    private static final long DATA_TABLE_POSITION_PENDING = -2L;

    public int getCompression() {
        return compression;
    }

    public long getEventsWritten() {
        long n = 0;
        for (Aedat4CameraTrack t : tracks) {
            n += countStream(t.eventsStreamId());
        }
        return n;
    }

    public long getFramesWritten() {
        long n = 0;
        for (Aedat4CameraTrack t : tracks) {
            n += countStream(t.framesStreamId());
        }
        return n;
    }

    public long getImuSamplesWritten() {
        long n = 0;
        for (Aedat4CameraTrack t : tracks) {
            n += countStream(t.imuStreamId());
        }
        return n;
    }

    public int getTrackCount() {
        return tracks.size();
    }

    public List<Aedat4CameraTrack> getTracks() {
        return tracks;
    }

    private long countStream(int streamId) {
        long n = 0;
        for (DataDefinition d : dataDefinitions) {
            if (d.streamId == streamId) {
                n += d.numElements;
            }
        }
        return n;
    }

    /** Writes all packets; includes polarity events marked filteredOut. */
    public synchronized void writeBundle(PacketBundle bundle) throws IOException {
        writeBundle(bundle, false);
    }

    /**
     * @param skipFilteredOut if true, omit events with {@link BasicEvent#isFilteredOut()}
     *                        (STCF etc.). Uses {@link EventPacket#iterator()} which
     *                        skips those events; {@link EventPacket#getSize()} still
     *                        counts them.
     */
    public synchronized void writeBundle(PacketBundle bundle, boolean skipFilteredOut) throws IOException {
        writeBundle(bundle, skipFilteredOut, 0);
    }

    /**
     * Write a camera's {@link PacketBundle} onto that track's EVTS/FRME/IMUS IDs.
     */
    public synchronized void writeBundle(PacketBundle bundle, boolean skipFilteredOut, int trackIndex)
            throws IOException {
        if (bundle == null || bundle.isEmpty()) {
            return;
        }
        if (trackIndex < 0 || trackIndex >= tracks.size()) {
            throw new IllegalArgumentException("AEDAT-4 track index " + trackIndex
                    + " out of range 0.." + (tracks.size() - 1));
        }
        currentTrack = tracks.get(trackIndex);
        for (TypedDataPacket packet : bundle) {
            if (packet == null || packet.isEmpty()) {
                continue;
            }
            if (packet.getPacketType() == PacketType.POLARITY && packet instanceof net.sf.jaer.event.EventPacket) {
                writeEventPacket((net.sf.jaer.event.EventPacket<?>) packet, skipFilteredOut);
            } else if (packet instanceof FramePacket) {
                writeFramePacket((FramePacket) packet);
            } else if (packet instanceof ImuPacket) {
                writeImuPacket((ImuPacket) packet);
            }
        }
    }

    private void writeEventPacket(net.sf.jaer.event.EventPacket<?> packet, boolean skipFilteredOut)
            throws IOException {
        final int size = packet.getSize();
        if (size == 0) {
            return;
        }
        evTimestamps = ensureLongs(evTimestamps, size);
        evXs = ensureShorts(evXs, size);
        evYs = ensureShorts(evYs, size);
        evPolarities = ensureBooleans(evPolarities, size);
        int n = 0;
        if (skipFilteredOut) {
            // Same as display / reconstructRawPacket: iterator skips filteredOut.
            for (BasicEvent event : packet) {
                n = appendPolarityEvent(event, evTimestamps, evXs, evYs, evPolarities, n);
            }
        } else {
            for (int k = 0; k < size; k++) {
                n = appendPolarityEvent(packet.getEvent(k), evTimestamps, evXs, evYs, evPolarities, n);
            }
        }
        if (n == 0) {
            return;
        }
        int minCap = Math.max(1024, n * 16 + 64);
        if (eventBuilder == null) {
            eventBuilder = new FlatBufferBuilder(minCap);
        } else {
            eventBuilder.clear();
        }
        int vector = net.sf.jaer.eventio.aedat4.dv.EventPacket.createElementsVector(
                eventBuilder, evTimestamps, evXs, evYs, evPolarities, n);
        int root = net.sf.jaer.eventio.aedat4.dv.EventPacket.createEventPacket(eventBuilder, vector);
        eventBuilder.finishSizePrefixed(root, "EVTS");
        byte[] payload = eventBuilder.sizedByteArray();
        writePacket(currentTrack.eventsStreamId(), payload, n, evTimestamps[0], evTimestamps[n - 1]);
    }

    private int appendPolarityEvent(BasicEvent event, long[] timestamps, short[] xs, short[] ys,
            boolean[] polarities, int n) {
        if (event == null) {
            return n;
        }
        if (event instanceof ApsDvsEvent) {
            ApsDvsEvent aps = (ApsDvsEvent) event;
            if (aps.isApsData() || aps.isImuSample()) {
                return n;
            }
        }
        timestamps[n] = toUnixUs(event.timestamp);
        xs[n] = event.x;
        ys[n] = event.y;
        polarities[n] = !(event instanceof PolarityEvent)
                || ((PolarityEvent) event).polarity == PolarityEvent.Polarity.On;
        return n + 1;
    }

    private void writeFramePacket(FramePacket packet) throws IOException {
        short[] pixels = packet.getPixels();
        if (pixels == null) {
            return;
        }
        byte[] pixelBytes = new byte[pixels.length * 2];
        for (int i = 0, j = 0; i < pixels.length; i++) {
            int value = pixels[i] & 0xffff;
            pixelBytes[j++] = (byte) value;
            pixelBytes[j++] = (byte) (value >>> 8);
        }
        long start = toUnixUs(packet.getTimestampStartUs());
        long end = toUnixUs(packet.getTimestampEndUs());
        long midpoint = start + ((end - start) / 2);
        FlatBufferBuilder builder = new FlatBufferBuilder(Math.max(1024, pixelBytes.length + 128));
        int pixelsOffset = Frame.createPixelsVector(builder, pixelBytes);
        int root = Frame.createFrame(builder, midpoint, start, end, start, end, FrameFormat.OPENCV_16U_C1,
                (short) packet.getWidth(), (short) packet.getHeight(), (short) 0, (short) 0, pixelsOffset,
                packet.getExposureUs(), FrameSource.SENSOR);
        builder.finishSizePrefixed(root, "FRME");
        writePacket(currentTrack.framesStreamId(), builder.sizedByteArray(), 1, start, end);
    }

    private void writeImuPacket(ImuPacket packet) throws IOException {
        int n = packet.getSize();
        int[] offsets = new int[n];
        long first = 0;
        long last = 0;
        FlatBufferBuilder builder = new FlatBufferBuilder(Math.max(1024, n * 96));
        for (int i = 0; i < n; i++) {
            IMUSample sample = packet.get(i);
            long timestamp = toUnixUs(sample.getTimestampUs());
            if (i == 0) {
                first = timestamp;
            }
            last = timestamp;
            offsets[i] = IMU.createIMU(builder, timestamp, sample.getTemperature(),
                    sample.getAccelX(), sample.getAccelY(), sample.getAccelZ(),
                    sample.getGyroTiltX(), sample.getGyroYawY(), sample.getGyroRollZ(),
                    0, 0, 0);
        }
        int vector = IMUPacket.createElementsVector(builder, offsets);
        int root = IMUPacket.createIMUPacket(builder, vector);
        builder.finishSizePrefixed(root, "IMUS");
        writePacket(currentTrack.imuStreamId(), builder.sizedByteArray(), n, first, last);
    }

    private void writePacket(int streamId, byte[] payload, long numElements, long timestampStart, long timestampEnd) throws IOException {
        ByteBuffer toWrite = Aedat4Compression.compressDirect(payload, compression);
        uncompressedPayloadBytes += payload.length;
        int compressedLen = toWrite.remaining();
        compressedPayloadBytes += compressedLen;
        // DV FileDataTable offsets address the encoded payload, not its PacketHeader.
        long byteOffset = channel.position() + 8L;
        packetHeader.clear();
        packetHeader.putInt(streamId);
        packetHeader.putInt(compressedLen);
        packetHeader.flip();
        channel.write(packetHeader);
        channel.write(toWrite);
        dataDefinitions.add(new DataDefinition(byteOffset, streamId, compressedLen, numElements, timestampStart, timestampEnd));
    }

    /**
     * Camera / packet timestamps are 32-bit µs. Sign-extending them to long
     * made Unix times jump backward every ~35.8 min. Treat as unsigned 32-bit
     * and add 2^32 on wrap so DV-compatible Unix µs stay monotonic.
     */
    private long toUnixUs(int relativeUs) {
        return baseUs + currentTrack.unwrapper.unwrapUnsigned32(relativeUs);
    }

    private long toUnixUs(long relativeUs) {
        if (relativeUs >= Integer.MIN_VALUE && relativeUs <= Integer.MAX_VALUE) {
            return toUnixUs((int) relativeUs);
        }
        return baseUs + currentTrack.unwrapper.unwrapRaw(relativeUs);
    }

    private byte[] buildIOHeader(long dataTablePosition) {
        return buildIOHeader(dataTablePosition, snapshot);
    }

    private byte[] buildIOHeader(long dataTablePosition, RecordingConfigurationSnapshot headerSnapshot) {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);
        int info = builder.createString(Aedat4InfoNode.build(tracks, compression));
        int root = IOHeader.createIOHeader(builder, compression, dataTablePosition, info);
        builder.finishSizePrefixed(root, "IOHE");
        return builder.sizedByteArray();
    }

    private byte[] buildFileDataTable() {
        FlatBufferBuilder builder = new FlatBufferBuilder(Math.max(1024, dataDefinitions.size() * 64));
        int[] offsets = new int[dataDefinitions.size()];
        for (int i = 0; i < dataDefinitions.size(); i++) {
            DataDefinition d = dataDefinitions.get(i);
            offsets[i] = FileDataDefinition.createFileDataDefinition(builder, d.byteOffset, d.streamId, d.size,
                    d.numElements, d.timestampStart, d.timestampEnd);
        }
        int vector = FileDataTable.createTableVector(builder, offsets);
        int root = FileDataTable.createFileDataTable(builder, vector);
        builder.finishSizePrefixed(root, "FTAB");
        return builder.sizedByteArray();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        Throwable failure = null;
        try {
            long tablePosition = channel.position();
            byte[] table = Aedat4Compression.compress(buildFileDataTable(), compression);
            channel.write(ByteBuffer.wrap(table));
            byte[] patchedHeader = buildIOHeader(tablePosition);
            if (patchedHeader.length != headerBytes.length) {
                throw new IOException(String.format(
                        "AEDAT-4 IOHeader size changed on close (%d -> %d); cannot patch dataTablePosition=%d",
                        headerBytes.length, patchedHeader.length, tablePosition));
            }
            long end = channel.position();
            channel.position(headerPosition);
            channel.write(ByteBuffer.wrap(patchedHeader));
            channel.position(end);
            headerBytes = patchedHeader;
        } catch (IOException | RuntimeException e) {
            failure = e;
        } finally {
            // Finalization failures (including a mutable-chip IOHeader size change)
            // must never retain the caller-supplied file handle. Close the owning
            // stream exactly once; if a custom stream throws before closing its
            // channel, the still-open channel is the fallback cleanup layer.
            try {
                outputStream.close();
            } catch (IOException | RuntimeException closeFailure) {
                failure = appendFailure(failure, closeFailure);
            }
            if (channel.isOpen()) {
                try {
                    channel.close();
                } catch (IOException | RuntimeException closeFailure) {
                    failure = appendFailure(failure, closeFailure);
                }
            }
        }
        rethrowCloseFailure(failure);
        log.info(formatCompressionSummary());
    }

    private static Throwable appendFailure(Throwable primary, Throwable next) {
        if (primary == null) {
            return next;
        }
        if (next != primary) {
            primary.addSuppressed(next);
        }
        return primary;
    }

    private static void rethrowCloseFailure(Throwable failure) throws IOException {
        if (failure == null) {
            return;
        }
        if (failure instanceof IOException) {
            throw (IOException) failure;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        throw new IOException("Unexpected AEDAT-4 close failure", failure);
    }

    /**
     * Estimated packet-payload compression vs uncompressed FlatBuffers
     * (headers/FileDataTable excluded). Relative measure of LZ4/ZSTD gain.
     */
    public String formatCompressionSummary() {
        if (uncompressedPayloadBytes <= 0) {
            return String.format("AEDAT-4 %s: no packet payloads written",
                    Aedat4Compression.nameOf(compression));
        }
        return "AEDAT-4 " + Aedat4Compression.formatPayloadCompression(
                compression, uncompressedPayloadBytes, compressedPayloadBytes);
    }

    /** {@code compressed / uncompressed} payload ratio, or 1 if nothing written. */
    public double getPayloadCompressionRatio() {
        if (uncompressedPayloadBytes <= 0) {
            return 1.0;
        }
        return compressedPayloadBytes / (double) uncompressedPayloadBytes;
    }

    @Override
    public String toString() {
        long events = 0;
        long frames = 0;
        long imuSamples = 0;
        for (DataDefinition d : dataDefinitions) {
            int rem = d.streamId % Aedat4CameraTrack.STREAMS_PER_CAMERA;
            if (rem < 0) {
                rem += Aedat4CameraTrack.STREAMS_PER_CAMERA;
            }
            if (rem == 0) {
                events += d.numElements;
            } else if (rem == 1) {
                frames += d.numElements;
            } else if (rem == 2) {
                imuSamples += d.numElements;
            }
        }
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(3);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("AEDAT-4 %s: %s events, %s frames, %s IMU samples",
                Aedat4Compression.nameOf(compression),
                eng.format((double) events).trim(),
                eng.format((double) frames).trim(),
                eng.format((double) imuSamples).trim()));
        long tMin = Long.MAX_VALUE;
        long tMax = Long.MIN_VALUE;
        for (DataDefinition d : dataDefinitions) {
            if (d.timestampStart > 0 && d.timestampStart < tMin) {
                tMin = d.timestampStart;
            }
            if (d.timestampEnd > tMax) {
                tMax = d.timestampEnd;
            }
        }
        if (tMax > tMin && tMin != Long.MAX_VALUE) {
            sb.append(", duration=").append(eng.format((tMax - tMin) * 1e-6).trim()).append("s");
        }
        if (uncompressedPayloadBytes > 0) {
            sb.append("; ").append(Aedat4Compression.formatPayloadCompression(
                    compression, uncompressedPayloadBytes, compressedPayloadBytes));
        }
        return sb.toString();
    }

    private static long[] ensureLongs(long[] a, int n) {
        return a == null || a.length < n ? new long[n] : a;
    }

    private static short[] ensureShorts(short[] a, int n) {
        return a == null || a.length < n ? new short[n] : a;
    }

    private static boolean[] ensureBooleans(boolean[] a, int n) {
        return a == null || a.length < n ? new boolean[n] : a;
    }

    private static final class DataDefinition {
        final long byteOffset;
        final int streamId;
        final int size;
        final long numElements;
        final long timestampStart;
        final long timestampEnd;

        DataDefinition(long byteOffset, int streamId, int size, long numElements, long timestampStart, long timestampEnd) {
            this.byteOffset = byteOffset;
            this.streamId = streamId;
            this.size = size;
            this.numElements = numElements;
            this.timestampStart = timestampStart;
            this.timestampEnd = timestampEnd;
        }
    }
}
