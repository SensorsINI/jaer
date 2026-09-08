package net.sf.jaer.eventio;

import java.util.ArrayList;
import java.util.List;
import ch.unizh.ini.jaer.chip.flyeye.FlyEye;
import eu.seebetter.ini.chips.davis.Davis346red;
import net.sf.jaer.chip.AEChip;

/**
 * Recording chip detection falls back to the AEChip allowlist when the
 * viewer's Sensor menu does not include the camera.
 *
 * {@code java -cp "build/classes;jars/*;lib/*" net.sf.jaer.eventio.RecordingChipDetectorDemo}
 */
public final class RecordingChipDetectorDemo {

    public static void main(String[] args) {
        testFlyEyeNotOnMenuResolvesFromAllowlist();
        System.out.println("RECORDING_CHIP_DETECTOR PASS");
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
