/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.minliu;

import ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlow;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.EventPacket;

/**
 * Uses patch matching to measure local optical flow. <b>Not</b> gradient based,
 * but rather matches local features backwards in time.
 *
 * @author Tobi, Jan 2016
 */
@Description("Computes true flow events with speed and vector direction using binary feature patch matching.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class PatchMatchFlow extends AbstractMotionFlow implements Observer {
    
    // These const values are for the fast implementation of the hamming weight calculation
    private final long m1  = 0x5555555555555555L; //binary: 0101...
    private final long m2  = 0x3333333333333333L; //binary: 00110011..
    private final long m4  = 0x0f0f0f0f0f0f0f0fL; //binary:  4 zeros,  4 ones ...
    private final long m8  = 0x00ff00ff00ff00ffL; //binary:  8 zeros,  8 ones ...
    private final long m16 = 0x0000ffff0000ffffL; //binary: 16 zeros, 16 ones ...
    private final long m32 = 0x00000000ffffffffL; //binary: 32 zeros, 32 ones
    private final long hff = 0xffffffffffffffffL; //binary: all ones
    private final long h01 = 0x0101010101010101L; //the sum of 256 to the power of 0,1,2,3...

    private int[][][] histograms = null;
    private int numSlices = 3;
    private int sx, sy;
    private int currentSliceIdx = 0, tMinus1SliceIdx = 1, tMinus2SliceIdx = 2;
    private int[][] currentSlice = null, tMinus1Slice = null, tMinus2Slice = null;
    private int patchDimension = getInt("patchDimension", 8);
    private int sliceDurationUs = getInt("sliceDurationUs", 1000);
    private int sliceEventCount = getInt("sliceEventCount", 1000);
    private String patchTT = "Patch matching";

    public enum SliceMethod {
        ConstantDuration, ConstantEventNumber
    };
    private SliceMethod sliceMethod = SliceMethod.valueOf(getString("sliceMethod", SliceMethod.ConstantDuration.toString()));
    private int eventCounter = 0;
    private int sliceLastTs = Integer.MIN_VALUE;

    public PatchMatchFlow(AEChip chip) {
        super(chip);
        chip.addObserver(this); // to allocate memory once chip size is known
        setPropertyTooltip(patchTT, "patchDimension", "linear dimenion of patches to match, in pixels");
        setPropertyTooltip(patchTT, "searchDistance", "search distance for matching patches, in pixels");
        setPropertyTooltip(patchTT, "timesliceDurationUs", "duration of patches in us");
        setPropertyTooltip(patchTT, "sliceEventNumber", "number of collected events in each bitmap");
    }

    @Override
    public EventPacket filterPacket(EventPacket in) {
        for (Object ein : in) {
            extractEventInfo(ein);
            if (measureAccuracy || discardOutliersForStatisticalMeasurementEnabled) {
                imuFlowEstimator.calculateImuFlow((ApsDvsEvent) inItr.next());
                setGroundTruth();
            }
            if (isInvalidAddress(searchDistance)) {
                continue;
            }
            if (xyFilter()) {
                continue;
            }
            countIn++;
            // compute flow
            maybeRotateSlices();
            accumulateEvent();
            SADResult sadResult=minSad(x, y, tMinus2Slice, tMinus1Slice);

            // reject values that are unreasonable
            if (accuracyTests()) {
                continue;
            }

            // writeOutputEvent();
            if (measureAccuracy) {
                motionFlowStatistics.update(vx, vy, v, vxGT, vyGT, vGT);
            }
        }
        motionFlowStatistics.updatePacket(countIn, countOut);
        return isShowRawInputEnabled() ? in : dirPacket;

    }

    @Override
    public synchronized void resetFilter() {
        super.resetFilter();
        eventCounter = 0;
        lastTs = Integer.MIN_VALUE;

        if (histograms == null || histograms.length != subSizeX || histograms[0].length != subSizeY) {
            histograms = new int[numSlices][subSizeX][subSizeY];
        }
        for (int[][] a : histograms) {
            for (int[] b : a) {
                Arrays.fill(b, 0);
            }
        }
        currentSliceIdx = 0;
        tMinus1SliceIdx = 1;
        tMinus2SliceIdx = 2;
        assignSliceReferences();
    }

    private void assignSliceReferences() {
        currentSlice = histograms[currentSliceIdx];
        tMinus1Slice = histograms[tMinus1SliceIdx];
        tMinus2Slice = histograms[tMinus2SliceIdx];
    }

    @Override
    public void update(Observable o, Object arg) {
        super.update(o, arg);
        if (!isFilterEnabled()) {
            return;
        } 
        if (o instanceof AEChip && chip.getNumPixels() > 0) {
            resetFilter();
        }
    }

    /**
     * uses the current event to maybe rotate the slices
     */
    private void maybeRotateSlices() {
        switch (sliceMethod) {
            case ConstantDuration:
                int dt = ts - sliceLastTs;
                if (dt < sliceDurationUs && dt < 0) {
                    return;
                }
                break;
            case ConstantEventNumber:
                if (eventCounter++ < sliceEventCount) {
                    return;
                }
        }
        currentSliceIdx = (currentSliceIdx + 1) % numSlices;
        tMinus1SliceIdx = (tMinus1SliceIdx + 1) % numSlices;
        tMinus2SliceIdx = (tMinus2SliceIdx + 1) % numSlices;
        sliceEventCount = 0;
        sliceLastTs = ts;
        assignSliceReferences();
    }

    /**
     * Accumulates the current event to the current slice
     */
    private void accumulateEvent() {
        currentSlice[x][y] += e.getPolaritySignum();
    }

    private void clearSlice(int idx) {
        for (int[] a : histograms[idx]) {
            Arrays.fill(a, 0);
        }
    }

    
    /**
     * Computes min SAD shift around point x,y using patchDimension and
     * searchDistance
     *
     * @param x coordinate in subsampled space
     * @param y
     * @param prevSlice
     * @param curSlice
     * @return SADResult that provides the shift and SAD value
     */
    private SADResult minSad(int x, int y, int[][] prevSlice, int[][] curSlice) {
        // for now just do exhaustive search over all shifts up to +/-searchDistance
        SADResult sadResult = new SADResult(0, 0, 0);
        int minSad = Integer.MAX_VALUE, minDx = 0, minDy = 0;
        for (int dx = -searchDistance; dx < searchDistance; dx++) {
            for (int dy = -searchDistance; dy < searchDistance; dy++) {
                int sad = sad(x, y, dx, dy, prevSlice, curSlice);
                if (sad <= minSad) {
                    minSad = sad;
                    sadResult.dx = dx;
                    sadResult.dy = dy;
                }
            }
        }
        return sadResult;
    }

    /**
     * computes SAD centered on x,y with shift of dx,dy for prevSliceIdx
     * relative to curSliceIdx patch.
     *
     * @param x coordinate in subSampled space
     * @param y
     * @param dx
     * @param dy
     * @param prevSliceIdx
     * @param curSliceIdx
     * @return SAD value
     */
    private int sad(int x, int y, int dx, int dy, int[][] prevSlice, int[][] curSlice) {
        if (x < searchDistance || x > subSizeX - searchDistance || y < searchDistance || y > subSizeY - searchDistance) {
            return 0;
        }
        int sad = 0;
        for (int xx = x - searchDistance; xx < x + searchDistance; xx++) {
            for (int yy = y - searchDistance; yy < y + searchDistance; yy++) {
                int d = prevSlice[xx + dx][yy + dy] - curSlice[xx][yy];
                if (d < 0) {
                    d = -d;
                }
                sad += d;
            }
        }
        return sad;
    }
    
    //This uses fewer arithmetic operations than any other known  
    //implementation on machines with fast multiplication.
    //It uses 12 arithmetic operations, one of which is a multiply.
    public long popcount_3(long x) {
        x -= (x >> 1) & m1;             //put count of each 2 bits into those 2 bits
        x = (x & m2) + ((x >> 2) & m2); //put count of each 4 bits into those 4 bits 
        x = (x + (x >> 4)) & m4;        //put count of each 8 bits into those 8 bits 
        return (x * h01)>>56;  //returns left 8 bits of x + (x<<8) + (x<<16) + (x<<24) + ... 
    }
    
    private class SADResult {
 
       float dx, dy;
        float sadValue;

        public SADResult(float dx, float dy, float sadValue) {
            this.dx = dx;
            this.dy = dy;
            this.sadValue = sadValue;
        }

        @Override
        public String toString() {
            return String.format("dx,dy=%d,%5 SAD=%d",dx,dy,sadValue);
        }             

    }

    /**
     * @return the patchDimension
     */
    public int getPatchDimension() {
        return patchDimension;
    }

    /**
     * @param patchDimension the patchDimension to set
     */
    public void setPatchDimension(int patchDimension) {
        this.patchDimension = patchDimension;
        putInt("patchDimension", patchDimension);
    }

    /**
     * @return the sliceMethod
     */
    public SliceMethod getSliceMethod() {
        return sliceMethod;
    }

    /**
     * @param sliceMethod the sliceMethod to set
     */
    public void setSliceMethod(SliceMethod sliceMethod) {
        this.sliceMethod = sliceMethod;
        putString("sliceMethod", sliceMethod.toString());
    }

    /**
     * @return the sliceDurationUs
     */
    public int getSliceDurationUs() {
        return sliceDurationUs;
    }

    /**
     * @param sliceDurationUs the sliceDurationUs to set
     */
    public void setSliceDurationUs(int sliceDurationUs) {
        this.sliceDurationUs = sliceDurationUs;
        putInt("sliceDurationUs", sliceDurationUs);
    }

    /**
     * @return the sliceEventCount
     */
    public int getSliceEventCount() {
        return sliceEventCount;
    }

    /**
     * @param sliceEventCount the sliceEventCount to set
     */
    public void setSliceEventCount(int sliceEventCount) {
        this.sliceEventCount = sliceEventCount;
        putInt("sliceEventCount", sliceEventCount);
    }    
}
