/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;
import java.util.TimerTask;
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
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Slave version of the ImageCreator for a second DVS128 camera; needs a
 * ImageCreator filter as master to work
 *
 * @author philipp
 */
public class ImageCreatorSlave extends FremeExtractor implements Observer {

    protected static final Logger log = Logger.getLogger("ImageCreator");
    public int sizeX;
    public int sizeY;
    public int size;
    float xOffset;
    float yOffset;
    public float grayValueScaling;
    public boolean invert = false;
    boolean imageCreatorAlive = false;
    java.util.Timer savingImageTimer;
    public boolean savingImage;

    protected Freme<Float> freme = null;
    static public ImageCreator imageCreator = null;
    public boolean gettingImage = false;
    FilterChain filterChain = null;

    public ImageCreatorSlave(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(new RotateFilter(chip));
        filterChain.add(new BackgroundActivityFilter(chip));
        setEnclosedFilterChain(filterChain);
        this.chip = chip;
        setPropertyTooltip("Reset", "resets the image frame and fills it with the initial gray value again");
        setPropertyTooltip("ConnectToMaster", "tries to find an ImageCreator filter to connect to");
        setPropertyTooltip("imageCreatorAlive", "indicates if this filter is connected to an ImageCreator filter");
        setPropertyTooltip("saveImage", "saves the current image created by the imageCreator");
        setPropertyTooltip("ToggleSaveImages", "starts saving the histogram images");
        setPropertyTooltip("savingImage", "indicates if constant image saving is active");
    }

    public void doConnectToMaster() {   //find a ImageCreator filter and use it as a master, there should be one master filter. Get all required variables from the master.
        setImageCreatorAlive(true);
        sizeX = imageCreator.getSizeX();
        sizeY = imageCreator.getSizeY();
        size = imageCreator.getSize();
        grayValueScaling = imageCreator.getGrayValueScaling();
        checkFreme();
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
        if (frame == null || display == null || display.getWidth() != sizeX || display.getHeight() != sizeY) {
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

    public static ImageCreator findExistingImageCreator(AEViewer myViewer) {   //find master ImageCreator
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

    public void doToggleSavingImage() {
        if (isSavingImage() != true) {
            setSavingImage(true);
        } else {
            setSavingImage(false);
        }
    }

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
    public void setSavingImage(boolean savingImage) {
        if (savingImage == true) {
            try {
                // Repeat the SavingImageTask without delay and with 5000ms between executions
                JFileChooser f = new JFileChooser();
                f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int state = f.showSaveDialog(null);
                if (state == JFileChooser.APPROVE_OPTION) {
                    savingImageTimer = new java.util.Timer();
                    savingImageTimer.scheduleAtFixedRate(new SaveImageTask(f.getSelectedFile()), 0, 5000);
                }
            } catch (HeadlessException e) {//Catch exception if any
                log.warning("Error: " + e.getMessage());
            }
        } else {
            savingImageTimer.cancel();
            savingImageTimer = null;
        }
        this.savingImage = savingImage;
    }//</editor-fold>

    private class SaveImageTask extends TimerTask {

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
            AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
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
