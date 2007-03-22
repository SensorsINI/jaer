/*
 * ChipRendererDisplayMethod.java
 *
 * Created on May 4, 2006, 9:07 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 4, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.graphics;

import java.awt.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

/**
 * Renders using OpenGL the RGB histogram values from ChipRenderer.
 
 * @author tobi
 */
public class ChipRendererDisplayMethod extends DisplayMethod implements DisplayMethod2D {
    
    /**
     * Creates a new instance of ChipRendererDisplayMethod
     */
    public ChipRendererDisplayMethod(ChipCanvas chipCanvas) {
        super(chipCanvas);
    }
    
    /** called by ChipCanvas.display(GLAutoDrawable) to draw the RGB fr histogram values. The GL context is assumed to already be
     transformed so that chip pixel x,y values can be used for coordinate values, with 0,0 at LL corner.
     */
    public void display(GLAutoDrawable drawable){
        Chip2DRenderer renderer=chipCanvas.renderer;
        float[][][] fr = renderer.getFr();
        if (fr == null){
            return;
        }

        GL gl=setupGL(drawable);
        float gray = renderer.getGrayValue();
        gl.glClearColor(gray,gray,gray,0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        
        try{
            // now iterate over the frame (fr)
            Point p0=chipCanvas.zoom.getStartPoint();
            Point p1=chipCanvas.zoom.getEndPoint();
            int x0=p0.x, x1=p1.x, y0=p0.y, y1=p1.y;
            for (int x = x0; x < x1; x++){
                for (int y = y0; y < y1; y++){
                    float[] f = fr[y][x];
                    if(f[0]==gray && f[1]==gray && f[2]==gray) continue;
                    // dont flip y direction because retina coordinates are from botton to top (in user space, after lens) are the same as default OpenGL coordinates
                    gl.glColor3f(f[0],f[1],f[2]);
                    gl.glRectf(x-.5f,y-.5f, x+.5f, y+.5f);
                }
            }
        }catch(ArrayIndexOutOfBoundsException e){
            log.warning("while drawing frame buffer");
            e.printStackTrace();
            chipCanvas.unzoom(); // in case it was some other chip had set the zoom
        }
        
        chipCanvas.checkGLError(gl,glu,"after rendering histogram rectangles");
        
        // outline frame
        gl.glColor4f(0,0,1f,0f);
        gl.glLineWidth(1f);
        {
            gl.glBegin(GL.GL_LINE_LOOP);
            final float o = .5f;
            final float w = chip.getSizeX()-1;
            final float h = chip.getSizeY()-1;
            gl.glVertex2f(-o,-o);
            gl.glVertex2f(w+o,-o);
            gl.glVertex2f(w+o,h+o);
            gl.glVertex2f(-o,h+o);
            gl.glEnd();
        }
        chipCanvas.checkGLError(gl,glu,"after rendering frame of chip");
        
// following are apparently not needed, this happens anyhow before buffer swap
//        gl.glFlush();
//        gl.glFinish();
        
    }
    
    
    
}
