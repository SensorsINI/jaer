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
import net.sf.jaer.util.DrawGL;

/**
 * Useful static methods for text rendering in the annotation of an EventFilter
 * chip output display. Assumes pixel-based coordinates of GL context.
 *
 * @author tobi
 * @see TextRendererScalefor utility static method to compute a reasonable
 lineShiftMultiplier
 */
public class MultilineAnnotationTextRenderer {

    private static final float additionalSpace = 2f;

    private static TextRenderer renderer;
    private static float yshift = 0;
    private static float xposition = 1;
    private static float lineShiftMultiplier = 1.15f;
    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static Color color = Color.WHITE;
    private static int fontSize = 9;
    private static boolean rebuildRenderer = true;

    /**
     * Call to reset to origin.
     *
     * @param yOrigin in pixels from bottom of GLCanvas.
     */
    public static void resetToYPositionPixels(float yOrigin) {
        yshift = yOrigin;
    }

    /**
     * Call to set x position (1 by default)
     *
     * @param xPosition in pixels from bottom of GLCanvas.
     */
    public static void setXPosition(float xPosition) {
        xposition = xPosition;
    }

    /**
     * Renders the string starting at last position. Embedded newlines start a
     * new line of text which is intended slightly. Therefore multiple calls to
     * renderMultilineString will create groups of text lines, with each group
     * starting with a leading line followed by indented lines. E.g.
     *
     * <pre>
     *      MultilineAnnotationTextRenderer.resetToYPositionPixels(10);
     * String s = String.format("Controller dynamics:\ntau=%.1fms\nQ=%.2f",tau*1000,Q);
     * MultilineAnnotationTextRenderer.renderMultilineString(s);
     * </pre> might render
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
        String[] lines = s.split("\n");
        if (lines == null) {
            return;
        }

        try {
            Rectangle2D r = null; // get bounds from first string rendered
            for (String l : lines) {
                if (l == null) {
                    continue;
                }
                r = DrawGL.drawString(fontSize, xposition, yshift, 0, color, l);
                yshift -= r.getHeight() * lineShiftMultiplier;
                l = "  " + l;
            }
        } catch (GLException e) {
            log.warning("caught " + e + " when trying to render text into the current OpenGL context");
        }
//        yshift -= additionalSpace;  // add additional space between multiline strings
    }

    /**
     * Returns overall text scaling (0.15 by default)
     *
     * @return the lineShiftMultiplier
     */
    public static float getLineShiftMultiplier() {
        return lineShiftMultiplier;
    }

    /**
     * Sets overall text scaling (0.15 by default)
     *
     * @param aScale the lineShiftMultiplier to set
     */
    public static void setLineShiftMultiplier(float aScale) {
        lineShiftMultiplier = aScale;
    }

    /**
     * Returns overall text font size (24 by default)
     *
     * @return the font size
     */
    public static int getFontSize() {
        return fontSize;
    }

    /**
     * Sets overall text Font size (24 by default)
     *
     * @param FontSize the font size to set
     */
    public static void setFontSize(int aFontSize) {
        fontSize = aFontSize;
        rebuildRenderer = true;
    }

    public static void setDefaultScale() {
        lineShiftMultiplier = 0.15f;
    }

    /**
     * Returns static (global) rendering color
     *
     * @return the color
     */
    public static Color getColor() {
        return color;
    }

    /**
     * Sets the rendering color, which applies to the entire set of strings to
     * be rendered on multiple lines.
     *
     * @param aColor the color to set
     */
    public static void setColor(Color aColor) {
        color = aColor;
    }


}
