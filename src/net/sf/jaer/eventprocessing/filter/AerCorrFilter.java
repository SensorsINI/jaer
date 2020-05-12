/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import java.util.Observable;
import java.util.Arrays;
import java.util.Random;

@Description("Models the AerCorr chip which detects and can be used to filter out uncorrelated activity")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class AerCorrFilter extends EventFilter2D {

    final int MAX_Ileak = 1000000, MIN_Ileak = 1;
    final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;

    private float iLeakPicoAmps = getFloat("iLeakPicoAmps", 100);

    private int subsampleBy = getInt("subsampleBy", 0);

    private float[][] vCap;
    private float[][] vTh;
    int[][] lastTimesMap;
    private float[][] cap;
    private float[][] vRs;
    private float[][] iLeakRealpA;
    private int ts = 0; // used to reset filter
    private int sx;
    private int sy;
    private Random r;
    private float iLeakCOV = getFloat("iLeakCOV", 0.04f);
    private float capCOV = getFloat("CapCOV", 0.04f);
    private float vRsCOV = getFloat("VrsCOV", 0.04f);
    private float vThCOV = getFloat("VthCOV", 0.04f);

    public AerCorrFilter(AEChip chip) {
        super(chip);
        initFilter();
        setPropertyTooltip("iLeakPicoAmps", "Set capacitor leak current in picoamps");
        setPropertyTooltip("subsampleBy", "Past events are spatially subsampled (address right shifted) by this many bits");
        setPropertyTooltip("iLeakCOV", "The leak currents vary by this coefficient of variation (1 sigma)");
        setPropertyTooltip("vRsCOV", "The reset voltage vary by this coefficient of variation (1 sigma)");
        setPropertyTooltip("vThCOV", "The threshold voltage vary by this coefficient of variation (1 sigma)");
        setPropertyTooltip("capCOV", "The capacitance vary by this coefficient of variation (1 sigma)");
        
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (lastTimesMap == null) {
            allocateMaps(chip);
        }

        for (Object eIn : in) {
            if (eIn == null) {
                break;
            }
            BasicEvent e = (BasicEvent) eIn;
            if (e.isSpecial()) {
                continue; // skip IMU, etc
            }
            short x = (short) (e.x >>> subsampleBy), y = (short) (e.y >>> subsampleBy);

            ts = e.timestamp;
            int lastT = lastTimesMap[x][y];
            int dtUs = (ts - lastT);
            if(dtUs<0) {
//                log.warning("negative deltaT");
//                resetFilter();
                e.setFilteredOut(true); // filter out this first event (which occurs when lastTimesMap has been initialized and not event has come yet)
                lastTimesMap[x][y] = ts;
                continue;
            }
            float deltaV = ((iLeakRealpA[x][y] / cap[x][y]) * dtUs * 1e-6f);
            vCap[x][y] -= deltaV;  ///&& lastT != DEFAULT_TIMESTAMP)
            if(vCap[x][y]<0)vCap[x][y]=0; // it cannot go negative voltage
            if (!(vCap[x][y] > vTh[x][y]/* && lastT != DEFAULT_TIMESTAMP*/)) { // don't let any event through unless the cap is really charged up still
                e.setFilteredOut(true);
            } else {
                //System.out.println(e.x+" "+e.y);
            }
            vCap[x][y] = vRs[x][y];

            lastTimesMap[x][y] = ts;
        }

        return in;////return in?
    }

    @Override
    public synchronized final void resetFilter() {
        initFilter();
    }

    @Override
    public final void initFilter() {
        r = new Random();
        allocateMaps(chip);
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
    }
//why synchronized?
    synchronized private void allocateMaps(AEChip chip) {
        if (chip != null && chip.getNumCells() > 0) {
            lastTimesMap = new int[chip.getSizeX()][chip.getSizeY()];
            for (int[] arrayRow : lastTimesMap) {
                Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
            }

            vCap = new float[chip.getSizeX()][chip.getSizeY()];
            cap = new float[chip.getSizeX()][chip.getSizeY()];
            // Initialize two dimensional array, by first getting the rows and then setting their values.
            for (float[] arrayRow : cap) {
                for (int i = 0; i < arrayRow.length; i++) {
                    arrayRow[i] = 165e-15f * ((float) r.nextGaussian() * 0.03f + 1f);
                    //arrayRow[i] = 165e-15;
                }
            }

            vTh = new float[chip.getSizeX()][chip.getSizeY()];
             // Initialize two dimensional array, by first getting the rows and then setting their values.
             for (float[] arrayRow : vTh) {
                for (int i = 0; i < arrayRow.length; i++) {
                     arrayRow[i] = (float)(0.6*(r.nextGaussian() * 0.005f + 1.197f));
             }
             }
            vRs = new float[chip.getSizeX()][chip.getSizeY()];
            // Initialize two dimensional array, by first getting the rows and then setting their values.
            for (float[] arrayRow : vRs) {
                for (int i = 0; i < arrayRow.length; i++) {
                    arrayRow[i] = (float) (r.nextGaussian() * 0.005f + 1.197f);
                    //arrayRow[i] = 1.2;
                }
            }
            iLeakRealpA = new float[chip.getSizeX()][chip.getSizeY()];
            // Initialize two dimensional array, by first getting the rows and then setting their values.
            for (float[] arrayRow : iLeakRealpA) {
                for (int i = 0; i < arrayRow.length; i++) {
                    //arrayRow[i] = (float)(getIleak())*1e-13;
                    arrayRow[i] = (float) (iLeakPicoAmps * (r.nextGaussian() * iLeakCOV + 1f)) * 1e-12f;//should have different sigma and u for different iLeak values
                }
            }
        }
    }

    public Object getFilterState() {
        return lastTimesMap;
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --SubsampleBy--">
    public int getSubsampleBy() {
        return subsampleBy;
    }

    /**
     * Sets the number of bits to subsample by when storing events into the map
     * of past events. Increasing this value will increase the number of events
     * that pass through and will also allow passing events from small sources
     * that do not stimulate every pixel.
     *
     * @param subsampleBy the number of bits, 0 means no subsampling, 1 means
     * cut event time map resolution by a factor of two in x and in y
     */
    public void setSubsampleBy(int subsampleBy) {
        if (subsampleBy < 0) {
            subsampleBy = 0;
        } else if (subsampleBy > 4) {
            subsampleBy = 4;
        }
        this.subsampleBy = subsampleBy;
        putInt("subsampleBy", subsampleBy);
    }
    // </editor-fold>

    /**
     * @return the iLeak
     */
    public float getIleakPicoAmps() {
        return iLeakPicoAmps;
    }

    public int getMinIleak() {
        return (int) MIN_Ileak;
    }

    public int getMaxIleak() {
        return (int) MAX_Ileak;
    }

    /**
     * @param Ileak the iLeak to set
     */
    public void setIleakPicoAmps(float Ileak) {
        this.iLeakPicoAmps = Ileak;
        putFloat("iLeakPicoAmps", this.iLeakPicoAmps);
        allocateMaps(chip);
    }

    /**
     * @return the iLeakCOV
     */
    public float getiLeakCOV() {
        return iLeakCOV;
    }

    /**
     * @param iLeakCOV the iLeakCOV to set
     */
    public void setiLeakCOV(float iLeakCOV) {
        this.iLeakCOV = iLeakCOV;
        putFloat("iLeakCOV", iLeakCOV);
        allocateMaps(chip);
    }

    /**
     * @return the capCOV
     */
    public float getCapCOV() {
        return capCOV;
    }

    /**
     * @param capCOV the capCOV to set
     */
    public void setCapCOV(float capCOV) {
        this.capCOV = capCOV;
        putFloat("capCOV", capCOV);
        allocateMaps(chip);
    }

    /**
     * @return the vRsCOV
     */
    public float getvRsCOV() {
        return vRsCOV;
    }

    /**
     * @param vRsCOV the vRsCOV to set
     */
    public void setvRsCOV(float vRsCOV) {
        this.vRsCOV = vRsCOV;
        putFloat("vRsCOV", vRsCOV);
        allocateMaps(chip);
    }

    /**
     * @return the vThCOV
     */
    public float getvThCOV() {
        return vThCOV;
    }

    /**
     * @param vThCOV the vThCOV to set
     */
    public void setvThCOV(float vThCOV) {
        this.vThCOV = vThCOV;
        putFloat("vThCOV", vThCOV);
        allocateMaps(chip);
    }

}
