/*
 * Last updated on March 23, 2010, 3:49 AM
 *
 *  * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

import com.sun.opengl.util.GLUT;
import java.awt.Color;
import javax.media.opengl.GL;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.*;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author Jun Haeng Lee @ SAIT
 */
public class BlurringFilter2D extends EventFilter2D  implements FrameAnnotater, Observer {

    // properties
    private int cellLifeTimeUs = getPrefs().getInt("BlurringFilter2D.cellLifeTimeUs", 10000);
    private int cellSizePixels = getPrefs().getInt("BlurringFilter2D.cellSizePixels", 8);
    protected float mixingFactor = getPrefs().getFloat("BlurringFilter2D.mixingFactor", 0.05f);
    private boolean showCells = getPrefs().getBoolean("BlurringFilter2D.showCells", true);
    private boolean showCellMass = getPrefs().getBoolean("BlurringFilter2D.showCellMass", false);
    private int thresholdEventsForVisibleCell = getPrefs().getInt("BlurringFilter2D.thresholdEventsForVisibleCell", 20);
    private int thresholdMassForVisibleCell = getPrefs().getInt("BlurringFilter2D.thresholdMassForVisibleCell", 7);
    private float updateIntervalMs = getPrefs().getFloat("BlurringFilter2D.updateIntervalMs", 25);
    private boolean showBorderCellsOnly = getPrefs().getBoolean("BlurringFilter2D.showBorderCellsOnly", false);
    private boolean filledCells = getPrefs().getBoolean("BlurringFilter2D.filledCells", false);


    // variables
    private int numOfCellsX = 0, numOfCellsY = 0;
    private ArrayList <Cell> cellArray = new ArrayList(chip.getSizeX()*chip.getSizeY());
    protected AEChip mychip;
    protected int lastTime;
    protected Random random = new Random();

    public BlurringFilter2D(AEChip chip) {
        super(chip);
        this.mychip = chip;
        initFilter();
        chip.addObserver(this);

        final String sizing = "Sizing", movement = "Movement", lifetime = "Lifetime", disp = "Display", global = "Global", update = "Update", logging = "Logging";

        setPropertyTooltip(lifetime, "cellLifeTimeUs", "Event lifetime");
        setPropertyTooltip(lifetime, "thresholdEventsForVisibleCell", "Cell needs this many events to be visible");
        setPropertyTooltip(lifetime, "thresholdMassForVisibleCell", "Cell needs this much mass to be visible");
        setPropertyTooltip(disp, "showCells", "Show detected cells");
        setPropertyTooltip(disp, "filledCells", "Filled symbols");
        setPropertyTooltip(disp, "showBorderCellsOnly", "Sweep out all cells except border cells");
        setPropertyTooltip(disp, "showCellMass", "Show mass of the detected cells");
        setPropertyTooltip(sizing, "cellSizePixels", "Cell size in number of pixels");
        setPropertyTooltip(movement, "mixingFactor", "Cell size in number of pixels");
        setPropertyTooltip(global, "updateIntervalMs", "cluster list is pruned and clusters are merged with at most this interval in ms");
    }

    @Override
    public String toString() {
        String s = cellArray != null ? Integer.toString(numOfCellsX).concat(" by ").concat(Integer.toString(numOfCellsY)) : null;
        String s2 = "RectangularClusterTracker with " + s + " cells ";
        return s2;
    }

    public void update(Observable o, Object arg) {
        initFilter();
    }


    static enum CellType {CORNER_00, CORNER_01, CORNER_10, CORNER_11, EDGE_0Y, EDGE_1Y, EDGE_X0, EDGE_X1, INSIDE}
    static enum CellProperty {NOT_VISIBLE, VISIBLE_ISOLATED, VISIBLE_HAS_NEIGHBOR, VISIBLE_BORDER, VISIBLE_INSIDE}
    public class Cell {

        // Cell index
        public Point2D.Float cellIndex = new Point2D.Float();

        /** location of cell in pixels */
        public Point2D.Float location = new Point2D.Float(); // location in chip pixels

        /** One of CellType */
        CellType cellType;
        CellProperty cellProperty;
        protected boolean visible = false;

        public void reset(){
            mass = 1;
            numEvents = 0;
            numOfNeighbors = 0;
            setProperty(CellProperty.NOT_VISIBLE);
        }

        protected Color color = null;
        /** Number of events collected by this cell.*/
        protected int numEvents = 0;
        /** The "mass" of the cell is the weighted number of events it has collected.
         * The mass decays over time and is incremented by one by each collected event.
         * The mass decays with a first order time constant of cellLifetimeWithoutSupportUs in us.
         */
        protected float mass = 1;

        // number of neighbors
        protected int numOfNeighbors = 0;

        /** This is the last time in timestamp ticks that the cell was updated, either by an event
         * or by a regular update such as {@link #updateCellLocations(int)}. This time can be used to
         * compute postion updates given a cell velocity and time now.
         */
        protected int lastUpdateTime;
        private int cellNumber;
        /** Flag which is set true once a cell has obtained sufficient support. */
        private float[] rgb = new float[4];

        /** Construct a cell with index. */
        @SuppressWarnings("empty-statement")
        public Cell(int indexX, int indexY) {
            float hue = random.nextFloat();
            Color c = Color.getHSBColor(hue, 1f, 1f);
            setColor(c);

            if(indexX < 0 || indexY < 0 || indexX >= numOfCellsX || indexY >= numOfCellsY)
            {
                // exception
            }

            cellIndex.x = (float) indexX;
            cellIndex.y = (float) indexY;

            location.x = (cellIndex.x + 1) * cellSizePixels/2;
            location.y = (cellIndex.y + 1) * cellSizePixels/2;

            numEvents = 0;

            cellNumber = (int) cellIndex.x + (int)cellIndex.y*numOfCellsX;

            setProperty(CellProperty.NOT_VISIBLE);
        }

        /** Overrides default hashCode to return {@link #cellNumber}. This overriding
         * allows for storing cells in lists and checking for them by their cellNumber.
         *
         * @return cellNumber
         */
        @Override
        public int hashCode() {
            return cellNumber;
        }

        /** Two cells are equal if their {@link #cellNumber}'s are equal.
         *
         * @param obj another Cell.
         * @return true if equal.
         */
        @Override
        public boolean equals(Object obj) { // derived from http://www.geocities.com/technofundo/tech/java/equalhash.html
            if (this == obj) {
                return true;
            }
            if ((obj == null) || (obj.getClass() != this.getClass())) {
                return false;
            }
            // object must be Test at this point
            Cell test = (Cell) obj;
            return cellNumber == test.cellNumber;
        }

        /** Draws this cell using OpenGL.
         *
         * @param drawable area to draw this.
         */
        public void draw(GLAutoDrawable drawable) {
            final float BOX_LINE_WIDTH = 2f; // in chip
            GL gl = drawable.getGL();

            // set color and line width of cell annotation
            setColorAutomatically();
            getColor().getRGBComponents(rgb);
            gl.glColor3fv(rgb, 0);
            gl.glLineWidth(BOX_LINE_WIDTH);

            // draw cell rectangle
            drawCell(gl, (int) getLocation().x, (int) getLocation().y, (int) cellSizePixels/2, (int) cellSizePixels/2);

            // text annoations on cells, setup
            final int font = GLUT.BITMAP_HELVETICA_10;
            if (showCellMass && this.cellProperty != CellProperty.NOT_VISIBLE) {
                mychip.getCanvas().getGlut().glutBitmapString(font, String.format("m=%.1f", getMassNow(lastUpdateTime)));
            }
        }

        protected void setProperty(CellProperty cellProperty){
            this.cellProperty = cellProperty;
        }

        /** updates cell by one event.
        @param event the event
         */
        public void addEvent(BasicEvent event) {
            //Increments mass of cell by one after decaying it away since the lastEventTimestamp according
            // to exponential decay with time constant cellLifetimeWithoutSupportUs.
            incrementMass(event.getTimestamp());
            lastUpdateTime = event.getTimestamp();

            numEvents++;
        } // End of void addEvent


         /** Computes and returns {@link #mass} at time t, using the last time an event hit this cell
         * and
         * the {@link #cellLifetimeWithoutSupportUs}. Does not change the mass.
         *
         * @param t timestamp now.
         * @return the mass.
         */
        protected float getMassNow(int t) {
            float m = mass * (float) Math.exp(((float) (lastUpdateTime - t)) / cellLifeTimeUs);
            return m;
        }

        /**Increments mass of cell by one after decaying it away since the {@link #lastEventTimestamp} according
        to exponential decay with time constant {@link #cellLifetimeWithoutSupportUs}.
        @param event used for event timestamp.
         */
        protected void incrementMass(int timeStamp) {
            mass = 1 + mass * (float) Math.exp(((float) lastUpdateTime - timeStamp) / cellLifeTimeUs);
        }

        /** Total number of events collected by this cell.
         * @return the numEvents
         */
        public int getNumEvents() {
            return numEvents;
        }

        final public Point2D.Float getLocation() {
            return location;
        }

        final public boolean isVisible(){
            return visible;
        }

        final public boolean isAboveThreshold() {
            visible = isEventNumAboveThreshold()&&isMassAboveThreshold();
            if(visible)
                setProperty(CellProperty.VISIBLE_ISOLATED);
            else
                setProperty(CellProperty.NOT_VISIBLE);
            return visible;
        }

        final public boolean isEventNumAboveThreshold() {
            boolean ret = true;
           if (numEvents < thresholdEventsForVisibleCell) {
                ret = false;
            }
            return ret;
        }

        final public boolean isMassAboveThreshold() {
            boolean ret = true;
            if (this.getMassNow(lastTime) < thresholdMassForVisibleCell) {
                ret = false;
            }
            return ret;
        }

        @Override
        public String toString() {
            return String.format("Cell index=(%d, %d), location = (%d, %d), mass = %.2f, numEvents=%d",
                    (int) cellIndex.x, (int) cellIndex.y,
                    (int) location.x,  (int) location.y,
                    mass,
                    numEvents);
        }

        public Color getColor() {
            return color;
        }

        public void setColor(Color color) {
            this.color = color;
        }

        /** Sets color according to measured cell mass */
        public void setColorAccordingToMass() {
            float hue = 1/mass;
            if (hue > 1) {
                hue = 1;
            }
            setColor(Color.getHSBColor(hue, 1f, 1f));
        }

        public void setColorAutomatically() {
            setColorAccordingToMass();
        }

        public int getCellNumber() {
            return cellNumber;
        }
    } // End of class Cell


    private int nextUpdateTimeUs = 0; // next timestamp we should update cell list
    private boolean updateTimeInitialized = false;// to initialize time for cell list update

    synchronized private EventPacket<? extends BasicEvent> blurring(EventPacket<BasicEvent> in) {
        boolean updatedCells = false;
    
        if (in.getSize() == 0) {
            return out;
        }

        try{
            // add events to the corresponding cell
            for (BasicEvent ev : in) {
                int subIndexX = (int) 2*ev.getX()/cellSizePixels;
                int subIndexY = (int) 2*ev.getY()/cellSizePixels;

    //            System.out.println("("+ev.getX()+", "+ev.getY()+") --> ("+subIndexX+", "+subIndexY+")");
                if(subIndexX >= numOfCellsX && subIndexY >= numOfCellsY)
                    initFilter();

                if(subIndexX != numOfCellsX && subIndexY != numOfCellsY)
                    cellArray.get(subIndexX + subIndexY * numOfCellsX).addEvent(ev);
                if(subIndexX != numOfCellsX && subIndexY != 0)
                    cellArray.get(subIndexX + (subIndexY - 1) * numOfCellsX).addEvent(ev);
                if(subIndexX != 0 && subIndexY != numOfCellsY)
                    cellArray.get(subIndexX - 1 + subIndexY * numOfCellsX).addEvent(ev);
                if(subIndexY != 0 && subIndexX != 0)
                    cellArray.get(subIndexX - 1 + (subIndexY - 1) * numOfCellsX).addEvent(ev);

                lastTime = ev.getTimestamp();

                if (!updateTimeInitialized) {
                    nextUpdateTimeUs = (int) (lastTime + updateIntervalMs * 1000 / AEConstants.TICK_DEFAULT_US);
                    updateTimeInitialized = true;
                }
                // ensure cluster list is scanned at least every updateIntervalMs
                if (lastTime >= nextUpdateTimeUs) {
                    nextUpdateTimeUs = (int) (lastTime + updateIntervalMs * 1000 / AEConstants.TICK_DEFAULT_US);
                    updateCells(lastTime);
                    updatedCells = true;
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            // this is in case cell list is modified by real time filter during updating cells
            log.warning(e.getMessage());
        }
        // TODO update here again, relying on the fact that lastEventTimestamp was set by possible previous update according to
        // schedule; we have have double update of velocity using same dt otherwise
        if (!updatedCells) {
            updateCells(in.getLastTimestamp()); // at laest once per packet update list
        }
        
        return in;
    }

    private void updateCells(int t) {
        if(!cellArray.isEmpty()){
            Iterator itr = cellArray.iterator();
            Cell tmpCell = null;
            int timeSinceSupport;

            while(itr.hasNext())
            {
                try{
                    tmpCell = (Cell) itr.next();

                    timeSinceSupport = t - tmpCell.lastUpdateTime;

                    if(timeSinceSupport > cellLifeTimeUs)
                    {
                        tmpCell.reset();
                    }
                    else
                    {
                        if(tmpCell.cellNumber == 0)
                            if(!tmpCell.isAboveThreshold())
                                tmpCell.numOfNeighbors = 0;

                        int cellIndexX = (int) tmpCell.cellIndex.x;
                        int cellIndexY = (int) tmpCell.cellIndex.y;
                        int up = cellIndexX + (cellIndexY+1)*numOfCellsX;
                        int down = cellIndexX + (cellIndexY-1)*numOfCellsX;
                        int right = cellIndexX+1 + cellIndexY*numOfCellsX;
                        int left = cellIndexX-1 + cellIndexY*numOfCellsX;

                        tmpCell.numOfNeighbors = 0;
                        switch(tmpCell.cellType){
                            case CORNER_00:
                                if(cellArray.get(up).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(right).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;

                                if(tmpCell.numOfNeighbors > 0){
                                    if(tmpCell.cellProperty != CellProperty.VISIBLE_BORDER)
                                        tmpCell.setProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                                else {
                                    if(tmpCell.isVisible())
                                        tmpCell.setProperty(CellProperty.VISIBLE_ISOLATED);
                                    else
                                        tmpCell.setProperty(CellProperty.NOT_VISIBLE);
                                }

                                if(tmpCell.numOfNeighbors == 2)
                                {
                                    tmpCell.setProperty(CellProperty.VISIBLE_INSIDE);
                                    cellArray.get(up).setProperty(CellProperty.VISIBLE_BORDER);
                                    cellArray.get(right).setProperty(CellProperty.VISIBLE_BORDER);
                                }
                                break;
                            case CORNER_01:
                                if(cellArray.get(down).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(right).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;

                                if(tmpCell.numOfNeighbors > 0){
                                    if(tmpCell.cellProperty != CellProperty.VISIBLE_BORDER)
                                        tmpCell.setProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                                else {
                                    if(tmpCell.isVisible())
                                        tmpCell.setProperty(CellProperty.VISIBLE_ISOLATED);
                                    else
                                        tmpCell.setProperty(CellProperty.NOT_VISIBLE);
                                }

                                if(tmpCell.numOfNeighbors == 2)
                                {
                                    tmpCell.setProperty(CellProperty.VISIBLE_INSIDE);
                                    cellArray.get(right).setProperty(CellProperty.VISIBLE_BORDER);
                                    if(cellArray.get(down).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(down).setProperty(CellProperty.VISIBLE_BORDER);
                                }
                                break;
                            case CORNER_10:
                                if(cellArray.get(up).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(left).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;

                                if(tmpCell.numOfNeighbors > 0){
                                    if(tmpCell.cellProperty != CellProperty.VISIBLE_BORDER)
                                        tmpCell.setProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                                else {
                                    if(tmpCell.isVisible())
                                        tmpCell.setProperty(CellProperty.VISIBLE_ISOLATED);
                                    else
                                        tmpCell.setProperty(CellProperty.NOT_VISIBLE);
                                }

                                if(tmpCell.numOfNeighbors == 2)
                                {
                                    tmpCell.setProperty(CellProperty.VISIBLE_INSIDE);
                                    cellArray.get(up).setProperty(CellProperty.VISIBLE_BORDER);
                                    if(cellArray.get(left).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(left).setProperty(CellProperty.VISIBLE_BORDER);
                                }
                                break;
                            case CORNER_11:
                                if(cellArray.get(down).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(left).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;

                                if(tmpCell.numOfNeighbors > 0){
                                    if(tmpCell.cellProperty != CellProperty.VISIBLE_BORDER)
                                        tmpCell.setProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                                else {
                                    if(tmpCell.isVisible())
                                        tmpCell.setProperty(CellProperty.VISIBLE_ISOLATED);
                                    else
                                        tmpCell.setProperty(CellProperty.NOT_VISIBLE);
                                }

                                if(tmpCell.numOfNeighbors == 2)
                                {
                                    tmpCell.setProperty(CellProperty.VISIBLE_INSIDE);
                                    if(cellArray.get(down).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(down).setProperty(CellProperty.VISIBLE_BORDER);
                                    if(cellArray.get(left).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(left).setProperty(CellProperty.VISIBLE_BORDER);
                                }
                                break;
                            case EDGE_0Y:
                                if(cellArray.get(up).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(down).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(left).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;

                                if(tmpCell.numOfNeighbors > 0){
                                    if(tmpCell.cellProperty != CellProperty.VISIBLE_BORDER)
                                        tmpCell.setProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                                else {
                                    if(tmpCell.isVisible())
                                        tmpCell.setProperty(CellProperty.VISIBLE_ISOLATED);
                                    else
                                        tmpCell.setProperty(CellProperty.NOT_VISIBLE);
                                }

                                if(tmpCell.numOfNeighbors == 3)
                                {
                                    tmpCell.setProperty(CellProperty.VISIBLE_INSIDE);
                                    cellArray.get(up).setProperty(CellProperty.VISIBLE_BORDER);
                                    if(cellArray.get(down).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(down).setProperty(CellProperty.VISIBLE_BORDER);
                                    if(cellArray.get(left).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(left).setProperty(CellProperty.VISIBLE_BORDER);
                                }
                                break;
                            case EDGE_1Y:
                                if(cellArray.get(up).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(down).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(right).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;

                                if(tmpCell.numOfNeighbors > 0){
                                    if(tmpCell.cellProperty != CellProperty.VISIBLE_BORDER)
                                        tmpCell.setProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                                else {
                                    if(tmpCell.isVisible())
                                        tmpCell.setProperty(CellProperty.VISIBLE_ISOLATED);
                                    else
                                        tmpCell.setProperty(CellProperty.NOT_VISIBLE);
                                }

                                if(tmpCell.numOfNeighbors == 3)
                                {
                                    tmpCell.setProperty(CellProperty.VISIBLE_INSIDE);
                                    cellArray.get(up).setProperty(CellProperty.VISIBLE_BORDER);
                                    cellArray.get(right).setProperty(CellProperty.VISIBLE_BORDER);
                                    if(cellArray.get(down).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(down).setProperty(CellProperty.VISIBLE_BORDER);
                                }
                                break;
                            case EDGE_X0:
                                if(cellArray.get(up).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(right).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(left).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;

                                if(tmpCell.numOfNeighbors > 0){
                                    if(tmpCell.cellProperty != CellProperty.VISIBLE_BORDER)
                                        tmpCell.setProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                                else {
                                    if(tmpCell.isVisible())
                                        tmpCell.setProperty(CellProperty.VISIBLE_ISOLATED);
                                    else
                                        tmpCell.setProperty(CellProperty.NOT_VISIBLE);
                                }

                                if(tmpCell.numOfNeighbors == 3)
                                {
                                    tmpCell.setProperty(CellProperty.VISIBLE_INSIDE);
                                    cellArray.get(up).setProperty(CellProperty.VISIBLE_BORDER);
                                    cellArray.get(right).setProperty(CellProperty.VISIBLE_BORDER);
                                    if(cellArray.get(left).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(left).setProperty(CellProperty.VISIBLE_BORDER);
                                }
                                break;
                            case EDGE_X1:
                                if(cellArray.get(down).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(right).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(left).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;

                                if(tmpCell.numOfNeighbors > 0){
                                    if(tmpCell.cellProperty != CellProperty.VISIBLE_BORDER)
                                        tmpCell.setProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                                else {
                                    if(tmpCell.isVisible())
                                        tmpCell.setProperty(CellProperty.VISIBLE_ISOLATED);
                                    else
                                        tmpCell.setProperty(CellProperty.NOT_VISIBLE);
                                }

                                if(tmpCell.numOfNeighbors == 3)
                                {
                                    tmpCell.setProperty(CellProperty.VISIBLE_INSIDE);
                                    cellArray.get(right).setProperty(CellProperty.VISIBLE_BORDER);
                                    if(cellArray.get(down).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(down).setProperty(CellProperty.VISIBLE_BORDER);
                                    if(cellArray.get(left).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(left).setProperty(CellProperty.VISIBLE_BORDER);
                                }
                                break;
                            case INSIDE:
                                if(cellArray.get(up).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(down).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(right).isAboveThreshold() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;
                                if(cellArray.get(left).isVisible() && tmpCell.isVisible())
                                    tmpCell.numOfNeighbors++;

                                if(tmpCell.numOfNeighbors > 0){
                                    if(tmpCell.cellProperty != CellProperty.VISIBLE_BORDER)
                                        tmpCell.setProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                                else {
                                    if(tmpCell.isVisible())
                                        tmpCell.setProperty(CellProperty.VISIBLE_ISOLATED);
                                    else
                                        tmpCell.setProperty(CellProperty.NOT_VISIBLE);
                                }

                                if(tmpCell.numOfNeighbors == 4)
                                {
                                    tmpCell.setProperty(CellProperty.VISIBLE_INSIDE);
                                    cellArray.get(up).setProperty(CellProperty.VISIBLE_BORDER);
                                    cellArray.get(right).setProperty(CellProperty.VISIBLE_BORDER);
                                    if(cellArray.get(down).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(down).setProperty(CellProperty.VISIBLE_BORDER);
                                    if(cellArray.get(left).cellProperty != CellProperty.VISIBLE_INSIDE)
                                        cellArray.get(left).setProperty(CellProperty.VISIBLE_BORDER);
                                }
                                break;
                            default:
                                break;
                        } // End of switch
                    } // End of else in if(timeSinceSupport > cellLifeTimeUs)
                } catch (java.util.ConcurrentModificationException e) {
                    // this is in case cell list is modified by real time filter during updating cells
                    log.warning(e.getMessage());
                }
            }
        }
    }

    protected void drawCell(GL gl, int x, int y, int sx, int sy) {
        gl.glPushMatrix();
        gl.glTranslatef(x, y, 0);

        if(filledCells)
            gl.glBegin(GL.GL_QUADS);
        else
            gl.glBegin(GL.GL_LINE_LOOP);

        {
            gl.glVertex2i(-sx, -sy);
            gl.glVertex2i(+sx, -sy);
            gl.glVertex2i(+sx, +sy);
            gl.glVertex2i(-sx, +sy);
        }
        gl.glEnd();
        gl.glPopMatrix();
    }


    public void annotate(float[][][] frame) {
        if (!isFilterEnabled()) {
            return;
        }
        // disable for now TODO
        if (mychip.getCanvas().isOpenGLEnabled()) {
            return; // done by open gl annotator
        }
        if (showCells) {
//            drawCell(c, frame);
        }
    }

    public void annotate(Graphics2D g) {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        GL gl = drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if (gl == null) {
            log.warning("null GL in BlurringFilter2D.annotate");
            return;
        }
        float[] rgb = new float[4];
        gl.glPushMatrix();
        try {
            if (showCells) {
                Cell tmpCell;
                for(int i=0; i<cellArray.size(); i++)
                {
                    tmpCell=cellArray.get(i);

                    if(showBorderCellsOnly){
                        if(tmpCell.cellProperty == CellProperty.VISIBLE_BORDER)
                            tmpCell.draw(drawable);
                    } else {
                        if(tmpCell.cellProperty != CellProperty.NOT_VISIBLE)
                            tmpCell.draw(drawable);
                    }
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            // this is in case cell list is modified by real time filter during rendering of cells
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (in == null) {
            return null;
        }

        if (enclosedFilter != null) {
            out = enclosedFilter.filterPacket(in);
            out = blurring(out);
            return out;
        } else {
            out = blurring((EventPacket<BasicEvent>) in);
            return out;
        }
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void initFilter() {
        int prev_numOfCellsX = numOfCellsX;
        int prev_numOfCellsY = numOfCellsY;

        // calculate the required number of cells
        if(2*mychip.getSizeX()%cellSizePixels == 0)
            numOfCellsX = (int) (2*mychip.getSizeX()/cellSizePixels)-1;
        else
            numOfCellsX = (int) (2*mychip.getSizeX()/cellSizePixels);

        if(2*mychip.getSizeY()%cellSizePixels == 0)
            numOfCellsY = (int) (2*mychip.getSizeY()/cellSizePixels)-1;
        else
            numOfCellsY = (int) (2*mychip.getSizeY()/cellSizePixels);


        // initialize all cells
        if((numOfCellsX > 0 && numOfCellsY > 0) &&
           (prev_numOfCellsX != numOfCellsX || prev_numOfCellsY != numOfCellsY))
        {
            if(!cellArray.isEmpty())
                cellArray.clear();

            for(int j=0; j<numOfCellsY; j++){
                for(int i=0; i<numOfCellsX; i++)
                {
                    Cell newCell = new Cell(i, j);

                    if(i == 0){
                        if(j == 0)
                            newCell.cellType = CellType.CORNER_00;
                        else if(j == numOfCellsY-1)
                            newCell.cellType = CellType.CORNER_01;
                        else
                            newCell.cellType = CellType.EDGE_0Y;
                        }
                    else if(i == numOfCellsX-1){
                        if(j == 0)
                            newCell.cellType = CellType.CORNER_10;
                        else if(j == numOfCellsY-1)
                            newCell.cellType = CellType.CORNER_11;
                        else
                            newCell.cellType = CellType.EDGE_1Y;
                        }
                    else{
                        if(j == 0)
                            newCell.cellType = CellType.EDGE_X0;
                        else if(j == numOfCellsY-1)
                            newCell.cellType = CellType.EDGE_X1;
                        else
                            newCell.cellType = CellType.INSIDE;
                        }

                    cellArray.add(newCell.cellNumber, newCell);
                }
            }
        }
}

    @Override
    public void resetFilter() {
        if(!cellArray.isEmpty()){
            Iterator itr = cellArray.iterator();

            while(itr.hasNext())
                 ((Cell) itr.next()).reset();
        }
    }


    public int getCellLifeTimeUs() {
        return cellLifeTimeUs;
    }

    public void setCellLifeTimeUs(int cellLifeTimeUs) {
        this.cellLifeTimeUs = cellLifeTimeUs;
        getPrefs().putInt("BlurringFilter2D.cellLifeTimeUs", cellLifeTimeUs);
    }

    public int getCellSizePixels() {
        return cellSizePixels;
    }

    public void setCellSizePixels(int cellSizePixels) {
        this.cellSizePixels = cellSizePixels;
        getPrefs().putInt("BlurringFilter2D.cellSizePixels", cellSizePixels);
        initFilter();
    }

    public float getMixingFactor() {
        return mixingFactor;
    }

    public void setMixingFactor(float mixingFactor) {
        this.mixingFactor = mixingFactor;
        getPrefs().putFloat("BlurringFilter2D.mixingFactor", mixingFactor);
    }

    public int getThresholdEventsForVisibleCell() {
        return thresholdEventsForVisibleCell;
    }

    public void setThresholdEventsForVisibleCell(int thresholdEventsForVisibleCell) {
        this.thresholdEventsForVisibleCell = thresholdEventsForVisibleCell;
        getPrefs().putInt("BlurringFilter2D.thresholdEventsForVisibleCell", thresholdEventsForVisibleCell);
    }

    public int getThresholdMassForVisibleCell() {
        return thresholdMassForVisibleCell;
    }

    public void setThresholdMassForVisibleCell(int thresholdMassForVisibleCell) {
        this.thresholdMassForVisibleCell = thresholdMassForVisibleCell;
        getPrefs().putInt("BlurringFilter2D.thresholdMassForVisibleCell", thresholdMassForVisibleCell);
    }


    public boolean isShowCells() {
        return showCells;
    }

    public void setShowCells(boolean showCells) {
        this.showCells = showCells;
        getPrefs().putBoolean("BlurringFilter2D.showCells", showCells);
    }

    public boolean isShowCellMass() {
        return showCellMass;
    }

    public void setShowCellMass(boolean showCellMass) {
        this.showCellMass = showCellMass;
    }

        /**
     * @return the updateIntervalMs
     */
    public float getUpdateIntervalMs() {
        return updateIntervalMs;
    }

    public float getMinUpdateIntervalMs() {
        return 1;
    }

    public float getMaxUpdateIntervalMs() {
        return 100;
    }

    /**
    The minimum interval between cell list updating for purposes of resetting list of cells. Allows for fast playback of data
    and analysis with large packets of data.
     * @param updateIntervalMs the updateIntervalMs to set
     */
    public void setUpdateIntervalMs(float updateIntervalMs) {
        support.firePropertyChange("updateIntervalMs", this.updateIntervalMs, updateIntervalMs);
        this.updateIntervalMs = updateIntervalMs;
        getPrefs().putFloat("RectangularClusterTracker.updateIntervalMs", updateIntervalMs);
    }

    public boolean isshowBorderCellsOnly() {
        return showBorderCellsOnly;
    }

    public void setshowBorderCellsOnly(boolean showBorderCellsOnly) {
        this.showBorderCellsOnly = showBorderCellsOnly;
        getPrefs().putBoolean("BlurringFilter2D.showBorderCellsOnly", showBorderCellsOnly);
    }

    public boolean isFilledCells() {
        return filledCells;
    }

    public void setFilledCells(boolean filledCells) {
        this.filledCells = filledCells;
        getPrefs().putBoolean("BlurringFilter2D.filledCells", filledCells);
    }
}
