/*
 * StereoTranslateRotate.java
 *
 * Created on 26. April 2006, 14:31
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.stereopsis;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.*;


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
    }
    
    
    public int getDx() {
        return this.dx;
    }
    
    public void setDx(final int dx) {
        getPrefs().putInt("StereoTranslateRotate.dx", dx);
        support.firePropertyChange("dx", this.dx, dx);
        this.dx = dx;
    }
    
    public int getDy() {
        return dy;
    }
    
    public void setDy(int dy) {
        getPrefs().putInt("StereoTranslateRotate.dy", dy);
        support.firePropertyChange("dy", this.dy, dy);
        this.dy = dy;
    }
    
    public float getPhi() {
        return this.phi;
    }
    
    /** Set the rotation angle phi.
     @param phi the angle in radians
     */
    public void setPhi(final float phi) {
        getPrefs().putDouble("StereoTranslateRotate.phi", phi);
        support.firePropertyChange("phi", this.phi, phi);
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
        if (in == null) return null;
        if (!filterEnabled) return in;
        if(!(in.getEventPrototype() instanceof BinocularEvent)){
            log.warning(this+" needs BinocularEvents as input, disabling filter");
            setFilterEnabled(false);
            return in;
        }
        if (enclosedFilter != null) in = enclosedFilter.filterPacket(in);
        if(out==null || out.getEventClass()!=BinocularEvent.class) out=new EventPacket(BinocularEvent.class);
        
        int size_x = chip.getSizeX();
        int size_y = chip.getSizeY();
        
        double sin_phi = Math.sin(getPhi());
        double cos_phi = Math.cos(getPhi());
        
        /* x' = x*cos_phi - y*sin_phi
           y' = y*cos_phi + x*sin_phi */
        OutputEventIterator o=out.outputIterator(); // don't need to clear it, this resets to start of output packet
        for(Object i:in){
            BinocularEvent e=(BinocularEvent)i;
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
            short x,y;
            short x_c = (short)(e.getX() - size_x/2);
            short y_c = (short)(e.getY() - size_y/2);
            
            if (e.eye==BinocularEvent.Eye.LEFT) {
                x = (short)(cos_phi*x_c + sin_phi*y_c + size_x/2 + getDx());
                y = (short)(cos_phi*y_c - sin_phi*x_c + size_y/2 + getDy());
            } else {
                x = (short)(cos_phi*x_c - sin_phi*y_c + size_x/2 - getDx());
                y = (short)(cos_phi*y_c + sin_phi*x_c + size_y/2 - getDy());
            }
            
            if (x < 0 || x > size_x-1 || y < 0 || y > size_y-1) continue;
            BinocularEvent oe=(BinocularEvent)o.nextOutput();
            oe.copyFrom(e);
            oe.setX(x);
            oe.setY(y);
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
    
    
}
