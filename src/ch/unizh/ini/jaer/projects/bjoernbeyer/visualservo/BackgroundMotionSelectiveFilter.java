
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import net.sf.jaer.util.Vector2D;
import java.util.logging.Level;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OpticalFlowEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.label.SmoothOpticalFlowLabeler;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.label.DvsDirectionSelectiveFilter;
import net.sf.jaer.eventprocessing.label.DvsOrientationFilter;

/**
 *
 * @author Bjoern
 */
@Description("Labels global and object motion based on OMS-type information from the motionDirection")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class BackgroundMotionSelectiveFilter extends AbstractBackgroundSelectiveFilter {
    
    private final SmoothOpticalFlowLabeler motionFilter;
    private boolean showExcitatoryAverageMotion = false;
    protected float exciteInhibitRatioThreshold = getFloat("exciteInhibitRatioThreshold",.45f);
    
    private EventPacket motionPacket = null;
     
    //Filter Variables
    private final Vector2D avgExcitatoryDir = new Vector2D();
    private final Vector2D avgInhibitoryDir = new Vector2D();
    protected double exciteInhibitRatio = 0;
    int curTime = 0;
    // End filter Variables
    
    private Vector2D[][] lastDirMap;
    protected int[][] lastTimesMap;

    //TODO: If subsampling is used as input for this filter the regions that are used for updating are currently not subsampled
    public BackgroundMotionSelectiveFilter(AEChip chip) {
        super(chip);

        motionFilter = new SmoothOpticalFlowLabeler(chip);
            motionFilter.setAnnotationEnabled(false);
            //We need to make sure that the enclosed orientation as well as the enclosed
            // directionselective Filter output all their events.
            ((DvsDirectionSelectiveFilter) motionFilter.getEnclosedFilter()).setPassAllEvents(true);
            ((DvsOrientationFilter) ((DvsDirectionSelectiveFilter) motionFilter.getEnclosedFilter()).getEnclosedFilter()).setPassAllEvents(true);
        setEnclosedFilter(motionFilter);
    }

    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(in==null) return null;
        if(!filterEnabled) return in;

        motionPacket = motionFilter.filterPacket(in); 
        if (motionPacket.getEventClass() != OpticalFlowEvent.class) {
            log.log(Level.WARNING, "input events are {0}, but they need to be OpticalFlowEvent's", motionPacket.getEventClass());
            return in;
        }  
        if(getOutputPolarityEvents()) {
            checkOutputPacketEventType(PolarityEvent.class);
        } else {
            checkOutputPacketEventType(BackgroundInhibitedEvent.class);
        }
        
        checkMaps();
        OutputEventIterator outItr = out.outputIterator();
       
        for(Object eIn:motionPacket) {
            OpticalFlowEvent e = (OpticalFlowEvent) eIn;
            
            //It is important that this filter is getting all events from the
            // directionSelective and orientationFilters. This means that the 
            // current event could be an event that has no orientation or no
            // direction of motion. However we can still inhibit this event, if
            // it is in the vicinity of detected global motion. So we do our
            // center/surround computation for every event and block it, if the
            // motion in the center is similar to the motion in the surround.
            // It is important that we inhibit ANY event, not just those that
            // have motion attached to them.
            
            x = e.x;
            y = e.y;
            curTime = e.timestamp;
            //We only need to fill the HistoryMaps if the event has a valid direction
            // If not we can not use it to average anyway. However wwe can still
            // process this event, as there might be directions in the receptive field
            if(e.isHasDirection()){
                lastDirMap[x][y].setLocation(e.optFlowVelPPS);
                lastTimesMap[x][y] = curTime;
            }
            
            avgExcitatoryDir.setLocation(getAvgDir(x,y,curTime,excitationCirc));
            avgInhibitoryDir.setLocation(getAvgDir(x,y,curTime,inhibitionCirc));
            //System.out.println("excecDir:"+avgExcitatoryDir + " inhibitDir:"+avgInhibitoryDir);

            if(avgExcitatoryDir.length() != 0) avgExcitatoryDir.unify(); 
            if(avgInhibitoryDir.length() != 0) avgInhibitoryDir.unify();
            exciteInhibitRatio = avgInhibitoryDir.dotProduct(avgExcitatoryDir);
            if(avgInhibitoryDir.length()==0 && avgExcitatoryDir.length() != 0) exciteInhibitRatio = -1; //Only ExcitatoryMotion detected
            if(avgInhibitoryDir.length()!=0 && avgExcitatoryDir.length() == 0) exciteInhibitRatio = 1; //Only InhibitoryMotion detected
            //As we are calculating the dot product of unified vectors, we are
            // sure that the ratio will be between -1 and 1.
            
            if( exciteInhibitRatio >= exciteInhibitRatioThreshold){// INHIBITION
                hasGlobalMotion = 1;
                if(!getShowInhibitedEvents()) continue;
            } else {// EXCITATION
                hasGlobalMotion=0;
            }
            
            writeEvent(outItr,e);
            //System.out.println("type:"+hasGlobalMotion+" outLength:"+outDir.length()+" inLength:"+centerDir.length()+" ratio:"+saveRatio);
        }
        return getShowRawInputEnabled() ? in : out;
    }

    private void writeEvent(OutputEventIterator outItr, OpticalFlowEvent e) { 
        if(getOutputPolarityEvents()) {
            PolarityEvent oe = (PolarityEvent) outItr.nextOutput();
            oe.copyFrom(e);
        } else {
            BackgroundInhibitedEvent oe = (BackgroundInhibitedEvent) outItr.nextOutput();
            oe.copyFrom(e);
            oe.hasGlobalMotion = hasGlobalMotion;
        }   
    }
    
    @Override protected void checkMaps(){
        if(lastTimesMap==null || lastTimesMap.length!=maxX || lastTimesMap[0].length!=maxY){
            allocateTimesMap();
        }
        if(lastDirMap==null ||  lastDirMap.length!=maxX || lastDirMap[0].length!=maxY){
            allocateLastDirMap();
        }
    }
    
    private void allocateTimesMap() {
        if(!isFilterEnabled()) return;
        lastTimesMap=new int[maxX][maxY];
        log.log(Level.INFO,"allocated int[{0}][{1}] array for last event times", new Object[]{maxX,maxY});
    }
    
    private void allocateLastDirMap() {
        if(!isFilterEnabled()) return;
        lastDirMap=new Vector2D[maxX][maxY];
        //Need to deep-initialize this, such that we can assign to each element directly
        // this is done only once and hence shouldnt be too expensive.
        for(int i=0;i<maxX;i++) {
            for(int j=0;j<maxY;j++) {
                lastDirMap[i][j] = new Vector2D();
            }
        }
        log.log(Level.INFO,"allocated int[{0}][{1}] array for last optic flow velocities", new Object[]{maxX,maxY});
    }
    
    /** Computes the average optical flow vector from all positions given in a 
     * position list and with an (x,y) offset.
     * 
     * Each vector to be averaged is weighted by the temporal distance to the 
     * current event, effectively highpass filtering the Vectors before averaging.
     * Events with a dt > maxDtMs are ignored and not taken into account in the average.
     * 
     * @param x the offset of the pixel list in the x direction
     * @param ythe offset of the pixel list in the y direction
     * @param xyPixelList A [n][2] list where the first dimension is a list of 
     *  points that are defined in the second dimension. [n][0] is the x-component
     *  [n][1] is the y component of the point to be averaged.
     * @return the averaged vector. If no Event in the Testregion passed the 
     * significanceTest a (0,0) vector is returned.*/
    private Vector2D getAvgDir(int x, int y, int curTime, int[][] xyPixelList) {
        Vector2D res = new Vector2D(0,0);
        int n = 0,dt = 0,xLoc,yLoc;
        float foo = 0f;

        for (int[] xyItem : xyPixelList) {
            xLoc = xyItem[0] + x;
            yLoc = xyItem[1] + y;
            if(xLoc < minX || xLoc >= maxX || yLoc < minY || yLoc >= maxY) {
                //We are 'out of bounds' of the sensor. Nothing we can do here.
                // This DOES mean that we get some boundary effects, where the
                // average is not as meaningful as in the middle of the sensor,
                // but if the radii of excitation and inhibition are sufficiently
                // large it should not be a problem.
                continue;
            }
            
            dt = curTime - lastTimesMap[xLoc][yLoc];
            
            if(dt <0) continue;//just to make sure
            foo = -4*(dt/(float)(maxDtUs));//When dt is larger than maxDtUs we want to reject it. AND we want to use the exp approx in the range -4:0 so we factor the 4 here. this does not matter, as theresult returned is just compared and does not carry internal meaning
            if(foo < -4) continue; //Saves computation time: exp(-4) ~ 0.01
            
            //Approximating the Exponential function as exp(x) = lim(1+x/n)^n for n->infinity.
            // Sufficiently good for small values for n==4 this is at least 20times cheaper than actually doing exp.
            foo = 1f+foo/4f;
            foo *= foo; foo *= foo;

            res.addFraction(lastDirMap[xLoc][yLoc],foo);
            n++;
        }
        
        if(n!=0) res.div(n);
        return res;
    }

    public boolean isShowExcitatoryAverageMotion() {
        return showExcitatoryAverageMotion;
    }

    public void setShowExcitatoryAverageMotion(boolean showExcitatoryAverageMotion) {
        this.showExcitatoryAverageMotion = showExcitatoryAverageMotion;
    }
        
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --exciteInhibitionRatioThreshold--">
    public float getExciteInhibitRatioThreshold() {
        return exciteInhibitRatioThreshold;
    }

    public void setExciteInhibitRatioThreshold(float exciteInhibitRatioThreshold) {
        float setValue = exciteInhibitRatioThreshold;
        if(setValue > 1) setValue = 1;
        if(setValue < -1)setValue = -1;
        this.exciteInhibitRatioThreshold = setValue;
        putFloat("exciteInhibitRatioThreshold",setValue);
    }
    // </editor-fold>
}
