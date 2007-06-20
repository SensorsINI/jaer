/*
 * Goalie.java
 *
 * Created on July 5, 2006, 1:17 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 5, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.chip.retina.sensorymotor;

import ch.unizh.ini.caviar.aemonitor.AEConstants;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.eventprocessing.filter.XYTypeFilter;
import ch.unizh.ini.caviar.eventprocessing.tracking.RectangularClusterTracker;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.caviar.hardwareinterface.ServoInterface;
import ch.unizh.ini.caviar.hardwareinterface.usb.ServoTest;
import ch.unizh.ini.caviar.hardwareinterface.usb.SiLabsC8051F320_USBIO_ServoController;
import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;

/**
 * Controls a servo motor that swings an arm in the way of a ball rolling towards a goal box.
 
 * @author tobi
 */
public class Goalie extends EventFilter2D implements FrameAnnotater{
    static Preferences prefs=Preferences.userNodeForPackage(Goalie.class);
    
    private boolean flipX=prefs.getBoolean("Goalie.flipX",false);
    //private float gain=prefs.getFloat("Goalie.gain",1f);
    //private float offset=prefs.getFloat("Goalie.offset",0);
    private boolean useVelocityForGoalie=prefs.getBoolean("Goalie.useVelocityForGoalie",true);
    
    //private float goaliePosition=.5f;  // servo motor control makes high servo values on left of picture when viewed looking from retina
    // 0.5f is in middle. 0 is far right, 1 is far left
    private long lastServoPositionTime=0; // used to relax servos after inactivity
    private int relaxationDelayMs=prefs.getInt("Goalie.relaxationDelayMs",300);
    private long learnDelayMS = prefs.getLong("Goalie.learnTimeMs",60000);
    
    RectangularClusterTracker tracker;
    //private boolean isRelaxed=true;
//    final int NUM_CLUSTERS_DEFAULT=5; // allocate lots of clusters in case there is clutter
    volatile RectangularClusterTracker.Cluster ball=null;
    private int pixelsToEdgeOfGoal=prefs.getInt("Goalie.pixelsToEdgeOfGoal",35);
    private int blindspotDelayMs=prefs.getInt("Goalie.blindspotDelayMs",100);
    
    private boolean useSoonest=prefs.getBoolean("Goalie.useSoonest",false); // use soonest ball rather than closest
    private boolean ignoreHand=prefs.getBoolean("Goalie.ignoreHand", false); // ignore 'balls' that are too large
    
    //private int goalieArmRows=prefs.getInt("Goalie.goalieArmRows",8); // these rows are ignored for acquiring new balls

    //private final int SERVO_NUMBER=0; // the servo number on the controller
    
    // possible states, ACTIVE meaning blocking ball we can see,
    // RELAXED is between blocks,
    // BLINDSPOT is when we are still blocking a ball that should hit goal but has passed out of sight
    private enum State {ACTIVE, RELAXED, BLINDSPOT};
    private State state=State.RELAXED; // initial state
    //private boolean showServoControl=false; // flag to run servo control gui
    //ServoTest servoTest=null;
    
    private int topRowsToIgnore=prefs.getInt("Goalie.topRowsToIgnore",0); // balls here are ignored (hands)
    
    private int rowsFromBottomToGoal=prefs.getInt("Goalie.rowsFromBottomToGoal",30); // defines distance in retina pixel rows from bottom of image to goal
    
    //private int goalieArmTauMs=prefs.getInt("Goalie.goalieArmTauMs",30); // default value for goalie arm lowpass 1st order time constant
    
    //LowpassFilter goalieFilter=null; // lowpass filter for goalie arm control signal
    
    
    //Arm control
    private ServoArm servoArm;
    
    /**
     * Creates a new instance of Goalie
     */
    public Goalie(AEChip chip) {
        super(chip);
        
        tracker=new RectangularClusterTracker(chip);
        tracker.setFilterEnabled(false);
        
        // only top filter
        XYTypeFilter xyfilter = new XYTypeFilter(chip);
        
        xyfilter.setFilterEnabled(false);
        xyfilter.setXEnabled(true);
        xyfilter.setYEnabled(true);
        xyfilter.setTypeEnabled(false);
        xyfilter.setStartY(pixelsToEdgeOfGoal);
        xyfilter.setEndY(128);
        xyfilter.setStartX(0);
        xyfilter.setEndX(128);
        tracker.setEnclosedFilter(xyfilter);
        
        
        //servo arm
        servoArm = new ServoArm(chip, pixelsToEdgeOfGoal);
        servoArm.initFilter();
        servoArm.setFilterEnabled(false);
      
//        tracker.setMaxNumClusters(NUM_CLUSTERS_DEFAULT); // ball will be closest object
        setEnclosedFilter(tracker);
        chip.getCanvas().addAnnotator(this);
        initFilter();
    }
    
    /** sets goalie arm.
     @param f 1 for far right, 0 for far left as viewed from above, i.e. from retina. gain value is also applied here so
     that user calibrates system such that 0 means pixel 0, 1 means pixel chip.getSizeX()
     @param timestamp the timestamp in us of the the last event
     */
   /* private void setGoalie(float f, int timestamp){
        isRelaxed=false;
        if(state!=state.ACTIVE) {
            state=State.ACTIVE;
            log.info("state ACTIVE");
        }
        f=(f-.5f)*gain+.5f+offset;
        if(f<0) f=0; else if(f>1) f=1;
        // goalie position stores the present value of position variable after proportional gain and offset are applied
        goaliePosition=goalieFilter.filter(f,timestamp); // filter using current time in us
//        System.out.println(String.format("t= %d in= %5.2f out= %5.2f",timestamp,f,goaliePosition));
        if(servo!=null){
            try{
                ServoInterface s=(ServoInterface)servo;
                s.setServoValue(SERVO_NUMBER,1-goaliePosition); // servo is servo 1 for goalie
            }catch(HardwareInterfaceException e){
                e.printStackTrace();
            }
        }
        
    }
    */
    /*
    private void relaxGoalie() {
        if(state!=state.RELAXED){
            state=State.RELAXED;
            log.info("state=RELAXED");
        }
        isRelaxed=true;
        if(servo==null) return;
        try{
            ServoInterface s=(ServoInterface)servo;
            s.disableServo(0);
            s.disableServo(1);
            lastServoPositionTime=System.currentTimeMillis();
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
        }
    }
    */
    int currentTime=0;
    
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        //checkHardware();
        getEnclosedFilter().setFilterEnabled(true);
        getEnclosedFilter().filterPacket(in);
        servoArm.filterPacket(in);
        
        if(!in.isEmpty()) currentTime=in.getLastTimestamp();
        ball=getPutativeBallCluster();
        checkForBlindspot(ball);
        // each of two clusters are used to control one servo
        RectangularClusterTracker.Cluster clusterLeft, clusterRight;
        checkToRelax(ball);
        switch(state){
            case ACTIVE:
            case RELAXED:
                // not enough time has passed to relax and we might have a ball
                if(ball!=null && ball.isVisible() ){ // check for null because ball seems to disappear on us when using processOnAcquisition mode (realtime mode)
                    // we have a ball, so move servo to position needed
                    // goalie:
                    // compute intersection of velocity vector of ball with bottom of view
                    // this is the place we should put the goalie
                    // this is computed from time to reach bottom (y/vy) times vx plus the x location
                    float x=(float)ball.location.x;
                    if(useVelocityForGoalie){
                        if(ball.velocity.y<0){ // don't use vel unless ball is rolling towards goal
                            x-=(float)(ball.location.y+rowsFromBottomToGoal)/ball.velocity.y*ball.velocity.x; // we need minus sign here because vel.y is negative
                        }
                    }
                    servoArm.setPosition((int)x);
                    lastServoPositionTime=System.currentTimeMillis();
                    state = state.ACTIVE;
                    //setGoalie(f,in.getLastTimestamp());
                }
                break;
        }
        return in;
    }
    
    RectangularClusterTracker.Cluster oldBall=null;
    
    /**
     * Gets the putative ball cluster. This method applies rules to determine the most likely ball cluster.
     Returns null if there is no ball.
     *
     * @return ball with min y, assumed closest to viewer. This should filter out a lot of hands that roll the ball towards the goal.
     *     If useVelocityForGoalie is true, then the ball ball must also be moving towards the goal.
     */
    private RectangularClusterTracker.Cluster getPutativeBallCluster(){
        if(tracker.getNumClusters()==0) return null;
        float minDistance=Float.POSITIVE_INFINITY, f, minTimeToImpact=Float.POSITIVE_INFINITY;
        RectangularClusterTracker.Cluster closest=null, soonest=null;
        for(RectangularClusterTracker.Cluster c:tracker.getClusters()){
            if( c.isVisible()){ // cluster must be visible
                if(!useSoonest){  // compute nearest cluster
                    if((f=(float)c.location.y) < minDistance ) {
                        if( (!useVelocityForGoalie) || (useVelocityForGoalie && c.velocity.y<=0)){
                            // give closest ball unconditionally if not using ball velocity
                            // but if using velocity, then only give ball if it is moving towards goal
                            minDistance=f;
                            closest=c;
                            // will it hit earlier?
                        }
                    }
                }else{ // use soonest to hit
                    float t=computeTimeToImpactMs(c);
                    if(t<minTimeToImpact) {
                        soonest=c;
                        minTimeToImpact=t;
                    }
                    
                }
            } // visible
        }
        
        RectangularClusterTracker.Cluster returnBall;
        if(useSoonest) returnBall= soonest; else returnBall= closest;
        
        // check if ball is possible goalie arm.
        // ball is arm if it is a new ball and its location is within the goalieArmRows of the bottom
        // of the image
        //if(returnBall!=null && returnBall!=oldBall && returnBall.location.y<goalieArmRows){
        //    returnBall=null;
        //}
        
        if(returnBall!=null && returnBall.location.y>chip.getSizeY()-topRowsToIgnore) return null;
        if(!ignoreHand || (ignoreHand && returnBall!=null && returnBall.getAverageEventDistance()<2*returnBall.getRadius())) {
            oldBall=returnBall;
            return returnBall;
        }else{
            return null;
        }
    } // getPutativeBallCluster
    
    /** @return time in ms to impact of cluster with goal line
     @see #pixelsToEdgeOfGoal
     */
    private float computeTimeToImpactMs(RectangularClusterTracker.Cluster cluster){
        if(cluster==null){
            log.warning("passed null cluster to getTimeToImpactMs");
            return Float.POSITIVE_INFINITY;
        }
        float y=cluster.location.y;
        float dy=cluster.velocity.y; // velocity of cluster in pixels/tick
        if(dy>=0) return Float.POSITIVE_INFINITY;
        dy=dy/(AEConstants.TICK_DEFAULT_US*1e-6f);
        float dt=-1000f*(y+pixelsToEdgeOfGoal)/dy;
        return dt;
    }
    
    /*private float applyGain(float f){
        return .5f+(f-.5f)*gain;
    }*/
    
    public Object getFilterState() {
        return null;
    }
    
    public void resetFilter() {
        tracker.resetFilter();
    }
    
    /** initializes arm lowpass filter */
    public void initFilter() {
  //      goalieFilter=new LowpassFilter();
  //      goalieFilter.setTauMs(goalieArmTauMs);
//        // turn off line buffering for System.out
//      FileOutputStream fdout =
//          new FileOutputStream(FileDescriptor.out);
//      BufferedOutputStream bos =
//          new BufferedOutputStream(fdout, 1024);
//      PrintStream ps =
//          new PrintStream(bos, false);
//  
//      System.setOut(ps);
    }
    
    @Override public void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        servoArm.setFilterEnabled(yes);
        if(yes) {
            servoArm.startLogging();
        } else {
            servoArm.relax();
        }
        /*if(!yes && servo!=null){
            ServoInterface s=(ServoInterface)servo;
            try{
                s.disableAllServos();
                s.close();
            }catch(HardwareInterfaceException e){
                e.printStackTrace();
            }
            servo=null;
        }*/
        //if(!yes) servoArm.relax();
    }
    
    /*private HardwareInterface servo=null;
    private int warningCount=0;
    
    private void checkHardware(){
        if(servo==null){
            servo=new SiLabsC8051F320_USBIO_ServoController();
            try{
                servo.open();
            }catch(HardwareInterfaceException e){
                servo=null;
                if(warningCount++%1000==0){
                    e.printStackTrace();
                }
            }
        }
    }
    */
    public boolean isFlipX() {
        return flipX;
    }
    
    /** Sets whether to flip the x, in case the servo motor is reversed
     @param flipX true to reverse
     */
    synchronized public void setFlipX(boolean flipX) {
        this.flipX = flipX;
        prefs.putBoolean("Goalie.flipX",flipX);
    }
    
   /* public float getGain() {
        return gain;
    }
    */
    
    /** Sets the open loop proportional controller gain for the goalie arm
     @param gain the gain, 0-3 range
     */
    /*
     public void setGain(float gain) {
        if(gain<0) gain=0; else if(gain>3) gain=3;
        this.gain = gain;
        prefs.putFloat("Goalie.gain",gain);
    }
    */
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    GLUquadric ballQuad;
    GLU glu;
    float[] ballColor=new float[3];
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled() ) return;
        tracker.annotate(drawable);
     //   if(isRelaxed) return;
        GL gl=drawable.getGL();
        gl.glPushMatrix();
        float x,y,radius;
        if(ball!=null){
            synchronized(ball){
                ball.getColor().getRGBColorComponents(ballColor);
                x=ball.location.x;
                y=ball.location.y;
                radius=ball.getRadius();
            }
            if(glu==null) glu=new GLU();
            if(ballQuad==null) ballQuad = glu.gluNewQuadric();
            if(state==state.BLINDSPOT) gl.glColor3f(0,0,.5f); else gl.glColor3fv(ballColor,0);
            gl.glPushMatrix();
            gl.glTranslatef(x,y,0);
            glu.gluQuadricDrawStyle(ballQuad,GLU.GLU_FILL);
            glu.gluDisk(ballQuad,radius-1,radius+1.,16,1);
            gl.glPopMatrix();
        }
        float f=servoArm.getDesiredPosition();
        gl.glRectf(f-8,pixelsToEdgeOfGoal+2,f+8,pixelsToEdgeOfGoal-1);
        
        gl.glColor3d(0.8,0.8,0.8);
        f = servoArm.getActualPosition();
        gl.glRectf(f-6,pixelsToEdgeOfGoal+2,f+6,pixelsToEdgeOfGoal-1);
        
        gl.glPopMatrix();
    }
    
    public boolean isUseVelocityForGoalie() {
        return useVelocityForGoalie;
    }
    
    /** Sets whether the goalie uses the ball velocity or just the position
     @param useVelocityForGoalie true to use ball velocity
     */
    public void setUseVelocityForGoalie(boolean useVelocityForGoalie) {
        this.useVelocityForGoalie = useVelocityForGoalie;
        prefs.putBoolean("Goalie.useVelocityForGoalie",useVelocityForGoalie);
    }
    
    public int getRelaxationDelayMs() {
        return relaxationDelayMs;
    }
    
    /** sets the delay after all targets disappear that the goalie relaxes
     @param relaxationDelayMs delay in ms
     */
    public void setRelaxationDelayMs(int relaxationDelayMs) {
        this.relaxationDelayMs = relaxationDelayMs;
        prefs.putInt("Goalie.learnDelayMS",relaxationDelayMs);
    }
    
     public int getLearnDelayMs() {
        return relaxationDelayMs;
    }
    
    /** sets the delay after all targets disappear that the goalie relaxes
     @param relaxationDelayMs delay in ms
     */
    public void setLearnDelayMs(long learnDelayMS) {
        this.learnDelayMS = learnDelayMS;
        prefs.putLong("Goalie.learnDelayMS",learnDelayMS);
    }
    
//    public float getOffset() {
//        return offset;
//    }
    
    /** sets open loop offset of goalie arm
     @param offset -1 to 1
     */
//    public void setOffset(float offset) {
//        if(offset<-1) offset=-1; else if(offset>1) offset=1;
//        this.offset = offset;
//        prefs.putFloat("Goalie.offset",offset);
//    }
    
    private void checkToRelax(RectangularClusterTracker.Cluster ball){
        // if enough time has passed AND there is no visible ball, then relax servo
        if( state==State.ACTIVE &&  (ball==null || !ball.isVisible()) && System.currentTimeMillis()- lastServoPositionTime > relaxationDelayMs ){
            servoArm.relax();
            state = state.RELAXED;
        }
        
        if( state == State.RELAXED && 
            (System.currentTimeMillis()- lastServoPositionTime > learnDelayMS)) {
            servoArm.startLearning();
            lastServoPositionTime = System.currentTimeMillis();
        }
        
        
    }
    
    int blindspotStartTime;
    final int BLINDSPOT_RANGE=7; // rows at bottom where blindspot starts
    
    // checks ball cluster to see if it has hit bottom of scene, if so, sets the state to be BLINDSPOT and starts the blindspot timer
    private void checkForBlindspot(RectangularClusterTracker.Cluster ball) {
        switch(state){
            case ACTIVE:
                if(ball!=null && ball.location.y<BLINDSPOT_RANGE){
                    if(state!=state.BLINDSPOT){
                        state=State.BLINDSPOT;
                        log.info("state=BLINDSPOT");
                    }
                    blindspotStartTime=ball.getLastEventTimestamp();
                }
                break;
            case BLINDSPOT:
                int dt=currentTime-blindspotStartTime;
                if(dt<0 || AEConstants.TICK_DEFAULT_US*(dt)>blindspotDelayUs){
                    state=state.ACTIVE;
                }
                break;
        }
    }
    
    public int getBlindspotDelayMs() {
        return blindspotDelayMs;
    }
    
    /** Sets time that goalie holds position when ball enters blindspot in front of goal
     @param blindspotDelayMs the time in ms
     */
    public void setBlindspotDelayMs(int blindspotDelayMs) {
        this.blindspotDelayMs = blindspotDelayMs;
        prefs.putInt("Goalie.blindspotDelayMs",blindspotDelayMs);
        blindspotDelayUs=blindspotDelayMs*1000;
    }
    
    int blindspotDelayUs=blindspotDelayMs*1000;
    
    public boolean isUseSoonest() {
        return useSoonest;
    }
    
    /** If true, then goalie uses ball that will hit soonest. If false, goalie uses ball that is closest to goal.
     @see #setUseVelocityForGoalie
     @param useSoonest true to use soonest threat, false to use closest threat
     */
    public void setUseSoonest(boolean useSoonest) {
        this.useSoonest = useSoonest;
        prefs.putBoolean("Goalie.useSoonest",useSoonest);
    }
    
    public boolean isIgnoreHand() {
        return ignoreHand;
    }
    
    /** Set true to ignore ball clusters that have a size that is too large - these are probably hands
     @param ignoreHand true to ignore balls that are large
     */
    public void setIgnoreHand(boolean ignoreHand) {
        this.ignoreHand = ignoreHand;
    }
    
//    public boolean isShowServoControl() {
//        return showServoControl;
//    }
    
    /** When set true, shows the servo test gui
     @param showServoControl true to show, false to hide
     */
//    public void setShowServoControl(boolean showServoControl) {
//        this.showServoControl = showServoControl;
//        if(showServoControl){
//            if(servoTest==null){
//                servoTest=new ServoTest((ServoInterface)servo);
//            }else{
//                servoTest.setVisible(true);
//            }
//        }else{
//            if(servoTest!=null) servoTest.setVisible(false);
//        }
//    }
    
    public int getTopRowsToIgnore() {
        return topRowsToIgnore;
    }
    
    public void setTopRowsToIgnore(int topRowsToIgnore) {
        if(topRowsToIgnore>chip.getSizeY()) topRowsToIgnore=chip.getSizeY();
        this.topRowsToIgnore = topRowsToIgnore;
        prefs.putInt("Goalie.topRowsToIgnore",topRowsToIgnore);
    }
    
    public int getRowsFromBottomToGoal() {
        return rowsFromBottomToGoal;
    }
    
    /** Defines the number of retina rows is the distance from bottom of image to goal.
     Constrained to range 0-chip.getSizeY()/2.
     @param rowsFromBottomToGoal the number of rows of pixels
     */
    public void setRowsFromBottomToGoal(int rowsFromBottomToGoal) {
        if(rowsFromBottomToGoal<0) rowsFromBottomToGoal=0; else if(rowsFromBottomToGoal>chip.getSizeY()/2) rowsFromBottomToGoal=chip.getSizeY()/2;
        this.rowsFromBottomToGoal = rowsFromBottomToGoal;
        prefs.putInt("Goalie.rowsFromBottomToGoal",rowsFromBottomToGoal);
    }
    
//    public int getGoalieArmRows() {
//        return goalieArmRows;
//    }
//    
    /** These rows at bottom of image are ignored for acquiring new balls - they are assumed to come from seeing
     our own arm.
     @param goalieArmRows the number of retina rows at bottom of image to ignore for acquisition of new balls. Constrained to
     0-chip.getSizeY()/2.
     */
//    public void setGoalieArmRows(int goalieArmRows) {
//        if(goalieArmRows<0) goalieArmRows=0; else if(goalieArmRows>chip.getSizeY()/2) goalieArmRows=chip.getSizeY()/2;
//        this.goalieArmRows = goalieArmRows;
//        prefs.putInt("Goalie.goalieArmRows",goalieArmRows);
//    }
////    
//    public int getGoalieArmTauMs() {
//        return goalieArmTauMs;
//    }
    
    /** Sets the lowpass time constant for the goalie arm position signal. This helps prevent the arm from overshooting its mark
     by limiting the rate of change of the control signal.
     @param goalieArmTauMs the first order time constant in ms
     */
//    public void setGoalieArmTauMs(int goalieArmTauMs) {
//        if(goalieArmTauMs<1) goalieArmTauMs=1;
//        this.goalieArmTauMs = goalieArmTauMs;
//        prefs.putInt("Goalie.goalieArmTauMs",goalieArmTauMs);
//        goalieFilter.setTauMs(goalieArmTauMs);
//    }
    
}
