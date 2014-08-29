/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.*;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.TimerTask;
import java.util.logging.Logger;
import javax.swing.JFrame;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.FremeExtractor;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.filter.RotateFilter;
import net.sf.jaer.eventprocessing.freme.Freme;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import org.ine.telluride.jaer.tell2011.head6axis.Head6DOF_ServoController;

/**
 *
 * @author philipp
 */
public class ImageCreator extends FremeExtractor implements Observer {

    protected static final Logger log = Logger.getLogger("ImageCreator");
    int currentRow = 0;
    int currentColumn = 0;
    int warningCount = 10;

    // pixel allocation after movement
    private final float stepSize;
    public final float eyeRangeX;
    public final float eyeRangeY;
    public final float headRangeX;
    public final float headRangeY;
    private final int numRows; //-.5f bis +.5f
    private final int numColumns; //-.5f bis +.5f
    private final int offsetScale;
    public final int sizeX;
    public final int sizeY;
    public final int size;
    //final protected float[] startGrayValues; 
    float xOffset;
    float yOffset;
    public float grayValueScaling;
    public boolean invert = false;
    public boolean standAlone = true;
    boolean jitteringActive = false;
    java.util.Timer jitterTimer;
    public float[] startPosition = new float[2];
    public float jitterAmplitude = .0133f;
    public float jitterFreqHz = 3.5f;
    public boolean jitterEnabled = false;

    protected Freme<Float> freme = null;
    CaptureImage capture = null;
    public boolean gettingImage = false;
    FilterChain filterChain = null;
    Head6DOF_ServoController headControl = null;

    public ImageCreator(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(new RotateFilter(chip));
        filterChain.add(new BackgroundActivityFilter(chip));
        headControl = new Head6DOF_ServoController(chip);
        // headControl.getSupport().addPropertyChangeListener(Head6DOF_ServoController_robothead6DOF.Message.PanTiltSet.name(), this);
        filterChain.add(headControl);
        setEnclosedFilterChain(filterChain);
        this.chip = chip;
        final String jitt = "Jitter Options";
        setPropertyTooltip(jitt, "jitterAmplitude", "the amplitude of the jitter; higher value results in larger circle");
        setPropertyTooltip(jitt, "jitterFreqHz", "defines the jitter frequency");
        setPropertyTooltip("Reset", "resets the image frame and fills it with the initial gray value again");
        setPropertyTooltip("StartCaptureImage", "starts the rasterscan and the paiting on the image frame");
        setPropertyTooltip("ToggleJittering", "toggles between constant jitter motion of the Eyes or no motion");
        setPropertyTooltip("StopCaptureImage", "stops the rasterscan and the paiting on the image frame");
        setPropertyTooltip("standAlone", "indicates if this filter is standalone or called by the ITDImageCreator filter");
        headRangeX = headControl.getHEAD_PAN_LIMIT();
        headRangeY = headControl.getHEAD_TILT_LIMIT();
        eyeRangeX = headControl.getEYE_PAN_LIMIT();
        eyeRangeY = headControl.getEYE_TILT_LIMIT();
        stepSize = .01f;
        numRows = (int) ((2 * headRangeY) / stepSize);
        numColumns = (int) ((2 * headRangeX) / stepSize);
        offsetScale = (int) (1f * (stepSize * 100));
        sizeX = (int) (128 + (numColumns * offsetScale) + (((2 * eyeRangeX) / stepSize) * offsetScale));
        sizeY = (int) (128 + (numRows * offsetScale) + (((2 * eyeRangeY) / stepSize) * offsetScale));
        size = sizeX * sizeY;
        grayValueScaling = 0.001f;
     //   freme = new Freme<Float>(getSizeX(), getSizeY());
            
        //Arrays.fill(startGrayValues, .5f);
    }

    @Override
    public synchronized final void resetFilter() {
        rgbValues = null;
        freme = null;
    }

    @Override
    public void update(Observable o, Object arg) {
    }

    @Override
    public final void initFilter() {
        resetFilter();
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        out = filterChain.filterPacket(in);
        if (out == null) {
            return null;
        }
        checkFreme();
        for (Object ein : out) {
            if (isGettingImage()) {
                PolarityEvent e = (PolarityEvent) ein;
                int x = (int) (e.x + getxOffset());  //getxOffset defines the x position of the pixel in the global picture
                int y = (int) (e.y + getyOffset());  //getyOffset defines the y position of the pixel in the global picture
                int idx = getIndex(x, y);
                boolean isOn = e.getPolaritySignum() > 0;
                if (invert == true) {
                    isOn = !isOn;
                }
                float value = get(idx);
                if (isOn) {
                    set(idx, value + getGrayValueScaling());
                } else {
                    set(idx, value - getGrayValueScaling());
                }
            }
        }
        if (isGettingImage()) {
            repaintFreme();
        }
        return out;
    }

    @Override
    public int getIndex(int x, int y) {
        return (y * getSizeX() + x);
    }

    /**
     * Subclasses implement this method to ensure that the map has the right
     * size and is not empty.
     */
    @Override
    public void checkDisplay() {
        if (rgbValues == null || rgbValues.length != 3 * getSize()) {
            rgbValues = new float[3 * getSize()];
        }
        if (frame == null || display == null || display.getSizeX() != getSizeX() || display.getSizeY() != getSizeY()) {
            display = ImageDisplay.createOpenGLCanvas(); // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
            display.setImageSize(getSizeX(), getSizeY()); // set dimensions of image      

            frame = new JFrame(getClass().getSimpleName());  // make a JFrame to hold it
            frame.setPreferredSize(new Dimension(getSizeX() * 4, getSizeY() * 4));  // set the window size
            frame.getContentPane().add(display, BorderLayout.CENTER); // add the GLCanvas to the center of the window
            frame.pack(); // otherwise it wont fill up the display
        }

        if (this.isFilterEnabled() && this.isDisplayFreme() && !frame.isVisible()) {
            frame.setVisible(true);
        }

        if ((!this.isFilterEnabled() || !this.isDisplayFreme()) && frame.isVisible()) {
            frame.setVisible(false);
        }
    }

    @Override
    public void setRGB(int idx) {
        float value = freme.get(idx);
        int nIdx = 3 * idx;
        rgbValues[nIdx++] = value;
        rgbValues[nIdx++] = value;
        rgbValues[nIdx] = value;
    }

    @Override
    public void checkFreme() {
        checkDisplay();
        if (freme == null || freme.size() != getSize()) {
            freme = new Freme<Float>(getSizeX(), getSizeY());
            freme.fill(.5f);
            Arrays.fill(rgbValues, .5f);
            repaintFreme();
        }
    }

    public float get(int idx) {
        return freme.get(idx);
    }

    public void set(int idx, float value) {
        if (value > 1f) {
            value = 1f;
        }
        if (value < 0f) {
            value = 0f;
        }
        freme.rangeCheck(idx);
        freme.set(idx, value);
        setRGB(idx);
    }

    @Override
    public Freme<Float> getFreme() {
        return freme;
    }

    public void doReset() throws HardwareInterfaceException, IOException {
        checkFreme();
        checkDisplay();
        freme.fill(.5f);
        Arrays.fill(rgbValues, .5f);
    }

    public void doStartCaptureImage() {
        if (isGettingImage() == false) {
            checkFreme();
            checkDisplay();
            try {
                freme.fill(.5f);
                headControl.setHeadDirection(currentColumn * stepSize, currentRow * stepSize);
                headControl.setVergence(.0f);
                headControl.setEyeGazeDirection(.0f, .0f);
                if (isStandAlone() == true) {
                    capture = new CaptureImage();
                    capture.start(); // starts the thread
                }
                gettingImage = true;
            } catch (HardwareInterfaceException | IOException ex) {
                log.severe("can not set robothead direction: " + ex.toString());
            }
        } else {
            log.info("CaptureImage already active");
        }
    }

    public void doToggleJittering() {
        if (isJitteringActive() != true) {
            setJitteringActive(true);
        } else {
            setJitteringActive(false);
        }
    }

    public void doStopCaptureImage() {
        if (isGettingImage() == true) {
            capture.stopThread();
        } else {
            log.info("no active CaptureImage Thread");
        }
    }

    /**
     * @return the invert
     */
    boolean isInvert() {
        return invert;
    }

    /**
     * @param invert the invert to set
     */
    public void setInvert(boolean invert) {
        this.invert = invert;
    }

    /**
     * @return the xOffset
     */
    float getxOffset() {
        return xOffset;
    }

    /**
     * @param xOffset the xOffset to set
     */
    void setxOffset(float head, float eyes) {
        this.xOffset = ((head + getHeadRangeX() + eyes + getEyeRangeX()) * 100);
    }

    /**
     * @return the yOffset
     */
    float getyOffset() {
        return yOffset;
    }

    /**
     * @param yOffset the yOffset to set
     */
    void setyOffset(float head, float eyes) {
        this.yOffset = ((head + getHeadRangeY() + eyes + getEyeRangeY()) * 100);
    }

    /**
     * @return the eyeRangeX
     */
    public float getEyeRangeX() {
        return eyeRangeX;
    }

    /**
     * @return the eyeRangeY
     */
    public float getEyeRangeY() {
        return eyeRangeY;
    }

    /**
     * @return the headRangeX
     */
    public float getHeadRangeX() {
        return headRangeX;
    }

    /**
     * @return the headRangeY
     */
    public float getHeadRangeY() {
        return headRangeY;
    }

    /**
     * @return the sizeX
     */
    public int getSizeX() {
        return sizeX;
    }

    /**
     * @return the sizeY
     */
    public int getSizeY() {
        return sizeY;
    }

    /**
     * @return the size
     */
    public int getSize() {
        return size;
    }

    /**
     * @return the grayValueScaling
     */
    public float getGrayValueScaling() {
        return grayValueScaling;
    }

    /**
     * @return the standAlone
     */
    public boolean isStandAlone() {
        return standAlone;
    }

    /**
     * @param standAlone the standAlone to set
     */
    public void setStandAlone(boolean standAlone) {
        this.standAlone = standAlone;
    }

    /**
     * @return the gettingImage
     */
    public boolean isGettingImage() {
        return gettingImage;
    }

    private class JittererTask extends TimerTask {

        long startTime = System.currentTimeMillis();

        JittererTask() {
            super();
        }

        @Override
        public void run() {
            long t = System.currentTimeMillis() - startTime;
            double phase = Math.PI * 2 * (double) t / 1000 * jitterFreqHz;
            float dx = (float) (jitterAmplitude * Math.sin(phase));
            float dy = (float) (jitterAmplitude * Math.cos(phase));
            try {
                headControl.setEyeGazeDirection(startPosition[0] + dx, startPosition[1] + dy);
                if (headControl.gazeDirection.getEyeDirection().getX() + dx - headControl.gazeDirection.getEyeDirection().getX() < 0 || headControl.gazeDirection.getEyeDirection().getY() + dy - headControl.gazeDirection.getEyeDirection().getY() < 0) {
                    setInvert(true);
                    setxOffset((float) headControl.getGazeDirection().getHeadDirection().getX(), (float) headControl.getGazeDirection().getEyeDirection().getX());
                    setyOffset((float) headControl.getGazeDirection().getHeadDirection().getY(), (float) headControl.getGazeDirection().getEyeDirection().getY());
                } else {
                    setInvert(false);
                    setxOffset((float) headControl.getGazeDirection().getHeadDirection().getX(), (float) headControl.getGazeDirection().getEyeDirection().getX());
                    setyOffset((float) headControl.getGazeDirection().getHeadDirection().getY(), (float) headControl.getGazeDirection().getEyeDirection().getY());
                }
            } catch (HardwareInterfaceException | IOException ex) {
                log.severe(ex.toString());
            }
        }
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterFreqHz--">
    public float getJitterFreqHz() {
        return jitterFreqHz;
    }

    /**
     * Sets the frequency of the jitter.
     *
     * @param jitterFreqHz in Hz.
     */
    public void setJitterFreqHz(float jitterFreqHz) {
        this.jitterFreqHz = jitterFreqHz;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterAmplitude--">
    public float getJitterAmplitude() {
        return jitterAmplitude;
    }

    /**
     * Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt
     * during jittering
     *
     * @param jitterAmplitude the amplitude
     */
    public void setJitterAmplitude(float jitterAmplitude) {
        this.jitterAmplitude = jitterAmplitude;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter for JitteringAcitve">
    /**
     * @return the jitteringActive
     */
    boolean isJitteringActive() {
        return jitteringActive;
    }

    /**
     * @param jitteringActive the jitteringActive to set
     */
    void setJitteringActive(boolean jitteringActive) {
        if (jitteringActive == true) {
            setStandAlone(false);
            jitterTimer = new java.util.Timer();
            // Repeat the JitterTask without delay and with 20ms between executions
            startPosition[0] = (float) headControl.gazeDirection.getEyeDirection().getX();
            startPosition[1] = (float) headControl.gazeDirection.getEyeDirection().getY();
            jitterTimer.scheduleAtFixedRate(new JittererTask(), 0, 20);
        } else {
            jitterTimer.cancel();
            jitterTimer = null;
        }
        this.jitteringActive = jitteringActive;
    }// </editor-fold>

    private class CaptureImage extends Thread {

        CaptureImage() {
            setName("CaptureImage");
        }

        volatile boolean stop = false;

        public void stopThread() {
            stop = true;
            //freme = null;
            interrupt();
        }

        @Override
        public void run() {
            for (; currentRow < numRows;) {
                if (currentColumn <= numColumns) {
                    try {
                        headControl.setHeadDirection(-getHeadRangeX() + (currentColumn * stepSize), -getHeadRangeY() + (currentRow * stepSize));   //step = 0.16 == 20px; stepSize = 0.02 == 2.5px
                        //columnOffset = currentColumn * offsetScale;
                        setxOffset((float) headControl.getGazeDirection().getHeadDirection().getX(), (float) headControl.getGazeDirection().getEyeDirection().getX());
                        setyOffset((float) headControl.getGazeDirection().getHeadDirection().getY(), (float) headControl.getGazeDirection().getEyeDirection().getY());
                        Thread.sleep(12);
                    } catch (HardwareInterfaceException | IOException | InterruptedException ex) {
                        log.severe(ex.toString());
                    }
                    currentColumn = currentColumn + 1;
                } else {
                    currentRow = currentRow + 1;
                    //rowOffset = currentRow * offsetScale;
                    currentColumn = 0;
                    try {
                        gettingImage = false;
                        headControl.setHeadDirection(-getHeadRangeX() + (currentColumn * stepSize), -getHeadRangeY() + (currentRow * stepSize));
                        Thread.sleep(1500);
                        gettingImage = true;
                    } catch (HardwareInterfaceException | IOException | InterruptedException ex) {
                        log.severe(ex.toString());
                    }
                }
            }
            gettingImage = false;
            stopThread();
        }
    }
}
