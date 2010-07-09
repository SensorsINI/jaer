/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Base class for slot car controllers.
 *
 * @author tobi
 */
class AbstractSlotCarController extends EventFilter2D implements FrameAnnotater, SlotCarControllerInterface{

    public AbstractSlotCarController(AEChip chip) {
        super(chip);
    }


    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void resetFilter() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void initFilter() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void annotate(GLAutoDrawable drawable) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public float computeControl(CarTracker tracker, SlotcarTrack track) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public float getThrottle() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String logControllerState() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String getLogContentsHeader() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
