/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.graphics;


import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.logging.Logger;

import com.jogamp.opengl.GLException;

import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Color;
import net.sf.jaer.util.TextRendererScale;

/**
 * Useful static methods for text rendering in the annotation of an EventFilter chip output display.
 * Assumes pixel-based coordinates of GL context.
 *
 * @author tobi
 * @see TextRendererScale for utility static method to compute a reasonable scale
 */
public class MultilineAnnotationTextRenderer {
	private static final float additionalSpace = 2f;

	private static TextRenderer renderer;
	private static float yshift=0;
	private static float xposition=1;
	private static float scale = .15f;
	private static final Logger log=Logger.getLogger("MultilineAnnotationTextRenderer");
        private static Color color=Color.WHITE;

	/** Call to reset to origin.
	 * 
	 * @param yOrigin in pixels from bottom of GLCanvas.
	 */
	public static void resetToYPositionPixels(float yOrigin){
		yshift=yOrigin;
	}

	/** Renders the string starting at last position.
	 * Embedded newlines start a new line of text which is intended slightly.
	 * Therefore multiple calls to renderMultilineString will create groups of text lines, with each
	 * group starting with a leading line followed by indented lines.
	 * E.g.
	 * 
	 * <pre>
	 *      MultilineAnnotationTextRenderer.resetToYPositionPixels(10);
            String s = String.format("Controller dynamics:\ntau=%.1fms\nQ=%.2f",tau*1000,Q);
            MultilineAnnotationTextRenderer.renderMultilineString(s);
	 * </pre>
	 * might render
	 * <pre>
	 * Filter parameters
	 *   tau=34ms
	 *   Q=16
	 * </pre>
	 * 
	 *
	 * @param s the string to render.
	 */
	public static void renderMultilineString(String s) {
		if ( renderer == null ){
			renderer = new TextRenderer(new Font("SansSerif",Font.PLAIN,24),true,true);
			renderer.setColor(color);
		}
		String[] lines = s.split("\n");
		if (lines == null) {
			return;
		}

		try {
			renderer.begin3DRendering();
			boolean first = true;
			for (String l : lines) {
				if (l == null) {
					continue;
				}
				Rectangle2D r = renderer.getBounds(l);
				yshift -= r.getHeight() * scale;
				if (!first) {
					l = "  " + l;
				}
				first = false;
				renderer.draw3D(l, xposition, yshift, 0, scale);
			}
			renderer.end3DRendering();
		} catch (GLException e) {
			log.warning("caught " + e + " when trying to render text into the current OpenGL context");
		}
		yshift -= additionalSpace;  // add additional space between multiline strings
	}

    /**
     * Returns overall text scaling (0.15 by default)
     * @return the scale
     */
    public static float getScale() {
        return scale;
    }

    /** Sets overall text scaling (0.15 by default)
     * @param aScale the scale to set
     */
    public static void setScale(float aScale) {
        scale = aScale;
    }
    
    public static void setDefaultScale(){
        scale=0.15f;
    }

    /**
     * Returns static (global) rendering color
     * @return the color
     */
    public static Color getColor() {
        return color;
    }

    /**
     * Sets the rendering color, which applies to the entire set of strings to be rendered on multiple lines.
     * @param aColor the color to set
     */
    public static void setColor(Color aColor) {
        color = aColor;
    }

    /**
     * Returns the TextRenderer used by MultilineAnnotationTextRenderer. This renderer will be null before the first use.
     * @return the renderer, or null if it has not been used yet
     */
    public static TextRenderer getRenderer() {
        return renderer;
    }
    
        

}
