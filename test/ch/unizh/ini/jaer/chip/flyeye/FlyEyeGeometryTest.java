package ch.unizh.ini.jaer.chip.flyeye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.sf.jaer.stereopsis.Stereopsis;

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

    @Test
    public void panoramicPackRoundTripsUniqueColumns() {
        int ov = FlyEyeGeometry.DEFAULT_OVERLAP;
        for (boolean right : new boolean[] {false, true}) {
            for (int nx = 0; nx < FlyEyeGeometry.NATIVE_W; nx++) {
                int pano = FlyEyeGeometry.toPanoramicX(nx, right, false, ov);
                int addr = FlyEye.Extractor.packPanoramicAddress(pano, 40, 1, right, ov, false);
                assertTrue("addr>=0 pano=" + pano + " right=" + right, addr >= 0);
                boolean decodedRight = (addr & Stereopsis.MASK_RIGHT_ADDR) != 0;
                assertEquals(right, decodedRight);
                int addrX = (addr & 0xfe) >>> 1;
                int nativeX = (FlyEyeGeometry.NATIVE_W - 1) - addrX;
                assertEquals(nx, nativeX);
                assertEquals(0, addr & 1); // On polarity, DVS128 fliptype
                assertEquals(40, (addr & 0x7f00) >>> 8);
            }
        }
        assertEquals(-1, FlyEye.Extractor.packPanoramicAddress(240, 0, 1, true, ov, false));
        assertEquals(-1, FlyEye.Extractor.packPanoramicAddress(0, 128, 1, false, ov, false));
    }
}
