/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.nio.IntBuffer;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLJPanel;
import javax.media.opengl.fixedfunc.GLLightingFunc;
import javax.media.opengl.fixedfunc.GLMatrixFunc;
import javax.media.opengl.glu.GLU;

import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * An openGL container that displays the CLCamera raw frame data.
 * 
 * @author Tobi
 */
public class CLRawFramePanel extends GLJPanel implements GLEventListener, AEListener {

	final static Logger log = Logger.getLogger("CLCamera");
	final static GLU glu = new GLU();
	CLRetinaHardwareInterface hardware = null;
	PSEyeCLModelRetina chip;
	private boolean fillsHorizontally = false, fillsVertically = false; // filled in in the reshape method to show how chip fills drawable space
	private final double ZCLIP = 1;

	/** Orthographic projection clipping area. */
	private class ClipArea {

		float left = 0, right = 0, bottom = 0, top = 0;

		ClipArea(float l, float r, float b, float t) {
			left = l;
			right = r;
			bottom = b;
			top = t;
		}
	}
	/** The actual clipping box bounds for the default orthographic projection. */
	private ClipArea clipArea = new ClipArea(0, 0, 0, 0);

	/** Border around chip in clipping area. */
	private class Borders {

		float leftRight = 0, bottomTop = 0;
	};
	/** The actual borders in model space around the chip area. */
	private Borders borders = new Borders();

	public CLRawFramePanel(PSEyeCLModelRetina chip) {
		super();
		this.chip = chip;
		hardware = (CLRetinaHardwareInterface) chip.getHardwareInterface();
		addGLEventListener(this);
		setSize(new Dimension(chip.getSizeX(),chip.getSizeY()));
	}

	@Override
	public void init(GLAutoDrawable drawable) {
		drawable.setAutoSwapBufferMode(true);
		GL2 gl = drawable.getGL().getGL2();

		gl.setSwapInterval(1);
		gl.glShadeModel(GLLightingFunc.GL_FLAT);

		gl.glClearColor(0, 0, 0, 0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		gl.glLoadIdentity();

		gl.glRasterPos3f(0, 0, 0);
		gl.glColor3f(1, 1, 1);
		checkGLError(gl, glu, "after init");

	}

	@Override
	public void display(GLAutoDrawable drawable) {
		displayPixmap(drawable);
	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		GL2 gl = drawable.getGL().getGL2();
		gl.glLoadIdentity();
		final int chipSizeX = chip.getSizeX();
		final int chipSizeY = chip.getSizeY();
		float newscale;
		final float border = 0; // getBorderSpacePixels()*(float)height/width;
		if (chipSizeY > chipSizeX) {
			// chip is tall and skinny, so set j2dScale by frame height/chip height
			newscale = (height - border) / chipSizeY;
			fillsVertically = true;
			fillsHorizontally = false;
			if ((newscale * chipSizeX) > width) { // unless it runs into left/right, then set to fill width
				newscale = (width - border) / chipSizeX;
				fillsHorizontally = true;
				fillsVertically = false;
			}
		} else {
			// chip is square or squat, so set j2dScale by frame width / chip width
			newscale = (width - border) / chipSizeX;
			fillsHorizontally = true;
			fillsVertically = false;
			if ((newscale * chipSizeY) > height) {// unless it runs into top/bottom, then set to fill height
				newscale = (height - border) / chipSizeY;
				fillsVertically = true;
				fillsHorizontally = false;
			}
		}
		setDefaultProjection(gl, drawable); // this sets orthographic projection so that chip pixels are scaled to the drawable area
		gl.glViewport(0, 0, width, height);
		repaint();

	}

	private void displayPixmap(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		if (gl == null) {
			return;
		}
		if (hardware == null) {
			return;
		}
		if (hardware.getEvents() == null) {
			return;
		}
		AEPacketRaw packet = hardware.getEvents();
		int[] pixvals = packet.getAddresses();
		IntBuffer buf = IntBuffer.wrap(pixvals);

		clearDisplay(gl);
		final int ncol = chip.getSizeX();
		final int nrow = chip.getSizeY();
		checkGLError(gl, glu, "before pixmap");
		final int wi = drawable.getWidth(), hi = drawable.getHeight();
		float scale = 1;
		final float border = 0; // chip.getCanvas().getBorderSpacePixels();
		scale = (hi - (2 * border)) / (chip.getSizeY());
		//            scale = ((float) wi - 2 * border) / (chip.getSizeX());

		gl.glPixelZoom(scale, -scale); // flips y according to CLCamera output
		gl.glRasterPos2f(-.5f, chip.getSizeY()-.5f); // to UL corner of chip, but must be inside viewport or else it is ignored, breaks on zoom     if (zoom.isZoomEnabled() == false) {

		buf.position(0);
		gl.glDrawPixels(ncol, nrow, GL.GL_BGRA, GL2GL3.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
		checkGLError(gl, glu, "after pixmap");
	}

	private float clearDisplay(GL2 gl) {
		float gray = 0;
		gl.glClearColor(gray, gray, gray, 0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);

		return gray;
	}

	/** Utility method to check for GL errors. Prints stacked up errors up to a limit.
    @param g the GL context
    @param glu the GLU used to obtain the error strings
    @param msg an error message to log to e.g., show the context
	 */
	public void checkGLError(GL2 g, GLU glu, String msg) {
		int error = g.glGetError();
		int nerrors = 3;
		while ((error != GL.GL_NO_ERROR) && (nerrors-- != 0)) {
			StackTraceElement[] trace = Thread.currentThread().getStackTrace();
			if (trace.length > 1) {
				String className = trace[2].getClassName();
				String methodName = trace[2].getMethodName();
				int lineNumber = trace[2].getLineNumber();
				log.warning("GL error number " + error + " " + glu.gluErrorString(error) + " : " + msg + " at " + className + "." + methodName + " (line " + lineNumber + ")");
			} else {
				log.warning("GL error number " + error + " " + glu.gluErrorString(error) + " : " + msg);
			}
			//             Thread.dumpStack();
			error = g.glGetError();
		}
	}

	/** Sets the projection matrix so that we getString an orthographic projection that is the size of the
    canvas with z volume -ZCLIP to ZCLIP padded with extra space around the sides.

    @param g the GL context
    @param d the GLAutoDrawable canvas
	 */
	protected void setDefaultProjection(GL2 g, GLAutoDrawable d) {
		/*
        void glOrtho(GLdouble left,
        GLdouble right,
        GLdouble bottom,
        GLdouble top,
        GLdouble near,
        GLdouble far)
		 */
		final int w = getWidth(), h = getHeight(); // w,h of screen
		final int sx = chip.getSizeX(), sy = chip.getSizeY(); // chip size
		final float border = 0; // desired smallest border in screen pixels
		float glScale;
		checkGLError(g, glu, "before setDefaultProjection");
		g.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
		g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
		// now we set the clipping volume so that the volume is clipped according to whether the window is tall (ar>1) or wide (ar<1).

		if (fillsHorizontally) { //tall
			glScale = (w - (2 * border)) / sx; // chip pix to screen pix scaling in horizontal&vert direction
			float b = border / glScale; // l,r border in model coordinates
			if (b <= 0) {
				b = 1;
			}
			float bb = ((h / glScale) - sy) / 2; // leftover y in model coordinates that makes up vertical border
			if (bb <= 0) {
				bb = 1;
			}
			clipArea.left = -b;
			clipArea.right = sx + b;
			clipArea.bottom = -bb;
			clipArea.top = sy + bb;
			borders.leftRight = b;
			borders.bottomTop = bb;
			g.glOrtho(-b, sx + b, -bb, (sy + bb), ZCLIP, -ZCLIP); // clip area has same ar as screen!
		} else {
			glScale = (h - (2 * border)) / sy;
			float b = border / glScale;
			if (b <= .5f) {
				b = 1;
			}
			float bb = ((w / glScale) - sx) / 2; // leftover y in model coordinates that makes up vertical border
			if (bb <= 0) {
				bb = 1;
			}
			clipArea.left = -bb;
			clipArea.right = sx + bb;
			clipArea.bottom = -b;
			clipArea.top = sy + b;
			borders.leftRight = bb;
			borders.bottomTop = b;
			g.glOrtho(-bb, (sx + bb), -b, sy + b, ZCLIP, -ZCLIP);
		}
		g.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt == hardware.newEventPropertyChange) {
			try {
				display();
			} catch (Exception e) {
				log.warning("caught " + e + " when dislay() was called in response to the event " + evt);

			}
		}
	}

	@Override
	public void dispose(GLAutoDrawable arg0) {
		// TODO Auto-generated method stub

	}
}
