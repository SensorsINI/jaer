/*
 * StereoEventTimingMonitor.java
 * Monitor incoming events in defined area, output mean time of packet and min/max recorded time
 *
 *
 * Paul Rogister, Created on June, 2008
 *
 */


package ch.unizh.ini.jaer.projects.stereo3D;

import java.awt.Graphics2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;


/**
 * StereoEventTimingMonitor:
 * Monitor synchro of left and right stereo retina, in defined pixel area, output mean time of packet and min/max recorded time
 * Apply to Binocular events, from left and fight retina
 * @author rogister
 */
public class StereoEventTimingMonitor extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {


	protected final int RIGHT = 1;
	protected final int LEFT = 0;

	protected final int ON = 1;
	protected final int OFF = 0;


	// retina size, should be coded in hard, should come from chip
	//private int retinaSize=128;//getPrefs().getInt("EventTimingMonitor.retinaSize",128);


	protected AEChip chip;
	private AEChipRenderer renderer;

	private boolean logDataEnabled=false;
	private PrintStream logStream=null;

	// Parameters appearing in the GUI

	private int[] x_min = new int[2];
	private int[] x_max = new int[2];
	private int[] y_min = new int[2];
	private int[] y_max = new int[2];





	private int timeWindowLength = getPrefs().getInt("StereoEventTimingMonitor.timeWindowLength",0);
	{setPropertyTooltip("timeWindowLength","duration of time window in us");}

	private int minEvents = getPrefs().getInt("StereoEventTimingMonitor.minEvents",100);
	{setPropertyTooltip("minEvents","min events to log results");}


	private boolean showZone = getPrefs().getBoolean("StereoEventTimingMonitor.showZone",true);

	// do not forget to add a set and a getString/is method for each new parameter, at the end of this .java file


	// global variables


	int[] startTime = new int[2];
	int[] endTime = new int[2];
	int[] meanTime = new int[2];

	int[] startTimeFinal = new int[2];
	int[] endTimeFinal = new int[2];
	int[] meanTimeFinal = new int[2];


	boolean[] restartMeanComputation = new boolean[2];
	boolean[] computed = new boolean[2];
	Vector[] timings = new Vector[2];
	//  Vector offtimings = new Vector();



	/** Creates a new instance of GravityCentersImageDumper */
	public StereoEventTimingMonitor(AEChip chip) {
		super(chip);
		this.chip=chip;
		renderer=chip.getRenderer();

		x_min[LEFT] = getPrefs().getInt("StereoEventTimingMonitor.left_x_min",0);
		{setPropertyTooltip("left_x_min","monitored area x_min in pixel coordinates");}
		x_max[LEFT] = getPrefs().getInt("StereoEventTimingMonitor.left_x_max",0);
		{setPropertyTooltip("left_x_max","monitored area x_max in pixel coordinates");}
		y_min[LEFT] = getPrefs().getInt("StereoEventTimingMonitor.left_y_min",0);
		{setPropertyTooltip("left_y_min","monitored area y_min in pixel coordinates");}
		y_max[LEFT] = getPrefs().getInt("StereoEventTimingMonitor.left_y_max",0);
		{setPropertyTooltip("left_y_max","monitored area y_max in pixel coordinates");}
		x_min[RIGHT] = getPrefs().getInt("StereoEventTimingMonitor.right_x_min",0);
		{setPropertyTooltip("right_x_min","monitored area x_min in pixel coordinates");}
		x_max[RIGHT] = getPrefs().getInt("StereoEventTimingMonitor.right_x_max",0);
		{setPropertyTooltip("right_x_max","monitored area x_max in pixel coordinates");}
		y_min[RIGHT] = getPrefs().getInt("StereoEventTimingMonitor.right_y_min",0);
		{setPropertyTooltip("right_y_min","monitored area y_min in pixel coordinates");}
		y_max[RIGHT] = getPrefs().getInt("StereoEventTimingMonitor.right_y_max",0);
		{setPropertyTooltip("right_y_max","monitored area y_max in pixel coordinates");}



		initFilter();




		chip.addObserver(this);


	}

	@Override
	public void initFilter() {
		resetFilter();
	}



	private void initDefault(String key, String value){
		if(getPrefs().get(key,null)==null) {
			getPrefs().put(key,value);
		}
	}

	// the method that actually does the tracking
	synchronized private void track(EventPacket<TypedEvent> ae){

		int n=ae.getSize();
		if(n==0) {
			return;
		}

		//    if( !chip.getAeViewer().isSingleStep()){
		//        chip.getAeViewer().aePlayer.pause();
		//    }



		for(TypedEvent e:ae){

			processEvent(e);

		}

		if(computed[LEFT]&&computed[RIGHT]){
			int diffStart = startTimeFinal[LEFT]-startTimeFinal[RIGHT];
			int diffEnd = endTimeFinal[LEFT]-endTimeFinal[RIGHT];
			int diffMean = meanTimeFinal[LEFT]-meanTimeFinal[RIGHT];

			System.out.println("diffStart "+diffStart+" diffEnd "+diffEnd+" diffMean "+diffMean+" ");


			startTimeFinal[LEFT] = 0;
			startTimeFinal[RIGHT] = 0;

			endTimeFinal[LEFT] = 0;
			endTimeFinal[RIGHT] = 0;

			meanTimeFinal[LEFT] = 0;
			meanTimeFinal[RIGHT] = 0;

			computed[LEFT] = false;
			computed[RIGHT] = false;
		}

		//    if( !chip.getAeViewer().isSingleStep()){
		//       chip.getAeViewer().aePlayer.resume();
		//   }

	}

	// processing one event
	protected void processEvent( TypedEvent e ){
		int leftOrRight;
		if (e instanceof BinocularEvent){
			BinocularEvent be = (BinocularEvent)e;
			leftOrRight = be.eye == BinocularEvent.Eye.LEFT ? 0 : 1; //be sure if left is same as here

		} else {
			return;
		}


		if (restartMeanComputation[leftOrRight]) {
			if ((e.x > x_min[leftOrRight]) && (e.x < x_max[leftOrRight]) && (e.y > y_min[leftOrRight]) && (e.y < y_max[leftOrRight])) {
				startTime[leftOrRight] = e.timestamp;
				restartMeanComputation[leftOrRight] = false;
				//      System.out.println("!!! start at " + startTime[leftOrRight] + ", left? " + leftOrRight);
			}
		}



		if((startTime[leftOrRight]!=0)&&(e.timestamp>(startTime[leftOrRight]+timeWindowLength))){
			// end mean computation
			if(timings[leftOrRight].size()>minEvents){
				meanTime[leftOrRight] = mean(timings[leftOrRight]);
				//   chip.getAeViewer().aePlayer.pause();
				//     System.out.println("meanTime "+meanTime[leftOrRight]);
				//   System.out.println("startTime "+startTime[leftOrRight]);
				//  System.out.println("endTime "+endTime[leftOrRight]);
				//      System.out.println("nb events "+timings[leftOrRight].size()+" left? "+leftOrRight);
				//      System.out.println("Vector "+leftOrRight);
				//      System.out.println(timings[leftOrRight]);
				//      System.out.println("end Vector");
				//     chip.getAeViewer().aePlayer.resume();
				startTimeFinal[leftOrRight] = startTime[leftOrRight];
				endTimeFinal[leftOrRight] =endTime[leftOrRight];
				meanTimeFinal[leftOrRight] = meanTime[leftOrRight];


				computed[leftOrRight] = true;
			}
			//           if(offtimings.size()>minEvents){
			//               System.out.println("nb events "+offtimings.size()+" left? "+left);
			//                    System.out.println("Off Vector");
			//                    System.out.println(offtimings);
			//                    System.out.println("end Off Vector");
			//
			//           }
			// restart mean computation
			restartMeanComputation[leftOrRight] = true;
			//    System.out.println("??? end at "+endTime[leftOrRight]+", left? "+leftOrRight);
			timings[leftOrRight].clear();

			// offtimings.clear();
			startTime[leftOrRight] = 0;
		}

		// endTime = e.timestamp;
		if ((e.x > x_min[leftOrRight]) && (e.x < x_max[leftOrRight]) && (e.y > y_min[leftOrRight]) && (e.y < y_max[leftOrRight])) {
			if (e.type == ON) {

				endTime[leftOrRight] = e.timestamp;
				timings[leftOrRight].add(new Integer(endTime[leftOrRight]));


			} else {
				// offtimings.add(new Integer(endTime));
			}
		}

	}


	private int mean(Vector<Integer> v){

		int res = 0;


		for( Integer i:v){
			res += i.intValue();
		}

		double meanres = res/v.size();
		long lres = Math.round(meanres);
		//   System.out.println("jres: "+jres+" v.size: "+v.size()+" lmeanres "+lmeanres+" lires "+lires+" ilires "+(int)lires);

		return (int)lres;
	}

	@Override
	public String toString(){
		String s="StereoEventTimingMonitor";
		return s;
	}


	public Object getFilterState() {
		return null;
	}

	private boolean isGeneratingFilter() {
		return false;
	}

	@Override
	synchronized public void resetFilter() {
		timings = new Vector[2];
		timings[LEFT] = new Vector();
		timings[RIGHT] = new Vector();

		//offtimings = new Vector();
		startTime = new int[2];
		endTime =  new int[2];
		meanTime =  new int[2];
		restartMeanComputation =  new boolean[2];
		restartMeanComputation[LEFT] = true;
		restartMeanComputation[RIGHT] = true;
		computed = new boolean[2];
		computed[LEFT] = false;
		computed[RIGHT] = false;

		startTimeFinal[LEFT] = 0;
		startTimeFinal[RIGHT] = 0;

		endTimeFinal[LEFT] = 0;
		endTimeFinal[RIGHT] = 0;

		meanTimeFinal[LEFT] = 0;
		meanTimeFinal[RIGHT] = 0;

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
		if(!(in.getEventPrototype() instanceof BinocularEvent)) {
			// System.out.println("not a binocular event!");
			return in;
		}

		track(in);

		return in;
	}

	@Override
	public void update(Observable o, Object arg) {
		initFilter();
	}


	/***********************************************************************************
	 * // drawing on player window
	 ********************************************************************************/

	public void annotate(Graphics2D g) {
	}

	protected void drawBoxCentered(GL2 gl, int x, int y, int sx, int sy){
		gl.glBegin(GL.GL_LINE_LOOP);
		{
			gl.glVertex2i(x-sx,y-sy);
			gl.glVertex2i(x+sx,y-sy);
			gl.glVertex2i(x+sx,y+sy);
			gl.glVertex2i(x-sx,y+sy);
		}
		gl.glEnd();
	}

	protected void drawBox(GL2 gl, int x, int x2, int y, int y2){
		gl.glBegin(GL.GL_LINE_LOOP);
		{
			gl.glVertex2i(x,y);
			gl.glVertex2i(x2,y);
			gl.glVertex2i(x2,y2);
			gl.glVertex2i(x,y2);
		}
		gl.glEnd();
	}

	@Override
	synchronized public void annotate(GLAutoDrawable drawable) {
		//final float LINE_WIDTH=5f; // in pixels
		if(!isFilterEnabled()) {
			return;
		}


		GL2 gl=drawable.getGL().getGL2(); // when we getString this we are already set up with scale 1=1 pixel, at LL corner
		if(gl==null){
			log.warning("null GL in GravityCentersImageDumper.annotate");
			return;
		}
		// float[] rgb=new float[4];
		gl.glPushMatrix();
		try{

			// like draw door
			if(showZone){

				gl.glColor3f(1,0,0);

				drawBox(gl,x_min[LEFT],x_max[LEFT],y_min[LEFT],y_max[LEFT]);
				gl.glColor3f(0,0,1);
				drawBox(gl,x_min[RIGHT],x_max[RIGHT],y_min[RIGHT],y_max[RIGHT]);
			}

		}catch(java.util.ConcurrentModificationException e){
			// this is in case cluster list is modified by real time filter during rendering of clusters
			log.warning(e.getMessage());
		}
		gl.glPopMatrix();
	}

	public synchronized boolean isLogDataEnabled() {
		return logDataEnabled;
	}

	public synchronized void setLogDataEnabled(boolean logDataEnabled) {
		this.logDataEnabled = logDataEnabled;
		if(!logDataEnabled) {
			logStream.flush();
			logStream.close();
			logStream=null;
		}else{
			try{
				logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File("PawTrackerData.txt"))));
				logStream.println("# clusterNumber lasttimestamp x y avergeEventDistance");
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}



	public void setShowZone(boolean showZone){
		this.showZone = showZone;

		getPrefs().putBoolean("StereoEventTimingMonitor.showZone",showZone);
	}
	public boolean isShowZone(){
		return showZone;
	}

	public void setleft_x_min(int x_min) {
		this.x_min[LEFT] = x_min;
		getPrefs().putInt("StereoEventTimingMonitor.left_x_min",x_min);
	}
	public int getleft_x_min() {
		return x_min[LEFT];
	}


	public void setLeft_x_max(int x_max) {
		this.x_max[LEFT] = x_max;
		getPrefs().putInt("StereoEventTimingMonitor.left_x_max",x_max);
	}
	public int getLeft_x_max() {
		return x_max[LEFT];
	}


	public void setLeft_y_min(int y_min) {
		this.y_min[LEFT] = y_min;
		getPrefs().putInt("StereoEventTimingMonitor.left_y_min",y_min);
	}
	public int getLeft_y_min() {
		return y_min[LEFT];
	}


	public void setLeft_y_max(int y_max) {
		this.y_max[LEFT] = y_max;
		getPrefs().putInt("StereoEventTimingMonitor.left_y_max",y_max);
	}
	public int getLeft_y_max() {
		return y_max[LEFT];
	}


	public void setRight_x_min(int x_min) {
		this.x_min[RIGHT] = x_min;
		getPrefs().putInt("StereoEventTimingMonitor.right_x_min",x_min);
	}
	public int getRight_x_min() {
		return x_min[RIGHT];
	}


	public void setRight_x_max(int x_max) {
		this.x_max[RIGHT] = x_max;
		getPrefs().putInt("StereoEventTimingMonitor.right_x_max",x_max);
	}
	public int getRight_x_max() {
		return x_max[RIGHT];
	}


	public void setRight_y_min(int y_min) {
		this.y_min[RIGHT] = y_min;
		getPrefs().putInt("StereoEventTimingMonitor.right_y_min",y_min);
	}
	public int getRight_y_min() {
		return y_min[RIGHT];
	}


	public void setRight_y_max(int y_max) {
		this.y_max[RIGHT] = y_max;
		getPrefs().putInt("StereoEventTimingMonitor.right_y_max",y_max);
	}
	public int getRight_y_max() {
		return y_max[RIGHT];
	}


	public void setTimeWindowLength(int timeWindowLength) {
		this.timeWindowLength = timeWindowLength;
		getPrefs().putInt("StereoEventTimingMonitor.timeWindowLength",timeWindowLength);
	}
	public int getTimeWindowLength() {
		return timeWindowLength;
	}

	public void setMinEvents(int minEvents) {
		this.minEvents = minEvents;
		getPrefs().putInt("StereoEventTimingMonitor.minEvents",minEvents);
	}
	public int getMinEvents() {
		return minEvents;
	}




}
