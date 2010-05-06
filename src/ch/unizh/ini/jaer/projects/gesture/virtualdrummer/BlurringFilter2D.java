/*
 * Last updated on April 23, 2010, 11:40 AM
 *
 *  * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

import java.awt.Color;
import java.awt.geom.Point2D.Float;
import javax.media.opengl.GL;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.*;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Finds clusters of events using spatio-temporal correlation between events.
 * Events occured within the specified area (called a Cell) are considered strongly correalated.
 * How much the events are correlated is evaluated using a parameter called 'mass'.
 * The cell mass is the weighted number of events collected by the cell.
 * By thresholding the mass of cells, active cells can be defined.
 * Then the clusters of events can be detected from the cell groups.
 * The cell group is a group of active cells which are linked each other. (If two neighboring cells are active, they are linked).
 * (Notice) BlurringFilter2D is a cluster finder rather than a filter. It does NOT filters any events.
 *
 * @author Jun Haeng Lee
 */
public class BlurringFilter2D extends EventFilter2D implements FrameAnnotater, Observer {

    /* properties */
    private int cellMassTimeConstantUs = getPrefs().getInt("BlurringFilter2D.cellMassTimeConstantUs", 20000);
    private int cellLifeTimeUs = getPrefs().getInt("BlurringFilter2D.cellLifeTimeUs", 200000);
    private int thresholdEventsForVisibleCell = getPrefs().getInt("BlurringFilter2D.thresholdEventsForVisibleCell", 1);
    private int thresholdMassForVisibleCell = getPrefs().getInt("BlurringFilter2D.thresholdMassForVisibleCell", 25);
    private boolean showCells = getPrefs().getBoolean("BlurringFilter2D.showCells", true);
    private boolean filledCells = getPrefs().getBoolean("BlurringFilter2D.filledCells", false);
    private boolean showBorderCellsOnly = getPrefs().getBoolean("BlurringFilter2D.showBorderCellsOnly", false);
    private boolean showInsideCellsOnly = getPrefs().getBoolean("BlurringFilter2D.showInsideCellsOnly", false);
    private int cellSizePixels = getPrefs().getInt("BlurringFilter2D.cellSizePixels", 8);

    /* Constants to define neighbor cells */
    static int UPDATE_UP = 0x01;
    static int UPDATE_DOWN = 0x02;
    static int UPDATE_RIGHT = 0x04;
    static int UPDATE_LEFT = 0x08;

    /* variables */
    private int numOfCellsX = 0, numOfCellsY = 0;  // number of cells in x (column) and y (row) directions.
    private ArrayList<Cell> cellArray = new ArrayList<Cell>(); // array of cells
    private HashSet<Integer> validCellIndexSet = new HashSet(); // index of active cells which have mass greater than the threshold
    private HashMap<Integer, CellGroup> cellGroup = new HashMap<Integer, CellGroup>(); // cell groups found
    /**
     * DVS Chip
     */
    protected AEChip mychip; 
    /**
     * last updat time. It is the timestamp of the latest event.
     */
    protected int lastTime;
    /**
     * number of cell groups found
     */
    protected int numOfGroup = 0;
    /**
     * random
     */
    protected Random random = new Random();

    /**
     * Constructor
     * @param chip
     */
    public BlurringFilter2D(AEChip chip) {
        super(chip);
        this.mychip = chip;
        initFilter();
        chip.addObserver(this);
        addObserver(this);

        final String threshold = "Threshold", sizing = "Sizing", movement = "Movement", lifetime = "Lifetime", disp = "Display", global = "Global", update = "Update", logging = "Logging";

        setPropertyTooltip(lifetime, "cellMassTimeConstantUs", "Time constant of cell mass. The cell mass decays exponetially unless a new event is added.");
        setPropertyTooltip(lifetime, "cellLifeTimeUs", "A cell will be reset if there is no additional event with this value since the last update");
        setPropertyTooltip(threshold, "thresholdEventsForVisibleCell", "Cell needs this many events to be visible");
        setPropertyTooltip(threshold, "thresholdMassForVisibleCell", "Cell needs this much mass to be visible");
        setPropertyTooltip(disp, "showCells", "Show active cells");
        setPropertyTooltip(disp, "filledCells", "Use filled symbols to illustrate the active cells");
        setPropertyTooltip(disp, "showBorderCellsOnly", "Show border cells (boundary cells of a cell group) only among the active cells");
        setPropertyTooltip(disp, "showInsideCellsOnly", "Show inside cells (surrounded by the border cells) only among the active cells");
        setPropertyTooltip(sizing, "cellSizePixels", "Side length of a square cell in number of pixels. Cell spacing is decided to the half of this value. Thus, neighboring cells overlaps each other.");
    }

    @Override
    public String toString() {
        String s = cellArray != null ? Integer.toString(numOfCellsX).concat(" by ").concat(Integer.toString(numOfCellsY)) : null;
        String s2 = "BlurringFilter2D with " + s + " cells ";
        return s2;
    }

    public void update(Observable o, Object arg) {
        if (o == this) {
            UpdateMessage msg = (UpdateMessage) arg;
            updateCells(msg.timestamp); // at least once per packet update list
        } else if (o instanceof AEChip) {
            initFilter();
        }
    }

    /**
     * Definition of cell types
     * CORNER_* : cells located in corners
     * EDGE_* : cells located in edges
     * INSIDE: all cells except corner and egde cells
     */
    static enum CellType {

        CORNER_00, CORNER_01, CORNER_10, CORNER_11, EDGE_0Y, EDGE_1Y, EDGE_X0, EDGE_X1, INSIDE
    }

    /**
     * Definition of cell properties
     * NOT_VISIBLE : non-active cells
     * VISIBLE_ISOLATED : active cells with no neighbor
     * VISIBLE_HAS_NEIGHBOR : active cells with (a) neighbor(s)
     * VISIBLE_BORDER : active cells which make the boundary of a cell group
     * VISIBLE_INSIDE : active cells which are surrounded by the border cells
     */
    static enum CellProperty {

        NOT_VISIBLE, VISIBLE_ISOLATED, VISIBLE_HAS_NEIGHBOR, VISIBLE_BORDER, VISIBLE_INSIDE
    }

    /**
     * Cell property update type
     * FORCED : update the cell property regardless of current property
     * CHECK : property update is done conditionally based on the current property
     */
    static enum CellPropertyUpdate {

        FORCED, CHECK
    }

    /**
     * Definition of cells.
     * The cell is a partial area of events-occuring space.
     * Events within the cell are considered strongly correalated.
     * Cell spacing is decided to the half of cell's side length to increase the spatial resolution.
     * Thus, neighboring cells overlaps each other.
     */
    public class Cell {

        /**
         * Cell index in (x_index, y_index)
         */
        public Point2D.Float cellIndex = new Point2D.Float();
        /**
         *  location in chip pixels
         */
        public Point2D.Float location = new Point2D.Float();
        /**
         * cell type. One of {CORNER_00, CORNER_01, CORNER_10, CORNER_11, EDGE_0Y, EDGE_1Y, EDGE_X0, EDGE_X1, INSIDE}
         */
        CellType cellType;
        /**
         * cell property. One of {NOT_VISIBLE, VISIBLE_ISOLATED, VISIBLE_HAS_NEIGHBOR, VISIBLE_BORDER, VISIBLE_INSIDE}
         */
        CellProperty cellProperty;
        /**
         * Tag to identify the group which the cell belongs to.
         */
        protected int groupTag = -1;
        /**
         * active cell or not. If a cell is active, it's visible.
         */
        protected boolean visible = false;
        /**
         * cell color to display the cell.
         */
        protected Color color = null;
        /**
         * Number of events collected by this cell when it is active.
         */
        protected int numEvents = 0;
        /** The "mass" of the cell is the weighted number of events it has collected.
         * The mass decays over time and is incremented by one by each collected event.
         * The mass decays with a first order time constant of cellMassTimeConstantUs in us.
         */
        protected float mass = 0;
        /**
         *  number of active neighbors
         */
        protected int numOfNeighbors = 0;
        /** This is the last and first time in timestamp ticks that the cell was updated, by an event
         * This time can be used to compute postion updates given a cell velocity and time now.
         */
        protected int lastEventTimestamp, firstEventTimestamp;
        private int cellNumber; // defined as cellIndex.x + cellIndex.y * numOfCellsX
        private float[] rgb = new float[4];

        /**
         * Construct a cell with index.
         * @param indexX
         * @param indexY
         */
        public Cell(int indexX, int indexY) {
            float hue = random.nextFloat();
            Color c = Color.getHSBColor(hue, 1f, 1f);
            setColor(c);

            if (indexX < 0 || indexY < 0 || indexX >= numOfCellsX || indexY >= numOfCellsY) {
                // exception
            }

            cellIndex.x = (float) indexX;
            cellIndex.y = (float) indexY;
            location.x = (cellIndex.x + 1) * cellSizePixels / 2; // Cell spacing is decided to the half of cell's side length
            location.y = (cellIndex.y + 1) * cellSizePixels / 2; // Cell spacing is decided to the half of cell's side length
            
            cellNumber = (int) cellIndex.x + (int) cellIndex.y * numOfCellsX;
            setCellProperty(CellProperty.NOT_VISIBLE);
            resetGroupTag();
            visible = false;
            numEvents = 0;
            mass = 0;
            numOfNeighbors = 0;
            lastEventTimestamp = firstEventTimestamp = 0;
        }

        /**
        * Reset a cell with initial values
        */
        public void reset() {
            mass = 0;
            numEvents = 0;
            numOfNeighbors = 0;
            visible = false;
            setCellProperty(CellProperty.NOT_VISIBLE);
            resetGroupTag();
            lastEventTimestamp = firstEventTimestamp = 0;
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
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if ((obj == null) || (obj.getClass() != this.getClass())) {
                return false;
            }

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
            drawCell(gl, (int) getLocation().x, (int) getLocation().y, (int) cellSizePixels / 2, (int) cellSizePixels / 2);
        }

        /** get cell property.
         *
         * @return
         */
        private CellProperty getCellProperty() {
            return cellProperty;
        }

        /** set cell property
         *
         * @param cellProperty
         */
        private void setCellProperty(CellProperty cellProperty) {
            this.cellProperty = cellProperty;
        }

        /** set cell property to VISIBLE_BORDER.
         * If cellProperUpdateType is CellPropertyUpdate.CHECK, an inside cell cannot be a border cell.
         *
         * @param groupTag
         * @param cellProperUpdateType
         */
        private void setPropertyToBorder(int groupTag, CellPropertyUpdate cellProperUpdateType) {
            if (cellProperUpdateType == CellPropertyUpdate.CHECK) {
                if (this.cellProperty != CellProperty.VISIBLE_INSIDE) {
                    setCellProperty(CellProperty.VISIBLE_BORDER);
                }
            } else {
                setCellProperty(CellProperty.VISIBLE_BORDER);
            }
            setGroupTag(groupTag);
        }

        /** updates cell by one event.
         *
         * @param event
         */
        public void addEvent(BasicEvent event) {
            incrementMass(event.getTimestamp());
            lastEventTimestamp = event.getTimestamp();
            if (numEvents == 0) {
                firstEventTimestamp = lastEventTimestamp;
            }

            numEvents++;
        }

        /** Computes and returns {@link #mass} at time t, using the last time an event hit this cell
         * and the {@link #cellMassTimeConstantUs}. Does not change the mass.
         *
         * @param t timestamp now.
         * @return the mass.
         */
        protected float getMassNow(int t) {
            float m = mass * (float) Math.exp(((float) (lastEventTimestamp - t)) / cellMassTimeConstantUs);
            return m;
        }

        /** returns the mass without considering the current time.
         *
         * @return mass
         */
        protected float getMass() {
            return mass;
        }

        /**
         * Increments mass of cell by one after decaying it away since the {@link #lastEventTimestamp} according
         * to exponential decay with time constant {@link #cellMassTimeConstantUs}.
         * @param timeStamp
         */
        protected void incrementMass(int timeStamp) {
            mass = 1 + mass * (float) Math.exp(((float) lastEventTimestamp - timeStamp) / cellMassTimeConstantUs);
        }

        /** returns the total number of events collected by this cell.
         * @return the numEvents
         */
        public int getNumEvents() {
            return numEvents;
        }

        /** returns the cell location in pixels.
         *
         * @return cell location in pixels.
         */
        final public Point2D.Float getLocation() {
            return location;
        }

        /** returns true if the cell is active.
         * Otherwise, returns false.
         * @return true if the cell is active
         */
        final public boolean isVisible() {
            return visible;
        }

        /** checks if the cell is active
         *
         * @return true if the cell is active
         */
        final public boolean isAboveThreshold() {
            visible = isEventNumAboveThreshold() && isMassAboveThreshold();
            if (visible) {
                cellProperty = CellProperty.VISIBLE_ISOLATED;
            } else {
                cellProperty = CellProperty.NOT_VISIBLE;
            }

            resetGroupTag();

            return visible;
        }

        /** checks if the number of events collected by the cell is above the threshold
         *
         * @return true if the number of events collected by the cell is above the threshold
         */
        final public boolean isEventNumAboveThreshold() {
            boolean ret = true;
            if (numEvents < thresholdEventsForVisibleCell) {
                ret = false;
            }
            return ret;
        }

        /** checks if the cell mass is above the threshold
         *
         * @return true if the mass of the cell is above the threshold
         */
        final public boolean isMassAboveThreshold() {
            if (this.getMassNow(lastTime) < thresholdMassForVisibleCell) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return String.format("Cell index=(%d, %d), location = (%d, %d), mass = %.2f, numEvents=%d",
                    (int) cellIndex.x, (int) cellIndex.y,
                    (int) location.x, (int) location.y,
                    mass,
                    numEvents);
        }

        /**
         * returns the color of the cell when it is displayed on the screen
         * @return
         */
        public Color getColor() {
            return color;
        }

        /**
         * set the color
         * @param color of the cell when it is displayed on the screen
         */
        public void setColor(Color color) {
            this.color = color;
        }

        /** Sets color according to measured cell mass
         *
         */
        public void setColorAccordingToMass() {
            float hue = 1 / mass;
            if (hue > 1) {
                hue = 1;
            }
            setColor(Color.getHSBColor(hue, 1f, 1f));
        }

        /** Set color automatically
         * Currently ,it's done based on the cell mass
         */
        public void setColorAutomatically() {
            setColorAccordingToMass();
        }

        /** returns cell index
         *
         * @return cell index
         */
        public Float getCellIndex() {
            return cellIndex;
        }

        /** returns cell type
         *
         */
        private CellType getCellType() {
            return cellType;
        }

        /** set cell type
         *
         * @param cellType
         */
        private void setCellType(CellType cellType) {
            this.cellType = cellType;
        }

        /** returns the number of actice neighbors
         *
         * @return number of active neighbors
         */
        public int getNumOfNeighbors() {
            return numOfNeighbors;
        }

        /** set the number of actice ni\eighbors
         *
         * @param numOfNeighbors
         */
        public void setNumOfNeighbors(int numOfNeighbors) {
            this.numOfNeighbors = numOfNeighbors;
        }

        /** increases the number of neighbors
         *
         */
        public void increaseNumOfNeighbors() {
            numOfNeighbors++;
        }

        /** returns the cell number
         *
         * @return cell number
         */
        public int getCellNumber() {
            return cellNumber;
        }

        /** returns the group tag
         *
         * @return group tag
         */
        public int getGroupTag() {
            return groupTag;
        }

        /** set the group tag
         *
         * @param groupTag
         */
        public void setGroupTag(int groupTag) {
            // If groupTag is a negative value, give a new group tag
            if (groupTag < 0) {
                if (this.groupTag < 0) {
                    this.groupTag = numOfGroup;
                    numOfGroup++;
                }
            } else {
                this.groupTag = groupTag;
            }
        }

        /** reset group tag
         *
         */
        public void resetGroupTag() {
            this.groupTag = -1;
        }

        /** returns the first event timestamp
         *
         * @return timestamp of the first event collected by the cell
         */
        public int getFirstEventTimestamp() {
            return firstEventTimestamp;
        }

        /** set the first event timestamp
         *
         * @param firstEventTimestamp
         */
        public void setFirstEventTimestamp(int firstEventTimestamp) {
            this.firstEventTimestamp = firstEventTimestamp;
        }

        /** returns the last event timestamp
         *
         * @return timestamp of the last event collected by the cell
         */
        public int getLastEventTimestamp() {
            return lastEventTimestamp;
        }

        /** set the last event timestamp
         *
         * @param lastEventTimestamp
         */
        public void setLastEventTimestamp(int lastEventTimestamp) {
            this.lastEventTimestamp = lastEventTimestamp;
        }
    } // End of class Cell



    /** Definition of cell group
     * Cell group is a group of active cells which are linked each other.
     * Any two neighboring cells are called linked if they are active simultaneously.
     * Each member cell within the cell group has its property which is one of {VISIBLE_BORDER, VISIBLE_INSIDE}.
     * Member cells with VISIBLE_BORDER property are the border cells making the boundary of the cell group.
     * All cell except the border cells should have VISIBLE_INSIDE property.
     * Cell groups are utilized as a basis for finding clusters.
     */
    public class CellGroup {

        public Point2D.Float location = new Point2D.Float(); // location of the group in chip pixels. Center of member cells location weighted by their mass.
        protected int numEvents; // Number of events collected by this group at each update. Sum of the number of events of all member cells.
        protected float mass; // Sum of the mass of all member cells.
        /** This is the last and the first time in timestamp ticks that the group was updated by an event.
         * The largest one among the lastUpdateTime of all member cells becomes groups's lastEventTimestamp.
         * The smallest one among the firstUpdateTime of all member cells becomes groups's firstEventTimestamp.
         */
        protected int lastEventTimestamp, firstEventTimestamp;
        /** Parameters to represent the area of the group.
         * minX(Y) : minimum X(Y) among the locations of member cells
         * maxX(Y) : maximum X(Y) among the locations of member cells
         */
        protected float minX, maxX, minY, maxY;
        protected int tag; // Group number (index)
        protected boolean hitEdge = false; // Indicate if this cell group is hitting edge
        protected boolean matched = false; // used in tracker
        HashSet<Cell> memberCells = null; // Member cells consisting of this group

        /**
         * Constructor
         */
        public CellGroup() {
            memberCells = new HashSet();
            reset();
        }

        /** constructor with the first member cell
         *
         * @param firstCell
         */
        public CellGroup(Cell firstCell) {
            this();
            add(firstCell);
        }

        /** reset the cell group
         *
         */
        public void reset() {
            location.setLocation(-1f, -1f);
            numEvents = 0;
            mass = 0;
            tag = -1;
            memberCells.clear();
            maxX = maxY = 0;
            minX = chip.getSizeX();
            minY = chip.getSizeX();
            hitEdge = false;
            matched = false;
        }

        /**
         *  adds a cell into the cell group
         * @param inCell
         */
        public void add(Cell inCell) {
            // if the first cell is added
            if (tag < 0) {
                tag = inCell.getGroupTag();
                firstEventTimestamp = inCell.getFirstEventTimestamp();
                lastEventTimestamp = inCell.getLastEventTimestamp();
                location.x = inCell.location.x;
                location.y = inCell.location.y;
                mass = inCell.getMass();
            } else {
                float prev_mass = mass;
                float leakyFactor;

                firstEventTimestamp = Math.min(firstEventTimestamp, inCell.getFirstEventTimestamp());
                numEvents += inCell.getNumEvents();

                if (lastEventTimestamp < inCell.getLastEventTimestamp()) {
                    leakyFactor = (float) Math.exp(((float) lastEventTimestamp - inCell.getLastEventTimestamp()) / cellMassTimeConstantUs);
                    mass = inCell.getMass() + mass * leakyFactor;
                    location.x = (inCell.getLocation().x * inCell.getMass() + location.x * prev_mass * leakyFactor) / (mass);
                    location.y = (inCell.getLocation().y * inCell.getMass() + location.y * prev_mass * leakyFactor) / (mass);

                    lastEventTimestamp = inCell.getLastEventTimestamp();
                } else {
                    leakyFactor = (float) Math.exp(((float) inCell.getLastEventTimestamp() - lastEventTimestamp) / cellMassTimeConstantUs);
                    mass += inCell.getMass() * leakyFactor;
                    location.x = (inCell.getLocation().x * inCell.getMass() * leakyFactor + location.x * prev_mass) / (mass);
                    location.y = (inCell.getLocation().y * inCell.getMass() * leakyFactor + location.y * prev_mass) / (mass);
                }
            }

            if (inCell.getLocation().x < minX) {
                minX = inCell.getLocation().x;
            }
            if (inCell.getLocation().x > maxX) {
                maxX = inCell.getLocation().x;
            }
            if (inCell.getLocation().y < minY) {
                minY = inCell.getLocation().y;
            }
            if (inCell.getLocation().y > maxY) {
                maxY = inCell.getLocation().y;
            }

            // check if this group is hitting edges
            if (!hitEdge && ((int) inCell.getCellIndex().x == 0 || (int) inCell.getCellIndex().y == 0 || (int) inCell.getCellIndex().x == numOfCellsX - 1 || (int) inCell.getCellIndex().y == numOfCellsY - 1)) {
                hitEdge = true;
            }

            memberCells.add(inCell);

        }

        /** merges cell groups
         *
         * @param targetGroup
         */
        public void merge(CellGroup targetGroup) {
            if (targetGroup == null) {
                return;
            }

            float prev_mass = mass;
            float leakyFactor;

            numEvents += targetGroup.numEvents;
            firstEventTimestamp = Math.min(firstEventTimestamp, targetGroup.firstEventTimestamp);

            if (lastEventTimestamp < targetGroup.lastEventTimestamp) {
                leakyFactor = (float) Math.exp(((float) lastEventTimestamp - targetGroup.lastEventTimestamp) / cellMassTimeConstantUs);
                mass = targetGroup.mass + mass * leakyFactor;
                location.x = (targetGroup.location.x * targetGroup.mass + location.x * prev_mass * leakyFactor) / (mass);
                location.y = (targetGroup.location.y * targetGroup.mass + location.y * prev_mass * leakyFactor) / (mass);

                lastEventTimestamp = targetGroup.lastEventTimestamp;
            } else {
                leakyFactor = (float) Math.exp(((float) targetGroup.lastEventTimestamp - lastEventTimestamp) / cellMassTimeConstantUs);
                mass += (targetGroup.mass * leakyFactor);
                location.x = (targetGroup.location.x * targetGroup.mass * leakyFactor + location.x * prev_mass) / (mass);
                location.y = (targetGroup.location.y * targetGroup.mass * leakyFactor + location.y * prev_mass) / (mass);
            }

            if (targetGroup.minX < minX) {
                minX = targetGroup.minX;
            }
            if (targetGroup.maxX > maxX) {
                maxX = targetGroup.maxX;
            }
            if (targetGroup.minY < minY) {
                minY = targetGroup.minY;
            }
            if (targetGroup.maxY > maxY) {
                maxY = targetGroup.maxY;
            }

            for(Cell tmpCell : targetGroup.getMemberCells()) {
                tmpCell.setGroupTag(tag);
                memberCells.add(tmpCell);
            }

            targetGroup.reset();
        }

        /** calculate the distance between two groups in pixels
         *
         * @param targetGroup
         * @return
         */
        public float locationDistancePixels(CellGroup targetGroup) {
            return (float) Math.sqrt(Math.pow(location.x - targetGroup.location.x, 2.0) + Math.pow(location.y - targetGroup.location.y, 2.0));
        }

        /** calculate the distance between two groups in cells
         *
         * @param targetGroup
         * @return
         */
        public float locationDistanceCells(CellGroup targetGroup) {
            return locationDistancePixels(targetGroup) / ((float) cellSizePixels / 2);
        }

        /** returns member cells
         *
         * @return
         */
        public HashSet<Cell> getMemberCells() {
            return memberCells;
        }

        /** returns the number of member cells
         *
         * @return
         */
        public int getNumMemberCells() {
            return memberCells.size();
        }

        /** returns the number of events
         *
         * @return
         */
        public int getNumEvents() {
            return numEvents;
        }

        /** returns the group mass.
         * Time constant is not necessary.
         * @return
         */
        public float getMass() {
            return mass;
        }

        /** returns the first event timestamp of the group.
         *
         * @return
         */
        public int getFirstEventTimestamp() {
            return firstEventTimestamp;
        }

        /** returns the last event timestamp of the group.
         *
         * @return
         */
        public int getLastEventTimestamp() {
            return lastEventTimestamp;
        }

        /** returns the location of the cell group.
         *
         * @return
         */
        public Float getLocation() {
            return location;
        }

        /** returns the inner radius of the cell group.
         *
         * @return
         */
        public float getInnerRadiusPixels() {
            return Math.min(Math.min(Math.abs(location.x - minX), Math.abs(location.x - maxX)), Math.min(Math.abs(location.y - minY), Math.abs(location.y - maxY)));
        }

        /** returns the outter radius of the cell group.
         *
         * @return
         */
        public float getOutterRadiusPixels() {
            return Math.max(Math.max(Math.abs(location.x - minX), Math.abs(location.x - maxX)), Math.max(Math.abs(location.y - minY), Math.abs(location.y - maxY)));
        }

        /** returns the raidus of the group area by assuming that the shape of the group is a square.
         *
         * @return
         */
        public float getAreaRadiusPixels() {
            return (float) Math.sqrt((float) getNumMemberCells()) * cellSizePixels / 4;
        }

        /** check if the targer location is within the inner radius of the group.
         *
         * @param targetLoc
         * @return
         */
        public boolean isWithinInnerRadius(Float targetLoc) {
            boolean ret = false;
            float innerRaidus = getInnerRadiusPixels();

            if (Math.abs(location.x - targetLoc.x) <= innerRaidus && Math.abs(location.y - targetLoc.y) <= innerRaidus) {
                ret = true;
            }

            return ret;
        }

        /** check if the targer location is within the outter radius of the group.
         *
         * @param targetLoc
         * @return
         */
        public boolean isWithinOuterRadius(Float targetLoc) {
            boolean ret = false;
            float outterRaidus = getOutterRadiusPixels();

            if (Math.abs(location.x - targetLoc.x) <= outterRaidus && Math.abs(location.y - targetLoc.y) <= outterRaidus) {
                ret = true;
            }

            return ret;
        }

        /** check if the targer location is within the area radius of the group.
         *
         * @param targetLoc
         * @return
         */
        public boolean isWithinAreaRadius(Float targetLoc) {
            boolean ret = false;
            float areaRaidus = getAreaRadiusPixels();

            if (Math.abs(location.x - targetLoc.x) <= areaRaidus && Math.abs(location.y - targetLoc.y) <= areaRaidus) {
                ret = true;
            }

            return ret;
        }

        /** check if the cell group contains the given event.
         * It checks the location of the events
         * @param ev
         * @return
         */
        public boolean contains(BasicEvent ev) {
            boolean ret = false;

            int subIndexX = (int) 2 * ev.getX() / cellSizePixels;
            int subIndexY = (int) 2 * ev.getY() / cellSizePixels;

            if (subIndexX >= numOfCellsX && subIndexY >= numOfCellsY) {
                ret = false;
            }

            if (!ret && subIndexX != numOfCellsX && subIndexY != numOfCellsY) {
                ret = validCellIndexSet.contains(subIndexX + subIndexY * numOfCellsX);
            }
            if (!ret && subIndexX != numOfCellsX && subIndexY != 0) {
                ret = validCellIndexSet.contains(subIndexX + (subIndexY - 1) * numOfCellsX);
            }
            if (!ret && subIndexX != 0 && subIndexY != numOfCellsY) {
                ret = validCellIndexSet.contains(subIndexX - 1 + subIndexY * numOfCellsX);
            }
            if (!ret && subIndexY != 0 && subIndexX != 0) {
                ret = validCellIndexSet.contains(subIndexX - 1 + (subIndexY - 1) * numOfCellsX);
            }

            return ret;
        }

        /** Returns true if the cell group contains border cells or corner cells.
         *
         */
        public boolean isHitEdge() {
            return hitEdge;
        }

        /** Returns true if the cell group is matched to a cluster
         * This is used in a tracker module
         * @return
         */
        public boolean isMatched() {
            return matched;
        }

        /** Set true if the cell group is matched to a cluster
         * So, other cluster cannot take this group as a cluster
         * @param matched
         */
        public void setMatched(boolean matched) {
            this.matched = matched;
        }
    } // End of class cellGroup



    /** Processes the incoming events to have blurring filter output after first running the blurring to update the Cells.
     *
     * @param in the input packet.
     * @return the packet after filtering by the enclosed FilterChain.
     */
    @Override
    synchronized public EventPacket<?> filterPacket (EventPacket<?> in){
        if ( in == null ){
            return null;
        }

        if ( getEnclosedFilterChain() != null ){
            out = getEnclosedFilterChain().filterPacket(in);
        }
        blurring(out);

        return out;
    }

    /** Allocate the incoming events into the cells
     *
     * @param in the input packet of BasicEvent
     * @return the original input packet
     */
    synchronized private EventPacket<?> blurring(EventPacket<?> in) {
        boolean updatedCells = false;

        if (in.getSize() == 0) {
            return out;
        }

        try {
            // add events to the corresponding cell
            for (BasicEvent ev : in) {

                // don't reset on nonmonotonic, rather reset on rewind, which happens automatically
//                if(ev.timestamp < lastTime){
//                    resetFilter();
//                }
                int subIndexX = (int) 2 * ev.getX() / cellSizePixels;
                int subIndexY = (int) 2 * ev.getY() / cellSizePixels;

                if (subIndexX >= numOfCellsX && subIndexY >= numOfCellsY) {
                    initFilter();
                }

                if (subIndexX != numOfCellsX && subIndexY != numOfCellsY) {
                    cellArray.get(subIndexX + subIndexY * numOfCellsX).addEvent(ev);
                }
                if (subIndexX != numOfCellsX && subIndexY != 0) {
                    cellArray.get(subIndexX + (subIndexY - 1) * numOfCellsX).addEvent(ev);
                }
                if (subIndexX != 0 && subIndexY != numOfCellsY) {
                    cellArray.get(subIndexX - 1 + subIndexY * numOfCellsX).addEvent(ev);
                }
                if (subIndexY != 0 && subIndexX != 0) {
                    cellArray.get(subIndexX - 1 + (subIndexY - 1) * numOfCellsX).addEvent(ev);
                }

                lastTime = ev.getTimestamp();
                updatedCells = maybeCallUpdateObservers(in, lastTime);

            }
        } catch (IndexOutOfBoundsException e) {
            initFilter();
            // this is in case cell list is modified by real time filter during updating cells
            log.warning(e.getMessage());
        }

        if (!updatedCells) {
            updateCells(lastTime); // at laest once per packet update list
        }

        return in;
    }


    /** Updates cell properties of all cells at time t.
     * Checks if the cell is active.
     * Checks if the cell has (a) neighbor(s).
     * Checks if the cell belongs to a group.
     * Set the cell property based on the test results.
     * @param t
     */
    synchronized private void updateCells(int t) {
        validCellIndexSet.clear();

       if (!cellArray.isEmpty()) {
            int timeSinceSupport;

            // reset number of group before starting update
            numOfGroup = 0;
            cellGroup.clear(); 
            for(Cell tmpCell:cellArray){
                try {
                    // reset stale cells
                    timeSinceSupport = t - tmpCell.lastEventTimestamp;
                    if (timeSinceSupport > cellLifeTimeUs) {
                        tmpCell.reset();
                    }

                    // calculate the cell number of neighbor cells
                    int cellIndexX = (int) tmpCell.getCellIndex().x;
                    int cellIndexY = (int) tmpCell.getCellIndex().y;
                    int up = cellIndexX + (cellIndexY + 1) * numOfCellsX;
                    int down = cellIndexX + (cellIndexY - 1) * numOfCellsX;
                    int right = cellIndexX + 1 + cellIndexY * numOfCellsX;
                    int left = cellIndexX - 1 + cellIndexY * numOfCellsX;

                    tmpCell.setNumOfNeighbors(0);
                    switch (tmpCell.getCellType()) {
                        case CORNER_00:
                            // check threshold of the first cell
                            tmpCell.isAboveThreshold();

                            if (cellArray.get(up).isAboveThreshold()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(right).isAboveThreshold()) {
                                tmpCell.increaseNumOfNeighbors();
                            }

                            if (tmpCell.getNumOfNeighbors() > 0) {
                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                            }

                            if (tmpCell.getNumOfNeighbors() == 2) {
                                tmpCell.setGroupTag(-1);
                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(up).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(right).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                updateGroup(tmpCell, UPDATE_UP | UPDATE_RIGHT);
                            }
                            break;
                        case CORNER_01:
                            if (cellArray.get(down).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(right).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }

                            if (tmpCell.getNumOfNeighbors() > 0) {
                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                            }

                            if (tmpCell.getNumOfNeighbors() == 2) {
                                if (cellArray.get(right).getGroupTag() == cellArray.get(down).getGroupTag()) {
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                } else {
                                    tmpCell.setGroupTag(-1);
                                }

                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(right).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(down).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_DOWN | UPDATE_RIGHT);
                            }
                            break;
                        case CORNER_10:
                            if (cellArray.get(up).isAboveThreshold()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(left).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }

                            if (tmpCell.getNumOfNeighbors() > 0) {
                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                            }

                            if (tmpCell.getNumOfNeighbors() == 2) {
                                tmpCell.setGroupTag(-1);
                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(up).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(left).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_UP | UPDATE_LEFT);
                            }
                            break;
                        case CORNER_11:
                            if (cellArray.get(down).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(left).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }

                            if (tmpCell.getNumOfNeighbors() > 0) {
                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                            }

                            if (tmpCell.getNumOfNeighbors() == 2) {
                                if (cellArray.get(left).getGroupTag() == cellArray.get(down).getGroupTag()) {
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                } else {
                                    if (cellArray.get(left).getGroupTag() > 0 && cellArray.get(down).getGroupTag() > 0) {
                                        tmpCell.setGroupTag(Math.min(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));

                                        // do merge here
                                        int targetGroupTag = Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag());
                                        cellGroup.get(tmpCell.getGroupTag()).merge(cellGroup.get(targetGroupTag));
                                        cellGroup.remove(targetGroupTag);
                                    } else if (cellArray.get(left).getGroupTag() < 0 && cellArray.get(down).getGroupTag() < 0) {
                                        tmpCell.setGroupTag(-1);
                                    } else {
                                        tmpCell.setGroupTag(Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));
                                    }
                                }

                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(down).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(left).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_DOWN | UPDATE_LEFT);
                            }
                            break;
                        case EDGE_0Y:
                            if (cellArray.get(up).isAboveThreshold()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(down).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(right).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }

                            if (tmpCell.getNumOfNeighbors() > 0) {
                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                            }

                            if (tmpCell.getNumOfNeighbors() == 3) {
                                if (cellArray.get(right).getGroupTag() == cellArray.get(down).getGroupTag()) {
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                } else {
                                    tmpCell.setGroupTag(-1);
                                }

                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(up).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(down).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(right).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_UP | UPDATE_DOWN | UPDATE_RIGHT);
                            }
                            break;
                        case EDGE_1Y:
                            if (cellArray.get(up).isAboveThreshold()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(down).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(left).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }

                            if (tmpCell.getNumOfNeighbors() > 0) {
                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                            }

                            if (tmpCell.getNumOfNeighbors() == 3) {
                                if (cellArray.get(left).getGroupTag() == cellArray.get(down).getGroupTag()) {
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                } else {
                                    if (cellArray.get(left).getGroupTag() > 0 && cellArray.get(down).getGroupTag() > 0) {
                                        tmpCell.setGroupTag(Math.min(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));

                                        // do merge here
                                        int targetGroupTag = Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag());
                                        cellGroup.get(tmpCell.getGroupTag()).merge(cellGroup.get(targetGroupTag));
                                        cellGroup.remove(targetGroupTag);
                                    } else if (cellArray.get(left).getGroupTag() < 0 && cellArray.get(down).getGroupTag() < 0) {
                                        tmpCell.setGroupTag(-1);
                                    } else {
                                        tmpCell.setGroupTag(Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));
                                    }
                                }

                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(up).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(down).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(left).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_UP | UPDATE_DOWN | UPDATE_LEFT);
                            }
                            break;
                        case EDGE_X0:
                            if (cellArray.get(up).isAboveThreshold()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(right).isAboveThreshold()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(left).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }

                            if (tmpCell.getNumOfNeighbors() > 0) {
                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                            }

                            if (tmpCell.getNumOfNeighbors() == 3) {
                                tmpCell.setGroupTag(-1);
                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(up).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(right).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(left).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_UP | UPDATE_RIGHT | UPDATE_LEFT);
                            }
                            break;
                        case EDGE_X1:
                            if (cellArray.get(down).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(right).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(left).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }

                            if (tmpCell.getNumOfNeighbors() > 0) {
                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                            }

                            if (tmpCell.getNumOfNeighbors() == 3) {
                                if (cellArray.get(right).getGroupTag() == cellArray.get(down).getGroupTag()) {
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                }

                                if (cellArray.get(left).getGroupTag() == cellArray.get(down).getGroupTag()) {
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                } else {
                                    if (cellArray.get(left).getGroupTag() > 0 && cellArray.get(down).getGroupTag() > 0) {
                                        tmpCell.setGroupTag(Math.min(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));

                                        // do merge here
                                        int targetGroupTag = Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag());
                                        cellGroup.get(tmpCell.getGroupTag()).merge(cellGroup.get(targetGroupTag));
                                        cellGroup.remove(targetGroupTag);
                                    } else if (cellArray.get(left).getGroupTag() < 0 && cellArray.get(down).getGroupTag() < 0) {
                                        tmpCell.setGroupTag(-1);
                                    } else {
                                        tmpCell.setGroupTag(Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));
                                    }
                                }

                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(right).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(down).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(left).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_DOWN | UPDATE_RIGHT | UPDATE_LEFT);
                            }
                            break;
                        case INSIDE:
                            if (cellArray.get(up).isAboveThreshold()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(down).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(right).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }
                            if (cellArray.get(left).isVisible()) {
                                tmpCell.increaseNumOfNeighbors();
                            }

                            if (tmpCell.getNumOfNeighbors() > 0 && tmpCell.isVisible()) {
                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                                }
                            }

                            if (tmpCell.getNumOfNeighbors() == 4 && tmpCell.isVisible()) {
                                if (cellArray.get(right).getGroupTag() == cellArray.get(down).getGroupTag()) {
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                }

                                if (cellArray.get(left).getGroupTag() == cellArray.get(down).getGroupTag()) {
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                } else {
                                    if (cellArray.get(left).getGroupTag() > 0 && cellArray.get(down).getGroupTag() > 0) {
                                        tmpCell.setGroupTag(Math.min(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));

                                        // do merge here
                                        int targetGroupTag = Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag());
                                        cellGroup.get(tmpCell.getGroupTag()).merge(cellGroup.get(targetGroupTag));
                                        cellGroup.remove(targetGroupTag);
                                    } else if (cellArray.get(left).getGroupTag() < 0 && cellArray.get(down).getGroupTag() < 0) {
                                        tmpCell.setGroupTag(-1);
                                    } else {
                                        tmpCell.setGroupTag(Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));
                                    }
                                }

                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(up).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(down).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(right).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(left).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);

                                updateGroup(tmpCell, UPDATE_UP | UPDATE_DOWN | UPDATE_RIGHT | UPDATE_LEFT);
                            }
                            break;
                        default:
                            break;
                    } // End of switch
                    if (tmpCell.getCellProperty() == CellProperty.VISIBLE_INSIDE || tmpCell.getCellProperty() == CellProperty.VISIBLE_BORDER) {
                        validCellIndexSet.add(tmpCell.cellNumber);
                    }
                } catch (java.util.ConcurrentModificationException e) {
                    // this is in case cell list is modified by real time filter during updating cells
                    initFilter();
                    log.warning(e.getMessage());
                }

            } // End of while
        } // End of if
    }

    /** Updates the cell group with a new member cell
     *
     * @param inCell : new member cell
     * @param updateOption : option for updating neighbor cells. Selected neighbors are updated together.
     * All neighbor cells are updated together with option 'UPDATE_UP | UPDATE_DOWN | UPDATE_RIGHT | UPDATE_LEFT'.
     */
    private final void updateGroup(Cell inCell, int updateOption) {
        CellGroup tmpGroup = null;
        if (cellGroup.containsKey(inCell.getGroupTag())) {
            tmpGroup = cellGroup.get(inCell.getGroupTag());
            tmpGroup.add(inCell);
        } else {
            tmpGroup = new CellGroup(inCell);
            cellGroup.put(tmpGroup.tag, tmpGroup);
        }

        int cellIndexX = (int) inCell.getCellIndex().x;
        int cellIndexY = (int) inCell.getCellIndex().y;
        int up = cellIndexX + (cellIndexY + 1) * numOfCellsX;
        int down = cellIndexX + (cellIndexY - 1) * numOfCellsX;
        int right = cellIndexX + 1 + cellIndexY * numOfCellsX;
        int left = cellIndexX - 1 + cellIndexY * numOfCellsX;

        if ((updateOption & UPDATE_UP) > 0) {
            tmpGroup.add(cellArray.get(up));
        }
        if ((updateOption & UPDATE_DOWN) > 0) {
            tmpGroup.add(cellArray.get(down));
        }
        if ((updateOption & UPDATE_RIGHT) > 0) {
            tmpGroup.add(cellArray.get(right));
        }
        if ((updateOption & UPDATE_LEFT) > 0) {
            tmpGroup.add(cellArray.get(left));
        }
    }

    /** Draw cell
     *
     * @param gl
     * @param x
     * @param y
     * @param sx
     * @param sy
     */
    protected void drawCell(GL gl, int x, int y, int sx, int sy) {
        gl.glPushMatrix();
        gl.glTranslatef(x, y, 0);

        if (filledCells) {
            gl.glBegin(GL.GL_QUADS);
        } else {
            gl.glBegin(GL.GL_LINE_LOOP);
        }

        {
            gl.glVertex2i(-sx, -sy);
            gl.glVertex2i(+sx, -sy);
            gl.glVertex2i(+sx, +sy);
            gl.glVertex2i(-sx, +sy);
        }
        gl.glEnd();
        gl.glPopMatrix();
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
        gl.glPushMatrix();
        try {
            if (showCells) {
                Cell tmpCell;
                for (int i = 0; i < cellArray.size(); i++) {
                    tmpCell = cellArray.get(i);

                    if (showBorderCellsOnly && tmpCell.getCellProperty() == CellProperty.VISIBLE_BORDER)
                        tmpCell.draw(drawable);

                    if (showInsideCellsOnly && tmpCell.getCellProperty() == CellProperty.VISIBLE_INSIDE)
                        tmpCell.draw(drawable);

                    if (!showBorderCellsOnly && !showInsideCellsOnly)
                        if(tmpCell.getCellProperty() != CellProperty.NOT_VISIBLE)
                            tmpCell.draw(drawable);
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            // this is in case cell list is modified by real time filter during rendering of cells
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
    }


    @Override
    synchronized public void initFilter() {
        int prev_numOfCellsX = numOfCellsX;
        int prev_numOfCellsY = numOfCellsY;

        // calculate the required number of cells
        if (2 * mychip.getSizeX() % cellSizePixels == 0) {
            numOfCellsX = (int) (2 * mychip.getSizeX() / cellSizePixels) - 1;
        } else {
            numOfCellsX = (int) (2 * mychip.getSizeX() / cellSizePixels);
        }

        if (2 * mychip.getSizeY() % cellSizePixels == 0) {
            numOfCellsY = (int) (2 * mychip.getSizeY() / cellSizePixels) - 1;
        } else {
            numOfCellsY = (int) (2 * mychip.getSizeY() / cellSizePixels);
        }

        lastTime = 0;
        validCellIndexSet.clear();
        cellGroup.clear();
        numOfGroup = 0;


        // initialize all cells
        if ((numOfCellsX > 0 && numOfCellsY > 0) &&
                (prev_numOfCellsX != numOfCellsX || prev_numOfCellsY != numOfCellsY)) {
            if (!cellArray.isEmpty()) {
                cellArray.clear();
            }

            for (int j = 0; j < numOfCellsY; j++) {
                for (int i = 0; i < numOfCellsX; i++) {
                    Cell newCell = new Cell(i, j);

                    if (i == 0) {
                        if (j == 0) {
                            newCell.setCellType(CellType.CORNER_00);
                        } else if (j == numOfCellsY - 1) {
                            newCell.setCellType(CellType.CORNER_01);
                        } else {
                            newCell.setCellType(CellType.EDGE_0Y);
                        }
                    } else if (i == numOfCellsX - 1) {
                        if (j == 0) {
                            newCell.setCellType(CellType.CORNER_10);
                        } else if (j == numOfCellsY - 1) {
                            newCell.setCellType(CellType.CORNER_11);
                        } else {
                            newCell.setCellType(CellType.EDGE_1Y);
                        }
                    } else {
                        if (j == 0) {
                            newCell.setCellType(CellType.EDGE_X0);
                        } else if (j == numOfCellsY - 1) {
                            newCell.setCellType(CellType.EDGE_X1);
                        } else {
                            newCell.setCellType(CellType.INSIDE);
                        }
                    }

                    cellArray.add(newCell.getCellNumber(), newCell);
                }
            }
        }
    }

    @Override
    synchronized public void resetFilter() {
        for (Cell c : cellArray) {
            c.reset();
        }

        lastTime = 0;
        validCellIndexSet.clear();
        cellGroup.clear();
        numOfGroup = 0;
    }

    /** returns the time constant of the cell mass
     *
     * @return time constant of the cell mass
     */
    public int getCellMassTimeConstantUs() {
        return cellMassTimeConstantUs;
    }

    /** set the time constant of the cell mass
     *
     * @param cellMassTimeConstantUs
     */
    public void setCellMassTimeConstantUs(int cellMassTimeConstantUs) {
        this.cellMassTimeConstantUs = cellMassTimeConstantUs;
        getPrefs().putInt("BlurringFilter2D.cellMassTimeConstantUs", cellMassTimeConstantUs);
    }

    /** returns the life time the cell to be reset without additional events
     *
     * @return
     */
    public int getCellLifeTimeUs() {
        return cellLifeTimeUs;
    }

    /** set the life time the cell
     *
     * @param cellLifeTimeUs
     */
    public void setCellLifeTimeUs(int cellLifeTimeUs) {
        this.cellLifeTimeUs = cellLifeTimeUs;
        getPrefs().putInt("BlurringFilter2D.cellLifeTimeUs", cellLifeTimeUs);
    }

    /** returns the cell size in pixels
     *
     * @return ell size in pixels
     */
    public int getCellSizePixels() {
        return cellSizePixels;
    }

    /** set the cell size
     *
     * @param cellSizePixels
     */
    synchronized public void setCellSizePixels(int cellSizePixels) {
        this.cellSizePixels = cellSizePixels;
        getPrefs().putInt("BlurringFilter2D.cellSizePixels", cellSizePixels);
        initFilter();
    }

    /** returns the threshold of number of events.
     * Only the cells with the number of events above this value will be active and visible on the screen.
     *
     * @return threshold of number of events
     */
    public int getThresholdEventsForVisibleCell() {
        return thresholdEventsForVisibleCell;
    }

    /** set the threshold of number of events.
     * Only the cells with the number of events above this value will be active and visible on the screen.
     *
     * @param thresholdEventsForVisibleCell
     */
    public void setThresholdEventsForVisibleCell(int thresholdEventsForVisibleCell) {
        this.thresholdEventsForVisibleCell = thresholdEventsForVisibleCell;
        getPrefs().putInt("BlurringFilter2D.thresholdEventsForVisibleCell", thresholdEventsForVisibleCell);
    }

    /**returns the threshold of cell mass.
     * Only the cells with mass above this value will be active and visible on the screen.
     *
     * @return
     */
    public int getThresholdMassForVisibleCell() {
        return thresholdMassForVisibleCell;
    }

    /**set the threshold of cell mass.
     * Only the cells with mass above this value will be active and visible on the screen.
     *
     * @param thresholdMassForVisibleCell
     */
    public void setThresholdMassForVisibleCell(int thresholdMassForVisibleCell) {
        this.thresholdMassForVisibleCell = thresholdMassForVisibleCell;
        getPrefs().putInt("BlurringFilter2D.thresholdMassForVisibleCell", thresholdMassForVisibleCell);
    }

    /** returns true if the active cells are visible on the screen
     *
     * @return
     */
    public boolean isShowCells() {
        return showCells;
    }

    /** set true if you want the active cells visible on the screen
     *
     * @param showCells
     */
    public void setShowCells(boolean showCells) {
        this.showCells = showCells;
        getPrefs().putBoolean("BlurringFilter2D.showCells", showCells);
    }

    /** return true if only the border cells are visible on the screen
     *
     * @return true if only the border cells are visible on the screen
     */
    public boolean isshowBorderCellsOnly() {
        return showBorderCellsOnly;
    }

    /** set true if you want the border cells visible on the screen
     *
     * @param showBorderCellsOnly
     */
    public void setshowBorderCellsOnly(boolean showBorderCellsOnly) {
        this.showBorderCellsOnly = showBorderCellsOnly;
        getPrefs().putBoolean("BlurringFilter2D.showBorderCellsOnly", showBorderCellsOnly);
    }

    /** return true if only the inside cells are visible on the screen
     *
     * @return true if only the inside cells are visible on the screen
     */
    public boolean isshowInsideCellsOnly() {
        return showInsideCellsOnly;
    }

    /** set true if you want the inside cells visible on the screen
     *
     * @param showInsideCellsOnly
     */
    public void setshowInsideCellsOnly(boolean showInsideCellsOnly) {
        this.showInsideCellsOnly = showInsideCellsOnly;
        getPrefs().putBoolean("BlurringFilter2D.showInsideCellsOnly", showInsideCellsOnly);
    }

    /** return true if the visible cells are displayed with filled square on the screen.
     *
     * @return
     */
    public boolean isFilledCells() {
        return filledCells;
    }

    /** set true if you want the visible cells displayed with filled square on the screen.
     *
     * @param filledCells
     */
    public void setFilledCells(boolean filledCells) {
        this.filledCells = filledCells;
        getPrefs().putBoolean("BlurringFilter2D.filledCells", filledCells);
    }

    /** returns the number of cell groups detected
     *
     * @return number of cell groups detected at each update
     */
    public int getNumOfGroup() {
        return numOfGroup;
    }

    /** returns cell groups
     *
     * @return collection of cell groups
     */
    public Collection getCellGroup() {
        return cellGroup.values();
    }

    /** returns the last timestamp ever recorded at this filter
     *
     * @return the last timestamp ever recorded at this filter
     */
    public int getLastTime() {
        return lastTime;
    }
}
