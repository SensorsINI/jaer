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
import net.sf.jaer.chip.AEChip;
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

/** Writes uncompressed AEDAT-4 files with DV-compatible FlatBuffers packets. */
public class Aedat4FileOutputStream implements Closeable {

    public static final byte[] VERSION_LINE = new byte[]{'#', '!', 'A', 'E', 'R', '-', 'D', 'A', 'T', '4', '.', '0', '\r', '\n'};
    public static final int STREAM_EVENTS = 0;
    public static final int STREAM_FRAMES = 1;
    public static final int STREAM_IMU = 2;

    private final FileOutputStream outputStream;
    private final FileChannel channel;
    private final AEChip chip;
    private final long baseUs;
    private final List<DataDefinition> dataDefinitions = new ArrayList<>();
    private final long headerPosition;
    private byte[] headerBytes;
    private boolean closed;

    public Aedat4FileOutputStream(File file, AEChip chip) throws IOException {
        this(new FileOutputStream(file), chip);
    }

    public Aedat4FileOutputStream(FileOutputStream outputStream, AEChip chip) throws IOException {
        this.outputStream = outputStream;
        this.channel = outputStream.getChannel();
        this.chip = chip;
        this.baseUs = System.currentTimeMillis() * 1000L;
        channel.write(ByteBuffer.wrap(VERSION_LINE));
        headerPosition = channel.position();
        headerBytes = buildIOHeader(-1);
        channel.write(ByteBuffer.wrap(headerBytes));
    }

    public synchronized void writeBundle(PacketBundle bundle) throws IOException {
        if (bundle == null || bundle.isEmpty()) {
            return;
        }
        for (TypedDataPacket packet : bundle) {
            if (packet == null || packet.isEmpty()) {
                continue;
            }
            if (packet.getPacketType() == PacketType.POLARITY && packet instanceof net.sf.jaer.event.EventPacket) {
                writeEventPacket((net.sf.jaer.event.EventPacket<?>) packet);
            } else if (packet instanceof FramePacket) {
                writeFramePacket((FramePacket) packet);
            } else if (packet instanceof ImuPacket) {
                writeImuPacket((ImuPacket) packet);
            }
        }
    }

    private void writeEventPacket(net.sf.jaer.event.EventPacket<?> packet) throws IOException {
        int n = packet.getSize();
        long[] timestamps = new long[n];
        short[] xs = new short[n];
        short[] ys = new short[n];
        boolean[] polarities = new boolean[n];
        int i = 0;
        for (Object object : packet) {
            BasicEvent event = (BasicEvent) object;
            timestamps[i] = toUnixUs(event.timestamp);
            xs[i] = event.x;
            ys[i] = event.y;
            polarities[i] = !(event instanceof PolarityEvent) || ((PolarityEvent) event).polarity == PolarityEvent.Polarity.On;
            i++;
            if (i == n) {
                break;
            }
        }
        FlatBufferBuilder builder = new FlatBufferBuilder(Math.max(1024, n * 16 + 64));
        int vector = net.sf.jaer.eventio.aedat4.dv.EventPacket.createElementsVector(builder, timestamps, xs, ys, polarities);
        int root = net.sf.jaer.eventio.aedat4.dv.EventPacket.createEventPacket(builder, vector);
        builder.finishSizePrefixed(root, "EVTS");
        byte[] payload = builder.sizedByteArray();
        writePacket(STREAM_EVENTS, payload, n, n == 0 ? 0 : timestamps[0], n == 0 ? 0 : timestamps[n - 1]);
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
        long byteOffset = channel.position();
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(streamId);
        header.putInt(payload.length);
        header.flip();
        channel.write(header);
        channel.write(ByteBuffer.wrap(payload));
        dataDefinitions.add(new DataDefinition(byteOffset, streamId, payload.length, numElements, timestampStart, timestampEnd));
    }

    private long toUnixUs(long relativeUs) {
        return baseUs + relativeUs;
    }

    private byte[] buildIOHeader(long dataTablePosition) {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);
        int info = builder.createString(Aedat4InfoNode.build(chip));
        int root = IOHeader.createIOHeader(builder, CompressionType.NONE, dataTablePosition, info);
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
        if (patchedHeader.length == headerBytes.length) {
            long end = channel.position();
            channel.position(headerPosition);
            channel.write(ByteBuffer.wrap(patchedHeader));
            channel.position(end);
            headerBytes = patchedHeader;
        }
        closed = true;
        outputStream.close();
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
        return String.format("AEDAT-4: %s events, %s frames, %s IMU samples",
                eng.format((double) events).trim(),
                eng.format((double) frames).trim(),
                eng.format((double) imuSamples).trim());
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
