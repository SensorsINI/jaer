package ch.unizh.ini.jaer.projects.multitracking;

import static org.bytedeco.javacpp.opencv_core.BORDER_CONSTANT;
import static org.bytedeco.javacpp.opencv_core.CV_32FC3;
import static org.bytedeco.javacpp.opencv_core.CV_8U;

import java.awt.Component;
import java.awt.Container;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.opencv_calib3d;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.FileStorage;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacpp.opencv_core.RectVector;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.opencv_objdetect;
import org.bytedeco.javacpp.opencv_objdetect.HOGDescriptor;
import org.jblas.ComplexFloatMatrix;
import org.jblas.FloatMatrix;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;

import ch.unizh.ini.jaer.chip.multicamera.MultiDAVIS346BCameraChip;
import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
//import matlabcontrol.MatlabConnectionException;
//import matlabcontrol.MatlabInvocationException;
//import matlabcontrol.MatlabProxy;
//import matlabcontrol.MatlabProxyFactory;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraApsDvsEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
//import net.sf.jaer.eventprocessing.tracking.EinsteinClusterTracker.Cluster;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.stereopsis.MultiCameraHardwareInterface;
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
	protected float radius= getFloat("radius", 40);
	//private double radius=35;
	Vector<Vector<Float>> joints = new Vector<Vector<Float>>();
	Vector<Vector<TrackPoints>>jointsToTrack= new Vector<Vector<TrackPoints>>();
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
	private Vector<Cluster> clusters=new Vector<Cluster>();
	private Mat FundamentalMat;
	private Mat P1;
	private Mat P2;
	private Mat T;
	private MultiDAVIS346BCameraChip chi;
	private int cameraDisplayed;
	private boolean calibrationloaded=false;
	private Vector<LinkedList<FloatMatrix>> Xfinals=new Vector<LinkedList<FloatMatrix>>();
    //private Plot3DPanel plot3d = new Plot3DPanel();
    double[][] ptToplot;
    private Triangulation3DViewer triview;
	private boolean triviewActivated;
	private FloatMatrix e2;
	private FloatMatrix Proj1;
	private FloatMatrix Proj2;
	private Mat R;

	public trackerForJoints(AEChip chip) {
		super(chip);
		//chi=(MultiDAVIS346BCameraChip) chip;
		chip.addObserver(this);
		final String size="Size";
		setPropertyTooltip("Size", "radius", "size (starting) in pixel");
//		frameExtractor = new ApsFrameExtractor(chip);
		filterChain = new FilterChain(chip);
		//plot3d.setVisible(false);
		//new FrameView(plot3d);

//		filterChain.add(frameExtractor);
//		frameExtractor.setExtRender(false);
//		setEnclosedFilterChain(filterChain);
		resetFilter();
		//readMatTextFile();


	}

	// TO DO rewrite it properly
	@Override
	public EventPacket filterPacket(EventPacket in) {
		//System.out.println(in);
		//EventPacket[] camerasPacket=separatedCameraPackets(in);

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

		//getEnclosedFilterChain().filterPacket(in);

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
			Vector<Vector<Pool>> poolOfEvents = new Vector<Vector<Pool>>();
			//for(int h=0; h<camerasPacket.length; h++){
		   //for(int h=0; h<2; h++){
			int numCam=0;
			for (Object o : in) {
				MultiCameraApsDvsEvent e = (MultiCameraApsDvsEvent) o;
				if (e.isSpecial()) {
					continue;
				}
			int camera= e.camera;

			   if (numCam<(camera+1)){
				   numCam=camera+1;
			   }

				float xmedian = 0f;
				float ymedian = 0f;


				if((camera+1)>poolOfEvents.size()){
					int diffnumCam=(camera+1)-poolOfEvents.size();
					for(int j=0; j<diffnumCam;j++){
					Vector<Pool> vect=new Vector<Pool>();
					poolOfEvents.add(vect);
					int index=poolOfEvents.indexOf(vect);
				//}


				   for(int i=0; i<jointsToTrack.get(index).size(); i++){

					Pool pool = new Pool();
					poolOfEvents.get(index).add(pool);

				    }
		         }
				}
				//for (Object o : camerasPacket[h]) {
				//for (Object o : in) {




//				switch(camera) {
//					case 0:
					for(int i=0; i<this.jointsToTrack.get(0).size(); i++){
						if ((e.x<(jointsToTrack.get(camera).get(i).x+radius))&&//-((h*sx)/2))
							(e.x>(jointsToTrack.get(camera).get(i).x-radius))&&//-((h*sx)/2)
							(e.y<(jointsToTrack.get(camera).get(i).y+radius))&&
							(e.y>(jointsToTrack.get(camera).get(i).y-radius))){
							//poolOfEvents.get(camera).get(i).xs.add((int) e.x);
							//poolOfEvents.get(camera).get(i).ys.add((int) e.y);


						if(e.polarity==Polarity.On){
							poolOfEvents.get(camera).get(i).xs.add((int) e.x);
							poolOfEvents.get(camera).get(i).ys.add((int) e.y);
						}
					}
					}
//					case 1:
//						System.out.println("case1 limit:");
//						System.out.println(sx);
//						System.out.println(jointsToTrack.get(1).get(0).x);
//						System.out.println(jointsToTrack.get(1).get(0).x-((1*sx)/2));
//
//						System.out.println(jointsToTrack.get(1).get(0).y);
//						System.out.println("points coord:");
//						System.out.println(e.x);
//						System.out.println(e.y);
//						System.out.println("case1 limit:");
//						for(int i=0; i<this.jointsToTrack.get(1).size(); i++){
//
//							if ((e.x<(jointsToTrack.get(1).get(i).x+radius))&&//-((h*sx)/2))
//								(e.x>(jointsToTrack.get(1).get(i).x-radius))&&//-((h*sx)/2)
//								(e.y<(jointsToTrack.get(1).get(i).y+radius))&&
//								(e.y>(jointsToTrack.get(1).get(i).y-radius))) {
//								poolOfEvents.get(1).get(i).xs.add((int) e.x);
//								poolOfEvents.get(1).get(i).ys.add((int) e.y);
//								System.out.println("yay");
//								System.out.println(e.x);
//								System.out.println(e.y);
//
//							}
//						}

//			       	}
//				}
//			 System.out.println("numCam:"+numCam);
			 for(int h=0; h<numCam; h++){
				for(int i=0; i<jointsToTrack.get(h).size(); i++){
					Vector<Integer> xs=poolOfEvents.get(h).get(i).xs;
					Vector<Integer> ys=poolOfEvents.get(h).get(i).ys;
					//System.out.println(h);
					//System.out.println(i);
					//System.out.println(xs);
					//System.out.println(ys);
					//xs.sort(null);
					//ys.sort(null);


					if(poolOfEvents.get(h).get(i).xs.size()!=0)  {

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

						double errx=jointsToTrack.get(h).get(i).x-getMean(xs);
						double erry=jointsToTrack.get(h).get(i).y-getMean(ys);
						double err=Math.sqrt(Math.pow(errx, 2)+Math.pow(erry, 2));

						if(err<30){
						jointsToTrack.get(h).get(i).x=(double) getMean(xs);
						jointsToTrack.get(h).get(i).y= (double) getMean(ys);
						}
//						System.out.println("/////////////////");
//						System.out.println(h);
//						System.out.println(jointsToTrack.get(h).get(i).x);
//						System.out.println(jointsToTrack.get(h).get(i).y);
//						System.out.println("/////////////////");

					}

				}
				}





		}
			for(int i=0;i<jointsToTrack.firstElement().size(); i++){
				//TriangulatePointPosition(jointsToTrack.get(0).get(i), jointsToTrack.get(1).get(i));
				triangulationUsingJBlas(jointsToTrack.get(0).get(i), jointsToTrack.get(1).get(i), i);
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
		//GL2 gl3 =drawable.getGL().getGL2();
		float rad=this.radius*2;
		float scale=5;
		if(jointsloaded){
			for(int i=0;i<joints.size(); i++){
				float centerX1=joints.get(i).get(0);
				float centerX2=joints.get(i).get(2);
				float width=rad;
				float height=rad;
				float angle=0;
				float centerY1=joints.get(i).get(1);
				float centerY2=joints.get(i).get(3);
				DrawGL.drawBox(gl, centerX1, centerY1, width, height, angle);
				DrawGL.drawBox(gl, centerX2, centerY2, width, height, angle);
				DrawGL.drawLine(gl, centerX1, centerY1, centerX2, centerY2, scale);
			}
		}
		if(startclicktracking){
			for(int h =0; h<jointsToTrack.size();h++){
				float width=rad;
				float height=rad;
				float angle=0;
				float[] centerX = new float[jointsToTrack.get(h).indexOf(jointsToTrack.get(h).lastElement())+1];
				float[] centerY = new float[jointsToTrack.get(h).indexOf(jointsToTrack.get(h).lastElement())+1];

				gl.glPushMatrix();
				gl.glColor3f(0, 0, 1);
				gl.glLineWidth(4);
				for(int i=0;i<jointsToTrack.get(h).size(); i++){
					centerX[i]=jointsToTrack.get(h).get(i).x.floatValue()+((h*sx)/2);
					//System.out.println(centerX);
					centerY[i]=jointsToTrack.get(h).get(i).y.floatValue();


					gl.glBegin(GL.GL_LINE_LOOP);
					//System.out.println(centerY);

					gl.glVertex2d(centerX[i]-(radius/2), centerY[i]-(radius/2));
					gl.glVertex2d(centerX[i]+(radius/2), centerY[i]-(radius/2));
					gl.glVertex2d(centerX[i]+(radius/2), centerY[i]+(radius/2));
					gl.glVertex2d(centerX[i]-(radius/2), centerY[i]+(radius/2));

					gl.glEnd();
				}
//				gl.glColor3f(1, 0, 0);
//				gl.glBegin(GL.GL_LINE_LOOP);
//				//System.out.println(centerY);
//
//				gl.glVertex2d((e2.get(0)-(radius/2))+((3*sx)/4), (e2.get(1)-(radius/2))+(sy/2));
//				gl.glVertex2d(e2.get(0)+(radius/2)+((3*sx)/4), (e2.get(1)-(radius/2))+(sy/2));
//				gl.glVertex2d(e2.get(0)+(radius/2)+((3*sx)/4), e2.get(1)+(radius/2)+(sy/2));
//				gl.glVertex2d((e2.get(0)-(radius/2))+((3*sx)/4), e2.get(1)+(radius/2)+(sy/2));
//
//				gl.glEnd();




//				gl.glPushMatrix();
//				gl.glColor3f(0, 0, 1);
//				gl.glLineWidth(4);
//				for(int i=0;i<Xfinals.size(); i++){
//					//centerX[i]=Xfinals.get(i).get(0);
//					//System.out.println(centerX);
//					//centerY[i]=Xfinals.get(i).get(1);
//					//centerZ[i]=Xfinals.get(i).get(2);
//
//					gl.glBegin(GL.GL_LINE_LOOP);
//					//System.out.println(centerY);
//
//					gl.glVertex3d(Xfinals.get(i).get(0)-(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)-(radius/2));
//					gl.glVertex3d(Xfinals.get(i).get(0)+(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)-(radius/2));
//					gl.glVertex3d(Xfinals.get(i).get(0)-(radius/2), Xfinals.get(i).get(1)+(radius/2), Xfinals.get(i).get(2)-(radius/2));
//					gl.glVertex3d(Xfinals.get(i).get(0)+(radius/2), Xfinals.get(i).get(1)+(radius/2), Xfinals.get(i).get(2)+(radius/2));
//					gl.glVertex3d(Xfinals.get(i).get(0)-(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)+(radius/2));
//					gl.glVertex3d(Xfinals.get(i).get(0)+(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)+(radius/2));
//
//					gl.glEnd();
//				}


			}
			//				for(int i=0;i<jointsToTrack.firstElement().size(); i++){
			//					TriangulatePointPosition(jointsToTrack.get(0).get(i), jointsToTrack.get(1).get(i));
			//				}
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
		//MatlabProxyFactory factory = new MatlabProxyFactory();


		//			try {
		//				MatlabProxy proxy = factory.getProxy();
		//				proxy.eval("cd C:/Users/sophie/Documents/MATLAB/pose_estimation_code_release_v1.22/code");
		//				proxy.eval("startup");
		//				proxy.eval("[T sticks_imgcoor] = PoseEstimStillImage(pwd, 'images', 'test.jpg', 1, 'full', [119 70 158 142]', fghigh_params, parse_params_Buffy3and4andPascal, [], pm2segms_params, true);");
		//				joints=((Vector[])proxy.getVariable("sticks_imgcoor"))[0];
		//				proxy.exit();
		//				proxy.disconnect();
		//				System.exit(-1);
		//			}
		//			catch (MatlabConnectionException | MatlabInvocationException e) {
		//				// TODO Auto-generated catch block
		//				e.printStackTrace();
		//			}

		loadJoints();

	}

	public void doAssignJointsPosition() {
		//e.xs.add(chip.getCanvas().getMousePixel().getX()
		//chip.getCanvas().getRenderer().getXsel();
		int h=Math.floorDiv(chip.getCanvas().getRenderer().getXsel(),sx/2);
		TrackPoints e = new TrackPoints(chip.getCanvas().getRenderer().getXsel()-((h*sx)/2),chip.getCanvas().getRenderer().getYsel());
		//int h=Math.floorDiv(chip.getCanvas().getRenderer().getXsel(),sx/2);
		System.out.println(h);
		//int h=chi.displaycamera;
		if((h+1)>jointsToTrack.size()){
			Vector<TrackPoints> vect=new Vector<TrackPoints>();
			jointsToTrack.add(vect);
		}
		this.jointsToTrack.get(h).add(e);
		for(int j=0; j<jointsToTrack.size();j++){
		for(int i=0; i<jointsToTrack.get(j).size();i++){
			System.out.println(jointsToTrack.get(j).get(i).x);
			System.out.println(jointsToTrack.get(j).get(i).y);
		}
		}
		//System.out.println(jointsToTrack.size());
	}

	public void doStartClickTracking(){
		startclicktracking=true;
		//plot3d.setVisible(true);
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


	public void triangulationUsingJBlas(TrackPoints tp2,TrackPoints tp1,int i){
		if (!calibrationloaded){
			calibrationloaded=true;
			final JFileChooser j = new JFileChooser();
			j.setCurrentDirectory(new File(dirPath));
			j.setApproveButtonText("Select folder");
			j.setDialogTitle("Select a folder that has XML files storing calibration");
			j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // let user specify a base filename
			j.setApproveButtonText("Select folder");
			j.setApproveButtonToolTipText("Only enabled for a folder that has cameraMatrix.xml and distortionCoefs.xml");
			setButtonState(j, j.getApproveButtonText(), calibrationExists(j.getCurrentDirectory().getPath()));
			j.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent pce) {
					setButtonState(j, j.getApproveButtonText(), calibrationExists(j.getCurrentDirectory().getPath()));
				}
			});
			int ret = j.showOpenDialog(null);
			if (ret != JFileChooser.APPROVE_OPTION) {
				return;
			}
			dirPath = j.getSelectedFile().getPath();
			putString("dirPath", dirPath);
			try{
				FundamentalMat = deserializeMat(dirPath, "FundamentalMat");
				P1=deserializeMat(dirPath, "P1");
				P2=deserializeMat(dirPath, "P2");
				T=deserializeMat(dirPath, "translationVectorsfinal");
				R=deserializeMat(dirPath, "rotationVectorsfinal");
			}catch (Exception h) {
				log.warning("Could not load existing calibration from folder " + dirPath + " on construction:" + h.toString());
			}
//			ptToplot=new double[1][1];
//			ptToplot[0][0]=0;
//			plot3d.addScatterPlot("3D reprojection", ptToplot);
			//getting the capabilities object of GL2 profile
//Test test =new Test(chip.getCanvas());
//test.start();
//			startNewWindows();
			double[][] fundam=makeMatrixfromMat(FundamentalMat);
			double[][] proj1=makeMatrixfromMat(P1);
			double[][] proj2=makeMatrixfromMat(P2);
			double[][] tranlate=makeMatrixfromMat(T);
			double[][] rotate=makeMatrixfromMat(R);

			FloatMatrix Translate=new FloatMatrix(new float[] {(float)tranlate[0][0]/10,(float)tranlate[1][0]/10,(float)tranlate[2][0]/10});
			Translate.print();


			FloatMatrix Rotate=new FloatMatrix(new float[][] {{(float) rotate[0][0],(float) rotate[0][1],(float)rotate[0][2]},
	            {(float)rotate[1][0],(float)rotate[1][1], (float)rotate[1][2]},
	            {(float)rotate[2][0],(float)rotate[2][1],(float)rotate[2][2]}});





			FloatMatrix Fundam=new FloatMatrix(new float[][] {{(float) fundam[0][0],(float) fundam[0][1],(float)fundam[0][2]},
												            {(float)fundam[1][0],(float)fundam[1][1], (float)fundam[1][2]},
												            {(float)fundam[2][0],(float)fundam[2][1],(float)fundam[2][2]}});


			 Proj1=new FloatMatrix(new float[][] {{(float) proj1[0][0],(float) proj1[0][1],(float)proj1[0][2],(float) proj1[0][3]},
																{(float)proj1[1][0],(float)proj1[1][1], (float)proj1[1][2],(float)proj1[1][3]},
																{(float)proj1[2][0],(float)proj1[2][1],(float)proj1[2][2],(float)proj1[2][3]}});
			//System.out.println("Proj1:");
			//Proj1.print();

			 Proj2=new FloatMatrix(new float[][] {{(float) proj2[0][0],(float) proj2[0][1],(float)proj2[0][2],(float) proj2[0][3]},
															{(float)proj2[1][0],(float)proj2[1][1], (float)proj2[1][2],(float)proj2[1][3]},
															{(float)proj2[2][0],(float)proj2[2][1],(float)proj2[2][2],(float)proj2[2][3]}});

		      triview = new Triangulation3DViewer(chip.getCanvas());
		      triview.XSize=sx;
		      triview.YSize=sy;
		      triview.setPositionSecondCamera(Translate);
		      Rotate.mmul(Translate).print();
		      triview.startNewWindows();

//		     triviewActivated=true;
		}

//		System.out.println(printMatD(P1));
//		System.out.println(printMatD(P2));


		FloatMatrix X1=new FloatMatrix(4);
		FloatMatrix X2=new FloatMatrix(4);

		FloatMatrix Xfinal=new FloatMatrix(3);
		FloatMatrix x1=new FloatMatrix(new float[] {(sx/2)-tp1.x.floatValue(),sy-tp1.y.floatValue(), (float) 3.5});

		e2=new FloatMatrix(3);
//		System.out.println("x1:");
//		x1.print();


		FloatMatrix x2=new FloatMatrix(new float[] {(sx/2)-tp2.x.floatValue(),sy-tp2.y.floatValue(), (float) 3.5});
//		System.out.println("x2:");
//		x2.print();

//		System.out.println("Proj2:");
//		Proj2.print();
		FloatMatrix A=new FloatMatrix(4, 4);
		A.putRow(0,Proj1.getRow(2).mul(x1.get(0)).add(Proj1.getRow(0).mul(-1)));
		A.putRow(1,Proj1.getRow(2).mul(x1.get(1)).add(Proj1.getRow(1).mul(-1)));
		A.putRow(2,Proj2.getRow(2).mul(x2.get(0)).add(Proj2.getRow(0).mul(-1)));
		A.putRow(3,Proj2.getRow(2).mul(x2.get(1)).add(Proj2.getRow(1).mul(-1)));
		//A.print();
		ComplexFloatMatrix[] C=org.jblas.Eigen.eigenvectors(A.transpose().mmul(A));
		//FloatMatrix[] C=org.jblas.Singular.fullSVD(A);
//		C[0].print();
//		C[1].print();
//		C[2].print();

//        C[0].getColumn(0).print();
//        C[0].getColumn(1).print();
//        C[0].getColumn(2).print();
//        C[0].getColumn(3).print();

        //C[1].print();
//		X1=org.jblas.Solve.solveLeastSquares(Proj1,x1);
//		e2=org.jblas.Solve.solveLeastSquares(org.jblas.Solve.pinv(Proj2), X1);
//		e2.print();

//		X2=org.jblas.Solve.solveLeastSquares(Proj2,x2);

//		Xfinal.put(0,((X1.get(0))+(X2.get(0)/X2.get(3)))/2);
//		Xfinal.put(1,((X1.get(1))+(X2.get(1)/X2.get(3)))/2);
//		Xfinal.put(2,(X1.get(2)+(X2.get(2)/X2.get(3)))/2);

//		Xfinal=org.jblas.Solve.solve(A,FloatMatrix.zeros(4,1));
        Xfinal=C[0].getColumn(3).getReal();

        FloatMatrix X=new FloatMatrix(new float[] {(Xfinal.get(0))/(Xfinal.get(3)*10),(Xfinal.get(1))/(Xfinal.get(3)*10), (Xfinal.get(2))/(Xfinal.get(3)*10)});
//		Xfinal.print();
        X.print();

        try {
			triview.semaphore.acquire();

        if (Xfinals.size()<(i+1)){
        	int z = (i+1)-Xfinals.size();
        	for(int t=0; t<z; t++){
        		LinkedList<FloatMatrix> l = new LinkedList<FloatMatrix>();
        		Xfinals.add(l);
        	}
        }
		if (Xfinals.get(i).size()<10){
		Xfinals.get(i).add(X);

		}
		else{
			Xfinals.get(i).pop();
			Xfinals.get(i).add(X);
		}
		update3Dplot(Xfinals);

		triview.semaphore.release();

        }
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//ptToplot=fromVectToTab(Xfinals);


        //triview.preventNewEvent();
		//plot3d.addScatterPlot("3D reprojection", ptToplot);
		//plot3d.repaint();
		//float[] xfinal = Xfinal.data;
		//xfinal
		// Build the 3D scatterplot of the datas in a Panel
       // Plot3DPanel plot3d = new Plot3DPanel(Xfinals,"datas","SCATTER");
        // Display a Frame containing the plot panel
       // new FrameView(plot3d);
	}



	private double[][] makeVectorfromMat(Mat t2) {
		// TODO Auto-generated method stub
		return null;
	}

	private void update3Dplot(Vector<LinkedList<FloatMatrix>> xfinals2) {
		triview.setVectToDisplay(xfinals2);


	}

	private static double[][] fromVectToTab(LinkedList<FloatMatrix> xfinals2) {
		double[][] tab=new double[xfinals2.size()][3];
		for (int i=0; i<xfinals2.size();i++){
			tab[i][0]=xfinals2.get(i).get(0);
			tab[i][1]=xfinals2.get(i).get(1);
			tab[i][2]=xfinals2.get(i).get(2);
		}
		return tab;
	}

	public void TriangulatePointPosition(TrackPoints tp1,TrackPoints tp2){
//		System.out.println("coordinate of the points to triangulate:");
//		System.out.println(tp1.x-(sx/2));
//		System.out.println(tp1.y-(sy/2));
//		Systdem.out.println(tp2.x-((3*sx)/4));
//		System.out.println(tp2.y-(sy/2));
//		System.out.println("end of points coordinate");
		//public void TriangulatePointPosition(){
		if (!calibrationloaded){
			calibrationloaded=true;
			final JFileChooser j = new JFileChooser();
			j.setCurrentDirectory(new File(dirPath));
			j.setApproveButtonText("Select folder");
			j.setDialogTitle("Select a folder that has XML files storing calibration");
			j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // let user specify a base filename
			j.setApproveButtonText("Select folder");
			j.setApproveButtonToolTipText("Only enabled for a folder that has cameraMatrix.xml and distortionCoefs.xml");
			setButtonState(j, j.getApproveButtonText(), calibrationExists(j.getCurrentDirectory().getPath()));
			j.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent pce) {
					setButtonState(j, j.getApproveButtonText(), calibrationExists(j.getCurrentDirectory().getPath()));
				}
			});
			int ret = j.showOpenDialog(null);
			if (ret != JFileChooser.APPROVE_OPTION) {
				return;
			}
			dirPath = j.getSelectedFile().getPath();
			putString("dirPath", dirPath);
			try{
				FundamentalMat = deserializeMat(dirPath, "FundamentalMat");
				P1=deserializeMat(dirPath, "P1");
				P2=deserializeMat(dirPath, "P2");
			}catch (Exception i) {
				log.warning("Could not load existing calibration from folder " + dirPath + " on construction:" + i.toString());
			}
		}
		//			double[][] F;
		//			F=makeMatrixfromMat(FundamentalMat);
		//			float[] l1=multiply(F,p1);
		//			float[] l2=multiply(transpose(F),p1);
		float[] p1=new float[2];
		//			p1[0]=120;
		//			p1[1]=50;
		p1[0]=tp1.x.floatValue()-(sx/2);
		p1[1]=tp1.y.floatValue()-(sy/2);


		float[] p2=new float[2];
		//			p2[0]=140;
		//			p2[1]=20;
		p2[0]=tp2.x.floatValue()-(sx/2);
		p2[1]=tp2.y.floatValue()-((sy)/2);

		FloatPointer ip1=new FloatPointer(p1);
		FloatPointer ip2=new FloatPointer(p2);

		Mat matpoints1=new Mat(ip1);
		Mat matpoints2=new Mat(ip2);

		matpoints1=matpoints1.reshape(2);
		matpoints2=matpoints2.reshape(2);

		Mat points4D=new Mat();
		opencv_calib3d.triangulatePoints(P2, P1, matpoints1, matpoints2, points4D);
//		System.out.println(points4D.rows());
//		System.out.println(points4D.cols());
		//System.out.println(printMatD(points4D));
		int npoints=points4D.checkVector(3);
		int npoints2=points4D.checkVector(4);
//		System.out.println(npoints);
//		System.out.println(npoints2);
		points4D=points4D.reshape(4,1);
		//points4D.resize(4);
//		System.out.println(points4D.rows());
//		System.out.println(points4D.cols());
		npoints=points4D.checkVector(3);
		npoints2=points4D.checkVector(4);
//		System.out.println(npoints);
//		System.out.println(npoints2);
//		System.out.println(printMatD(points4D));
		Mat points3D = new Mat();

		opencv_calib3d.convertPointsFromHomogeneous(points4D, points3D);
		System.out.println(printMatD(points3D));
		double[][] finalPoint4D=makeMatrixfromMat(points3D);
		System.out.println("finalPoint4D:");
		System.out.println(finalPoint4D[0]);
		System.out.println(finalPoint4D[1]);
		System.out.println(finalPoint4D[2]);
		System.out.println("end");
		//			try {
		//				MatlabEngine eng = MatlabEngine.startMatlab();
		//				double[] p=p1;
		//				double[][] F=makeMatrixfromMat(FundamentalMat);
		//				double[][] F1=eng.feval("transpose", F);
		//				eng.evalAsync("l1 = F .*p1;");
		//				eng.evalAsync("l2 = F1 .*p2;");
		//		        eng.close();
		//			}
		//	        catch (IllegalArgumentException | IllegalStateException | InterruptedException | RejectedExecutionException | ExecutionException e1) {
		//				// TODO Auto-generated catch block
		//				e1.printStackTrace();
		//			}

	}


        @SuppressWarnings("deprecation")
	private String printMatD(Mat M) {
		StringBuilder sb = new StringBuilder();
		int c = 0;
		for (int k = 0; k < (M.rows()); k++) {
			for (int l = 0; l < (M.cols()); l++) {
				sb.append(String.format("%10.5f\t", M.getDoubleBuffer().get(c)));//M.getDoubleBuffer().get(c))
				//System.out.println(sb.toString());
				c++;
			}
			sb.append("\n");
		}
		return sb.toString();
	}



	private double[][] transpose(double[][] f) {
		// TODO Auto-generated method stub
		return null;
	}

	private float[] multiply(double[][] f, float[] p1) {
		// TODO Auto-generated method stub
		return null;
	}

        @SuppressWarnings("deprecation")
	private double[][] makeMatrixfromMat(Mat M) {
		double[][] matrix = new double[M.rows()][M.cols()];
		int c=0;
		for (int k = 0; k < (M.rows()); k++) {
		for (int l = 0; l < (M.cols()); l++) {
			matrix[k][l]=M.getDoubleBuffer().get(c);//M.getDoubleBuffer().get(c))
//			System.out.println(matrix[k][l]);
			c++;
		}
//		System.out.println("//");
		}
//		System.out.println("///////////////");
		//System.out.println(matrix);
		return matrix;
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

	private boolean calibrationExists(String dirPath) {
		String fn = dirPath + File.separator + "cameraMatrix" + ".xml";
		File f = new File(fn);
		boolean cameraMatrixExists = f.exists();
		fn = dirPath + File.separator + "distortionCoefs" + ".xml";
		f = new File(fn);
		boolean distortionCoefsExists = f.exists();
		if (distortionCoefsExists && cameraMatrixExists) {
			return true;
		} else {
			return false;
		}
	}
	static void setButtonState(Container c, String buttonString, boolean flag) {
		int len = c.getComponentCount();
		for (int i = 0; i < len; i++) {
			Component comp = c.getComponent(i);

			if (comp instanceof JButton) {
				JButton b = (JButton) comp;

				if (buttonString.equals(b.getText())) {
					b.setEnabled(flag);
				}

			} else if (comp instanceof Container) {
				setButtonState((Container) comp, buttonString, flag);
			}
		}
	}
	@Override
	public void update(Observable arg0, Object arg1) {
		// TODO Auto-generated method stub

	}

	public EventPacket[] separatedCameraPackets(EventPacket in){

		int n = in.getSize();
		int numCameras=MultiCameraHardwareInterface.NUM_CAMERAS;
		EventPacket[] camerasPacket=new EventPacket[numCameras];
		int[] freePositionPacket= new int[numCameras];

		Iterator evItr = in.iterator();
		for(int i=0; i<n; i++) {
			Object e = evItr.next();
			if ( e == null ){
				log.warning("null event, skipping");
			}
			MultiCameraApsDvsEvent ev = (MultiCameraApsDvsEvent) e;
			if (ev.isSpecial()) {
				continue;
			}
			int camera= ev.camera;

			//Inizialization of the cameraPackets depending on how many cameras are connected
			//CameraPackets is an array of the EventPackets sorted by camera
			for(int c=0; c<numCameras; c++) {
				camerasPacket[c]=new EventPacket();
				camerasPacket[c].allocate(n);
				camerasPacket[c].clear();
			}

			//Allocation of each event in the new sorted Packet
			freePositionPacket[camera]=camerasPacket[camera].getSize();
			camerasPacket[camera].elementData[freePositionPacket[camera]]=ev;
			camerasPacket[camera].size=camerasPacket[camera].size+1;
		}

		return camerasPacket;
	}
	public void startNewWindows() {

	      //getting the capabilities object of GL2 profile
	      final GLProfile profile = GLProfile.get( GLProfile.GL2 );
	      GLCapabilities capabilities = new GLCapabilities(profile);

	      // The canvas
	      final GLCanvas glcanvas = new GLCanvas( capabilities );
	      triview = new Triangulation3DViewer(chip.getCanvas());
	      glcanvas.addGLEventListener( triview);
	      glcanvas.setSize( 800, 800 );

	      //creating frame
	      final JFrame frame = new JFrame (" triangulation 3D renderer");

	      //adding canvas to it
	      frame.getContentPane().add( glcanvas );
	      frame.setSize(frame.getContentPane().getPreferredSize() );
	      frame.setVisible( true );
	   }//end of main

	public final float getradius() {
		return radius;
	}

	/**
	 * max number of clusters
	 *
	 * @param radius
	 */
	public void setradius(final float radius) {
		this.radius = radius;
		putDouble("radius", radius);
	}

	public float getMinradius() {
		return 0;
	}

	public float getMaxradius() {
		return 50;
	}

}





