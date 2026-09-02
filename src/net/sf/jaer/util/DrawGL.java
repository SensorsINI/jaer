/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Static utility methods for drawing stuff. Surround these calls with
 * pushMatrix/popMatrix.
 *
 * @author Bjoern, Tobi Delbruck
 */
public final class DrawGL {

    static final float RAD_TO_DEG = (float) (180 / Math.PI);
    private static final Logger log = Logger.getLogger("net.sf.jaer");

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
     * Draws an arrow vector using current open gl color. After the call, the
     * origin of the current coordinate has been translated to the origin of the
     * vector.
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
     * @param angle the angle relative to E in degrees
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
     * @param angleRad the angle relative to East in radians
     */
    public static void drawCross(final GL2 gl, final float centerX, final float centerY, final float length, final float angleRad) {

        gl.glTranslatef(centerX, centerY, 0);
        if (angleRad != 0) {
            gl.glRotatef(angleRad * RAD_TO_DEG, 0, 0, 1);
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
     * @param angleRad in radians
     * @param N number of segments used to draw ellipse
     */
    public static void drawEllipse(GL2 gl, float centerX, float centerY, float radiusX, float radiusY, float angleRad, int N) {

        gl.glTranslatef(centerX, centerY, 0);
        if (angleRad != 0) {
            gl.glRotatef(angleRad, 0, 0, 1);
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
     * Per-{@link GLContext} TextRenderer cache, inner key atlas pixel size
     * ({@link #atlasFontSize}). JOGL glyph textures belong to the context that
     * first used the renderer; a JVM-wide map made two AEViewers share one atlas
     * (garbled Welcome overlay). Constructing a new TextRenderer every string
     * leaked those textures (RSS growth during live view / recording overlays).
     * Reuse within a context; {@link #disposeCurrentContextRenderers()} when
     * the canvas is destroyed. {@link #drawString}, {@link #lineHeight}, and
     * {@link #measureStringWidth} all go through {@link #textRendererFor}.
     */
    private static final Object TEXT_RENDERER_CACHE_LOCK = new Object();
    private static final IdentityHashMap<GLContext, Map<Integer, TextRenderer>> textRenderersByContext = new IdentityHashMap<>();
    /** Cached chip-pixel line height per requested {@code fontSize} (before the fontSize&lt;10 scale). */
    private static final Map<Integer, Float> cachedLineHeights = new HashMap<>();

    /**
     * Atlas size for {@code TextRenderer}'s Font. Sizes below 10 are drawn with a 4×
     * font at scale 0.25 so glyphs stay sharp; the cache key is this atlas size so
     * requested 8 and atlas 32 share one renderer.
     */
    private static int atlasFontSize(int fontSize) {
        if (fontSize < 1) {
            fontSize = 1;
        }
        return fontSize < 10 ? fontSize * 4 : fontSize;
    }

    /** draw3D scale matching {@link #atlasFontSize}. */
    private static float drawScale(int fontSize) {
        return fontSize < 10 ? 0.25f : 1f;
    }

    /**
     * Returns the reused TextRenderer for {@code requestedFontSize} in the
     * current GL context. Only place {@code new TextRenderer} runs in this class,
     * and only on a cache miss for that context.
     */
    private static TextRenderer textRendererFor(int requestedFontSize) {
        GLContext ctx = GLContext.getCurrent();
        if (ctx == null) {
            throw new GLException("DrawGL TextRenderer requires a current GL context");
        }
        final int atlas = atlasFontSize(requestedFontSize);
        synchronized (TEXT_RENDERER_CACHE_LOCK) {
            Map<Integer, TextRenderer> byAtlas = textRenderersByContext.get(ctx);
            if (byAtlas == null) {
                byAtlas = new HashMap<>();
                textRenderersByContext.put(ctx, byAtlas);
            }
            TextRenderer r = byAtlas.get(atlas);
            if (r == null) {
                r = new TextRenderer(new Font("SansSerif", Font.PLAIN, atlas), true, true);
                // Intel Arc (igxelpgicd64.dll) can ACCESS_VIOLATION in glDrawArrays from
                // TextRenderer's Pipelined_QuadRenderer; ChipCanvas already disables this.
                r.setUseVertexArrays(false);
                byAtlas.put(atlas, r);
            }
            return r;
        }
    }

    /**
     * Disposes cached {@link TextRenderer}s for the current GL context. Call
     * while that context is current (canvas destroy / GLEventListener.dispose).
     * Idempotent.
     */
    public static void disposeCurrentContextRenderers() {
        GLContext ctx = GLContext.getCurrent();
        if (ctx == null) {
            return;
        }
        Map<Integer, TextRenderer> byAtlas;
        synchronized (TEXT_RENDERER_CACHE_LOCK) {
            byAtlas = textRenderersByContext.remove(ctx);
        }
        if (byAtlas == null) {
            return;
        }
        for (TextRenderer r : byAtlas.values()) {
            if (r == null) {
                continue;
            }
            try {
                r.dispose();
            } catch (RuntimeException e) {
                log.log(Level.FINE, "TextRenderer.dispose: {0}", e.toString());
            }
        }
    }

    /**
     * Drops cached renderers for {@code ctx} without {@code dispose()} (context
     * already dead). Avoids pinning the {@link GLContext} in the identity map.
     */
    public static void forgetContext(GLContext ctx) {
        if (ctx == null) {
            return;
        }
        synchronized (TEXT_RENDERER_CACHE_LOCK) {
            textRenderersByContext.remove(ctx);
        }
    }

    /**
     * Draws a string using TextRenderer.draw using native GL coordinates,
     * usually setup to represent pixels on AEChip. Embedded newlines are not
     * rendered as additional lines.
     *
     * @param fontSize typically 5 to 18, font is Font("SansSerif", Font.PLAIN, fontSize)
     * @param x x position (0 at left)
     * @param y y position (0 at bottom)
     * @param alignmentX 0 for left aligned, .5 for centered, 1 for right
     * @param color, e.g. Color.red
     * @param s the string to draw
     * @return the bounds of the text. For left-aligned strings ({@code alignmentX==0})
     *         width is 0 (per-frame {@code getBounds} is skipped to avoid GlyphVector
     *         allocation); height is {@link #lineHeight}. Use
     *         {@link #measureStringWidth} when layout needs the string width.
     */
    public static Rectangle2D drawString(int fontSize, float x, float y, float alignmentX, Color color, String s) { // TODO gl is not actually used
        final float scale = drawScale(fontSize);
        // Line height uses getBounds; must not run inside begin3DRendering.
        final float leftAlignHeight = (alignmentX == 0) ? lineHeight(fontSize) : 0;
        TextRenderer textRenderer = textRendererFor(fontSize);
        textRenderer.begin3DRendering();
        textRenderer.setColor(color);
        Rectangle2D r;
        if (alignmentX == 0) {
            // getBounds() builds a GlyphVector for the whole string; noise-filter overlays
            // change every frame and are left-aligned, so skip the per-string measure.
            // Height is still needed for MultilineAnnotationTextRenderer line advance.
            r = new Rectangle2D.Float(0, 0, 0, leftAlignHeight);
            textRenderer.draw3D(s, x, y, 0, scale);
        } else {
            r = textRenderer.getBounds(s);
            r.setRect(r.getX(), r.getY(), r.getWidth() * scale, r.getHeight() * scale); // adjust bounds for actual drawing scale of text
            textRenderer.draw3D(s, (int) (x - alignmentX * r.getWidth()), (int) (y), 0, scale);
        }
        textRenderer.end3DRendering();
        return r;
    }

    /**
     * Cached chip-pixel line height for {@code fontSize}, using the same
     * fontSize&lt;10 scale as {@link #drawString}. Measures a probe string once
     * per size. Call from the GL thread (same as {@code drawString}).
     */
    public static float lineHeight(int fontSize) {
        if (fontSize < 1) {
            fontSize = 1;
        }
        Float cached = cachedLineHeights.get(fontSize);
        if (cached != null) {
            return cached;
        }
        final int requested = fontSize;
        try {
            Rectangle2D r = textRendererFor(requested).getBounds("Ag");
            float h = (float) (r.getHeight() * drawScale(requested));
            if (h < 1f) {
                h = requested * 1.25f;
            }
            cachedLineHeights.put(requested, h);
            return h;
        } catch (RuntimeException e) {
            return requested * 1.25f;
        }
    }

    /**
     * Chip-pixel width of {@code s} at {@code fontSize}, using the same
     * fontSize&lt;10 scale as {@link #drawString}.
     */
    public static float measureStringWidth(int fontSize, String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        Rectangle2D r = textRendererFor(fontSize).getBounds(s);
        return (float) (r.getWidth() * drawScale(fontSize));
    }

    /**
     * Shrinks {@code startFontSize} so every line fits in {@code maxChipWidth}.
     */
    public static int fontSizeToFitWidth(int startFontSize, String[] lines, float maxChipWidth) {
        int fs = Math.max(6, startFontSize);
        if (lines == null || lines.length == 0 || maxChipWidth <= 0) {
            return fs;
        }
        float longest = 0;
        for (String line : lines) {
            longest = Math.max(longest, measureStringWidth(fs, line));
        }
        if (longest <= maxChipWidth || longest <= 0) {
            return fs;
        }
        return Math.max(6, (int) Math.floor(fs * maxChipWidth / longest));
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
     * @deprecated Only for backward capability, use
     * #drawString(int,float,float,float,Color,String)
     */
    @Deprecated
    public static Rectangle2D drawString(GLAutoDrawable drawable, int fontSize, float x, float y, float alignmentX, Color color, String s) {
        Rectangle2D r = drawString(fontSize, x, y, alignmentX, color, s);
        return r;
    }

    /**
     * Draws a string using TextRenderer.draw using native GL coordinates,
     * usually setup to represent pixels on AEChip. Embedded newlines are not
     * rendered as additional lines.
     * <p>
     * If the TextRenderer does not exist for DrawGL, it is created. This can
     * cause problems if it is out of context, so it might be necessary to
     * create it.
     *
     * @param gl the rendering context surface
     * @param fontSize typically 12 to 36
     * @param x x position (0 at left)
     * @param y y position (0 at bottom)
     * @param alignmentX 0 for left aligned, .5 for centered, 1 for right
     * @param color, e.g. Color.red
     * @param s the string to draw
     * @return the bounds of the text
     * @deprecated Only for backward capability, use
     * #drawString(int,float,float,float,Color,String)
     */
    @Deprecated
    public static Rectangle2D drawString(GL2 gl, int fontSize, float x, float y, float alignmentX, Color color, String s) { // TODO gl is not actually used
        Rectangle2D r = drawString(fontSize, x, y, alignmentX, color, s);
        return r;
    }


    /**
     * Draws a string with drop shadow effect using TextRenderer.draw using
     * native GL coordinates, usually setup to represent pixels on AEChip
     *
     * @param gl the rendering context surface (not actually used)
     * @param fontSize typically 12 to 36
     * @param x x position (0 at left)
     * @param y y position (0 at bottom)
     * @param alignmentX 0 for left aligned, .5 for centered, 1 for right
     * @param color, e.g. Color.red
     * @param s the string to draw
     * @return the bounds of the text
     */
    public static Rectangle2D drawStringDropShadow(int fontSize, float x, float y, float alignmentX, Color color, String s) {
        drawString(fontSize, x + 1, y - 1, alignmentX, Color.black, s);
        Rectangle2D r = drawString(fontSize, x, y, alignmentX, color, s);
        return r;
    }


    /**
     * Standard leading for stacked overlay lines (CSS-style 1.5).
     * {@link #drawString} does not wrap on {@code \n}; use
     * {@link #lineAdvance(int)} between lines.
     */
    public static final float DEFAULT_LINE_SPACING = 1.5f;

    /**
     * Chip-pixel Y step between baselines for multiline text:
     * {@link #lineHeight(int)} times {@link #DEFAULT_LINE_SPACING}.
     */
    public static float lineAdvance(int fontSize) {
        return lineAdvance(fontSize, DEFAULT_LINE_SPACING);
    }

    /**
     * Chip-pixel Y step between baselines: {@link #lineHeight(int)} times
     * {@code spacing} (1.5 is conventional).
     */
    public static float lineAdvance(int fontSize, float spacing) {
        return lineHeight(fontSize) * spacing;
    }

    /**
     * Draws {@code lines} top-down with drop shadow. {@code yTop} is the
     * baseline of the first line. Returns the bounds of the last line drawn.
     *
     * @param spacing multiplier of {@link #lineHeight}; use
     * {@link #DEFAULT_LINE_SPACING} for 1.5.
     */
    public static Rectangle2D drawLinesDropShadow(int fontSize, float x, float yTop, float alignmentX,
            Color color, String[] lines, float spacing) {
        Rectangle2D last = null;
        if (lines == null) {
            return null;
        }
        float y = yTop;
        float adv = lineAdvance(fontSize, spacing);
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            last = drawStringDropShadow(fontSize, x, y, alignmentX, color, line);
            y -= adv;
        }
        return last;
    }

    public static Rectangle2D drawLinesDropShadow(int fontSize, float x, float yTop, float alignmentX,
            Color color, String[] lines) {
        return drawLinesDropShadow(fontSize, x, yTop, alignmentX, color, lines, DEFAULT_LINE_SPACING);
    }

    /**
     * @deprecated use {@link #drawStringDropShadow(int, float, float, float, Color, String)}
     */
    @Deprecated
    public static Rectangle2D drawStringDropShadow(GL2 gl, int fontSize, float x, float y, float alignmentX, Color color, String s) {
        return drawStringDropShadow(fontSize, x, y, alignmentX, color, s);
    }

}
