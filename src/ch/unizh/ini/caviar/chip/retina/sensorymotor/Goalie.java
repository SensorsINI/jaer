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

import ch.unizh.ini.caviar.JAERViewer;
import ch.unizh.ini.caviar.aemonitor.AEConstants;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.eventprocessing.FilterChain;
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
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;

/**
 * Controls a servo motor that swings an arm in the way of a ball rolling towards a goal box.
 * Calibrates itself as well.
 *
 * @author tobi delbruck/manuel lang
 */
public class Goalie extends EventFilter2D implements FrameAnnotater, Observer{
    //static Preferences getPrefs()=Preferences.userNodeForPackage(Goalie.class);
    
    // private boolean flipX=getPrefs().getBoolean("Goalie.flipX",false);
    //private float gain=getPrefs().getFloat("Goalie.gain",1f);
    //private float offset=getPrefs().getFloat("Goalie.offset",0);
    private boolean useVelocityForGoalie=getPrefs().getBoolean("Goalie.useVelocityForGoalie",true);
    
    private final int MIN_BALL_Y_SPEED_TO_USE=10; // ball must have at least this y speed to use it for computing arm position
    
    {setPropertyTooltip("useVelocityForGoalie","uses ball velocity to calc impact position");}
    
    //private float goaliePosition=.5f;  // servo motor control makes high servo values on left of picture when viewed looking from retina
    // 0.5f is in middle. 0 is far right, 1 is far left
    private long lastServoPositionTime=0; // used to relax servos after inactivity
    private int relaxationDelayMs=getPrefs().getInt("Goalie.relaxationDelayMs",300);
    {setPropertyTooltip("relaxationDelayMs","time [ms] before motor relaxes\n");}
    private long learnDelayMS = getPrefs().getLong("Goalie.learnTimeMs",60000);
    {setPropertyTooltip("learnDelayMS","time [ms] of no balls present before a new learning cycle starts ");}
    
    RectangularClusterTracker tracker;
    //private boolean isRelaxed=true;
//    final int NUM_CLUSTERS_DEFAULT=5; // allocate lots of clusters in case there is clutter
    volatile RectangularClusterTracker.Cluster ball=null;
    
    final Object ballLock = new Object();
    
    private int armRows=getPrefs().getInt("Goalie.pixelsToEdgeOfGoal",40);
    {setPropertyTooltip("armRows","arm and ball tracking separation line position [pixels]");}
    
    private int pixelsToTipOfArm=getPrefs().getInt("Goalie.pixelsToTipOfArm",32);
    {setPropertyTooltip("pixelsToTipOfArm","defines distance in rows from bottom of image to tip of arm, used for computing arm position [pixels]");}
    
    private boolean useSoonest=getPrefs().getBoolean("Goalie.useSoonest",false); // use soonest ball rather than closest
    {setPropertyTooltip("useSoonest","react on soonest ball first");}
    private boolean ignoreHand=getPrefs().getBoolean("Goalie.ignoreHand", false); // ignore 'balls' that are too large
    {setPropertyTooltip("ignoreHand","try to ignore human hands");}
    
    //private int goalieArmRows=getPrefs().getInt("Goalie.goalieArmRows",8); // these rows are ignored for acquiring new balls
    
    //private final int SERVO_NUMBER=0; // the servo number on the controller
    
    // possible states, ACTIVE meaning blocking ball we can see,
    // RELAXED is between blocks,
    // BLINDSPOT is when we are still blocking a ball that should hit goal but has passed out of sight
    private enum State {ACTIVE, RELAXED, BLINDSPOT};
    private State state=State.RELAXED; // initial state
    //private boolean showServoControl=false; // flag to run servo control gui
    //ServoTest servoTest=null;
    
    private int topRowsToIgnore=getPrefs().getInt("Goalie.topRowsToIgnore",0); // balls here are ignored (hands)
    {setPropertyTooltip("topRowsToIgnore","botto rows to ignore");}
    
    //private int goalieArmTauMs=getPrefs().getInt("Goalie.goalieArmTauMs",30); // default value for goalie arm lowpass 1st order time constant
    
    //LowpassFilter goalieFilter=null; // lowpass filter for goalie arm control signal
    
    
    //Arm control
    private ServoArm servoArm;
    
    //FilterChain for GUI
    FilterChain guiChain;
    
    /**
     * Creates a new instance of Goalie
     */
    public Goalie(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        
        //build hierachy
        guiChain = new FilterChain(chip);
        tracker=new RectangularClusterTracker(chip);
        servoArm = new ServoArm(chip);
        XYTypeFilter xyfilter = new XYTypeFilter(chip);
        
        guiChain.add(tracker);
        guiChain.add(servoArm);
        setEnclosedFilterChain(guiChain);
        tracker.setEnclosedFilter(xyfilter);
        tracker.setEnclosed(true, this);
        servoArm.setEnclosed(true, this);
        xyfilter.setEnclosed(true, tracker);
        
        //tracker.setFilterEnabled(false);
        
        // only top filter
        
        xyfilter.setXEnabled(true);
        xyfilter.setYEnabled(true);
        xyfilter.setTypeEnabled(false);
        xyfilter.setStartY(armRows);
        
        
        servoArm.initFilter();
        servoArm.setCaptureRange(0,0, 128, armRows);
        
        
        
//        tracker.setMaxNumClusters(NUM_CLUSTERS_DEFAULT); // ball will be closest object
        
        
        
        
        chip.getCanvas().addAnnotator(this);
        initFilter();
    }
    
    /** sets goalie arm.
     * @param f 1 for far right, 0 for far left as viewed from above, i.e. from retina. gain value is also applied here so
     * that user calibrates system such that 0 means pixel 0, 1 means pixel chip.getSizeX()
     * @param timestamp the timestamp in us of the the last event
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
        tracker.filterPacket(in);
        servoArm.filterPacket(in);
        
        if(!in.isEmpty()) currentTime=in.getLastTimestamp();
        synchronized (ballLock) {
            ball=getPutativeBallCluster();
            
            // each of two clusters are used to control one servo
            RectangularClusterTracker.Cluster clusterLeft, clusterRight;
            checkToRelax(ball);
        }
        switch(state){
            case ACTIVE:
            case RELAXED:
                // not enough time has passed to relax and we might have a ball
                if(ball!=null && ball.isVisible() ){ // check for null because ball seems to disappear on us when using processOnAcquisition mode (realtime mode)
                    // we have a ball, so move servo to position needed
                    // goalie:
                    // compute intersection of velocity vector of ball with bottom of view.
                    // this is the place we should put the goalie.
                    // this is computed from time to reach bottom (y/vy) times vx plus the x location.
                    // we also include a parameter pixelsToEdgeOfGoal which is where the goalie arm is in the field of view
                    if(JAERViewer.globalTime1 == 0)
                        JAERViewer.globalTime1 = System.nanoTime();
                    
                    float x=(float)ball.location.x;
                    if(useVelocityForGoalie){
                        Point2D.Float v=ball.getVelocityPPS();
                        if(v.y<-MIN_BALL_Y_SPEED_TO_USE){ // don't use vel unless ball is rolling towards goal
                            // we need minus sign here because vel.y is negative
                            x-=(float)(ball.location.y-pixelsToTipOfArm)/v.y*v.x;
//                            if(x<0 || x>chip.getSizeX()) System.out.println("x="+x);
                        }
                    }
                    servoArm.setPosition((int)x);
                    lastServoPositionTime=System.currentTimeMillis();
                    checkToRelax_state = 0;   //next time idle move arm back to middle
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
     * Returns null if there is no ball.
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
                        if( (!useVelocityForGoalie) || (useVelocityForGoalie && c.getVelocityPPS().y<=0)){
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
     * @see #pixelsToEdgeOfGoal
     */
    private float computeTimeToImpactMs(RectangularClusterTracker.Cluster cluster){
        if(cluster==null){
            log.warning("passed null cluster to getTimeToImpactMs");
            return Float.POSITIVE_INFINITY;
        }
        float y=cluster.location.y;
        float dy=cluster.getVelocityPPS().y; // velocity of cluster in pixels/tick
        if(dy>=0) return Float.POSITIVE_INFINITY;
        dy=dy/(AEConstants.TICK_DEFAULT_US*1e-6f);
        float dt=-1000f*(y-pixelsToTipOfArm)/dy;
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
        
        servoArm.setCaptureRange(0,0, chip.getSizeX(), armRows);
        
        
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
  /*
    public boolean isFlipX() {
        return flipX;
    }
   */
    /** Sets whether to flip the x, in case the servo motor is reversed
     * @param flipX true to reverse
     */
    /*
    synchronized public void setFlipX(boolean flipX) {
        this.flipX = flipX;
        getPrefs().putBoolean("Goalie.flipX",flipX);
    }
     */
    
   /* public float getGain() {
        return gain;
    }
    */
    
    /** Sets the open loop proportional controller gain for the goalie arm
     * @param gain the gain, 0-3 range
     */
    /*
     public void setGain(float gain) {
        if(gain<0) gain=0; else if(gain>3) gain=3;
        this.gain = gain;
        getPrefs().putFloat("Goalie.gain",gain);
    }
     */
    
    /** not used */
    public void annotate(float[][][] frame) {
    }
    
    /** not used */
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
        float x=0.0f,y=0.0f,radius=0.0f;
        synchronized(ballLock){
            if(ball != null) {
                ball.getColor().getRGBColorComponents(ballColor);
                x=ball.location.x;
                y=ball.location.y;
                radius=ball.getRadius();
            }
        }
        if(glu==null) glu=new GLU();
        if(ballQuad==null) ballQuad = glu.gluNewQuadric();
        if(state==state.BLINDSPOT) gl.glColor3f(0,0,.5f); else gl.glColor3fv(ballColor,0);
        gl.glPushMatrix();
        gl.glTranslatef(x,y,0);
        glu.gluQuadricDrawStyle(ballQuad,GLU.GLU_FILL);
        glu.gluDisk(ballQuad,radius-1,radius+1.,16,1);
        gl.glPopMatrix();
        
        float f=servoArm.getDesiredPosition(); // draw desired position of arm in color of ball being blocked
        gl.glRectf(f-8,pixelsToTipOfArm+2,f+8,pixelsToTipOfArm-1);
        
        gl.glColor3d(0.8,0.8,0.8);
        f = servoArm.getActualPosition(); // draw actual tracked position of arm in light grey
        gl.glRectf(f-6,pixelsToTipOfArm+2,f+6,pixelsToTipOfArm-1);
        
        gl.glPopMatrix();
    }
    
    public boolean isUseVelocityForGoalie() {
        return useVelocityForGoalie;
    }
    
    /** Sets whether the goalie uses the ball velocity or just the position
     * @param useVelocityForGoalie true to use ball velocity
     */
    public void setUseVelocityForGoalie(boolean useVelocityForGoalie) {
        this.useVelocityForGoalie = useVelocityForGoalie;
        getPrefs().putBoolean("Goalie.useVelocityForGoalie",useVelocityForGoalie);
    }
    
    public int getRelaxationDelayMs() {
        return relaxationDelayMs;
    }
    
    /** sets the delay after all targets disappear that the goalie relaxes
     * @param relaxationDelayMs delay in ms
     */
    public void setRelaxationDelayMs(int relaxationDelayMs) {
        this.relaxationDelayMs = relaxationDelayMs;
        getPrefs().putInt("Goalie.learnDelayMS",relaxationDelayMs);
    }
    
    
//    public float getOffset() {
//        return offset;
//    }
    
    /** sets open loop offset of goalie arm
     * @param offset -1 to 1
     */
//    public void setOffset(float offset) {
//        if(offset<-1) offset=-1; else if(offset>1) offset=1;
//        this.offset = offset;
//        getPrefs().putFloat("Goalie.offset",offset);
//    }
    private int checkToRelax_state = 0;
    private void checkToRelax(RectangularClusterTracker.Cluster ball){
        // if enough time has passed AND there is no visible ball, then relax servo
        
        if( state==State.ACTIVE &&  (ball==null || !ball.isVisible()) && System.currentTimeMillis()- lastServoPositionTime > relaxationDelayMs
                && checkToRelax_state == 0  ){
            servoArm.relax();
            state = state.RELAXED;
            checkToRelax_state = 1;
        }
        
        if( state == State.RELAXED &&
                (System.currentTimeMillis()- lastServoPositionTime > relaxationDelayMs*10)
                && checkToRelax_state == 1) {
            servoArm.setPosition(chip.getSizeX() / 2);
            checkToRelax_state = 2;
        }
        
        if( state == State.RELAXED &&
                (System.currentTimeMillis()- lastServoPositionTime > relaxationDelayMs*11)
                && checkToRelax_state == 2) {
            servoArm.relax();
            checkToRelax_state = 3;
        }
        
        if( state == State.RELAXED &&
                (System.currentTimeMillis()- lastServoPositionTime > getLearnDelayMS())) {
            servoArm.startLearning();
            lastServoPositionTime = System.currentTimeMillis();
        }
        
        
    }
    
    int blindspotStartTime;
    final int BLINDSPOT_RANGE=7; // rows at bottom where blindspot starts
    
    
    public boolean isUseSoonest() {
        return useSoonest;
    }
    
    /** If true, then goalie uses ball that will hit soonest. If false, goalie uses ball that is closest to goal.
     * @see #setUseVelocityForGoalie
     * @param useSoonest true to use soonest threat, false to use closest threat
     */
    public void setUseSoonest(boolean useSoonest) {
        this.useSoonest = useSoonest;
        getPrefs().putBoolean("Goalie.useSoonest",useSoonest);
    }
    
    public boolean isIgnoreHand() {
        return ignoreHand;
    }
    
    /** Set true to ignore ball clusters that have a size that is too large - these are probably hands
     * @param ignoreHand true to ignore balls that are large
     */
    public void setIgnoreHand(boolean ignoreHand) {
        this.ignoreHand = ignoreHand;
    }
    
//    public boolean isShowServoControl() {
//        return showServoControl;
//    }
    
//    /** When set true, shows the servo test gui
//     @param showServoControl true to show, false to hide
//     */
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
        getPrefs().putInt("Goalie.topRowsToIgnore",topRowsToIgnore);
    }
    
    public int getArmRows() {
        return armRows;
    }
    
    /** Defines the number of retina rows from bottom of image to tip of goalie arm to define tracking region for arm.
     *If this is too small then the arm will be tracked as balls, greatly confusing things.
     * Constrained to range 0-chip.getSizeY()/2.
     * @param pixelsToEdgeOfGoal the number of rows of pixels
     */
    public void setArmRows(int armRows) {
        if(armRows<0) armRows=0; else if(armRows>chip.getSizeY()/2) armRows=chip.getSizeY()/2;
        this.armRows = armRows;
        ((XYTypeFilter) tracker.getEnclosedFilter()).setStartY(armRows);
        servoArm.setCaptureRange(0, 0, chip.getSizeX(), armRows);
        
        getPrefs().putInt("Goalie.pixelsToEdgeOfGoal",armRows);
    }
    
//    public int getGoalieArmRows() {
//        return goalieArmRows;
//    }
//
    /** These rows at bottom of image are ignored for acquiring new balls - they are assumed to come from seeing
     * our own arm.
     * @param goalieArmRows the number of retina rows at bottom of image to ignore for acquisition of new balls. Constrained to
     * 0-chip.getSizeY()/2.
     */
//    public void setGoalieArmRows(int goalieArmRows) {
//        if(goalieArmRows<0) goalieArmRows=0; else if(goalieArmRows>chip.getSizeY()/2) goalieArmRows=chip.getSizeY()/2;
//        this.goalieArmRows = goalieArmRows;
//        getPrefs().putInt("Goalie.goalieArmRows",goalieArmRows);
//    }
////
//    public int getGoalieArmTauMs() {
//        return goalieArmTauMs;
//    }
    
    /** Sets the lowpass time constant for the goalie arm position signal. This helps prevent the arm from overshooting its mark
     * by limiting the rate of change of the control signal.
     * @param goalieArmTauMs the first order time constant in ms
     */
//    public void setGoalieArmTauMs(int goalieArmTauMs) {
//        if(goalieArmTauMs<1) goalieArmTauMs=1;
//        this.goalieArmTauMs = goalieArmTauMs;
//        getPrefs().putInt("Goalie.goalieArmTauMs",goalieArmTauMs);
//        goalieFilter.setTauMs(goalieArmTauMs);
//    }
    
    /** @return the delay before learning starts */
    public int getLearnDelayMS() {
        return (int)learnDelayMS;
    }
    
    public void setLearnDelayMS(int learnDelayMS) {
        getPrefs().putLong("Goalie.learnTimeMs", (long)learnDelayMS);
        this.learnDelayMS = (long)learnDelayMS;
    }
    
    public void update(Observable o, Object arg) {
        servoArm.setCaptureRange(0, 0, chip.getSizeX(), armRows); // when chip changes, we need to set this
    }
    
    
    public void doResetLearning(){
        servoArm.resetLearning();
    }
    
    public void doLearn() { // since "do"Learn automatically added to GUI
        servoArm.startLearning();
    }
    
    public void doRelax() { // automatically built into GUI for Goalie
        servoArm.relax();
    }
    
    public int getPixelsToTipOfArm() {
        return pixelsToTipOfArm;
    }
    
    /** Defines the distance in image rows to the tip of the goalie arm. This number is slightly different
     *than the armRows parameter because of perspective.
     *@param pixelsToTipOfArm the number of image rows to the tip
     */
    public void setPixelsToTipOfArm(int pixelsToTipOfArm) {
        if(pixelsToTipOfArm>chip.getSizeY()/2) pixelsToTipOfArm=chip.getSizeY()/2;
        this.pixelsToTipOfArm = pixelsToTipOfArm;
        getPrefs().putInt("Goalie.pixelsToTipOfArm",pixelsToTipOfArm);
    }
    
    /** Returns the ball tracker */
    public RectangularClusterTracker getTracker(){
        return tracker;
    }
    
    
    
}
