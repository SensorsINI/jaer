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

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * An AE filter that filters for a range of x,y,type address. These values are
 * persistent and can be used to filter out borders of the input or particular
 * types of input events. A rectangular region may either be passed (default) or
 * blocked.
 *
 * @author tobi
 */
@Description("Filters a region defined by x, y, and event type ranges")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class XYTypeFilter extends EventFilter2D implements FrameAnnotater, Observer, MouseListener, MouseMotionListener {

    final private static float[] SELECT_COLOR = {.8f, 0, 0, .5f};
    private int startX = getPrefs().getInt("XYTypeFilter.startX", 0);
    private int endX = getPrefs().getInt("XYTypeFilter.endX", 0);
    private boolean xEnabled = getPrefs().getBoolean("XYTypeFilter.xEnabled", false);
    private int startY = getPrefs().getInt("XYTypeFilter.startY", 0);
    private int endY = getPrefs().getInt("XYTypeFilter.endY", 0);
    private boolean yEnabled = getPrefs().getBoolean("XYTypeFilter.yEnabled", false);
    private int startType = getPrefs().getInt("XYTypeFilter.startType", 0);
    private int endType = getPrefs().getInt("XYTypeFilter.endType", 0);
    private boolean typeEnabled = getPrefs().getBoolean("XYTypeFilter.typeEnabled", false);
    private boolean invertEnabled = getPrefs().getBoolean("XYTypeFilter.invertEnabled", false);
    public short x = 0, y = 0;
    public byte type = 0;
    private short xAnd;
    private short yAnd;
    private byte typeAnd;
    private SelectionRectangle selection = null;
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    private Point startPoint = null, endPoint = null, clickedPoint = null;
    private Point currentMousePoint = null;
    private int maxEvents = 0;
    private int index = 0;
    private short xspike, yspike;
    private byte typespike;
    private int ts, repMeasure, i;
    private volatile boolean selecting = false;
    private static float lineWidth = 1f;
    private int startx, starty, endx, endy;
    private boolean multiSelectionEnabled = prefs().getBoolean("XYTypeFilter.multiSelectionEnabled", false);
    private ArrayList<SelectionRectangle> selectionList = new ArrayList(1);

    synchronized public void doEraseSelections() {
        selectionList.clear();
        setStartX(0);
        setEndX(chip.getSizeX() - 1);
        setStartY(0);
        setEndY(chip.getSizeY() - 1);
        setStartType(0);
        setEndType(chip.getNumCellTypes() - 1);
        setXEnabled(false);
        setYEnabled(false);
        setTypeEnabled(false);
    }

    public XYTypeFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("invertEnabled", "invert so that events inside region are blocked");
        resetFilter();
        if ((chip.getCanvas() != null) && (chip.getCanvas().getCanvas() != null)) {
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }
        final String y = "y", x = "x", t = "type";
        setPropertyTooltip(y, "YEnabled", "filter based on row");
        setPropertyTooltip(y, "endY", "ending row");
        setPropertyTooltip(y, "startY", "starting row");
        setPropertyTooltip(x, "endX", "ending column");
        setPropertyTooltip(x, "XEnabled", "filter based on column");
        setPropertyTooltip(x, "startX", "starting column");
        setPropertyTooltip(t, "startType", "starting cell type");
        setPropertyTooltip(t, "typeEnabled", "filter based on cell type");
        setPropertyTooltip(t, "endType", "ending cell type");
        setPropertyTooltip("invertEnabled", "invert filtering to pass events outside selection");
        setPropertyTooltip("multiSelectionEnabled", "allows defining multiple regions to filter on");

        doLoadMultiSelection();
    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number. filtering may occur
     * in place in the in packet.
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        int i;
        if (!(typeEnabled || xEnabled || yEnabled)) {
            return in;// optimized when filter not enabled
        }

        int n = in.getSize();
        if (n == 0) {
            return in;
        }
//        checkOutputPacketEventType(in);
//        OutputEventIterator outItr = out.outputIterator();
        if (selecting) {
            return in;
        }

        Iterator itr=null;
        if(in instanceof ApsDvsEventPacket){
            itr=((ApsDvsEventPacket)in).fullIterator();
        }else{
            itr=in.inputIterator();
        }
        // for each event only write it to the tmp buffers if it matches
        while(itr.hasNext()){
//        for (Object obj : in) {
            BasicEvent e = (BasicEvent) (itr.next());
            if (e.isFilteredOut() || (e instanceof ApsDvsEvent && !((ApsDvsEvent)e).isDVSEvent())) {
                continue;
            }
            block(e);
            
            if (multiSelectionEnabled) {
                if (selectionList.isEmpty()) {
                    return in;
                }
                if (!invertEnabled) {
                    for (SelectionRectangle r : selectionList) {
                        if (r.contains(e)) {
                            pass( e);
                            break;
                        }
                    }
                } else {
                    boolean blocked = false;
                    for (SelectionRectangle r : selectionList) {
                        if (r.contains(e)) {
                            blocked = true;
                        }
                    }
                    if (!blocked) {
                        pass( e);
                    }

                }
            } else if (!invertEnabled) {
                // if we pass all 'inside' tests then pass event, otherwise continue to next event
                if (xEnabled && ((e.x < startX) || (e.x > endX))) {
                    continue; // failed xtest, x outisde, goto next event
                }
                if (yEnabled && ((e.y < startY) || (e.y > endY))) {
                    continue;
                }
                if (typeEnabled) {
                    TypedEvent te = (TypedEvent) e;
                    if ((te.type < startType) || (te.type > endType)) {
                        continue;
                    }
                    pass(te);
                } else {
                    pass( e);
                }
            } else {
                // if we pass all outside tests then any test pass event
                if (!(xEnabled && ((e.x < startX) || (e.x > endX)))
                        && !(yEnabled && ((e.y < startY) || (e.y > endY)))) {
                    continue; // failed xtest, x outisde, goto next event
                }
                if (typeEnabled) {
                    TypedEvent te = (TypedEvent) e;
                    if (!((te.type < startType) || (te.type > endType))) {
                        continue;
                    }
                    pass(te);
                } else {
                    pass(e);
                }
            }
        }

        return in;
    }

//    private void pass(OutputEventIterator outItr, BasicEvent e) {
//        outItr.nextOutput().copyFrom(e);
//    }
//
//    private void pass(OutputEventIterator outItr, TypedEvent te) {
//        outItr.nextOutput().copyFrom(te);
//    }
    
    private void pass(BasicEvent e){
        e.setFilteredOut(false);
    }
    
    private void block(BasicEvent e){
        e.setFilteredOut(true);
    }

    @Override
    synchronized public void resetFilter() {
//        startX=0; endX=chip.getSizeX();
//        startY=0; endY=chip.getSizeY();
//        startType=0; endType=chip.getNumCellTypes();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    private int clip(int val, int limit) {
        if ((val > limit) && (limit != 0)) {
            return limit;
        } else if (val < 0) {
            return 0;
        }
        return val;
    }

    public int getStartX() {
        return startX;
    }

    public void setStartX(int startX) {
        int old = this.startX;
        startX = clip(startX, chip.getSizeX());
        this.startX = startX;
        getPrefs().putInt("XYTypeFilter.startX", startX);
        getSupport().firePropertyChange("startX", old, startX);
        setXEnabled(true);

    }

    public int getEndX() {
        return endX;
    }

    public void setEndX(int endX) {
        int old = this.endX;
        endX = clip(endX, chip.getSizeX());
        this.endX = endX;
        getPrefs().putInt("XYTypeFilter.endX", endX);
        getSupport().firePropertyChange("endX", old, endX);
        setXEnabled(true);

    }

    public boolean isXEnabled() {
        return xEnabled;
    }

    public void setXEnabled(boolean xEnabled) {
        boolean old = this.xEnabled;
        this.xEnabled = xEnabled;
        getPrefs().putBoolean("XYTypeFilter.xEnabled", xEnabled);
        getSupport().firePropertyChange("XEnabled", old, xEnabled);

    }

    public int getStartY() {
        return startY;
    }

    public void setStartY(int startY) {
        int old = this.starty;
        startY = clip(startY, chip.getSizeY());
        this.startY = startY;
        getPrefs().putInt("XYTypeFilter.startY", startY);
        getSupport().firePropertyChange("startY", old, startY);

        setYEnabled(true);
    }

    public int getEndY() {
        return endY;
    }

    public void setEndY(int endY) {
        int old = this.endY;
        endY = clip(endY, chip.getSizeY());
        this.endY = endY;
        getPrefs().putInt("XYTypeFilter.endY", endY);
        getSupport().firePropertyChange("endY", old, endY);
        setYEnabled(true);

    }

    public boolean isYEnabled() {
        return yEnabled;
    }

    public void setYEnabled(boolean yEnabled) {
        boolean old = this.yEnabled;
        this.yEnabled = yEnabled;
        getPrefs().putBoolean("XYTypeFilter.yEnabled", yEnabled);
        getSupport().firePropertyChange("YEnabled", old, yEnabled);

    }

    public int getStartType() {
        return startType;
    }

    public void setStartType(int startType) {
        int old = this.startType;
        startType = clip(startType, chip.getNumCellTypes());
        this.startType = startType;
        getPrefs().putInt("XYTypeFilter.startType", startType);
        getSupport().firePropertyChange("startType", old, startType);
        setTypeEnabled(true);

    }

    public int getEndType() {
        return endType;
    }

    public void setEndType(int endType) {
        int old = this.endType;
        endType = clip(endType, chip.getNumCellTypes());
        this.endType = endType;
        getPrefs().putInt("XYTypeFilter.endType", endType);
        getSupport().firePropertyChange("endType", old, endType);
        setTypeEnabled(true);

    }

    public boolean isTypeEnabled() {
        return typeEnabled;
    }

    public void setTypeEnabled(boolean typeEnabled) {
        boolean old = this.typeEnabled;
        this.typeEnabled = typeEnabled;
        getPrefs().putBoolean("XYTypeFilter.typeEnabled", typeEnabled);
        getSupport().firePropertyChange("typeEnabled", old, typeEnabled);

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if ((drawable == null) || (chip.getCanvas() == null)) {
            return;
        }
        canvas = chip.getCanvas();
        glCanvas = (GLCanvas) canvas.getCanvas();
//        int sx = chip.getSizeX(), sy = chip.getSizeY();
        GL2 gl = drawable.getGL().getGL2();
        if (selecting) {
            drawSelection(gl, selection, SELECT_COLOR);
        }
        gl.glPushMatrix();
        {
            if (!multiSelectionEnabled) {
                gl.glColor3f(0, 0, 1);
                gl.glLineWidth(2f);
                gl.glBegin(GL.GL_LINE_LOOP);
                gl.glVertex2i(startX, startY);
                gl.glVertex2i(endX, startY);
                gl.glVertex2i(endX, endY);
                gl.glVertex2i(startX, endY);
                gl.glEnd();
            } else {
                for (SelectionRectangle r : selectionList) {
                    r.draw(gl);
                }
            }
        }
        gl.glPopMatrix();

    }

    private void drawSelection(GL2 gl, Rectangle r, float[] c) {
        if (r == null) {
            return;
        }
        gl.glPushMatrix();
        gl.glColor3fv(c, 0);
        gl.glLineWidth(lineWidth);
        gl.glTranslatef(-.5f, -.5f, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(selection.x, selection.y);
        gl.glVertex2f(selection.x + selection.width, selection.y);
        gl.glVertex2f(selection.x + selection.width, selection.y + selection.height);
        gl.glVertex2f(selection.x, selection.y + selection.height);
        gl.glEnd();
        gl.glPopMatrix();

    }

    @Override
    public void update(Observable o, Object arg) {
    }

    public boolean isInvertEnabled() {
        return invertEnabled;
    }

    public void setInvertEnabled(boolean invertEnabled) {
        this.invertEnabled = invertEnabled;
        getPrefs().putBoolean("XYTypeFilter.invertEnabled", invertEnabled);

    }

    @Override
    public void mousePressed(MouseEvent e) {
        Point p = canvas.getPixelFromMouseEvent(e);
        startPoint = p;
        selecting = true;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if ((startPoint == null) || canvas.getPixelFromMouseEvent(e).equals(startPoint)) {
            return;
        }
        selection = getSelection(e); // TODO sets and returns same object, not really good behavior
        if (multiSelectionEnabled) {
            selectionList.add(selection);
        }
        setStartX(startx);
        setEndX(endx);
        setStartY(starty);
        setEndY(endy);
        selecting = false;

    }

    @Override
    public void mouseMoved(MouseEvent e) {
        currentMousePoint = canvas.getPixelFromMouseEvent(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        selecting = false;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (startPoint == null) {
            return;
        }
        getSelection(e);
    }

    private SelectionRectangle getSelection(MouseEvent e) {
        Point p = canvas.getPixelFromMouseEvent(e);
        endPoint = p;
        startx = min(startPoint.x, endPoint.x);
        starty = min(startPoint.y, endPoint.y);
        endx = max(startPoint.x, endPoint.x);
        endy = max(startPoint.y, endPoint.y);
        int w = endx - startx;
        int h = endy - starty;
        selection = new SelectionRectangle(startx, starty, w, h);
        return selection;

    }

    private int min(int a, int b) {
        return a < b ? a : b;
    }

    private int max(int a, int b) {
        return a > b ? a : b;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Point p = canvas.getPixelFromMouseEvent(e);
        log.info(e.getSource().toString());
        clickedPoint = p;
    }
// already handled by setSelected below
//    @Override
//    public synchronized void setFilterEnabled (boolean yes){
//        super.setFilterEnabled(yes);
//        if ( glCanvas == null ){
//            return;
//        }
//        if ( yes ){
//            glCanvas.addMouseListener(this);
//            glCanvas.addMouseMotionListener(this);
//
//        } else{
//            glCanvas.removeMouseListener(this);
//            glCanvas.removeMouseMotionListener(this);
//        }
//    }

    @Override
    public void setSelected(boolean yes) {
        super.setSelected(yes);
        if (glCanvas == null) {
            return;
        }
//          log.info("selected="+yes);
        if (yes) {
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
            glCanvas.addMouseListener(this);
            glCanvas.addMouseMotionListener(this);

        } else {
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
        }

    }

    /**
     * @return the multiSelectionEnabled
     */
    public boolean isMultiSelectionEnabled() {
        return multiSelectionEnabled;
    }

    /**
     * @param multiSelectionEnabled the multiSelectionEnabled to set
     */
    public void setMultiSelectionEnabled(boolean multiSelectionEnabled) {
        this.multiSelectionEnabled = multiSelectionEnabled;
        prefs().putBoolean("XYTypeFilter.multiSelectionEnabled", multiSelectionEnabled);

    }

    private class SelectionRectangle extends Rectangle implements Serializable {

        public SelectionRectangle(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        public boolean contains(BasicEvent e) {
            return contains(e.x, e.y);
        }

        private void draw(GL2 gl) {
            gl.glColor3f(0, 0, 1);
            gl.glLineWidth(2f);
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2i(x, y);
            gl.glVertex2i(x + width, y);
            gl.glVertex2i(x + width, y + height);
            gl.glVertex2i(x, y + height);
            gl.glEnd();
        }
    }

    public final synchronized void doLoadMultiSelection() {

        try {

            byte[] b = prefs().getByteArray("XYTypeFilter.multiSelection", null);
            if (b == null) {
//					log.info("no MultiSelection saved in preferences, can't load it");
                return;
            }
            ByteArrayInputStream bis = new ByteArrayInputStream(b);
            ObjectInputStream ois = new ObjectInputStream(bis);
            Object o = ois.readObject();
            if (o == null) {
                throw new NullPointerException("Couldn't read x values for the MuliSelection from preferences");
            }
            int[] xValues = (int[]) o;
            o = ois.readObject();
            if (o == null) {
                throw new NullPointerException("Couldn't read y values for the MuliSelection from preferences");
            }
            int[] yValues = (int[]) o;
            o = ois.readObject();
            if (o == null) {
                throw new NullPointerException("Couldn't read x values for the MuliSelection from preferences");
            }
            int[] widthValues = (int[]) o;
            o = ois.readObject();
            if (o == null) {
                throw new NullPointerException("Couldn't read x values for the MuliSelection from preferences");
            }
            int[] heightValues = (int[]) o;
            for (int n = 0; n < xValues.length; n++) {
                selectionList.add(new SelectionRectangle(xValues[n], yValues[n], widthValues[n], heightValues[n]));
            }
            ois.close();
            bis.close();
            log.info("loaded selection from preferencdes");
        } catch (Exception e) {
            log.warning("couldn't load throttle profile: " + e);
        }
    }

    synchronized public void doSaveMultiSelection() {
        if (selectionList == null) {
            log.warning("no profile to save");
            return;
        }
        try {
            int xValue[] = new int[selectionList.size()];
            int yValue[] = new int[selectionList.size()];
            int widthValue[] = new int[selectionList.size()];
            int heightValue[] = new int[selectionList.size()];
            Iterator iterator = selectionList.iterator();
            int j = 0;
            while (iterator.hasNext()) {
                SelectionRectangle tmpRect = (SelectionRectangle) iterator.next();
                xValue[j] = tmpRect.x;
                yValue[j] = tmpRect.y;
                widthValue[j] = tmpRect.width;
                heightValue[j] = tmpRect.height;
                j++;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(xValue);
            oos.writeObject(yValue);
            oos.writeObject(widthValue);
            oos.writeObject(heightValue);
            prefs().putByteArray("XYTypeFilter.multiSelection", bos.toByteArray());
            oos.close();
            bos.close();
            log.info("multi selection saveed to preferences");
        } catch (Exception e) {
            log.warning("couldn't save profile: " + e);
        }

    }

}
