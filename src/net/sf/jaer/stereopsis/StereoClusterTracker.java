/*
 * StereoClusterTracker.java
 *
 * Created on July 24, 2006, 7:58 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 24, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.stereopsis;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Random;
import java.util.logging.Logger;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTrackerEvent;
import net.sf.jaer.util.PlayWavFile;
import net.sf.jaer.util.filter.LowpassFilter3D;

import com.jogamp.opengl.util.gl2.GLUT;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.util.DrawGL;
/**
 * Extends ClusterTracker to track objects in 3-d space. The StereoCluster is extended to include disparity information and the events
 * used to track are BinocularEvents.
 *
 * @author tobi
 */
@Description("Extends RectangularClusterTracker to track BinocularEvents by adding a disparity to each BinocularCluster")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class StereoClusterTracker extends RectangularClusterTracker{
	private Logger log = Logger.getLogger ("StereoClassTracker");
	private float velocityMixingFactor = getFloat ("velocityMixingFactor",.001f);
	StereoChipInterface stereoChip = null;
	/** list of Clusters, this field hides the super's 2d image plane Cluster's */
	//    protected ArrayList<Cluster> clusters = new ArrayList<Cluster> ();
	StereoGeometry geom;
	private boolean logDataEnabled = false;
	PrintStream logStream = null;
	private float[] p = new float[ 3 ]; // used for 3d location and velocityPPT computation
	private float[] v = new float[ 3 ]; // used for 3d velocityPPT computation
	static final float TICK_SECONDS = 1e-6f;
	private boolean playSounds = getBoolean("playSounds",false);
	private float soundDispVelThr = getFloat ("soundDispVelThr",1000);
	private float disparityMixingFactor=getFloat("disparityMixingFactor",mixingFactor);
	private boolean displayStereoClusterAnnotation=getBoolean("displayStereoClusterAnnotation",true);

	/** Creates a new instance of StereoClusterTracker */
	public StereoClusterTracker (AEChip chip){
		super (chip);
		if ( (chip != null) && (chip instanceof StereoChipInterface) ){
			stereoChip = (StereoChipInterface)chip;
		} else{
			log.warning ("AEChip " + chip + " is not StereoChipInterface");
		}
		setEnclosedFilter(new StereoTranslateRotate(chip));
		geom = new StereoGeometry(chip);
		String stereotips="Stereo";
		setPropertyTooltip(stereotips,"playSounds", "play random wave file when disparity velocity towards us exceeds soundDispVelThr");
		setPropertyTooltip(stereotips,"soundDispVelThr", "when disparity velocity exceeds this threshold play sound effect (punch detector)");
		setPropertyTooltip(stereotips,"disparityMixingFactor", "mixing factor for cluster disparity value; reduce to smooth more");
		setPropertyTooltip(stereotips,"displayStereoClusterAnnotation", "draw disparity info for clusters");
		setPropertyTooltip(stereotips,"velocityMixingFactor","fraction by which velocity of cluster is updated by each event");
	}

	@Override
	public synchronized EventPacket filterPacket (EventPacket in){
		if ( in == null ){
			return null;
		}
		if (  ! filterEnabled ){
			return in;
		}
		if ( enclosedFilter != null ){
			in = enclosedFilter.filterPacket (in);
		}
		track (in);
		return in;
	}



	/**
	 * returns the physically nearest (to observer) visible cluster based on maximum disparity. Not to be confused with method
	 *     getNearestCluster(BasicEvent ev) that returns the closest cluster to an event in the image plane.
	 *     A cluster is only returned if has received enough support to become visible.
	 *
	 *
	 * @return closest cluster, or null if there are no clusters
	 */
	public StereoCluster getNearestCluster (){
		if ( (clusters == null) || (clusters.size () == 0) ){
			return null;
		}
		float maxDisparity = Float.NEGATIVE_INFINITY;
		StereoCluster cluster = null;
		for ( Cluster x:clusters ){
			StereoCluster c=(StereoCluster)x;
			if ( c.getDisparity () > maxDisparity ){
				cluster = c;
				maxDisparity = c.getDisparity ();
			}
		}
		if ( (cluster != null) && cluster.isVisible () ){
			return cluster;
		} else{
			return null;
		}
	}

	/** Given AE, returns first (thus oldest) cluster that event is within.
    The radius of the cluster here depends on whether size scaling is enabled.
	 * @param event the event
	 * @return cluster that contains event within the cluster's radius, modfied by aspect ratio. null is returned if no cluster is close enough.
	 */
	@Override
	public StereoCluster getFirstContainingCluster (BasicEvent event){
		if ( clusters.isEmpty () ){
			return null;
		}
		float minDistance = Float.MAX_VALUE;
		StereoCluster closest = null;
		float currentDistance = 0;
		for ( Cluster x:clusters ){
			StereoCluster c=(StereoCluster)x;
			if ( ( currentDistance = c.distanceTo (event) ) < minDistance ){
				closest = c;
				minDistance = currentDistance;
			}
		}

		if ( (closest != null) && (minDistance <= closest.getRadius ()) ){
			return closest;
		} else{
			return null;
		}
	}


	//    /** Factory method to create a new Cluster; override when subclassing Cluster.
	//     *
	//     * @return a new empty Cluster
	//     */
	//    @Override
	//    public Cluster createCluster() {
	//        return new StereoCluster();
	//    }

	/** Factory method to create a new Cluster; override when subclassing Cluster.
	 * @param ev the spawning event.
	 * @return a new empty Cluster
	 */
	@Override
	public Cluster createCluster(BasicEvent ev) {
		return new StereoCluster(ev);
	}

	/** Factory method to create a new Cluster; override when subclassing Cluster.
	 * @param one the first cluster.
	 * @param two the second cluster.
	 * @return a new empty Cluster
	 */
	@Override
	public Cluster createCluster(Cluster one, Cluster two) {
		return new StereoCluster((StereoCluster)one, (StereoCluster)two);
	}

	/** Factory method to create a new Cluster; override when subclassing Cluster.
	 *
	 * @param ev the spawning event.
	 * @param itr the output iterator to write events to when they fall in this cluster.
	 * @return a new empty Cluster
	 */
	@Override
	public Cluster createCluster(BasicEvent ev, OutputEventIterator itr) {
		return new StereoCluster(ev, itr);
	}

	@Override
	synchronized protected EventPacket track (EventPacket ae){
		super.track(ae);

		// iterate over all clusters, updating them for outside values that user may want
		// update position and velocities in 3d space
		for ( Cluster x:clusters ){
			StereoCluster c=(StereoCluster)x;

			// update location in 3d physical space
			geom.compute3dlocationM (c.location.x,c.location.y,c.getDisparity (),p);
			c.location3dm.x = p[0];
			c.location3dm.y = p[1];
			c.location3dm.z = p[2];

			// update velocityPPT in 3d physical space
			geom.compute3dVelocityMps (p,c.getDisparity (),c.getVelocityPPS ().x,c.getVelocityPPS ().y,c.disparityVelocity,v);
			c.velocity3dmps.x = v[0];
			c.velocity3dmps.y = v[1];
			c.velocity3dmps.z = v[2];

			// update paths of clusters
			c.updatePath (ae.getLastTimestamp());
		}

		if ( isLogDataEnabled () && (getNumClusters () == 1) && clusters.get (0).isVisible () && (((StereoCluster)(clusters.get (0))).getDisparity () > 4) ){
			//            System.out.println(ae.getLastTimestamp()/1e6f+" "+clusters.getString(0));
			if ( logStream != null ){
				StereoCluster c = (StereoCluster)(clusters.get (0));
				logStream.println (String.format ("%d %f %f %f %f",clusterCounter,ae.getLastTimestamp () / 1e6f,c.location3dm.x,c.location3dm.y,c.location3dm.z));
				if ( logStream.checkError () ){
					log.warning ("eroror logging data");
				}
			}
		}

		//        if(clusters.size()>beforeMergeCount) throw new RuntimeException("more clusters after merge than before");
		return ae;

	}

	@Override
	public void setMaxNumClusters(int maxNumClusters) {
		super.setMaxNumClusters(maxNumClusters);
		resetFilter();
	}

	@Override
	synchronized public void resetFilter (){
		clusters.clear ();
	}

	/**
	 * @return the playSounds
	 */
	public boolean isPlaySounds (){
		return playSounds;
	}

	/**
	 * @param playSounds the playSounds to set
	 */
	public void setPlaySounds (boolean playSounds){
		this.playSounds = playSounds;
		putBoolean("playSounds",playSounds);
	}

	/**
	 * @return the soundDispVelThr
	 */
	public float getSoundDispVelThr (){
		return soundDispVelThr;
	}

	/**
	 * @param soundDispVelThr the soundDispVelThr to set
	 */
	public void setSoundDispVelThr (float soundDispVelThr){
		this.soundDispVelThr = soundDispVelThr;
		putFloat("soundDispVelThr",soundDispVelThr);
	}

	/**
	 * @return the disparityMixingFactor
	 */
	public float getDisparityMixingFactor (){
		return disparityMixingFactor;
	}

	/**
	 * @param disparityMixingFactor the disparityMixingFactor to set
	 */
	public void setDisparityMixingFactor (float disparityMixingFactor){
		this.disparityMixingFactor = disparityMixingFactor;
		if(disparityMixingFactor>1) {
			disparityMixingFactor=1;
		}
		else if(disparityMixingFactor<0) {
			disparityMixingFactor=0;
		}
		putFloat("disparityMixingFactor",disparityMixingFactor);
	}
	/**
    Extends the 2-d cluster to include 2.5-d disparity information. Adding an event
    updates the cluster's disparity value. The events added from left and right eye
    can have different x,y locations depending on misalignment and disparity. The cluster, however, has
    a single location in x,y,disparity space. When an event is added, the cluster disparity value is taken
    account of for determining the distance of the event to the cluster.

	 */
	public class StereoCluster extends RectangularClusterTracker.Cluster{

		/**
        the disparity of the cluster in pixels,
        i.e. the shift needed to bring one eye's view in registration with the other's
		 */
		private float disparity = 0;
		private float disparityVelocity = 0; // rate of change of disparity in pixels/second
		/** location of cluster in 3d space as computed from pixel location and disparity, given chip's StereoGeometry.
        Coordinate frame is centered on bridge of viewers nose and z increases with distance. Units are meters.
        x increases from 0 rightwards from midline in image coordinates (larger to right in image) and y increases upwards in the same way
		 */
		public LowpassFilter3D.Point3D location3dm = new LowpassFilter3D.Point3D ();
		/** velocityPPT of cluster in 3d space as computed from pixel location and disparity, given chip's StereoGeometry.
        Coordinate frame is centered on bridge of viewers nose and z increases with distance. Units are meters per second.
        x increases from 0 rightwards from midline in image coordinates (larger to right in image) and y increases upwards in the same way
		 */
		public LowpassFilter3D.Point3D velocity3dmps = new LowpassFilter3D.Point3D ();

		public StereoCluster (){
			super();
		}

		/** Constructs a new cluster centered on an event
        @param ev the event
		 */
		public StereoCluster (BasicEvent ev){
			super (ev);
			//        log.info("creating new StereoCluster from "+ev);
		}

		/** Creates a new Cluster using the event and generates a new output event which points back to the Cluster.
		 *
		 * @param ev the event to center the cluster on.
		 * @param outItr used to generate the new event pointing back to the cluster.
		 */
		protected StereoCluster(BasicEvent ev, OutputEventIterator outItr) {
			this(ev);
			if (!isVisible()) {
				return;
			}
			RectangularClusterTrackerEvent oe = (RectangularClusterTrackerEvent) outItr.nextOutput();
			oe.copyFrom(ev);
			oe.setCluster(this);
		}


		/** Constructs a cluster by merging two clusters */
		public StereoCluster (StereoCluster one,StereoCluster two){
			this ();
			super.mergeTwoClustersToThis(one, two);
			StereoCluster older = one.firstEventTimestamp < two.firstEventTimestamp ? one : two;
			disparity = older.disparity;
			disparityVelocity = older.disparityVelocity;  // don't forget the other fields!!!
			velocityPPS=older.velocityPPS;
			velocityPPT=older.velocityPPT;

		}

		/** adds one BasicEvent event to this cluster, updating its parameters in the process
        @param event the event to appendCopy
		 */
		@Override
		public void addEvent (BasicEvent event){
			addEvent ((BinocularEvent)event);
		}




		/** adds one BinocularEvent to this cluster, updating its parameters in the process
        @param event the event to appendCopy
		 */
		public void addEvent (BinocularEvent event){
			super.addEvent(event);

			float oldDisparity = updateDisparity(event);
			float dt = TICK_SECONDS * ( event.timestamp - lastEventTimestamp );
			if(dt>0){
				float dv = ( disparity - oldDisparity ) / dt;
				disparityVelocity = ((1-disparityMixingFactor) * disparityVelocity) + (disparityMixingFactor * dv);
				if ( playSounds && (disparityVelocity < -soundDispVelThr) ){
					soundPlayer.playRandom ();
				}
			}
		}

		/** Updates position of cluster given that event comes from right or left eye and the cluster
		 * has a disparity.
		 * @param event
		 * @param m
		 */
		@Override
		protected void updatePosition(BasicEvent e, float m) {
			BinocularEvent event = (BinocularEvent) e;
			float m1 = 1 - m;
			float d=event.eye==BinocularEvent.Eye.RIGHT?-disparity:disparity;

			location.x = ((m1 * location.x) + (m * (event.x+(d/2))));
			location.y = ((m1 * location.y) + (m * event.y));

		}



		private float updateDisparity(BinocularEvent event) {
			float thisEventDisparity = 0;
			// if we appendCopy events from each eye, moviing disparity and location according to each event, then a mismatch
			// in the number of events from each eye will putString cluster location closer to this eye; eventually the cluster center
			// will move so far away from one eye that that eye's inputs will be outside the cluster
			// and disparity tracking will be lost.
			switch ( event.eye ){
				case LEFT:
					thisEventDisparity = 2 * ( location.x - event.x ); // event left of cluster location makes disparity more negative
					break;
				case RIGHT:
					thisEventDisparity = -2 * ( location.x - event.x ); // right eye, if event to right of location it makes disparity more positive
					break;
				default:
					log.warning ("BinocularEvent doesn't have Eye type");
			}
			float oldDisparity = disparity;
			disparity = ((1-disparityMixingFactor) * disparity) + (disparityMixingFactor * thisEventDisparity);
			return oldDisparity;
		}

		/** Computes distance of cluster to event
        @param event the event
        @return distance of this cluster to the event in manhattan (cheap) metric (sum of abs values of x and y distance
		 */
		@Override
		public float distanceTo (BasicEvent event){
			BinocularEvent e = (BinocularEvent)event;
			float dx = 0;
			switch ( e.eye ){
				case LEFT:
					dx = event.x - ( location.x - (disparity / 2) );
					break;
				case RIGHT:
					dx = event.x - ( location.x + (disparity / 2) );
					break;
				default:
					log.warning ("BinocularEvent doesn't have Eye type");
			}
			final float dy = event.y - location.y;
			//            return Math.abs(dx)+Math.abs(dy);
			return distanceMetric (dx,dy);
			//            dx*=dx;
			//            dy*=dy;
			//            float distance=(float)Math.sqrt(dx+dy);
			//            return distance;
		}

		/**
		 * Computes distance to another cluster
		 *
		 * @param c the other StereoCluster
		 * @return distance of this cluster to the other cluster in pixels
		 */
		protected final float distanceTo (StereoCluster c){
			float dx = c.location.x - location.x;
			float dy = c.location.y - location.y;
			return distanceMetric (dx,dy);
			//            if(dx<0)dx=-dx;
			//            if(dy<0)dy=-dy;
			//            dx*=dx;
			//            dy*=dy;
			//            float distance=(float)Math.sqrt(dx+dy);
			//            distance=dx+dy;
			//            return distance;
		}

		@Override
		public String toString (){
			String s = String.format ("%s disp=%.1f, dispVel=%.1f, x,y,z=%.1f, %.1f, %.1f, vx,vy,vz=%.1f, %.1f, %.1f",
				super.toString (),
				disparity,disparityVelocity,
				location3dm.x,
				location3dm.y,
				location3dm.z,
				velocity3dmps.x,
				velocity3dmps.y,
				velocity3dmps.z);
			return s;
		}

		public float getDisparityVelocity (){
			return disparityVelocity;
		}

		public void setDisparityVelocity (float disparityVelocity){
			this.disparityVelocity = disparityVelocity;
		}

		/** @see #location3dm
		 */
		public LowpassFilter3D.Point3D getLocation3dm (){
			return location3dm;
		}

		/** @see #location3dm
		 */
		public void setLocation3dm (LowpassFilter3D.Point3D location3dm){
			this.location3dm = location3dm;
		}

		/** @see #velocity3dmps
		 */
		public LowpassFilter3D.Point3D getVelocity3dmps (){
			return velocity3dmps;
		}

		/** @see #velocity3dmps
		 */
		public void setVelocity3dmps (LowpassFilter3D.Point3D velocity3dmps){
			this.velocity3dmps = velocity3dmps;
		}

		@Override
		public void draw(GLAutoDrawable drawable) {
			super.draw(drawable);
			GL2 gl = drawable.getGL().getGL2();
			// left
			gl.glColor3f(0, 1, 0); // green
			gl.glLineWidth(1f);

			float x = (int) getLocation().x;
			float y = (int) getLocation().y;
			float sy = (int) getRadius(); // sx sy are (half) size of rectangle
			float sx = sy;
			int x2 = (int) (x - (getDisparity() / 2));
                        gl.glPushMatrix();
                            DrawGL.drawBox(gl, x2, y, 2*sx, 2*sy, 0);
                            if (isDynamicAngleEnabled()) {
                                DrawGL.drawLine(gl, 0, 0, sx, 0, 1);
                            }
                        gl.glPopMatrix();

			// red right
			gl.glColor3f(1, 0, 0); // green
			gl.glLineWidth(1f);

			x2 = (int) (x + (getDisparity() / 2));
                        gl.glPushMatrix();
                            DrawGL.drawBox(gl, x2, y, 2*sx, 2*sy, 0);
                            if (isDynamicAngleEnabled()) {
                                DrawGL.drawLine(gl, 0, 0, sx, 0, 1);
                            }
                        gl.glPopMatrix();
		}



		/** Returns disparity in pixels.
		 *
		 * @return disparity in pixels
		 */
		public float getDisparity (){
			return disparity;
		}

		public void setDisparity (float disparity){
			this.disparity = disparity;
		}
	}

	@Override
	synchronized public void annotate (GLAutoDrawable drawable){
		super.annotate(drawable);
		final float LINE_WIDTH = 6f; // in pixels
		if (  ! isFilterEnabled () ){
			return;
		}
		GL2 gl = drawable.getGL().getGL2(); // when we getString this we are already set up with updateShape 1=1 pixel, at LL corner
		if ( gl == null ){
			log.warning ("null GL in StereoClusterTracker.annotate");
			return;
		}
		float[] rgb = new float[ 4 ];
		gl.glPushMatrix ();
		try{
			for ( Cluster b:clusters ){
				StereoCluster c=(StereoCluster)b;
				if ( c.isVisible () || isShowAllClusters()){
					int x = (int)c.getLocation ().x;
					int y = (int)c.getLocation ().y;


					int sy = (int)c.getRadius (); // sx sy are (half) size of rectangle
					int sx = sy;
					//
					//                    if ( isColorClustersDifferentlyEnabled () ){
					//                    } else{
					//                        float brightness = (float)Math.max (0.1f,Math.min (1f,c.getLifetime () / fullbrightnessLifetime));
					//
					//                        c.setColor(new Color(brightness,brightness,0,1f));
					//                    }
					//                    c.getColor ().getRGBComponents (rgb);
					//                    if (c.isVisible()) {
					//                        gl.glColor3fv(rgb, 0);
					//                    } else {
					//                        gl.glColor3f(.3f, .3f, .3f);
					//                    }
					//                    gl.glLineWidth (LINE_WIDTH);
					//

                                        //                    gl.glPushMatrix();
                                        //                        drawGL.drawBox(gl, x, y, 2*sx, 2*sy, 0);
                                        //                        if (isDynamicAngleEnabled()) {
                                        //                            drawGL.drawLine(gl, 0, 0, sx, 0, 1);
                                        //                        }
                                        //                    gl.glPopMatrix();
					//                    // draw left and right disparity clusters


					// left
					if(c.isVisible()){
						gl.glColor3f (0,1,0); // green
						gl.glLineWidth (LINE_WIDTH / 2);

						int x2 = (int)( x - (c.getDisparity () / 2) );
                                                gl.glPushMatrix();
                                                    DrawGL.drawBox(gl, x2, y, 2*sx, 2*sy, 0);
                                                    if (isDynamicAngleEnabled()) {
                                                        DrawGL.drawLine(gl, 0, 0, sx, 0, 1);
                                                    }
                                                gl.glPopMatrix();

						// red right
						gl.glColor3f (1,0,0); // green
						gl.glLineWidth (LINE_WIDTH / 2);

						x2 = (int)( x + (c.getDisparity () / 2) );
						gl.glPushMatrix();
                                                    DrawGL.drawBox(gl, x2, y, 2*sx, 2*sy, 0);
                                                    if (isDynamicAngleEnabled()) {
                                                        DrawGL.drawLine(gl, 0, 0, sx, 0, 1);
                                                    }
                                                gl.glPopMatrix();
					}


					//                    gl.glPointSize (LINE_WIDTH);
					//                    gl.glBegin (GL.GL_POINTS);
					//                    {
					//                        java.util.List<ClusterPathPoint> points = c.getPath ();
					//                        for ( Point2D.Float p:points ){
					//                            gl.glVertex2f (p.x,p.y);
					//                        }
					//                    }
					//                    gl.glEnd ();

					//                    // now draw velocityPPT vector
					//                    if ( isUseVelocity () ){
					//                        gl.glBegin (GL2.GL_LINES);
					//                        {
					//                            gl.glVertex2i (x,y);
					//                            gl.glVertex2f (x + c.getVelocityPPS ().x * getVelocityVectorScaling(),y + c.getVelocityPPS ().y * getVelocityVectorScaling());
					//                        }
					//                        gl.glEnd ();
					//                    }

					if (isDisplayStereoClusterAnnotation()) {
						int font = GLUT.BITMAP_TIMES_ROMAN_24;
						GLUT glut = chip.getCanvas().getGlut();
						gl.glColor3f(1, 1, 1);

						gl.glRasterPos3f(c.location.x, c.location.y, 0);
						glut.glutBitmapString(font, String.format("d=%.1f, dv=%.1f",c.disparity,c.disparityVelocity));
					}

				} // visible cluster
			} // clusters

			if ( isDisplayStereoClusterAnnotation() && (getNearestCluster () != null) ){
				StereoCluster c = getNearestCluster ();
				int font = GLUT.BITMAP_TIMES_ROMAN_24;
				GLUT glut = chip.getCanvas ().getGlut ();
				gl.glColor3f (1,1,1);

				gl.glRasterPos3f (1,13,0);
				glut.glutBitmapString (font,String.format ("x,y,z=%5.2f, %5.2f, %5.2f",c.location3dm.x,c.location3dm.y,c.location3dm.z));

				gl.glRasterPos3f (1,8,0);
				glut.glutBitmapString (font,String.format ("vx,vy,vz=%5.2f, %5.2f, %5.2f",c.velocity3dmps.x,c.velocity3dmps.y,c.velocity3dmps.z));

				gl.glRasterPos3f (1,3,0);
				glut.glutBitmapString (font,String.format ("disp=%6.1f dispVel=%6.1f",c.getDisparity (),c.disparityVelocity));
			} // text for closest cluster only
		} catch ( java.util.ConcurrentModificationException e ){
			// this is in case cluster list is modified by real time filter during rendering of clusters
			log.warning (e.getMessage ());
		}
		gl.glPopMatrix ();
	}

	@Override
	synchronized public boolean isLogDataEnabled (){
		return logDataEnabled;
	}

	@Override
	synchronized public void setLogDataEnabled (boolean logDataEnabled){
		this.logDataEnabled = logDataEnabled;
		if (  ! logDataEnabled ){
			logStream.flush ();
			logStream.close ();
			logStream = null;
		} else{
			try{
				logStream = new PrintStream (new BufferedOutputStream (new FileOutputStream (new File ("stereoClusterTrackerData.txt"))));
			} catch ( Exception e ){
				e.printStackTrace ();
			}
		}
	}

	public float getVelocityMixingFactor (){
		return velocityMixingFactor;
	}

	public void setVelocityMixingFactor (float velocityMixingFactor){
		if ( velocityMixingFactor > 1 ){
			velocityMixingFactor = 1;
		}
		this.velocityMixingFactor = velocityMixingFactor;
		putFloat("velocityMixingFactor",velocityMixingFactor);
	}

	/**
	 * @return the displayStereoClusterAnnotation
	 */
	public boolean isDisplayStereoClusterAnnotation() {
		return displayStereoClusterAnnotation;
	}

	/**
	 * @param displayStereoClusterAnnotation the displayStereoClusterAnnotation to set
	 */
	public void setDisplayStereoClusterAnnotation(boolean displayStereoClusterAnnotation) {
		this.displayStereoClusterAnnotation = displayStereoClusterAnnotation;
		putBoolean("displayStereoClusterAnnotation", displayStereoClusterAnnotation);
	}
	private SoundPlayer soundPlayer = new SoundPlayer ();
	private class SoundPlayer{
		private String[] soundFiles = { "ow.wav","biff.wav","oof.wav" };
		PlayWavFile player;
		Random r = new Random ();
		PlayWavFile T;
		void playRandom (){
			if((T!=null) && T.isAlive ()) {
				return;
			}
			T=new PlayWavFile (soundFiles[r.nextInt (soundFiles.length)]);
			T.start ();

		}
	}
}
