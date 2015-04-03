/*
 * ControlFilter.java
 *
 * Created on 4. Februar 2008, 10:36
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead.robotcontrol;

import java.awt.Graphics2D;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Observable;

import java.util.Observer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;
import ch.unizh.ini.jaer.projects.robothead.Angle;
import ch.unizh.ini.jaer.projects.robothead.ArrayReader;
import ch.unizh.ini.jaer.projects.robothead.Bins;
import ch.unizh.ini.jaer.projects.robothead.CorrelatorFilter;
import ch.unizh.ini.jaer.projects.robothead.HmmFilter;
import ch.unizh.ini.jaer.projects.robothead.retinacochlea.CochleaExtractorFilter;
import ch.unizh.ini.jaer.projects.robothead.retinacochlea.LEDTracker;
import ch.unizh.ini.jaer.projects.robothead.retinacochlea.RetinaExtractorFilter;
import ch.unizh.ini.jaer.projects.robothead.retinacochlea.RotateRetinaFilter;

import com.jogamp.opengl.util.gl2.GLUT;






/**
 * Main Filter to control Koala Robot actions.
 *
 *
 * @author jaeckeld
 */
public class ControlFilter extends EventFilter2D implements Observer, FrameAnnotater {

	private int lowPass=getPrefs().getInt("ControlFilter.lowPass",5);
	private int driveSteps=getPrefs().getInt("ControlFilter.driveSteps",30);
	private int driveSpeed=getPrefs().getInt("ControlFilter.driveSpeed",50);
	private int minAngle=getPrefs().getInt("ControlFilter.minAngle",15);
	private int goPanTiltX=getPrefs().getInt("ControlFilter.goPanTiltX",0);
	private boolean registerPath=getPrefs().getBoolean("ControlFilter.registerPath",false);
	private boolean detectCollision=getPrefs().getBoolean("ControlFilter.detectCollision",false);
	private boolean stopRobot=getPrefs().getBoolean("ControlFilter.stopRobot",false);
	private boolean connectKoala=getPrefs().getBoolean("ControlFilter.connectKoala",false);
	private boolean connectPanTilt=getPrefs().getBoolean("ControlFilter.connectPanTilt",false);
	private boolean useRetina=getPrefs().getBoolean("ControlFilter.useRetina",false);

	KoalaControl controller;



	public int[] lastAngles;
	Angle myAngle = new Angle(3);
	int actualAngle;
	int trackedAngle;

	int countBufferPos;
	int port;
	static String state;          // hearing / turning / driving / looking

	double[][] retinaLUT;       // Look up tables

	// all the Filters needed

	RotateRetinaFilter rotator;

	FilterChain soundFilterChain;
	FilterChain lightFilterChain;

	RetinaExtractorFilter eye;
	LEDTracker tracker;

	CochleaExtractorFilter ear;
	HmmFilter selector;
	CorrelatorFilter correlator;

	// calibrator:

	CalibrationMachine calibrator;

	// Looking Machine:

	LookingMachine viewer;


	/** Creates a new instance of ControlFilter */
	public ControlFilter(AEChip chip) {

		super(chip);

		setPropertyTooltip("lowPass", "Parameter for lowpass filtering");
		setPropertyTooltip("driveSteps", "Robot drives this distance and then listens again [cm]");
		setPropertyTooltip("driveSpeed", "Robot drives this distance and then listens again [cm]");

		setPropertyTooltip("minAngle", "If detected Angle is below this angle, drive there");
		setPropertyTooltip("goPanTiltX", "Set PanTilt position manually");

		setPropertyTooltip("registerPath","set to write drive path into file");
		setPropertyTooltip("detectCollision","Use detect Collision");
		setPropertyTooltip("stopRobot","STOP ROBOT");
		setPropertyTooltip("connectKoala","Connect to the Koala");
		setPropertyTooltip("connectPanTilt","Connect to the Koala");
		setPropertyTooltip("useRetina","Connect to the Koala");

		// build filter hierarchy:

		soundFilterChain = new FilterChain(chip);
		lightFilterChain = new FilterChain(chip);

		rotator=new RotateRetinaFilter(chip);
		rotator.setFilterEnabled(true);

		eye = new RetinaExtractorFilter(chip) ;
		tracker= new LEDTracker(chip);

		ear= new CochleaExtractorFilter(chip);
		ear.setFilterEnabled(true);
		selector= new HmmFilter(chip);
		correlator= new CorrelatorFilter(chip);
		correlator.setFilterEnabled(true);

		soundFilterChain.add(ear);         // create Filterchain for cochlea
		//        soundFilterChain.add(selector);     // uncomment this to insert HmmFilter
		soundFilterChain.add(correlator);

		lightFilterChain.add(eye);      // create Filterchain for retina
		lightFilterChain.add(tracker);


		//setEnclosedFilterChain(soundFilterChain);
		setEnclosedFilterChain(lightFilterChain);       // the GUI will only take one Filterchain, therefore:
		setEnclosedFilter(correlator);

		// initialize Koala

		controller = new KoalaControl();
		port=6;

		retinaLUT=loadRetinaLUT();      // getString saved RetinaLUT
		dispLUT();

		resetFilter();

		//        //if(isConnectKoala())
		//            controller.initiate(port);
		//
		//        //if(isConnectPanTilt())
		//            controller.initPanTilt();   //so...!

	}

	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {

		if(!isFilterEnabled()){
			//controller.close();
			return in;       // only use if filter enabled
		}
		checkOutputPacketEventType(in);

		in=rotator.filterPacket(in);    // this filter rotates only the retina Events for visualization and is always applied

		if(state=="stop") {
			return in;
		}

		if(state=="hearing"){            // STATE HEARING
			soundFilterChain.filterPacket(in);
			if(Bins.getSumOfBins()>=correlator.getNumberOfPairs()){     // wait till Correlation Buffer is filled

				int ANG=correlator.getAngle();                    // getString Angle and fill into LowPassBuffer lastAngles
				for(int i=0; i<(lastAngles.length-1); i++){
					lastAngles[i]=lastAngles[i+1];
				}
				lastAngles[lastAngles.length-1]=ANG;
				countBufferPos++;

				dispAngles();
				System.out.println("Low passed Angle: "+ getLowPassedAngle());


				if(countBufferPos>=lowPass){               // LowPass Buffer filled!
					actualAngle=getLowPassedAngle();       // Angle decided, go now to next state

					if(useRetina==true){
						startLooking();     // always reset before
						state="looking";    // always look after hearing
					}else
						if(Math.abs(actualAngle)>minAngle){         //TODO HERE: maybe check if angle = 90 deg, turn and listen again, , first do this in state diagram
							KoalaControl.regStartCoordTime();
							KoalaControl.turnRobot(actualAngle);
							state="turning";
						}else{
							KoalaControl.regStartCoordTime();
							KoalaControl.goRobot(driveSteps,driveSpeed);
							state="driving";
						}
				}
			}
		}


		if(state=="looking"){
			lightFilterChain.filterPacket(in);
			boolean finnished;
			finnished=viewer.running(tracker.getLED());
			if(finnished==true){                     // finnished looking
				if (viewer.finalPos==0){    //if LED not found
					// do Nothing, stay with actualAngle
					if(Math.abs(actualAngle)>minAngle){         //TODO HERE: maybe check if angle = 90 deg, turn and listen again, , first do this in state diagram
						KoalaControl.regStartCoordTime();
						KoalaControl.turnRobot(actualAngle);
						state="turning";
					}else{
						KoalaControl.regStartCoordTime();

						KoalaControl.goRobot(driveSteps,driveSpeed);
						state="driving";
					}


				}else{              // LED found!!
					actualAngle=viewer.panTiltPos-java.lang.Math.round(calkAngle(viewer.finalPos));     // take angle from LED !
					System.out.println("LED localized at "+actualAngle);
					KoalaControl.goRobot(20,driveSpeed); // to position of retina coordinates
					state="driveNTurn";
				}

			}
		}

		if(state=="driveNTurn"){        // this state is necessery to getString to the retina coordinates before turning
			// hopefully to remove..
			if(!KoalaControl.IsRobotMoving()){        // robot ended moving
				if(Math.abs(actualAngle)>minAngle){         //TODO HERE: maybe check if angle = 90 deg, turn and listen again, , first do this in state diagram
					KoalaControl.regStartCoordTime();
					KoalaControl.turnRobot(actualAngle);
					state="turning";
				}else{
					KoalaControl.regStartCoordTime();

					KoalaControl.goRobot(driveSteps,driveSpeed);
					state="driving";
				}
			}

		}

		if(state=="turning"){       // waiting for the robot to end moving
			if(!KoalaControl.IsRobotMoving()){        // robot ended moving
				KoalaControl.regCoordTime();

				if(KoalaControl.IsThereObstacle){
					state="obstacle";
					KoalaControl.handleObstacle();
				}
				else{
					resetHearing();     // I like to reset Hearing before starting hearing
					state="hearing";
				}
			}
		}

		if(state=="driving"){       // waiting for the robot to end moving
			if(!KoalaControl.IsRobotMoving()){        // robot ended moving
				KoalaControl.regCoordTime();
				if(KoalaControl.IsThereObstacle){
					state="obstacle";
					KoalaControl.handleObstacle();
				}
				else{
					resetHearing();     // I like to reset Hearing before starting hearing
					state="hearing";
				}
			}
		}

		if(state=="obstacle"){      // have an obstacle in front of me and need to drive around

			if(!KoalaControl.IsThereObstacle){    // when it drove around obstacle
				resetHearing();
				state="hearing";
			}

		}

		if(state=="calibrate"){

			lightFilterChain.filterPacket(in);
			boolean finnished;
			finnished=calibrator.running(tracker.getLED());

			if(finnished==true){
				retinaLUT=calibrator.getLUT();
				try {
					saveRetinaLUT(retinaLUT);
				} catch (UnsupportedEncodingException ex) {
					ex.printStackTrace();
				} catch (FileNotFoundException ex) {
					ex.printStackTrace();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
				setState("hearing");
			}
		}

		if(state=="tracking"){

			lightFilterChain.filterPacket(in);
			if(tracker.getLED()!=null){
				trackedAngle=goPanTiltX-java.lang.Math.round(calkAngle(tracker.getLED().getLocation().getX()));     // take angle from LED !
				//System.out.println("Position "+tracker.getLED().getLocation().getX()+" angle "+java.lang.Math.round(calkAngle(tracker.getLED().getLocation().getX())));
			}
		}



		return in;

	}


	public Object getFilterState() {
		return null;
	}

	@Override
	public void resetFilter(){
		//        System.out.println("reset!");
		lastAngles=new int[lowPass];


		state="hearing";
		resetHearing();

		KoalaControl.registerPath=isRegisterPath();
		KoalaControl.detCollision=isDetectCollision();

	}

	@Override
	public void initFilter(){
		System.out.println("init!");
	}

	@Override
	public void update(Observable o, Object arg){
		initFilter();
	}

	public int getLowPass(){
		return lowPass;
	}
	public void setLowPass(int lowPass){
		getPrefs().putInt("ControlFilter.lowPass",lowPass);
		getSupport().firePropertyChange("lowPass",this.lowPass,lowPass);
		this.lowPass=lowPass;

	}

	public int getDriveSteps(){
		return driveSteps;
	}
	public void setDriveSteps(int driveSteps){
		getPrefs().putInt("ControlFilter.driveSteps",driveSteps);
		getSupport().firePropertyChange("driveSteps",this.driveSteps,driveSteps);
		this.driveSteps=driveSteps;

	}
	public int getDriveSpeed(){
		return driveSpeed;
	}
	public void setDriveSpeed(int driveSpeed){
		getPrefs().putInt("ControlFilter.driveSpeed",driveSpeed);
		getSupport().firePropertyChange("driveSpeed",this.driveSpeed,driveSpeed);
		this.driveSpeed=driveSpeed;

	}
	public int getMinAngle(){
		return minAngle;
	}
	public void setMinAngle(int minAngle){
		getPrefs().putInt("ControlFilter.minAngle",minAngle);
		getSupport().firePropertyChange("minAngle",this.minAngle,minAngle);
		this.minAngle=minAngle;

	}
	public int getGoPanTiltX(){
		return goPanTiltX;
	}
	public void setGoPanTiltX(int goPanTiltX){
		getPrefs().putInt("ControlFilter.goPanTiltX",goPanTiltX);
		getSupport().firePropertyChange("goPanTiltX",this.goPanTiltX,goPanTiltX);
		this.goPanTiltX=goPanTiltX;
		controller.setDegreePT(1,goPanTiltX);

	}

	public boolean isRegisterPath(){
		return registerPath;
	}
	public void setRegisterPath(boolean registerPath){
		this.registerPath=registerPath;
		getPrefs().putBoolean("ControlFilter.registerPath",registerPath);
		getSupport().firePropertyChange("ControlFilter.registerPath",this.registerPath,registerPath);
		KoalaControl.registerPath=this.registerPath;
		if(isRegisterPath()) {
			KoalaControl.resetRegFile();
		}
	}

	public boolean isDetectCollision(){
		return detectCollision;
	}
	public void setDetectCollision(boolean detectCollision){
		this.detectCollision=detectCollision;
		getPrefs().putBoolean("ControlFilter.detectCollision",detectCollision);
		getSupport().firePropertyChange("ControlFilter.detectCollision",this.detectCollision,detectCollision);
		KoalaControl.detCollision=this.detectCollision;
	}

	public boolean isStopRobot(){
		return stopRobot;
	}
	public void setStopRobot(boolean stopRobot){
		this.stopRobot=stopRobot;
		getPrefs().putBoolean("ControlFilter.stopRobot",stopRobot);
		getSupport().firePropertyChange("ControlFilter.stopRobot",this.stopRobot,stopRobot);
		KoalaControl.setSpeeds(0,0);  // make robot stop
		if(isStopRobot()) {
			state="stop";
		}
		else {
			state="hearing";
		}
	}

	public boolean isConnectKoala(){
		return connectKoala;
	}
	public void setConnectKoala(boolean connectKoala){
		this.connectKoala=connectKoala;
		getPrefs().putBoolean("ControlFilter.connectKoala",connectKoala);
		getSupport().firePropertyChange("ControlFilter.connectKoala",this.connectKoala,connectKoala);

		if(isConnectKoala()) {
			KoalaControl.initiate(port);
		}
		else {
			controller.close();
		}

	}
	public boolean isConnectPanTilt(){
		return connectPanTilt;
	}
	public void setConnectPanTilt(boolean connectPanTilt){
		this.connectPanTilt=connectPanTilt;
		getPrefs().putBoolean("ControlFilter.connectPanTilt",connectPanTilt);
		getSupport().firePropertyChange("ControlFilter.connectPanTilt",this.connectPanTilt,connectPanTilt);

		if(isConnectPanTilt()) {
			KoalaControl.initPanTilt();
		}
		else {
			KoalaControl.closePT();
		}

	}
	public boolean isUseRetina(){
		return useRetina;
	}
	public void setUseRetina(boolean useRetina){
		this.useRetina=useRetina;
		getPrefs().putBoolean("ControlFilter.useRetina",useRetina);
		getSupport().firePropertyChange("ControlFilter.useRetina",this.useRetina,useRetina);

	}

	public void dispAngles(){
		for (int lastAngle : lastAngles) {
			System.out.print(lastAngle+" ");
		}
		System.out.print("  ==>  "+getLowPassedAngle()+"\n");
	}

	public int getLowPassedAngle(){         // TODO: do better low-passing....
		int[] ang=myAngle.getAngArray();
		int[] storedAngles=new int[ang.length];

		for (int lastAngle : lastAngles) {
			int index=0;
			for(int j=0;j<ang.length;j++){
				if (lastAngle==ang[j]){
					index=j;
					storedAngles[j]++;
				}
			}
		}
		int maxInd=0;
		for(int i=0;i<storedAngles.length;i++){
			if (storedAngles[i]>=storedAngles[maxInd]) {
				maxInd=i;
			}
		}
		return ang[maxInd];
	}
	public void resetHearing(){
		countBufferPos=0;
		lastAngles=new int[lowPass];    // empty LowPass Buffer
		correlator.resetFilter();         // reset

	}
	public void annotate(float[][][] frame) {
	}

	public void annotate(Graphics2D g) {
	}

	EngineeringFormat fmt=new EngineeringFormat();


	@Override
	public void annotate(GLAutoDrawable drawable) {
		if(!isFilterEnabled()) {
			return;
		}
		GL2 gl=drawable.getGL().getGL2();
		//gl.glPushMatrix();
		final GLUT glut=new GLUT();
		gl.glColor3f(1,1,1);
		gl.glRasterPos3f(0,5,5);

		glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format(state));
		if(state=="tracking"){
			glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format(" Tracked Angle = %s",trackedAngle));


		}


		//glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("  Angle=%s",ANG));
		//gl.glPopMatrix();
	}

	public void doCalibration(){

		calibrator=new CalibrationMachine();
		state="calibrate";

	}
	public void doStopCalibration(){
		setState("hearing");
	}

	public void doTracking(){

		state="tracking";

	}
	public void doStopTracking(){
		setState("hearing");
	}
	static public void setState(String wyw){
		state=wyw;
	}
	public void startLooking(){
		viewer=new LookingMachine();
	}
	public int calkAngle(double pixelPos){

		double minVal=100;
		int minValInd=0;
		for(int i=0;i<retinaLUT[0].length;i++){
			double diff=pixelPos-retinaLUT[1][i];
			if(java.lang.Math.abs(diff)<minVal){
				minVal=java.lang.Math.abs(diff);
				minValInd=i;
			}
		}
		return (int)retinaLUT[0][minValInd];

	}
	public void saveRetinaLUT(double[][] rLUT) throws UnsupportedEncodingException, FileNotFoundException, IOException{

		String path = "c:\\ETH\\RobotHead\\LUTs\\";     // where LUT is gonna be saved
		String filename= "retinaLUT.txt";

		FileOutputStream stream = new FileOutputStream(path+filename);
		OutputStreamWriter out = new OutputStreamWriter(stream, "ASCII");


		for (double[] element : rLUT) {
			for(int i=0;i<rLUT[0].length;i++){             // writes array into file...
				out.write(String.valueOf(element[i]));
				out.write(" ");
			}
			out.write("\r\n");
		}



		out.close();
	}

	public double[][] loadRetinaLUT(){

		String path = "c:\\ETH\\RobotHead\\LUTs\\";     // where LUT is saved
		String filename= "retinaLUT.txt";

		ArrayReader myReader = new ArrayReader();
		double[][] rLUT = myReader.readArray(path,filename);

		return rLUT;
	}
	public void dispLUT(){
		for(int i=0;i<retinaLUT[0].length;i++){
			System.out.println(retinaLUT[0][i]+" => "+retinaLUT[1][i]);
		}
	}

}

