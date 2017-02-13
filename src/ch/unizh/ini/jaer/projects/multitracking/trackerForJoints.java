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

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.FileStorage;
import org.bytedeco.javacpp.opencv_core.Mat;

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
	double radius=30.;
	Vector<Vector<Float>> joints = new Vector<Vector<Float>>();
	private Mat jointsToTrack;
	private String dirPath = getString("dirPath", System.getProperty("user.dir"));
	private boolean  jointsloaded=false;
	ApsFrameExtractor frameExtractor=new ApsFrameExtractor(chip);
	private final FilterChain filterChain;
	private float[] lastFrame1 = null;
	LowpassFilter xFilter = new LowpassFilter();
	LowpassFilter yFilter = new LowpassFilter();
	int lastts = 0;
	private boolean startnewtrack=false;


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
						Pool pool = new Pool(n);
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
				for(int i=0; i<joints.size(); i++){
					for(int j=0; j<2; j++){
						Vector<Integer> xs=poolOfEvents.get(i+(2*j)).xs;
						Vector<Integer> ys=poolOfEvents.get(i+(2*j)).ys;
						//xs.sort(null);
						//ys.sort(null);


						if(xs.size()!=0)  { // got actual events
//							float x, y;
//							if ((xs.size() % 2) != 0) { // odd number points, take middle one, e.g. n=3, take element 1
//								x = xs.get(xs.size() / 2);
//								y = ys.get(xs.size() / 2);
//							} else { // even num events, take avg around middle one, eg n=4, take avg of elements 1,2
//								x = ((float) xs.get((xs.size() / 2) - 1) + xs.get(xs.size() / 2)) / 2f;
//								y = ((float) ys.get((xs.size() / 2) - 1) + ys.get(xs.size() / 2)) / 2f;
//							}
//							xmedian = xFilter.filter(x, lastts);
//							ymedian = yFilter.filter(y, lastts);
							joints.get(i).set(j, (float) getMean(xs));
							joints.get(i).set(j+2, (float) getMean(ys));
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
			}
			//		}
			return in;

		}

public int getMean(Vector<Integer> xs){
	int mean=0;
	for (int i=1; i<xs.size(); i++){
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
			float radius=30;
			float scale=5;
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

			opencv_core.copyMakeBorder(imgSave, imgPadded, borderheigh/2, borderheigh/2, borderwidth/2,borderwidth/2, BORDER_CONSTANT);
			String path="C:/Users/iniLabs/Documents/MATLAB/pose_estimation_code_release_v1.22/example_data/images";
			//save the taken picture
			String filename = "test"+".jpg";
			String fullFilePath = path + "\\" + filename;
			org.bytedeco.javacpp.opencv_imgcodecs.imwrite(fullFilePath, imgPadded);
			log.info("wrote " + fullFilePath);


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

		@Override
		public void update(Observable arg0, Object arg1) {
			// TODO Auto-generated method stub

		}


	}



