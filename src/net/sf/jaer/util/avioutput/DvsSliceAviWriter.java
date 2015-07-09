package net.sf.jaer.util.avioutput;

import eu.visualize.ini.convnet.DvsSubsamplerToFrame;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import com.jogamp.opengl.GLAutoDrawable;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Writes out AVI movie with DVS time or event slices as AVI frame images with
 * desired output resolution
 *
 * @author Tobi Delbruck
 */
@Description("Writes out AVI movie with DVS constant-number-of-event slices as AVI frame images with desired output resolution")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DvsSliceAviWriter extends AbstractAviWriter implements FrameAnnotater{

    private DvsSubsamplerToFrame dvsSubsampler = null;
    private int dimx, dimy, grayScale;
    private int dvsMinEvents = getInt("dvsMinEvents", 10000);
    private JFrame frame = null;
    public ImageDisplay display;
    private boolean showOutput;

    public DvsSliceAviWriter(AEChip chip) {
        super(chip);
        dimx = getInt("dimx", 36);
        dimy = getInt("dimy", 36);
        grayScale = getInt("grayScale", 100);
        showOutput = getBoolean("showOutput", true);
        DEFAULT_FILENAME = "DvsEventSlices.avi";
        setPropertyTooltip("grayScale", "1/grayScale is the amount by which each DVS event is added to time slice 2D gray-level histogram");
        setPropertyTooltip("dimx", "width of AVI frame");
        setPropertyTooltip("dimy", "height of AVI frame");
        setPropertyTooltip("showOutput", "shows output in JFrame/ImageDisplay");
        setPropertyTooltip("dvsMinEvents", "minimum number of events to run net on DVS timeslice");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
//        frameExtractor.filterPacket(in); // extracts frames with nornalization (brightness, contrast) and sends to apsNet on each frame in PropertyChangeListener
        // send DVS timeslice to convnet
        super.filterPacket(in);
        final int sizeX = chip.getSizeX();
        final int sizeY = chip.getSizeY();
        checkSubsampler();
        for (BasicEvent e : in) {
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            PolarityEvent p = (PolarityEvent) e;
            dvsSubsampler.addEvent(p, sizeX, sizeY);
            if (dvsSubsampler.getAccumulatedEventCount() > dvsMinEvents) {
                maybeShowOutput(dvsSubsampler);
                if (aviOutputStream != null) {
                    BufferedImage bi = toImage(dvsSubsampler);
                    try {
                        writeTimecode(e.timestamp);
                        aviOutputStream.writeFrame(bi);
                        incrementFramecountAndMaybeCloseOutput();
                    } catch (IOException ex) {
                        log.warning(ex.toString());
                        ex.printStackTrace();
                        setFilterEnabled(false);
                    }
                }
                dvsSubsampler.clear();
            }
        }
        return in;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if(dvsSubsampler==null) return;
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY()*.8f);
        MultilineAnnotationTextRenderer.setScale(.3f);
        String s=String.format("mostOffCount=%d\n mostOnCount=%d",dvsSubsampler.getMostOffCount(),dvsSubsampler.getMostOnCount());
        MultilineAnnotationTextRenderer.renderMultilineString(s);
    }
    
    

    @Override
    public synchronized void doStartRecordingAndSaveAVIAs() {
        String[] s={"dimx="+dimx,"dimy="+dimy,"grayScale="+grayScale,"dvsMinEvents="+dvsMinEvents,"format="+format.toString(),"compressionQuality="+compressionQuality};
        setAdditionalComments(s);
        super.doStartRecordingAndSaveAVIAs(); //To change body of generated methods, choose Tools | Templates.
    }
    
    

    private void checkSubsampler() {
        if (dvsSubsampler == null || dimx * dimy != dvsSubsampler.getnPixels()) {
            if(aviOutputStream!=null && dvsSubsampler!=null){
                log.info("closing existing output file because output resolution has changed");
                doCloseFile();
            }
            dvsSubsampler = new DvsSubsamplerToFrame(dimx, dimy, grayScale);
        }
    }

    private BufferedImage toImage(DvsSubsamplerToFrame subSampler) {
        BufferedImage bi = new BufferedImage(dimx, dimy, BufferedImage.TYPE_INT_BGR);
        int[] bd = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < dimy; y++) {
            for (int x = 0; x < dimx; x++) {
                int b = (int) (255 * subSampler.getValueAtPixel(x, y));
                int g = b;
                int r = b;

                bd[(dimx - y - 1) * dimy + x] = (b << 16) | (g << 8) | r | 0xFF000000;
            }
        }

        return bi;

    }

    synchronized public void maybeShowOutput(DvsSubsamplerToFrame subSampler) {
        if (!showOutput) {
            return;
        }
        if (frame == null) {
            String windowName = "DVS slice";
            frame = new JFrame(windowName);
            frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
            frame.setPreferredSize(new Dimension(600, 600));
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            display = ImageDisplay.createOpenGLCanvas();
            display.setBorderSpacePixels(10);
            display.setImageSize(dimx, dimy);
            display.setSize(200, 200);
            panel.add(display);

            frame.getContentPane().add(panel);
            frame.pack();
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    setShowOutput(false);
                }
            });
        }
        if (!frame.isVisible()) {
            frame.setVisible(true);
        }
        if(display.getWidth()!=dimx || display.getHeight()!=dimy){
             display.setImageSize(dimx, dimy);
       }
        for (int x = 0; x < dimx; x++) {
            for (int y = 0; y < dimy; y++) {
                display.setPixmapGray(x, y, subSampler.getValueAtPixel(x, y));
            }
        }
        display.repaint();
    }

    /**
     * @return the dvsMinEvents
     */
    public int getDvsMinEvents() {
        return dvsMinEvents;
    }

    /**
     * @param dvsMinEvents the dvsMinEvents to set
     */
    public void setDvsMinEvents(int dvsMinEvents) {
        this.dvsMinEvents = dvsMinEvents;
        putInt("dvsMinEvents", dvsMinEvents);
    }

    /**
     * @return the dimx
     */
    public int getDimx() {
        return dimx;
    }

    /**
     * @param dimx the dimx to set
     */
    synchronized public void setDimx(int dimx) {
        this.dimx = dimx;
        putInt("dimx", dimx);
    }

    /**
     * @return the dimy
     */
    public int getDimy() {
        return dimy;
    }

    /**
     * @param dimy the dimy to set
     */
    synchronized public void setDimy(int dimy) {
        this.dimy = dimy;
        putInt("dimy", dimy);
    }

    /**
     * @return the showOutput
     */
    public boolean isShowOutput() {
        return showOutput;
    }

    /**
     * @param showOutput the showOutput to set
     */
    public void setShowOutput(boolean showOutput) {
        boolean old = this.showOutput;
        this.showOutput = showOutput;
        putBoolean("showOutput", showOutput);
        getSupport().firePropertyChange("showOutput", old, showOutput);
    }

    /**
     * @return the grayScale
     */
    public int getGrayScale() {
        return grayScale;
    }

    /**
     * @param grayScale the grayScale to set
     */
    public void setGrayScale(int grayScale) {
        if (grayScale < 1) {
            grayScale = 1;
        }
        this.grayScale = grayScale;
        putInt("grayScale", grayScale);
        if (dvsSubsampler != null) {
            dvsSubsampler.setColorScale(grayScale);
        }
    }

}
