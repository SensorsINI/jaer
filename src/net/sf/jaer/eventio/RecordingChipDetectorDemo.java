package net.sf.jaer.eventio;

import java.util.ArrayList;
import java.util.List;
import ch.unizh.ini.jaer.chip.flyeye.FlyEye;
import eu.seebetter.ini.chips.davis.DAVIS240C;
import eu.seebetter.ini.chips.davis.Davis346red;
import net.sf.jaer.chip.AEChip;
import prophesee.chip.PropheseeIMX636HD;

/**
 * Recording chip detection falls back to the AEChip allowlist when the
 * viewer's Sensor menu does not include the camera.
 *
 * {@code java -cp "build/classes;jars/*;lib/*" net.sf.jaer.eventio.RecordingChipDetectorDemo}
 */
public final class RecordingChipDetectorDemo {

    public static void main(String[] args) {
        testFlyEyeNotOnMenuResolvesFromAllowlist();
        testNumericExportFilenameIsNotAChip();
        testJaerConventionFilenameStillDetected();
        System.out.println("RECORDING_CHIP_DETECTOR PASS");
    }

    private static void testNumericExportFilenameIsNotAChip() {
        RecordingChipDetector.Hint hint = RecordingChipDetector.fromFilename("3-export.aedat4");
        assertTrue(hint == null, "3-export.aedat4 must not be a filename chip hint, got " + hint);
        List<Class<? extends AEChip>> loaded = new ArrayList<>();
        loaded.add(DAVIS240C.class);
        loaded.add(PropheseeIMX636HD.class);
        loaded.add(Davis346red.class);
        Class<? extends AEChip> byThree = RecordingChipDetector.resolve(
                new RecordingChipDetector.Hint("3", null, null, "filename"), loaded);
        assertTrue(byThree == null, "hint '3' must not resolve to " + byThree);
        assertTrue("DAVIS240C-3".equals(
                RecordingChipDetector.ensureChipFilenamePrefix("3", "DAVIS240C")),
                "Save As prefix for 3.h5, got "
                        + RecordingChipDetector.ensureChipFilenamePrefix("3", "DAVIS240C"));
        assertTrue("DAVIS240C-2016-02-22".equals(
                RecordingChipDetector.ensureChipFilenamePrefix("DAVIS240C-2016-02-22", "DAVIS240C")),
                "already-prefixed basename must stay");
    }

    private static void testJaerConventionFilenameStillDetected() {
        RecordingChipDetector.Hint hint = RecordingChipDetector.fromFilename(
                "DAVIS240C-2016-02-22T14-53-11+0100-00000075-0.aedat");
        assertTrue(hint != null && "DAVIS240C".equals(hint.name),
                "jAER Chip-datetime filename, got " + hint);
    }

    private static void testFlyEyeNotOnMenuResolvesFromAllowlist() {
        RecordingChipDetector.Hint hint = new RecordingChipDetector.Hint(
                "FlyEye", 240, 128, "aedat4-stream-0");
        List<Class<? extends AEChip>> menu = new ArrayList<>();
        menu.add(Davis346red.class);
        Class<? extends AEChip> onMenu = RecordingChipDetector.resolve(hint, menu);
        assertTrue(onMenu == null, "FlyEye must not match a Davis346red-only menu, got " + onMenu);
        Class<? extends AEChip> fromAllowlist = RecordingChipDetector.resolvePreferringLoaded(hint, menu);
        assertTrue(fromAllowlist == FlyEye.class,
                "FlyEye recording must resolve from allowlist when missing from menu, got "
                + fromAllowlist);
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
