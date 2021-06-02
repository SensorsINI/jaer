/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.freme;

import java.awt.Color;
import java.util.Arrays;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.FremeExtractor;

/**
 * Class that creates a map of local edge orientations. Adapted from the 
 * OrientationFreme
 * 
 * @author Christian
 */
@Description("Local orientation by spatio-temporal correlation")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class OrientationFreme extends FremeExtractor{
    
    protected int[] lastTimesOn, lastTimesOff; 
    protected float[] oriHistoryMap;
    protected Freme<Float> freme;
    
    private boolean useWideKernel = getPrefs().getBoolean("OrientationFreme.useWideKernel",false);
    {setPropertyTooltip("useWideKernel","Should the orientation be computed with a wide kernel?");}
    
    public OrientationFreme(AEChip chip){
        super(chip);
        this.chip = chip;
        freme = new Freme<Float>(chip.getSizeX(), chip.getSizeY());
    }
    
    @Override
    public void resetFilter() {
        rgbValues = null;
        freme = null;
        checkFreme();
        Arrays.fill(lastTimesOn, -1);
        Arrays.fill(lastTimesOff, -1);
        Arrays.fill(oriHistoryMap, 0.0f);
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    @Override
    public EventPacket filterPacket(EventPacket in) {
        if ( in == null ){
            return null;
        }
        if ( enclosedFilter != null ){
            in = enclosedFilter.filterPacket(in);
        }

        Class inputClass = in.getEventClass();
        if ( !( inputClass == PolarityEvent.class) ){
            log.warning("wrong input event class "+in.getEventClass()+" in the input packet" + in + ", disabling filter");
            setFilterEnabled(false);
            return in;
        }

        checkFreme();
        if(renderNew)Arrays.fill(rgbValues, 0.0f);
        
        for ( Object ein:in ){
            PolarityEvent e = (PolarityEvent)ein;
            int x = e.x;
            int y = e.y;
            int idx = getIndex(x, y);
            boolean isOn = e.getPolaritySignum()>0;

            setTs(idx, e.timestamp, isOn);
                    
            if(x>0 && x<sizeX-1 && y>0 && y<sizeY-1){
                if(useWideKernel){
                    set(idx, getOriWideKernel(x, y, isOn));
                }else{
                    set(idx, getOriNarrowKernel(x, y, isOn));
                }
            }
        }
        
        repaintFreme();
        
        return in;
    }
    
    private float getOriWideKernel(int x, int y, boolean isOn){
        //horizontal orientation
        float horizontal = getTs(getIndex(x+1, y+1), isOn)-getTs(getIndex(x-1, y+1), isOn);
        horizontal += getTs(getIndex(x+1, y), isOn)-getTs(getIndex(x-1, y), isOn);
        horizontal += getTs(getIndex(x+1, y-1), isOn)-getTs(getIndex(x-1, y-1), isOn);

        //vertical orientation
        float vertical = getTs(getIndex(x-1, y+1), isOn)-getTs(getIndex(x-1, y-1), isOn);
        vertical += getTs(getIndex(x, y+1), isOn)-getTs(getIndex(x, y-1), isOn);
        vertical += getTs(getIndex(x+1, y+1), isOn)-getTs(getIndex(x+1, y-1), isOn);
        return (float)Math.atan(vertical/horizontal);
    }
    
    private float getOriNarrowKernel(int x, int y, boolean isOn){
        //horizontal orientation
        float horizontal = getTs(getIndex(x+1, y), isOn)-getTs(getIndex(x-1, y), isOn);

        //vertical orientation
        float vertical = getTs(getIndex(x, y+1), isOn)-getTs(getIndex(x, y-1), isOn);
        
        return (float)Math.atan(vertical/horizontal);
    }
    
    private void setTs(int idx, int ts, boolean isOn){
        if(isOn){
            lastTimesOn[idx] = ts;
        }else{
            lastTimesOff[idx] = ts;
        }
    }
    
    private int getTs(int idx, boolean isOn){
        if(isOn){
            return lastTimesOn[idx];
        }else{
            return lastTimesOff[idx];
        }
    }
    
    public float get(int idx){
        return freme.get(idx);
    }
    
    public void set(int idx, float value){
        freme.rangeCheck(idx);
        freme.set(idx, value);
        setRGB(idx);
    }
    
    @Override
    public void setRGB(int idx){
        float value = freme.get(idx);
        int nIdx = 3*idx;
        value += (float)Math.PI/2;
        value = value/(float)Math.PI;
        Color color = Color.getHSBColor(value, 1.0f, 1.0f);
        rgbValues[nIdx++] = (float)color.getRed()/255.0f;
        rgbValues[nIdx++] = (float)color.getGreen()/255.0f;
        rgbValues[nIdx] = (float)color.getBlue()/255.0f;
    }
    
    public boolean isUseWideKernel (){
        return useWideKernel;
    }

    public void setUseWideKernel (boolean useWideKernel){
        this.useWideKernel = useWideKernel;
        getPrefs().putBoolean("OrientationFreme.useWideKernel",useWideKernel);
    }
    
    @Override
    public void checkFreme() {
        checkDisplay();
        if(freme==null || freme.size() != size){
            freme = new Freme<Float>(sizeX, sizeY);
            freme.fill(0.0f);
        }
        if(lastTimesOn == null || lastTimesOff == null || oriHistoryMap == null 
                || lastTimesOn.length != size || lastTimesOff.length != size || oriHistoryMap.length != size ){
            lastTimesOn = new int[size];
            lastTimesOff = new int[size];
            oriHistoryMap = new float[size];
        }
    }

    @Override
    public Freme<Float> getFreme() {
        return freme;
    }
    
}
