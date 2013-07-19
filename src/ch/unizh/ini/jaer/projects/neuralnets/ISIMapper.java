/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import net.sf.jaer.event.BasicEvent;

/**
 * Maps inter-spike intervals onto Mel-Frequency-Cepstrum coefficients.  These 
 * are computed according to the Mel-scale:
 * 
 * Just be aware that the frequencies specified might not correspond to the 
 * frequency content of the sound (as if you're using the cochlea you likely have 
 * more than one event per wave-peak).
 * 
 * m=1127*(1+f/700)
 * 
 * @author Peter
 */
public class ISIMapper extends NetMapper{
    
    public int lastTimes[][];

    public int minBin=50;
    public int maxBin=25000;
    
    private float maxMel;
    private float minMel;
    
    
    public float scaleFactor=1127;
    public float freqMultiplier=1000000/700;
    
//    private float minFreqHz;
//    private float maxFreqHz;
    public int nBins;
    
    
    public ISIMapper(int nChannels,int nBin)
    {
        
        lastTimes=new int[nChannels][4];
        
        nBins=nBin;
        
        minMel=melScale(maxBin);
        maxMel=melScale(minBin);
        
    }
    
    
//    @Override
//    public int loc2addr(short channel, short yloc, byte source,int timestamp) 
//    {
//        int dT=timestamp-lastTimes[channel];
////        
////        int loc=(int)Math.floor(nBins*(dT-minBin)/(maxBin-minBin));
////                
//        lastTimes[channel]=timestamp;
////        
////        if (loc>0 && loc<lastTimes.length)
////            return loc;
////        else
////            return -1;
//        
//        return isi2bin(dT);
//        
//    }
    
    
    
    /** Returns a bin index for the specified ISI, or -1 if it doesn't fit anywhere */
    public int isi2bin(int isiUs)
    {
        float sc=melScale(isiUs);
        
        if (sc>=maxMel || sc<minMel)
            return -1;
        else 
            return (int)(Math.floor(nBins*(melScale(isiUs)-minMel)/(maxMel-minMel)));
        
    }
    
    public float melScale(int isiUs)
    {
        return (float)(scaleFactor*Math.log(1+freqMultiplier/isiUs));
    }
    
    
    public void setNBins(int nBin)
    {
        nBins=nBin;
    }
            
    
    
    static int hz2us(float hz)
    {
        return (int) (1000000/hz);
    }
    
    static float us2hz(int us)
    {
        return 1000000/us;
    }

    public float getMinFreqHz() {
        return us2hz(maxBin);
    }

    public void setMinFreqHz(float minFreqHz) {
        maxBin=hz2us(minFreqHz);
        minMel=melScale(maxBin);
    }

    public float getMaxFreqHz() {
        return us2hz(minBin);
    }

    public void setMaxFreqHz(float maxFreqHz) {
        minBin=hz2us(maxFreqHz);
        maxMel=melScale(minBin);
    }

//    @Override
//    public int loc2addr(short xloc, short yloc, byte source) {
//        throw new UnsupportedOperationException("Not supported yet.");
//    }

    @Override
    public int ev2addr(BasicEvent ev) {
        
        int unit=ev.address>>8;
        
        int dT=ev.timestamp-lastTimes[ev.x][unit];
        
        lastTimes[ev.x][unit]=ev.timestamp;
        
        return isi2bin(dT);
    }

    
}
