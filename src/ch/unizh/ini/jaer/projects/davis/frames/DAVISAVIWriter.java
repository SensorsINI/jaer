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
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.util.avioutput.AVIOutputStream;

/**
 * Writes AVI file from DAVIS APS frames, using ApsFrameExtractor. The AVI file
 * is in RAW format with pixel values 0-255 coming from ApsFrameExtractor
 * displayed frames, which are offset and scaled by it.
 *
 * @author Tobi
 */
@Description("Writes AVI file from DAVIS APS frames, using ApsFrameExtractor")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DAVISAVIWriter extends EventFilter2D implements PropertyChangeListener {

    ApsFrameExtractor apsFrameExtractor;
    AVIOutputStream aviOutputStream = null;
    private static final String DEFAULT_FILENAME = "DAVIS_APS.avi";
    private String lastFileName = getString("lastFileName", DEFAULT_FILENAME);
    ApsDvsChip apsDvsChip = null;
    private int framesWritten = 0;
    private final int logEveryThisManyFrames = 30;
    private boolean writeTimecodeFile = getBoolean("writeTimecodeFile", true);
    private static final String TIMECODE_SUFFIX = "-timecode.txt";
    private File timecodeFile = null;
    private FileWriter timecodeWriter = null;
    private boolean closeOnRewind = getBoolean("closeOnRewind", true);
    private boolean propertyChangeListenerAdded = false;

    public DAVISAVIWriter(AEChip chip) {
        super(chip);
        FilterChain filterChain = new FilterChain(chip);
        apsFrameExtractor = new ApsFrameExtractor(chip);
        apsFrameExtractor.getSupport().addPropertyChangeListener(this);
        filterChain.add(apsFrameExtractor);
        setEnclosedFilterChain(filterChain);
        setPropertyTooltip("saveAVIFileAs", "Opens the output file. The AVI file is in RAW format with pixel values 0-255 coming from ApsFrameExtractor displayed frames, which are offset and scaled by it.");
        setPropertyTooltip("closeFile", "Closes the output file if it is open.");
        setPropertyTooltip("writeTimecodeFile", "writes a file alongside AVI file (with suffix " + TIMECODE_SUFFIX + ") that maps from AVI frame to AER timestamp for that frame (the frame end timestamp)");
        setPropertyTooltip("closeOnRewind", "closes recording on rewind event, to allow unattended operation");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!propertyChangeListenerAdded) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().addPropertyChangeListener(this);
                propertyChangeListenerAdded = true;
            }
        }
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
            float[] frame = apsFrameExtractor.getNewFrame();

            BufferedImage bufferedImage = new BufferedImage(chip.getSizeX(), chip.getSizeY(), BufferedImage.TYPE_3BYTE_BGR);
            WritableRaster raster = bufferedImage.getRaster();
            int sx = chip.getSizeX(), sy = chip.getSizeY();
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    int k = apsFrameExtractor.getIndex(x, y);
//                    bufferedImage.setRGB(x, y, (int) (frame[k] * 1024));
                    int v = (int) (frame[k] * 255), yy = sy - y - 1; // must flip image vertially according to java convention that image starts at upper left
                    raster.setSample(x, yy, 0, v);
                    raster.setSample(x, yy, 1, v);
                    raster.setSample(x, yy, 2, v);
                }
            }
            try {
                aviOutputStream.writeFrame(bufferedImage);
                if (timecodeWriter != null) {
                    int timestamp = apsFrameExtractor.getLastFrameTimestamp();
                    timecodeWriter.write(String.format("%d %d\n", framesWritten, timestamp));

                }
                if (++framesWritten % logEveryThisManyFrames == 0) {
                    log.info(String.format("wrote %d frames", framesWritten));
                }

            } catch (IOException ex) {
                Logger.getLogger(DAVISAVIWriter.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else if (evt.getPropertyName() == AEInputStream.EVENT_REWIND) {
            doCloseFile();
        }
    }

    synchronized public void doSaveAVIFileAs() {
        if (aviOutputStream != null) {
            JOptionPane.showMessageDialog(null, "AVI output stream is already opened");
            return;
        }
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
        if (!c.getSelectedFile().getName().toLowerCase().endsWith(".avi")) {
            String newName = c.getSelectedFile().toString() + ".avi";
            c.setSelectedFile(new File(newName));
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
                if (timecodeWriter != null) {
                    timecodeWriter.close();
                    timecodeWriter = null;
                }
                log.info("Closed " + lastFileName + " with " + framesWritten + " frames");
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }

    }

    private void openAVIOutputStream(File f) {
        try {
            aviOutputStream = new AVIOutputStream(f, AVIOutputStream.VideoFormat.RAW);
            int frameRate = apsDvsChip==null?10:(int) apsDvsChip.getFrameRateHz();
            if (frameRate == 0) {
                JOptionPane.showMessageDialog(null, "Frame rate is reported as 0, setting to 10 by default.\nEnable the Image Sensor/Display Frames option in HW Configuration panel so that frame rate is computed.", "Couldn't set correct frame rate", JOptionPane.WARNING_MESSAGE, null);
                frameRate = 10;
            }
            aviOutputStream.setFrameRate(frameRate);
//            aviOutputStream.setVideoDimension(chip.getSizeX(), chip.getSizeY());
            lastFileName = f.toString();
            putString("lastFileName", lastFileName);
            if (writeTimecodeFile) {
                String s = f.toString().subSequence(0, f.toString().lastIndexOf(".")).toString() + TIMECODE_SUFFIX;
                timecodeFile = new File(s);
                timecodeWriter = new FileWriter(timecodeFile);
                timecodeWriter.write(String.format("# timecode file relating frames of AVI file to AER timestamps\n"));
                timecodeWriter.write(String.format("# written %s\n", new Date().toString()));
                timecodeWriter.write(String.format("# frameNumber timestamp\n"));
            }
            log.info("Opened " + f.toString());
            framesWritten = 0;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, ex.toString(), "Couldn't create output file stream", JOptionPane.WARNING_MESSAGE, null);
            return;
        }
    }

    /**
     * @return the writeTimecodeFile
     */
    public boolean isWriteTimecodeFile() {
        return writeTimecodeFile;
    }

    /**
     * @param writeTimecodeFile the writeTimecodeFile to set
     */
    public void setWriteTimecodeFile(boolean writeTimecodeFile) {
        this.writeTimecodeFile = writeTimecodeFile;
        putBoolean("writeTimecodeFile", writeTimecodeFile);
    }

    /**
     * @return the closeOnRewind
     */
    public boolean isCloseOnRewind() {
        return closeOnRewind;
    }

    /**
     * @param closeOnRewind the closeOnRewind to set
     */
    public void setCloseOnRewind(boolean closeOnRewind) {
        this.closeOnRewind = closeOnRewind;
        putBoolean("closeOnRewind", closeOnRewind);
    }
}
