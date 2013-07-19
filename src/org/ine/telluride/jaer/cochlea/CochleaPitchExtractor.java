/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.cochlea;

import java.util.logging.Logger;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

import org.ine.telluride.jaer.wowwee.RoboQuadCommands;
import org.ine.telluride.jaer.wowwee.WowWeeRSHardwareInterface;

/*
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
 */ 
/**
 * Extracts pitch from AE cochlea spike output.
 * 
 * @author tyu (teddy yu, ucsd)
 */
@Description("Extracts pitch from AE cochlea spike output.")
public class CochleaPitchExtractor extends EventFilter2D {
    private static final int NUM_CHANS=32;
  
    private int[][] spikeBuffer = null;
    private boolean[] bufferFull;
    private int[] bufferIndex = null;
    
    private int spikeCount = 0;
    private int bufferSize = 10;

    static Logger log=Logger.getLogger("HarmonicDetector");

    // 2000:200:20000 = 90 bins
    int periodMin = 2000;
    int periodMax = 20000;
    int periodStep = 200;
    private int numBins = 91;
    private int[] histogram = null;
    
    int chanNum, ii, binNum, count;
    int popThreshold = 1000;
    int ifHarmonics = 0;
    
    private int isiValue;

    int isiOrder;
    int maxISIorder = 10;
    int minISIperiod = 2000;
    
    int lastTs = 0;
// 250000 us = 0.25 s    
    int TsInterval = 250000;

    private RoboQuadCommands rCommands;
    private WowWeeRSHardwareInterface hw;
    
    int harmonicHistory = 0;
    int commandThreshold = 2;
    
    public CochleaPitchExtractor(AEChip chip) {
        super(chip);
        
        resetFilter();
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        if(in==null) return in;

        for(Object o : in) {
            if(spikeCount == 0) {
                log.info("start period here ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
            }
            spikeCount++;

            TypedEvent e=(TypedEvent) o;
            
            chanNum = e.x & 31;
            bufferIndex[chanNum]++;
            if (bufferIndex[chanNum]>=bufferSize) {
                bufferIndex[chanNum]=0;
                if (!bufferFull[chanNum]) bufferFull[chanNum]=true;
                //would it be quicker to just write without checking?
            }
            spikeBuffer[chanNum][bufferIndex[chanNum]] = e.timestamp;
            
            updateHistogram(e.x & 31, e.timestamp);            
            
            // when spike count reaches threshold amount, detect harmonic peaks
//            if(spikeCount >= popThreshold) {
            // when time elapsed reaches time threshold, detect harmonic peaks
            if(e.timestamp-lastTs > TsInterval) {
//                log.info("****** spike threshold reached *******************");
                // detect harmonics
                ifHarmonics = detectHarmonics();
                // reset spike count
//                spikeCount = 0;
                // reset last timestamp
                lastTs = lastTs + TsInterval;
                // reset histogram
                resetHistogram();
                harmonicHistory = harmonicHistory + ifHarmonics;
                if(harmonicHistory > commandThreshold) {
                    log.info("Tell Robot to Dance!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    hw.sendWowWeeCmd((short) rCommands.Dance_Demo);
                }
                if(harmonicHistory < -commandThreshold) {
                    log.info("Tell Robot to Be Surprised!!!!!!!!!!!!!!!!!!!!!");
                    hw.sendWowWeeCmd((short) rCommands.Surprise);
                }
            }
        }
        return in;
    }

    public void updateHistogram(int address, int timestamp) {
        // process spike buffer to construct histogram of ISI's
        // read in current event

        // find appropriate channel
        // calculate multiple order ISI's = spike buffer row - current event
        // compute ISI's channel by channel
        for(isiOrder=0; isiOrder<maxISIorder; isiOrder++) {
            isiValue = timestamp - spikeBuffer[address][isiOrder];

        // screen if ISI's are greater than minimum ISI value
            if(isiValue > minISIperiod) {
                if(isiValue < periodMax) {
        // for each acceptable ISI value, find appropriate bin
        // update histogram count
                    binNum = (int)((isiValue-periodMin)/periodStep);
                    histogram[binNum]++;
                }
            }
        }
        return;
    }

    public int detectHarmonics() {
        int totalNumSpikes = 0;
    
        checkHardware();
        
//        log.info("detecting harmonics - how many total spikes?");
        for(binNum=0; binNum<numBins; binNum++) {
            totalNumSpikes = totalNumSpikes + histogram[binNum];
        }
//        log.info(Integer.toString(totalNumSpikes));
        
        if(totalNumSpikes > popThreshold) {
        // search for fundamental
        // binNum = 10 -> 250 Hz, binNum = 40 -> 100 Hz
/*            
            log.info("enough spikes to process!");
            for(binNum=0; binNum<numBins; binNum++) {
                log.info(Integer.toString(histogram[binNum]));
            }
*/   
int sideOffset = 2;
int sideRange = 8;
int threshold = 125;
int minNonZeroIndex = 4;
int localMaxVal = 0;
int localMaxPos = 0;

double countRatio = 1.5;
double slopeFactor = 1.2;
double minCountLeft = 0;
double minCountRight = 0;

// determine local max value and index
    for(binNum=minNonZeroIndex; binNum<40; binNum++) {
        if(localMaxVal < histogram[binNum]) {
            localMaxVal = histogram[binNum];
            localMaxPos = binNum;
        }
    }        
/*
    log.info("detect local peak at .............................");
    log.info(Integer.toString(localMaxPos));
    log.info(Integer.toString(localMaxVal));    
*/    
// determine local side values on left and right sides
    int minCountLeftMin = minNonZeroIndex;
    int minCountLeftMax = numBins;
    int minCountRightMin = minNonZeroIndex;
    int minCountRightMax = numBins;
    
    if(localMaxPos-sideOffset-sideRange > minNonZeroIndex) {
        minCountLeftMin = localMaxPos-sideOffset-sideRange;            
    }
    if(localMaxPos-sideOffset+sideRange < numBins) {
        minCountLeftMax = localMaxPos-sideOffset+sideRange;
    }
    if(localMaxPos+sideOffset-sideRange > minNonZeroIndex) {
        minCountRightMin = localMaxPos+sideOffset-sideRange;            
    }
    if(localMaxPos+sideOffset+sideRange < numBins) {
        minCountRightMax = localMaxPos+sideOffset+sideRange;
    }

    for(ii=minCountLeftMin; ii<minCountLeftMax; ii++) {
        minCountLeft = minCountLeft + histogram[ii];
    }
    minCountLeft = minCountLeft / (minCountLeftMax-minCountLeftMin+1) * countRatio;
    for(ii=minCountRightMin; ii<minCountRightMax; ii++) {
        minCountRight = minCountRight + histogram[ii];
    }
    minCountRight= minCountRight / (minCountRightMax-minCountRightMin+1) * countRatio;

/*
    log.info(Integer.toString(histogram[minCountLeftMin]));    
    log.info(Integer.toString(histogram[minCountRightMin]));    
    log.info(Integer.toString(histogram[minCountLeftMax]));    
    log.info(Integer.toString(histogram[minCountRightMax]));    
*/

    log.info(Double.toString(minCountLeft));    
    log.info(Double.toString(minCountRight));    
    log.info(Double.toString(minCountRight*slopeFactor));    
    
    if(localMaxVal > threshold) {
        if(localMaxVal > minCountLeft) {
            if(localMaxVal > minCountRight) {
                if((minCountRight*slopeFactor) > minCountLeft) {
                    log.info("detect coo! ***************************************");
//                double harmonicFreq = 10^6/(localMaxPos*periodStep+periodMin);
  //              log.info(Integer.toString(localMaxPos));
//                log.info(Integer.toString(localMaxPos*periodStep+periodMin));
//                log.info(Double.toString(harmonicFreq));
                    return 1;
                }
            }
        }
    }
    log.info("detect hiss!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    return -1;
}
log.info("not enough spiking event inputs");
return 0;
}
    
    @Override
    public void resetFilter() {
        allocateSpikeBuffer();
        resetHistogram();
        harmonicHistory = 0;
        lastTs = 0;
    }

    @Override
    public void initFilter() {
        allocateSpikeBuffer();
        resetHistogram();
        harmonicHistory = 0;
        lastTs = 0;
    }

    public int getBufferSize() {
        return bufferSize;
    }
        
    public void setBufferSize(int bufferSize) {
        this.bufferSize=bufferSize;
        getPrefs().putInt("CochleaPitchExtractor.bufferSize", bufferSize);
    }
    
    private void allocateSpikeBuffer() {
//        log.info("allocating spike buffer");
        spikeBuffer= new int [NUM_CHANS][bufferSize];
        bufferFull = new boolean [NUM_CHANS];
        bufferIndex = new int [NUM_CHANS];
        for (chanNum=0; chanNum<NUM_CHANS; chanNum++) {
            for (ii=0; ii<bufferSize; ii++){
                spikeBuffer[chanNum][ii] = -periodMin;
            }
            bufferIndex[chanNum]=-1;
            bufferFull[chanNum]=false;
        }
    }

    private void resetHistogram() {
    // reset histogram values to 0              
        histogram = new int[numBins];
        for(binNum=0; binNum<numBins; binNum++) {
            histogram[binNum] = 0;
        }
        spikeCount = 0;
    }
    
    void checkHardware() {
        if(hw==null) {
            hw=new WowWeeRSHardwareInterface();
        }
        try {
            if(!hw.isOpen()) {
                hw.open();
            }
        } catch(HardwareInterfaceException e) {
            log.warning(e.toString());
    }
    }
}