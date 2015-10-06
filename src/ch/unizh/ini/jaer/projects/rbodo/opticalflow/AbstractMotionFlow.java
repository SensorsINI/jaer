package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.util.jama.Matrix;

/**
 * This abstract class extends the functionality of the basic AbstractMotionFlowIMU.
 * It contains fields (spatial and temporal search distance) that are used by 
 * algorithms that compute motion flow without the IMU, e.g. LucasKanadeFlow. 
 * Because we don't want them to appear in the GUI of ImuFlow, we put them in a
 * separate class instead of AbstractMotionFlowIMU.
 * @author rbodo
 */

@Description("Abstract base class for motion optical flow.")
@DevelopmentStatus(DevelopmentStatus.Status.Abstract)
abstract public class AbstractMotionFlow extends AbstractMotionFlowIMU {
    int searchDistance = getInt("searchDistance",2); 

    // Events must occur in this time interval to be considered.
    int maxDtThreshold = getInt("maxDtThreshold",100000);

    // Coefficients for the Savitzky-Golay filter, and parameters for the polynomial fit.
    double[][] C, a;
    private double[][] A;
    int fitOrder = getInt("fitOrder",2);

    // Indices.
    int sx, sy, i, j, ii, jj;
    
    public AbstractMotionFlow(AEChip chip) {
        super(chip);
        computeSavitzkyGolayCoefficients();
        setPropertyTooltip("searchDistance","search distance to each side");
        setPropertyTooltip("maxDtThreshold","max delta time (us) that is considered");
        setPropertyTooltip(smoo,"fitOrder","Order of fitting polynomial used for smoothing");
    }
    
    // Compute the convolution coefficients of a two-dimensional Savitzky-Golay
    // filter used to smooth the data.
    final synchronized void computeSavitzkyGolayCoefficients() {
        A = new double[(2*searchDistance+1)*(2*searchDistance+1)][(fitOrder+1)*(fitOrder+2)/2];
        a = new double[Math.round((fitOrder+1)*(fitOrder+2)/4f)][Math.round((fitOrder+1)*(fitOrder+2)/4f)];
        ii = 0;
        jj = 0;
        for (sy = -searchDistance; sy <= searchDistance; sy++)
            for (sx = -searchDistance; sx <= searchDistance; sx++) {
                for (j = 0; j <= fitOrder; j++)
                    for (i = 0; i <= fitOrder-j; i++)
                        A[ii][jj++] = Math.pow(sx,i)*Math.pow(sy,j);
                ii++;
                jj = 0;
            }
        C = (new Matrix(A)).inverse().getArray();
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --searchDistance--">
    public int getSearchDistance() {return searchDistance;}

    synchronized public void setSearchDistance(int searchDistance) {
        if (searchDistance > 12)
            searchDistance = 12;
        else if (searchDistance < 1) searchDistance = 1; // limit size
        while ((2*searchDistance+1)*(2*searchDistance+1) < (fitOrder+1)*(fitOrder+2)/2)
            searchDistance++;
        this.searchDistance = searchDistance;
        putInt("searchDistance",searchDistance);
        support.firePropertyChange("searchDistance",this.searchDistance,searchDistance);
        resetFilter();
        computeSavitzkyGolayCoefficients();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --maxDtTreshold--">
    public int getMaxDtThreshold() {return maxDtThreshold;}

    public void setMaxDtThreshold(final int maxDtThreshold) {
        this.maxDtThreshold = maxDtThreshold;
        putInt("maxDtThreshold",maxDtThreshold);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --fitOrder--">
    public int getFitOrder() {return this.fitOrder;}

    synchronized public void setFitOrder(int fitOrder) {
        while ((2*searchDistance+1)*(2*searchDistance+1) < (fitOrder+1)*(fitOrder+2)/2)
            fitOrder--;
        this.fitOrder = fitOrder;
        putInt("fitOrder",fitOrder);
        support.firePropertyChange("fitOrder",this.fitOrder,fitOrder);
        resetFilter();
        computeSavitzkyGolayCoefficients();
    }
    // </editor-fold>
}