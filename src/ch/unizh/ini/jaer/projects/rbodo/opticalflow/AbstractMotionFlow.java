package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import com.jogamp.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.util.jama.Matrix;

/**
 * This abstract class extends the functionality of the basic
 * AbstractMotionFlowIMU. It contains fields (spatial and temporal search
 * distance) that are used by algorithms that compute motion flow without the
 * IMU, e.g. LucasKanadeFlow. Because we don't want them to appear in the GUI of
 * ImuFlow, we put them in a separate class instead of AbstractMotionFlowIMU.
 *
 * @author rbodo
 */
@Description("Abstract base class for motion optical flow.")
@DevelopmentStatus(DevelopmentStatus.Status.Abstract)
abstract public class AbstractMotionFlow extends AbstractMotionFlowIMU {

    int searchDistance = getInt("searchDistance", 2);

    // Events must occur in this time interval to be considered.
    int maxDtThreshold = getInt("maxDtThreshold", 100000);

    private boolean showTimestampMap = getBoolean("showTimestampMap", false);

    public enum ShowTimestampMaskMask {
        BothOnAndOff, OnOnly, OffOnly
    };
    private ShowTimestampMaskMask showTimestampMapMask = ShowTimestampMaskMask.values()[getInt("showTimestampMapMask", 0)];

    private float showTimestampMapAlpha = getFloat("annotateAlpha", 0.5f);

    // Coefficients for the Savitzky-Golay filter, and parameters for the polynomial fit.
    double[][] C, a;
    private double[][] A;
    int fitOrder = getInt("fitOrder", 2);

    // Indices.
    int sx, sy, i, j, ii, jj;

    public AbstractMotionFlow(AEChip chip) {
        super(chip);
        computeSavitzkyGolayCoefficients();
        setPropertyTooltip("searchDistance", "search distance to each side");
        setPropertyTooltip(smoo, "maxDtThreshold", "max delta time (us) of timestamps from current event time that are considered. Also sets grayscale scaling of showTimestampMap display.");
        setPropertyTooltip(smoo, "fitOrder", "Order of fitting polynomial used for smoothing");
        setPropertyTooltip(disp, "showTimestampMap", "Superimposes a color-coded timestamp map on the display. This map shows the lastTimesMap[][][] of the latest event as a color code. The type of events shown is set by showTimestampMapMask.");
        setPropertyTooltip(disp, "showTimestampMapMask", "The timestamps shown from the map are set by this mask value. ");
        setPropertyTooltip(disp, "showTimestampMapAlpha", "The alpha (brightness) of the overlaid timestamp map when showTimestampMap is enabled. ");
        // check lastLoggingFolder to see if it really exists, if not, default to user.dir
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable); //To change body of generated methods, choose Tools | Templates.
        if (showTimestampMap) {
            AEChipRenderer renderer;
            renderer = (AEChipRenderer) chip.getRenderer();
            renderer.setExternalRenderer(true);
            renderer.resetAnnotationFrame(0.0f);
            if (renderer instanceof AEFrameChipRenderer) {
                AEFrameChipRenderer frameRenderer = (AEFrameChipRenderer) renderer;
                frameRenderer.setAnnotateAlpha(showTimestampMapAlpha);
            }
            int sx = chip.getSizeX(), sy = chip.getSizeY();
            // scale all timetamp values by maxDtThreshold
            int maxTs = Integer.MIN_VALUE;
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int pol = 0; pol < 2; pol++) {
                        int ts = lastTimesMap[x][y][pol];
                        if (ts > maxTs) {
                            maxTs = ts;
                        }
                    }
                }
            }

            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    int ts = Integer.MIN_VALUE;
                    switch (showTimestampMapMask) {
                        case OffOnly:
                            ts = lastTimesMap[x][y][0];
                            break;
                        case OnOnly:
                            ts = lastTimesMap[x][y][1];
                            break;
                        case BothOnAndOff:
                            int ts0 = lastTimesMap[x][y][0],
                             ts1 = ts = lastTimesMap[x][y][1];
                            ts = ts0 > ts1 ? ts0 : ts1;
                    }
                    if (ts == 0) {
                        continue; // don't bother for uninitialized timestamps
                    }
                    float v = (1 - ((float) (maxTs - ts) / maxDtThreshold)); // TODO should be maxDtThreshold
                    if (v > 1) {
                        v = 1;
                    } else if (v < 0) {
                        v = 0;
                    }
                    float[] colors = new float[3];
                    colors[0] = v;
                    colors[1] = v;
                    colors[2] = v;
                    renderer.setAnnotateColorRGB(x, y, colors);
                }
            }

        }
    }

    // Compute the convolution coefficients of a two-dimensional Savitzky-Golay
    // filter used to smooth the data.
    final synchronized void computeSavitzkyGolayCoefficients() {
        A = new double[(2 * searchDistance + 1) * (2 * searchDistance + 1)][(fitOrder + 1) * (fitOrder + 2) / 2];
        a = new double[Math.round((fitOrder + 1) * (fitOrder + 2) / 4f)][Math.round((fitOrder + 1) * (fitOrder + 2) / 4f)];
        ii = 0;
        jj = 0;
        for (sy = -searchDistance; sy <= searchDistance; sy++) {
            for (sx = -searchDistance; sx <= searchDistance; sx++) {
                for (j = 0; j <= fitOrder; j++) {
                    for (i = 0; i <= fitOrder - j; i++) {
                        A[ii][jj++] = Math.pow(sx, i) * Math.pow(sy, j);
                    }
                }
                ii++;
                jj = 0;
            }
        }
        C = (new Matrix(A)).inverse().getArray();
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --searchDistance--">
    public int getSearchDistance() {
        return searchDistance;
    }

    synchronized public void setSearchDistance(int searchDistance) {
        if (searchDistance > 12) {
            searchDistance = 12;
        } else if (searchDistance < 1) {
            searchDistance = 1; // limit size
        }
        while ((2 * searchDistance + 1) * (2 * searchDistance + 1) < (fitOrder + 1) * (fitOrder + 2) / 2) {
            searchDistance++;
        }
        this.searchDistance = searchDistance;
        putInt("searchDistance", searchDistance);
        support.firePropertyChange("searchDistance", this.searchDistance, searchDistance);
        resetFilter();
        computeSavitzkyGolayCoefficients();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --maxDtTreshold--">
    public int getMaxDtThreshold() {
        return maxDtThreshold;
    }

    public void setMaxDtThreshold(final int maxDtThreshold) {
        this.maxDtThreshold = maxDtThreshold;
        putInt("maxDtThreshold", maxDtThreshold);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --fitOrder--">
    public int getFitOrder() {
        return this.fitOrder;
    }

    synchronized public void setFitOrder(int fitOrder) {
        while ((2 * searchDistance + 1) * (2 * searchDistance + 1) < (fitOrder + 1) * (fitOrder + 2) / 2) {
            fitOrder--;
        }
        this.fitOrder = fitOrder;
        putInt("fitOrder", fitOrder);
        support.firePropertyChange("fitOrder", this.fitOrder, fitOrder);
        resetFilter();
        computeSavitzkyGolayCoefficients();
    }
    // </editor-fold>

    /**
     * @return the showTimestampMap
     */
    public boolean isShowTimestampMap() {
        return showTimestampMap;
    }

    /**
     * @param showTimestampMap the showTimestampMap to set
     */
    public void setShowTimestampMap(boolean showTimestampMap) {
        this.showTimestampMap = showTimestampMap;
        putBoolean("showTimestampMap", showTimestampMap);
        AEChipRenderer renderer;
        renderer = (AEChipRenderer) chip.getRenderer();
        renderer.setExternalRenderer(showTimestampMap);
    }

    /**
     * @return the showTimestampMapMask
     */
    public ShowTimestampMaskMask getShowTimestampMapMask() {
        return showTimestampMapMask;
    }

    /**
     * @param showTimestampMapMask the showTimestampMapMask to set
     */
    public void setShowTimestampMapMask(ShowTimestampMaskMask showTimestampMapMask) {
        this.showTimestampMapMask = showTimestampMapMask;
        putInt("showTimestampMapMask", showTimestampMapMask.ordinal());
    }

    /**
     * @return the showTimestampMapAlpha
     */
    public float getShowTimestampMapAlpha() {
        return showTimestampMapAlpha;
    }

    /**
     * @param showTimestampMapAlpha the showTimestampMapAlpha to set
     */
    public void setShowTimestampMapAlpha(float showTimestampMapAlpha) {
        if (showTimestampMapAlpha > 1.0) {
            showTimestampMapAlpha = 1.0f;
        }
        if (showTimestampMapAlpha < 0.0) {
            showTimestampMapAlpha = 0.0f;
        }
        this.showTimestampMapAlpha = showTimestampMapAlpha;
        AEChipRenderer renderer;
        renderer = (AEChipRenderer) chip.getRenderer();
        if (renderer instanceof AEFrameChipRenderer) {
            AEFrameChipRenderer frameRenderer = (AEFrameChipRenderer) renderer;
            frameRenderer.setAnnotateAlpha(showTimestampMapAlpha);
        }
    }

}
