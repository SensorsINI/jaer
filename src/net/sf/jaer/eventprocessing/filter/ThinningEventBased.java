/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.filter;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.fixedfunc.GLMatrixFunc;
import javax.media.opengl.glu.GLU;
import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
/**
 * Event-based thinning filter
 * @author rogister
 */
public class ThinningEventBased extends EventFilter2D{


	private int decayTimeLimit=getPrefs().getInt("ThinningEventBased.decayTimeLimit",10000);
	{setPropertyTooltip("decayTimeLimit","[microsec (us)] for decaying accumulated events");}

	private float balance=getPrefs().getFloat("ThinningEventBased.balance",1f);
	private float threshold=getPrefs().getFloat("ThinningEventBased.threshold",0.1f);
	{setPropertyTooltip("threshold","minimum value for generating events");}
	private float thinningThreshold=getPrefs().getFloat("ThinningEventBased.thinningThreshold",0.1f);
	{setPropertyTooltip("thinningThreshold","minimum value for interior of shape");}
	//  private float threshold2=getPrefs().getFloat("ThinningEventBased.threshold2",0.1f);
	//   {setPropertyTooltip("threshold2","minimum value for attaching segment");}

	private int intensityZoom = getPrefs().getInt("ThinningEventBased.intensityZoom",2);
	{setPropertyTooltip("intensityZoom","zoom in display window");}
	private float brightness=getPrefs().getFloat("ThinningEventBased.brightness",1f);
	{setPropertyTooltip("brightness","brightness or increase of display for accumulated values");}
	private boolean limitRange = getPrefs().getBoolean("ThinningEventBased.limitRange",true);
	private boolean decayEveryFrame = getPrefs().getBoolean("ThinningEventBased.decayEveryFrame",false);
	//   private boolean initGray = getPrefs().getBoolean("ThinningEventBased.initGray",false);



	int retinaSize = 128;
	protected float eventPoints[][] = new float[retinaSize][retinaSize];
	protected int thinnedPoints[][] = new int[retinaSize][retinaSize];
	protected int eventTimePoints[][] = new int[retinaSize][retinaSize];

	float event_strength= 1f;

	protected int colorScale = 2;
	protected float grayValue = 0.5f;
	protected int currentTime;

	//output
	boolean filterOutput = false;
	protected OutputEventIterator outItr;




	public ThinningEventBased(AEChip chip){
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

		// segment init


		accumulateAndFilter(in);



		if (insideIntensityCanvas!=null) {
			insideIntensityCanvas.repaint();
		}
		// return in and display results in window
		if(!filterOutput){
			return in;
		}
		return out;
	}

	protected void accumulateAndFilter(EventPacket<?> ae ){
		if(filterOutput){
			thinnedPoints = new int[retinaSize][retinaSize];
		}

		float step = event_strength / (colorScale + 1);
		int tempcurrentTime = ae.getLastTimestamp() ;
		if(tempcurrentTime!=0){
			currentTime = tempcurrentTime; // for avoid wrong timing to corrupt data
		}


		//  if(currentTime==0){
		//      System.out.println("ThinningEventBased accumulateAndFilter currentTime="+currentTime);
		//    }
		for(Object o:ae){
			PolarityEvent e=(PolarityEvent)o;
			int type=e.getType();

			float newValue = step * (type - grayValue);

			if(newValue<0) {
				newValue = newValue*balance;
			}

			eventPoints[e.x][e.y] +=  newValue; //+=

				eventTimePoints[e.x][e.y] = e.timestamp;

				if(limitRange){
					// keep in range [0-1]
					if(eventPoints[e.x][e.y]<0) {
						eventPoints[e.x][e.y]=0;
					}
					else if(eventPoints[e.x][e.y]>1) {
						eventPoints[e.x][e.y]=1;
					}
				}

				applyEventbasedThinning(e.x,e.y,newValue,e.timestamp);

		}

		// loop to generate events like using an additional network layer
		for(Object o:ae){
			PolarityEvent e=(PolarityEvent)o;
			// int type=e.getType();
			//  float f = decayedValue(thinnedPoints[e.x][e.y],currentTime-eventTimePoints[e.x][e.y]);
			// if(f>threshold){
			if(thinnedPoints[e.x][e.y]>threshold){
				generateEvent(e);

			}

		}

	}

	protected void applyEventbasedThinning(int x, int y, float v, int time ){

		// thin
		//        int randChoice = (int)Math.random();
		//        if(randChoice>0.5){
		//
		//           thinnedPoints[x][y] = thin_a(0,v,x,y);
		//           thin_surround_a(0,x,y);
		//           thinnedPoints[x][y] = thin_b(thinnedPoints[x][y],v,x,y);
		//           thin_surround_b(thinnedPoints[x][y],x,y);
		//        } else {
		//           thinnedPoints[x][y] = thin_a(0,v,x,y);
		//           thin_surround_a(0,x,y);
		//        }


		thinnedPoints[x][y] = thin_a(0,v,x,y);
		thin_surround_a(0,x,y);
		thinnedPoints[x][y] = thin_b(thinnedPoints[x][y],v,x,y);
		thin_surround_b(thinnedPoints[x][y],x,y);

	}

	int binarize( float v){
		if(v>=0.5f){
			return 1;
		} else {
			return 0;
		}
	}



	protected float getValueFor( float[][] points, int x, int y){
		float value = 0;
		if (decayEveryFrame) {
			//  if (initGray) {
			value = decayedValue(points[x][y], currentTime - eventTimePoints[x][y], 0.5f);
			if(value<0.5)
			{
				value = 1-value; // only get positive values
				// } else {
				//      value = decayedValue(points[x][y], currentTime - eventTimePoints[x][y]);
				//  }
			}

		}
		else {
			value = points[x][y];
		}
		return value;

	}

	protected float getValueFor( float value, int x, int y){
		float res = 0;
		if (decayEveryFrame) {

			res = decayedValue(value, currentTime - eventTimePoints[x][y]);


		}
		else {
			res = value;
		}
		return res;

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

	protected void generateEvent(int x, int y, float value, int time){


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
		}




	}

	protected void generateEvent(PolarityEvent e){
		PolarityEvent oe=(PolarityEvent) outItr.nextOutput(); // if we pass input, obtain next output event
		oe.copyFrom(e);


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
		//  System.out.println("ThinningEventBased resetFilter");
		thinnedPoints = new int[retinaSize][retinaSize];
		eventPoints = new float[retinaSize][retinaSize];
		//if(initGray)
		resetToGray(eventPoints);
		eventTimePoints = new int[retinaSize][retinaSize];

	}

	@Override
	public void initFilter() {
		// System.out.println("ThinningEventBased initFilter");
		thinnedPoints = new int[retinaSize][retinaSize];
		eventPoints = new float[retinaSize][retinaSize];
		//if(initGray)
		resetToGray(eventPoints);
		eventTimePoints = new int[retinaSize][retinaSize];


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
		insideIntensityFrame=new JFrame("ThinningEventBased");
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
				if(e.getKeyCode()==KeyEvent.VK_F){
					filterOutput = !filterOutput;
					insideIntensityCanvas.display();
				}

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

			private void drawPointsGreen( int[][] points, int[][] times, int time, GL2 gl ){

				for (int i = 0; i<points.length; i++){
					for (int j = 0; j<points[i].length; j++){
						float f = points[i][j];

						if(decayEveryFrame){


							f = decayedValue(points[i][j],time-times[i][j]);
							//                           if(f<0.5f){
								//                               f = 0;
								//                           } else {
									//                               f = 1;
							//                           }

						}


						gl.glColor3f(0,f,0);
						gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);

					}
				}

			}

			private void drawThinnedPoints( int[][] points, GL2 gl ){



				for (int i = 0; i<points.length; i++){
					for (int j = 0; j<points[i].length; j++){

						//  float f = getValueFor(contour.eventsArray[i][j].value,i,j);
						float f;
						if(decayEveryFrame){


							f = decayedValue(points[i][j],currentTime-eventTimePoints[i][j]);

							if(filterOutput){
								if(f<threshold) {
									f=0;
								}
							}

						} else {
							f = getValueFor(points[i][j],i,j);
						}

						//    if (f>threshold) {

							gl.glColor3f(0, f, 0);
							gl.glRectf(i * intensityZoom, (j) * intensityZoom, (i + 1) * intensityZoom, (j + 1) * intensityZoom);
							//     }
					}
				}

			}


			private void drawPoints( float[][] points, int[][] times, int time, GL2 gl ){


				for (int i = 0; i<points.length; i++){
					for (int j = 0; j<points[i].length; j++){
						float f = points[i][j];

						if(decayEveryFrame){

							//  if(initGray){
								f = decayedValue(points[i][j],time-times[i][j],0.5f);
								//  } else {
								//      f = decayedValue(points[i][j],time-times[i][j]);
								//  }
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


				// System.out.println("display left ");

				if (thinnedPoints != null) {
					// drawPointsGreen(thinnedPoints, eventTimePoints, currentTime, gl);
					drawThinnedPoints(thinnedPoints, gl);
					//
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
				gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
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


	// thinning algo, adapted from CACM 1984 march (Zhang and Suen)
	// Zhang, T Y and Suen, C Y, "A Fast Parallel Algorithm for Thinning Digital. Patterns",
	// CACM, Voi.27, No.3, March 1984, pp. 236-239

	int thin_a( int thinValue, float v, int x, int y){
		//   int thinValue = 0;
		if(Math.abs(v)>thinningThreshold){
			// thin
			int[] a = new int[8];
			int[] arbr = new int[2];
			thinValue = 1;

			arbr = t1a ( x, y, a,retinaSize,retinaSize, thinningThreshold);

			int p1 = a[0]*a[2]*a[4];
			int p2 = a[2]*a[4]*a[6];
			if ( (arbr[0] == 1) && ((arbr[1]>=2) && (arbr[1]<=6)) &&
				(p1 == 0) && (p2 == 0) )  {
				thinValue = 0;
			}
		} else {
			thinValue = 0;
		}
		return thinValue;

	}

	void thin_surround_a( int p, int x, int y ){

		for (int i=x-2; i<(x+3);i++){
			if((i>=0)&&(i<retinaSize)){
				for (int j=y-2; j<(y+3);j++){
					if((j>=0)&&(j<retinaSize)){
						// EventPoint surroundPoint = eventPoints[i][j];
						thinnedPoints[i][j] = thin_a(p,getValueFor(eventPoints,i,j),i,j);

					}
				}
			}
		}

	}

	void thin_surround_b( int p, int x, int y ){
		for (int i=x-2; i<(x+3);i++){
			if((i>=0)&&(i<retinaSize)){
				for (int j=y-2; j<(y+3);j++){
					if((j>=0)&&(j<retinaSize)){

						thinnedPoints[i][j] = thin_b(p,getValueFor(eventPoints,i,j),i,j);

					}
				}
			}
		}

	}

	int thin_b( int tvalue, float v, int x, int y){
		int thinValue = tvalue;
		// if(v==1){
		// thin
		int[] a = new int[8];
		int[] arbr = new int[2];
		// ep.skValue = 1;

		arbr = t1b ( x, y, a,retinaSize,retinaSize, thinningThreshold);

		int p1 = a[0]*a[2]*a[6];
		int p2 = a[0]*a[4]*a[6];
		if ( (arbr[0] == 1) && ((arbr[1]>=2) && (arbr[1]<=6)) &&
			(p1 == 0) && (p2 == 0) )  {
			thinValue = 0;
		}
		//  }
	//  generateEvent(x,y,thinValue,eventTimePoints[x][y]);

		return thinValue;
	}


	int[] t1a( int i, int j, int[] a, int nn, int mm, float thresh ){
		int[] arbr = new int[2];
		//...
		/*	Return the number of 01 patterns in the sequence of pixels
	P2 p3 p4 p5 p6 p7 p8 p9.					*/

		int n,m;
		int b;
		for (n=0; n<8; n++) {
			a[n] = 0;
		}
		if ((i-1) >= 0) {
			if (eventPoints[i-1][j]>thresh) {
				a[0] = 1;
			}
			if ((j+1) < mm) {
				if (getValueFor(eventPoints,i-1,j+1)>thresh) {
					a[1] = 1;
				}
			}
			if ((j-1) >= 0) {
				if (getValueFor(eventPoints,i-1,j-1)>thresh) {
					a[7] = 1;
				}
			}
		}
		if ((i+1) < nn) {
			if (eventPoints[i+1][j]>thresh) {
				a[4] = 1;
			}
			if ((j+1) < mm) {
				if (getValueFor(eventPoints,i+1,j+1)>thresh) {
					a[3] = 1;
				}
			}
			if ((j-1) >= 0) {
				if (getValueFor(eventPoints,i+1,j-1)>thresh) {
					a[5] = 1;
				}
			}
		}
		if ((j+1) < mm) {
			if (getValueFor(eventPoints,i,j+1)>thresh) {
				a[2] = 1;
			}
		}
		if ((j-1) >= 0) {
			if (getValueFor(eventPoints,i,j-1)>thresh) {
				a[6] = 1;
			}
		}

		m= 0;	b = 0;
		for (n=0; n<7; n++) {
			if ((a[n]==0) && (a[n+1]==1)) {
				m++;
			}
			b = b + a[n];
		}
		if ((a[7] == 0) && (a[0] == 1)) {
			m++;
		}
		b = b + a[7];

		arbr[0] = m;
		arbr[1] = b;
		return arbr;
	}

	int[] t1b( int i, int j, int[] a, int nn, int mm, float thresh ){
		int[] arbr = new int[2];
		//...
		/*	Return the number of 01 patterns in the sequence of pixels
	P2 p3 p4 p5 p6 p7 p8 p9.					*/

		int n,m;
		int b;
		for (n=0; n<8; n++) {
			a[n] = 0;
		}
		if ((i-1) >= 0) {
			a[0] = thinnedPoints[i-1][j];
			if ((j+1) < mm) {
				a[1] = thinnedPoints[i-1][j+1];
			}
			if ((j-1) >= 0) {
				a[7] = thinnedPoints[i-1][j-1];
			}
		}
		if ((i+1) < nn) {
			if (thinnedPoints[i+1][j]>thresh) {
				a[4] = 1;
			}
			if ((j+1) < mm) {
				a[3] = thinnedPoints[i+1][j+1];
			}
			if ((j-1) >= 0) {
				a[5] = thinnedPoints[i+1][j-1];
			}
		}
		if ((j+1) < mm) {
			a[2] = thinnedPoints[i][j+1];
		}
		if ((j-1) >= 0) {
			a[6] = thinnedPoints[i][j-1];
		}

		m= 0;	b = 0;
		for (n=0; n<7; n++) {
			if ((a[n]==0) && (a[n+1]==1)) {
				m++;
			}
			b = b + a[n];
		}
		if ((a[7] == 0) && (a[0] == 1)) {
			m++;
		}
		b = b + a[7];

		arbr[0] = m;
		arbr[1] = b;
		return arbr;
	}




	protected float distanceBetween( int x1, int y1, int x2, int y2){

		double dx = x1-x2;
		double dy = y1-y2;

		float dist = (float)Math.sqrt((dy*dy)+(dx*dx));


		return dist;
	}





	// ---------------   get set




	public void setIntensityZoom(int intensityZoom) {
		this.intensityZoom = intensityZoom;

		getPrefs().putInt("ThinningEventBased.intensityZoom",intensityZoom);
	}

	public int getIntensityZoom() {
		return intensityZoom;
	}
	public float getBrightness() {
		return brightness;
	}

	public void setBrightness(float brightness) {
		this.brightness = brightness;

		getPrefs().putFloat("ThinningEventBased.brightness",brightness);
	}

	public float getBalance() {
		return balance;
	}

	public void setBalance(float balance) {
		this.balance = balance;

		getPrefs().putFloat("ThinningEventBased.balance",balance);
	}


	public float getThreshold() {
		return threshold;
	}

	public void setThreshold(float threshold) {
		this.threshold = threshold;

		getPrefs().putFloat("ThinningEventBased.threshold",threshold);
	}

	public float getThinningThreshold() {
		return thinningThreshold;
	}

	public void setThinningThreshold(float thinningThreshold) {
		this.thinningThreshold = thinningThreshold;

		getPrefs().putFloat("ThinningEventBased.thinningThreshold",thinningThreshold);
	}


	//    public float getThreshold2() {
	//        return threshold2;
	//    }
	//
	//    public void setThreshold2(float threshold2) {
	//        this.threshold2 = threshold2;
	//
	//        getPrefs().putFloat("ThinningEventBased.threshold2",threshold2);
	//    }

	public void setLimitRange(boolean limitRange){
		this.limitRange = limitRange;

		getPrefs().putBoolean("ThinningEventBased.limitRange",limitRange);
	}
	public boolean getLimitRange(){
		return limitRange;
	}

	public void setDecayTimeLimit(int decayTimeLimit) {
		this.decayTimeLimit = decayTimeLimit;

		getPrefs().putInt("ThinningEventBased.decayTimeLimit",decayTimeLimit);
	}
	public int getDecayTimeLimit() {
		return decayTimeLimit;
	}

	public void setDecayEveryFrame(boolean decayEveryFrame){
		this.decayEveryFrame = decayEveryFrame;

		getPrefs().putBoolean("ThinningEventBased.decayEveryFrame",decayEveryFrame);
	}
	public boolean getDecayEveryFrame(){
		return decayEveryFrame;
	}


	//    public void setInitGray(boolean initGray){
	//        this.initGray = initGray;
	//
	//        getPrefs().putBoolean("ThinningEventBased.initGray",initGray);
	//    }
	//    public boolean getInitGray(){
	//        return initGray;
	//    }


}
