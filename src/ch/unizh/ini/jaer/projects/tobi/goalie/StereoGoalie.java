/*
 * Goalie.java
 *
 * Created on July 5, 2006, 1:17 PM
 * Cloned to StereoGoalie July 12, 2012 in Telluride to modify for Stereo vision
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 5, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.jaer.projects.tobi.goalie;

import java.awt.geom.Point2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.filter.RotateFilter;
import net.sf.jaer.eventprocessing.filter.XYTypeFilter;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.stereopsis.StereoClusterTracker;
import net.sf.jaer.util.StateMachineStates;

import org.ine.telluride.jaer.tell2011.head6axis.Head6DOF_ServoController;

import com.jogamp.opengl.util.gl2.GLUT;
import net.sf.jaer.DevelopmentStatus;

/**
 * Controls a servo arm in the way of a ball rolling towards a goal box.
 * Calibrates itself as well.
 * Uses stereo pair of DVS.
 *
 * @author tGoalielbruck/manuel lang
 */
@Description("Stereo Vision Binocular Goalie robot from Telluride 2012")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class StereoGoalie extends EventFilter2D implements FrameAnnotater, Observer{

	final String LOGGING_FILENAME="goalie.csv";
	private final int RELAXED_POSITION_DELAY_MS=100; // ms to getString to middle relaxed position
	private final String stateCat="State Criteria", armCat="Arm control", geomCat="Geometry", ballCat="Ball";
	private boolean useVelocityForGoalie=getPrefs().getBoolean("Goalie.useVelocityForGoalie",true);
	{setPropertyTooltip(armCat, "useVelocityForGoalie","uses ball velocity to calc impact position");}
	private int minPathPointsToUseVelocity=getPrefs().getInt("Goalie.minPathPointsToUseVelocity",10);
	{setPropertyTooltip(ballCat,"minPathPointsToUseVelocity","only after path has this many points is velocity used to predict path");}
	private int maxYToUseVelocity=getPrefs().getInt("Goalie.maxYToUseVelocity",90);
	{setPropertyTooltip(ballCat,"maxYToUseVelocity","don't use ball velocity unless ball.location.y is less than this (prevents spastic movements in response to hands)");}

	private int relaxationDelayMs=getPrefs().getInt("Goalie.relaxationDelayMs",500);
	{setPropertyTooltip(stateCat,"relaxationDelayMs","time [ms] before goalie first relaxes to middle after a movement. Goalie sleeps sleepDelaySec after this.\n");}
	private int sleepDelaySec=getPrefs().getInt("Goalie.sleepDelaySec",20);
	{setPropertyTooltip(stateCat,"sleepDelaySec","time [sec] before goalie sleeps");}
	private long learnDelayMS = getPrefs().getLong("Goalie.learnTimeMs",60000);
	{setPropertyTooltip(stateCat,"learnDelayMS","time [ms] of no balls present before a new learning cycle starts ");}
	private float wakeupBallDistance=getPrefs().getFloat("Goalie.wakeupBallDistance",.25f);
	{setPropertyTooltip(stateCat,"wakeupBallDistance","fraction of vertical image that ball must travel to wake up from SLEEP state");}
	private boolean logGoalieEnabled=getPrefs().getBoolean("Goalie.logGoalieEnabled",false);
	{setPropertyTooltip("logGoalieEnabled","(over)writes a file goalie.csv in startup folder (java) to log goalie action when Goalie is enabled");}
	private int maxPlayingTimeBeforeRestSec=getPrefs().getInt("Goalie.maxPlayingTimeBeforeRestSec",120);
	{setPropertyTooltip(stateCat,"maxPlayingTimeBeforeRestSec","goalie plays this long before demanding a rest of restIntervalSec between definite balls; prevents endless blocking to noise");}
	private int restIntervalSec=getPrefs().getInt("Goalie.restIntervalSec",15);
	{setPropertyTooltip(stateCat,"restIntervalSec","required interval between definite balls after rest started to exit sleep state");}


	private int armRows=getPrefs().getInt("Goalie.pixelsToEdgeOfGoal",40);
	{setPropertyTooltip(geomCat,"armRows","arm and ball tracking separation line position [pixels]");}
	private int pixelsToTipOfArm=getPrefs().getInt("Goalie.pixelsToTipOfArm",32);
	{setPropertyTooltip(geomCat,"pixelsToTipOfArm","defines distance in rows from bottom of image to tip of arm, used for computing arm position [pixels]");}
	private boolean useSoonest=getPrefs().getBoolean("Goalie.useSoonest",false); // use soonest ball rather than closest
	{setPropertyTooltip(ballCat,"useSoonest","react on soonest ball first");}
	private int topRowsToIgnore=getPrefs().getInt("Goalie.topRowsToIgnore",0); // balls here are ignored (hands)
	{setPropertyTooltip(geomCat,"topRowsToIgnore","top rows in scene to ignore for purposes of active ball blocking (balls are still tracked there)");}
	private int rangeOutsideViewToBlockPixels=getPrefs().getInt("Goalie.rangeOutsideViewToBlockPixels",10); // we only block shots that are this much outside scene, to avoid reacting continuously to people moving around laterally
	{setPropertyTooltip(geomCat,"rangeOutsideViewToBlockPixels","goalie will ignore balls that are more than this many pixels outside end of goal line");}
	private float parallaxFactor=getFloat("parallaxFactor",1.3f);
	{setPropertyTooltip(geomCat,"parallaxFactor","correct for goalie hand parallax (top of hand which is tracked for learning is closer than bottom which blocks ball)");}


	/** possible states,
     <ol>
     <li> ACTIVE meaning blocking ball we can see,
     <li> RELAXED is between blocks.
    <li> SLEEPING is after there have not been any definite balls for a while and we are waiting for a clear ball directed
    at the goal before we start blocking again. This reduces annoyance factor due to background mStatent at top of scene.
     <li> EXHAUSTED is when there has been ceaseless activity for too long, indicating reactions to noisy retina input
     </ol>
	 */
	public enum State {ACTIVE, RELAXED, SLEEPING, EXHAUSTED}
	protected class GoalieState extends StateMachineStates{
		State state=State.SLEEPING;
		@Override
		public Enum getInitial(){
			return State.SLEEPING;
		}
	}
	private GoalieState state=new GoalieState();

	//Arm control
	protected ServoArm servoArm;
	private XYTypeFilter xYFilter;

	//FilterChain for GUI
	FilterChain trackingFilterChain;

	StereoClusterTracker tracker;
	protected volatile StereoClusterTracker.Cluster ball=null;
	GoalieTableFilter tableFilter=null;

	final Object ballLock = new Object();
	private long lastServoPositionTime=0; // used to relax servos after inactivity
	private float lastBallCrossingX=Float.NaN; // used for logging
	long lastDefiniteBallTime=0;            // last time ball was definitely seen
	private int checkToRelax_state = 0;     // this is substate during relaxation to middle to goal

	/**
	 * Creates an instance of Goalie
	 */
	public StereoGoalie(AEChip chip) {
		super(chip);
		chip.addObserver(this);

		//build hierarchy
		trackingFilterChain = new FilterChain(chip);
		tracker=new StereoClusterTracker(chip);
		servoArm = new ServoArm(chip);
		xYFilter = new XYTypeFilter(chip);
		tableFilter=new GoalieTableFilter(chip);

		trackingFilterChain.add(new Head6DOF_ServoController(chip));
		trackingFilterChain.add(new RotateFilter(chip));
		trackingFilterChain.add(new BackgroundActivityFilter(chip));
		trackingFilterChain.add(tableFilter);
		trackingFilterChain.add(tracker);
		trackingFilterChain.add(servoArm);
		setEnclosedFilterChain(trackingFilterChain);
		tracker.setEnclosedFilter(xYFilter);
		tracker.setEnclosed(true, this);
		servoArm.setEnclosed(true, this);
		xYFilter.setEnclosed(true, tracker);

		// only top filter

		xYFilter.setXEnabled(true);
		xYFilter.setYEnabled(true);
		xYFilter.setTypeEnabled(false);
		xYFilter.setStartY(armRows);


		servoArm.initFilter();
		servoArm.setCaptureRange(0,0, 128, armRows);

		initFilter();
	}


	@Override
	synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
		if(!isFilterEnabled()) {
			return in;
		}
		getEnclosedFilterChain().filterPacket(in);
		servoArm.filterPacket(in);

		synchronized (ballLock) {
			ball=getPutativeBallCluster(); // whether ball is returned also depends on state, if sleeping, harder to getString one
			checkAndSetState(ball);
		}
		// not enough time has passed to relax and we might have a ball
		if(ball==null){
			lastBallCrossingX=Float.NaN;
		}else if(ball.isVisible()){ // check for null because ball seems to disappear on us when using processOnAcquisition mode (realtime mode)
			// we have a ball, so move servo to position needed.
			//          if(JAERViewer.globalTime1 == 0)
			//          JAERViewer.globalTime1 = System.nanoTime();


			lastBallCrossingX=computeRequiredArmPosition(ball);
			if((lastBallCrossingX>=-rangeOutsideViewToBlockPixels)&&(lastBallCrossingX<=(chip.getSizeX()+rangeOutsideViewToBlockPixels))){
				// only block balls that are blockable....
				setState(State.ACTIVE); // we are about to move the arm
				servoArm.setPosition((int)lastBallCrossingX);
				lastServoPositionTime=System.currentTimeMillis();
				checkToRelax_state=0;   //next time idle move arm back to middle
			}
		}
		logGoalie(in);
		return in;
	}

	/** Returns required hand position in pixels (0=left side)
	 *
	 * @param ball
	 * @return
	 */
	private float computeRequiredArmPosition(StereoClusterTracker.Cluster ball){
		// compute intersection of velocityPPT vector of ball with bottom of view.
		// this is the place we should putString the goalie.
		// this is computed from time to reach bottom (y/vy) times vx plus the x location.
		// we also include a parameter pixelsToTipOfArm which is where the goalie arm is in the field of view,
		// and a parallax correction factor for the learned position of the hand.
		if(ball==null) {
			throw new RuntimeException("null ball, shouldn't happen");
		}
		float x=ball.location.x;
		if(ball.getLocation().getY()>maxYToUseVelocity)
		{
			return x; // if ball is too far away, don't use ball velocityPPT
		}
		if(useVelocityForGoalie && ball.isVelocityValid() && (ball.getPath().size()>=minPathPointsToUseVelocity)){
			Point2D.Float v=ball.getVelocityPPS();
			double v2=(v.x*v.x)+v.y+v.y;
			if(v.y<0 /*&& v2>MIN_BALL_SPEED_TO_USE_PPS2*/){
				// don't use vel unless ball is rolling towards goal
				// we need minus sign here because vel.y is negative
				x-=((ball.location.y-pixelsToTipOfArm)/v.y)*v.x;
			}
		}
		return computeParallaxCorrection(x);
	}

	// corrects for parallax of learned vs. actual position of bottom of goalie hand. Since the top of the hand is tracked
	// for learning, the actual required position of the hand is the same when centered under the camera, but must
	// be larger when the hand is off center. E.g. if the top of the hand is half the distance to the camera as the bottom, then
	// the actual position must be 2 times further from the center, so that the bottom of the hand is at the proper position on
	// the table.
	private float computeParallaxCorrection(float ballPos){
		int sx2=chip.getSizeX()/2;
		return (parallaxFactor*(ballPos-sx2))+sx2;
	}

	/**
	 * Gets the putative ball cluster. This method applies rules to determine the most likely ball cluster.
	 * Returns null if there is no ball.
	 *
	 * @return ball with min y, assumed closest to viewer. This should filter out a lot of hands that roll the ball towards the goal.
	 *     If useVelocityForGoalie is true, then the ball ball must also be moving towards the goal. If useSoonest is true, then the ball
	 * that will first cross the goal line, based on ball y velocityPPT, will be returned. If goalie is sleeping, ball must have moved
     at least wakeupBallDistance towards goal or ball will be returned null.
	 */
	private StereoClusterTracker.Cluster getPutativeBallCluster(){
		if(tracker.getNumClusters()==0) {
			return null;
		}
		float minDistance=Float.POSITIVE_INFINITY, f, minTimeToImpact=Float.POSITIVE_INFINITY;
		StereoClusterTracker.Cluster closest=null, soonest=null;
		for(StereoClusterTracker.Cluster c:tracker.getClusters()){
			if( c.isVisible()){ // cluster must be visible
				if(!useSoonest){  // compute nearest cluster
					if((f=c.location.y) < minDistance ) {
						if( (!useVelocityForGoalie) || (useVelocityForGoalie && (c.getVelocityPPS().y<=0))){
							// give closest ball unconditionally if not using ball velocityPPT
							// but if using velocityPPT, then only give ball if it is moving towards goal
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

		StereoClusterTracker.Cluster returnBall;
		if(useSoonest) {
			returnBall= soonest;
		}
		else {
			returnBall= closest;
		}

		if((returnBall!=null) && (returnBall.location.y>(chip.getSizeY()-topRowsToIgnore))) {
			return null;
		}

		// if we are SLEEPING then don't return a ball unless it is definitely one
		// if we detect a definite ball then we remember the time we saw it and only sleep after
		//  SLEEP_DELAY_MS has passed since we saw a real ball
		if((returnBall!=null) && (/*returnBall.getLifetime()>DEFINITE_BALL_LIFETIME_US  &&*/ returnBall.getDistanceYFromBirth()< (-chip.getMaxSize()*getWakeupBallDistance()))){
			lastDefiniteBallTime=System.currentTimeMillis(); // we definitely got a real ball
		}else{
			if((getState()==State.SLEEPING) || (getState()==State.EXHAUSTED)) {
				returnBall=null;
			}
		}
		return returnBall;
	} // getPutativeBallCluster

	/** @return time in ms to impact of cluster with goal line
	 * @see #pixelsToEdgeOfGoal
	 */
	private float computeTimeToImpactMs(StereoClusterTracker.Cluster cluster){
		if(cluster==null){
			log.warning("passed null cluster to getTimeToImpactMs");
			return Float.POSITIVE_INFINITY;
		}
		float y=cluster.location.y;
		float dy=cluster.getVelocityPPS().y; // velocityPPT of cluster in pixels per second
		if(dy>=0) {
			return Float.POSITIVE_INFINITY;
		}
		dy=dy/(AEConstants.TICK_DEFAULT_US*1e-6f);
		float dt=(-1000f*(y-pixelsToTipOfArm))/dy;
		return dt;
	}

	/*private float applyGain(float f){
        return .5f+(f-.5f)*gain;
    }*/

	public Object getFilterState() {
		return null;
	}

	@Override
	public void resetFilter() {
		tracker.resetFilter();
	}

	/** initializes arm lowpass filter */
	@Override
	public void initFilter() {
		servoArm.setCaptureRange(0,0, chip.getSizeX(), armRows);
	}

	@Override synchronized public void setFilterEnabled(boolean yes){
		super.setFilterEnabled(yes);
		if(yes){
			////            if(chip.getAeViewer().getPlayMode()==AEViewer.PlayMode.LIVE){
			//            if(chip.getFilterChain().getProcessingMode()!=FilterChain.ProcessingMode.ACQUISITION){
			//                chip.getFilterChain().setProcessingMode(FilterChain.ProcessingMode.ACQUISITION);
			//                log.info("set filter chain to FilterChain.ProcessingMode.ACQUISITION for real time operation");
			////                JOptionPane.showMessageDialog(chip.getAeViewer().isVisible()?chip.getAeViewer():null,"set FilterChain.ProcessingMode.ACQUISITION for real time operation");
			//            }
			if(logGoalieEnabled) {
				startLogging();
			}

		}
		if(!yes){
			stopLogging();
		}
	}

	GLUquadric ballQuad;
	GLU glu;
	float[] ballColor=new float[3];

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if(!isFilterEnabled() ) {
			return;
		}
		tracker.annotate(drawable);
		//   if(isRelaxed) return;
		GL2 gl=drawable.getGL().getGL2();
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
		if(glu==null) {
			glu=new GLU();
		}
		if(ballQuad==null) {
			ballQuad = glu.gluNewQuadric();
		}
		gl.glColor3f(1,1,0);
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

		// show state of Goalie (arm shows its own state)
		gl.glPushMatrix();
		int font = GLUT.BITMAP_HELVETICA_18;
		gl.glRasterPos3f((chip.getSizeX() / 2)-15, 7,0);

		// annotate the cluster with the arm state, e.g. relaxed or learning
		chip.getCanvas().getGlut().glutBitmapString(font, getState().toString());

		gl.glPopMatrix();
	}

	public boolean isUseVelocityForGoalie() {
		return useVelocityForGoalie;
	}

	/** Sets whether the goalie uses the ball velocityPPT or just the position
	 * @param useVelocityForGoalie true to use ball velocityPPT
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
		getPrefs().putInt("Goalie.relaxationDelayMs",relaxationDelayMs);
	}


	/** Sets the state depending on ball and history */
	private void checkAndSetState(StereoClusterTracker.Cluster ball){
		// if enough time has passed AND there is no visible ball, then relax servo in  a series of steps
		// that initiate move arm to middle, wait for movement, and then turn off servo pulses
		long timeSinceLastPosition=System.currentTimeMillis()- lastServoPositionTime;


		// check for leaving exhaustion. goalie is exhausted when it has seen real balls continuously for maxPlayingTimeBeforeRestSec.
		// it then demands restIntervalSec of no real balls before it will play again.
		// this should help reduce arm damage when input is just noise because the lights are off or there is some problem with lighting
		if(getState()==State.EXHAUSTED){
			if((System.currentTimeMillis()-lastDefiniteBallTime)>(getRestIntervalSec()*1000)){
				setState(State.SLEEPING);
			}
		}

		if( (getState()==State.ACTIVE) &&  ((ball==null) || !ball.isVisible()) &&  (timeSinceLastPosition > relaxationDelayMs) && (checkToRelax_state == 0)  ){
			servoArm.setPosition(chip.getSizeX() / 2);
			setState(State.RELAXED);
			checkToRelax_state = 1;
		}

		// after relaxationDelayMs position to middle for next ball.
		if( (getState() == State.RELAXED) &&
			(timeSinceLastPosition > (relaxationDelayMs+RELAXED_POSITION_DELAY_MS)) // wait for arm to getString to middle
			&& (checkToRelax_state == 1)) {
			servoArm.relax(); // turn off servo (if it is one that turns off when you stop sending pulses)
			checkToRelax_state = 2;
		}


		// if we have relaxed to the middle and sufficient time has gone by since we got a ball, then we go to sleep state where its harder
		// to getString a ball
		if((getState()==State.RELAXED) && (checkToRelax_state==2) &&
			((System.currentTimeMillis()-lastDefiniteBallTime)>(sleepDelaySec*1000))){
			setState(State.SLEEPING);
		}

		// if we've been relaxed a really long time start recalibrating
		if( (getState() == State.SLEEPING) && (timeSinceLastPosition > getLearnDelayMS())) {
			servoArm.startLearning();
			lastServoPositionTime = System.currentTimeMillis();
		}
	}

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

	public int getTopRowsToIgnore() {
		return topRowsToIgnore;
	}

	public void setTopRowsToIgnore(int topRowsToIgnore) {
		if(topRowsToIgnore>chip.getSizeY()) {
			topRowsToIgnore=chip.getSizeY();
		}
		this.topRowsToIgnore = topRowsToIgnore;
		getPrefs().putInt("Goalie.topRowsToIgnore",topRowsToIgnore);
	}

	public int getArmRows() {
		return armRows;
	}

	/** Defines the number of retina rows from bottom of image to tip of goalie arm to define tracking region for arm.
	 *If this is too small then the arm will be tracked as balls, greatly confusing things.
	 * Constrained to range 0-chip.getSizeY()/2.
	 * @param armRows the number of rows of pixels
	 */
	public void setArmRows(int armRows) {
		if(armRows<0) {
			armRows=0;
		}
		else if(armRows>(chip.getSizeY()/2)) {
			armRows=chip.getSizeY()/2;
		}
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

	/** @return the delay before learning starts */
	public int getLearnDelayMS() {
		return (int)learnDelayMS;
	}

	public void setLearnDelayMS(int learnDelayMS) {
		getPrefs().putLong("Goalie.learnTimeMs", learnDelayMS);
		this.learnDelayMS = learnDelayMS;
	}

	@Override
	public void update(Observable o, Object arg) {
		servoArm.setCaptureRange(0, 0, chip.getSizeX(), armRows); // when chip changes, we need to set this
	}


	public void doResetLearning(){
		servoArm.resetLearning();
	}

	public void doLearn() { // since "do"Learn automatically added to GUI
		servoArm.setLearningFailed(false);
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
		if(pixelsToTipOfArm>(chip.getSizeY()/2)) {
			pixelsToTipOfArm=chip.getSizeY()/2;
		}
		this.pixelsToTipOfArm = pixelsToTipOfArm;
		getPrefs().putInt("Goalie.pixelsToTipOfArm",pixelsToTipOfArm);
	}

	/** Returns the ball tracker */
	public StereoClusterTracker getTracker(){
		return tracker;
	}

	public float getWakeupBallDistance() {
		return wakeupBallDistance;
	}

	public void setWakeupBallDistance(float wakeupBallDistance) {
		if(wakeupBallDistance>.7f) {
			wakeupBallDistance=.7f;
		}
		else if(wakeupBallDistance<0) {
			wakeupBallDistance=0;
		}
		this.wakeupBallDistance = wakeupBallDistance;
		getPrefs().putFloat("Goalie.wakeupBallDistance",wakeupBallDistance);
	}

	public int getSleepDelaySec() {
		return sleepDelaySec;
	}

	public void setSleepDelaySec(int sleepDelaySec) {
		this.sleepDelaySec = sleepDelaySec;
		getPrefs().putInt("Goalie.sleepDelaySec",sleepDelaySec);
	}

	public XYTypeFilter getXYFilter() {
		return xYFilter;
	}

	public void setXYFilter(XYTypeFilter xYFilter) {
		this.xYFilter = xYFilter;
	}

	public int getRangeOutsideViewToBlockPixels() {
		return rangeOutsideViewToBlockPixels;
	}

	public void setRangeOutsideViewToBlockPixels(int rangeOutsideViewToBlockPixels) {
		if(rangeOutsideViewToBlockPixels<0) {
			rangeOutsideViewToBlockPixels=0;
		}
		this.rangeOutsideViewToBlockPixels = rangeOutsideViewToBlockPixels;
		getPrefs().putInt("Goalie.rangeOutsideViewToBlockPixels",rangeOutsideViewToBlockPixels);
	}

	File loggingFile=null;
	PrintWriter loggingWriter=null;
	long startLoggingTime;
	private void startLogging(){
		try {
			loggingFile=new File(LOGGING_FILENAME);
			loggingWriter=new PrintWriter(new BufferedOutputStream(new FileOutputStream(loggingFile)));
			startLoggingTime=System.nanoTime();
			loggingWriter.println("# goalie logging started "+new Date());
			loggingWriter.println("# timeNs, ballx, bally, armDesired, armActual, ballvelx, ballvely, lastBallCrossingX, lastTimestamp, eventRateHz, numEvents");
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void stopLogging(){
		if(loggingWriter!=null){
			loggingWriter.close();
			loggingWriter=null;
		}
	}

	private void logGoalie(EventPacket<?> in){
		if(loggingWriter==null) {
			return;
		}
		long t=System.nanoTime()-startLoggingTime;
		float ballx=Float.NaN, bally=Float.NaN, ballvelx=Float.NaN, ballvely=Float.NaN;
		if(ball!=null) {
			ballx=ball.getLocation().x;
			bally=ball.getLocation().y;
			ballvelx=ball.getVelocityPPS().x;
			ballvely=ball.getVelocityPPS().y;
		}
		loggingWriter.format("%d, %f, %f, %d, %d, %f, %f, %f, %d, %f, %d\n",
			t,
			ballx,
			bally,
			servoArm.getDesiredPosition(),
			servoArm.getActualPosition(),
			ballvelx,
			ballvely,
			lastBallCrossingX,
			in.getLastTimestamp(),
			in.getEventRateHz(),
			in.getSize()
			);
	}

	@Override
	protected void finalize() throws Throwable {
		stopLogging();
	}

	public int getMinPathPointsToUseVelocity() {
		return minPathPointsToUseVelocity;
	}

	public void setMinPathPointsToUseVelocity(int minPathPointsToUseVelocity) {
		this.minPathPointsToUseVelocity = minPathPointsToUseVelocity;
		getPrefs().putInt("Goalie.minPathPointsToUseVelocity",minPathPointsToUseVelocity);
	}

	public int getMaxYToUseVelocity() {
		return maxYToUseVelocity;
	}

	public void setMaxYToUseVelocity(int maxYToUseVelocity) {
		if(maxYToUseVelocity>chip.getSizeY()) {
			maxYToUseVelocity=chip.getSizeY();
		}
		this.maxYToUseVelocity = maxYToUseVelocity;
		getPrefs().putInt("Goalie.maxYToUseVelocity",maxYToUseVelocity);
	}

	public Enum getState() {
		return state.get();
	}

	/** Sets the state. On transition to ACTIVE the tracker is reset */
	public void setState(State newState) {
		// if current state was sleeping and new state is active then reset the arm tracker to try to unstick
		// it from noise spikes
		if((getState()==State.SLEEPING) && (newState==State.ACTIVE)){
			servoArm.getArmTracker().resetFilter(); //
		}
		state.set(newState);
	}

	public boolean isLogGoalieEnabled() {
		return logGoalieEnabled;
	}

	public void setLogGoalieEnabled(boolean logGoalieEnabled) {
		this.logGoalieEnabled = logGoalieEnabled;
		getPrefs().putBoolean("Goalie.logGoalieEnabled",logGoalieEnabled);
		if(logGoalieEnabled){
			startLogging();
		}else{
			stopLogging();
		}
	}

	public int getMaxPlayingTimeBeforeRestSec() {
		return maxPlayingTimeBeforeRestSec;
	}

	public void setMaxPlayingTimeBeforeRestSec(int maxPlayingTimeBeforeRestSec) {
		this.maxPlayingTimeBeforeRestSec = maxPlayingTimeBeforeRestSec;
		getPrefs().putInt("Goalie.maxPlayingTimeBeforeRestSec",maxPlayingTimeBeforeRestSec);
	}

	public int getRestIntervalSec() {
		return restIntervalSec;
	}

	public void setRestIntervalSec(int restIntervalSec) {
		this.restIntervalSec = restIntervalSec;
		getPrefs().putInt("Goalie.restIntervalSec",restIntervalSec);
	}

	/**
	 * @return the parallaxFactor
	 */
	public float getParallaxFactor() {
		return parallaxFactor;
	}

	/**
	 * @param parallaxFactor the parallaxFactor to set
	 */
	public void setParallaxFactor(float parallaxFactor) {
		this.parallaxFactor = parallaxFactor;
		putFloat("parallaxFactor",parallaxFactor);
	}

}
