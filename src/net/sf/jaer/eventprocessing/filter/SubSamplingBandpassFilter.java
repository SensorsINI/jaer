/*
 * SubSamplingBandpassFilter.java
 *
 * Created on May 13, 2006, 7:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 13, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.eventprocessing.filter;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
/**
Does an event-based spatio-temporal highpass filter,
so that only small isolated objects  pass through.
Uses a subsampled surround for very high efficiency.
Incoming events are subsampled and their times are stored in a subsample
event time map. Each entry in this map keeps track of the latest event time for this
subsampled location, thus it represents activity in this region of input space.
This map is used to determine if an event has been preceeded
by surrouding activity. The use of prior subsampling means that only a
small number of memory locations need to be checked to see if the event has sufficiently small
surround activity.
<br>
<img src="doc-files/subSampler.png">

 * @author tobi
 */
@Description("Does an event-based spatio-temporal highpass filter, so that only small isolated objects  pass through. Uses a subsampled surround for very high efficiency.")
public class SubSamplingBandpassFilter extends EventFilter2D implements Observer,FrameAnnotater{
	public static DevelopmentStatus getDevelopmentStatus(){
		return DevelopmentStatus.Beta;
	}
	private int surroundScale = getPrefs().getInt("SubSamplingBandpassFilter.surroundScale",1);
	private int surroundRadius = getPrefs().getInt("SubSamplingBandpassFilter.surroundRadius",1);
	private float surroundRateThresholdHz = getPrefs().getFloat("SubSamplingBandpassFilter.surroundRateThresholdHz",10);
	private float rateMixingFactor = getPrefs().getFloat("SubSamplingBandpassFilter.rateMixingFactor",0.01f),  rateMixingFactorMinus = 1 - rateMixingFactor;
	private int surSizeX,  surSizeY; // size of subsampled surround timestamp array
	/** the time in timestamp ticks (1us at present) that a spike in surround
    will inhibit a spike from center passing through.
	 */
	private int dtSurround = getPrefs().getInt("SubSamplingBandpassFilter.dtSurround",8000);
	int[][] surroundTimestamps, centerTimestamps;
	float[][] surroundRates;

	/**
	 * Creates a new instance of SubSamplingBandpassFilter
	 */
	public SubSamplingBandpassFilter (AEChip c){
		super(c);
		chip.addObserver(this);
		initFilter();
		setShowAnnotationEnabled(false);
		setPropertyTooltip("dtSurround","Sets the time in timestamp ticks (1us at present) that a spike in surround will inhibit a spike from center passing through.");
		setPropertyTooltip("surroundScale","Sets the scale in bits of the surround that is searched for prior events. The search occurs over the subsampled map whose scale is set by surroundScale.");
		setPropertyTooltip("surroundRadius","Sets the radius in 'pixels' of the surround that is searched for prior events. The search occurs over the subsampled map whose scale is set by surroundScale.");
		setPropertyTooltip("surroundRateThresholdHz","If the average spike rate in the surround exceeds this value in Hz then the spike is filtered out.");
		setPropertyTooltip("showAnnotationEnabled","True shows the scale of the surround as a grid.");
		setPropertyTooltip("rateMixingFactor","Increase to make the surround event rate more quickly reflect the activity.");
	}

	@Override
	synchronized public void setFilterEnabled (boolean yes){
		super.setFilterEnabled(yes);
		if ( !yes ){
			// free memory
			surroundTimestamps = null;
			centerTimestamps = null;
		} else{
			initFilter();
		}
	}

	public Object getFilterState (){
		return null;
	}

	@Override
	public void resetFilter (){
		if ( surroundTimestamps != null ){
			for (int[] surroundTimestamp : surroundTimestamps) {
				Arrays.fill(surroundTimestamp,Integer.MAX_VALUE);
			}
		}
		//        if(surroundRates!=null){
		//            for(int i=0;i<surroundRates.length;i++){
		//                Arrays.fill(surroundRates[i],0);
		//            }
		//        }
		//        initFilter();
	}

	@Override
	synchronized public void update (Observable o,Object arg){
		initFilter();
	}

	@Override
	synchronized public void initFilter (){
		if ( chip.getMaxSize() == 0 ){
			return; // this happens on super init of AERetina/AEChip
		}
		setSurroundRadius(surroundRadius);
		setSurroundScale(surroundScale);
		computeOffsets();
	}

	void checkMaps (){
		surSizeX = chip.getSizeX() / ( 1 << surroundScale );
		surSizeY = chip.getSizeY() / ( 1 << surroundScale );
		if ( (surroundTimestamps == null) || (surroundTimestamps.length != surSizeX) || (surroundTimestamps[0].length != surSizeY) ){
			allocateMaps();
		}
	}

	void allocateMaps (){
		surroundTimestamps = new int[ surSizeX ][ surSizeY ];
		surroundRates = new float[ surSizeX ][ surSizeY ];
	}
	// Offset is a relative position
	final class Offset{
		int x, y;

		Offset (int x,int y){
			this.x = x;
			this.y = y;
		}

		@Override
		public String toString (){
			return x + "," + y;
		}
	}
	// these arrays hold relative offsets to write to for center and surround timestamp splatts
	Offset[] centerOffsets, surroundOffsets;

	/** computes an array of offsets that we write to when we getString an event. */
	private synchronized void computeOffsets (){
		ArrayList<Offset> surList = new ArrayList<Offset>();
		//        ArrayList<Offset> cenList=new ArrayList<Offset>();
		int x, y;
		// march around CCW each time writing from corner to just before next corner
		y = -surroundRadius;
		for ( x = -surroundRadius ; x < surroundRadius ;
			x++ ){
			surList.add(new Offset(x,y));
		}
		x = surroundRadius;
		for ( y = -surroundRadius ; y < surroundRadius ;
			y++ ){
			surList.add(new Offset(x,y));
		}
		y = surroundRadius;
		for ( x = surroundRadius ; x > -surroundRadius ; x-- ){
			surList.add(new Offset(x,y));
		}
		x = -surroundRadius;
		for ( y = surroundRadius ; y > -surroundRadius ; y-- ){
			surList.add(new Offset(x,y));
		}
		surroundOffsets = new Offset[ 1 ];
		surroundOffsets = surList.toArray(surroundOffsets);
		//        log.info("checking "+surroundOffsets.length+" neighbors for each event");
	}
	//    public int getDtCenter() {
	//        return dtCenter;
	//    }
	//
	//    public void setDtCenter(int dtCenter) {
	//        this.dtCenter = dtCenter;
	//        prefs.putInt("SubSamplingBandpassFilter.dtCenter",dtCenter);
	//    }

	public int getDtSurround (){
		return dtSurround;
	}

	/** sets the time in timestamp ticks (1us at present) that a spike in surround
    will inhibit a spike from center passing through.
    @param dtSurround the time in us
	 */
	public void setDtSurround (int dtSurround){
		this.dtSurround = dtSurround;
		getPrefs().putInt("SubSamplingBandpassFilter.dtSurround",dtSurround);
	}

	public int getSurroundScale (){
		return surroundScale;
	}

	/**
	 * Set the scale of the surround map subsampling in bits. Clipped to minimum value 0 (no subsampling) up to
    a maximum value of log_2(chip.getMaxSize()).
	 *
	 *
	 * @param surroundScale the number of bits of subsampling, e.g. 1 means subsample every 2x2 area to a single address.
	 */
	synchronized public void setSurroundScale (int surroundScale){
		// if chip is default, not initialized, then size will be zero, don't do anything
		int n = chip.getMaxSize();
		if ( n == 0 ){
			return;
		}
		int nbitsmax = (int)Math.round(Math.log(n) / Math.log(2));
		if ( surroundScale < 0 ){
			surroundScale = 0;
		} else if ( surroundScale >= nbitsmax ){
			surroundScale = nbitsmax;
		}
		this.surroundScale = surroundScale;
		//        subSampler.setBits(surroundScale);
		surSizeX = chip.getSizeX() / ( 1 << surroundScale );
		surSizeY = chip.getSizeY() / ( 1 << surroundScale );
		getPrefs().putInt("SubSamplingBandpassFilter.surroundScale",surroundScale);
		computeOffsets();
	}

	public int getSurroundRadius (){
		return surroundRadius;
	}

	/** sets the surround radius. This value is clipped to be at least 1 and at most the maximum possible search distance given
    the surroundScale. This value sets the radius in 'pixels' of the surround
    that is searched for prior events. The search occurs over the subsampled map whose scale is set by surroundScale.
    @param surroundRadius the radius in pixels for a square area. 1 is 8 pixels (3x3 minus center), etc.
    @see #setSurroundScale
	 */
	synchronized public void setSurroundRadius (int surroundRadius){
		// problem is that chip is null or default when first called, so size gets set to zero....
		int max = chip.getMaxSize() / ( 1 << getSurroundScale() );
		if ( max == 0 ){
			return;
		}
		if ( surroundRadius < 1 ){
			surroundRadius = 1;
		} else if ( surroundRadius > max ){
			surroundRadius = max;
		}
		this.surroundRadius = surroundRadius;
		getPrefs().putInt("SubSamplingBandpassFilter.surroundRadius",surroundRadius);
		computeOffsets();
	}

	@Override
	synchronized public EventPacket filterPacket (
		EventPacket in){
		if ( enclosedFilter != null ){
			in = enclosedFilter.filterPacket(in);
		}
		checkOutputPacketEventType(in);
		checkMaps();
		int n = in.getSize();
		if ( n == 0 ){
			return in;
		}
		int bits = surroundScale;

		// for each event, we only output the event if there has NOT been an event in the subsampled map in the surround ring in the past dt.
		// therefore we first check for a previous event in the surround, then we write the subsampled event to the map

		// iterate over this at the same time
		OutputEventIterator o =
			out.outputIterator();

		for ( Object obj:in ){
			PolarityEvent i = (PolarityEvent)obj;
                        if(i.isSpecial()) continue;
			// if the event occurred too close after a surround spike don't pass it.
			if ( filterEvent(i) ){
				o.nextOutput().copyFrom(i);
			}
		}
		return out;
	}

	/**
    Has two functions: Updates subsampled event rate map, and checks event to see if
    surround activity is low enough to pass it.
    <p>
    The surround activity and timestamp maps are subsampled
    (that's why checking them is fast).
    The instantaneous activity of this event is
    computed from the difference between
    this event's timestamp and any previous event in this subfield (location) in the
    subsampled timestamp map. Thus the rate reflects the averarge rate of the pool
    of cells in this subfield, not the rate of cells within the pool (all the cells are
    lumped together).
    The average rate in a subfield is computed by mixing it using a mixing factor with
    prior estimates of rate. Therefore higher activity will update the rate
    more quickly. Thus the time constant of this update is not constant. The mixing
    factor defines the
    "event constant".

    @return false to NOT let event through, true to pass it.
	 */
	private boolean filterEvent (BasicEvent i){
		boolean ret = true;
		int sx = i.x >> surroundScale; // getString subsampled x addr
		int sy = i.y >> surroundScale; // subsampled y addr
		int x = 0, y = 0, dt, st;
		float sumRate = 0f;
		float instanRate;
		//        int sumdt=0;
		int count = 0;
		// first compute the subsampled rate estimate and store it for future event use
		st = surroundTimestamps[sx][sy];
		dt = i.timestamp - st;
		if ( dt <= 0 ){
			dt = 1;
		}
		instanRate = (1e6f * AEConstants.TICK_DEFAULT_US) / dt;
		float r = surroundRates[sx][sy]; // stored avg rate in this subfield
		r = (rateMixingFactorMinus * r) + (rateMixingFactor * instanRate);
		surroundRates[sx][sy] = r;

		// now check surround to see if we can pass this event
		for ( Offset d:surroundOffsets ){
			// for each offset compute lookup into surroundTimestamps
			x = sx + d.x;
			if ( (x < 0) || (x >= surSizeX) ){
				continue;
			}
			y = sy + d.y;
			if ( (y < 0) || (y >= surSizeY) ){
				continue; // bounds checking
			}            // since the surround won't be updated unless there is an event in it, it will act like a peak detector.
			// thus we force an update towards zero rate anytime any event 'touches' a subfield
			surroundRates[x][y] *= rateMixingFactorMinus;
			sumRate += surroundRates[x][y]; // to compute avg surround rate
			count++; // count num summed
		}

		sumRate /= count; // compute avg surround rate
		if ( sumRate > surroundRateThresholdHz ){
			ret = false;
		}

		surroundTimestamps[sx][sy] = i.timestamp; // save this events timestamp in the subsampled map
		return ret;
	}

	public void annotate (float[][][] frame){
	}

	public void annotate (Graphics2D g){
	}

	@Override
	public void annotate (GLAutoDrawable drawable){
		if ( !isAnnotationEnabled() ){
			return;
		}
		GL2 gl = drawable.getGL().getGL2();
		gl.glColor3f(0,0,1);
		gl.glLineWidth(.2f);
		int n = 1 << surroundScale;
		int sy = chip.getSizeY();
		gl.glPushMatrix();
		gl.glBegin(GL.GL_LINES);
		for ( int i = 1 ; i < surSizeX ;
			i++ ){
			gl.glVertex2i(i * n,0);
			gl.glVertex2i(i * n,sy);
		}
		int sx = chip.getSizeX();
		for ( int i = 1 ; i < surSizeY ;
			i++ ){
			gl.glVertex2i(0,i * n);
			gl.glVertex2i(sx,i * n);
		}
		gl.glEnd();
		gl.glPopMatrix();
	}

	public boolean isShowAnnotationEnabled (){
		return isAnnotationEnabled();
	}

	public void setShowAnnotationEnabled (boolean showAnnotationEnabled){
		setAnnotationEnabled(showAnnotationEnabled);
	}

	public float getsurroundRateThresholdHz (){
		return surroundRateThresholdHz;
	}

	/** If the surround spike rate exceeds this value in Hz then the spike is filtered out (it is inhibited).
	 */
	public void setsurroundRateThresholdHz (float surroundRateThresholdHz){
		this.surroundRateThresholdHz = surroundRateThresholdHz;
		getPrefs().putFloat("SubSamplingBandpassFilter.surroundRateThresholdHz",surroundRateThresholdHz);
	}

	public float getRateMixingFactor (){
		return rateMixingFactor;
	}

	public void setRateMixingFactor (float rateMixingFactor){
		if ( rateMixingFactor < 0 ){
			rateMixingFactor = 0;
		} else if ( rateMixingFactor > 1 ){
			rateMixingFactor = 1;
		}
		this.rateMixingFactor = rateMixingFactor;
		getPrefs().putFloat("SubSamplingBandpassFilter.rateMixingFactor",rateMixingFactor);
		rateMixingFactorMinus = 1 - rateMixingFactor;
	}
}
