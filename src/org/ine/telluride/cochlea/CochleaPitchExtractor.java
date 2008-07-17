/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.cochlea;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.event.TypedEvent;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;

/**
 * Extracts pitch from AE cochlea spike output.
 * 
 * @author tyu (teddy yu, ucsd)
 */
public class CochleaPitchExtractor extends EventFilter2D{
    private static final int NUM_CHANS=32;
    
    private int channelStart=getPrefs().getInt("CochleaPitchExtractor.channelStart", 0);
    {setPropertyTooltip("channelStart","starting cochlea tap for pitch");}
    private int channelEnd=getPrefs().getInt("CochleaPitchExtractor.channelEnd", 31);
    {setPropertyTooltip("channelEnd","end cochlea channel for pitch");}

    private int[] histogram = null;
    private int[][][] spikeBuffer = null;
    private boolean[][] bufferFull;
    
    private ANFSpikeBuffer anf=null;
    private int spikeCount = 0;
    private int bufferSize = 0;

    int periodMin, periodMax, periodStep;
    private int numBins = 20;
    
    int chanNum, id, ii, jj, bin, count;
    
    ANFSpikeBuffer anfSpikeBuffer=null;
    
    @Override
    public String getDescription() {
        return "Extracts pitch from AE cochlea spike output.";
    }
    
    public CochleaPitchExtractor(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        if(!checkSpikeBuffer()){
            throw new RuntimeException("Can't find prior ANFSpikeBuffer in filter chain");
        }
        if(in==null) return in;
           
        for(Object o : in) {
            TypedEvent e=(TypedEvent) o;
            spikeCount++;
            
            // with each spike update histogram
            // when spike count reaches threshold amount, detect peaks
        }
        
        
        // with each incoming spike construct histogram within frequency range
        // - minimum isi value = 2000 us
        // - histogram range = 2000 us -> 20000 us with binSize = 200 us
        // histogram is now constructed
        // - look for fundamental frequency peak in range of 100-250 Hz
        // - look for harmonic frequencies at 1/2*frequency = 2* period
        
        return in;
    }

    public void updateHistogram() {
        // process spike buffer to construct histogram of ISI's
        // read in current event
        
        // find appropriate channel
        // calculate multiple order ISI's = spike buffer row - current event
        
        // screen if ISI's are greater than minimum ISI value
        
        // for each acceptable ISI value, find appropriate bin
        // update histogram count
        
        // compute ISI's channel by channel
//        for(chanNum=0; chanNum<NUM_CHANS; chanNum++) {
//            for(ii=0; ii<bufferSize; ii++) {
//                delays[ii]=spikeBuffer[chanNum][0][ii];
//            }
//        }

        //bin delays
//        for(bin=0; ii<numBins; bin++) {
//            count=0;
//            for(ii=0; ii<bufferSize; ii++) {
                //for(ii=0; jj<bufferSize; jj++) {
//                    if(delays[ii][jj]>=ITDBinEdges[bin]&&delays[ii][jj]<=ITDBinEdges[bin+1]) {
//                        count++;
//                    }
//                }
//            }
//            ITDBuffer[bin]=count;
//        }

        return;
    }
    
    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
        
    }

    @Override
    public void initFilter() {
    }

    public int getChannelStart() {
        return channelStart;
    }

    public void setChannelStart(int channelStart) {
        this.channelStart=channelStart;
        getPrefs().putInt("CochleaPitchExtractor.channelStart", channelStart);
    }

    public int getChannelEnd() {
        return channelEnd;
    }

    public void setChannelEnd(int channelEnd) {
        this.channelEnd=channelEnd;
        getPrefs().putInt("CochleaPitchExtractor.channelEnd", channelEnd);
    }
    
    private void allocateSpikeBuffer() {
        spikeBuffer= new int[NUM_CHANS][2][bufferSize];
        bufferFull = new boolean[NUM_CHANS][2];
    }
        
    private boolean checkSpikeBuffer() {
    // construct spike buffer if does not already exist
        if(anf==null) {
            anf=(ANFSpikeBuffer) chip.getFilterChain().findFilter(ANFSpikeBuffer.class);
            bufferSize = anf.getBufferSize();
            allocateSpikeBuffer();
            
            return anf!=null;
        } else {
            return true;
        }
    }

    private void initHistogram() {
    // initialize histogram           
        for(bin=0; bin<numBins; bin++) {
            histogram[bin] = 0;
        }
    }

}
