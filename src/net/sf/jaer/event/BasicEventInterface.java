/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.event;

/**
 * Interface to a basic event, with x,y position and timestamp. 
 * Additional attributes include special type.
 * @author tobi
 */
public interface BasicEventInterface {

    /**
     * @return the address
     */
    int getAddress();

    int getNumCellTypes();

    int getTimestamp();

    int getType();

    short getX();

    short getY();

    /**
     * Is this event to be ignored in iteration (skipped over)? Used to filter out events in-place, without incurring the overhead of copying
     * other events to a mostly-duplicated output packet.
     * @return the filteredOut
     */
    boolean isFilteredOut();

    /**
     * Indicates that this event is a special event, e.g.
     * originating from a separate hardware input pin or from a special, i.e., exceptional,
     * source.
     *
     * @return the special
     */
    boolean isSpecial();

    /**
     * @param address the address to set
     */
    void setAddress(int address);

    /**
     * Flags this event to be ignored in iteration (skipped over). Used to filter out events in-place, without incurring the overhead of copying
     * other events to a mostly-duplicated output packet.
     * @param filteredOut the filteredOut to set
     */
    void setFilteredOut(boolean filteredOut);

    /**
     * Indicates that this event is a special (e.g. synchronizing) event, e.g.
     * originating from a separate hardware input pin or from a special source.
     * This method also sets or clears the SPECIAL_EVENT_BIT_MASK in the address.
     *
     * @param special the special to set
     */
    void setSpecial(boolean yes);

    void setTimestamp(int timestamp);

    void setX(short x);

    void setY(short y);
    
}
