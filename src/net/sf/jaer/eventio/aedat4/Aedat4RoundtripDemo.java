package net.sf.jaer.eventio.aedat4;

import java.io.File;
import java.io.FileInputStream;
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
            output.writeBundle(bundle);
        }

        int decoded = decodeFirstEventPacketLength(file);
        boolean pass = decoded == 4;
        System.out.println((pass ? "PASS" : "FAIL") + " AEDAT-4 roundtrip events=" + decoded + " file=" + file.getAbsolutePath());
        if (!pass) {
            System.exit(1);
        }
    }

    private static int decodeFirstEventPacketLength(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file); FileChannel channel = input.getChannel()) {
            channel.position(Aedat4FileOutputStream.VERSION_LINE.length);
            ByteBuffer header = readSizePrefixed(channel);
            net.sf.jaer.eventio.aedat4.dv.IOHeader.getSizePrefixedRootAsIOHeader(header);
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
                    return net.sf.jaer.eventio.aedat4.dv.EventPacket.getSizePrefixedRootAsEventPacket(payload).elementsLength();
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
