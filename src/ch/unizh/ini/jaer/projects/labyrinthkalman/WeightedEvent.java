package ch.unizh.ini.jaer.projects.labyrinthkalman;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/*
 * WeightedEvent.java
 */

/**
 * An event with a real value weight.
 * @author Jan Funke
 */
public class WeightedEvent extends PolarityEvent {

    public float weight = 1.0f;

    /** Creates a new instance of WeightedEvent */
    public WeightedEvent() {
        super();
    }

    public float getWeight(){
        return weight;
    }

    @Override public String toString(){
        return super.toString()+" weight="+weight;
    }

    @Override public int getNumCellTypes() {
        return 2;
    }

    @Override public int getType(){
        return 0;
    }

    /** copies fields from source event src to this event
     @param src the event to copy from
     */
    @Override public void copyFrom(BasicEvent src){
        super.copyFrom(src);
    }
}
