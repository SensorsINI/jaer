/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.awt.Font;

import com.jogamp.opengl.GLAutoDrawable;
import javax.swing.JOptionPane;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.TobiLogger;

import com.jogamp.opengl.util.awt.TextRenderer;
/**
 * Controls virtual slot cars on designed or extracted tracks.
 *
 * @author Michael Pfeiffer
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
@Description("Slot car racer for virtual cars and tracks")
public class VirtualSlotcarRacer extends EventFilter2D implements FrameAnnotater{

	private boolean showTrackEnabled = prefs().getBoolean("VirtualSlotcarRacer.showTrack",true);
	private boolean virtualCarEnabled = prefs().getBoolean("VirtualSlotcarRacer.virtualCar",false);

	private TobiLogger tobiLogger;

	// The virtual race track
	private RacetrackFrame raceTrack = null;

	// The designer of race tracks
	private SlotcarFrame trackDesigner = null;

	// Filter for extracting tracks from retina input
	private TrackDefineFilter trackExtractor = null;

	private CarTracker carTracker;
	private FilterChain filterChain;
	private boolean overrideThrottle = prefs().getBoolean("VirtualSlotcarRacer.overrideThrottle",true);
	private float overriddenThrottleSetting = prefs().getFloat("VirtualSlotcarRacer.overriddenThrottleSetting",0);
	//    private SlotCarController controller = null;
	private SlotcarTrack trackModel;
	private TextRenderer renderer;
	private SimpleSpeedController speedController;
	private Slotcar throttleController;
	private float maxThrottle=prefs().getFloat("VirtualSlotcarRacer.maxThrottle",1);


	// Where does the track come from? Designer, loaded, extracted
	private int trackOrigin;

	private final int TRACK_DESIGNED = 0;    // Use track from designer
	private final int TRACK_LOADED = 1;      // Use a loaded track
	private final int TRACK_EXTRACTED = 2;   // Use a track extracted from a filter
	private final int TRACK_UNDEFINED = -1;  // Track is currently undefined

	private float displayStepSize = 0.001f;


	public VirtualSlotcarRacer (AEChip chip){
		super(chip);
		trackDesigner = new SlotcarFrame();


		filterChain = new FilterChain(chip);

		carTracker = new CarTracker(chip);
		filterChain.add(carTracker);

		speedController = new SimpleSpeedController(chip);
		filterChain.add(speedController);

		// The virtual car accepts the throttle commands
		throttleController = new Slotcar(null);

		setEnclosedFilterChain(filterChain);

		tobiLogger = new TobiLogger("VirtualSlotcarRacer","racer data "+speedController.logContents());

		// tooltips for properties
		String con="Controller", dis="Display", ov="Override", vir="Virtual car", log="Logging";
		setPropertyTooltip(con,"desiredSpeed","Desired speed from speed controller");
		setPropertyTooltip(ov,"overrideThrottle","Select to override the controller throttle setting");
		setPropertyTooltip(con,"maxThrottle","Absolute limit on throttle for safety");
		setPropertyTooltip(ov,"overriddenThrottleSetting","Manual overidden throttle setting");
		setPropertyTooltip(vir,"virtualCarEnabled","Enable display of virtual car on virtual track");
		setPropertyTooltip(log,"logRacerDataEnabled","enables logging of racer data");

		trackOrigin = TRACK_UNDEFINED;
	}

	public void doLearnTrack (){
		JOptionPane.showMessageDialog(chip.getAeViewer(),"I should start a TrackdefineFilter" +
			"and extract the track from there!");

		throttleController.setTrack(trackModel);
		trackOrigin = TRACK_EXTRACTED;
	}

	@Override
	public EventPacket<?> filterPacket (EventPacket<?> in){
		getEnclosedFilterChain().filterPacket(in);
		setThrottle();
		return in;
	}

	private ThrottleBrake lastThrottle=new ThrottleBrake();

	synchronized private void setThrottle() {

		if (isOverrideThrottle()) {
			lastThrottle.throttle = getOverriddenThrottleSetting();
		} else {
			lastThrottle = speedController.computeControl(carTracker, trackModel);
		}
		lastThrottle.throttle=lastThrottle.throttle>maxThrottle? maxThrottle:lastThrottle.throttle;

		if (throttleController != null) {
			// Pass current throttle to virtual racer
			throttleController.setThrottleValue(lastThrottle.throttle);
		}

		if (isLogRacerDataEnabled()) {
			logRacerData(speedController.logControllerState());
		}

	}

	@Override
	public void resetFilter() {
		// TODO: Do something with virtual race track
	}

	@Override
	public void initFilter (){
	}

	@Override
	public void annotate (GLAutoDrawable drawable){
		carTracker.annotate(drawable);
		speedController.annotate(drawable);
		if ( renderer == null ){
			renderer = new TextRenderer(new Font("SansSerif",Font.PLAIN,24),true,true);
		}
		renderer.begin3DRendering();
		String s="Throttle:"+lastThrottle;
		final float scale=.25f;
		renderer.draw3D(s,0,2,0,scale);
		//        Rectangle2D bounds=renderer.getBounds(s);
		renderer.end3DRendering();
		//        GL2 gl=drawable.getGL().getGL2();
		//        gl.glRectf((float)bounds.getMaxX()*scale, 2,(float) (chip.getSizeX()-scale*bounds.getWidth())*lastThrottle, 4);

		if (raceTrack != null) {
			System.out.println("Painting race track!");
			raceTrack.repaint();
		}
	}


	public void doDesignTrack() {
		trackDesigner.setVisible(true);
		trackOrigin = TRACK_DESIGNED;
	}

	public void doLoadTrack() {
		JOptionPane.showMessageDialog(chip.getAeViewer(),"I should load the track from a file!");
		trackOrigin = TRACK_LOADED;
	}

	public void doStartRace() {
		// Extract the track from the defined source
		switch (trackOrigin) {
			case TRACK_LOADED: {
				System.out.println("The track should already be loaded");
				break;
			}
			case TRACK_DESIGNED: {
				trackModel = trackDesigner.getTrack();
				break;
			}
			case TRACK_EXTRACTED: {
				if (trackExtractor != null) {
					trackModel = trackExtractor.getTrack();
				}
				else {
					System.out.println("WARNING: No track designer defined!");
				}
				break;
			}
			case TRACK_UNDEFINED: {
				System.out.println("WARNING: Cannot start race! No track defined!");
				return;
			}
		}

		if (trackModel == null) {
			System.out.println("WARNING: No valid track defined!");
			return;
		}
		if (trackModel.getNumPoints() < 3) {
			System.out.println("WARNING: Track has less than 3 points, cannot race!");
			return;
		}

		// Initialize car
		throttleController.setTrack(trackModel);

		// Start the car
		System.out.println("Starting the Slotcar now!");

		// TODO: Start a new thread for the car controller???


		// Initialize race display
		raceTrack = new RacetrackFrame();
		raceTrack.setTrack(trackModel, displayStepSize);
		raceTrack.setCar(throttleController);
		raceTrack.setVisible(true);
	}

	/**
	 * @return the overrideThrottle
	 */
	public boolean isOverrideThrottle (){
		return overrideThrottle;
	}

	/**
	 * @param overrideThrottle the overrideThrottle to copyFrom
	 */
	public void setOverrideThrottle (boolean overrideThrottle){
		this.overrideThrottle = overrideThrottle;
		prefs().putBoolean("VirtualSlotcarRacer.overrideThrottle", overrideThrottle);
	}

	/**
	 * @return the overriddenThrottleSetting
	 */
	public float getOverriddenThrottleSetting (){
		return overriddenThrottleSetting;
	}

	/**
	 * @param overriddenThrottleSetting the overriddenThrottleSetting to copyFrom
	 */
	public void setOverriddenThrottleSetting (float overriddenThrottleSetting){
		this.overriddenThrottleSetting = overriddenThrottleSetting;
		prefs().putFloat("VirtualSlotcarRacer.overriddenThrottleSetting",overriddenThrottleSetting);
	}

	// for GUI slider
	public float getMaxOverriddenThrottleSetting (){
		return 1;
	}

	public float getMinOverriddenThrottleSetting (){
		return 0;
	}

	/**
	 * @return the virtualCarEnabled
	 */
	public boolean isVirtualCarEnabled (){
		return virtualCarEnabled;
	}

	/**
	 * @param virtualCarEnabled the virtualCarEnabled to copyFrom
	 */
	public void setVirtualCarEnabled (boolean virtualCarEnabled){
		this.virtualCarEnabled = virtualCarEnabled;
		prefs().putBoolean("VirtualSlotcarRacer.virtualCarEnabled",virtualCarEnabled);

	}

	public synchronized void setLogRacerDataEnabled(boolean logDataEnabled) {
		tobiLogger.setEnabled(logDataEnabled);
	}

	public synchronized void logRacerData(String s) {
		tobiLogger.log(s);
	}

	public boolean isLogRacerDataEnabled() {
		if(tobiLogger==null) {
			return false;
		}
		return tobiLogger.isEnabled();
	}

	/**
	 * @return the maxThrottle
	 */
	public float getMaxThrottle() {
		return maxThrottle;
	}

	/**
	 * @param maxThrottle the maxThrottle to copyFrom
	 */
	public void setMaxThrottle(float maxThrottle) {
		if(maxThrottle>1) {
			maxThrottle=1;
		}
		else if(maxThrottle<0) {
			maxThrottle=0;
		}
		this.maxThrottle = maxThrottle;
		prefs().putFloat("VirtualSlotcarRacer.maxThrottle",maxThrottle);
	}

	public float getMaxMaxThrottle(){return 1;}
	public float getMinMaxThrottle(){return 0;}
}
