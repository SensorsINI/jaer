package ch.unizh.ini.jaer.projects.hopfield.orientationlearn;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Point2D.Float;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.Vector;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.hopfield.matrix.HopfieldNetwork;

import com.sun.opengl.util.j2d.TextRenderer;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.FrameAnnotater;

public class RectangularClusterClassifier extends EventFilter2D implements Observer, FrameAnnotater {
	public RectangularClusterTracker firstClusterFinder;
	public FilterChain filterchain;
	private Bbox regionOfInterest;
	private TargetDetector targetDetect;
	private java.awt.geom.Point2D.Float oldLocation;
	private float oldAccelY;
	private float oldAccelX;
	private float oldAccelSize;
	private float oldRadius;
	private float oldAngle;
	private float oldAspectRatio;
	private float oldAccelAngle;
	private double classifyResults[];
	private float colors[][];
	private TextRenderer titleRenderer;
	protected boolean useSpeedData;


	public float kFilteringFactor;

	 public float getMinKFilteringFactor(){
	        return 0;
	    }

	    public float getMaxKFilteringFactor(){
	        return 1;
	    }
	    
	public float getKFilteringFactor() {
		return kFilteringFactor;
	}

	public void setKFilteringFactor(float filteringFactor) {
		kFilteringFactor = filteringFactor;
	}
	private class Bbox {
		public float startx, starty, endx, endy;

		public Bbox() {
			startx = starty = endx = endy = 0;
		}
	}

	Bbox detectTarget() {
		List<RectangularClusterTracker.Cluster> clusterList = firstClusterFinder
		.getClusters();
		// the size will basically be either 1 or 2
		// first calculate average eventrate
		float rateSum = 0;
		for (int ctr = 0; ctr < clusterList.size(); ctr++) {
			rateSum += clusterList.get(ctr).getAvgEventRate();
		}
		if (clusterList.size() > 0) {
			Cluster clst = clusterList.get(0);
			float radius = clst.getRadius();
			java.awt.geom.Point2D.Float location = clst.getLocation();
			// calculate the difference

			float accelerationY = (-oldLocation.y + location.y);
			float accelerationX = (-oldLocation.x + location.x);
			float accelerationSize = -oldRadius + clst.getRadius();// old size -
			// currentsize
			float accelerationAngle = -oldAngle + clst.getAngle(); 

			// use the old difference too
			accelerationSize = (float) ((accelerationSize * kFilteringFactor) + (oldAccelSize * (1.0 - kFilteringFactor)));
			accelerationX = (float) ((accelerationX * kFilteringFactor) + (oldAccelX * (1.0 - kFilteringFactor)));
			accelerationY = (float) ((accelerationY * kFilteringFactor) + (oldAccelY * (1.0 - kFilteringFactor)));
			accelerationAngle= (float) ((accelerationAngle *kFilteringFactor) + (oldAccelAngle*(1.0 - kFilteringFactor)));
			if (oldLocation.x == 0 && oldLocation.y == 0) {

			} else {
				oldAccelX = accelerationX;
				oldAccelY = accelerationY;
				oldAccelSize = accelerationSize;
				oldAccelAngle = accelerationAngle;
				oldAspectRatio = clst.getAspectRatio();
				clst.setAngle(oldAngle + accelerationAngle);
				clst.setRadius(oldRadius + accelerationSize);
				location.x = oldLocation.x + accelerationX;
				location.y = oldLocation.y + accelerationY;
			}
			// create the new position

			// current location time previous location
			oldAngle = clst.getAngle();
			oldLocation = (Float) location.clone();
			oldRadius = clst.getRadius();
			float aspectRatio = clst.getAspectRatio();
			Bbox box = getBox(radius, location, aspectRatio);

			return box;
		}
		return null;
	}


	public void doTrain(){
		//get last accel
		Map<String,Double> finalMaterial = new HashMap<String,Double>();
		finalMaterial.put("angle", new Double(oldAngle));
		finalMaterial.put("ratio",new Double(oldAspectRatio));
		double speed = Math.sqrt(Math.pow(oldAccelX, 2) + Math.pow(oldAccelY, 2));
		finalMaterial.put("velocity",new Double(speed/oldRadius));
		trainedData.add(finalMaterial);


	}
	private Bbox getBox(float radius, java.awt.geom.Point2D.Float location,
			float aspectRatio) {
		Bbox box = new Bbox();
		float radiusX = radius / aspectRatio;
		float radiusY = radius * aspectRatio;

		box.startx = (float) location.getX() - radiusX;
		box.starty = (float) location.getY() - radiusY;
		box.endx = (float) location.getX() + radiusX;
		box.endy = (float) location.getY() + radiusY;
		return box;

	}
	private class TargetDetector extends EventFilter2D implements

	PropertyChangeListener {


		// private RectangularClusterTracker firstClusterFinder;
		// private XYTypeFilter xyfilter;
		// private FilterChain filterchain;

		public TargetDetector(AEChip chip) {
			super(chip);
			firstClusterFinder = new RectangularClusterTracker(chip);
			firstClusterFinder.setMaxNumClusters(1);
			firstClusterFinder.setClusterSize(0.47f);
			firstClusterFinder.setClusterLifetimeWithoutSupportUs(5);
			firstClusterFinder.setPathsEnabled(false);
			filterchain = new FilterChain(chip);

			firstClusterFinder.setEnclosed(true, this);

			//firstClusterFinder.setClusterSize((float) 1.0);
			firstClusterFinder.getPropertyChangeSupport()
			.addPropertyChangeListener("filterEnabled", this);

			filterchain.add(firstClusterFinder);

			setEnclosedFilterEnabledAccordingToPref(firstClusterFinder, null);

			setEnclosedFilterChain(filterchain);

			initFilter();
		}

		public void propertyChange(PropertyChangeEvent evt) {
			if (!evt.getPropertyName().equals("filterEnabled"))
				return;
			try {
				setEnclosedFilterEnabledAccordingToPref((EventFilter) (evt
						.getSource()), (Boolean) (evt.getNewValue()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public EventPacket<?> filterPacket(EventPacket<?> in) {
			if (!isFilterEnabled())
				return in;
			// log.info("Box info "+xyfilter.getStartX()+" "+xyfilter.getStartY()+" "+xyfilter.getEndX()+" "+xyfilter.getEndY()+"\n ");
			return filterchain.filterPacket(in);
		}

		private void setEnclosedFilterEnabledAccordingToPref(
				EventFilter filter, Boolean enb) {
			String key = "TargetDetector." + filter.getClass().getSimpleName()
			+ ".filterEnabled";
			if (enb == null) {
				// set according to preference
				boolean en = getPrefs().getBoolean(key, false); // default
				// disabled
				filter.setFilterEnabled(en);
			} else {
				boolean en = enb.booleanValue();
				getPrefs().putBoolean(key, en);
			}
		}

		public void resetFilter() {
			// filterchain.reset();
			initFilter();
		}

		public void initFilter() {
			kFilteringFactor = (float) 0.05;
			oldLocation = new java.awt.geom.Point2D.Float();
			oldLocation.x = 0;
			oldLocation.y = 0;
			oldRadius = 0;
			oldAccelX = 0;
			oldAccelY = 0;
			oldAccelSize = 0;
			oldAngle = 0;
			firstClusterFinder.setFilterEnabled(true);
			firstClusterFinder.setDynamicSizeEnabled(true);
			firstClusterFinder.setDynamicAspectRatioEnabled(false);
			firstClusterFinder.setDynamicAngleEnabled(true);
			trainedData = new Vector<Map<String,Double>>();
			colors = new float[15][3];
			Random random = new Random();
			for(int i = 0; i<15;i++){
				colors[i][0] = random.nextFloat();
				colors[i][1] = random.nextFloat();
				colors[i][2] = random.nextFloat();
			}
			titleRenderer = new TextRenderer(new Font("Helvetica", Font.PLAIN,16));

		}

		public Object getFilterState() {
			return null;
		}

		/** Overrides to avoid setting preferences for the enclosed filters */
		@Override
		public void setFilterEnabled(boolean yes) {
			this.filterEnabled = yes;
			getPrefs().putBoolean("filterEnabled", yes);
		}

		@Override
		public boolean isFilterEnabled() {
			return this.filterEnabled; // force active
		}

	}
	private Vector<Map<String, Double>> trainedData;



	public void doClassify(){
		if(trainedData.size()==0)
			return;
		Map<String,Double> finalMaterial = new HashMap<String,Double>();
		finalMaterial.put("angle", new Double(oldAngle));
		finalMaterial.put("ratio",new Double(oldAspectRatio));
		double speed = Math.sqrt(Math.pow(oldAccelX, 2) + Math.pow(oldAccelY, 2));
		finalMaterial.put("velocity",new Double(speed/oldRadius));

		double closest_neighbour_distance = Integer.MAX_VALUE;
		double closest_2d_neighbour_distance = Integer.MAX_VALUE;
		int closest_neighbour = -1;
		int closest_2d_neighbour = -1;
		classifyResults = new double[trainedData.size()];
		for(int i =0;i<trainedData.size();i++){
			double total_distance = 0;
			double total_2d_distance = 0;
			double distance_1 = (int) Math.pow((finalMaterial.get("angle") - trainedData.get(i).get("angle")),2);
			total_distance+= distance_1;
			double distance_2 = (int) Math.pow((finalMaterial.get("ratio") - trainedData.get(i).get("ratio")),2);
			total_distance+= distance_2;
			double distance_3 = (int) Math.pow((finalMaterial.get("velocity") - trainedData.get(i).get("velocity")),2);
			total_distance+= distance_3;

			total_distance = Math.sqrt(distance_1 + distance_2 + distance_3);
			total_2d_distance = Math.sqrt(distance_1 + distance_2);

			if(total_distance < closest_neighbour_distance){
				closest_neighbour = i;
				closest_neighbour_distance = total_distance;
			}
			if(total_2d_distance < closest_2d_neighbour_distance){
				closest_2d_neighbour = i;
				closest_2d_neighbour_distance = total_2d_distance;
			}
			if(useSpeedData)
				classifyResults[i] = closest_neighbour_distance;
			else
				classifyResults[i] = closest_2d_neighbour_distance;
		}

		System.out.println("Result: "+ closest_neighbour + " Distance:"+ closest_neighbour_distance+"\t Result 2D: "+ closest_2d_neighbour + " Distance 2D:"+ closest_2d_neighbour_distance);

	}
	//train button



	public boolean isUseSpeedData() {
		return useSpeedData;
	}

	public void setUseSpeedData(boolean useSpeedData) {
		this.useSpeedData = useSpeedData;
	}

	public RectangularClusterClassifier(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		targetDetect = new TargetDetector(chip);
		targetDetect.setFilterEnabled(true);
		// TODO Auto-generated constructor stub
	}

	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		// TODO Auto-generated method stub
		targetDetect.filterPacket(in);

		regionOfInterest = detectTarget();
		doClassify();
		return in;
	}

	@Override
	public Object getFilterState() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void initFilter() {
		// TODO Auto-generated method stub

		targetDetect.setFilterEnabled(true);
	}

	@Override
	public void resetFilter() {
		// TODO Auto-generated method stub

	}

	public void update(Observable o, Object arg) {
		// TODO Auto-generated method stub

	}

	public void annotate(float[][][] frame) {
		// TODO Auto-generated method stub

	}

	public void annotate(Graphics2D g) {
		// TODO Auto-generated method stub

	}

	
	    
	public void annotate(GLAutoDrawable drawable) {
		// TODO Auto-generated method stub
		firstClusterFinder.annotate(drawable);
		GL gl = drawable.getGL();
		int midX = 50;
		int midY = 0;
		if(titleRenderer!=null && classifyResults!=null){
			titleRenderer.beginRendering(drawable.getWidth(), drawable.getHeight());
			titleRenderer.endRendering();
			gl.glPushAttrib( GL.GL_DEPTH_BUFFER_BIT );
			gl.glPushAttrib( GL.GL_COLOR_BUFFER_BIT ); 
			
				gl.glDisable( GL.GL_DEPTH_TEST );
				gl.glEnable (GL.GL_BLEND);
				gl.glBlendFunc (GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

				for(int i = 0;i<classifyResults.length;i++){
					int y = 12*i;
					int width = (int) (50 * (classifyResults[i]));
					titleRenderer.draw("Class"+i, midX+50, midY+10+(y*3));
					gl.glColor4f(colors[i][0], colors[i][1], colors[i][2], .5f);

					gl.glRecti( midX -50,  midY-5+y, midX-50+width, midY+5+y );

				}

				gl.glColor3f( 0.0f, 0.0f, 0.0f );


				gl.glPopAttrib(); 
				gl.glPopAttrib();	
			}
		}

	}

