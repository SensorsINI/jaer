/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.capocaccia.cne.jaer.cne2012.smalleyemovements;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.filter.RotateFilter;
import net.sf.jaer.graphics.ImageDisplay;
import ch.unizh.ini.jaer.hardware.pantilt.PanTiltAimer;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.BasicEvent;

/**
 * Moves DVS128 using pantilt and maintains continuous image based on DVS
 * outputs produced by small jittering eye movements.
 *
 * @author tobi
 */
@Description("Small eye movements from pan-tilt are used to build up gradient image")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SmallEyeMovements extends EventFilter2D implements Observer, PropertyChangeListener {

    PanTiltAimer aimer = null;
    FilterChain filterChain = null;
    ImageDisplay display = null;
    private float eventGrayWeight = getFloat("eventGrayWeight", 1f / 10);
    private float fadeRate = getFloat("fadeRate", 0.01f);
    private float grayLevel = 0.5f;
    private volatile boolean resetImageFlag = false;

    public SmallEyeMovements(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(new RotateFilter(chip));
        filterChain.add(new BackgroundActivityFilter(chip));
        aimer = new PanTiltAimer(chip);
        aimer.getSupport().addPropertyChangeListener(PanTiltAimer.Message.PanTiltSet.name(), this);
        filterChain.add(aimer);
        setEnclosedFilterChain(filterChain);
        setPropertyTooltip("eventGrayWeight", "how much gray scale each event updates image");
        setPropertyTooltip("fadeRate", "rate that old events fade away; 1 means each event instantaneously updates image completely");
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                log.info("disabling servos");
                aimer.doDisableServos();
            }
        });
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        out = filterChain.filterPacket(in);
        if (resetImageFlag) {
            display.resetFrame(grayLevel);
            resetImageFlag = false; // caused by aimer gui movement of eye
        }
        for (Object o : out) {
            PolarityEvent e = (PolarityEvent) o;
            updateImage(e);
        }
        display.repaint();
        return out;
    }
    private int lastptsign = 1;

    private void updateImage(PolarityEvent e) {
        float[] rgb = display.getPixmapRGB(e.x, e.y);
        float oldvalue = rgb[0]; // old value
        float[] ptchange = aimer.getPanTiltHardware().getPreviousPanTiltChange(); // get sign of eye movements, 
        int ptsign = (int) Math.signum(ptchange[0] * ptchange[1]); //+1 for up and to right
        if (ptsign == 0) {
            ptsign = lastptsign;
        } else {
            lastptsign = ptsign;
        }
        int pol = e.getPolaritySignum(); // sign of event
        float newvalue = (1 - fadeRate) * oldvalue + fadeRate * (oldvalue + ptsign * pol * eventGrayWeight);
        display.setPixmapGray(e.x, e.y, newvalue);
    }

    @Override
    public void resetFilter() {
        resetImage();
    }

    public void doAim() {
        aimer.doAim();
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            if (display == null) {
                display = ImageDisplay.createOpenGLCanvas();
            }
            display.resetFrame(grayLevel);
            display.setImageSize(chip.getSizeX(), chip.getSizeY());
            display.setBorderSpacePixels(5);
            display.setPreferredSize(new Dimension(600, 600));
//            display.setMinimumSize(new Dimension(200, 200));
//            display.setMaximumSize(new Dimension(800, 800));
//            Component[] ca=chip.getAeViewer().getImagePanel().getComponents();
//            for(Component c:ca){
//                if(c==chip.getCanvas().getCanvas()){
//                    chip.getAeViewer().getImagePanel().remove(c);
//                    chip.getAeViewer().getImagePanel().add(c,BorderLayout.WEST);
//                }
//            }
            chip.getAeViewer().getImagePanel().add(display, BorderLayout.WEST); // display will shrink vertically but not horizonstally
            resetImage();
            //            chip.getAeViewer().getImagePanel().revalidate();
//            chip.getAeViewer().pack();
        } else {
            try {
                if (display != null) {
                    chip.getAeViewer().getImagePanel().remove(display);
                }
            } catch (NullPointerException e) {
                log.warning(e.toString());
            }
        }
    }

    private void resetImage() {
        if (display != null) {
            display.clearImage();
        }
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof AEChip) {
            AEChip c = (AEChip) o;
            if (c.getSizeX() > 0 && c.getSizeY() > 0) {
                if (display != null) {
                    display.clearImage();
                }
            }
        }
    }

    /**
     * @return the eventGrayWeight
     */
    public float getEventGrayWeight() {
        return eventGrayWeight;
    }

    /**
     * @param eventGrayWeight the eventGrayWeight to set
     */
    public void setEventGrayWeight(float eventGrayWeight) {
        this.eventGrayWeight = eventGrayWeight;
        putFloat("eventGrayWeight", eventGrayWeight);
    }

    /**
     * @return the fadeRate
     */
    public float getFadeRate() {
        return fadeRate;
    }

    /**
     * @param fadeRate the fadeRate to set
     */
    public void setFadeRate(float fadeRate) {
        if (fadeRate < 0) {
            fadeRate = 0;
        } else if (fadeRate > 1) {
            fadeRate = 1;
        }
        this.fadeRate = fadeRate;
        putFloat("fadeRate", fadeRate);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        resetImageFlag = true;
    }
}
