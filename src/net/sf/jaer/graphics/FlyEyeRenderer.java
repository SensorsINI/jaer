/*
 * FlyEyeRenderer.java
 *
 * Panoramic DVS128 pair. GrayLevel / RedGreen / RedBlue use the same ON/OFF
 * coloring as a single DVS on both eyes. Overlap is not disambiguated.
 */
package net.sf.jaer.graphics;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.chip.flyeye.FlyEye;
import ch.unizh.ini.jaer.chip.flyeye.FlyEyeGeometry;
import net.sf.jaer.chip.AEChip;
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
        if (e.isSpecial()) {
            return;
        }
        // FlyEyeEvent.getNumCellTypes() is 4 (camera×polarity). DavisRenderer
        // would then paint type-color RGB instead of GrayLevel/RedGreen/RedBlue.
        float[] map = dvsEventsMap.array();
        final int index = getIndex(e);
        if ((index < 0) || (index >= map.length)) {
            return;
        }
        updateEventMapsByPolarity(e, map, index);
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
