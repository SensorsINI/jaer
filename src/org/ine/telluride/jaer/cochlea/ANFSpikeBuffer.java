/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.cochlea;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
/**
 * Extracts pitch from AE cochlea spike output.
 * 
 * @author ahs (Andrew Schwartz, MIT)
 */
@Description("Keep a buffer of spikes on each channel/cochlea")
public class ANFSpikeBuffer extends EventFilter2D{
    private static final int NUM_CHANS = 32;
    private int bufferSize=getPrefs().getInt("ANFSpikeBuffer.bufferSize",25);
    {setPropertyTooltip("bufferSize","Number of spikes held per channel/cochlea in ANF spike buffer");}
    
    private int[][][] spikeBuffer = null;
    private int[][] bufferIndex = null;
    private boolean[][] bufferFull = null;
        
    int chan, id, ii, jj;
    
    public ANFSpikeBuffer(AEChip chip) {
        super(chip);

        resetFilter();
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        if(in==null) return in;

        for(Object o:in){
            TypedEvent e=(TypedEvent)o;
            chan = e.x & 31;
            id = e.y;
            bufferIndex[id][chan]++;
            if (bufferIndex[id][chan]>=bufferSize) {
                bufferIndex[id][chan]=0;
                if (!bufferFull[id][chan]) bufferFull[id][chan]=true;
                //would it be quicker to just write without checking?
            }
            spikeBuffer[id][chan][bufferIndex[id][chan]] = e.timestamp;
        
        }
        //e.x, e.timestamp
        return in;
    }

    
    public int[][][] getBuffer() {
        return spikeBuffer;
    }
    
   
    public boolean[][] getBufferFull() {
        return bufferFull;
    }
    
    @Override
    public void resetFilter() {
        allocateBuffer();
    }

    @Override
    public void initFilter() {
        allocateBuffer();
    }

    public int getBufferSize() {
        return bufferSize;
    }
    
    private void allocateBuffer() {
        //allcoate spike buffers
        spikeBuffer = new int[2][NUM_CHANS][bufferSize];
        bufferIndex = new int[2][NUM_CHANS];
        bufferFull = new boolean[2][NUM_CHANS];
        
        //initialize per-channel buffer values
        for (id=0;id<2;id++){
            for (chan=0; chan<NUM_CHANS; chan++) {
                for (ii=0;ii<bufferSize;ii++){
                    spikeBuffer[id][chan][ii] = 0;
                }
                bufferIndex[id][chan]=0;
                bufferFull[id][chan]=false;
            }
        }
    }

    public void setBufferSize(int bufferSize) {
        System.out.println("Setting buffer size");
        this.bufferSize = bufferSize;
        getPrefs().putInt("ANFSpikeBuffer.bufferSize",bufferSize);
        allocateBuffer();
    }
   
}
