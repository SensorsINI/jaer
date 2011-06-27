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
import java.util.ArrayList;
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
    /* added into CLCamera.cameraMode
    private int frameRate = getPrefs().getInt("frameRate", 120);
     */
    private boolean autoGainEnabled = getPrefs().getBoolean("autoGainEnabled", true);
    private boolean autoExposureEnabled = getPrefs().getBoolean("autoExposureEnabled", true);
    private int eventThreshold = getPrefs().getInt("eventThreshold", 4);
    private boolean initialized = false; // used to avoid writing events for all pixels of first frame of data
    private boolean linearInterpolateTimeStamp = getPrefs().getBoolean("linearInterpolateTimeStamp", false);
    private int lastEventTimeStamp;
//    private PolarityEvent tempEvent = new PolarityEvent();
    private BasicEvent tempEvent = new BasicEvent();
    private ArrayList<Integer> discreteEventCount = new ArrayList<Integer>();
    private boolean colorMode = false;

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
                colorMode = (hw.getCameraMode().color == CLCamera.CLEYE_COLOR_PROCESSED);
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
            int eventTimeDelta = ts - lastEventTimeStamp;
            OutputEventIterator itr = out.outputIterator();
            if (linearInterpolateTimeStamp) discreteEventCount.clear();
            int sx = getSizeX(), sy = getSizeY(), i = 0, j = 0;
            int pixval = 0, n = 0, diff = 0, lastval = 0;
            float pixR = 0, pixB = 0;
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    if (colorMode) {
                        pixR = (float) ((pixVals[i] >> 4) & 0xff);
                        pixB = (float) (pixVals[i] & 0xff);
                        pixval = 255;
                        if (pixR > 0 || pixB > 0) 
                            pixval = (int) (255 - 510 * (pixR * pixB) / (pixR * pixR + pixB * pixB));
                    } else pixval = pixVals[i] & 0xff; // get gray value 0-255
                    if (!initialized) {
                        lastEventPixelValues[i] += pixval; // update stored gray level for first frame

                    } else {
                        lastval = lastEventPixelValues[i];
                        diff = pixval - lastval;
                        if (diff > eventThreshold) { // if our gray level is sufficiently higher than the stored gray level
                            n = diff / eventThreshold;
                            for (j = 0; j < n; j++) { // use down iterator as ensures latest timestamp as last event
                                PolarityEvent e = (PolarityEvent) itr.nextOutput();
                                e.x = (short) x;
                                e.y = (short) (sy - y - 1); // flip y according to jAER with 0,0 at LL
                                e.type = 1;  // ON type
                                e.setPolarity(PolarityEvent.Polarity.On);
                                if (linearInterpolateTimeStamp) {
                                    e.timestamp = ts - j * eventTimeDelta / n;
                                    orderedLastSwap(out, j);
                                }
                                else{
                                    e.timestamp = ts;
                                }
                                
                            }
                            lastEventPixelValues[i] += eventThreshold * n; // update stored gray level by events
                        } else if (diff < -eventThreshold) {
                            n = -diff / eventThreshold;
                            for (j = 0; j < n; j++) {
                                PolarityEvent e = (PolarityEvent) itr.nextOutput();
                                e.x = (short) x;
                                e.y = (short) (sy - y - 1);
                                e.type = 0;
                                e.setPolarity(PolarityEvent.Polarity.Off);
                                if (linearInterpolateTimeStamp) {
                                    e.timestamp = ts - j * eventTimeDelta / n;
                                    orderedLastSwap(out, j);
                                }
                                else{
                                    e.timestamp = ts;
                                }
                                
                            }
                            lastEventPixelValues[i] -= eventThreshold * n;
                        }
                    }

                    i++;
                }
            }
         initialized=true;
         lastEventTimeStamp = ts;
       }
    }

    /*
     * Used to reorder event packet using interpolated time step.
     * Much faster then using Arrays.sort on event packet
     */
    private void orderedLastSwap(EventPacket out, int timeStep) {
        while (discreteEventCount.size() <= timeStep) {
            discreteEventCount.add(0);
        }
        discreteEventCount.set(timeStep, discreteEventCount.get(timeStep) + 1);
        if (timeStep > 0) {
            int previousStepCount = 0;
            for (int i = 0; i < timeStep; i++) {
                previousStepCount += (int) discreteEventCount.get(i);
            }
            int size = out.getSize() - 1;
            swap(out, size, size - previousStepCount);
        }
    }
    
    /*
     * Exchange positions of two events in packet
     */
    private void swap(EventPacket out, int index1, int index2) {
        BasicEvent[] elementData = out.getElementData();
        tempEvent = elementData[index1];
        elementData[index1] = elementData[index2];
        elementData[index2] = tempEvent;
        
        /*  Old code - written as unsure about Java value/reference management 
         * (i.e. didn't want to create large local copies of element data).
        PolarityEvent e1 = (PolarityEvent) out.getEvent(index1);
        PolarityEvent e2 = (PolarityEvent) out.getEvent(index2);
        tempEvent.copyFrom(e1);
        e1.copyFrom(e2);
        e2.copyFrom(tempEvent);
         */        
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
    /* removed by mlk as not runtime changable
    public int getFrameRate() {
        return frameRate;
    }
     */

    /**
     * @param frameRate the frameRate to set
     */
    /* removed by mlk as not runtime changable
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
     */

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
