/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.frames;

import eu.seebetter.ini.chips.ApsDvsChip;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.util.avioutput.AVIOutputStream;

/**
 * Writes AVI file from DAVIS APS frames, using ApsFrameExtractor.
 * The AVI file is in RAW format with pixel values 0-255 coming from ApsFrameExtractor displayed frames, which are offset and scaled by it.
 *
 * @author Tobi
 */
@Description("Writes AVI file from DAVIS APS frames, using ApsFrameExtractor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DAVISAVIWriter extends EventFilter2D implements PropertyChangeListener {

    ApsFrameExtractor apsFrameExtractor;
    AVIOutputStream aviOutputStream = null;
    private String DEFAULT_FILENAME = "DAVIS_APS.avi";
    private String lastFileName = getString("lastFileName", DEFAULT_FILENAME);
    ApsDvsChip apsDvsChip = null;
    private int framesWritten = 0;
    private final int logEveryThisManyFrames = 30;

    public DAVISAVIWriter(AEChip chip) {
        super(chip);
        FilterChain filterChain = new FilterChain(chip);
        apsFrameExtractor = new ApsFrameExtractor(chip);
        apsFrameExtractor.getSupport().addPropertyChangeListener(this);
        filterChain.add(apsFrameExtractor);
        setEnclosedFilterChain(filterChain);
        setPropertyTooltip("saveAVIFileAs", "Opens the output file. The AVI file is in RAW format with pixel values 0-255 coming from ApsFrameExtractor displayed frames, which are offset and scaled by it.");
        setPropertyTooltip("closeFile", "Closes the output file if it is open.");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        apsDvsChip = (ApsDvsChip) chip;
        apsFrameExtractor.filterPacket(in);
        return in;
    }

    @Override
    public void resetFilter() {
        apsFrameExtractor.resetFilter();
    }

    @Override
    public void initFilter() {
        apsFrameExtractor.initFilter();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (aviOutputStream != null && evt.getPropertyName() == ApsFrameExtractor.EVENT_NEW_FRAME) {
            double[] frame = apsFrameExtractor.getNewFrame();
            BufferedImage bufferedImage = new BufferedImage(chip.getSizeX(), chip.getSizeY(), BufferedImage.TYPE_3BYTE_BGR);
            WritableRaster raster = bufferedImage.getRaster();
            int sx = chip.getSizeX(), sy = chip.getSizeY();
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    int k = apsFrameExtractor.getIndex(x, y);
//                    bufferedImage.setRGB(x, y, (int) (frame[k] * 1024));
                    int v=(int)(frame[k]*255), yy=sy - y - 1; // must flip image vertially according to java convention that image starts at upper left
                    raster.setSample(x, yy, 0, v); 
                    raster.setSample(x, yy, 1, v); 
                    raster.setSample(x, yy, 2, v); 
                }
            }
            try {
                aviOutputStream.writeFrame(bufferedImage);
                if (++framesWritten % logEveryThisManyFrames == 0) {
                    log.info(String.format("wrote %d frames", framesWritten));
                }

//        apsFrameExtractor.getIndex(x, y);
            } catch (IOException ex) {
                Logger.getLogger(DAVISAVIWriter.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    synchronized public void doSaveAVIFileAs() {
        JFileChooser c = new JFileChooser(lastFileName);
        c.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".avi");
            }

            @Override
            public String getDescription() {
                return "AVI (Audio Video Interleave) Microsoft video file";
            }
        });
        c.setSelectedFile(new File(lastFileName));
        int ret = c.showSaveDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFileName = c.getSelectedFile().toString();
        if (c.getSelectedFile().exists()) {
            int r = JOptionPane.showConfirmDialog(null, "File " + c.getSelectedFile().toString() + " already exists, overwrite it?");
            if (r != JOptionPane.OK_OPTION) {
                return;
            }
        }
        openAVIOutputStream(c.getSelectedFile());
    }

    synchronized public void doCloseFile() {
        if (aviOutputStream != null) {
            try {
                aviOutputStream.close();
                aviOutputStream = null;
                log.info("Closed " + lastFileName);
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }

    }

    private void openAVIOutputStream(File f) {
        try {
            aviOutputStream = new AVIOutputStream(f, AVIOutputStream.VideoFormat.RAW);
            aviOutputStream.setFrameRate((int) apsDvsChip.getFrameRateHz());
//            aviOutputStream.setVideoDimension(chip.getSizeX(), chip.getSizeY());
            lastFileName = f.toString();
            putString("lastFileName", lastFileName);
            log.info("Opened " + f.toString());
            framesWritten = 0;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, ex.toString(), "Couldn't create output file stream", JOptionPane.WARNING_MESSAGE, null);
            return;
        }
    }
}
