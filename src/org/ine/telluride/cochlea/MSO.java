/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.cochlea;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.event.TypedEvent;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import org.ine.telluride.cochlea.ANFSpikeBuffer;
/**
 * Extracts pitch from AE cochlea spike output.
 * 
 * @author ahs (Andrew Schwartz, MIT)
 */
public class MSO extends EventFilter2D {
    private static final int NUM_CHANS=32;
    private int bufferSize=getPrefs().getInt("MSO.bufferSize", 25);
    

    {
        setPropertyTooltip("bufferSize", "Number of spikes held in ITD buffer");
    }
    private int binWidth=getPrefs().getInt("MSO.binWidth", 100);
    

    {
        setPropertyTooltip("binWidth", "Bin width for ITD hisotgram");
    }
    private int numBins=getPrefs().getInt("MSO.numBins", 15);
    

    {
        setPropertyTooltip("numBins", "Total number of bins, centered about 0");
    }
    private int[][][] spikeBuffer=null;
    private int[][] bufferIndex=null;
    private boolean[][] bufferFull=null;
    private float[] ITDBuffer=null;
    private int[] ITDBins=null;
    private int[] ITDBinEdges=null;
    private boolean[] includeChannelInITD=null;
    private int[][] delays=null;
    ANFSpikeBuffer anfSpikeBuffer=null;
    int chan, id, ii, jj, bin, count;

    @Override
    public String getDescription() {
        return "Computes ITD of incoming binaural signal";
    }

    public MSO(AEChip chip) {
        super(chip);

        resetFilter();
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) {
            return in;
        }
        if(!checkSpikeBuffer()){
            throw new RuntimeException("Can't find prior ANFSpikeBuffer in filter chain");
        };
        if(in==null) {
            return in;
        }
        for(Object o : in) {
            TypedEvent e=(TypedEvent) o;
            chan=e.x&31;
            id=e.x&32;
            spikeBuffer[chan][id][bufferIndex[chan][id]]=e.timestamp;
            bufferIndex[chan][id]++;
            if(bufferIndex[chan][id]>bufferSize) {
                bufferIndex[chan][id]=0;
                if(!bufferFull[chan][id]) {
                    bufferFull[chan][id]=true;
                //would it be quicker to just write without checking?
                }
            }

        }
        //e.x, e.timestamp
        return in;
    }

    public void computeITD() {
        //update MSO.ITDBuffer with current ITD state

        //compute delays in buffers
        for(chan=0; chan<NUM_CHANS; chan++) {
            if(includeChannelInITD[chan]) {
                for(ii=0; ii<bufferSize; ii++) {
                    for(jj=0; ii<bufferSize; jj++) {
                        delays[ii][jj]=spikeBuffer[chan][0][ii]-spikeBuffer[chan][1][jj];
                    }
                }
            }
        }

        //bin delays
        for(bin=0; ii<numBins; bin++) {
            count=0;
            for(ii=0; ii<bufferSize; ii++) {
                for(ii=0; jj<bufferSize; jj++) {
                    if(delays[ii][jj]>=ITDBinEdges[bin]&&delays[ii][jj]<=ITDBinEdges[bin+1]) {
                        count++;
                    }
                }
            }
            ITDBuffer[bin]=count;
        }

        return;
    }

    public float[] getITD() {
        return ITDBuffer;
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
        //allcoate spike buffers
        spikeBuffer=new int[NUM_CHANS][2][bufferSize];
        bufferIndex=new int[NUM_CHANS][2];
        bufferFull=new boolean[NUM_CHANS][2];
        includeChannelInITD=new boolean[NUM_CHANS];
        delays=new int[bufferSize][bufferSize];

        //define ITD bins from parameters
        ITDBuffer=new float[numBins];
        ITDBinEdges=new int[numBins+1];
        ITDBins=new int[numBins];

        //initialize per-channel buffer values
        for(chan=0; chan<=NUM_CHANS; chan++) {
            for(id=0; id<2; id++) {
                for(ii=0; ii<bufferSize; ii++) {
                    spikeBuffer[chan][id][ii]=0;
                }
                bufferIndex[chan][id]=0;
                bufferFull[chan][id]=false;
            }
            includeChannelInITD[chan]=true;
        }

        //allocate ITD buffer values
        for(ii=0; ii<numBins; ii++) {
            ITDBinEdges[ii]=(-numBins*binWidth)/2+ii*binWidth;
            ITDBins[ii]=(-(numBins-1)*binWidth)/2+ii*binWidth;
            ITDBuffer[ii]=0;
        }
        ITDBinEdges[numBins+1]=-ITDBinEdges[0];

    }

    @Override
    public void initFilter() {
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize=bufferSize;
        getPrefs().putInt("MSO.bufferSize", bufferSize);
    }

    public int getBinWidth() {
        return binWidth;
    }

    public void setBinWidth(int binWidth) {
        this.binWidth=binWidth;
        getPrefs().putInt("MSO.binWidth", binWidth);
    }

    public int getNumBins() {
        return numBins;
    }

    public void setNumBins(int numBins) {
        this.numBins=numBins;
        getPrefs().putInt("MSO.numBins", numBins);
    }

    private boolean checkSpikeBuffer() {
        if(anfSpikeBuffer==null) {
            anfSpikeBuffer=(ANFSpikeBuffer) chip.getFilterChain().findFilter(ANFSpikeBuffer.class);
            return true;
        } else {
            return false;
        }
    }
}
