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

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.gl2.GLUT;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;

/**
 * An AE filter that filters for a range of x,y,type address. These values are
 * persistent and can be used to filter out borders of the input or particular
 * types of input events. A rectangular region may either be passed (default) or
 * blocked.
 *
 * @author tobi
 */
@Description("Filters a region of interest (ROI) defined by x, y, and event type ranges")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class XYTypeFilter extends EventFilter2DMouseAdaptor implements FrameAnnotater, MouseListener, MouseMotionListener {

    final private static float[] SELECT_COLOR = {.8f, 0, 0, .5f};
    private int startX = getInt("startX", 0);
    private int endX = getInt("endX", 0);
    private boolean xEnabled = getBoolean("xEnabled", false);
    private int startY = getInt("startY", 0);
    private int endY = getInt("endY", 0);
    private boolean yEnabled = getBoolean("yEnabled", false);
    private int startType = getInt("startType", 0);
    private int endType = getInt("endType", 0);
    private boolean typeEnabled = getBoolean("typeEnabled", false);
    private boolean invertSelection = getBoolean("invertSelection", false);
    public short x = 0, y = 0;
    public byte type = 0;
    private short xAnd;
    private short yAnd;
    private byte typeAnd;
    private SelectionRectangle selection = null;
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    private Point startPoint = null, endPoint = null, clickedPoint = null;
    private int index = 0;
    private volatile boolean selecting = false;
    private static float lineWidth = 1f;
    private int startx, starty, endx, endy;
    private boolean multiSelectionEnabled = prefs().getBoolean("multiSelectionEnabled", false);
    private ArrayList<SelectionRectangle> selectionList = new ArrayList(1);
    protected boolean showTypeFilteringText = getBoolean("showTypeFilteringText", true);
    private boolean lockSelections = getBoolean("lockSelections", false);

    private boolean circularShapeFilter = getBoolean("circularShapeFilter", false);
    private int circularRadiusPixels = getInt("circularRadiusPixels", 50);

    private Point circularShapeCenterPoint = (Point) getObject("circularShapeCenterPoint", null);
    private boolean circularShapeCenterSelecting = false;

    public XYTypeFilter(AEChip chip) {
        super(chip);
        resetFilter();
        if ((chip.getCanvas() != null) && (chip.getCanvas().getCanvas() != null)) {
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }
        final String y = "y", x = "x", t = "type", c = "circle";

        setPropertyTooltip("invertEnabled", "invert so that events inside region are blocked");
        setPropertyTooltip(y, "YEnabled", "filter based on row");
        setPropertyTooltip(y, "endY", "ending row");
        setPropertyTooltip(y, "startY", "starting row");
        setPropertyTooltip(x, "endX", "ending column");
        setPropertyTooltip(x, "XEnabled", "filter based on column");
        setPropertyTooltip(x, "startX", "starting column");
        setPropertyTooltip(t, "startType", "starting cell type");
        setPropertyTooltip(t, "typeEnabled", "filter based on cell type");
        setPropertyTooltip(t, "endType", "ending cell type");
        setPropertyTooltip(t, "showTypeFilteringText", "show what type of events are passed through (usually 0=OFF, 1=ON for DVS and DAVIS)");
        setPropertyTooltip(c, "circularRadiusPixels", "radius of circular selection");
        setPropertyTooltip(c, "circularShapeFilter", "filter in based on circular disk shape");
        setPropertyTooltip("invertSelection", "invert filtering to pass events outside selection");
        setPropertyTooltip("lockSelections", "lock all selections to avoid inadvertent mouse selections");
        setPropertyTooltip("multiSelectionEnabled", "allows defining multiple regions to filter on");
        setPropertyTooltip("multiSelectionEnabled", "allows defining multiple regions to filter on");

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
        if (!(typeEnabled || xEnabled || yEnabled || circularShapeFilter)) {
            return in;// optimized when filter not enabled
        }

        int n = in.getSize();
        if (n == 0) {
            return in;
        }
//        checkOutputPacketEventType(in);
//        OutputEventIterator outItr = out.outputIterator();
        if (selecting || circularShapeCenterSelecting) {
            return in;
        }

        Iterator itr = null;
        if (in instanceof ApsDvsEventPacket) {
            itr = ((ApsDvsEventPacket) in).fullIterator();
        } else {
            itr = in.inputIterator();
        }
        // for each event only write it to the tmp buffers if it matches
        while (itr.hasNext()) {
//        for (Object obj : in) {
            BasicEvent e = (BasicEvent) (itr.next());
            if (e.isFilteredOut() || (e instanceof ApsDvsEvent && !((ApsDvsEvent) e).isDVSEvent())) {
                continue;
            }
            block(e);

            if (isCircularShapeFilter() && circularShapeCenterPoint != null) {
                Point p = new Point(e.x, e.y);
                double dist = circularShapeCenterPoint.distance(p);
                if (!invertSelection && dist <= getCircularRadiusPixels()) {
                    pass(e);
                } else if (invertSelection && dist > getCircularRadiusPixels()) {
                    pass(e);
                }
            } else if (multiSelectionEnabled) {
                if (selectionList.isEmpty()) {
                    return in;
                }
                if (!invertSelection) {
                    for (SelectionRectangle r : selectionList) {
                        if (r.contains(e)) {
                            pass(e);
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
                        pass(e);
                    }

                }
            } else if (!invertSelection) {
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
                    pass(e);
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
    private void pass(BasicEvent e) {
        e.setFilteredOut(false);
    }

    private void block(BasicEvent e) {
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
        doLoadMultiSelection();
        resetFilter();
    }

    @Override
    public synchronized void cleanup() {
        super.cleanup();
        doSaveMultiSelection();
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
        putInt("startX", startX);
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
        putInt("endX", endX);
        getSupport().firePropertyChange("endX", old, endX);
        setXEnabled(true);

    }

    public boolean isXEnabled() {
        return xEnabled;
    }

    public void setXEnabled(boolean xEnabled) {
        boolean old = this.xEnabled;
        this.xEnabled = xEnabled;
        putBoolean("xEnabled", xEnabled);
        getSupport().firePropertyChange("XEnabled", old, xEnabled);

    }

    public int getStartY() {
        return startY;
    }

    public void setStartY(int startY) {
        int old = this.starty;
        startY = clip(startY, chip.getSizeY());
        this.startY = startY;
        putInt("startY", startY);
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
        putInt("endY", endY);
        getSupport().firePropertyChange("endY", old, endY);
        setYEnabled(true);

    }

    public boolean isYEnabled() {
        return yEnabled;
    }

    public void setYEnabled(boolean yEnabled) {
        boolean old = this.yEnabled;
        this.yEnabled = yEnabled;
        putBoolean("yEnabled", yEnabled);
        getSupport().firePropertyChange("YEnabled", old, yEnabled);

    }

    public int getStartType() {
        return startType;
    }

    public void setStartType(int startType) {
        int old = this.startType;
        startType = clip(startType, chip.getNumCellTypes());
        this.startType = startType;
        putInt("startType", startType);
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
        putInt("endType", endType);
        getSupport().firePropertyChange("endType", old, endType);
        setTypeEnabled(true);

    }

    public boolean isTypeEnabled() {
        return typeEnabled;
    }

    public void setTypeEnabled(boolean typeEnabled) {
        boolean old = this.typeEnabled;
        this.typeEnabled = typeEnabled;
        putBoolean("typeEnabled", typeEnabled);
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
        if (selecting || circularShapeCenterSelecting) {
            drawSelection(gl, selection, SELECT_COLOR);
        }
        gl.glPushMatrix();
        {
            if (isCircularShapeFilter() && circularShapeCenterPoint != null) {
                gl.glColor3f(0, 0, 1);
                gl.glLineWidth(2f);
                gl.glPushMatrix();
                DrawGL.drawCross(gl, circularShapeCenterPoint.x, circularShapeCenterPoint.y, 10, 0);
                gl.glPopMatrix();
                gl.glPushMatrix();
                DrawGL.drawCircle(gl, circularShapeCenterPoint.x, circularShapeCenterPoint.y, getCircularRadiusPixels(), 64);
                gl.glPopMatrix();
            } else if (!multiSelectionEnabled) {
                gl.glColor3f(0, 0, 1);
                gl.glLineWidth(2f);
                gl.glBegin(GL.GL_LINE_LOOP);
                gl.glVertex2i(startX, startY);
                gl.glVertex2i(endX + 1, startY);
                gl.glVertex2i(endX + 1, endY + 1);
                gl.glVertex2i(startX, endY + 1);
                gl.glEnd();
            } else {
                for (SelectionRectangle r : selectionList) {
                    r.draw(gl);
                }
            }
        }
        gl.glPopMatrix();

        if (showTypeFilteringText && typeEnabled) {
            gl.glPushMatrix();
            {
                final GLUT glut = new GLUT();
                gl.glColor3f(.2f, .2f, .8f); // must set color before raster position (raster position is like glVertex)
                gl.glRasterPos3f(10, 2 * chip.getSizeY() / 3, 0);
                String s = String.format("XYTypeFilter: Only passing type %d - %d", startType, endType);
                glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, s);
            }
            gl.glPopMatrix();
        }
    }

    private void drawSelection(GL2 gl, Rectangle r, float[] c) {
        if (r == null) {
            return;
        }
        gl.glPushMatrix();
        gl.glColor3fv(c, 0);
        gl.glLineWidth(4);
//        gl.glTranslatef(-.5f, -.5f, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
//        System.out.println("selection="+selection.toString());
        gl.glVertex2f(selection.x, selection.y);
        gl.glVertex2f(selection.x + selection.width, selection.y);
        gl.glVertex2f(selection.x + selection.width, selection.y + selection.height);
        gl.glVertex2f(selection.x, selection.y + selection.height);
        gl.glEnd();
        gl.glPopMatrix();

    }

    public boolean isInvertSelection() {
        return invertSelection;
    }

    public void setInvertSelection(boolean invertSelection) {
        this.invertSelection = invertSelection;
        putBoolean("invertSelection", invertSelection);

    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (lockSelections) {
            return;
        }
        if (isDontProcessMouse()) {
            return;
        }
        Point p = canvas.getPixelFromMouseEvent(e);
        startPoint = p;
        selecting = true;
    }


    @Override
    public void mouseReleased(MouseEvent e) {
        if (lockSelections) {
            return;
        }
        if (isDontProcessMouse()) {
            return;
        }
        if ((startPoint == null)) {
            return;
        }
        selection = getSelection(e); // TODO sets and returns same object, not really good behavior
        if (multiSelectionEnabled) {
            selectionList.add(selection);
        }
        setStartX(startx);
        setEndX(endx - 1);
        setStartY(starty);
        setEndY(endy - 1);
        selecting = false;

    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (lockSelections) {
            return;
        }
        selecting = false;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (lockSelections) {
            return;
        }
        if (startPoint == null) {
            return;
        }
        if (isDontProcessMouse()) {
            return;
        }
        getSelection(e);
    }

    private SelectionRectangle getSelection(MouseEvent e) {
        Point p = canvas.getPixelFromMouseEvent(e);
        endPoint = p;
        startx = min(startPoint.x, endPoint.x);
        starty = min(startPoint.y, endPoint.y);
        endx = max(startPoint.x, endPoint.x) + 1;
        endy = max(startPoint.y, endPoint.y) + 1;
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
        if (circularShapeCenterSelecting) {
            circularShapeCenterSelecting = false;
            circularShapeCenterPoint = new Point(clickedPoint);
            putObject("circularShapeCenterPoint", circularShapeCenterPoint);
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
        prefs().putBoolean("multiSelectionEnabled", multiSelectionEnabled);

    }

    /**
     * returns the number of selected cells.
     *
     * @return the number of selected cells (number of pixels * type range)
     */
    public int getNumCellsSelected() {
        return getNumPixelsSelected() * getNumTypesSelected();
    }

    /**
     * returns the number of selected pixels, including no selection (all
     * pixels) and multi-selection rectangles.
     *
     * @return the number of selected pixels
     */
    public int getNumPixelsSelected() {
        if (!isMultiSelectionEnabled()) {
            int dx = (isXEnabled() ? (int) Math.abs(getEndX() - getStartX()) : getChip().getSizeX());
            int dy = (isYEnabled() ? (int) Math.abs(getEndY() - getStartY()) : getChip().getSizeY());
            int npix = dx * dy;
            return npix;
        } else {
            int npix = 0;
            for (SelectionRectangle r : selectionList) {
                npix += r.getNumPixels();
            }
            return npix;
        }
    }

    private int getNumTypesSelected() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private class SelectionRectangle extends Rectangle implements Serializable {

        public SelectionRectangle(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        public boolean contains(BasicEvent e) {
//            return contains(e.x, e.y);
            return (e.x >= x && e.x < x + width) && (e.y >= y && e.y < y + height);
        }

        public int getNumPixels() {
            return width * height;
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

    public final synchronized void doLoadMultiSelection() {

        try {
            SavedMultiSelection ss = (SavedMultiSelection) getObject("multiSelection", null);
            if (ss == null || ss.size == 0) {
                return;
            }
            for (int i = 0; i < ss.size; i++) {
                selectionList.add(new SelectionRectangle(ss.xValues[i], ss.yValues[i], ss.widthValues[i], ss.heightValues[i]));
            }
            log.info(String.format("loaded %d selection rectangles from preferences", ss.size));
        } catch (Exception e) {
            log.warning("couldn't load throttle profile: " + e);
        }
    }

    synchronized public void doSaveMultiSelection() {
        if (selectionList == null || selectionList.isEmpty()) {
//            log.warning("no profile to save");
            return;
        }
        try {
            SavedMultiSelection ss = new SavedMultiSelection(this);
            putObject("multiSelection", ss);
            log.info(String.format("Saved %d rectangles to preferences", ss.size));
        } catch (Exception e) {
            log.warning("couldn't save profile: " + e);
        }

    }

    public void doSelectCircularShapeCenterPoint() {
        circularShapeCenterSelecting = true;
    }

    /**
     * @return the showTypeFilteringText
     */
    public boolean isShowTypeFilteringText() {
        return showTypeFilteringText;
    }

    /**
     * @param showTypeFilteringText the showTypeFilteringText to set
     */
    public void setShowTypeFilteringText(boolean showTypeFilteringText) {
        this.showTypeFilteringText = showTypeFilteringText;
    }

    /**
     * @return the circularShapeFilter
     */
    public boolean isCircularShapeFilter() {
        return circularShapeFilter;
    }

    /**
     * @param circularShapeFilter the circularShapeFilter to set
     */
    public void setCircularShapeFilter(boolean circularShapeFilter) {
        boolean old = this.circularShapeFilter;
        this.circularShapeFilter = circularShapeFilter;
        putBoolean("circularShapeFilter", circularShapeFilter);
        getSupport().firePropertyChange("circularShapeFilter", old, this.circularShapeFilter);
    }

    /**
     * @return the circularRadiusPixels
     */
    public int getCircularRadiusPixels() {
        return circularRadiusPixels;
    }

    /**
     * @param circularRadiusPixels the circularRadiusPixels to set
     */
    public void setCircularRadiusPixels(int circularRadiusPixels) {
        this.circularRadiusPixels = circularRadiusPixels;
        putInt("circularRadiusPixels", circularRadiusPixels);
    }

    /**
     * @return the lockSelections
     */
    public boolean isLockSelections() {
        return lockSelections;
    }

    /**
     * @param lockSelections the lockSelections to set
     */
    public void setLockSelections(boolean lockSelections) {
        this.lockSelections = lockSelections;
        putBoolean("lockSelections", lockSelections);
    }

    private static class SavedMultiSelection implements Serializable {

        int size, xValues[], yValues[], widthValues[], heightValues[];

        public SavedMultiSelection(XYTypeFilter xyTypeFilter) {
            if (xyTypeFilter.selectionList == null || xyTypeFilter.selectionList.isEmpty()) {
                size = 0;
                return;
            }
            size = xyTypeFilter.selectionList.size();
            xValues = new int[size];
            yValues = new int[size];
            widthValues = new int[size];
            heightValues = new int[size];
            Iterator iterator = xyTypeFilter.selectionList.iterator();
            int j = 0;
            while (iterator.hasNext()) {
                SelectionRectangle tmpRect = (SelectionRectangle) iterator.next();
                xValues[j] = tmpRect.x;
                yValues[j] = tmpRect.y;
                widthValues[j] = tmpRect.width;
                heightValues[j] = tmpRect.height;
                j++;
            }
        }
    }

}
