/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.minliu;

import ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlow;
import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.awt.geom.Point2D;
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
import net.sf.jaer.util.filter.HighpassFilter;

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
    private int tMinus2SliceIdx = 0, tMinus1SliceIdx = 1, currentSliceIdx = 2;
    private int[][] currentSlice = null, tMinus1Slice = null, tMinus2Slice = null;
    private BitSet[] histogramsBitSet = null;
    private BitSet currentSli = null, tMinus1Sli = null, tMinus2Sli = null;
    private int patchDimension = getInt("patchDimension", 8);
    protected boolean measurePerformance = getBoolean("measurePerformance", false);
    private boolean displayOutputVectors = getBoolean("displayOutputVectors", true);
    public enum PatchCompareMethod {
        HammingDistance, SAD
    };
    private PatchCompareMethod patchCompareMethod = PatchCompareMethod.valueOf(getString("patchCompareMethod", PatchCompareMethod.HammingDistance.toString()));

    private int sliceDurationUs = getInt("sliceDurationUs", 1000);
    private int sliceEventCount = getInt("sliceEventCount", 1000);
    private String patchTT = "Patch matching";
    private float sadSum = 0;
    private boolean rewindFlg = false; // The flag to indicate the rewind event.
    private TransformAtTime lastTransform = null, imageTransform = null;
    private FilterChain filterChain;
    private Steadicam cameraMotion;
    private int packetNum;
    private int sx2;
    private int sy2;
    private double panTranslationDeg;
    private double tiltTranslationDeg;
    private float rollDeg;
    private int lastImuTimestamp = 0;
    private float panRate = 0, tiltRate = 0, rollRate = 0; // in deg/sec
    private float panOffset = getFloat("panOffset", 0), tiltOffset = getFloat("tiltOffset", 0), rollOffset = getFloat("rollOffset", 0);
    private float panDC = 0, tiltDC = 0, rollDC = 0;
    private boolean showTransformRectangle = getBoolean("showTransformRectangle", true);
    private boolean removeCameraMotion = getBoolean("removeCameraMotion", true);

 // calibration
    private boolean calibrating = false; // used to flag calibration state
    private int calibrationSampleCount = 0;
    private int NUM_CALIBRATION_SAMPLES_DEFAULT = 800; // 400 samples /sec
    protected int numCalibrationSamples = getInt("numCalibrationSamples", NUM_CALIBRATION_SAMPLES_DEFAULT);
    private CalibrationFilter panCalibrator, tiltCalibrator, rollCalibrator;
    TextRenderer imuTextRenderer = null;
    private boolean showGrid = getBoolean("showGrid", true);
    private int flushCounter = 0;
    public enum SliceMethod {
        ConstantDuration, ConstantEventNumber
    };
    private SliceMethod sliceMethod = SliceMethod.valueOf(getString("sliceMethod", SliceMethod.ConstantDuration.toString()));
    private int eventCounter = 0;
    // private int sliceLastTs = Integer.MIN_VALUE;
    private int sliceLastTs = 0;
    HighpassFilter panTranslationFilter = new HighpassFilter();
    HighpassFilter tiltTranslationFilter = new HighpassFilter();
    HighpassFilter rollFilter = new HighpassFilter();
    private float highpassTauMsTranslation = getFloat("highpassTauMsTranslation", 1000);
    private float highpassTauMsRotation = getFloat("highpassTauMsRotation", 1000);
    private boolean highPassFilterEn = getBoolean("highPassFilterEn", false);


    public PatchMatchFlow(AEChip chip) {
        super(chip);
        
        filterChain = new FilterChain(chip);
        cameraMotion = new Steadicam(chip);
        cameraMotion.setFilterEnabled(true);
        cameraMotion.setDisableRotation(true);
        cameraMotion.setDisableTranslation(true);
        // filterChain.add(cameraMotion);
        setEnclosedFilterChain(filterChain);
        
        String imu = "IMU";

        chip.addObserver(this); // to allocate memory once chip size is known
        setPropertyTooltip(patchTT, "patchDimension", "linear dimenion of patches to match, in pixels");
        setPropertyTooltip(patchTT, "searchDistance", "search distance for matching patches, in pixels");
        setPropertyTooltip(patchTT, "patchCompareMethod", "method to compare two patches");
        setPropertyTooltip(patchTT, "sliceDurationUs", "duration of patches in us");
        setPropertyTooltip(patchTT, "sliceEventCount", "number of collected events in each bitmap");
        setPropertyTooltip(dispTT, "highpassTauMsTranslation", "highpass filter time constant in ms to relax transform back to zero for translation (pan, tilt) components");
        setPropertyTooltip(dispTT, "highpassTauMsRotation", "highpass filter time constant in ms to relax transform back to zero for rotation (roll) component");
        setPropertyTooltip(dispTT, "highPassFilterEn", "enable the high pass filter or not");
        setPropertyTooltip(dispTT, "showTransformRectangle", "Disable to not show the red transform square and red cross hairs");
        setPropertyTooltip(dispTT, "displayOutputVectors", "display the output motion vectors or not");
        setPropertyTooltip(imu, "removeCameraMotion", "Remove the camera motion");
        setPropertyTooltip(imu, "zeroGyro", "zeros the gyro output. Sensor should be stationary for period of 1-2 seconds during zeroing");
        setPropertyTooltip(imu, "eraseGyroZero", "Erases the gyro zero values");

        panCalibrator = new CalibrationFilter();
        tiltCalibrator = new CalibrationFilter();
        rollCalibrator = new CalibrationFilter();
        rollFilter.setTauMs(highpassTauMsRotation);
        panTranslationFilter.setTauMs(highpassTauMsTranslation);
        tiltTranslationFilter.setTauMs(highpassTauMsTranslation);
        
        lastTransform = new TransformAtTime(ts,
        new Point2D.Float(
                (float)(0),
                (float)(0)),
                (float) (0));
    }

    @Override
    public EventPacket filterPacket(EventPacket in) {
        setupFilter(in);
        sadSum = 0;

        packetNum++;

        ApsDvsEventPacket in2 = (ApsDvsEventPacket) in;
        Iterator itr = in2.fullIterator();   // We also need IMU data, so here we use the full iterator. 
        while (itr.hasNext()) {
            Object ein = itr.next();
            if (ein == null) {
                log.warning("null event passed in, returning input packet");
                return in;
            }
            
            extractEventInfo(ein);
            ApsDvsEvent apsDvsEvent = (ApsDvsEvent) ein;
            if (apsDvsEvent.isImuSample()) {
                IMUSample s = apsDvsEvent.getImuSample();
                lastTransform = updateTransform(s);
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

            if(!removeCameraMotion) {
                showTransformRectangle = true;
                int nx = e.x - 120, ny = e.y - 90;
                e.x = (short) ((((lastTransform.cosAngle * nx) - (lastTransform.sinAngle * ny)) + lastTransform.translationPixels.x) + 120);
                e.y = (short) (((lastTransform.sinAngle * nx) + (lastTransform.cosAngle * ny) + lastTransform.translationPixels.y) + 90);
                e.address = chip.getEventExtractor().getAddressFromCell(e.x, e.y, e.getType()); // so event is logged properly to disk
                if ((e.x > 239) || (e.x < 0) || (e.y > 179) || (e.y < 0)) {
                    e.setFilteredOut(true); // TODO this gradually fills the packet with filteredOut events, which are never seen afterwards because the iterator filters them outputPacket in the reused packet.
                    continue; // discard events outside chip limits for now, because we can't render them presently, although they are valid events
                } else {
                    e.setFilteredOut(false);
                }
                extractEventInfo(e); // Update x, y, ts and type    
            } else {
                showTransformRectangle = false;
            }
            
            long startTime = 0;
            if (measurePerformance) {
                    startTime = System.nanoTime();
            }
            // compute flow
            maybeRotateSlices();
            accumulateEvent();
            SADResult result = new SADResult(0,0,0);
            switch(patchCompareMethod) {
                case HammingDistance: 
                    result = minHammingDistance(x, y, tMinus2Sli, tMinus1Sli);
                    break;
                case SAD:
                    result = minSad(x, y, tMinus2Slice, tMinus1Slice);
                    break;
            }            
            vx = result.dx * 5;
            vy = result.dy * 5;
            v = (float) Math.sqrt(vx * vx + vy * vy);
            
            if (measurePerformance) {
                long dt = System.nanoTime() - startTime;
                float us = 1e-3f * dt;
                log.info(String.format("Per event processing time: %.1fus", us));
            }
//            long[] testByteArray1 = tMinus1Sli.toLongArray();
//            long[] testByteArray2 = tMinus2Sli.toLongArray();
//            tMinus1Sli.andNot(tMinus2Sli);

//            long test1 = popcount_3((long) sadSum);
            
//                DavisChip apsDvsChip = (DavisChip) chip;
//                int frameStartTimestamp = apsDvsChip.getFrameExposureStartTimestampUs();
//                int frameEndTimestamp = apsDvsChip.getFrameExposureEndTimestampUs();
//                int frameCounter = apsDvsChip.getFrameCount();
//                // if a frame has been read outputPacket, then save the last transform to apply to rendering this frame
//                imageTransform = lastTransform;
//                ChipRendererDisplayMethodRGBA displayMethod = (ChipRendererDisplayMethodRGBA) chip.getCanvas().getDisplayMethod(); // TODO not ideal (tobi)
//                displayMethod.setImageTransform(lastTransform.translationPixels, lastTransform.rotationRad);
                                    
            // reject values that are unreasonable
            if (accuracyTests()) {
                continue;
            }

            if(displayOutputVectors) {
                writeOutputEvent();
            }
            if (measureAccuracy) {
                motionFlowStatistics.update(vx, vy, v, vxGT, vyGT, vGT);
            }
        }
        
//        if(cameraMotion.getLastTransform() != null) {
//            lastTransform = cameraMotion.getLastTransform();
//        }
//        ChipRendererDisplayMethodRGBA displayMethod = (ChipRendererDisplayMethodRGBA) chip.getCanvas().getDisplayMethod();        // After the rewind event, restore sliceLastTs to 0 and rewindFlg to false.
//        displayMethod.getImageTransform();
//        displayMethod.setImageTransform(lastTransform.translationPixels, lastTransform.rotationRad);          

        
        if(rewindFlg) {
            rewindFlg = false;
            sliceLastTs = 0;
            flushCounter = 10;
            
            panDC = 0;
            tiltDC = 0;
            rollDC = 0;
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
        if (histogramsBitSet == null) {
            histogramsBitSet = new BitSet[numSlices];
        }
        
        for(int ii = 0; ii < numSlices; ii ++) {
            histogramsBitSet[ii] = new BitSet(subSizeX*subSizeY);
        }  
        
        tMinus2SliceIdx = 0;
        tMinus1SliceIdx = 1;
        currentSliceIdx = 2;
        assignSliceReferences();
                            
        sliceLastTs = 0;
        packetNum = 0;
        rewindFlg = true;
    }

//    @Override
//    public void annotate(GLAutoDrawable drawable) {
//        GL2 gl = null;
//        if (showTransformRectangle) {
//            gl = drawable.getGL().getGL2();
//        }
//
//        if (gl == null) {
//            return;
//        }
//        // draw transform
//        gl.glPushMatrix();
//        
//        // Use this blur rectangle to indicate where is the zero point position.
//        gl.glColor4f(.1f, .1f, 1f, .25f);
//        gl.glRectf(0, 0, 10, 10);
//        
//        gl.glLineWidth(1f);
//        gl.glColor3f(1, 0, 0);
//        
//        if(chip != null) {
//            sx2 = chip.getSizeX() / 2;
//            sy2 = chip.getSizeY() / 2;
//        } else {
//            sx2 = 0;
//            sy2 = 0;
//        }
//        // translate and rotate
//        if(lastTransform != null) {
//            gl.glTranslatef(lastTransform.translationPixels.x + sx2, lastTransform.translationPixels.y + sy2, 0);
//            gl.glRotatef((float) ((lastTransform.rotationRad * 180) / Math.PI), 0, 0, 1);            
//            
//            // draw xhairs on frame to help show locations of objects and if they have moved.
//           gl.glBegin(GL.GL_LINES); // sequence of individual segments, in pairs of vertices
//           gl.glVertex2f(0, 0);  // start at origin
//           gl.glVertex2f(sx2, 0);  // outputPacket to right
//           gl.glVertex2f(0, 0);  // origin
//           gl.glVertex2f(-sx2, 0); // outputPacket to left
//           gl.glVertex2f(0, 0);  // origin
//           gl.glVertex2f(0, sy2); // up
//           gl.glVertex2f(0, 0);  // origin
//           gl.glVertex2f(0, -sy2); // down
//           gl.glEnd();
//
//           // rectangle around transform
//           gl.glTranslatef(-sx2, -sy2, 0); // lower left corner
//           gl.glBegin(GL.GL_LINE_LOOP); // loop of vertices
//           gl.glVertex2f(0, 0); // lower left corner
//           gl.glVertex2f(sx2 * 2, 0); // lower right
//           gl.glVertex2f(2 * sx2, 2 * sy2); // upper right
//           gl.glVertex2f(0, 2 * sy2); // upper left
//           gl.glVertex2f(0, 0); // back of lower left
//           gl.glEnd();
//           gl.glPopMatrix();
//        }      
//    }
    
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
     * Computes transform using current gyro outputs based on timestamp supplied
     * and returns a TransformAtTime object. 
     *
     * @param timestamp the timestamp in us.
     * @return the transform object representing the camera rotationRad
     */
    synchronized public TransformAtTime updateTransform(IMUSample imuSample) {
        int timestamp = imuSample.getTimestampUs();
        float dtS = (timestamp - lastImuTimestamp) * 1e-6f;
        lastImuTimestamp = timestamp;
        
        if (flushCounter-- >= 0) {
            return new TransformAtTime(ts,
                    new Point2D.Float( (float)(0),(float)(0)),
                    (float) (0));  // flush some samples if the timestamps have been reset and we need to discard some samples here
        }
        
        panRate = imuSample.getGyroYawY();
        tiltRate = imuSample.getGyroTiltX();
        rollRate = imuSample.getGyroRollZ();
        if (calibrating) {
            calibrationSampleCount++;
            if (calibrationSampleCount > numCalibrationSamples) {
                calibrating = false;
                panOffset = panCalibrator.computeAverage();
                tiltOffset = tiltCalibrator.computeAverage();
                rollOffset = rollCalibrator.computeAverage();
                panDC = 0;
                tiltDC = 0;
                rollDC = 0;
                putFloat("panOffset", panOffset);
                putFloat("tiltOffset", tiltOffset);
                putFloat("rollOffset", rollOffset);
                log.info(String.format("calibration finished. %d samples averaged to (pan,tilt,roll)=(%.3f,%.3f,%.3f)", numCalibrationSamples, panOffset, tiltOffset, rollOffset));
            } else {
                panCalibrator.addSample(panRate);
                tiltCalibrator.addSample(tiltRate);
                rollCalibrator.addSample(rollRate);
            }
            return new TransformAtTime(ts,
                                new Point2D.Float( (float)(0),(float)(0)),
                                (float) (0));
        }       
        
        panDC += getPanRate() * dtS;
        tiltDC += getTiltRate() * dtS;
        rollDC += getRollRate() * dtS;
        
        if(highPassFilterEn) {
        panTranslationDeg = panTranslationFilter.filter(panDC, timestamp);
        tiltTranslationDeg = tiltTranslationFilter.filter(tiltDC, timestamp);
        rollDeg = rollFilter.filter(rollDC, timestamp);            
        } else {
            panTranslationDeg = panDC;
            tiltTranslationDeg = tiltDC;
            rollDeg = rollDC;
        }
        float radValPerPixel = (float) Math.atan(chip.getPixelWidthUm() / (1000 * getLensFocalLengthMm()));
        
        // Use the lens focal length and camera resolution.
        TransformAtTime tr = new TransformAtTime(timestamp,
                new Point2D.Float(
                        (float) ((Math.PI / 180) * panTranslationDeg/radValPerPixel),
                        (float) ((Math.PI / 180) * tiltTranslationDeg/radValPerPixel)),
                (-rollDeg * (float) Math.PI) / 180);
        return tr;
    }    
    
    
    private class CalibrationFilter {

        int count = 0;
        float sum = 0;

        void reset() {
            count = 0;
            sum = 0;
        }

        void addSample(float sample) {
            sum += sample;
            count++;
        }

        float computeAverage() {
            return sum / count;
        }
    }
    
    /**
     * @return the panRate
     */
    public float getPanRate() {
        return panRate - panOffset;
    }

    /**
     * @return the tiltRate
     */
    public float getTiltRate() {
        return tiltRate - tiltOffset;
    }

    /**
     * @return the rollRate
     */
    public float getRollRate() {
        return rollRate - rollOffset;
    }
    
    /**
     * uses the current event to maybe rotate the slices
     */
    private void maybeRotateSlices() {
        switch (sliceMethod) {
            case ConstantDuration:
                int dt = ts - sliceLastTs;
                if(rewindFlg) {
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
    
    synchronized public void doEraseGyroZero() {
        panOffset = 0;
        tiltOffset = 0;
        rollOffset = 0;
        putFloat("panOffset", 0);
        putFloat("tiltOffset", 0);
        putFloat("rollOffset", 0);
        log.info("calibration erased");
    }
    
    synchronized public void doZeroGyro() {
        calibrating = true;
        calibrationSampleCount = 0;
        panCalibrator.reset();
        tiltCalibrator.reset();
        rollCalibrator.reset();
        log.info("calibration started");

//        panOffset = panRate; // TODO offsets should really be some average over some samples
//        tiltOffset = tiltRate;
//        rollOffset = rollRate;
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
        int minSum = Integer.MAX_VALUE, sum = 0;
        SADResult sadResult = new SADResult(0, 0, 0);
        for (int dx = -searchDistance; dx <= searchDistance; dx++) {
            for (int dy = -searchDistance; dy <= searchDistance; dy++) {                
                sum = hammingDistance(x, y, dx, dy, prevSlice, curSlice);
                if(sum < minSum) {
                    minSum = sum;
                    sadResult.dx = dx;
                    sadResult.dy = dy;
                    sadResult.sadValue = minSum;
                }
            }
        }
        if(sadResult.dx != 1 || sadResult.dy != 1) {
            int tmp = 0;
        }
        return sadResult;
    }
    
    /**
     * computes Hamming distance centered on x,y with patch of patchSize for prevSliceIdx
     * relative to curSliceIdx patch.
     *
     * @param x coordinate in subSampled space
     * @param y
     * @param patchSize
     * @param prevSliceIdx
     * @param curSliceIdx
     * @return SAD value
     */
    private int hammingDistance(int x, int y, int dx, int dy, BitSet prevSlice, BitSet curSlice) {
        int retVal = 0;
        
        // Make sure 0<=xx+dx<subSizeX, 0<=xx<subSizeX and 0<=yy+dy<subSizeY, 0<=yy<subSizeY,  or there'll be arrayIndexOutOfBoundary exception.
        if (x < patchDimension - dx || x >= subSizeX - patchDimension - dx || x < patchDimension || x >= subSizeX - patchDimension
                || y < patchDimension - dy || y >= subSizeY - patchDimension - dy || y < patchDimension || y >= subSizeY - patchDimension) {
            return 0;
        }
        
        for (int xx = x - patchDimension; xx <= x + patchDimension; xx++) {
            for (int yy = y - patchDimension; yy <= y + patchDimension; yy++) {
                if(curSlice.get((xx + 1 + dx) + (yy + dy) * subSizeX) != prevSlice.get((xx + 1) + (yy) * subSizeX)) {
                    retVal += 1;
                }   
            }
        }
        return retVal;
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
        for (int dx = -searchDistance; dx <= searchDistance; dx++) {
            for (int dy = -searchDistance; dy <= searchDistance; dy++) {
                int sad = sad(x, y, dx, dy, prevSlice, curSlice);
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
     * @param x coordinate in subSampled space
     * @param y
     * @param dx
     * @param dy
     * @param prevSliceIdx
     * @param curSliceIdx
     * @return SAD value
     */
    private int sad(int x, int y, int dx, int dy, int[][] prevSlice, int[][] curSlice) {
        // Make sure 0<=xx+dx<subSizeX, 0<=xx<subSizeX and 0<=yy+dy<subSizeY, 0<=yy<subSizeY,  or there'll be arrayIndexOutOfBoundary exception.
        if (x < patchDimension - dx || x >= subSizeX - patchDimension - dx || x < patchDimension || x >= subSizeX - patchDimension
                || y < patchDimension - dy || y >= subSizeY - patchDimension - dy || y < patchDimension || y >= subSizeY - patchDimension) {
            return 0;
        }
        
        int sad = 0;
        for (int xx = x - patchDimension; xx <= x + patchDimension; xx++) {
            for (int yy = y - patchDimension; yy <= y + patchDimension; yy++) {
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

    public boolean isShowTransformRectangle() {
        return showTransformRectangle;
    }

    public void setShowTransformRectangle(boolean showTransformRectangle) {
        this.showTransformRectangle = showTransformRectangle;
    }

    public boolean isRemoveCameraMotion() {
        return removeCameraMotion;
    }

    public void setRemoveCameraMotion(boolean removeCameraMotion) {
        this.removeCameraMotion = removeCameraMotion;
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

    public float getHighpassTauMsTranslation() {
        return highpassTauMsTranslation;
    }

    public void setHighpassTauMsTranslation(float highpassTauMs) {
        this.highpassTauMsTranslation = highpassTauMs;
        putFloat("highpassTauMsTranslation", highpassTauMs);
        panTranslationFilter.setTauMs(highpassTauMs);
        tiltTranslationFilter.setTauMs(highpassTauMs);   
    }

    public float getHighpassTauMsRotation() {
        return highpassTauMsRotation;
    }

    public void setHighpassTauMsRotation(float highpassTauMs) {
        this.highpassTauMsRotation = highpassTauMs;
        putFloat("highpassTauMsRotation", highpassTauMs);
        rollFilter.setTauMs(highpassTauMs);    
    }

    public boolean isHighPassFilterEn() {
        return highPassFilterEn;
    }

    public void setHighPassFilterEn(boolean highPassFilterEn) {
        this.highPassFilterEn = highPassFilterEn;
        putBoolean("highPassFilterEn", highPassFilterEn);
    }

    public boolean isMeasurePerformance() {
        return measurePerformance;
    }

    public void setMeasurePerformance(boolean measurePerformance) {
        this.measurePerformance = measurePerformance;
        putBoolean("measurePerformance", measurePerformance);
    }

    public boolean isDisplayOutputVectors() {
        return displayOutputVectors;
    }

    public void setDisplayOutputVectors(boolean displayOutputVectors) {
        this.displayOutputVectors = displayOutputVectors;
        putBoolean("displayOutputVectors", measurePerformance);

    }
    
}
