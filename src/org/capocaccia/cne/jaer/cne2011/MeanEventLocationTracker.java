/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.capocaccia.cne.jaer.cne2011;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author tobi
 */
public class MeanEventLocationTracker extends EventFilter2D implements FrameAnnotater{

    public static String getDescription(){return "Example for CNE 2011";}
    
    float xmean, ymean;
    private float mixingRate=getFloat("mixingRate", 0.01f);
private float radiusOfTransmission=getFloat("radiusOfTransmission",10);
    
    public MeanEventLocationTracker(AEChip chip) {
        super(chip);
    }

    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        for(BasicEvent o:in){
            xmean=(1-getMixingRate())*xmean+o.x*getMixingRate();
            ymean=(1-getMixingRate())*ymean+o.y*getMixingRate();
        }
        checkOutputPacketEventType(in);
        float maxsq=radiusOfTransmission*radiusOfTransmission;
        OutputEventIterator itr=out.outputIterator();
          for(BasicEvent e:in){
              float dx=e.x-xmean;
              float dy=e.y-ymean;
              float sq=dx*dx+dy*dy;
              if(sq<maxsq){
                  BasicEvent outEvent=itr.nextOutput();
                  outEvent.copyFrom(e);
              }
        }     
        
        return out;
    }

    @Override
    public void resetFilter() {
        xmean=chip.getSizeX()/2;
        ymean=chip.getSizeY()/2;
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl=drawable.getGL();
        gl.glColor4f(1, 0, 0, .3f);
        gl.glRectf(xmean-4, ymean-4, xmean+4, ymean+4);
    }

    /**
     * @return the mixingRate
     */
    public float getMixingRate() {
        return mixingRate;
    }

    /**
     * @param mixingRate the mixingRate to set
     */
    public void setMixingRate(float mixingRate) {
        this.mixingRate = mixingRate;
        putFloat("mixingRate",mixingRate);
    }

    /**
     * @return the radiusOfTransmission
     */
    public float getRadiusOfTransmission() {
        return radiusOfTransmission;
    }

    /**
     * @param radiusOfTransmission the radiusOfTransmission to set
     */
    public void setRadiusOfTransmission(float radiusOfTransmission) {
        this.radiusOfTransmission = radiusOfTransmission;
        putFloat("radiusOfTransmission",radiusOfTransmission);
    }
    
}
