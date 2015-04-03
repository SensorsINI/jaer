/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.filter;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
/**
 * Spatial low pass filter using event based algorithm
 * redondant maybe with Tobi's SubSamplingBandpassFilter and SpatialBandPassFilter
 * except only spatial not temporal (although decay is used)
 * @author rogister
 */
public class LowPassFilterEventGenerator extends EventFilter2D{


	private int decayTimeLimit=getPrefs().getInt("LowPassFilterEventGenerator.decayTimeLimit",10000);
	{setPropertyTooltip("decayTimeLimit","[microsec (us)] for decaying accumulated events");}
	private int radius=getPrefs().getInt("LowPassFilterEventGenerator.radius",1);
	private float balance=getPrefs().getFloat("LowPassFilterEventGenerator.balance",1f);

	private int intensityZoom = getPrefs().getInt("LowPassFilterEventGenerator.intensityZoom",2);
	{setPropertyTooltip("intensityZoom","zoom in display window");}
	private float brightness=getPrefs().getFloat("LowPassFilterEventGenerator.brightness",1f);
	{setPropertyTooltip("brightness","brightness or increase of display for accumulated values");}
	private boolean limitRange = getPrefs().getBoolean("LowPassFilterEventGenerator.limitRange",true);
	private boolean decayEveryFrame = getPrefs().getBoolean("LowPassFilterEventGenerator.decayEveryFrame",false);
	private boolean initGray = getPrefs().getBoolean("LowPassFilterEventGenerator.initGray",false);

	private float threshold=getPrefs().getFloat("LowPassFilterEventGenerator.threshold",0.4f);
	{setPropertyTooltip("threshold","threshold on acc values for generating new events");}

	int retinaSize = 128;
	//  protected float eventPoints[][] = new float[retinaSize][retinaSize];
	protected float filteredEventPoints[][] = new float[retinaSize][retinaSize];
	protected int filteredTimePoints[][] = new int[retinaSize][retinaSize];

	float event_strength= 2f;
	float radiusSq = radius * radius;
	protected int colorScale = 2;
	protected float grayValue = 0.5f;
	protected int currentTime;
	protected OutputEventIterator outItr;


	public LowPassFilterEventGenerator(AEChip chip){
		super(chip);

	}

	/** This filterPacket method assumes the events have PolarityEvent type
	 *
	 * @param in the input packet
	 * @return the output packet, where events have possibly been deleted from the input
	 */
	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		if(!isFilterEnabled()) {
			return in;
		}
		checkInsideIntensityFrame();
		checkOutputPacketEventType(in); // make sure the built-in output packet has same event type as input packet
		outItr=out.outputIterator(); // get the built in iterator for output events


		accumulateAndFilter(in);

		if (insideIntensityCanvas!=null) {
			insideIntensityCanvas.repaint();
		}
		// return in and display results in window
		return out;
	}

	protected void accumulateAndFilter(EventPacket<?> ae ){
		float step = event_strength / (colorScale + 1);
		int tempcurrentTime = ae.getLastTimestamp() ;
		if(tempcurrentTime!=0){
			currentTime = tempcurrentTime; // for avoid wrong timing to corrupt data
		}

		for(Object o:ae){
			PolarityEvent e=(PolarityEvent)o;
			int type=e.getType();

			float newValue = step * (type - grayValue);

			if(newValue<0) {
				newValue = newValue*balance;
			}

			//  eventPoints[e.x][e.y] = newValue;
			//  eventPoints[e.x][e.y] +=  newValue;

			// if(limitRange){
			// keep in range [0-1]
			//       if(eventPoints[e.x][e.y]<-1)eventPoints[e.x][e.y]=-1;
			//       else if(eventPoints[e.x][e.y]>1)eventPoints[e.x][e.y]=1;
			//   }

			applyEventbasedFilter(e.x,e.y,newValue,e.timestamp);

		}

		// loop to generate events like using an additional network layer
		for(Object o:ae){
			PolarityEvent e=(PolarityEvent)o;

			int type=e.getType();

			if(Math.abs(filteredEventPoints[e.x][e.y])>threshold){
				Point p = null;
				if (initGray) {
					float n = howManyNeighbors(filteredEventPoints, e.x, e.y, type)/2;
					float randG = (float)(Math.random() * n);
					p = lowPassPoint(filteredEventPoints, e.x, e.y, randG, type);
					// int sum = sumN(filteredEventPoints,e.x,e.y);
					//  if(sum>9) System.out.println("sum "+sum);
					// get ind


				} else {
					int n = howManyNeighbors(e.x, e.y);
					float randG = (float) (Math.random() * n);
					p = lowPassPoint(filteredEventPoints, e.x, e.y, randG, type);
					// int sum = sumN(filteredEventPoints,e.x,e.y);
					//  if(sum>9) System.out.println("sum "+sum);
					// get ind


				}
				if (p != null) {
					generateEvent(e, p.x, p.y);
				}
			}

		}
	}

	int howManyNeighbors( int x, int y){
		int res = 0;
		for (int i=x-1; i<(x+1+1);i++){
			if((i>=0)&&(i<retinaSize)){
				for (int j=y-1; j<(y+1+1);j++){
					if((j>=0)&&(j<retinaSize)){

						res++;
					}
				}
			}
		}
		return res;
	}

	int howManyNeighbors( float points[][], int x, int y, int type){
		int res = 0;
		for (int i=x-1; i<(x+1+1);i++){
			if((i>=0)&&(i<retinaSize)){
				for (int j=y-1; j<(y+1+1);j++){
					if((j>=0)&&(j<retinaSize)){
						if(type>0.5){
							if (points[i][j] >= 0.5) {
								res++;
							}
						} else {
							if (points[i][j] <= 0.5) {
								res++;
							}
						}
					}
				}
			}
		}
		return res;
	}

	int sumN( float points[][], int x, int y){
		int res = 0;
		for (int i=x-1; i<(x+1+1);i++){
			if((i>=0)&&(i<retinaSize)){
				for (int j=y-1; j<(y+1+1);j++){
					if((j>=0)&&(j<retinaSize)){
						//System.out.println("points["+i+"]["+j+"] "+points[i][j]);
						res+=points[i][j];
					}
				}
			}
		}
		return res;
	}

	Point lowPassPoint(float points[][] , int x, int y, float randSelect, int type){
		Point p = new Point();
		float sum = 0;
		for (int i=x-1; i<(x+1+1);i++){
			if((i>=0)&&(i<retinaSize)){
				for (int j=y-1; j<(y+1+1);j++){
					if((j>=0)&&(j<retinaSize)){
						if(type>0.5){
							sum += (points[i][j]-0.5);

						} else {
							sum += (0.5-points[i][j]);
						}
						if (sum >= randSelect) {
							p.x = i;
							p.y = j;
							return p;
						}
					}
				}
			}
		}

		return null;
	}

	Point lowPassPoint(float points[][] , int x, int y, float randSelect){
		Point p = new Point();
		float sum = 0;
		for (int i=x-1; i<(x+1+1);i++){
			if((i>=0)&&(i<retinaSize)){
				for (int j=y-1; j<(y+1+1);j++){
					if((j>=0)&&(j<retinaSize)){

						sum += points[i][j];
						if(sum>=randSelect){
							p.x = i;
							p.y = j;
							return p;
						}
					}
				}
			}
		}

		return null;
	}

	protected void applyEventbasedFilter(int x, int y, float v, int time ){

		float radiusRangeTotal = ((radius*2)+1)*((radius*2)+1); //border effect not taken into account
		float value = v/radiusRangeTotal;
		//float radiusRangeTotal = computeRangeTotal(radius);
		for (int i=x-radius; i<(x+radius+1);i++){
			if((i>=0)&&(i<retinaSize)){
				for (int j=y-radius; j<(y+radius+1);j++){
					if((j>=0)&&(j<retinaSize)){

						if(decayEveryFrame){
							if(initGray){
								filteredEventPoints[i][j] = decayedValue(filteredEventPoints[i][j],time-filteredTimePoints[i][j],0.5f);
							} else {
								filteredEventPoints[i][j] = decayedValue(filteredEventPoints[i][j],time-filteredTimePoints[i][j]);

							}
						}
						filteredEventPoints[i][j] += value;

						if(limitRange){
							// keep in range [0-1]
							if(filteredEventPoints[i][j]<0) {
								filteredEventPoints[i][j]=0;
							}
							else if(filteredEventPoints[i][j]>1) {
								filteredEventPoints[i][j]=1;
							}
						}

						filteredTimePoints[i][j] = time;

						// generates events
						// generateEvent(i,j,filteredEventPoints[i][j],filteredTimePoints[i][j]);

					}
				}
			}
		}


	}

	protected void generateEvent(PolarityEvent e){
		PolarityEvent oe=(PolarityEvent) outItr.nextOutput(); // if we pass input, obtain next output event
		oe.copyFrom(e);


	}

	// pb with grey
	protected void generateEvent(PolarityEvent e, int x, int y){
		PolarityEvent oe=(PolarityEvent) outItr.nextOutput(); // if we pass input, obtain next output event
		oe.copyFrom(e);
		oe.x = (short)x;
		oe.y = (short)y;


	}

	protected void generateEvent(int x, int y, float value, int time){

		if (initGray) {
			if(value>(0.5f+threshold)){
				// new ON event
				PolarityEvent e = new PolarityEvent();
				e.timestamp = time;
				e.x = (short)x;
				e.y = (short)y;
				e.type = 1;
				e.polarity = PolarityEvent.Polarity.On;

				PolarityEvent oe=(PolarityEvent) outItr.nextOutput(); // if we pass input, obtain next output event
				oe.copyFrom(e);
			} else if(value<(0.5f-threshold)){
				// new OFF event
				PolarityEvent e = new PolarityEvent();
				e.timestamp = time;
				e.x = (short)x;
				e.y = (short)y;
				e.type = 0;
				e.polarity = PolarityEvent.Polarity.Off;
				PolarityEvent oe=(PolarityEvent) outItr.nextOutput(); // if we pass input, obtain next output event
				oe.copyFrom(e);
			}
		} else {
			if(value>threshold){
				// new ON event
				PolarityEvent e = new PolarityEvent();
				e.timestamp = time;
				e.x = (short)x;
				e.y = (short)y;
				e.type = 1;
				e.polarity = PolarityEvent.Polarity.On;

				PolarityEvent oe=(PolarityEvent) outItr.nextOutput(); // if we pass input, obtain next output event
				oe.copyFrom(e);
			} // OFF Event?
		}

	}

	protected float decayedValue( float value, int time ){
		float res=value;

		float dt = (float)time/(float)decayTimeLimit;
		if(dt<0) {
			dt = 0;
		}
		//if(dt<1){
		res = value - (0.1f * dt);
		// }
		return res;
	}

	protected float decayedValue( float value, int time, float reference ){
		float res=value;

		float dt = (float)time/(float)decayTimeLimit;
		if(dt<0) {
			dt = 0;
		}



		//if(dt<1){
		if(value>reference){ // converge toward reference
			res = value - (0.1f * dt);
			if(res<0.5f) {
				res = 0.5f;
			}
			if(res>value) {
				res = value;
			}

		} else if(value<reference){
			res = value + (0.1f * dt);
			if(res>0.5f) {
				res = 0.5f;
			}
			if(res<value) {
				res = value;
			}
		}
		// }
		return res;
	}


	protected void resetToGray( float[][] array ){
		for (int x=0;x<array.length;x++){
			for (int y=0;y<array[0].length;y++){
				array[x][y] = 0.5f;
			}
		}
	}


	public Object getFilterState() {
		return null;
	}

	@Override
	public void resetFilter() {
		filteredEventPoints = new float[retinaSize][retinaSize];
		//  eventPoints = new float[retinaSize][retinaSize];
		if(initGray) {
			resetToGray(filteredEventPoints);
		}
		filteredTimePoints = new int[retinaSize][retinaSize];
	}

	@Override
	public void initFilter() {
		filteredEventPoints = new float[retinaSize][retinaSize];
		// eventPoints = new float[retinaSize][retinaSize];
		if(initGray) {
			resetToGray(filteredEventPoints);
		}
		filteredTimePoints = new int[retinaSize][retinaSize];

	}




	// show 2D view

	void checkInsideIntensityFrame(){
		if(insideIntensityFrame==null) {
			createInsideIntensityFrame();
		}
	}

	JFrame insideIntensityFrame=null;
	GLCanvas insideIntensityCanvas=null;

	private static final GLU glu = new GLU();

	int highlight_x = 0;
	int highlight_y = 0;
	int highlight_xR = 0;

	boolean highlight = false;
	float rotation = 0;
	//    GLUT glut=null;
	void createInsideIntensityFrame(){
		insideIntensityFrame=new JFrame("LowPassFilterEventGenerator");
		insideIntensityFrame.setPreferredSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
		insideIntensityCanvas=new GLCanvas();

		insideIntensityCanvas.addKeyListener( new KeyListener(){
			/** Handle the key typed event from the text field. */
			@Override
			public void keyTyped(KeyEvent e) {

			}

			@Override
			public void keyPressed(KeyEvent e) {

			}

			@Override
			public void keyReleased(KeyEvent e) {


			}
		});

		insideIntensityCanvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {


			}
			@Override
			public void mouseReleased(MouseEvent e){


			}
		});


		insideIntensityCanvas.addGLEventListener(new GLEventListener(){
			@Override
			public void init(GLAutoDrawable drawable) {
			}




			private void drawPoints( float[][] points, int[][] times, int time, GL2 gl ){


				for (int i = 0; i<points.length; i++){
					for (int j = 0; j<points[i].length; j++){
						float f = points[i][j];

						if(decayEveryFrame){

							if(initGray){
								f = decayedValue(points[i][j],time-times[i][j],0.5f);
							} else {
								f = decayedValue(points[i][j],time-times[i][j]);
							}
						}

						f = f*brightness;


						gl.glColor3f(f,f,f);
						gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);

					}
				}

			}

			@Override
			synchronized public void display(GLAutoDrawable drawable) {

				GL2 gl=drawable.getGL().getGL2();
				gl.glLoadIdentity();
				//gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
				gl.glClearColor(0,0,0,0);
				gl.glClear(GL.GL_COLOR_BUFFER_BIT);
				// int font = GLUT.BITMAP_HELVETICA_12;

				if(filteredEventPoints!=null){
					// System.out.println("display left ");
					drawPoints(filteredEventPoints,filteredTimePoints,currentTime,gl);
				}

				int error=gl.glGetError();
				if(error!=GL.GL_NO_ERROR){
					// if(glu==null) glu=new GLU();
					//log.warning("GL error number "+error+" "+glu.gluErrorString(error));
				}
			}

			@Override
			synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
				GL2 gl=drawable.getGL().getGL2();
				final int B=10;
				gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
				gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
				gl.glOrtho(-B,drawable.getSurfaceWidth()+B,-B,drawable.getSurfaceHeight()+B,10000,-10000);
				gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
				gl.glViewport(0,0,width,height);
			}

			@Override
			public void dispose(GLAutoDrawable arg0) {
				// TODO Auto-generated method stub

			}
		});
		insideIntensityFrame.getContentPane().add(insideIntensityCanvas);
		insideIntensityFrame.pack();
		insideIntensityFrame.setVisible(true);
	}


	// ---------------   get set


	public int getRadius() {
		return radius;
	}

	public void setRadius(int radius) {

		this.radius=radius;
		radiusSq = radius * radius;
		getPrefs().putInt("LowPassFilterEventGenerator.radius", radius);
	}

	public void setIntensityZoom(int intensityZoom) {
		this.intensityZoom = intensityZoom;

		getPrefs().putInt("LowPassFilterEventGenerator.intensityZoom",intensityZoom);
	}

	public int getIntensityZoom() {
		return intensityZoom;
	}
	public float getBrightness() {
		return brightness;
	}

	public void setBrightness(float brightness) {
		this.brightness = brightness;

		getPrefs().putFloat("LowPassFilterEventGenerator.brightness",brightness);
	}

	public float getBalance() {
		return balance;
	}

	public void setBalance(float balance) {
		this.balance = balance;

		getPrefs().putFloat("LowPassFilterEventGenerator.balance",balance);
	}

	public void setLimitRange(boolean limitRange){
		this.limitRange = limitRange;

		getPrefs().putBoolean("LowPassFilterEventGenerator.limitRange",limitRange);
	}
	public boolean getLimitRange(){
		return limitRange;
	}

	public void setDecayTimeLimit(int decayTimeLimit) {
		this.decayTimeLimit = decayTimeLimit;

		getPrefs().putInt("LowPassFilterEventGenerator.decayTimeLimit",decayTimeLimit);
	}
	public int getDecayTimeLimit() {
		return decayTimeLimit;
	}

	public void setDecayEveryFrame(boolean decayEveryFrame){
		this.decayEveryFrame = decayEveryFrame;

		getPrefs().putBoolean("LowPassFilterEventGenerator.decayEveryFrame",decayEveryFrame);
	}
	public boolean getDecayEveryFrame(){
		return decayEveryFrame;
	}


	public void setInitGray(boolean initGray){
		this.initGray = initGray;

		getPrefs().putBoolean("LowPassFilterEventGenerator.initGray",initGray);
	}
	public boolean getInitGray(){
		return initGray;
	}
	public float getThreshold() {
		return threshold;
	}

	public void setThreshold(float threshold) {
		this.threshold = threshold;

		getPrefs().putFloat("LowPassFilterEventGenerator.threshold",threshold);
	}
}
