/*
 * DirectionSelectiveFilter.java
 *
 * Created on November 2, 2005, 8:24 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.eventprocessing.label;

import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLException;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MotionOrientationEvent;
import net.sf.jaer.event.OrientationEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Computes motion based nearest event (in past time) in neighboring pixels.
 *<p>
 *Output cells type has values 0-7,
 * 0 being upward motion, increasing by 45 deg CCW to 7 being motion up and to right.
 *
 *
 * @author tobi
 */
@Description("Local motion by time-of-travel of orientation events")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DirectionSelectiveFilter extends EventFilter2D implements Observer, FrameAnnotater {
	final int NUM_INPUT_TYPES=8; // 4 orientations * 2 polarities
	private int sizex,sizey; // chip sizes
	private boolean showGlobalEnabled=getBoolean("showGlobalEnabled",false);
	private boolean showVectorsEnabled=getBoolean("showVectorsEnabled",false);

	/** event must occur within this time in us to generate a motion event */
	private int maxDtThreshold=getInt("maxDtThreshold",100000); // default 100ms
	private int minDtThreshold=getInt("minDtThreshold",100); // min 100us to filter noise or multiple spikes

	private int searchDistance=getInt("searchDistance",3);
	private float ppsScale=getFloat("ppsScale",.05f);

	//    private float maxSpeedPPS=prefs.getFloat("maxSpeedPPS",100);

	private boolean speedControlEnabled=getBoolean("speedControlEnabled", true);
	private float speedMixingFactor=getFloat("speedMixingFactor",.001f);
	private int excessSpeedRejectFactor=getInt("excessSpeedRejectFactor",3);

	private boolean showRawInputEnabled=getBoolean("showRawInputEnabled",false);

	private boolean useAvgDtEnabled=getBoolean("useAvgDtEnabled",false);

	// taulow sets time const of lowpass filter, limiting max frequency
	private int tauLow=getInt("tauLow",100);

	private int subSampleShift=getInt("subSampleShift",0);

	private boolean jitterVectorLocations=getBoolean("jitterVectorLocations", true);
	private float jitterAmountPixels=getFloat("jitterAmountPixels",.5f);


	private EventPacket oriPacket=null; // holds orientation events
	private EventPacket dirPacket=null; // the output events, also used for rendering output events


	int[][][] lastTimesMap; // map of input orientation event times, [x][y][type] where type is mixture of orienation and polarity

	/** the number of cell output types */
	//    public final int NUM_TYPES=8;
	int PADDING=2; // padding around array that holds previous orientation event timestamps to prevent arrayoutofbounds errors and need for checking
	int P=1; // PADDING/2
	int lastNumInputCellTypes=2;
	SimpleOrientationFilter oriFilter;
	private MotionVectors motionVectors;
	//    private LowpassFilter speedFilter=new LowpassFilter();
	float avgSpeed=0;

	/**
	 * Creates a new instance of DirectionSelectiveFilter
	 */
	public DirectionSelectiveFilter(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		resetFilter();
		setFilterEnabled(false);
		oriFilter=new SimpleOrientationFilter(chip);
		oriFilter.setAnnotationEnabled(false);
		setEnclosedFilter(oriFilter);
		motionVectors = new MotionVectors();
		final String disp="Display";
		setPropertyTooltip(disp,"ppsScale", "scale of pixels per second to draw local and global motion vectors");
		setPropertyTooltip("subSampleShift", "Shift subsampled timestamp map stores by this many bits");
		setPropertyTooltip("tauLow", "time constant in ms of lowpass filters for global motion signals");
		setPropertyTooltip("useAvgDtEnabled", "uses average delta time over search instead of minimum");
		setPropertyTooltip(disp,"showRawInputEnabled", "shows the input events, instead of the motion types");
		setPropertyTooltip("excessSpeedRejectFactor", "local speeds this factor higher than average are rejected as non-physical");
		setPropertyTooltip("speedMixingFactor", "speeds computed are mixed with old values with this factor");
		setPropertyTooltip("speedControlEnabled", "enables filtering of excess speeds");
		setPropertyTooltip("searchDistance", "search distance perpindicular to orientation, 1 means search 1 to each side");
		setPropertyTooltip("minDtThreshold", "min delta time (us) for past events allowed for selecting a particular direction");
		setPropertyTooltip("maxDtThreshold", "max delta time (us) that is considered");
		setPropertyTooltip(disp,"showVectorsEnabled", "shows local motion vectors");
		setPropertyTooltip(disp,"showGlobalEnabled", "shows global tranlational, rotational, and expansive motion");
		setPropertyTooltip(disp, "jitterAmountPixels", "how much to jitter vector origins by in pixels");
		setPropertyTooltip(disp,"jitterVectorLocations","whether to jitter vector location to see overlapping vectors more easily");
	}

	public Object getFilterState() {
		return lastTimesMap;
	}

	@Override
	synchronized public void resetFilter() {
		setPadding(getSearchDistance()); // make sure to set padding
		sizex=chip.getSizeX();
		sizey=chip.getSizeY();
	}

	void checkMap(){
		if((lastTimesMap==null) || (lastTimesMap.length!=(chip.getSizeX()+PADDING)) || (lastTimesMap[0].length!=(chip.getSizeY()+PADDING)) || (lastTimesMap[0][0].length!=NUM_INPUT_TYPES)){
			allocateMap();
		}
	}

	private void allocateMap() {
		if(!isFilterEnabled()) {
			return;
		}
		lastTimesMap=new int[chip.getSizeX()+PADDING][chip.getSizeY()+PADDING][NUM_INPUT_TYPES];
		log.info(String.format("allocated int[%d][%d][%d] array for last event times",chip.getSizeX(),chip.getSizeY(),NUM_INPUT_TYPES));
	}

	GLU glu = null;
	GLUquadric expansionQuad;
	boolean hasBlendChecked = false;
	boolean hasBlend = false;

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if (!isFilterEnabled()) {
			return;
		}
		GL2 gl = drawable.getGL().getGL2();
		if (gl == null) {
			return;
		}
		if (!hasBlendChecked) {
			hasBlendChecked = true;
			String glExt = gl.glGetString(GL.GL_EXTENSIONS);
			if (glExt.indexOf("GL_EXT_blend_color") != -1) {
				hasBlend = true;
			}
		}
		if (hasBlend) {
			try {
				gl.glEnable(GL.GL_BLEND);
				gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
				gl.glBlendEquation(GL.GL_FUNC_ADD);
			} catch (GLException e) {
				e.printStackTrace();
				hasBlend = false;
			}
		}

		if (isShowGlobalEnabled()) {
			// draw global translation vector
			gl.glPushMatrix();
			gl.glColor3f(1, 1, 1);
			gl.glTranslatef(chip.getSizeX()/2,chip.getSizeY()/2,0);
			gl.glLineWidth(6f);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(0,0);
			Translation t=motionVectors.translation;
			int mult=chip.getMaxSize()/4;
			gl.glVertex2f(t.xFilter.getValue()*ppsScale*mult,t.yFilter.getValue()*ppsScale*mult);
			gl.glEnd();
			gl.glPopMatrix();

			// draw global rotation vector as line left/right
			gl.glPushMatrix();
			gl.glTranslatef(chip.getSizeX()/2, (chip.getSizeY()*3)/4,0);
			gl.glLineWidth(6f);
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2i(0,0);
			Rotation rot=motionVectors.rotation;
			int multr=chip.getMaxSize()*10;
			gl.glVertex2f(-rot.filter.getValue()*multr*ppsScale,0);
			gl.glEnd();
			gl.glPopMatrix();

			// draw global expansion as circle with radius proportional to expansion metric, smaller for contraction, larger for expansion
			if(glu==null) {
				glu=new GLU();
			}
			if(expansionQuad==null) {
				expansionQuad = glu.gluNewQuadric();
			}
			gl.glPushMatrix();
			gl.glTranslatef(chip.getSizeX()/2, (chip.getSizeY())/2,0);
			gl.glLineWidth(6f);
			Expansion e=motionVectors.expansion;
			int multe=chip.getMaxSize()*4;
			glu.gluQuadricDrawStyle(expansionQuad,GLU.GLU_FILL);
			double rad=(1+e.filter.getValue())*ppsScale*multe;
			glu.gluDisk(expansionQuad,rad,rad+1,16,1);
			gl.glPopMatrix();

			//            // draw expansion compass vectors as arrows pointing in.getOutputPacket() from origin
			//            gl.glPushMatrix();
			//            gl.glTranslatef(chip.getSizeX()/2, (chip.getSizeY())/2,0);
			//            gl.glLineWidth(6f);
			//            gl.glBegin(GL2.GL_LINES);
			//            gl.glVertex2i(0,0);
			//            gl.glVertex2f(0, (1+e.north.getValue())*multe*ppsScale);
			//            gl.glVertex2i(0,0);
			//            gl.glVertex2f(0, (-1-e.south.getValue())*multe*ppsScale);
			//            gl.glVertex2i(0,0);
			//            gl.glVertex2f((-1-e.west.getValue())*multe*ppsScale,0);
			//            gl.glVertex2i(0,0);
			//            gl.glVertex2f((1+e.east.getValue())*multe*ppsScale,0);
			//            gl.glEnd();
			//            gl.glPopMatrix();
		}

		if((dirPacket!=null) && isShowVectorsEnabled()){
			// draw individual motion vectors
			gl.glPushMatrix();
			gl.glColor4f(1f,1f,1f,0.7f);
			gl.glLineWidth(1f);
			gl.glBegin(GL.GL_LINES);
			int frameDuration=dirPacket.getDurationUs();
			for(Object o:dirPacket){
				MotionOrientationEvent e=(MotionOrientationEvent)o;
				drawMotionVector(gl,e,frameDuration);
			}
			gl.glEnd();
			gl.glPopMatrix();
		}
	}
	Random r = new Random();

	// plots a single motion vector which is the number of pixels per second times scaling
	void drawMotionVector(GL2 gl, MotionOrientationEvent e, int frameDuration) {
		float jx = 0, jy = 0;
		if (jitterVectorLocations) {
			jx = (r.nextFloat() - .5f) * jitterAmountPixels;
			jy = (r.nextFloat() - .5f) * jitterAmountPixels;
		}
		float startx=e.x+jx,starty=e.y+jy;
		gl.glVertex2f(startx,starty);
		MotionOrientationEvent.Dir d = MotionOrientationEvent.unitDirs[e.direction];
		float speed = e.speed * ppsScale;
		//        Point2D.Float vector=MotionOrientationEvent.computeMotionVector(e);
		//        float xcomp=(float)(vector.getX()*ppsScale);
		//        float ycomp=(float)(vector.getY()*ppsScale);
		//        gl.glVertex2d(e.x+xcomp, e.y+ycomp);
		// motion vector points in direction of motion, *from* dir value (minus sign) which points in direction from prevous event
		float endx=(e.x-(d.x*speed))+jx, endy=(e.y-(d.y*speed))+jy;
		gl.glVertex2f(endx,endy);
		// compute arrowhead
		final float headlength=1;
		float vecx=endx-startx, vecy=endy-starty; // orig vec
		float vx2=vecy, vy2=-vecx; // right angles +90 CW
		float arx=-vecx+vx2, ary=-vecy+vy2; // halfway between pointing back to origin
		float l=(float)Math.sqrt((arx*arx)+(ary*ary)); // length
		arx=(arx/l)*headlength; ary=(ary/l)*headlength; // normalize to headlength
		// draw arrow (half)
		gl.glVertex2f(endx,endy);
		gl.glVertex2f(endx+arx, endy+ary);
		// other half, 90 degrees
		gl.glVertex2f(endx,endy);
		gl.glVertex2f((endx+ary), endy-arx);
	}

	@Override
	synchronized public EventPacket filterPacket(EventPacket in) {
		// we use two additional packets: oriPacket which holds the orientation events, and dirPacket that holds the dir vector events
		oriPacket=oriFilter.filterPacket(in);  // compute orientation events.  oriFilter automatically sends bypassed events to oriPacket
		if(dirPacket==null) {
			dirPacket=new ApsDvsEventPacket(MotionOrientationEvent.class);
		}
		oriPacket.setOutputPacket(dirPacket); // so when we iterate over oriPacket we send the bypassed APS events to dirPacket
		checkMap();
		// filter
		lastNumInputCellTypes=in.getNumCellTypes();

		int n=oriPacket.getSize();

		// if the input is ON/OFF type, then motion detection doesn't make much sense because you are likely to detect
		// the nearest event from along the same edge, not from where the edge moved from.
		// therefore, this filter only really makes sense to use with an oriented input.
		//
		// when the input is oriented (e.g. the events have an orientation type) then motion estimation consists
		// of just checking in a direction *perpindicular to the edge* for the nearest event of the same input orientation type.

		// for each event write out an event of type according to the direction of the most recent previous event in neighbors
		// only write the event if the delta time is within two-sided threshold

		//        hist.reset();


		try{
			//            long stime=System.nanoTime();
			//            if(timeLimitEnabled) timeLimiter.start(getTimeLimitMs()); // ns from us by *1024
			OutputEventIterator outItr=dirPacket.outputIterator(); // this initializes the output iterator of dirPacket
			for(Object ein:oriPacket){ // as we iterate using the built-in next() method we will bypass APS events to the dirPacket outputPacket of oriPacket using its output iterator

				OrientationEvent e=(OrientationEvent)ein;
				int x=((e.x>>>subSampleShift)+P); // x and y are offset inside our timestamp storage array to avoid array access violations
				int y=((e.y>>>subSampleShift)+P);
				int polValue=((e.polarity==PolarityEvent.Polarity.On?1:2));
				byte type=(byte)(e.orientation*polValue); // type information here is mixture of input orientation and polarity, in order to match both characteristics
				int ts=e.timestamp;  // getString event x,y,type,timestamp of *this* event
				// update the map here - this is ok because we never refer to ourselves anyhow in computing motion
				lastTimesMap[x][y][type]=ts;

				// for each output cell type (which codes a direction of motion), find the dt
				// between the orientation cell type perdindicular
				// to this direction in this pixel and in the neighbor - but only find the dt in that single direction.

				// also, only find time to events of the same *polarity* and orientation. otherwise we will falsely match opposite polarity
				// orientation events which arise from two sides of edges

				// find the time of the most recent event in a neighbor of the same type as the present input event
				// but only in the two directions perpindiclar to this orientation. Each of these codes for motion but in opposite directions.

				// ori input has type 0 for horizontal (red), 1 for 45 deg (blue), 2 for vertical (cyan), 3 for 135 deg (green)
				// for each input type, check in the perpindicular directions, ie, (dir+2)%numInputCellTypes and (dir+4)%numInputCellTypes

				// this computation only makes sense for ori type input

				// neighbors are of same type
				// they are in direction given by unitDirs in lastTimesMap
				// the input type tells us which offset to use, e.g. for type 0 (0 deg horiz ori), we offset first in neg vert direction, then in positive vert direction
				// thus the unitDirs used here *depend* on orientation assignments in DirectionSelectiveFilter

				int dt1=0,dt2=0;
				int mindt1=Integer.MAX_VALUE, mindt2=Integer.MAX_VALUE;
				MotionOrientationEvent.Dir d;
				byte outType = e.orientation; // set potential output type to be same as type to start

				d=MotionOrientationEvent.unitDirs[e.orientation];

				int dist=1, dist1=1, dist2=1, dt=0, mindt=Integer.MAX_VALUE;

				if(!useAvgDtEnabled){
					// now iterate over search distance to find minimum delay between this input orientation event and previous orientiation input events in
					// offset direction
					for(int s=1;s<=searchDistance;s++){
						dt=ts-lastTimesMap[x+(s*d.x)][y+(s*d.y)][type]; // this is time between this event and previous
						if(dt<mindt1){
							dist1=s; // dist is distance we found min dt
							mindt1=dt;
						}
					}
					d=MotionOrientationEvent.unitDirs[e.orientation+4];
					for(int s=1;s<=searchDistance;s++){
						dt=ts-lastTimesMap[x+(s*d.x)][y+(s*d.y)][type];
						if(dt<mindt2){
							dist2=s; // dist is still the distance we have the global mindt
							mindt2=dt;
						}
					}
					if(mindt1<mindt2){ // if summed dt1 < summed dt2 the average delay in this direction is smaller
						dt=mindt1;
						outType=e.orientation;
						dist=dist1;
					}else{
						dt=mindt2;
						outType=(byte)(e.orientation+4);
						dist=dist2;
					}
					// if the time between us and the most recent neighbor event lies within the interval, write an output event
					if((dt<maxDtThreshold) && (dt>minDtThreshold)){
						float speed=(1e6f*dist)/dt;
						avgSpeed=((1-speedMixingFactor)*avgSpeed)+(speedMixingFactor*speed);
						if(speedControlEnabled && (speed>(avgSpeed*excessSpeedRejectFactor))) {
							continue;
						} // don't store event if speed too high compared to average
						MotionOrientationEvent eout=(MotionOrientationEvent)outItr.nextOutput();
						eout.copyFrom((OrientationEvent)ein);
						eout.direction=outType;
						eout.delay=(short)dt; // this is a actually the average dt for this direction
						//                    eout.delay=(short)mindt; // this is the mindt found
						eout.distance=(byte)dist;
						eout.speed=speed;
						eout.dir=MotionOrientationEvent.unitDirs[outType];
						eout.velocity.x=-speed*eout.dir.x; // these have minus sign because dir vector points towards direction that previous event occurred
						eout.velocity.y=-speed*eout.dir.y;
						//                    avgSpeed=speedFilter.filter(MotionOrientationEvent.computeSpeedPPS(eout),eout.timestamp);
						motionVectors.addEvent(eout);
						//                    hist.add(outType);
					}
				}else{
					// use average time to previous ori events
					// iterate over search distance to find average delay between this input orientation event and previous orientiation input events in
					// offset direction. only count event if it falls in acceptable delay bounds
					int n1=0, n2=0; // counts of passing matches, each direction
					float speed1=0, speed2=0; // summed speeds
					for(int s=1;s<=searchDistance;s++){
						dt=ts-lastTimesMap[x+(s*d.x)][y+(s*d.y)][type]; // this is time between this event and previous
						if(pass(dt)){
							n1++;
							speed1+=(float)s/dt; // sum speed in pixels/us
						}
					}

					d=MotionOrientationEvent.unitDirs[e.orientation+4];
					for(int s=1;s<=searchDistance;s++){
						dt=ts-lastTimesMap[x+(s*d.x)][y+(s*d.y)][type];
						if(pass(dt)){
							n2++;
							speed2+=(float)s/dt;
						}
					}

					if((n1==0) && (n2==0))
					{
						continue; // no pass
					}

					float speed=0;
					dist=searchDistance/2;
					if(n1>n2){
						speed=speed1/n1;
						outType=e.orientation;
					}else if(n2>n1){
						speed=speed2/n2;
						outType=(byte)(e.orientation+4);
					}else{
						if((speed1/n1)<(speed2/n2)){
							speed=speed1/n1;
							outType=e.orientation;
						}else{
							speed=speed2/n2;
							outType=(byte)(e.orientation+4);
						}
					}
					//                    dt/= (searchDistance); // dt is normalized by search disance because we summed over the whole search distance

					// if the time between us and the most recent neighbor event lies within the interval, write an output event
					if((n1>0) || (n2>0)){
						speed=1e6f*speed;
						avgSpeed=((1-speedMixingFactor)*avgSpeed)+(speedMixingFactor*speed);
						if(speedControlEnabled && (speed>(avgSpeed*excessSpeedRejectFactor))) {
							continue;
						} // don't output event if speed too high compared to average
						MotionOrientationEvent eout=(MotionOrientationEvent)outItr.nextOutput();
						eout.copyFrom((OrientationEvent)ein);
						eout.direction=outType;
						eout.delay=(short)(dist*speed);
						eout.distance=(byte)dist;
						eout.speed=speed;
						eout.dir=MotionOrientationEvent.unitDirs[outType];
						eout.velocity.x=-speed*eout.dir.x; // these have minus sign because dir vector points towards direction that previous event occurred
						eout.velocity.y=-speed*eout.dir.y;
						motionVectors.addEvent(eout);
					}
				}
			}
		}catch(ArrayIndexOutOfBoundsException e){
			e.printStackTrace();
			//            System.err.println("DirectionSelectiveFilter caught exception "+e+" probably caused by change of input cell type, reallocating lastTimesMap");
			checkMap();
		}

		if(isShowRawInputEnabled()) {
			return in;
		}
		return dirPacket; // returns the output packet containing both MotionOrientationEvent and the bypassed APS samples
	}

	private boolean pass(int dt){
		return ((dt<maxDtThreshold) && (dt>minDtThreshold));
	}

	public int getMaxDtThreshold() {
		return maxDtThreshold;
	}

	public void setMaxDtThreshold(final int maxDtThreshold) {
		this.maxDtThreshold = maxDtThreshold;
		putInt("maxDtThreshold",maxDtThreshold);
	}

	public int getMinDtThreshold() {
		return minDtThreshold;
	}

	public void setMinDtThreshold(final int minDtThreshold) {
		this.minDtThreshold = minDtThreshold;
		putInt("minDtThreshold", minDtThreshold);
	}

	@Override
	public void initFilter() {
		resetFilter();
	}

	@Override
	public void update(Observable o, Object arg) {
		initFilter();
	}

	public int getSearchDistance() {
		return searchDistance;
	}

	private Point2D.Float translationVector=new Point2D.Float();

	/** Returns the 2-vector of global translational average motion.
     @return translational motion in pixels per second, as computed and filtered by Translation
	 */
	public Point2D.Float getTranslationVector(){
		translationVector.x=motionVectors.translation.xFilter.getValue();
		translationVector.y=motionVectors.translation.yFilter.getValue();
		return translationVector;
	}


	/** @return rotational motion of image around center of chip in rad/sec as computed from the global motion vector integration */
	public float getRotationRadPerSec(){
		float rot=motionVectors.rotation.filter.getValue();
		return rot;
	}

	public static final int MAX_SEARCH_DISTANCE=12;

	synchronized public void setSearchDistance(int searchDistance) {
		if(searchDistance>MAX_SEARCH_DISTANCE) {
			searchDistance=MAX_SEARCH_DISTANCE;
		}
		else if(searchDistance<1)
		{
			searchDistance=1; // limit size
		}
		this.searchDistance = searchDistance;
		setPadding(searchDistance);
		allocateMap();
		putInt("searchDistance",searchDistance);
	}

	//    public VectorHistogram getHist() {
	//        return hist;
	//    }

	/** The motion vectors are the global motion components */
	public MotionVectors getMotionVectors() {
		return motionVectors;
	}

	public boolean isSpeedControlEnabled() {
		return speedControlEnabled;
	}

	public void setSpeedControlEnabled(boolean speedControlEnabled) {
		this.speedControlEnabled = speedControlEnabled;
		putBoolean("speedControlEnabled",speedControlEnabled);
	}


	public boolean isShowGlobalEnabled() {
		return showGlobalEnabled;
	}

	public void setShowGlobalEnabled(boolean showGlobalEnabled) {
		this.showGlobalEnabled = showGlobalEnabled;
		putBoolean("showGlobalEnabled",showGlobalEnabled);
	}

	private void setPadding (int searchDistance){
		PADDING = 2 * searchDistance;
		P = ( PADDING / 2 );
	}


	/** global translatory motion, pixels per second */
	public class Translation{
		LowpassFilter xFilter=new LowpassFilter(), yFilter=new LowpassFilter();
		Translation(){
			xFilter.setTauMs(tauLow);
			yFilter.setTauMs(tauLow);
		}
		void addEvent(MotionOrientationEvent e){
			int t=e.timestamp;
			xFilter.filter(e.velocity.x,t);
			yFilter.filter(e.velocity.y,t);
		}
	}

	/** rotation around center, positive is CCW, radians per second
     @see MotionVectors
	 */
	public class Rotation{
		LowpassFilter filter=new LowpassFilter();
		Rotation(){
			filter.setTauMs(tauLow);
		}
		void addEvent(MotionOrientationEvent e){
			// each event implies a certain rotational motion. the larger the radius, the smaller the effect of a given local motion vector on rotation.
			// the contribution to rotational motion is computed by dot product between tangential vector (which is closely related to radial vector)
			// and local motion vector.
			// if vx,vy is the local motion vector, rx,ry the radial vector (from center of rotation), and tx,ty the tangential
			// *unit* vector, then the tagential velocity is comnputed as v.t=rx*tx+ry*ty.
			// the tangential vector is given by dual of radial vector: tx=-ry/r, ty=rx/r, where r is length of radial vector
			// thus tangential comtribution is given by v.t/r=(-vx*ry+vy*rx)/r^2.

			int rx=e.x-(sizex/2), ry=e.y-(sizey/2);
			if((rx==0) && (ry==0))
			{
				return; // don't add singular event at origin
			}
			//            float phi=(float)Math.atan2(ry,rx); // angle of event in rad relative to center, 0 is rightwards
			//            float r=(float)Math.sqrt(rx*rx+ry*ry); // radius of event from center
			float r2=(rx*rx)+(ry*ry); // radius of event from center
			float dphi=( (-e.velocity.x*ry) + (e.velocity.y*rx) )/r2;
			int t=e.timestamp;
			filter.filter(dphi,t);
		}

	}

	/** @see MotionVectors */
	public class Expansion{
		// global expansion
		LowpassFilter filter=new LowpassFilter();
		// compass quadrants
		LowpassFilter north=new LowpassFilter(),south=new LowpassFilter(),east=new LowpassFilter(),west=new LowpassFilter();
		Expansion(){
			filter.setTauMs(tauLow);
		}
		void addEvent(MotionOrientationEvent e){
			// each event implies a certain expansion contribution. Velocity components in the radial direction are weighted
			// by radius; events that are close to the origin contribute more to expansion metric than events that are near periphery.
			// the contribution to expansion is computed by dot product between radial vector
			// and local motion vector.
			// if vx,vy is the local motion vector, rx,ry the radial vector (from center of rotation)
			// then the radial velocity is comnputed as v.r/r.r=(vx*rx+vy*ry)/(rx*rx+ry*ry), where r is radial vector.
			// thus in scalar units, each motion event contributes v/r to the metric.
			// this metric is exactly 1/Tcoll with Tcoll=time to collision.

			int rx=e.x-(sizex/2), ry=e.y-(sizey/2);
			final int f=2; // singular region
			//            if(rx==0 && ry==0) return; // don't add singular event at origin
			if(((rx>-f) && (rx<f)) && ((ry>-f) && (ry<f)))
			{
				return; // don't add singular event at origin
			}
			float r2=(rx*rx)+(ry*ry); // radius of event from center
			float dradial=( (e.velocity.x*rx) + (e.velocity.y*ry) )/r2;
			int t=e.timestamp;
			filter.filter(dradial,t);
			if((rx>0) && (rx>ry) && (rx>-ry)) {
				east.filter(dradial,t);
			}
			else if((ry>0) && (ry>rx) && (ry>-rx)) {
				north.filter(dradial,t);
			}
			else if((rx<0) && (rx<ry) && (rx<-ry)) {
				west.filter(dradial,t);
			}
			else {
				south.filter(dradial,t);
			}
		}
	}

	/** represents the global motion metrics from statistics of dir selective and simple cell events.
     The Translation is the global translational average motion vector (2 components).
     Rotation is the global rotation scalar around the center of the
     sensor. Expansion is the expansion or contraction scalar around center.
	 */
	public class MotionVectors{

		public Translation translation=new Translation();
		public Rotation rotation=new Rotation();
		public Expansion expansion=new Expansion();

		public void addEvent(MotionOrientationEvent e){
			translation.addEvent(e);
			rotation.addEvent(e);
			expansion.addEvent(e);
		}
	}

	public boolean isShowVectorsEnabled() {
		return showVectorsEnabled;
	}

	public void setShowVectorsEnabled(boolean showVectorsEnabled) {
		this.showVectorsEnabled = showVectorsEnabled;
		putBoolean("showVectorsEnabled",showVectorsEnabled);
	}

	public float getPpsScale() {
		return ppsScale;
	}

	/** scale for drawn motion vectors, pixels per second per pixel */
	public void setPpsScale(float ppsScale) {
		this.ppsScale = ppsScale;
		putFloat("ppsScale",ppsScale);
	}

	public float getSpeedMixingFactor() {
		return speedMixingFactor;
	}

	public void setSpeedMixingFactor(float speedMixingFactor) {
		if(speedMixingFactor>1) {
			speedMixingFactor=1;
		}
		else if(speedMixingFactor<Float.MIN_VALUE) {
			speedMixingFactor=Float.MIN_VALUE;
		}
		this.speedMixingFactor = speedMixingFactor;
		putFloat("speedMixingFactor",speedMixingFactor);

	}

	public int getExcessSpeedRejectFactor() {
		return excessSpeedRejectFactor;
	}

	public void setExcessSpeedRejectFactor(int excessSpeedRejectFactor) {
		this.excessSpeedRejectFactor = excessSpeedRejectFactor;
		putInt("excessSpeedRejectFactor",excessSpeedRejectFactor);
	}

	public int getTauLow() {
		return tauLow;
	}

	public void setTauLow(int tauLow) {
		this.tauLow = tauLow;
		putInt("tauLow",tauLow);
		motionVectors.translation.xFilter.setTauMs(tauLow);
		motionVectors.translation.yFilter.setTauMs(tauLow);
		motionVectors.rotation.filter.setTauMs(tauLow);
		motionVectors.expansion.filter.setTauMs(tauLow);
	}

	public boolean isShowRawInputEnabled() {
		return showRawInputEnabled;
	}

	public void setShowRawInputEnabled(boolean showRawInputEnabled) {
		this.showRawInputEnabled = showRawInputEnabled;
		putBoolean("showRawInputEnabled",showRawInputEnabled);
	}

	public boolean isUseAvgDtEnabled() {
		return useAvgDtEnabled;
	}

	public void setUseAvgDtEnabled(boolean useAvgDtEnabled) {
		this.useAvgDtEnabled = useAvgDtEnabled;
		putBoolean("useAvgDtEnabled",useAvgDtEnabled);
	}

	public int getSubSampleShift() {
		return subSampleShift;
	}

	/** Sets the number of spatial bits to subsample events times by. Setting this equal to 1, for example,
     subsamples into an event time map with halved spatial resolution, aggreating over more space at coarser resolution
     but increasing the search range by a factor of two at no additional cost
     @param subSampleShift the number of bits, 0 means no subsampling
	 */
	synchronized public void setSubSampleShift(int subSampleShift) {
		if(subSampleShift<0) {
			subSampleShift=0;
		}
		else if(subSampleShift>4) {
			subSampleShift=4;
		}
		this.subSampleShift = subSampleShift;
		putInt("subSampleShift",subSampleShift);
	}

	/**
	 * @return the jitterVectorLocations
	 */
	public boolean isJitterVectorLocations() {
		return jitterVectorLocations;
	}


	/**
	 * @param jitterVectorLocations the jitterVectorLocations to set
	 */
	public void setJitterVectorLocations(boolean jitterVectorLocations) {
		this.jitterVectorLocations = jitterVectorLocations;
		putBoolean("jitterVectorLocations", jitterVectorLocations);
		getChip().getAeViewer().interruptViewloop();
	}

	/**
	 * @return the jitterAmountPixels
	 */
	public float getJitterAmountPixels() {
		return jitterAmountPixels;
	}

	/**
	 * @param jitterAmountPixels the jitterAmountPixels to set
	 */
	public void setJitterAmountPixels(float jitterAmountPixels) {
		this.jitterAmountPixels = jitterAmountPixels;
		putFloat("jitterAmountPixels",jitterAmountPixels);
		getChip().getAeViewer().interruptViewloop();
	}



}
