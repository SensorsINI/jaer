/*
 * HoughCircleTracker.java
 *
 * Created May 07 2011 by Jan Funke
 * inspired by HoughEyeTracker.java
 * extended by Lorenz Muller
 */

package net.sf.jaer.eventprocessing.tracking;

import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import javax.swing.JFrame;
import javax.swing.JPanel;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.EventFilterDataLogger;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.projects.labyrinthkalman.WeightedEvent;

/**
 * A simple circle tracker based on a hough transform that correctly tracks the maximum even when it's location changes out from under us.
 * @author Jan Funke
 */
@Description("Circle tracker based on a hough transform")
public class HoughCircleTracker extends EventFilter2D implements FrameAnnotater, Observer {

	// the Hough space
	int cameraX;
	int cameraY;
	float[][] accumulatorArray;

	//for decay in Hough space
	float timeStamp = 0;

	// the running maxima in Hough space
	Coordinate[] maxCoordinate;

	// the running values of the current maximum in Hough space
	float[] maxValue;

	// history of the encountered spikes to remove the least recent one from
	// hough space
	Coordinate[] eventHistory;
	int bufferIndex = 0;

	// visualisation stuff
	int angleListLength = 18;
	float[] sinTau = new float[angleListLength];
	float[] cosTau = new float[angleListLength];
	Coordinate[] circleOutline = new Coordinate[angleListLength];

	EventFilterDataLogger dataLogger;
	JFrame targetFrame;
	DrawPanel gazePanel;

	// parameters
	private float   radius	 = getPrefs().getFloat("HoughCircleTracker.radius", 0.8f);
	private int     bufferLength   = getPrefs().getInt("HoughCircleTracker.bufferLength", 4000);
	private int     threshold      = getPrefs().getInt("HoughCircleTracker.threshold", 15);
	private boolean logDataEnabled = false;
	private float   decay	  = getPrefs().getFloat("HoughCircleTracker.decay", 1.0f);
	private int     nrMax	  = getPrefs().getInt("HoughCircleTracker.nrMax", 1);
	private boolean decayMode      = getPrefs().getBoolean("HoughCricleTracker.decayMode", true);
	private boolean drawHough      = getPrefs().getBoolean("HoughCircleTracker.drawHough", false);
	private boolean locDepression  = getPrefs().getBoolean("HoughCirclreTracker.locDepression", true);


	public HoughCircleTracker(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		resetFilter();
		setPropertyTooltip("radius","radius of circle in pixels");
		setPropertyTooltip("bufferLength","number of events to consider when searching for new maximum location");
		setPropertyTooltip("threshold",""); // TODO
		setPropertyTooltip("decay","");
		setPropertyTooltip("nrMax","");
		setPropertyTooltip("decayMode","");
		setPropertyTooltip("drawHough","overlays the Hough space real values on the sensor output");
	}

	public Object getFilterState() {
		return null;
	}

	@Override
	public void resetFilter() {
		initTracker();
	}

	final class Coordinate {

		public float x, y;

		Coordinate(){
			x = 0;
			y = 0;
		}

		Coordinate(float x, float y){
			this.x = x;
			this.y = y;
		}

		public void setCoordinate(float x, float y){
			this.x = x;
			this.y = y;
		}
	}

	synchronized private void initTracker() {

		System.out.println("HoughCircleTracker initialising...");

		accumulatorArray = new float[chip.getSizeX()][chip.getSizeY()];

		if((chip.getSizeX()==0) || (chip.getSizeY()==0)){
			return;
		}

		cameraX = chip.getSizeX();
		cameraY = chip.getSizeY();

		for(int i=0;i<chip.getSizeX();i++){
			for(int j=0; j < chip.getSizeY();j++){
				accumulatorArray[i][j]=0;
			}
		}

		eventHistory = new Coordinate[bufferLength];
		for(int i=0;i<bufferLength;i++) {
			eventHistory[i] = null;
		}

		bufferIndex = 0;
		maxValue = new float[nrMax];
		maxCoordinate = new Coordinate[nrMax];
		for(int i = 0; i<nrMax; i++)
		{
			maxCoordinate[i] = null;
		}
		for (int i = 0; i<nrMax; i++)
		{
			maxValue[i] = 0;

		}

		for (int i = 0;i<angleListLength;i++){
			sinTau[i] = (float)(Math.sin(((2*Math.PI)/angleListLength)*i));
			cosTau[i] = (float)(Math.cos(((2*Math.PI)/angleListLength)*i));
			circleOutline[i] = new Coordinate(radius*cosTau[i],radius*sinTau[i]);
		}
	}

	@Override
	public void initFilter() {
		resetFilter();
	}

	@Override
	public void update(Observable o, Object arg) {
		if(!isFilterEnabled()) {
			return;
		}
		initFilter();
	}

	public boolean getLocDepression(){
		return locDepression;
	}

	synchronized public void setLocDepression(boolean locDepression){
		getPrefs().putBoolean("HoughCircleTracker.locDepression",locDepression);
		if(locDepression != this.locDepression) {
			resetFilter();
		}
		this.locDepression = locDepression;
	}

	public boolean getDecayMode(){
		return decayMode;
	}

	synchronized public void setDecayMode(boolean decayMode){
		getPrefs().putBoolean("HoughCircleTracker.decayMode",decayMode);
		if(decayMode != this.decayMode) {
			resetFilter();
		}
		this.decayMode = decayMode;
	}

	public boolean getDrawHough(){
		return drawHough;
	}

	synchronized public void setDrawHough(boolean drawHough){
		getPrefs().putBoolean("HoughCircleTracker.drawHough",drawHough);
		if(drawHough != this.drawHough) {
			resetFilter();
		}
		this.drawHough = drawHough;
	}

	public float getRadius() {
		return radius;
	}


	synchronized public void setRadius(float radius) {

		if(radius < 0) {
			radius = 0;
		}
		getPrefs().putFloat("HoughCircleTracker.radius", radius);

		if(radius != this.radius) {
			resetFilter();
		}

		this.radius = radius;
	}

	public float getDecay() {
		return decay;
	}


	synchronized public void setDecay(float decay){
		if(decay < 0) {
			decay = 0;
		}

		if(decay != this.decay)
		{
			resetFilter();
		}

		this.decay = decay;
	}


	public int getNrMax() {
		return nrMax;
	}

	synchronized public void setNrMax(int nrMax){

		if(nrMax < 0) {
			nrMax = 0;
		}

		this.nrMax = nrMax;
		getPrefs().putInt("HoughCircleTracker.nrMax", nrMax);
		resetFilter();
	}

	public int getBufferLength() {
		return bufferLength;
	}

	synchronized public void setBufferLength(int bufferLength) {

		if(bufferLength < 0) {
			bufferLength=0;
		}

		this.bufferLength = bufferLength;

		getPrefs().putInt("HoughCircleTracker.bufferLength", bufferLength);
		resetFilter();
	}

	public int getThreshold() {
		return threshold;
	}

	synchronized public void setThreshold(int threshold) {

		if(threshold < 0) {
			threshold=0;
		}

		getPrefs().putInt("HoughCircleTracker.threshold", threshold);
		this.threshold = threshold;
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {

		cameraY=chip.getSizeY();
		cameraX=chip.getSizeX();

		if((accumulatorArray == null) || (maxValue == null) || (maxCoordinate == null)) {
			return;
		}

		GL2 gl=drawable.getGL().getGL2();

		// draw the Hough space
		if(drawHough == true)
		{
			for (int x = 0; x < cameraX; x++) {
				for (int y = 0; y < cameraY; y++) {

					float red   = accumulatorArray[x][y]/maxValue[0];
					float green = 1.0f - red;

					gl.glColor4f(red,green,0.0f,.3f);
					gl.glRectf(
						x-0.5f,
						y-0.5f,
						x+0.5f,
						y+0.5f);
				}
			}
		}

		// draw the circle
		gl.glColor4f(0.0f,0.0f,1.0f,1.0f);
		gl.glLineWidth(4);
		for(int j = 0; j<nrMax; j++)
		{
			if(maxCoordinate[j]== null) {
				continue;
			}
			gl.glRectf(maxCoordinate[j].x-1.0f,maxCoordinate[j].y-1.0f,
				maxCoordinate[j].x+1.0f,maxCoordinate[j].y+1.0f);
			//			gl.glBegin(GL2.GL_LINE_LOOP);
			//			for (int i = 0;i<angleListLength;i++){
			//				gl.glVertex2d(
			//						circleOutline[i].x + maxCoordinate[j].x,
			//						circleOutline[i].y + maxCoordinate[j].y);
			//			}
			//			gl.glEnd();
		}

	}

	// fast inclined ellipse drawing algorithm; ellipse eqn: A*x^2+B*y^2+C*x*y-1 = 0
	// the algorithm is fast because it uses just integer addition and subtraction
	void accumulate(Coordinate event, float weight){

		// TODO: this is a little overhead here, since we only draw circles in
		// Hough space (not ellipses)
		int centerX = (int)event.x;
		int centerY = (int)event.y;
		int aa	  = Math.round(radius*radius);
		int bb	  = aa;
		int twoC	= 0;

		int x = 0;
		int y = Math.round((float)Math.sqrt(bb));
		int twoaa = 2*aa;
		int twobb = 2*bb;
		int dx =   (twoaa*y) + (twoC*x);   //slope =dy/dx
		int dy = -((twobb*x) + (twoC*y));
		int ellipseError = aa*((y*y)-bb);

		// first sector: (dy/dx > 1) -> y+1 (x+1)
		// d(x,y+1)   = 2a^2y+a^2+2cx				= dx+aa
		// d(x+1,y+1) = 2b^2x+b^2+2cy+2c+2a^2y+a^2+2cx = d(x,y+1)-dy+bb
		while (dy > dx){
			increaseHoughPoint(centerX+x,centerY+y,weight);
			increaseHoughPoint(centerX-x,centerY-y,weight);
			ellipseError = ellipseError + dx + aa;
			dx = dx + twoaa;
			dy = dy - twoC;
			y = y + 1;
			if ((((2*ellipseError)-dy)+bb) > 0) {
				ellipseError = (ellipseError - dy) + bb ;
				dx = dx + twoC;
				dy = dy - twobb;
				x = x + 1;
			}
		}

		// second sector: (dy/dx > 0) -> x+1 (y+1)
		// d(x+1,y)   = 2b^2x+b^2+2cy				= -dy+bb
		// d(x+1,y+1) = 2b^2x+b^2+2cy+2c+2a^2y+a^2+2cx = d(x+1,y)+dx+aa
		while (dy > 0){
			increaseHoughPoint(centerX+x,centerY+y,weight);
			increaseHoughPoint(centerX-x,centerY-y,weight);
			ellipseError = (ellipseError - dy) + bb;
			dx = dx + twoC;
			dy = dy - twobb;
			x = x + 1;
			if (((2*ellipseError) + dx + aa) < 0){
				ellipseError = ellipseError + dx + aa ;
				dx = dx + twoaa;
				dy = dy - twoC;
				y = y + 1;
			}
		}

		// third sector: (dy/dx > -1) -> x+1 (y-1)
		// d(x+1,y)   = 2b^2x+b^2+2cy				= -dy+bb
		// d(x+1,y-1) = 2b^2x+b^2+2cy-2c-2a^2y+a^2-2cx = d(x+1,y)-dx+aa
		while (dy > - dx){
			increaseHoughPoint(centerX+x,centerY+y,weight);
			increaseHoughPoint(centerX-x,centerY-y,weight);
			ellipseError = (ellipseError - dy) + bb;
			dx = dx + twoC;
			dy = dy - twobb;
			x = x + 1;
			if ((((2*ellipseError) - dx) + aa) > 0){
				ellipseError = (ellipseError - dx) + aa;
				dx = dx - twoaa;
				dy = dy + twoC;
				y = y - 1;
			}
		}

		// fourth sector: (dy/dx < 0) -> y-1 (x+1)
		// d(x,y-1)   = -2a^2y+a^2-2cx			   = -dx+aa
		// d(x+1,y-1) = 2b^2x+b^2+2cy-2c-2a^2y+a^2-2cx = d(x+1,y)-dy+bb
		while (dx > 0){
			increaseHoughPoint(centerX+x,centerY+y,weight);
			increaseHoughPoint(centerX-x,centerY-y,weight);
			ellipseError = (ellipseError - dx) + aa;
			dx = dx - twoaa;
			dy = dy + twoC;
			y = y - 1;
			if ((((2*ellipseError) - dy) + bb) < 0){
				ellipseError = (ellipseError - dy) + bb;
				dx = dx + twoC;
				dy = dy - twobb;
				x = x + 1;
			}
		}

		//fifth sector (dy/dx > 1) -> y-1 (x-1)
		// d(x,y-1)   = -2a^2y+a^2-2cx				= -dx+aa
		// d(x-1,y-1) = -2b^2x+b^2-2cy+2c-2a^2y+a^2-2cx = d(x+1,y)+dy+bb
		while ((dy < dx)&& (x > 0)){
			increaseHoughPoint(centerX+x,centerY+y,weight);
			increaseHoughPoint(centerX-x,centerY-y,weight);
			ellipseError = (ellipseError - dx) + aa;
			dx = dx - twoaa;
			dy = dy + twoC;
			y = y - 1;
			if (((2*ellipseError) + dy + bb) > 0){
				ellipseError = ellipseError  + dy + bb;
				dx = dx - twoC;
				dy = dy + twobb;
				x = x - 1;
			}
		}

		// sixth sector: (dy/dx > 0) -> x-1 (y-1)
		// d(x-1,y)   = -2b^2x+b^2-2cy				= dy+bb
		// d(x-1,y-1) = -2b^2x+b^2-2cy+2c-2a^2y+a^2-2cx = d(x+1,y)-dx+aa
		while ((dy < 0)&& (x > 0)){
			increaseHoughPoint(centerX+x,centerY+y,weight);
			increaseHoughPoint(centerX-x,centerY-y,weight);
			ellipseError = ellipseError + dy + bb;
			dx = dx - twoC;
			dy = dy + twobb;
			x = x - 1;
			if ((((2*ellipseError) - dx) + aa) < 0){
				ellipseError = (ellipseError  - dx) + aa;
				dx = dx - twoaa;
				dy = dy + twoC;
				y = y - 1;
			}
		}

		// seventh sector: (dy/dx > -1) -> x-1 (y+1)
		// d(x-1,y)   = -2b^2x+b^2-2cy				= dy+bb
		// d(x-1,y+1) = -2b^2x+b^2-2cy-2c+2a^2y+a^2+2cx = d(x+1,y)-dx+aa
		while ((dy < - dx)&& (x > 0)){
			increaseHoughPoint(centerX+x,centerY+y,weight);
			increaseHoughPoint(centerX-x,centerY-y,weight);
			ellipseError = ellipseError + dy + bb;
			dx = dx - twoC;
			dy = dy + twobb;
			x = x - 1;
			if (((2*ellipseError) + dx + aa) > 0){
				ellipseError = ellipseError  + dx + aa;
				dx = dx + twoaa;
				dy = dy - twoC;
				y = y + 1;
			}
		}

		// eight sector: (dy/dx < 0) -> y+1 (x-1)
		// d(x,y+1)   = 2a^2y+a^2+2cx				 = dx+aa
		// d(x-1,y+1) = -2b^2x+b^2-2cy-2c+2a^2y+a^2+2cx = d(x,y+1)+dy+bb
		while (((dy > 0) && (dx < 0))&& (x > 0)){
			increaseHoughPoint(centerX+x,centerY+y,weight);
			increaseHoughPoint(centerX-x,centerY-y,weight);
			ellipseError = ellipseError + dx + aa;
			dx = dx + twoaa;
			dy = dy - twoC;
			y = y + 1;
			if (((2*ellipseError) + dy + bb) < 0){
				ellipseError = ellipseError + dy + bb ;
				dx = dx - twoC;
				dy = dy + twobb;
				x = x - 1;
			}
		}
	}

	boolean islocmax(int x, int y)
	{
		int locMaxRad = 1;
		int i =1;
		if (((x-locMaxRad) < 0) || ((x+locMaxRad) > (chip.getSizeX()-1))
			|| ((y-locMaxRad) < 0) || ((y+locMaxRad) > (chip.getSizeY()-1))) {
			return false;
		}
		if(accumulatorArray[x][y]<accumulatorArray[x][y+1]) {
			return false;
		}
		if(accumulatorArray[x][y]<accumulatorArray[x][y-1]) {
			return false;
		}
		if(accumulatorArray[x][y]<accumulatorArray[x+1][y]) {
			return false;
		}
		if(accumulatorArray[x][y]<accumulatorArray[x-1][y]) {
			return false;
		}
		if(accumulatorArray[x][y]<accumulatorArray[x+1][y-1]) {
			return false;
		}
		if(accumulatorArray[x][y]<accumulatorArray[x-1][y+1]) {
			return false;
		}
		if(accumulatorArray[x][y]<accumulatorArray[x+1][y+1]) {
			return false;
		}
		if(accumulatorArray[x][y]<accumulatorArray[x-1][y-1]) {
			return false;
		}


		return true;


	}

	void increaseHoughPoint(int x, int y, float weight) {

		if ((x < 0) || (x > (chip.getSizeX() - 1)) || (y < 0) || (y > (chip.getSizeY() - 1))) {
			return;
		}

		// increase the value of the hough point
		accumulatorArray[x][y] = accumulatorArray[x][y] + weight;

		// check if this is a new maximum
		for(int i=0; i<nrMax; i++)
		{
			if (accumulatorArray[x][y] >= maxValue[i]) {

				maxValue[i] = accumulatorArray[x][y];

				if ((maxValue[i] > threshold) && (maxCoordinate[i] != null)){
					maxCoordinate[i].x = x;
					maxCoordinate[i].y = y;
				}
				i += nrMax;
			}
		}
	}

	@Override
	synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
		cameraY=chip.getSizeY();
		cameraX=chip.getSizeX();

		if (in.isEmpty()) {
			return in;
		}

		if(decayMode == true) {
			float delta_t = in.getLastTimestamp() - timeStamp;
			float decay_factor = 1.0f/(0.0001f * decay * delta_t);

			//for an exponentially decaying hough-space-weight.
			for (int x = 0; x < cameraX; x++) {
				for (int y = 0; y < cameraY; y++) {
					accumulatorArray[x][y] *= decay_factor;

				}
			}
		}
		timeStamp = in.getLastTimestamp();


		maxValue = new float[nrMax];
		for(int i = 0; i<nrMax; i++)
		{
			if(maxCoordinate[i] == null) {
				maxCoordinate[i] = new Coordinate(0.0f,0.0f);
			}

		}
		for (BasicEvent event : in) {

			float weight=1;
			if(event instanceof WeightedEvent){
				WeightedEvent weightedEvent = (WeightedEvent)event;
				weight = weightedEvent.weight;
			}

			// save event in history
			eventHistory[bufferIndex] = new Coordinate(event.x, event.y);

			// accumulate all possible circle centers for the current event
			accumulate(eventHistory[bufferIndex], weight);

			// increase buffer index
			bufferIndex = (bufferIndex+1)%bufferLength;

			// remove the least recent event from hough space
			if((eventHistory[bufferIndex] != null) && (decayMode == false)) {
				accumulate(eventHistory[bufferIndex], -1);
			}
		}
		for(int i = 0; i<nrMax; i++)
		{
			maxValue[i] = 0;
		}
		for (int x = 0; x < cameraX; x++) {
			for (int y = 0; y < cameraY; y++) {

				for(int i=0; i<nrMax; i++)
				{
					if ((accumulatorArray[x][y] >= maxValue[i])
						&& islocmax(x,y)) {

						maxValue[i] = accumulatorArray[x][y];

						if (maxValue[i] > threshold){
							maxCoordinate[i].x = x;
							maxCoordinate[i].y = y;
						}
						i += nrMax;
					}
				}
			}

		}

		OutputEventIterator itr = out.outputIterator();
		for(int i = 0; i<nrMax; i++)
		{
			int x = (int)maxCoordinate[i].x;
			int y = (int)maxCoordinate[i].y;

			if(locDepression == true)
			{
				if(((x-1) > 0) && ((x+1) < (chip.getSizeX()-1))
					&& ((y-1) > 0) && ((y+1) < (chip.getSizeY()-1)))
				{
					accumulatorArray[x][y] *= 0.01f;
					accumulatorArray[x][y+1] *= 0.1f;
					accumulatorArray[x][y-1] *= 0.1f;
					accumulatorArray[x+1][y] *= 0.1f;
					accumulatorArray[x-1][y] *= 0.1f;
					accumulatorArray[x+1][y+1] *= 0.1f;
					accumulatorArray[x-1][y+1] *= 0.1f;
					accumulatorArray[x-1][y-1] *= 0.1f;
					accumulatorArray[x-1][y-1] *= 0.1f;
				}

			}

			BasicEvent outEvent = itr.nextOutput();
			outEvent.x = (short)maxCoordinate[i].x;
			outEvent.y = (short)maxCoordinate[i].y;
			outEvent.timestamp = (int) timeStamp;
			//this timestamp is only on packet resolution!
		}
		// pass events unchanged to next filter
		return out;
	}

	synchronized public boolean isLogDataEnabled() {
		return logDataEnabled;
	}

	synchronized public void setLogDataEnabled(boolean logDataEnabled) {

		this.logDataEnabled = logDataEnabled;

		if(dataLogger == null) {
			dataLogger = new EventFilterDataLogger(this,"# x y");
		}

		dataLogger.setEnabled(logDataEnabled);

		if(logDataEnabled){

			targetFrame = new JFrame("EyeTargget");
			gazePanel   = new DrawPanel();

			targetFrame.setLocation( 0, 0 );
			targetFrame.setSize( Toolkit.getDefaultToolkit().getScreenSize() );
			targetFrame.add( gazePanel );
			targetFrame.setVisible(true);
			targetFrame.addKeyListener( new KeyListener() {
				@Override
				public void keyTyped( KeyEvent e ) {
					//					System.out.println( "typed " + e.getKeyChar() );
					//					System.out.println( "typed " + e.getKeyCode() );
					gazePanel.newPosition();
				}
				@Override
				public void keyPressed( KeyEvent e ) {}
				@Override
				public void keyReleased( KeyEvent e ) {}
			});

		}
		else {
			targetFrame.setVisible(false);
		}
	}

	@SuppressWarnings("serial")
	class DrawPanel extends JPanel {

		int width  = targetFrame.getSize().width;
		int height = targetFrame.getSize().height;
		int x = 50;
		int y = height/2;
		int count = 0;
		int w = 1;

		@Override
		protected void paintComponent( Graphics g ) {

			width  = targetFrame.getSize().width;
			height = targetFrame.getSize().height;
			x = 50 + (count*(width-100))/2;;
			y = 50 + (count*(height-100))/2;
			super.paintComponent(g);
			g.fillRect(x,y,10,10);
		}

		public void newPosition() {

			if (isLogDataEnabled()) {
				dataLogger.log(String.format("%d %d %f %f", maxCoordinate[0].x, maxCoordinate[0].y));
			}

			count = (count+w)%3;
			if (count>1) {
				w=-1;
			}
			if (count<1) {
				w=+1;
			}

			repaint();
		}
	}
}
