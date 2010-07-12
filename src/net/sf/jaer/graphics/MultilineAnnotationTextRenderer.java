/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.graphics;

import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import javax.media.opengl.GLAutoDrawable;

/**
 * Holds static methods for text rendering in the annotation of an EventFilter chip output display.
 *
 * @author tobi
 */
public class MultilineAnnotationTextRenderer {

    private static TextRenderer renderer;
    private static float yshift=0;
    private static float xposition=1;

    /** Call to reset to origin */
    public static void resetToOrigin(){
        yshift=0;
    }

    /** Renders the string with newlines ('\n') starting at last position.
     *
     * @param s the string to render.
     */
     public static void renderMultilineString(String s) {
        if ( renderer == null ){
            renderer = new TextRenderer(new Font("SansSerif",Font.PLAIN,24),true,true);
        }
        final float scale = .25f;
        String[] lines = s.split("\n");
        if(lines==null) return;

        renderer.begin3DRendering();
        for (String l : lines) {
            if(l==null) continue;
            Rectangle2D r=renderer.getBounds(l);
            yshift+=r.getHeight()*scale;
            renderer.draw3D(l, xposition, yshift, 0, scale);
        }
        renderer.end3DRendering();
    }

}
