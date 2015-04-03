/*
 * NearestEventMotionComputer.java
 *
 * Created on November 2, 2005, 8:24 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.eventprocessing.label;

import com.jogamp.opengl.GLAutoDrawable;
import java.util.Observable;
import java.util.Observer;


import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.orientation.ApsDvsOrientationEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.orientation.DvsMotionOrientationEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Computes motion based nearest event (in past time) in nearest neighboring pixels. Unlike DirectionSelectiveFilter, NearestEventMotionComputer uses
 the non-oriented events and is only useful for small particles where nearest neighbor makes sense. Even then for particles that are larger than one pixel it will
 make lots of errors.
 *
 * @author tobi
 */
@Description("Computes motion based on nearest events - for particles")
public class NearestEventMotionComputer extends EventFilter2D implements Observer, FrameAnnotater {
       public boolean isGeneratingFilter(){ return true;}

    /** event must occur within this time in us to generate a motion event */
    protected int maxDtThreshold=getPrefs().getInt("NearestEventMotionComputer.maxDtThreshold",Integer.MAX_VALUE);
    protected int minDtThreshold=getPrefs().getInt("NearestEventMotionComputer.minDtThreshold",0); // let everything through

    int[][][] lastTimesMap;

    /** the number of cell output types */
    public final int NUM_TYPES=8;

    // initial capacity of reused out packet
    private final int INITIAL_OUT_CAPACITY=4096;

    /** Creates a new instance of NearestEventMotionComputer */
    public NearestEventMotionComputer(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        resetFilter();
        setFilterEnabled(false);
    }

//    /** Creates a new instance of NearestEventMotionComputer */
//    public NearestEventMotionComputer(AEChip chip, EventFilter2D in) {
//        super(chip,in);
//        resetFilter();
//        setFilterEnabled(false);
//    }
//
//    /** Creates a new instance of NearestEventMotionComputer */
//    public NearestEventMotionComputer(EventFilter2D in) {
//        super(in);
//        resetFilter();
//        setFilterEnabled(false);
//    }

    public Object getFilterState() {
        return lastTimesMap;
    }

    @Override
	synchronized public void resetFilter() {
//        allocateMap(chip,2); // we hard-allocate enough timestamp arrays to handle up to 4 orientations TODO this should be generalized to the ae input type
    }

    void checkMap(EventPacket in){
        if((lastTimesMap==null)||(lastTimesMap.length!=(chip.getSizeX()+PADDING))||(lastTimesMap[0].length!=(chip.getSizeY()+PADDING))||(lastTimesMap[0][0].length!=in.getNumCellTypes())){
            allocateMap(in.getNumCellTypes());
        }
    }

    private void allocateMap(int numCellTypes) {
        if(!isFilterEnabled()) {
			return;
		}
        log.info("allocate lastTimesMap");
        lastTimesMap=new int[chip.getSizeX()+PADDING][chip.getSizeY()+PADDING][numCellTypes];
    }


    int maxEvents=0;
    int index=0;
    private short x,y;
    private byte type;
    private int ts;

    static final int PADDING=2;
    static final short P=1;
    int lastNumInputCellTypes=2;


    final class Dir{
        int x, y;
        Dir(int x, int y){
            this.x=x;
            this.y=y;
        }
    }

    // these offsets are indexed by inputType, then by (inputType+4)%8 (opposite direction)
    // when input type is orientation, then input type 0 is 0 deg horiz edge, so first index could be to down, second to up
    // so list should start with down
    final Dir[] offsets={
        new Dir(0,-1), // down
        new Dir(1,-1), // down right
        new Dir(1,0), // right
        new Dir(1,1), // 45 up right
        new Dir(0,1), // up
        new Dir(-1,1), // up left
        new Dir(-1,0), // left
        new Dir(-1,-1), // down left
    };



    public int getMaxDtThreshold() {
        return this.maxDtThreshold;
    }

    public void setMaxDtThreshold(final int maxDtThreshold) {
        this.maxDtThreshold = maxDtThreshold;
        getPrefs().putInt("NearestEventMotionComputer.maxDtThreshold",maxDtThreshold);
    }

    public int getMinDtThreshold() {
        return this.minDtThreshold;
    }

    public void setMinDtThreshold(final int minDtThreshold) {
        this.minDtThreshold = minDtThreshold;
        getPrefs().putInt("NearestEventMotionComputer.minDtThreshold", minDtThreshold);
    }

    @Override
	public void initFilter() {
        resetFilter();
    }

    @Override
	public void update(Observable o, Object arg) {
        initFilter();
    }

    @Override
	public void annotate(GLAutoDrawable drawable) {
    }

    @Override protected void clearOutputPacket(){
        if(out==null){
            out=new EventPacket(ApsDvsOrientationEvent.class);
        }else{
            out.clear();
        }
    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number.
     */
    @Override
	public EventPacket filterPacket(EventPacket in) {
        if(in==null) {
			return null;
		}
        if(!filterEnabled) {
			return in;
		}
        if(enclosedFilter!=null) {
			in=enclosedFilter.filterPacket(in);
		}
        int n=in.getSize();
        checkOutputPacketEventType(DvsMotionOrientationEvent.class);
        checkMap(in);
        // if the input is ON/OFF type, then motion detection doesn't make much sense because you are likely to detect
        // the nearest event from along the same edge, not from where the edge moved from.
        // therefore, this filter only really makes sense to use with an oriented input or with small objects like particles or raindrops
        //
        // for each event write out an event of type according to the direction of the most recent previous event in neighbors
        // only write the event if the delta time is within two-sided threshold
        OutputEventIterator outItr=out.outputIterator();
        try{
            for(Object ein:in){
                TypedEvent e=(TypedEvent)ein;
                x=(short)(e.x+P); // x and y are offset inside our timestamp storage array to avoid array access violations
                y=(short)(e.y+P);
                type=e.type;
                ts=e.timestamp;  // getString event x,y,type,timestamp of *this* event

                // for each output cell type (which codes a direction of motion), find the dt
                // between the orientation cell type perdindicular
                // to this direction in this pixel and in the neighbor - but only find the dt in that single direction.

                // find the time of the most recent event in a neighbor of the same type as the present input event
                // but only in the two directions perpindiclar to this orientation. Each of these codes for motion but in opposite directions.

                // ori input has type 0 for horizontal (red), 1 for 45 deg (blue), 2 for vertical (cyan), 3 for 135 deg (green)
                // for each input type, check in the perpindicular directions, ie, (dir+2)%numInputCellTypes and (dir+4)%numInputCellTypes

                // this computation only makes sense for ori type input

                // neighbors are of same type
                // they are in direction given by offsets in lastTimesMap
                // the input type tells us which offset to use, e.g. for type 0 (0 deg horiz ori), we offset first
//                int lastt=lastTimesMap[x+ix[type]][y+iy[type]][type]; // last event time in 1st neighbor
                int dt;
                Dir d;
                byte outType;
                int mindt=Integer.MAX_VALUE;
                outType=type;
                for(byte i=0;i<offsets.length;i++){
                    d=offsets[i];
                    dt=ts-lastTimesMap[x+d.x][y+d.y][type];
                    if(dt<mindt){
                        mindt=dt;
                        outType=i;
                    }
                }
                if((mindt<maxDtThreshold) && (mindt>minDtThreshold)){
                    DvsMotionOrientationEvent eout=(DvsMotionOrientationEvent)outItr.nextOutput();
                    eout.setX((short)(x-P));
                    eout.setY((short)(y-P));
                    eout.type=outType;
                    eout.setDirection(outType);
                    eout.timestamp=ts;
                }
                lastTimesMap[x][y][type]=ts;  // update the map
            }
        }catch(ArrayIndexOutOfBoundsException e){
            log.warning("NearestEventMotionComputer caught exception "+e+" probably caused by change of input cell type, reallocating lastTimesMap");
            checkMap(in);
        }

        return out;
    }

}
