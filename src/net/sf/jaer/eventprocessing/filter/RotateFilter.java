 /*
 * RotateFilter.java
 *
 * Created on July 7, 2006, 6:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 7, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.eventprocessing.filter;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
/**
 * Transforms the events in various ways,
e.g. rotates the events so that x becomes y and y becomes x.
This filter acts on events in-place in the packet so it should be rather fast
because it doesn't need to copy events, only modify them.
 * @author tobi
 */
@Description("Rotates the addresses")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class RotateFilter extends EventFilter2D implements Observer{
    private boolean swapXY = getBoolean("swapXY",false);
    private boolean rotate90deg = getBoolean("rotate90deg",false);
    private boolean invertY = getBoolean("invertY",false);
    private boolean invertX = getBoolean("invertX",false);
    private float angleDeg = getFloat("angleDeg",0f);
    private float cosAng = (float)Math.cos(angleDeg * Math.PI / 180);
    private float sinAng = (float)Math.sin(angleDeg * Math.PI / 180);
    private int sx,  sy;

    /** Creates a new instance of RotateFilter */
    public RotateFilter (AEChip chip){
        super(chip);
        setPropertyTooltip("swapXY","swaps x and y coordinates");
        setPropertyTooltip("rotate90deg","rotates by 90 CCW");
        setPropertyTooltip("invertY","flips Y");
        setPropertyTooltip("invertX","flips X");
        setPropertyTooltip("angleDeg","CCW rotation angle in degrees");

        sx = chip.getSizeX();
        sy = chip.getSizeY();
        chip.addObserver(this);  // to update chip size parameters
    }

    public EventPacket<?> filterPacket (EventPacket<?> in){
        short tmp;
        final int sx2=sx/2, sy2=sy/2;
        Iterator itr;
        if(in instanceof ApsDvsEventPacket){
            itr=((ApsDvsEventPacket)in).fullIterator();
        }else{
            itr=in.iterator();
        }
        while(itr.hasNext()){
            Object o=itr.next();
            BasicEvent e = (BasicEvent)o;
            if ( swapXY ){
                tmp = e.x;
                e.x = e.y;
                e.y = tmp;
            }
            if ( rotate90deg ){
                tmp = e.x;
                e.x = (short)( sy - e.y - 1 );
                e.y = tmp;
            }
            if ( invertY ){
                e.y = (short)( sy - e.y - 1 );

            }
            if ( invertX ){
                e.x = (short)( sx - e.x - 1 );
            }

            if(angleDeg!=0){
                int x2=e.x-sx2, y2=e.y-sy2;
                int x3=(int)Math.round(+cosAng*(x2)-sinAng*(y2));
                int y3=(int)Math.round(+sinAng*(x2)+cosAng*(y2));
                e.x=(short)(x3+sx2);
                e.y=(short)(y3+sy2);
                if(e.x<0||e.x>=sx||e.y<0||e.y>=sy) {e.x=0; e.y=0;}
//                 if(e.y<0||e.y>=sy) e.y=0; // TODO should remove these events but don't want expense of output packet, so map them to 0,0
            }
        }
        return in;
    }

    public Object getFilterState (){
        return null;
    }

    public void resetFilter (){
    }

    public void initFilter (){
    }

    public boolean isSwapXY (){
        return swapXY;
    }

    public void setSwapXY (boolean swapXY){
        this.swapXY = swapXY;
        putBoolean("swapXY",swapXY);
    }

    public boolean isRotate90deg (){
        return rotate90deg;
    }

    public void setRotate90deg (boolean rotate90deg){
        this.rotate90deg = rotate90deg;
        putBoolean("rotate90deg",rotate90deg);
    }

    public boolean isInvertY (){
        return invertY;
    }

    public void setInvertY (boolean invertY){
        this.invertY = invertY;
        putBoolean("invertY",invertY);
    }

    public boolean isInvertX (){
        return invertX;
    }

    public void setInvertX (boolean invertX){
        this.invertX = invertX;
        putBoolean("invertX",invertX);
    }

    public void update (Observable o,Object arg){
        sx = chip.getSizeX();
        sy = chip.getSizeY();
    }

    /**
     * @return the angleDeg
     */
    public float getAngleDeg (){
        return angleDeg;
    }

    /**
     * @param angleDeg the angleDeg to set
     */
    public void setAngleDeg (float angleDeg){
        // round to nearest 5 deg
//        if(angleDeg==0) this.angleDeg=0; else if(angleDeg>this.angleDeg) this.angleDeg+=1; else if(angleDeg<this.angleDeg)this.angleDeg-=1;
//        this.angleDeg = (int)Math.round(this.angleDeg);
        this.angleDeg=angleDeg;
        putFloat("angleDeg",angleDeg);
        cosAng = (float)Math.cos(angleDeg * Math.PI / 180);
        sinAng = (float)Math.sin(angleDeg * Math.PI / 180);
    }
}
