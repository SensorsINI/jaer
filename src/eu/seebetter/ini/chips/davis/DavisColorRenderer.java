/*
 * ChipRenderer.java
 *
 * Created on May 2, 2006, 1:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.util.Random;

import java.nio.FloatBuffer;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEvent.ColorFilter;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.util.histogram.SimpleHistogram;

/**
 * Class adapted from DavisRenderer to render CDAVIS=rgbDAVIS output.
 *
 * The frame buffer is RGBA so four bytes per pixel. The rendering uses a
 * texture which is a power of two multiple of image size, so watch out for
 * getWidth and getHeight; they return this value and not the number of pixels
 * being rendered from the chip.
 *
 * @author christian, tobi
 * @see ChipRendererDisplayMethod
 */
public class DavisColorRenderer extends DavisRenderer {

    // Special pixel arrangement, where DVS is only found once every four pixels, as in Chenghan Li CDAVIS
    private final boolean isDVSQuarterOfAPS;

    // Color filter pattern arrangement.
    // First lower left, then lower right, then upper right, then upper left.
    private final ColorFilter[] colorFilterSequence;

    // Whether the APS readout follows DAVIS procedure (reset then signal read), or
    // the CDAVIS readout: signal then reset for true CDS.
    private final boolean isCDavisReadout;

    // Color correction values matrix -> 3 colors (RGB) x 4 values.
    private final float[][] colorCorrectionMatrix;

    // Given a pixel being at positions 0, 1, 2, 3 in the same arrangement as in colorFilterSequence,
    // what is its color and the color of all its neighbors? This four tables pre-compute that for
    // fast lookup later on.
    private final static int NEIGHBORHOOD_SIZE = 9;

    /** 3x3 neighborhood offsets from center pixel; index layout matches original endFrame. */
    private static final int[] NEIGHBOR_DX = {-1, 0, 1, -1, 0, 1, -1, 0, 1};
    private static final int[] NEIGHBOR_DY = {1, 1, 1, 0, 0, 0, -1, -1, -1};

    /** Precomputed CFA neighbor stencils for the four 2x2 phase positions. */
    private final CfaPhaseStencil[] phaseStencils = new CfaPhaseStencil[4];

    private final float[] onColor, offColor;

    /** When true, demosaic runs in GPU display; pixBuffer stays raw CFA luminance. */
    private boolean gpuDemosaicEnabled = false;

    /** AWB scale factors for GPU shader (gR, gB). */
    private float awbGreenOverRed = 1f;
    private float awbGreenOverBlue = 1f;

    /**
     * Neighbor offsets for demosaic averaging, derived once from the CFA 2x2 pattern.
     */
    private static final class CfaPhaseStencil {

        final ColorFilter center;
        final int[] rDx = new int[4];
        final int[] rDy = new int[4];
        int rCount;
        final int[] gDx = new int[4];
        final int[] gDy = new int[4];
        int gCount;
        final int[] bDx = new int[4];
        final int[] bDy = new int[4];
        int bCount;

        CfaPhaseStencil(final ColorFilter[] pattern) {
            center = pattern[4];
            for (int i = 0; i < NEIGHBORHOOD_SIZE; i++) {
                if (i == 4) {
                    continue;
                }
                final ColorFilter cf = pattern[i];
                if (cf == ColorFilter.R) {
                    rDx[rCount] = NEIGHBOR_DX[i];
                    rDy[rCount] = NEIGHBOR_DY[i];
                    rCount++;
                } else if (cf == ColorFilter.G) {
                    gDx[gCount] = NEIGHBOR_DX[i];
                    gDy[gCount] = NEIGHBOR_DY[i];
                    gCount++;
                } else if (cf == ColorFilter.B) {
                    bDx[bCount] = NEIGHBOR_DX[i];
                    bDy[bCount] = NEIGHBOR_DY[i];
                    bCount++;
                }
            }
        }
    }

    public DavisColorRenderer(final AEChip chip, final boolean isDVSQuarterOfAPS, final ColorFilter[] colorFilterSequence,
            final boolean isCDavisReadout, final float[][] colorCorrectionMatrix) {
        super(chip);

        // Input array length check.
        if (colorFilterSequence.length != 4) {
            throw new RuntimeException("ColorFilterSequence must have 4 elements (2x2 box).");
        }

        if (colorCorrectionMatrix.length != 3) {
            throw new RuntimeException("ColorCorrectionMatrix must have 3 elements (3 colors, RGB).");
        }

        for (final float[] colorCorrectionMatrixInternal : colorCorrectionMatrix) {
            if (colorCorrectionMatrixInternal.length != 4) {
                throw new RuntimeException("ColorCorrectionMatrix sub-array must have 4 elements (4 correction values).");
            }
        }

        this.isDVSQuarterOfAPS = isDVSQuarterOfAPS;
        this.colorFilterSequence = colorFilterSequence;
        this.isCDavisReadout = isCDavisReadout;
        this.colorCorrectionMatrix = colorCorrectionMatrix;

        this.offColor = new float[4];
        this.onColor = new float[4];

        phaseStencils[0] = new CfaPhaseStencil(buildCfaNeighborPattern(colorFilterSequence, 0));
        phaseStencils[1] = new CfaPhaseStencil(buildCfaNeighborPattern(colorFilterSequence, 1));
        phaseStencils[2] = new CfaPhaseStencil(buildCfaNeighborPattern(colorFilterSequence, 2));
        phaseStencils[3] = new CfaPhaseStencil(buildCfaNeighborPattern(colorFilterSequence, 3));
    }

    private static ColorFilter[] buildCfaNeighborPattern(final ColorFilter[] colorFilterSequence, final int phase) {
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

    private static int cfaPhaseIndex(final int x, final int y) {
        if ((y & 1) == 0) {
            return (x & 1) == 0 ? 0 : 1;
        }
        return (x & 1) == 0 ? 3 : 2;
    }

    @Override
    protected void updateEventMaps(final PolarityEvent e) {
        float[] map = dvsEventsMap.array();

        final int index = getIndex(e);
        if ((index < 0) || (index >= map.length)) {
            return;
        }

        // Support expanding one DVS event to cover a four pixel box, resulting in
        // an expansion to four pixels, for visualization without holes.
        final boolean expandToFour = isDVSQuarterOfAPS && !isSeparateAPSByColor();
        int idx1 = 0, idx2 = 0, idx3 = 0;

        if (expandToFour) {
            idx1 = getPixMapIndex(e.x + 1, e.y);
            idx2 = getPixMapIndex(e.x, e.y + 1);
            idx3 = getPixMapIndex(e.x + 1, e.y + 1);
        }

        // Change colors of DVS if SeparatyAPSByColor is selected: instead of Red/Green
        // for all, each quarter has its own color based on the pixel color.
        if (!isDVSQuarterOfAPS /*&& isSeparateAPSByColor()*/) {
            switch (((ApsDvsEvent) e).getColorFilter()) {
                case R:
                    // Red
                    onColor[0] = colorContrastAdditiveStep;
                    onColor[1] = 0.0f;
                    onColor[2] = 0.0f;

                    // Lighter Red
                    offColor[0] = 0.0f;
                    offColor[1] = 0.0f;
                    offColor[2] = 0.0f;

                    break;

                case G:
                    // Green
                    onColor[0] = 0.0f;
                    onColor[1] = colorContrastAdditiveStep;
                    onColor[2] = 0.0f;

                    // Lighter Green
                    offColor[0] = 0.0f;
                    offColor[1] = 0.0f;
                    offColor[2] = 0.0f;

                    break;

                case B:
                    // Blue
                    onColor[0] = 0.0f;
                    onColor[1] = 0.0f;
                    onColor[2] = colorContrastAdditiveStep;

                    // Lighter Blue
                    offColor[0] = 0.0f;
                    offColor[1] = 0.0f;
                    offColor[2] = 0.0f;

                    break;

                case W:
                    // White
                    onColor[0] = colorContrastAdditiveStep;
                    onColor[1] = colorContrastAdditiveStep;
                    onColor[2] = colorContrastAdditiveStep;

                    // Grey
                    offColor[0] = -colorContrastAdditiveStep;
                    offColor[1] = -colorContrastAdditiveStep;
                    offColor[2] = -colorContrastAdditiveStep;

                    break;
            }
        }

        if (packet.getNumCellTypes() > 2) {
            checkTypeColors(packet.getNumCellTypes());

            if ((e instanceof OrientationEventInterface) && (((OrientationEventInterface) e).isHasOrientation() == false)) {
                // if event is orientation event but orientation was not set, just draw as gray level
                map[index] = 1.0f; // if(f[0]>1f) f[0]=1f;
                map[index + 1] = 1.0f; // if(f[1]>1f) f[1]=1f;
                map[index + 2] = 1.0f; // if(f[2]>1f) f[2]=1f;

                if (expandToFour) {
                    map[idx1] = 1.0f;
                    map[idx1 + 1] = 1.0f;
                    map[idx1 + 2] = 1.0f;
                    map[idx2] = 1.0f;
                    map[idx2 + 1] = 1.0f;
                    map[idx2 + 2] = 1.0f;
                    map[idx3] = 1.0f;
                    map[idx3 + 1] = 1.0f;
                    map[idx3 + 2] = 1.0f;
                }
            } else {
                // if color scale is 1, then last value is used as the pixel value, which quantizes the color to full
                // scale.
                final float[] c = typeColorRGBComponents[e.getType()];
                map[index] = c[0]; // if(f[0]>1f) f[0]=1f;
                map[index + 1] = c[1]; // if(f[1]>1f) f[1]=1f;
                map[index + 2] = c[2]; // if(f[2]>1f) f[2]=1f;

                if (expandToFour) {
                    map[idx1] = c[0];
                    map[idx1 + 1] = c[1];
                    map[idx1 + 2] = c[2];
                    map[idx2] = c[0];
                    map[idx2 + 1] = c[1];
                    map[idx2 + 2] = c[2];
                    map[idx3] = c[0];
                    map[idx3 + 1] = c[1];
                    map[idx3 + 2] = c[2];
                }
            }

            final float alpha = map[index + 3] + (1.0f / colorScale);
            map[index + 3] += clip01(alpha);

            if (expandToFour) {
                map[idx1 + 3] += alpha;
                map[idx2 + 3] += alpha;
                map[idx3 + 3] += alpha;
            }
        } else if (colorMode == ColorMode.ColorTime) {
            final int ts0 = packet.getFirstTimestamp();
            final float dt = packet.getDurationUs();

            int ind = (int) Math.floor(((AEChipRenderer.NUM_TIME_COLORS - 1) * (e.timestamp - ts0)) / dt);
            if (ind < 0) {
                ind = 0;
            } else if (ind >= timeColors.length) {
                ind = timeColors.length - 1;
            }

            map[index] = timeColors[ind][0];
            map[index + 1] = timeColors[ind][1];
            map[index + 2] = timeColors[ind][2];
            map[index + 3] = 0.5f;

            if (expandToFour) {
                map[idx1] = timeColors[ind][0];
                map[idx1 + 1] = timeColors[ind][1];
                map[idx1 + 2] = timeColors[ind][2];
                map[idx2] = timeColors[ind][0];
                map[idx2 + 1] = timeColors[ind][1];
                map[idx2 + 2] = timeColors[ind][2];
                map[idx3] = timeColors[ind][0];
                map[idx3 + 1] = timeColors[ind][1];
                map[idx3 + 2] = timeColors[ind][2];

                map[idx1 + 3] = 0.5f;
                map[idx2 + 3] = 0.5f;
                map[idx3 + 3] = 0.5f;
            }
        } else if (colorMode == ColorMode.GrayTime) {
            final int ts0 = packet.getFirstTimestamp();
            final float dt = packet.getDurationUs();
            final float v = 0.95f - (0.95f * ((e.timestamp - ts0) / dt));

            map[index] = v;
            map[index + 1] = v;
            map[index + 2] = v;
            map[index + 3] = 1.0f;

            if (expandToFour) {
                map[idx1] = v;
                map[idx1 + 1] = v;
                map[idx1 + 2] = v;
                map[idx2] = v;
                map[idx2 + 1] = v;
                map[idx2 + 2] = v;
                map[idx3] = v;
                map[idx3 + 1] = v;
                map[idx3 + 2] = v;

                map[idx1 + 3] = 1.0f;
                map[idx2 + 3] = 1.0f;
                map[idx3 + 3] = 1.0f;
            }
        } else {
            switch (colorMode) {
                case GrayLevel:
                    onColor[0] = colorContrastAdditiveStep;
                    onColor[1] = colorContrastAdditiveStep;
                    onColor[2] = colorContrastAdditiveStep;
                    offColor[0] = -colorContrastAdditiveStep;
                    offColor[1] = -colorContrastAdditiveStep;
                    offColor[2] = -colorContrastAdditiveStep;
                    break;
                case RedGreen:
                    onColor[0] = 0;
                    onColor[1] = colorContrastAdditiveStep;
                    onColor[2] = 0;
                    offColor[0] = colorContrastAdditiveStep;
                    offColor[1] = 0;
                    offColor[2] = 0;
                    break;

            }

            if ((e.polarity == PolarityEvent.Polarity.On) || ignorePolarityEnabled) {
                map[index] += onColor[0];
                map[index + 1] += onColor[1];
                map[index + 2] += onColor[2];

                if (expandToFour) {
                    map[idx1] += onColor[0];
                    map[idx1 + 1] += onColor[1];
                    map[idx1 + 2] += onColor[2];
                    map[idx2] += onColor[0];
                    map[idx2 + 1] += onColor[1];
                    map[idx2 + 2] += onColor[2];
                    map[idx3] += onColor[0];
                    map[idx3 + 1] += onColor[1];
                    map[idx3 + 2] += onColor[2];
                }
            } else {
                map[index] += offColor[0];
                map[index + 1] += offColor[1];
                map[index + 2] += offColor[2];

                if (expandToFour) {
                    map[idx1] += offColor[0];
                    map[idx1 + 1] += offColor[1];
                    map[idx1 + 2] += offColor[2];
                    map[idx2] = +offColor[0];
                    map[idx2 + 1] += offColor[1];
                    map[idx2 + 2] += offColor[2];
                    map[idx3] += offColor[0];
                    map[idx3 + 1] += offColor[1];
                    map[idx3 + 2] += offColor[2];
                }
            }

            final float alpha = map[index + 3] + (1.0f / colorScale);
            map[index + 3] = clip01(alpha);

            if (expandToFour) {
                map[idx1 + 3] = alpha;
                map[idx2 + 3] = alpha;
                map[idx3 + 3] = alpha;
            }
        }
    }

    private final Random random = new Random();

    @Override
    protected void updateFrameBuffer(final ApsDvsEvent e) {
        final float[] buf = pixBuffer.array();
        // TODO if playing backwards, then frame will come out white because B sample comes before A

        if (!e.isStartOfFrame() && skipApsEvent()) { 
            // we currently skip all APS events until the next start of frame that we should render
            return;
        }
        if (e.isStartOfFrame()) {
            if (skipFrame()) {
                return;
            }
            startFrame(e.timestamp);
            renderedApsFrame = true;
        } else if ((!isCDavisReadout && e.isResetRead())
                || (isCDavisReadout && e.isResetRead() && !isGlobalShutter())
                || (isCDavisReadout && e.isSignalRead() && isGlobalShutter())) {
            final int index = getIndex(e);
            if ((index < 0) || (index >= buf.length)) {
                return;
            }

            buf[index] = e.getAdcSample();
        } else if ((!isCDavisReadout && e.isSignalRead())
                || (isCDavisReadout && e.isSignalRead() && !isGlobalShutter())
                || (isCDavisReadout && e.isResetRead() && isGlobalShutter())) {
            final int index = getIndex(e);
            if ((index < 0) || (index >= buf.length)) {
                return;
            }

            int val = 0;

            if (isCDavisReadout && isGlobalShutter()) {
                // The second read in GS mode is the reset read, so we have to invert this.
                val = (e.getAdcSample() - (int) buf[index]);
            } else {
                val = ((int) buf[index] - e.getAdcSample());
            }

            if (val < 0) {
                val = 0;
            }
            if ((val >= 0) && (val < minValue)) {
                minValue = val;
            } else if (val > maxValue) {
                maxValue = val;
            }

            // right here sample-reset value of this pixel is in val
            if (computeHistograms) {
                if (!((DavisChip) chip).getAutoExposureController().isCenterWeighted()) {
                    nextHist.add(val);
                } else {
                    // randomly append histogram values to histogram depending on distance from center of image
                    // to implement a simple form of center weighting of the histogram
                    float d = (1 - Math.abs(((float) e.x - (sizeX / 2)) / sizeX)) + Math.abs(((float) e.y - (sizeY / 2)) / sizeY);
                    // d is zero at center, 1 at corners
                    d *= d;

                    final float r = random.nextFloat();
                    if (r > d) {
                        nextHist.add(val);
                    }
                }
            }

            final float fval = normalizeFramePixel(val);
            buf[index] = fval;
            buf[index + 1] = fval;
            buf[index + 2] = fval;
            buf[index + 3] = 1;
        } else if (e.isEndOfFrame()) {
            endFrame(e.timestamp);

            final SimpleHistogram tmp = currentHist;
            if (computeHistograms) {
                currentHist = nextHist;
                nextHist = tmp;
                nextHist.reset();
            }

            ((DavisChip) chip).controlExposure();
        }
    }

    /**
     * Returns index into pixmap according to separateAPSByColor flag
     *
     * @param x
     * @param y
     * @param color
     * @return the index
     */
    @Override
    protected int getIndex(final BasicEvent e) {
        int x = e.x, y = e.y;

        if ((x < 0) || (y < 0) || (x >= sizeX) || (y >= sizeY)) {
            if ((System.currentTimeMillis() - lastWarningPrintedTimeMs) > INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS) {
                log.warning(String.format(
                        "Event %s out of bounds and cannot be rendered in bounds sizeX=%d sizeY=%d - delaying next warning for %dms",
                        e.toString(), sizeX, sizeY, INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS));
                lastWarningPrintedTimeMs = System.currentTimeMillis();
            }

            return -1;
        }

        if (isSeparateAPSByColor()) {
            // Separate by using X/Y position, and not color, because some colors
            // might appear twice (think G), and would then be mapped to same quadrant.
            if ((y % 2) == 0) {
                if ((x % 2) == 0) {
                    // Lower left.
                    x = x / 2;
                    y = y / 2;
                } else {
                    x = (x / 2) + (chip.getSizeX() / 2);
                    y = y / 2;
                }
            } else {
                if ((x % 2) == 0) {
                    // Upper left.
                    x = x / 2;
                    y = (y / 2) + (chip.getSizeY() / 2);
                } else {
                    // Upper right.
                    x = (x / 2) + (chip.getSizeX() / 2);
                    y = (y / 2) + (chip.getSizeY() / 2);
                }
            }
        }

        return getPixMapIndex(x, y);
    }

    public boolean isSeparateAPSByColor() {
        return ((DavisDisplayConfigInterface) chip.getBiasgen()).isSeparateAPSByColor();
    }

    public boolean isAutoWhiteBalance() {
        return ((DavisDisplayConfigInterface) chip.getBiasgen()).isAutoWhiteBalance();
    }

    public boolean isColorCorrection() {
        return ((DavisDisplayConfigInterface) chip.getBiasgen()).isColorCorrection();
    }

    public boolean isGlobalShutter() {
        return ((DavisDisplayConfigInterface) chip.getBiasgen()).isGlobalShutter();
    }

    /**
     * Runs AWB, demosaic, and optional color correction on pixBuffer.
     * Called from endFrame; exposed for headless benchmark.
     */
    public void processColorFrame() {
        if (isSeparateAPSByColor()) {
            return;
        }
        final float[] image = pixBuffer.array();
        if (isAutoWhiteBalance() && !isMonochrome()) {
            applyAutoWhiteBalance(image);
        }
        if (!isMonochrome()) {
            demosaicColorFrame(image);
        } else {
            applyMonochromeQuads(image);
        }
    }

    public void setGpuDemosaicEnabled(final boolean gpuDemosaicEnabled) {
        this.gpuDemosaicEnabled = gpuDemosaicEnabled;
    }

    public boolean isGpuDemosaicEnabled() {
        return gpuDemosaicEnabled;
    }

    public float getAwbGreenOverRed() {
        return awbGreenOverRed;
    }

    public float getAwbGreenOverBlue() {
        return awbGreenOverBlue;
    }

    public float[][] getColorCorrectionMatrix() {
        return colorCorrectionMatrix;
    }

    public ColorFilter[] getColorFilterSequence() {
        return colorFilterSequence;
    }

    public FloatBuffer getGpuDisplayPixBuffer() {
        return pixBuffer;
    }

    /** Stable APS frame snapshot; updated only at endFrame. */
    public FloatBuffer getGpuDisplayPixmap() {
        return pixmap;
    }

    public FloatBuffer getGpuDisplayDvsEventsMap() {
        return dvsEventsMap;
    }

    public FloatBuffer getGpuDisplayAnnotateMap() {
        return annotateMap;
    }

    /**
     * Computes AWB scale factors from raw CFA in pixBuffer; does not modify the buffer.
     */
    public void computeAutoWhiteBalanceFactors() {
        final float[] image = pixBuffer.array();
        final int sx = chip.getSizeX();
        final int sy = chip.getSizeY();
        final int tw = textureWidth;
        final float[] wrgbTotal = new float[4];
        final int[] wrgbCount = new int[4];

        for (int y = 0; y < sy; y += 2) {
            for (int x = 0; x < sx; x += 2) {
                wrgbTotal[colorFilterSequence[0].ordinal()] += image[4 * (y * tw + x)];
                wrgbTotal[colorFilterSequence[1].ordinal()] += image[4 * (y * tw + x + 1)];
                wrgbTotal[colorFilterSequence[2].ordinal()] += image[4 * ((y + 1) * tw + x + 1)];
                wrgbTotal[colorFilterSequence[3].ordinal()] += image[4 * ((y + 1) * tw + x)];
                wrgbCount[colorFilterSequence[0].ordinal()]++;
                wrgbCount[colorFilterSequence[1].ordinal()]++;
                wrgbCount[colorFilterSequence[2].ordinal()]++;
                wrgbCount[colorFilterSequence[3].ordinal()]++;
            }
        }

        wrgbTotal[ColorFilter.R.ordinal()] /= wrgbCount[ColorFilter.R.ordinal()];
        wrgbTotal[ColorFilter.G.ordinal()] /= wrgbCount[ColorFilter.G.ordinal()];
        wrgbTotal[ColorFilter.B.ordinal()] /= wrgbCount[ColorFilter.B.ordinal()];

        awbGreenOverRed = wrgbTotal[ColorFilter.G.ordinal()] / wrgbTotal[ColorFilter.R.ordinal()];
        awbGreenOverBlue = wrgbTotal[ColorFilter.G.ordinal()] / wrgbTotal[ColorFilter.B.ordinal()];
    }

    private void applyAutoWhiteBalance(final float[] image) {
        final int sx = chip.getSizeX();
        final int sy = chip.getSizeY();
        final int tw = textureWidth;
        final float[] wrgbTotal = new float[4];
        final int[] wrgbCount = new int[4];

        for (int y = 0; y < sy; y += 2) {
            for (int x = 0; x < sx; x += 2) {
                wrgbTotal[colorFilterSequence[0].ordinal()] += image[4 * (y * tw + x)];
                wrgbTotal[colorFilterSequence[1].ordinal()] += image[4 * (y * tw + x + 1)];
                wrgbTotal[colorFilterSequence[2].ordinal()] += image[4 * ((y + 1) * tw + x + 1)];
                wrgbTotal[colorFilterSequence[3].ordinal()] += image[4 * ((y + 1) * tw + x)];
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
                applyWhiteBalanceToQuadPixel(image, tw, x, y, colorFilterSequence[0], gR, gB);
                applyWhiteBalanceToQuadPixel(image, tw, x + 1, y, colorFilterSequence[1], gR, gB);
                applyWhiteBalanceToQuadPixel(image, tw, x + 1, y + 1, colorFilterSequence[2], gR, gB);
                applyWhiteBalanceToQuadPixel(image, tw, x, y + 1, colorFilterSequence[3], gR, gB);
            }
        }
    }

    private static void applyWhiteBalanceToQuadPixel(final float[] image, final int tw, final int x, final int y,
            final ColorFilter cf, final float gR, final float gB) {
        final int idx = 4 * (y * tw + x);
        if (cf == ColorFilter.R) {
            image[idx] *= gR;
        } else if (cf == ColorFilter.B) {
            image[idx + 2] *= gB;
        }
    }

    private void demosaicColorFrame(final float[] image) {
        final int sx = chip.getSizeX();
        final int sy = chip.getSizeY();
        final int tw = textureWidth;
        final boolean cc = isColorCorrection();
        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                final CfaPhaseStencil st = phaseStencils[cfaPhaseIndex(x, y)];
                final int idx = 4 * (y * tw + x);
                final float lum = image[idx];
                final float r = interpolateChannel(ColorFilter.R, st, image, tw, sx, sy, x, y, lum);
                final float g = interpolateChannel(ColorFilter.G, st, image, tw, sx, sy, x, y, lum);
                final float b = interpolateChannel(ColorFilter.B, st, image, tw, sx, sy, x, y, lum);
                if (cc) {
                    writeColorCorrectedPixel(image, idx, r, g, b);
                } else {
                    image[idx] = r;
                    image[idx + 1] = g;
                    image[idx + 2] = b;
                }
            }
        }
    }

    private float interpolateChannel(final ColorFilter channel, final CfaPhaseStencil st, final float[] image,
            final int tw, final int sx, final int sy, final int x, final int y, final float centerLum) {
        if (st.center == channel) {
            return centerLum;
        }
        final int[] dx;
        final int[] dy;
        final int n;
        switch (channel) {
            case R:
                dx = st.rDx;
                dy = st.rDy;
                n = st.rCount;
                break;
            case G:
                dx = st.gDx;
                dy = st.gDy;
                n = st.gCount;
                break;
            case B:
                dx = st.bDx;
                dy = st.bDy;
                n = st.bCount;
                break;
            default:
                return centerLum;
        }
        float sum = 0;
        int count = 0;
        for (int i = 0; i < n; i++) {
            final int nx = x + dx[i];
            final int ny = y + dy[i];
            if ((nx >= 0) && (nx < sx) && (ny >= 0) && (ny < sy)) {
                sum += image[4 * (ny * tw + nx)];
                count++;
            }
        }
        return sum / count;
    }

    private void writeColorCorrectedPixel(final float[] image, final int idx, final float r, final float g, final float b) {
        image[idx] = (colorCorrectionMatrix[0][0] * r) + (colorCorrectionMatrix[0][1] * g)
                + (colorCorrectionMatrix[0][2] * b) + colorCorrectionMatrix[0][3];
        image[idx + 1] = (colorCorrectionMatrix[1][0] * r) + (colorCorrectionMatrix[1][1] * g)
                + (colorCorrectionMatrix[1][2] * b) + colorCorrectionMatrix[1][3];
        image[idx + 2] = (colorCorrectionMatrix[2][0] * r) + (colorCorrectionMatrix[2][1] * g)
                + (colorCorrectionMatrix[2][2] * b) + colorCorrectionMatrix[2][3];
    }

    private void applyMonochromeQuads(final float[] image) {
        final int sx = chip.getSizeX();
        final int sy = chip.getSizeY();
        for (int y = 0; y < sy; y += 2) {
            for (int x = 0; x < sx; x += 2) {
                final float val1 = image[getPixMapIndex(x + 1, y)];
                final float val2 = image[getPixMapIndex(x + 1, y + 1)];
                final float val3 = image[getPixMapIndex(x, y + 1)];
                final float gray = (val1 + val2 + val3) / 3;
                final int index = getPixMapIndex(x + 1, y);
                image[index] = gray;
                image[index + 1] = gray;
                image[index + 2] = gray;
                image[index + 3] = 1;
            }
        }
    }

    @Override
    protected void endFrame(final int ts) {
        if (gpuDemosaicEnabled && !isSeparateAPSByColor()) {
            if (isAutoWhiteBalance() && !isMonochrome()) {
                computeAutoWhiteBalanceFactors();
            }
            // Snapshot raw CFA to pixmap for stable GPU/CPU display (pixBuffer changes during rolling readout).
            super.endFrame(ts, true);
        } else {
            processColorFrame();
            super.endFrame(ts);
        }
    }

    /**
     * Convenience method to check the VideoControl setting for this camera
     */
    public boolean isMonochrome() {
        return ((DavisConfig) chip.getBiasgen()).getVideoControl().isMonochrome();
    }

    /** Benchmark access to CFA sequence. */
    ColorFilter[] benchmarkCfaSequence() {
        return colorFilterSequence;
    }

    /** Benchmark access to color correction matrix. */
    float[][] benchmarkColorCorrectionMatrix() {
        return colorCorrectionMatrix;
    }

}
