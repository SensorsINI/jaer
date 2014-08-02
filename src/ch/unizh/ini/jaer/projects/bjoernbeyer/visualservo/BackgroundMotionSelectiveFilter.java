
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import net.sf.jaer.util.Vector2D;
import java.util.logging.Level;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OpticalFlowEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.label.SmoothOpticalFlowLabeler;
import javax.media.opengl.GL2;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.PolarityEvent;

/**
 *
 * @author Bjoern
 */
@Description("Labels global and object motion based on OMS-type information from the motionDirection")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class BackgroundMotionSelectiveFilter extends AbstractBackgroundSelectiveFilter {
    

    
    private final SmoothOpticalFlowLabeler motionFilter;
    
    private EventPacket motionPacket = null;
     
    //Filter Variables
    private final Vector2D avgExcitatoryDir = new Vector2D();
    private final Vector2D avgInhibitoryDir = new Vector2D();
    // End filter Variables
    
    private Vector2D[][] lastDirMap;
    protected int[][] lastTimesMap;
    
    //TODO: make sure that everytime a raidus changes we calculate the new circle.
    //TODO: Das wird jetzt alles nicht funktionieren wenn man subsampled im anderen Filter oder?
    public BackgroundMotionSelectiveFilter(AEChip chip) {
        super(chip);

        motionFilter = new SmoothOpticalFlowLabeler(chip);
        motionFilter.setAnnotationEnabled(false);
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
        if(isOutputPolarityEvents()) {
            checkOutputPacketEventType(PolarityEvent.class);
        } else {
            checkOutputPacketEventType(BackgroundMotionInhibitedEvent.class);
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

            lastDirMap[x][y].setLocation(e.optFlowVelPPS);
            lastTimesMap[x][y] = e.timestamp;
            
            avgExcitatoryDir.setLocation(getAvgDir(x,y,excitationCirc));
            avgInhibitoryDir.setLocation(getAvgDir(x,y,inhibitionCirc));
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
                if(!isShowInhibitedEvents()) continue;
            } else {// EXCITATION
                hasGlobalMotion=0;
            }
            
            writeEvent(outItr,e);
            //System.out.println("type:"+hasGlobalMotion+" outLength:"+outDir.length()+" inLength:"+centerDir.length()+" ratio:"+saveRatio);
        }
        return isShowRawInputEnabled() ? in : out;
    }

    private void writeEvent(OutputEventIterator outItr, OpticalFlowEvent e) { 
        if(isOutputPolarityEvents()) {
            PolarityEvent oe = (PolarityEvent) outItr.nextOutput();
            oe.copyFrom(e);
        } else {
            BackgroundMotionInhibitedEvent oe = (BackgroundMotionInhibitedEvent) outItr.nextOutput();
            oe.copyFrom(e);
            oe.avgExcitationVel.setLocation(avgExcitatoryDir);
            oe.avgInhibitionVel.setLocation(avgInhibitoryDir);
            oe.exciteInhibitionRatio = exciteInhibitRatio;
            oe.hasGlobalMotion = hasGlobalMotion;
        }   
    }
    
    @Override public void annotate(GLAutoDrawable drawable) {
        if(!isOutputPolarityEvents()){
            GL2 gl = drawable.getGL().getGL2();
            // draw individual motion vectors
            if(isDrawInhibitExcitePoints()) {
                gl.glPushMatrix();
                gl.glColor3f(1, 1, 1);
                gl.glLineWidth(3f);
                gl.glPointSize(2);
                for (Object o : out) {
                    BackgroundMotionInhibitedEvent e = (BackgroundMotionInhibitedEvent) o;
                    float[][] c=chip.getRenderer().makeTypeColors(2);
                    gl.glColor3fv(c[e.hasGlobalMotion],0);
                    gl.glBegin(GL2.GL_POINTS);
                        gl.glVertex2d(e.x, e.y);
                    gl.glEnd();
                }
                gl.glPopMatrix();
            }

            if(isDrawCenterCell()) {
                gl.glPushMatrix();

                gl.glPointSize(2);
                gl.glLineWidth(4f);

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
                gl.glPopMatrix();
                
                int xOffset = maxX/2,yOffset = maxY/2;
                int scale=20;
                for(Object o : out) {
                    BackgroundMotionInhibitedEvent e = (BackgroundMotionInhibitedEvent) o;
                    if(e.x == maxX/2 && e.y == maxY/2){
                        gl.glPushMatrix();
                        gl.glColor3f(0, 1, 0);
                        e.avgExcitationVel.drawVector(gl, xOffset+20, yOffset, 1, scale);
                        gl.glPopMatrix();
                        
                        gl.glPushMatrix();
                        gl.glColor3f(1, 0, 0);
                        e.avgInhibitionVel.drawVector(gl, xOffset-20, yOffset, 1, scale);
                        gl.glPopMatrix();

                        gl.glColor3f(1, 1, 1);
                        gl.glRectf(-10, 0, -5,  100*(float)e.exciteInhibitionRatio);
                        gl.glRectf(-10.5f, 0, -10, 100);
                    }
                }
            }
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
    private Vector2D getAvgDir(int x, int y, int[][] xyPixelList) {
        Vector2D res = new Vector2D(0,0);
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
            
            dt = lastTimesMap[x][y]-lastTimesMap[xLoc][yLoc];
            fac = 1-(dt/(maxDtMs*TICK_PER_MS));
            
            if(fac <= 0){
                continue; 
                //This means that dt >= maxDtMs
                //No need to add zero and also dont increment n as nothing was added.
            }
            res.addFraction(lastDirMap[xLoc][yLoc],fac);
            n++;
        }
        
        if(n!=0) res.div(n);
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
