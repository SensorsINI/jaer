/*
 * KalmanFilter.java
 *
 * This class provides the RectangularClusterTracker with Kalman filtering. For each cluster instance in the RectangularClusterTracker which is
 *an argument of the constructor, a Kalman Filter datastructure is provided. With a mapToRoad optioin, metric of the
 *state vectors is changed from pixel in meters. The measurements used for the "update" step of the Kalman filter is the
 *positon of the supported cluster. This class has to be instancieated only once per RectangularClusterTracker. Multiple instances of
 *clusters are managed via LinkedList(to look which clusters are new and which died) and a HashMap which maps a Cluster to
 *its datastructure.
 */

package net.sf.jaer.eventprocessing.tracking;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.Matrix;

import com.jogamp.opengl.util.gl2.GLUT;


public class KalmanFilter extends EventFilter2D implements FrameAnnotater, Observer{//, PreferenceChangeListener {


	//    static Preferences prefs=Preferences.userNodeForPackage(KalmanFilter.class);

	List<Cluster> clusters;
	AEChip chip;
	AEChipRenderer renderer;
	GLUT glut;

	//This variable is the minimum number of events that happen till the Kalman Filters are recalculated
	private int nbOfEventsTillTrack = getPrefs().getInt("KalmanFilter.nbOfEventsTillTrack",100);
	//Variable sets the distance to the vanishing point in meters
	private float distToVanishingPoint= getPrefs().getFloat("KalmanFilter.distToVanishingPoint",300f);
	//the measurement variance for the Kalman Filters(for each cluster the same)
	private float maxMeasurementVariance = getPrefs().getFloat("KalmanFilter.measurementVariance",8f);
	//the process Variance for each parameter
	private float minProcessVariance = getPrefs().getFloat("KalmanFilter.minProcessVariance",1f);
	//the height of the bridge
	private float bridgeHeight = getPrefs().getFloat("KalmanFilter.bridgeHeight",5f);
	//with this option enabled, the metric of the kalman filter switches from pixel to meters
	private boolean mapToRoad=getPrefs().getBoolean("KalmanFilter.mapToRoad", false);
	//The distance to the first pixel we see
	private int distTo1Px = getPrefs().getInt("KalmanFilter.distTo1Px",2);
	//with this option on, the clusters are set to the actual positioin of the kalman filter after calculation.
	private boolean feedbackToCluster = getPrefs().getBoolean("KalmanFilter.feedbackToCluster",false);
	//    private boolean doLog = prefs.getBoolean("KalmanFilter.doLog",false);
	private boolean useDynamicVariances = getPrefs().getBoolean("KalmanFilter.useDynamicVariances",true);

	private float processVariance;
	private float measurementVariance;

	private float beta; //Angle between highway and camera at the first pixel
	private float cameraAngle;//Opening angle of the camera
	private int dimStateVector; //the dimension of x - the state vector
	private int dimMeasurement; //the dimension of the measurement(here x and y =2)

	//The instance of the cluster tracker to getString the data(measurements)
	private RectangularClusterTracker tracker;
	//private float clusterSize=prefs.getFloat("RectangularClusterTracker.clusterSize",.2f);

	//for doing a log file
	final String nl = System.getProperty("line.separator");

	/** Creates a new instance of KalmanFilter */
	public KalmanFilter(AEChip chip, RectangularClusterTracker tracker) {
		super(chip);
		this.chip=chip;
		renderer=chip.getRenderer();
		glut = chip.getCanvas().getGlut();
		initFilter();
		chip.addObserver(this);
		//        prefs.addPreferenceChangeListener(this);
		this.tracker = tracker;

	}

	/**
	 *Initialises the filter and geometry is recalculated
	 */
	@Override
	public void initFilter() {

		//        System.out.println(clusters.toString());
		//        clusters = RectangularClusterTracker.getClusters();
		//        if(clusters != null){
		//        for(Cluster c:clusters){
		//            kalmans.putString(c,new ClusterData(c));
		//        }}
		beta = (float)Math.atan(bridgeHeight/distTo1Px);//Angle between highway and camera at the first pixel
		cameraAngle = beta - (float)Math.atan(bridgeHeight/distToVanishingPoint);
		dimStateVector = 4;
		dimMeasurement = 2;
	}


	//    ArrayList<Cluster> pruneList=new ArrayList<Cluster>(1);
	//The clusters are managed in a linked list, has to be a dynamic collection
	LinkedList<Cluster> pruneList = new LinkedList<Cluster>();
	HashMap<Cluster,ClusterData> zombieList = new HashMap<Cluster,ClusterData>();
	//We use the hashmap to map the clusters to their Kalman Filter datastructure
	HashMap<Cluster,ClusterData> kalmans = new HashMap<Cluster,ClusterData>();
	//just an iterator that counts how many events that happened since the last calculation
	private int iteratorNbOfEventsTillTrack = 0;

	/**
	 * Here a packet of events is processed. First the method checks, if enough events happened to calculate the Kalman Filter.
	 * If not, it happens nothing, else for every cluster there is the prediction and update step is done.
	 *
	 *@param ae A Packet of events to process
	 */
	synchronized private void track(EventPacket<? extends BasicEvent> ae){
		clusters = tracker.getClusters();
		pruneList.addAll(tracker.getPruneList());
		iteratorNbOfEventsTillTrack+=ae.getSize();
		if(iteratorNbOfEventsTillTrack >= nbOfEventsTillTrack){
			iteratorNbOfEventsTillTrack = 0;
		}else{
			return;
		}
		//check if they have still support, if not appendCopy them to the zombie list.
		for(Cluster c:pruneList){
			kalmans.remove(c);
			continue;
		}
		pruneList.clear();
		for(Cluster c:clusters){
			if(kalmans.containsKey(c)){
				kalmans.get(c).predict();
				kalmans.get(c).update();
				//                if(doLog){
					//                    try {
				//                        logWriter.write(c.getLastEventTimestamp() + "\t"+ distAtPixel(c.getLocation()).y + "\t" +kalmans.getString(c).x[0]+ "\t" +
				//                                kalmans.getString(c).x[1]+"\t" + kalmans.getString(c).R[0][0] + "\t" + kalmans.getString(c).R[1][1]+ "\t" + kalmans.getString(c).Q[0][0] +
				//                                "\t"+ kalmans.getString(c).Q[1][1] + "\t" +
				//                                kalmans.getString(c).P[0][0] + "\t" + kalmans.getString(c).P[0][1] + "\t"+ kalmans.getString(c).P[1][0] + "\t"+ kalmans.getString(c).P[1][1]+ nl);
				//                    } catch (IOException ex) {
				//                        ex.printStackTrace();
				//                    }
				//                }
			}else{
				kalmans.put(c,new ClusterData(c));
				//                if(doLog){
				//                    try {
				//                        logWriter.write("#new cluster"+nl);
				//                    } catch (IOException ex) {
				//                        ex.printStackTrace();
				//                    }
				//                }
			}
		}
	}


	@Override
	public String toString(){
		return "KalmanFilter.toString not yet implemented";
	}
	/**
	 *The data class for each Cluster. In this data structure all the necessary matrices for the Kalman Filter is stored. Each
	 *cluster is assigned to such a structure. The class provides also the algorithm itself(prediction and update step) of
	 *the KF.
	 */
	final public class ClusterData{
		Cluster c; //the observation
		private int latestTimeStampOfLastStep;
		private float[][] F; //Transition Model(tranistion of x(t-1) -> x(t)
		private float[][] B; //maps the control vector on the state vector
		private float[][] Q; //variance matrix of normally distributed process noise
		private float[][] H; //observation model: maps x on z(state vector onto the observation)
		private float[][] R; //variance matrix of  normally distributed observation noise
		private float[][] Pp; //pred. error cov. matrix
		private float[][] P; //error cov. matrix
		private float[] x; //the state vector
		private float[] xp; //the predicted state vector
		private float[] z; // observation
		private float[] u; //control vector
		private float[] y; //error/residual of measurement resp. innovation

		private float deltaTime;

		/**
		 * The only constructor. For a Cluster it creates the necessary datastructures.
		 *@param c The Cluster for which the data are initialized.
		 */
		ClusterData(Cluster c){
			this.c = c;
			initData();
		}

		/**
		 *This method initializes the Kalman Filter for the supported cluster depending on the actual cluster position and
		 *the chosen metric(see mapToRoad parameter).
		 */
		private void initData(){
			F = new float[dimStateVector][dimStateVector];
			Q = new float[dimStateVector][dimStateVector];
			B = new float[dimStateVector][dimStateVector];
			H = new float[dimMeasurement][dimStateVector];
			R = new float[dimMeasurement][dimMeasurement];
			Pp = new float[dimStateVector][dimStateVector];
			P = new float[dimStateVector][dimStateVector];
			xp = new float[dimStateVector];
			x = new float[dimStateVector];
			z = new float[dimMeasurement];
			u = new float[dimStateVector];
			y = new float[dimMeasurement];

			latestTimeStampOfLastStep = c.getLastEventTimestamp()-c.getLifetime();
			deltaTime = c.getLifetime();

			measurementVariance = 1;
			processVariance = maxMeasurementVariance+100;//depends actually on how many events we collect till track...

			//since we are little unsure about the initial position:
				Matrix.identity(P);
				Matrix.identity(F);

				H[0][0] = 1; H[0][1] = 0; H[0][2] = 0;H[0][3] = 0;
				H[1][0] = 0; H[1][1] = 0; H[1][2] = 1;H[1][3] = 0;

				Matrix.zero(R);
				R[0][0] = measurementVariance; R[1][1] = measurementVariance;


				Matrix.zero(Q);


				B[0][0]= 0;B[1][1]=0;B[0][1]=0;B[1][0]=0;
				if(!mapToRoad){
					x[0] = (float)c.getLocation().getY();
					x[1] = 0; // artificial initial velocity in y- direction ( units in Pixel)
					x[2] = (float)c.getLocation().getX();
					x[3] = 0; // artificial initial velocity in x- direction
					//System.out.println("new init at y = " + x[0]);
					z[0] = c.getLocation().y; // the observation
					z[1] = c.getLocation().x;

				}else{
					/*
					 *If map to raod option is selected, we calculate the distance to the pixel where the Cluster is in meters
					 *and store this distance in the state vector, but only for y-coordinate.
					 *This is done by distAtPixelY.
					 */
					Point2D.Float pointInMeters = distAtPixel(c.getLocation());
					x[0] = pointInMeters.y;
					x[1] = 0; // artificial initial velocity in y- direction in [m/s]
					x[2] = pointInMeters.x;
					x[3] = 0; // artificial initial velocity in x- direction

					z[0] = pointInMeters.y;
					z[1] = pointInMeters.x;
				}
		}

		/**
		 *The prediction step of the KalmanFilter. The new predicted state vector xp and also the Error cov.Matrix P is
		 *calculated.
		 */
		void predict(){
			//xp = addVector(multmatrix(F,x),multMatrix(G,a));
			updateVariances();
			deltaTime = (c.getLastEventTimestamp() - latestTimeStampOfLastStep)*1.e-6f;
			//System.out.println("deltaTime in s = " + deltaTime);
			if(c.getLastEventTimestamp() > latestTimeStampOfLastStep) {
				latestTimeStampOfLastStep = c.getLastEventTimestamp();
			}
			F[0][1]= deltaTime; F[2][3]= deltaTime;
			B[0][0] = (float)Math.pow(deltaTime,2)/2f;
			B[1][1] = deltaTime;
			xp =  multMatrix(F,x);
			//System.out.println("predicted x:");Matrix.print(xp);


			Q[0][0] = (float)Math.pow(deltaTime,4)/4;  Q[0][1] = (float)Math.pow(deltaTime,3)/2;
			Q[1][0] = Q[0][1];                  Q[1][1] = (float)Math.pow(deltaTime,2);
			//Q = multMatrix(Q,(processVariance*perspectiveScale(c.getLocation())));
			//Matrix.print(Q);
			//            Matrix.identity(Q);
			//Q = multMatrix(Q,processVariance);
			//Q[0][0] = processVariance;Q[1][1] = processVariance;
			//Pp = F*P*F'+Q
			Q = multMatrix(Q,processVariance);
			Pp = addMatrix(multMatrix(multMatrix(F,P),transposeMatrix(F)),Q);

			//System.out.println("predicted P:");Matrix.print(Pp);
		}


		private float mixingFactor = tracker.getMixingFactor();
		private void updateVariances(){
			if(useDynamicVariances){
				if (processVariance > minProcessVariance) {
					processVariance = ((1-mixingFactor)*processVariance) - (mixingFactor*minProcessVariance);
				}
				else {
					processVariance = minProcessVariance;
				}
				if(measurementVariance < maxMeasurementVariance) {
					measurementVariance = ((1-mixingFactor)*measurementVariance) + (mixingFactor*maxMeasurementVariance);
				}
				else {
					measurementVariance = maxMeasurementVariance;
				}
			}else{
				processVariance = minProcessVariance;
				measurementVariance = maxMeasurementVariance;
			}
		}

		/**
		 *The update step for the Kalman Filter. As measurement the actual position of the supported cluster is taken.
		 *Depending on the metric(see map to road) the measurement is first translated in meters.
		 */
		void update(){
			//the observation or measurement:
			if(!mapToRoad){
				z[0] = c.getLocation().y; // the observation
				z[1] = c.getLocation().x;

			}else{
				Point2D.Float pm = distAtPixel(c.getLocation());
				z[0] = pm.y;
				z[1] = pm.x;
			}

			//z[1] = 0; // the velocity observation (doesn't matter)
			//H[0][0] = perspectiveScale(c.getLocation());

			//y = z-H*xp
			float[] yTemp = new float[dimMeasurement];

			yTemp = multMatrix(H,xp);
			Matrix.subtract(z,yTemp,y);
			//System.out.println("The error(y[2] should be 0) ~y = ");Matrix.print(y);

			//update R:
			R[0][0] = measurementVariance; R[1][1] = measurementVariance;

			//S = H*Pp*H'+R
			float[][] S = new float[dimMeasurement][dimMeasurement];
			float[][] STemp = new float[dimMeasurement][dimStateVector];
			float[][] STemp2 = new float[dimMeasurement][dimMeasurement];
			Matrix.multiply(H,Pp,STemp);
			Matrix.multiply(STemp,transposeMatrix(H),STemp2); Matrix.add(STemp2,R,S);

			//K = Pp*H'*inv(S)
			float[][] K = new float[dimStateVector][dimMeasurement];
			float[][] Ktemp = new float[dimStateVector][dimMeasurement];
			//System.out.println("H: ");Matrix.print(H);
			Matrix.multiply(Pp,transposeMatrix(H),Ktemp);
			//System.out.println("\nS: "); Matrix.print(S);

			Matrix.invert(S);
			//System.out.println("\ninv(S): "); Matrix.print(S);
			//S[0][0] = 1/S[0][0];
			//System.out.println("\nMatrix inv(S):");Matrix.print(S);
			Matrix.multiply(Ktemp,S,K);
			//System.out.println("\nKtemp: "); Matrix.print(K);
			//System.out.println("\nThe new Kalman Gain:");Matrix.print(K);

			//P = (I-K*H)Pp
			float[][] I = new float[dimStateVector][dimStateVector];
			float[][] Ptemp = new float[dimStateVector][dimStateVector];
			Matrix.identity(I);
			Matrix.multiply(K,H,P);Matrix.subtract(I,P,Ptemp);Matrix.multiply(Ptemp,Pp,P);
			//System.out.println("\nthe new P:\n");Matrix.print(P);

			//x = xp + K*y
			float[] xTemp = new float[dimStateVector];
			Matrix.multiply(K,y,xTemp);
			Matrix.add(xp,xTemp,x);

			if(feedbackToCluster){
				if(!mapToRoad) {
					c.setLocation(new Point2D.Float(x[2],x[0]));
				}
				else {
					c.setLocation(pixelYAtDist(new Point2D.Float(x[2],x[0])));
				}
			}

			//System.out.println("\nThe new state vector:\ny: " + x[0]);
			//System.out.println("velocity:" + x[1]);

		}


	};

	/**
	 *This method takes a point p. Depending on parameters(distanceToVanishingPoint, distanceTo1Px, bridgeHeight) and also depending
	 *on  if a pixel is set as vanishing point, the distance in meters is calculated. For the x value, the center of the
	 *window is assumed to be 0 and the right side the positive one.
	 *@param p The pixel for which the distance on the road is calcuated
	 *@return How many meters a pixel is away on the road.
	 */
	private final Point2D.Float distAtPixel(Point2D.Float p){
		Point2D.Float r = new Point2D.Float();
		if(!renderer.isPixelSelected()) {
			r.y = bridgeHeight / (float)Math.tan(beta - (p.y* (cameraAngle/chip.getSizeY())));
		}
		else {
			r.y =  bridgeHeight / (float)Math.tan(beta - (p.y* (cameraAngle/renderer.getYsel())));
		}
		//with y, calculate now the x
		float maxX = (r.y+distTo1Px)*(float)Math.tan(cameraAngle/2);
		r.x = (maxX / chip.getSizeX()/2) * (p.x - (chip.getSizeX()/2f));

		return r;
	}

	/**
	 *This method calculates which y-coordinate a pixel has, that is a certain distance ( in meters ) away.
	 *@param meters The distance in meters
	 *@return the y-coordinate of the pixel with that distance.
	 */
	private final Point2D.Float pixelYAtDist(Point2D.Float pMeters){
		Point2D.Float p = new Point2D.Float();
		if(!renderer.isPixelSelected()) {
			p.y = ((beta-(float)(Math.atan(bridgeHeight/pMeters.y)))/(cameraAngle/chip.getMaxSize()));
		}
		else {
			p.y = (int)((beta-(float)(Math.atan(bridgeHeight/pMeters.y)))/(cameraAngle/renderer.getYsel()));
		}

		float maxX = (pMeters.y+distTo1Px)*(float)Math.tan(cameraAngle/2);
		p.x = (pMeters.x/(maxX/chip.getSizeX()/2))+(chip.getSizeX()/2f);
		return p;
	}
	/**
	 *This method calculates which y-coordinate a pixel has, that is a certain distance ( in meters ) away.
	 *@param meters The distance in meters
	 *@return the y-coordinate of the pixel with that distance as a float(exact position)
	 */
	private final float floatPixelYAtDist(float meters){
		if(!renderer.isPixelSelected()) {
			return ((beta-(float)(Math.atan(bridgeHeight/meters)))/(cameraAngle/chip.getMaxSize()));
		}
		else {
			return ((beta-(float)(Math.atan(bridgeHeight/meters)))/(cameraAngle/renderer.getYsel()));
		}
	}
	/**
	 *This is a linear map that maps a cluster at the vanishing point to 0, and at the buttom of the window the result would
	 *be 1.
	 *@param p Pixel to map
	 *@return The linear map from 0 to 1, depending on position p.
	 */
	private final float perspectiveScale(Point2D.Float p){
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
	final static float[][] transposeMatrix(float[][] a){
		int ra = a.length; int ca = a[0].length;
		float[][] m = new float[ca][ra];
		for(int i = 0; i< ra;i++){
			for(int j = 0; j < ca; j++) {
				m[j][i] = a[i][j];
			}
		}
		return m;
	}

	final static float[][] multMatrix(float[][] a, float s){
		int ra = a.length; int ca = a[0].length;
		float[][] m = new float[ra][ca];
		for(int i = 0; i < ra;i++) {
			for(int j = 0; j < ca; j++) {
				m[i][j] = a[i][j]*s;
			}
		}
		return m;
	}

	final static float[][] multMatrix(float[][] a, float[][] b){
		int ra = a.length; int ca = a[0].length;
		int rb = b.length; int cb = b[0].length;
		if(ca != rb){ System.err.println("Matrix dimensions do not agree"); return null;}
		float[][] m = new float[ra][cb];
		for(int i = 0; i < ra;i++) {
			for(int j = 0; j < cb; j++){
				m[i][j] =0;
				for(int k = 0; k < ca;k++) {
					m[i][j] += a[i][k]*b[k][j];
				}
			}
		}
		return m;
	}

	final static float[] multMatrix(float[][] a, float[] x){
		int ra = a.length; int ca = a[0].length;
		if(ca != x.length){ System.err.println("Matrix dimensions do not agree"); return null;}
		float[] m = new float[ra];
		for(int i = 0; i < ra; i++){
			m[i] =0;
			for(int k = 0; k < ca;k++) {
				m[i] += a[i][k]*x[k];
			}
		}
		return m;
	}

	final static float[][] addMatrix(float [][] a, float[][] b){
		int ra = a.length; int ca = a[0].length;
		int rb = b.length; int cb = b[0].length;
		if((ca != cb) || (ra != rb)){ System.err.println("Matrix dimensions do not agree"); return null;}
		float[][] m = new float[ra][cb];
		for(int i = 0; i < ra;i++) {
			for(int j = 0; j < cb; j++) {
				m[i][j] = a[i][j]+b[i][j];
			}
		}
		return m;
	}

	final static float[] addVector(float[] a, float[] b){
		int ra = a.length; int rb = b.length;
		if(ra != rb){System.err.println("Vector dimension do not agree.");return null;}
		float[] m = new float[ra];
		for(int i = 0; i< ra; i++){
			m[i] = a[i]+b[i];
		}
		return m;
	}

	final void drawFilter(final ClusterData cd, float[][][] fr, Color color){
		int xp0 = 0; //y-position of prediction
		int xp2 = 0;//x-position of prediction vector
		int x0 = 0; //y-position of state vector
		int x2 = 0;//x-position of statevector;
		if(!mapToRoad){
			xp0 = Math.round(cd.xp[0]);
			x0 = Math.round(cd.x[0] );
			xp2 = Math.round(cd.xp[2]);
			x2 = Math.round(cd.x[2] );
		}else{
			Point2D.Float predXInPx = pixelYAtDist(new Point2D.Float(cd.xp[2],cd.xp[0]));
			Point2D.Float xInPx = pixelYAtDist(new Point2D.Float(cd.x[2],cd.x[0]));

			xp0 = Math.round(predXInPx.y);
			x0 = Math.round(xInPx.y);
			xp2 = Math.round(predXInPx.x);
			x2 = Math.round(xInPx.x);
		}

		//Draw the real statevector

		colorPixel(x2,x0,fr,color);

		//Draw the predictionvector(little circle):
		colorPixel(xp2-1, xp0 ,  fr,color);
		colorPixel(xp2+1, xp0 ,  fr,color);
		colorPixel(xp2,   xp0-1 ,fr,color);
		colorPixel(xp2,   xp0+1 ,fr,color);

		//Print the velocity
		//System.out.println("velocity: " + cd.x[1]);
	}

	static final int clusterColorChannel=2;

	/** @param x x location of pixel
	 *@param y y location
	 *@param fr the frame data
	 *@param channel the RGB channel number 0-2
	 *@param brightness the brightness 0-1
	 */
	final void colorPixel(final int x, final int y, final float[][][] fr, Color color){
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

	public Object getFilterState() {
		return null;
	}

	public boolean isGeneratingFilter() {
		return false;
	}

	@Override
	synchronized public void resetFilter() {
		kalmans.clear();
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

	@Override
	public void update(Observable o, Object arg) {
		initFilter();
	}

	@Override
	synchronized public void annotate(GLAutoDrawable drawable) {
		if(kalmans==null) {
			return;
		}
		final float LINE_WIDTH=1f; // in pixels
		GL2 gl=drawable.getGL().getGL2(); // when we getString this we are already set up with scale 1=1 pixel, at LL corner
		if(!isFilterEnabled()) {
			return;
		}
		float[] rgb=new float[4];
		float x,y;
		int font = GLUT.BITMAP_HELVETICA_12;
		gl.glPushMatrix();
		{
			for(Cluster c:kalmans.keySet()){
				if(c.isVisible()){
					if(!mapToRoad){
						x = kalmans.get(c).x[2];
						y = kalmans.get(c).x[0];
					}else{
						Point2D.Float xInPx = pixelYAtDist(new Point2D.Float(kalmans.get(c).x[2],kalmans.get(c).x[0]));
						x = xInPx.x;
						y = xInPx.y;
					}

					c.getColor().getRGBComponents(rgb);
					gl.glColor3fv(rgb,0);
					gl.glLineWidth(LINE_WIDTH);

					//draw the state vector as a cross
					gl.glBegin(GL.GL_LINES);
					{
						gl.glVertex2f(x-1f,y);
						gl.glVertex2f(x+1f,y);

						gl.glVertex2f(x,y-1f);
						gl.glVertex2f(x,y+1f);

					}
					gl.glEnd();

					//draw the prediction as a square
					if(!mapToRoad){
						x = kalmans.get(c).xp[2];
						y = kalmans.get(c).xp[0];
					}else{
						Point2D.Float xInPx = pixelYAtDist(new Point2D.Float(kalmans.get(c).xp[2],kalmans.get(c).xp[0]));
						x = xInPx.x;
						y = xInPx.y;
					}
					gl.glBegin(GL.GL_LINE_STRIP);
					{
						gl.glVertex2f(x+2f,y);
						gl.glVertex2f(x,y-2f);
						gl.glVertex2f(x-2f,y);
						gl.glVertex2f(x,y+2f);
						gl.glVertex2f(x+2f,y);

					}
					//show the velocity
					gl.glEnd();
					gl.glRasterPos3f(x+2,y+2,0);
					glut.glutBitmapString(font, String.format("v(y) = %.1f",kalmans.get(c).x[1]));
					//show the velocity as a vector
					gl.glBegin(GL.GL_LINES);
					{
						gl.glVertex2f(x,y);
						gl.glVertex2f(x+kalmans.get(c).x[3],y+kalmans.get(c).x[1]);
					}
					gl.glEnd();
				}
			}
		}
		gl.glPopMatrix();
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//setter and getter methods
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void setDistToVanishingPoint(float d){
		if(d <= distTo1Px) {
			d=distTo1Px+1;
		}
		distToVanishingPoint = d;
		getPrefs().putFloat("KalmanFilter.distToVanishingPoint",d);
	}
	public float getDistToVanishingPoint(){
		return distToVanishingPoint;
	}
	public boolean getMapToRoad(){
		return mapToRoad;
	}
	public void setMapToRoad(boolean mapToRoad){
		this.mapToRoad = mapToRoad;
		getPrefs().putBoolean("KalmanFilter.mapToRoad",mapToRoad);
	}
	public boolean getFeedbackToCluster(){
		return feedbackToCluster;
	}
	public void setFeedbackToCluster(boolean feedbackToCluster){
		this.feedbackToCluster = feedbackToCluster;
		getPrefs().putBoolean("KalmanFilter.feedbackToCluster",feedbackToCluster);
	}
	public float getBridgeHeight(){
		return bridgeHeight;
	}
	public void setBridgeHeight(float bridgeHeight){
		if (bridgeHeight < 1) {
			bridgeHeight = 1;
		}
		this.bridgeHeight = bridgeHeight;
		getPrefs().putFloat("KalmanFilter.bridgeHeight",bridgeHeight);
	}
	public float getMaxMeasurementVariance(){
		return maxMeasurementVariance;
	}
	public void setMaxMeasurementVariance(float maxMeasurementVariance){
		if (maxMeasurementVariance < 1) {
			maxMeasurementVariance = 1;
		}
		this.maxMeasurementVariance = maxMeasurementVariance;
		getPrefs().putFloat("KalmanFilter.maxMeasurementVariance",maxMeasurementVariance);
	}
	public float getMinProcessVariance(){
		return minProcessVariance;
	}
	public void setMinProcessVariance(float minProcessVariance){
		if (minProcessVariance < 1) {
			minProcessVariance = 1;
		}
		this.minProcessVariance = minProcessVariance;
		getPrefs().putFloat("KalmanFilter.minProcessVariance",minProcessVariance);
	}
	public int getNbOfEventsTillTrack(){
		return nbOfEventsTillTrack;
	}
	public void setNbOfEventsTillTrack(int nbOfEventsTillTrack){
		if (nbOfEventsTillTrack < 1) {
			nbOfEventsTillTrack = 1;
		}
		this.nbOfEventsTillTrack = nbOfEventsTillTrack;
		getPrefs().putInt("KalmanFilter.nbOfEventsTillTrack",nbOfEventsTillTrack);
	}

	public int getDistTo1Px(){
		return distTo1Px;
	}
	public void setDistTo1Px(int distTo1Px){
		if (distTo1Px < 1) {
			distTo1Px = 1;
		}
		this.distTo1Px = distTo1Px;
		getPrefs().putInt("KalmanFilter.distTo1Px",distTo1Px);
	}
	public void setUseDynamicVariances(boolean useDynamicVariances){
		this.useDynamicVariances = useDynamicVariances;
	}
	public boolean getUseDynamicVariances(){
		return useDynamicVariances;
	}
	private BufferedWriter logWriter;

	//    public void setDoLog(boolean doLog){
	//        Calendar cal = Calendar.getInstance();
	//        System.out.println();
	//
	//        if(doLog){
	//            try{
	//                logWriter = new BufferedWriter(new FileWriter(new File(".","cluster_Kalman_log"+ cal.getString(Calendar.YEAR)+
	//                        (cal.getString(Calendar.MONTH)+1)+cal.getString(Calendar.DAY_OF_MONTH)+"_"+
	//                        cal.getString(Calendar.HOUR_OF_DAY)+cal.getString(Calendar.MINUTE)+"_"+cal.getString(Calendar.SECOND)+".txt")));
	//
	//            }catch(IOException ioe){
	//                System.out.println(ioe.toString());
	//            }
	//        }else{
	//            if(logWriter != null){
	//                try {
	//                    logWriter.close();
	//                } catch (IOException ex) {
	//                    ex.printStackTrace();
	//                }
	//            }
	//        }
	//        prefs.putBoolean("WingTracker.doLog",doLog);
	//        this.doLog = doLog;
	//    }
	//    public boolean getDoLog(){
	//        return doLog;
	//    }

}
