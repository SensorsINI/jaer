/*
 * PolarityEvent.java
 *
 * Created on May 27, 2006, 11:48 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 27, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.event;

/**
 * Represents an event with a polarity that is On or Off type
 * @author tobi
 */
public class PolarityEvent extends TypedEvent{
    
    public static enum Polarity {On, Off};
    public Polarity polarity=Polarity.On;
    
    /** Creates a new instance of PolarityEvent */
    public PolarityEvent() {
    }
    
    @Override public String toString(){
        return super.toString()+" polarity="+polarity;
    }
    
    /** copies fields from source event src to this event
     @param src the event to copy from
     */
    @Override public void copyFrom(BasicEvent src){
        PolarityEvent e=(PolarityEvent)src;
        super.copyFrom(e);
        this.polarity=e.polarity;
    }
    
    @Override public int getNumCellTypes() {
        return 2;
    }
    
    @Override public int getType(){
        return polarity==Polarity.Off? 0: 1;
    }
}
