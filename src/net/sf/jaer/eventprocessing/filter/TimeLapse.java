/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GLAutoDrawable;
import eu.seebetter.ini.chips.DavisChip;
import java.util.TimerTask;
import java.util.Timer;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Exposes DAVIS frames at desired (and low) frame rate for a time lapse movie
 * mode.
 *
 * @author tobid
 */
@Description("Exposes DAVIS frames at desired (and low) frame rate for a time lapse movie mode")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class TimeLapse extends EventFilter2D implements FrameAnnotater {

    private float frameRateHz = getFloat("frameRateHz", 1);
    private Timer frameTimer = null;
    FrameCaptureTask frameCaptureTimerTask = null;

    public TimeLapse(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {

        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
    }

    /**
     * @return the frameRateHz
     */
    public float getFrameRateHz() {
        return frameRateHz;
    }

    /**
     * @param frameRateHz the frameRateHz to set
     */
    public void setFrameRateHz(float frameRateHz) {
        this.frameRateHz = frameRateHz;
        putFloat("frameRateHz", frameRateHz);
        cancelAndCreateTimer(isFilterEnabled());
    }

    private void cancelAndCreateTimer(boolean yes) {
        if (frameTimer != null) {
            frameTimer.cancel();
            frameTimer = null;
        }
        if (yes) {

            frameTimer = new Timer("TimeLapseFrameTimer", true);
            frameCaptureTimerTask = new FrameCaptureTask((DavisChip) chip);
            frameTimer.scheduleAtFixedRate(frameCaptureTimerTask, 0, (int) (1e3 / frameRateHz));
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            if (!(chip instanceof DavisChip)) {
                log.warning("Only works with a DavisChip");
                setFilterEnabled(false);
                return;
            }
        }
        cancelAndCreateTimer(yes);
    }

    private class FrameCaptureTask extends TimerTask {

        DavisChip davisChip;

        public FrameCaptureTask(DavisChip davisChip) {
            this.davisChip = davisChip;
        }

        @Override
        public void run() {
            davisChip.takeSnapshot();
        }
    }
}
