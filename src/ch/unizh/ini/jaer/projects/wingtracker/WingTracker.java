/**
 * WingTracker.java
 *
 * Created on 9. Juni 2006, 12:41
 * @author Janick Cardinale, INFK, ETHZ
 * @version 1.0
 */


package ch.unizh.ini.jaer.projects.wingtracker;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
//import ch.unizh.ini.caviar.util.PreferencesEditor;
import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLException;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.Matrix;

import com.jogamp.opengl.util.gl2.GLUT;
/**
 * Tracks a fruit fly wing beat in two different ways, after a initialization phase. Begin with the method track, there the events are
 * classified to the right edge and depending on the state the evaluation goes on.
 * So there are different states in this "filter":
 *
 * initialization: data are recorded(a hardcoded number of events in the method track() with state = Init) and
 * then an analysis is done for this data. First
 * there is a 2-means algorithm(method kmeans) to localize the 2 wings. afterwards basic geometry is used to calculate the
 * bodyposition and the heading of the fly. This is done in the method findFly().
 *
 * Tracking: every event changes the actual position of the correspoinding wingedge with a lowpassfilter. This is done in
 * the method track() with the state = TRACKING.
 *
 * Kalman: the second way to track is with a extended Kalman Filter. Every event is taken as an measurement for the filter.
 * First in the method track the events is classified in left/right wing and leading/trailing edge. There are 4 instances of the
 * inner class EKF which supports data for each wingedge. The prediction and update are methods of this inner class.
 */
@Description("<html> Tracks a fruit fly wing beat in two different ways, after a initialization phase. <br> Begin with the method track, there the events are classified to the right edge and <br>depending on the state the evaluation goes on.o there are different states in this filter: <br>initialization: data are recorded(a hardcoded number of events in the method track() with state = Init) and then an <br>analysis is done for this data. First there is a 2-means algorithm(method kmeans) to <br>localize the 2 wings. afterwards basic geometry is used to calculate the bodyposition and the heading of the fly. <br>This is done in the method findFly().  <p>Tracking: every event changes the actual position of the correspoinding wingedge with a lowpassfilter. <br>This is done in the method track() with the state = TRACKING.  <br>Kalman: the second way to track is with a extended Kalman Filter. <br>Every event is taken as a measurement for the filter. <br>First in the method track the events is classified in left/right wing and leading/trailing edge. <br>There are 4 instances of the inner class EKF which supports data for each wingedge. <br>The prediction and update are methods of this inner class.")
public class WingTracker extends EventFilter2D implements FrameAnnotater, Observer{//, PreferenceChangeListener {

	AEChip chip;
	AEChipRenderer renderer;
	GLUT glut;

	/*
	 *The variable inicates in which state we are. next state is to indicate what state it follows after init.
	 */
	enum State {INITIAL,  TRACKING, KALMAN};
	State state = State.INITIAL;
	State nextState = State.TRACKING;

	/*
	 *This to parameters are for the init-phase:
	 */
	final private int eventsToInit = 2000;//nb of events to buffer for analysis
	final private int iterationsOfKMeans = 20;//nb of interations of the k-means algorithm to detect the wing-means


	private Point2D.Float body; //the original(mean between prototypes) body-position.
	private Point2D.Float bodyOffset = new Point2D.Float(0f,0f); //offset to original body position, corrected by human
	/*
	 *The following 2 variables are not stored in rads but in points. They indicate the current mean of one wing. Their
	 *initial position they getString from the k-means algo. After a whole wingbeat they are updated to the new mean of all events
	 *in the searchrange
	 */
	private Point2D.Float prototypeL;
	private Point2D.Float prototypeR;


	private float heading;//the heading is the overall heading of the fly in radians in the unit circle with center at the body.
	/*
	 *The following variables are in radians(except the freq.) in the unit circle with x-axis=heading and the center at body. So
	 *the left parameters are always <PI.
	 */
	private float positionLeft, positionRight, amplitudeLeft, amplitudeRight, frequenceLeft, frequenceRight;
	private float leftLeadingEdge, leftTrailingEdge, rightLeadingEdge, rightTrailingEdge;
	private float centroidLeft, centroidRight;//the same as prototypes, but in radians
	//the searchRange is the doubled distance from body to a prototype. It indicates which events are used for calculation
	private float searchRange;

	//if auto-detection of the heading fails, one can flip the heading manually, should not be done while Kalmanfiltering
	private boolean flipHeading = getBoolean("WingTracker.flipHeading",false);
	//if the searchRange is to small, one can increase it by hand with a additional offset
	private float searchRangeOffset = getFloat("WingTracker.searchRangeOffset",0);
	//the hysteresis is used for the TRACKING state for updating the frequency and amplitude ->see doParamUpdate()
	private float hysteresis = getFloat("WingTracker.hysteresis",(float)(Math.PI/180)*10f);
	//the mixing factor is the parameter of the low-pass filter, indicates how a single event influence the track.
	private float mixingFactor =  getFloat("WingTracker.mixingFactor",0.1f);
	//one can do a log->in the std. home directory there will be a txt file created.
	private boolean doLog = getBoolean("WingTracker.doLog",false);
	//the prototypes are updated each wing-beat. so with this option on, this is also done with the body. (if there was
	//a correction by a mouse click, this correction is stored and added to the new mean position of the prototypes)
	private boolean doBodyUpdate = getBoolean("WingTracker.doBodyUpdate",true);
	//The prototypes are updated each wing beat. with this option on, the heading (orthogonal to the line between the
	//prototypes) is updated too.
	private boolean doHeadingUpdate = getBoolean("WingTracker.doHeadingUpdate",true);
	//changes the state to KALMAN, if false-> state = TRACKING (e.g.low-pass filtering)
	private boolean useKalmanFiltering = getBoolean("WingTracker.useKalmanFiltering",false);
	//this is a modified checkbox and should be in reality a button, just to show the EKFParameterwindow, if one closed it
	private boolean showEKFParameterWindow = getBoolean("WingTracker.showEKFParameterWindow",false);
	//this parameter is for KALMAN only. If it is too slow, one can increase this number a little bit. Then events are
	//buffered and averaged( a sort of prefiltering) before a new update of the EKF is invoked.
	private int nbEventsToCollectPerEdge = getInt("WingTracker.nbEventsToCollectPerEdge",1);
	//the EKF-instances, for each wing-edge there is one.

	private EKF LLE,RLE,LTE,RTE;//leftleadingedge EKF

	private BufferedWriter logWriter;//to do the log
	final String nl = System.getProperty("line.separator");//gets the line separator of the current system (for the log)

	public WingTracker(AEChip chip) {
		super(chip);
		this.chip=chip;
		renderer=chip.getRenderer();
		setPropertyTooltip("nbEventsToCollectPerEdge", "this parameter is for KALMAN only. If it is too slow, one can increase this number a little bit. Then events are buffered and averaged( a sort of prefiltering) before a new update of the EKF is invoked");
		setPropertyTooltip("showEKFParameterWindow", "this is a modified checkbox and should be in reality a button, just to show the EKFParameterwindow, if one closed it");
		setPropertyTooltip("useKalmanFiltering", "changes the state to KALMAN, if false-> state = TRACKING (e.g.low-pass filtering)");
		setPropertyTooltip("doHeadingUpdate", "The prototypes are updated each wing beat. with this option on, the heading (orthogonal to the line between the prototypes) is updated too.");
		setPropertyTooltip("doBodyUpdate", "the prototypes are updated each wing-beat. so with this option on, this is also done with the body. (if there was a correction by a mouse click, this correction is stored and added to the new mean position of the prototypes)");
		setPropertyTooltip("doLog", "one can do a log->in the std. home directory there will be a txt file created.");
		setPropertyTooltip("mixingFactor", "the mixing factor is the parameter of the low-pass filter, indicates how a single event influence the track.");
		setPropertyTooltip("hysteresis", "the hysteresis is used for the TRACKING state for updating the frequency and amplitude ->see doParamUpdate()");
		setPropertyTooltip("searchRangeOffset", "if the searchRange is to small, one can increase it by hand with a additional offset");
		setPropertyTooltip("flipHeading", "if auto-detection of the heading fails, one can flip the heading manually, should not be done while Kalmanfiltering");
		setPropertyTooltip("doShowEKFParameterWindow", "Shows the parameters for the Kalman filters");
		initFilter();
		chip.addObserver(this);
		//        prefs.addPreferenceChangeListener(this);
		glut = chip.getCanvas().getGlut();
		/*
		 *We need to add a mouse listener to the canvas of the chip. with a mouse click on can set the body postion of
		 *the fly.
		 */
		chip.getCanvas().getCanvas().addMouseListener(new MouseAdapter(){
			@Override
			public void mouseClicked(MouseEvent e){
				if(e.getButton() == MouseEvent.BUTTON1){
					if(body == null)
					{
						return;// if the init-phase is not yet done
					}
					Point p = getPixelFromMouseEvent(e);
					//the offset to the original body-position(which is the mean of the two prototypes is stored.
					Point2D.Float m = meanPoints(prototypeR,prototypeL);
					bodyOffset.x = p.x - m.x;
					bodyOffset.y = p.y - m.y;
					//the body position is set to the new position
					body.x = p.x;
					body.y = p.y;
					//after the mouse click, the edges has to be reinitialized. Otherwise it is possible that the acual
					//positions are very unlikely and they diverge.
					initWingEdges();
					//also for the KALMAN tracker, the search range should be adapted. It can even happen,that for the
					//TRACKING state after mouseclick the edges do not cross anymore->no update->no cross->etc.
					searchRange = (float)(body.distance(prototype1)+body.distance(prototype2));
				}
			}
		});
		//set the next state (TRACKING is default)
		setNextState();
		//this is just for not recording a lot of data if it is not deactivated by human
		setDoLog(false);
		setFlipHeading(false);

	}
	/**
	 *this method looks in the prefs if KalmanFiltering is activated, according to this it sets the next state(after init)
	 */
	private void setNextState(){
		if(useKalmanFiltering) {
			nextState = State.KALMAN;
		}
		else {
			nextState = State.TRACKING;
		}
	}
	private Point getPixelFromMouseEvent(MouseEvent e){
		//getString the right pixel from the canvas
		Point p = chip.getCanvas().getPixelFromMouseEvent(e);
		return p;
	}

	//just static variables
	final PolarityEvent.Polarity On=PolarityEvent.Polarity.On;
	final PolarityEvent.Polarity Off=PolarityEvent.Polarity.Off;

	//counts the events to record the events for the init-analysis
	private int eventCounter = 0;
	//this two buffers store the events while one wing beat, they are used to update the prototypes. After one wingbeat
	//they are cleared.and the story begins new. The counters are for the same reason. This is all done in updateParams();
	private Point2D.Float leftBuffer = new Point2D.Float(0,0);
	private Point2D.Float rightBuffer = new Point2D.Float(0,0);
	private int rightBufferCount = 0,leftBufferCount = 0;
	/*
	 *These buffers and counters are for collecting events before invoke the kalman filter. the events are averaged and
	 *then the kalman filter is invoked
	 */
	private float lleBuffer = 0f; private int lleEventCount = 0;
	private float lteBuffer = 0f; private int lteEventCount = 0;
	private float rleBuffer = 0f; private int rleEventCount = 0;
	private float rteBuffer = 0f; private int rteEventCount = 0;
	//the initMask serves for decreasing the number of events for the k-Means algorithm. Every pixel which gets an event has
	//an true entry in the initMask. The k-means algorithm works then only on these pixel.
	private boolean[][] initMask;
	//the parameterwindow for the Kalman Filter
	private EKFParameterWindow ekfpw;

	/**
	 *track() takes a packet of events and processes them depending on the state the tracker is currently. The method
	 *distinguish betweet 3 states: INITIAL, TRACKING, KALMAN. If the state is INITIAL, the data are recorded, till enough
	 *data are recorded. Afterwards a few analysis is done to find the fly and do geometry. If the state is TRACKING or KALMAN
	 *with every event the parameters are updated.
	 *The method provides also to write a logFile(with parameterstatus) in a txt file in the home directory.
	 *@param ae A packet of BasicEvents
	 */
	synchronized private void track(EventPacket<? extends BasicEvent> ae){
		if(state == State.INITIAL){//we are in the init phase
			if(initMask == null){ //the initMaskk has to be initialized as false(every entry),this is done once(per reset)
				initMask = new boolean[chip.getSizeX()][chip.getSizeY()];
				for(int i = 0; i < chip.getSizeX(); i++){
					for(int j = 0; j < chip.getSizeY();j++) {
						initMask[i][j] = false;
					}
				}
			}
			if(eventCounter >= eventsToInit){ //now we recorded enough data, the analysis begins
				kMeans(); //first cluster in 2 classes. left and right wing.
				findFly();//find fly does some geometry and finds body position etc.
				initWingEdges();//just initialize the wing-edges and the centroids
				state = nextState;//we are finished, go to TRACKING or KALMAN
				eventCounter = 0;//if one would like to reset the tracker, this is set to 0

			}else{
				for(int n = 0; n < ae.getSize();n++) {
					if(!initMask[ae.getEvent(n).x][ae.getEvent(n).y]){//if the event is not recorded befor
						initPoints.add(new EventWingPair(ae.getEvent(n),WingType.Unknown));//record the event
						initMask[ae.getEvent(n).x][ae.getEvent(n).y] = true;//set the entry in the mask
					}
				}
			}
			eventCounter += ae.getSize();//increase the counter so we getString finished once
		}

		String logLine = new String();//this is a line which is appended by the bufferedWriter in the end of one iteration(per event)
		if(state == State.TRACKING){ //we are at the low-pass filtering
			Point2D.Float eventPoint=new Point2D.Float();
			float m1 = 1-mixingFactor;//just for performance
			//this variable represents the measurement(actually the event in radians w.r.t. the body-heading unit circle.
			float rads = 0f;

			for(BasicEvent o:ae){
				PolarityEvent ev=(PolarityEvent)o;
				eventPoint.x=ev.x;
				eventPoint.y=ev.y;
				//if the event is outside of the search range, continue with next event...
				if(body.distance(ev.x,ev.y) > (searchRange+searchRangeOffset)) {
					continue;
				}
				//each event is converted to radians in the body-heading unit-circle
				rads = radiansInHeadingCircle(eventPoint);
				if(rads < Math.PI ){//left or right wing; left is always < PI
					leftBuffer.x += ev.getX(); leftBuffer.y += ev.getY();
					leftBufferCount++;
					if(ev.polarity==Off){
						leftLeadingEdge = (m1*leftLeadingEdge) + (mixingFactor*rads); //here the filtering is done
						//the logLine is appended to a txt file by the BufferedWriter
						if(doLog){
							logLine = "1\t" + ev.getTimestamp() + "\t" + leftLeadingEdge + "\t" + frequenceLeft + "\t"+ amplitudeLeft;
						}
					}else{
						leftTrailingEdge = (m1*leftTrailingEdge) + (mixingFactor*rads);
						if(doLog){
							logLine = "2\t" + ev.getTimestamp() + "\t" + leftTrailingEdge+ "\t" + frequenceLeft + "\t"+ amplitudeLeft;;
						}
					}
					//for the parameterUpdate, we mean the edges from the left side, the frequency and the amplitude is
					//calculated then for this mean. Perhaps this is not a nice idea.
					positionLeft = (leftLeadingEdge+leftTrailingEdge)/2;
				}else{
					/*
					 *Here we are at the right side of the heading. Everything is analogous to the left side.
					 */
					rightBuffer.x+= ev.getX(); rightBuffer.y += ev.getY();
					rightBufferCount++;
					if(ev.polarity==Off){
						rightLeadingEdge = (m1*rightLeadingEdge) + (mixingFactor*rads);
						if(doLog){
							logLine = "3\t" + ev.getTimestamp() + "\t" + rightLeadingEdge+ "\t" + frequenceRight + "\t"+ amplitudeRight;;
						}
					}else{
						rightTrailingEdge = (m1*rightTrailingEdge) + (mixingFactor*rads);
						if(doLog){
							logLine = "4\t" + ev.getTimestamp() + "\t" + rightTrailingEdge;
						}
					}
					positionRight = (rightLeadingEdge+rightTrailingEdge)/2;
				}
				//the parameters(frequency,amplitude,centroids,prototypes) are updated
				if(doLog) {
					logLine = logLine + "\t" + rads;
				}
				updateParams(ev.timestamp);
			}
		}
		/*
		 *Here we are in the Kalman Filtering state
		 */
		if(state == State.KALMAN){
			Point2D.Float eventPoint=new Point2D.Float();
			float rads = 0f; //the measurement, or the event in radians
			for(BasicEvent o:ae){
				PolarityEvent ev=(PolarityEvent)o;
				eventPoint.x=ev.x;
				eventPoint.y=ev.y;

				//if the event is outside of the searchRange, we dont care. The searchRangeOffset is set by human.
				if(body.distance(ev.x,ev.y) > (searchRange+searchRangeOffset)) {
					continue;
				}
				//getString the radians of the event
				rads = radiansInHeadingCircle(eventPoint);
				if(rads < Math.PI ){//left or right wing
					/*
					 *here we are at the left side
					 */
					if(ev.polarity==Off){
						if(LLE == null) {
							LLE = new EKF("left leading edge",ev.timestamp,centroidLeft);
						}
						if(!LLE.getUseEdge())
						{
							continue;//if the usage- parameter in the EKF Parameterwindow is unabled, we are finished
						}

						lleEventCount++;//if we want to collect/average some events to increase the performance
						lleBuffer += rads;

						if(lleEventCount < nbEventsToCollectPerEdge){
							continue;//here we are not finished with collecting
						}
						rads = lleBuffer/nbEventsToCollectPerEdge; //the average is taken
						lleBuffer = 0f; lleEventCount = 0;
						//The kalmanfilter is invoked. Prediction and update phase:
						LLE.predict(ev.timestamp);
						LLE.update(rads);
						//Just to perform analysis, carry the lowpassfilter approach:
						//                        leftLeadingEdge = (1-mixingFactor)*leftLeadingEdge + mixingFactor*rads;
						//                        positionLeft = (leftLeadingEdge+leftTrailingEdge)/2;
						//                        updateParams(ev.timestamp);

						//The logLine is set. The logLine-String is appended to the logfile afterwards.
						if(doLog){
							logLine = "1\t" + ev.getTimestamp() + "\t" + LLE.getEdgeInRads() + "\t" +
								LLE.x[0] + "\t" + LLE.x[1] + "\t" + LLE.x[2] + "\t" + LLE.x[3] + "\t" + rads;
						}
					}else{
						/*
						 *The event seems to be affecting the left trailing edge. It's all analogous to the left leading edge.
						 */
						if(LTE == null) {
							LTE = new EKF("left trailing edge",ev.timestamp,centroidLeft);
						}
						if(!LTE.getUseEdge()) {
							continue;
						}

						lteEventCount++;
						lteBuffer += rads;
						if(lteEventCount < nbEventsToCollectPerEdge){
							continue;
						}
						rads = lteBuffer/nbEventsToCollectPerEdge;
						lteBuffer = 0f; lteEventCount = 0;
						leftTrailingEdge = ((1-mixingFactor)*leftTrailingEdge) + (mixingFactor*rads);
						positionLeft = (leftLeadingEdge+leftTrailingEdge)/2;
						updateParams(ev.timestamp);
						LTE.predict(ev.timestamp);
						LTE.update(rads);
						if(doLog){
							logLine = "2\t" + ev.getTimestamp() + "\t" + LTE.getEdgeInRads() + "\t" +
								LTE.x[0] + "\t" + LTE.x[1] + "\t" + LTE.x[2] + "\t" + LTE.x[3] + "\t" + rads;
						}
					}

				}else{
					/*
					 *We are now on the right side. every thing is analogous to the left side. For comments see above.
					 */
					if(ev.polarity==Off){
						if(RLE == null) {
							RLE = new EKF("right leading edge",ev.timestamp,centroidRight);
						}
						if(!RLE.getUseEdge()) {
							continue;
						}

						rleEventCount++;
						rleBuffer += rads;
						if(rleEventCount < nbEventsToCollectPerEdge){
							continue;
						}
						rads = rleBuffer/nbEventsToCollectPerEdge;
						rleBuffer = 0f; rleEventCount = 0;

						RLE.predict(ev.timestamp);
						RLE.update(rads);
						if(doLog){
							logLine = "3\t" + ev.getTimestamp() + "\t" + RLE.getEdgeInRads() + "\t" +
								RLE.x[0] + "\t" + RLE.x[1] + "\t" + RLE.x[2] + "\t" + RLE.x[3] + "\t" + rads;
						}

					}else{
						if(RTE == null) {
							RTE = new EKF("right trailing edge",ev.timestamp,centroidRight);
						}
						if(!RTE.getUseEdge()) {
							continue;
						}

						rteEventCount++;
						rteBuffer += rads;
						if(rteEventCount < nbEventsToCollectPerEdge){
							continue;
						}
						rads = rteBuffer/nbEventsToCollectPerEdge;
						rteBuffer = 0f; rteEventCount = 0;

						RTE.predict(ev.timestamp);
						RTE.update(rads);
						if(doLog){
							logLine = "4\t" + ev.getTimestamp() + "\t" + RTE.getEdgeInRads() + "\t" +
								RTE.x[0] + "\t" + RTE.x[1] + "\t" + RTE.x[2] + "\t" + RTE.x[3] + "\t" + rads;
						}
					}

				}
			}
		}
		/*
		 * if we are recording a text file, the BufferedWriter writes here the logLine string which was modified before
		 */
		if(doLog){
			try{
				if(logWriter != null){
					logWriter.write(logLine+nl);
				}
			}catch(IOException ioe){
				System.out.println(ioe.toString());
			}

		}
	}

	/*
	 * This class opens a little window with tabbed pane. A tab can be added with the method addTab
	 */
	final public class EKFParameterWindow extends JFrame{
		JTabbedPane tabPane = new JTabbedPane();
		public EKFParameterWindow(){
			super();
			setTitle("EKF Parameters");
			//setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
			setSize( 200, 400 );
			tabPane.setRequestFocusEnabled(false); // might help with java.lang.ArrayIndexOutOfBoundsException: 1 according to http://coding.derkeiler.com/Archive/Java/comp.lang.java.gui/2005-06/msg00352.html
			add(tabPane);
			setVisible( true );
		}
		public void addTab(String name, JPanel jp){
			tabPane.add(name,jp);
			pack();
		}
	}

	/**
	 * This class serves as data structure for the Kalman Filter for all the wing-edges.
	 * It supports also the calculations "predict" and "update". There is also an inner class. Each wing has its own ParamterPanel
	 * which is then sent to the EKFParameterwindow. In this Parameterpanel the variables of the EKF are displayed and can be changed
	 * by the user.
	 */
	final public class EKF{

		//if not this edge is should not be calculated, this option can be choosen in the Parameter Window for EKF
		private boolean useEdge = true;

		//stored last timeStep, this is used to calculate the deltaTime
		private int latestTimeStampOfLastStep;
		private float[][] F; //Transition Model(tranistion of x(t-1) -> x(t)
		private float[][] B; //maps the control vector on the state vector
		private float[][] Q; //variance matrix of normally distributed process noise
		private float[][] H; //observation model: maps x on z(state vector onto the observation)
		private float[][] R; //variance matrix of  normally distributed observation noise
		private float[][] Pp; //pred. error cov. matrix
		private float[][] P; //error cov. matrix
		private float[] x; //the state vector
		private float[] xp; //the predicted state vector
		private float[] z; // observation
		private float[] u; //control vector
		private float[] y; //error/residual of measurement resp. innovation

		/*
		 *There are 2 different dimensions. Our statevector consists of ( position, amplitude, phase, angular freq.).
		 *The measurement we getString is a one dimensional measurements in radians.
		 */
		final int dimStateVector = 4;
		final int dimMeasurement = 1;
		private float deltaTime; // used in the Transitionmatrix
		private float time; // just because of technical reasons. time is used in update and prediction method.
		float measurementVariance = 1000; //the init of the measurementVariance

		/*
		 *every edge has its own panel for parameters, the panel is then displayed in the class EKFParameterWindow.
		 *The class EKFParameterPanel is an innerclass of EKF, since every EKF has one.
		 */
		EKFParameterPanel ekfpp;
		String name; //the name of the EKF, for example "left leading edge"
		/**
		 *Create a EKF datastructure. The datastructure contains also a panel which contains all necessary information about
		 *current vectors and matrices.
		 *@param name The name of the EKF instance, for example: left leading edge. This is used to recognize the EKF in the supported panel if you have multiple instances.
		 *@param timeStampOfCreationTime time stamp in mikrosec. this is necessary for the first prediction step, delta T is calculated from the difference of this time and the first event.
		 *@param initPosition A guess or the true position of the tracked object.
		 */
		EKF(String name, int timeStampOfCreationTime, float initPosition){ //the constr. is called in track()
			latestTimeStampOfLastStep = timeStampOfCreationTime;
			initData(initPosition);
			this.name = name;
			ekfpp = new EKFParameterPanel(name);
			time = timeStampOfCreationTime*1e-6f;
		}

		/**
		 * This method init the data for the EKF. It just sets values to the arrays.
		 * @arguments initPosition An approximation of the position of the wing. can be the centroid for example.
		 */

		void initData(float initPosition){
			F = new float[dimStateVector][dimStateVector];
			Q = new float[dimStateVector][dimStateVector];
			B = new float[dimStateVector][dimStateVector];
			H = new float[dimMeasurement][dimStateVector];
			R = new float[dimMeasurement][dimMeasurement];
			Pp = new float[dimStateVector][dimStateVector];
			P = new float[dimStateVector][dimStateVector];
			xp = new float[dimStateVector];
			x = new float[dimStateVector];
			z = new float[dimMeasurement];
			u = new float[dimStateVector];
			y = new float[dimMeasurement];

			deltaTime = 0;//1 event is lost...
			//since we are little unsure about the initial position:
				Matrix.identity(P);
				P[1][1] = 0;
				P[3][3] = 1;
				P[2][3] = 1;
				P[3][2] = 1;

				x[0] = initPosition;//initial null phase = 90 deg
				x[1] = 1f; //initial amplitude of 57 deg
				x[2] = initPosition;//initial phase
				x[3] = 240*2f*(float)Math.PI; //initial freq. of 200???

				//this is necessary! if after initialization there is an event with the same timestamp as the creation date,
				//then xp would be 0 and an update will be done! This is consistent since if dt = 0, there is no update done on xp
				xp[0] = x[0];xp[1] = x[1];xp[2] = x[2];xp[3] = x[3];

				H[0][0] = 1; H[0][1] = (float)Math.sin(x[2]); H[0][2] = x[1]*(float)Math.cos(x[2]);H[0][3] = 0;

				Matrix.identity(F);
				Matrix.zero(R);
				R[0][0] = measurementVariance;
				Q[0][0] = 2f; Q[0][1] = 0f; Q[0][2] = 0f; Q[0][3] = 0f;
				Q[1][0] = 0f; Q[1][1] = 1f; Q[1][2] = 0f; Q[1][3] = 0f;
				Q[2][0] = 0f; Q[2][1] = 0f; Q[2][2] = .5f;Q[2][3] = 1f;
				Q[3][0] = 0f; Q[3][1] = 0f; Q[3][2] = 1f; Q[3][3] = 2f;


				z[0] = 0;
				//            System.out.println("Init:\n_________");
				//            System.out.println("H: ");Matrix.print(H);
				//            System.out.println("Q: ");Matrix.print(Q);
				//            System.out.println("R: ");Matrix.print(R);
				//            System.out.println("F: ");Matrix.print(F);
				//            System.out.println("x: ");Matrix.print(x);
				//            System.out.println("P: ");Matrix.print(P);
		}


		/**
		 *The prediction phase of the EKF-Algorithm. x and P are predicted.
		 *@param t the timestamp in microseconds of the next measurement.
		 */
		void predict(int t){
			//            System.out.println("Prediction:\n__________");

			int timeStamp = t;
			/*
			 *we should only do a prediction, if there is a new timestamp! Else the phase would increase
			 */
			if(timeStamp > latestTimeStampOfLastStep){
				deltaTime = (timeStamp - latestTimeStampOfLastStep)*1.e-6f;
				latestTimeStampOfLastStep = timeStamp; //stor the last timestamp
			}else{
				return; //the prediction was made before for this timestamp!
			}
			time = t*1e-6f;

			F[2][3] = deltaTime;

			xp =  Matrix.multMatrix(F,x);
			//            System.out.println("predicted x:");Matrix.print(xp);

			//Pp = F*P*F'+Q
			Pp = Matrix.addMatrix(Matrix.multMatrix(Matrix.multMatrix(F,P),Matrix.transposeMatrix(F)),Q);

			//            System.out.println("predicted P:");Matrix.print(Pp);
		}

		/**
		 *the update step of the EKF. The argument rads is just the measurement in radians.
		 *@param rads measurement in radians.
		 */
		void update(float rads){
			//            System.out.println("Update:\n__________");
			//y = z-H*xp
			y[0] = rads - (x[0] + (x[1]*(float)Math.sin(x[2])));

			//H, linearized at point xp
			H[0][0] = 1; H[0][1] = (float)Math.sin(x[2]); H[0][2] = xp[1]*(float)Math.cos(x[2]);H[0][3] = time*xp[0]*(float)Math.cos(x[2]);
			//            System.out.println("\nH: ");Matrix.print(H);

			//S = H*Pp*H'+R
			float[][] S = new float[dimMeasurement][dimMeasurement];
			float[][] STemp = new float[dimMeasurement][dimStateVector];
			float[][] STemp2 = new float[dimMeasurement][dimMeasurement];
			Matrix.multiply(H,Pp,STemp);
			Matrix.multiply(STemp,Matrix.transposeMatrix(H),STemp2); Matrix.add(STemp2,R,S);
			//            System.out.println("S: " + S[0][0]);

			//K = Pp*H'*inv(S)
			float[][] K = new float[dimStateVector][dimMeasurement];
			float[][] Ktemp = new float[dimStateVector][dimMeasurement];
			Matrix.multiply(Pp,Matrix.transposeMatrix(H),Ktemp);
			S[0][0] = 1/S[0][0];
			Matrix.multiply(Ktemp,S,K);
			//            System.out.println("\nThe new Kalman Gain:");Matrix.print(K);

			//P = (I-K*H)Pp
			float[][] I = new float[dimStateVector][dimStateVector];
			float[][] Ptemp = new float[dimStateVector][dimStateVector];
			Matrix.identity(I);
			Matrix.multiply(K,H,P);Matrix.subtract(I,P,Ptemp);Matrix.multiply(Ptemp,Pp,P);
			//            System.out.println("\nthe new P:\n");Matrix.print(P);

			//x = xp + K*y
			float[] xTemp = new float[dimStateVector];
			Matrix.multiply(K,y,xTemp);
			Matrix.add(xp,xTemp,x);
			//            System.out.println("new state vector x: "); Matrix.print(x);
		}

		/**
		 * The following function computes the objective function h.
		 *@return the actual track in radians of the filter
		 */
		public float getEdgeInRads(){
			return x[0]+(x[1]*(float)Math.sin(x[2]));
		}
		/**
		 *@param useEdge if you want to use this created instance of an EKF. If not,the calcuation and annotation is leaved out.
		 */
		public void setUseEdge(boolean useEdge){
			this.useEdge = useEdge;
		}
		/**
		 *return if the current instance of the EKF(wingedge) is calculated and annotated
		 */
		public boolean getUseEdge(){
			return useEdge;
		}
		/**
		 * The class supports a panel which contains all important parameters of the current EKF.
		 */
		final public class EKFParameterPanel extends JPanel{
			/**
			 *In this constructor a Panel with the Matrices Q, R,P and the state vector x is created. Also a checkbox to
			 *determine if the instance of the EKF should really be used. The matrices/vectors are displayed in JTable and
			 *their values are changable. If there is no ParameterWindow created per wingTracker, the parameterWindow is
			 *created new, else the panel is appended to the window as a tab.
			 *@param name The String is showed in the title of the Parameterwindow to recognize this instance of EKF(wingEdge)
			 */
			public EKFParameterPanel(String name){
				super();
				setLayout(new GridLayout(0,2,5,10));
				add(new Label("Use the " + name));
				JCheckBox cb1 = new JCheckBox("",true);
				cb1.addItemListener( new ItemListener() {
					@Override
					public void itemStateChanged( ItemEvent e ) {
						useEdge = !useEdge;
					}
				} );
				add(cb1);
				add(new Label("The process variance: Q = "));
				addMatrix(Q);
				add(new Label("The measurement variance: R = "));
				addMatrix(R);
				add(new Label("State Vector: x = "));
				addMatrix(x);
				add(new Label("Error cov.-matrix. P = "));
				addMatrix(P);

				if(ekfpw == null) {
					ekfpw = new EKFParameterWindow();
				}
				ekfpw.addTab(name,this);
			}
			/**
			 *If you want to add a additional Vector to the Pane.
			 *@param m The vector to add in the pane in form of a JTable
			 */
			public void addMatrix(float[] m){
				JTable t = new JTable(new VectorModel(m));

				this.add(t);

				setVisible(true);
				//this.repaint();
			}
			/**
			 *If you want to add a additional Matrix to the Pane.
			 *@param m The 2D Matrix to add in the pane in form of a JTable.
			 */
			public void addMatrix(float[][] m){
				JTable t = new JTable(new MatrixModel(m));
				t.setRowSelectionAllowed(true);
				t.setColumnSelectionAllowed(true);
				this.add(t);
				setVisible(true);
			}
			/**
			 *Standard model to represent an array as a Matrix in an JTable
			 */
			private final class MatrixModel extends AbstractTableModel {
				float[][] matrix;
				public MatrixModel(float[][] matrix){
					this.matrix = matrix;
				}
				@Override
				public int getRowCount() {
					return matrix.length;
				}
				@Override
				public int getColumnCount() {
					return matrix[0].length;
				}
				@Override
				public Object getValueAt( int row, int col ) {
					return matrix[row][col];
				}
				@Override
				public void setValueAt( Object aValue, int rowIndex, int columnIndex ) {
					matrix[rowIndex][columnIndex] = Float.parseFloat((String)aValue);
				}
				@Override
				public boolean isCellEditable( int rowIndex, int columnIndex ){
					return true;
				}
			}
			/**
			 * Standard model to represent an array as a vetor in an JTable.
			 * if a vector should be shown in a table, this is the model for that.
			 */
			private final class VectorModel extends AbstractTableModel {
				float[] v;
				public VectorModel(float[] v){
					this.v = v;
				}
				@Override
				public int getRowCount() {
					return v.length;
				}
				@Override
				public int getColumnCount() {
					return 1;
				}
				@Override
				public Object getValueAt( int row, int col ) {
					return v[row];
				}
				@Override
				public void setValueAt( Object aValue, int rowIndex, int columnIndex ) {
					v[rowIndex] = Float.parseFloat((String)aValue);
				}
				@Override
				public boolean isCellEditable( int rowIndex, int columnIndex ){
					return true;
				}
			}
		}
	};
	/**
	 *Does nothing.
	 */
	@Override
	public void initFilter() {
		//state = State.INITIAL;
	}

	/**
	 * computes from a point(x,y) the radians where the first point on the left side of the heading corresponds to 0 and the
	 * first point on the right side corresponds to 2*pi. The back of the fly corresponds to pi. The center of this unitcircle
	 * is represented by the body(which can be set by a mouse click)
	 *@param point Point which should be converted in radians
	 */
	private float radiansInHeadingCircle(Point2D.Float point){
		return radiansInHeadingCircle(point.x,point.y);
	}

	/**
	 * computes from a point(x,y) the radians where the first point on the left side of the heading corresponds to 0 and the
	 * first point on the right side corresponds to 2*pi. The back of the fly corresponds to pi. The center of this unitcircle
	 * is represented by the body(which can be set by a mouse click)
	 *@param x x coord from the point which should be converted in radians
	 *@param y y coord of that point
	 */
	private float radiansInHeadingCircle(float x, float y){
		float angle = radiansInUnitCircle(x,y) - heading;
		if (angle < 0) {
			return (float)(2*Math.PI) + angle;
		}
		return angle;
	}

	/**
	 * Calculates a the radians to the unit-circle with center at the body
	 *@param point Point which should be converted in radians
	 */
	private float radiansInUnitCircle(Point2D.Float point){
		return radiansInUnitCircle(point.x,point.y);
	}

	/**
	 * Calculates a the radians to the unit-circle with center at the body
	 *@param x x coordinate of the point which is converted in radians
	 *@param y y coordinate of that point
	 */
	private float radiansInUnitCircle(float x, float y){
		float[] p = {x - body.x,y - body.y};
		float pn = Matrix.norm2(p);
		p[0] = p[0]/pn;
		p[1] = p[1]/pn;
		float angle = (float)Math.acos(p[0]);
		if(p[1]<0){//int the third or fourth quadrant
			angle = (float)((Math.PI*2) - angle);
		}
		return angle;
	}

	/**
	 *calculates the distance of the event and the wingEdge-Line. This is actually not used anymore
	 *@param ev The event
	 *@param wingEdge A Point which determines the actualPosition of the wing edge line.
	 */
	private float distEventToWing(BasicEvent ev, Point2D.Float wingEdge){
		float[] e = {ev.x,ev.y};
		float[] w = {wingEdge.x,wingEdge.y};

		float scaling = ((ev.x*wingEdge.x) + (ev.y*wingEdge.y))/(Matrix.norm2(e)*Matrix.norm2(w));
		w[0] = scaling*w[0] ; //corresponds now to the closest point on the wingedge-line to the event
		w[1] = scaling*w[1] ;
		return (float)(new Point2D.Float(w[0],w[1])).distance(ev.x,ev.y);
	}

	//this boolean are to store if the wing crossed the hysteresis from one to the other side(it cant't cross a edge of the
	//hysteresis 2 times in the same direction)
	private boolean leftPositive = true,rightPositive = false;
	private int lastTimeStampL = 0, lastTimeStampR = 0;//for the frequency in update params
	private float leftTail =0, leftFront=0, rightTail =0,rightFront =0;//for amplitude in update params(min and max finder variables)

	/**
	 * This method is only used in TRACKING state. It updates the frequency and amplitude and checks for zero-crossing.
	 *It also updates the centroids after each wing beat for both wings, the heading(if selected) and the body position(if selected)
	 *The update is only done once per detected wingbeat.
	 *@param t the most actual time stamp.
	 */
	private void updateParams(int t){
		//The method is symmetric for the left and right wing, if the method is called, everything is checked for new updates
		if(!leftPositive ){//if the left wingposition(mean of the edges) on the head-side
			if(positionLeft < leftTail)
			{
				leftTail = positionLeft;//amplitude: we search for a minimum of the wingposition
			}
			if( (positionLeft-(centroidLeft+hysteresis)) > 0 ){ //we check if the position crossed the hysteresis(from head to back)
				/*
				 *here we crossed, so we set back the minimum and maximum finder of the wingposition
				 */
				leftPositive = true;//the next time we assume we are on the back side of the fly
				amplitudeLeft = Math.abs(leftFront-leftTail);//update the amplitude
				leftTail = centroidLeft;
				leftFront = centroidLeft;
				return;//we assume that work is done for this event.
			}
		}

		if(leftPositive){
			if(positionLeft >= leftFront){ //we check if there is a new max(radians at the back of the fly) of the position
				leftFront = positionLeft;
			}
			if( (positionLeft-(centroidLeft-hysteresis)) < 0){ //if we have crossed the hysteresis from back to head
				leftPositive = false; //next time we assume to go from head to tail
				frequenceLeft = 1f/((t-lastTimeStampL)*(float)1.e-6); //update the frequency
				lastTimeStampL = t;//store the time we crossed for next wing beat

				/*
				 *we update now the prototypes and the centroids. for this we look at the recorded events during the last
				 *wing beats and avarage. The average is taken as new prototype location
				 */
				if(leftBufferCount != 0) {
					prototypeL.setLocation(((1-mixingFactor)*prototypeL.x)+(mixingFactor*(leftBuffer.x/leftBufferCount)),
						((1-mixingFactor)*prototypeL.y)+(mixingFactor*(leftBuffer.y/leftBufferCount)));
				}
				leftBuffer.x = 0;leftBuffer.y = 0; leftBufferCount = 0; //set the buffers back to 0

				/*
				 *if the bodyupdate checkbutton is selected we update the body by finding the new mean of the prototypes and
				 *adding the offset which was probably selected by mouseclick
				 */
				if(doBodyUpdate){
					body = meanPoints(prototypeR,prototypeL);
					body.x += bodyOffset.x; body.y += bodyOffset.y;
				}
				//update the centroid variable(just the same as the prototype but in radians)
				centroidLeft = radiansInHeadingCircle(prototypeL.x,prototypeL.y);
				/*
				 *if the bodyposition or prototypes change, also the searchrange changes, since the searchrange is the doubled distance from
				 *the center to a prototype
				 */
				searchRange = (float)(body.distance(prototypeR)+body.distance(prototypeL));
				/*
				 *The next step updates the heading, it takes an orthogonal line throw the center of the line of the 2 prototypes
				 *this is not consistent! the next step, all angles have a little error till they're updated
				 */
				if(doHeadingUpdate){
					float x = (-(prototypeR.y-prototypeL.y)/2)+body.x;
					float y = ((prototypeR.x-prototypeL.x)/2)+body.y;
					heading = radiansInUnitCircle(x,y);
				}
				return;
			}
		}
		/*
		 *The rest of this method is analogous for the right side, for comments see above, how this is done for the left wing.
		 */
		if(rightPositive){
			if(positionRight < rightTail) {
				rightTail = positionRight;
			}
			if( (positionRight-(centroidRight+hysteresis)) > 0 ){
				rightPositive = false;
				amplitudeRight = Math.abs(rightFront-rightTail);
				rightTail = centroidRight;
				rightFront = centroidRight;
				return;
			}
		}
		if(!rightPositive){
			if(positionRight >= rightFront){
				rightFront = positionRight;
			}
			if( (positionRight-(centroidRight-hysteresis)) < 0){
				//crossed!
				rightPositive = true;
				frequenceRight = 1f/((t-lastTimeStampR)*(float)1.e-6);
				lastTimeStampR = t;
				//centroid update
				if(rightBufferCount != 0) {
					prototypeR.setLocation(((1-mixingFactor)*prototypeR.x)+(mixingFactor*(rightBuffer.x/rightBufferCount)),
						((1-mixingFactor)*prototypeR.y)+(mixingFactor*(rightBuffer.y/rightBufferCount)));
				}
				rightBuffer.x = 0;rightBuffer.y = 0; rightBufferCount = 0;

				if(doBodyUpdate){
					body = meanPoints(prototypeR,prototypeL);
					body.x += bodyOffset.x; body.y += bodyOffset.y;
				}
				centroidRight = radiansInHeadingCircle(prototypeR.x,prototypeR.y);
				searchRange = (float)(body.distance(prototypeR)+body.distance(prototypeL));
				//this is not consistent! the next step, all angles have a little error till they're updated
				//heading = (centroidLeft+centroidRight)-(float)Math.PI;
				//heading = radiansInUnitCircle((body.x-prototypeR.x)+(body.x-prototypeL.x),(body.y-prototypeR.y)+(body.y-prototypeL.y));
				if(doHeadingUpdate){
					float x = (-(prototypeR.y-prototypeL.y)/2)+body.x;
					float y = ((prototypeR.x-prototypeL.x)/2)+body.y;
					heading = radiansInUnitCircle(x,y);
				}

				return;
			}
		}
	}

	//these two variables are the same as prototypeR and prototypeL but without knowing on which side (L or R) they are. they
	//are used for k-means and are then assigned to a side.
	private Point2D.Float prototype1;
	private Point2D.Float prototype2;

	/**
	 *This method sets body position, sets the heading (with a algorithm that tries to find out where the head is), sets the search
	 *range, and
	 *assigns the prototype1,prototype2 to left or right depending on the heading. It is necessary that prototype1 and
	 *prototype2 has a meaningful value, this values come out of the k-means algorithm.
	 */
	private void findFly(){
		body = meanPoints(prototype1,prototype2);
		body.x += bodyOffset.x; body.y += bodyOffset.y;
		searchRange = (float)(body.distance(prototype1)+body.distance(prototype2));

		float x = (-(prototype2.y-prototype1.y)/2)+body.x;
		float y = ((prototype2.x-prototype1.x)/2)+body.y;
		heading = radiansInUnitCircle(x,y);

		/*
		 *Try to find out where the fly heading is: from the recorded data(we used for k-means) we now calculate the mean
		 *of the radians, where the actual heading is taken as reference(the acual heading is the x-axis of a unit circle):
		 *
		 *We simulate the wingedge tracks on the data we recorded for the k-means algorithm. Then we search for maxima and minima
		 *of the wingposition. The left side is this one where the maxima is closer to the heading line than the minima.
		 */

		float cL = radiansInHeadingCircle(prototype1);
		float cR = radiansInHeadingCircle(prototype2);
		float maxLeft = 0; float minLeft = (float)Math.PI;
		float maxRight = 0;float minRight = 2f*(float)Math.PI;

		for(EventWingPair ewp:initPoints){
			PolarityEvent e = (PolarityEvent)ewp.getEvent();
			if(e.polarity == On)
			{
				continue; //we only care about the leading edges(where the polarity is off)
			}
			if((((body.x-e.x)*(body.x-e.x))+((body.y-e.y)*(body.y-e.y))) <= (searchRange*searchRange)){ //is the event in searchrange
				float rads = radiansInHeadingCircle(e.x,e.y);//getString radians
				if(ewp.getWingType() == WingType.Left){
					cL = ((1-mixingFactor)*cL)+(mixingFactor*rads); //simulate the lowpass filter
					if(maxLeft < cL) {
						maxLeft = cL;
					}
					if(minLeft > cL) {
						minLeft = cL;
					}
				}else{
					cR = ((1-mixingFactor)*cR)+(mixingFactor*rads);
					if(maxRight < rads) {
						maxRight = rads;
					}
					if(minRight > rads) {
						minRight = rads;
					}
				}
			}
		}
		boolean flip = false;//if we flip in the end or not
		boolean shouldFlipLeft = false,shouldFlipRight = false; //both wings are analyzed, and both can have an oppinion where the heading should be
		if(minLeft < ((float)Math.PI-maxLeft)){ //see description above
			shouldFlipLeft = true;
		}
		if((minRight-Math.PI) > ((2*(float)Math.PI)-maxRight)){
			shouldFlipRight = true;
		}
		if(shouldFlipLeft == shouldFlipRight){ //this is a strong flip,both results are the same and we believe in that
			flip = shouldFlipRight; //can also be shouldflipLeft.
			//            System.out.println("strong flip");
		}else{
			//here we look, which one is "stronger"; which wing has the lower "opening angle"
			if((maxLeft-minLeft) > (maxRight-minRight)){
				flip = shouldFlipLeft;
			}else{
				flip = shouldFlipRight;
			}
			//            System.out.println("Weak flip: " + flip);
		}
		//finally, flip the heading if we should to
		if(flip){
			heading += (float)Math.PI;
			if(heading >= ((float)Math.PI*2f)) {
				heading -= Math.PI*2f;
			}
		}

		//try to find out, where the head of the fly is, this is done just by looking at a possible head position(there are 2)
		//and count the events in this reagion. The head is assumed there, where less events happen, since the wings
		//shouldn't appear in this region.

		//        float sinh = (float)Math.sin(heading);
		//        float cosh = (float)Math.cos(heading);
		//        Point2D.Float pos1 = new Point2D.Float(cosh*searchRange+body.x,sinh*searchRange+body.y);
		//        Point2D.Float pos2 = new Point2D.Float(body.x+(body.x-pos1.x),body.y+(body.y-pos1.y));
		//        int pos1Count = 0; //counts the events at the actual head position
		//        int pos2Count = 0; //counts the events on the other possible head position (on the heading line)
		//        float searchHeadRange = searchRange/3f;
		//        for(EventWingPair ewp:initPoints){
		//            if(pos1.distance(ewp.event.x,ewp.event.y) < searchHeadRange){
		//                pos1Count++;
		//                continue;
		//            }
		//            if(pos2.distance(ewp.event.x,ewp.event.y) < searchHeadRange){
		//                pos2Count++;
		//            }
		//        }
		////        System.out.println("heading in deg = " + (180f*heading)/Math.PI);
		//        System.out.println("pos2: "+ pos2.toString() + "pos1: " + pos1.toString());
		//        System.out.println("pos2Count: "+pos2Count+", pos1Count: "+pos1Count);

		//        if(pos2Count < pos1Count){//the head seems to be at pos2, so we turn
		//
		//            heading += (float)Math.PI;
		//            if(heading >= (float)Math.PI*2f)
		//                heading -= Math.PI*2f;
		//        }

		//assign prototype1,prototype2 to left and right.
		if(radiansInHeadingCircle(prototype1.x,prototype1.y) <= Math.PI){
			prototypeL = prototype1;
			prototypeR = prototype2;
		}else{
			prototypeL = prototype2;
			prototypeR = prototype1;
		}
	}
	/**
	 *The kMeans algorithm, in this case k = 2. As datapoints the method uses the recorded datapoints stored in the vector
	 *initpoints where each element conists of a EventWingPair.
	 *Goal is to cluster the datapoints in 2 clusters, one cluster for the wingedge and the other one for the right
	 *one. The classification is stored in the EventWingPair datastructure.
	 */
	private void kMeans(){ //or 2means
		float dist1,dist2;
		float x,y;
		prototype1 = new Point2D.Float(0f,(float)chip.getSizeY()-1);
		prototype2 = new Point2D.Float((float)chip.getSizeX()-1,0f);
		Point2D.Float tempPrototype1 = new Point2D.Float(0f,0f);
		Point2D.Float tempPrototype2 = new Point2D.Float(0f,0f);
		int n1 = 0;int n2=0;
		for(int i = 0; i < iterationsOfKMeans;i++){
			for(int j = 0; j < initPoints.size();j++){
				x = initPoints.elementAt(j).event.x;
				y = initPoints.elementAt(j).event.y;
				dist1 = (float)(Math.pow(x-prototype1.x,2)+Math.pow(y-prototype1.y,2));
				dist2 = (float)(Math.pow(x-prototype2.x,2)+Math.pow(y-prototype2.y,2));
				if(dist1 < dist2){
					initPoints.elementAt(j).setWingType(WingType.Left); //todo: n-1 iterations, this is useless
					n1++;
					tempPrototype1.x += x;
					tempPrototype1.y += y;
				}else{
					initPoints.elementAt(j).setWingType(WingType.Right);
					n2++;
					tempPrototype2.x += x;
					tempPrototype2.y += y;
				}
			}
			prototype1.setLocation(tempPrototype1.x / n1,tempPrototype1.y / n1);
			prototype2.setLocation(tempPrototype2.x / n2,tempPrototype2.y / n2);
			tempPrototype1.setLocation(0f,0f);
			tempPrototype2.setLocation(0f,0f);

			n1 = 0;
			n2 = 0;

		}
	}
	//initpoints is are the EventWingPairs with the recorded events for initialization. The classification of the wings is then done
	//in the k-means algorithm.
	private Vector<EventWingPair> initPoints = new Vector<EventWingPair>();
	enum WingType {Unknown, Left, Right};
	/**
	 *The Class provides a datastructure where an event can be assigned to a WingType. The WingType is an enumeration with entries:
	 *Unknown, Left, Right. Getter and Setter methods are provided.
	 */
	final class EventWingPair{
		BasicEvent event;
		WingType wing = WingType.Unknown;

		public EventWingPair(BasicEvent event, WingType w){
			this.event = event;
			wing = w;
		}

		public void setWingType(WingType w){
			wing = w;
		}
		public WingType getWingType(){
			return wing;
		}
		public BasicEvent getEvent(){
			return event;
		}
	}

	/**
	 *@return The midpoint of two points
	 *@param a first Point
	 *@param b second point
	 */
	private Point2D.Float meanPoints(Point2D.Float a, Point2D.Float b){
		return new Point2D.Float(((a.x-b.x)/2)+ b.x,((a.y-b.y)/2)+b.y);
	}

	/**
	 * This method initializes the edges on the correct side, if the angle in the heading-unit-circle is < 180 deg, then
	 * the prototype corresponds to the left edge(actually we don't know which wing it is, this is only in the code)
	 */
	private void initWingEdges(){
		leftLeadingEdge = radiansInHeadingCircle(prototypeL.x,prototypeL.y);
		leftTrailingEdge = leftLeadingEdge;
		centroidLeft = leftLeadingEdge;
		rightLeadingEdge = radiansInHeadingCircle(prototypeR.x,prototypeR.y);
		rightTrailingEdge = rightLeadingEdge;
		centroidRight = rightLeadingEdge;
	}

	/**
	 * This method shows just the initialisation. The tracker should be used with OpenGL.
	 */
	final void drawFilter(float[][][] fr){
		if(state != State.INITIAL){
			colorPixel(Math.round(prototypeL.x),Math.round(prototypeL.y),fr,Color.green);
			colorPixel(Math.round(prototypeR.x),Math.round(prototypeR.y),fr,Color.green);
			colorPixel(Math.round(body.x),Math.round(body.y),fr,Color.blue);
		}
	}


	/** @param x x location of pixel
	 *@param y y location
	 *@param fr the frame data
	 *@param channel the RGB channel number 0-2
	 *@param brightness the brightness 0-1
	 */
	final void colorPixel(final int x, final int y, final float[][][] fr, Color color){
		if((y<0) || (y>(fr.length-1)) || (x<0) || (x>(fr[0].length-1))) {
			return;
		}
		float[] rgb=color.getRGBColorComponents(null);
		float[] f=fr[y][x];
		for(int i=0;i<3;i++){
			f[i]=rgb[i];
		}
		//        fr[y][x][channel]=brightness;
		////        if(brightness<1){
		//        for(int i=0;i<3;i++){
		//            if(i!=channel) fr[y][x][i]=0;
		//        }
		////        }
	}
	/**
	 *@return The state of the tracker. The state is an element of an enumaration.
	 */
	public Object getFilterState() {
		return state;
	}

	public boolean isGeneratingFilter() {
		return false;
	}

	@Override
	synchronized public void resetFilter() {
		//the eventCounter for the initialization back to 0, new data are recorded
		eventCounter = 0;
		//set the state to init
		state = State.INITIAL;
		//look at the prefs and set the state after INIT
		setNextState();
		//delete the recorded data to record new data
		initPoints.clear();
		//set back the body offset
		bodyOffset.setLocation(0,0);
		//delete the initmask(new data are recorded and the mask is filled again)
		initMask = null;
		//clear the EKFParameterWindow
		if(ekfpw != null){
			ekfpw.dispose();
			ekfpw = null;
		}
		//set the EKF objects to zero so that they are created new.
		LLE = null; RLE = null; LTE = null; RTE = null;
	}

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
		track(in);
		return in;
	}

	@Override
	public void update(Observable o, Object arg) {
	}

	public void annotate(Graphics2D g) {
	}

	private boolean hasBlendChecked = false;
	private boolean hasBlend = false;

	GLU glu;
	GLUquadric flySurround;
	@Override
	public void annotate(GLAutoDrawable drawable) {
		if(!isFilterEnabled()) {
			return;
		}
		GL2 gl=drawable.getGL().getGL2();
		if(gl==null) {
			return;
		}
		gl.glPushMatrix();
		gl.glColor3f(1,0,0);
		gl.glLineWidth(.3f);

		//Prepare blending
		if(!hasBlendChecked){
			hasBlendChecked=true;
			String glExt=gl.glGetString(GL.GL_EXTENSIONS);
			if(glExt.indexOf("GL_EXT_blend_color")!=-1) {
				hasBlend=true;
			}
		}
		if(hasBlend){
			try{
				gl.glEnable(GL.GL_BLEND);
				gl.glBlendFunc(GL.GL_SRC_ALPHA,GL.GL_ONE_MINUS_SRC_ALPHA);
				gl.glBlendEquation(GL.GL_FUNC_ADD);
			}catch(GLException e){
				e.printStackTrace();
				hasBlend=false;
			}
		}

		if(state == State.TRACKING){
			//print out position string
			int font = GLUT.BITMAP_HELVETICA_12;
			gl.glColor3f(1,0,0);
			gl.glRasterPos3f(0,18,0);
			glut.glutBitmapString(font, String.format("Freq(L) = %.1f, Ampl(L) = %.1f", frequenceLeft,(180/Math.PI)*amplitudeLeft));

			gl.glRasterPos3f(0,14,0);
			glut.glutBitmapString(font, String.format("Freq(R) = %.1f, Ampl(R) = %.1f", frequenceRight,(180/Math.PI)*amplitudeRight));


			//draw the body in blue
			gl.glColor3f(0,0,1);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(body.x-1,body.y);
			gl.glVertex2f(body.x+1,body.y);
			gl.glVertex2f(body.x,body.y-1);
			gl.glVertex2f(body.x,body.y+1);
			gl.glEnd();

			//draw the prototypes:
			gl.glBegin(GL.GL_LINE_STRIP);
			gl.glVertex2f(prototypeL.x-1f,prototypeL.y );
			gl.glVertex2f(prototypeL.x,prototypeL.y -1f);
			gl.glVertex2f(prototypeL.x+1f,prototypeL.y);
			gl.glVertex2f(prototypeL.x,prototypeL.y +1f);
			gl.glVertex2f(prototypeL.x-1f,prototypeL.y );
			gl.glEnd();

			gl.glColor3f(1,1,1);
			gl.glBegin(GL.GL_LINE_STRIP);
			gl.glVertex2f(prototypeR.x-1f,prototypeR.y );
			gl.glVertex2f(prototypeR.x,prototypeR.y -1f);
			gl.glVertex2f(prototypeR.x+1f,prototypeR.y);
			gl.glVertex2f(prototypeR.x,prototypeR.y +1f);
			gl.glVertex2f(prototypeR.x-1f,prototypeR.y );
			gl.glEnd();


			//Draw the lines: first rotate, so that the heading is horizontal at 0 degrees:
			gl.glPushMatrix();

			//Translate to the body and rotate to zero degrees
			gl.glTranslatef(body.x,body.y,0);
			gl.glRotatef((180/(float)Math.PI)*heading,0,0,1);

			//draw the lines fly axes in white
			gl.glColor3f(1,1,1);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(searchRange+(.1f*searchRange),0);
			gl.glVertex2f(-searchRange+(.1f*searchRange),0);
			gl.glEnd();

			//draw the arrow to indicate where the heading (0 deg) is..:
			gl.glBegin(GL.GL_TRIANGLES);
			gl.glVertex2f(searchRange+(.1f*searchRange),0f);
			gl.glVertex2f(searchRange,-2f);
			gl.glVertex2f(searchRange,2f);
			gl.glEnd();

			//draw the hysteresis

			float h = searchRange*(float)Math.tan(hysteresis);
			gl.glColor4f(1f,0,0,.3f);
			gl.glPushMatrix();
			gl.glRotatef((180/(float)Math.PI)*centroidLeft,0,0,1);
			gl.glBegin(GL.GL_TRIANGLES);
			gl.glVertex2f(0f,0f);
			gl.glVertex2f(searchRange,h);
			gl.glVertex2f(searchRange,-h);
			gl.glEnd();
			gl.glPopMatrix();

			//draw the hysteresis
			gl.glPushMatrix();
			gl.glRotatef((180/(float)Math.PI)*centroidRight,0,0,1);
			gl.glBegin(GL.GL_TRIANGLES);
			gl.glVertex2f(0f,0f);
			gl.glVertex2f(searchRange,h);

			gl.glVertex2f(searchRange,-h);
			gl.glEnd();
			gl.glPopMatrix();

			//draw the wings
			//draw the leading edges in black:
			gl.glColor3f(0,0,0);
			gl.glLineWidth(3);
			gl.glPushMatrix();
			gl.glRotatef((180/(float)Math.PI)*rightLeadingEdge,0,0,1);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(0,0);
			gl.glVertex2f(searchRange,0);//tentative
			gl.glEnd();

			gl.glRotatef((180/(float)Math.PI)*(-rightLeadingEdge+leftLeadingEdge),0,0,1);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(0,0);
			gl.glVertex2f(searchRange,0);//tentative
			gl.glEnd();
			gl.glPopMatrix();

			//draw the trailing edges in white:
			gl.glColor3f(1,1,1);
			gl.glPushMatrix();
			gl.glRotatef((180/(float)Math.PI)*rightTrailingEdge,0,0,1);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(0,0);
			gl.glVertex2f(searchRange,0);//tentative
			gl.glEnd();

			gl.glRotatef((180/(float)Math.PI)*(-rightTrailingEdge+leftTrailingEdge),0,0,1);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(0,0);
			gl.glVertex2f(searchRange,0);//tentative
			gl.glEnd();
			gl.glPopMatrix();


			//draw the circle around the fly
			gl.glLineWidth(1);
			if(glu==null) {
				glu=new GLU();
			}
			if(flySurround==null) {
				flySurround = glu.gluNewQuadric();
			}
			gl.glColor4f(0,1,0,.2f);
			glu.gluQuadricDrawStyle(flySurround,GLU.GLU_FILL);
			glu.gluDisk(flySurround,0,searchRange+searchRangeOffset,16,1);

			gl.glPopMatrix();

			//            gl.glPushMatrix();
			//            gl.glRotatef((180/(float)Math.PI)*originalHeading,0,0,1);
			//            gl.glColor4f(1,1,1,.5f);
			//            gl.glBegin(GL2.GL_LINES);
			//            gl.glVertex2f((float)chip.getSizeX()/2,0);
			//            gl.glVertex2f(-(float)chip.getSizeX()/2,0);
			//            gl.glPopMatrix();
		}
		if(state == State.KALMAN){
			//an edge can be null if it is not created before this thread runs(there was no event for this edge)
			if((LLE == null) || (LTE == null) || (RLE == null) || (RTE == null)) {
				return;
			}
			//Write down the frequence and amplitude
			int font = GLUT.BITMAP_HELVETICA_12;
			gl.glColor3f(1,0,0);
			gl.glRasterPos3f(0,18,0);
			glut.glutBitmapString(font, String.format("Freq(LLE) = %.1f, Ampl(LLE) = %.1f", LLE.x[3]/(2f*(float)Math.PI),(180/Math.PI)*LLE.x[1]));

			gl.glColor3f(1,0.5f,0.5f);
			gl.glRasterPos3f(0,14,0);
			glut.glutBitmapString(font, String.format("Freq(LTE) = %.1f, Ampl(LTE) = %.1f", LTE.x[3]/(2f*(float)Math.PI),(180/Math.PI)*LTE.x[1]));

			gl.glColor3f(1,0,0);
			gl.glRasterPos3f(0,10,0);
			glut.glutBitmapString(font, String.format("Freq(RLE) = %.1f, Ampl(RLE) = %.1f", RLE.x[3]/(2f*(float)Math.PI),(180/Math.PI)*RLE.x[1]));

			gl.glColor3f(1,0.5f,0.5f);
			gl.glRasterPos3f(0,6,0);
			glut.glutBitmapString(font, String.format("Freq(RTE) = %.1f, Ampl(RTE) = %.1f", RTE.x[3]/(2f*(float)Math.PI),(180/Math.PI)*RTE.x[1]));

			//draw the body in blue
			gl.glColor3f(0,0,1);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(body.x-1,body.y);
			gl.glVertex2f(body.x+1,body.y);
			gl.glVertex2f(body.x,body.y-1);
			gl.glVertex2f(body.x,body.y+1);
			gl.glEnd();

			///////////////////////////////////////////////////////////////////////////////
			//Draw the lines: first rotate, so that the heading is horizontal at 0 degrees:
			///////////////////////////////////////////////////////////////////////////////
			gl.glPushMatrix();

			//Translate to the body and rotate to zero degrees
			gl.glTranslatef(body.x,body.y,0);
			gl.glRotatef((180/(float)Math.PI)*heading,0,0,1);

			//draw the lines fly axes in white
			gl.glColor3f(1,1,1);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(searchRange+(.1f*searchRange),0);
			gl.glVertex2f(-searchRange+(.1f*searchRange),0);
			gl.glEnd();

			//draw the arrow to indicate where the heading (0 deg) is..:
			gl.glBegin(GL.GL_TRIANGLES);
			gl.glVertex2f(searchRange+(.1f*searchRange),0f);
			gl.glVertex2f(searchRange,-2f);
			gl.glVertex2f(searchRange,2f);
			gl.glEnd();

			//draw the wings
			//draw the leading edges in black:
			if(LLE.getUseEdge()){
				gl.glColor3f(0,0f,0f);
				gl.glPushMatrix();
				gl.glRotatef((180/(float)Math.PI)*LLE.getEdgeInRads(),0,0,1);
				gl.glBegin(GL.GL_LINES);
				gl.glVertex2f(0,0);
				gl.glVertex2f(searchRange,0);
				gl.glEnd();
				gl.glPopMatrix();
			}
			if(RLE.getUseEdge()){
				gl.glColor3f(0,0f,0f);
				gl.glPushMatrix();
				gl.glRotatef((180/(float)Math.PI)*RLE.getEdgeInRads(),0,0,1);
				gl.glBegin(GL.GL_LINES);
				gl.glVertex2f(0,0);
				gl.glVertex2f(searchRange,0);
				gl.glEnd();
				gl.glPopMatrix();
			}
			//draw the trailing edges in white
			if(RTE.getUseEdge()){
				gl.glColor3f(1,1,1);
				gl.glPushMatrix();
				gl.glRotatef((180/(float)Math.PI)*RTE.getEdgeInRads(),0,0,1);
				gl.glBegin(GL.GL_LINES);
				gl.glVertex2f(0,0);
				gl.glVertex2f(searchRange,0);
				gl.glEnd();
				gl.glPopMatrix();
			}
			if(LTE.getUseEdge()){
				gl.glColor3f(1,1,1);
				gl.glPushMatrix();
				gl.glRotatef((180/(float)Math.PI)*LTE.getEdgeInRads(),0,0,1);
				gl.glBegin(GL.GL_LINES);
				gl.glVertex2f(0,0);
				gl.glVertex2f(searchRange,0);
				gl.glEnd();
				gl.glPopMatrix();
			}
			//draw a0
			//            gl.glColor3f(1f,1f,0f);
			//            gl.glPushMatrix();
			//            gl.glRotatef((180/(float)Math.PI)*LLE.x[0],0,0,1);
			//            gl.glBegin(GL2.GL_LINES);
			//            gl.glVertex2f(0,0);
			//            gl.glVertex2f(searchRange,0);//tentative
			//            gl.glEnd();
			//            gl.glPopMatrix();

			//draw the search range in green
			if(glu==null) {
				glu=new GLU();
			}
			if(flySurround==null) {
				flySurround = glu.gluNewQuadric();
			}
			gl.glColor4f(0,1,0,.2f);
			glu.gluQuadricDrawStyle(flySurround,GLU.GLU_FILL);
			glu.gluDisk(flySurround,0,searchRange+searchRangeOffset,16,1);
			gl.glPopMatrix();
		}
		gl.glPopMatrix();
	}


	//public void preferenceChange(PreferenceChangeEvent evt) {
	// distToVanishingPoint=prefs.getfloat("KalmanFilter.distToVanishingPoint",300);
	//}

	public void setDoLog(boolean doLog){
		Calendar cal = Calendar.getInstance();
		//        System.out.println();

		if(doLog){
			try{
				logWriter = new BufferedWriter(new FileWriter(new File(".","wingLog_"+ cal.get(Calendar.YEAR)+
					(cal.get(Calendar.MONTH)+1)+cal.get(Calendar.DAY_OF_MONTH)+"_"+
					cal.get(Calendar.HOUR_OF_DAY)+cal.get(Calendar.MINUTE)+"_"+cal.get(Calendar.SECOND)+".txt")));
				logWriter.write("#edgetype leftleadingEdge = 1"+nl+
					"#edgetype leftTrailingEdge = 2" + nl +
					"#edgetype rightleadingEdge = 3"+ nl+
					"#edgtype rightTrailinEdge = 4"+ nl+
					"#Output of WRA track: edgetype timeStamp EdgePos eventPos" + nl+
					"#Output of Kalman filter track: edgetype timestamp trackPos wingPos Amplitude Phase angularFreq."+nl);
			}catch(IOException ioe){
				System.out.println(ioe.toString());
			}
		}else{
			if(logWriter != null){
				try {
					logWriter.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		}
		getPrefs().putBoolean("WingTracker.doLog",doLog);
		this.doLog = doLog;
	}
	public boolean getDoLog(){
		return doLog;
	}

	public float getMixingFactor() {
		return mixingFactor;
	}

	public void setMixingFactor(float mixingFactor) {
		if(mixingFactor>1) {
			mixingFactor=1;
		}
		else if(mixingFactor<0) {
			mixingFactor=0;
		}
		this.mixingFactor = mixingFactor;
		getPrefs().putFloat("WingTracker.mixingFactor",mixingFactor);
	}
	public float getHysteresis() {
		return hysteresis;
	}

	public void setHysteresis(float hysteresis) {
		if(hysteresis>Math.PI) {
			mixingFactor=(float)Math.PI;
		}
		else if(hysteresis<0) {
			hysteresis=0;
		}
		this.hysteresis = hysteresis;
		getPrefs().putFloat("WingTracker.hysteresis",hysteresis);
	}

	public float getSearchRangeOffset(){
		return searchRangeOffset;
	}

	public void setSearchRangeOffset(float searchRangeOffset){
		this.searchRangeOffset = searchRangeOffset;
		getPrefs().putFloat("WingTracker.searchRangeOffset",searchRangeOffset);
	}
	public void setdoBodyUpdate(boolean doBodyUpdate){
		this.doBodyUpdate = doBodyUpdate;
		getPrefs().putBoolean("WingTracker.doBodyUpdate",doBodyUpdate);
	}
	public boolean getdoBodyUpdate(){
		return doBodyUpdate;
	}
	public void setdoHeadingUpdate(boolean doHeadingUpdate){
		this.doHeadingUpdate = doHeadingUpdate;
		getPrefs().putBoolean("WingTracker.doHeadingUpdate",doHeadingUpdate);
	}
	public boolean getdoHeadingUpdate(){
		return doHeadingUpdate;
	}
	public void setUseKalmanFiltering(boolean useKalmanFiltering){
		this.useKalmanFiltering = useKalmanFiltering;
		getPrefs().putBoolean("WingTracker.useKalmanFiltering",useKalmanFiltering);
		if(state == State.INITIAL) {
			if(useKalmanFiltering) {
				nextState = State.KALMAN;
			}
			else {
				nextState = State.TRACKING;
			}
			return;
		}
		if(useKalmanFiltering) {
			state = State.KALMAN;
		}
		else {
			state = State.TRACKING;
		}

	}
	public boolean getUseKalmanFiltering(){
		return useKalmanFiltering;
	}
	public int getNbEventsToCollectPerEdge(){
		return nbEventsToCollectPerEdge;
	}
	public void setNbEventsToCollectPerEdge(int nbEventsToCollectPerEdge){
		if(nbEventsToCollectPerEdge < 1) {
			nbEventsToCollectPerEdge = 1;
		}
		this.nbEventsToCollectPerEdge = nbEventsToCollectPerEdge;
		getPrefs().putInt("WingTracker.nbEventsToCollectPerEdge", nbEventsToCollectPerEdge);
	}
	public void setFlipHeading(boolean flipHeading){
		this.flipHeading = flipHeading;
		getPrefs().putBoolean("WingTracker.flipHeading",flipHeading);

		float pi = (float)Math.PI;
		//swith the heading
		heading += pi;
		if(heading >= (pi*2f)) {
			heading -= pi*2f;
		}
		//switch the centroids
		float tempc = centroidLeft;
		centroidLeft = centroidRight+pi;
		centroidRight = tempc+pi;

		//switch the leadingEdges
		float temp = leftLeadingEdge;
		leftLeadingEdge = rightLeadingEdge-pi;
		rightLeadingEdge = pi+temp;
		//switch the trailing edges
		temp = leftTrailingEdge;
		leftTrailingEdge = rightTrailingEdge-pi;
		rightTrailingEdge = pi+temp;

		//switch the prototypes
		Point2D.Float tempP = prototypeL;
		prototypeL = prototypeR;
		prototypeR = tempP;

	}
	public boolean getFlipHeading(){
		return flipHeading;
	}
	public void doShowEKFParameterWindow(){
		if(!(ekfpw == null)){
			ekfpw.setVisible(true);
		}
		getPrefs().putBoolean("WingTracker.showEKFParameterWindow",false);
	}

}


