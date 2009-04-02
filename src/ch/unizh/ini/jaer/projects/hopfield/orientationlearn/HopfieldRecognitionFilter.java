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
import java.awt.image.DataBufferByte;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.imageio.ImageIO;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.BinocularOrientationEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OrientationEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.MultipleXYTypeFilter;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.projects.hopfield.matrix.HopfieldNetwork;
import ch.unizh.ini.jaer.projects.hopfield.matrix.IntMatrix;

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
	private int trainingCounter;
	private BufferedImage barImage;
	ByteBuffer barBuffer;
    
    
	public void setKFilteringFactor(float filteringFactor) {
		kFilteringFactor = filteringFactor;
	}
	
	public boolean isPerfectInputMode;
	
	
	
	public boolean isPerfectInputMode() {
		return isPerfectInputMode;
	}

	public void setPerfectInputMode(boolean isPerfectInputMode) {
		//reset the grid
		bTrainingImage = new BufferedImage(512, 512,
				BufferedImage.TYPE_INT_RGB);
		if(trainingPanel!=null)
			trainingPanel.repaint();
		this.isPerfectInputMode = isPerfectInputMode;
	}

	public boolean isMultiHopfield;

	public boolean isMultiHopfield() {
		return isMultiHopfield;
	}

	public void setMultiHopfield(boolean isMultiHopfield) {
		this.isMultiHopfield = isMultiHopfield;
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
	public MultipleXYTypeFilter xyfilter;
	public RectangularClusterTracker secondClusterFinder;
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
			xyfilter = new MultipleXYTypeFilter(chip);
			firstClusterFinder.setMaxNumClusters(1);
			firstClusterFinder.setClusterSize(0.47f);
			firstClusterFinder.setClusterLifetimeWithoutSupportUs(5);
			firstClusterFinder.setPathsEnabled(false);
			secondClusterFinder = new RectangularClusterTracker(chip);
			filterchain = new FilterChain(chip);

			firstClusterFinder.setEnclosed(true, this);
		
			//firstClusterFinder.setClusterSize((float) 1.0);
			xyfilter.setEnclosed(true, this);

			xyfilter.getPropertyChangeSupport().addPropertyChangeListener(
					"filterEnabled", this);
			firstClusterFinder.getPropertyChangeSupport()
			.addPropertyChangeListener("filterEnabled", this);
			secondClusterFinder.getPropertyChangeSupport()
			.addPropertyChangeListener("filterEnabled", this);

			filterchain.add(firstClusterFinder);
			filterchain.add(xyfilter);
			filterchain.add(secondClusterFinder);

			setEnclosedFilterEnabledAccordingToPref(xyfilter, null);
			setEnclosedFilterEnabledAccordingToPref(firstClusterFinder, null);
			setEnclosedFilterEnabledAccordingToPref(secondClusterFinder, null);

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
			classifiedClass = -1; // not classified
			xyfilter.setXEnabled(true);
			xyfilter.setYEnabled(true);
			firstClusterFinder.setFilterEnabled(true);
			xyfilter.setFilterEnabled(false);
			secondClusterFinder.setFilterEnabled(false);
			firstClusterFinder.setDynamicSizeEnabled(true);
			secondClusterFinder.setDynamicSizeEnabled(false);
			xyfilter.setMaxBoxNum(firstClusterFinder.getMaxNumClusters());
			finalClassifier = new HopfieldNetwork(hopfieldGridX * hopfieldGridY);
			titleRenderer = new TextRenderer(new Font("Helvetica", Font.PLAIN,
					16));
			toBeTrained = new boolean[hopfieldGridX * hopfieldGridY];
			trainingCounter = 0;

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

	public static String getDescription() {
		return "Local orientation by spatio-temporal correlation";
	}

	public boolean isGeneratingFilter() {
		return true;
	}

	private boolean showHopfieldEnabled = getPrefs().getBoolean(
			"SimpleOrientationFilter.showHopfieldEnabled", false);
	{
		setPropertyTooltip("showHopfieldEnabled",
		"shows line of average orientation");
	}

	private boolean classifyEnabled = getPrefs().getBoolean(
			"SimpleOrientationFilter.classifyEnabled", false);

	public boolean isClassifyEnabled() {
		return classifyEnabled;
	}

	public void setClassifyEnabled(boolean classifyEnabled) {
		this.classifyEnabled = classifyEnabled;
		if(classifyEnabled){
			doClassifyData();
		}
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

	public void setRegionOfInterestEnabled(boolean regionOfInterestEnabled) {
		this.regionOfInterestEnabled = regionOfInterestEnabled;
	}

	private boolean passAllEvents = getPrefs().getBoolean(
			"SimpleOrientationFilter.passAllEvents", false);
	{
		setPropertyTooltip("passAllEvents",
		"Passes all events, even those that do not get labled with orientation");
	}

	private int hopfieldGridX = getPrefs().getInt(
			"SimpleOrientationFilter.hopfieldGridX", 16);
	{
		setPropertyTooltip("hopfieldGridX",
		"Shift subsampled timestamp map stores by this many bits");
	}

	private int hopfieldGridY = getPrefs().getInt(
			"SimpleOrientationFilter.hopfieldGridX", 16);
	{
		setPropertyTooltip("hopfieldGridY",
		"Shift subsampled timestamp map stores by this many bits");
	}

	public float decayParameter = getPrefs().getFloat(
			"SimpleOrientationFilter.decayParameter", (float) 0.78);
	public float trainThreshold = getPrefs().getFloat(
			"SimpleOrientationFilter.trainThreshold", (float) 0.7);
	private boolean train = getPrefs().getBoolean(
			"SimpleOrientationFilter.train", false);

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
		// allocateMaps(); // will allocate even if filter is enclosed and
		// enclosing is not enabled


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

	

	public void annotate(Graphics2D g) {

	}

	public void annotate(GLAutoDrawable drawable) {

		if (!isAnnotationEnabled())
			return;
		GL gl = drawable.getGL();
		if (isRegionOfInterestEnabled()) {
			firstClusterFinder.annotate(drawable);
		}
	
		if (!isMultiHopfield && bTrainingImage != null) {
			int index = 0;
			double ratioX = (double) bTrainingImage.getWidth() / hopfieldGridX;
			double ratioY = (double) bTrainingImage.getHeight() / hopfieldGridY;

			for (int i = 0; i < hopfieldGridY; i++) {

				for (int j = 0; j < hopfieldGridX; j++) {
					int rgbValue = (int) (grid[index++] * 255);

					int rgb = makeARGB(255, rgbValue, rgbValue, rgbValue);// Integer.parse("0xFF"+
					// characterRep+characterRep+characterRep);
					if (bTrainingImage != null && !isPerfectInputMode) {// check to remove
						int xPos = (int) (j * ratioX);
						int yPos = (int) (i * ratioY);
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

			if (trainingPanel != null) {
				trainingPanel.repaint();
				trainingPanelDigital.repaint();
			}
		}
		if (isClassifyEnabled() && classNames!=null ) {
			//if(eventCounter)
				doClassifyData();
				titleRenderer.beginRendering(drawable.getWidth(), drawable.getHeight());

			      titleRenderer.endRendering();
				
				 gl.glPushAttrib( GL.GL_DEPTH_BUFFER_BIT );
			      gl.glPushAttrib( GL.GL_COLOR_BUFFER_BIT ); {

				gl.glDisable( GL.GL_DEPTH_TEST );
				    
				gl.glEnable (GL.GL_BLEND);
				gl.glBlendFunc (GL.GL_SRC_ALPHA, 
						GL.GL_ONE_MINUS_SRC_ALPHA);
			        
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
			 	for(int i = 0;i<classifyResults.length;i++){
					int y = 12*i;
					int width = (int) (100 * (classifyResults[i]));
					titleRenderer.draw(classNames[i], midX+50, midY+10+(y*3));
					if(i == classifiedClass){
						gl.glColor4f( 1.0f, 0.0f, 0.0f, .5f );
					 	
					}
					else{
						gl.glColor4f( .0f, 1.0f, 0.0f, .5f );
					 	
					}
					gl.glRecti( midX -50,  midY-5+y, midX-50+width, midY+5+y );

				}
				
				gl.glColor3f( 0.0f, 0.0f, 0.0f );

			      } gl.glPopAttrib(); 
			      gl.glPopAttrib();	
				
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
	 * than the number put in
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

			// get times to neighbors in all directions
			// check if search distance has been changed before iterating - for
			// some reason the synchronized doesn't work
			eventCounter++;
			if(eventCounter >= decayThreshold){
			decayAwayGrid();
			eventCounter = 0;
			}
			
			// check if inside the region of interest
			int insideRegionX = 0, leftSideRegionX = 0, insideRegionY = 0, bottomSideX = 0;
			int mainWidth = 128, mainHeight = 128;
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
		for (int i = 0; i < (hopfieldGridX * hopfieldGridY); i++) {
			grid[i] *= decayParameter;
		}
	}

	public boolean isPassAllEvents() {
		return passAllEvents;
	}

	/**
	 * Set this to true to pass all events even if they don't satisfy
	 * orientation test. These passed events have no orientation set.
	 * 
	 * @param passAllEvents
	 *            true to pass all events, false to pass only events that pass
	 *            coicidence test.
	 */
	public void setPassAllEvents(boolean passAllEvents) {
		this.passAllEvents = passAllEvents;
		getPrefs().putBoolean("SimpleOrientationFilter.passAllEvents",
				passAllEvents);
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
		if (bimages != null)
			this.hopfield[direction].trainWithImage(toBeTrained,
					bimages[direction]);
		else
			this.hopfield[direction].train(toBeTrained);
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
	private JFrame learningWindow;
	private int trainingMaterialNumber = 0;
	private String classNames[];
	private Label foundLabel;
	private TrainingData trainingData;
	
	public void doTrainPerfectData(){
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
				finalClassifier.trainForClassification(toBeTrained,name);
				classNames[i] = name;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	public void doShowLearning() {
		//add a text field to show which one is found?
		
		//String path = trainingMaterials[trainingMaterialNumber];
		if(learningWindow == null){

			learningWindow = new JFrame("Accumulation Process");
			// window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			GridLayout myLayout = new GridLayout(2, 2);
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
	public void setHopfieldGridX(int hopfieldGridX) {
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
	public void setHopfieldGridY(int hopfieldGridY) {
		this.hopfieldGridY = hopfieldGridY;
		resetHopfield();
	}

	

	/**
	 * @return the train
	 */
	public boolean isTrain() {
		return train;
	}

	/**
	 * @param train
	 *            the train to set
	 */
	public void setTrain(boolean train) {
		if (train) {
			resetHopfield();
			setShowHopfieldEnabled(true);
		}
		this.train = train;
	}

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
	//		// get the pixel data
	//
	//		// train the final network for classification
	//
	//		finalClassifier.trainForClassification(pixelBuffer);
	//		doClearBuffer();
	//	}

	public void doTrainHopfieldNetwork() {
		// get the pixel data
		//	doVisualise();
		this.train(0);
		finalClassifier.trainForClassification(toBeTrained,"");
		// train the final network for classification

	}

	public void doClassifyData() {
		//		// get the pixel data
		//
		//		// get the final network from hopfield and then from classification
		//
		toBeTrained = new boolean[hopfieldGridX * hopfieldGridY];
		double ratioX = (double)  hopfieldGridX/bTrainingImage.getWidth();
		double ratioY = (double)  hopfieldGridY / bTrainingImage.getHeight();
		BufferedImageOp op = new AffineTransformOp(AffineTransform
				.getScaleInstance(ratioX, ratioY), new RenderingHints(
						RenderingHints.KEY_INTERPOLATION,
						RenderingHints.VALUE_INTERPOLATION_BICUBIC));
		int index = 0;
		BufferedImage dst = op.filter(bTrainingImage, null);
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

		
		boolean fromHopfield[] =  this.hopfield[0].present(toBeTrained);
		//draw fromHopfield
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
		//get the array
		classifyResults = finalClassifier.classify(fromHopfield);
		//visualize it
		//load the bar
		double maxLikelihood = 0.5;
		classifiedClass = -1;
		for(int i = 0;i<classifyResults.length;i++){
			double likelihood = classifyResults[i];
			if(likelihood > maxLikelihood){
				maxLikelihood = likelihood;
				classifiedClass = i;
			}
		}
		if(classifiedClass<0)
			foundLabel.setText("Not Found!");
		else
			foundLabel.setText("Found: "+ classNames[classifiedClass]);
			
		trainingPanelResult.repaint();
		learningWindow.repaint();

	}
	//
	//	public void doClearBuffer() {
	//		// get the pixel data
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

	public boolean[] presentToHopfield(int direction) {

		// TODO: convert to separate function
		return this.hopfield[direction].present(toBeTrained);

	}

	/**
	 * @return the showHopfieldEnabled
	 */
	public boolean isShowHopfieldEnabled() {
		return showHopfieldEnabled;
	}

	/**
	 * @param showHopfieldEnabled
	 *            the showHopfieldEnabled to set
	 */
	public void setShowHopfieldEnabled(boolean showHopfieldEnabled) {
		this.showHopfieldEnabled = showHopfieldEnabled;
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
