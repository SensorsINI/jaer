/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import java.util.Observable;
import java.util.Observer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author Bjoern
 */
abstract class AbstractBackgroundSelectiveFilter extends EventFilter2D implements Observer, FrameAnnotater {
    
    /** ticks per ms of input time */
    protected final int TICK_PER_MS = 1000;
    protected final int MINX = 0, MINY = 0;
    
    protected int maxX, maxY;
     
    //with outerRadius=30 and innerRadius=20 we have 1576 pixels in the inhibitory
    // range. Hence roughly 10% of the overall 16'384 pixels.
    protected int inhibitionOuterRadiusPX       = getInt("inhibitionOuterRadius",30); //The outer radius of inhibition in pixel from the current position.
    protected int inhibitionInnerRadiusPX       = getInt("inhibitionInnerRadius",28); //The inner radius of inhibition (meaning pixel closer to current than this will not inhibit) in pixel.
    //with excitationRadius=5 we average over 80 pixels around the center pixel
    protected int excitationOuterRadiusPX       = getInt("excitationOuterRadius",6); //The excitation radius in pixel from current position. By default the current cell does not self excite.
    protected int excitationInnerRadiusPX       = getInt("excitationOuterRadius",1); //The excitation radius in pixel from current position. By default the current cell does not self excite.
    protected int circleCoarseness              = getInt("circleCoarseness",2);      
    protected float maxDtMs                     = getFloat("maxDtMs",50f); //The maximum temporal distance in milliseconds between the current event and the last event in an inhibiting or exciting location that is taken into acount for the averge inhibition/excitation vector. Events with a dt larger than this will be ignored. Events with half the dt than this will contribute with 50% of their length.
    protected float exciteInhibitRatioThreshold = getFloat("exciteInhibitRatioThreshold",.45f);
    protected boolean showRawInputEnabled       = getBoolean("showRawInputEnabled",false);
    protected boolean drawInhibitExcitePoints   = getBoolean("drawInhibitExcitePoints",false);
    protected boolean drawCenterCell            = getBoolean("drawCenterCell",false); 
    protected boolean showInhibitedEvents       = getBoolean("showInhibitedEvents", true);
    protected boolean outputPolarityEvents      = getBoolean("outputPolarityEvents", false);
    
    //Filter Variables
    protected byte hasGlobalMotion;
    protected double exciteInhibitRatio = 0;
    // End filter Variables
    
    protected int[][] inhibitionCirc,excitationCirc;
    
    protected int x,y;
    
    
    public AbstractBackgroundSelectiveFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        resetFilter();
    }
    
    @Override abstract public EventPacket<?> filterPacket(EventPacket<?> in);
    
    abstract protected void checkMaps();
    
    @Override public final void resetFilter() {
        checkMaps();
        
        maxX=chip.getSizeX();
        maxY=chip.getSizeY();
        
        inhibitionCirc = PixelCircle(inhibitionOuterRadiusPX,inhibitionInnerRadiusPX,circleCoarseness);
        excitationCirc = PixelCircle(excitationOuterRadiusPX,excitationInnerRadiusPX,circleCoarseness);//inner Radius 1 means that the cell does not excite itself.
    }
    
    @Override public void initFilter() { resetFilter(); }

    @Override public void update(Observable o, Object arg) {
        if (o instanceof AEChip) {
            if (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY) {
                resetFilter();
            }
        }
    }

    private int[][] PixelCircle(int outerRadius, int innerRadius, int coarseness){
        //savely oversizing the array, we return only the first n elements anyway
        // The integer sequence A000328 of pixels in a circle is expansive to compute
        // All upper bounds involve square roots and potentiation.
        int[][] pixCirc = new int[(1+4*outerRadius*outerRadius)-(4*innerRadius*innerRadius)][2]; 
        int n = 0;
        if(coarseness<1)coarseness=1;
        
        for(int xCirc = -outerRadius; xCirc<=outerRadius; xCirc+=coarseness) {
            for(int yCirc = -outerRadius; yCirc<=outerRadius; yCirc+=coarseness) {
                if(((xCirc*xCirc)+(yCirc*yCirc) <= (outerRadius*outerRadius)) && ((xCirc*xCirc)+(yCirc*yCirc) >= (innerRadius*innerRadius))){
                    pixCirc[n][0] = xCirc;
                    pixCirc[n][1] = yCirc;
                    n++;    
                }
            }
        }
        //Here we basically just copy the result into a new array with the exact size that we need.
        // This is not a problem as this only happens once when the precomputations are done.
        int[][] res = new int[n][2];
        for(int p=0;p<n;p++) {
            res[p][0] = pixCirc[p][0];
            res[p][1] = pixCirc[p][1];
        }
        return res;
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --exciteInhibitionRatioThreshold--">
    public float getExciteInhibitRatioThreshold() {
        return exciteInhibitRatioThreshold;
    }

    public void setExciteInhibitRatioThreshold(float exciteInhibitRatioThreshold) {
        float setValue = exciteInhibitRatioThreshold;
        if(setValue > 1) setValue = 1;
        if(setValue < -1)setValue = -1;
        this.exciteInhibitRatioThreshold = setValue;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --inhibitionOuterRadius--">
    public int getInhibitionOuterRadius() {
        return inhibitionOuterRadiusPX;
    }

    public void setInhibitionOuterRadius(final int inhibitionOuterRadius) {
        this.inhibitionOuterRadiusPX = inhibitionOuterRadius;
        resetFilter(); //need to recalculate the circles
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --inhibitionInnerRadius--">
    public int getInhibitionInnerRadius() {
        return inhibitionInnerRadiusPX;
    }

    public void setInhibitionInnerRadius(final int inhibitionInnerRadius) {
        this.inhibitionInnerRadiusPX = inhibitionInnerRadius;
        resetFilter(); //need to recalculate the circles
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --excitationOuterRadius--">
    public int getExcitationOuterRadius() {
        return excitationOuterRadiusPX;
    }

    public void setExcitationOuterRadius(final int excitationRadius) {
        this.excitationOuterRadiusPX = excitationRadius;
        resetFilter(); //need to recalculate the circles
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --excitationInnerRadius--">
    public int getExcitationInnerRadius() {
        return excitationInnerRadiusPX;
    }

    public void setExcitationInnerRadius(final int excitationRadius) {
        int setValue = excitationRadius;
        if(excitationRadius <= 1) setValue = 1;
        this.excitationInnerRadiusPX = setValue;
        resetFilter(); //need to recalculate the circles
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --maxDtMs--">
    public float getMaxDtMs() {
      return maxDtMs;
    }

    public void setMaxDtMs(float maxDtMs) {
      this.maxDtMs = maxDtMs;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --circleCoarseness--">
    public int getCircleCoarseness() {
        return circleCoarseness;
    }

    public void setCircleCoarseness(int circleCoarseness) {
        if(circleCoarseness<1){
            this.circleCoarseness = 1;
        } else this.circleCoarseness = circleCoarseness;
        resetFilter(); //need to recalculate the circles
    }

    // </editor-fold>

}
