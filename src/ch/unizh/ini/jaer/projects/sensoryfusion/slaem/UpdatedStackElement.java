/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import com.jogamp.opengl.GLAutoDrawable;

/**
 *
 * @author Christian
 */
public interface UpdatedStackElement {
    
    boolean contains(Object obj, float oriTol, float distTol);
    
    public void draw(GLAutoDrawable drawable);
    
}
