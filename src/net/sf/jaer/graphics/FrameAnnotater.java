/*
 * FrameAnnotater.java
 *
 * Created on December 20, 2005, 7:17 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.graphics;

import com.jogamp.opengl.GLAutoDrawable;

/**
 An EventFilter2D should implement FrameAnnotator in order to render annotations onto the ChipCanvas during processing.
 <p>
 * A class implements this interface in order to graphically annotate rendered frames. The class can directly set RGB pixel values for the rendered
 *image.
 *<p>
 * A class adds itself to the RetinaCanvasInterface to be called to render itself after
 *the events have been rendered.
 *<p>
 *A third method was added for OpenGL rendering of annotations.
 *
 * @author tobi
 */
public interface FrameAnnotater {


    public void setAnnotationEnabled(boolean yes);
    public boolean isAnnotationEnabled();

    /** Each annotator enters annotate with graphics context current, in coordinates with pixel 0,0 in
     *LL corner and pixel spacing 1 unit after the scaling transform (which is already active).
     * The FrameAnnotater then can use JOGL calls to render to the screen by getting the GL context, e.g. the following
     * code, used in the context of an AEChip object, draws a golden lines from LL to UR of the pixel array.
     * <pre>
        GL2 gl = drawable.getGL().getGL2();
        gl.glBegin(GL2.GL_LINES);
        gl.glColor3f(.5f, .5f, 0);
        gl.glVertex2f(0, 0);
        gl.glVertex2f(getSizeX() - 1, getSizeY() - 1);
        gl.glEnd();
     * </pre>
     *
     @param drawable the OpenGL drawable components, e.g., GLCanvas
     */
    public void annotate(GLAutoDrawable drawable);
}
