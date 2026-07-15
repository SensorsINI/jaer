/*
 * DisplayMethod3D.java
 *
 * Created on May 5, 2006, 9:53 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.graphics;

/**
 * Marker interface for DisplayMethod that renders in 3D.
 *
 * @author tobi
 */
public interface DisplayMethod3D {

    /**
     * Called when the user unzooms (Ctrl-0). Implementations can refit the 3D
     * view so the full rendered volume is visible.
     */
    default void unzoomToFitVolume() {
    }
}
