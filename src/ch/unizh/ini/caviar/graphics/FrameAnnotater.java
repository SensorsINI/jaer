/*
 * FrameAnnotater.java
 *
 * Created on December 20, 2005, 7:17 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.graphics;

import java.awt.Graphics2D;
import javax.media.opengl.GLAutoDrawable;

/**
 * A class implements this interface in order to graphically annotate rendered frames. The class can directly set RGB pixel values for the rendered
 *image. (This does not allow drawing e.g. with Java2D, however.)
 *<p>
 *A second method of annotation was added later to allow direct Graphics2D annotation. A class adds itself to the RetinaCanvasInterface to be called to render itself after 
 *the events have been rendered.
 *<p>
 *A third method was added for OpenGL rendering of annotations. If rendering is done with Java2D in the OpenGL drawable, it is not always done synchronously, and so
 *this method was added that is called just after all other OpenGL frame buffer rendering.
 *
 * @author tobi
 */
public interface FrameAnnotater {
    
    public void setAnnotationEnabled(boolean yes);
    public boolean isAnnotationEnabled();
    
    /** annotate the RGB frame somehow
     *@param frame the RGB pixel information. First dimension is Y, second is X, third is RGB 
     @deprecated use the openGL annotation
     */
    public void annotate(float[][][] frame);

    /** each annotator is called by the relevant class (e.g. EyeTracker) and enters annotate with graphics context current, in coordinates with pixel 0,0 in
     *UL corner and pixel spacing 1 unit before scaling transform (which is already active).
     @param g the Graphics2D context
     @deprecated use the openGL annotation
     */
    public void annotate(Graphics2D g);
    
    /** each annotator is called by the relevant class (e.g. EyeTracker) and enters annotate with graphics context current, in coordinates with pixel 0,0 in
     *LL corner  (note opposite from Java2D, I think) and pixel spacing 1 unit after the scaling transform (which is already active).
     @param drawable the OpenGL drawable components, e.g., GLCanvas
     */
    public void annotate(GLAutoDrawable drawable);
}
