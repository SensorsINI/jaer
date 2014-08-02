
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import java.util.logging.Level;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import javax.media.opengl.GL2;
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
    
    //Filter Variables
    private float avgExcitatoryActivity = 0f, avgInhibitoryActivity = 0f;
    // End filter Variables
    
    private int[][][] lastEventTypeMap;
    protected int[][][] lastTimesMap;
    protected int polValue;
    
    //TODO: make sure that everytime a raidus changes we calculate the new circle.
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
        if(isOutputPolarityEvents()) {
            checkOutputPacketEventType(PolarityEvent.class);
        } else {
            checkOutputPacketEventType(BackgroundActivityInhibitedEvent.class);
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
            
            avgExcitatoryActivity = getAvgActivity(x,y,polValue,excitationCirc);
            avgInhibitoryActivity = getAvgActivity(x,y,polValue,inhibitionCirc);
//            System.out.println("excecDir:"+avgExcitatoryActivity + " inhibitDir:"+avgInhibitoryActivity);

            exciteInhibitRatio = avgExcitatoryActivity-avgInhibitoryActivity;
//            System.out.println(exciteInhibitRatio);
            
            if( exciteInhibitRatio < exciteInhibitRatioThreshold){// INHIBITION
                hasGlobalMotion = 1;
//                System.out.println("Thresh:"+exciteInhibitRatioThreshold+" ratio:"+exciteInhibitRatio);
                if(!isShowInhibitedEvents()) continue;
            } else {// EXCITATION
                hasGlobalMotion=0;
            }
            
            writeEvent(outItr,e);
            //System.out.println("type:"+hasGlobalMotion+" outLength:"+outDir.length()+" inLength:"+centerDir.length()+" ratio:"+saveRatio);
        }
        return isShowRawInputEnabled() ? in : out;
    }

    private void writeEvent(OutputEventIterator outItr, PolarityEvent e) { 
        if(isOutputPolarityEvents()) {
            PolarityEvent oe = (PolarityEvent) outItr.nextOutput();
            oe.copyFrom(e);
        } else {
            BackgroundActivityInhibitedEvent oe = (BackgroundActivityInhibitedEvent) outItr.nextOutput();
            oe.copyFrom(e);
            oe.exciteInhibitionRatio = exciteInhibitRatio;
            oe.hasGlobalMotion = hasGlobalMotion;
        }   
    }
    
    @Override public void annotate(GLAutoDrawable drawable) {
        if(!isOutputPolarityEvents()){
            GL2 gl = drawable.getGL().getGL2();
            gl.glLineWidth(3f);
            gl.glPointSize(2);
            // draw individual motion vectors
            if(isDrawInhibitExcitePoints()) {
                // <editor-fold defaultstate="collapsed" desc="-- annotate Pixels that where associated with global motion --">
                gl.glPushMatrix();
                    for (Object o : out) {
                        BackgroundActivityInhibitedEvent e = (BackgroundActivityInhibitedEvent) o;
                        float[][] c=chip.getRenderer().makeTypeColors(2);
                        gl.glColor3fv(c[e.hasGlobalMotion],0);
                        gl.glBegin(GL2.GL_POINTS);
                        gl.glVertex2d(e.x, e.y);
                        gl.glEnd();
                    }
                gl.glPopMatrix();
                // </editor-fold>
            }

            if(isDrawCenterCell()) {
                // <editor-fold defaultstate="collapsed" desc="-- annotates a exemplatory Center cell with inhibitory and excitatory region & the average activity in those regions --">
                gl.glPushMatrix();
                
                //Draw regions of average
                gl.glBegin(GL2.GL_POINTS);
                    gl.glColor3f(1, 1, 0);
                    for (int[] circ1 : inhibitionCirc) {
                        gl.glVertex2d(circ1[0]+maxX/2, circ1[1]+maxY/2);
                    }
                    gl.glColor3f(0, 1, 1);
                    for (int[] circ1 : excitationCirc) {
                        gl.glVertex2d(circ1[0]+maxX/2, circ1[1]+maxY/2);
                    }
                gl.glEnd();

                int xOffset = 20,yOffset = maxY/2;
                for(Object o : out) {
                    BackgroundActivityInhibitedEvent e = (BackgroundActivityInhibitedEvent) o;
                    if(e.x == maxX/2 && e.y == maxY/2){
                        gl.glBegin(GL2.GL_LINES);
                            gl.glColor3f(0, 1, 0);
                            gl.glRectf(xOffset, yOffset, xOffset+5, yOffset+e.avgExcitationActivity);
                            gl.glColor3f(1, 0, 0);
                            gl.glRectf(-xOffset, yOffset, -xOffset+5, yOffset+e.avgInhibitionActivity);
                        gl.glEnd();

                        gl.glColor3f(1, 1, 1);
                        gl.glRectf(-10, 0, -5,  100*(float)e.exciteInhibitionRatio);
                        gl.glRectf(-10.5f, 0, -10, 100);
                    }
                }
                gl.glPopMatrix();
                // </editor-fold>
            }
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
    
////////////////////////    /** Computes the average optical flow vector from all positions given in a 
////////////////////////     * position list and with an (x,y) offset.
////////////////////////     * 
////////////////////////     * Each vector to be averaged is weighted by the temporal distance to the 
////////////////////////     * current event, effectively highpass filtering the Vectors before averaging.
////////////////////////     * Events with a dt > maxDtMs are ignored and not taken into account in the average.
////////////////////////     * 
////////////////////////     * @param x the offset of the pixel list in the x direction
////////////////////////     * @param ythe offset of the pixel list in the y direction
////////////////////////     * @param xyPixelList A [n][2] list where the first dimension is a list of 
////////////////////////     *  points that are defined in the second dimension. [n][0] is the x-component
////////////////////////     *  [n][1] is the y component of the point to be averaged.
////////////////////////     * @return the averaged vector. If no Event in the Testregion passed the 
////////////////////////     * significanceTest a (0,0) vector is returned.*/
    private float getAvgActivity(int x, int y, int type, int[][] xyPixelList) {
        float res = 0f;
        int n = 0,dt = 0,xLoc,yLoc;
        float fac;

        for (int[] xyItem : xyPixelList) {
            xLoc = xyItem[0] + x;
            yLoc = xyItem[1] + y;
            if(xLoc < MINX || xLoc >= maxX || yLoc < MINY || yLoc >= maxY) {
                //We are 'out of bounds' of the sensor. Nothing we can do here.
                // This DOES mean that we get some boundary effects, where the
                // average is not as meaningful as in the middle of the sensor,
                // but if the radii of excitation and inhibition are sufficiently
                // large it should not be a problem.
                continue;
            }
            
            dt = lastTimesMap[x][y][type]-lastTimesMap[xLoc][yLoc][type];
            fac = 1-(dt/(maxDtMs*TICK_PER_MS));
            
            if(fac <= 0){
                continue; 
                //This means that dt >= maxDtMs
                //No need to add zero and also dont increment n as nothing was added.
            }
            res += fac;
            n++;
        }
        
        if(n!=0) res /= n;
        return res;
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --outputPolarityEvents--">
    public boolean isOutputPolarityEvents() {
        return outputPolarityEvents;
    }

    public void setOutputPolarityEvents(boolean outputPolarityEvents) {
        this.outputPolarityEvents = outputPolarityEvents;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --showRawInputEnabled--">
    public boolean isShowRawInputEnabled() {
        return showRawInputEnabled;
    }

    public void setShowRawInputEnabled(final boolean showRawInputEnabled) {
        this.showRawInputEnabled = showRawInputEnabled;
        putBoolean("showRawInputEnabled",showRawInputEnabled);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --drawInhibitExcitePoints--">
    public boolean isDrawInhibitExcitePoints() {
        return drawInhibitExcitePoints;
    }

    public void setDrawInhibitExcitePoints(boolean drawMotionVectors) {
        this.drawInhibitExcitePoints = drawMotionVectors;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --drawCenterCell--">
    public boolean isDrawCenterCell() {
        return drawCenterCell;
    }

    public void setDrawCenterCell(boolean drawCenterCell) {
        this.drawCenterCell = drawCenterCell;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --showInhibitedEvents--">
    public boolean isShowInhibitedEvents() {
        return showInhibitedEvents;
    }

    public void setShowInhibitedEvents(boolean showTotalInhibitedEvents) {
        this.showInhibitedEvents = showTotalInhibitedEvents;
    }
    // </editor-fold>
}
