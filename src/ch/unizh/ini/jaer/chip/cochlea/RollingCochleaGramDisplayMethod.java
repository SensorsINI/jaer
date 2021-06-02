/*
 * RetinaCanvas.java
 *
 * Created on January 9, 2006, 6:50 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;


import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.DisplayMethod2D;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Shows events from stereo cochlea as a rastergram. Each packet is painted to the right of previous ones. The screen is used as the memory.
 *When the time reaches the right side, it resets to the left and the screen is cleared. The timescale is set by the ChipRenderer colorScale, which the user
 *can set with the UP/DOWN arrows buttons. It is set so that the entire width of the screen is a certain number of seconds.
 * @author tobi
 */
public class RollingCochleaGramDisplayMethod extends DisplayMethod implements DisplayMethod2D, HasSelectedCochleaChannel {

        public static final String EVENT_SCREEN_CLEARED="RollingCochleaGramDisplayMethod.EVENT_SCREEN_CLEARED";

	/**
	 * Creates a new instance of CochleaGramDisplayMethod
	 *
	 * @param c the canvas we are drawing on
	 */
	public RollingCochleaGramDisplayMethod(ChipCanvas c) {
		super(c);
	}
	final float rasterWidth = 0.003f; // width of screen;
	final int BORDER = 50; // pixels
	protected int startTime = 0; // the time (in timestamp units) that the strip starts at currently; is updated when chart reaches timeWidthUs
	protected boolean clearScreenEnabled = true; // set by rendering when it is time to resetart the strip chart because time has passed right edge
	int font = GLUT.BITMAP_HELVETICA_18;
	int oldColorScale = 0;
	boolean hasBlend = false;
	boolean hasBlendChecked = false;
	/** Set by rendering to total width in us time of current strip chart */
	protected float timeWidthUs; // set horizontal scale so that we can just use relative timestamp for x
	/** The selected channel is displayed by a marker line in the display. This feature is used to select channels in the Equalizer. */
	protected int selectedChannel=-1;

	private int prevSelectedChannel=-1;
	/** set by rendering so that 1 unit drawing scale draws to right side, 0 to left during rendering, using scaling as in
    <pre>
    drawTimeScale = (drawable.getWidth() / timeWidthUs); // scale horizontal is draw
    gl.glScalef(drawTimeScale, yScale, 1);
    </pre>
	 */
	protected float drawTimeScale;

	/** displays individual events as cochleagram
	 * @param drawable the drawable passed in by OpenGL
	 */
	@Override
	public void display(GLAutoDrawable drawable) {
		AEChipRenderer renderer = (AEChipRenderer) getChipCanvas().getRenderer();
		int ntaps = chip.getSizeX();
		EventPacket ae = (EventPacket) chip.getLastData();
		if ((ae == null) || ae.isEmpty()) {
			return;
		}
		int t0 = ae.getFirstTimestamp();

		GL2 gl = drawable.getGL().getGL2();
		gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
		gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
		gl.glOrtho(-BORDER, drawable.getSurfaceWidth() + BORDER, -BORDER, drawable.getSurfaceHeight() + BORDER, 10000, -10000);
		gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
		// blend may not be available depending on graphics mode or opengl version.
		if (!hasBlendChecked) {
			hasBlendChecked = true;
			String glExt = gl.glGetString(GL.GL_EXTENSIONS);
			if (glExt.indexOf("GL_EXT_blend_color") != -1) {
				hasBlend = true;
			}
		}
		if (hasBlend) {
			try {
				gl.glEnable(GL.GL_BLEND);
				gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
				gl.glBlendEquation(GL.GL_FUNC_ADD);
			} catch (GLException e) {
				log.warning("tried to use glBlend which is supposed to be available but got following exception");
				gl.glDisable(GL.GL_BLEND);
				e.printStackTrace();
				hasBlend = false;
			}
		}
		gl.glLoadIdentity();
		// translate origin to this point
		gl.glTranslatef(0, 0, 0);
		// scale everything by rastergram scale
		float yScale = (drawable.getSurfaceHeight()) / (float) ntaps;// scale vertical is draableHeight/numPixels
		// timewidth comes from render contrast setting
		// width starts with colorScale=1 to be the standard refresh rate
		// so that a raster will refresh every frame and reduces by powers of two
		int colorScale = getRenderer().getColorScale();
		if (colorScale != oldColorScale) {
			clearScreenEnabled = true;
		}
		oldColorScale = colorScale;
		int frameRate = 60; // hz
		if (chip instanceof AEChip) {
			frameRate = ((AEChip) chip).getAeViewer().getDesiredFrameRate();
		}
		timeWidthUs = (1e6f / frameRate) * (1 << colorScale); // set horizontal scale so that we can just use relative timestamp for x
		drawTimeScale = (drawable.getSurfaceWidth() / timeWidthUs); // scale horizontal is draw
		gl.glScalef(drawTimeScale, yScale, 1);
		// make sure we're drawing back buffer (this is probably true anyhow)
		gl.glDrawBuffer(GL.GL_FRONT_AND_BACK);

		// render events
		if (clearScreenEnabled) {
			clearScreen(gl);
			clearScreenEnabled = false;
			startTime = t0;
                        getSupport().firePropertyChange(RollingCochleaGramDisplayMethod.EVENT_SCREEN_CLEARED, 0, startTime);
		}

		// draw time axis with label of total raster time
		gl.glColor3f(0, 0, 1);
		gl.glLineWidth(4f);
		gl.glBegin(GL.GL_LINES);
		{
			gl.glVertex3f(0, 0, 0);
			gl.glVertex3f(timeWidthUs, 0, 0); // time axis
		}
		gl.glEnd();

		String timeLabel = String.format("%.3f s", timeWidthUs * 1e-6f);
		int width = glut.glutBitmapLength(font, timeLabel);
		gl.glRasterPos3f(timeWidthUs * .9f, 2, 0);
		glut.glutBitmapString(font, timeLabel);

		// draw selected channel, if any
		if (getSelectedChannel() >= 0) {
			gl.glLineWidth(1f);
			final float sc = .8f;
			// erase previous line if possible
			if (hasBlend && (prevSelectedChannel >= 0)) {
				gl.glColor3fv(getSelColor(prevSelectedChannel),0);
				gl.glBlendEquation(GL.GL_FUNC_REVERSE_SUBTRACT);
				gl.glBegin(GL.GL_LINES);
				{
					gl.glVertex3f(0, (prevSelectedChannel/2)+.5f, 0);
					gl.glVertex3f(timeWidthUs, (prevSelectedChannel/2)+.5f, 0); // selected channel line
				}
				gl.glEnd();
				gl.glBlendEquation(GL.GL_FUNC_ADD);
			}
			gl.glColor3fv(getSelColor(selectedChannel),0);
			gl.glBegin(GL.GL_LINES);
			{
				gl.glVertex3f(0, (selectedChannel/2)+.5f, 0);
				gl.glVertex3f(timeWidthUs, (selectedChannel/2)+.5f, 0); // selected channel line
			}
			gl.glEnd();
			prevSelectedChannel=getSelectedChannel();
		}

		// draw channel (vertical) axis
		gl.glColor3f(0, 0, 1);
		gl.glLineWidth(4f);
		gl.glBegin(GL.GL_LINES);
		{
			gl.glVertex3f(0, 0, 0);
			gl.glVertex3f(0, chip.getSizeX(), 0); // taps axis
		}
		gl.glEnd();
		gl.glRasterPos3f(0, chip.getSizeX(), 0);
		glut.glutBitmapString(font, "Channel");

		final float w = timeWidthUs / getChipCanvas().getCanvas().getWidth(); // spike raster as fraction of screen width
		float[][] typeColors = renderer.getTypeColorRGBComponents();
		if(typeColors==null) {
			log.warning("null event type colors typeColors passed back from renderer, will not render samples");
			return;
		}
		try {
			for (Object o : ae) {
				TypedEvent ev = (TypedEvent) o;
				gl.glColor3fv(typeColors[ev.type], 0);// FIXME depends on these colors having been created by a rendering cycle...
				//            CochleaGramDisplayMethod.typeColor(gl,ev.type);
				float t = ev.timestamp - startTime; // z goes from 0 (oldest) to 1 (youngest)
				gl.glRectf(t, ev.x, t + w, ev.x + 1);
				if ((t > timeWidthUs) || (t < 0)) {
					clearScreenEnabled = true;
				}
			}
		} catch (NullPointerException ex) {
			log.warning("caught a null pointer exception while rendering events, probably colors of events not fully instantiated yet");
		}



		gl.glFlush();
		//        log.info("after flush, timeWidthUs="+timeWidthUs+" ");
		//        gl.glFinish();  // should not need to be called, according to http://www.opengl.org/discussion_boards/ubbthreads.php?ubb=showflat&Number=196733

		getChipCanvas().checkGLError(gl, glu, "after RollingCochleaGramDisplayMethod");
	}

	// called at end of drawing rasters across screen
	void clearScreen(GL2 gl) {
		gl.glDrawBuffer(GL.GL_FRONT_AND_BACK);
		gl.glClearColor(0, 0, 0, 0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		getChipCanvas().checkGLError(gl, glu, "after RollingCochleaGramDisplayMethod,clearScreen");
	}

        /** Returns the current total time width of displayed timestamps
         * 
         * @return the width of display, in us
         */
	protected float getTimeWidthUs() {
		return timeWidthUs;
	}

        /** Returns the starting time of displayed timestamps, in us
         * 
         * @return starting timestamp, in us
         */
	protected float getStartTimeUs() {
		return startTime;
	}

	/**
	 * @return the selectedChannel
	 */
	@Override
	public int getSelectedChannel() {
		return selectedChannel;
	}

	/**
	 * Set this channel >=0 to show a selected channel. Set to negative integer to display display of selected channel.
	 * @param selectedChannel the selectedChannel to set
	 */
	@Override
	public void setSelectedChannel(int selectedChannel) {
		this.selectedChannel = selectedChannel;
	}

	private float sc=.8f;
	private float[] redSel={sc,0,0}, greenSel={0,sc,0};
	private float[] getSelColor(int channel){
		if((channel%2)==1) {
			return redSel;
		}
		else {
			return greenSel;
		}
	}
}
