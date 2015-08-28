/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.retinamodel;

import java.awt.Font;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.SpikeSound;

import com.jogamp.opengl.util.awt.TextRenderer;

/**
 * Superclass for retina model cells developed in VISUALIZE.
 *
 * @author tobi
 */
public abstract class AbstractRetinaModelCell extends EventFilter2D implements FrameAnnotater, Observer {

    protected boolean showSubunits = getBoolean("showSubunits", false);
    protected boolean showOutputCell = getBoolean("showOutputCell", false);
    protected int subunitSubsamplingBits = getInt("subunitSubsamplingBits", 3); // each
    // subunit
    // is
    // 2^n
    // squared
    // pixels
    protected float subunitDecayTimeconstantMs = getFloat("subunitDecayTimeconstantMs", 2);
    protected float maxSpikeRateHz = getFloat("maxSpikeRateHz", 100);
    protected int minUpdateIntervalUs = getInt("minUpdateIntervalUs", 10000);
    protected SpikeSound spikeSound = new SpikeSound();
    protected TextRenderer renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 10), true, true);
    GLU glu = null;
    GLUquadric quad = null;
    boolean hasBlendChecked = false;
    boolean hasBlend = false;
    protected boolean enableSpikeSound = getBoolean("enableSpikeSound", true);
    protected boolean poissonFiringEnabled = getBoolean("poissonFiringEnabled", false);

    public AbstractRetinaModelCell(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        setPropertyTooltip("-showSubunits", "Enables showing subunit activity annotation over retina output");
        setPropertyTooltip("-subunitSubsamplingBits",
                "Each subunit integrates events from 2^n by 2^n pixels, where n=subunitSubsamplingBits");
        setPropertyTooltip("subunitDecayTimeconstantMs", "Subunit activity decays with this time constant in ms");
        setPropertyTooltip("-enableSpikeSound", "Enables audio spike output from approach cell");
        setPropertyTooltip("-maxSpikeRateHz", "Maximum spike rate of approach cell in Hz for Poisson firing model");
        setPropertyTooltip("-minUpdateIntervalUs",
                "subunits activities are decayed to zero at least this often in us, even if they receive no input");
        setPropertyTooltip("-poissonFiringEnabled",
                "The ganglion cell fires according to Poisson rate model for net synaptic input");
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (glu == null) {
            glu = new GLU();
            quad = glu.gluNewQuadric();
        }

    }

    @Override
    public void update(Observable o, Object arg) {
        if ((arg != null) && ((arg == Chip2D.EVENT_SIZEX) || (arg == Chip2D.EVENT_SIZEY)) && (chip.getNumPixels() > 0)) {
            initFilter();
        }
    }

    /**
     * @return the showSubunits
     */
    public boolean isShowSubunits() {
        return showSubunits;
    }

    /**
     * @param showSubunits the showSubunits to set
     */
    public void setShowSubunits(boolean showSubunits) {
        this.showSubunits = showSubunits;
        putBoolean("showSubunits", showSubunits);
    }

    /**
     * @return the showobjectMotionCell
     */
    public boolean isShowOutputCell() {
        return showOutputCell;
    }

    /**
     * @param showobjectMotionCell the showobjectMotionCell to set
     */
    public void setShowOutputCell(boolean showObjectMotionCell) {
        this.showOutputCell = showObjectMotionCell;
        putBoolean("showOutputCell", showObjectMotionCell);
    }

    /**
     * @return the subunitSubsamplingBits
     */
    public int getSubunitSubsamplingBits() {
        return subunitSubsamplingBits;
    }

    /**
     * @param subunitSubsamplingBits the subunitSubsamplingBits to set
     */
    public synchronized void setSubunitSubsamplingBits(int subunitSubsamplingBits) {
        this.subunitSubsamplingBits = subunitSubsamplingBits;
        putInt("subunitSubsamplingBits", subunitSubsamplingBits);
        resetFilter();
    }

    /**
     * @param subunitDecayTimeconstantMs the subunitDecayTimeconstantMs to set
     */
    public void setSubunitDecayTimeconstantMs(float subunitDecayTimeconstantMs) {
        this.subunitDecayTimeconstantMs = subunitDecayTimeconstantMs;
        putFloat("subunitDecayTimeconstantMs", subunitDecayTimeconstantMs);
    }

    /**
     * @return the enableSpikeSound
     */
    public boolean isEnableSpikeSound() {
        return enableSpikeSound;
    }

    /**
     * @param enableSpikeSound the enableSpikeSound to set
     */
    public void setEnableSpikeSound(boolean enableSpikeSound) {
        this.enableSpikeSound = enableSpikeSound;
        putBoolean("enableSpikeSound", enableSpikeSound);
    }

    /**
     * @return the maxSpikeRateHz
     */
    public float getMaxSpikeRateHz() {
        return maxSpikeRateHz;
    }

    /**
     * @param maxSpikeRateHz the maxSpikeRateHz to set
     */
    public void setMaxSpikeRateHz(float maxSpikeRateHz) {
        this.maxSpikeRateHz = maxSpikeRateHz;
        putFloat("maxSpikeRateHz", maxSpikeRateHz);
    }

    /**
     * @return the minUpdateIntervalUs
     */
    public int getMinUpdateIntervalUs() {
        return minUpdateIntervalUs;
    }

    /**
     * @param minUpdateIntervalUs the minUpdateIntervalUs to set
     */
    public void setMinUpdateIntervalUs(int minUpdateIntervalUs) {
        this.minUpdateIntervalUs = minUpdateIntervalUs;
        putInt("minUpdateIntervalUs", minUpdateIntervalUs);
    }

    /**
     * @return the poissonFiringEnabled
     */
    public boolean isPoissonFiringEnabled() {
        return poissonFiringEnabled;
    }

    /**
     * @param poissonFiringEnabled the poissonFiringEnabled to set
     */
    public void setPoissonFiringEnabled(boolean poissonFiringEnabled) {
        this.poissonFiringEnabled = poissonFiringEnabled;
        putBoolean("poissonFiringEnabled", poissonFiringEnabled);
    }

}
