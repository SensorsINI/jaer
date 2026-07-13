/*
 * DavisExtractBundleDemo.java
 *
 * Smoke check for DavisFrameAssembler (jAER 3.0 Phase 2). Avoids constructing a
 * full Davis AEChip (OpenGL/biasgen). extractBundle itself is compile-checked
 * and exercised when ViewLoop switches in Phase 3.
 */
package eu.seebetter.ini.chips.davis;

import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketType;

/**
 * Standalone smoke demo for APS → {@link FramePacket} assembly.
 */
public final class DavisExtractBundleDemo {

    private DavisExtractBundleDemo() {
    }

    public static void main(String[] args) {
        final int w = 2, h = 2;
        DavisFrameAssembler asm = new DavisFrameAssembler(w, h, 1000);
        PacketBundle bundle = new PacketBundle();
        int t = 1000;
        short[][] order = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};

        for (short[] xy : order) {
            boolean first = xy[0] == 0 && xy[1] == 0;
            boolean last = xy[0] == 1 && xy[1] == 1;
            FramePacket f = asm.process(200, t++, xy[0], xy[1], ApsDvsEvent.ReadoutType.ResetRead, first, last, false);
            if (f != null) {
                System.out.println("unexpected frame during reset");
                System.exit(1);
            }
        }
        FramePacket done = null;
        for (short[] xy : order) {
            boolean first = xy[0] == 0 && xy[1] == 0;
            boolean last = xy[0] == 1 && xy[1] == 1;
            FramePacket f = asm.process(50, t++, xy[0], xy[1], ApsDvsEvent.ReadoutType.SignalRead, first, last, false);
            if (f != null) {
                done = f;
                bundle.add(f);
            }
        }

        System.out.println(bundle);
        boolean ok = done != null
                && done.getPacketType() == PacketType.FRAME
                && done.getWidth() == 2
                && done.getHeight() == 2
                && done.getPixel(0, 0) == 150
                && done.getPixel(1, 1) == 150
                && bundle.getNumPackets() == 1;
        System.out.println("pix(0,0)=" + (done == null ? "null" : done.getPixel(0, 0)) + " (expect 150)");
        System.out.println(ok ? "PASS" : "FAIL");
        if (!ok) {
            System.exit(1);
        }
    }
}
