/*
 * Pre-refactor color frame processing for benchmark comparison only.
 */
package eu.seebetter.ini.chips.davis;

import net.sf.jaer.event.ApsDvsEvent.ColorFilter;

/**
 * Original endFrame color path before CFA stencil optimization.
 *
 * @author tobi
 */
public final class DavisColorRendererLegacy {

    private static final int NEIGHBORHOOD_SIZE = 9;

    private DavisColorRendererLegacy() {
    }

    public static void processColorFrame(final float[] image, final int sx, final int sy, final int textureWidth,
            final ColorFilter[] colorFilterSequence, final float[][] colorCorrectionMatrix,
            final boolean autoWhiteBalance, final boolean colorCorrection, final boolean monochrome) {
        if (autoWhiteBalance && !monochrome) {
            applyAutoWhiteBalance(image, sx, sy, textureWidth, colorFilterSequence);
        }
        if (!monochrome) {
            demosaicColorFrame(image, sx, sy, textureWidth, colorFilterSequence);
            if (colorCorrection) {
                applyColorCorrection(image, sx, sy, textureWidth, colorCorrectionMatrix);
            }
        } else {
            applyMonochromeQuads(image, sx, sy, textureWidth);
        }
    }

    private static void applyAutoWhiteBalance(final float[] image, final int sx, final int sy, final int tw,
            final ColorFilter[] colorFilterSequence) {
        final float[] wrgbTotal = new float[4];
        final int[] wrgbCount = new int[4];

        for (int y = 0; y < sy; y += 2) {
            for (int x = 0; x < sx; x += 2) {
                wrgbTotal[colorFilterSequence[0].ordinal()] += image[pixIndex(x, y, tw)];
                wrgbTotal[colorFilterSequence[1].ordinal()] += image[pixIndex(x + 1, y, tw)];
                wrgbTotal[colorFilterSequence[2].ordinal()] += image[pixIndex(x + 1, y + 1, tw)];
                wrgbTotal[colorFilterSequence[3].ordinal()] += image[pixIndex(x, y + 1, tw)];
                wrgbCount[colorFilterSequence[0].ordinal()]++;
                wrgbCount[colorFilterSequence[1].ordinal()]++;
                wrgbCount[colorFilterSequence[2].ordinal()]++;
                wrgbCount[colorFilterSequence[3].ordinal()]++;
            }
        }

        wrgbTotal[ColorFilter.R.ordinal()] /= wrgbCount[ColorFilter.R.ordinal()];
        wrgbTotal[ColorFilter.G.ordinal()] /= wrgbCount[ColorFilter.G.ordinal()];
        wrgbTotal[ColorFilter.B.ordinal()] /= wrgbCount[ColorFilter.B.ordinal()];

        final float gR = wrgbTotal[ColorFilter.G.ordinal()] / wrgbTotal[ColorFilter.R.ordinal()];
        final float gB = wrgbTotal[ColorFilter.G.ordinal()] / wrgbTotal[ColorFilter.B.ordinal()];

        for (int y = 0; y < sy; y += 2) {
            for (int x = 0; x < sx; x += 2) {
                applyWhiteBalancePixel(image, tw, x, y, colorFilterSequence[0], gR, gB);
                applyWhiteBalancePixel(image, tw, x + 1, y, colorFilterSequence[1], gR, gB);
                applyWhiteBalancePixel(image, tw, x + 1, y + 1, colorFilterSequence[2], gR, gB);
                applyWhiteBalancePixel(image, tw, x, y + 1, colorFilterSequence[3], gR, gB);
            }
        }
    }

    private static void applyWhiteBalancePixel(final float[] image, final int tw, final int x, final int y,
            final ColorFilter cf, final float gR, final float gB) {
        final int idx = pixIndex(x, y, tw);
        if (cf == ColorFilter.R) {
            image[idx] *= gR;
        } else if (cf == ColorFilter.B) {
            image[idx + 2] *= gB;
        }
    }

    private static void demosaicColorFrame(final float[] image, final int sx, final int sy, final int tw,
            final ColorFilter[] colorFilterSequence) {
        final ColorFilter[] colors0 = buildNeighborPattern(colorFilterSequence, 0);
        final ColorFilter[] colors1 = buildNeighborPattern(colorFilterSequence, 1);
        final ColorFilter[] colors2 = buildNeighborPattern(colorFilterSequence, 2);
        final ColorFilter[] colors3 = buildNeighborPattern(colorFilterSequence, 3);
        final ColorFilter[] colors = new ColorFilter[NEIGHBORHOOD_SIZE];
        final float[] valuesR = new float[NEIGHBORHOOD_SIZE];
        final float[] valuesG = new float[NEIGHBORHOOD_SIZE];
        final float[] valuesB = new float[NEIGHBORHOOD_SIZE];

        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                if ((y % 2) == 0) {
                    if ((x % 2) == 0) {
                        System.arraycopy(colors0, 0, colors, 0, NEIGHBORHOOD_SIZE);
                    } else {
                        System.arraycopy(colors1, 0, colors, 0, NEIGHBORHOOD_SIZE);
                    }
                } else {
                    if ((x % 2) == 0) {
                        System.arraycopy(colors3, 0, colors, 0, NEIGHBORHOOD_SIZE);
                    } else {
                        System.arraycopy(colors2, 0, colors, 0, NEIGHBORHOOD_SIZE);
                    }
                }

                if (y == 0) {
                    colors[6] = null;
                    colors[7] = null;
                    colors[8] = null;
                } else if (y == (sy - 1)) {
                    colors[0] = null;
                    colors[1] = null;
                    colors[2] = null;
                }

                if (x == 0) {
                    colors[0] = null;
                    colors[3] = null;
                    colors[6] = null;
                } else if (x == (sx - 1)) {
                    colors[2] = null;
                    colors[5] = null;
                    colors[8] = null;
                }

                int index = 0;
                if (colors[0] != null) {
                    index = pixIndex(x - 1, y + 1, tw);
                    valuesR[0] = image[index];
                    valuesG[0] = image[index + 1];
                    valuesB[0] = image[index + 2];
                }
                if (colors[1] != null) {
                    index = pixIndex(x, y + 1, tw);
                    valuesR[1] = image[index];
                    valuesG[1] = image[index + 1];
                    valuesB[1] = image[index + 2];
                }
                if (colors[2] != null) {
                    index = pixIndex(x + 1, y + 1, tw);
                    valuesR[2] = image[index];
                    valuesG[2] = image[index + 1];
                    valuesB[2] = image[index + 2];
                }
                if (colors[3] != null) {
                    index = pixIndex(x - 1, y, tw);
                    valuesR[3] = image[index];
                    valuesG[3] = image[index + 1];
                    valuesB[3] = image[index + 2];
                }
                if (colors[5] != null) {
                    index = pixIndex(x + 1, y, tw);
                    valuesR[5] = image[index];
                    valuesG[5] = image[index + 1];
                    valuesB[5] = image[index + 2];
                }
                if (colors[6] != null) {
                    index = pixIndex(x - 1, y - 1, tw);
                    valuesR[6] = image[index];
                    valuesG[6] = image[index + 1];
                    valuesB[6] = image[index + 2];
                }
                if (colors[7] != null) {
                    index = pixIndex(x, y - 1, tw);
                    valuesR[7] = image[index];
                    valuesG[7] = image[index + 1];
                    valuesB[7] = image[index + 2];
                }
                if (colors[8] != null) {
                    index = pixIndex(x + 1, y - 1, tw);
                    valuesR[8] = image[index];
                    valuesG[8] = image[index + 1];
                    valuesB[8] = image[index + 2];
                }

                index = pixIndex(x, y, tw);
                valuesR[4] = image[index];
                valuesG[4] = image[index + 1];
                valuesB[4] = image[index + 2];

                image[index] = generateRForPixel(colors, valuesR);
                image[index + 1] = generateGForPixel(colors, valuesG);
                image[index + 2] = generateBForPixel(colors, valuesB);
            }
        }
    }

    private static void applyColorCorrection(final float[] image, final int sx, final int sy, final int tw,
            final float[][] colorCorrectionMatrix) {
        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                final int index = pixIndex(x, y, tw);
                final float r = image[index];
                final float g = image[index + 1];
                final float b = image[index + 2];
                image[index] = (colorCorrectionMatrix[0][0] * r) + (colorCorrectionMatrix[0][1] * g)
                        + (colorCorrectionMatrix[0][2] * b) + colorCorrectionMatrix[0][3];
                image[index + 1] = (colorCorrectionMatrix[1][0] * r) + (colorCorrectionMatrix[1][1] * g)
                        + (colorCorrectionMatrix[1][2] * b) + colorCorrectionMatrix[1][3];
                image[index + 2] = (colorCorrectionMatrix[2][0] * r) + (colorCorrectionMatrix[2][1] * g)
                        + (colorCorrectionMatrix[2][2] * b) + colorCorrectionMatrix[2][3];
            }
        }
    }

    private static void applyMonochromeQuads(final float[] image, final int sx, final int sy, final int tw) {
        for (int y = 0; y < sy; y += 2) {
            for (int x = 0; x < sx; x += 2) {
                final float val1 = image[pixIndex(x + 1, y, tw)];
                final float val2 = image[pixIndex(x + 1, y + 1, tw)];
                final float val3 = image[pixIndex(x, y + 1, tw)];
                final float gray = (val1 + val2 + val3) / 3;
                final int index = pixIndex(x + 1, y, tw);
                image[index] = gray;
                image[index + 1] = gray;
                image[index + 2] = gray;
                image[index + 3] = 1;
            }
        }
    }

    private static ColorFilter[] buildNeighborPattern(final ColorFilter[] colorFilterSequence, final int phase) {
        final ColorFilter[] pattern = new ColorFilter[NEIGHBORHOOD_SIZE];
        switch (phase) {
            case 0:
                pattern[0] = colorFilterSequence[2];
                pattern[1] = colorFilterSequence[3];
                pattern[2] = colorFilterSequence[2];
                pattern[3] = colorFilterSequence[1];
                pattern[4] = colorFilterSequence[0];
                pattern[5] = colorFilterSequence[1];
                pattern[6] = colorFilterSequence[2];
                pattern[7] = colorFilterSequence[3];
                pattern[8] = colorFilterSequence[2];
                break;
            case 1:
                pattern[0] = colorFilterSequence[3];
                pattern[1] = colorFilterSequence[2];
                pattern[2] = colorFilterSequence[3];
                pattern[3] = colorFilterSequence[0];
                pattern[4] = colorFilterSequence[1];
                pattern[5] = colorFilterSequence[0];
                pattern[6] = colorFilterSequence[3];
                pattern[7] = colorFilterSequence[2];
                pattern[8] = colorFilterSequence[3];
                break;
            case 2:
                pattern[0] = colorFilterSequence[0];
                pattern[1] = colorFilterSequence[1];
                pattern[2] = colorFilterSequence[0];
                pattern[3] = colorFilterSequence[3];
                pattern[4] = colorFilterSequence[2];
                pattern[5] = colorFilterSequence[3];
                pattern[6] = colorFilterSequence[0];
                pattern[7] = colorFilterSequence[1];
                pattern[8] = colorFilterSequence[0];
                break;
            case 3:
                pattern[0] = colorFilterSequence[1];
                pattern[1] = colorFilterSequence[0];
                pattern[2] = colorFilterSequence[1];
                pattern[3] = colorFilterSequence[2];
                pattern[4] = colorFilterSequence[3];
                pattern[5] = colorFilterSequence[2];
                pattern[6] = colorFilterSequence[1];
                pattern[7] = colorFilterSequence[0];
                pattern[8] = colorFilterSequence[1];
                break;
            default:
                throw new IllegalArgumentException("phase=" + phase);
        }
        return pattern;
    }

    private static float generateRForPixel(final ColorFilter[] pixelColors, final float[] redValues) {
        if (pixelColors[4] == ColorFilter.R) {
            return redValues[4];
        }
        float redSum = 0;
        int redCount = 0;
        for (int i = 0; i < NEIGHBORHOOD_SIZE; i++) {
            if (pixelColors[i] == ColorFilter.R) {
                redSum += redValues[i];
                redCount++;
            }
        }
        return redSum / redCount;
    }

    private static float generateGForPixel(final ColorFilter[] pixelColors, final float[] greenValues) {
        if (pixelColors[4] == ColorFilter.G) {
            return greenValues[4];
        }
        float greenSum = 0;
        int greenCount = 0;
        for (int i = 0; i < NEIGHBORHOOD_SIZE; i++) {
            if (pixelColors[i] == ColorFilter.G) {
                greenSum += greenValues[i];
                greenCount++;
            }
        }
        return greenSum / greenCount;
    }

    private static float generateBForPixel(final ColorFilter[] pixelColors, final float[] blueValues) {
        if (pixelColors[4] == ColorFilter.B) {
            return blueValues[4];
        }
        float blueSum = 0;
        int blueCount = 0;
        for (int i = 0; i < NEIGHBORHOOD_SIZE; i++) {
            if (pixelColors[i] == ColorFilter.B) {
                blueSum += blueValues[i];
                blueCount++;
            }
        }
        return blueSum / blueCount;
    }

    private static int pixIndex(final int x, final int y, final int textureWidth) {
        return 4 * ((y * textureWidth) + x);
    }
}
