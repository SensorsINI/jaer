/*
 * ClusterTracker.java
 *
 * Created on December 5, 2005, 3:49 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.eventprocessing.tracking;
import java.awt.Color;
import java.awt.Graphics2D;

import java.awt.geom.Point2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Tracks blobs of events. Based (probably very loosely) on conversation with Martin Litzenberger.
 *<p>
 *The ClusterTracker starts with no clusters. When called with AEPacket2D of events,
 * assigns the first cluster to the median event location, based
 *on MedianTracker notion of median event location, iff the location
 * std dev is not too large. Then every subsequent
 * event that is processed is either assigned to an existing cluster (if
 *the event location is close enough to the cluster center) or a new cluster is generated.
 * A cluster remains until its support
 *fades away, meaning that it lacks support compared with other clusters. Initially,
 * there will be just random clusters corresponding to
 *noise events, but these will quickly be pruned away because they will lack support
 * compared with real clusters, which will have continued support.
 *<p>
 *Clusters also have persistence: if a cluster has sustained support, then it keeps
 * itself alive according to this support. The implicit (past) support is
 *a quantity that is charged up by support and fades away over time without support.
 * Thus a cluster can survive even when it gets no support for a time. This
 *mechanism keeps clusters from objects that move and then momentarily stop.
 *
 * @author tobi
 @deprecated This class is no longer maintained. Use RectanglularClusterTracker instead.
 */
@Deprecated
public class ClusterTracker extends EventFilter2D implements FrameAnnotater, Observer, PreferenceChangeListener {


	//    static Preferences prefs=Preferences.userNodeForPackage(ClusterTracker.class);
	//    PreferencesEditor editor;
	//    JFrame preferencesFrame;

	protected ArrayList<Cluster> clusters=new ArrayList<Cluster>();
	protected AEChip chip;
	AEChipRenderer renderer;


	protected float defaultClusterRadius;
	protected float mixingFactor=getPrefs().getFloat("ClusterTracker.mixingFactor",0.01f); // amount each event moves COM of cluster towards itself
	protected float velocityMixingFactor=getPrefs().getFloat("ClusterTracker.velocityMixingFactor",0.01f); // mixing factor for velocity computation

	protected float surround=getPrefs().getFloat("ClusterTracker.surround",1f);
	protected boolean scaleEnabled=getPrefs().getBoolean("ClusterTracker.scaleEnabled", true);
	protected boolean pathsEnabled=getPrefs().getBoolean("ClusterTracker.pathsEnabled", true);
	protected boolean colorClustersDifferentlyEnabled=getPrefs().getBoolean("ClusterTracker.colorClustersDifferentlyEnabled",false);
	protected boolean useOnePolarityOnlyEnabled=getPrefs().getBoolean("ClusterTracker.useOnePolarityOnlyEnabled",false);
	protected boolean useOffPolarityOnlyEnabled=getPrefs().getBoolean("ClusterTracker.useOffPolarityOnlyEnabled",false);
	protected float aspectRatio=getPrefs().getFloat("ClusterTracker.aspectRatio",1f);
	protected float clusterSize=getPrefs().getFloat("ClusterTracker.clusterSize",.2f);
	protected boolean growMergedSizeEnabled=getPrefs().getBoolean("ClusterTracker.growMergedSizeEnabled",false);
	private boolean showVelocity=getPrefs().getBoolean("ClusterTracker.showVelocity",true);
	private boolean logDataEnabled=false;
	PrintStream logStream=null;

	KalmanFilter kalmanFilter;
	/** Creates a new instance of ClusterTracker */
	public ClusterTracker(AEChip chip) {
		super(chip);
		this.chip=chip;
		renderer=chip.getRenderer();
		initFilter();
		chip.addObserver(this);
		getPrefs().addPreferenceChangeListener(this);
	}

	@Override
	public void initFilter() {
		initDefaults();
		defaultClusterRadius=Math.max(chip.getSizeX(),chip.getSizeY())*getClusterSize();
	}

	void initDefaults(){
		initDefault("ClusterTracker.clusterLifetimeWithoutSupport","10000");
		initDefault("ClusterTracker.maxNumClusters","10");
		initDefault("ClusterTracker.clusterSize","0.15f");
		initDefault("ClusterTracker.numEventsStoredInCluster","100");
		initDefault("ClusterTracker.thresholdEventsForVisibleCluster","30");

		//        initDefault("ClusterTracker.","");
	}

	void initDefault(String key, String value){
		if(getPrefs().get(key,null)==null) {
			getPrefs().put(key,value);
		}
	}

	//    ArrayList<Cluster> pruneList=new ArrayList<Cluster>(1);
	protected LinkedList<Cluster> pruneList=new LinkedList<Cluster>();

	synchronized private void track(EventPacket<BasicEvent> ae){
		int n=ae.getSize();
		if(n==0) {
			return;
		}
		int maxNumClusters=getMaxNumClusters();

		// for each event, see which cluster it is closest to and add it to this cluster.
		// if its too far from any cluster, make a new cluster if we can
		//        for(int i=0;i<n;i++){
		for(BasicEvent ev:ae){
			//            EventXYType ev=ae.getEvent2D(i);
			ClosestCluster closest=getFirstContainingCluster(ev);
			if((closest.cluster!=null) && (closest.distance<closest.cluster.getRadius())){
				closest.cluster.addEvent(ev);
			}else if(scaleEnabled && (closest.cluster!=null) && (closest.distance<(closest.cluster.getRadius()+getSurround()))){
				//change the radius if the event is not inside the rectangle
				closest.cluster.scale(ev);
				continue;
			}else if(clusters.size()<maxNumClusters){ // start a new cluster
				Cluster newCluster=new Cluster(ev);
				clusters.add(newCluster);
			}
		}
		// prune out old clusters that don't have support
		int clusterLifetimeWithoutSupport=getClusterLifetimeWithoutSupport();
		pruneList.clear();
		for(Cluster c:clusters){
			if((ae.getLastTimestamp()-c.getLastEventTimestamp())>clusterLifetimeWithoutSupport) {
				pruneList.add(c);
			}
		}
		clusters.removeAll(pruneList);

		// merge clusters that are too close to each other.
		// this must be done interatively, because merging 4 or more clusters feedforward can result in more clusters than
		// you start with. each time we merge two clusters, we start over, until there are no more merges on iteration.

		// for each cluster, if it is close to another cluster then merge them and start over.

		//        int beforeMergeCount=clusters.size();
		boolean mergePending;
		Cluster c1=null,c2=null;
		do{
			mergePending=false;
			int nc=clusters.size();
			outer:
				for(int i=0;i<nc;i++){
					c1=clusters.get(i);
					for(int j=i+1;j<nc;j++){
						c2=clusters.get(j); // getString the other cluster
						if(c1.distanceTo(c2)<(c1.getRadius()+c2.getRadius())) { // if distance is less than sum of radii merge them
							// if cluster is close to another cluster, merge them
							mergePending=true;
							break outer; // break out of the outer loop
						}
					}
				}
			if(mergePending && (c1!=null) && (c2!=null)){
				pruneList.add(c1);
				pruneList.add(c2);
				clusters.remove(c1);
				clusters.remove(c2);
				clusters.add(new Cluster(c1,c2));
			}
		}while(mergePending);

		//        // update all cluster sizes
		//        // note that without this following call, clusters maintain their starting size until they are merged with another cluster.
		if(isHighwayPerspectiveEnabled()){
			for(Cluster c:clusters){
				c.setRadius(defaultClusterRadius);
			}
		}

		// update paths of clusters
		for(Cluster c:clusters) {
			c.updatePath();
		}

		//        if(clusters.size()>beforeMergeCount) throw new RuntimeException("more clusters after merge than before");
		if(isLogDataEnabled() && (getNumClusters()>0)){
			if(logStream!=null) {
				Cluster c=clusters.get(0);
				logStream.println(String.format("%d %d %f %f", c.getClusterNumber(), c.lastTimestamp,c.location.x,c.location.y));
				if(logStream.checkError()) {
					log.warning("eroror logging data");
				}
			}
		}


	}

	final class ClusterPair{
		Cluster c1,c2;
		ClusterPair(Cluster c1, Cluster c2){
			this.c1=c1; this.c2=c2;
		}
	}

	public int getNumClusters(){
		return clusters.size();
	}


	/**
	 * method that given AE, returns closest cluster and distance to it. The actual computation returns the first cluster that is within the
     minDistance of the event, which reduces the computation at the cost of reduced precision.
	 * @param event the event
	 * @return closest cluster
	 */
	public ClosestCluster closestCluster(BasicEvent event){
		float minDistance=Float.MAX_VALUE;
		Cluster closest=null;
		float currentDistance=0;
		for(Cluster c:clusters){
			if((currentDistance=c.distanceTo(event))<minDistance){
				closest=c;
				minDistance=currentDistance;
			}
		}
		return new ClosestCluster(minDistance, closest);
	}

	/** Given AE, returns first cluster that event is within
	 * @param event the event
	 * @return cluster that contains event within the cluster's radius
	 */
	public ClosestCluster getFirstContainingCluster(BasicEvent event){
		float minDistance=Float.MAX_VALUE;
		Cluster closest=null;
		float currentDistance=0;
		for(Cluster c:clusters){
			if((currentDistance=c.distanceTo(event))<c.getRadius()){
				closest=c;
				minDistance=currentDistance;
				break;
			}
		}
		return new ClosestCluster(minDistance, closest);
	}

	/** Represents a cluster and a distance (the distance of an event to the cluster)
	 */
	public final class ClosestCluster{
		public float distance;
		public Cluster cluster;
		public ClosestCluster(float d, Cluster c){
			distance=d;
			cluster=c;
		}
	}

	protected int clusterCounter=0;

	/** Represents a single tracked object */
	public class Cluster{
		public Point2D.Float location=new Point2D.Float(); // location in chip pixels
		public Point2D.Float velocity=new Point2D.Float(); // velocity in chip pixels/sec
		public float tauMsVelocity=50; // LP filter time constant for velocity change
		//        private LowpassFilter velocityFilter=new LowpassFilter();

		private float radius; // in chip chip pixels
		//        private float mass; // a cluster has a mass correspoding to its support - the higher the mass, the harder it is to change its velocity

		protected final int MAX_PATH_LENGTH=100;
		protected ArrayList<Point2D.Float> path=new ArrayList<Point2D.Float>(MAX_PATH_LENGTH);
		//        public class SpaceTimePoint{
		//            public SpaceTimePoint(Point2D.Float point, int timestamp){
		//                this.point=point;
		//                this.timestamp=timestamp;
		//            }
		//            public SpaceTimePoint(float x, float y, int timestamp){
		//                point.x=x;
		//                point.y=y;
		//                this.timestamp=timestamp;
		//            }
		//            public Point2D.Float point=new Point2D.Float();
		//            public int timestamp;
		//        }
		protected Color color=null;

		protected int numEvents;
		//        ArrayList<EventXYType> events=new ArrayList<EventXYType>();
		protected int lastTimestamp, firstTimestamp;
		protected float eventRate; // in events/sec
		private int clusterNumber;

		public Cluster(){
			setRadius(defaultClusterRadius);
			float hue=random.nextFloat();
			Color color=Color.getHSBColor(hue,1f,1f);
			setColor(color);
			setClusterNumber(clusterCounter++);
		}

		public Cluster(BasicEvent ev){
			this();
			location.x=ev.x;
			location.y=ev.y;
			lastTimestamp=ev.timestamp;
			firstTimestamp=lastTimestamp;
			setRadius(defaultClusterRadius);
			//            System.out.println("constructed "+this);
		}

		// if a pixel has been selected (we ask the renderer) then we compute the perspective from this vanishing point
		final float perspectiveScale(Point2D.Float p){
			if(!renderer.isPixelSelected()){
				float yfrac=1f-(p.y/chip.getSizeY()); // yfrac grows to 1 at bottom of image
				return yfrac;
			}else{
				// scale is 0 at vanishing point and grows linearly to 1 at max size of chip
				int size=chip.getMaxSize();
				float d=(float)p.distance(renderer.getXsel(),renderer.getYsel());
				float scale=d/size;
				return scale;
			}
		}

		/** Constructs a cluster by merging two clusters */
		public Cluster(Cluster one, Cluster two){
			this();
			// merge locations by just averaging
			//            location.x=(one.location.x+two.location.x)/2;
			//            location.y=(one.location.y+two.location.y)/2;

			// merge locations by average weighted by number of events supporting cluster
			location.x=((one.location.x*one.numEvents)+(two.location.x*two.numEvents))/(one.numEvents+two.numEvents);
			location.y=((one.location.y*one.numEvents)+(two.location.y*two.numEvents))/(one.numEvents+two.numEvents);

			lastTimestamp=(one.lastTimestamp+two.lastTimestamp)/2;
			numEvents=one.numEvents+two.numEvents;
			firstTimestamp=Math.min(one.firstTimestamp, two.firstTimestamp); // make lifetime the oldest src cluster
			Cluster older=one.firstTimestamp<two.firstTimestamp? one:two;
			path=older.path;
			//            Color c1=one.getColor(), c2=two.getColor();
			setColor(older.getColor());
			//            System.out.println("merged "+one+" with "+two);
			//the radius should increase
			//            setRadius((one.getRadius()+two.getRadius())/2);
			if(growMergedSizeEnabled){
				float R = (one.getRadius()+two.getRadius())/2;
				setRadius(R + (getMixingFactor()*R));
			}else{
				setRadius(older.getRadius());
			}

		}

		public int getLastEventTimestamp(){
			//            EventXYType ev=events.getString(events.size()-1);
			//            return ev.timestamp;
			return lastTimestamp;
		}

		public void addEvent(BasicEvent event){
			if((event instanceof TypedEvent)){
				TypedEvent e=(TypedEvent)event;
				if(useOnePolarityOnlyEnabled){
					if(useOffPolarityOnlyEnabled){
						if(e.type==1) {
							return;
						}
					}else{
						if(e.type==0) {
							return;
						}
					}
				}
			}

			// save location for computing velocity
			float oldx=location.x, oldy=location.y;

				float m=mixingFactor,m1=1-m;;

				// compute new cluster location by mixing old location with event location by using
				// mixing factor

				location.x=((m1*location.x)+(m*event.x));
				location.y=((m1*location.y)+(m*event.y));

				if(showVelocity){
					// update velocity vector using old and new position only if valid dt
					// and update it by the mixing factors
					float dt=event.timestamp-lastTimestamp;
					if(dt>0){
						float oldvelx=velocity.x;
						float oldvely=velocity.y;

						float velx=(location.x-oldx)/dt;
						float vely=(location.y-oldy)/dt;

						float vm1=1-velocityMixingFactor;
						velocity.x=(vm1*oldvelx)+(velocityMixingFactor*velx);
						velocity.y=(vm1*oldvely)+(velocityMixingFactor*vely);
					}
				}

				int prevLastTimestamp=lastTimestamp;
				lastTimestamp=event.timestamp;
				numEvents++;
				eventRate=1f/((lastTimestamp-prevLastTimestamp)+Float.MIN_VALUE);
		}

		/** sets the cluster radius according to distance of event from cluster center
         @param event the event to scale with
		 */
		public final void scale(BasicEvent event){
			float r = getRadius();

			float d = Math.abs(event.x-location.x);
			if(d > r) {
				setRadius(r + (mixingFactor*d));
			}

			d = Math.abs(event.y-location.y);
			if(d > radius) {
				setRadius( r + (mixingFactor*d));
			}
		}

		/** @return distance of this cluster to the event in manhatten (cheap) metric (sum of abs values of x and y distance */
		public float distanceTo(BasicEvent event){
			final float dx=event.x-location.x;
			final float dy=event.y-location.y;
			//            return Math.abs(dx)+Math.abs(dy);
			return distanceMetric(dx,dy);
			//            dx*=dx;
			//            dy*=dy;
			//            float distance=(float)Math.sqrt(dx+dy);
			//            return distance;
		}

		public float distanceMetric(float dx,float dy){
			return ((dx>0)?dx:-dx)+((dy>0)?dy:-dy);
		}

		/** @return distance in x direction of this cluster to the event */
		final float distanceToX(BasicEvent event){
			float distance=Math.abs(event.x-location.x);
			return distance;
		}

		/** @return distance in y direction of this cluster to the event */
		final float distanceToY(BasicEvent event){
			float distance=Math.abs(event.y-location.y);
			return distance;
		}

		/** @return distance of this cluster to the other cluster */
		protected final float distanceTo(Cluster c){
			float dx=c.location.x-location.x;
			float dy=c.location.y-location.y;
			return distanceMetric(dx,dy);
			//            if(dx<0)dx=-dx;
			//            if(dy<0)dy=-dy;
			//            dx*=dx;
			//            dy*=dy;
			//            float distance=(float)Math.sqrt(dx+dy);
			//            distance=dx+dy;
			//            return distance;
		}

		public final float getRadius(){
			return radius;
		}

		public void setRadius(float r){
			if(!isHighwayPerspectiveEnabled()) {
				radius=r;
			}
			else{
				radius=defaultClusterRadius*perspectiveScale(location);
			}
		}

		final public Point2D.Float getLocation() {
			return location;
		}
		public void setLocation(Point2D.Float l){
			location = l;
		}

		/** @return true if cluster has enough support */
			final public boolean isVisible(){
				boolean ret=true;
				if(numEvents<getThresholdEventsForVisibleCluster()) {
					ret=false;
				}
				return ret;
			}

			/** @return lifetime of cluster in timestamp ticks */
			final public int getLifetime(){
				return lastTimestamp-firstTimestamp;
			}

			final public void updatePath(){
				if(!pathsEnabled) {
					return;
				}
				path.add(new Point2D.Float(location.x,location.y));
				if(path.size()>MAX_PATH_LENGTH) {
					path.remove(path.get(0));
				}
			}

			@Override
			public String toString(){
				return "Cluster #"+getClusterNumber()+" with "+numEvents+" events near "+(int)location.x+", "+(int)location.y;
			}

			public ArrayList<Point2D.Float> getPath() {
				return path;
			}

			public Color getColor() {
				return color;
			}

			public void setColor(Color color) {
				this.color = color;
			}

			/** @return averaged velocity of cluster in pixels per second. The velocity is instantaneously
         computed from the movement of the cluster caused by the last event, then this velocity is mixed
         with the the old velocity by the mixing factor. Thus the mixing factor is appplied twice: once for moving
         the cluster and again for changing the velocity.
			 */
			public Point2D.Float getVelocity() {
				return velocity;
			}

			public int getClusterNumber() {
				return clusterNumber;
			}

			public void setClusterNumber(int clusterNumber) {
				this.clusterNumber = clusterNumber;
			}
	}

	public ArrayList<ClusterTracker.Cluster> getClusters() {
		return clusters;
	}

	public LinkedList<ClusterTracker.Cluster> getPruneList(){
		return pruneList;
	}

	protected static final float fullbrightnessLifetime=1000000;


	protected Random random=new Random();

	final void drawCluster(final Cluster c, float[][][] fr){
		int x=(int)c.getLocation().x;
		int y=(int)c.getLocation().y;


		int sy=(int)c.getRadius(); // sx sy are (half) size of rectangle
		int sx=sy;
		int ix, iy;
		int mn,mx;

		if(isColorClustersDifferentlyEnabled()){
		}else{
			float brightness=Math.max(0f,Math.min(1f,c.getLifetime()/fullbrightnessLifetime));
			Color color=Color.getHSBColor(.5f,1f,brightness);
			c.setColor(color);
		}

		Color color=c.getColor();
		if(true){ // draw boxes
			iy=y-sy;    // line under center
			mn=x-sx;
			mx=x+sx;
			for(ix=mn;ix<=mx;ix++){
				colorPixel(ix,iy,fr,clusterColorChannel,color);
			}
			iy=y+sy;    // line over center
			for(ix=mn;ix<=mx;ix++){
				colorPixel(ix,iy,fr,clusterColorChannel,color);
			}
			ix=x-sx;        // line to left
			mn=y-sy;
			mx=y+sy;
			for(iy=mn;iy<=mx;iy++){
				colorPixel(ix,iy,fr,clusterColorChannel,color);
			}
			ix=x+sx;    // to right
			for(iy=mn;iy<=mx;iy++){
				colorPixel(ix,iy,fr,clusterColorChannel,color);
			}
		}else{ // draw diamond reflecting manhatten distance measure doesn't look very nice because not antialiased at all
			iy=y-sy;    // line up right from bot
			ix=x;
			mx=x+sx;
			while(ix<mx){
				colorPixel(ix++,iy++,fr,clusterColorChannel,color);
			}
			mx=x+sx;
			ix=x;
			iy=y+sy;    // line down right from top
			while(ix<mx){
				colorPixel(ix++,iy--,fr,clusterColorChannel,color);
			}
			ix=x;        // line from top down left
			iy=y+sy;
			while(iy>=y){
				colorPixel(ix--,iy--,fr,clusterColorChannel,color);
			}
			ix=x;
			iy=y-sy;
			while(iy<y){
				colorPixel(ix--,iy++,fr,clusterColorChannel,color);
			}
		}

		ArrayList<Point2D.Float> points=c.getPath();
		for(Point2D.Float p:points){
			colorPixel(Math.round(p.x),Math.round(p.y),fr,clusterColorChannel,color);
		}

	}

	static final int clusterColorChannel=2;

	/** @param x x location of pixel
	 *@param y y location
	 *@param fr the frame data
	 *@param channel the RGB channel number 0-2
	 *@param brightness the brightness 0-1
	 */
	final void colorPixel(final int x, final int y, final float[][][] fr, int channel, Color color){
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

	int clusterLifetimeWithoutSupport=getPrefs().getInt("ClusterTracker.clusterLifetimeWithoutSupport",10000);

	/** lifetime of cluster in ms without support */
	final public int getClusterLifetimeWithoutSupport() {
		return clusterLifetimeWithoutSupport;
	}

	/** lifetime of cluster in ms without support */
	public void setClusterLifetimeWithoutSupport(final int clusterLifetimeWithoutSupport) {
		this.clusterLifetimeWithoutSupport=clusterLifetimeWithoutSupport;
		getPrefs().putInt("ClusterTracker.clusterLifetimeWithoutSupport", clusterLifetimeWithoutSupport);
	}

	/** max distance from cluster to event as fraction of size of array */
	final public float getClusterSize() {
		return clusterSize;
	}

	/** sets max distance from cluster to event as fraction of size of array */
	public void setClusterSize(float clusterSize) {
		if(clusterSize>1f) {
			clusterSize=1f;
		}
		if(clusterSize<0) {
			clusterSize=0;
		}
		defaultClusterRadius=Math.max(chip.getSizeX(),chip.getSizeY())*clusterSize;
		this.clusterSize=clusterSize;
		for(Cluster c:clusters){
			c.setRadius(defaultClusterRadius);
		}
		getPrefs().putFloat("ClusterTracker.clusterSize", clusterSize);
	}

	int maxNumClusters=getPrefs().getInt("ClusterTracker.maxNumClusters",10);

	/** max number of clusters */
	final public int getMaxNumClusters() {
		return maxNumClusters;
	}

	/** max number of clusters */
	public void setMaxNumClusters(final int maxNumClusters) {
		this.maxNumClusters=maxNumClusters;
		getPrefs().putInt("ClusterTracker.maxNumClusters", maxNumClusters);
	}

	//    /** number of events to store for a cluster */
	//    public int getNumEventsStoredInCluster() {
	//        return prefs.getInt("ClusterTracker.numEventsStoredInCluster",10);
	//    }
	//
	//    /** number of events to store for a cluster */
	//    public void setNumEventsStoredInCluster(final int numEventsStoredInCluster) {
	//        prefs.putInt("ClusterTracker.numEventsStoredInCluster", numEventsStoredInCluster);
	//    }

	int thresholdEventsForVisibleCluster=getPrefs().getInt("ClusterTracker.thresholdEventsForVisibleCluster",10);

	/** number of events to make a potential cluster visible */
	final public int getThresholdEventsForVisibleCluster() {
		return thresholdEventsForVisibleCluster;
	}

	/** number of events to make a potential cluster visible */
	public void setThresholdEventsForVisibleCluster(final int thresholdEventsForVisibleCluster) {
		this.thresholdEventsForVisibleCluster=thresholdEventsForVisibleCluster;
		getPrefs().putInt("ClusterTracker.thresholdEventsForVisibleCluster", thresholdEventsForVisibleCluster);
	}



	public Object getFilterState() {
		return null;
	}

	public boolean isGeneratingFilter() {
		return false;
	}

	@Override
	synchronized public void resetFilter() {
		clusters.clear();
		//Emre: The following causes nullpointer exceptions when no kalman filter is used. Should this not go into an enclosed filter?
		if(kalmanFilter != null) {
			kalmanFilter.resetFilter();
		}
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



	private boolean highwayPerspectiveEnabled=getPrefs().getBoolean("ClusterTracker.highwayPerspectiveEnabled",false);

	public boolean isHighwayPerspectiveEnabled() {
		return highwayPerspectiveEnabled;
	}

	public void setHighwayPerspectiveEnabled(boolean highwayPerspectiveEnabled) {
		this.highwayPerspectiveEnabled = highwayPerspectiveEnabled;
		getPrefs().putBoolean("ClusterTracker.highwayPerspectiveEnabled",highwayPerspectiveEnabled);
	}

	public float getMixingFactor() {
		return mixingFactor;
	}

	public void setMixingFactor(float mixingFactor) {
		if(mixingFactor<0) {
			mixingFactor=0;
		} if(mixingFactor>1) {
			mixingFactor=1f;
		}
		this.mixingFactor = mixingFactor;
		getPrefs().putFloat("ClusterTracker.mixingFactor",mixingFactor);
	}
	public float getSurround() {
		return surround;
	}

	public void setSurround(float surround){
		if(surround < 0) {
			surround = 0;
		}
		this.surround = surround;
		getPrefs().putFloat("ClusterTracker.surround",surround);
	}
	public boolean isPathsEnabled() {
		return pathsEnabled;
	}

	public void setPathsEnabled(boolean pathsEnabled) {
		this.pathsEnabled = pathsEnabled;
		getPrefs().putBoolean("ClusterTracker.pathsEnabled",pathsEnabled);
	}
	public boolean getScaleEnabled(){
		return scaleEnabled;
	}
	public void setScaleEnabled(boolean scaleEnabled){
		this.scaleEnabled = scaleEnabled;
		getPrefs().putBoolean("ClusterTracker.scaleEnabled",scaleEnabled);
	}

	public boolean isColorClustersDifferentlyEnabled() {
		return colorClustersDifferentlyEnabled;
	}

	public void setColorClustersDifferentlyEnabled(boolean colorClustersDifferentlyEnabled) {
		this.colorClustersDifferentlyEnabled = colorClustersDifferentlyEnabled;
		getPrefs().putBoolean("ClusterTracker.colorClustersDifferentlyEnabled",colorClustersDifferentlyEnabled);
	}

	@Override
	public void update(Observable o, Object arg) {
		initFilter();
	}

	public boolean isUseOnePolarityOnlyEnabled() {
		return useOnePolarityOnlyEnabled;
	}

	public void setUseOnePolarityOnlyEnabled(boolean useOnePolarityOnlyEnabled) {
		this.useOnePolarityOnlyEnabled = useOnePolarityOnlyEnabled;
		getPrefs().putBoolean("ClusterTracker.useOnePolarityOnlyEnabled",useOnePolarityOnlyEnabled);
	}

	public boolean isUseOffPolarityOnlyEnabled() {
		return useOffPolarityOnlyEnabled;
	}

	public void setUseOffPolarityOnlyEnabled(boolean useOffPolarityOnlyEnabled) {
		this.useOffPolarityOnlyEnabled = useOffPolarityOnlyEnabled;
		getPrefs().putBoolean("ClusterTracker.useOffPolarityOnlyEnabled",useOffPolarityOnlyEnabled);
	}

	public void annotate(Graphics2D g) {
	}

	@Override
	synchronized public void annotate(GLAutoDrawable drawable) {
		final float LINE_WIDTH=6f; // in pixels
		if(!isFilterEnabled()) {
			return;
		}
		GL2 gl=drawable.getGL().getGL2(); // when we getString this we are already set up with scale 1=1 pixel, at LL corner
		if(gl==null){
			log.warning("null GL in ClusterTracker.annotate");
			return;
		}
		float[] rgb=new float[4];
		gl.glPushMatrix();
		try{
			{
				for(Cluster c:clusters){
					if(c.isVisible()){
						int x=(int)c.getLocation().x;
						int y=(int)c.getLocation().y;


						int sy=(int)c.getRadius(); // sx sy are (half) size of rectangle
						int sx=sy;

						if(isColorClustersDifferentlyEnabled()){
						}else{
							float brightness=Math.max(0f,Math.min(1f,c.getLifetime()/fullbrightnessLifetime));
							Color color=Color.getHSBColor(.5f,1f,brightness);
							c.setColor(color);
						}
						c.getColor().getRGBComponents(rgb);
						gl.glColor3fv(rgb,0);
						gl.glLineWidth(LINE_WIDTH);
						//                    drawGLCluster(x-sx,y-sy,x+sx,y+sy);
						gl.glBegin(GL.GL_LINE_LOOP);
						{
							gl.glVertex2i(x,y-sy);
							gl.glVertex2i(x+sx,y);
							gl.glVertex2i(x+sx,y);
							gl.glVertex2i(x,y+sy);
							gl.glVertex2i(x,y+sy);
							gl.glVertex2i(x-sx,y);
							gl.glVertex2i(x,y-sy);
						}
						gl.glEnd();
						gl.glPointSize(LINE_WIDTH);
						gl.glBegin(GL.GL_POINTS);
						{
							ArrayList<Point2D.Float> points=c.getPath();
							for(Point2D.Float p:points){
								gl.glVertex2f(p.x,p.y);
							}
						}
						gl.glEnd();

						// now draw velocity vector
						if(showVelocity){
							gl.glBegin(GL.GL_LINES);
							{
								gl.glVertex2i(x,y);
								gl.glVertex2f(x+(c.velocity.x*1e6f),y+(c.velocity.y*1e6f));
							}
							gl.glEnd();
						}
					}
				}
			}
		}catch(java.util.ConcurrentModificationException e){
			// this is in case cluster list is modified by real time filter during rendering of clusters
			log.warning(e.getMessage());
		}
		gl.glPopMatrix();
	}

	//    void drawGLCluster(int x1, int y1, int x2, int y2)

	/** annotate the rendered retina frame to show locations of clusters */
	synchronized public void annotate(float[][][] frame) {
		if(!isFilterEnabled()) {
			return;
		}
		// disable for now TODO
		if(chip.getCanvas().isOpenGLEnabled())
		{
			return; // done by open gl annotator
		}
		for(Cluster c:clusters){
			if(c.isVisible()){
				drawCluster(c, frame);
			}
		}
	}

	@Override
	public void preferenceChange(PreferenceChangeEvent evt) {
		mixingFactor=getPrefs().getFloat("ClusterTracker.mixingFactor",0.1f); // amount each event moves COM of cluster towards itself
		pathsEnabled=getPrefs().getBoolean("ClusterTracker.pathsEnabled", true);
		colorClustersDifferentlyEnabled=getPrefs().getBoolean("ClusterTracker.colorClustersDifferentlyEnabled",false);
		useOnePolarityOnlyEnabled=getPrefs().getBoolean("ClusterTracker.useOnePolarityOnlyEnabled",false);
		useOffPolarityOnlyEnabled=getPrefs().getBoolean("ClusterTracker.useOffPolarityOnlyEnabled",false);
		aspectRatio=getPrefs().getFloat("ClusterTracker.aspectRatio",1f);
	}

	public boolean isGrowMergedSizeEnabled() {
		return growMergedSizeEnabled;
	}

	public void setGrowMergedSizeEnabled(boolean growMergedSizeEnabled) {
		this.growMergedSizeEnabled = growMergedSizeEnabled;
		getPrefs().putBoolean("ClusterTracker.growMergedSizeEnabled",growMergedSizeEnabled);
	}

	public float getVelocityMixingFactor() {
		return velocityMixingFactor;
	}

	public void setVelocityMixingFactor(float velocityMixingFactor) {
		if(velocityMixingFactor<0) {
			velocityMixingFactor=0;
		} if(velocityMixingFactor>1) {
			velocityMixingFactor=1f;
		}
		this.velocityMixingFactor = velocityMixingFactor;
		getPrefs().putFloat("ClusterTracker.velocityMixingFactor",velocityMixingFactor);
	}

	public void setShowVelocity(boolean showVelocity){
		this.showVelocity = showVelocity;
		getPrefs().putBoolean("ClusterTracker.showVelocity",showVelocity);
	}
	public boolean isShowVelocity(){
		return showVelocity;
	}

	synchronized public boolean isLogDataEnabled() {
		return logDataEnabled;
	}

	synchronized public void setLogDataEnabled(boolean logDataEnabled) {
		this.logDataEnabled = logDataEnabled;
		if(!logDataEnabled) {
			logStream.flush();
			logStream.close();
			logStream=null;
		}else{
			try{
				logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File("stereoClusterTrackerData.txt"))));
				logStream.println("# clusterNumber lasttimestamp x y");
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}


}
