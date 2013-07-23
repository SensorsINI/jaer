/*
 * EventFilterRaw.java
 *
 * Created on November 9, 2005, 8:50 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.eventprocessing;

import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * A filter that operates on raw AE packets.
 *
 * @author tobi
 */
abstract public class EventFilterRaw extends EventFilter {

       protected EventFilterRaw enclosedFilter;


    abstract AEPacketRaw filter(AEPacketRaw in);

   /** Creates a new instance of AbstractEventFilter
       @param inFilter a filter to call before the this.filter is called.
    */
    public EventFilterRaw(EventFilterRaw inFilter) {
    	super(null);
        this.enclosedFilter=inFilter;
    }

}
