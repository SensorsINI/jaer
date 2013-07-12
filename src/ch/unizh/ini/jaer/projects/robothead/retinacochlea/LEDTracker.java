/*
 * RetinaCochleaFilter.java
 *
 * Created on 29. Januar 2008, 10:26
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead.retinacochlea;


import java.awt.Graphics2D;
import java.util.Observable;
import java.util.Observer;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;

import com.jogamp.opengl.util.gl2.GLUT;


/**
 * 
 * This class divides the incoming Events into Retina and Cochlea Events.
 * The Retina Events are tracked by an enclosed RectangularClusterTracker, and
 * the Cluster information is provided.
 * the cochlea Events are converted to normal Cochlea Events (y value-64) and
 * passed on to the next filter...
 *
 * @author jaeckeld
 */
@Description("LED tracker which is part of David Jaeckel's robot head project")
public class LEDTracker extends EventFilter2D implements Observer, FrameAnnotater{

	private float minEventRate=getPrefs().getFloat("RetinaCochleaFilter.minEventRate",0.2f);
	private int minLifeTime=getPrefs().getInt("RetinaCochleaFilter.minLifeTime",10);
	private float maxSizeChange=getPrefs().getFloat("RetinaCochleaFilter.maxSizeChange",0.5f);


	RectangularClusterTracker tracker;
	public static RectangularClusterTracker.Cluster LED=null;
	RectangularClusterTracker.Cluster oneCluster;

	volatile private boolean doTracking;
	volatile private boolean LEDRecognized;

	private float oldSize;


	/**
	 * Creates a new instance of RetinaCochleaFilter
	 */
	public LEDTracker(AEChip chip) {
		super(chip);

		setPropertyTooltip("minEventRate", "Minimum Event rate to recognize LED");
		setPropertyTooltip("minLifeTime", "Minimum Lifetime to recognize LED");
		setPropertyTooltip("maxSizeChange", "Minimum Lifetime to recognize LED");

		tracker=new RectangularClusterTracker(chip);
		setEnclosedFilter(tracker);

		doTracking=true;

		initFilter();

	}


	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {

		if(in==null) {
			return null;
		}
		if(!filterEnabled) {
			return in;
		}

		if(isDoTracking()){
			tracker.filterPacket(in);     // track retina Events, but first rotates them 90 deg (with enclosed rotator)!!
			oneCluster=getLEDCluster();

			if (oneCluster!=null){
				float sizeChange=oneCluster.getMeasuredSizeCorrectedByPerspective()-oldSize;

				if((oneCluster.getAvgEventRate()>minEventRate) && (oneCluster.getLifetime()>(minLifeTime*1000)) && (sizeChange<maxSizeChange) && (oneCluster.getLocation().getX()<64)){     // TODO experiment with lifetime etc. to ensure safe detection...
					LED=oneCluster;
					LEDRecognized=true;
					//System.out.println(LED.getLocation().toString()+"     "+LED.getMeasuredSizeCorrectedByPerspective());

				}else{
					LED=null;
					LEDRecognized=false;
				}

				oldSize=oneCluster.getMeasuredSizeCorrectedByPerspective();

			}
		}
		//LED=oneCluster;

		// location.x = Höhe und y = Winkel bzw. horizontale Komponente, die ich betrachten muss...

		return in;

	}

	private RectangularClusterTracker.Cluster getLEDCluster(){
		if(tracker.getNumClusters()==0) {
			return null;
		}
		return tracker.getClusters().get(0);
	}

	EngineeringFormat fmt=new EngineeringFormat();

	@Override
	public void annotate(GLAutoDrawable drawable) {

		if(!isFilterEnabled()) {
			return;
		}
		GL2 gl=drawable.getGL().getGL2();
		//gl.glPushMatrix();
		final GLUT glut=new GLUT();
		gl.glColor3f(1,1,1);
		gl.glRasterPos3f(0,10,10);
		if(LEDRecognized){
			glut.glutBitmapString(GLUT.BITMAP_HELVETICA_12,String.format(" LED Detected: x = %s",fmt.format(LED.getLocation().getX())));
			glut.glutBitmapString(GLUT.BITMAP_HELVETICA_12,String.format(" y = %s",fmt.format(LED.getLocation().getY())));
		}
	}

	/** not used */
	public void annotate(float[][][] frame) {
	}

	/** not used */
	public void annotate(Graphics2D g) {
	}
	public Object getFilterState() {
		return null;
	}

	public float getMinEventRate(){
		return minEventRate;
	}

	public void setMinEventRate(float minEventRate){
		getPrefs().putFloat("RetinaCochleaFilter.minEventRate",minEventRate);
		getSupport().firePropertyChange("minEventRate",this.minEventRate,minEventRate);
		this.minEventRate=minEventRate;
	}
	public int getMinLifeTime(){
		return minLifeTime;
	}

	public void setMinLifeTime(int minLifeTime){
		getPrefs().putInt("RetinaCochleaFilter.minLifeTime",minLifeTime);
		getSupport().firePropertyChange("minLifeTime",this.minLifeTime,minLifeTime);
		this.minLifeTime=minLifeTime;
	}

	public float getMaxSizeChange(){
		return maxSizeChange;
	}

	public void setMaxSizeChange(float maxSizeChange){
		getPrefs().putFloat("RetinaCochleaFilter.maxSizeChange",maxSizeChange);
		getSupport().firePropertyChange("maxSizeChange",this.maxSizeChange,maxSizeChange);
		this.maxSizeChange=maxSizeChange;
	}

	@Override
	public void resetFilter(){
		//        System.out.println("reset!");


	}
	@Override
	public void initFilter(){
		System.out.println("init!");

	}

	@Override
	public void update(Observable o, Object arg){
		initFilter();
	}
	public boolean isLEDRecognized(){
		return LEDRecognized;
	}


	public void setDoTracking(boolean set){
		doTracking=set;
	}
	public boolean isDoTracking(){
		return doTracking;
	}

	public RectangularClusterTracker.Cluster getLED(){
		if(isLEDRecognized()) {
			return LED;
		}
		else {
			return null;
		}
	}

}

