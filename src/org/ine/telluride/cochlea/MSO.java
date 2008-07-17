/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.cochlea;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.event.TypedEvent;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
//import org.ine.telluride.cochlea.ANFSpikeBuffer;

/**
 * Extracts pitch from AE cochlea spike output.
 * 
 * @author ahs (Andrew Schwartz, MIT)
 */
public class MSO extends EventFilter2D {
    private static final int NUM_CHANS=32;
    private int binWidth=getPrefs().getInt("MSO.binWidth", 100);
    {setPropertyTooltip("binWidth", "Bin width for ITD hisotgram");}
    private int numBins=getPrefs().getInt("MSO.numBins", 15);
    {setPropertyTooltip("numBins", "Total number of bins, centered about 0");}

    private float[] ITDBuffer=null;
    private int[] ITDBins=null;
    private int[] ITDBinEdges=null;
    private boolean[] includeChannelInITD = new boolean[NUM_CHANS];
    private int[][] delays=null;
    private ANFSpikeBuffer anf=null;
    private int chan, id, ii, jj, bin, count;
    private int spikeCount = 0;
    private int bufferSize = 0;
    private int[][][] spikeBuffer = null;
    private boolean[][] bufferFull;
    
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
        }
        if(in==null) {
            return in;
        }
        for(Object o : in) {
            TypedEvent e=(TypedEvent) o;
            spikeCount++;
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
        binWidth = getPrefs().getInt("MSO.binWidth",100);
        numBins = getPrefs().getInt("MSO.numBins", 15);
        
        allocateITDBuffers();
    }

    @Override
    public void initFilter() {
        allocateITDBuffers();
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
        if(anf==null) {
            anf=(ANFSpikeBuffer) chip.getFilterChain().findFilter(ANFSpikeBuffer.class);
            bufferSize = anf.getBufferSize();
            allocateSpikeBuffer();
            
            return anf!=null;
        } else {
            return true;
        }
    }
    
    private void allocateSpikeBuffer() {
        spikeBuffer= new int[NUM_CHANS][2][bufferSize];
        bufferFull = new boolean[NUM_CHANS][2];
    }
    
    private void allocateITDBuffers() {
        delays=new int[bufferSize][bufferSize];
        ITDBuffer=new float[numBins];
        ITDBinEdges=new int[numBins+1];
        ITDBins=new int[numBins];

        //initialize per-channel buffer values
        for(chan=0; chan<=NUM_CHANS; chan++) {
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
}
