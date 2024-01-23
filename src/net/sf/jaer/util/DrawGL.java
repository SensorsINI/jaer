/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;

/**
 * Static utility methods for drawing stuff. Surround these calls with
 * pushMatrix/popMatrix.
 *
 * @author Bjoern, Tobi Delbruck
 */
public final class DrawGL {

    private static TextRenderer textRenderer = null;
    static final float RAD_TO_DEG = (float) (180 / Math.PI);

    /**
     * Don't let anyone instantiate this class.
     */
    private DrawGL() {
    }

    /**
     * Draws an arrow vector using current open gl color, starting from 0,0,
     * using head length 1 and scaling 1
     *
     * @param gl the opengl context
     * @param headX The x length of arrow
     * @param headY the y length of arrow
     */
    public static void drawVector(GL2 gl, float headX, float headY) {
        drawVector(gl, 0, 0, headX, headY, 1, 1);
    }

    /**
     * Draws an arrow vector using current open gl color, using headlength 1 and
     * scaling 1
     *
     * @param gl the opengl context
     * @param origX the arrow origin location x
     * @param origY the arrow origin location x
     * @param headX The x length of arrow
     * @param headY the y length of arrow
     */
    public static void drawVector(GL2 gl, float origX, float origY, float headX, float headY) {
        drawVector(gl, origX, origY, headX, headY, 1, 1);
    }

    /**
     * Draws an arrow vector using current open gl color. After the call, the origin of the current coordinate has been translated to the origin of the vector.
     *
     * @param gl the opengl context
     * @param origX the arrow origin location x
     * @param origY the arrow origin location x
     * @param headX The x length of arrow
     * @param headY the y length of arrow
     * @param headlength the length of the arrow tip segments as fraction of
     * entire arrow length, after scaling
     * @param scale the scaling used for drawing the arrow
     */
    public static void drawVector(GL2 gl, float origX, float origY, float headX, float headY, float headlength, float scale) {
        float endx = headX * scale, endy = headY * scale;
        float arx = -endx + endy, ary = -endx - endy;   // halfway between pointing back to origin
        float l = (float) Math.sqrt((arx * arx) + (ary * ary)); // length
        arx = (arx / l) * headlength;
        ary = (ary / l) * headlength; // normalize to headlength

        gl.glTranslatef(origX, origY, 0);

        gl.glBegin(GL2.GL_LINES);
        {
            gl.glVertex2f(0, 0);
            gl.glVertex2f(endx, endy);
            // draw arrow (half)
            gl.glVertex2f(endx, endy);
            gl.glVertex2f(endx + arx, endy + ary);
            // other half, 90 degrees
            gl.glVertex2f(endx, endy);
            gl.glVertex2f(endx + ary, endy - arx);
        }
        gl.glEnd();
    }

    private static int boxDisplayListId = 0;
    private static float boxLastW, boxLastH;

    /**
     * Draws a box using current open gl color
     *
     * @param gl the opengl context
     * @param centerX the box origin location x
     * @param centerY the box origin location x
     * @param width The x length of box
     * @param height the y length of box
     * @param angle the angle relative to E
     */
    public static void drawBox(final GL2 gl, final float centerX, final float centerY, final float width, final float height, final float angle) {

        gl.glTranslatef(centerX, centerY, 0);
        if (angle != 0) {
            gl.glRotatef(angle * RAD_TO_DEG, 0, 0, 1);
        }

        if (boxDisplayListId == 0 || width != 2 * boxLastW || height != 2 * boxLastH) {
            if (boxDisplayListId != 0) {
                gl.glDeleteLists(boxDisplayListId, 1);
            }
            boxDisplayListId = gl.glGenLists(1);
            gl.glNewList(boxDisplayListId, GL2.GL_COMPILE);
            boxLastW = width / 2;
            boxLastH = height / 2;
            gl.glBegin(GL.GL_LINE_LOOP);
            {
                gl.glVertex2f(-boxLastW, -boxLastH);
                gl.glVertex2f(+boxLastW, -boxLastH);
                gl.glVertex2f(+boxLastW, +boxLastH);
                gl.glVertex2f(-boxLastW, +boxLastH);
            }
            gl.glEnd();
            gl.glEndList();
        }
        gl.glCallList(boxDisplayListId);

    }

    private static int crossDisplayListId = 0;
    private static float crossLastL;

    /**
     * Draws a cross using current open gl color
     *
     * @param gl the opengl context
     * @param centerX the cross origin location x
     * @param centerY the cross origin location x
     * @param length The x length of cross
     * @param angle the angle relative to E in radians
     */
    public static void drawCross(final GL2 gl, final float centerX, final float centerY, final float length, final float angle) {

        gl.glTranslatef(centerX, centerY, 0);
        if (angle != 0) {
            gl.glRotatef(angle * RAD_TO_DEG, 0, 0, 1);
        }

        if (crossDisplayListId == 0 || length != 2 * crossLastL) {
            if (crossDisplayListId != 0) {
                gl.glDeleteLists(crossDisplayListId, 1);
            }
            crossDisplayListId = gl.glGenLists(1);
            gl.glNewList(crossDisplayListId, GL2.GL_COMPILE);
            crossLastL = length / 2;
            gl.glBegin(GL.GL_LINES);
            {
                gl.glVertex2f(-crossLastL, -crossLastL);
                gl.glVertex2f(+crossLastL, +crossLastL);
                gl.glVertex2f(+crossLastL, -crossLastL);
                gl.glVertex2f(-crossLastL, +crossLastL);
            }
            gl.glEnd();
            gl.glEndList();
        }
        gl.glCallList(crossDisplayListId);
    }

    /**
     * Draws ellipse. Set the line width before drawing, and push and pop matrix
     *
     * @param gl
     * @param centerX
     * @param centerY
     * @param radiusX
     * @param radiusY
     * @param angle
     * @param N number of segments used to draw ellipse
     */
    public static void drawEllipse(GL2 gl, float centerX, float centerY, float radiusX, float radiusY, float angle, int N) {
        final float r2d = (float) (180 / Math.PI);

        gl.glTranslatef(centerX, centerY, 0);
        if (angle != 0) {
            gl.glRotatef(angle * r2d, 0, 0, 1);
        }

        gl.glBegin(GL.GL_LINE_LOOP);
        {
            for (int i = 0; i < N; i++) {
                double a = ((float) i / N) * 2 * Math.PI;
                double cosA = Math.cos(a);
                double sinA = Math.sin(a);

                gl.glVertex2d(radiusX * cosA, radiusY * sinA);
            }
        }
        gl.glEnd();
    }

    /**
     * Draws a circle. Set the line width before drawing, and push and pop
     * matrix
     *
     * @param gl
     * @param centerX
     * @param centerY
     * @param radius
     * @param N number of segments used to draw ellipse
     */
    public static void drawCircle(GL2 gl, float centerX, float centerY, float radius, int N) {
        drawEllipse(gl, centerX, centerY, radius, radius, 0, N);
    }

    /**
     * Draws a line. Set the line width before drawing, and push and pop matrix.
     *
     * @param gl
     * @param startX
     * @param startY
     * @param lengthX
     * @param lengthY
     * @param scale scales the line length by this factor
     */
    public static void drawLine(GL2 gl, float startX, float startY, float lengthX, float lengthY, float scale) {
        gl.glTranslatef(startX, startY, 0);

        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(0, 0);
        gl.glVertex2f(lengthX * scale, lengthY * scale);
        gl.glEnd();
    }

    /**
     * Draws a string using TextRenderer.draw somewhere on the entire drawing
     * surface
     *
     * @param drawable surface
     * @param fontSize typically 12 to 36
     * @param x fractional pixel array x position, e.g. .5f for center, 0 for
     * left edge
     * @param y fractional y pixel array position, e.g. 0 for bottom of view, 1
     * for top
     * @param alignmentX 0 for left aligned, .5 for centered, 1 for right
     * aligned
     * @param color, e.g. Color.red
     * @param s the string to draw
     * @return the bounds of the text
     */
    public static Rectangle2D drawString(GLAutoDrawable drawable, int fontSize, float x, float y, float alignmentX, Color color, String s) {
        if (getTextRenderer() == null || getTextRenderer().getFont().getSize() != fontSize) {
            setTextRenderer(new TextRenderer(new Font("SansSerif", Font.PLAIN, fontSize), true, false));
        }
        getTextRenderer().beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        getTextRenderer().setColor(color);
        Rectangle2D r = getTextRenderer().getBounds(s);
        getTextRenderer().draw(s, (int) ((x * drawable.getSurfaceWidth()) - alignmentX * r.getWidth()), (int) (y * drawable.getSurfaceHeight()));
        getTextRenderer().endRendering();
        return r;
    }

    /**
     * Draws a string using TextRenderer.draw using native GL coordinates,
     * usually setup to represent pixels on AEChip.
     * Embedded newlines are not rendered as additional lines.
     * <p>
     * If the TextRenderer does not exist for DrawGL, it is created. This can cause problems if it is out of context, so 
     * it might be necessary to create it.
     *
     * @param gl the rendering context surface
     * @param fontSize typically 12 to 36
     * @param x x position (0 at left)
     * @param y y position (0 at bottom)
     * @param alignmentX 0 for left aligned, .5 for centered, 1 for right
     * @param color, e.g. Color.red
     * @param s the string to draw
     * @return the bounds of the text
     */
    public static Rectangle2D drawString(GL2 gl, int fontSize, float x, float y, float alignmentX, Color color, String s) { // TODO gl is not actually used
        if (getTextRenderer() == null || getTextRenderer().getFont().getSize() != fontSize) {
            setTextRenderer(new TextRenderer(new Font("SansSerif", Font.PLAIN, fontSize), true, false));
        }
        getTextRenderer().begin3DRendering();
        getTextRenderer().setColor(color);
        Rectangle2D r = getTextRenderer().getBounds(s);
        getTextRenderer().draw(s, (int) (x - alignmentX * r.getWidth()), (int) (y));
        getTextRenderer().end3DRendering();
        return r;
    }

   /**
     * Draws a string with drop shadow effect using TextRenderer.draw using native GL coordinates,
     * usually setup to represent pixels on AEChip
     *
     * @param gl the rendering context surface
     * @param fontSize typically 12 to 36
     * @param x x position (0 at left)
     * @param y y position (0 at bottom)
     * @param alignmentX 0 for left aligned, .5 for centered, 1 for right
     * @param color, e.g. Color.red
     * @param s the string to draw
     * @return the bounds of the text
     */
       public static Rectangle2D drawStringDropShadow(GL2 gl, int fontSize, float x, float y, float alignmentX, Color color, String s) {
        drawString(gl, fontSize, x+1, y-1, alignmentX, Color.black, s);
        Rectangle2D r = drawString(gl, fontSize, x, y, alignmentX, color, s);
        return r;
    }

    /**
     * Returns the text renderer if it has been initialized. These should only be initialized in the OpenGL rendering context.
     * @return the textRenderer
     */
    public static TextRenderer getTextRenderer() {
        return textRenderer;
    }

    /**
     * Allows setting a text renderer for DrawGL, which might be useful for determining a scale, etc, before rendering.
     * @param aTextRenderer the textRenderer to set
     */
    public static void setTextRenderer(TextRenderer aTextRenderer) {
        textRenderer = aTextRenderer;
    }
}
