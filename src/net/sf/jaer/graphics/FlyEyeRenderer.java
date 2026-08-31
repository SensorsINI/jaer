/*
 * FlyEyeRenderer.java
 *
 * Panoramic DVS128 pair: left = green, right = red, overlap yellow.
 */
package net.sf.jaer.graphics;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.chip.flyeye.FlyEye;
import ch.unizh.ini.jaer.chip.flyeye.FlyEyeGeometry;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.FlyEyeEvent;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;

/**
 * Renders FlyEye panoramic events. Do not use {@link BinocularDVSRenderer}.
 */
public class FlyEyeRenderer extends DavisRenderer implements FrameAnnotater {

    private boolean annotationEnabled = true;

    public FlyEyeRenderer(AEChip chip) {
        super(chip);
    }

    @Override
    public synchronized void render(final PacketBundle bundle) {
        super.render(bundle);
    }

    @Override
    protected void updateEventMaps(final PolarityEvent e) {
        if (!(e instanceof FlyEyeEvent fe)) {
            super.updateEventMaps(e);
            return;
        }
        final int index = getIndex(e);
        float[] map = dvsEventsMap.array();
        if ((index < 0) || (index >= map.length)) {
            return;
        }
        map[index + 3] = 1f;
        final boolean on = (e.polarity == PolarityEvent.Polarity.On) || ignorePolarityEnabled;
        final float step = on ? colorContrastAdditiveStep : -colorContrastAdditiveStep;
        if (fe.camera == FlyEyeEvent.Camera.LEFT) {
            map[index + 1] += step; // green
        } else {
            map[index] += step; // red
        }
    }

    @Override
    public void setAnnotationEnabled(boolean yes) {
        annotationEnabled = yes;
    }

    @Override
    public boolean isAnnotationEnabled() {
        return annotationEnabled;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!annotationEnabled || !(chip instanceof FlyEye fly)) {
            return;
        }
        int ov = fly.getOverlapPixels();
        float x0 = FlyEyeGeometry.overlapLeft(ov);
        float x1 = FlyEyeGeometry.overlapRight(ov);
        GL2 gl = drawable.getGL().getGL2();
        gl.glColor4f(1f, 1f, 1f, 0.35f);
        gl.glLineWidth(1f);
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(x0, 0);
        gl.glVertex2f(x0, chip.getSizeY());
        gl.glVertex2f(x1, 0);
        gl.glVertex2f(x1, chip.getSizeY());
        gl.glEnd();
    }
}
