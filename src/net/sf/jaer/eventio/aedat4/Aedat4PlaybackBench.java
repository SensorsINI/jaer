package net.sf.jaer.eventio.aedat4;

import java.io.EOFException;
import java.io.File;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import prophesee.chip.PropheseeIMX636HD;

/**
 * Headless ConstantDuration playback of an AEDAT-4 file: times
 * {@code readPacketByTime} and chip {@code extractPacket}.
 *
 * <pre>
 * java -cp "build/classes:lib/*:jars/*" net.sf.jaer.eventio.aedat4.Aedat4PlaybackBench \
 *   "sampleData/PropheseeIMX636HD Metavision driving_sample street.aedat4" 20000
 * </pre>
 */
public final class Aedat4PlaybackBench {

    public static void main(String[] args) throws Exception {
        File file = new File(args.length > 0 ? args[0]
                : "sampleData/PropheseeIMX636HD Metavision driving_sample street.aedat4");
        int dtUs = args.length > 1 ? Integer.parseInt(args[1]) : 20_000;
        if (!file.isFile()) {
            System.err.println("missing file: " + file.getAbsolutePath());
            System.exit(2);
        }
        AEChip chip = new PropheseeIMX636HD();
        System.out.println("open " + file.getName() + " chip=" + chip.getClass().getSimpleName()
                + " dtUs=" + dtUs);
        long tOpen = System.nanoTime();
        Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
        System.out.printf("open %.0f ms  events=%d  duration=%.3fs  scanTimeslice=%s%n",
                (System.nanoTime() - tOpen) * 1e-6, in.size(), in.getDurationUs() * 1e-6,
                Boolean.toString(in.isScanTimesliceInPacket()));
        System.out.println(in.getFileInfo().replace('\n', ' '));

        // Warm the packet cache / JIT.
        for (int i = 0; i < 8; i++) {
            try {
                in.readPacketByTime(dtUs);
            } catch (EOFException e) {
                break;
            }
        }
        in.rewind();
        in.resetPlaybackProfile();

        long tWall = System.nanoTime();
        long nsExtract = 0;
        int maxEvents = 0;
        int slices = 0;
        try {
            while (true) {
                AEPacketRaw raw = in.readPacketByTime(dtUs);
                int n = raw.getNumEvents();
                if (n > maxEvents) {
                    maxEvents = n;
                }
                long t0 = System.nanoTime();
                EventPacket<?> typed = chip.getEventExtractor().extractPacket(raw);
                nsExtract += System.nanoTime() - t0;
                if (typed != null) {
                    typed.getSize();
                }
                slices++;
            }
        } catch (EOFException done) {
            // file consumed
        }
        double wallS = (System.nanoTime() - tWall) * 1e-9;
        double fileS = Math.max(1e-9, in.getDurationUs() * 1e-6);
        System.out.println(in.formatPlaybackProfile());
        System.out.printf("chip extractPacket  %7.1f ms  (%.2f ms/slice)%n",
                nsExtract * 1e-6, nsExtract * 1e-6 / Math.max(1, slices));
        System.out.printf("wall %.2fs  file %.3fs  realtimeFactor %.2fx  maxEvents/slice=%d%n",
                wallS, fileS, fileS / wallS, maxEvents);
        in.close();
    }
}
