
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import java.util.logging.Level;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.PolarityEvent;

/**
 *
 * @author Bjoern
 */
@Description("Labels global and object motion based on OMS-type information from polarity events")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class BackgroundActivitySelectiveFilter extends AbstractBackgroundSelectiveFilter {
    private float integratedExcitatoryPot = 0f, integratedInhibitoryPot = 0f;
    
    private int[][][] lastEventTypeMap;
    protected int[][][] lastTimesMap;
    protected int polValue;
    protected float potDiff = 0f;
    protected float rejectionThreshold = getFloat("rejectionThreshold",.35f);
    
    //TODO: Das wird jetzt alles nicht funktionieren wenn man subsampled im anderen Filter oder?
    public BackgroundActivitySelectiveFilter(AEChip chip) {
        super(chip);
    }

    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(in==null) return null;
        if(!filterEnabled) return in;

        if (in.getEventClass() != PolarityEvent.class) {
            log.log(Level.WARNING, "input events are {0}, but they need to be PolarityEvent's", in.getEventClass());
            return in;
        }  
        
        if(getOutputPolarityEvents()) {
            checkOutputPacketEventType(PolarityEvent.class);
        } else {
            checkOutputPacketEventType(BackgroundInhibitedEvent.class);
        }
        
        checkMaps();
        OutputEventIterator outItr = out.outputIterator();
       
        for(Object eIn:in) {
            PolarityEvent e = (PolarityEvent) eIn;
            
            x = e.x;
            y = e.y;
            polValue = ((e.getPolarity() == PolarityEvent.Polarity.On) ? 0 : 1);

            lastEventTypeMap[x][y][polValue]++;
            lastTimesMap[x][y][polValue] = e.timestamp;
            
            integratedExcitatoryPot = getAvgPot(x,y,polValue,excitationCirc);
            integratedInhibitoryPot = getAvgPot(x,y,polValue,inhibitionCirc);
            
            
            potDiff = integratedExcitatoryPot-integratedInhibitoryPot;
            if(potDiff<0)potDiff*=-1;//Math.abs is super slow compared to this
            
//            System.out.printf("avgExcite %.5f , avgInhibit %.5f , tempDiff %.5f , thresh %.5f \n", avgExcitatoryDt,avgInhibitoryDt,exciteInhibitTempDiffUs,exciteInhibitThresholdUs);
            if( potDiff < rejectionThreshold){// INHIBITION
                hasGlobalMotion = 1;
                if(!getShowInhibitedEvents()) continue;
            } else {// EXCITATION
                hasGlobalMotion=0;
            }
            
            writeEvent(outItr,e);
        }
        return getShowRawInputEnabled() ? in : out;
    }

    private void writeEvent(OutputEventIterator outItr, PolarityEvent e) { 
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
        if(lastEventTypeMap==null ||  lastEventTypeMap.length!=maxX || lastEventTypeMap[0].length!=maxY){
            allocateLastEventTypeMap();
        }
    }
    
    private void allocateTimesMap() {
        if(!isFilterEnabled()) return;
        lastTimesMap=new int[maxX][maxY][2];
        log.log(Level.INFO,"allocated int[{0}][{1}][{2}] array for last event times", new Object[]{maxX,maxY,2});
    }
    
    private void allocateLastEventTypeMap() {
        if(!isFilterEnabled()) return;
        lastEventTypeMap=new int[maxX][maxY][2];
        //Need to deep-initialize this, such that we can assign to each element directly
        // this is done only once and hence shouldnt be too expensive.
        for(int i=0;i<maxX;i++) {
            for(int j=0;j<maxY;j++) {
                for(int k=0;k<2;k++){
                    lastEventTypeMap[i][j][k] = 0;
                }
            }
        }
        log.log(Level.INFO,"allocated int[{0}][{1}][{2}] array for last pixel activities", new Object[]{maxX,maxY,2});
    }
    
    /** Computes the integrated potential between this event and all events
     * in the xyPixelList.
     * 
     * The pixelList gives a replacement relative to the given x,y positions
     * and only if an event occured will its dt count towards the average.
     * Events with a dt higher than a threshold are not concidered.
     * 
     * @param x the offset of the pixel list in the x direction
     * @param ythe offset of the pixel list in the y direction
     * @param xyPixelList A [n][2] list where the first dimension is a list of 
     *  points that are defined in the second dimension. [n][0] is the x-component
     *  [n][1] is the y component of the point to be averaged.
     * @return the integrated potential. If no Event in the Testregion passed the 
     * significanceTest 0 is returned.*/
    private float getAvgPot(int x, int y, int type, int[][] xyPixelList) {
        float res = 0f, foo = 0f;
        int n = 0,dt = 0,xLoc,yLoc;

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
            dt = lastTimesMap[x][y][type]-lastTimesMap[xLoc][yLoc][type];

            if(dt <0) continue;//just to make sure
            foo = -4*(dt/(float)(maxDtUs));//When dt is larger than maxDtUs we want to reject it. AND we want to use the exp approx in the range -4:0 so we factor the 4 here. this does not matter, as theresult returned is just compared and does not carry internal meaning
            if(foo < -4) continue; //Saves computation time: exp(-4) ~ 0.01
            
            //Approximating the Exponential function as exp(x) = lim(1+x/n)^n for n->infinity.
            // Sufficiently good for small values for n==4 this is at least 20times cheaper than actually doing exp.
            foo = 1f+foo/4f;
            foo *= foo; foo *= foo;

            res += foo;
            n++;
        }
        
        if(n!=0) {
            res /= n;
        }//Else res is still 0f;
        return res;
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --exciteInhibitionRatioThreshold--">
    public float getrejectionThreshold() {
        return rejectionThreshold;
    }

    public void setrejectionThreshold(float exciteInhibitThresholdMs) {
        float setValue = exciteInhibitThresholdMs;
        if(setValue < 0)setValue = 0;
        if(setValue > 1)setValue = 1;
        this.rejectionThreshold = setValue;
        putFloat("rejectionThreshold",setValue);
    }
    // </editor-fold>
}
