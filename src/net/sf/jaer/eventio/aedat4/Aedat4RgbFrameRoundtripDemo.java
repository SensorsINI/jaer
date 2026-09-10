package net.sf.jaer.eventio.aedat4;

import java.io.File;
import net.sf.jaer.chip.opencv.OpenCvFrameCamera;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;

/**
 * RGB {@link FramePacket} AEDAT-4 {@code OPENCV_8U_C3} roundtrip.
 *
 * {@code java -cp "build/classes;jars/*;lib/*" net.sf.jaer.eventio.aedat4.Aedat4RgbFrameRoundtripDemo}
 */
public final class Aedat4RgbFrameRoundtripDemo {

    private Aedat4RgbFrameRoundtripDemo() {
    }

    public static void main(String[] args) throws Exception {
        OpenCvFrameCamera chip = new OpenCvFrameCamera();
        chip.setSizeX(2);
        chip.setSizeY(2);
        FramePacket frame = new FramePacket(2, 2, FramePacket.ColorMode.RGB);
        short[] p = frame.getPixels();
        // jAER bottom origin: row y=0 is bottom. R,G,B at (0,0)
        p[0] = 10;
        p[1] = 20;
        p[2] = 30;
        p[3] = 40;
        p[4] = 50;
        p[5] = 60;
        p[6] = 70;
        p[7] = 80;
        p[8] = 90;
        p[9] = 100;
        p[10] = 110;
        p[11] = 120;
        frame.setTimestampStartUs(1000);
        frame.setTimestampEndUs(2000);
        PacketBundle bundle = new PacketBundle();
        bundle.add(frame);
        File file = File.createTempFile("jaer-aedat4-rgb-frame", ".aedat4");
        try (Aedat4FileOutputStream out = new Aedat4FileOutputStream(file, chip, CompressionType.NONE, 1_700_000_000_000_000L)) {
            out.writeBundle(bundle);
        }
        Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip, null);
        try {
            if (!in.hasFramePackets()) {
                throw new AssertionError("expected FRME packets");
            }
            FramePacket decoded = null;
            for (Aedat4FileInputStream.RecordedPacket rec : in.packetsInRecordOrder()) {
                if (rec.kind == Aedat4FileInputStream.RecordedPacket.Kind.FRAME) {
                    decoded = in.decodeRecordedFrame(rec);
                    break;
                }
            }
            if (decoded == null) {
                throw new AssertionError("no decoded frame");
            }
            if (decoded.getWidth() != 2 || decoded.getHeight() != 2
                    || decoded.getColorMode() != FramePacket.ColorMode.RGB) {
                throw new AssertionError("geometry " + decoded);
            }
            short[] d = decoded.getPixels();
            int r = (d[0] & 0xffff) >> 8;
            int g = (d[1] & 0xffff) >> 8;
            int b = (d[2] & 0xffff) >> 8;
            if (r != 10 || g != 20 || b != 30) {
                throw new AssertionError("pixel (0,0) RGB got " + r + "," + g + "," + b);
            }
        } finally {
            in.close();
        }
        System.out.println("AEDAT4_RGB_FRAME_ROUNDTRIP PASS " + file.getAbsolutePath());
    }
}
