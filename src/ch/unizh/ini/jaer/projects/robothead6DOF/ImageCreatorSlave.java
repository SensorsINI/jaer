/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.*;
import java.util.ArrayList;
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
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 *
 * @author philipp
 */
public class ImageCreatorSlave extends FremeExtractor implements Observer {

    protected static final Logger log = Logger.getLogger("ImageCreator");
    // pixel allocation after movement
    public int sizeX;
    public int sizeY;
    public int size;
    float xOffset;
    float yOffset;
    public float grayValueScaling;
    public boolean invert = false;
    boolean imageCreatorAlive = false;

    protected Freme<Float> freme = null;
    static public ImageCreator imageCreator = null;
    public boolean gettingImage = false;
    FilterChain filterChain = null;

    public ImageCreatorSlave(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(new RotateFilter(chip));
        filterChain.add(new BackgroundActivityFilter(chip));
        // headControl.getSupport().addPropertyChangeListener(Head6DOF_ServoController_robothead6DOF.Message.PanTiltSet.name(), this);
        setEnclosedFilterChain(filterChain);
        this.chip = chip;
        setPropertyTooltip("Reset", "resets the image frame and fills it with the initial gray value again");
        setPropertyTooltip("ConnectToMaster", "tries to find an ImageCreator filter to connect to");
        setPropertyTooltip("imageCreatorAlive", "indicates if this filter is connected to an ImageCreator filter");
        //this.setFilterEnabled(true);
    }

    public void doConnectToMaster() {
        setImageCreatorAlive(true);
        sizeX = imageCreator.getSizeX();
        sizeY = imageCreator.getSizeY();
        size = imageCreator.getSize();
        grayValueScaling = imageCreator.getGrayValueScaling();
       // freme = new Freme<Float>(sizeX, sizeY);
        checkFreme();
        //checkDisplay();
        //Arrays.fill(rgbValues, .5f);
        //freme.fill(.5f);
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
        for (Object ein : out) {
            if (imageCreatorAlive == true) {
                checkFreme();
            }
            if (imageCreatorAlive == true && imageCreator.isGettingImage()) {
                PolarityEvent e = (PolarityEvent) ein;
                int x = (int) (e.x + imageCreator.getxOffset());  //getxOffset defines the x position of the pixel in the global picture
                int y = (int) (e.y + imageCreator.getyOffset());  //getyOffset defines the y position of the pixel in the global picture
                int idx = getIndex(x, y);
                boolean isOn = e.getPolaritySignum() > 0;
                if (imageCreator.isInvert() == true) {
                    isOn = !isOn;
                }
                float value = get(idx);
                if (isOn) {
                    set(idx, value + imageCreator.getGrayValueScaling());
                } else {
                    set(idx, value - imageCreator.getGrayValueScaling());
                }
            }
        }
        if (imageCreatorAlive == true && imageCreator.isGettingImage()) {
            repaintFreme();
        }
        return out;
    }
    
    public void doReset() throws HardwareInterfaceException, IOException {
        checkFreme();
        checkDisplay();
        freme.fill(.5f);
        Arrays.fill(rgbValues, .5f);
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

    public static ImageCreator findExistingImageCreator(AEViewer myViewer) {
        ArrayList<AEViewer> viewers = myViewer.getJaerViewer().getViewers();
        for (AEViewer v : viewers) {
            if (imageCreator == null) {
                AEChip c = v.getChip();
                FilterChain fc = c.getFilterChain();
                //Check for ImageCreator Filter:
                imageCreator = (ImageCreator) fc.findFilter(ImageCreator.class);
            } else {
                return imageCreator;
            }
        }
        return imageCreator;
    }

    /**
     * @return the gettingImage
     */
    public boolean isGettingImage() {
        return gettingImage;
    }

    // <editor-fold defaultstate="collapsed" desc="is/setter for imageCreatorAlive">
    /**
     * @return the imageCreatorAlive
     */
    public boolean isImageCreatorAlive() {
        return imageCreatorAlive;
    }

    /**
     * @param imageCreatorAlive the imageCreatorAlive to set
     */
    public void setImageCreatorAlive(boolean imageCreatorAlive) {
        boolean old = this.imageCreatorAlive;
        imageCreator = findExistingImageCreator(chip.getAeViewer());
        if (imageCreator != null && imageCreator.isFilterEnabled()) {
            this.imageCreatorAlive = true;
        } else {
            log.info("can not find ImageCreator thread; please activate ImageCreator thread for DVS");
            this.imageCreatorAlive = false;
        }
        getSupport().firePropertyChange("imageCreatorAlive", old, this.imageCreatorAlive);
    } // </editor-fold>
}
