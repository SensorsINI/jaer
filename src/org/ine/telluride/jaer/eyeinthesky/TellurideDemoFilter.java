package org.ine.telluride.jaer.eyeinthesky;

import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.FrameAnnotater;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GL;
import java.awt.*;

/**
 * User: jauerbac
 * Date: Jul 3, 2009
 * Time: 8:24:13 PM
 */
public class TellurideDemoFilter extends EventFilter2D implements FrameAnnotater {

    private int xCool = getPrefs().getInt("TellurideDemoFilter.xCool", 0);

    public TellurideDemoFilter(AEChip chip) {
        super(chip);
    }

    public Object getFilterState() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void resetFilter() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void initFilter() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled())
            return in;
        for(BasicEvent e : in) {
            if(e.x == xCool) {
                System.out.println("cool " + e.timestamp);
            }
        }
        return in;

    }

    public void annotate(float[][][] frame) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void annotate(Graphics2D g) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void annotate(GLAutoDrawable drawable) {
        if (!isAnnotationEnabled()) {
            return;
        }
        GL gl = drawable.getGL();
        gl.glBegin((GL.GL_LINES));
        gl.glColor3f(.5f, .5f, 0);
        gl.glVertex2f(xCool, 0);
        gl.glVertex2f(xCool, chip.getSizeY());
        gl.glEnd();

    }

    public int getxCool() {
        return xCool;
    }

    public void setxCool(int xCool) {
        this.xCool = xCool;
        getPrefs().putInt("TellurideDemoFilter.xCool", xCool);
    }
}
