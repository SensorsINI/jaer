/*
Copyright June 13, 2011 Andreas Steiner, Inst. of Neuroinformatics, UNI-ETH Zurich
*/

package ch.unizh.ini.jaer.projects.opticalflow.mdc2d;

import ch.unizh.ini.jaer.projects.opticalflow.MotionData;

/**
 * This class could be used to subtract the fixed pattern noise (FPN) from 
 * frames, thereby improving the motion calculations.
 * <br /><br />
 * 
 * <b>THIS CLASS IS IN AN EARLY DEVELOPMENTAL STAGE AND DOES NOT WORK YET; BUT IT
 *    MIGHT BE A STARTING POINT FOR SOMEONE WILLING TO WORK ON THIS ISSUE</b>
 * 
 * @author andstein
 */
public class FixedPatternNoiseRemover {
    
    private float fpn[][][];
    private float updateFactor;
    
    
    public FixedPatternNoiseRemover(MotionData data)
    {
        int n= data.getNumLocalChannels();
        float ptr[][]= data.getRawDataPixel()[0];
        fpn= new float[n][ptr.length][ptr[0].length];
        
        setUpdateFactor(.01f);
        
        setFPN(data);
    }
    

    public void setFPN(MotionData data) {
        for(int channel=0; channel<data.getNumLocalChannels(); channel++)
            setFPN(data, channel);
    }

    public void setFPN(MotionData data,int channel)
    {
        float pixels[][]= data.getRawDataPixel()[channel];
        float fpnc[][]= new float[pixels.length][pixels[0].length];
        fpn[channel]= fpnc;
        
        for(int y=0; y<fpnc.length; y++)
            System.arraycopy(pixels[y], 0, fpnc[y], 0, fpnc[y].length);
    }
    
    public void updateFPN(MotionData data) {
        for(int channel=0; channel<data.getNumLocalChannels(); channel++)
            updateFPN(data, channel);
    }
    
    
    public void updateFPN(MotionData data,int channel)
    {
        float[][] pixels= data.getRawDataPixel()[channel];
        float fpnc[][]= fpn[channel];
        
        for(int y=0; y<fpnc.length; y++)
            for(int x=0; x<fpnc[y].length; x++)
            {
                fpnc[y][x]*= 1-getUpdateFactor();
                fpnc[y][x]+= getUpdateFactor()*pixels[y][x];
            }
    }

    public float getUpdateFactor() {
        return updateFactor;
    }

    public void setUpdateFactor(float updateFactor) {
        this.updateFactor = updateFactor;
    }
    
    public void subtractFPN(MotionData data,int channel)
    {
        float[][] pixels= data.getRawDataPixel()[channel];
        float fpnc[][]= fpn[channel];
        
        for(int y=0; y<fpnc.length; y++)
            for(int x=0; x<fpnc[y].length; x++)
            {
                pixels[y][x] -= fpnc[y][x];
                pixels[y][x] += 1;
                pixels[y][x] /= 2;
            }
    }
    
    public void subtractFPN(MotionData data) {
        for(int channel=0; channel<data.getNumLocalChannels(); channel++)
            subtractFPN(data, channel);
    }
    
}
