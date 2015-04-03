/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.tobi.zipzaps;

import java.awt.Graphics2D;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;


/**
 *
 * @author Manu
 * ver 3.0 % adding the velocityPPT control based on distance !
 * Did some reorganizing of the methods in the file
 * Added velocityPPT , CarLocationOld, CarLocationNew, angle
 * Deleted carInfo
 * 
 */
public class ZipZapTrackFollower extends EventFilter2D implements FrameAnnotater, Observer {

	private int radius = getPrefs().getInt("ZipZapTrackFilter.radius", 30);
	private int x0 = getPrefs().getInt("ZipZapTrackFilter.x0", 63);
	private int y0 = getPrefs().getInt("ZipZapTrackFilter.y0", 63);
	volatile RectangularClusterTracker.Cluster car = null;
	RectangularClusterTracker tracker;
	BackgroundActivityFilter backgroundActivity;
	FilterChain trackingFilterChain;
	ZipZapControl control;
	private static long timeCommandSent = System.currentTimeMillis();
	private static float [] velocity= {0,0};
	private static double angleOfCar=0;
	private float [] carLocationNew = {0,0}; // x,y
	private float [] carLocationOld = {0,0} ; //x,y
	private float [] distanceMoved ={0,0}; //x,y
	private long tOld=System.currentTimeMillis(); // used for calculating the velocityPPT
	private long tNew; // used for calculating the velocityPPT
	private long temp=0;
	static int count=0;
	static int waitTimeForSendingCommand=300;
	static int trackedOnce=0;
	double carTurnAngle=Math.PI/6;
	String track="Circle";

	public ZipZapTrackFollower(AEChip chip) {
		super(chip);

		trackingFilterChain = new FilterChain(chip);
		tracker = new RectangularClusterTracker(chip);
		backgroundActivity = new BackgroundActivityFilter(chip);
		control = new ZipZapControl();

		tracker.setMaxNumClusters(1);
		backgroundActivity.setDt(11532);
		backgroundActivity.setSubsampleBy(1);

		trackingFilterChain.add(backgroundActivity);
		trackingFilterChain.add(tracker);

		tracker.setEnclosed(true, this);
		backgroundActivity.setEnclosed(true, this);

		setEnclosedFilterChain(trackingFilterChain);

	}

	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		if (!isFilterEnabled()) {
			return in;
		}
		backgroundActivity.filterPacket(in);

		tracker.filterPacket(in);
		car = getPutativeCarCluster();
		if (!(car == null)) {
			if (car.isVisible()) {
				driveCar();
			}

		}

		return in;
	}

	private void driveCar() {

		computeCarInfo();
		System.out.println("**********************************************");
		System.out.println("Time tOld "+temp);
		System.out.println("Time tNew "+tNew);
		System.out.println("CarLocationNew X "+carLocationNew[0]+" CarLocationNew Y "+carLocationNew[1]);
		System.out.println("DistanceMoved X "+distanceMoved[0]+" DistanceMoved Y "+distanceMoved[1]);
		System.out.println("velocity X "+velocity[0]+" velocity Y "+velocity[1]);
		System.out.println("Angle "+angleOfCar);
		System.out.println("SpeedFactor "+waitTimeForSendingCommand);
		System.out.println("**********************************************");
		if ((System.currentTimeMillis() - timeCommandSent) > waitTimeForSendingCommand)//&& car.location.x < 110)
		{
			giveCarCommands();
			timeCommandSent = System.currentTimeMillis();
			tNew=System.currentTimeMillis();

		} else {
			if (((System.currentTimeMillis() - timeCommandSent) > (waitTimeForSendingCommand/5) )) {
				control.coast();
				//   control.straight();
			}
		}

	}




	public void giveCarCommands() {

		float angleOfTrack=0;
		float distanceFromTrack=0;
		float K=5; // gain Factor
		float angleToTurn = 0;
		float angularDistance=0;
		//(float) ((angleOfTrack - angleOfCar) + Math.atan((K * distanceFromTrack) / Math.sqrt(velocityPPT[0] * velocityPPT[0] + velocityPPT[1] * velocityPPT[1])));
		//angleToTurn-angleOfTrack;
		if (track.equalsIgnoreCase("Line")) {
			distanceFromTrack = ((car.location.y) - 60);
			angleOfTrack = 0;
			angleToTurn = (float) ((angleOfTrack - angleOfCar) + Math.atan((K * distanceFromTrack) / Math.sqrt((velocity[0] * velocity[0]) + (velocity[1] * velocity[1]))));
			angularDistance=angleToTurn-angleOfTrack;
		}else if(track.equalsIgnoreCase("Circle")){
			if(car.location.y>y0)
			{
				distanceFromTrack = (car.location.y-(float)Math.sqrt((radius*radius)-((car.location.x-x0)*(car.location.x-x0))))+63;
				angleOfTrack=(float)Math.sqrt((radius*radius)-((car.location.x-x0)*(car.location.x-x0)))-(float)Math.sqrt((radius*radius)-((car.location.x-x0-.1)*(car.location.x-x0-.1)));
				angleToTurn = (float) ((angleOfTrack - angleOfCar) + Math.atan((K * distanceFromTrack) / Math.sqrt((velocity[0] * velocity[0]) + (velocity[1] * velocity[1]))));
			}
			else if(car.location.y<y0){
				distanceFromTrack = car.location.y+(float)Math.sqrt((radius*radius)-((car.location.x-x0)*(car.location.x-x0)))+63;
				angleOfTrack=-(float)Math.sqrt((radius*radius)-((car.location.x-x0)*(car.location.x-x0)))+(float)Math.sqrt((radius*radius)-((car.location.x-x0-.1)*(car.location.x-x0-.1)));
				angleToTurn = (float) ((angleOfTrack - angleOfCar) + Math.atan((K * distanceFromTrack) / Math.sqrt((velocity[0] * velocity[0]) + (velocity[1] * velocity[1]))));
			}
		}
		if(Math.abs(angleToTurn) < carTurnAngle ){
			//commandForCar("Straight");
			speedControl("Slow");
			return;
		}
		else if(angleToTurn < 0){
			commandForCar("Left");
			speedControl("Fast");
			return;
		}else if(angleToTurn>0){
			commandForCar("Right");
			speedControl("Fast");
			return;
		}
	}

	public void speedControl(String s1)
	{
		if(s1.equalsIgnoreCase("Slow"))
		{
			waitTimeForSendingCommand=waitTimeForSendingCommand+10;
			return;
		}
		if(s1.equalsIgnoreCase("Fast")){
			if(waitTimeForSendingCommand>50) {
				waitTimeForSendingCommand=waitTimeForSendingCommand-10;
			}
			return;
		}


	}

	public void commandForCar(String direction){
		if (direction.equalsIgnoreCase("STRAIGHT")){ // Do nothing at all

			control.coast(); control.straight(); control.fwd();control.stop();
			return;
		}
		if (direction.equalsIgnoreCase("Left")){
			// Steer Left
			control.coast();control.straight();control.left();control.fwd();control.stop();
			//control.coast();control.left();control.fwd();control.stop();
			System.out.println(car.location.y);System.out.println("Turned Left");
			return;
		}
		if (direction.equalsIgnoreCase("Right")) {
			// Steer Right
			control.coast(); control.straight();control.right(); control.fwd();control.stop();
			System.out.println(car.location.y);System.out.println("Turned Right");
			return;

		}
		if(direction.equalsIgnoreCase("BRAKE")){
			control.coast();control.straight();
			System.out.println(car.location.y);System.out.println("BRAKED");
			return;
		}
		if(direction.equalsIgnoreCase("Reverse")){
			control.coast();control.straight();control.back();control.back();control.back();control.back();control.back();control.back();control.back();control.back();control.coast();
			System.out.println(car.location.y);System.out.println("Reversed");
			return;
		}


	}

	public void computeCarInfo(){
		if( (System.currentTimeMillis()-tOld)>100 )
		{
			tNew=System.currentTimeMillis();
			carLocationNew[0]=car.location.x;
			carLocationNew[1]=car.location.y;
			distanceMoved[0]= (carLocationNew[0]-carLocationOld[0]);
			distanceMoved[1]= (carLocationNew[1]-carLocationOld[1]);
			velocity[0]= ( distanceMoved[0])/ (tNew-tOld) ;
			velocity[1]= (distanceMoved[1]) / (tNew-tOld) ;
			angleOfCar=Math.atan( (distanceMoved[1])/(distanceMoved[0]));
			temp=tOld;
			tOld=tNew;
			carLocationOld[0]=carLocationNew[0];
			carLocationOld[1]=carLocationNew[1];

		}
	}



	private RectangularClusterTracker.Cluster getPutativeCarCluster() {
		if (tracker.getNumClusters() == 0) {
			return null;
		}

		RectangularClusterTracker.Cluster soonest = null;
		RectangularClusterTracker.Cluster returnCar = null;
		for (RectangularClusterTracker.Cluster c : tracker.getClusters()) {
			if (c.isVisible()) { // cluster must be visible
				returnCar = c;
				trackedOnce=trackedOnce+1;
			}
			else  {
				if(trackedOnce>0) {
					commandForCar("Straight");
				}
				else {
					control.back();
				}control.fwd();
			}

		}
		return returnCar;
	}

	@Override
	public void resetFilter() {
	}

	@Override
	public void initFilter() {
	}

	public int getRadius() {
		return radius;
	}

	public void setRadius(int radius) {
		this.radius = radius;
		getPrefs().putInt("ZipZapTrackFollower.radius", radius);
	}

	public int getX0() {
		return x0;
	}

	public void setX0(int x0) {
		this.x0 = x0;
		getPrefs().putInt("ZipZapTrackFollower.x0", x0);
	}

	public int getY0() {
		return y0;
	}

	public void setY0(int y0) {
		this.y0 = y0;
		getPrefs().putInt("ZipZapTrackFollower.y0", y0);
	}

	public void annotate(float[][][] frame) {
	}

	public void annotate(Graphics2D g) {
	}
	GLUquadric trackQuad;
	GLU glu;
	float[] trackColor = {0.8f, 0.8f, 0.8f};

	// Draws the Circular Track
	@Override
	public void annotate(GLAutoDrawable drawable) {

		if (!isFilterEnabled()) {
			return;
		}
		GL2 gl = drawable.getGL().getGL2();
		if (glu == null) {
			glu = new GLU();
		}
		if (trackQuad == null) {
			trackQuad = glu.gluNewQuadric();
		}
		gl.glPushMatrix();
		gl.glColor3d(0.8, 0.8, 0.8);
		gl.glColor3fv(trackColor, 0);
		gl.glPushMatrix();
		gl.glTranslatef(x0, y0, 0);
		glu.gluQuadricDrawStyle(trackQuad, GLU.GLU_FILL);
		glu.gluDisk(trackQuad, radius - 1, radius + 1., 16, 1);
		gl.glPopMatrix();



	}



	// Not Used
	@Override
	public void update(Observable o, Object arg) {
		throw new UnsupportedOperationException("Not supported yet.");
	}
}

