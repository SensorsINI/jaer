/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

/**
 * Marks a class that can supply property tooltip strings. These are used to annotate GUI components. 
 * A class can use {@link PropertyTooltipSupport} to easily add tooltips.
 * 
 * @author Tobi
 * @see PropertyTooltipSupport
 */
public interface HasPropertyTooltips {
  
    /** @return the tooltip for the property.
     @param propertyName the property name
     */
    public String getPropertyTooltip(String propertyName);
}
