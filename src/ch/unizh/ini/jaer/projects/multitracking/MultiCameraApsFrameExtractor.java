/*
 * Copyright (C) 2018 Gemma.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.multitracking;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import eu.seebetter.ini.chips.DavisChip;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.JAERViewer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventprocessing.EventFilter;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.ImageDisplay;
import org.apache.commons.io.FilenameUtils;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.opencv_core;
import static org.bytedeco.javacpp.opencv_core.CV_8U;
import static org.bytedeco.javacpp.opencv_core.CV_8UC3;
import static org.bytedeco.javacpp.opencv_imgproc.CV_GRAY2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;

/**
 * Extracts CIS APS frames from Syncronized Multiple DAVIS sensors. Use
 * <ul>
 * <li>hasNewFrame() to check whether a new frame is available
 * <li>getDisplayBuffer() to get a clone of the latest raw pixel values
 * <li>getNewFrame() to get the latest double buffer of displayed values
 * </ul>
 *
 * @author Gemma
 */


@Description("Method to acquire a frame from a stream of APS sample events")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class MultiCameraApsFrameExtractor extends EventFilter2D
        implements Observer /* Observer needed to get change events on chip construction */ {

    //VARIABLES////
    private AEViewer aevi;
    private JAERViewer jaevi;
    ArrayList<AEViewer> arrayOfAEvi;
    private int numOfAEviewers;
    private FilterChain filterchain;
    Vector<AEChip> chips = new Vector<>();
    Vector<ApsFrameExtractor> fraext = new Vector<>(numOfAEviewers);
    private final ApsFrameExtractor frameExtractor;
    ArrayList<BufferedImage> bufferedImages = new ArrayList<BufferedImage>(numOfAEviewers);
    private String dirPath = getString("dirPath", System.getProperty("user.dir"));
    private int numberOfImages = getInt("numberOfImages", 20);
    private boolean saveimage=false;
    private int countSavedImages=0;
    private int countViewers=0;
    private int waitingTime=getInt("waitingTime", 1000);
    int lastTime=0;

    public MultiCameraApsFrameExtractor(final AEChip chip) {
        super(chip);
	chip.addObserver(this);
	frameExtractor = new ApsFrameExtractor(chip);
        setPropertyTooltip("numberOfImages", "set number of images to save");
        setPropertyTooltip("waitingTime", "time to wait between two following images during the multiple saving in ms");

    }

        
    public void doSwitchGlobalView(){

        aevi = this.chip.getAeViewer();

        //switch to globalViewMode
        jaevi = aevi.getJaerViewer();

        //get the list of active viewers
        arrayOfAEvi = jaevi.getViewers();
        filterchain = new FilterChain(chip);

        numOfAEviewers=arrayOfAEvi.size();

        for (AEViewer aev : arrayOfAEvi){
                aev.setVisible(true);
                AEChip chi = aev.getChip();
                chi.addObserver(this);
                chips.add(chi);
//			System.out.println(chi.getAeViewer());
                System.out.println(aev);
        }

        for (int i=0;i<numOfAEviewers;i++){
            ApsFrameExtractor frameExtractortemp = new ApsFrameExtractor(arrayOfAEvi.get(i).getChip());
            fraext.add(frameExtractortemp);
            filterchain.add(frameExtractortemp);
            //filterchaintiers.add(frameExtractortemp);

            arrayOfAEvi.get(i).getChip().getFilterChain().add(frameExtractortemp);
            //arrayOfAEvi.get(1).getChip().getFilterChain().add(frameExtractortemp);
            frameExtractortemp.setExtRender(false);

            bufferedImages.add(new BufferedImage(chip.getSizeX(), chip.getSizeY(), BufferedImage.TYPE_INT_RGB));
        }
        setEnclosedFilterChain(filterchain);

        chip.getFilterFrame().rebuildContents();
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        int dT=in.getFirstTimestamp() - lastTime;
        if(saveimage==true && dT>=waitingTime*1000){
            for (int f=0;f<numOfAEviewers;f++){
                if(fraext.get(f).hasNewFrame()){
                    countViewers++;
                }  
            }
            if(countViewers==numOfAEviewers && countSavedImages<=numberOfImages){
                saveImage(countSavedImages);
                countSavedImages++;
                countViewers=0;
                lastTime=in.getFirstTimestamp();
                System.out.println("Saved "+ countSavedImages+" of "+numberOfImages+" time: "+lastTime);
            }
        }
        if(countSavedImages==numberOfImages){
           saveimage=false;
           countSavedImages=0;
        }
        return in;
    }

    /**
     */
    public void doSaveMultipleImagesAsPNG(){
        if (dirPath==null){
            dirPath = Paths.get(".").toAbsolutePath().normalize().toString();
        }
        saveimage=true; 
        for (int f=0;f<numOfAEviewers;f++){
            fraext.get(f).setShowAPSFrameDisplay(true);
        }
    }
    
    
    public void saveImage(int n){      
        
        for (int f=0;f<numOfAEviewers;f++){
            BufferedImage theImage = new BufferedImage(chip.getSizeX(), chip.getSizeY(), BufferedImage.TYPE_INT_RGB);
            ImageDisplay singleApsDisplay = fraext.get(f).apsDisplay;
            for (int y = 0; y < chip.getSizeY(); y++) {
                for (int x = 0; x < chip.getSizeX(); x++) {
                    final int idx = singleApsDisplay.getPixMapIndex(x, chip.getSizeY() - y - 1);
                    final int value = ((int) (256 * singleApsDisplay.getPixmapArray()[idx]) << 16)
                        | ((int) (256 * singleApsDisplay.getPixmapArray()[idx + 1]) << 8) | (int) (256 * singleApsDisplay.getPixmapArray()[idx + 2]);
                    theImage.setRGB(x, y, value);
                }
            }              
            bufferedImages.set(f, theImage);

            String selectedPath= dirPath;

            final Date d = new Date();
            final String PNG = "png";
            final String viewer = String.format("viewer%d", f);
            final String series = Integer.toString(n);
//            final String fn = "ApsFrame-" + viewer + "-"+AEDataFile.DATE_FORMAT.format(d) + "-"+series+"." + PNG;
            final String fn = "ApsFrame-" + viewer + "-"+"-00"+series+"." + PNG;

            File outputfile = new File(selectedPath + File.separator + fn);

            if (!FilenameUtils.isExtension(outputfile.getAbsolutePath(), PNG)) {
                String ext = FilenameUtils.getExtension(outputfile.toString());
                String newfile = outputfile.getAbsolutePath();
                if (ext != null && !ext.isEmpty() && !ext.equals(PNG)) {
                    newfile = outputfile.getAbsolutePath().replace(ext, PNG);
                } else {
                    newfile = newfile + "." + PNG;
                }
                outputfile = new File(newfile);
            }

            try {
                ImageIO.write(bufferedImages.get(f), "png", outputfile);
                log.info("wrote PNG " + outputfile);
    //            JOptionPane.showMessageDialog(chip.getFilterFrame(), "Wrote "+userDir+File.separator+fn, "Saved PNG image", JOptionPane.INFORMATION_MESSAGE);
            } catch (final IOException ex) {
                Logger.getLogger(ApsFrameExtractor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    
    synchronized public void doSetPath() {
        JFileChooser j = new JFileChooser();
        j.setCurrentDirectory(new File(dirPath));
        j.setApproveButtonText("Select");
        j.setDialogTitle("Select a folder and base file name for calibration images");
        j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // let user specify a base filename
        int ret = j.showSaveDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) {
                return;
        }
        //imagesDirPath = j.getSelectedFile().getAbsolutePath();
        dirPath = j.getSelectedFile().getPath();
        log.log(Level.INFO, "Changed images path to {0}", dirPath);
        putString("dirPath", dirPath);
	}
    
    @Override
    public void resetFilter() {
        
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    @Override
    public void update(final Observable o, final Object arg) {
        if ((o != null) && (arg != null)) {
            if ((o instanceof AEChip) && (arg.equals(Chip2D.EVENT_SIZEX) || arg.equals(Chip2D.EVENT_SIZEY))) {
                initFilter();
            }
        }
    }
    
    /**
    * @param numberOfImages the numberOfImages to set
    */
    public void setNumberOfImages(int numberOfImages) {
            this.numberOfImages = numberOfImages;
            putInt("numberOfImages", numberOfImages);
    }

    /**
     * @return the numberOfImages
     */
    public int getNumberOfImages() {
            return numberOfImages;
    }
    
    /**
    * @param waitingTime the waitingTime to set
    */
    public void setWaitingTime(int waitingTime) {
            this.waitingTime = waitingTime;
            putInt("waitingTime", waitingTime);
    }

    /**
     * @return the waitingTime
     */
    public int getWaitingTime() {
            return waitingTime;
    }


    
}
