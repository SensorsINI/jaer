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
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;

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
        verifyOwnedConstructorClosesOnInitializationFailure();

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
}
