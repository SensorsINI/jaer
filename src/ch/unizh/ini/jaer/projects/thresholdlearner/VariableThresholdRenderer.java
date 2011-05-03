/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.thresholdlearner;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OrientationEvent;
import net.sf.jaer.graphics.RetinaRenderer;
import net.sf.jaer.chip.AEChip;

/**
 * Used to render the events in a packet using the Threshold map for the AERetina.
 * 
 * @author tobi
 */
public class VariableThresholdRenderer extends RetinaRenderer {

    ThresholdMap thresholdMap;

    public VariableThresholdRenderer(AEChip chip) {
        super(chip);
        thresholdMap = new ThresholdMap(chip);
    }

    /**
     * does the rendering using selected method.
     *
     * @param packet a packet of events (already extracted from raw events)
     * @see #setColorMode
     */
    public synchronized void render(EventPacket packet) {
        if (packet == null) {
            return;
        }
        this.packet = packet;
        checkPixmapAllocation();
        float[] f = getPixmapArray();
        float a;
        selectedPixelEventCount = 0; // init it for this packet
        try {
            if (!accumulateEnabled) {
                resetFrame(.5f); // also sets grayValue
            }
            step = 2f / (colorScale + 1);
            // colorScale=1,2,3;  step = 1, 1/2, 1/3, 1/4,  ;
            // later type-grayValue gives -.5 or .5 for spike value, when
            // multipled gives steps of 1/2, 1/3, 1/4 to end up with 0 or 1 when colorScale=1 and you have one event
            for (Object obj : packet) {
                BasicEvent e = (BasicEvent) obj;
                int type = e.getType();
                if (e.x == xsel && e.y == ysel) {
                    playSpike(type);
                }
                int ind = getPixMapIndex(e.x, e.y);
                a = f[ind];
                a += step*thresholdMap.getThreshold(e.x, e.y, type) * (type - grayValue);  // type-.5 = -.5 or .5; step*type= -.5, .5, (cs=1) or -.25, .25 (cs=2) etc.
                f[ind] = a;
                f[ind + 1] = a;
                f[ind + 2] = a;
            }
            autoScaleFrame(f);
        } catch (ArrayIndexOutOfBoundsException e) {
            if (chip.getFilterChain() != null && (chip.getFilterChain().getProcessingMode() != net.sf.jaer.eventprocessing.FilterChain.ProcessingMode.ACQUISITION)) { // only print if real-time mode has not invalidated the packet we are trying render
                e.printStackTrace();
                log.warning(e.toString() + ": ChipRenderer.render(), some event out of bounds for this chip type?");
            }
        }
        pixmap.rewind();
    }
}
