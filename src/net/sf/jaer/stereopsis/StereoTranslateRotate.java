/*
 * StereoTranslateRotate.java
 *
 * Created on 26. April 2006, 14:31
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.stereopsis;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;


/**
 * Shifts both images relatively to each other. A static transformation is applied to each eye's events as follows. First a rotation
 is applied
 <literal>
  x' = x*cos_phi - y*sin_phi
           y' = y*cos_phi + x*sin_phi
 </literal>
 Then the translations dx and dy are applied. The transformations are applied to the left eye events and then the dual is applied to the right eye events.
 * @author Peter Hess, Tobi Delbruck
 */
public class StereoTranslateRotate extends EventFilter2D {
    
    public boolean isGeneratingFilter(){ return false;}
    
    /** shifts left and right image by dx. positive values shift the images apart
     * rotates images around their center by phi. left image is rotated clockwise and right image is rotated counterclockwise
     */
    
    private int dx = getPrefs().getInt("StereoTranslateRotate.dx", 0);
    private int dy = getPrefs().getInt("StereoTranslateRotate.dy",0);
    private float phi = getPrefs().getFloat("StereoTranslateRotate.phi", 0.0f);
    private boolean swapEyes=getPrefs().getBoolean("StereoTranslateRotate.swapEyes",false);
    
    public StereoTranslateRotate(AEChip chip){
        super(chip);
        setPropertyTooltip("dx","x translation in pixels; left goes +dx, right goes -dx");
        setPropertyTooltip("dy","y translation in pixels; left goes +dy, right goes -dy");
        setPropertyTooltip("phi","rotation in radians around center; x' = x*cos_phi - y*sin_phi, y' = y*cos_phi + x*sin_phi");
    }
    
    
    public int getDx() {
        return this.dx;
    }
    
    public void setDx(final int dx) {
        getPrefs().putInt("StereoTranslateRotate.dx", dx);
        getSupport().firePropertyChange("dx", this.dx, dx);
        this.dx = dx;
    }
    
    public int getDy() {
        return dy;
    }
    
    public void setDy(int dy) {
        getPrefs().putInt("StereoTranslateRotate.dy", dy);
        getSupport().firePropertyChange("dy", this.dy, dy);
        this.dy = dy;
    }
    
    public float getPhi() {
        return this.phi;
    }
    
    /** Set the rotation angle phi.
     @param phi the angle in radians
     */
    public void setPhi(final float phi) {
        getPrefs().putFloat("StereoTranslateRotate.phi", phi);
        getSupport().firePropertyChange("phi", this.phi, phi);
        this.phi = phi;
    }
    
    synchronized public void resetFilter() {
    }
    
    public void initFilter() {
        resetFilter();
    }
    
    public Object getFilterState() {
        return null;
    }
    
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!(in.getEventPrototype() instanceof BinocularEvent)){
            log.warning(this+" needs BinocularEvents as input, disabling filter");
            setFilterEnabled(false);
            return in;
        }
        checkOutputPacketEventType(BinocularEvent.class);
        if (enclosedFilter != null) in = enclosedFilter.filterPacket(in);

        int size_x = chip.getSizeX();
        int size_y = chip.getSizeY();
        int halfSize_x = size_x/2;
        int halfSize_y = size_y/2;
        
        float sin_phi = (float) Math.sin(getPhi());
        float cos_phi = (float) Math.cos(getPhi());
        
        /* x' = x*cos_phi - y*sin_phi
           y' = y*cos_phi + x*sin_phi */
        short x, y;
        int x_c, y_c;
        OutputEventIterator o=out.outputIterator(); // don't need to clear it, this resets to start of output packet
        for(int i=0; i<in.getSize(); i++){
            BinocularEvent e=(BinocularEvent)in.getEvent(i);
            if(swapEyes){
                switch(e.eye){
                    case LEFT:
                        e.eye=BinocularEvent.Eye.RIGHT;
                        e.type=(byte)1;
                        break;
                    default:
                        e.eye=BinocularEvent.Eye.LEFT;
                        e.type=(byte)0;
                }
            }
            
            x_c = e.x - halfSize_x;
            y_c = e.y - halfSize_y;
            
            if (e.eye==BinocularEvent.Eye.LEFT) {
                x = (short)(cos_phi*x_c + sin_phi*y_c + halfSize_x + dx);
                y = (short)(cos_phi*y_c - sin_phi*x_c + halfSize_y + dy);
            } else {
                x = (short)(cos_phi*x_c - sin_phi*y_c + halfSize_x - dx);
                y = (short)(cos_phi*y_c + sin_phi*x_c + halfSize_y - dy);
            }
            
            if (x < 0 || x > size_x-1 || y < 0 || y > size_y-1) continue;
            BinocularEvent oe=(BinocularEvent)o.nextOutput();
//            oe.copyFrom(e);
//            oe.setX((short) x);
//            oe.setY((short) y);
            // to reduce computational cost... (oe.copyFrom() takes too much cost!)
            oe.address = e.address;
            oe.eye = e.eye;
            oe.polarity = e.polarity;
            oe.timestamp = e.timestamp;
            oe.type = e.type;
            oe.x = x;
            oe.y = y;
        }
        return out;
    }

    public boolean isSwapEyes() {
        return swapEyes;
    }

    public void setSwapEyes(boolean swapEyes) {
        this.swapEyes = swapEyes;
        getPrefs().putBoolean("StereoTranslateRotate.swapEyes",swapEyes);
    }

    @Override
    public String toString (){
        return String.format("StereoTranslateRotate dx=%dpx, dy=%dpx, phi=%.2fdeg",dx,dy,180*phi/3.141592f);
    }



    
}
