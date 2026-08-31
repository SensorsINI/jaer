package ch.unizh.ini.jaer.chip.flyeye;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * DVS128 panoramic remap is bijective per camera, including overlap and flipX.
 */
public class FlyEyeGeometryTest {

    @Test
    public void nativeRoundTripsForOverlapAndFlip() {
        FlyEyeGeometry.assertBijection();
    }

    @Test
    public void panoramicWidthIsTwoNativeMinusOverlap() {
        assertEquals(256, FlyEyeGeometry.panoramicWidth(0));
        assertEquals(240, FlyEyeGeometry.panoramicWidth(FlyEyeGeometry.DEFAULT_OVERLAP));
        assertEquals(128, FlyEyeGeometry.panoramicWidth(128));
        assertEquals(128, FlyEyeGeometry.panoramicWidth(999));
    }

    @Test
    public void overlapBandIsSharedNasalColumns() {
        int ov = FlyEyeGeometry.DEFAULT_OVERLAP;
        int w = FlyEyeGeometry.NATIVE_W;
        assertEquals(112, FlyEyeGeometry.overlapLeft(ov));
        assertEquals(128, FlyEyeGeometry.overlapRight(ov));
        assertEquals(FlyEyeGeometry.overlapLeft(ov),
                FlyEyeGeometry.toPanoramicX(w - ov, false, false, ov));
        assertEquals(FlyEyeGeometry.overlapLeft(ov),
                FlyEyeGeometry.toPanoramicX(0, true, false, ov));
        assertEquals(FlyEyeGeometry.overlapRight(ov) - 1,
                FlyEyeGeometry.toPanoramicX(w - 1, false, false, ov));
        assertEquals(FlyEyeGeometry.overlapRight(ov) - 1,
                FlyEyeGeometry.toPanoramicX(ov - 1, true, false, ov));
    }
}
