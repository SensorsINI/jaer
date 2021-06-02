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
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.DisplayMethod2D;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Shows events from stereo cochlea as a rastergram.
 * Time increases to the right and covers one time slice of events
 * as passed to the rendering.
 * Channel increases upwards. Channels are the event x addresses.
 *
 * @author tobi
 */
public class CochleaGramDisplayMethod extends DisplayMethod implements DisplayMethod2D, HasSelectedCochleaChannel {

	boolean hasBlend=false;
	boolean hasBlendChecked=false;
	private int selectedChannel; // displays selected channel in Equalizer, for example

	/**
	 * Creates a new instance of CochleaGramDisplayMethod
	 *
	 * @param c the canvas we are drawing on
	 */
	public CochleaGramDisplayMethod(ChipCanvas c) {
		super(c);
	}

	final float rasterWidth=0.006f; // width of each spike in raster plot

	/** Border around raster plot in pixels. */
	public static final int BORDER=50; // pixels

	/** displays individual events as cochleagram
	 * @param drawable the drawable passed in by OpenGL
	 */
	@Override
	public void display(GLAutoDrawable drawable) {
		if(drawable==null){
			log.warning("null drawable, not displaying");
			return;
		}
		GL2 gl = drawable.getGL().getGL2();
		if(!hasBlendChecked){
			hasBlendChecked=true;
			String glExt=gl.glGetString(GL.GL_EXTENSIONS);
			if(glExt.indexOf("GL_EXT_blend_color")!=-1) {
				hasBlend=true;
			}
		}
		if(hasBlend){
			try{
				gl.glEnable(GL.GL_BLEND);
				gl.glBlendFunc(GL.GL_ONE,GL.GL_ONE);
				gl.glBlendEquation(GL.GL_FUNC_ADD);
			}catch(GLException e){
				log.warning("tried to use glBlend which is supposed to be available but got following exception");
				gl.glDisable(GL.GL_BLEND);
				e.printStackTrace();
				hasBlend=false;
			}
		}

		gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
		gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
		gl.glOrtho(-BORDER, drawable.getSurfaceWidth() + BORDER, -BORDER, drawable.getSurfaceHeight() + BORDER, 10000, -10000);
		gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
		gl.glClearColor(0, 0, 0, 0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		gl.glLoadIdentity();
		// translate origin to this point
		gl.glTranslatef(0, 0, 0);
		// scale everything by rastergram scale
		float ys = (drawable.getSurfaceHeight()) / (float) chip.getSizeX();// scale vertical is draableHeight/numPixels
		float xs = (drawable.getSurfaceWidth()); // scale horizontal is draw
		gl.glScalef(xs, ys, 1);
		// make sure we're drawing back buffer (this is probably true anyhow)
		//        gl.glDrawBuffer(GL.GL_BACK);

		// draw axes
		gl.glColor3f(0, 0, 1);
		gl.glLineWidth(1f);
		int len = chip.getSizeX() - 3;
		gl.glBegin(GL.GL_LINES);
		{
			gl.glVertex3f(0, 0, 0);
			gl.glVertex3f(0, len, 0); // taps axis
			gl.glVertex3f(0, 0, 0);
			gl.glVertex3f(1, 0, 0); // time axis
		}
		gl.glEnd();
		// draw axes labels x,y,t. See tutorial at http://jerome.jouvie.free.fr/OpenGl/Tutorials/Tutorial18.php
		int font = GLUT.BITMAP_HELVETICA_18;
		gl.glPushMatrix();
		{
			final int FS = 1; // distance in pixels of text from endZoom of axis
			gl.glRasterPos3f(0, chip.getSizeX(), 0);
			glut.glutBitmapString(font, "Channel");
			gl.glRasterPos3f(1, 0, 0);
			glut.glutBitmapString(font, "Time");
		}
		gl.glPopMatrix();

		// render events

		EventPacket ae = (EventPacket) chip.getLastData();
		if ((ae == null) || ae.isEmpty()) {
			return;
		}
		int n = ae.getSize();
		int t0 = ae.getFirstTimestamp();
		int dt = (ae.getLastTimestamp() - t0) + 1;
		float z;
		if(chip.getRenderer()==null){
			log.warning("null chip Renderer, can't get event type colors, not rendering events");
			return;
		}
		float[][] typeColors = ((AEChipRenderer) chip.getRenderer()).getTypeColorRGBComponents();
		try {
			for (Object o : ae) {
				TypedEvent ev = (TypedEvent) o;
				gl.glColor3fv(typeColors[ev.type], 0); // TODO depends on these colors having been created by a rendering cycle...
				//            CochleaGramDisplayMethod.typeColor(gl, ev.type);
				//            if(ev.type==0) gl.glColor4f(1,0,0,alpha); else gl.glColor4f(0,1,0,alpha); // red right
				z = (float) (ev.timestamp-t0) / dt; // z goes from 0 (oldest) to 1 (youngest)
				gl.glRectf(z,ev.x,z+rasterWidth,ev.x+1); // taps increse upwards
			}
		} catch (ClassCastException e) {
			log.warning("while rendering events caught " + e + ", some filter is casting events to BasicEvent?");
		} catch(NullPointerException npe){
			log.warning("caught "+npe.toString()+", need a rendering cycle to create event type colors");
		}

		// draw selected channel
		if (getSelectedChannel() >= 0) {
			final float sc = .8f;
			gl.glColor3fv(getSelColor(selectedChannel),0);
			gl.glLineWidth(1f);
			len = chip.getSizeX() - 3;
			gl.glBegin(GL.GL_LINES);
			{
				gl.glVertex3f(0, (selectedChannel/2)+.5f, 0);
				gl.glVertex3f(1, (selectedChannel/2)+.5f, 0);
			}
			gl.glEnd();
		}

		getChipCanvas().checkGLError(gl, glu, "after CochleaGramDisplayMethod");

	}

	private float sc = .8f;
	private float[] redSel = {sc, 0, 0}, greenSel = {0, sc, 0};

	private float[] getSelColor(int channel) {
		if ((channel % 2) == 1) {
			return redSel;
		} else {
			return greenSel;
		}
	}


	/** Sets the gl color depending on cochlea cell type
    @param gl the GL context
    @param type the cell type 0-3
	 */
	public static void typeColor(GL2 gl, int type) {
		final float alpha = 0.5f;
		switch (type) {
			case 0:
				gl.glColor4f(1, 0, 0, alpha);
				break;
			case 2:
				gl.glColor4f(0, 1, 0, alpha);
				break;
			case 1:
				gl.glColor4f(1, 1, 0, alpha);
				break;
			case 3:
				gl.glColor4f(0, 0, 1, alpha);
			default:
				gl.glColor4f(.5f, .5f, .5f, alpha);
		}
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
}
