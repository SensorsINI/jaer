/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.event;

/**
 * Additional interface of BasicEvent
 * @author tobi
 */
public interface TypedEventInterface extends BasicEventInterface{

    /** The type field of the event. Generally a small number representing the cell type.
     *
     * @return the type
     */
    int getType();
    
}
