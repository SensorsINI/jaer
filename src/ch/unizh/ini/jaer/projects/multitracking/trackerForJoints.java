package ch.unizh.ini.jaer.projects.multitracking;

import static org.bytedeco.javacpp.opencv_core.BORDER_CONSTANT;
import static org.bytedeco.javacpp.opencv_core.CV_32FC3;
import static org.bytedeco.javacpp.opencv_core.CV_8U;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.FileStorage;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacpp.opencv_core.RectVector;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.opencv_objdetect;
import org.bytedeco.javacpp.opencv_objdetect.HOGDescriptor;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import matlabcontrol.MatlabConnectionException;
import matlabcontrol.MatlabInvocationException;
import matlabcontrol.MatlabProxy;
import matlabcontrol.MatlabProxyFactory;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
//import net.sf.jaer.eventprocessing.tracking.EinsteinClusterTracker.Cluster;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Example to compute the location of one joint
 * @author Sophie
 *
 */
@Description("mean event location for a joint in gait analysis")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class trackerForJoints extends EventFilter2D implements FrameAnnotater, Observer{
	private int sx; // set to chip.getSizeX()
	private int sy; // chip.getSizeY()
	private int lastTimestamp = 0;
	private float[] lastFrame = null, outFrame = null;
	// mean coordinate of the tracker center
	Double x=0.;
	Double y=0.;
	// list of the current parallel trackers
	//Vector<TrackFocus> listOfTrackFocus;
	// radius of the tracker zone of interest
	double radius=10;
	Vector<Vector<Float>> joints = new Vector<Vector<Float>>();
	Vector<TrackPoints>jointsToTrack= new Vector<TrackPoints>();
	private String dirPath = getString("dirPath", System.getProperty("user.dir"));
	private boolean  jointsloaded=false;
	ApsFrameExtractor frameExtractor=new ApsFrameExtractor(chip);
	private final FilterChain filterChain;
	private float[] lastFrame1 = null;
	LowpassFilter xFilter = new LowpassFilter();
	LowpassFilter yFilter = new LowpassFilter();
	int lastts = 0;
	private boolean startnewtrack=false;
	RectVector foundLocations1;
	private boolean personDetected=false;
	private boolean startclicktracking=false;
	private boolean clustering=false;
	private Vector<BasicEvent> visitedPoints=new Vector<BasicEvent>();
	private Double epsilon=(double) 10;
	private int MinPts=30;
	private Vector<Cluster> clusters=new Vector<Cluster>();;

	public trackerForJoints(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		frameExtractor = new ApsFrameExtractor(chip);
		filterChain = new FilterChain(chip);
		filterChain.add(frameExtractor);
		frameExtractor.setExtRender(false);
		setEnclosedFilterChain(filterChain);
		resetFilter();
		readMatTextFile();
	}

	// TO DO rewrite it properly
	@Override
	public EventPacket filterPacket(EventPacket in) {
		if(clustering)
		{
			densityBasedClustering(in);// do this check to avoid always running filter
		}
		int n = in.getSize();
		//System.out.println(n);
		if(!filterEnabled)
		{
			return in; // do this check to avoid always running filter
		}
		if(enclosedFilter!=null) {
			in=enclosedFilter.filterPacket(in); // you can enclose a filter in a filter
		}

		getEnclosedFilterChain().filterPacket(in);

		// for each event only keep it if it is within dt of the last time
		// an event happened in the direct neighborhood
		//	        Iterator itr = ((ApsDvsEventPacket) in).fullIterator();
		//	        while (itr.hasNext()) {
		//	            Object o = itr.next();
		//	            if (o == null) {
		//	                break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
		//	            }
		//BasicEvent e = (BasicEvent) o;
		if(startnewtrack==true){
			while(startnewtrack==true){
				Iterator itr = ((ApsDvsEventPacket) in).fullIterator();
				while ((itr.hasNext())&&(startnewtrack==true)) {
					Object o = itr.next();
					if (o == null) {
						break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
					}
					BasicEvent e = (BasicEvent) o;
					//take a picture
					if(frameExtractor.hasNewFrame()){
						float [] lastFrame1 = frameExtractor.getNewFrame();
						//FloatPointer p1 = new FloatPointer(lastFrame1);
						Mat input1 = new Mat(lastFrame1);
						input1.convertTo(input1, CV_8U, 255, 0);
						Mat img1 = input1.reshape(0, sy);
						StartNewTrack(img1);
						System.out.println("tracking is starting");
						startnewtrack=false;
						chip.getAeViewer().getAePlayer().setPaused(false);
					}
				}
			}
		}
		lastts = in.getLastTimestamp();
		if(jointsloaded){
			float xmedian = 0f;
			float ymedian = 0f;

			Vector<Pool> poolOfEvents = new Vector<Pool>();
			for (Object o : in) {
				BasicEvent e = (BasicEvent) o;
				if (e.isSpecial()) {
					continue;
				}
				for(int i=0; i<joints.size(); i++){
					for(int j=0; j<2; j++){
						Pool pool = new Pool();
						poolOfEvents.add(pool);
					}
				}
				for(int i=0; i<joints.size(); i++){
					for(int j=0; j<2; j++){
						if ((e.x<(joints.get(i).get(j)+radius))&&
							(e.x>(joints.get(i).get(j)-radius))&&
							(e.y<(joints.get(i).get(j+2)+radius))&&
							(e.y>(joints.get(i).get(j+2)-radius))) {
							poolOfEvents.get(i+(2*j)).xs.add((int) e.x);
							poolOfEvents.get(i+(2*j)).ys.add((int) e.y);
						}
					}
				}
			}
				for(int i=0; i<joints.size(); i++){
					for(int j=0; j<2; j++){
						Vector<Integer> xs=poolOfEvents.get(i+(2*j)).xs;
						Vector<Integer> ys=poolOfEvents.get(i+(2*j)).ys;
						//xs.sort(null);
						//ys.sort(null);


						if(xs.size()!=0)  { // got actual events
							float x, y;
							if ((xs.size() % 2) != 0) { // odd number points, take middle one, e.g. n=3, take element 1
								x = xs.get(xs.size() / 2);
								y = ys.get(xs.size() / 2);
							} else { // even num events, take avg around middle one, eg n=4, take avg of elements 1,2
								x = ((float) xs.get((xs.size() / 2) - 1) + xs.get(xs.size() / 2)) / 2f;
								y = ((float) ys.get((xs.size() / 2) - 1) + ys.get(xs.size() / 2)) / 2f;
							}
							xmedian = xFilter.filter(x, lastts);
							ymedian = yFilter.filter(y, lastts);
							//joints.get(i).set(j, (float) getMean(xs));
							//joints.get(i).set(j+2, (float) getMean(ys));
						}
					}
				}
					//			for(int i=0; i<joints.size(); i++){
					//				for(int j=0; j<2; j++){
					//					int[] xs = new int[n], ys = new int[n];// big enough for all events, including IMU and APS events if there are those too
					//					int index = 0;
					//					float xmedian = 0f;
					//					float ymedian = 0f;
					//					for (Object o : in) {
					//						BasicEvent e = (BasicEvent) o;
					//						if (e.isSpecial()) {
					//							continue;
					//						}
					//						if ((e.x<(joints.get(i).get(j)+radius))&&
					//							(e.x>(joints.get(i).get(j)-radius))&&
					//							(e.y<(joints.get(i).get(j+2)+radius))&&
					//							(e.y>(joints.get(i).get(j+2)-radius))) {
					//							xs[index] = e.x;
					//							ys[index] = e.y;
					//							index++;
					//						}
					//						if(index==0)  { // got no actual events
					//							return in;
					//						}
					//					}
					//					Arrays.sort(xs, 0, index); // only sort up to index because that's all we saved
					//					Arrays.sort(ys, 0, index);
					//					float x, y;
					//					if ((index % 2) != 0) { // odd number points, take middle one, e.g. n=3, take element 1
					//						x = xs[index / 2];
					//						y = ys[index / 2];
					//					} else { // even num events, take avg around middle one, eg n=4, take avg of elements 1,2
					//						x = ((float) xs[(index / 2) - 1] + xs[index / 2]) / 2f;
					//						y = ((float) ys[(index / 2) - 1] + ys[index / 2]) / 2f;
					//					}
					//					xmedian = xFilter.filter(x, lastts);
					//					ymedian = yFilter.filter(y, lastts);
					//					joints.get(i).set(j, xmedian);
					//					joints.get(i).set(j+2, ymedian);
					//
					//				}
					//			}
					//			//System.out.println(joints);
				}

		   if(startclicktracking){

			   float xmedian = 0f;
				float ymedian = 0f;

				Vector<Pool> poolOfEvents = new Vector<Pool>();
				for(int i=0; i<jointsToTrack.size(); i++){

					Pool pool = new Pool();
					poolOfEvents.add(pool);

			}
				for (Object o : in) {
					BasicEvent e = (BasicEvent) o;
					if (e.isSpecial()) {
						continue;
					}

					for(int i=0; i<this.jointsToTrack.size(); i++){

							if ((e.x<(jointsToTrack.get(i).x+radius))&&
								(e.x>(jointsToTrack.get(i).x-radius))&&
								(e.y<(jointsToTrack.get(i).y+radius))&&
								(e.y>(jointsToTrack.get(i).y-radius))) {
								poolOfEvents.get(i).xs.add((int) e.x);
								poolOfEvents.get(i).ys.add((int) e.y);

						}
					}
				}
					for(int i=0; i<jointsToTrack.size(); i++){
						Vector<Integer> xs=poolOfEvents.get(i).xs;
						Vector<Integer> ys=poolOfEvents.get(i).ys;

							//xs.sort(null);
							//ys.sort(null);


							if(poolOfEvents.get(i).xs.size()!=0)  {

//								float x, y;
//								if ((xs.size() % 2) != 0) { // odd number points, take middle one, e.g. n=3, take element 1
//									x = xs.get(xs.size() / 2);
//									y = ys.get(xs.size() / 2);
//								} else { // even num events, take avg around middle one, eg n=4, take avg of elements 1,2
//									x = ((float) xs.get((xs.size() / 2) - 1) + xs.get(xs.size() / 2)) / 2f;
//									y = ((float) ys.get((xs.size() / 2) - 1) + ys.get(xs.size() / 2)) / 2f;
//								}
//								xmedian = xFilter.filter(x, lastts);
//								ymedian = yFilter.filter(y, lastts);
//								jointsToTrack.get(i).x= (double) xmedian;
//								jointsToTrack.get(i).y= (double) ymedian;
								jointsToTrack.get(i).x=(double) getMean(xs);
								jointsToTrack.get(i).y= (double) getMean(ys);
								System.out.println("/////////////////");
								System.out.println(i);
								System.out.println(jointsToTrack.get(i).x);
								System.out.println(jointsToTrack.get(i).y);
							}

					}


				}

			return in;

		}
public void doclustering(){
	clustering=true;
}

//DBSCAN
private void densityBasedClustering(EventPacket in) {
	Cluster C;
		for (Object o : in) {
			BasicEvent e = (BasicEvent) o;
			if (e.isSpecial()) {
				continue;
			}
			if (visitedPoints.contains(e)){
				continue;
			}
			visitedPoints.add(e);
			Vector<BasicEvent> NeighborPoints=new Vector<BasicEvent>();
		    NeighborPoints = regionQuery(e,epsilon, in);
		    if (NeighborPoints.size()>=MinPts){
		    	C= new Cluster(e);
		    	expandCluster(e, NeighborPoints, C, in);
		    }

		}

	}

private void expandCluster(BasicEvent e, Vector<BasicEvent> neighPoints, Cluster c,EventPacket in) {
	//c.addEvent(e);
	for(int i=0; i<neighPoints.size(); i++){
		BasicEvent b=neighPoints.get(i);
		if (!visitedPoints.contains(b)){
			visitedPoints.add(b);
			Vector<BasicEvent> neighbPts=new Vector<BasicEvent>();
			neighbPts = regionQuery(b,epsilon,in);
		    if (neighbPts.size()>=MinPts){
		    	neighPoints=addPointOfNeighboring(neighPoints, neighbPts);
		    	  }
		    if(DoesClustersContainBE(b)){
		    	c.addEvent(b);

		    }
		}
	}

}

private boolean DoesClustersContainBE(BasicEvent be) {
	int counter=0;
	for (Cluster c:clusters){
		if (c.ListOfEvent.contains(be)){
			counter++;
		}
	}
	if (counter==0){
		return false;
	}
	else{
		return true;
	}
}

private Vector<BasicEvent> addPointOfNeighboring(Vector<BasicEvent> neighbPts, Vector<BasicEvent> neighbPtsToAdd) {
	for(BasicEvent e:neighbPtsToAdd){
		neighbPts.add(e);
	}
	return neighbPts;
}

private Cluster getNearestCluster(BasicEvent event) {
	float minDistance = Float.MAX_VALUE;
	Cluster closest = null;
	float currentDistance = 0;
	for (Cluster c : clusters) {
		float rX = c.radiusX;
		float rY = c.radiusY; // this is surround region for purposes of dynamicSize scaling of cluster size or aspect ratio
//		if (dynamicSizeEnabled) {
//			rX *= surround;
//			rY *= surround; // the event is captured even when it is in "invisible surround"
//		}
		float dx, dy;
		if (((dx = c.distanceToX(event)) < rX) && ((dy = c.distanceToY(event)) < rY)) { // needs instantaneousAngle metric
			currentDistance = dx + dy;
			if (currentDistance < minDistance) {
				closest = c;
				minDistance = currentDistance;
				c.distanceToLastEvent = minDistance;
				c.xDistanceToLastEvent = dx;
				c.yDistanceToLastEvent = dy;
			}
		}
	}
	return closest;
}

public Vector<BasicEvent> regionQuery(BasicEvent e, Double eps, EventPacket in){
	Vector<BasicEvent> NeighborPoints=new Vector<BasicEvent>();
	for (Object o : in) {
		BasicEvent be = (BasicEvent) o;
		if (be.isSpecial()) {
			continue;
		}
		float distance= (float) Math.sqrt(Math.pow(Math.abs(be.x-e.x),2)+Math.pow(Math.abs(be.y-e.y),2));
		if (distance<eps){
			NeighborPoints.add(be);
		}
	}
	return NeighborPoints;

}


public int getMean(Vector<Integer> xs){
	int mean=0;
	for (int i=0; i<xs.size(); i++){
		mean=mean+xs.get(i);
	}
	mean=mean/xs.size();
	return mean;
}


		public void readMatTextFile(){
			joints = new Vector<Vector<Float>>();
			int i=0;
			int j=0;
			try {
				for (String line : Files.readAllLines(Paths.get("C:/Users/iniLabs/Documents/MATLAB/pose_estimation_code_release_v1.22/example_data/myFile.txt"))) {
					for (String part : line.split("   ")) {
						if(!part.isEmpty()){
							System.out.println(part);
							System.out.println(Double.valueOf(part));
							if(j<10){
								Vector<Float> list=new Vector<Float>();
								joints.add(list);
							}
							Float x = (Double.valueOf(part)).floatValue();
							joints.get(j % 10).add(i, x);
							j++;
						}
					}
					i++;
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println(joints);
		}

		@Override
		public void resetFilter() {
			initFilter();
			//filterChain.reset();
		}

		@Override
		public void initFilter() {
			sx = chip.getSizeX();
			sy = chip.getSizeY();
		}

		@Override
		public void annotate(GLAutoDrawable drawable) {
			GL2 gl = drawable.getGL().getGL2();
			float radius=20;
			float scale=5;
			if(jointsloaded){
			for(int i=0;i<joints.size(); i++){
				float centerX1=joints.get(i).get(0);
				float centerX2=joints.get(i).get(2);
				float width=radius;
				float height=radius;
				float angle=0;
				float centerY1=joints.get(i).get(1);
				float centerY2=joints.get(i).get(3);
				DrawGL.drawBox(gl, centerX1, centerY1, width, height, angle);
				DrawGL.drawBox(gl, centerX2, centerY2, width, height, angle);
				DrawGL.drawLine(gl, centerX1, centerY1, centerX2, centerY2, scale);
			}
			}
			if(startclicktracking){
				float width=radius;
				float height=radius;
				float angle=0;
				float[] centerX = new float[jointsToTrack.indexOf(jointsToTrack.lastElement())+1];
				float[] centerY = new float[jointsToTrack.indexOf(jointsToTrack.lastElement())+1];
				 gl.glPushMatrix();
			     gl.glColor3f(0, 0, 1);
			     gl.glLineWidth(4);
				for(int i=0;i<jointsToTrack.size(); i++){
					centerX[i]=jointsToTrack.get(i).x.floatValue();
					//System.out.println(centerX);
					centerY[i]=jointsToTrack.get(i).y.floatValue();


			     gl.glBegin(GL.GL_LINE_LOOP);
					//System.out.println(centerY);

			        gl.glVertex2d(centerX[i]-(radius/2), centerY[i]-(radius/2));
			        gl.glVertex2d(centerX[i]+(radius/2), centerY[i]-(radius/2));
			        gl.glVertex2d(centerX[i]+(radius/2), centerY[i]+(radius/2));
			        gl.glVertex2d(centerX[i]-(radius/2), centerY[i]+(radius/2));

			        gl.glEnd();
				}
//			        gl.glBegin(GL.GL_LINE_LOOP);
//
//			        gl.glVertex2d(centerX[1]-(radius/2), centerY[1]-(radius/2));
//			        gl.glVertex2d(centerX[1]+(radius/2), centerY[1]-(radius/2));
//			        gl.glVertex2d(centerX[1]+(radius/2), centerY[1]+(radius/2));
//			        gl.glVertex2d(centerX[1]-(radius/2), centerY[1]+(radius/2));;
//
//					gl.glEnd();
//				    gl.glPopMatrix();

//				    DrawGL.drawBox(gl, centerX[0], centerY[0], width, height, angle);
//					DrawGL.drawBox(gl, centerX[1], centerY[1], width, height, angle);
			}
			if(clustering){
				for(int j=0; j<clusters.size(); j++){
					int[] means=getMeans(clusters.get(j).ListOfEvent);
					 gl.glBegin(GL.GL_LINE_LOOP);
						//System.out.println(centerY);

				        gl.glVertex2d(means[0]-(radius/2), means[1]-(radius/2));
				        gl.glVertex2d(means[0]+(radius/2), means[1]-(radius/2));
				        gl.glVertex2d(means[0]+(radius/2), means[1]+(radius/2));
				        gl.glVertex2d(means[0]-(radius/2), means[1]+(radius/2));

				        gl.glEnd();
				}
			}


		}
		private int[] getMeans(Vector<BasicEvent> listOfEvent) {
			int[] means= new int[2];
			Vector<Integer> x=new Vector<Integer>();
			Vector<Integer> y=new Vector<Integer>();
			int meanx;
			int meany;
			for(int i=0; i<listOfEvent.size(); i++){
				BasicEvent e=listOfEvent.get(i);
				x.add((int) e.x);
				y.add((int) e.y);
			}
			meanx=getMean(x);
			meany=getMean(y);
			means[0]=meanx;
			means[1]=meanx;
			return means;
		}

		public void doStartNewTrack(){
			System.out.println("let's start a newtracking");
			startnewtrack=true;
		}
		public void StartNewTrack(Mat img1){
			System.out.println(frameExtractor.hasNewFrame());
			//	if(frameExtractor.hasNewFrame()){
			//		//take a picture
			//		lastFrame1 = frameExtractor.getNewFrame();
			//		FloatPointer p1 = new FloatPointer(lastFrame1);
			//		Mat input1 = new Mat(p1);
			//		input1.convertTo(input1, CV_8U, 255, 0);
			//		Mat img1 = input1.reshape(0, sy);
			Mat imgSave = new Mat(sy, sx, CV_8U);
			opencv_core.flip(img1, imgSave, 0);
			//stop the player - for non real time analysis
			chip.getAeViewer().getAePlayer().setPaused(true);
			// let border be the same in all directions
			int borderheigh=500-sy;
			int borderwidth=356-sx;
			Mat imgPadded = new Mat(500, 356, CV_8U);

			opencv_core.Size dsize=new opencv_core.Size(0,0);
            Mat imgBig = new Mat(sx*2, sy*2, CV_8U);
            Rect roi = new Rect(imgBig.cols()/4,0,346,500);
            Mat imgcropped = new Mat();
            imgcropped = imgBig.apply(roi).clone();
            //Mat imgcropped = new Mat(imgBig, roi).clone();
           // Mat imgfinal = imgcropped.clone();
            //imgCropped
			opencv_core.copyMakeBorder(imgSave, imgPadded, borderheigh/2, borderheigh/2, borderwidth/2,borderwidth/2, BORDER_CONSTANT);
			opencv_imgproc.resize(imgSave, imgBig, dsize, 2., 2., opencv_imgproc.INTER_AREA);
			String path="C:/Users/iniLabs/Documents/MATLAB/pose_estimation_code_release_v1.22/example_data/images";
			//save the taken picture
			String filename = "test"+".jpg";
			String fullFilePath = path + "\\" + filename;
			org.bytedeco.javacpp.opencv_imgcodecs.imwrite(fullFilePath, imgcropped);
			//org.bytedeco.javacpp.opencv_imgcodecs.imwrite(path + "\\" + "imgBig"+".jpg", imgBig);
			log.info("wrote " + fullFilePath);
			poepleDetection(imgSave);
            //doAssignJointsPosition();
			//MATLAB JOB
			// To get a sort of handler to the matlab instance
			MatlabProxyFactory factory = new MatlabProxyFactory();


			try {
				MatlabProxy proxy = factory.getProxy();
				proxy.eval("cd C:/Users/sophie/Documents/MATLAB/pose_estimation_code_release_v1.22/code");
				proxy.eval("startup");
				proxy.eval("[T sticks_imgcoor] = PoseEstimStillImage(pwd, 'images', 'test.jpg', 1, 'full', [119 70 158 142]', fghigh_params, parse_params_Buffy3and4andPascal, [], pm2segms_params, true);");
				joints=((Vector[])proxy.getVariable("sticks_imgcoor"))[0];
				proxy.exit();
				proxy.disconnect();
				System.exit(-1);
			}
			catch (MatlabConnectionException | MatlabInvocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			loadJoints();

		}

	public void doAssignJointsPosition() {
			//e.xs.add(chip.getCanvas().getMousePixel().getX()
		//chip.getCanvas().getRenderer().getXsel();
		TrackPoints e = new TrackPoints(chip.getCanvas().getRenderer().getXsel(),chip.getCanvas().getRenderer().getYsel());
		this.jointsToTrack.add(e);
		for(int i=0; i<jointsToTrack.size();i++){
			System.out.println(jointsToTrack.get(i).x);
			System.out.println(jointsToTrack.get(i).y);
		}
		System.out.println(jointsToTrack.size());
		}
      public void doStartClickTracking(){
    	  startclicktracking=true;
      }
      public void doStopClickTracking(){
    	  startclicktracking=false;
    	  jointsToTrack.removeAllElements();
      }
		public void doStopTrack(){
			jointsloaded=false;
		}

		public void serializeMat(String dir, String name, opencv_core.Mat sMat) {
			String fn = dir + File.separator + name + ".xml";

			FileStorage storage = new opencv_core.FileStorage(fn, opencv_core.FileStorage.WRITE);
			opencv_core.write(storage, name, sMat);
			storage.release();

			log.info("saved in " + fn);
		}



		public Mat deserializeMat(String dir, String name) {
			String fn = dirPath + File.separator + name + ".xml";
			opencv_core.Mat mat = new opencv_core.Mat(CV_32FC3);
			System.out.println("coucou"+" typeIs"+mat.type());

			opencv_core.FileStorage storage = new opencv_core.FileStorage(fn, opencv_core.FileStorage.READ);

			opencv_core.read(storage.get(name), mat);
			storage.release();

			if (mat.empty()) {
				return null;
			}
			/*	log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
		+ "\nmatrix: " + mat.toString() + "\n" + printMatD(mat));*/

			System.out.println(name+" typeIs"+mat.type());
			return mat;
		}



		private void loadJoints() {
			//cameraMatrix2, distortionCoefs2, imgSize, rotationVectorsfinal, translationVectorsfinal,EssentialMat, FundamentalMat,


			//jointsToTrack = deserializeMat(dirPath, "jointsToTrack");
			readMatTextFile();
			//distortionCoefs = deserializeMat(dirPath, "distortionCoefs");



			setInitialJointPosition();
			jointsloaded=true;
		}

		private void setInitialJointPosition() {


		}

		public void poepleDetection(Mat img1){
			//Mat img1=opencv_imgcodecs.imread("C:/Users/iniLabs/Desktop/testSophie/RealTest/imagesTest.jpg");
			//Mat img2=opencv_imgcodecs.imread("C:/Users/iniLabs/Desktop/testSophie/RealTest/test2.jpg");

			HOGDescriptor hog = new HOGDescriptor();
		    Mat svmdetector;
		    FloatPointer ip=opencv_objdetect.HOGDescriptor.getDefaultPeopleDetector();
		    svmdetector=new Mat(ip);
		    hog.setSVMDetector(svmdetector);
		    foundLocations1 = new RectVector() ;

		    hog.detectMultiScale(img1, foundLocations1);
			System.out.println(foundLocations1.size());
			org.bytedeco.javacpp.opencv_core.Scalar rectColor = new org.bytedeco.javacpp.opencv_core.Scalar(0, 255);
			if(foundLocations1.size()!=0){
			for(long i=0;i<foundLocations1.size();i++){
				Rect r = new Rect(foundLocations1.get(i));
				//Point pt1=r.br();
				//Point pt2=r.tl();
				//Mat img=img1;
				opencv_imgproc.rectangle(img1, r,rectColor);
				//img1=img1.adjustROI(r.tl().y(), r.br().y(), r.tl().x(),  r.br().x());
			}
			personDetected=true;
			}

			String filename = chip.getName() + "-" + "HOG" + "-" + "img1rect"+ ".jpg";
			String fullFilePath = dirPath + "\\" + filename;
			org.bytedeco.javacpp.opencv_imgcodecs.imwrite(fullFilePath, img1);
			log.info("wrote " + fullFilePath);



			}


		@Override
		public void update(Observable arg0, Object arg1) {
			// TODO Auto-generated method stub

		}


	}



