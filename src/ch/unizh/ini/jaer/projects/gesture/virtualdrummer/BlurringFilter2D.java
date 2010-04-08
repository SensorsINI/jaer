/*
 * Last updated on March 23, 2010, 3:49 AM
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
    private int thresholdEventsForVisibleCell = getPrefs().getInt("BlurringFilter2D.thresholdEventsForVisibleCell", 10);
    private int thresholdMassForVisibleCell = getPrefs().getInt("BlurringFilter2D.thresholdMassForVisibleCell", 50);
    private boolean showCells = getPrefs().getBoolean("BlurringFilter2D.showCells", true);
    private boolean filledCells = getPrefs().getBoolean("BlurringFilter2D.filledCells", false);
    private boolean showBorderCellsOnly = getPrefs().getBoolean("BlurringFilter2D.showBorderCellsOnly", false);
    private boolean showInsideCellsOnly = getPrefs().getBoolean("BlurringFilter2D.showInsideCellsOnly", false);
//    private boolean showCellMass = getPrefs().getBoolean("BlurringFilter2D.showCellMass", false);
    private int cellSizePixels = getPrefs().getInt("BlurringFilter2D.cellSizePixels", 8);
//    private boolean filterEventsEnabled=getPrefs().getBoolean("BlurringFilter2D.filterEventsEnabled",false);

    // Constants
    static int UPDATE_UP = 0x01;
    static int UPDATE_DOWN = 0x02;
    static int UPDATE_RIGHT = 0x04;
    static int UPDATE_LEFT = 0x08;


    // variables
    private int numOfCellsX = 0, numOfCellsY = 0;
    private ArrayList <Cell> cellArray = new ArrayList<Cell>();
    private HashMap <Integer, CellGroup> cellGroup = new HashMap<Integer, CellGroup>();
    private HashSet <Integer> validCellIndexSet = new HashSet();
    protected AEChip mychip;
    protected int lastTime;
    protected int numOfGroup = 0;
    protected Random random = new Random();

    public BlurringFilter2D(AEChip chip) {
        super(chip);
        this.mychip = chip;
        initFilter();
        chip.addObserver(this);

        final String threshold = "Threshold", sizing = "Sizing", movement = "Movement", lifetime = "Lifetime", disp = "Display", global = "Global", update = "Update", logging = "Logging";

        setPropertyTooltip(lifetime, "cellLifeTimeUs", "Event lifetime");
        setPropertyTooltip(threshold, "thresholdEventsForVisibleCell", "Cell needs this many events to be visible");
        setPropertyTooltip(threshold, "thresholdMassForVisibleCell", "Cell needs this much mass to be visible");
        setPropertyTooltip(disp, "showCells", "Show detected cells");
        setPropertyTooltip(disp, "filledCells", "Filled symbols");
        setPropertyTooltip(disp, "showBorderCellsOnly", "Sweep out all cells except border cells");
        setPropertyTooltip(disp, "showInsideCellsOnly", "Sweep out all cells except inside cells");
//        setPropertyTooltip(disp, "showCellMass", "Show mass of the detected cells");
        setPropertyTooltip(sizing, "cellSizePixels", "Cell size in number of pixels");
//        setPropertyTooltip(global,"filterEventsEnabled","<html>If disabled, input packet is unaltered. <p>If enabled, output packet contains filtered events only.");
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
    static enum CellPropertyUpdate {FORCED, CHECK}
    public class Cell {

        // Cell index
        public Point2D.Float cellIndex = new Point2D.Float();

        /** location of cell in pixels */
        public Point2D.Float location = new Point2D.Float(); // location in chip pixels

        /** One of CellType */
        CellType cellType;
        CellProperty cellProperty;
        protected int groupTag = -1;
        protected boolean visible = false;

        public void reset(){
            mass = 1;
            numEvents = 0;
            numOfNeighbors = 0;
            setCellProperty(CellProperty.NOT_VISIBLE);
            groupTag = -1;
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
        protected int lastEventTimestamp, firstEventTimestamp;
        private int cellNumber;
        /** Flag which is set true once a cell has obtained sufficient support. */
        private float[] rgb = new float[4];

        /** Construct a cell with index. */
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
            setCellProperty(CellProperty.NOT_VISIBLE);
            groupTag = -1;
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
//            final int font = GLUT.BITMAP_HELVETICA_10;
//            if (showCellMass && this.cellProperty != CellProperty.NOT_VISIBLE) {
//                mychip.getCanvas().getGlut().glutBitmapString(font, String.format("m=%.1f", getMassNow(lastUpdateTime)));
//            }
        }

        private CellProperty getCellProperty(){
            return cellProperty;
        }

        private void setCellProperty(CellProperty cellProperty){
            this.cellProperty = cellProperty;
        }

        private void setPropertyToBorder(int groupTag, CellPropertyUpdate cellPropertypdate){
            if(cellPropertypdate == CellPropertyUpdate.CHECK){
                if(this.cellProperty != CellProperty.VISIBLE_INSIDE)
                    setCellProperty(CellProperty.VISIBLE_BORDER);
            }
            else
                setCellProperty(CellProperty.VISIBLE_BORDER);
            setGroupTag(groupTag);
        }

        /** updates cell by one event.
        @param event the event
         */
        public void addEvent(BasicEvent event) {
            incrementMass(event.getTimestamp());
            lastEventTimestamp = event.getTimestamp();
            if(numEvents == 0)
                firstEventTimestamp = lastEventTimestamp;

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
            float m = mass * (float) Math.exp(((float) (lastEventTimestamp - t)) / cellLifeTimeUs);
            return m;
        }

        protected float getMass() {
            return mass;
        }

        /**Increments mass of cell by one after decaying it away since the {@link #lastEventTimestamp} according
        to exponential decay with time constant {@link #cellLifetimeWithoutSupportUs}.
        @param event used for event timestamp.
         */
        protected void incrementMass(int timeStamp) {
            mass = 1 + mass * (float) Math.exp(((float) lastEventTimestamp - timeStamp) / cellLifeTimeUs);
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
                setCellProperty(CellProperty.VISIBLE_ISOLATED);
            else
                setCellProperty(CellProperty.NOT_VISIBLE);

            resetGroupTag();

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

        public Float getCellIndex() {
            return cellIndex;
        }

        private CellType getCellType() {
            return cellType;
        }

        private void setCellType(CellType cellType) {
            this.cellType = cellType;
        }

        public int getNumOfNeighbors() {
            return numOfNeighbors;
        }

        public void setNumOfNeighbors(int numOfNeighbors) {
            this.numOfNeighbors = numOfNeighbors;
        }

        public void increaseNumOfNeighbors(){
            numOfNeighbors++;
        }

        public int getCellNumber() {
            return cellNumber;
        }
        
        public int getGroupTag(){
            return groupTag;
        }

        public void setGroupTag(int groupTag){
            // If groupTag is a negative value, give a new group tag
            if(groupTag < 0){
                if(this.groupTag < 0){
                    this.groupTag = numOfGroup;
                    numOfGroup++;
                }
            }
            else{
                this.groupTag = groupTag;
            }
        }

        public void resetGroupTag(){
            this.groupTag = -1;
        }

        public int getFirstEventTimestamp() {
            return firstEventTimestamp;
        }

        public void setFirstEventTimestamp(int firstEventTimestamp) {
            this.firstEventTimestamp = firstEventTimestamp;
        }

        public int getLastEventTimestamp() {
            return lastEventTimestamp;
        }

        public void setLastEventTimestamp(int lastEventTimestamp) {
            this.lastEventTimestamp = lastEventTimestamp;
        }
    } // End of class Cell


    class CellGroup {
        public Point2D.Float location = new Point2D.Float(); // location in chip pixels
        /** Number of events collected by this group.*/
        protected int numEvents;
        /** The "mass" of the group is the weighted number of events it has collected.
         * Sum of the mass of all member cells
         */
        protected float mass;
        /** This is the last time in timestamp ticks that the group was updated, either by an event
         * The largest one among the lastUpdateTime of all member cells
         */
        protected int lastEventTimestamp, firstEventTimestamp;
        /** Parameters to represent the area of the group
         */
        protected float minX, maxX, minY, maxY;
        /** Group number
         * groupTag of the member cells
         */
        protected int tag;
        /** Indicate if this cell group is hitting edge
         *
         */
        protected boolean hitEdge = false;
        protected boolean matched = false;
        /** Member cells consisting of this group
         *
         */
        HashSet <Cell> memberCells = null;

        public CellGroup() {
            memberCells = new HashSet();
            reset();
        }

        public CellGroup(Cell firstCell) {
            this();
            add(firstCell);
        }

        public void reset(){
            location.setLocation(-1f, -1f);
            numEvents = 0;
            mass = 0;
            tag = -1;
            memberCells.clear();
            maxX = maxY = 0;
            minX = chip.getSizeX();
            minY = chip.getSizeX();
        }

        public void add(Cell inCell){
            if(tag < 0){
                tag = inCell.getGroupTag();
            }

            float prev_mass = mass;
            float leakyFactor;

            if(numEvents == 0)
                firstEventTimestamp = inCell.getFirstEventTimestamp();
            else
                firstEventTimestamp = Math.min(firstEventTimestamp, inCell.getFirstEventTimestamp());

            numEvents +=inCell.getNumEvents();

            if(lastEventTimestamp < inCell.getLastEventTimestamp()){
                leakyFactor = (float) Math.exp(((float) lastEventTimestamp - inCell.getLastEventTimestamp()) / cellLifeTimeUs);
                mass = inCell.getMass() + mass * leakyFactor;
                location.x = (inCell.getLocation().x * inCell.getMass() + location.x * prev_mass * leakyFactor) / (mass);
                location.y = (inCell.getLocation().y * inCell.getMass() + location.y * prev_mass * leakyFactor) / (mass);

                lastEventTimestamp = inCell.getLastEventTimestamp();
            } else {
                leakyFactor = (float) Math.exp(((float) inCell.getLastEventTimestamp() - lastEventTimestamp) / cellLifeTimeUs);
                mass += inCell.getMass() * leakyFactor;
                location.x = (inCell.getLocation().x * inCell.getMass() * leakyFactor + location.x * prev_mass) / (mass);
                location.y = (inCell.getLocation().y * inCell.getMass() * leakyFactor + location.y * prev_mass) / (mass);
            }

            if(inCell.getLocation().x < minX) minX = inCell.getLocation().x;
            if(inCell.getLocation().x > maxX) maxX = inCell.getLocation().x;
            if(inCell.getLocation().y < minY) minY = inCell.getLocation().y;
            if(inCell.getLocation().y > maxY) maxY = inCell.getLocation().y;

            // check if this group is hitting edges
            if(!hitEdge && ((int) inCell.getCellIndex().x == 0 || (int) inCell.getCellIndex().y == 0 || (int) inCell.getCellIndex().x == numOfCellsX-1 || (int) inCell.getCellIndex().y == numOfCellsY-1))
                hitEdge = true;

            memberCells.add(inCell);

        }

        public void merge(CellGroup targetGroup){
            if(targetGroup == null)
                return;

            float prev_mass = mass;
            float leakyFactor;

            numEvents += targetGroup.numEvents;

            firstEventTimestamp = Math.min(firstEventTimestamp, targetGroup.firstEventTimestamp);

            if(lastEventTimestamp < targetGroup.lastEventTimestamp){
                leakyFactor = (float) Math.exp(((float) lastEventTimestamp - targetGroup.lastEventTimestamp) / cellLifeTimeUs);
                mass = targetGroup.mass + mass * leakyFactor;
                location.x = (targetGroup.location.x * targetGroup.mass + location.x * prev_mass * leakyFactor) / (mass);
                location.y = (targetGroup.location.y * targetGroup.mass + location.y * prev_mass * leakyFactor) / (mass);

                lastEventTimestamp = targetGroup.lastEventTimestamp;
            } else {
                leakyFactor = (float) Math.exp(((float) targetGroup.lastEventTimestamp - lastEventTimestamp) / cellLifeTimeUs);
                mass += (targetGroup.mass * leakyFactor);
                location.x = (targetGroup.location.x * targetGroup.mass * leakyFactor + location.x * prev_mass) / (mass);
                location.y = (targetGroup.location.y * targetGroup.mass * leakyFactor + location.y * prev_mass) / (mass);
            }

            if(targetGroup.minX < minX) minX = targetGroup.minX;
            if(targetGroup.maxX > maxX) maxX = targetGroup.maxX;
            if(targetGroup.minY < minY) minY = targetGroup.minY;
            if(targetGroup.maxY > maxY) maxY = targetGroup.maxY;

            Iterator itr = targetGroup.iterator();
            while(itr.hasNext()){
                Cell tmpCell = (Cell) itr.next();
                tmpCell.setGroupTag(tag);
                memberCells.add(tmpCell);
            }

            targetGroup.reset();
        }

        public float locationDistancePixels(CellGroup targetGroup){
            return (float) Math.sqrt(Math.pow(location.x-targetGroup.location.x, 2.0) + Math.pow(location.y-targetGroup.location.y, 2.0));
        }

        public float locationDistanceCells(CellGroup targetGroup){
            return locationDistancePixels(targetGroup)/((float) cellSizePixels/2);
        }


        public Iterator iterator(){
            return memberCells.iterator();
        }

        public int getNumMemberCells(){
            return memberCells.size();
        }

        public int getNumEvents(){
            return numEvents;
        }

        public float getMass(){
            return mass;
        }

        public int getFirstEventTimestamp() {
            return firstEventTimestamp;
        }

        public int getLastEventTimestamp() {
            return lastEventTimestamp;
        }

        public Float getLocation() {
            return location;
        }

        public float getInnerRadiusPixels(){
            return Math.min(Math.min(Math.abs(location.x-minX), Math.abs(location.x-maxX)), Math.min(Math.abs(location.y-minY), Math.abs(location.y-maxY)));
        }

        public float getOutterRadiusPixels(){
            return Math.max(Math.max(Math.abs(location.x-minX), Math.abs(location.x-maxX)), Math.max(Math.abs(location.y-minY), Math.abs(location.y-maxY)));
        }

        public float getAreaRadiusPixels(){
            return (float) Math.sqrt((float) getNumMemberCells()) * cellSizePixels / 4;
        }

        public boolean isWithinInnerRadius(Float targetLoc){
            boolean ret = false;
            float innerRaidus = getInnerRadiusPixels();

            if(Math.abs(location.x - targetLoc.x) <= innerRaidus && Math.abs(location.y - targetLoc.y) <= innerRaidus)
                ret =  true;

            return ret;
        }

        public boolean isWithinOuterRadius(Float targetLoc){
            boolean ret = false;
            float outterRaidus = getOutterRadiusPixels();

            if(Math.abs(location.x - targetLoc.x) <= outterRaidus && Math.abs(location.y - targetLoc.y) <= outterRaidus)
                ret =  true;

            return ret;
        }

        public boolean isWithinAreaRadius(Float targetLoc){
            boolean ret = false;
            float areaRaidus = getAreaRadiusPixels();

            if(Math.abs(location.x - targetLoc.x) <= areaRaidus && Math.abs(location.y - targetLoc.y) <= areaRaidus)
                ret =  true;

            return ret;
        }

        public boolean contains(BasicEvent ev){
            boolean ret = false;

            int subIndexX = (int) 2*ev.getX()/cellSizePixels;
            int subIndexY = (int) 2*ev.getY()/cellSizePixels;

            if(subIndexX >= numOfCellsX && subIndexY >= numOfCellsY)
                ret = false;

            if(!ret && subIndexX != numOfCellsX && subIndexY != numOfCellsY)
                ret = validCellIndexSet.contains(subIndexX + subIndexY * numOfCellsX);
            if(!ret && subIndexX != numOfCellsX && subIndexY != 0)
                ret = validCellIndexSet.contains(subIndexX + (subIndexY - 1) * numOfCellsX);
            if(!ret && subIndexX != 0 && subIndexY != numOfCellsY)
                ret = validCellIndexSet.contains(subIndexX - 1 + subIndexY * numOfCellsX);
            if(!ret && subIndexY != 0 && subIndexX != 0)
                ret = validCellIndexSet.contains(subIndexX - 1 + (subIndexY - 1) * numOfCellsX);

            return ret;
        }

        public boolean isHitEdge() {
            return hitEdge;
        }

        public boolean isMatched() {
            return matched;
        }

        public void setMatched(boolean matched) {
            this.matched = matched;
        }


    } // End of class cellGroup


    synchronized private EventPacket<? extends BasicEvent> blurring(EventPacket<BasicEvent> in) {
        boolean updatedCells = false;
//        ArrayList<BasicEvent> eventPacketCopy = new ArrayList(in.getSize());
//        OutputEventIterator outItr = null;

        if (in.getSize() == 0) {
            return out;
        }

//        if (filterEventsEnabled) {
//            outItr = out.outputIterator(); // reset output packet
//        }

        try{
            // add events to the corresponding cell
            for (BasicEvent ev : in) {
//                if (filterEventsEnabled)
//                {
//                    BasicEvent e = new BasicEvent();
//                    e.copyFrom(ev);
//                    eventPacketCopy.add(e);
//                }

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

            }
        } catch (IndexOutOfBoundsException e) {
            initFilter();
            // this is in case cell list is modified by real time filter during updating cells
            log.warning(e.getMessage());
        }

        updateCells(in.getLastTimestamp()); // at laest once per packet update list

/*        if (filterEventsEnabled) {
            Iterator itr = eventPacketCopy.iterator();
            while(itr.hasNext()){
                BasicEvent tmpEv = (BasicEvent) itr.next();
                if(isFiltered(tmpEv)){
                    BasicEvent e = outItr.nextOutput();
                    e.copyFrom(tmpEv);
                }
            }
        }
        
        if (filterEventsEnabled) {
            return out;
        } else {
*/            return in;
//        }
    }

/*
    private boolean isFiltered(BasicEvent ev){
        Iterator itr = cellGroup.values().iterator();
        while(itr.hasNext()){
            CellGroup cg = (CellGroup) itr.next();
            if(cg.contains(ev))
                return true;
        }

        return false;
    }
*/

    private void updateCells(int t) {
        validCellIndexSet.clear();;

        if(!cellArray.isEmpty()){
            Iterator itr = cellArray.iterator();
            Cell tmpCell = null;
            int timeSinceSupport;

            // reset number of group before starting update
            numOfGroup = 0;
            cellGroup.clear();
            while(itr.hasNext()) {
                try{
                    tmpCell = (Cell) itr.next();

                    timeSinceSupport = t - tmpCell.lastEventTimestamp;

                    if(timeSinceSupport > cellLifeTimeUs)
                        tmpCell.reset();

                    int cellIndexX = (int) tmpCell.getCellIndex().x;
                    int cellIndexY = (int) tmpCell.getCellIndex().y;
                    int up = cellIndexX + (cellIndexY+1)*numOfCellsX;
                    int down = cellIndexX + (cellIndexY-1)*numOfCellsX;
                    int right = cellIndexX+1 + cellIndexY*numOfCellsX;
                    int left = cellIndexX-1 + cellIndexY*numOfCellsX;

                    tmpCell.setNumOfNeighbors(0);
                    switch(tmpCell.getCellType()){
                        case CORNER_00:
                            // check threshold of the first cell
                            tmpCell.isAboveThreshold();

                            if(cellArray.get(up).isAboveThreshold())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(right).isAboveThreshold())
                                tmpCell.increaseNumOfNeighbors();

                            if(tmpCell.getNumOfNeighbors() > 0){
                                if(tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER)
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                            }

                            if(tmpCell.getNumOfNeighbors() == 2)
                            {
                                tmpCell.setGroupTag(-1);
                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(up).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(right).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                updateGroup(tmpCell, UPDATE_UP|UPDATE_RIGHT);
                            }
                            break;
                        case CORNER_01:
                            if(cellArray.get(down).isVisible())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(right).isVisible())
                                tmpCell.increaseNumOfNeighbors();

                            if(tmpCell.getNumOfNeighbors() > 0){
                                if(tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER)
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                            }

                            if(tmpCell.getNumOfNeighbors() == 2)
                            {
                                if(cellArray.get(right).getGroupTag() == cellArray.get(down).getGroupTag())
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                else
                                    tmpCell.setGroupTag(-1);

                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(right).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(down).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_DOWN|UPDATE_RIGHT);
                            }
                            break;
                        case CORNER_10:
                            if(cellArray.get(up).isAboveThreshold())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(left).isVisible())
                                tmpCell.increaseNumOfNeighbors();

                            if(tmpCell.getNumOfNeighbors() > 0){
                                if(tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER)
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                            }

                            if(tmpCell.getNumOfNeighbors() == 2)
                            {
                                tmpCell.setGroupTag(-1);
                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(up).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(left).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_UP|UPDATE_LEFT);
                            }
                            break;
                        case CORNER_11:
                            if(cellArray.get(down).isVisible())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(left).isVisible())
                                tmpCell.increaseNumOfNeighbors();

                            if(tmpCell.getNumOfNeighbors() > 0){
                                if(tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER)
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                            }

                            if(tmpCell.getNumOfNeighbors() == 2)
                            {
                                if(cellArray.get(left).getGroupTag() == cellArray.get(down).getGroupTag()){
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                } else {
                                    if(cellArray.get(left).getGroupTag() > 0 && cellArray.get(down).getGroupTag() > 0){
                                        tmpCell.setGroupTag(Math.min(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));

                                        // do merge here
                                        int targetGroupTag = Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag());
                                        cellGroup.get(tmpCell.getGroupTag()).merge(cellGroup.get(targetGroupTag));
                                        cellGroup.remove(targetGroupTag);
                                    } else if(cellArray.get(left).getGroupTag() < 0 && cellArray.get(down).getGroupTag() < 0){
                                        tmpCell.setGroupTag(-1);
                                    } else {
                                        tmpCell.setGroupTag(Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));
                                    }
                                }

                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(down).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(left).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_DOWN|UPDATE_LEFT);
                            }
                            break;
                        case EDGE_0Y:
                            if(cellArray.get(up).isAboveThreshold())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(down).isVisible())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(right).isVisible())
                                tmpCell.increaseNumOfNeighbors();

                            if(tmpCell.getNumOfNeighbors() > 0){
                                if(tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER)
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                            }

                            if(tmpCell.getNumOfNeighbors() == 3)
                            {
                                if( cellArray.get(right).getGroupTag() == cellArray.get(down).getGroupTag())
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                else
                                    tmpCell.setGroupTag(-1);

                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(up).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(down).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(right).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_UP|UPDATE_DOWN|UPDATE_RIGHT);
                            }
                            break;
                        case EDGE_1Y:
                            if(cellArray.get(up).isAboveThreshold())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(down).isVisible())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(left).isVisible())
                                tmpCell.increaseNumOfNeighbors();

                            if(tmpCell.getNumOfNeighbors() > 0){
                                if(tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER)
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                            }

                            if(tmpCell.getNumOfNeighbors() == 3)
                            {
                                if(cellArray.get(left).getGroupTag() == cellArray.get(down).getGroupTag()){
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                } else {
                                    if(cellArray.get(left).getGroupTag() > 0 && cellArray.get(down).getGroupTag() > 0){
                                        tmpCell.setGroupTag(Math.min(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));

                                        // do merge here
                                        int targetGroupTag = Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag());
                                        cellGroup.get(tmpCell.getGroupTag()).merge(cellGroup.get(targetGroupTag));
                                        cellGroup.remove(targetGroupTag);
                                    } else if(cellArray.get(left).getGroupTag() < 0 && cellArray.get(down).getGroupTag() < 0){
                                        tmpCell.setGroupTag(-1);
                                    } else {
                                        tmpCell.setGroupTag(Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));
                                    }
                                }

                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(up).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(down).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(left).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_UP|UPDATE_DOWN|UPDATE_LEFT);
                            }
                            break;
                        case EDGE_X0:
                            if(cellArray.get(up).isAboveThreshold())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(right).isAboveThreshold())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(left).isVisible())
                                tmpCell.increaseNumOfNeighbors();

                            if(tmpCell.getNumOfNeighbors() > 0){
                                if(tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER)
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                            }

                            if(tmpCell.getNumOfNeighbors() == 3)
                            {
                                tmpCell.setGroupTag(-1);
                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(up).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(right).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(left).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_UP|UPDATE_RIGHT|UPDATE_LEFT);
                            }
                            break;
                        case EDGE_X1:
                            if(cellArray.get(down).isVisible())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(right).isVisible())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(left).isVisible())
                                tmpCell.increaseNumOfNeighbors();

                            if(tmpCell.getNumOfNeighbors() > 0){
                                if(tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER)
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                            }

                            if(tmpCell.getNumOfNeighbors() == 3)
                            {
                                if( cellArray.get(right).getGroupTag() == cellArray.get(down).getGroupTag())
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());

                                if(cellArray.get(left).getGroupTag() == cellArray.get(down).getGroupTag()){
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                } else {
                                    if(cellArray.get(left).getGroupTag() > 0 && cellArray.get(down).getGroupTag() > 0){
                                        tmpCell.setGroupTag(Math.min(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));

                                        // do merge here
                                        int targetGroupTag = Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag());
                                        cellGroup.get(tmpCell.getGroupTag()).merge(cellGroup.get(targetGroupTag));
                                        cellGroup.remove(targetGroupTag);
                                    } else if(cellArray.get(left).getGroupTag() < 0 && cellArray.get(down).getGroupTag() < 0){
                                        tmpCell.setGroupTag(-1);
                                    } else {
                                        tmpCell.setGroupTag(Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));
                                    }
                                }

                                tmpCell.setCellProperty(CellProperty.VISIBLE_INSIDE);

                                cellArray.get(right).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.FORCED);
                                cellArray.get(down).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                cellArray.get(left).setPropertyToBorder(tmpCell.getGroupTag(), CellPropertyUpdate.CHECK);
                                updateGroup(tmpCell, UPDATE_DOWN|UPDATE_RIGHT|UPDATE_LEFT);
                            }
                            break;
                        case INSIDE:
                            if(cellArray.get(up).isAboveThreshold())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(down).isVisible())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(right).isVisible())
                                tmpCell.increaseNumOfNeighbors();
                            if(cellArray.get(left).isVisible())
                                tmpCell.increaseNumOfNeighbors();

                            if(tmpCell.getNumOfNeighbors() > 0 && tmpCell.isVisible()){
                                if(tmpCell.getCellProperty() != CellProperty.VISIBLE_BORDER)
                                    tmpCell.setCellProperty(CellProperty.VISIBLE_HAS_NEIGHBOR);
                            }

                            if(tmpCell.getNumOfNeighbors() == 4 && tmpCell.isVisible())
                            {
                                if( cellArray.get(right).getGroupTag() == cellArray.get(down).getGroupTag())
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());

                                if(cellArray.get(left).getGroupTag() == cellArray.get(down).getGroupTag()){
                                    tmpCell.setGroupTag(cellArray.get(down).getGroupTag());
                                } else {
                                    if(cellArray.get(left).getGroupTag() > 0 && cellArray.get(down).getGroupTag() > 0){
                                        tmpCell.setGroupTag(Math.min(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag()));

                                        // do merge here
                                        int targetGroupTag = Math.max(cellArray.get(down).getGroupTag(), cellArray.get(left).getGroupTag());
                                        cellGroup.get(tmpCell.getGroupTag()).merge(cellGroup.get(targetGroupTag));
                                        cellGroup.remove(targetGroupTag);
                                    } else if(cellArray.get(left).getGroupTag() < 0 && cellArray.get(down).getGroupTag() < 0){
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

                                updateGroup(tmpCell, UPDATE_UP|UPDATE_DOWN|UPDATE_RIGHT|UPDATE_LEFT);
                            }
                            break;
                        default:
                            break;
                    } // End of switch
                    if(tmpCell.getCellProperty() == CellProperty.VISIBLE_INSIDE || tmpCell.getCellProperty() == CellProperty.VISIBLE_BORDER){
                        validCellIndexSet.add(tmpCell.cellNumber);
                    }
                } catch (java.util.ConcurrentModificationException e) {
                    // this is in case cell list is modified by real time filter during updating cells
                    initFilter();
                    log.warning(e.getMessage());
                }
            } // End of while
//            System.out.println("Number of groups = " + cellGroup.size());
        } // End of if
    }

    private final void updateGroup(Cell inCell, int updateOption){
        CellGroup tmpGroup = null;
        if(cellGroup.containsKey(inCell.getGroupTag())){
            tmpGroup = cellGroup.get(inCell.getGroupTag());
            tmpGroup.add(inCell);
        } else {
            tmpGroup = new CellGroup(inCell);
            cellGroup.put(tmpGroup.tag, tmpGroup);
        }

        int cellIndexX = (int) inCell.getCellIndex().x;
        int cellIndexY = (int) inCell.getCellIndex().y;
        int up = cellIndexX + (cellIndexY+1)*numOfCellsX;
        int down = cellIndexX + (cellIndexY-1)*numOfCellsX;
        int right = cellIndexX+1 + cellIndexY*numOfCellsX;
        int left = cellIndexX-1 + cellIndexY*numOfCellsX;

        if((updateOption&UPDATE_UP) > 0)
            tmpGroup.add(cellArray.get(up));
        if((updateOption&UPDATE_DOWN) > 0)
            tmpGroup.add(cellArray.get(down));
        if((updateOption&UPDATE_RIGHT) > 0)
            tmpGroup.add(cellArray.get(right));
        if((updateOption&UPDATE_LEFT) > 0)
            tmpGroup.add(cellArray.get(left));
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
        gl.glPushMatrix();
        try {
            if (showCells) {
                Cell tmpCell;
                for(int i=0; i<cellArray.size(); i++)
                {
                    tmpCell=cellArray.get(i);

                    if(showBorderCellsOnly && showInsideCellsOnly){
                        if(tmpCell.getCellProperty() == CellProperty.VISIBLE_BORDER || tmpCell.getCellProperty() == CellProperty.VISIBLE_INSIDE)
                            tmpCell.draw(drawable);
                    } else if(showBorderCellsOnly && !showInsideCellsOnly){
                        if(tmpCell.getCellProperty() == CellProperty.VISIBLE_BORDER)
                            tmpCell.draw(drawable);
                    } else if(!showBorderCellsOnly && showInsideCellsOnly){
                        if(tmpCell.getCellProperty() == CellProperty.VISIBLE_INSIDE)
                            tmpCell.draw(drawable);
                    }
                    else {
                        if(tmpCell.getCellProperty() != CellProperty.NOT_VISIBLE)
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
        } else {
            out = blurring((EventPacket<BasicEvent>) in);
        }
//        System.out.println("Num of event w/o filter: " + in.getSize()  + ", w/ filter: "+out.getSize());

        return out;
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

        lastTime = 0;
        validCellIndexSet.clear();
        numOfGroup = 0;


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
                            newCell.setCellType(CellType.CORNER_00);
                        else if(j == numOfCellsY-1)
                            newCell.setCellType(CellType.CORNER_01);
                        else
                            newCell.setCellType(CellType.EDGE_0Y);
                        }
                    else if(i == numOfCellsX-1){
                        if(j == 0)
                            newCell.setCellType(CellType.CORNER_10);
                        else if(j == numOfCellsY-1)
                            newCell.setCellType(CellType.CORNER_11);
                        else
                            newCell.setCellType(CellType.EDGE_1Y);
                        }
                    else{
                        if(j == 0)
                            newCell.setCellType(CellType.EDGE_X0);
                        else if(j == numOfCellsY-1)
                            newCell.setCellType(CellType.EDGE_X1);
                        else
                            newCell.setCellType(CellType.INSIDE);
                        }

                    cellArray.add(newCell.getCellNumber(), newCell);
                }
            }
        }
}

    @Override
    public void resetFilter() {
        for(Cell c: cellArray)
            c.reset();

        for(CellGroup cg: cellGroup.values())
            cg.reset();

        lastTime = 0;
        validCellIndexSet.clear();
        numOfGroup = 0;

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

//    public boolean isShowCellMass() {
//        return showCellMass;
//    }

//    public void setShowCellMass(boolean showCellMass) {
//        this.showCellMass = showCellMass;
//    }

    public boolean isshowBorderCellsOnly() {
        return showBorderCellsOnly;
    }

    public void setshowBorderCellsOnly(boolean showBorderCellsOnly) {
        this.showBorderCellsOnly = showBorderCellsOnly;
        getPrefs().putBoolean("BlurringFilter2D.showBorderCellsOnly", showBorderCellsOnly);
    }

    public boolean isshowInsideCellsOnly() {
        return showInsideCellsOnly;
    }

    public void setshowInsideCellsOnly(boolean showInsideCellsOnly) {
        this.showInsideCellsOnly = showInsideCellsOnly;
        getPrefs().putBoolean("BlurringFilter2D.showInsideCellsOnly", showInsideCellsOnly);
    }

    public boolean isFilledCells() {
        return filledCells;
    }

    public void setFilledCells(boolean filledCells) {
        this.filledCells = filledCells;
        getPrefs().putBoolean("BlurringFilter2D.filledCells", filledCells);
    }

    /**
     * @return the filterEventsEnabled
     */
//    public boolean isFilterEventsEnabled() {
//        return filterEventsEnabled;
//    }

    /**
     * @param filterEventsEnabled the filterEventsEnabled to set
     */
//    synchronized public void setFilterEventsEnabled(boolean filterEventsEnabled) {
//        this.filterEventsEnabled = filterEventsEnabled;
//        getPrefs().putBoolean("BlurringFilter2D.filterEventsEnabled",filterEventsEnabled);
//        initFilter();
//    }

    public int getNumOfGroup() {
        return numOfGroup;
    }

    public Collection getCellGroup() {
        return cellGroup.values();
    }

    public int getLastTime() {
        return lastTime;
    }
}
