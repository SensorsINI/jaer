/*
 * DisplayFundamentalMatrix.java
 * This filters need to load the file fundamental.txt in order to perform
 * This file is obtain from calibration, it contains the fundametal matrix
 * this filter just display some epipolar lines computed from F
 * to check its validity
 * Paul Rogister, Created on May, 2010
 *
 */


package ch.unizh.ini.jaer.projects.stereo3D;

import java.awt.Graphics2D;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Observable;
import java.util.Observer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;


/**
 * StereoMatcherOnTime:
 * combine streams of events from two cameras into one single stream of 3D event
 *
 * @author rogister
 */
public class DisplayFundamentalMatrix extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {


	protected final int RIGHT = 1;
	protected final int LEFT = 0;
	//    protected final int NODISPARITY = -999;

	//  int minDiff = 14; // miniumpossible synchrony is 15 us
	protected AEChip chip;
	private AEChipRenderer renderer;

	private boolean logDataEnabled=false;
	private PrintStream logStream=null;



	private boolean swapRight = getPrefs().getBoolean("DisplayFundamentalMatrix.swapRight",true);
	private boolean useTranspose = getPrefs().getBoolean("DisplayFundamentalMatrix.useTranspose",true);


	private int left_x = getPrefs().getInt("DisplayFundamentalMatrix.left_x",64);
	private int left_y = getPrefs().getInt("DisplayFundamentalMatrix.left_y",64);

	boolean firstRun = true;


	private float[] fmatrix = new float[9];



	// global variables

	private int retinaSize=128;//getPrefs().getInt("GravityCentersImageDumper.retinaSize",128);




	/** Creates a new instance of GravityCentersImageDumper */
	public DisplayFundamentalMatrix(AEChip chip) {
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




	@Override
	public String toString(){
		String s="DisplayFundamentalMatrix";
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
			loadFundamentalFromTextFile("fundamental.txt");
			//System.out.println("fundamental.txt loaded");
		}
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

		firstRun = false;
		if(swapRight){
			int sx = chip.getSizeX();
			// invert symetry on x for left events so we can compare

			short tmp;
			for(Object o:in){
				BinocularEvent e = (BinocularEvent)o;
				int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;
				if(eye==RIGHT){
					e.x = (short)(sx - e.x - 1);
				}
			}
		}
		// System.out.println("nbevents: "+nbevents+" in.size: "+in.getSize());
		return in;

	}






	@Override
	public void update(Observable o, Object arg) {
		initFilter();
	}







	// d2=F'*[events_0(1,i)+1;events_0(2,i)+1;1];
	protected float[] computeEpipolarLine( int x, int y ){
		float line[] = new float[3];

		line[0] = (fmatrix[0]*x) + (fmatrix[1]*y) + fmatrix[2];
		line[1] = (fmatrix[3]*x) + (fmatrix[4]*y) + fmatrix[5];
		line[2] = (fmatrix[6]*x) + (fmatrix[7]*y) + fmatrix[8];



		return line;
	}

	protected float[] computeEpipolarLineTranspose( int x, int y ){
		float line[] = new float[3];



		line[0] = (fmatrix[0]*x) + (fmatrix[3]*y) + fmatrix[6];
		line[1] = (fmatrix[1]*x) + (fmatrix[4]*y) + fmatrix[7];
		line[2] = (fmatrix[2]*x) + (fmatrix[5]*y) + fmatrix[8];


		return line;
	}






	// load fundamental matrix

	private void loadFundamentalFromTextFile(String filename ) {
		try {
			//use buffering, reading one line at a time
			//FileReader always assumes default encoding is OK!
			BufferedReader input = new BufferedReader(new FileReader(filename));
			try {
				String line = null; //not declared within while loop

				// int x = 0;
				// int y = 0;
				float[] data = new float[9];
				int d=0;
				while((line = input.readLine()) != null) {
					String[] result = line.split("\\s");
					//System.out.println("parsing  = "+line);
					for (String element : result) {
						// store variables
						//  System.out.println("parsing input: "+i+" = "+result[i]);
						data[d] = Float.parseFloat(element);
						d++;
					}
				}

				// store
				fmatrix = data;

			} finally {
				input.close();
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}



	// replace by if at least two element far enough from grey level 0.5f
	protected float diffToGrey( float[][] farray ){
		float res = 0;

		// sum of elements
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				res += Math.abs(farray[i][j]-0.5f);
			}
		}

		return res;
	}






	/***********************************************************************************
	 * // drawing on player window
	 ********************************************************************************/

	public void annotate(Graphics2D g) {
	}

	protected void drawBoxCentered(GL2 gl, int x, int y, int sx, int sy){
		gl.glBegin(GL.GL_LINE_LOOP);
		{
			gl.glVertex2i(x-sx,y-sy);
			gl.glVertex2i(x+sx+1,y-sy);
			gl.glVertex2i(x+sx+1,y+sy+1);
			gl.glVertex2i(x-sx,y+sy+1);
		}
		gl.glEnd();
	}

	protected void drawBox(GL2 gl, int x, int x2, int y, int y2){
		gl.glBegin(GL.GL_LINE_LOOP);
		{
			gl.glVertex2i(x,y);
			gl.glVertex2i(x2,y);
			gl.glVertex2i(x2,y2);
			gl.glVertex2i(x,y2);
		}
		gl.glEnd();
	}

	protected void drawLine(GL2 gl, int x1, int y1, int x2, int y2){
		gl.glBegin(GL.GL_LINE_LOOP);
		{
			gl.glVertex2i(x1,y1);
			gl.glVertex2i(x2,y2);

		}
		gl.glEnd();
	}

	@Override
	synchronized public void annotate(GLAutoDrawable drawable) {
		final float LINE_WIDTH=5f; // in pixels
		if (!isFilterEnabled()) {
			return;
		}


		GL2 gl = drawable.getGL().getGL2(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
			if (gl == null) {
				log.warning("null GL in GravityCentersImageDumper.annotate");
				return;
			}

			gl.glColor3f(1, 0, 1);

			float[] line;
			//   for(int i=5;i<retinaSize;i+=5){
			if (useTranspose) {
				line = computeEpipolarLineTranspose(left_x, left_y);
			} else {
				line = computeEpipolarLine(left_x, left_y);
			}
			int iy1 = Math.round(-(line[2]) / line[1]);
			int iy2 = Math.round(-((line[0] * 127) + line[2]) / line[1]);
			drawLine(gl, 0, iy1, 127, iy2);
			//   }

			gl.glPushMatrix();

			gl.glPopMatrix();
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

	public void setswapRight(boolean swapRight){
		this.swapRight = swapRight;
		getPrefs().putBoolean("DisplayFundamentalMatrix.swapRight",swapRight);
	}
	public boolean isswapRight(){
		return swapRight;
	}

	public void setUseTranspose(boolean useTranspose){
		this.useTranspose = useTranspose;
		getPrefs().putBoolean("DisplayFundamentalMatrix.useTranspose",useTranspose);
	}
	public boolean isUseTranspose(){
		return useTranspose;
	}



	public int getLeft_x() {
		return left_x;
	}

	public void setLeft_x(int left_x) {
		this.left_x = left_x;
		getPrefs().putInt("DisplayFundamentalMatrix.left_x",left_x);
	}
	public int getLeft_y() {
		return left_y;
	}

	public void setLeft_y(int left_y) {
		this.left_y = left_y;
		getPrefs().putInt("DisplayFundamentalMatrix.left_y",left_y);
	}
}
