package ch.unizh.ini.jaer.projects.hopfield.orientationlearn;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Point2D.Float;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.FrameAnnotater;

import com.sun.opengl.util.j2d.TextRenderer;

public class RectangularClusterClassifier extends EventFilter2D implements Observer, FrameAnnotater {
	public RectangularClusterTracker firstClusterFinder;
	public FilterChain filterchain;
	private Bbox regionOfInterest;
	private TargetDetector targetDetect;
//
private java.awt.geom.Point2D.Float oldLocation;
//	private float oldAccelY;
//	private float oldAccelX;
//	private float oldAccelSize;
//	private float oldRadius;
//	private float oldAngle;
//	private float oldAspectRatio;
//	private float oldAccelAngle;
	private float speed1X, speed1Y;
	private boolean isClassifying;
	private int maxClusterNumber = 3;
	private int classificationResult[];
	public boolean isClassifying() {
		return isClassifying;
	}

	public void setClassifying(boolean isClassifying) {
		this.isClassifying = isClassifying;
	}

	private double classifyResults[];
	private float colors[][];
	private TextRenderer titleRenderer;
	private kMeans	kMeansClustifier;
	protected boolean useSpeedData;
	private BufferedImage bPlottingImage;
	private static NetworkVisualisePanel plottingPanel;
	private static JFrame plottingWindow ;
	

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
			rateSum += clusterList.get(ctr).getAvgEventRate() / clusterList.get(ctr).getRadius();
		}
		if (clusterList.size() > 0) {
			Cluster clst = clusterList.get(0);
			float radius = clst.getRadius();
			java.awt.geom.Point2D.Float location = clst.getLocation();
			// calculate the difference
			speed1Y = (-oldLocation.y + location.y);
			speed1X = (-oldLocation.x + location.x);

//			float accelerationY = (-oldLocation.y + location.y);
//			float accelerationX = (-oldLocation.x + location.x);
//			float accelerationSize = -oldRadius + clst.getRadius();// old size -
//			// currentsize
//			float accelerationAngle = -oldAngle + clst.getAngle(); 
//
//			// use the old difference too
//			accelerationSize = (float) ((accelerationSize * kFilteringFactor) + (oldAccelSize * (1.0 - kFilteringFactor)));
//			accelerationX = (float) ((accelerationX * kFilteringFactor) + (oldAccelX * (1.0 - kFilteringFactor)));
//			accelerationY = (float) ((accelerationY * kFilteringFactor) + (oldAccelY * (1.0 - kFilteringFactor)));
//			accelerationAngle= (float) ((accelerationAngle *kFilteringFactor) + (oldAccelAngle*(1.0 - kFilteringFactor)));
//			if (oldLocation.x == 0 && oldLocation.y == 0) {
//
//			} else {
//				oldAccelX = accelerationX;
//				oldAccelY = accelerationY;
//				oldAccelSize = accelerationSize;
//				oldAccelAngle = accelerationAngle;
//				oldAspectRatio = clst.getAspectRatio();
////				clst.setAngle(oldAngle + accelerationAngle);
////				clst.setRadius(oldRadius + accelerationSize);
////				location.x = oldLocation.x + accelerationX;
////				location.y = oldLocation.y + accelerationY;
//			}
//			// create the new position
//
//			// current location time previous location
//			oldAngle = clst.getAngle();
			oldLocation = (Float) location.clone();
//			oldRadius = clst.getRadius();
			float aspectRatio = clst.getAspectRatio();
			Bbox box = getBox(radius, location, aspectRatio);

			return box;
		}
		return null;
	}

	public int train(double carEvtRate2, float firstParam){
		//get last accel
		int classificationLocalResult = -1;
		Map<String,Double> finalMaterial = new HashMap<String,Double>();
		finalMaterial.put("param1", new Double(firstParam));
		finalMaterial.put("param2",new Double(carEvtRate2));
		trainedData.add(finalMaterial);
		
		kMeansClustifier.addDataPoint((double)firstParam,(double) carEvtRate2,bPlottingImage);
		if(plottingPanel!=null){
			plottingPanel.repaint();
			plottingWindow.repaint();
		}
		if(isClassifying){
			classificationLocalResult = kMeansClustifier.classifyPoint((double)firstParam,(double) carEvtRate2);
			System.out.println("Belongs to class:"+ classificationResult);
		}
		return classificationLocalResult;
	}
	
	public void doClearKMeans(){
		kMeansClustifier = new kMeans(2);
		for(int i = 0;i<bPlottingImage.getWidth();i++){
			for(int j = 0;j<bPlottingImage.getHeight();j++){
				bPlottingImage.setRGB(i, j, 0x000000);
				
			}
		}
		if(plottingPanel!=null){
			plottingPanel.repaint();
			plottingWindow.repaint();
		}
	}
	
	public void doRunClustering(){
		for(int i = 0;i<bPlottingImage.getWidth();i++){
			for(int j = 0;j<bPlottingImage.getHeight();j++){
				bPlottingImage.setRGB(i, j, 0x000000);
				
			}
		}
		kMeansClustifier.runKMeans(bPlottingImage);
		if(plottingPanel!=null){
			plottingPanel.repaint();
			plottingWindow.repaint();
		}
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
			firstClusterFinder.setAspectRatio((float) 1.0);
			firstClusterFinder.setFilterEnabled(false);
			firstClusterFinder.setClusterLifetimeIncreasesWithAge(true);
			firstClusterFinder.setClusterLifetimeWithoutSupportUs(22582);
			firstClusterFinder.setClusterSize((float) 0.1054);//(538425);
			firstClusterFinder.setDynamicAngleEnabled(false);
			firstClusterFinder.setDynamicAspectRatioEnabled(false);
			firstClusterFinder.setDynamicSizeEnabled(true);
			firstClusterFinder.setEnableClusterExitPurging(true);
			firstClusterFinder.setGrowMergedSizeEnabled(true);
			firstClusterFinder.setHighwayPerspectiveEnabled(false);
			firstClusterFinder.setMaxNumClusters(maxClusterNumber);
			firstClusterFinder.setMixingFactor((float) 0.0023);
			firstClusterFinder.setPathLength(100);
			firstClusterFinder.setPredictiveVelocityFactor(1);
			firstClusterFinder.setSurround((float) 2.5);
			firstClusterFinder.setThresholdEventsForVisibleCluster(500);//3000
			firstClusterFinder.setThresholdVelocityForVisibleCluster((float) 0.0);
			firstClusterFinder.setUseVelocity(true);
			firstClusterFinder.setVelocityPoints(10);
			firstClusterFinder.setPathsEnabled(false);
			filterchain = new FilterChain(chip);
			firstClusterFinder.setEnclosed(true, this);
			firstClusterFinder.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled", this);
			firstClusterFinder.setColorClustersDifferentlyEnabled(true);
			firstClusterFinder.setShowAllClusters(true);
			
			
			firstClusterFinder.setEnclosed(true, this);
			firstClusterFinder.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled", this);
			filterchain = new FilterChain(chip);
			filterchain.add(firstClusterFinder);
//			filterchain.add(secondClusterFinder);

			setEnclosedFilterEnabledAccordingToPref(firstClusterFinder, null);
		
//			setEnclosedFilterEnabledAccordingToPref(secondClusterFinder, null);

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
		private NetworkVisualisePanel myVisualisePanel;
		
		public void initFilter() {
			kFilteringFactor = (float) 0.05;
			firstClusterFinder.setFilterEnabled(true);
//			secondClusterFinder.setFilterEnabled(true);
			trainedData = new Vector<Map<String,Double>>();
			colors = new float[15][3];
			Random random = new Random();
			for(int i = 0; i<15;i++){
				colors[i][0] = random.nextFloat();
				colors[i][1] = random.nextFloat();
				colors[i][2] = random.nextFloat();
			}
			titleRenderer = new TextRenderer(new Font("Helvetica", Font.PLAIN,16));
			kMeansClustifier = new kMeans(2);
			bPlottingImage = new BufferedImage(600,600,BufferedImage.TYPE_INT_RGB);
			oldLocation = new java.awt.geom.Point2D.Float();
			oldLocation.x = 0;
			oldLocation.y = 0;
			classificationResult = new int[maxClusterNumber];
			for(int i = 0;i<maxClusterNumber;i++)
				classificationResult[i] = -1;
		
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

		public void doCluster(){
			//should clusterifize
			kMeansClustifier.runKMeans();
			System.out.println(kMeansClustifier);
			
		}

	}
	private Vector<Map<String, Double>> trainedData;



	

	public void doShowPlotting(){
		plottingPanel = new NetworkVisualisePanel(bPlottingImage);
		plottingWindow = new JFrame("Cluster Results");
		// window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
	//	GridLayout myLayout = new GridLayout(0,2);
		//plottingWindow.setLayout(myLayout);
		plottingWindow.setSize(new java.awt.Dimension(100, 100));
		
		plottingWindow.getContentPane().removeAll();
		
//		Insets insets = plottingWindow.getInsets();
//		Dimension size = plottingPanel.getPreferredSize();
//		plottingPanel.setBounds(25 + insets.left, 5 + insets.top,
//		             size.width, size.height);
//		
		
		
//		
//		Insets insets = frame.getInsets();
//		frame.setSize(300 + insets.left + insets.right,
//		              125 + insets.top + insets.bottom);
		
		URL imageURL3 = getClass().getResource("/ch/unizh/ini/jaer/projects/hopfield/orientationlearn/resources/place_holder.png");
		try {
			BufferedImage place_holder = ImageIO.read(new File(imageURL3.toURI()));
			//plottingWindow.getContentPane().add(new NetworkVisualisePanel(place_holder));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

		
		
		
		URL imageURL2 = getClass().getResource("/ch/unizh/ini/jaer/projects/hopfield/orientationlearn/resources/size.png");
		try {
			BufferedImage size = ImageIO.read(new File(imageURL2.toURI()));
			NetworkVisualisePanel tempPanel = new NetworkVisualisePanel(size);
			tempPanel.setPreferredSize(new Dimension(600, 30));
			plottingWindow.getContentPane().add(tempPanel,BorderLayout.PAGE_END);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
		URL imageURL = getClass().getResource("/ch/unizh/ini/jaer/projects/hopfield/orientationlearn/resources/aspect_ratio.png");
		try {
			BufferedImage aspectRatio = ImageIO.read(new File(imageURL.toURI()));
			NetworkVisualisePanel tempPanel = new NetworkVisualisePanel(aspectRatio);
			tempPanel.setPreferredSize(new Dimension(30, 600));
		
			plottingWindow.getContentPane().add(tempPanel, BorderLayout.LINE_START);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		plottingWindow.getContentPane().add(plottingPanel,BorderLayout.CENTER);
		
		
		plottingWindow.setVisible(true);
		
	}

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
		if (!isFilterEnabled())
			return in;
		targetDetect.filterPacket(in);
		regionOfInterest = detectTarget();	
//		regionOfInterestTwo = detectTarget();
		
//		if(regionOfInterest == null){
//			didHaveBefore = false;
//			eventCounter = 0;
//		}
//		else{
			//doClassify();
//			if(eventCounter == 10){
//				didHaveBefore = true;
//				eventCounter = 0;
//				doTrain();
//			}
//			if(!didHaveBefore){
//				eventCounter++;
//			}
			//if(eventCounter % 10 == 0){
		if(firstClusterFinder.getNumVisibleClusters()>0){
		for(int clustNumber = 0;clustNumber<firstClusterFinder.getNumClusters();clustNumber++){
			if(firstClusterFinder.getClusters().get(clustNumber).isVisible()){
			double velocitySquare = Math.sqrt((speed1X * speed1X) + (speed1Y * speed1Y));
			double measuredRadius = velocitySquare/firstClusterFinder.getClusters().get(clustNumber).getMeasuredRadius();
			double measuredAspectRatio = firstClusterFinder.getClusters().get(clustNumber).getMeasuredAspectRatio();
			
			double param1Ratio = 3;
			double param2Ratio = 30;
			
			
			//check if inside the square, ignore edge boxes
			if(measuredRadius <= 20 && regionOfInterest.startx > chip.getSizeX()/10 && regionOfInterest.endx < chip.getSizeX()*9 / 10){ // (chip.getSizeX() - (regionOfInterest.endx+(firstClusterFinder.getClusters().get(0).getRadius()/2)) > 0)){
			//firstParam = firstClusterFinder.getClusters().get(0).getAspectRatio();
				classificationResult[clustNumber] = train((measuredRadius*600/param2Ratio),(float) (measuredAspectRatio*600/param1Ratio));
	//			classificationResult[clustNumber] = train((testParam*600/param1Ratio),(float) (testParam*600/param1Ratio));
			}
			}
		}
		
		}
	
	else{
		for(int i = 0 ;i<maxClusterNumber; i++)
		classificationResult[i] = -1; //unclassified
	}
		
				
				
				
			//}
			//eventCounter++;
			

			
//		}
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
		if(isFilterEnabled()){
			
				if(!firstClusterFinder.getClusters().isEmpty()){
					for(int i = 0;i<firstClusterFinder.getNumClusters();i++){
					if(classificationResult[i] == 0)
						firstClusterFinder.getClusters().get(i).setColor(new Color(0xFF,0x00,0x00));
					else{
						if(classificationResult[i]==1)
							firstClusterFinder.getClusters().get(i).setColor(new Color(0x00,0xFF,0x00));
						else{
							firstClusterFinder.getClusters().get(i).setColor(new Color(0x00,0x00,0xFF));
						}
					}
					}
				}
					
//				if(!secondClusterFinder.getClusters().isEmpty() && classificationResult == 1)
//					secondClusterFinder.getClusters().get(0).setColor(new Color(0x00,0xFF,0x00));
		firstClusterFinder.annotate(drawable);
//		secondClusterFinder.annotate(drawable);
		
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

	}

