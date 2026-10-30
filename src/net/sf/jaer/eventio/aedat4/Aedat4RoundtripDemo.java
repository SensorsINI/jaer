package net.sf.jaer.eventio.aedat4;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;
import eu.seebetter.ini.chips.davis.imu.IMUSample;

/** Minimal AEDAT-4 write/decode smoke test. */
public class Aedat4RoundtripDemo {

    public static void main(String[] args) throws Exception {
        File file = File.createTempFile("jaer-aedat4-roundtrip", ".aedat4");
        PacketBundle bundle = new PacketBundle();
        net.sf.jaer.event.EventPacket<PolarityEvent> events = new net.sf.jaer.event.EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        for (int i = 0; i < 4; i++) {
            PolarityEvent event = out.nextOutput();
            event.timestamp = 1000 + i;
            event.x = (short) (10 + i);
            event.y = (short) (20 + i);
            event.setPolarity((i & 1) == 0 ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
        }
        if (events.getSize() != 4) {
            throw new IllegalStateException("Demo packet size is " + events.getSize());
        }
        bundle.add(events);
        try (Aedat4FileOutputStream output = new Aedat4FileOutputStream(file, null)) {
            java.lang.reflect.Field packetHeader = Aedat4FileOutputStream.class.getDeclaredField("packetHeader");
            packetHeader.setAccessible(true);
            if (!((ByteBuffer) packetHeader.get(output)).isDirect()) {
                throw new IllegalStateException("Reusable packet header must be direct");
            }
            output.writeBundle(bundle);
        }
        verifyUncompressedSizeFromHeaders();
        verifyOwnedConstructorClosesOnInitializationFailure();
        verifyImuRebaseOnlyForHostClock();
        verifyMegaPacketFramesMatchActualEventTime();
        verifyPolarityRowsAt10Hz();

        verifyDvFileDataTable(file);
        int decoded = decodeFirstEventPacketLength(file);
        File rerecorded = File.createTempFile("jaer-aedat4-rerecord-roundtrip", ".aedat4");
        Aedat4Lz4Rerecorder.rerecord(file, rerecorded, null);
        verifyDvFileDataTable(rerecorded);
        int rerecordedDecoded = decodeFirstEventPacketLength(rerecorded);
        boolean pass = decoded == 4 && rerecordedDecoded == 4;
        // Verify struct layout: x/y/polarity must survive write (regresses the pad(1) bug).
        net.sf.jaer.eventio.aedat4.dv.EventPacket packet = firstEventPacket(file);
        for (int i = 0; i < 4; i++) {
            net.sf.jaer.eventio.aedat4.dv.Event e = packet.elements(i);
            short expectX = (short) (10 + i);
            short expectY = (short) (20 + i);
            boolean expectOn = (i & 1) == 0;
            if (e.x() != expectX || e.y() != expectY || e.polarity() != expectOn) {
                pass = false;
                System.out.println("FAIL field mismatch i=" + i
                        + " got x=" + e.x() + " y=" + e.y() + " pol=" + e.polarity()
                        + " expect x=" + expectX + " y=" + expectY + " pol=" + expectOn);
            }
        }
        System.out.println((pass ? "PASS" : "FAIL") + " AEDAT-4 roundtrip events=" + decoded
                + " rerecordedEvents=" + rerecordedDecoded + " file=" + file.getAbsolutePath()
                + " rerecordedFile=" + rerecorded.getAbsolutePath());
        if (!pass) {
            System.exit(1);
        }
    }

    /** Codec headers must expose uncompressed size from a 32-byte peek (File info). */
    private static void verifyUncompressedSizeFromHeaders() throws IOException {
        byte[] raw = new byte[4096];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i * 31);
        }
        int[] codecs = {
            net.sf.jaer.eventio.aedat4.dv.CompressionType.LZ4,
            net.sf.jaer.eventio.aedat4.dv.CompressionType.ZSTD,
            net.sf.jaer.eventio.aedat4.dv.CompressionType.ZSTD_HIGH
        };
        for (int codec : codecs) {
            byte[] packed = Aedat4Compression.compress(raw, codec);
            int peekLen = Math.min(Aedat4Compression.UNCOMPRESSED_SIZE_HEADER_BYTES, packed.length);
            byte[] prefix = java.util.Arrays.copyOf(packed, peekLen);
            long size = Aedat4Compression.uncompressedSize(prefix, codec, packed.length);
            if (size != raw.length) {
                throw new IllegalStateException("uncompressedSize peek failed for "
                        + Aedat4Compression.nameOf(codec) + ": got " + size + " expected " + raw.length
                        + " compressed=" + packed.length + " peek=" + peekLen);
            }
        }
    }

    /** A file-owning constructor must close its stream if initialization aborts. */
    private static void verifyOwnedConstructorClosesOnInitializationFailure() throws Exception {
        File failed = File.createTempFile("jaer-aedat4-constructor-failure", ".aedat4");
        CountingFileOutputStream stream = new CountingFileOutputStream(failed);
        net.sf.jaer.chip.AEChip throwingChip = new org.objenesis.ObjenesisStd()
                .newInstance(ThrowingSnapshotChip.class);
        Constructor<Aedat4FileOutputStream> constructor = Aedat4FileOutputStream.class.getDeclaredConstructor(
                FileOutputStream.class, net.sf.jaer.chip.AEChip.class, int.class, long.class,
                net.sf.jaer.eventio.RecordingConfigurationSnapshot.class, boolean.class);
        constructor.setAccessible(true);
        try {
            constructor.newInstance(stream, throwingChip,
                    net.sf.jaer.eventio.aedat4.dv.CompressionType.LZ4,
                    System.currentTimeMillis() * 1000L, null, true);
            throw new IllegalStateException("Expected constructor initialization failure");
        } catch (InvocationTargetException expected) {
            if (!(expected.getCause() instanceof InjectedInitializationFailure)) {
                throw expected;
            }
        }
        if (stream.closeCalls != 1 || stream.getChannel().isOpen()) {
            throw new IllegalStateException("Owned failed constructor did not close its stream exactly once");
        }
        if (!failed.delete()) {
            failed.deleteOnExit();
        }
        System.out.println("PASS owned constructor failure closes stream");
    }

    private static final class ThrowingSnapshotChip extends net.sf.jaer.chip.AEChip {
        @Override
        public net.sf.jaer.biasgen.Biasgen getBiasgen() {
            throw new InjectedInitializationFailure();
        }
    }

    private static final class InjectedInitializationFailure extends RuntimeException {
    }

    private static final class CountingFileOutputStream extends FileOutputStream {
        int closeCalls;
        private boolean closing;

        CountingFileOutputStream(File file) throws IOException {
            super(file);
        }

        @Override
        public void close() throws IOException {
            if (closing) {
                super.close();
                return;
            }
            closeCalls++;
            closing = true;
            try {
                super.close();
            } finally {
                closing = false;
            }
        }
    }

    private static void verifyDvFileDataTable(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file); FileChannel channel = input.getChannel()) {
            channel.position(Aedat4FileOutputStream.VERSION_LINE.length);
            ByteBuffer headerBuffer = readSizePrefixed(channel);
            net.sf.jaer.eventio.aedat4.dv.IOHeader header =
                    net.sf.jaer.eventio.aedat4.dv.IOHeader.getSizePrefixedRootAsIOHeader(headerBuffer);
            int compression = Aedat4Compression.clamp(header.compression());
            if (compression == net.sf.jaer.eventio.aedat4.dv.CompressionType.NONE) {
                throw new IllegalStateException("Demo requires compressed output");
            }
            long tablePosition = header.dataTablePosition();
            long tableBytes = channel.size() - tablePosition;
            if (tablePosition < channel.position() || tableBytes <= 0 || tableBytes > Integer.MAX_VALUE) {
                throw new IllegalStateException("Invalid FileDataTable region");
            }
            channel.position(tablePosition);
            ByteBuffer encoded = ByteBuffer.allocate((int) tableBytes);
            readFully(channel, encoded);
            byte[] compressed = encoded.array();
            byte[] flat;
            try {
                flat = Aedat4Compression.decompress(compressed, compression);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("FileDataTable must use IOHeader compression", e);
            }
            net.sf.jaer.eventio.aedat4.dv.FileDataTable table =
                    net.sf.jaer.eventio.aedat4.dv.FileDataTable.getSizePrefixedRootAsFileDataTable(
                            ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN));
            if (table.tableLength() != 1) {
                throw new IllegalStateException("Expected one FileDataTable entry, got " + table.tableLength());
            }
            net.sf.jaer.eventio.aedat4.dv.FileDataDefinition definition = table.table(0);
            long payloadOffset = definition.byteOffset();
            int payloadSize = definition.packetInfoSize();
            if (payloadOffset < 8 || payloadOffset + payloadSize > tablePosition) {
                throw new IllegalStateException("FileDataTable payload offset is out of bounds");
            }
            channel.position(payloadOffset - 8);
            ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, packetHeader);
            packetHeader.flip();
            if (packetHeader.getInt() != definition.packetInfoStreamID()
                    || packetHeader.getInt() != payloadSize) {
                throw new IllegalStateException("FileDataTable byteOffset must point to packet payload");
            }
            System.out.println("PASS DV FileDataTable compression and payload offsets");
        }
    }

    private static net.sf.jaer.eventio.aedat4.dv.EventPacket firstEventPacket(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file); FileChannel channel = input.getChannel()) {
            channel.position(Aedat4FileOutputStream.VERSION_LINE.length);
            ByteBuffer header = readSizePrefixed(channel);
            int compression = Aedat4Compression.clamp(
                    net.sf.jaer.eventio.aedat4.dv.IOHeader.getSizePrefixedRootAsIOHeader(header).compression());
            ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            while (channel.position() + 8 <= channel.size()) {
                packetHeader.clear();
                readFully(channel, packetHeader);
                packetHeader.flip();
                int streamId = packetHeader.getInt();
                int size = packetHeader.getInt();
                ByteBuffer payload = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
                readFully(channel, payload);
                payload.flip();
                if (streamId == Aedat4FileOutputStream.STREAM_EVENTS) {
                    byte[] flat = new byte[payload.remaining()];
                    payload.get(flat);
                    flat = Aedat4Compression.decompress(flat, compression);
                    return net.sf.jaer.eventio.aedat4.dv.EventPacket.getSizePrefixedRootAsEventPacket(
                            ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN));
                }
            }
            throw new IllegalStateException("No EVTS packet found");
        }
    }

    private static int decodeFirstEventPacketLength(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file); FileChannel channel = input.getChannel()) {
            channel.position(Aedat4FileOutputStream.VERSION_LINE.length);
            ByteBuffer header = readSizePrefixed(channel);
            int compression = Aedat4Compression.clamp(
                    net.sf.jaer.eventio.aedat4.dv.IOHeader.getSizePrefixedRootAsIOHeader(header).compression());
            ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            int packetsSeen = 0;
            while (channel.position() + 8 <= channel.size()) {
                packetHeader.clear();
                readFully(channel, packetHeader);
                packetHeader.flip();
                int streamId = packetHeader.getInt();
                int size = packetHeader.getInt();
                if (size < 0 || size > channel.size() - channel.position()) {
                    throw new IllegalStateException("Invalid packet header streamId=" + streamId + " size=" + size);
                }
                ByteBuffer payload = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
                readFully(channel, payload);
                payload.flip();
                packetsSeen++;
                if (streamId == Aedat4FileOutputStream.STREAM_EVENTS) {
                    byte[] flat = new byte[payload.remaining()];
                    payload.get(flat);
                    flat = Aedat4Compression.decompress(flat, compression);
                    return net.sf.jaer.eventio.aedat4.dv.EventPacket.getSizePrefixedRootAsEventPacket(
                            ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN)).elementsLength();
                }
            }
            throw new IllegalStateException("No EVTS packet found; packetsSeen=" + packetsSeen);
        }
    }

    private static ByteBuffer readSizePrefixed(FileChannel channel) throws Exception {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, sizeBuffer);
        sizeBuffer.flip();
        int size = sizeBuffer.getInt();
        ByteBuffer data = ByteBuffer.allocate(size + 4).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(size);
        readFully(channel, data);
        data.flip();
        return data;
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws Exception {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new java.io.EOFException();
            }
        }
    }

    /**
     * A packet longer than 500 ms is read by actual event time. A 20 ms slice
     * must not include a frame at 50 ms. A 100 ms slice must, and its events
     * must reach that frame.
     */
    private static void verifyMegaPacketFramesMatchActualEventTime() throws Exception {
        File file = File.createTempFile("jaer-aedat4-megapacket-frames", ".aedat4");
        final int n = 5000;
        // Span must reach the 500 ms timeslice-scan threshold. Shorter packets
        // interpolate the table and would attach this frame by packet time.
        final int lastTs = 600_000;
        final long base = 1_000_000L;
        long[] ts = new long[n];
        short[] xs = new short[n];
        short[] ys = new short[n];
        boolean[] on = new boolean[n];
        for (int i = 0; i < n; i++) {
            int cam = i < 4000 ? i : 4000 + (int) ((i - 4000) * (long) (lastTs - 4000) / 999);
            ts[i] = base + cam;
            xs[i] = 1;
            ys[i] = 1;
            on[i] = true;
        }
        com.google.flatbuffers.FlatBufferBuilder builder = new com.google.flatbuffers.FlatBufferBuilder(n * 16 + 64);
        int vector = net.sf.jaer.eventio.aedat4.dv.EventPacket.createElementsVector(builder, ts, xs, ys, on, n);
        int root = net.sf.jaer.eventio.aedat4.dv.EventPacket.createEventPacket(builder, vector);
        builder.finishSizePrefixed(root, "EVTS");
        byte[] payload = builder.sizedByteArray();
        PacketBundle bundle = new PacketBundle();
        net.sf.jaer.event.FramePacket frame = new net.sf.jaer.event.FramePacket(8, 8,
                net.sf.jaer.event.FramePacket.ColorMode.GRAYSCALE);
        frame.setTimestampStartUs(50_000);
        frame.setTimestampEndUs(50_100);
        frame.setExposureUs(100);
        bundle.add(frame);
        AEChip chip = new org.objenesis.ObjenesisStd()
                .newInstance(eu.seebetter.ini.chips.davis.Davis346blue.class);
        // Objenesis skips the constructor, so setSizeX would notify a null observer list.
        java.lang.reflect.Field sizeX = net.sf.jaer.chip.Chip2D.class.getDeclaredField("sizeX");
        java.lang.reflect.Field sizeY = net.sf.jaer.chip.Chip2D.class.getDeclaredField("sizeY");
        sizeX.setAccessible(true);
        sizeY.setAccessible(true);
        sizeX.setInt(chip, 346);
        sizeY.setInt(chip, 260);
        try (Aedat4FileOutputStream output = new Aedat4FileOutputStream(new FileOutputStream(file), chip,
                net.sf.jaer.eventio.aedat4.dv.CompressionType.NONE, base)) {
            // One copied packet keeps the long span. writeBundle would close a row every 100 ms.
            output.writeCopiedPacket(Aedat4FileOutputStream.STREAM_EVENTS, payload, n, ts[0], ts[n - 1]);
            output.writeBundle(bundle);
        }
        Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
        try {
            net.sf.jaer.aemonitor.AEPacketRaw early = in.readPacketByTime(20_000);
            if (frameCount(in) != 0) {
                throw new IllegalStateException("20 ms slice attached the 50 ms frame");
            }
            int earlyLast = lastTimestamp(early);
            if (earlyLast < 0 || earlyLast >= 50_000) {
                throw new IllegalStateException("20 ms slice last timestamp " + earlyLast);
            }
            in.rewind();
            net.sf.jaer.aemonitor.AEPacketRaw full = in.readPacketByTime(100_000);
            if (frameCount(in) != 1) {
                throw new IllegalStateException("100 ms slice must include the 50 ms frame");
            }
            int fullLast = lastTimestamp(full);
            if (fullLast < 50_000) {
                throw new IllegalStateException(
                        "100 ms slice events end at " + fullLast + " us, before the 50 ms frame");
            }
        } finally {
            in.close();
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
        System.out.println("PASS mega-packet APS frames follow actual event time");
    }

    private static int frameCount(Aedat4FileInputStream in) {
        PacketBundle typed = new PacketBundle();
        in.appendTypedPackets(typed);
        int frames = 0;
        for (net.sf.jaer.event.TypedDataPacket p : typed) {
            if (p instanceof net.sf.jaer.event.FramePacket) {
                frames++;
            }
        }
        return frames;
    }

    private static int lastTimestamp(net.sf.jaer.aemonitor.AEPacketRaw packet) {
        int n = packet.getNumEvents();
        if (n <= 0) {
            return -1;
        }
        return packet.getTimestamps()[n - 1];
    }

    /**
     * Polarity rows close every 100 ms of event time. Five writes at one
     * timestamp stay one row.
     */
    private static void verifyPolarityRowsAt10Hz() throws Exception {
        File spaced = File.createTempFile("jaer-aedat4-10hz-", ".aedat4");
        File repeated = File.createTempFile("jaer-aedat4-10hz-same-", ".aedat4");
        File lossy = File.createTempFile("jaer-aedat4-10hz-lbev-", ".aedat4");
        try {
            int[] stamps = {0, 50_000, 150_000, 250_000};
            try (Aedat4FileOutputStream output = new Aedat4FileOutputStream(spaced, null,
                    net.sf.jaer.eventio.aedat4.dv.CompressionType.NONE, 1_000_000L)) {
                output.writeBundle(eventsAt(stamps));
            }
            if (Aedat4Concat.packetTimeRanges(spaced).size() != 3) {
                throw new IllegalStateException("100 ms boundaries should be 3 EVTS rows, got "
                        + Aedat4Concat.packetTimeRanges(spaced).size());
            }
            try (Aedat4FileOutputStream output = new Aedat4FileOutputStream(repeated, null,
                    net.sf.jaer.eventio.aedat4.dv.CompressionType.NONE, 1_000_000L)) {
                for (int i = 0; i < 5; i++) {
                    output.writeBundle(eventsAt(new int[] {1_000}));
                }
            }
            if (Aedat4Concat.packetTimeRanges(repeated).size() != 1) {
                throw new IllegalStateException("same timestamp across writes should be 1 row, got "
                        + Aedat4Concat.packetTimeRanges(repeated).size());
            }
            Aedat4FileInputStream repeatedIn = new Aedat4FileInputStream(repeated, null);
            try {
                if (repeatedIn.getIndexedEventCount() != 5) {
                    throw new IllegalStateException("repeated writes lost events: " + repeatedIn.getIndexedEventCount());
                }
            } finally {
                repeatedIn.close();
            }
            // Quantizing can shrink a 100 ms gap by almost one bin, so these gaps stay over 100 ms.
            int[] lossyStamps = {0, 50_000, 160_000, 270_000};
            try (Aedat4FileOutputStream output = new Aedat4FileOutputStream(lossy, null,
                    net.sf.jaer.eventio.aedat4.dv.CompressionType.NONE, 1_000_000L, null,
                    Aedat4LossyTimeBins.SHIFT_DEFAULT)) {
                output.writeBundle(eventsAt(lossyStamps));
            }
            if (Aedat4Concat.packetTimeRanges(lossy).size() != 3) {
                throw new IllegalStateException("lossy 100 ms boundaries should be 3 rows, got "
                        + Aedat4Concat.packetTimeRanges(lossy).size());
            }
        } finally {
            spaced.delete();
            repeated.delete();
            lossy.delete();
        }
        System.out.println("PASS polarity rows close at 10 Hz of event time");
    }

    private static PacketBundle eventsAt(int[] timestamps) {
        PacketBundle bundle = new PacketBundle();
        net.sf.jaer.event.EventPacket<PolarityEvent> events = new net.sf.jaer.event.EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        for (int i = 0; i < timestamps.length; i++) {
            PolarityEvent event = out.nextOutput();
            event.timestamp = timestamps[i];
            event.x = (short) i;
            event.y = 1;
            event.setPolarity(PolarityEvent.Polarity.On);
        }
        bundle.add(events);
        return bundle;
    }

    /**
     * Davis shares the DVS 1 µs clock — keep IMU Unix times. Mini/Micro is
     * host-stamped and must rebase onto the polarity window.
     */
    private static void verifyImuRebaseOnlyForHostClock() throws Exception {
        if (Aedat4FileOutputStream.imuHostStamped(null)) {
            throw new IllegalStateException("null chip must not look Mini/Micro host-stamped");
        }
        AEChip micro = new org.objenesis.ObjenesisStd()
                .newInstance(ch.unizh.ini.jaer.chip.retina.DVXplorerMicro.class);
        AEChip davis = new org.objenesis.ObjenesisStd()
                .newInstance(eu.seebetter.ini.chips.davis.Davis346blue.class);
        if (!Aedat4FileOutputStream.imuHostStamped(micro)) {
            throw new IllegalStateException("DVXplorerMicro must be host-stamped");
        }
        if (Aedat4FileOutputStream.imuHostStamped(davis)) {
            throw new IllegalStateException("Davis346blue must not rebase IMU onto DVS");
        }
        long davisOff = imuMinusEvtsStartUs(writeImuOffsetFile(davis));
        long microOff = imuMinusEvtsStartUs(writeImuOffsetFile(micro));
        if (Math.abs(davisOff - 999_000L) > 5_000L) {
            throw new IllegalStateException("Davis IMU must keep ~999 ms offset from DVS, got " + davisOff + " us");
        }
        if (Math.abs(microOff) > 5_000L) {
            throw new IllegalStateException("Mini/Micro IMU must rebase onto DVS, got offset " + microOff + " us");
        }
        System.out.println("PASS IMU rebase gated Davis offset=" + davisOff + "us Micro offset=" + microOff + "us");
    }

    private static File writeImuOffsetFile(AEChip chip) throws Exception {
        File file = File.createTempFile("jaer-aedat4-imu-rebase-", ".aedat4");
        PacketBundle bundle = new PacketBundle();
        net.sf.jaer.event.EventPacket<PolarityEvent> events = new net.sf.jaer.event.EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        for (int i = 0; i < 4; i++) {
            PolarityEvent event = out.nextOutput();
            event.timestamp = 1000 + i;
            event.x = (short) (10 + i);
            event.y = (short) (20 + i);
            event.setPolarity((i & 1) == 0 ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
        }
        ImuPacket imu = new ImuPacket(3);
        for (int i = 0; i < 3; i++) {
            IMUSample sample = imu.nextOutput();
            sample.setFromPhysicalUnits(1_000_000 + i * 1000, 0, 0, 1, 0, 0, 0, 25);
        }
        bundle.add(events);
        bundle.add(imu);
        try (Aedat4FileOutputStream output = new Aedat4FileOutputStream(new FileOutputStream(file), chip,
                net.sf.jaer.eventio.aedat4.dv.CompressionType.LZ4,
                RecordingConfigurationSnapshot.captureFromChip(null))) {
            output.writeBundle(bundle);
        }
        return file;
    }

    private static long imuMinusEvtsStartUs(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file); FileChannel channel = input.getChannel()) {
            channel.position(Aedat4FileOutputStream.VERSION_LINE.length);
            ByteBuffer headerBuffer = readSizePrefixed(channel);
            net.sf.jaer.eventio.aedat4.dv.IOHeader header =
                    net.sf.jaer.eventio.aedat4.dv.IOHeader.getSizePrefixedRootAsIOHeader(headerBuffer);
            int compression = Aedat4Compression.clamp(header.compression());
            long tablePosition = header.dataTablePosition();
            channel.position(tablePosition);
            ByteBuffer encoded = ByteBuffer.allocate((int) (channel.size() - tablePosition));
            readFully(channel, encoded);
            byte[] flat = Aedat4Compression.decompress(encoded.array(), compression);
            net.sf.jaer.eventio.aedat4.dv.FileDataTable table =
                    net.sf.jaer.eventio.aedat4.dv.FileDataTable.getSizePrefixedRootAsFileDataTable(
                            ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN));
            Long evtsStart = null;
            Long imuStart = null;
            for (int i = 0; i < table.tableLength(); i++) {
                net.sf.jaer.eventio.aedat4.dv.FileDataDefinition def = table.table(i);
                int streamId = def.packetInfoStreamID();
                if (streamId == Aedat4FileOutputStream.STREAM_EVENTS && evtsStart == null) {
                    evtsStart = def.timestampStart();
                } else if (streamId == Aedat4FileOutputStream.STREAM_IMU && imuStart == null) {
                    imuStart = def.timestampStart();
                }
            }
            if (evtsStart == null || imuStart == null) {
                throw new IllegalStateException("Missing EVTS/IMUS table entries in " + file);
            }
            return imuStart - evtsStart;
        }
    }
}
