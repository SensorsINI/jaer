/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.minliu;

import ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlow;
import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.Steadicam;
import net.sf.jaer.eventprocessing.filter.TransformAtTime;

/**
 * Uses patch matching to measureTT local optical flow. <b>Not</b> gradient
 * based, but rather matches local features backwards in time.
 *
 * @author Tobi and Min, Jan 2016
 */
@Description("Computes optical flow with vector direction using binary block matching")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class PatchMatchFlow extends AbstractMotionFlow implements Observer {

    private int[][][] histograms = null;
    private int numSlices = 3;
    private int sx, sy;
    private int tMinus2SliceIdx = 0, tMinus1SliceIdx = 1, currentSliceIdx = 2;
    private int[][] currentSlice = null, tMinus1Slice = null, tMinus2Slice = null;
    private ArrayList<Integer[]>[][] spikeTrains = null;   // Spike trains for one block
    private ArrayList<int[][]>[] histogramsAL = null;
    private ArrayList<int[][]> currentAL = null, previousAL = null, previousMinus1AL = null; // One is for current, the second is for previous, the third is for the one before previous one
    private BitSet[] histogramsBitSet = null;
    private BitSet currentSli = null, tMinus1Sli = null, tMinus2Sli = null;
    private int patchDimension = getInt("patchDimension", 9);
    private boolean displayOutputVectors = getBoolean("displayOutputVectors", true);
    private int eventPatchDimension = getInt("eventPatchDimension", 3);
    private int forwardEventNum = getInt("forwardEventNum", 10);
    private float cost = getFloat("cost", 0.001f);
    private float confidenceThreshold = getFloat("confidence threshold", 1f);
    private int thresholdTime = getInt("thresholdTime", 1000000);
    private int[][] lastFireIndex = null;  // Events are numbered in time order for every block. This variable is for storing the last event index fired on all blocks.
    private int[][] eventSeqStartTs = null;
    private boolean preProcessEnable = false;

    public enum PatchCompareMethod {
        JaccardDistance, HammingDistance, SAD, EventSqeDistance
    };
    private PatchCompareMethod patchCompareMethod = PatchCompareMethod.valueOf(getString("patchCompareMethod", PatchCompareMethod.HammingDistance.toString()));

    private int sliceDurationUs = getInt("sliceDurationUs", 100000);
    private int sliceEventCount = getInt("sliceEventCount", 1000);
    private boolean rewindFlg = false; // The flag to indicate the rewind event.
    private FilterChain filterChain;
    private Steadicam cameraMotion;

    // calibration
    private boolean calibrating = false; // used to flag calibration state
    private int calibrationSampleCount = 0;
    private int NUM_CALIBRATION_SAMPLES_DEFAULT = 800; // 400 samples /sec
    protected int numCalibrationSamples = getInt("numCalibrationSamples", NUM_CALIBRATION_SAMPLES_DEFAULT);
    TextRenderer imuTextRenderer = null;
    private boolean showGrid = getBoolean("showGrid", true);

    public enum SliceMethod {
        ConstantDuration, ConstantEventNumber, AdaptationDuration
    };
    private SliceMethod sliceMethod = SliceMethod.valueOf(getString("sliceMethod", SliceMethod.ConstantDuration.toString()));
    private int eventCounter = 0;
    private int sliceLastTs = 0;

    public PatchMatchFlow(AEChip chip) {
        super(chip);

        filterChain = new FilterChain(chip);
        cameraMotion = new Steadicam(chip);
        cameraMotion.setFilterEnabled(true);
        cameraMotion.setDisableRotation(true);
        cameraMotion.setDisableTranslation(true);
        // filterChain.add(cameraMotion);
        setEnclosedFilterChain(filterChain);

        String patchTT = "Block matching";
        String eventSqeMatching = "Event squence matching";
        String preProcess = "Denoise";

        chip.addObserver(this); // to allocate memory once chip size is known
        setPropertyTooltip(preProcess, "preProcessEnable", "enable this to denoise before data processing");
        setPropertyTooltip(preProcess, "forwardEventNum", "Number of events have fired on the current block since last processing");
        setPropertyTooltip(preProcess, "confidenceThreshold", "Confidence threshold for rejecting unresonable value; Range from 0 to 1");
        setPropertyTooltip(patchTT, "patchDimension", "linear dimenion of patches to match, in pixels");
        setPropertyTooltip(patchTT, "searchDistance", "search distance for matching patches, in pixels");
        setPropertyTooltip(patchTT, "patchCompareMethod", "method to compare two patches");
        setPropertyTooltip(patchTT, "sliceDurationUs", "duration of patches in us, also called sample interval");
        setPropertyTooltip(patchTT, "sliceMethod", "method for determining time slice duration for block matching");
        setPropertyTooltip(eventSqeMatching, "cost", "The cost to translation one event to the other position");
        setPropertyTooltip(eventSqeMatching, "thresholdTime", "The threshold value of interval time between the first event and the last event");
        setPropertyTooltip(eventSqeMatching, "sliceEventCount", "number of collected events in each bitmap");
        setPropertyTooltip(eventSqeMatching, "eventPatchDimension", "linear dimenion of patches to match, in pixels");
        setPropertyTooltip(dispTT, "displayOutputVectors", "display the output motion vectors or not");
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        setupFilter(in);
        checkArrays();

        ApsDvsEventPacket in2 = (ApsDvsEventPacket) in;
        Iterator itr = in2.fullIterator();   // Wfffsfe also need IMU data, so here we use the full iterator. 
        while (itr.hasNext()) {
            Object ein = itr.next();
            if (ein == null) {
                log.warning("null event passed in, returning input packet");
                return in;
            }

            if (!extractEventInfo(ein)) {
                continue;
            }
            ApsDvsEvent apsDvsEvent = (ApsDvsEvent) ein;
            if (apsDvsEvent.isImuSample()) {
                IMUSample s = apsDvsEvent.getImuSample();
                continue;
            }
            if (apsDvsEvent.isApsData()) {
                continue;
            }
            // inItr = in.inputIterator;
            if (measureAccuracy || discardOutliersForStatisticalMeasurementEnabled) {
                imuFlowEstimator.calculateImuFlow((ApsDvsEvent) inItr.next());
                setGroundTruth();
            }

            if (xyFilter()) {
                continue;
            }
            countIn++;

            // compute flow
            SADResult result = new SADResult(0, 0, 0);

            int blockLocX = x / eventPatchDimension;
            int blockLocY = y / eventPatchDimension;

            // Build the spike trains of every block, every block is consist of 3*3 pixels.
            if (spikeTrains[blockLocX][blockLocY] == null) {
                spikeTrains[blockLocX][blockLocY] = new ArrayList();
            }
            int spikeBlokcLength = spikeTrains[blockLocX][blockLocY].size();
            int previousTsInterval = 0;
            if (spikeBlokcLength == 0) {
                previousTsInterval = ts;
            } else {
                previousTsInterval = ts - spikeTrains[blockLocX][blockLocY].get(spikeBlokcLength - 1)[0];
            }
            spikeTrains[blockLocX][blockLocY].add(new Integer[]{ts, type});

            switch (patchCompareMethod) {
                case HammingDistance:
                    maybeRotateSlices();
                    accumulateEvent();

                    if (preProcessEnable) {
                        // There're enough events fire on the specific block now.
                        if (spikeTrains[blockLocX][blockLocY].size() - lastFireIndex[blockLocX][blockLocY] >= forwardEventNum) {
                            lastFireIndex[blockLocX][blockLocY] = spikeTrains[blockLocX][blockLocY].size() - 1;
                            result = minHammingDistance(x, y, tMinus2Sli, tMinus1Sli);
                            result.dx = result.dx / sliceDurationUs * 1000000;
                            result.dy = result.dy / sliceDurationUs * 1000000;
                        }
                    } else {
                        result = minHammingDistance(x, y, tMinus2Sli, tMinus1Sli);
                        result.dx = result.dx / sliceDurationUs * 1000000;
                        result.dy = result.dy / sliceDurationUs * 1000000;
                    }

                    break;
                case SAD:
                    maybeRotateSlices();
                    accumulateEvent();
                    if (preProcessEnable) {
                        // There're enough events fire on the specific block now
                        if (spikeTrains[blockLocX][blockLocY].size() - lastFireIndex[blockLocX][blockLocY] >= forwardEventNum) {
                            lastFireIndex[blockLocX][blockLocY] = spikeTrains[blockLocX][blockLocY].size() - 1;
                            result = minSad(x, y, tMinus2Sli, tMinus1Sli);
                            result.dx = result.dx / sliceDurationUs * 1000000;
                            result.dy = result.dy / sliceDurationUs * 1000000;

                        }
                    } else {
                        result = minSad(x, y, tMinus2Sli, tMinus1Sli);
                        result.dx = result.dx / sliceDurationUs * 1000000;
                        result.dy = result.dy / sliceDurationUs * 1000000;
                    }
                    break;
                case JaccardDistance:
                    maybeRotateSlices();
                    accumulateEvent();
                    if (preProcessEnable) {
                        // There're enough events fire on the specific block now
                        if (spikeTrains[blockLocX][blockLocY].size() - lastFireIndex[blockLocX][blockLocY] >= forwardEventNum) {
                            lastFireIndex[blockLocX][blockLocY] = spikeTrains[blockLocX][blockLocY].size() - 1;
                            result = minJaccardDistance(x, y, tMinus2Sli, tMinus1Sli);
                            result.dx = result.dx / sliceDurationUs * 1000000;
                            result.dy = result.dy / sliceDurationUs * 1000000;
                        }
                    } else {
                        result = minJaccardDistance(x, y, tMinus2Sli, tMinus1Sli);
                        result.dx = result.dx / sliceDurationUs * 1000000;
                        result.dy = result.dy / sliceDurationUs * 1000000;
                    }
                    break;
                case EventSqeDistance:
                    if (previousTsInterval < 0) {
                        spikeTrains[blockLocX][blockLocY].remove(spikeTrains[blockLocX][blockLocY].size() - 1);
                        continue;
                    }

                    if (previousTsInterval >= thresholdTime) {
                        float maxDt = 0;
                        float[][] dataPoint = new float[9][2];
                        if (blockLocX >= 1 && blockLocY >= 1 && blockLocX <= 238 && blockLocY <= 178) {
                            for (int ii = -1; ii < 2; ii++) {
                                for (int jj = -1; jj < 2; jj++) {
                                    float dt = ts - eventSeqStartTs[blockLocX + ii][blockLocY + jj];

                                    // Remove the seq1 itself                             
                                    if (0 == ii && 0 == jj) {
                                        // continue;
                                        dt = 0;
                                    }

                                    dataPoint[(ii + 1) * 3 + (jj + 1)][0] = dt;
                                    if (dt > maxDt) {
                                    }
                                }
                            }
                        }
                        // result = minVicPurDistance(blockLocX, blockLocY);  

                        eventSeqStartTs[blockLocX][blockLocY] = ts;
                        boolean allZeroFlg = true;
                        for (int mm = 0; mm < 9; mm++) {
                            for (int nn = 0; nn < 1; nn++) {
                                if (dataPoint[mm][nn] != 0) {
                                    allZeroFlg = false;
                                }
                            }
                        }
                        if (allZeroFlg) {
                            continue;
                        }
                        KMeans cluster = new KMeans();
                        cluster.setData(dataPoint);
                        int[] initialValue = new int[3];
                        initialValue[0] = 0;
                        initialValue[1] = 4;
                        initialValue[2] = 8;
                        cluster.setInitialByUser(initialValue);
                        cluster.cluster();
                        ArrayList<ArrayList<Integer>> kmeansResult = cluster.getResult();
                        float[][] classData = cluster.getClassData();
                        int firstClusterIdx = -1, secondClusterIdx = -1, thirdClusterIdx = -1;
                        for (int i = 0; i < 3; i++) {
                            if (kmeansResult.get(i).contains(0)) {
                                firstClusterIdx = i;
                            }
                            if (kmeansResult.get(i).contains(4)) {
                                secondClusterIdx = i;
                            }
                            if (kmeansResult.get(i).contains(8)) {
                                thirdClusterIdx = i;
                            }
                        }
                        if (kmeansResult.get(firstClusterIdx).size() == 3
                                && kmeansResult.get(firstClusterIdx).size() == 3
                                && kmeansResult.get(firstClusterIdx).size() == 3
                                && kmeansResult.get(firstClusterIdx).contains(1)
                                && kmeansResult.get(firstClusterIdx).contains(2)) {
                            result.dx = -1 / (classData[secondClusterIdx][0] - classData[firstClusterIdx][0]) * 1000000 * 0.2f * eventPatchDimension;;
                            result.dy = 0;
                        }
                        if (kmeansResult.get(firstClusterIdx).size() == 3
                                && kmeansResult.get(firstClusterIdx).size() == 3
                                && kmeansResult.get(firstClusterIdx).size() == 3
                                && kmeansResult.get(thirdClusterIdx).contains(2)
                                && kmeansResult.get(thirdClusterIdx).contains(5)) {
                            result.dy = -1 / (classData[thirdClusterIdx][0] - classData[secondClusterIdx][0]) * 1000000 * 0.2f * eventPatchDimension;;
                            result.dx = 0;
                        }
                    }
                    break;
            }
            vx = result.dx;
            vy = result.dy;
            v = (float) Math.sqrt(vx * vx + vy * vy);


            // reject values that are unreasonable
            if (accuracyTests(result)) {
                continue;
            }

            if (displayOutputVectors) {
                writeOutputEvent();
            }
            if (measureAccuracy) {
                motionFlowStatistics.update(vx, vy, v, vxGT, vyGT, vGT);
            }
        }

        if (rewindFlg) {
            rewindFlg = false;
            sliceLastTs = 0;

            final int sx = chip.getSizeX(), sy = chip.getSizeY();
            for (int i = 0; i < sx; i++) {
                for (int j = 0; j < sy; j++) {
                    if (spikeTrains[i][j] != null) {
                        spikeTrains[i][j] = null;
                    }
                    if (lastFireIndex != null) {
                        lastFireIndex[i][j] = 0;
                    }
                    eventSeqStartTs[i][j] = 0;
                }
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
            if (numSlices == 0 && subSizeX == 0 && subSizeX == 0) {
                return;
            }
            histograms = new int[numSlices][subSizeX][subSizeY];
        }
        for (int[][] a : histograms) {
            for (int[] b : a) {
                Arrays.fill(b, 0);
            }
        }
        if (histogramsBitSet == null) {
            histogramsBitSet = new BitSet[numSlices];
        }

        for (int ii = 0; ii < numSlices; ii++) {
            histogramsBitSet[ii] = new BitSet(subSizeX * subSizeY);
        }

        // Initialize 3 ArrayList's histogram, every pixel has three patches: current, previous and previous-1
        if (histogramsAL == null) {
            histogramsAL = new ArrayList[3];
        }

        if (spikeTrains == null & subSizeX != 0 & subSizeY != 0) {
            spikeTrains = new ArrayList[subSizeX][subSizeY];
        }
        if (patchDimension != 0) {
            int colPatchCnt = subSizeX / patchDimension;
            int rowPatchCnt = subSizeY / patchDimension;

            for (int ii = 0; ii < numSlices; ii++) {
                histogramsAL[ii] = new ArrayList();
                for (int jj = 0; jj < colPatchCnt * rowPatchCnt; jj++) {
                    int[][] patch = new int[patchDimension][patchDimension];
                    histogramsAL[ii].add(patch);
                }
            }
        }

        tMinus2SliceIdx = 0;
        tMinus1SliceIdx = 1;
        currentSliceIdx = 2;
        assignSliceReferences();

        sliceLastTs = 0;
        rewindFlg = true;
    }

    @Override
    public void update(Observable o, Object arg) {
        if (!isFilterEnabled()) {
            return;
        }
        super.update(o, arg);
        if (o instanceof AEChip && chip.getNumPixels() > 0) {
            resetFilter();
        }
    }

    /**
     * uses the current event to maybe rotate the slices
     */
    private void maybeRotateSlices() {
        int dt = ts - sliceLastTs;

        switch (sliceMethod) {
            case ConstantDuration:
                if (rewindFlg) {
                    return;
                }
                if (dt < sliceDurationUs || dt < 0) {
                    return;
                }
                break;
            case ConstantEventNumber:
                if (eventCounter++ < sliceEventCount) {
                    return;
                }
            case AdaptationDuration:
                log.warning("The adaptation method is not supported yet.");
                return;
        }

        /* The index cycle is " current idx -> t1 idx -> t2 idx -> current idx".
           Change the index, the change should like this:
           next t2 = previous t1 = histogram(previous t2 idx + 1);
           next t1 = previous current = histogram(previous t1 idx + 1);
         */
        currentSliceIdx = (currentSliceIdx + 1) % numSlices;
        tMinus1SliceIdx = (tMinus1SliceIdx + 1) % numSlices;
        tMinus2SliceIdx = (tMinus2SliceIdx + 1) % numSlices;
        sliceEventCount = 0;
        sliceLastTs = ts;
        assignSliceReferences();
    }

    private int updateAdaptDuration() {
        return 1000;
    }

    private void assignSliceReferences() {
        currentSlice = histograms[currentSliceIdx];
        tMinus1Slice = histograms[tMinus1SliceIdx];
        tMinus2Slice = histograms[tMinus2SliceIdx];

        currentSli = histogramsBitSet[currentSliceIdx];
        tMinus1Sli = histogramsBitSet[tMinus1SliceIdx];
        tMinus2Sli = histogramsBitSet[tMinus2SliceIdx];

        currentSli.clear();
    }

    /**
     * Accumulates the current event to the current slice
     */
    private void accumulateEvent() {
        currentSlice[x][y] += e.getPolaritySignum();
        currentSli.set((x + 1) + y * subSizeX);  // All evnets wheather 0 or 1 will be set in the BitSet Slice.
    }

    private void clearSlice(int idx) {
        for (int[] a : histograms[idx]) {
            Arrays.fill(a, 0);
        }
    }

    /**
     * Computes hamming eight around point x,y using patchDimension and
     * searchDistance
     *
     * @param x coordinate in subsampled space
     * @param y
     * @param prevSlice
     * @param curSlice
     * @return SADResult that provides the shift and SAD value
     */
    private SADResult minHammingDistance(int x, int y, BitSet prevSlice, BitSet curSlice) {
        float minSum = Integer.MAX_VALUE, sum = 0;
        SADResult sadResult = new SADResult(0, 0, 0);
        for (int dx = -searchDistance; dx <= searchDistance; dx++) {
            for (int dy = -searchDistance; dy <= searchDistance; dy++) {
                 sum = hammingDistance(x, y, dx, dy, prevSlice, curSlice);
                if (sum <= minSum) {
                    minSum = sum;
                    sadResult.dx = dx;
                    sadResult.dy = dy;
                    sadResult.sadValue = minSum;
                }
            }
        }

        return sadResult;
    }

    /**
     * computes Hamming distance centered on x,y with patch of patchSize for
     * prevSliceIdx relative to curSliceIdx patch.
     *
     * @param x coordinate x in subSampled space
     * @param y coordinate y in subSampled space
     * @param dx   
     * @param dy
     * @param prevSlice
     * @param curSlice
     * @return Hamming Distance value
     */
    private float hammingDistance(int x, int y, int dx, int dy, BitSet prevSlice, BitSet curSlice) {
        float retVal = 0;
        int blockRadius = patchDimension / 2;
        float validPixNumCurrSli = 0, validPixNumPrevSli = 0; // The valid pixel number in the current block

        // Make sure 0<=xx+dx<subSizeX, 0<=xx<subSizeX and 0<=yy+dy<subSizeY, 0<=yy<subSizeY,  or there'll be arrayIndexOutOfBoundary exception.
        if (x < blockRadius + dx || x >= subSizeX - blockRadius + dx || x < blockRadius || x >= subSizeX - blockRadius
                || y < blockRadius + dy || y >= subSizeY - blockRadius + dy || y < blockRadius || y >= subSizeY - blockRadius) {
            return Integer.MAX_VALUE;
        }

        for (int xx = x - blockRadius; xx <= x + blockRadius; xx++) {
            for (int yy = y - blockRadius; yy <= y + blockRadius; yy++) {
                boolean currSlicePol = curSlice.get((xx + 1) + (yy) * subSizeX); // binary value on (xx, yy) for current slice
                boolean prevSlicePol = prevSlice.get((xx + 1 - dx) + (yy - dy) * subSizeX); // binary value on (xx, yy) for previous slice
                
                if (currSlicePol != prevSlicePol) {
                    retVal += 1;
                }
                if (currSlicePol == true) {
                    validPixNumCurrSli += 1;
                }   
                if (prevSlicePol == true) {
                    validPixNumPrevSli += 1;
                }
            }
        }
        
        // Normalize the return value
        if(validPixNumCurrSli == 0 || validPixNumPrevSli == 0) {  // If valid pixel number of any slice is 0, then we set the distance to very big value so we can exclude it.
            retVal = Integer.MAX_VALUE;
        } else {
            // retVal = (validPixNumCurrSli/((2*blockRadius + 1) * (2*blockRadius + 1))) * (retVal/((2*blockRadius + 1) * (2*blockRadius + 1)));     
            retVal = retVal/(validPixNumCurrSli);
        }
        return retVal;
    }

    /**
     * Computes hamming weight around point x,y using patchDimension and
     * searchDistance
     *
     * @param x coordinate in subsampled space
     * @param y
     * @param prevSlice
     * @param curSlice
     * @return SADResult that provides the shift and SAD value
     */
    private SADResult minJaccardDistance(int x, int y, BitSet prevSlice, BitSet curSlice) {
        float minSum = Integer.MAX_VALUE, sum = 0;
        SADResult sadResult = new SADResult(0, 0, 0);
        for (int dx = -searchDistance; dx <= searchDistance; dx++) {
            for (int dy = -searchDistance; dy <= searchDistance; dy++) {
                sum = jaccardDistance(x, y, dx, dy, prevSlice, curSlice);
                if (sum <= minSum) {
                    minSum = sum;
                    sadResult.dx = dx;
                    sadResult.dy = dy;
                    sadResult.sadValue = minSum;
                }
            }
        }

        return sadResult;
    }

    /**
     * computes Hamming distance centered on x,y with patch of patchSize for
     * prevSliceIdx relative to curSliceIdx patch.
     *
     * @param x coordinate in subSampled space
     * @param y
     * @param patchSize
     * @param prevSlice
     * @param curSlice
     * @return SAD value
     */
    private float jaccardDistance(int x, int y, int dx, int dy, BitSet prevSlice, BitSet curSlice) {
        float retVal = 0;
        float M01 = 0, M10 = 0, M11 = 0;
        int blockRadius = patchDimension / 2;

        // Make sure 0<=xx+dx<subSizeX, 0<=xx<subSizeX and 0<=yy+dy<subSizeY, 0<=yy<subSizeY,  or there'll be arrayIndexOutOfBoundary exception.
        if (x < blockRadius + dx || x >= subSizeX - blockRadius + dx || x < blockRadius || x >= subSizeX - blockRadius
                || y < blockRadius + dy || y >= subSizeY - blockRadius + dy || y < blockRadius || y >= subSizeY - blockRadius) {
            return 1;
        }

        for (int xx = x - blockRadius; xx <= x + blockRadius; xx++) {
            for (int yy = y - blockRadius; yy <= y + blockRadius; yy++) {
                if (curSlice.get((xx + 1) + (yy) * subSizeX) == true && prevSlice.get((xx + 1 - dx) + (yy - dy) * subSizeX) == true) {
                    M11 += 1;
                }
                if (curSlice.get((xx + 1) + (yy) * subSizeX) == true && prevSlice.get((xx + 1 - dx) + (yy - dy) * subSizeX) == false) {
                    M01 += 1;
                }
                if (curSlice.get((xx + 1) + (yy) * subSizeX) == false && prevSlice.get((xx + 1 - dx) + (yy - dy) * subSizeX) == true) {
                    M10 += 1;
                }
            }
        }
        if (0 == M01 + M10 + M11) {
            retVal = 0;
        } else {
            retVal = M11 / (M01 + M10 + M11);
        }
        retVal = 1 - retVal;
        return retVal;
    }

    private SADResult minVicPurDistance(int blockX, int blockY) {
        ArrayList<Integer[]> seq1 = new ArrayList(1);
        SADResult sadResult = new SADResult(0, 0, 0);

        int size = spikeTrains[blockX][blockY].size();
        int lastTs = spikeTrains[blockX][blockY].get(size - forwardEventNum)[0];
        for (int i = size - forwardEventNum; i < size; i++) {
            seq1.add(spikeTrains[blockX][blockY].get(i));
        }

//        if(seq1.get(2)[0] - seq1.get(0)[0] > thresholdTime) {
//            return sadResult;
//        }
        double minium = Integer.MAX_VALUE;
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                // Remove the seq1 itself
                if (0 == i && 0 == j) {
                    continue;
                }
                ArrayList<Integer[]> seq2 = new ArrayList(1);

                if (blockX >= 2 && blockY >= 2) {
                    ArrayList<Integer[]> tmpSpikes = spikeTrains[blockX + i][blockY + j];
                    if (tmpSpikes != null) {
                        for (int index = 0; index < tmpSpikes.size(); index++) {
                            if (tmpSpikes.get(index)[0] >= lastTs) {
                                seq2.add(tmpSpikes.get(index));
                            }
                        }

                        double dis = vicPurDistance(seq1, seq2);
                        if (dis < minium) {
                            minium = dis;
                            sadResult.dx = -i;
                            sadResult.dy = -j;

                        }
                    }

                }

            }
        }
        lastFireIndex[blockX][blockY] = spikeTrains[blockX][blockY].size() - 1;
        if (sadResult.dx != 1 || sadResult.dy != 0) {
            // sadResult = new SADResult(0, 0, 0);
        }
        return sadResult;
    }

    private double vicPurDistance(ArrayList<Integer[]> seq1, ArrayList<Integer[]> seq2) {
        int sum1Plus = 0, sum1Minus = 0, sum2Plus = 0, sum2Minus = 0;
        Iterator itr1 = seq1.iterator();
        Iterator itr2 = seq2.iterator();
        int length1 = seq1.size();
        int length2 = seq2.size();
        double[][] distanceMatrix = new double[length1 + 1][length2 + 1];

        for (int h = 0; h <= length1; h++) {
            for (int k = 0; k <= length2; k++) {
                if (h == 0) {
                    distanceMatrix[h][k] = k;
                    continue;
                }
                if (k == 0) {
                    distanceMatrix[h][k] = h;
                    continue;
                }

                double tmpMin = Math.min(distanceMatrix[h][k - 1] + 1, distanceMatrix[h - 1][k] + 1);
                double event1 = seq1.get(h - 1)[0] - seq1.get(0)[0];
                double event2 = seq2.get(k - 1)[0] - seq2.get(0)[0];
                distanceMatrix[h][k] = Math.min(tmpMin, distanceMatrix[h - 1][k - 1] + cost * Math.abs(event1 - event2));
            }
        }

        while (itr1.hasNext()) {
            Integer[] ii = (Integer[]) itr1.next();
            if (ii[1] == 1) {
                sum1Plus += 1;
            } else {
                sum1Minus += 1;
            }
        }

        while (itr2.hasNext()) {
            Integer[] ii = (Integer[]) itr2.next();
            if (ii[1] == 1) {
                sum2Plus += 1;
            } else {
                sum2Minus += 1;
            }
        }

        // return Math.abs(sum1Plus - sum2Plus) + Math.abs(sum1Minus - sum2Minus);
        return distanceMatrix[length1][length2];
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
    private SADResult minSad(int x, int y, BitSet prevSlice, BitSet curSlice) {
        // for now just do exhaustive search over all shifts up to +/-searchDistance
        SADResult sadResult = new SADResult(0, 0, 0);
        float minSad = 1;
        for (int dx = -searchDistance; dx <= searchDistance; dx++) {
            for (int dy = -searchDistance; dy <= searchDistance; dy++) {
                float sad = sad(x, y, dx, dy, prevSlice, curSlice);
                if (sad <= minSad) {
                    minSad = sad;
                    sadResult.dx = dx;
                    sadResult.dy = dy;
                    sadResult.sadValue = minSad;
                }
            }
        }
        return sadResult;
    }

    /**
     * computes SAD centered on x,y with shift of dx,dy for prevSliceIdx
     * relative to curSliceIdx patch.
     *
     * @param x coordinate x in subSampled space
     * @param y coordinate y in subSampled space
     * @param dx block shift of x 
     * @param dy block shift of y
     * @param prevSliceIdx
     * @param curSliceIdx
     * @return SAD value
     */
    private float sad(int x, int y, int dx, int dy, BitSet prevSlice, BitSet curSlice) {
        int blockRadius = patchDimension / 2;
        // Make sure 0<=xx+dx<subSizeX, 0<=xx<subSizeX and 0<=yy+dy<subSizeY, 0<=yy<subSizeY,  or there'll be arrayIndexOutOfBoundary exception.
        if (x < blockRadius + dx || x >= subSizeX - blockRadius + dx || x < blockRadius || x >= subSizeX - blockRadius
                || y < blockRadius + dy || y >= subSizeY - blockRadius + dy || y < blockRadius || y >= subSizeY - blockRadius) {
            return 1;
        }

        float sad = 0;
        float validPixNum = 0; // The valid pixel number in the current block
        for (int xx = x - blockRadius; xx <= x + blockRadius; xx++) {
            for (int yy = y - blockRadius; yy <= y + blockRadius; yy++) {
                int d = ((curSlice.get((xx + 1) + (yy) * subSizeX)) ? 1 : 0) - 
                         ((prevSlice.get((xx + 1 - dx) + (yy - dy) * subSizeX)) ? 1 : 0);
                if (curSlice.get((xx + 1) + (yy) * subSizeX) == true) {
                    validPixNum += 1;
                }                
                if (d <= 0) {
                    d = -d;
                }
                sad += d;
            }
        }
        
        // Normalize sad
        if(sad == 0) {
            sad = 1 - (validPixNum/((2*blockRadius + 1) * (2*blockRadius + 1)));
        } else {
            sad = 1 - (validPixNum/((2*blockRadius + 1) * (2*blockRadius + 1))) * (sad/(2 * (2*blockRadius + 1) * (2*blockRadius + 1)));            
        }
        return sad;
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
            return String.format("dx,dy=%d,%5 SAD=%d", dx, dy, sadValue);
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

    public int getEventPatchDimension() {
        return eventPatchDimension;
    }

    public void setEventPatchDimension(int eventPatchDimension) {
        this.eventPatchDimension = eventPatchDimension;
        putInt("eventPatchDimension", eventPatchDimension);

    }

    public int getForwardEventNum() {
        return forwardEventNum;
    }

    public void setForwardEventNum(int forwardEventNum) {
        this.forwardEventNum = forwardEventNum;
        putInt("forwardEventNum", forwardEventNum);
    }

    public float getCost() {
        return cost;
    }

    public void setCost(float cost) {
        this.cost = cost;
        putFloat("cost", cost);
    }

    public int getThresholdTime() {
        return thresholdTime;
    }

    public void setThresholdTime(int thresholdTime) {
        this.thresholdTime = thresholdTime;
        putInt("thresholdTime", thresholdTime);
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

    public PatchCompareMethod getPatchCompareMethod() {
        return patchCompareMethod;
    }

    public void setPatchCompareMethod(PatchCompareMethod patchCompareMethod) {
        this.patchCompareMethod = patchCompareMethod;
        putString("patchCompareMethod", patchCompareMethod.toString());
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

    public boolean isDisplayOutputVectors() {
        return displayOutputVectors;
    }

    public void setDisplayOutputVectors(boolean displayOutputVectors) {
        this.displayOutputVectors = displayOutputVectors;
        putBoolean("displayOutputVectors", displayOutputVectors);

    }

    public boolean isPreProcessEnable() {
        return preProcessEnable;
    }

    public void setPreProcessEnable(boolean preProcessEnable) {
        this.preProcessEnable = preProcessEnable;
        putBoolean("preProcessEnable", preProcessEnable);
    }

    public float getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(float confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
        putFloat("confidenceThreshold", confidenceThreshold);    
    }

    private void checkArrays() {
        if (lastFireIndex == null) {
            lastFireIndex = new int[chip.getSizeX()][chip.getSizeY()];
        }
        if (eventSeqStartTs == null) {
            eventSeqStartTs = new int[chip.getSizeX()][chip.getSizeY()];
        }
    }

    /**
     *
     * @param distResult
     * @return the confidence of the result. True mens it's not good and should be rejected, false means we should accept it.
     */
    public synchronized boolean accuracyTests(SADResult distResult) {
        boolean retVal =  super.accuracyTests(); //To change body of generated methods, choose Tools | Templates.
        
        if(distResult.sadValue >= this.confidenceThreshold) {
            retVal = true || retVal;
        }
        
        return retVal;
    }

}
