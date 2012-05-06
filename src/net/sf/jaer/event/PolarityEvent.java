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

package net.sf.jaer.event;

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

       /**
     * @return the polarity
     */
    public Polarity getPolarity() {
        return polarity;
    }

    /**
     * @param polarity the polarity to set
     */
    public void setPolarity(Polarity polarity) {
        this.polarity = polarity;
    }
    
    /** Returns +1 if polarity is On or -1 if polarity is Off.
     * 
     * @return +1 from On event, -1 from Off event. 
     */
    public int getPolaritySignum(){
        switch(polarity){
            case Off: return -1;
            case On: return +1;
        }
        throw new Error("Events should never have undefined Polarity. We should never get here.");
    }


}
