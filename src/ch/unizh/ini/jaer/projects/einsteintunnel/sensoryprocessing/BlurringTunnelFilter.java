/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing;

import java.awt.Color;
import java.awt.geom.Point2D.Float;
import javax.media.opengl.GL;
import java.awt.geom.Point2D;
import java.util.*;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.eventprocessing.filter.*;

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
 * @author Jun Haeng Lee customized for Einstein Passage Project by braendch
 */
public class BlurringTunnelFilter extends EventFilter2D implements FrameAnnotater, Observer {

    /* properties */
    private int cellMassTimeConstantUs = getPrefs().getInt("BlurringFilter2D.cellMassTimeConstantUs", 100000);
    private int cellLifeTimeUs = getPrefs().getInt("BlurringFilter2D.cellLifeTimeUs", 50000);
    private int thresholdMassForVisibleCell = getPrefs().getInt("BlurringFilter2D.thresholdMassForVisibleCell", 2);
    private boolean showCells = getPrefs().getBoolean("BlurringFilter2D.showCells", true);
    private boolean filledCells = getPrefs().getBoolean("BlurringFilter2D.filledCells", false);
    private int cellSizePixels = getPrefs().getInt("BlurringFilter2D.cellSizePixels", 5);
	private int maxGroupSizeX = getPrefs().getInt("BlurringFilter2D.maxGroupSizeX", 50);

    /* Constants to define neighbor cells */
    static int UPDATE_UP = 0x01;
    static int UPDATE_DOWN = 0x02;
    static int UPDATE_RIGHT = 0x04;
    static int UPDATE_LEFT = 0x08;

    /* variables */
    private int numOfCellsX = 0, numOfCellsY = 0;  // number of cells in x (column) and y (row) directions.
    private ArrayList<Cell> cellArray = new ArrayList<Cell>();// array of cells
    private HashSet<Integer> validCellIndexSet = new HashSet(); // index of active cells which have mass greater than the threshold
    private HashSet<Integer> updatedIndexSet = new HashSet(); // index of active cells been updated within a update loop
    private HashMap<Integer, CellGroup> cellGroups = new HashMap<Integer, CellGroup>(); // cell groups found
    /**
     * DVS Chip
     */
    protected AEChip mychip;
    /**
     * Blurring filter to getString clusters
     */
    protected BackgroundActivityFilter bfilter;
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
    public BlurringTunnelFilter(AEChip chip) {
        super(chip);
        this.mychip = chip;
        filterChainSetting();
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
		setPropertyTooltip(sizing, "maxGroupSizeX", "Maximal size of a cell group (along the x axis) before it gets disolved.");


    }

    protected void filterChainSetting() {
        bfilter = new BackgroundActivityFilter(chip);
        bfilter.addObserver(this); // to getString us called during blurring filter iteration at least every updateIntervalUs
        setEnclosedFilter(bfilter);
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
        /** The "mass" of the cell is the weighted number of events it has collected.
         * The mass decays over time and is incremented by one by each collected event.
         * The mass decays with a first order time constant of cellMassTimeConstantUs in us.
         */
        protected float mass = 0.0f;
	/**
         *  number of events
         */
        protected int numEvents = 0;
        /** This is the last and first time in timestamp ticks that the cell was updated, by an event
         * This time can be used to compute postion updates given a cell velocity and time now.
         */
        protected int lastEventTimestamp = 0;
        protected int firstEventTimestamp = 0;
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
            resetGroupTag();
            visible = false;
            mass = 0.0f;
			numEvents = 0;
            lastEventTimestamp = firstEventTimestamp = 0;
        }

        /**
         * Reset a cell with initial values
         */
        public void reset() {
	    //log.info("Reset cell "+String.valueOf(cellNumber));
            mass = 0.0f;
            visible = false;
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

		protected void drawCell(GLAutoDrawable drawable) {
			final float BOX_LINE_WIDTH = 2f; // in chip
			GL gl = drawable.getGL();

			setColorAccordingToGroup();
			// set color and line width
			gl.glColor3fv(color.getRGBColorComponents(null), 0);
			gl.glLineWidth(BOX_LINE_WIDTH);

			// draws the receptive field of a neuron
			gl.glPushMatrix();
			gl.glTranslatef((int) getLocation().x, (int) getLocation().y, 0);

			if (filledCells) {
				gl.glBegin(GL.GL_QUADS);
			} else {
				gl.glBegin(GL.GL_LINE_LOOP);
			}

			int halfSize = (int) cellSizePixels / 2;
			gl.glVertex2i(-halfSize, -halfSize);
			gl.glVertex2i(+halfSize, -halfSize);
			gl.glVertex2i(+halfSize, +halfSize);
			gl.glVertex2i(-halfSize, +halfSize);

			gl.glEnd();
			gl.glPopMatrix();
		}

        /** updates cell by one event.
         *
         * @param event
         */
        public void addEvent(BasicEvent event) {
	    numEvents++;
            incrementMass(event.getTimestamp());
            lastEventTimestamp = event.getTimestamp();
            if (firstEventTimestamp == 0) {
                firstEventTimestamp = lastEventTimestamp;
            }
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
            mass = 1.0f + mass * (float) Math.exp(((float) lastEventTimestamp - timeStamp) / cellMassTimeConstantUs);
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
        public boolean isAboveThreshold() {
            if (getMassNow(lastTime) < thresholdMassForVisibleCell) {
		if(groupTag>0){
		    //log.info("remove cell "+String.valueOf(cellNumber));
		    if(cellGroups.containsKey(groupTag))cellGroups.get(groupTag).remove(this);
		}
		groupTag = -1;
		numEvents = 0;
		visible = false;
            } else {
                visible = true;
            }
            return visible;
        }

        @Override
        public String toString() {
            return String.format("Cell index=(%d, %d), location = (%d, %d), mass = %.2f, numEvents=%d",
                    (int) cellIndex.x, (int) cellIndex.y,
                    (int) location.x, (int) location.y,
                    mass);
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

         /** Sets color according to measured cell mass
         *
         */
        public void setColorAccordingToGroup() {
	    if(groupTag > 0){
		Color c = cellGroups.get(groupTag).getColor();
		setColor(c);
	    }
        }

        /** Set color automatically
         * Currently ,it's done based on the cell mass
         */
        public void setColorAutomatically() {
            setColorAccordingToGroup();
	    //setColorAccordingToMass();
	    //log.info(String.valueOf(groupTag));
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
            this.groupTag = groupTag;
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
        protected float mass; // Sum of the mass of all member cells.
        /** This is the last and the first time in timestamp ticks that the group was updated by an event.
         * The largest one among the lastUpdateTime of all member cells becomes groups's lastEventTimestamp.
         * The smallest one among the firstUpdateTime of all member cells becomes groups's firstEventTimestamp.
         */
        protected Color color;
        protected int lastEventTimestamp, firstEventTimestamp;
        /** Parameters to represent the area of the group.
         * minX(Y) : minimum X(Y) among the locations of member cells
         * maxX(Y) : maximum X(Y) among the locations of member cells
         */
        protected float minX, maxX, minY, maxY;
		protected int numEvents = 0;
        protected int tag = -1; // Group number (index)
		protected int trackerIndex = -1; // tracker which covers group
        protected boolean hitEdge = false; // Indicate if this cell group is hitting edge
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
            float hue = random.nextFloat();
            Color c = Color.getHSBColor(hue, 1f, 1f);
            setColor(c);
            //log.info("New group "+String.valueOf(tag));
        }

        /** reset the cell group
         *
         */
        public void reset() {
            location.setLocation(-1f, -1f);
            mass = 0;
            tag = -1;
			numEvents = 0;
            memberCells.clear();
            maxX = maxY = 0;
            minX = chip.getSizeX();
            minY = chip.getSizeX();
            hitEdge = false;
            trackerIndex = -1;
        }

        /**
         *  adds a cell into the cell group
         * @param inCell
         */
        public void add(Cell inCell) {
            // if the first cell is added
            if (tag < 0) {
                boolean tagFound = false;
                int possibleTag = 1;
                while(!tagFound){
                    if(cellGroups.containsKey(possibleTag)){
                        possibleTag ++;
                    } else {
                        tag = possibleTag;
						tagFound = true;
                    }
                }
                firstEventTimestamp = inCell.getFirstEventTimestamp();
                lastEventTimestamp = inCell.getLastEventTimestamp();
                location.x = inCell.location.x;
                location.y = inCell.location.y;
                mass = inCell.getMass();
            } else {
                float prev_mass = mass;
                float leakyFactor;

                firstEventTimestamp = Math.min(firstEventTimestamp, inCell.getFirstEventTimestamp());

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
	    inCell.setGroupTag(tag);
	    numEvents += inCell.numEvents;

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
	    //log.info(String.valueOf(tag)+" merged with "+String.valueOf(targetGroup.tag));
            if (targetGroup == null) {
                return;
            }
            float prev_mass = mass;
            float leakyFactor;

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
	    if(this.firstEventTimestamp<targetGroup.firstEventTimestamp){
		for (Cell tmpCell : targetGroup.getMemberCells()) {
		    this.add(tmpCell);
		}
		cellGroups.remove(targetGroup.tag);
	    } else {
		for (Cell thisCell : this.getMemberCells()) {
		    targetGroup.add(thisCell);
		}
		cellGroups.remove(this.tag);
	    }
        }

        /** removes a Cell from a group
         *
         * @param cell
         */
        public void remove(Cell rmCell) {
			numEvents -= rmCell.numEvents;

            rmCell.setGroupTag(-1);
            memberCells.remove(rmCell);

            if(memberCells.size()>0){
                //recalculate Min, Max
                maxX = maxY = 0;
                minX = chip.getSizeX();
                minY = chip.getSizeX();
                hitEdge = false;
                Iterator cellItr = memberCells.iterator();
                while(cellItr.hasNext()){
                    Cell inCell = (Cell)cellItr.next();
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
                }
            } else {
                deleteGroup(this);
            }
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

        /** returns the tag of the group
         *
         * @return
         */
        public int getTag() {
            return tag;
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

        /** sets the color
         *
         */
        public void setColor(Color color) {
            this.color = color;
        }

        /** returns the color
         *
         * @return
         */
        public Color getColor() {
            return color;
        }

        /** returns the group mass.
         * Time constant is not necessary.
         * @return
         */
        public float getMass() {
            return mass;
        }

        protected float getMassNow(int t) {
            float m = mass * (float) Math.exp(((float) (lastEventTimestamp - t)) / cellMassTimeConstantUs);
            return m;
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
         * @returntmpCell.getGroupTag() > 0 && downCell.getGroupTag() > 0 &&
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

        /** Returns the index of the covering tracker
         * @return
         */
        public int getTrackerIndex() {
            return trackerIndex;
        }

        /** Set the index of the covering tracker
         * @param matched
         */
        public void setTrackerIndex(int trackerIndex) {
            this.trackerIndex = trackerIndex;
        }
    } // End of class cellGroup


    /** Processes the incoming events to have blurring filter output after first running the blurring to update the Cells.
     *
     * @param in the input packet.
     * @return the packet after filtering by the enclosed FilterChain.
     */
    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (in == null) {
            return null;
        }

        if (enclosedFilter != null) {
            out = enclosedFilter.filterPacket(in);
        } else {
            out = in;
        }

        if (getEnclosedFilterChain() != null) {
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
        Cell thisCell = null;

        if (in == null) {
            return in;
        }

        if (in.getSize() == 0) {
            return in;
        }

        try {
            // add events to the corresponding cell
            for (int i = 0; i < in.getSize(); i++) {
                BasicEvent ev = in.getEvent(i);

                // don't reset on nonmonotonic, rather reset on rewind, which happens automatically
//                if(ev.timestamp < lastTime){
//                    resetFilter();
//                }
                int subIndexX = (int) (2.0f * ev.getX() / cellSizePixels);
                int subIndexY = (int) (2.0f * ev.getY() / cellSizePixels);

                if (subIndexX >= numOfCellsX && subIndexY >= numOfCellsY) {
                    initFilter();
                }

                if (subIndexX != numOfCellsX && subIndexY != numOfCellsY) {
                    thisCell = cellArray.get(subIndexX + subIndexY * numOfCellsX);
                }
                if(subIndexX != numOfCellsX && subIndexY != 0) {
                    thisCell = cellArray.get(subIndexX + (subIndexY - 1) * numOfCellsX);
                }
                if (subIndexX != 0 && subIndexY != numOfCellsY) {
                    thisCell = cellArray.get(subIndexX - 1 + subIndexY * numOfCellsX);
                }
                if (subIndexY != 0 && subIndexX != 0) {
                    thisCell = cellArray.get(subIndexX - 1 + (subIndexY - 1) * numOfCellsX);
                }

                lastTime = ev.getTimestamp();
                thisCell.addEvent(ev);
                updateCell(lastTime, thisCell);
                //maybeCallUpdateObservers(in, lastTime);

            }
        } catch (IndexOutOfBoundsException e) {
            initFilter();
            // this is in case cell list is modified by real time filter during updating cells
            log.warning(e.getMessage());
        }

        updateCells();
        //updateCellGroups();

        return in;
    }

    private void updateCellGroups(){
		//log.info("update cell groups");
        HashSet<CellGroup> oldGroups = new HashSet<CellGroup>();// array of cells
		Iterator groupItr = cellGroups.keySet().iterator();
        while(groupItr.hasNext()) {
            Object key = groupItr.next();
            CellGroup tmpGroup  = cellGroups.get((Integer)key);
			if(tmpGroup.getMassNow(lastTime)<thresholdMassForVisibleCell){
				oldGroups.add(tmpGroup);
			}
        }
		Iterator delItr = oldGroups.iterator();
		while(delItr.hasNext()){
			deleteGroup((CellGroup)delItr.next());
		}
    }

    synchronized private void updateCells(){
	//log.info("update cells");
         for (int i = 0; i < cellArray.size(); i++) {
            Cell tmpCell = cellArray.get(i);
	    //if(tmpCell.getGroupTag()>0)log.info("cell "+String.valueOf(tmpCell.cellNumber)+" is in group "+String.valueOf(tmpCell.groupTag));
            tmpCell.isAboveThreshold();
        }
    }

    /** Updates cell properties of all cells at time t.
     * Checks if the cell is active.
     * Checks if the cell has (a) neighbor(s).
     * Checks if the cell belongs to a group.
     * Set the cell property based on the test results.
     * @param t
     */
    synchronized private void updateCell(int t, Cell tmpCell) {

        validCellIndexSet.clear();

        if (!cellArray.isEmpty()) {
            int timeSinceSupport;

            Cell upCell, downCell, leftCell, rightCell;
                try {
                    // reset stale cells
                    timeSinceSupport = t - tmpCell.lastEventTimestamp;
                    if (timeSinceSupport > cellLifeTimeUs) {
                        tmpCell.reset();
                    }

                    // calculate the cell number of neighbor cells
                    int cellIndexX = (int) tmpCell.getCellIndex().x;
                    int cellIndexY = (int) tmpCell.getCellIndex().y;

                    switch (tmpCell.getCellType()) {
//                        case CORNER_00:
//                            upCell = cellArray.get(cellIndexX + (cellIndexY + 1) * numOfCellsX);
//                            rightCell = cellArray.get(cellIndexX + 1 + cellIndexY * numOfCellsX);
//
//                            if (upCell.isAboveThreshold()) {
//                                tmpCell.increaseNumOfNeighbors();
//                                if(tmpCell.getGroupTag() != upCell.getGroupTag()){
//                                        cellGroups.get(tmpCell.getGroupTag()).merge(cellGroups.get(upCell.getGroupTag()));
//                                    }
//                            }
//                            if (rightCell.isAboveThreshold()) {
//                                tmpCell.increaseNumOfNeighbors();
//                                if(tmpCell.getGroupTag() != rightCell.getGroupTag()){
//                                        cellGroups.get(tmpCell.getGroupTag()).merge(cellGroups.get(rightCell.getGroupTag()));
//                                    }
//                            }
//
//                            if(tmpCell.isAboveThreshold()){
//                                if(tmpCell.getGroupTag()<0){
//                                    CellGroup newGroup = new CellGroup(tmpCell);
//                                    cellGroups.put(newGroup.getTag(), newGroup);
//                                }
//                            }
//                            updatedIndexSet.add(tmpCell.cellNumber);
//
//                            if (tmpCell.getNumOfNeighbors() > 0) {
//                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
//                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
//                                }
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() == 2) {
//                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);
//
//                                upCell.setPropertyToBorder( CellPropertyUpdate.FORCED);
//                                rightCell.setPropertyToBorder(CellPropertyUpdate.FORCED);
//                            }
//                            break;
//                        case CORNER_01:
//                            downCell = cellArray.get(cellIndexX + (cellIndexY - 1) * numOfCellsX);
//                            rightCell = cellArray.get(cellIndexX + 1 + cellIndexY * numOfCellsX);
//
//                            if (downCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//                            if (rightCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() > 0) {
//                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
//                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
//                                }
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() == 2) {
//                                if (rightCell.getGroupTag() == downCell.getGroupTag()) {
//                                    tmpCell.setGroupTag(downCell.getGroupTag());
//                                } else {
//                                    tmpCell.setGroupTag(-1);
//                                }
//
//                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);
//
//                                rightCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                                downCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                            }
//                            break;
//                        case CORNER_10:
//                            upCell = cellArray.get(cellIndexX + (cellIndexY + 1) * numOfCellsX);
//                            leftCell = cellArray.get(cellIndexX - 1 + cellIndexY * numOfCellsX);
//
//                            if (upCell.isAboveThreshold()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//                            if (leftCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() > 0) {
//                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
//                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
//                                }
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() == 2) {
//                                tmpCell.setGroupTag(-1);
//                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);
//
//                                upCell.setPropertyToBorder(CellPropertyUpdate.FORCED);
//                                leftCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                            }
//                            break;
//                        case CORNER_11:
//                            downCell = cellArray.get(cellIndexX + (cellIndexY - 1) * numOfCellsX);
//                            leftCell = cellArray.get(cellIndexX - 1 + cellIndexY * numOfCellsX);
//
//                            if (downCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//                            if (leftCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() > 0) {
//                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
//                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
//                                }
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() == 2) {
//                                if (leftCell.getGroupTag() == downCell.getGroupTag()) {
//                                    tmpCell.setGroupTag(downCell.getGroupTag());
//                                } else {
//                                    if (leftCell.getGroupTag() > 0 && downCell.getGroupTag() > 0) {
//                                        tmpCell.setGroupTag(Math.min(downCell.getGroupTag(), leftCell.getGroupTag()));
//
//                                        // do merge here
//                                        int targetGroupTag = Math.max(downCell.getGroupTag(), leftCell.getGroupTag());
//                                        cellGroups.get(tmpCell.getGroupTag()).merge(cellGroups.get(targetGroupTag));
//                                        cellGroups.remove(targetGroupTag);
//                                    } else if (leftCell.getGroupTag() < 0 && downCell.getGroupTag() < 0) {
//                                        tmpCell.setGroupTag(-1);
//                                    } else {
//                                        tmpCell.setGroupTag(Math.max(downCell.getGroupTag(), leftCell.getGroupTag()));
//                                    }
//                                }
//
//                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);
//
//                                downCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                                leftCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                            }
//                            break;
//                        case EDGE_0Y:
//                            upCell = cellArray.get(cellIndexX + (cellIndexY + 1) * numOfCellsX);
//                            downCell = cellArray.get(cellIndexX + (cellIndexY - 1) * numOfCellsX);
//                            rightCell = cellArray.get(cellIndexX + 1 + cellIndexY * numOfCellsX);
//
//                            if (upCell.isAboveThreshold()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//                            if (downCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//                            if (rightCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() > 0) {
//                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
//                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
//                                }
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() == 3) {
//                                if (rightCell.getGroupTag() == downCell.getGroupTag()) {
//                                    tmpCell.setGroupTag(downCell.getGroupTag());
//                                } else {
//                                    tmpCell.setGroupTag(-1);
//                                }
//
//                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);
//
//                                upCell.setPropertyToBorder(CellPropertyUpdate.FORCED);
//                                downCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                                rightCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                            }
//                            break;
//                        case EDGE_1Y:
//                            upCell = cellArray.get(cellIndexX + (cellIndexY + 1) * numOfCellsX);
//                            downCell = cellArray.get(cellIndexX + (cellIndexY - 1) * numOfCellsX);
//                            leftCell = cellArray.get(cellIndexX - 1 + cellIndexY * numOfCellsX);
//
//                            if (upCell.isAboveThreshold()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//                            if (downCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//                            if (leftCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() > 0) {
//                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
//                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
//                                }
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() == 3) {
//                                if (leftCell.getGroupTag() == downCell.getGroupTag()) {
//                                    tmpCell.setGroupTag(downCell.getGroupTag());
//                                } else {
//                                    if (leftCell.getGroupTag() > 0 && downCell.getGroupTag() > 0) {
//                                        tmpCell.setGroupTag(Math.min(downCell.getGroupTag(), leftCell.getGroupTag()));
//
//                                        // do merge here
//                                        int targetGroupTag = Math.max(downCell.getGroupTag(), leftCell.getGroupTag());
//                                        cellGroups.get(tmpCell.getGroupTag()).merge(cellGroups.get(targetGroupTag));
//                                        cellGroups.remove(targetGroupTag);
//                                    } else if (leftCell.getGroupTag() < 0 && downCell.getGroupTag() < 0) {
//                                        tmpCell.setGroupTag(-1);
//                                    } else {
//                                        tmpCell.setGroupTag(Math.max(downCell.getGroupTag(), leftCell.getGroupTag()));
//                                    }
//                                }
//
//                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);
//
//                                upCell.setPropertyToBorder(CellPropertyUpdate.FORCED);
//                                downCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                                leftCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                            }
//                            break;
//                        case EDGE_X0:
//                            upCell = cellArray.get(cellIndexX + (cellIndexY + 1) * numOfCellsX);
//                            rightCell = cellArray.get(cellIndexX + 1 + cellIndexY * numOfCellsX);
//                            leftCell = cellArray.get(cellIndexX - 1 + cellIndexY * numOfCellsX);
//
//                            if (upCell.isAboveThreshold()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//                            if (rightCell.isAboveThreshold()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//                            if (leftCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() > 0) {
//                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
//                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
//                                }
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() == 3) {
//                                tmpCell.setGroupTag(-1);
//                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);
//
//                                upCell.setPropertyToBorder(CellPropertyUpdate.FORCED);
//                                rightCell.setPropertyToBorder(CellPropertyUpdate.FORCED);
//                                leftCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                            }
//                            break;
//                        case EDGE_X1:
//                            downCell = cellArray.get(cellIndexX + (cellIndexY - 1) * numOfCellsX);
//                            rightCell = cellArray.get(cellIndexX + 1 + cellIndexY * numOfCellsX);
//                            leftCell = cellArray.get(cellIndexX - 1 + cellIndexY * numOfCellsX);
//
//                            if (downCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//                            if (rightCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//                            if (leftCell.isVisible()) {
//                                tmpCell.increaseNumOfNeighbors();
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() > 0) {
//                                if (tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER) {
//                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
//                                }
//                            }
//
//                            if (tmpCell.getNumOfNeighbors() == 3) {
//                                if (rightCell.getGroupTag() == downCell.getGroupTag()) {
//                                    tmpCell.setGroupTag(downCell.getGroupTag());
//                                }
//
//                                if (leftCell.getGroupTag() == downCell.getGroupTag()) {
//                                    tmpCell.setGroupTag(downCell.getGroupTag());
//                                } else {
//                                    if (leftCell.getGroupTag() > 0 && downCell.getGroupTag() > 0) {
//                                        tmpCell.setGroupTag(Math.min(downCell.getGroupTag(), leftCell.getGroupTag()));
//
//                                        // do merge here
//                                        int targetGroupTag = Math.max(downCell.getGroupTag(), leftCell.getGroupTag());
//                                        cellGroups.get(tmpCell.getGroupTag()).merge(cellGroups.get(targetGroupTag));
//                                        cellGroups.remove(targetGroupTag);
//                                    } else if (leftCell.getGroupTag() < 0 && downCell.getGroupTag() < 0) {
//                                        tmpCell.setGroupTag(-1);
//                                    } else {
//                                        tmpCell.setGroupTag(Math.max(downCell.getGroupTag(), leftCell.getGroupTag()));
//                                    }
//                                }
//
//                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);
//
//                                rightCell.setPropertyToBorder(CellPropertyUpdate.FORCED);
//                                downCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                                leftCell.setPropertyToBorder(CellPropertyUpdate.CHECK);
//                            }
//                            break;
                        case INSIDE:
                            upCell = cellArray.get(cellIndexX + (cellIndexY + 1) * numOfCellsX);
                            downCell = cellArray.get(cellIndexX + (cellIndexY - 1) * numOfCellsX);
                            rightCell = cellArray.get(cellIndexX + 1 + cellIndexY * numOfCellsX);
                            leftCell = cellArray.get(cellIndexX - 1 + cellIndexY * numOfCellsX);

							//update actual cell
							if(tmpCell.isAboveThreshold() && tmpCell.getGroupTag()>0){
								CellGroup tmpGroup = cellGroups.get(tmpCell.getGroupTag());
								if(Math.abs(tmpCell.location.x - tmpGroup.minX) > maxGroupSizeX || Math.abs(tmpCell.location.x - tmpGroup.maxX) > maxGroupSizeX){
									deleteGroup(tmpGroup);
								}
							}
							//log.info("Cntr: "+String.valueOf(tmpCell.cellNumber)+": "+String.valueOf(tmpCell.visible)+", Up: "+String.valueOf(upCell.cellNumber)+": "+String.valueOf(upCell.visible)+", Down: "+String.valueOf(downCell.cellNumber)+": "+String.valueOf(downCell.visible)+", Left: "+String.valueOf(leftCell.cellNumber)+": "+String.valueOf(leftCell.visible)+", Right: "+String.valueOf(rightCell.cellNumber)+": "+String.valueOf(rightCell.visible));


                            if (upCell.isAboveThreshold()) {
                                if(upCell.getGroupTag() > 0 && tmpCell.getGroupTag() != upCell.getGroupTag()){
                                    if(tmpCell.getGroupTag() > 0){
										cellGroups.get(upCell.getGroupTag()).merge(cellGroups.get(tmpCell.getGroupTag()));
									} else {
										cellGroups.get(upCell.getGroupTag()).add(tmpCell);
									}
								}
                            }
                            if (downCell.isAboveThreshold()) {
                                if(downCell.getGroupTag() > 0 && tmpCell.getGroupTag() != downCell.getGroupTag()){
                                    if(tmpCell.getGroupTag() > 0){
										cellGroups.get(downCell.getGroupTag()).merge(cellGroups.get(tmpCell.getGroupTag()));
									} else {
										cellGroups.get(downCell.getGroupTag()).add(tmpCell);
									}
								}
                            }

                            if (leftCell.isAboveThreshold()) {
                                if(leftCell.getGroupTag() > 0 && tmpCell.getGroupTag() != leftCell.getGroupTag()){
                                    if(tmpCell.getGroupTag() > 0){
										cellGroups.get(leftCell.getGroupTag()).merge(cellGroups.get(tmpCell.getGroupTag()));
                                    } else {
										cellGroups.get(leftCell.getGroupTag()).add(tmpCell);
									}
								}
                            }
                            if (rightCell.isAboveThreshold()) {
                                if(rightCell.getGroupTag() > 0 && tmpCell.getGroupTag() != rightCell.getGroupTag()){
                                    if(tmpCell.getGroupTag() > 0){
										cellGroups.get(rightCell.getGroupTag()).merge(cellGroups.get(tmpCell.getGroupTag()));
									} else {
										cellGroups.get(rightCell.getGroupTag()).add(tmpCell);
									}
								}
                            }

                            if(tmpCell.isVisible()){
                                if(tmpCell.getGroupTag()<0){
                                    CellGroup newGroup = new CellGroup(tmpCell);
									cellGroups.put(newGroup.getTag(), newGroup);
                                }
                            }
                            break;
                        default:
                            break;
                    } // End of switch
                } catch (java.util.ConcurrentModificationException e) {
                    // this is in case cell list is modified by real time filter during updating cells
                    initFilter();
					log.warning(e.getMessage());
			}
		} // End of if
    }

    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        GL gl = drawable.getGL(); // when we getString this we are already set up with scale 1=1 pixel, at LL corner
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

                    if (tmpCell.isVisible()){
                        tmpCell.drawCell(drawable);
					}
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            // this is in case neuron list is modified by real time filter during rendering of neurons
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
        cellGroups.clear();
        numOfGroup = 0;


        // initialize all cells
        if ((numOfCellsX > 0 && numOfCellsY > 0)
                && (prev_numOfCellsX != numOfCellsX || prev_numOfCellsY != numOfCellsY)) {
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
        cellGroups.clear();
        numOfGroup = 0;
    }

    private void deleteGroup(CellGroup thisGroup){
	cellGroups.remove(thisGroup.tag);

	Iterator cellItr = thisGroup.memberCells.iterator();
	while(cellItr.hasNext()){
	    Cell tmpCell = (Cell)cellItr.next();
	    tmpCell.setGroupTag(-1);
	}
        //thisGroup = null;
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

	/** returns max size of a cell group along the x axis
     *
     * @return maxGroupSizeX
     */
    public int getMaxGroupSizeX() {
        return maxGroupSizeX;
    }

    /** set the cell size
     *
     * @param cellSizePixels
     */
    synchronized public void setMaxGroupSizeX(int maxGroupSizeX) {
        this.maxGroupSizeX = maxGroupSizeX;
        getPrefs().putInt("BlurringFilter2D.maxGroupSizeX", maxGroupSizeX);
        initFilter();
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
    public Collection getCellGroups() {
	HashMap<Integer, CellGroup> cloneGroups = (HashMap)cellGroups.clone();
	return cloneGroups.values();
    }

    /** returns the last timestamp ever recorded at this filter
     *
     * @return the last timestamp ever recorded at this filter
     */
    public int getLastTime() {
        return lastTime;
    }
}
