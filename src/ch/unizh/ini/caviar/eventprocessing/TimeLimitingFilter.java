/*
 * TimeLimitingFilter.java
 *
 * Created on March 21, 2007, 9:49 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.eventprocessing;

/**
 * A marker interface that a filter uses to mark that it can limit its own processing time. Used in GUI construction to avoid putting
 the limit controls on each filter GUI.
 
 * @author tobi
 */
public interface TimeLimitingFilter {
    
}
