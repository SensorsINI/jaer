
package net.sf.jaer.event;

import net.sf.jaer.event.PolarityEvent.Polarity;

/** Additional interface of PolarityEvent
 * @author tobi */
public interface PolarityEventInterface extends BasicEventInterface {

    /**
     * @return the polarity */
    public Polarity getPolarity();

    /** Returns +1 if polarity is On or -1 if polarity is Off.
     *
     * @return +1 from On event, -1 from Off event. */
    int getPolaritySignum();

    /**
     * @param polarity the polarity to set */
    void setPolarity(Polarity polarity);
    
}