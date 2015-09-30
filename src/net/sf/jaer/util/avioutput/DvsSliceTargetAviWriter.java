package net.sf.jaer.util.avioutput;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.beans.PropertyChangeEvent;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.TreeMap;

import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;

import eu.visualize.ini.convnet.DvsSubsamplerToFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ChipCanvas;
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
public class DvsSliceTargetAviWriter extends AbstractAviWriter
implements FrameAnnotater {

    private static final int currentTargetTypeID = 0;
	private static final int CURSOR_SIZE_CHIP_PIXELS = 3;
	private DvsSubsamplerToFrame dvsSubsampler = null;
    private int dimx, dimy, grayScale;
    private int dvsMinEvents = getInt("dvsMinEvents", 10000);
    private JFrame frame = null;
    public ImageDisplay display;
    private boolean showOutput;
    private volatile boolean newFrameAvailable = false;
    private int endOfFrameTimestamp=0, lastTimestamp=0;
    protected boolean writeDvsSliceImageOnApsFrame = getBoolean("writeDvsSliceImageOnApsFrame", false);
    private boolean rendererPropertyChangeListenerAdded=false;
    private AEFrameChipRenderer renderer=null;
    private HashMap<String, String> mapDataFilenameToTargetFilename = new HashMap();
    private String lastFileName = getString("lastFileName", DEFAULT_FILENAME);
    private String lastDataFilename = null;
    private TreeMap<Integer, SimultaneouTargetLocations> targetLocations = new TreeMap();
    private TargetLocation targetLocation = null;
    private GLUquadric mouseQuad = null;
    private int minSampleTimestamp = Integer.MAX_VALUE, maxSampleTimestamp = Integer.MIN_VALUE;
    private int targetRadius = getInt("targetRadius", 10);
    // file statistics
    private long firstInputStreamTimestamp = 0, lastInputStreamTimestamp = 0, inputStreamDuration = 0;
    private long filePositionEvents = 0, fileLengthEvents = 0;
    private int filePositionTimestamp = 0;
    private boolean warnSave = true;
    private final int N_FRACTIONS = 1000;
    private boolean[] labeledFractions = new boolean[N_FRACTIONS];  //to annotate graphically what has been labeled so far in event stream
    private boolean[] targetPresentInFractions = new
    boolean[N_FRACTIONS];  // to annotate graphically what has beenlabeled so far in event stream
    private ArrayList<TargetLocation> currentTargets = new ArrayList(10); // currently valid targets
	private ChipCanvas chipCanvas;
	private GLCanvas glCanvas;
    private int minTargetPointIntervalUs = getInt("minTargetPointIntervalUs", 10000);
    private int maxTimeLastTargetLocationValidUs = getInt("maxTimeLastTargetLocationValidUs", 100000);



    public DvsSliceTargetAviWriter(AEChip chip) {
        super(chip);
        dimx = getInt("dimx", 36);
        dimy = getInt("dimy", 36);
        grayScale = getInt("grayScale", 100);
        showOutput = getBoolean("showOutput", true);
        DEFAULT_FILENAME = "DvsTargetAvi.avi";
        setPropertyTooltip("grayScale", "1/grayScale is the amount by which each DVS event is added to time slice 2D gray-level histogram");
        setPropertyTooltip("dimx", "width of AVI frame");
        setPropertyTooltip("dimy", "height of AVI frame");
        setPropertyTooltip("loadLocations", "loads locations from a file");
        setPropertyTooltip("showOutput", "shows output in JFrame/ImageDisplay");
        setPropertyTooltip("dvsMinEvents", "minimum number of events to run net on DVS timeslice (only if writeDvsSliceImageOnApsFrame is false)");
        setPropertyTooltip("writeDvsSliceImageOnApsFrame", "<html>write DVS slice image for each APS frame end event (dvsMinEvents ignored).<br>The frame is written at the end of frame APS event.<br><b>Warning: to capture all frames, ensure that playback time slices are slow enough that all frames are rendered</b>");
        setPropertyTooltip("minTargetPointIntervalUs", "minimum interval between target positions in the database in us");
        setPropertyTooltip("maxTimeLastTargetLocationValidUs", "this time after last sample, the data is shown as not yet been labeled. This time specifies how long a specified target location is valid after its last specified location.");


        Arrays.fill(labeledFractions, false);
        Arrays.fill(targetPresentInFractions, false);
        try {
            byte[] bytes = getPrefs().getByteArray("TargetLabeler.hashmap", null);
            if (bytes != null) {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                mapDataFilenameToTargetFilename = (HashMap<String,String>) in.readObject();
                in.close();
                log.info("loaded mapDataFilenameToTargetFilename: " + mapDataFilenameToTargetFilename.size() + " entries");
            } else {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private TargetLocation lastNewTargetLocation = null;

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
//        frameExtractor.filterPacket(in); // extracts frames with nornalization (brightness, contrast) and sends to apsNet on each frame in PropertyChangeListener
        // send DVS timeslice to convnet
        super.filterPacket(in);
       if(!rendererPropertyChangeListenerAdded){
            rendererPropertyChangeListenerAdded=true;
            renderer=(AEFrameChipRenderer)chip.getRenderer();
            renderer.getSupport().addPropertyChangeListener(this);
        }
        final int sizeX = chip.getSizeX();
        final int sizeY = chip.getSizeY();
        checkSubsampler();


        for (BasicEvent e : in) {
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            PolarityEvent p = (PolarityEvent) e;

            //check if events is in target window, if yes add it to the subsampler
            if (((long) e.timestamp - (long) lastTimestamp) >= minTargetPointIntervalUs) {
                // show the nearest TargetLocation if at least minTargetPointIntervalUs has passed by,
                // or "No target" if the location was previously
                Map.Entry<Integer, SimultaneouTargetLocations> mostRecentTargetsBeforeThisEvent = targetLocations.lowerEntry(e.timestamp);
                if (mostRecentTargetsBeforeThisEvent != null) {
                    for (TargetLocation t : mostRecentTargetsBeforeThisEvent.getValue()) {
                        if ((t == null) || ((t != null) && ((e.timestamp - t.timestamp) > maxTimeLastTargetLocationValidUs))) {
                            targetLocation = null;
                        } else {
                            if (targetLocation != t) {
                                targetLocation = t;
                                currentTargets.add(targetLocation);
                                markDataHasTarget(targetLocation.timestamp);
                            }
                        }
                    }

                lastTimestamp = e.timestamp;
                // find next saved target location that is just before this time (lowerEntry)
                TargetLocation newTargetLocation = null;

                lastNewTargetLocation = newTargetLocation;
             }
            }
            if (e.timestamp < lastTimestamp) {
                lastTimestamp = e.timestamp;
            }
            //prune list of current targets to their valid lifetime, and remove leftover targets in the future
            ArrayList<TargetLocation> removeList = new ArrayList();
            for (TargetLocation t : currentTargets) {
                if (((t.timestamp + maxTimeLastTargetLocationValidUs) < in.getLastTimestamp()) || (t.timestamp > in.getLastTimestamp())) {
                    removeList.add(t);
                }
            }
            currentTargets.removeAll(removeList);
            // TO DO multiple targets per time..
            // it should produce one dvsSubampler image per target
            for (TargetLocation t : currentTargets) {
                if (t.location != null) {
                	//add this event into the dvsSubsampler if it is an event that is part of the tracked patch (target)
                	double x_max = t.location.getX() + (t.dimx/2.0);
                	double x_min = t.location.getX() - (t.dimx/2.0);
                	double y_max = t.location.getY() + (t.dimy/2.0);
                	double y_min = t.location.getY() - (t.dimy/2.0);
                	if( (p.x <= x_max) && (p.x >= x_min)  && (p.y <= y_max) && (p.y >= y_min)){
                		// re-scale p to x_min,x_max=y_min,y_max
                		//p.setX( (short) (t.location.getX()/(sizeX)) );
                		//p.setY( (short) (t.location.getY()/(sizeY)) );
                		//System.out.println(p.x);
                		//System.out.println(p.y);
                		dvsSubsampler.addEvent(p, sizeX, sizeY);
                        if ((writeDvsSliceImageOnApsFrame && newFrameAvailable && (e.timestamp>=endOfFrameTimestamp))
                                || ((!writeDvsSliceImageOnApsFrame && (dvsSubsampler.getAccumulatedEventCount() > dvsMinEvents))
                                && !chip.getAeViewer().isPaused())) {
                            if(writeDvsSliceImageOnApsFrame) {
                                newFrameAvailable=false;
                            }
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


                }



            }

        }
        if(writeDvsSliceImageOnApsFrame && ((lastTimestamp-endOfFrameTimestamp)>1000000)){
            log.warning("last frame event was received more than 1s ago; maybe you need to enable Display Frames in the User Control Panel?");
        }
        return in;
    }

    /**
     * @return the minTargetPointIntervalUs
     */
    public int getMinTargetPointIntervalUs() {
        return minTargetPointIntervalUs;
    }

    /**
     * @param minTargetPointIntervalUs the minTargetPointIntervalUs to set
     */
    public void setMinTargetPointIntervalUs(int minTargetPointIntervalUs) {
        this.minTargetPointIntervalUs = minTargetPointIntervalUs;
        putInt("minTargetPointIntervalUs", minTargetPointIntervalUs);
    }


    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (dvsSubsampler == null) {
            return;
        }
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .8f);
        MultilineAnnotationTextRenderer.setScale(.3f);
        String s = String.format("mostOffCount=%d\n mostOnCount=%d",dvsSubsampler.getMostOffCount(), dvsSubsampler.getMostOnCount());
        MultilineAnnotationTextRenderer.renderMultilineString(s);

        GL2 gl = drawable.getGL().getGL2();
        chipCanvas = chip.getCanvas();
        if (chipCanvas == null) {
            return;
        }
        glCanvas = (GLCanvas) chipCanvas.getCanvas();
        if (glCanvas == null) {
            return;
        }
        if (isSelected()) {
            Point mp = glCanvas.getMousePosition();
            Point p = chipCanvas.getPixelFromPoint(mp);
            if (p == null) {
                return;
            }
            checkBlend(gl);
            float[] compArray = new float[4];
            gl.glColor3fv(targetTypeColors[currentTargetTypeID % targetTypeColors.length].getColorComponents(compArray), 0);
            gl.glLineWidth(3f);
            gl.glPushMatrix();
            gl.glTranslatef(p.x, p.y, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, -CURSOR_SIZE_CHIP_PIXELS / 2);
            gl.glVertex2f(0, +CURSOR_SIZE_CHIP_PIXELS / 2);
            gl.glVertex2f(-CURSOR_SIZE_CHIP_PIXELS / 2, 0);
            gl.glVertex2f(+CURSOR_SIZE_CHIP_PIXELS / 2, 0);
            gl.glEnd();
            gl.glTranslatef(.5f, -.5f, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, -CURSOR_SIZE_CHIP_PIXELS / 2);
            gl.glVertex2f(0, +CURSOR_SIZE_CHIP_PIXELS / 2);
            gl.glVertex2f(-CURSOR_SIZE_CHIP_PIXELS / 2, 0);
            gl.glVertex2f(+CURSOR_SIZE_CHIP_PIXELS / 2, 0);
            gl.glEnd();
//            if (quad == null) {
//                quad = glu.gluNewQuadric();
//            }
//            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
//            glu.gluDisk(quad, 0, 3, 32, 1);
            gl.glPopMatrix();
        }

        for (TargetLocation t : currentTargets) {
            if (t.location != null) {
                t.draw(drawable, gl);
            }
        }

    }



    @Override
    public synchronized void doStartRecordingAndSaveAVIAs() {
        String[] s = {"dimx=" + dimx, "dimy=" + dimy, "grayScale=" + grayScale, "dvsMinEvents=" + dvsMinEvents, "format=" + format.toString(), "compressionQuality=" + compressionQuality};
        setAdditionalComments(s);
        super.doStartRecordingAndSaveAVIAs(); //To change body of generated methods, choose Tools | Templates.
    }

    private void checkSubsampler() {
        if ((dvsSubsampler == null) || ((dimx * dimy) != dvsSubsampler.getnPixels())) {
            if ((aviOutputStream != null) && (dvsSubsampler != null)) {
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
                int idx=((dimy - y - 1) * dimx) + x;
                if(idx>=bd.length){
                    throw new RuntimeException(String.format("index %d out of bounds for x=%d y=%d",idx,x,y));
                }
                bd[idx] = (b << 16) | (g << 8) | r | 0xFF000000;
            }
        }

        return bi;

    }

    synchronized public void maybeShowOutput(DvsSubsamplerToFrame subSampler) {
        if (!showOutput) {
            return;
        }
        if (frame == null) {
            String windowName = "DVS target slice";
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
                @Override
                public void windowClosing(WindowEvent e) {
                    setShowOutput(false);
                }
            });
        }
        if (!frame.isVisible()) {
            frame.setVisible(true);
        }
        if ((display.getWidth() != dimx) || (display.getHeight() != dimy)) {
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
     * @return the maxTimeLastTargetLocationValidUs
     */
    public int getMaxTimeLastTargetLocationValidUs() {
        return maxTimeLastTargetLocationValidUs;
    }

    /**
     * @param maxTimeLastTargetLocationValidUs the
     * maxTimeLastTargetLocationValidUs to set
     */
    public void setMaxTimeLastTargetLocationValidUs(int maxTimeLastTargetLocationValidUs) {
        if (maxTimeLastTargetLocationValidUs < minTargetPointIntervalUs) {
            maxTimeLastTargetLocationValidUs = minTargetPointIntervalUs;
        }
        this.maxTimeLastTargetLocationValidUs = maxTimeLastTargetLocationValidUs;
        putInt("maxTimeLastTargetLocationValidUs", maxTimeLastTargetLocationValidUs);

    }

    /**
     * @return the grayScale
     */
    public int getGrayScale() {
        return grayScale;
    }

    private int getFractionOfFileDuration(int timestamp) {
        if (inputStreamDuration == 0) {
            return 0;
        }
        return (int) Math.floor((N_FRACTIONS * ((float) (timestamp - firstInputStreamTimestamp))) / inputStreamDuration);
    }

    private void markDataHasTarget(int timestamp) {
        if (inputStreamDuration == 0) {
            return;
        }
        int frac = getFractionOfFileDuration(timestamp);
        if ((frac < 0) || (frac >= labeledFractions.length)) {
            log.warning("fraction " + frac + " is out of range " + labeledFractions.length + ", something is wrong");
            return;
        }
        labeledFractions[frac] = true;
        targetPresentInFractions[frac] = true;
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

    synchronized public void doLoadLocations() {
        lastFileName = mapDataFilenameToTargetFilename.get(lastDataFilename);
        if (lastFileName == null) {
            lastFileName = DEFAULT_FILENAME;
        }
        if ((lastFileName != null) && lastFileName.equals(DEFAULT_FILENAME)) {
            File f = chip.getAeViewer().getRecentFiles().getMostRecentFile();
            if (f == null) {
                lastFileName = DEFAULT_FILENAME;
            } else {
                lastFileName = f.getPath();
            }
        }
        JFileChooser c = new JFileChooser(lastFileName);
        c.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".txt");
            }

            @Override
            public String getDescription() {
                return "Text target label files";
            }
        });
        c.setMultiSelectionEnabled(false);
        c.setSelectedFile(new File(lastFileName));
        ChipCanvas chipCanvas = chip.getCanvas();
        GLCanvas glCanvas = (GLCanvas) chipCanvas.getCanvas();
        int ret = c.showOpenDialog(glCanvas);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFileName = c.getSelectedFile().toString();
        putString("lastFileName", lastFileName);
        loadLocations(new File(lastFileName));
    }

    /**
     * Loads last locations. Note that this is a lengthy operation
     */
    synchronized public void loadLastLocations() {
        if (lastFileName == null) {
            return;
        }
        File f = new File(lastFileName);
        if (!f.exists() || !f.isFile()) {
            return;
        }
        loadLocations(f);
    }


    synchronized private void loadLocations(File f) {
        log.info("loading " + f);
        try {
            setCursor(new Cursor(Cursor.WAIT_CURSOR));
            targetLocations.clear();
            minSampleTimestamp = Integer.MAX_VALUE;
            maxSampleTimestamp = Integer.MIN_VALUE;
            try {
                BufferedReader reader = new BufferedReader(new FileReader(f));
                String s = reader.readLine();
                StringBuilder sb = new StringBuilder();
                while ((s != null) && s.startsWith("#")) {
                    sb.append(s + "\n");
                    s = reader.readLine();
                }
                log.info("header lines on " + f.getAbsolutePath() + "are\n" + sb.toString());
                while (s != null) {
                    Scanner scanner = new Scanner(s);
                    try {
                        int frame = scanner.nextInt();
                        int ts = scanner.nextInt();
                        int x = scanner.nextInt();
                        int y = scanner.nextInt();
                        int targetTypeID = 0;
                        int targetdimx = targetRadius;
                        int targetdimy = targetRadius;
                        try {
                            targetTypeID = scanner.nextInt();
                            try {
                                // added target dimensions compatibility
                                targetdimx = scanner.nextInt();
                                targetdimy = scanner.nextInt();
                                }catch (NoSuchElementException e) {
                                // older type file with only single target and no targetClassID and no x,y dimensions
                                }
                        } catch (NoSuchElementException e) {
                            // older type file with only single target
                        }
                        targetLocation = new TargetLocation(frame, ts,
                                new Point(x, y),
                                targetTypeID,
                                targetdimx,
                                targetdimy); // read target location
                    } catch (NoSuchElementException ex2) {
                        throw new IOException(("couldn't parse file " + f) == null ? "null" : f.toString() + ", got InputMismatchException on line: " + s);
                    }
                    if ((targetLocation.location.x == -1) && (targetLocation.location.y == -1)) {
                        targetLocation.location = null;
                    }
                    addSample(targetLocation.timestamp, targetLocation);
                    markDataHasTarget(targetLocation.timestamp);
                    if (targetLocation != null) {
                        if (targetLocation.timestamp > maxSampleTimestamp) {
                            maxSampleTimestamp = targetLocation.timestamp;
                        }
                        if (targetLocation.timestamp < minSampleTimestamp) {
                            minSampleTimestamp = targetLocation.timestamp;
                        }
                    }
                    s = reader.readLine();
                }
                log.info("done loading " + f);
                if (lastDataFilename != null) {
                	mapDataFilenameToTargetFilename.put(lastDataFilename, f.getPath());
                }
            } catch (FileNotFoundException ex) {
                ChipCanvas chipCanvas = chip.getCanvas();
                GLCanvas glCanvas = (GLCanvas) chipCanvas.getCanvas();
                JOptionPane.showMessageDialog(glCanvas, ("couldn't find file " + f) == null ? "null" : f.toString() + ": got exception " + ex.toString(), "Couldn't load locations",
                	JOptionPane.WARNING_MESSAGE, null);
            } catch (IOException ex) {
                ChipCanvas chipCanvas = chip.getCanvas();
                GLCanvas glCanvas = (GLCanvas) chipCanvas.getCanvas();
                JOptionPane.showMessageDialog(glCanvas, ("IOException with file " + f) == null ? "null" : f.toString() + ": got exception " + ex.toString(), "Couldn't load locations",
                	JOptionPane.WARNING_MESSAGE, null);
            }
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }


    /**
     * List of targets simultaneously present at a particular timestamp
     */
    private class SimultaneouTargetLocations extends ArrayList<TargetLocation> {

    }

    /**
     * @return the targetLocation
     */
    public TargetLocation getTargetLocation() {
        return targetLocation;
    }

    private void addSample(int timestamp, TargetLocation newTargetLocation) {
        SimultaneouTargetLocations s = targetLocations.get(timestamp);
        if (s == null) {
            s = new SimultaneouTargetLocations();
            targetLocations.put(timestamp, s);
        }
        s.add(newTargetLocation);
    }


    /**
     * @return the writeDvsSliceImageOnApsFrame
     */
    public boolean isWriteDvsSliceImageOnApsFrame() {
        return writeDvsSliceImageOnApsFrame;
    }

    /**
     * @param writeDvsSliceImageOnApsFrame the writeDvsSliceImageOnApsFrame to
     * set
     */
    public void setWriteDvsSliceImageOnApsFrame(boolean writeDvsSliceImageOnApsFrame) {
        this.writeDvsSliceImageOnApsFrame = writeDvsSliceImageOnApsFrame;
        putBoolean("writeDvsSliceImageOnApsFrame", writeDvsSliceImageOnApsFrame);
    }

      @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ((evt.getPropertyName() == AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE)) {
            AEFrameChipRenderer renderer=(AEFrameChipRenderer)evt.getNewValue();
            endOfFrameTimestamp=renderer.getTimestampFrameEnd();
            newFrameAvailable = true;
        } else if (isCloseOnRewind() && (evt.getPropertyName() == AEInputStream.EVENT_REWIND)) {
            doCloseFile();
        }
    }

      private final Color[] targetTypeColors = {Color.BLUE, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED};

      class TargetLocation {

          int timestamp;
          int frameNumber;
          Point location; // center of target location
          int targetClassID; // class of target, i.e. car, person
          int dimx; // dimension of target x
          int dimy; // y

          public TargetLocation(int frameNumber, int timestamp, Point location, int targetTypeID, int dimx, int dimy) {
              this.frameNumber = frameNumber;
              this.timestamp = timestamp;
              this.location = location != null ? new Point(location) : null;
              this.targetClassID = targetTypeID;
              this.dimx = dimx;
              this.dimy = dimy;
          }

          private void draw(GLAutoDrawable drawable, GL2 gl) {
        	  GLU glu = new GLU();
              gl.glPushMatrix();
              gl.glTranslatef(location.x, location.y, 0f);
              float[] compArray = new float[4];
              gl.glColor3fv(targetTypeColors[targetClassID % targetTypeColors.length].getColorComponents(compArray), 0);
              if (mouseQuad == null) {
                 mouseQuad = glu.gluNewQuadric();
              }
              glu.gluQuadricDrawStyle(mouseQuad, GLU.GLU_LINE);
              glu.gluDisk(mouseQuad, dimx/2, (dimy/2) +1, 32, 1);
              //System.out.println(String.format("(%d,%d,%d,%d)", dimx/2, (dimy/2) + 1, 32, 1));
              gl.glPopMatrix();
          }

          @Override
          public String toString() {
              return String.format("TargetLocation frameNumber=%d timestamp=%d location=%s", frameNumber, timestamp, location == null ?
"null" : location.toString());
          }

      }
}