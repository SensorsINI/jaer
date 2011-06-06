/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import ch.unizh.ini.jaer.projects.thresholdlearner.TemporalContrastEvent;
import cl.eye.CLCamera.InvalidParameterException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * A behavioral model of an AE retina using the code laboratories interface to a PS eye camera.
 * 
 * @author tobi
 */
@Description("AE retina using the PS eye camera")
public class PSEyeCLModelRetina extends AEChip {

    private int[] lastEventPixelValues = new int[320 * 240];
    private int gain = getPrefs().getInt("gain", 30);
    private int exposure = getPrefs().getInt("exposure", 511);
    private int frameRate = getPrefs().getInt("frameRate", 120);
    private boolean autoGainEnabled = getPrefs().getBoolean("autoGainEnabled", true);
    private boolean autoExposureEnabled = getPrefs().getBoolean("autoExposureEnabled", true);
    private int eventThreshold = getPrefs().getInt("eventThreshold", 4);
    private boolean initialized = false; // used to avoid writing events for all pixels of first frame of data
    private boolean linearInterpolateTimeStamp = getPrefs().getBoolean("linearInterpolateTimeStamp", false);
    private int lastEventTimeStamp;

    public PSEyeCLModelRetina() {
        setSizeX(320);
        setSizeY(240);
        setEventExtractor(new EventExtractor(this));
        setEventClass(PolarityEvent.class);
        setBiasgen(new Controls(this));
    }

    @Override
    public void setHardwareInterface(HardwareInterface hardwareInterface) {
        super.setHardwareInterface(hardwareInterface);
        if (hardwareInterface != null && (hardwareInterface instanceof CLRetinaHardwareInterface)) {
            try {
                CLRetinaHardwareInterface hw = (CLRetinaHardwareInterface) hardwareInterface;
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        }
    }

    public void sendConfiguration() {
        HardwareInterface hardwareInterface = getHardwareInterface();
        if ((hardwareInterface != null) && hardwareInterface.isOpen() && (hardwareInterface instanceof CLRetinaHardwareInterface)) {
            try {
                CLRetinaHardwareInterface hw = (CLRetinaHardwareInterface) hardwareInterface;
                hw.setGain(gain);
                hw.setExposure(exposure);
                hw.setAutoExposure(autoExposureEnabled);
                hw.setAutoGain(autoGainEnabled);
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        }
    }

    public class Controls extends Biasgen {

        public Controls(Chip chip) {
            super(chip);
        }

        @Override
        public JPanel buildControlPanel() {
            return new CLCameraControlPanel(PSEyeCLModelRetina.this);
        }

        @Override
        public void open() throws HardwareInterfaceException {
            super.open();
            PSEyeCLModelRetina.this.sendConfiguration();

        }
    }

    public class EventExtractor extends TypedEventExtractor<TemporalContrastEvent> {

        public EventExtractor(AEChip aechip) {
            super(aechip);
        }

        // TODO logged events cannot be read in here since they are not complete frames anymore!
        
        @Override
        public synchronized void extractPacket(AEPacketRaw in, EventPacket out) {
            out.allocate(chip.getNumPixels());
            int[] pixVals = in.getAddresses(); // pixel RGB values stored here by hardware interface
            int ts = in.getTimestamps()[0]; // timestamps stored here, currently only first timestamp meaningful TODO multiple frames stored here
            int youngestIndex = 0; int maxN = 1;  // used to find youngest event for ordering
            int eventTimeDelta = ts - lastEventTimeStamp;
            OutputEventIterator itr = out.outputIterator();
            int sx = getSizeX(), sy = getSizeY(), i = 0;
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    int s = 0; // color channel, which is 0,8,16?
                    int pixval = (pixVals[i] & (0xff << s)) >>> s; // get gray value 0-255
                    if (!initialized) {
                        lastEventPixelValues[i] += pixval; // update stored gray level for first frame

                    } else {
                        int lastval = lastEventPixelValues[i];
                        int diff = pixval - lastval;
                        if (diff > eventThreshold) { // if our gray level is sufficiently higher than the stored gray level
                            int n = diff / eventThreshold;
                            for (int j = n; 0 < j; j--) { // use down iterator as ensures latest timestamp as last event
                                PolarityEvent e = (PolarityEvent) itr.nextOutput();
                                e.x = (short) x;
                                e.y = (short) (sy - y - 1); // flip y according to jAER with 0,0 at LL
                                e.type = 1;  // ON type
                                if (n > 1 && linearInterpolateTimeStamp) {
                                    e.timestamp = ts - (j - 1) * eventTimeDelta / n;
                                }
                                else{
                                    e.timestamp = ts;
                                }
                                e.setPolarity(PolarityEvent.Polarity.On);
                            }
                            if (linearInterpolateTimeStamp && n > maxN){
                                maxN = n;
                                youngestIndex = out.getSize();
                            }
                            lastEventPixelValues[i] += eventThreshold * n; // update stored gray level by events
                        } else if (diff < -eventThreshold) {
                            int n = -diff / eventThreshold;
                            for (int j = n; 0 < j; j--) {
                                PolarityEvent e = (PolarityEvent) itr.nextOutput();
                                e.x = (short) x;
                                e.y = (short) (sy - y - 1);
                                e.type = 0;
                                if (n > 1 && linearInterpolateTimeStamp) {
                                    e.timestamp = ts - (j - 1) * eventTimeDelta / n;
                                }
                                else{
                                    e.timestamp = ts;
                                }
                                e.setPolarity(PolarityEvent.Polarity.Off);
                            }
                            if (linearInterpolateTimeStamp && n > maxN){
                                maxN = n;
                                youngestIndex = out.getSize();
                            }
                            lastEventPixelValues[i] -= eventThreshold * n;
                        }
                    }

                    i++;
                }
            }
         initialized=true;
         lastEventTimeStamp = ts;
         // clumsy hack to insure first event is also youngest
         if (linearInterpolateTimeStamp && youngestIndex > 0){
             if (youngestIndex > out.getSize()){
                 youngestIndex = out.getSize();
             }
             PolarityEvent e = new PolarityEvent();
             e.copyFrom((PolarityEvent) out.getEvent(youngestIndex));
             ((PolarityEvent) out.getEvent(youngestIndex)).copyFrom((PolarityEvent) out.getFirstEvent());
             ((PolarityEvent) out.getFirstEvent()).copyFrom((PolarityEvent) e);
         }
       }
    }

    /**
     * Get the value of gain
     *
     * @return the value of gain
     */
    public int getGain() {
        return gain;
    }

    /**
     * Set the value of gain
     *
     * @param gain new value of gain
     */
    public void setGain(int gain) {
        this.gain = gain;
        getPrefs().putInt("gain", gain);
        sendConfiguration();
    }

    /**
     * @return the exposure
     */
    public int getExposure() {
        return exposure;
    }

    /**
     * @param exposure the exposure to set
     */
    public void setExposure(int exposure) {
        this.exposure = exposure;
        getPrefs().putInt("exposure", exposure);
        sendConfiguration();
    }

    /**
     * @return the frameRate
     */
    public int getFrameRate() {
        return frameRate;
    }

    /**
     * @param frameRate the frameRate to set
     */
    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
        getPrefs().putInt("frameRate", frameRate);
        HardwareInterface hardwareInterface = getHardwareInterface();
        if (hardwareInterface != null && (hardwareInterface instanceof CLRetinaHardwareInterface)) {
            try {
                CLRetinaHardwareInterface hw = (CLRetinaHardwareInterface) hardwareInterface;
                hw.setFrameRateHz(frameRate);
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        }
    }

    /**
     * @return the autoGainEnabled
     */
    public boolean isAutoGainEnabled() {
        return autoGainEnabled;
    }

    /**
     * @param autoGainEnabled the autoGainEnabled to set
     */
    public void setAutoGainEnabled(boolean autoGainEnabled) {
        this.autoGainEnabled = autoGainEnabled;
        getPrefs().putBoolean("autoGainEnabled", autoGainEnabled);
        sendConfiguration();
    }

    /**
     * @return the autoExposureEnabled
     */
    public boolean isAutoExposureEnabled() {
        return autoExposureEnabled;
    }

    /**
     * @param autoExposureEnabled the autoExposureEnabled to set
     */
    public void setAutoExposureEnabled(boolean autoExposureEnabled) {
        this.autoExposureEnabled = autoExposureEnabled;
        getPrefs().putBoolean("autoExposureEnabled", autoExposureEnabled);
        sendConfiguration();
    }

    /**
     * @return the eventThreshold
     */
    public int getEventThreshold() {
        return eventThreshold;
    }

    /**
     * @param eventThreshold the eventThreshold to set
     */
    public void setEventThreshold(int eventThreshold) {
        this.eventThreshold = eventThreshold;
        getPrefs().putInt("eventThreshold", eventThreshold);
    }
    
    /**
     * @return whether using linear interpolation of TimeStamps
     */
    public boolean getLinearInterpolateTimeStamp() {
        return linearInterpolateTimeStamp;
    }

    /**
     * @param eventThreshold the eventThreshold to set
     */
    public void setLinearInterpolateTimeStamp(boolean linearInterpolateTimeStamp) {
        this.linearInterpolateTimeStamp = linearInterpolateTimeStamp;
        getPrefs().putBoolean("linearInterpolateTimeStamp", linearInterpolateTimeStamp);
    }
}
