/*
 * FlyEyeGeometry.java
 *
 * Panoramic remap for a DVS128 pair looking outward with a central overlap.
 */
package ch.unizh.ini.jaer.chip.flyeye;

/**
 * Bijective map {@code (nativeX, camera)} ↔ {@code (panoX, camera)} for FlyEye.
 * Overlap panoramic x is shared; camera disambiguates. No undistort.
 */
public final class FlyEyeGeometry {

    public static final int NATIVE_W = 128;
    public static final int NATIVE_H = 128;
    public static final int DEFAULT_OVERLAP = 16;

    private FlyEyeGeometry() {
    }

    public static int clampOverlap(int overlapPixels) {
        if (overlapPixels < 0) {
            return 0;
        }
        if (overlapPixels > NATIVE_W) {
            return NATIVE_W;
        }
        return overlapPixels;
    }

    /** Panoramic {@code sizeX = 2W - overlap}. */
    public static int panoramicWidth(int overlapPixels) {
        return 2 * NATIVE_W - clampOverlap(overlapPixels);
    }

    /**
     * Left unique columns occupy {@code [0, W)}. Right unique occupy
     * {@code [W - overlap, 2W - overlap)}. Overlap is {@code [W - overlap, W)}.
     *
     * @param nativeX sensor x in {@code [0, W)} after DVS128 extractor flipx
     * @param right true for the right-looking camera
     * @param flipX extra mount flip on this camera
     */
    public static int toPanoramicX(int nativeX, boolean right, boolean flipX, int overlapPixels) {
        int ov = clampOverlap(overlapPixels);
        int x = flipX ? (NATIVE_W - 1 - nativeX) : nativeX;
        if (right) {
            return x + NATIVE_W - ov;
        }
        return x;
    }

    /**
     * Inverse of {@link #toPanoramicX} for a valid {@code (panoX, camera)} pair.
     */
    public static int toNativeX(int panoX, boolean right, boolean flipX, int overlapPixels) {
        int ov = clampOverlap(overlapPixels);
        int x = right ? panoX - (NATIVE_W - ov) : panoX;
        if (flipX) {
            x = NATIVE_W - 1 - x;
        }
        return x;
    }

    /** Inclusive left edge of the overlap band in panoramic x. */
    public static int overlapLeft(int overlapPixels) {
        return NATIVE_W - clampOverlap(overlapPixels);
    }

    /** Exclusive right edge of the overlap band in panoramic x. */
    public static int overlapRight(int overlapPixels) {
        return NATIVE_W;
    }

    /**
     * Throws if {@code (nativeX, camera)} does not round-trip for the usual
     * overlap/flip combinations. Used by {@link #main} and unit tests.
     */
    public static void assertBijection() {
        int[] overlaps = {0, 1, DEFAULT_OVERLAP, 64, NATIVE_W};
        boolean[] flips = {false, true};
        boolean[] cameras = {false, true};
        for (int ov : overlaps) {
            for (boolean flip : flips) {
                for (boolean right : cameras) {
                    for (int nx = 0; nx < NATIVE_W; nx++) {
                        int pano = toPanoramicX(nx, right, flip, ov);
                        int back = toNativeX(pano, right, flip, ov);
                        if (back != nx) {
                            throw new AssertionError(String.format(
                                    "bijection failed native=%d right=%s flip=%s overlap=%d pano=%d back=%d",
                                    nx, right, flip, ov, pano, back));
                        }
                        int maxPano = panoramicWidth(ov);
                        if (pano < 0 || pano >= maxPano) {
                            throw new AssertionError(String.format(
                                    "pano x %d out of [0,%d) native=%d right=%s overlap=%d",
                                    pano, maxPano, nx, right, ov));
                        }
                    }
                }
            }
        }
        // Overlap pano x is shared: left native [W-ov, W) and right native [0, ov)
        int ov = DEFAULT_OVERLAP;
        int leftOverlapStart = toPanoramicX(NATIVE_W - ov, false, false, ov);
        int rightOverlapStart = toPanoramicX(0, true, false, ov);
        int leftOverlapEnd = toPanoramicX(NATIVE_W - 1, false, false, ov);
        int rightOverlapEnd = toPanoramicX(ov - 1, true, false, ov);
        if (leftOverlapStart != rightOverlapStart || leftOverlapStart != overlapLeft(ov)) {
            throw new AssertionError("overlap start must match, got left="
                    + leftOverlapStart + " right=" + rightOverlapStart + " edge=" + overlapLeft(ov));
        }
        if (leftOverlapEnd != rightOverlapEnd || leftOverlapEnd != overlapRight(ov) - 1) {
            throw new AssertionError("overlap end must match, got left="
                    + leftOverlapEnd + " right=" + rightOverlapEnd + " edge=" + (overlapRight(ov) - 1));
        }
    }

    public static void main(String[] args) {
        assertBijection();
        System.out.println("FlyEyeGeometry bijection OK (DVS128 "
                + NATIVE_W + "x" + NATIVE_H + ", default overlap " + DEFAULT_OVERLAP + ")");
    }
}
