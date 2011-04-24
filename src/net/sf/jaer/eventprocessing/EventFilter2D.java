/*
 * EventFilter2D.java
 *
 * Created on November 9, 2005, 8:49 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.eventprocessing;

import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;

/**
 * A filter that filters or otherwise processes a packet of events.
 * <p>
 * 
 * @author tobi
 */
abstract public class EventFilter2D extends EventFilter {

    /** The built-in reference to the output packet */
    protected EventPacket out = null;


    protected float currenrtUpdateIntervalMs;

    /** Resets the output packet to be a new packet if none has been instanced or clears the packet
    if it exists
     */
    protected void resetOut() {
        if (out == null) {
            out = new EventPacket();
        } else {
            out.clear();
        }
    }

    /** checks out packet to make sure it is the same type as the 
    input packet. This method is used for filters that must pass output
    that has same event type as input.
    @param in the input packet
    @see #out
     */
    protected void checkOutputPacketEventType(EventPacket in) {
        if (out != null && out.getEventClass() == in.getEventClass()) {
            return;
        }
        out = new EventPacket(in.getEventClass());
    }

    /** checks out packet to make sure it is the same type as the given class. This method is used for filters that must pass output
    that has a particular output type.
    @param outClass the output packet.
     */
    protected void checkOutputPacketEventType(Class<? extends BasicEvent> outClass) {
        if (out == null || out.getEventClass() == null || out.getEventClass() != outClass) {
//            Class oldClass=out.getEventClass();
            out = new EventPacket(outClass);
//           log.info("oldClass="+oldClass+" outClass="+outClass+"; allocated new "+out);
        }
    }

    /** Subclasses implement this method to define custom processing.
    @param in the input packet
    @return the output packet
     */
    public abstract EventPacket<?> filterPacket(EventPacket<?> in);

    /** Subclasses should call this super initializer */
    public EventFilter2D(AEChip chip) {
        super(chip);
        this.chip = chip;
    }
    /** overrides EventFilter type in EventFilter */
    protected EventFilter2D enclosedFilter;

    /** A filter can enclose another filter and can access and process this filter. Note that this
    processing is not automatic. Enclosing a filter inside another filter means that it will
    be built into the GUI as such
    @return the enclosed filter
     */
    @Override
    public EventFilter2D getEnclosedFilter() {
        return this.enclosedFilter;
    }

    /** A filter can enclose another filter and can access and process this filter. Note that this
    processing is not automatic. Enclosing a filter inside another filter means that it will
    be built into the GUI as such.
    @param enclosedFilter the enclosed filter
     */
    public void setEnclosedFilter(final EventFilter2D enclosedFilter) {
        if(this.enclosedFilter!=null){
            log.warning("replacing existing enclosedFilter= "+this.enclosedFilter+" with new enclosedFilter= "+enclosedFilter);
        }
        super.setEnclosedFilter(enclosedFilter, this);
        this.enclosedFilter = enclosedFilter;
    }

    /** Resets the filter
    @param yes true to reset
     */
    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            resetOut();
        } else {
            out = null; // garbage collect
        }
    }

 
    private int nextUpdateTimeUs = 0; // next timestamp we should update cluster list
    private boolean updateTimeInitialized = false;// to initialize time for cluster list update
    private int lastUpdateTimeUs=0;

    /** Checks for passage of interval of at least updateIntervalMs since the last update and notifies Observers if time has passed.
     * Observers are called with the update timestamp.
     * @param timestamp
     * @return true if Observers were notified.
     */
    public boolean maybeCallUpdateObservers(EventPacket packet, int timestamp) {
        if (!updateTimeInitialized || currenrtUpdateIntervalMs != chip.getFilterChain().getUpdateIntervalMs()) {
            nextUpdateTimeUs = (int) (timestamp + chip.getFilterChain().getUpdateIntervalMs() * 1000 / AEConstants.TICK_DEFAULT_US);
            updateTimeInitialized = true; // TODO may not be handled correctly after rewind of filter
            currenrtUpdateIntervalMs = chip.getFilterChain().getUpdateIntervalMs();
        }
        // ensure observers are called by next event after upateIntervalUs
        if (timestamp >= nextUpdateTimeUs || timestamp<lastUpdateTimeUs /* handle rewind of time */) {
            nextUpdateTimeUs = (int) (timestamp + chip.getFilterChain().getUpdateIntervalMs() * 1000 / AEConstants.TICK_DEFAULT_US);
//            log.info("notifying update observers after "+(timestamp-lastUpdateTimeUs)+"us");
            setChanged();
            notifyObservers(new UpdateMessage(this, packet, timestamp));
            lastUpdateTimeUs=timestamp;
            return true;
        }
        return false;
    }

    /** Observers are called with the update message as the argument of the update; the observable is the EventFilter that calls the update.
     * @param packet the event packet concerned with this update.
     * @param timestamp the time of the update in timestamp ticks (typically us).
     */
    public void callUpdateObservers(EventPacket packet, int timestamp) {
        updateTimeInitialized = false;
        setChanged();
        notifyObservers(new UpdateMessage(this, packet, timestamp));
    }

    /** Supplied as object for update. 
     @see #maybeCallUpdateObservers
     */
    public class UpdateMessage{
        public EventPacket packet;
        public int timestamp;
        EventFilter2D source;

        /** When a filter calls for an update of listeners it supplies this object.
         * 
         * @param source - the source of the update
         * @param packet - the EventPacket
         * @param timestamp - the timestamp in us (typically) of the update 
         */
        public UpdateMessage(EventFilter2D source, EventPacket packet, int timestamp ) {
            this.packet = packet;
            this.timestamp = timestamp;
            this.source = source;
        }

        public String toString(){
            return "UpdateMessage source="+source+" packet="+packet+" timestamp="+timestamp;
        }
    }
}
