/*
 * FrameAnnotatorAdapter.java
 *
 * Created on March 11, 2006, 2:00 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright March 11, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.graphics;

import com.jogamp.opengl.GLAutoDrawable;

/**
 * Provides empty implementations of all FrameAnnotater methods, in case a class wishes to subclass this class and simply override the desired annotation methods.
 *
 * @author tobi
 */
public class FrameAnnotatorAdapter implements FrameAnnotater {

    /** Creates a new instance of FrameAnnotatorAdapter */
    public FrameAnnotatorAdapter() {
    }

    @Override
	public void annotate(GLAutoDrawable drawable) {
    }

    private boolean annotationEnabled=true;

    @Override
	public void setAnnotationEnabled(boolean yes) {
        annotationEnabled=yes;
    }

    @Override
	public boolean isAnnotationEnabled() {
        return annotationEnabled;
    }


}
