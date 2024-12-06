/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.minliu;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.EngineeringFormat;

/**
 * A simple utility to do hand measurements of velocity using mouse
 *
 * @author Tobi Delbruck
 */
@Description("A simple utility to do hand measurements of velocity using mouse")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class Speedometer extends EventFilter2DMouseAdaptor implements FrameAnnotater {

    private int currentTimestamp = 0;
    protected TimePoint startPoint;
    protected TimePoint endPoint;
    private boolean firstPoint = true;
    protected float speedPps = 0;
    protected float vxPps = 0;
    protected float vyPps = 0;
    protected Point2D.Float velocityPps = new Point2D.Float();
    private float distance, deltaTimestamp;
    EngineeringFormat engFmt = new EngineeringFormat();
    private static final float[] START_COLOR = new float[]{0, 1, 0, 1}, END_COLOR = new float[]{1, 0, 0, 1};
    private int fontSize = getInt("fontSize", 9);

    public Speedometer(AEChip chip) {
        super(chip);
        startPoint = getTimepoint("startPoint");
        endPoint = getTimepoint("endPoint");
        computeVelocity();
        setPropertyTooltip("fontSize", "size of info string font");
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (in.getSize() > 2) {
            currentTimestamp = (in.getLastTimestamp() + in.getFirstTimestamp()) / 2;
        }
        return in;
    }

    @Override
    public void resetFilter() {
        firstPoint = true;
    }

    @Override
    public void initFilter() {
    }

    @Override
    synchronized public void mouseClicked(MouseEvent e) {
        if (!isSelected()) {
            return;
        }
        if (e == null || e.getPoint() == null || getMousePixel(e) == null) {
            firstPoint = true;
            setStartPoint(null);
            setEndPoint(null);
            log.info("reset to first point");
            return; // handle out of bounds, which should reset
        }
        if (firstPoint) {
            setStartPoint(new TimePoint(getMousePixel(e), currentTimestamp));
            setEndPoint(null);
        } else {
            setEndPoint(new TimePoint(getMousePixel(e), currentTimestamp));
        }
        computeVelocity();
        firstPoint = !firstPoint;
    }

    private void computeVelocity() {
        if (getStartPoint() != null && getEndPoint() != null) {
            distance = (float) (getEndPoint().distance(getStartPoint()));
            int dx = getEndPoint().x - getStartPoint().x, dy = getEndPoint().y - getStartPoint().y;
            deltaTimestamp = getEndPoint().t - getStartPoint().t;
            speedPps = 1e6f * distance / deltaTimestamp;
            vxPps = 1e6f * dx / deltaTimestamp;
            vyPps = 1e6f * dy / deltaTimestamp;
            velocityPps.setLocation(vxPps, vyPps);
            log.info(String.format("%s pps (vx,vy)=(%s,%s)", engFmt.format(speedPps), engFmt.format(vxPps), engFmt.format(vyPps)));
        }
    }

    /**
     * Checks if a preference value exists.
     *
     * @param key - the filter preference key header is prepended.
     * @return true if a non-null value exists
     */
    private class TimePoint extends Point {

        int t;

        public TimePoint(int t, int xx, int yy) {
            super(xx, yy);
            this.t = t;
        }

        public TimePoint(Point p, int t) {
            super(p.x, p.y);
            this.t = t;
        }

    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if (isSelected()) {
            if (firstPoint) {
                setCursorColor(START_COLOR);
            } else {
                setCursorColor(END_COLOR);
            }
        }
//        super.annotate(drawable); // TODO messes up so no OFF events show, don't know why
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        drawCursor(gl, getStartPoint(), START_COLOR);
        drawCursor(gl, getEndPoint(), END_COLOR);
        gl.glPopMatrix(); // must push pop since drawCursor translates?
        if (getStartPoint() != null && getEndPoint() != null) {
            gl.glPushMatrix();
            gl.glColor3f(1, 1, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(getStartPoint().x, getStartPoint().y);
            gl.glVertex2f(getEndPoint().x, getEndPoint().y);
            gl.glEnd();

            String s = String.format("Speedometer: Speed: %s pps (%.0fpix/%ss), dx/dy=%d/%d", engFmt.format(speedPps), distance, engFmt.format(1e-6f * deltaTimestamp), (int) Math.abs(endPoint.x - startPoint.x), (int) Math.abs(endPoint.y - startPoint.y));
            float x = (getStartPoint().x + getEndPoint().x) / 2;
            float y = (float) (Math.min(getEndPoint().y, getStartPoint().y));
            DrawGL.drawStringDropShadow(fontSize, x, y, .5f, Color.white, s);
//            drawString(drawable, s);
            gl.glPopMatrix(); // must push pop since drawCursor translates?
        } else if (getStartPoint() != null) {
            String s = String.format("Speedometer: Left click for end point. Time from IN mark: %ss", engFmt.format(1e-6f * (currentTimestamp - getStartPoint().t)));
            gl.glPushMatrix();
            Point p = getChip().getCanvas().getMousePixel();
            if (p != null) {
                gl.glTranslatef(p.x, p.y, 0);
            }
            drawString(drawable, s);
            gl.glPopMatrix(); // must push pop since drawCursor translates?

        } else if (isControlsVisible()) {
            String s = "Speedometer: Left click for start point";
            gl.glPushMatrix();
            Point p = getChip().getCanvas().getMousePixel();
            if (p != null) {
                gl.glTranslatef(p.x, p.y, 0);
            }
            drawString(drawable, s);
            gl.glPopMatrix(); // must push pop since drawCursor translates?
        } else {
            String s = "Speedometer: Select Controls button to enable mouse selection of start and end points ";
            gl.glPushMatrix();
            drawString(drawable, s);
            gl.glPopMatrix(); // must push pop since drawCursor translates?
        }
    }

    private void drawString(GLAutoDrawable drawable, String s) throws GLException {
        DrawGL.drawStringDropShadow(fontSize, 0.5f, .2f, 0f, Color.white, s);
    }

    private void drawCursor(GL2 gl, Point p, float[] color) {
        if (p == null) {
            return;
        }
//        checkBlend(gl); // no OFF events show after setting blending here, interaction with blending in the display method???
        gl.glPushMatrix();
        gl.glColor4fv(color, 0);
        gl.glLineWidth(3f);
        gl.glTranslatef(p.x, p.y, 0);
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex2f(0, -CURSOR_SIZE_CHIP_PIXELS / 2);
        gl.glVertex2f(0, +CURSOR_SIZE_CHIP_PIXELS / 2);
        gl.glVertex2f(-CURSOR_SIZE_CHIP_PIXELS / 2, 0);
        gl.glVertex2f(+CURSOR_SIZE_CHIP_PIXELS / 2, 0);
        gl.glEnd();
        gl.glTranslatef(.5f, -.5f, 0);
        gl.glColor4f(0, 0, 0, 1);
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex2f(0, -CURSOR_SIZE_CHIP_PIXELS / 2);
        gl.glVertex2f(0, +CURSOR_SIZE_CHIP_PIXELS / 2);
        gl.glVertex2f(-CURSOR_SIZE_CHIP_PIXELS / 2, 0);
        gl.glVertex2f(+CURSOR_SIZE_CHIP_PIXELS / 2, 0);
        gl.glEnd();
        gl.glPopMatrix();
    }

    /**
     * @return the speedPps
     */
    public float getSpeedPps() {
        return speedPps;
    }

    /**
     * @return the vxPps
     */
    public float getVxPps() {
        return vxPps;
    }

    /**
     * @return the vyPps
     */
    public float getVyPps() {
        return vyPps;
    }

    public Point2D.Float getVelocity() {
        return velocityPps;
    }

    public boolean isMeasurementValid() {
        return getStartPoint() != null && getEndPoint() != null;
    }

    /**
     * @return the startPoint
     */
    public TimePoint getStartPoint() {
        return startPoint;
    }

    /**
     * @param startPoint the startPoint to set
     */
    public void setStartPoint(TimePoint startPoint) {
        this.startPoint = startPoint;
        putTimepoint("startPoint", startPoint);
    }

    /**
     * @return the endPoint
     */
    public TimePoint getEndPoint() {
        return endPoint;
    }

    /**
     * @param endPoint the endPoint to set
     */
    public void setEndPoint(TimePoint endPoint) {
        this.endPoint = endPoint;
        putTimepoint("endPoint", endPoint);
    }

    private void putTimepoint(String pointName, TimePoint timepoint) {
        if (timepoint == null) {
            return;
        }
        putInt(pointName + ".x", timepoint.x);
        putInt(pointName + ".y", timepoint.y);
        putInt(pointName + ".t", timepoint.t);
    }

    private TimePoint getTimepoint(String pointName) {
        if (preferenceExists(pointName + ".x")) {
            return null;
        }
        TimePoint t = new TimePoint(getInt(pointName + ".t", 0), getInt(pointName + ".x", 0), getInt(pointName + ".y", 0));
        return t;
    }

    /**
     * @return the fontSize
     */
    public int getFontSize() {
        return fontSize;
    }

    /**
     * @param fontSize the fontSize to set
     */
    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

}
