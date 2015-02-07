/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.avioutput;

import ch.unizh.ini.jaer.projects.davis.frames.*;
import com.jogamp.common.nio.Buffers;
import eu.seebetter.ini.chips.ApsDvsChip;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
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
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Writes AVI file from displayed AEViewer frames, The AVI file is in RAW
 * format.
 *
 * @author Tobi
 */
@Description("Writes AVI file AEViewer displayed OpenGL graphics")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class JaerAviWriter extends EventFilter2D implements  FrameAnnotater {

    AVIOutputStream aviOutputStream = null;
    private static final String DEFAULT_FILENAME = "jAER-AEViewer.avi";
    private String lastFileName = getString("lastFileName", DEFAULT_FILENAME);
    private int framesWritten = 0;
    private final int logEveryThisManyFrames = 30;
    private boolean writeTimecodeFile = getBoolean("writeTimecodeFile", true);
    private static final String TIMECODE_SUFFIX = "-timecode.txt";
    private File timecodeFile = null;
    private FileWriter timecodeWriter = null;
    private boolean closeOnRewind = getBoolean("closeOnRewind", true);
    private boolean propertyChangeListenerAdded = false;
    private AVIOutputStream.VideoFormat format=AVIOutputStream.VideoFormat.valueOf(getString("format", AVIOutputStream.VideoFormat.RAW.toString()));
    private int maxFrames=getInt("maxFrames",0);
    private float compressionQuality=getFloat("compressionQuality",0.9f);

    public JaerAviWriter(AEChip chip) {
        super(chip);
        setPropertyTooltip("saveAVIFileAs", "Opens the output file. The AVI file is in RAW format with pixel values 0-255 coming from ApsFrameExtractor displayed frames, which are offset and scaled by it.");
        setPropertyTooltip("closeFile", "Closes the output file if it is open.");
        setPropertyTooltip("writeTimecodeFile", "writes a file alongside AVI file (with suffix " + TIMECODE_SUFFIX + ") that maps from AVI frame to AER timestamp for that frame (the frame end timestamp)");
        setPropertyTooltip("closeOnRewind", "closes recording on rewind event, to allow unattended operation");
        setPropertyTooltip("format", "video file is writtent to this output format (note that RLE will throw exception because OpenGL frames are not 4 or 8 bit images)");
        setPropertyTooltip("maxFrames", "file is automatically closed after this many frames have been written; set to 0 to disable");
        setPropertyTooltip("framesWritten", "READONLY, shows number of frames written");
        setPropertyTooltip("compressionQuality", "In PNG or JPG format, sets compression quality; 0 is lowest quality and 1 is highest, 0.9 is default value");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (chip instanceof ApsDvsChip && !propertyChangeListenerAdded) {
            if (chip.getAeViewer() != null) {
                propertyChangeListenerAdded = true;
            }
        }
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
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
                    log.info("Closed timecode file "+timecodeFile.toString());
                    timecodeWriter = null;
                }
                log.info("Closed " + lastFileName + " in format "+format+" with " + framesWritten + " frames");
            } catch (Exception ex) {
                log.warning(ex.toString());
                ex.printStackTrace();
                aviOutputStream=null;
            }
        }

    }

    private void openAVIOutputStream(File f) {
        try {
            aviOutputStream = new AVIOutputStream(f, format);
            aviOutputStream.setFrameRate(chip.getAeViewer().getFrameRate());
            aviOutputStream.setVideoCompressionQuality(compressionQuality);
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
                log.info("Opened timecode file "+timecodeFile.toString());
            }
            log.info("Opened AVI output file " + f.toString()+" with format "+format);
            framesWritten = 0;
            getSupport().firePropertyChange("framesWritten", null, framesWritten);
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

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if(aviOutputStream==null) return;
        GL2 gl=drawable.getGL().getGL2();
        BufferedImage bi=toImage(gl, drawable.getNativeSurface().getSurfaceWidth(), drawable.getNativeSurface().getSurfaceHeight());
       
            try {
                aviOutputStream.writeFrame(bi);
                if (timecodeWriter != null) {
                    int timestamp = chip.getAeViewer().getAePlayer().getTime();
                    timecodeWriter.write(String.format("%d %d\n", framesWritten, timestamp));

                }
                if (++framesWritten % logEveryThisManyFrames == 0) {
                    log.info(String.format("wrote %d frames", framesWritten));
                }
                getSupport().firePropertyChange("framesWritten", null, framesWritten);
                if(maxFrames>0 && framesWritten>=maxFrames){
                    log.info("wrote maxFrames="+maxFrames+" frames; closing AVI file");
                    doCloseFile();
                }

            } catch (Exception e) {
                log.warning("While writing AVI frame, caught exception, closing file: "+e.toString());
                doCloseFile();
            } 
    }

    public BufferedImage toImage(GL2 gl, int w, int h) {

        gl.glReadBuffer(GL.GL_FRONT); // or GL.GL_BACK
        ByteBuffer glBB = Buffers.newDirectByteBuffer(4 * w * h);
        gl.glReadPixels(0, 0, w, h, GL2.GL_BGRA, GL.GL_BYTE, glBB);

        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_BGR);
        int[] bd = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int b = 2 * glBB.get();
                int g = 2 * glBB.get();
                int r = 2 * glBB.get();
                int a=glBB.get(); // not using

                bd[(h - y - 1) * w + x] = (b << 16) | (g << 8) | r | 0xFF000000;
            }
        }

        return bi;
    }

    /**
     * @return the format
     */
    public AVIOutputStream.VideoFormat getFormat() {
        return format;
    }

    /**
     * @param format the format to set
     */
    public void setFormat(AVIOutputStream.VideoFormat format) {
        this.format = format;
        putString("format", format.toString());
    }

    /**
     * @return the maxFrames
     */
    public int getMaxFrames() {
        return maxFrames;
    }

    /**
     * @param maxFrames the maxFrames to set
     */
    public void setMaxFrames(int maxFrames) {
        this.maxFrames = maxFrames;
        putInt("maxFrames",maxFrames);
    }

    /**
     * @return the framesWritten
     */
    public int getFramesWritten() {
        return framesWritten;
    }

    /**
     * @param framesWritten the framesWritten to set
     */
    public void setFramesWritten(int framesWritten) {
        // do nothing, only here to expose in GUI
    }

    /**
     * @return the compressionQuality
     */
    public float getCompressionQuality() {
        return compressionQuality;
    }

    /**
     * @param compressionQuality the compressionQuality to set
     */
    public void setCompressionQuality(float compressionQuality) {
        if(compressionQuality<0) compressionQuality=0; else if(compressionQuality>1)compressionQuality=1;
        this.compressionQuality = compressionQuality;
        putFloat("compressionQuality",compressionQuality);
    }
}
