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
    
    //temp:
    private int[][] bufferIndex = null;
    
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
            
            //---TEMP local implementation of spikeBuffer
            //ON REMOVE uncomment spikeBuffer=anf.getBuffer in void computeITD()
            TypedEvent e=(TypedEvent)o;
            chan = e.x & 31;
            id = ((e.x & 32)>0)?1:0;            
            bufferIndex[id][chan]++;
            if (bufferIndex[id][chan]>=bufferSize) {
                bufferIndex[id][chan]=0;
                if (!bufferFull[id][chan]) bufferFull[id][chan]=true;
                //would it be quicker to just write without checking?
            }
            spikeBuffer[id][chan][bufferIndex[id][chan]] = e.timestamp;
            //---END TEMP
            
            
            spikeCount++;
            if (spikeCount==100) {
                computeITD();
                System.out.println("ITD Compute!");
                for (ii=0;ii<numBins;ii++) {
                    System.out.print(ITDBins[ii]);
                }
                System.out.print('\n');
                for (ii=0;ii<numBins;ii++) {
                    System.out.print(ITDBuffer[ii]);
                }
                System.out.print('\n');
                
                spikeCount = 0;
            }
        }
        //e.x, e.timestamp
        return in;
    }

    public void computeITD() {
        //update MSO.ITDBuffer with current ITD state
        //spikeBuffer = anf.getBuffer();
        //compute delays in buffers
        for(chan=0; chan<NUM_CHANS; chan++) {
            if(includeChannelInITD[chan]) {
                for(ii=0; ii<bufferSize; ii++) {
                    for(jj=0; jj<bufferSize; jj++) {
                        delays[ii][jj]=spikeBuffer[0][chan][ii]-spikeBuffer[1][chan][jj];
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
        allocateSpikeBuffer();
    }

    @Override
    public void initFilter() {
        binWidth = getPrefs().getInt("MSO.binWidth",100);
        numBins = getPrefs().getInt("MSO.numBins", 15);
        
        allocateITDBuffers();
        allocateSpikeBuffer();
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
        System.out.println("Allocating spike buffer");
        spikeBuffer= new int[2][NUM_CHANS][bufferSize];
        bufferFull = new boolean[2][NUM_CHANS];
        delays=new int[bufferSize][bufferSize];
        
        //---TEMP
        bufferIndex = new int [2][NUM_CHANS];
        for (id=0;id<2;id++){
            for (chan=0; chan<NUM_CHANS; chan++) {
                for (ii=0;ii<bufferSize;ii++){
                    spikeBuffer[id][chan][ii] = 0;
                }
                bufferIndex[id][chan]=0;
                bufferFull[id][chan]=false;
            }
        }
        //---END TEMP
    }
    
    private void allocateITDBuffers() {
        ITDBuffer=new float[numBins];
        ITDBinEdges=new int[numBins+1];
        ITDBins=new int[numBins];

        //initialize per-channel buffer values
        for(chan=0; chan<NUM_CHANS; chan++) {
            includeChannelInITD[chan]=true;
        }

        //set ITD buffer values
        for(ii=0; ii<numBins; ii++) {
            ITDBinEdges[ii]=(-numBins*binWidth)/2+ii*binWidth;
            ITDBins[ii]=(-(numBins-1)*binWidth)/2+ii*binWidth;
            ITDBuffer[ii]=0;
        }
        ITDBinEdges[numBins]=-ITDBinEdges[0];

    }
}
