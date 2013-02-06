/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.retinamodel;

import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLException;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.SpikeSound;

/**
 * Models a single approach cell discovered by Botond Roska group in Basel. This
 * cell responds to approaching (expanding, but not translating) dark objects,
 * such as perhaps a hungry bird diving on the mouse.
 *
 * From Botond: The important point is NO delay.
 *
 * Small subunits, that make OFF excitation and ON inhibition input to the
 * approach cell.
 *
 * Importantly both subunits have an expansive nonlinearity such that the ON
 * subunit does not respond when there is a darkening input signal and its
 * response increases nonlinearly with contrast. (you can implement as
 * nonlinearity that has two segments, zero up to a positive number and then
 * linear or below zero it is zero and above zero some exponential. Same
 * nonlinearity for OFF subunits.
 *
 * Ganglion cell is much larger than the subunits and sums them together.
 *
 * This way when there is lateral motion the ON inhibition and OFF excitation
 * sums together to zero response (because of the lack of delay) but when there
 * is approach motion of a black object then there is only excitation.
 *
 * The importance of the nonlinearity is that this way the system will respond
 * when there is an approaching object.
 *
 * @author tobi
 */
@Description("Models approach cell discovered by Botond Roska group")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ApproachCell extends EventFilter2D implements FrameAnnotater, Observer {

    private boolean showSubunits = getBoolean("showSubunits", true);
    private boolean showApproachCell = getBoolean("showApproachCell", true);
    private int subunitSubsamplingBits = getInt("subunitSubsamplingBits", 4); // each subunit is 2^n squared pixels
    private float subunitDecayTimeconstantMs = getFloat("subunitDecayTimeconstantMs", 30);
    private boolean enableSpikeSound = getBoolean("enableSpikeSound", true);
    private ApproachCellModel approachCellModel = new ApproachCellModel();
    private float synapticEFoldingConstant=getFloat("synapticEFoldingConstant",10);
    private float maxSpikeRateHz=getFloat("maxSpikeRateHz",100);
    
//    private SubSampler subSampler=new SubSampler(chip);
    private Subunits subunits;
    private SpikeSound spikeSound = new SpikeSound();

    public ApproachCell(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        setPropertyTooltip("showSubunits", "Enables showing subunit activity annotation over retina output");
        setPropertyTooltip("showApproachCell", "Enables showing approach cell activity annotation over retina output");
        setPropertyTooltip("subunitSubsamplingBits", "Each subunit integrates events from 2^n by 2^n pixels, where n=subunitSubsamplingBits");
        setPropertyTooltip("synapticEFoldingConstant", "Subunit activity inputs to the approach neuron with exponential of subunit activity where exponential efolds with this constant");
        setPropertyTooltip("subunitDecayTimeconstantMs", "Subunit activity decays with this time constant in ms");
        setPropertyTooltip("enableSpikeSound", "Enables audio spike output from approach cell");
        setPropertyTooltip("maxSpikeRateHz", "Maximum spike rate of approach cell in Hz");

    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!(in.getEventPrototype() instanceof PolarityEvent)) {
            return in;
        }
        for (Object o : in) {
            PolarityEvent e = (PolarityEvent) o;
            subunits.update(e);
            boolean spiked = approachCellModel.update(e.timestamp);

        }
        System.out.println(String.format("spikeRate=%.1g \tonActivity=%.2f \toffActivity=%.1f",approachCellModel.spikeRate,subunits.onActivity,subunits.offActivity));

        return in;
    }

    @Override
    public void resetFilter() {
        subunits = new Subunits();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }
    GLU glu = new GLU();
    GLUquadric quad = glu.gluNewQuadric();
    boolean hasBlendChecked = false;
    boolean hasBlend = false;

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
        if (!hasBlendChecked) {
            hasBlendChecked = true;
            String glExt = gl.glGetString(GL.GL_EXTENSIONS);
            if (glExt.indexOf("GL_EXT_blend_color") != -1) {
                hasBlend = true;
            }
        }
        if (hasBlend) {
            try {
                gl.glEnable(GL.GL_BLEND);
                gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                gl.glBlendEquation(GL.GL_FUNC_ADD);
            } catch (GLException e) {
                e.printStackTrace();
                hasBlend = false;
            }
        }
        gl.glPushMatrix();
        gl.glTranslatef(chip.getSizeX()/2, chip.getSizeY()/2,10);
        if (showApproachCell && approachCellModel.spikeRate>0) {
            gl.glColor4f(1, 1, 1, .3f);
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            glu.gluDisk(quad, 0, approachCellModel.spikeRate, 32, 1);
        }
        gl.glPopMatrix();
        
        if(showSubunits){
            // on activity
            gl.glColor4f(0,1,0,.3f);
            gl.glRectf(-10, 0, -5, subunits.computeOnActivity());
            gl.glColor4f(1,0,0,.3f);
            gl.glRectf(-20, 0, -15, subunits.computeOffActivity());
        }

    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg != null && (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY) && chip.getNumPixels() > 0) {
            initFilter();
        }
    }

    private enum SubunitType {

        Off, On
    };

    // handles all subunits on and off
    private class Subunits {

        Subunit[][] onSubunits, offSubunits;
        float onActivity = 0, offActivity = 0;
        int nx;
        int ny;
        int ntot;

        public Subunits() {
            reset();
        }

        // updates appropriate subunit 
        synchronized public void update(PolarityEvent e) {
            // subsample retina address to clump retina input pixel blocks.
            int x = e.x >> subunitSubsamplingBits, y = e.y >> subunitSubsamplingBits;
            // on subunits are updated by ON events, off by OFF events
            float oldActivity;
            switch (e.polarity) {
                case Off: // these subunits are excited by OFF events and in turn excite the approach cell
                    oldActivity = offSubunits[x][y].vmem;
                    offSubunits[x][y].update(e);
                    offActivity = offActivity + offSubunits[x][y].vmem - oldActivity;
                    break;
                case On: // these are excited by ON activity and in turn inhibit the approach cell
                    oldActivity = onSubunits[x][y].vmem;
                    onSubunits[x][y].update(e);
                    onActivity = onActivity + onSubunits[x][y].vmem - oldActivity;
            }
        }

        float computeOnActivity() {
            return onActivity / ntot;
        }

        float computeOffActivity() {
            return offActivity / ntot;
        }

        synchronized private void reset() {
            onActivity = 0;
            offActivity = 0;
            nx = (chip.getSizeX() >> getSubunitSubsamplingBits()) ;
            ny = (chip.getSizeY() >> getSubunitSubsamplingBits()) ;
            if(nx<1)nx=1; if(ny<1) ny=1; // always at least one subunit
            ntot = nx * ny;
            onSubunits = new Subunit[nx][ny];
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    onSubunits[x][y] = new Subunit();
                }
            }
            offSubunits = new Subunit[nx][ny];
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    offSubunits[x][y] = new Subunit();
                }
            }
        }
    }

    // models one single subunit ON of OFF.
    // polarity is ignored here and only handled on update of approach cell
    private class Subunit {

        float vmem;
//        SubunitType type;
        int lastUpdateTimestamp;

        public void update(PolarityEvent e) {
            int dt = e.timestamp - lastUpdateTimestamp;
            lastUpdateTimestamp=e.timestamp;
            if (dt < 0) {
                lastUpdateTimestamp = e.timestamp;
                log.warning("negative delta time=" + dt + " from " + e + "; ignoring event");
                return;
            }
            float decayFactor = (float) Math.exp(-dt / (1000 * subunitDecayTimeconstantMs));
            vmem *= decayFactor;
            vmem = vmem + 1;
        }
    }

    // models soma and integration and spiking of approach cell
    private class ApproachCellModel {

        float spikeRate;
        int lastTimestamp = 0;
//        float threshold;
//        float refracPeriodMs;
        Random r = new Random();

        private boolean update(int timestamp) {
            int dtUs = timestamp - lastTimestamp;
            lastTimestamp = timestamp;
            spikeRate = (float) (Math.exp(subunits.computeOffActivity()/synapticEFoldingConstant) - Math.exp(subunits.computeOnActivity()/synapticEFoldingConstant));
            if(spikeRate>getMaxSpikeRateHz()) spikeRate=getMaxSpikeRateHz();
            if (r.nextFloat() < spikeRate * 1e-6f * dtUs) {
                if (enableSpikeSound) {
                    spikeSound.play();
                }
                return true;
            } else {
                return false;
            }
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
        putBoolean("showSubunits",showSubunits);
    }

    /**
     * @return the showApproachCell
     */
    public boolean isShowApproachCell() {
        return showApproachCell;
    }

    /**
     * @param showApproachCell the showApproachCell to set
     */
    public void setShowApproachCell(boolean showApproachCell) {
        this.showApproachCell = showApproachCell;
        putBoolean("showApproachCell",showApproachCell);
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
    synchronized public void setSubunitSubsamplingBits(int subunitSubsamplingBits) {
        this.subunitSubsamplingBits = subunitSubsamplingBits;
        putInt("subunitSubsamplingBits",subunitSubsamplingBits);
        resetFilter();
    }

    /**
     * @return the subunitDecayTimeconstantMs
     */
    public float getSubunitDecayTimeconstantMs() {
        return subunitDecayTimeconstantMs;
    }

    /**
     * @param subunitDecayTimeconstantMs the subunitDecayTimeconstantMs to set
     */
    public void setSubunitDecayTimeconstantMs(float subunitDecayTimeconstantMs) {
        this.subunitDecayTimeconstantMs = subunitDecayTimeconstantMs;
        putFloat("subunitDecayTimeconstantMs",subunitDecayTimeconstantMs);
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
        putBoolean("enableSpikeSound",enableSpikeSound);
    }

    /**
     * @return the synapticEFoldingConstant
     */
    public float getSynapticEFoldingConstant() {
        return synapticEFoldingConstant;
    }

    /**
     * @param synapticEFoldingConstant the synapticEFoldingConstant to set
     */
    public void setSynapticEFoldingConstant(float synapticEFoldingConstant) {
        this.synapticEFoldingConstant = synapticEFoldingConstant;
        putFloat("synapticEFoldingConstant",synapticEFoldingConstant);
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
        putFloat("maxSpikeRateHz",maxSpikeRateHz);
    }
}
