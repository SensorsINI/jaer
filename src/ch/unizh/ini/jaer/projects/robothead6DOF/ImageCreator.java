/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
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
 * performs a raster scan with the 6DOF robot head and paints a histogram image
 * of the incoming spikes
 *
 * @author philipp
 */
public class ImageCreator extends FremeExtractor implements Observer {

    protected static final Logger log = Logger.getLogger("ImageCreator");
    int currentRow = 0;
    int currentColumn = 0;
    int warningCount = 10;

    // pixel allocation after movement
    private float columnStepSize;   //amount by which the head pans to the right on each step
    private float rowStepSize;      //amount by which the head tilts up on each step
    public float eyeRangeX;
    public float eyeRangeY;
    public float headRangeX;
    public float headRangeY;
    private int numRows; //number of rows that the head tilts upwards
    private int numColumns; //number of rows that the head pans to the right
    public float offsetScaleHeadPan; //offset for each movement the head performs
    public float offsetScaleHeadTilt;
    public float offsetScaleEyePan;
    public float offsetScaleEyeTilt;
    public final int sizeX;     //histgram image width (in pixels)
    public final int sizeY;     //histogram image height (in pixels)
    public final int size;      //number of all pixels in the histogram image
    float xOffset;              //actual x-offset of the current head position (headPositionX * offsetScaleHeadPan)
    float yOffset;              //actual y-offset of the current head position (headPositiony * offsetScaleHeadTilt)
    public float grayValueScaling; //factor by which each events changes the intensity value of a pixel
    public boolean invert = false;
    public boolean standAlone = true;   //defines if the head is controlled by other filter (ITD filter)
    boolean jitteringActive = false;
    java.util.Timer jitterTimer;
    public float[] jitterStartPosition = new float[2];
    public float jitterAmplitude = .0133f;
    public float jitterFreqHz = 3.5f;
    public boolean jitterEnabled = false;
    java.util.Timer savingImageTimer;
    public boolean savingImage;

    long limitedJitterTime;  //time in ms when the jitter task starts

    protected Freme<Float> freme = null;
    CaptureImage capture = null;
    CenterSweep sweep = null;
    public boolean gettingImage = false;
    FilterChain filterChain = null;
    Head6DOF_ServoController headControl = null;

    public ImageCreator(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(new RotateFilter(chip));
        filterChain.add(new BackgroundActivityFilter(chip));
        headControl = new Head6DOF_ServoController(chip);
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
        setPropertyTooltip("ToggleSavingImage", "saves the current image created by the imageCreator");
        setPropertyTooltip("StartCaptureJitteringImage", "starts jittering and painting the image");
        setPropertyTooltip("StartCenterSweep", "perform a single center sweep and paint image");
        setPropertyTooltip("gettingImage", "signals if current incoming events are used to change the image");
        setPropertyTooltip("savingImage", "indicates if constant image saving is active");
        headRangeX = headControl.getHEAD_PAN_LIMIT();
        headRangeY = headControl.getHEAD_TILT_LIMIT();
        eyeRangeX = headControl.getEYE_PAN_LIMIT();
        eyeRangeY = headControl.getEYE_TILT_LIMIT();
        columnStepSize = .01f;      //for each new column move head to the right by .01f (.01f = 0.5deg)
        rowStepSize = .05f;         //for each new row move head upwards by .05f (.01f = 0.5deg)
        numRows = (int) (((2 * headRangeY) / rowStepSize));  //total number of rows
        numColumns = (int) (((2 * headRangeX) / columnStepSize));  //total number of columns
        offsetScaleHeadPan = 2f; //average offsetscale for size calculation
        offsetScaleHeadTilt = 2f;
        offsetScaleEyePan = 2f;
        offsetScaleEyeTilt = 2f;
        //sizeX = 628;
        //sizeY = 418;
        sizeX = (int) (128 + (2 * headRangeX * 100 * offsetScaleHeadPan) + (2 * eyeRangeX * 100 * offsetScaleEyePan)); //calculates the width of the histogram image
        sizeY = (int) (128 + (2 * headRangeY * 100 * offsetScaleHeadTilt) + (2 * eyeRangeY * 100 * offsetScaleEyeTilt));
        size = sizeX * sizeY; //calculates the total number of pixels in the histogram image
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
        checkFreme();   //check that the freme is alive
        for (Object ein : out) {
            if (isGettingImage()) {  //if getting Image is activated, paint the picture
                PolarityEvent e = (PolarityEvent) ein;
                int x = (int) (e.x + getxOffset());  //getxOffset defines the x position of the pixel in the global picture
                int y = (int) (e.y + getyOffset());  //getyOffset defines the y position of the pixel in the global picture
                int idx = getIndex(x, y);  //gets the index in the histogram image of the selected pixel
                boolean isOn = e.getPolaritySignum() > 0;
                if (invert == true) {
                    isOn = !isOn;
                }
                float value = get(idx);  //gets current intensity value of the selected pixel
                if (isOn) {
                    set(idx, value + getGrayValueScaling());    //change intensity value more towards white
                } else {
                    set(idx, value - getGrayValueScaling());    //change intensity value more towards black
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
        if (frame == null || display == null || display.getWidth() != getSizeX() || display.getHeight() != getSizeY()) {
            display = ImageDisplay.createOpenGLCanvas(); // make a new ImageDisplay GLCanvas with default OpenGL capabilities
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
    public void setRGB(int idx) {       //sets all color channels of one pixel (idx) to the same value
        float value = freme.get(idx);
        int nIdx = 3 * idx;
        rgbValues[nIdx++] = value;
        rgbValues[nIdx++] = value;
        rgbValues[nIdx] = value;
    }

    @Override
    public void checkFreme() {
        checkDisplay();
        if (freme == null || freme.size() != getSize()) {       //if there is no histogram image or it has the wrong size
            freme = new Freme<Float>(getSizeX(), getSizeY());   //create new freme with correct size
            freme.fill(.5f);                                    //fill it with the color value .5f
            Arrays.fill(rgbValues, .5f);                        //fill the rgbValues array with .5f
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

    public void doReset() throws HardwareInterfaceException, IOException {   //resets the histogram image to evenly gray
        checkFreme();
        checkDisplay();
        freme.fill(.5f);
        Arrays.fill(rgbValues, .5f);
    }

    public void doToggleSavingImage() {  //toggles the image saving method
        if (isSavingImage() != true) {
            setSavingImage(true);
        } else {
            setSavingImage(false);
        }
    }

    public void doStartCaptureImage() {
        if (isGettingImage() == false) {
            checkFreme();
            checkDisplay();
            freme.fill(.5f);
            Arrays.fill(rgbValues, .5f);
            if (isStandAlone() == true) {
                try {
                    currentColumn = 0;
                    setGrayValueScaling(0.001f * rowStepSize * 100); //sets the gray value scaling based on number of rows
                    headControl.setHeadDirection(-getHeadRangeX() + (currentColumn * columnStepSize), -getHeadRangeY() + (currentRow * rowStepSize));
                    headControl.setVergence(.0f);  //infinity vergence
                    headControl.setEyeGazeDirection(.0f, .0f); //center gaze
                    capture = new CaptureImage();
                    capture.start(); // starts the thread
                } catch (HardwareInterfaceException | IOException ex) {
                    Logger.getLogger(ImageCreator.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                doStartCaptureJitteringImage(); //start capturing an image without raster scanning
            }
        } else {
            log.info("CaptureImage already active");
        }
    }

    public void doStartCenterSweep() {  //performs a simple sweep from left to right in the in the azimuthal position
        if (isGettingImage() == false) {
            checkFreme();
            checkDisplay();
            freme.fill(.5f);
            Arrays.fill(rgbValues, .5f);
            if (isStandAlone() == true) {
                try {
                    currentColumn = 0;
                    setGrayValueScaling(0.001f * 20);
                    headControl.setHeadDirection(-getHeadRangeX() + (currentColumn * columnStepSize), 0f);
                    headControl.setVergence(.0f);
                    headControl.setEyeGazeDirection(.0f, .0f);
                    sweep = new CenterSweep();
                    sweep.start(); // starts the sweep
                } catch (HardwareInterfaceException | IOException ex) {
                    Logger.getLogger(ImageCreator.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } else {
            log.info("CaptureImage already active");
        }
    }

    public void doStartCaptureJitteringImage() {
        if (isGettingImage() == false) {
            checkFreme();
            checkDisplay();
            freme.fill(.5f);
            Arrays.fill(rgbValues, .5f);
            setGrayValueScaling(0.05f);
            setGettingImage(true);
        } else {
            log.info("CaptureImage already active");
        }
    }

    public void doToggleJittering() {  //toggles between active and inacctive jitter
        if (isJitteringActive() != true) {
            setJitteringActive(true);
        } else {
            setJitteringActive(false);
        }
    }

    public void doStopCaptureImage() {  //stops the acquisition of spikes to the histogram image
        if (isGettingImage() == true) {
            if (capture != null && capture.isAlive() == true) {
                capture.stopThread();
            }
            setGettingImage(false);
        } else {
            log.info("no active CaptureImage Thread");
        }
    }

    // <editor-fold defaultstate="collapsed" desc="is/setter for invert">
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
    }// </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for xOffset">
    /**
     * @return the xOffset
     */
    float getxOffset() {
        return xOffset;
    }

    /**
     * @param xOffset the xOffset to set
     */
    void setxOffset(float headX, float eyesX) {
        this.xOffset = (((headX + getHeadRangeX()) * getOffsetScaleHeadPan() * 100) + ((eyesX + getEyeRangeX()) * getOffsetScaleEyePan() * 100));
    }// </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for yOffset">
    /**
     * @return the yOffset
     */
    float getyOffset() {
        return yOffset;
    }

    /**
     * @param yOffset the yOffset to set
     */
    void setyOffset(float headY, float eyesY) {
        this.yOffset = (((headY + getHeadRangeY()) * getOffsetScaleHeadTilt() * 100) + ((eyesY + getEyeRangeY()) * getOffsetScaleEyeTilt() * 100));
    }// </editor-fold>

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

    // <editor-fold defaultstate="collapsed" desc="is/setter for standAlone">
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
    }//</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter for gettingImage">
    /**
     * @return the gettingImage
     */
    public boolean isGettingImage() {
        return gettingImage;
    }

    /**
     * @param gettingImage the gettingImage to set
     */
    public void setGettingImage(boolean gettingImage) {
        boolean oldgettingImage = this.gettingImage;
        this.gettingImage = gettingImage;
        support.firePropertyChange("gettingImage", oldgettingImage, gettingImage);
    }//</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for grayValueScaling">
    /**
     * @return the grayValueScaling
     */
    public float getGrayValueScaling() {
        return this.grayValueScaling;
    }

    /**
     * @param grayValueScaling the grayValueScaling to set
     */
    public void setGrayValueScaling(float grayValueScaling) {
        float oldgrayValueScaling = this.grayValueScaling;
        this.grayValueScaling = grayValueScaling;
        support.firePropertyChange("grayValueScaling", oldgrayValueScaling, grayValueScaling);
    }//</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for offsetScaleHeadPan">
    /**
     * @return the offsetScaleHeadPan
     */
    public float getOffsetScaleHeadPan() {
        return this.offsetScaleHeadPan;
    }

    /**
     * @param offsetScaleHeadPan the offsetScaleHeadPan to set
     */
    public void setOffsetScaleHeadPan(float offsetScaleHeadPan) {
        float oldoffsetScaleHeadPan = this.offsetScaleHeadPan;
        this.offsetScaleHeadPan = offsetScaleHeadPan;
        support.firePropertyChange("offsetScaleHeadPan", oldoffsetScaleHeadPan, offsetScaleHeadPan);
    }//</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for offsetScaleHeadTilt">
    /**
     * @return the offsetScaleHeadTilt
     */
    public float getOffsetScaleHeadTilt() {
        return this.offsetScaleHeadTilt;
    }

    /**
     * @param offsetScaleHeadTilt the offsetScaleHeadTilt to set
     */
    public void setOffsetScaleHeadTilt(float offsetScaleHeadTilt) {
        float oldoffsetScaleHeadTilt = this.offsetScaleHeadTilt;
        this.offsetScaleHeadTilt = offsetScaleHeadTilt;
        support.firePropertyChange("offsetScaleHeadTilt", oldoffsetScaleHeadTilt, offsetScaleHeadTilt);
    }//</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for offsetScaleEyePan">
    /**
     * @return the offsetScaleEyePan
     */
    public float getOffsetScaleEyePan() {
        return this.offsetScaleEyePan;
    }

    /**
     * @param offsetScaleEyePan the offsetScaleEyePan to set
     */
    public void setOffsetScaleEyePan(float offsetScaleEyePan) {
        float oldoffsetScaleEyePan = this.offsetScaleEyePan;
        this.offsetScaleEyePan = offsetScaleEyePan;
        support.firePropertyChange("offsetScaleEyePan", oldoffsetScaleEyePan, offsetScaleEyePan);
    }//</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for offsetScaleEyeTilt">
    /**
     * @return the offsetScaleEyeTilt
     */
    public float getOffsetScaleEyeTilt() {
        return this.offsetScaleEyeTilt;
    }

    /**
     * @param offsetScaleEyeTilt the offsetScaleEyeTilt to set
     */
    public void setOffsetScaleEyeTilt(float offsetScaleEyeTilt) {
        float oldoffsetScaleEyeTilt = this.offsetScaleEyeTilt;
        this.offsetScaleEyeTilt = offsetScaleEyeTilt;
        support.firePropertyChange("offsetScaleEyeTilt", oldoffsetScaleEyeTilt, offsetScaleEyeTilt);
    }//</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter for savingImage">
    /**
     * @return the savingImage
     */
    public boolean isSavingImage() {
        return savingImage;
    }

    /**
     * @param savingImage the savingImage to set
     */
    public void setSavingImage(boolean savingImage) { //saves the histogram image every 5sec to a new file
        boolean oldsavingImage = this.savingImage;
        if (savingImage == true) {
            try {
                // Repeat the SavingImageTask without delay and with 5000ms between executions
                JFileChooser f = new JFileChooser();
                f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);  //choose file destinations once
                int state = f.showSaveDialog(null);
                if (state == JFileChooser.APPROVE_OPTION) {
                    savingImageTimer = new java.util.Timer();
                    savingImageTimer.scheduleAtFixedRate(new SaveImageTask(f.getSelectedFile()), 0, 5000); //define the saving rate (5000ms)
                }
            } catch (HeadlessException e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
        } else {
            savingImageTimer.cancel();
            savingImageTimer = null;
        }
        this.savingImage = savingImage;
        support.firePropertyChange("savingImage", oldsavingImage, savingImage);
    }//</editor-fold>

    private class JittererTask extends TimerTask {

        long startTime = System.currentTimeMillis();

        JittererTask() {
            super();
        }

        @Override
        public void run() {
            if (System.currentTimeMillis() - limitedJitterTime < 960) {  //perform jitter for given time; one circle takes 320ms
                long t = System.currentTimeMillis() - startTime;
                double phase = Math.PI * 2 * (double) t / 1000 * jitterFreqHz;
                float dx = (float) (jitterAmplitude * Math.sin(phase));
                float dy = (float) (jitterAmplitude * Math.cos(phase));
                try {
                    headControl.setEyeGazeDirection(jitterStartPosition[0] + dx, jitterStartPosition[1] + dy);
                    setxOffset((float) headControl.getGazeDirection().getHeadDirection().getX(), (float) headControl.getGazeDirection().getEyeDirection().getX());
                    setyOffset((float) headControl.getGazeDirection().getHeadDirection().getY(), (float) headControl.getGazeDirection().getEyeDirection().getY());
                    if (dx < 0 || dy < 0) {
                        setInvert(true);
                    } else {
                        setInvert(false);
                    }
                } catch (HardwareInterfaceException | IOException ex) {
                    log.severe(ex.toString());
                }
            } else {
                doToggleJittering();
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
        boolean oldjitteringActive = this.jitteringActive;
        if (jitteringActive == true) {
            setStandAlone(false);
            setGrayValueScaling(0.05f);
            jitterTimer = new java.util.Timer();
            // Repeat the JitterTask without delay and with 20ms between executions
            jitterStartPosition[0] = (float) headControl.gazeDirection.getEyeDirection().getX();
            jitterStartPosition[1] = (float) headControl.gazeDirection.getEyeDirection().getY();
            limitedJitterTime = System.currentTimeMillis();
            jitterTimer.scheduleAtFixedRate(new JittererTask(), 0, 20);
        } else {
            jitterTimer.cancel();
            jitterTimer = null;
        }
        this.jitteringActive = jitteringActive;
        support.firePropertyChange("jitteringActive", oldjitteringActive, jitteringActive);
    }// </editor-fold>

    private class CaptureImage extends Thread {

        CaptureImage() {
            setName("CaptureImage");
        }

        volatile boolean stop = false;

        public void stopThread() {
            stop = true;
            interrupt();
        }

        @Override
        public void run() {
            while (!stop) {
                try {
                    Thread.sleep(1000);
                    setGettingImage(true);
                } catch (InterruptedException ex) {
                    log.severe(ex.toString());
                }
                for (; currentRow < numRows;) {
                    for (; currentColumn < numColumns; currentColumn++) {
                        if (headControl.lensFocalLengthMm == 8f) {   //detailed offset for 8mm lenses
                            setOffsetScaleHeadTilt(1.7f);
                            if (currentColumn < 18) {
                                setOffsetScaleHeadPan(2.05f);
                            } else if (currentColumn < 36) {
                                setOffsetScaleHeadPan(2.05f);
                            } else if (currentColumn < 54) {
                                setOffsetScaleHeadPan(2.0f);
                            } else if (currentColumn < 72) {
                                setOffsetScaleHeadPan(1.95f);
                            } else if (currentColumn < 90) {
                                setOffsetScaleHeadPan(1.9f);
                            } else if (currentColumn < 108) {
                                setOffsetScaleHeadPan(1.9f);
                            } else if (currentColumn < 126) {
                                setOffsetScaleHeadPan(1.85f);
                            } else if (currentColumn < 144) {
                                setOffsetScaleHeadPan(1.85f);
                            } else if (currentColumn < 162) {
                                setOffsetScaleHeadPan(1.8f);
                            } else if (currentColumn < 180) {
                                setOffsetScaleHeadPan(1.8f);
                            }
                        }
                        try {
                            headControl.setHeadDirection(-getHeadRangeX() + (currentColumn * columnStepSize), -getHeadRangeY() + (currentRow * rowStepSize));   //step = 0.16 == 20px; columnStepSize = 0.02 == 2.5px for 4.5mm
                            setxOffset((float) headControl.getGazeDirection().getHeadDirection().getX(), (float) headControl.getGazeDirection().getEyeDirection().getX());
                            setyOffset((float) headControl.getGazeDirection().getHeadDirection().getY(), (float) headControl.getGazeDirection().getEyeDirection().getY());
                            Thread.sleep(10);
                        } catch (HardwareInterfaceException | IOException | InterruptedException ex) {
                            log.severe(ex.toString());
                        }
                    }
                    setGettingImage(false);
                    currentRow = currentRow + 1;
                    currentColumn = 0;
                    try {
                        headControl.setHeadDirection(-getHeadRangeX() + (currentColumn * columnStepSize), -getHeadRangeY() + (currentRow * rowStepSize));
                        setxOffset((float) headControl.getGazeDirection().getHeadDirection().getX(), (float) headControl.getGazeDirection().getEyeDirection().getX());
                        setyOffset((float) headControl.getGazeDirection().getHeadDirection().getY(), (float) headControl.getGazeDirection().getEyeDirection().getY());
                        Thread.sleep(1500);
                        setGettingImage(true);
                    } catch (HardwareInterfaceException | IOException | InterruptedException ex) {
                        log.severe(ex.toString());
                    }
                }
                setGettingImage(false);
                stopThread();
            }
        }
    }

    private class CenterSweep extends Thread {  //performs one single sweep in the azimuthal position

        CenterSweep() {
            setName("CenterSweep");
        }

        volatile boolean stop = false;

        public void stopThread() {
            stop = true;
            interrupt();
        }

        @Override
        public void run() {
            while (!stop) {
                try {
                    Thread.sleep(1000);
                    setGettingImage(true);
                } catch (InterruptedException ex) {
                    log.severe(ex.toString());
                }
                for (; currentColumn < numColumns; currentColumn++) {
                    if (headControl.lensFocalLengthMm == 8f) {   //detailed offset for 8mm lenses
                        setOffsetScaleHeadTilt(1.7f);
                        if (currentColumn < 18) {
                            setOffsetScaleHeadPan(2.05f);
                        } else if (currentColumn < 36) {
                            setOffsetScaleHeadPan(2.05f);
                        } else if (currentColumn < 54) {
                            setOffsetScaleHeadPan(2.0f);
                        } else if (currentColumn < 72) {
                            setOffsetScaleHeadPan(1.95f);
                        } else if (currentColumn < 90) {
                            setOffsetScaleHeadPan(1.9f);
                        } else if (currentColumn < 108) {
                            setOffsetScaleHeadPan(1.9f);
                        } else if (currentColumn < 126) {
                            setOffsetScaleHeadPan(1.85f);
                        } else if (currentColumn < 144) {
                            setOffsetScaleHeadPan(1.85f);
                        } else if (currentColumn < 162) {
                            setOffsetScaleHeadPan(1.8f);
                        } else if (currentColumn < 180) {
                            setOffsetScaleHeadPan(1.8f);
                        }
                    }
                    try {
                        headControl.setHeadDirection(-getHeadRangeX() + (currentColumn * columnStepSize), 0f);   //step = 0.16 == 20px; columnStepSize = 0.02 == 2.5px for 4.5mm
                        setxOffset((float) headControl.getGazeDirection().getHeadDirection().getX(), (float) headControl.getGazeDirection().getEyeDirection().getX());
                        setyOffset((float) headControl.getGazeDirection().getHeadDirection().getY(), (float) headControl.getGazeDirection().getEyeDirection().getY());
                        Thread.sleep(10);
                    } catch (HardwareInterfaceException | IOException | InterruptedException ex) {
                        log.severe(ex.toString());
                    }
                }
            }
            setGettingImage(false);
            stopThread();
        }
    }

    private class SaveImageTask extends TimerTask { //save the created images at a constant rate

        long startTime = System.currentTimeMillis();
        File path;

        SaveImageTask(File path) {
            super();
            this.path = path;
        }

        @Override
        public void run() {
            BufferedImage img = new BufferedImage(display.getWidth(), display.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < display.getHeight(); y++) {
                for (int x = 0; x < display.getWidth(); x++) {
                    int idx = getIndex(x, y);
                    int nIdx = 3 * idx;
                    int r = ((int) (rgbValues[nIdx++] * 255)); // red component 0...255
                    int g = ((int) (rgbValues[nIdx++] * 255)); // green component 0...255
                    int b = ((int) (rgbValues[nIdx] * 255)); // blue component 0...255
                    int col = (r << 16) | (g << 8) | b;
                    img.setRGB(x, y, col);
                }
            }
            AffineTransform tx = AffineTransform.getScaleInstance(1, -1);  //alligns the image correctly
            tx.translate(0, -img.getHeight(null));
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            img = op.filter(img, null);
            SimpleDateFormat sdfDate = new SimpleDateFormat("_yyyy_MM_dd_HH_mm_ss");//dd/MM/yyyy
            Date now = new Date();
            String strDate = sdfDate.format(now);
            try {
                ImageIO.write(img, "png", new File(String.format("%s/ImageCreator%s.png", path, strDate)));
            } catch (IOException ex) {
                log.warning("can not save Image, Error: " + ex.toString());
            }
        }

    }
}
