package ch.unizh.ini.jaer.projects.opticalflow;

import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import ch.unizh.ini.jaer.hardware.pantilt.PanTiltParserPanel;

/** Integrates consecutive MotionData objects to track total displacement
 *
 * This class keeps track of all received frame data containing measured
 * optical flow. It can then be used to extract the total offset since the
 * last <code>reset()</code>. Besides filtering and processing the received
 * global motion data for an optimal result, this class also keeps track of
 * all "off screen" pixels that should be drawn by
 * {@link ch.unizh.ini.jaer.projects.opticalflow.graphics.OpticalFlowDisplayMethod}
 *
 * <br /><br />
 *
 * Remarks about coordinates (in pixel <code>buffer</code> as well as <code>currentX/Y()</code>
 * and <code>x1,y1,x2,y2</code>)
 *
 * <ul>
 * <li> rounded to the next integer using the internal method
 *  <code>round</code>. The only place where <code>float</code> coordinates are
 *  used is in the internal tracking of the current coordinate.
 * </li>
 * <li> relative to the same initial top-left
 *  corner of the first frame. Because the coordinate system is <b>translated</b>
 *  by the <code>currentX/Y()</code> (see {@link #glTransform(com.jogamp.opengl.GL)}),
 *  these coordinates must always be shifted by <code>-currentX/Y()</code> when
 *  anything is drawn via GL.
 * </li>
 * </ul>
 *
 * @author andstein, Niklas
 */

public class OpticalFlowIntegrator
{
	static final Logger logg = Logger.getLogger(OpticalFlowIntegrator.class.getName()); // NK111202


	static Preferences prefs = Preferences.userNodeForPackage(OpticalFlowIntegrator.class);
	protected OpticalFlowIntegratorControlPanel controlPanel= null;
	protected Chip2DMotion chip;
	protected boolean useGlobalMotion2= true; // whether to use MotionData.getGlobal{X|Y}2
	protected int channel=0; // which pixel data channel to extract (doesn't matter for MDC2D)

	protected ArrayList<Point2D.Float> ppos;
	protected ArrayDeque<Pixel> buffer;
	protected float[][] pixels=null; // last received pixel data
	protected int x1,y1,x2,y2; // outermost viewport coordinates
	protected boolean clipping; // motion vector out of linear regime
	protected boolean error; // calculation could not be performed

	protected PanTiltParserPanel parserPanel= null;

	public void setPanTiltParserPanel(PanTiltParserPanel panel) {

		parserPanel= panel;
	}

	// NK111118, NK111122
	// a_max = (F_max / m) * N * T_frame / (2*l * tan(gamma))
	// - F_max:   Upper limit of magnitude of force applied [N]
	// - m:       Mass of object in front of lense [kg]
	// - N:       Resolution of sensor is N by N [pxl]
	// - l:       Distance from object to image sensor [m]
	// - T_frame: Frame duration [s/frames]
	// - gamma:   Half opening angle of camera optics [rad]
	// Assuming object being 0.3 [m] away from lense, lense angle 2*15 degrees, resolution of 20 pixels, frame rate 1/0.03 [Hz]
	protected final float a_max= 1e-6f; // (5f/0.5f) * 20f * 0.03f / (2f*0.3f * (float)Math.tan(15f*Math.PI/180f)); // Upper limit of (normalized) force expected to be applied to object [pxl/frame/frame]
	protected final float b_max= a_max * 1.5f;
	protected final float g= ((b_max - a_max) * (float)Math.PI) / 2f; // Just some strech factor..

	// NK111122
	protected ServoInterface hwInterface= null;

	protected GraphTrace[] traces; // to display frame related information

	/**
	 * stores data of fixed length to display in graph -- this is somewhat of
	 * a hack and could (should?) be extended to a more decent graph view with
	 * axes and so on...
	 */
	public class GraphTrace {
		protected float r,g,b;
		protected String name;
		public final int SIZE= 200; // number of elements in array
		protected float[] data;
		protected int i; // current index into array

		public GraphTrace(String name,float red,float green,float blue) {
			this.name= name;
			r=red; g=green; b=blue;
			data= new float[SIZE];
			i=0;
		}

		/**
		 * adds one value to the end of the buffer
		 *
		 * @param f
		 */
		public void addValue(float f) {
			data[i++]= f;
			i%= SIZE;
		}

		/**
		 * clears data buffer
		 */
		public void reset() {
			for(i=0; i<SIZE; i++) {
				data[i]= 0.f;
			}
			i=0;
		}

		/**
		 * for efficiency, drawing directly performed in this method; make
		 * sure an appropriate matrix was defined before (will draw onto
		 * <code>(0,0)-(SIZE,max(data))</code>
		 *
		 * @param GL JOGL drawable
		 */
		public void glDraw(GL2 gl) {
			gl.glColor3f(r,g,b);

			gl.glLineWidth(1f);
			gl.glBegin(GL.GL_LINE_STRIP);
			int j;
			for(j=0; j<SIZE; j++) {
				gl.glVertex3f(j,data[(i+j)%SIZE],0f);
			}
			gl.glEnd();
		}
	}

	/**
	 * stores coordinates as <code>int</code>, value as <code>float</code>
	 */
	public class Pixel {
		protected int x,y;
		protected float vs[]= null; // keep some values for updateValue()
		protected int n; // index into vs[]

		Pixel(int x,int y,float v) {
			this.x= x;
			this.y= y;

			vs= new float[(int) debugParam1]; // 1s at 30 Hz is a reasonable guess
			for(int i=0; i<vs.length; i++) {
				vs[i]= v;
			}
			n=0;
		}

		/**
		 * updates value of pixel with new value
		 * @param v
		 */
		public void updateValue(float v) {
			vs[n++ %vs.length]= v;
			//this.v= (n*this.v+ v)/++n; //TODO consider last 30 pictures or so heavily only
		}

		public float getValue() {
			float ret= 0f;
			for(int i=0; i<vs.length; i++) {
				ret+= vs[vs.length-i-1];
			}
			return ret/vs.length;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

	}

	/**
	 * rounding a <code>float</code> coordinate to an <code>int</code>
	 *
	 * @param f
	 * @return
	 */
	public int round(float f) {
		return (int) Math.floor(f);
	}

	/**
	 * reference to <code>Chip2DMotion</code> is used to calculate pixel array
	 * size etc
	 *
	 * @param chip
	 */
	public OpticalFlowIntegrator(Chip2DMotion chip) {
		this.chip= chip;
		pixels= null; // chip has correct X/Y size not set initially...

		// set up some graphs
		traces= new GraphTrace[4];
		traces[0]= new GraphTrace("dx unfiltered", 1f, 0f, 0f);
		traces[1]= new GraphTrace("dx filtered", 0f, 1f, 0f);
		traces[2]= new GraphTrace("ax", 0f, 0f, 1f);
		traces[3]= new GraphTrace("a_max", 1f, 1f, 1f);

		reset();
	}

	/**
	 * resets all buffers to zero
	 */
	public final void reset() {
		//        buffer= Collections.synchronizedList(new ArrayList<Pixel>());
		buffer= new ArrayDeque<Pixel>();

		ppos= new ArrayList<Point2D.Float>();
		ppos.add(new Point2D.Float(0f, 0f));
		ppos.add(new Point2D.Float(0f, 0f));

		x1= 0;
		y1= 0;
		x2= Math.max(1, chip.getSizeX()); // prevent division by zero upon initialization
		y2= Math.max(1, chip.getSizeY());

		for(GraphTrace g : traces) {
			g.reset();
		}


	}

	/**
	 * updates pixel <code>buffer</code> after a new <code>ppos</code>
	 * has been added
	 *
	 * @param frame pixel data will be saved temporarily
	 */
	protected void updateBuffers(MotionData frame) {
		int lx=round(getX(-1)), llx=round(getX(-2)); //  l{xy} are coords of current frame
		int ly=round(getY(-1)), lly=round(getY(-2)); // ll{xy} are coords of frame before
		int w=chip.getSizeX(),h=chip.getSizeY();

		// update corners of viewport
		if (lx<x1) {
			x1= lx;
		}
		if (ly<y1) {
			y1= ly;
		}
		if ((lx+w)>x2) {
			x2= lx+w;
		}
		if ((ly+h)>y2) {
			y2= ly+h;
		}

		float[][] rawPixels= frame.getRawDataPixel()[channel]; // does NOT copy values, only reference...
		boolean map[][]= new boolean[h][w]; // default values : false
		// first update pixels in buffer
		for(Pixel p : buffer) {
			if ((p.getX()>=lx) && (p.getX()<(lx+w)) &&
				(p.getY()>=ly) && (p.getY()<(ly+h))) {
				int x=p.getX()-lx,y=p.getY()-ly;
				p.updateValue(rawPixels[y][x]);
				map[y][x]= true;
			}
		}
		// then add new pixels
		for(int y=0; y<h; y++) {
			for(int x=0; x<w; x++) {
				if (!map[y][x]) {
					buffer.add(new Pixel(x+lx,y+ly,rawPixels[y][x]));
				}
			}
		}




		/*
        // first clear pixels covered by current/last frame
        Iterator<Pixel> it= buffer.iterator();
        while(it.hasNext()) {
            Pixel p= it.next();
            if ((p.getX() >= lx  && p.getX() < lx+w &&
                 p.getY() >= ly  && p.getY() < ly+h) ||
                (p.getX() >= llx && p.getX() < llx+w &&
                 p.getY() >= lly && p.getY() < lly+h))
                it.remove();
        }

        // then update pixels from last frame not covered by current frame
        if (pixels != null)
            for(int y=0; y<h; y++)
                for(int x=0; x<w; x++)
                    if (!(llx+x>lx && llx+x<lx+w) ||
                        !(lly+y>ly && lly+y<ly+h))
                        buffer.add(new Pixel(llx+x, lly+y, pixels[y][x]));

//        if (Math.random()<0.01)
//            System.err.println("buffer.size="+buffer.size());
//        while(buffer.size() >100)
//            buffer.remove(0);

        // copy frame data for next call
        pixels= frame.extractRawChannel(channel);
		 */
	}

	/**
	 * adds the <code>.getGlobal{X|Y}[2]</code> to the internal integrated
	 * x/y values
	 *
	 * @param frame
	 */
	int n=0;
	int FIRi=0; float FIRx[]= new float[8],FIRy[]= new float[8];
	public synchronized void addFrame(MotionData frame) {
		float dx=0f,dy=0f;
		if (useGlobalMotion2) {
			dx= frame.getGlobalX2();
			dy= frame.getGlobalY2();
		} else {
			dx= frame.getGlobalX();
			dy= frame.getGlobalY();
		}

		if ((Math.abs(dx)>10) || (Math.abs(dy)>10)) {
			error= true; //HACK
			return; // discard bogus values
		}

		clipping= error= false;
		if ((Math.abs(dx)>0.7f) || (Math.abs(dy)>0.7f)) {
			clipping= true;
		}
		if ((dx==0) && (dy==0)) {
			error= true;
		}

		//traces[0].addValue((float) (Math.sqrt(dx*dx+dy*dy)));
		//traces[0].addValue(dx);

		// go against flow
		dx*=-5;
		dy*=-8;
		if (debugOption) {
			dx*=1.5;
			dy*=1;
		}

		// NK111118 NK111122
		dx*= 1.2; //textField1; // 1.2 works quite perfectly
		dy*= 0.7; //textField2; // 0.7 works quite perfectly


		if (useFilter) {
			// simple fixed length FIR
			FIRx[FIRi++ %FIRx.length]= dx;
			FIRy[FIRi++ %FIRy.length]= dy;
			//  dx=dy=0f;
			//   for(int k=0; k<FIRx.length; k++) {
			//        dx+= FIRx[k]/((float)FIRx.length);
			//         dy+= FIRy[k]/((float)FIRy.length);
			//      }


			float[] FIR_copy = new float[FIRx.length];

			// MEDIAN for dx
			System.arraycopy(FIRx, 0, FIR_copy, 0, FIRx.length);
			Arrays.sort(FIR_copy);
			if ((FIR_copy.length % 2) == 1) {
				dx = FIR_copy[((FIR_copy.length+1)/2)-1];
			}
			else
			{
				float lower = FIR_copy[(FIR_copy.length/2)-1];
				float upper = FIR_copy[FIR_copy.length/2];

				dx = (lower + upper) / 2f;
			}

			// MEDIAN for dy
			System.arraycopy(FIRy, 0, FIR_copy, 0, FIRx.length);
			Arrays.sort(FIR_copy);
			if ((FIR_copy.length % 2) == 1) {
				dy = FIR_copy[((FIR_copy.length+1)/2)-1];
			}
			else
			{
				float lower = FIR_copy[(FIR_copy.length/2)-1];
				float upper = FIR_copy[FIR_copy.length/2];

				dy = (lower + upper) / 2f;
			}


			// simple IIR lowpass (assuming all frame.dt equal...)
			//            float ldx= getX(-1) - getX(-2);
			//            float ldy= getY(-1) - getY(-2);
			//            dx= ldx + (dx-ldx)/2;
			//            dy= ldy + (dy-ldy)/2;
		}
		traces[2].addValue(0);
		traces[3].addValue(0);


		//traces[1].addValue((float) (Math.sqrt(dx*dx+dy*dy)));
		traces[0].addValue(dy);
		traces[1].addValue(dx);

		if (parserPanel != null)
		{
			try{
				// play around with these values...
				parserPanel.setSpeedX(-0.016f * dx);
				parserPanel.setSpeedY(-0.012f * dy);
			}
			catch (HardwareInterfaceException ex){
				logg.info("OFI follow hardware exception : " + ex);
			}
		}

		// last element will be used when getX/Y() gets called
		ppos.add( new Point2D.Float(getX(-1)+dx, getY(-1)+dy) );

		// update pixel buffers
		updateBuffers(frame);

		//        if (Math.random()<.1)
		//            System.err.println("x,y="+currentX()+","+currentY()+"; "+x1+","+y1+"-"+x2+","+y2);
	}

	/**
	 * transforms the GL coordinates
	 *
	 * in the new coordinate system, the whole viewport (from
	 * <code>x1,y1</code> to <code>x2,y2</code>) will be visible in the same
	 * space that only contained the chip's pixel before
	 *
	 * @param gl to apply transformation matrices
	 */
	protected int drawableWidth,drawableHeight;
	protected boolean broaderThanHigh; // means there is space on left/right of pixel display
	public synchronized void glTransform(GLAutoDrawable drawable,GL2 gl) {

		drawableWidth = drawable.getSurfaceWidth ();
		drawableHeight= drawable.getSurfaceHeight();

		//FIXME scale better when trace is wider than high

		// move pixels to one side when traces are to be displayed
		if (showTraces) {
			if ((drawableWidth/chip.getSizeX()) > (drawableHeight/chip.getSizeY())) {
				broaderThanHigh= true;
				// draw chip canvas on the right
				gl.glTranslatef( (.4f * (drawableWidth-drawableHeight) *
					chip.getSizeY())/drawableHeight,0f,0f );
			} else {
				broaderThanHigh= false;
				// not implemented (highly unlikely)
			}
		}

		float dx= x2-x1;
		float dy= y2-y1;
		gl.glScalef(chip.getSizeX()/Math.max(dx, dy),
			chip.getSizeY()/Math.max(dx, dy),1f);
		gl.glTranslatef(currentX()-x1, currentY()-y1, 0f);
	}

	protected void glDrawTraces(GL2 gl) {
		if (!showTraces) {
			return;
		}

		if (!broaderThanHigh) {
			// highly unlikely; don't draw traces in this case
			return;
		}

		gl.glPushMatrix();

		// first move back to upper left corner
		gl.glTranslatef(-(currentX()-x1), -(currentY()-y1), 0f);
		// then move to the left to make place for the traces; also move
		gl.glTranslatef( (-1.f *(drawableWidth-drawableHeight)*(y2-y1))/drawableHeight,
			0f,0f);
		int size= traces[0].SIZE;
		// assume data range [-1..1]; indexed by sample [0..size]
		gl.glScalef((1.7f*(x2-x1))/size, (y2-y1)/2f, 0);
		// move to middle
		gl.glTranslatef(0f,1f,0f);
		// and finally use mathematical convention again
		gl.glScalef(1f,-1f,1f);

		// draw bounding box
		gl.glColor3f(.3f,.3f,.3f);
		gl.glLineWidth(2f);
		gl.glBegin(GL.GL_LINE_LOOP);
		gl.glVertex3f(  0f,-1f,0f);
		gl.glVertex3f(size,-1f,0f);
		gl.glVertex3f(size, 1f,0f);
		gl.glVertex3f(  0f, 1f,0f);
		gl.glEnd();

		// draw graphs
		for(GraphTrace g : traces) {
			g.glDraw(gl);
		}

		gl.glPopMatrix();
	}

	/**
	 * draws the buffered pixels onto GL canvas
	 *
	 * pixels still visible in the viewport but not part of the current frame
	 * are buffered internally and can be drawn onto the viewport (after a
	 * <code>glTransform</code> was performed) using this method. the value
	 * is scaled according to
	 *
	 * <pre>
	 *   value = (pixel_value - offset) * gain
	 * </pre>
	 *
	 * @param gl drawable
	 * @param offset
	 * @param gain
	 */
	public synchronized void glDrawPixels(GL2 gl,float offset,float gain) {
		int dx= -currentX();
		int dy= -currentY();

		for(OpticalFlowIntegrator.Pixel px : buffer) {
			float v= gain*(px.getValue()-offset);

			gl.glColor3f(v,v,v);
			gl.glRectf(px.getX()+dx   ,px.getY()+dy,
				px.getX()+dx+1f,px.getY()+dy+1f);
		}

		gl.glColor3f(0f,1f,1f);
		if (clipping) {
			gl.glColor3f(1f,.5f,0f);
		}
		if (error) {
			gl.glColor3f(1f,1f,0f);
		}
		gl.glLineWidth(2f);
		gl.glBegin(GL.GL_LINE_LOOP);
		gl.glVertex3f(x1+dx,y1+dy,0f);
		gl.glVertex3f(x2+dx,y1+dy,0f);
		gl.glVertex3f(x2+dx,y2+dy,0f);
		gl.glVertex3f(x1+dx,y2+dy,0f);
		gl.glEnd();

		// draw graph traces
		glDrawTraces(gl);
	}



	/**
	 * shortcut to access <code>ppos</code>
	 */
	protected float getX(int idx) {
		if (idx<0) {
			idx+= ppos.size();
		}
		return (float) ppos.get(idx).getX();
	}

	/**
	 * shortcut to access <code>ppos</code>
	 */
	protected float getY(int idx) {
		if (idx<0) {
			idx+= ppos.size();
		}
		return (float) ppos.get(idx).getY();
	}

	/**
	 * returns current coordinate of upper left corner
	 * @return
	 */
	protected int currentX() {
		return round(getX(-1));
	}

	/**
	 * returns current coordinate of upper left corner
	 * @return
	 */
	protected int currentY() {
		return round(getY(-1));
	}


	/* GUI interaction */
	protected boolean showTraces= prefs.getBoolean("showTraces", false);
	protected boolean useFilter= prefs.getBoolean("useFilter", false);

	// use these values freely to experiment...
	protected boolean debugOption= prefs.getBoolean("debugOption", false);
	protected float debugParam1= prefs.getFloat("debugParam1", 1.2f);
	protected float debugParam2= prefs.getFloat("debugParam2", 0.8f);

	public boolean isShowTraces() {
		return showTraces;
	}

	public void doShowTraces(boolean value) {
		showTraces = value;
		prefs.putBoolean("showTraces", debugOption);
	}

	public boolean isUsingFilter() {
		return useFilter;
	}

	public void setUseFilter(boolean value) {
		useFilter = value;
		prefs.putBoolean("useFilter", debugOption);
	}

	public boolean isDebugOption() {
		return debugOption;
	}

	public void setDebugOption(boolean value) {
		debugOption = value;
		prefs.putBoolean("debugOption", value);
	}

	public void setDebugParam1(String value) {
		if (value.equals("")) {
			debugParam1= 0;
		}
		else {
			debugParam1 = Float.parseFloat(value);
		}
		prefs.putFloat("debugParam1", debugParam1);
	}

	public String getDebugParam1() {
		return Float.toString(debugParam1);
	}

	public void setDebugParam2(String value) {
		if (value.equals("")) {
			debugParam2= 0;
		}
		else {
			debugParam2 = Float.parseFloat(value);
		}
		prefs.putFloat("debugParam2", debugParam2);
	}
	public String getDebugParam2() {
		return Float.toString(debugParam2);
	}

	public OpticalFlowIntegratorControlPanel getControlPanel() {
		if (controlPanel == null) {
			controlPanel= new OpticalFlowIntegratorControlPanel(this);
		}
		return controlPanel;
	}

}
