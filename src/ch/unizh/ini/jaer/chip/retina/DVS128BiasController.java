/*
 *
 * Created on January 9, 2006, 10:41 AM
 * Cloned from DVS128BiasController Feb 2011 by Tobi
 *
 */
package ch.unizh.ini.jaer.chip.retina;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.event.*;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.*;
import net.sf.jaer.util.filter.*;
import com.sun.opengl.util.*;
import java.awt.Graphics2D;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.prefs.*;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Controls the rate of events from the retina by controlling retina biases.
The event threshold is increased if rate exceeds rateHigh until rate drops below rateHigh.
The threshold is decreased if rate is lower than rateLow. 
Hysterisis limits crossing noise.
A lowpass filter smooths the rate measurements.
 *
 * @author tobi
 */
public class DVS128BiasController extends EventFilter2D implements FrameAnnotater {

    public static String getDescription() {
        return "Adaptively controls biases on DVS128 to control event rate";
    }
    protected int rateHigh = getInt("DVS128BiasController.rateHigh", 400);

//    private int rateMid=getInt("DVS128BiasController.rateMid",300);
    private int rateLow = getInt("DVS128BiasController.rateLow", 100);
    private int rateHysteresis = getInt("DVS128BiasController.rateHysteresis", 50);
    private float hysteresisFactor = getFloat("DVS128BiasController.hysteresisFactor", 1.3f);
    private float rateFilter3dBFreqHz = getFloat("DVS128BiasController.rateFilter3dBFreqHz", 1);
    private final static int MIN_CMD_INTERVAL_MS = 100;
    private long lastCommandTime = 0; // limits use of status messages that control biases
    private float tweakStepAmount = getFloat("DVS128BiasController.tweakStepAmount", .001f);

 
    enum State {

        INITIAL, LOW, MEDIUM, HIGH;
        private long timeChanged = 0;

        long getTimeChanged() {
            return timeChanged;
        }

        void setTimeChanged(long t) {
            timeChanged = t;
        }
    };
    State state = State.INITIAL, lastState = State.INITIAL;
    float lastrate = 0;
    LowpassFilter filter = new LowpassFilter();
    Writer logWriter;
    private boolean writeLogEnabled = false;
    int lastt = 0;

    /**
     * Creates a new instance of DVS128BiasController
     */
    public DVS128BiasController(AEChip chip) {
        super(chip);
        if (!(chip instanceof DVS128)) {
            log.warning(chip + " is not of type DVS128");
        }
        filter.set3dBFreqHz(rateFilter3dBFreqHz);
        setPropertyTooltip("rateLow", "event rate in keps for LOW state");
        setPropertyTooltip("rateHigh", "event rate in keps for HIGH state");
        setPropertyTooltip("rateHysteresis", "hysteresis for state change; after state entry, state exited only when avg rate changes by this factor from threshold");
        setPropertyTooltip("rateFilter3dBFreqHz", "3dB freq in Hz for event rate lowpass filter");
        setPropertyTooltip("hysteresisFactor", "hysteresis for state change; after state entry, state exited only when avg rate changes by this factor from threshold");
        setPropertyTooltip("tweakStepAmount", "amount to tweak by each step");
    }

    public Object getFilterState() {
        return null;
    }

    synchronized public void resetFilter() {
        if (chip.getHardwareInterface() == null) {
            return;  // avoid sending hardware commands unless the hardware is there and we are active
        }
        if (chip.getBiasgen() == null) {
            setFilterEnabled(false);
            log.warning("null biasgen object to operate on, disabled filter");
            return;
        }
        if (!(chip.getBiasgen() instanceof DVS128.Biasgen)) {
            setFilterEnabled(false);
            log.warning("Wrong type of biasgen object; should be DVS128.Biasgen but object is " + chip.getBiasgen() + "; disabled filter");
            return;
        }
        DVS128.Biasgen biasgen = (DVS128.Biasgen) getChip().getBiasgen();
        if (biasgen == null) {
//            log.warning("null biasgen, not doing anything");
            return;
        }
        biasgen.loadPreferences();
        state = State.INITIAL;
    }

    public int getRateHigh() {
        return rateHigh;
    }

    synchronized public void setRateHigh(int upperThreshKEPS) {
        this.rateHigh = upperThreshKEPS;
        putInt("DVS128BiasController.rateHigh", upperThreshKEPS);
    }

    public int getRateLow() {
        return rateLow;
    }

    synchronized public void setRateLow(int lowerThreshKEPS) {
        this.rateLow = lowerThreshKEPS;
        putInt("DVS128BiasController.rateLow", lowerThreshKEPS);
    }

    synchronized public EventPacket filterPacket(EventPacket in) {
        if (!isFilterEnabled()) {
            return in;
        }
        // TODO reenable check for LIVE mode here
//        if (chip.getAeViewer().getPlayMode() != AEViewer.PlayMode.LIVE) {
//            return in;  // don't servo on recorded data!
//        }
        float r = in.getEventRateHz() / 1e3f;

        lastt = (int) (System.currentTimeMillis() * 1000);
        float lpRate = filter.filter(r, lastt);

        setState(lpRate);
        setBiases();
        if (writeLogEnabled) {
            if (logWriter == null) {
                logWriter = openLoggingOutputFile();
            }
            try {
                logWriter.write(in.getLastTimestamp() + " " + r + " " + lpRate + " " + state.ordinal() + "\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return in;
    }

    void setState(float r) {
        lastState = state;
        switch (state) {
            case LOW:
                if (r > rateLow * hysteresisFactor) {
                    state = State.MEDIUM;
                }
                break;
            case MEDIUM:
                if (r < rateLow / hysteresisFactor) {
                    state = State.LOW;
                } else if (r > rateHigh * hysteresisFactor) {
                    state = State.HIGH;
                }
                break;
            case HIGH:
                if (r < rateHigh / hysteresisFactor) {
                    state = State.MEDIUM;
                }
                break;
            default:
                state = State.MEDIUM;
        }
    }

    void setBiases() {
        long dt=lastt - lastCommandTime;
        if (dt>0 && dt < MIN_CMD_INTERVAL_MS) {
            return; // don't saturate setup packet bandwidth and stall on blocking USB writes
        }
        DVS128.Biasgen biasgen = (DVS128.Biasgen) getChip().getBiasgen();
        if (biasgen == null) {
            log.warning("null biasgen, not doing anything");
            return;
        }
        float bw = biasgen.getThresholdTweak();
        switch (state) {
            case LOW:
                biasgen.setThresholdTweak(bw - getTweakStepAmount());
//                biasgen.decreaseThreshold();
                break;
            case HIGH:
                biasgen.setThresholdTweak(bw + getTweakStepAmount());
//               biasgen.increaseThreshold();
                break;
            default:
        }

    }

    public float getRateFilter3dBFreqHz() {
        return rateFilter3dBFreqHz;
    }

    synchronized public void setRateFilter3dBFreqHz(float rateFilter3dBFreqHz) {
        if (rateFilter3dBFreqHz < .01) {
            rateFilter3dBFreqHz = 0.01f;
        } else if (rateFilter3dBFreqHz > 20) {
            rateFilter3dBFreqHz = 20;
        }
        this.rateFilter3dBFreqHz = rateFilter3dBFreqHz;
        putFloat("DVS128BiasController.rateFilter3dBFreqHz", rateFilter3dBFreqHz);
        filter.set3dBFreqHz(rateFilter3dBFreqHz);

    }

    public void initFilter() {
    }

    public float getHysteresisFactor() {
        return hysteresisFactor;
    }

    synchronized public void setHysteresisFactor(float h) {
        if (h < 1) {
            h = 1;
        } else if (h > 5) {
            h = 5;
        }
        this.hysteresisFactor = h;
        putFloat("DVS128BiasController.hysteresisFactor", hysteresisFactor);
    }

   /**
     * @return the tweakStepAmount
     */
    public float getTweakStepAmount() {
        return tweakStepAmount;
    }

    /**
     * @param tweakStepAmount the tweakStepAmount to set
     */
    public void setTweakStepAmount(float tweakStepAmount) {
        this.tweakStepAmount = tweakStepAmount;
        putFloat("DVS128BiasController.tweakStepAmount", tweakStepAmount);
    }


    Writer openLoggingOutputFile() {
        DateFormat loggingFilenameDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
        String dateString = loggingFilenameDateFormat.format(new Date());
        String className = "DVS128BiasController";
        int suffixNumber = 0;
        boolean suceeded = false;
        String filename;
        Writer writer = null;
        File loggingFile;
        do {
            filename = className + "-" + dateString + "-" + suffixNumber + ".txt";
            loggingFile = new File(filename);
            if (!loggingFile.isFile()) {
                suceeded = true;
            }
        } while (suceeded == false && suffixNumber++ <= 5);
        if (suceeded == false) {
            log.warning("could not open a unigue new file for logging after trying up to " + filename);
            return null;
        }
        try {
            writer = new FileWriter(loggingFile);
            log.info("starting logging bias control at " + dateString);
            writer.write("# time rate lpRate state\n");
            writer.write(String.format("# rateLow=%f rateHigh=%f hysteresisFactor=%f, 3dbCornerFreqHz=%f\n", rateLow, rateHigh, hysteresisFactor, rateFilter3dBFreqHz));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return writer;
    }

    EngineeringFormat fmt = new EngineeringFormat();

    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        GL gl = drawable.getGL();
        gl.glPushMatrix();
        final GLUT glut = new GLUT();
        gl.glColor3f(1, 1, 1); // must set color before raster position (raster position is like glVertex)
        gl.glRasterPos3f(0, 0, 0);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("lpRate=%s, state=%s", fmt.format(filter.getValue()), state.toString()));
        gl.glPopMatrix();
    }

    public boolean isWriteLogEnabled() {
        return writeLogEnabled;
    }

    public void setWriteLogEnabled(boolean writeLogEnabled) {
        this.writeLogEnabled = writeLogEnabled;
    }
}
