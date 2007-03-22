/*
 * BinocularEvent.java
 *
 * Created on May 28, 2006, 7:45 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 28, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.event;

/**
 * Represents an event from a binocular source
 * @author tobi
 */
public class BinocularEvent extends PolarityEvent {
    public static enum Eye {LEFT,RIGHT};
    public Eye eye=Eye.LEFT;
    
    /** Creates a new instance of BinocularEvent */
    public BinocularEvent() {
    }
    
    @Override public int getType(){
        switch(eye){
            case LEFT: return 0;
            case RIGHT: default: return 1;
        }
    }
    
    @Override public String toString(){
        return super.toString()+" eye="+eye;
    }
    
    public int getNumCellTypes() {
        return 2;
    }
    
       /** copies fields from source event src to this event 
     @param src the event to copy from 
     */
    @Override public void copyFrom(BasicEvent src){
       PolarityEvent e=(PolarityEvent)src;
        super.copyFrom(src);
        if(e instanceof BinocularEvent){
            this.eye=((BinocularEvent)e).eye;
        }
    }

}
