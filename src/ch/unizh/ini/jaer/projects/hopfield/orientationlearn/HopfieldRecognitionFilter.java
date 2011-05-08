package ch.unizh.ini.jaer.projects.hopfield.orientationlearn;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D.Float;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.chart.Axis;
import net.sf.jaer.util.chart.Category;
import net.sf.jaer.util.chart.Series;
import net.sf.jaer.util.chart.XYChart;
import ch.unizh.ini.jaer.projects.hopfield.matrix.HopfieldNetwork;
import ch.unizh.ini.jaer.projects.hopfield.matrix.ImageTransforms;
import ch.unizh.ini.jaer.projects.hopfield.matrix.IntMatrix;
import ch.unizh.ini.jaer.projects.hopfield.matrix.KohonenAlgorithm;

import com.sun.opengl.util.j2d.TextRenderer;

public class HopfieldRecognitionFilter extends EventFilter2D implements Observer, FrameAnnotater {
	public float kFilteringFactor;

	public float getKFilteringFactor() {
		return kFilteringFactor;
	}

	private BufferedImage bimages[];
	private BufferedImage bTrainingImage;
	private BufferedImage bTrainingImageHopfieldResult;
	private BufferedImage bTrainingImageDigital;
	ByteBuffer barBuffer;
	private final int NUM_ACTIVITY_SAMPLES = 10000;
	private final int RESET_SCALE_COUNT = NUM_ACTIVITY_SAMPLES;
	private final int ACTVITY_SECONDS_TO_SHOW = 60;
	private final int RESET_FILTER_STARTUP_COUNT = 10;
	private final int TITLE_UPDATE_INTERVAL = 5;
	private Series[] activitySeries;
	private Axis timeAxis;
	private Axis activityAxis;
	private Category activityCategory;
	private XYChart activityChart;
	private int decayPointer;
	private float colors[][];
	private boolean isOverKohonen = true;
	private boolean isLiveInput = false;
	
	public boolean isLiveInput() {
		return isLiveInput;
	}

	public void setLiveInput(boolean isLiveInput) {
		this.isLiveInput = isLiveInput;
	}

	public boolean isOverKohonen() {
		return isOverKohonen;
	}

	public void setOverKohonen(boolean isOverKohonen) {
		this.isOverKohonen = isOverKohonen;
	}

	protected boolean isOverHopfield;

	public boolean isOverHopfield() {
		return isOverHopfield;
	}

	synchronized public void setOverHopfield(boolean isOverHopfield) {
		this.isOverHopfield = isOverHopfield;
	}

	public void setKFilteringFactor(float filteringFactor) {
		kFilteringFactor = filteringFactor;
	}

	public boolean isPerfectInputMode;



	public boolean isPerfectInputMode() {
		return isPerfectInputMode;
	}

	synchronized public void setPerfectInputMode(boolean isPerfectInputMode) {
		//reset the grid
		bTrainingImage = new BufferedImage(512, 512,
				BufferedImage.TYPE_INT_RGB);
		if(trainingPanel!=null)
			trainingPanel.repaint();
		this.isPerfectInputMode = isPerfectInputMode;
	}


	private java.awt.geom.Point2D.Float oldLocation;
	private float oldAccelY;
	private float oldAccelX;
	private float oldAccelSize;
	private NetworkVisualisePanel panels[];
	private NetworkVisualisePanel trainingPanel, trainingPanelDigital, trainingPanelResult;

	private TextRenderer titleRenderer;
	private float oldRadius;
	private Bbox regionOfInterest;
	public RectangularClusterTracker firstClusterFinder;
	public FilterChain filterchain;
	private int classifiedClass;
	private double classifyResults[];
	private ch.unizh.ini.jaer.projects.hopfield.orientationlearn.HopfieldRecognitionFilter.TargetDetector targetDetect;

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

			// use the old difference too
			accelerationSize = (float) ((accelerationSize * kFilteringFactor) + (oldAccelSize * (1.0 - kFilteringFactor)));
			accelerationX = (float) ((accelerationX * kFilteringFactor) + (oldAccelX * (1.0 - kFilteringFactor)));
			accelerationY = (float) ((accelerationY * kFilteringFactor) + (oldAccelY * (1.0 - kFilteringFactor)));
			if (oldLocation.x == 0 && oldLocation.y == 0) {

			} else {
				oldAccelX = accelerationX;
				oldAccelY = accelerationY;
				oldAccelSize = accelerationSize;
				clst.setRadius(oldRadius + accelerationSize);
				location.x = oldLocation.x + accelerationX;
				location.y = oldLocation.y + accelerationY;
			}
			// create the new position

			// current location time previous location

			oldLocation = (Float) location.clone();
			oldRadius = clst.getRadius();
			float aspectRatio = clst.getAspectRatio();
			Bbox box = getBox(radius, location, aspectRatio);

			return box;
		}
		return null;
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
			decayPointer = 0;
			classifiedClass = -1; // not classified
			firstClusterFinder.setFilterEnabled(true);
			firstClusterFinder.setDynamicSizeEnabled(true);
			finalClassifier = new HopfieldNetwork(hopfieldGridX * hopfieldGridY);
			titleRenderer = new TextRenderer(new Font("Helvetica", Font.PLAIN,
					16));
			//toBeTrained = new boolean[hopfieldGridX * hopfieldGridY];

			// filterchain.reset();
			

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

	public HopfieldRecognitionFilter(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		targetDetect = new TargetDetector(chip);
	}

	public boolean isGeneratingFilter() {
		return true;
	}

	private boolean classifyEnabled = getPrefs().getBoolean(
			"SimpleOrientationFilter.classifyEnabled", false);

	public boolean isClassifyEnabled() {
		return classifyEnabled;
	}

	public void setClassifyEnabled(boolean classifyEnabled) {
		classifyCounter = 0;
		this.classifyEnabled = classifyEnabled;

	}

	{
		setPropertyTooltip("showHopfieldEnabled",
		"shows line of average orientation");
	}

	/**
	 * events must occur within this time along orientation in us to generate an
	 * event
	 */

	private boolean regionOfInterestEnabled = getPrefs().getBoolean(
			"SimpleOrientationFilter.regionOfInterestEnabled", false);

	public boolean isRegionOfInterestEnabled() {
		return regionOfInterestEnabled;
	}

	synchronized public void setRegionOfInterestEnabled(boolean regionOfInterestEnabled) {
		this.regionOfInterestEnabled = regionOfInterestEnabled;
	}

	private int hopfieldGridX = getPrefs().getInt(
			"SimpleOrientationFilter.hopfieldGridX", 32);
	{
		setPropertyTooltip("hopfieldGridX",
		"Shift subsampled timestamp map stores by this many bits");
	}

	private int hopfieldGridY = getPrefs().getInt(
			"SimpleOrientationFilter.hopfieldGridX", 32);
	{
		setPropertyTooltip("hopfieldGridY",
		"Shift subsampled timestamp map stores by this many bits");
	}

	public float decayParameter = getPrefs().getFloat(
			"SimpleOrientationFilter.decayParameter", (float) 0.78);
	public float trainThreshold = getPrefs().getFloat(
			"SimpleOrientationFilter.trainThreshold", (float) 0.7);
	//	private boolean train = getPrefs().getBoolean(
	//			"SimpleOrientationFilter.train", false);

	{
		setPropertyTooltip("train", "Presents the pattern to Hopfield Network");
	}

	private int[][][] lastTimesMap;
	private int annotateCounter;



	public Object getFilterState() {
		return lastTimesMap;
	}

	synchronized public void resetFilter() {
		if (!isFilterEnabled())
			return;

	}

	/** overrides super method to allocate or free local memory */
	@Override
	synchronized public void setFilterEnabled(boolean yes) {
		super.setFilterEnabled(yes);
		if (yes) {
			resetFilter();
		} else {
			lastTimesMap = null;
			// lastOutputTimesMap=null;
			out = null;
		}
	}



	public void resetHopfield() {
		toBeTrained = new boolean[hopfieldGridX * hopfieldGridY];

		this.grid = new double[hopfieldGridX * hopfieldGridY];
		this.hopfield = new HopfieldNetwork[4];
		eventCounter = 0;
		annotateCounter = 0;
		int i = 0;
		for (i = 0; i < 4; i++)
			this.hopfield[i] = new HopfieldNetwork(this.hopfieldGridX
					* this.hopfieldGridY);
	}

	public void initFilter() {
		resetHopfield();
		resetFilter();
		targetDetect.setFilterEnabled(true);
	}

	public void update(Observable o, Object arg) {
		initFilter();
	}
	
	

	private void moveToGrid(){
		if ( bTrainingImage != null) {
			int index = 0;
			double ratioX = (double) bTrainingImage.getWidth() / hopfieldGridX;
			double ratioY = (double) bTrainingImage.getHeight() / hopfieldGridY;
			int momentCenterXs[] = new int[9];
			int momentCenterYs[] = new int[9];
			int dotCounts[] = new int[9];




			for (int i = 0; i < hopfieldGridY; i++) {

				for (int j = 0; j < hopfieldGridX; j++) {
					int rgbValue = (int) (grid[index++] * 255);

					int rgb = makeARGB(255, rgbValue, rgbValue, rgbValue);// Integer.parse("0xFF"+
					// characterRep+characterRep+characterRep);
					if (bTrainingImage != null && !isPerfectInputMode) {// check to remove
						int xPos = (int) (j * ratioX);
						int yPos = (int) (i * ratioY);
						if(grid[index - 1] > trainThreshold){
							momentCenterXs[0]+=j;//(j-(hopfieldGridX/2));
							momentCenterYs[0]+=i;//(i-(hopfieldGridY/2));
							dotCounts[0]++;
							//if up
							if(i<=hopfieldGridY/2){
								momentCenterXs[1]+=j;//(j-(hopfieldGridX/2));
								momentCenterYs[1]+=i;//(i-(hopfieldGridY/4));
								dotCounts[1]++;
							}
							//if down
							if(i>hopfieldGridY/2){
								momentCenterXs[2]+=j;//(j-(hopfieldGridX/2));
								momentCenterYs[2]+=i;//(i-(3*hopfieldGridY/4));
								dotCounts[2]++;
							}


							//if left
							if(j<=hopfieldGridX/2){
								momentCenterXs[3]+=j;//(j-(hopfieldGridX/4));
								momentCenterYs[3]+=i;//(i-(hopfieldGridY/2));
								dotCounts[3]++;
							}
							//if right
							if(j>hopfieldGridX/2){
								momentCenterXs[4]+=j;//(j-(3*hopfieldGridX/4));
								momentCenterYs[4]+=i;//(i-(hopfieldGridY/2));
								dotCounts[4]++;
							}
							//if up left
							if(i<=hopfieldGridY/2 && j<=hopfieldGridX/2){
								momentCenterXs[5]+=j;//(j-(hopfieldGridX/4));
								momentCenterYs[5]+=i;//(i-(hopfieldGridY/4));
								dotCounts[5]++;
							}
							//if up right
							if(i<=hopfieldGridY/2 && j>hopfieldGridX/2){
								momentCenterXs[6]+=j;//(j-(3*hopfieldGridX/4));
								momentCenterYs[6]+=i;//(i-(hopfieldGridY/4));
								dotCounts[6]++;
							}
							//if bottom left
							if(i>hopfieldGridY/2 && j<=hopfieldGridX/2){
								momentCenterXs[7]+=j;//(j-(hopfieldGridX/4));
								momentCenterYs[7]+=i;//(i-(3*hopfieldGridY/4));
								dotCounts[7]++;
							}
							//if bottom right
							if(i>hopfieldGridY/2 && j>hopfieldGridX/2){
								momentCenterXs[8]+=j;//(j-(3*hopfieldGridX/4));
								momentCenterYs[8]+=i;//(i-(3*hopfieldGridY/4));
								dotCounts[8]++;
							}
						}

						for (int k = 0; k < ratioY; k++) {
							for (int l = 0; l < ratioX; l++) {
								try{
									if (grid[index - 1] > trainThreshold) {
										bTrainingImageDigital.setRGB(xPos + l,
												(yPos + k), 0xFFFFFF);
										toBeTrained[index - 1] = true;

									} else {
										bTrainingImageDigital.setRGB(xPos + l,
												(yPos + k), 0x000000);
										toBeTrained[index - 1] = false;
									}

									bTrainingImage
									.setRGB(xPos + l, (yPos + k), rgb);
								}
								catch(Exception e){

								}
							}
						}

					}
				}
			}
			//int i = 0;
			int colors[] = new int[9];
			colors[0] = 0xFF0000;
			colors[1] = 0x00FF00;
			colors[2] = 0x0000FF;
			colors[3] = 0xFF00FF;
			colors[4] = 0xFFFF00;
			colors[5] = 0x00FFFF;
			colors[6] = 0xF000F0;
			colors[7] = 0xF0F000;
			colors[8] = 0x00CCCC;
			for(int i = 5;i<9;i++){
				if(dotCounts[i]==0)
					dotCounts[i] = 1;
				momentCenterXs[i]*=ratioX;
				momentCenterYs[i]*=ratioY;

				momentCenterXs[i]/=dotCounts[i];
				momentCenterYs[i]/=dotCounts[i];
				try{
					for (int k = 0; k < ratioY/3; k++) {
						for (int l = 0; l < ratioX/3; l++) {
							bTrainingImageDigital.setRGB((int)(momentCenterXs[i])+l,
									(int)(momentCenterYs[i])+k, colors[i]);


						}
					}
					//				bTrainingImageDigital.setRGB(momentCenterXs[i],
					//						momentCenterYs[i], 0xFF0000);
				}
				catch(Exception e){

				}


			}

			//divide into 4 quadrants
			for(int k = 0;k<bTrainingImageDigital.getWidth();k++){
				for(int j = 0;j<bTrainingImageDigital.getHeight();j++){
					if(k == bTrainingImageDigital.getWidth()/2 || j == bTrainingImageDigital.getHeight()/2){
						bTrainingImageDigital.setRGB(j, k, 0xFF0000);
					}
				}
			}

			if (trainingPanel != null) {
				trainingPanel.repaint();
				trainingPanelDigital.repaint();
			}

		}
	}


	public void annotate(Graphics2D g) {

	}
	long last_graph_time = 0;
	synchronized public void annotate(GLAutoDrawable drawable) {
		if (!isAnnotationEnabled())
			return;
		GL gl = drawable.getGL();
		if (isRegionOfInterestEnabled()) {
			firstClusterFinder.annotate(drawable);
		}
		if(!isLogDataEnabled){
			moveToGrid();
		}


		if (isClassifyEnabled()  ) {
			if(!isLogDataEnabled)
				doClassifyData();

			titleRenderer.beginRendering(drawable.getWidth(), drawable.getHeight());
			titleRenderer.endRendering();
			gl.glPushAttrib( GL.GL_DEPTH_BUFFER_BIT );
			gl.glPushAttrib( GL.GL_COLOR_BUFFER_BIT ); {
				gl.glDisable( GL.GL_DEPTH_TEST );
				gl.glEnable (GL.GL_BLEND);
				gl.glBlendFunc (GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

				// Draw a rectangle under part of image 
				// to prove alpha works.
				gl.glColor4f( .0f, 1.0f, 0.0f, .5f );
				int midX = 50;
				int midY = 0;
				int textMidX = 50, textMidY = 0;
				boolean drawFollowCursor = false;

				if (isRegionOfInterestEnabled()) {
					if (regionOfInterest != null ) {
						//draw inside region of interest
						if(drawFollowCursor){
							midX = (int) ((regionOfInterest.endx + regionOfInterest.startx)/2);
							midY = (int) ((regionOfInterest.endy + regionOfInterest.starty)/2);
						}
						else{
							if(classifiedClass>=0){
								textMidX = (int) ((regionOfInterest.endx + regionOfInterest.startx)/2);
								textMidY = (int) ((regionOfInterest.endy + regionOfInterest.starty)/2);

								titleRenderer.draw(classNames[classifiedClass], textMidX+(drawable.getWidth()/2), textMidY+(drawable.getHeight()/2));
							}
						}
					}

				}	
				if(classifyResults!=null){
					for(int i = 0;i<classifyResults.length;i++){
						int y = 12*i;
						int width = (int) (100 * (classifyResults[i]));
						titleRenderer.draw(classNames[i], midX+50, midY+10+(y*3));
						gl.glColor4f(colors[i][0], colors[i][1], colors[i][2], .5f);
						//					if(i == classifiedClass){
						//						gl.glColor4f( 1.0f, 0.0f, 0.0f, .5f );
						//					 	
						//					}
						//					else{
						//						gl.glColor4f( .0f, 1.0f, 0.0f, .5f );
						//					 	
						//					}
						gl.glRecti( midX -50,  midY-5+y, midX-50+width, midY+5+y );

					}

					gl.glColor3f( 0.0f, 0.0f, 0.0f );

				} gl.glPopAttrib(); 
				gl.glPopAttrib();	
			}

		} 
		if(activityChart!=null){
			if(classifyResults!=null){
				for(int i = 0;i<classifyResults.length;i++){

					Date now = new Date();
					long msTime = now.getTime();
					if(last_graph_time == 0)
						last_graph_time = msTime;
					msTime -= last_graph_time;
					activitySeries[i].add( msTime, (float) classifyResults[i]);
					timeAxis.setMaximum(msTime);
					timeAxis.setMinimum(msTime - 1000 * ACTVITY_SECONDS_TO_SHOW);
					activityAxis.setMaximum(1.0);
					activityAxis.setMinimum(0.0);//(1.0);

					activityChart.display();  
				}
			}
		}

	}

	private static int makeARGB(int a, int r, int g, int b) {
		return a << 24 | r << 16 | g << 8 | b;
	}

	public int returnGrayScaleAverage(int pixel) {
		int red   = (pixel >> 16) & 0xff;
		int green = (pixel >>  8) & 0xff;
		int blue  = (pixel      ) & 0xff;
		// Deal with the pixel as necessary...
		return (red+green+blue)/3;
	}
	public void annotate(float[][][] frame) {
		// if(!isAnnotationEnabled()) return;
	}

	public void clear() {
		int index = 0;
		for (int y = 0; y < this.hopfieldGridY; y++) {
			for (int x = 0; x < this.hopfieldGridX; x++) {
				this.grid[index++] = 0;
			}
		}
	}


	/**
	 * filters in to out. if filtering is enabled, the number of out may be less
	 * than the number putString in
	 * 
	 * @param in
	 *            input events can be null or empty.
	 *@return the processed events, may be fewer in number.
	 */



	private int eventCounter;
	private int decayThreshold = getPrefs().getInt(
			"SimpleOrientationFilter.decayThreshold", 500);
	{
		setPropertyTooltip("hopfieldGridX",
		"will be added");
	}


	public int getDecayThreshold() {
		return decayThreshold;
	}

	public void setDecayThreshold(int decayThreshold) {
		this.decayThreshold = decayThreshold;
	}

	synchronized public EventPacket filterPacket(EventPacket in) {
		if (in == null)
			return null;
		if (!filterEnabled)
			return in;
		if (enclosedFilter != null)
			in = enclosedFilter.filterPacket(in);
		if (isRegionOfInterestEnabled()) {
			targetDetect.filterPacket(in);
		}
		int mainWidth = chip.getSizeX(), mainHeight = chip.getSizeY();
		int n = in.getSize();
		if (n == 0)
			return in;
		regionOfInterest = detectTarget();
		Class inputClass = in.getEventClass();
		if (!(inputClass == PolarityEvent.class || inputClass == BinocularEvent.class)) {
			log.warning("wrong input event type " + in.getEventClass()
					+ ", disabling filter");
			setFilterEnabled(false);
			return in;
		}

		// check for binocular input


		checkOutputPacketEventType(in);



		OutputEventIterator outItr = out.outputIterator();

		int sizex = chip.getSizeX() - 1;
		int sizey = chip.getSizeY() - 1;


		for (Object ein : in) {
			PolarityEvent e = (PolarityEvent) ein;
			BasicEvent i=(BasicEvent)e;
			BasicEvent o=(BasicEvent)outItr.nextOutput();

			o.copyFrom(i);

			// getString times to neighbors in all directions
			// check if search distance has been changed before iterating - for
			// some reason the synchronized doesn't work
			eventCounter++;
			//if(eventCounter >= decayThreshold){
			decayAwayGrid();
			//eventCounter = 0;
			//}

			// check if inside the region of interest
			int insideRegionX = 0, leftSideRegionX = 0, insideRegionY = 0, bottomSideX = 0;
			if (!isRegionOfInterestEnabled()) {
				insideRegionX = e.x;
				insideRegionY = e.y;
			} else {
				if (regionOfInterest != null) {
					mainWidth = (int) (regionOfInterest.endx - regionOfInterest.startx);
					mainHeight = (int) (regionOfInterest.endy - regionOfInterest.starty);
					insideRegionX = (int) (e.x - (regionOfInterest.startx));
					leftSideRegionX = (int) (regionOfInterest.endx - e.x);
					insideRegionY = (int) (e.y - (regionOfInterest.starty));
					bottomSideX = (int) (regionOfInterest.endy - e.y);
				} else {
					insideRegionX = -50;
					insideRegionY = -50;
					leftSideRegionX = -50;
					bottomSideX = -50;
					clear();
				}
			}

			if ((insideRegionX >= 0 && bottomSideX >= 0
					&& insideRegionY >= 0 && leftSideRegionX >= 0)
					|| !isRegionOfInterestEnabled()) {
				// int xPos = insideRegionX >> (int)
				// log2(Math.ceil((double) (mainWidth /
				// hopfieldGridX)));
				// int yPos = ((insideRegionY >> (int)
				// log2(Math.ceil((double) (mainHeight /
				// hopfieldGridY))) * hopfieldGridX));
				int ratioX = (int) (double) mainWidth
				/ hopfieldGridX;

				int ratioY = (int) (double) mainHeight
				/ hopfieldGridY;

				int xPos = insideRegionX / ratioX;

				int yPos = (hopfieldGridY - (insideRegionY / ratioY));
				if(yPos < 0)
					yPos = 0;
				if(yPos >= hopfieldGridY)
					yPos = hopfieldGridY - 1;
				yPos  *= hopfieldGridX;
				if ((xPos + yPos) >= (hopfieldGridX * hopfieldGridY)) {
					xPos = (hopfieldGridX * hopfieldGridY)
					- (yPos + 1);
				}
				int new_value = -1;
				if (e.type == 1)
					new_value = 1;

				double next_value = grid[xPos + yPos]
				                         + (new_value * (1 - decayParameter));
				if (next_value < 0) {
					next_value = 0;
				}
				if (next_value > 1) {
					next_value = 1;
				}
				grid[xPos + yPos] = next_value;
				//continue;
			}
		}
		if (isClassifyEnabled()  && isLogDataEnabled) {
			moveToGrid();
			doClassifyData();
		}
		return out;
	}
	public float getMinDecayParameter(){
		return 0;
	}

	public float getMaxDecayParameter(){
		return 1;
	}
	public float getMinTrainThreshold(){
		return 0;
	}

	public float getMaxTrainThreshold(){
		return 1;
	}

	private void decayAwayGrid() {
		// scroll through the array each time multiplying
		grid[decayPointer] *= decayParameter;
		decayPointer++;
		if(decayPointer >= hopfieldGridX * hopfieldGridY)
			decayPointer = 0;
		//		
		//		for (int i = 0; i < (hopfieldGridX * hopfieldGridY); i++) {
		//			grid[i] *= decayParameter;
		//		}
	}


	


	// mert's additions to train
	public HopfieldNetwork hopfield[]; // there should be 4
	private HopfieldNetwork finalClassifier;
	private double grid[]; // there should be a grid per each grid
	private boolean tobePrinted[];
	private boolean toBeTrained[];

	void train(int direction) {
		// convert to boolean
		toBeTrained = new boolean[hopfieldGridX * hopfieldGridY];
		double ratioX = (double)  hopfieldGridX/bTrainingImage.getWidth();
		double ratioY = (double)  hopfieldGridY / bTrainingImage.getHeight();
		BufferedImageOp op = new AffineTransformOp(AffineTransform
				.getScaleInstance(ratioX, ratioY), new RenderingHints(
						RenderingHints.KEY_INTERPOLATION,
						RenderingHints.VALUE_INTERPOLATION_BICUBIC));
		int index = 0;
		BufferedImage dst = op.filter(bTrainingImage, null);
		int height = dst.getHeight();
		int width = dst.getWidth();
		for (int i = 0; i < dst.getHeight(); i++) {
			for (int j = 0; j < dst.getWidth(); j++) {
				int avgValue = returnGrayScaleAverage(dst.getRGB(j, i));
				if (avgValue >= 33) {
					if(index<hopfieldGridX*hopfieldGridY)
						toBeTrained[index] = true;
				} else {
					if(index<hopfieldGridX*hopfieldGridY)
						toBeTrained[index] = false;
				}

				index++;

			}
		}
		boolean fromHopfield[];
		int [] intToBeTrained = new int[toBeTrained.length];

		for(int j = 0; j<toBeTrained.length;j++){
			if(toBeTrained[j])
				intToBeTrained[j] = 1;
			else
				intToBeTrained[j] = 0;
		}
		IntMatrix output = ImageTransforms.calculate1DHadamard(IntMatrix.createColumnMatrix(intToBeTrained));
		boolean[] pattern = new boolean[output.size()];
		int sqrted = (int) Math.sqrt(output.size());
		for(int k = 0;k<pattern.length;k++){
			if(output.get(k, 0)/sqrted > 0){
				pattern[k] = true;
			}
			else{
				pattern[k] = false;
			}
		}
		if (bimages != null)
			this.hopfield[direction].trainWithImage(pattern,
					bimages[direction]);
		else
			this.hopfield[direction].train(pattern);
		// panel.setImage(bimage);
		if (panels != null && panels[direction] != null)
			panels[direction].repaint();

	}

	void printMatrix(int direction) {
		IntMatrix m = this.hopfield[direction].getMatrix();
		int i = 0;
		int j = 0;
		String outputLine = "\t";
		for (j = 0; j < m.getCols(); j++) {
			outputLine += j + "\t";
		}
		for (i = 0; i < m.getRows(); i++) {
			outputLine += "\n" + i + ":\t";
			for (j = 0; j < m.getCols(); j++) {
				outputLine += (int) m.get(i, j) + "\t";
			}
		}
		try {
			// Create file
			FileWriter fstream = new FileWriter("/tmp/out" + direction + ".txt");
			BufferedWriter out = new BufferedWriter(fstream);
			out.write(outputLine);
			// Close the output stream
			out.close();
		} catch (Exception e) {// Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
	}
	private JFrame learningWindow,kohonenWindow;
	private int trainingMaterialNumber = 0;
	private String classNames[];
	private Label foundLabel;
	private TrainingData trainingData;

	public void doConvolveText(){
		float[] elements = { 1.0f, 1.0f, 1.0f,
				0.0f, 0.0f, 0.0f,
				0.0f, 0.0f, 0.0f};
		BufferedImage tmp2 = null;
		try {
			tmp2 = ImageIO.read( new File( "/tmp/mert.jpg" ) );
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}//new BufferedImage(bTrainingImage.getWidth(), bTrainingImage.getHeight(), BufferedImage.TYPE_INT_RGB);

		BufferedImage tmp = new BufferedImage(tmp2.getWidth(), tmp2.getHeight(), BufferedImage.TYPE_INT_RGB);

		for(int k=0; k<tmp2.getWidth(); k++)
		{
			for(int j = 0; j < tmp2.getHeight(); j++)
				tmp.setRGB(k, j, tmp2.getRGB(k, j));
		}


		//	BufferedImage bimg = new 	BufferedImage(bTrainingImage.getWidth(),bTrainingImage.getHeight(),BufferedImage.TYPE_INT_RGB);
		Kernel kernel = new Kernel(3, 3, elements);
		ConvolveOp cop = new ConvolveOp(kernel);

		try {
			bTrainingImageDigital = ImageIO.read( new File( "/tmp/mert.jpg" ) );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}//tmp;//cop.filter(tmp,null);
		trainingPanel.repaint();
		trainingPanelDigital.repaint();
	}
	synchronized public void doTrainPerfectData(){
		trainingData = new TrainingData();
		//use training data to train current
		int width = 256;
		int height = 256;
		classNames = new String[trainingData.getNumberOfElements()];
		for(int i = 0;i<trainingData.getNumberOfElements();i++){
			bTrainingImage = new BufferedImage(width, height,BufferedImage.TYPE_INT_RGB);
			try {
				String filePath = trainingData.getPathOfTrainingMaterial(i);
				URL imageURL = getClass().getResource("/ch/unizh/ini/jaer/projects/hopfield/orientationlearn/resources/"+ filePath);
				bTrainingImage = ImageIO.read(new File(imageURL.toURI()));

				this.train(0);
				String name = trainingData.getNameOfTrainingMaterial(i);

				//instead of the real data, train the transformed one!
				int [] intToBeTrained = new int[toBeTrained.length];

				for(int j = 0; j<intToBeTrained.length;j++){
					if(toBeTrained[j])
						intToBeTrained[j] = 1;
					else
						intToBeTrained[j] = 0;
				}
				IntMatrix output = ImageTransforms.calculate1DHadamard(IntMatrix.createColumnMatrix(intToBeTrained));
				boolean[] pattern = new boolean[output.size()];
				int sqrted = (int) Math.sqrt(output.size());
				for(int k = 0;k<pattern.length;k++){
					if(output.get(k, 0)/sqrted > 0){
						pattern[k] = true;
					}
					else{
						pattern[k] = false;
					}
				}

				//finalClassifier.trainForClassification(toBeTrained,name);
				if(isOverHopfield)
					finalClassifier.trainForClassification(pattern, name);
				classNames[i] = name;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		colors = new float[trainingData.getNumberOfElements()][3];
		Random random = new Random();
		for(int i = 0; i<trainingData.getNumberOfElements();i++){
			colors[i][0] = random.nextFloat();
			colors[i][1] = random.nextFloat();
			colors[i][2] = random.nextFloat();
		}
		toBeTrained = new boolean[hopfieldGridX * hopfieldGridY];
	}
	private KohonenAlgorithm kohonenPanel;
	synchronized public void doShowKohonen(){
		//add a text field to show which one is found?

		//String path = trainingMaterials[trainingMaterialNumber];
		if(kohonenWindow == null){

			kohonenWindow = new JFrame("Kohonen Map");
			// window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			GridLayout myLayout = new GridLayout(1, 1);
			myLayout.setHgap(0);
			myLayout.setVgap(0);
			kohonenWindow.setLayout(myLayout);
		}
		kohonenPanel = new KohonenAlgorithm();
		kohonenPanel.init();

		kohonenWindow.getContentPane().add(kohonenPanel);

		kohonenWindow.pack();
		kohonenWindow.setVisible(true);
		kohonenWindow.setSize(new java.awt.Dimension(800, 800));


	}
	
	synchronized public void doTrainMoments(){
		//add a text field to show which one is found?

		//String path = trainingMaterials[trainingMaterialNumber];
//		if(kohonenWindow == null){
//
//			kohonenWindow = new JFrame("Kohonen Map");
//			// window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
//			GridLayout myLayout = new GridLayout(1, 1);
//			myLayout.setHgap(0);
//			myLayout.setVgap(0);
//			kohonenWindow.setLayout(myLayout);
//		}
		kohonenPanel = new KohonenAlgorithm();
		kohonenPanel.init();
		//this.doShowLearning();
		//this.doTrainPerfectData();
//		kohonenWindow.getContentPane().add(kohonenPanel);
//
//		kohonenWindow.pack();
//		kohonenWindow.setVisible(true);
//		kohonenWindow.setSize(new java.awt.Dimension(800, 800));


	}
	
	
	synchronized public void doShowLearning() {
		//add a text field to show which one is found?

		//String path = trainingMaterials[trainingMaterialNumber];
		if(learningWindow == null){

			learningWindow = new JFrame("Accumulation Process");
			// window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			GridLayout myLayout = new GridLayout(3, 3);
			myLayout.setHgap(0);
			myLayout.setVgap(0);
			learningWindow.setLayout(myLayout);
		}
		int width = 256;
		int height = 256;
		// initialize bimages
		// Create buffered image that does not support transparency
		bTrainingImage = new BufferedImage(width, height,
				BufferedImage.TYPE_INT_RGB);


		bTrainingImageDigital = new BufferedImage(width, height,
				BufferedImage.TYPE_INT_RGB);
		bTrainingImageHopfieldResult = new BufferedImage(width, height,
				BufferedImage.TYPE_INT_RGB);

		
		

		if(isPerfectInputMode){

			bTrainingImage = new BufferedImage(width, height,BufferedImage.TYPE_INT_RGB);
			try {
				String filePath = trainingData.getPathOfTrainingMaterial(trainingMaterialNumber);
				URL imageURL = getClass().getResource("/ch/unizh/ini/jaer/projects/hopfield/orientationlearn/resources/"+ filePath);
				bTrainingImage = ImageIO.read(new File(imageURL.getFile()));
				bTrainingImageDigital = ImageIO.read(new File(imageURL.getFile()));

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			trainingMaterialNumber = (trainingMaterialNumber + 1) % (trainingData.getNumberOfElements());
		}
		trainingPanel = new NetworkVisualisePanel(bTrainingImage);
		trainingPanelDigital = new NetworkVisualisePanel(bTrainingImageDigital);
		trainingPanelResult = new NetworkVisualisePanel(bTrainingImageHopfieldResult);
		learningWindow.getContentPane().removeAll();
		learningWindow.getContentPane().add(trainingPanel);
		learningWindow.getContentPane().add(trainingPanelDigital);

		learningWindow.getContentPane().add(trainingPanelResult);

		//		JPanel classificationResultPanel = new JPanel();
		//		FlowLayout resultLayout = new FlowLayout();
		//	
		//		classificationResultPanel.setLayout(resultLayout);
		foundLabel = new Label("none found yet");
		//		classificationResultPanel.add(foundLabel);

		learningWindow.getContentPane().add(foundLabel);

		learningWindow.pack();
		learningWindow.setVisible(true);
		learningWindow.setSize(new java.awt.Dimension(800, 800));
		//trainingMaterialNumber = (trainingMaterialNumber+1) % maxTrainingMaterial;
		//graph??
		activityChart = new XYChart("");
		activityChart.setBackground(Color.black);
		activityChart.setForeground(Color.white);
		activityChart.setGridEnabled(false);
		activitySeries = new Series[trainingData.getNumberOfElements()];
		timeAxis = new Axis(0, ACTVITY_SECONDS_TO_SHOW);
		timeAxis.setTitle("time");
		timeAxis.setUnit(ACTVITY_SECONDS_TO_SHOW / 60 + " minutes");

		activityAxis = new Axis(0, 1); // will be normalized
		activityAxis.setTitle("confidence");

		for(int i = 0; i<trainingData.getNumberOfElements();i++){
			activitySeries[i] = new Series(2, NUM_ACTIVITY_SAMPLES);
			activityCategory = new Category(activitySeries[i], new Axis[]{timeAxis, activityAxis});

			activityCategory.setColor(new float[]{colors[i][0], colors[i][1], colors[i][2]});

			activityChart.addCategory(activityCategory);
			activityAxis.setUnit("events");

		}

		activityChart.setToolTipText("Shows recent activity");

		//add all graphs here!

		learningWindow.getContentPane().add(activityChart);
		//bTrainingImageHopfieldResult = kohonenPanel.resultImages[closest];
		//bTrainingImage = kohonenPanel.resultImages[closest];
		trainingPanelResult.repaint();
		learningWindow.repaint();
	}

	/**
	 * @return the hopfieldGridX
	 */
	public int getHopfieldGridX() {
		return hopfieldGridX;
	}

	/**
	 * @param hopfieldGridX
	 *            the hopfieldGridX to set
	 */
	synchronized public void setHopfieldGridX(int hopfieldGridX) {
		this.hopfieldGridX = hopfieldGridX;
		resetHopfield();
	}

	/**
	 * @return the hopfieldGridY
	 */
	public int getHopfieldGridY() {
		return hopfieldGridY;
	}

	/**
	 * @param hopfieldGridY
	 *            the hopfieldGridY to set
	 */
	synchronized public void setHopfieldGridY(int hopfieldGridY) {
		this.hopfieldGridY = hopfieldGridY;
		resetHopfield();
	}



	//	/**
	//	 * @return the train
	//	 */
	//	public boolean isTrain() {
	//		return train;
	//	}
	//
	//	/**
	//	 * @param train
	//	 *            the train to set
	//	 */
	//	public void setTrain(boolean train) {
	//		if (train) {
	//			resetHopfield();
	//			setShowHopfieldEnabled(true);
	//		}
	//		this.train = train;
	//	}

	/**
	 * prints the matrix
	 */
	//	public void doPrintMatrix() {
	//		for (int i = 0; i < 4; i++) {
	//			printMatrix(i);
	//		}
	//	}
	//
	//	public void doTrainFinalNetwork() {
	//		// getString the pixel data
	//
	//		// train the final network for classification
	//
	//		finalClassifier.trainForClassification(pixelBuffer);
	//		doClearBuffer();
	//	}

	synchronized public void doTrainHopfieldNetwork() {
		// getString the pixel data
		//	doVisualise();
		this.train(0);
		finalClassifier.trainForClassification(toBeTrained,"");
		// train the final network for classification

	}
	private int classifyCounter = 0;
	private boolean isJustEuclidean = true;

	public boolean isJustEuclidean() {
		return isJustEuclidean;
	}

	public void setJustEuclidean(boolean isJustEuclidean) {
		this.isJustEuclidean = isJustEuclidean;
	}

	synchronized public void doClassifyData() {
		//		// getString the pixel data
		//
		//		// getString the final network from hopfield and then from classification
		//
		//toBeTrained = new boolean[hopfieldGridX * hopfieldGridY];
		if(classNames == null){
			//time to train
			doTrainPerfectData();
		}	
		if(bTrainingImageDigital == null)
			doShowLearning();
		double ratioX = (double)  hopfieldGridX/bTrainingImage.getWidth();
		double ratioY = (double)  hopfieldGridY / bTrainingImage.getHeight();

		int index = 0;

		//		BufferedImageOp op = new AffineTransformOp(AffineTransform
		//		.getScaleInstance(ratioX, ratioY), new RenderingHints(
		//				RenderingHints.KEY_INTERPOLATION,
		//				RenderingHints.VALUE_INTERPOLATION_BICUBIC));
		//		BufferedImage dst = op.filter(bTrainingImageDigital, null);
		//		for (int i = 0; i < dst.getHeight(); i++) {
		//			for (int j = 0; j < dst.getWidth(); j++) {
		//				int avgValue = returnGrayScaleAverage(dst.getRGB(j, i));
		//				if (avgValue >= 33) {
		//					if(index<hopfieldGridX*hopfieldGridY)
		//						toBeTrained[index] = true;
		//				} else {
		//					if(index<hopfieldGridX*hopfieldGridY)
		//						toBeTrained[index] = false;
		//				}
		//
		//				index++;
		//
		//			}
		//		}

		//
		//		
		//		boolean fromHopfield[] =  this.hopfield[0].present(toBeTrained);
		//		boolean beforeHopfield[] = toBeTrained.clone();
		//		fromHopfield = beforeHopfield;
		boolean fromHopfield[];

		if(isOverKohonen||isJustEuclidean){
			ratioX = (double)  hopfieldGridX/bTrainingImage.getWidth();
			ratioY = (double)  hopfieldGridY / bTrainingImage.getHeight();
			BufferedImageOp op = new AffineTransformOp(AffineTransform
					.getScaleInstance(ratioX, ratioY), new RenderingHints(
							RenderingHints.KEY_INTERPOLATION,
							RenderingHints.VALUE_INTERPOLATION_BICUBIC));
			BufferedImage dst = op.filter(bTrainingImageDigital, null);

			classifyResults = kohonenPanel.recognize(dst,isJustEuclidean);
			normalizeResults();
			//draw the smallest in the result screen
			double maxDistance = Integer.MAX_VALUE;
			int closest = -1;
			for(int i = 0;i<classifyResults.length;i++){
				if(classifyResults[i]<maxDistance){
					maxDistance = classifyResults[i];
					closest = i;
				}
				
			}
			BufferedImage resultImage = kohonenPanel.resultImages[closest];
			
			for(int i = 0;i<resultImage.getWidth();i++){
				for(int j = 0;j<resultImage.getHeight();j++){
					bTrainingImageHopfieldResult.setRGB(i,j, resultImage.getRGB(i, j));
				
				}
			}
			
			//bTrainingImageHopfieldResult = kohonenPanel.resultImages[closest];
			//bTrainingImage = kohonenPanel.resultImages[closest];
			trainingPanelResult.repaint();
			learningWindow.repaint();
			return;
		}
		int [] intToBeTrained = new int[toBeTrained.length];

		for(int j = 0; j<toBeTrained.length;j++){
			if(toBeTrained[j])
				intToBeTrained[j] = 1;
			else
				intToBeTrained[j] = 0;
		}
		IntMatrix output = ImageTransforms.calculate1DHadamard(IntMatrix.createColumnMatrix(intToBeTrained));
		boolean[] pattern = new boolean[output.size()];
		int sqrted = (int) Math.sqrt(output.size());
		for(int k = 0;k<pattern.length;k++){
			if(output.get(k, 0)/sqrted > 0){
				pattern[k] = true;
			}
			else{
				pattern[k] = false;
			}
		}

		if(isOverHopfield){
			if(bTrainingImageDigital == null)
				doShowLearning();
			fromHopfield = this.hopfield[0].present(pattern);	
			ratioX = (double) bTrainingImageDigital.getWidth() / hopfieldGridX;
			ratioY = (double) bTrainingImageDigital.getHeight() / hopfieldGridY;
			index = 0;

			for(int i = 0;i<hopfieldGridY;i++){
				for(int j = 0;j<hopfieldGridX;j++){
					int xPos = (int) (j * ratioX);
					int yPos = (int) (i * ratioY);

					for (int k = 0; k < ratioY; k++) {
						for (int l = 0; l < ratioX; l++) {
							if(fromHopfield[index])
								bTrainingImageHopfieldResult.setRGB(xPos + l, (yPos + k), 0xFFFFFF);
							else
								bTrainingImageHopfieldResult.setRGB(xPos + l, (yPos + k), 0x000000);
						}
					}
					index++;
				}
			}
		}
		else{
			fromHopfield = pattern;
			//bTrainingImageHopfieldResult = bTrainingImageDigital;
		}
		//draw fromHopfield



		//getString the array
		//classifyResults = finalClassifier.classify(fromHopfield);
		classifyResults = finalClassifier.classify(fromHopfield);
		//visualize it
		//load the bar
		double maxLikelihood = 0.5;
		classifiedClass = -1;
		for(int i = 0;i<classifyResults.length;i++){

			double likelihood = classifyResults[i];
			Date now = new Date();

			if(isLogDataEnabled && false){
				try {

					logWriter.write("\n"+classifyCounter+ "\t"+likelihood+"\t"+i+"\t"+classNames[i]+"\t"+ now.getTime());
					//if more than a certain confidence level, take a screenshot and print it out!
					if(likelihood > 0.6){
						if(bTrainingImageDigital!=null){
							File file = new File("/tmp/images/my_image_"+classifyCounter+".png");
							ImageIO.write(bTrainingImageDigital, "png", file);

						}

					}


				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}


			}
			if(likelihood > maxLikelihood){
				maxLikelihood = likelihood;
				classifiedClass = i;
			}
		}
		//if(isLogDataEnabled)
		classifyCounter++;
		if(trainingPanelResult == null){
			doShowLearning();
		}
		else{
			if(classifiedClass<0)
				foundLabel.setText("Not Found!");
			else
				foundLabel.setText("Found: "+ classNames[classifiedClass]);

			trainingPanelResult.repaint();
			learningWindow.repaint();
		}
		//write to the output if log enabled?


	}

	private void normalizeResults(){
		double maxDistance = 0;
		for(int i = 0;i<classifyResults.length;i++){
			if(classifyResults[i]>maxDistance){
				maxDistance = classifyResults[i];
			}
		}
		for(int i = 0;i<classifyResults.length;i++){
			classifyResults[i] = classifyResults[i]/maxDistance;
		}
		
	}
	protected boolean isLogDataEnabled;
	//
	//	public void doClearBuffer() {
	//		// getString the pixel data
	//
	//		// train the final network for classification
	//
	//		pixelBuffer = new boolean[hopfieldGridX * hopfieldGridY];
	//
	//	}

	//	public void doVisualise() {
	//		JFrame window = new JFrame("Hopfield Network Progress");
	//		// window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
	//		window.setPreferredSize(new java.awt.Dimension(512, 512));
	//		// window.setLocation(200, 100); // TODO
	//		window.setLayout(new GridLayout(2, 2));
	//		int width = hopfieldGridX * hopfieldGridY;
	//		int height = hopfieldGridX * hopfieldGridY;
	//		// initialize bimages
	//		bimages = new BufferedImage[4];
	//		panels = new NetworkVisualisePanel[4];
	//		// Create buffered image that does not support transparency
	//		for (int i = 0; i < 4; i++) {
	//			bimages[i] = new BufferedImage(width, height,
	//					BufferedImage.TYPE_INT_RGB);
	//			panels[i] = new NetworkVisualisePanel(bimages[i]);
	//			// panels[i].setLayout(new GridLayout(2, 1));
	//			window.getContentPane().add(panels[i]);
	//		}
	//		window.pack();
	//		window.setVisible(true);
	//	}

	public boolean isLogDataEnabled() {
		return isLogDataEnabled;
	}
	FileWriter logStream;
	BufferedWriter logWriter; 

	public void setLogDataEnabled(boolean isLogDataEnabled) {
		if(isLogDataEnabled){
			//open stream
			try {
				String filename;
				Date now = new Date();
				if(isOverHopfield)
					filename= "/tmp/confidence_over_hopfield_"+ now.getTime()+".txt";
				else
					filename= "/tmp/confidence_analog_"+ now.getTime()+".txt";

				logStream = new FileWriter(filename);

				logWriter= new BufferedWriter(logStream);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else{
			//close stream
			// Close the output stream
			try {
				logWriter.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		this.isLogDataEnabled = isLogDataEnabled;
	}

	public boolean[] presentToHopfield(int direction) {

		// TODO: convert to separate function
		return this.hopfield[direction].present(toBeTrained);

	}



	public float getDecayParameter() {
		return decayParameter;
	}

	public void setDecayParameter(float decayParameter) {
		this.decayParameter = decayParameter;
	}

	public float getTrainThreshold() {
		return trainThreshold;
	}

	public void setTrainThreshold(float trainThreshold) {
		this.trainThreshold = trainThreshold;
	}

}
