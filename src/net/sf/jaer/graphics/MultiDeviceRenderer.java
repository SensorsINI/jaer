package net.sf.jaer.graphics;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.multidevice.DeviceRelation;
import net.sf.jaer.chip.multidevice.DeviceSlot;
import net.sf.jaer.chip.multidevice.Modality;
import net.sf.jaer.chip.multidevice.MultiDeviceChip;
import net.sf.jaer.event.BasicEvent;

/**
 * Draws a {@link MultiDeviceChip} by adding each vision slot's tile origin
 * at pixmap index time. Event {@code x,y} stay native for filters.
 * A line box marks each vision array.
 */
public class MultiDeviceRenderer extends AEChipRenderer implements FrameAnnotater {

    private boolean annotationEnabled = true;

    public MultiDeviceRenderer(AEChip chip) {
        super(chip);
        if (chip != null && chip.getCanvas() != null) {
            for (DisplayMethod method : chip.getCanvas().getDisplayMethods()) {
                method.addAnnotator(this);
            }
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
        if (!annotationEnabled || !(chip instanceof MultiDeviceChip multi)) {
            return;
        }
        DeviceRelation relation = multi.getRelation();
        if (relation == null) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushAttrib(GL2.GL_LINE_BIT | GL2.GL_CURRENT_BIT);
        gl.glColor4f(0.85f, 0.85f, 0.85f, 0.9f);
        gl.glLineWidth(1.5f);
        for (DeviceSlot slot : multi.getSlots()) {
            if (slot.getModality() != Modality.VISION || slot.getChild() == null) {
                continue;
            }
            int w = slot.getChild().getSizeX();
            int h = slot.getChild().getSizeY();
            if (w <= 0 || h <= 0) {
                continue;
            }
            float x0 = relation.tileOriginX(multi, slot);
            float y0 = relation.tileOriginY(multi, slot);
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glVertex2f(x0, y0);
            gl.glVertex2f(x0 + w, y0);
            gl.glVertex2f(x0 + w, y0 + h);
            gl.glVertex2f(x0, y0 + h);
            gl.glEnd();
        }
        gl.glPopAttrib();
    }

    @Override
    protected int displayX(BasicEvent event) {
        return canvasX(chip instanceof MultiDeviceChip ? (MultiDeviceChip) chip : null, event);
    }

    @Override
    protected int displayY(BasicEvent event) {
        return canvasY(chip instanceof MultiDeviceChip ? (MultiDeviceChip) chip : null, event);
    }

    /** Canvas column for {@code event}, including the slot tile origin. */
    public static int canvasX(MultiDeviceChip chip, BasicEvent event) {
        if (event == null) {
            return 0;
        }
        DeviceSlot slot = slotIfVision(chip, event);
        if (slot == null) {
            return event.x;
        }
        DeviceRelation relation = chip.getRelation();
        if (relation == null) {
            return event.x;
        }
        return event.x + relation.tileOriginX(chip, slot);
    }

    /** Canvas row for {@code event}, including the slot tile origin. */
    public static int canvasY(MultiDeviceChip chip, BasicEvent event) {
        if (event == null) {
            return 0;
        }
        DeviceSlot slot = slotIfVision(chip, event);
        if (slot == null) {
            return event.y;
        }
        DeviceRelation relation = chip.getRelation();
        if (relation == null) {
            return event.y;
        }
        return event.y + relation.tileOriginY(chip, slot);
    }

    private static DeviceSlot slotIfVision(MultiDeviceChip chip, BasicEvent event) {
        if (chip == null || event == null) {
            return null;
        }
        DeviceSlot slot = chip.slot(event);
        if (slot == null || slot.getModality() != Modality.VISION) {
            return null;
        }
        return slot;
    }
}
