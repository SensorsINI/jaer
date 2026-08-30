/*
 * DavisExtractBundleDemo.java
 *
 * Smoke check for DavisFrameAssembler (jAER 3.0). Avoids constructing a full
 * Davis AEChip (OpenGL/biasgen).
 */
package eu.seebetter.ini.chips.davis;

import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketType;

/**
 * Standalone smoke demo for APS → {@link FramePacket} assembly (count-based EOF).
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

        // Early geometric "last" must not finish before W*H signal samples:
        // feed a 2x2 where pixLast is true on the first signal sample.
        DavisFrameAssembler asm2 = new DavisFrameAssembler(w, h, 1000);
        for (short[] xy : order) {
            asm2.process(200, t++, xy[0], xy[1], ApsDvsEvent.ReadoutType.ResetRead,
                    xy[0] == 0 && xy[1] == 0, xy[0] == 1 && xy[1] == 1, false);
        }
        FramePacket early = asm2.process(50, t++, (short) 0, (short) 0, ApsDvsEvent.ReadoutType.SignalRead, true, true, false);
        if (early != null) {
            System.out.println("FAIL: finished on early pixLast before W*H samples");
            System.exit(1);
        }
        for (int i = 1; i < 4; i++) {
            short[] xy = order[i];
            early = asm2.process(50, t++, xy[0], xy[1], ApsDvsEvent.ReadoutType.SignalRead, false, i == 3, false);
        }
        if (early == null) {
            System.out.println("FAIL: expected frame after W*H signal samples");
            System.exit(1);
        }

        // USB SOF must abandon a stuck half-frame so SignalRead is accepted again.
        DavisFrameAssembler asm3 = new DavisFrameAssembler(w, h, 1000);
        asm3.process(200, t++, (short) 0, (short) 0, ApsDvsEvent.ReadoutType.ResetRead, true, false, false);
        asm3.process(50, t++, (short) 0, (short) 0, ApsDvsEvent.ReadoutType.SignalRead, true, false, false);
        if (!asm3.isInFrame() || asm3.getSignalCount() != 1) {
            System.out.println("FAIL: expected in-progress frame before SOF resync");
            System.exit(1);
        }
        FramePacket sofAbandoned = asm3.onUsbFrameStart(t++);
        if (sofAbandoned != null) {
            System.out.println("FAIL: incomplete frame must not be emitted on SOF");
            System.exit(1);
        }
        if (!asm3.isInFrame() || asm3.getSignalCount() != 0) {
            System.out.println("FAIL: SOF should open a fresh frame");
            System.exit(1);
        }
        FramePacket afterSof = null;
        for (short[] xy : order) {
            asm3.process(200, t++, xy[0], xy[1], ApsDvsEvent.ReadoutType.ResetRead,
                    xy[0] == 0 && xy[1] == 0, xy[0] == 1 && xy[1] == 1, false);
        }
        for (int i = 0; i < 4; i++) {
            short[] xy = order[i];
            afterSof = asm3.process(50, t++, xy[0], xy[1], ApsDvsEvent.ReadoutType.SignalRead,
                    i == 0, i == 3, false);
        }
        if (afterSof == null) {
            System.out.println("FAIL: expected complete frame after SOF resync");
            System.exit(1);
        }
        FramePacket eofIncomplete = new DavisFrameAssembler(w, h, 1000).onUsbFrameEnd(t++);
        DavisFrameAssembler asm4 = new DavisFrameAssembler(w, h, 1000);
        asm4.onUsbFrameStart(t++);
        asm4.process(50, t++, (short) 0, (short) 0, ApsDvsEvent.ReadoutType.SignalRead, true, false, false);
        if (asm4.onUsbFrameEnd(t++) != null || !asm4.isInFrame() || asm4.getSignalCount() != 1) {
            System.out.println("FAIL: incomplete EOF should keep the frame open for late samples");
            System.exit(1);
        }
        if (eofIncomplete != null) {
            System.out.println("FAIL: EOF on idle assembler should be a no-op");
            System.exit(1);
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
