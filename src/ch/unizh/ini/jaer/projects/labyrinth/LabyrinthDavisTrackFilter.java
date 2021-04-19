/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import com.jogamp.opengl.GLAutoDrawable;
import eu.seebetter.ini.chips.DavisChip;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Filters out events from black labyrinth track walls.
 *
 * @author tobi
 */
@Description("Filters out events from black labyrinth track walls.")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class LabyrinthDavisTrackFilter extends EventFilter2D implements FrameAnnotater, Observer, PropertyChangeListener {

    protected float threshholdApsValueToPassEvents = getFloat("threshholdApsValueToPassEvents", .5f);
    ApsFrameExtractor apsFrameExtractor;
    private boolean[] blockedPixels;
    private float[] lastFrame = null;
    private boolean showBlockedPixels = getBoolean("showBlockedPixels", true);
    private DavisChip davisChip = null;
    private boolean freezeFiltering = false;
    protected boolean addToBlocked = false;

    public LabyrinthDavisTrackFilter(AEChip chip) {
        super(chip);
        FilterChain filterChain = new FilterChain(chip);
        apsFrameExtractor = new ApsFrameExtractor(chip);
        apsFrameExtractor.getSupport().addPropertyChangeListener(this);
        filterChain.add(apsFrameExtractor);
        setEnclosedFilterChain(filterChain);
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!(in.getEventPrototype() instanceof ApsDvsEvent)) {
            setFilterEnabled(false);
            log.warning("LabyrinthDavisTrackFilter can only operate on ApsDvsEvent's; in.getEventPrototype()=" + in.getEventPrototype());
            return in;
        }
        if (!(chip instanceof DavisChip)) {
            setFilterEnabled(false);
            log.warning("LabyrinthDavisTrackFilter can only operate with the AEChip instanceof DavisChip; chip=" + chip.toString());
            return in;
        }
        apsFrameExtractor.filterPacket(in);
        davisChip = (DavisChip) chip;
        if (lastFrame == null) {
            return in;
        }
        for (BasicEvent b : in) {
            if (b.isFilteredOut() || b.isSpecial()) {
                continue;
            }
            ApsDvsEvent e = (ApsDvsEvent) b;
            int k = apsFrameExtractor.getIndex(e.x, e.y);
            if (k > lastFrame.length) {
                continue; // TODO some bad event; not clear where it comes from
            }
            if (blockedPixels[k]) {
                e.setFilteredOut(true);
            } else {
                e.setFilteredOut(false);
            }
        }
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
        if (davisChip == null) {
            return;
        }
        float[] filtered = new float[]{0, 0, 1f, .5f};
        float[] unfiltered = new float[]{0, 0, 0, 0};
        if (isShowBlockedPixels() && lastFrame != null) {

            int sx = chip.getSizeX(), sy = chip.getSizeY();
            DavisRenderer renderer = (DavisRenderer) davisChip.getRenderer();
            renderer.setDisplayAnnotation(true);
            renderer.setAnnotateAlpha(1);
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    int k = apsFrameExtractor.getIndex(x, y);
                    int j = renderer.getPixMapIndex(x, y);
                    if (blockedPixels[k]) {
                        renderer.setAnnotateColorRGBA(j, filtered);
                    } else {
                        renderer.setAnnotateColorRGBA(j, unfiltered);

                    }
                }
            }
        } else {
            DavisRenderer renderer = (DavisRenderer) davisChip.getRenderer();
            renderer.setDisplayAnnotation(false);
        }
    }

    public void doResetBlocked() {
        resetBlocked();
    }

//    public void doCaptureTrack() {
//
//    }
    @Override
    public void update(Observable o, Object o1) {
        if (o instanceof AEChip && chip.getNumCells() > 0) {
            initFilter();
        }
    }

    /**
     * @return the threshholdApsValueToPassEvents
     */
    public float getThreshholdApsValueToPassEvents() {
        return threshholdApsValueToPassEvents;
    }

    /**
     * @param threshholdApsValueToPassEvents the threshholdApsValueToPassEvents
     * to set
     */
    public void setThreshholdApsValueToPassEvents(float threshholdApsValueToPassEvents) {
        if (threshholdApsValueToPassEvents > 1) {
            threshholdApsValueToPassEvents = 1;
        } else if (threshholdApsValueToPassEvents < 0) {
            threshholdApsValueToPassEvents = 0;
        }
        this.threshholdApsValueToPassEvents = threshholdApsValueToPassEvents;
        putFloat("threshholdApsValueToPassEvents", threshholdApsValueToPassEvents);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!freezeFiltering && evt.getPropertyName() == ApsFrameExtractor.EVENT_NEW_FRAME) {
            lastFrame = apsFrameExtractor.getNewFrame();
            if (blockedPixels == null || blockedPixels.length != lastFrame.length) {
                blockedPixels = new boolean[lastFrame.length];
            }
            if (!addToBlocked) {
                Arrays.fill(blockedPixels, false);
            }
            for (int i = 0; i < lastFrame.length; i++) {
                if (lastFrame[i] < threshholdApsValueToPassEvents) {
                    blockedPixels[i] = true;
                }
            }

        }
    }

    /**
     * @return the showBlockedPixels
     */
    public boolean isShowBlockedPixels() {
        return showBlockedPixels;
    }

    /**
     * @param showBlockedPixels the showBlockedPixels to set
     */
    public void setShowBlockedPixels(boolean showBlockedPixels) {
        this.showBlockedPixels = showBlockedPixels;
        putBoolean("showBlockedPixels", showBlockedPixels);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes); //To change body of generated methods, choose Tools | Templates.
        if (!yes) {
            try {
                DavisRenderer renderer = (DavisRenderer) davisChip.getRenderer();
                renderer.clearAnnotationMap();
            } catch (Exception e) {
                log.warning(e.toString());
            }
        }
    }

    /**
     * @return the freezeFiltering
     */
    public boolean isFreezeFiltering() {
        return freezeFiltering;
    }

    /**
     * @param freezeFiltering the freezeFiltering to set
     */
    public void setFreezeFiltering(boolean freezeFiltering) {
        this.freezeFiltering = freezeFiltering;
    }

    private void resetBlocked() {
        if (blockedPixels != null) {
            Arrays.fill(blockedPixels, false);
        }
    }

    /**
     * @return the addToBlocked
     */
    public boolean isAddToBlocked() {
        return addToBlocked;
    }

    /**
     * @param addToBlocked the addToBlocked to set
     */
    public void setAddToBlocked(boolean addToBlocked) {
        this.addToBlocked = addToBlocked;
    }

}
