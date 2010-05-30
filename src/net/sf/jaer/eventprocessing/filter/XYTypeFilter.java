/*
 * RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.eventprocessing.filter;
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
 * An AE filter that filters for a range of x,y,type address. These values are persistent and can be used to filter out borders of the input or particular
 * types of input events. A rectangular region may either be passed (default) or blocked.
 *
 * @author tobi
 */
public class XYTypeFilter extends EventFilter2D implements FrameAnnotater,Observer,MouseListener,MouseMotionListener{
    public static String getDescription (){
        return "Filters a region defined by x, y, and event type ranges";
    }
    final private static float[] SELECT_COLOR = { .8f,0,0,.5f };
    private int startX = getPrefs().getInt("XYTypeFilter.startX",0);
    private int endX = getPrefs().getInt("XYTypeFilter.endX",0);
    private boolean xEnabled = getPrefs().getBoolean("XYTypeFilter.xEnabled",false);
    private int startY = getPrefs().getInt("XYTypeFilter.startY",0);
    private int endY = getPrefs().getInt("XYTypeFilter.endY",0);
    private boolean yEnabled = getPrefs().getBoolean("XYTypeFilter.yEnabled",false);
    private int startType = getPrefs().getInt("XYTypeFilter.startType",0);
    private int endType = getPrefs().getInt("XYTypeFilter.endType",0);
    private boolean typeEnabled = getPrefs().getBoolean("XYTypeFilter.typeEnabled",false);
    private boolean invertEnabled = getPrefs().getBoolean("XYTypeFilter.invertEnabled",false);
    public short x = 0, y = 0;
    public byte type = 0;
    private short xAnd;
    private short yAnd;
    private byte typeAnd;
    Rectangle selection = null;
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    Point startPoint = null, endPoint = null, clickedPoint = null;
    private Point currentMousePoint = null;
    int maxEvents = 0;
    int index = 0;
    private short xspike, yspike;
    private byte typespike;
    private int ts, repMeasure, i;
    volatile boolean selecting = false;
    private static float lineWidth = 1f;
    int startx, starty, endx, endy;

    public XYTypeFilter (AEChip chip){
        super(chip);
        setPropertyTooltip("invertEnabled","invert so that events inside region are blocked");
        resetFilter();
        if ( chip.getCanvas() != null && chip.getCanvas().getCanvas() != null ){
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas)chip.getCanvas().getCanvas();
        }
        final String y = "y", x = "x", t = "type";
        setPropertyTooltip(y,"YEnabled","filter based on row");
        setPropertyTooltip(y,"endY","ending row");
        setPropertyTooltip(y,"startY","starting row");
        setPropertyTooltip(x,"endX","ending column");
        setPropertyTooltip(x,"XEnabled","filter based on column");
        setPropertyTooltip(x,"startX","starting column");
        setPropertyTooltip(t,"startType","starting cell type");
        setPropertyTooltip(t,"typeEnabled","filter based on cell type");
        setPropertyTooltip(t,"endType","ending cell type");
        setPropertyTooltip("invertEnabled","invert filtering to pass events outside selection");
    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number put in
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
        if ( selecting ){
            return in;
        }

        // for each event only write it to the tmp buffers if it matches
        for ( Object obj:in ){
            BasicEvent e = (BasicEvent)obj;
            if ( !invertEnabled ){
                // if we pass all 'inside' tests then pass event, otherwise continue to next event
                if ( xEnabled && ( e.x < startX || e.x > endX ) ){
                    continue; // failed xtest, x outisde, goto next event
                }
                if ( yEnabled && ( e.y < startY || e.y > endY ) ){
                    continue;
                }
                if ( typeEnabled ){
                    TypedEvent te = (TypedEvent)e;
                    if ( te.type < startType || te.type > endType ){
                        continue;
                    }
                    pass(outItr,te);
                } else{
                    pass(outItr,e);
                }
            } else{
                // if we pass all outside tests then any test pass event
                if ( xEnabled && ( e.x >= startX && e.x <= endX ) ){
                    if ( yEnabled && ( e.y >= startY && e.y <= endY ) ){
                        if ( typeEnabled ){
                            TypedEvent te = (TypedEvent)e;
                            if ( te.type >= startType && te.type <= endType ){
                                continue;
                            }
                        } else{
                            continue;
                        }
                    }
                }
                pass(outItr,e);
            }
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

    public int getStartX (){
        return startX;
    }

    public void setStartX (int startX){
        startX = clip(startX,chip.getSizeX());
        this.startX = startX;
        getPrefs().putInt("XYTypeFilter.startX",startX);
        setXEnabled(true);
        support.firePropertyChange("startX",null,startX);
    }

    public int getEndX (){
        return endX;
    }

    public void setEndX (int endX){
        endX = clip(endX,chip.getSizeX());
        this.endX = endX;
        getPrefs().putInt("XYTypeFilter.endX",endX);
        setXEnabled(true);
        support.firePropertyChange("endX",null,endX);
    }

    public boolean isXEnabled (){
        return xEnabled;
    }

    public void setXEnabled (boolean xEnabled){
        this.xEnabled = xEnabled;
        getPrefs().putBoolean("XYTypeFilter.xEnabled",xEnabled);
    }

    public int getStartY (){
        return startY;
    }

    public void setStartY (int startY){
        startY = clip(startY,chip.getSizeY());
        this.startY = startY;
        getPrefs().putInt("XYTypeFilter.startY",startY);
        setYEnabled(true);
        support.firePropertyChange("startY",null,startY);
    }

    public int getEndY (){
        return endY;
    }

    public void setEndY (int endY){
        endY = clip(endY,chip.getSizeY());
        this.endY = endY;
        getPrefs().putInt("XYTypeFilter.endY",endY);
        setYEnabled(true);
        support.firePropertyChange("endY",null,endY);
    }

    public boolean isYEnabled (){
        return yEnabled;
    }

    public void setYEnabled (boolean yEnabled){
        this.yEnabled = yEnabled;
        getPrefs().putBoolean("XYTypeFilter.yEnabled",yEnabled);
    }

    public int getStartType (){
        return startType;
    }

    public void setStartType (int startType){
        startType = clip(startType,chip.getNumCellTypes());
        this.startType = startType;
        getPrefs().putInt("XYTypeFilter.startType",startType);
        setTypeEnabled(true);
    }

    public int getEndType (){
        return endType;
    }

    public void setEndType (int endType){
        endType = clip(endType,chip.getNumCellTypes());
        this.endType = endType;
        getPrefs().putInt("XYTypeFilter.endType",endType);
        setTypeEnabled(true);
    }

    public boolean isTypeEnabled (){
        return typeEnabled;
    }

    public void setTypeEnabled (boolean typeEnabled){
        this.typeEnabled = typeEnabled;
        getPrefs().putBoolean("XYTypeFilter.typeEnabled",typeEnabled);
    }

    public void annotate (GLAutoDrawable drawable){
        if ( drawable == null || chip.getCanvas() == null ){
            return;
        }
        canvas = chip.getCanvas();
        glCanvas = (GLCanvas)canvas.getCanvas();
        int sx = chip.getSizeX(), sy = chip.getSizeY();
        GL gl = drawable.getGL();
        if ( selecting ){
            drawSelection(gl,selection,SELECT_COLOR);
        }
        gl.glPushMatrix();
        {
            gl.glColor3f(0,0,1);
            gl.glLineWidth(2f);
            gl.glBegin(gl.GL_LINE_LOOP);
            gl.glVertex2i(startX,startY);
            gl.glVertex2i(endX,startY);
            gl.glVertex2i(endX,endY);
            gl.glVertex2i(startX,endY);
            gl.glEnd();
        }
        gl.glPopMatrix();

    }

    private void drawSelection (GL gl,Rectangle r,float[] c){
        if ( r == null ){
            return;
        }
        gl.glPushMatrix();
        gl.glColor3fv(c,0);
        gl.glLineWidth(lineWidth);
        gl.glTranslatef(-.5f,-.5f,0);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(selection.x,selection.y);
        gl.glVertex2f(selection.x + selection.width,selection.y);
        gl.glVertex2f(selection.x + selection.width,selection.y + selection.height);
        gl.glVertex2f(selection.x,selection.y + selection.height);
        gl.glEnd();
        gl.glPopMatrix();

    }

    public void update (Observable o,Object arg){
    }

    public boolean isInvertEnabled (){
        return invertEnabled;
    }

    public void setInvertEnabled (boolean invertEnabled){
        this.invertEnabled = invertEnabled;
        getPrefs().putBoolean("XYTypeFilter.invertEnabled",invertEnabled);
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
        getSelection(e);
        selecting = false;
        setStartX(startx);
        setEndX(endx);
        setStartY(starty);
        setEndY(endy);
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
        getSelection(e);
    }

    private void getSelection (MouseEvent e){
        Point p = canvas.getPixelFromMouseEvent(e);
        endPoint = p;
        startx = min(startPoint.x,endPoint.x);
        starty = min(startPoint.y,endPoint.y);
        endx = max(startPoint.x,endPoint.x);
        endy = max(startPoint.y,endPoint.y);
        int w = endx - startx;
        int h = endy - starty;
        selection = new Rectangle(startx,starty,w,h);

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
}
