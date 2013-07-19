/*
 * OnlineCalibration4.java
 * match pixels from two DVS based on luminance and interspike intervals distances
 * dispay results in a disparity map
 *
 * Paul Rogister, Created on FEbruary, 2009
 *
 */

package ch.unizh.ini.jaer.projects.stereo3D;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.fixedfunc.GLMatrixFunc;
import javax.media.opengl.glu.GLU;
import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.util.gl2.GLUT;

/**
 * OnlineCalibration:
 * Open two Display Frame that show the accumulated value of the pixels activation
 * one for the left and one for the right retina when mounted on the stereoboard
 *
 * @author rogister
 */
public class OnlineCalibration4 extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {


	protected final int RIGHT = 1;
	protected final int LEFT = 0;

	int maxGCs = 100;

	int highlightIndex = -1;



	protected AEChip chip;
	private AEChipRenderer renderer;

	private boolean logDataEnabled=false;
	private PrintStream logStream=null;

	// Parameters appearing in the GUI

	private int timeWindow=getPrefs().getInt("OnlineCalibration4.timeWindow",1000);
	{setPropertyTooltip("timeWindow","timeWindow in us");}

	private int accThreshold=getPrefs().getInt("OnlineCalibration4.accThreshold",10);
	{setPropertyTooltip("accThreshold","minimum amount of events per pixel for matching");}


	//  private int cleaningThreshold=getPrefs().getInt("OnlineCalibration4.cleaningThreshold",1);
	//  {setPropertyTooltip("cleaningThreshold","minimum acc value below which cell is deleted to free memory");}

	private int maxThreshold=getPrefs().getInt("OnlineCalibration4.maxThreshold",1);
	// {setPropertyTooltip("maxThreshold","minimum max value f");}

	private int skipPixels=getPrefs().getInt("OnlineCalibration4.skipPixels",1);
	{setPropertyTooltip("skipPixels","optimize memory usage by skipping left pixels every n skipPixels");}



	private float intensityZoom = getPrefs().getFloat("OnlineCalibration4.intensityZoom",0.05f);
	{setPropertyTooltip("intensityZoom","zoom for association display window");}

	private float intensityZoom2 = getPrefs().getFloat("OnlineCalibration4.intensityZoom2",2);
	{setPropertyTooltip("intensityZoom2","zoom for 128*128 display window");}


	private int maxProcessTimeNs=getPrefs().getInt("OnlineCalibration4.maxProcessTimeNs",500000);
	{setPropertyTooltip("maxProcessTimeNs","max processing time allowed in nano sec");}

	private int factor=getPrefs().getInt("OnlineCalibration4.factor",10);

	//   private int currentTracker=getPrefs().getInt("OnlineCalibration4.currentTracker",0);
	//  {setPropertyTooltip("currentTracker","index oc current tracker for each left points");}


	private int leftColumnX=getPrefs().getInt("OnlineCalibration4.leftColumnX",65);


	//   private int minEvents = getPrefs().getInt("OnlineCalibration4.minEvents",100);
	//    {setPropertyTooltip("minEvents","min events to create GC");}

	private boolean showWindow1 = getPrefs().getBoolean("OnlineCalibration4.showWindow1", true);
	private boolean showWindow2 = getPrefs().getBoolean("OnlineCalibration4.showWindow2", true);
	private boolean showWindow3 = getPrefs().getBoolean("OnlineCalibration4.showWindow3", true);

	// do not forget to add a set and a getString/is method for each new parameter, at the end of this .java file
	// global variables

	private int retinaSize=128;//getPrefs().getInt("GravityCentersImageDumper.retinaSize",128);
	private int columnheight = getPrefs().getInt("OnlineCalibration4.columnheight",10);
	private int columnsize = getPrefs().getInt("OnlineCalibration4.columnsize",50);



	//    private int leftColumnX = 14;
	private int rightColumnX1 = 25;
	private int rightColumnX2 = 50;
	private int rightColumnX3 = 75;
	private int rightColumnX4 = 100;


	// int currentTracker = 0;
	float alpha = 0.9f;

	protected float grayValue = 0.5f;
	float step = 0.33334f;

	boolean firstRun = true;

	boolean logLeftAccPNG=false;
	boolean accLeftLogged=false;

	boolean logRightAccPNG=false;
	boolean accRightLogged=false;

	//    private boolean condition = getPrefs().getBoolean("GravityCentersImageDumper.condition",false);
	//   {setPropertyTooltip("condition","true or not?");}

	public static DateFormat loggingFilenameDateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");




	protected class TimePixel{
		public int index;
		public int time;

		public TimePixel( ){
			time = 0;
			index = 0;

		}

		public TimePixel(int index, int time){
			this.time = time;
			this.index = index;

		}

		public TimePixel(int x, int y, int time){
			this.time = time;

			index = (y*retinaSize)+x;

		}
	}


	int[] maxLuminance;
	int[] minLuminance;

	// mofidied by setDepthFrom, and yet specific to left side, need an array of 2 to use method fro mroght also
	float maxDepth;
	float minDepth;
	int firstEventTime;
	int lastEventTime;

	int nbTrackers = 10;
	int nbColumns = 4;



	//  int[][] associations = new int[retinaSize*retinaSize][nbTrackers];// 4 trackers per left pixel
	//  int[] currentTrackers = new int[retinaSize*retinaSize];
	//  int[] nbEventsPerPixel = new int[retinaSize*retinaSize];

	//int columnsize = 40;
	int[][][] pixColumns = new int[retinaSize][columnsize][columnheight];


	//   Hashtable associations = new Hashtable();


	Vector<TimePixel> leftPoints = new Vector<TimePixel>();
	Vector<TimePixel> rightPoints = new Vector<TimePixel>();

	/** Creates a new instance of GravityCentersImageDumper */
	public OnlineCalibration4(AEChip chip) {
		super(chip);
		this.chip=chip;
		renderer=chip.getRenderer();

		initFilter();

		chip.addObserver(this);


	}

	@Override
	public void initFilter() {

	}



	private void initDefault(String key, String value){
		if(getPrefs().get(key,null)==null) {
			getPrefs().put(key,value);
		}
	}

	// the method that actually does the tracking
	synchronized private void track(EventPacket<BinocularEvent> ae){

		int n=ae.getSize();
		if(n==0) {
			return;
		}

		//  debugcounter = 0;
		//  debugtotalcounter = 0;
		// reset

		//   maxLuminance = new int[2];
		//   minLuminance = new int[2];
		//   firstEventTime = ae.getFirstTimestamp();
		//   lastEventTime = ae.getLastTimestamp();
		//  debugNumberOfONEventperPixel = new int[retinaSize][retinaSize];
		//  debugNumberOfOFFEventperPixel = new int[retinaSize][retinaSize];

		// 1) create ISI and luminance for all pixels

		//  long startSysTime = System.currentTimeMillis();
		long startSysTime = System.nanoTime();


		long currentSysTime;
		int nbEventsProcessed=0;

		checkOutputPacketEventType(ae);
		OutputEventIterator outItr = out.outputIterator();

		for(BinocularEvent e:ae){

			// need to drop events there if not real time
			BinocularEvent o = (BinocularEvent) outItr.nextOutput();
			o.copyFrom(e);
			addToAssociation(e);
			nbEventsProcessed++;
			//    currentSysTime = System.currentTimeMillis();
			currentSysTime = System.nanoTime();
			if(currentSysTime>(startSysTime+maxProcessTimeNs)){
				// System.out.println("OnlineCalibration4 track: throwing away events, nbEventsProcessed "+nbEventsProcessed+"/"+ae.getSize());
				break;
			}

		}




		// need to increase currentTracker at some point

	}

	/*
    private void cleanMemory(){
         if (nbCalls > cleaningFrequency) { // clean memory

             System.out.println("cleanMemory");

            nbCalls = 0;
            // loop through all
            Enumeration assocKeys = associations.keys();
            while (assocKeys.hasMoreElements()) {
                Integer key = (Integer) assocKeys.nextElement();
                Hashtable row = (Hashtable) associations.getString(key);
                Enumeration rowKeys = row.keys();

                int maxval = 0;
                //int minval = 10000000;
                while (rowKeys.hasMoreElements()) {
                    Integer index = (Integer) rowKeys.nextElement();
                    Integer value = (Integer) row.getString(index);
                    int f = value.intValue();
                    if(f>maxval){
                        maxval = f;
                    }
                  //  if(f<minval){
                   //     minval = f;
                   // }
                    if (f <= cleaningThreshold) {

                        // delete cell
                        row.remove(index);
                        value = null;
                        //System.out.println("cleanMemory delete cell");
                    }

                }
                if(maxval<cleaningThreshold){

                    associations.remove(key);
                    row = null;
                }
              //System.out.println("cleanMemory min: "+minval);

            }
         }


    }
	 */

	@Override
	public String toString(){
		String s="OnlineCalibration4";
		return s;
	}


	public Object getFilterState() {
		return null;
	}

	private boolean isGeneratingFilter() {
		return false;
	}

	@Override
	synchronized public void resetFilter() {
		if(!firstRun){
			// System.out.println ("OnlineCalibration4 resetFilter ");
			logLeftAccPNG = false;
			logRightAccPNG = false;

			accLeftLogged = false;
			accRightLogged = false;
			resetArrays();

			//        for(int i=0;i<associations.length;i++){
			//            associations[i] = new Hashtable();
			//
			//
			//        }

		}
	}

	protected void resetArrays(){
		leftPoints = new Vector<TimePixel>();
		rightPoints = new Vector<TimePixel>();

		//  associations = new Hashtable();
		// associations = new int[retinaSize*retinaSize][nbTrackers];
	}

	protected void resetColumns(){

		pixColumns = new int[retinaSize][columnsize][columnheight];
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
		if(!(in.getEventPrototype() instanceof BinocularEvent)) {
			// System.out.println("not a binocular event!");
			return in;
		}
		//
		//        if(accLeftLogged&&logLeftAccPNG){
		//            logLeftAccPNG=false;
		//            accLeftLogged=false;
		//
		//            writePNG(leftImage3DOpenGL,"AccLeft");
		//
		//        }
		//
		//        if(accRightLogged&&logRightAccPNG){
		//            logRightAccPNG=false;
		//            accRightLogged=false;
		//
		//            writePNG(rightImage3DOpenGL,"AccRight");
		//
		//        }
		//
		checkAssociationDisplayFrame();
		checkLeftDisplayFrame();

		checkRightDisplayFrame();

		track(in);




		if (showWindow1) {
			associationdisplayCanvas.repaint();
		}
		if (showWindow2) {
			leftdisplayCanvas.repaint();
		}
		if (showWindow3) {
			rightdisplayCanvas.repaint();
		}



		return out; //in;
	}



	protected void writePNG( BufferedImage Image3D, String label){
		try {
			String dateString = loggingFilenameDateFormat.format(new Date());
			String filename = "stereopic-" + label + "-" + dateString + ".png";

			String homeDir = System.getProperty("user.dir");
			if (Image3D != null) {
				ImageIO.write(Image3D, "png", new File(homeDir, filename));
				System.out.println("logged: " + homeDir + " " + filename);
			} else {
				System.out.println("null: not logged: " + homeDir + " " + filename);
			}


		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}




	@Override
	public void update(Observable o, Object arg) {
		initFilter();
	}


	protected void addToAssociation(BinocularEvent e){
		if (firstRun) {
			firstRun = false;
			resetFilter();
		}

		int leftOrRight = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;
		int halfcolumnheight = columnheight/2;

		if (leftOrRight == LEFT) {


			if((e.x==leftColumnX)&&((e.y-halfcolumnheight)>0)&&((e.y+halfcolumnheight)<retinaSize)){
				//int index = e.y*retinaSize+e.x;
				// filtering indexes to prevent memory overflow
				// we only take every 9 pixels

				// if(index%skipPixels==0){

				//store left time
				leftPoints.add(new TimePixel(e.x,e.y,e.timestamp));

				Vector<TimePixel> toTrim = new Vector<TimePixel>();
				for(TimePixel tp:rightPoints){

					// associate with last right events
					if((e.timestamp-tp.time)<timeWindow){
						//  associations[index][tp.index]++;


						int xr = tp.index%retinaSize;

						// ryad constraint on order
						// if(e.x<xr){

						int yr = Math.round(tp.index/retinaSize);

						// if in one of the four columns
						//    int columnsize = Math.round((retinaSize-e.x)/5);

						if((xr>e.x)&&(xr<(e.x+columnsize))&&(yr>(e.y-halfcolumnheight))&&(yr<(e.y+halfcolumnheight))&&((yr-e.y)>0)&&(((yr-e.y)+columnheight)<retinaSize)){


							pixColumns[e.y][xr-e.x][yr-e.y]++;

						}
						//
						//                           if (xr == e.x + columnsize) {
						//                                 pixColumns[e.y][yr][0]++;
						//                           } else if (xr == e.x + columnsize * 2) {
						//                              pixColumns[e.y][yr][1]++;
						//                           } else if (xr == e.x + columnsize * 3) {
						//                               pixColumns[e.y][yr][2]++;
						//                           } else if (xr == e.x + columnsize * 4) {
						//                               pixColumns[e.y][yr][3]++;
						//                           }

						//                        if (xr == rightColumnX1) {
						//                                 pixColumns[e.y][yr][0]++;
						//                           } else if (xr == rightColumnX2) {
						//                              pixColumns[e.y][yr][1]++;
						//                           } else if (xr == rightColumnX3) {
						//                               pixColumns[e.y][yr][2]++;
						//                           } else if (xr == rightColumnX4) {
						//                               pixColumns[e.y][yr][3]++;
						//                           }





						// } // end if(e.x>xr)
					} else {

						// trim
						toTrim.add(tp);

					}

				}
				// trim
				rightPoints.removeAll(toTrim);


			}
		} else { // if RIGHT
			//store left time
			rightPoints.add(new TimePixel(e.x,e.y,e.timestamp));

			// int index = e.y*retinaSize+e.x;
			// int index = e.y*retinaSize+e.x;



			Vector<TimePixel> toTrim = new Vector<TimePixel>();
			for(TimePixel tp:leftPoints){

				// associate with last left events
				if((e.timestamp-tp.time)<timeWindow){
					//  associations[tp.index][index]++;
					// ryad constraint on order
					int xL = tp.index%retinaSize;
					// if(xL<e.x&&xL==leftColumnX){
					if(xL==leftColumnX){

						int yL = Math.round(tp.index/retinaSize);
						//  int columnsize = Math.round((retinaSize-xL)/5);
						// if in one of the four columns
						if((e.x>xL)&&(e.x<(xL+columnsize))&&(e.y>(yL-halfcolumnheight))&&(e.y<(yL+halfcolumnheight))&&((e.y-yL)>0)&&(((e.y-yL)+columnheight)<retinaSize)){

							pixColumns[yL][e.x-xL][e.y-yL]++;

						}

						//                           if (e.x == xL + columnsize) {
						//                                 pixColumns[xL][e.y][0]++;
						//                           } else if (e.x == xL + columnsize * 2) {
						//                              pixColumns[xL][e.y][1]++;
						//                           } else if (e.x == xL + columnsize * 3) {
						//                               pixColumns[xL][e.y][2]++;
						//                           } else if (e.x == xL + columnsize * 4) {
						//                               pixColumns[xL][e.y][3]++;
						//                           }

						//                          if (e.x == rightColumnX1) {
						//                                 pixColumns[yL][e.y][0]++;
						//                           } else if (e.x == rightColumnX2) {
						//                              pixColumns[yL][e.y][1]++;
						//                           } else if (e.x == rightColumnX3) {
						//                               pixColumns[yL][e.y][2]++;
						//                           } else if (e.x == rightColumnX4) {
						//                               pixColumns[yL][e.y][3]++;
						//                           }

					}
				} else {

					// trim
					toTrim.add(tp);

				}

			}
			// trim
			leftPoints.removeAll(toTrim);

		}
	}

	int closestAssociation( int index){
		int newindex = index;

		boolean decrease = true;
		int nbtry = 0;
		int maxNbtry = 10000000;
		// boolean found=false;
		while(nbtry<maxNbtry){
			nbtry++;
			if(newindex>=retinaSize){
				return index;
			}
			if(maxForArray(pixColumns[newindex])>0){
				// found = true;
				return newindex;
			}

			if (decrease) {
				newindex = newindex-1;
			}
			else {
				newindex = newindex+1;
			}
			if(newindex<0){
				decrease=false;
				newindex=index+1;
			}



		}


		return newindex;

	}

	int indexOfMaxValue(int[][] array, int j){
		int maxres = 0;
		int maxind = -1;
		for( int i=0;i<array.length;i++){
			if(array[i][j]>maxres){
				maxres = array[i][j];
				maxind = i;
			}
		}
		return maxind;
	}

	int indexOfMaxValue(int[] array ){
		int maxres = 0;
		int maxind = -1;
		for( int i=0;i<array.length;i++){
			if(array[i]>maxres){
				maxres = array[i];
				maxind = i;
			}
		}
		return maxind;
	}


	int maxForArray( int[][] array){
		int maxres = 0;
		for (int[] element : array) {
			for (int element2 : element) {
				if(element2>maxres){
					maxres = element2;
				}
			}
		}
		return maxres;
	}

	int maxForArray( int[] array){
		int maxres = 0;
		for (int element : array) {
			if(element>maxres){
				maxres = element;
			}
		}
		return maxres;
	}

	int maxAssociation( Hashtable row){
		int maxres = 0;

		Enumeration rowKeys = row.keys();


		while( rowKeys.hasMoreElements()) {
			Integer index = (Integer)rowKeys.nextElement();
			Integer value = (Integer)row.get(index);
			int f = value.intValue();
			if(f>maxres){
				maxres = f;
			}

		}



		return maxres;
	}

	// show 2D view

	void checkAssociationDisplayFrame(){
		if(showWindow1 && (associationdisplayFrame==null)) {
			createAssociationDisplayFrame();
		}
	}

	JFrame associationdisplayFrame=null;
	GLCanvas associationdisplayCanvas=null;
	BufferedImage leftImage3DOpenGL=null;

	private static final GLU glu = new GLU();


	//    GLUT glut=null;
	void createAssociationDisplayFrame(  ){
		associationdisplayFrame=new JFrame("Left-Right Associations");
		int iZoom = Math.round(intensityZoom);
		if(iZoom==0) {
			iZoom=1;
		}

		associationdisplayFrame.setPreferredSize(new Dimension(retinaSize*iZoom,retinaSize*iZoom));
		associationdisplayFrame.setSize(new Dimension(retinaSize*iZoom,retinaSize*iZoom));
		associationdisplayFrame.setMaximumSize(new Dimension(retinaSize*iZoom,retinaSize*iZoom));

		associationdisplayCanvas=new GLCanvas();

		associationdisplayCanvas.addKeyListener( new KeyListener(){
			/** Handle the key typed event from the text field. */
			@Override
			public void keyTyped(KeyEvent e) {

			}

			@Override
			public void keyPressed(KeyEvent e) {

			}

			@Override
			public void keyReleased(KeyEvent e) {




				if(e.getKeyCode()==KeyEvent.VK_L){
					// log all grativity points to a png image
					logLeftAccPNG = true;
					System.out.println("AssociationDisplayFrame:  logLeftAccPNG: "+logLeftAccPNG);
					//associationdisplayCanvas.display();

				}



			}
		});

		associationdisplayCanvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {

				int dx=associationdisplayCanvas.getWidth()-1;
				int dy=associationdisplayCanvas.getHeight()-1;

				// 3 is window's border width

				int x = (int)((evt.getX()-3)  / intensityZoom);
				int y = (int)((dy-evt.getY())  / intensityZoom);

				// int x = Math.round((evt.getX()-3)  / intensityZoom);
				// int y = Math.round((dy-evt.getY())  / intensityZoom);


				//  System.out.println("got x:"+x+" y:"+y+" from ["+evt.getX()+","+evt.getY()+"]");

				if (evt.getButton()==1){
					// if distinguishing button is important ...
				}

				associationdisplayCanvas.display();

			}
			@Override
			public void mouseReleased(MouseEvent e){


			}
		});


		associationdisplayCanvas.addGLEventListener(new GLEventListener(){
			@Override
			public void init(GLAutoDrawable drawable) {
			}

			private void drawAssociationsPoints(int[][] points, GL2 gl) {
				//  private void drawAllPoints(Hashtable associations, GL2 gl) {

				//    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);

				gl.glClearColor(0, 0, 0, 0);
				//  System.out.println("drawAllPoints: maxDepth: "+maxDepth+" ,minDepth : "+minDepth);

				// System.out.println("points.length: "+points.length+" points[i].length: "+points[0].length);
				for(int i=0;i<points.length;i++){

					for(int j=0;j<points[i].length;j++){
						// getString index fomr trackers
						int index = points[i][j];

						if(index!=0){ // all should be initialized to -1 but let's start like this
							gl.glColor3f(1, 1, 1);
							gl.glRectf(Math.round(i * intensityZoom), Math.round(index * intensityZoom), Math.round(i* intensityZoom)+1, Math.round(index  * intensityZoom)+1);
						}


					}


				}

			}



			void grabImage( GLAutoDrawable d ) {

				System.out.println("grab left image :  logLeftAccPNG: "+logLeftAccPNG);

				GL2 gl = d.getGL().getGL2();
				int width = 128; //d.getWidth();
				int height = 128; //d.getHeight();

				// Allocate a buffer for the pixels
				ByteBuffer rgbData = Buffers.newDirectByteBuffer(width * height * 3);

				// Set up the OpenGL state.
				gl.glReadBuffer(GL.GL_FRONT);
				gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);

				// Read the pixels into the ByteBuffer
				gl.glReadPixels(0,
					0,
					width,
					height,
					GL.GL_RGB,
					GL.GL_UNSIGNED_BYTE,
					rgbData);

				// Allocate space for the converted pixels
				int[] pixelInts = new int[width * height];

				// Convert RGB bytes to ARGB ints with no transparency. Flip
				// imageOpenGL vertically by reading the rows of pixels in the byte
				// buffer in reverse - (0,0) is at bottom left in OpenGL.

				int p = width * height * 3; // Points to first byte (red) in each row.
				int q;                  	// Index into ByteBuffer
				int i = 0;                 // Index into target int[]
				int bytesPerRow = width*3; // Number of bytes in each row

				for (int row = height - 1; row >= 0; row--) {
					p = row * bytesPerRow;
					q = p;
					for (int col = 0; col < width; col++) {
						int iR = rgbData.get(q++);
						int iG = rgbData.get(q++);
						int iB = rgbData.get(q++);

						pixelInts[i++] = ( (0xFF000000)
							| ((iR & 0xFF) << 16)
							| ((iG & 0xFF) << 8)
							| (iB & 0xFF) );
					}
				}

				// Set the data for the BufferedImage
				if((leftImage3DOpenGL==null) || (leftImage3DOpenGL.getWidth()!=width) || (leftImage3DOpenGL.getHeight()!=height)) {
					leftImage3DOpenGL = new BufferedImage(width,height, BufferedImage.TYPE_INT_ARGB);
				}
				leftImage3DOpenGL.setRGB(0, 0, width, height, pixelInts, 0, width);
			}



			@Override
			synchronized public void display(GLAutoDrawable drawable) {

				GL2 gl=drawable.getGL().getGL2();
				gl.glLoadIdentity();
				//gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
				gl.glClearColor(0,0,0,0);
				gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
				int font = GLUT.BITMAP_HELVETICA_12;



				synchronized (pixColumns) {
					//drawAssociationsPoints(pixColumns,gl);
				}



				if(logLeftAccPNG){
					grabImage(drawable);
					accLeftLogged=true;
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
				gl.glOrtho(0,drawable.getWidth(),0,drawable.getHeight(),0,1);
				// gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
				gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
				gl.glViewport(0,0,width,height);
			}

			public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
			}

			@Override
			public void dispose(GLAutoDrawable arg0) {
				// TODO Auto-generated method stub

			}
		});
		associationdisplayFrame.getContentPane().add(associationdisplayCanvas);
		associationdisplayFrame.pack();
		associationdisplayFrame.setVisible(true);
	}


	// show 2D view right

	void checkLeftDisplayFrame(){
		if(showWindow2 && (leftdisplayFrame==null)) {
			createLeftDisplayFrame();
		}
	}

	JFrame leftdisplayFrame=null;
	GLCanvas leftdisplayCanvas=null;
	BufferedImage rightImage3DOpenGL=null;
	//   private static final GLU glu = new GLU();


	//    GLUT glut=null;
	void createLeftDisplayFrame(  ){
		leftdisplayFrame=new JFrame("Display LEFT Frame");
		int iZoom = Math.round(intensityZoom);
		if(iZoom==0) {
			iZoom=1;
		}

		leftdisplayFrame.setPreferredSize(new Dimension(retinaSize*iZoom,retinaSize*iZoom));
		leftdisplayFrame.setSize(new Dimension(retinaSize*iZoom,retinaSize*iZoom));
		leftdisplayFrame.setMaximumSize(new Dimension(retinaSize*iZoom,retinaSize*iZoom));


		leftdisplayCanvas=new GLCanvas();

		leftdisplayCanvas.addKeyListener( new KeyListener(){
			/** Handle the key typed event from the text field. */
			@Override
			public void keyTyped(KeyEvent e) {

			}

			@Override
			public void keyPressed(KeyEvent e) {

			}

			@Override
			public void keyReleased(KeyEvent e) {




				if(e.getKeyCode()==KeyEvent.VK_L){
					// log all grativity points to a png image
					logRightAccPNG = true;
					System.out.println("LeftDisplayFrame:  logRightAccPNG: "+logRightAccPNG);
					//leftdisplayCanvas.display();

				}



			}
		});

		leftdisplayCanvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {



				if (evt.getButton()!=1){
					// if distinguishing button is important ...
					highlightIndex = -1;
					//                System.out.println("reset highlight");
				} else {
					//int dx = leftdisplayCanvas.getWidth() - 1;
					int dy = leftdisplayCanvas.getHeight() - 1;

					// 3 is window's border width

					int x = (int) ((evt.getX() - 3) / intensityZoom2);
					int y = (int) ((dy - evt.getY()) / intensityZoom2);


					highlightIndex = y; // * retinaSize + x;
					//System.out.println("got x:" + x + " y:" + y + " from [" + evt.getX() + "," + evt.getY() + "]");
					//System.out.println(" got index: "+highlightIndex);

					// check here is rights association is null
					// if null find closest one that is not null
					highlightIndex = closestAssociation(highlightIndex);
					//System.out.println(" new index: "+highlightIndex);


				}
				leftdisplayCanvas.display();
				rightdisplayCanvas.display();

			}
			@Override
			public void mouseReleased(MouseEvent e){


			}
		});


		leftdisplayCanvas.addGLEventListener(new GLEventListener(){
			@Override
			public void init(GLAutoDrawable drawable) {
			}

			/*
               private void drawAllPoints(float[][] points, GL2 gl) {

                    //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);

                    gl.glClearColor(0, 0, 0, 0);

                    for (int i = 0; i < retinaSize; i++) {
                        for (int j = 0; j < retinaSize; j++) {
                            gl.glColor3f(points[i][j], points[i][j], points[i][j]);
                            gl.glRectf(i * intensityZoom, j * intensityZoom, (i + 1) * intensityZoom, (j + 1) * intensityZoom);


                        }
                    }



            }

			 */

			private void drawFittingLineL( int[][] points, int xL, int yL, float r, float g, float b, GL2 gl){

				// debug

				// find regression
				int n = 0;
				// compute sums
				float sumX = 0;
				float sumX2 = 0;
				float sumY = 0;
				float sumXY = 0;


				for (int x=0;x<points.length;x++){
					int y = indexOfMaxValue(points[x]);
					if(y!=-1){
						//if(points[x][y]!=0){
						for(int k=0;k<points[x][y];k++){
							//debug
							//System.out.println("draw line: y: "+y);
							n++;

							sumX+=x;
							sumX2+=x*x;
							sumY+=y;
							sumXY+=x*y;
						}
					}
				}


				if(sumX!=0){
					float m = ((sumX*sumY) - (n*sumXY)) / ( (sumX*sumX) - (n*sumX2));
					float bx = ((sumX*sumXY) - (sumY*sumX2)) / ( (sumX*sumX) - (n*sumX2));

					// create two points
					float x1 = -retinaSize;
					float y1 = bx+(m*x1);
					float x2 = 2*retinaSize;
					float y2 = bx+(m*x2);

					// System.out.println("draw line: x1: "+x1+" y1: "+y1);
					// System.out.println("draw line: x2: "+x2+" y2: "+y2);

					gl.glColor3f(r, g, b);
					gl.glLineWidth( intensityZoom2 );
					gl.glBegin(GL2.GL_LINE_LOOP);
					{
						gl.glVertex2f((x1+xL) * intensityZoom2, (y1+yL) * intensityZoom2);
						gl.glVertex2f((x2+xL) * intensityZoom2, (y2+yL) * intensityZoom2);

					}
					gl.glEnd();
					gl.glLineWidth( 1.0f );
				}
				// plot


			}


			private void drawRightPoints(int[][][] points, GL2 gl) {

				//    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);

				gl.glClearColor(0, 0, 0, 0);
				//  System.out.println("drawAllPoints: maxDepth: "+maxDepth+" ,minDepth : "+minDepth);

				//   System.out.println("drawAllPoints right");
				int macolumnsize = retinaSize;
				if((highlightIndex>-1)&&(highlightIndex<macolumnsize)){
					//int xL = highlightIndex%retinaSize;
					//int yL = Math.round(highlightIndex/retinaSize);
					int yL = highlightIndex;
					int xL = leftColumnX;

					int halfcolumnheight = columnheight/2;
					// int columnsize = Math.round((retinaSize-xL)/5);
					for(int j=0;j<columnsize;j++){
						int ymax = indexOfMaxValue(points[highlightIndex][j]);
						for(int yr=0;yr<points[highlightIndex][j].length;yr++){


							//getString x,y


							int v = points[highlightIndex][j][yr];
							// should normalize
							if(yr==ymax){
								gl.glColor3f(v, 0, 0);
								gl.glRectf(Math.round((j+xL) * intensityZoom2)+2, Math.round(((yr+yL)-halfcolumnheight) * intensityZoom2), Math.round((j+xL)* intensityZoom2)+2+intensityZoom2, Math.round(((yr+yL)-halfcolumnheight)  * intensityZoom2)+1+intensityZoom2);

							} else {
								gl.glColor3f(v/factor, v/factor, v/factor);
								gl.glRectf(Math.round((j+xL) * intensityZoom2)+2, Math.round(((yr+yL)-halfcolumnheight) * intensityZoom2), Math.round((j+xL)* intensityZoom2)+2+intensityZoom2, Math.round(((yr+yL)-halfcolumnheight)  * intensityZoom2)+1+intensityZoom2);


							}
						}
					}

					//
					drawFittingLineL(points[highlightIndex],xL,yL-halfcolumnheight,1,1,1,gl);

				}

			}


			private void drawLeftPoints(int[][][] points, GL2 gl) {

				//    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);

				gl.glClearColor(0, 0, 0, 0);
				//  System.out.println("drawAllPoints: maxDepth: "+maxDepth+" ,minDepth : "+minDepth);

				for(int i=0;i<points.length;i++){

					// GET MAX
					//getString x,y
					//     int xL = i%retinaSize;;
					//     int yL = Math.round(i/retinaSize);
					// now becaues only one colukmn for left :
					int yL = i;
					int xL = leftColumnX;

					if(maxForArray(points[i])!=0){ // all should be initialized to -1 but let's start like this
						// if(points[i][currentTrackers[i]]!=0){
						// if(nbEventsPerPixel[i]!=0){

						float size = intensityZoom2;
						if (i == highlightIndex) {
							gl.glColor3f(0, 0, 1);
							size = 2*intensityZoom2;
						} else {


							gl.glColor3f(1, 1, 1);

						}
						gl.glRectf(Math.round(xL * intensityZoom2), Math.round(yL * intensityZoom2), Math.round(xL * intensityZoom2) + size, Math.round(yL * intensityZoom2) + size);
					}

				}

			}



			void grabImage( GLAutoDrawable d ) {

				System.out.println("grab right image :  logRightAccPNG: "+logRightAccPNG);


				GL2 gl = d.getGL().getGL2();
				int width = 128; //d.getWidth();
				int height = 128; //d.getHeight();

				// Allocate a buffer for the pixels
				ByteBuffer rgbData = Buffers.newDirectByteBuffer(width * height * 3);

				// Set up the OpenGL state.
				gl.glReadBuffer(GL.GL_FRONT);
				gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);

				// Read the pixels into the ByteBuffer
				gl.glReadPixels(0,
					0,
					width,
					height,
					GL.GL_RGB,
					GL.GL_UNSIGNED_BYTE,
					rgbData);

				// Allocate space for the converted pixels
				int[] pixelInts = new int[width * height];

				// Convert RGB bytes to ARGB ints with no transparency. Flip
				// imageOpenGL vertically by reading the rows of pixels in the byte
				// buffer in reverse - (0,0) is at bottom left in OpenGL.

				int p = width * height * 3; // Points to first byte (red) in each row.
				int q;                  	// Index into ByteBuffer
				int i = 0;                 // Index into target int[]
				int bytesPerRow = width*3; // Number of bytes in each row

				for (int row = height - 1; row >= 0; row--) {
					p = row * bytesPerRow;
					q = p;
					for (int col = 0; col < width; col++) {
						int iR = rgbData.get(q++);
						int iG = rgbData.get(q++);
						int iB = rgbData.get(q++);

						pixelInts[i++] = ( (0xFF000000)
							| ((iR & 0xFF) << 16)
							| ((iG & 0xFF) << 8)
							| (iB & 0xFF) );
					}
				}

				// Set the data for the BufferedImage
				if((rightImage3DOpenGL==null) || (rightImage3DOpenGL.getWidth()!=width) || (rightImage3DOpenGL.getHeight()!=height)) {
					rightImage3DOpenGL = new BufferedImage(width,height, BufferedImage.TYPE_INT_ARGB);
				}
				rightImage3DOpenGL.setRGB(0, 0, width, height, pixelInts, 0, width);
			}

			@Override
			synchronized public void display(GLAutoDrawable drawable) {

				GL2 gl=drawable.getGL().getGL2();
				gl.glLoadIdentity();
				//gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
				gl.glClearColor(0,0,0,0);
				gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
				int font = GLUT.BITMAP_HELVETICA_12;

				synchronized (pixColumns) {
					drawLeftPoints(pixColumns,gl);
					if(highlightIndex!=-1){
						drawRightPoints(pixColumns,gl);

					}
				}

				if(logRightAccPNG){
					grabImage(drawable);
					accRightLogged=true;
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
				// final int B=10;
				gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
				gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
				//   gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
				gl.glOrtho(0,drawable.getWidth(),0,drawable.getHeight(),0,1);
				gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
				gl.glViewport(0,0,width,height);


			}

			public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
			}

			@Override
			public void dispose(GLAutoDrawable arg0) {
				// TODO Auto-generated method stub

			}
		});
		leftdisplayFrame.getContentPane().add(leftdisplayCanvas);
		leftdisplayFrame.pack();
		leftdisplayFrame.setVisible(true);
	}

	// accurate pixel frame

	// show epipolar lines on right view

	void checkRightDisplayFrame(){
		if(showWindow3 && (rightdisplayFrame==null)) {
			createRightDisplayFrame();
		}
	}

	JFrame rightdisplayFrame=null;
	GLCanvas rightdisplayCanvas=null;
	//  BufferedImage rightImage3DOpenGL=null;
	//   private static final GLU glu = new GLU();


	//    GLUT glut=null;
	void createRightDisplayFrame(  ){
		rightdisplayFrame=new JFrame("Display RIGHT Frame");
		int iZoom = Math.round(intensityZoom);
		if(iZoom==0) {
			iZoom=1;
		}

		rightdisplayFrame.setPreferredSize(new Dimension(retinaSize*iZoom,retinaSize*iZoom));
		rightdisplayFrame.setSize(new Dimension(retinaSize*iZoom,retinaSize*iZoom));
		rightdisplayFrame.setMaximumSize(new Dimension(retinaSize*iZoom,retinaSize*iZoom));


		rightdisplayCanvas=new GLCanvas();

		rightdisplayCanvas.addKeyListener( new KeyListener(){
			/** Handle the key typed event from the text field. */
			@Override
			public void keyTyped(KeyEvent e) {

			}

			@Override
			public void keyPressed(KeyEvent e) {

			}

			@Override
			public void keyReleased(KeyEvent e) {




				if(e.getKeyCode()==KeyEvent.VK_L){
					// log all grativity points to a png image
					logRightAccPNG = true;
					System.out.println("RightDisplayFrame:  logRightAccPNG: "+logRightAccPNG);
					//rightdisplayCanvas.display();

				}



			}
		});

		rightdisplayCanvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {

				int dx=rightdisplayCanvas.getWidth()-1;
				int dy=rightdisplayCanvas.getHeight()-1;

				// 3 is window's border width

				int x = (int)((evt.getX()-3)  / intensityZoom);
				int y = (int)((dy-evt.getY())  / intensityZoom);


				//   highlightY = y;
				//   System.out.println("got x:"+x+" y:"+y+" from ["+evt.getX()+","+evt.getY()+"]");

				if (evt.getButton()==1){
					// if distinguishing button is important ...
					//  highlightY = -1;
					//  System.out.println("reset highlight");
				}

				rightdisplayCanvas.display();

			}
			@Override
			public void mouseReleased(MouseEvent e){


			}
		});


		rightdisplayCanvas.addGLEventListener(new GLEventListener(){
			@Override
			public void init(GLAutoDrawable drawable) {
			}

			/*
               private void drawAllPoints(float[][] points, GL2 gl) {

                    //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);

                    gl.glClearColor(0, 0, 0, 0);

                    for (int i = 0; i < retinaSize; i++) {
                        for (int j = 0; j < retinaSize; j++) {
                            gl.glColor3f(points[i][j], points[i][j], points[i][j]);
                            gl.glRectf(i * intensityZoom, j * intensityZoom, (i + 1) * intensityZoom, (j + 1) * intensityZoom);


                        }
                    }



            }

			 */

			private void drawFittingLineR( int[][] points, int xL, int yL, float r, float g, float b, GL2 gl){

				// debug

				// find regression
				int n = 0;
				// compute sums
				float sumX = 0;
				float sumX2 = 0;
				float sumY = 0;
				float sumXY = 0;


				for (int x=0;x<points.length;x++){
					int y = indexOfMaxValue(points[x]);
					if(y!=-1){
						//if(points[x][y]!=0){
						for(int k=0;k<points[x][y];k++){
							//debug
							//System.out.println("draw line: y: "+y);
							n++;

							sumX+=x;
							sumX2+=x*x;
							sumY+=y;
							sumXY+=x*y;
						}
					}
				}


				if(sumX!=0){
					float m = ((sumX*sumY) - (n*sumXY)) / ( (sumX*sumX) - (n*sumX2));
					float bx = ((sumX*sumXY) - (sumY*sumX2)) / ( (sumX*sumX) - (n*sumX2));

					// create two points
					float x1 = -retinaSize;
					float y1 = bx+(m*x1);
					float x2 = 2*retinaSize;
					float y2 = bx+(m*x2);

					// System.out.println("draw line: x1: "+x1+" y1: "+y1);
					// System.out.println("draw line: x2: "+x2+" y2: "+y2);

					gl.glColor3f(r, g, b);

					gl.glBegin(GL2.GL_LINE_LOOP);
					{
						gl.glVertex2f((x1+xL) * intensityZoom2, (y1+yL) * intensityZoom2);
						gl.glVertex2f((x2+xL) * intensityZoom2, (y2+yL) * intensityZoom2);

					}
					gl.glEnd();

				}
				// plot


			}


			private void drawFittingLine( int[][] points, GL2 gl){

				// debug

				// find regression
				int n = 0;
				// compute sums
				float sumX = 0;
				float sumX2 = 0;
				float sumY = 0;
				float sumXY = 0;


				for (int x=0;x<points.length;x++){
					int y = indexOfMaxValue(points[x]);
					//if(points[x][y]!=0){
					for(int k=0;k<points[x][y];k++){
						n++;

						sumX+=x;
						sumX2+=x*x;
						sumY+=y;
						sumXY+=x*y;

					}
				}


				if(sumX!=0){
					float m = ((sumX*sumY) - (n*sumXY)) / ( (sumX*sumX) - (n*sumX2));
					float b = ((sumX*sumXY) - (sumY*sumX2)) / ( (sumX*sumX) - (n*sumX2));

					// create two points
					float x1 = 0;
					float y1 = b;
					float x2 = retinaSize;
					float y2 = b+(m*x2);

					gl.glColor3f(1, 1, 1);

					gl.glBegin(GL2GL3.GL_LINE);
					{
						gl.glVertex2f(x1, y1);
						gl.glVertex2f(x2, y2);

					}
					gl.glEnd();

				}
				// plot


			}




			private void drawFittingLine( int[] points, GL2 gl){

				// debug
				//int[] points2 =
				// find regression
				int n = 0;
				// compute sums
				float sumX = 0;
				float sumX2 = 0;
				float sumY = 0;
				float sumXY = 0;

				for (int ind : points) {
					if(ind!=0){
						n++;
						int x = ind%retinaSize;
						int y = Math.round(ind/retinaSize);

						sumX+=x;
						sumX2+=x*x;
						sumY+=y;
						sumXY+=x*y;

					}
				}

				if(sumX!=0){
					float m = ((sumX*sumY) - (n*sumXY)) / ( (sumX*sumX) - (n*sumX2));
					float b = ((sumX*sumXY) - (sumY*sumX2)) / ( (sumX*sumX) - (n*sumX2));

					// create two points
					float x1 = 0;
					float y1 = b;
					float x2 = retinaSize;
					float y2 = b+(m*x2);

					gl.glColor3f(1, 1, 1);
					// gl.glRectf(x1 * intensityZoom2, y1 * intensityZoom2, x2* intensityZoom2, y2  * intensityZoom2);

					gl.glBegin(GL2GL3.GL_LINE);
					{
						gl.glVertex2f(x1, y1);
						gl.glVertex2f(x2, y2);

					}
					gl.glEnd();

				}
				// plot


			}


			private void drawRightPoints(int[][][] points, GL2 gl) {

				//    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);

				gl.glClearColor(0, 0, 0, 0);
				//  System.out.println("drawAllPoints: maxDepth: "+maxDepth+" ,minDepth : "+minDepth);

				//   System.out.println("drawAllPoints right");
				int macolumnsize = retinaSize;
				int halfcolumnheight = columnheight/2;
				int yL = highlightIndex;
				int xL = leftColumnX;
				if((highlightIndex>-1)&&(highlightIndex<macolumnsize)){
					//int xL = highlightIndex%retinaSize;
					//int yL = Math.round(highlightIndex/retinaSize);


					// int columnsize = Math.round((retinaSize-xL)/5);

					for(int j=0;j<columnsize;j++){
						int ymax = indexOfMaxValue(points[highlightIndex][j]);
						for(int yr=0;yr<points[highlightIndex][j].length;yr++){


							//getString x,y


							int v = points[highlightIndex][j][yr];
							// should normalize
							if(yr==ymax){
								gl.glColor3f(v, 0, 0);
								gl.glRectf(Math.round((j+xL) * intensityZoom2), Math.round(((yr+yL)-halfcolumnheight) * intensityZoom2), Math.round((j+xL)* intensityZoom2)+1, Math.round(((yr+yL)-halfcolumnheight)  * intensityZoom2)+1);

							} else {
								gl.glColor3f(v/factor, v/factor, v/factor);
								gl.glRectf(Math.round((j+xL) * intensityZoom2), Math.round(((yr+yL)-halfcolumnheight) * intensityZoom2), Math.round((j+xL)* intensityZoom2)+1, Math.round(((yr+yL)-halfcolumnheight)  * intensityZoom2)+1);

							}
						}
					}
					drawFittingLineR(points[highlightIndex],xL,yL-halfcolumnheight,0,1,1,gl);


				}

				// draw sample lines all acrossc
				int yi = 10;
				while(yi<retinaSize){

					yL = closestAssociation(yi);
					//   yL = yi;
					//if(maxForArray(points[yL])>maxThreshold){

					drawFittingLineR(points[yL],xL,yL-halfcolumnheight,1,1,1,gl);
					yi +=5;
					// }
				}
				// save data


			}



			void grabImage( GLAutoDrawable d ) {

				System.out.println("grab right image :  logRightAccPNG: "+logRightAccPNG);


				GL2 gl = d.getGL().getGL2();
				int width = 128; //d.getWidth();
				int height = 128; //d.getHeight();

				// Allocate a buffer for the pixels
				ByteBuffer rgbData = Buffers.newDirectByteBuffer(width * height * 3);

				// Set up the OpenGL state.
				gl.glReadBuffer(GL.GL_FRONT);
				gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);

				// Read the pixels into the ByteBuffer
				gl.glReadPixels(0,
					0,
					width,
					height,
					GL.GL_RGB,
					GL.GL_UNSIGNED_BYTE,
					rgbData);

				// Allocate space for the converted pixels
				int[] pixelInts = new int[width * height];

				// Convert RGB bytes to ARGB ints with no transparency. Flip
				// imageOpenGL vertically by reading the rows of pixels in the byte
				// buffer in reverse - (0,0) is at bottom left in OpenGL.

				int p = width * height * 3; // Points to first byte (red) in each row.
				int q;                  	// Index into ByteBuffer
				int i = 0;                 // Index into target int[]
				int bytesPerRow = width*3; // Number of bytes in each row

				for (int row = height - 1; row >= 0; row--) {
					p = row * bytesPerRow;
					q = p;
					for (int col = 0; col < width; col++) {
						int iR = rgbData.get(q++);
						int iG = rgbData.get(q++);
						int iB = rgbData.get(q++);

						pixelInts[i++] = ( (0xFF000000)
							| ((iR & 0xFF) << 16)
							| ((iG & 0xFF) << 8)
							| (iB & 0xFF) );
					}
				}

				// Set the data for the BufferedImage
				if((rightImage3DOpenGL==null) || (rightImage3DOpenGL.getWidth()!=width) || (rightImage3DOpenGL.getHeight()!=height)) {
					rightImage3DOpenGL = new BufferedImage(width,height, BufferedImage.TYPE_INT_ARGB);
				}
				rightImage3DOpenGL.setRGB(0, 0, width, height, pixelInts, 0, width);
			}

			@Override
			synchronized public void display(GLAutoDrawable drawable) {

				GL2 gl=drawable.getGL().getGL2();
				gl.glLoadIdentity();
				//gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
				gl.glClearColor(0,0,0,0);
				gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
				int font = GLUT.BITMAP_HELVETICA_12;

				synchronized (pixColumns) {
					drawRightPoints(pixColumns,gl);
				}

				if(logRightAccPNG){
					grabImage(drawable);
					accRightLogged=true;
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
				// final int B=10;
				gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
				gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
				//   gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
				gl.glOrtho(0,drawable.getWidth(),0,drawable.getHeight(),0,1);
				gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
				gl.glViewport(0,0,width,height);


			}

			public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
			}

			@Override
			public void dispose(GLAutoDrawable arg0) {
				// TODO Auto-generated method stub

			}
		});
		rightdisplayFrame.getContentPane().add(rightdisplayCanvas);
		rightdisplayFrame.pack();
		rightdisplayFrame.setVisible(true);
	}



	/***********************************************************************************
	 * // drawing on player window
	 ********************************************************************************/

	public void annotate(Graphics2D g) {
	}

	protected void drawBoxCentered(GL2 gl, int x, int y, int sx, int sy){
		gl.glBegin(GL2.GL_LINE_LOOP);
		{
			gl.glVertex2i(x-sx,y-sy);
			gl.glVertex2i(x+sx,y-sy);
			gl.glVertex2i(x+sx,y+sy);
			gl.glVertex2i(x-sx,y+sy);
		}
		gl.glEnd();
	}

	protected void drawBox(GL2 gl, int x, int x2, int y, int y2){
		gl.glBegin(GL2.GL_LINE_LOOP);
		{
			gl.glVertex2i(x,y);
			gl.glVertex2i(x2,y);
			gl.glVertex2i(x2,y2);
			gl.glVertex2i(x,y2);
		}
		gl.glEnd();
	}

	@Override
	synchronized public void annotate(GLAutoDrawable drawable) {
		final float LINE_WIDTH=5f; // in pixels
		if(!isFilterEnabled()) {
			return;
		}


		GL2 gl=drawable.getGL().getGL2(); // when we getString this we are already set up with scale 1=1 pixel, at LL corner
		if(gl==null){
			log.warning("null GL in GravityCentersImageDumper.annotate");
			return;
		}
		float[] rgb=new float[4];
		gl.glPushMatrix();
		try{


			gl.glColor3f(0,0,1);
			drawBox(gl,leftColumnX,leftColumnX+1,0,128);



			// int columnsize = Math.round((retinaSize-leftColumnX)/5);
			gl.glColor3f(1, 0, 0);
			for(int k=0;k<nbColumns;k++){
				// if in one of the four columns
				// int x = leftColumnX + columnsize*(k+1);
				int x = columnsize*(k+1);


				drawBox(gl, leftColumnX, leftColumnX + columnsize, 60, 60+columnheight);

			}




		}catch(java.util.ConcurrentModificationException e){
			// this is in case cluster list is modified by real time filter during rendering of clusters
			log.warning(e.getMessage());
		}
		gl.glPopMatrix();
	}

	//    void drawGLCluster(int x1, int y1, int x2, int y2)

	/** annotate the rendered retina frame to show locations of clusters */
	synchronized public void annotate(float[][][] frame) {
		if(!isFilterEnabled()) {
			return;
		}
		// disable for now TODO
		if(chip.getCanvas().isOpenGLEnabled())
		{
			return; // done by open gl annotator
		}

	}

	public synchronized boolean isLogDataEnabled() {
		return logDataEnabled;
	}

	public synchronized void setLogDataEnabled(boolean logDataEnabled) {
		this.logDataEnabled = logDataEnabled;
		if(!logDataEnabled) {
			logStream.flush();
			logStream.close();
			logStream=null;
		}else{
			try{
				logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File("PawTrackerData.txt"))));
				logStream.println("# clusterNumber lasttimestamp x y avergeEventDistance");
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}



	//
	//    public void setSkipPixels(int skipPixels) {
	//        this.skipPixels = skipPixels;
	//
	//        getPrefs().putInt("OnlineCalibration4.skipPixels",skipPixels);
	//    }
	//
	//    public int getSkipPixels() {
	//        return skipPixels;
	//    }

	//    public void setCurrentTracker(int currentTracker) {
	//        this.currentTracker = currentTracker;
	//
	//        getPrefs().putInt("OnlineCalibration4.currentTracker",currentTracker);
	//    }
	//
	//    public int getCurrentTracker() {
	//        return currentTracker;
	//    }
	//


	public void setMaxThreshold(int maxThreshold) {
		this.maxThreshold = maxThreshold;

		getPrefs().putInt("OnlineCalibration4.maxThreshold",maxThreshold);
	}

	public int getMaxThreshold() {
		return maxThreshold;
	}
	//
	//      public void setCleaningThreshold(int cleaningThreshold) {
	//        this.cleaningThreshold = cleaningThreshold;
	//
	//        getPrefs().putInt("OnlineCalibration4.cleaningThreshold",cleaningThreshold);
	//    }
	//
	//    public int getCleaningThreshold() {
	//        return cleaningThreshold;
	//    }
	//
	//
	//
	//    public void setAccThreshold(int accThreshold) {
	//        this.accThreshold = accThreshold;
	//
	//        getPrefs().putInt("OnlineCalibration4.accThreshold",accThreshold);
	//    }
	//
	//    public int getAccThreshold() {
	//        return accThreshold;
	//    }

	public void setfactor(int factor) {
		this.factor = factor;

		getPrefs().putInt("OnlineCalibration4.factor",factor);
	}

	public int getfactor() {
		return factor;
	}

	public void setLeftColumnX(int leftColumnX) {
		this.leftColumnX = leftColumnX;
		resetColumns();
		getPrefs().putInt("OnlineCalibration4.leftColumnX",leftColumnX);
	}

	public int getLeftColumnX() {
		return leftColumnX;
	}

	public void setmaxProcessTimeNs(int maxProcessTimeNs) {
		this.maxProcessTimeNs = maxProcessTimeNs;

		getPrefs().putInt("OnlineCalibration4.maxProcessTimeNs",maxProcessTimeNs);
	}

	public int getmaxProcessTimeNs() {
		return maxProcessTimeNs;
	}

	public void setColumnsize(int columnsize) {
		this.columnsize = columnsize;
		resetColumns();
		getPrefs().putInt("OnlineCalibration4.columnsize",columnsize);
	}

	public int getColumnsize() {
		return columnsize;
	}


	public void setColumnheight(int columnheight) {
		this.columnheight = columnheight;
		resetColumns();
		getPrefs().putInt("OnlineCalibration4.columnheight",columnheight);
	}

	public int getColumnheight() {
		return columnheight;
	}

	public void setTimeWindow(int timeWindow) {
		this.timeWindow = timeWindow;

		getPrefs().putInt("OnlineCalibration4.timeWindow",timeWindow);
	}

	public int getTimeWindow() {
		return timeWindow;
	}

	public void setIntensityZoom(float intensityZoom) {
		this.intensityZoom = intensityZoom;

		getPrefs().putFloat("OnlineCalibration4.intensityZoom",intensityZoom);
	}

	public float getIntensityZoom() {
		return intensityZoom;
	}


	public void setIntensityZoom2(float intensityZoom2) {
		this.intensityZoom2 = intensityZoom2;

		getPrefs().putFloat("OnlineCalibration4.intensityZoom2",intensityZoom2);
	}

	public float getIntensityZoom2() {
		return intensityZoom2;
	}




	public void setShowWindow1(boolean showWindow1){
		this.showWindow1 = showWindow1;

		getPrefs().putBoolean("OnlineCalibration4.showWindow1",showWindow1);
	}
	public boolean isShowWindow1(){
		return showWindow1;
	}

	public void setShowWindow2(boolean showWindow2){
		this.showWindow2 = showWindow2;

		getPrefs().putBoolean("OnlineCalibration4.showWindow2",showWindow2);
	}
	public boolean isShowWindow2(){
		return showWindow2;
	}

	public void setShowWindow3(boolean showWindow3){
		this.showWindow3 = showWindow3;

		getPrefs().putBoolean("OnlineCalibration4.showWindow3",showWindow3);
	}
	public boolean isShowWindow3(){
		return showWindow3;
	}





}
