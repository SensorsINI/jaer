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
    private final long baseUs;
    private final List<DataDefinition> dataDefinitions = new ArrayList<>();
    private final long headerPosition;
    private byte[] headerBytes;
    private boolean closed;
    /**
     * Camera timestamps are 32-bit µs; add 2^32 on wrap so AEDAT-4 Unix times
     * stay monotonic (12 h recordings). Shared across events/frames/IMU.
     */
    private final TimestampUnwrapper timestampUnwrapper = new TimestampUnwrapper();
    /** Uncompressed FlatBuffer packet payload bytes (before LZ4/ZSTD). */
    private long uncompressedPayloadBytes;
    /** Compressed packet payload bytes written to the file (same as uncompressed if NONE). */
    private long compressedPayloadBytes;
    private long[] evTimestamps;
    private short[] evXs;
    private short[] evYs;
    private boolean[] evPolarities;
    private FlatBufferBuilder eventBuilder;
    private final ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);

    public Aedat4FileOutputStream(File file, AEChip chip) throws IOException {
        this(new FileOutputStream(file), chip, CompressionType.LZ4);
    }

    public Aedat4FileOutputStream(File file, AEChip chip, int compression) throws IOException {
        this(new FileOutputStream(file), chip, compression);
    }

    public Aedat4FileOutputStream(FileOutputStream outputStream, AEChip chip) throws IOException {
        this(outputStream, chip, CompressionType.LZ4);
    }

    public Aedat4FileOutputStream(FileOutputStream outputStream, AEChip chip, int compression) throws IOException {
        this(outputStream, chip, compression, System.currentTimeMillis() * 1000L);
    }

    /**
     * @param baseUnixUs AEDAT-4 packet timestamps are Unix µs; playback stores
     *                   relative µs. Pass the source file's
     *                   {@code getAbsoluteStartingTimeMs() * 1000} to keep the
     *                   original timeline, or {@code <= 0} for wall-clock now.
     */
    public Aedat4FileOutputStream(File file, AEChip chip, int compression, long baseUnixUs) throws IOException {
        this(new FileOutputStream(file), chip, compression, baseUnixUs);
    }

    public Aedat4FileOutputStream(FileOutputStream outputStream, AEChip chip, int compression, long baseUnixUs)
            throws IOException {
        this.outputStream = outputStream;
        this.channel = outputStream.getChannel();
        this.chip = chip;
        this.compression = Aedat4Compression.clamp(compression);
        this.baseUs = baseUnixUs > 0 ? baseUnixUs : System.currentTimeMillis() * 1000L;
        channel.write(ByteBuffer.wrap(VERSION_LINE));
        headerPosition = channel.position();
        // FlatBuffers omits dataTablePosition when it equals the default (-1). Use a
        // non-default sentinel so the field is always present and close() can patch
        // the same-sized IOHeader with the real FileDataTable offset.
        headerBytes = buildIOHeader(DATA_TABLE_POSITION_PENDING);
        channel.write(ByteBuffer.wrap(headerBytes));
    }

    /** Sentinel written at open; replaced on {@link #close()} with the real table offset. */
    private static final long DATA_TABLE_POSITION_PENDING = -2L;

    public int getCompression() {
        return compression;
    }

    public long getEventsWritten() {
        return countStream(STREAM_EVENTS);
    }

    public long getFramesWritten() {
        return countStream(STREAM_FRAMES);
    }

    public long getImuSamplesWritten() {
        return countStream(STREAM_IMU);
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
        if (bundle == null || bundle.isEmpty()) {
            return;
        }
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
        writePacket(STREAM_EVENTS, payload, n, evTimestamps[0], evTimestamps[n - 1]);
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
        writePacket(STREAM_FRAMES, builder.sizedByteArray(), 1, start, end);
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
        writePacket(STREAM_IMU, builder.sizedByteArray(), n, first, last);
    }

    private void writePacket(int streamId, byte[] payload, long numElements, long timestampStart, long timestampEnd) throws IOException {
        ByteBuffer toWrite = Aedat4Compression.compressDirect(payload, compression);
        uncompressedPayloadBytes += payload.length;
        int compressedLen = toWrite.remaining();
        compressedPayloadBytes += compressedLen;
        long byteOffset = channel.position();
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
        return baseUs + timestampUnwrapper.unwrapUnsigned32(relativeUs);
    }

    private long toUnixUs(long relativeUs) {
        if (relativeUs >= Integer.MIN_VALUE && relativeUs <= Integer.MAX_VALUE) {
            return toUnixUs((int) relativeUs);
        }
        return baseUs + timestampUnwrapper.unwrapRaw(relativeUs);
    }

    private byte[] buildIOHeader(long dataTablePosition) {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);
        int info = builder.createString(Aedat4InfoNode.build(chip, compression));
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
        long tablePosition = channel.position();
        channel.write(ByteBuffer.wrap(buildFileDataTable()));
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
        closed = true;
        outputStream.close();
        log.info(formatCompressionSummary());
    }

    /**
     * Estimated packet-payload compression vs uncompressed FlatBuffers
     * (headers/FileDataTable excluded). Relative measure of LZ4/ZSTD gain.
     */
    public String formatCompressionSummary() {
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(3);
        if (uncompressedPayloadBytes <= 0) {
            return String.format("AEDAT-4 %s: no packet payloads written",
                    Aedat4Compression.nameOf(compression));
        }
        double pct = 100.0 * compressedPayloadBytes / (double) uncompressedPayloadBytes;
        return String.format(
                "AEDAT-4 %s: compressed to %.0f%% of raw (payload %sB -> %sB)",
                Aedat4Compression.nameOf(compression),
                pct,
                eng.format((double) uncompressedPayloadBytes).trim(),
                eng.format((double) compressedPayloadBytes).trim());
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
            if (d.streamId == STREAM_EVENTS) {
                events += d.numElements;
            } else if (d.streamId == STREAM_FRAMES) {
                frames += d.numElements;
            } else if (d.streamId == STREAM_IMU) {
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
            double pct = 100.0 * compressedPayloadBytes / (double) uncompressedPayloadBytes;
            sb.append(String.format("; compressed to %.0f%% of raw (%sB -> %sB)",
                    pct,
                    eng.format((double) uncompressedPayloadBytes).trim(),
                    eng.format((double) compressedPayloadBytes).trim()));
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
