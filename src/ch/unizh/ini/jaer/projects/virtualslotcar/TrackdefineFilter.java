/*
 * TrackdefineFilter.java
 *
 * Created on July 1, 2010, 01:05 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;
import net.sf.jaer.eventprocessing.filter.*;
import java.awt.*;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import java.awt.Graphics2D;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.*;
/**
 * An AE filter that first creates a histogram of incoming pixels, and then lets the user define
 * the detected track.
 *
 * @author Michael Pfeiffer
 */
public class TrackdefineFilter extends EventFilter2D implements FrameAnnotater,Observer,MouseListener,MouseMotionListener{
    public static String getDescription (){
        return "Detects a track from incoming pixels and user input";
    }
    // Variables declared in XYTypeFilter
    final private static float[] SELECT_COLOR = { .8f,0,0,.5f };
    public short x = 0, y = 0;
    public byte type = 0;
    private short xAnd;
    private short yAnd;
    private byte typeAnd;
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    private Point startPoint = null, clickedPoint = null;
    private Point currentMousePoint = null;
    private int maxEvents = 0;
    private int index = 0;
    private short xspike, yspike;
    private byte typespike;
    private int ts, repMeasure, i;
    private volatile boolean selecting = false;
    private static float lineWidth = 1f;
    
    // Start here with new variable declarations

    // Dimensions of the pixel array
    int numX;
    int numY;

    // Histogram data
    private float[][] pixData = null;

    // Total sum of histogram points
    float totalSum;

    // Draw histogram in annotate or not
    private boolean drawHist = prefs().getBoolean("TrackdefineFilter.drawHist", false);

    // Threshold for accepting points as track points
    private float histThresh = prefs().getFloat("TrackdefineFilter.histThresh",0.0001f);

    // Re-initialize counters after end of replay
    private boolean reInit = prefs().getBoolean("TrackdefineFilter.reInit", false);

    // Size of erosion mask
    private int erosionSize = prefs().getInt("TrackdefineFilter.erosionSize", 0);

    private int counter = 0;

    public TrackdefineFilter (AEChip chip){
        super(chip);

        resetFilter();
        if ( chip.getCanvas() != null && chip.getCanvas().getCanvas() != null ){
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas)chip.getCanvas().getCanvas();
        }
        // final String y = "y", x = "x", t = "type";
        setPropertyTooltip("drawHist", "Draw Histogram");
        setPropertyTooltip("histThresh", "Threshold of histogram points to display");
        setPropertyTooltip("reInit", "Reset counters after every replay");

        // New in TrackdefineFilter
        // Initialize histogram
        numX = chip.getSizeX();
        numY = chip.getSizeY();
        pixData = new float[numY][numX];

        resetFilter();
    }

    /**
     * Constructs the histogram
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    synchronized public EventPacket filterPacket (EventPacket in){
        if ( enclosedFilter != null ){
            in = enclosedFilter.filterPacket(in);
        }
        int i;

        int n = in.getSize();
        if ( n == 0 ){
            return in;
        }
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();
        
        // for each event only write it to the tmp buffers if it matches
        for ( Object obj:in ){
            BasicEvent e = (BasicEvent)obj;
            if ((e.x >= 0) && (e.y >= 0) &&
                    (e.x <numX) && (e.y < numY)) {
                // Increase histogram count
                pixData[e.y][e.x] += 1.0f;
                totalSum += 1.0f;
            }
     //       if ((pixData[e.y][e.x] / totalSum) > histThresh)
            pass(outItr,e);
        }

        return out;
    }

    private void pass (OutputEventIterator outItr,BasicEvent e){
        outItr.nextOutput().copyFrom(e);
    }

    private void pass (OutputEventIterator outItr,TypedEvent te){
        outItr.nextOutput().copyFrom(te);
    }

    synchronized public void resetFilter (){
//        startX=0; endX=chip.getSizeX();
//        startY=0; endY=chip.getSizeY();
//        startType=0; endType=chip.getNumCellTypes();

        int oldNumX = numX;
        int oldNumY = numY;
        numX = chip.getSizeX();
        numY = chip.getSizeY();

        if ((oldNumX != numX) || (oldNumY != numY))
            pixData = new float[numY][numX];

        System.out.println("Reset: " + reInit + " / " + totalSum);
        if (reInit) {
            pixData = new float[numY][numX];
            for (int i=0; i<numY; i++)
                for (int j=0; j<numX; j++)
                    pixData[i][j]=0.0f;

            totalSum = 0.0f;
        }
    }

    public void initFilter (){
        resetFilter();
    }

    private int clip (int val,int limit){
        if ( val > limit && limit != 0 ){
            return limit;
        } else if ( val < 0 ){
            return 0;
        }
        return val;
    }


    // Morphological erosion of track histogram
    private boolean[][] erosion() {
        boolean[][] bitmap = new boolean[numY][numX];
        int erSize = erosionSize;
        if (erSize <= 0) {
            // Return original image
            for (int i=0; i<numY; i++) {
                for (int j=0; j<numX; j++) {
                    if ((pixData[i][j] / totalSum) > histThresh) {
                        bitmap[i][j] = true;
                    } else
                        bitmap[i][j] = false;
                }
            }
            return bitmap;
        }


        for (int i=0; i<numY; i++) {
            for (int j=0; j<numX; j++) {
                boolean keep = true;
                for (int k=-erSize; k<=erSize; k++) {
                    for (int l=-erSize; l<=erSize; l++) {
                        int pixY = clip(i+k,numY);
                        int pixX = clip(j+l,numX);
                        if ((pixData[pixY][pixX] / totalSum) < histThresh) {
                            keep = false;
                            break;
                        }
                    }
                    if (keep == false)
                        break;
                }
                bitmap[i][j] = keep;
            }
        }

        return bitmap;
    }


    // Draws the histogram (only points above threshold)
    private void drawHistogram(GL gl) {
        // System.out.println("Drawing histogram..." + gl);
        boolean[][] bitmap = erosion();
        gl.glColor3f(1.0f,1.0f,0);
        gl.glBegin(gl.GL_POINTS);
        for (int i=0; i<numY; i++) {
            for (int j=0; j<numX; j++) {
                // if ((pixData[i][j] / totalSum) > histThresh) {
                if (bitmap[i][j]) {
                    gl.glVertex2i(j, i);
                    // gl.glRecti(i, j, i+1, j+1);
                }
            }
        }
        gl.glEnd();

    }

    private void histStatistics() {
        float maxH = 0;
        int count = 0;
        for (int i=0; i<numY; i++) {
            for (int j=0; j<numX; j++) {
                float cur = pixData[i][j] / totalSum;
                if (cur > maxH)
                    maxH = cur;
                if (cur > histThresh)
                    count++;
            }
        }

        System.out.println("Max: " + maxH + " / Count: " + count);
    }

    public void annotate (GLAutoDrawable drawable){
        if (counter < 1)
            counter++;
        else {
            if ( drawable == null || chip.getCanvas() == null ){
                System.out.println("Null, why?");
                return;
            }
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas)canvas.getCanvas();
            GL gl = drawable.getGL();

            // histStatistics();

            gl.glPushMatrix();
            if (drawHist) {
                drawHistogram(gl);
            }
            gl.glPopMatrix();
            
            counter = 0;
        }

    }


    public void update (Observable o,Object arg){
    }

    public void mousePressed (MouseEvent e){
        Point p = canvas.getPixelFromMouseEvent(e);
        startPoint = p;
        selecting = true;
    }

    public void mouseReleased (MouseEvent e){
        if ( startPoint == null ){
            return;
        }
        selecting = false;
    }

    public void mouseMoved (MouseEvent e){
        currentMousePoint = canvas.getPixelFromMouseEvent(e);
    }

    public void mouseExited (MouseEvent e){
        selecting = false;
    }

    public void mouseEntered (MouseEvent e){
    }

    public void mouseDragged (MouseEvent e){
        if ( startPoint == null ){
            return;
        }
    }

    private int min (int a,int b){
        return a < b ? a : b;
    }

    private int max (int a,int b){
        return a > b ? a : b;
    }

    public void mouseClicked (MouseEvent e){
        Point p = canvas.getPixelFromMouseEvent(e);
        clickedPoint = p;
    }

    @Override
    public synchronized void setFilterEnabled (boolean yes){
        super.setFilterEnabled(yes);
        if ( glCanvas == null ){
            return;
        }
        if ( yes ){
            glCanvas.addMouseListener(this);
            glCanvas.addMouseMotionListener(this);

        } else{
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
        }
    }

    public boolean isDrawHist() {
        return drawHist;
    }

    public void setDrawHist(boolean drawHist) {
        this.drawHist = drawHist;
        getPrefs().putBoolean("TrackdefineFilter.drawHist",drawHist);
    }

    public float getHistThresh() {
        return histThresh;
    }

    public void setHistThresh(float histThresh) {
        this.histThresh = histThresh;
        getPrefs().putFloat("TrackdefineFilter.histThresh",histThresh);
    }

    public boolean isReInit() {
        return reInit;
    }

    public void setReInit(boolean reInit) {
        this.reInit = reInit;
        getPrefs().putBoolean("TrackdefineFilter.reInit",reInit);
    }

    public int getErosionSize() {
        return erosionSize;
    }

    public void setErosionSize(int erosionSize) {
        this.erosionSize = erosionSize;
        getPrefs().putInt("TrackdefineFilter.erosionSize",erosionSize);
    }



}
