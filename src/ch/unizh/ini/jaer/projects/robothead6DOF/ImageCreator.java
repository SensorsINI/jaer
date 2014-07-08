/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import org.ine.telluride.jaer.tell2011.head6axis.Head6DOF_ServoController;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.*;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
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

/**
 *
 * @author philipp
 */
public class ImageCreator extends FremeExtractor implements Observer {

    protected static final Logger log = Logger.getLogger("ImageCreator");
    int currentRow = getInt("currentRow", 0);
    int currentColumn = getInt("currentColumn", 0);
    int warningCount = 10;
    
    // pixel allocation after movement
    private final float step = .01f;
    final protected float startX = -.5f, startY = -.5f;
    final protected float endX = .5f, endY = .5f;
    private final int numRows = (int) ((endY - startY) / step); //-.5f bis +.5f
    private final int numColumns = (int) ((endX - startX) / step); //-.5f bis +.5f
    private final int offsetScale = (int) (1f * (step * 100));
    final protected int sizeX, sizeY, size;
    final protected float[] startGrayValues; 
    final protected int startXpixel = (int) (offsetScale * startX);
    final protected int startYpixel = (int) (offsetScale * startY);
    volatile int columnOffset = 0;
    volatile int rowOffset = 0;
    
    
    protected Freme<Float> freme;
    CaptureImage capture = null;
    boolean gettingImage = false;
    boolean capturing = true;
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
        sizeX = chip.getSizeX() + (numColumns * offsetScale);
        sizeY = chip.getSizeX() + (numRows * offsetScale);
        size = sizeX * sizeY;
        startGrayValues = new float[size];
        freme = new Freme<Float>(sizeX, sizeY);
        Arrays.fill(startGrayValues, .5f);
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
            PolarityEvent e = (PolarityEvent) ein;
            int x = e.x + columnOffset;
            int y = e.y + rowOffset;
            int idx = getIndex(x, y);
            boolean isOn = e.getPolaritySignum() > 0;
            if (gettingImage && capturing) {
                float value = get(idx);
                if (isOn) {
                    set(idx, value + 0.001f);
                } else {
                    set(idx, value - 0.001f);
                }
            }
        }
        if (gettingImage) {
            repaintFreme();
        }
        return out;
    }

    @Override
    public int getIndex(int x, int y) {
        return (y * sizeX + x);
    }

    /**
     * Subclasses implement this method to ensure that the map has the right
     * size and is not empty.
     */
    @Override
    public void checkDisplay() {
        if (rgbValues == null || rgbValues.length != 3 * size) {
            rgbValues = new float[3 * size];
        }
        if (frame == null || display == null || display.getSizeX() != sizeX || display.getSizeY() != sizeY) {
            display = ImageDisplay.createOpenGLCanvas(); // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
            display.setImageSize(sizeX, sizeY); // set dimensions of image      

            frame = new JFrame(getClass().getSimpleName());  // make a JFrame to hold it
            frame.setPreferredSize(new Dimension(sizeX * 4, sizeY * 4));  // set the window size
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
        if (freme == null || freme.size() != size) {
            freme = new Freme<Float>(sizeX, sizeY);
            freme.fill(.5f);
            Arrays.fill(rgbValues, .5f);
            //repaintFreme();
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
        headControl.setHeadDirection(0f, 0f);
        headControl.setVergence(0f);
        headControl.setEyeGazeDirection(0f, 0f);
    }

    public void doStartCaptureImage() {
        if (gettingImage == false) {
            checkFreme();
            checkDisplay();
            try {
                freme.fill(.5f);
                headControl.setHeadDirection(currentColumn * step, currentRow * step);
                headControl.setVergence(.0f);
                headControl.setEyeGazeDirection(.0f, .0f);
                capture = new CaptureImage();
                capture.start(); // starts the thread
                gettingImage = true;
            } catch (HardwareInterfaceException | IOException ex) {
                log.severe("can not set robothead direction: " + ex.toString());
            }
        } else {
            log.info("CaptureImage already active");
        }
    }

    public void doStopCaptureImage() {
        if (gettingImage == true) {
            capture.stopThread();
        } else {
            log.info("no active CaptureImage Thread");
        }
    }

    private class CaptureImage extends Thread {

        CaptureImage() {
            setName("CaptureImage");
        }

        volatile boolean stop = false;

        public void stopThread() {
            stop = true;
            freme = null;
            interrupt();
        }

        @Override
        public void run() {
            for (; currentRow < numRows;) {
                if (currentColumn <= numColumns) {
                    try {
                        headControl.setHeadDirection(startX + (currentColumn * step), startY + (currentRow * step));   //step = 0.16 == 20px; step = 0.02 == 2.5px
                        columnOffset = currentColumn * offsetScale;
                        Thread.sleep(12);
                    } catch (HardwareInterfaceException | IOException | InterruptedException ex) {
                        log.severe(ex.toString());
                    }
                    currentColumn = currentColumn + 1;
                } else {
                    currentRow = currentRow + 1;
                    rowOffset = currentRow * offsetScale;
                    currentColumn = 0;
                    try {
                        capturing = false;
                        headControl.setHeadDirection(startX + (currentColumn * step), startY + (currentRow * step));
                        Thread.sleep(500);
                        capturing = true;
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
