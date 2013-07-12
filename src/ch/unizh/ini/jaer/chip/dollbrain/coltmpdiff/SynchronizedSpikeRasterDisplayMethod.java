/*
 * RetinaCanvas.java
 *
 * Created on January 9, 2006, 6:50 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dollbrain.coltmpdiff;

//import ch.unizh.ini.caviar.aemonitor.EventXYType;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Observable;
import java.util.Observer;
import java.util.prefs.Preferences;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLException;
import javax.media.opengl.fixedfunc.GLMatrixFunc;

import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.SyncEvent;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.DisplayMethod2D;
import net.sf.jaer.util.EngineeringFormat;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Shows events from a single or few chip addresses as a rastergram, synchronized by a special event.
 * Raster traces are drawn left to right, rastering down from top of display on each special event.
 * 
 * The time is scaled so that the entire width of the screen is the time between special events.
 * @author tobi
 */
public class SynchronizedSpikeRasterDisplayMethod extends DisplayMethod implements DisplayMethod2D, GLEventListener, PropertyChangeListener, Observer {

	static Preferences prefs = Preferences.userNodeForPackage(SynchronizedSpikeRasterDisplayMethod.class);
	private boolean addedPropertyChangeListener = false;

	/**
	 * Creates a new instance of SynchronizedSpikeRasterDisplayMethod
	 *
	 * @param c the canvas we are drawing on
	 */
	public SynchronizedSpikeRasterDisplayMethod(ChipCanvas c) {
		super(c);
		c.addGLEventListener(this);
		chip.addObserver(this);

	}
	final float rasterWidth = 0.01f; // width of each spike tick;
	final int BORDER = 50; // pixels
	int maxNumRasters = prefs.getInt("SynchronizedSpikeRasterDisplayMethod.maxNumRasters", 30);
	boolean clearScreenEnabled = true;
	int font = GLUT.BITMAP_HELVETICA_18;
	int oldMaxNumRasters = 0;
	boolean hasBlend = false;
	boolean hasBlendChecked = false;
	int lastSyncTime = 0;
	int maxTime = 1;
	int rasterNumber = 0;
	EventPacket spikes = new EventPacket(SyncEvent.class);
	OutputEventIterator addSpikeIterator = spikes.outputIterator();
	private boolean paused = false;

	private void clearSpikes() {
		spikes.clear();
		addSpikeIterator = spikes.outputIterator();
	}

	private void redrawSpikes(GLAutoDrawable drawable) {
		GL2 gl = setupMyGL(drawable);
		clearScreen(gl);
		rasterNumber = -1;
		drawSpikes(drawable, gl, spikes, true);
	}

	private void addSpike(SyncEvent ev) {
		addSpikeIterator.nextOutput().copyFrom(ev);
	}

	/** displays individual events as rastergram
	 * @param drawable the drawable passed in by OpenGL
	 */
	@Override
	synchronized public void display(GLAutoDrawable drawable) {
		ChipCanvas c = getChipCanvas();
		Chip2D chip = c.getChip();
		AEChip aeChip = (AEChip) chip;
		if (aeChip.getAeViewer().isPaused()) {
			return;
		}
		EventPacket ae = (EventPacket) chip.getLastData();
		if ((ae == null) || ae.isEmpty()) {
			return;
		}
		GL2 gl = setupMyGL(drawable);

		drawSpikes(drawable, gl, ae, false);
	}

	void clearScreen(GL2 gl) {
		gl.glDrawBuffer(GL.GL_FRONT_AND_BACK);
		gl.glClearColor(0, 0, 0, 0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		// draw axes
		//        gl.glColor3f(0, 0, 1);
		//        gl.glLineWidth(4f);
		//        gl.glBegin(GL2.GL_LINES);
		//        {
		//            gl.glVertex3f(0, 0, 0);
		//            gl.glVertex3f(0, chip.getSizeX(), 0); // taps axis
		//        }
		//        gl.glEnd();
		//        gl.glRasterPos3f(0, chip.getSizeX(), 0);
		//        glut.glutBitmapString(font, "Channel");
		getChipCanvas().checkGLError(gl, glu, "after display drawing,clearScreen");

	}

	@Override
	public void init(GLAutoDrawable drawable) {
	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		clearScreenEnabled = true;
	}

	synchronized public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
		redrawSpikes(drawable);
	}

	synchronized private void clear(GL2 gl) {
		rasterNumber = -1;
		clearScreen(gl);
		clearSpikes();
		maxTime = 1;
		clearScreenEnabled = false;
	}

	/** Draws rasters.
	 *
	 * @param drawable
	 * @param gl
	 * @param ae the event packet to draw.
	 * @param redrawing false to remember these spikes (append them to a list for redraw).
	 */
	private void drawSpikes(GLAutoDrawable drawable, GL2 gl, EventPacket ae, boolean redrawing) {
		// scale everything by rastergram scale
		// timewidth comes from render contrast setting
		// width starts with colorScale=1 to be the standard refresh rate
		// so that a raster will refresh every frame and reduces by powers of two
		maxNumRasters = 1 << getRenderer().getColorScale();
		if (maxNumRasters != oldMaxNumRasters) {
			clearScreenEnabled = true;
			maxTime = 1;
			prefs.putInt("SynchronizedSpikeRasterDisplayMethod.maxNumRasters", maxNumRasters);
		}
		float yScale = (drawable.getHeight()) / (float) maxNumRasters; // scale vertical is draableHeight/numPixels
		oldMaxNumRasters = maxNumRasters;
		float drawTimeScale = drawable.getWidth() / (float) maxTime; // scale horizontal is draw
		gl.glScalef(drawTimeScale, yScale, 1);
		// make sure we're drawing back buffer (this is probably true anyhow)
		gl.glDrawBuffer(GL.GL_FRONT_AND_BACK);
		// render events
		if (clearScreenEnabled) {
			clear(gl);
		}

		// autoscale time to max interval between syncs.
		boolean redrawPrevious = false;
		for (Object o : ae) {
			// while iterating through events, also compute special interval max to set xscale.
			//  if special interval increases, store new maxTime and redraw all the spikes
			SyncEvent ev = (SyncEvent) o;
			if (ev.isSpecial()) {
				int dt = ev.timestamp - lastSyncTime;
				lastSyncTime = ev.timestamp;
				if (dt > maxTime) {
					maxTime = dt;
					redrawPrevious = true;
				}
			}
		}
		if (!redrawing && redrawPrevious) {
			redrawSpikes(drawable);
		}
		for (Object o : ae) {
			// while iterating through events, also compute special interval max to set xscale.
			//  if special interval increases, store new maxTime and redraw all the spikes
			SyncEvent ev = (SyncEvent) o;
			int dt = ev.timestamp - lastSyncTime;
			if (ev.isSpecial()) {
				dt = 0;
				lastSyncTime = ev.timestamp;
				rasterNumber++;
				if (!redrawing && (rasterNumber > maxNumRasters)) {
					clear(gl);
				}
			}
			final float w = rasterWidth * maxTime; // spike raster as fraction of screen width
			switch (ev.type) {
				case 0:
					gl.glColor3f(0, 1, 0);
					gl.glBegin(GL.GL_TRIANGLES);		// Drawing Using Triangles
					gl.glVertex3f(dt, rasterNumber, 0);		// Top
					gl.glVertex3f(dt + w, rasterNumber, 0);		// Bottom Left
					gl.glVertex3f(dt + (w / 2), rasterNumber + 1, 0);		// Bottom Right
					gl.glEnd();
					break;
				case 1:
					gl.glColor3f(1, 0, 0);
					gl.glBegin(GL.GL_TRIANGLES);		// Drawing Using Triangles
					gl.glVertex3f(dt + (w / 2), rasterNumber, 0);		// Bottom Right
					gl.glVertex3f(dt + w, rasterNumber + 1, 0);		// Top
					gl.glVertex3f(dt, rasterNumber + 1, 0);		// Bottom Left
					gl.glEnd();
					break;
				case 2:
					gl.glColor3f(0, 0, 1);
					gl.glRectf(dt, rasterNumber, dt + w, rasterNumber + 1);
					break;
				default:
					gl.glColor3f(.1f, .1f, .1f);
			}
			//            CochleaGramDisplayMethod.typeColor(gl,ev.type);
			//            float t = (float) (ev.timestamp-lastSyncTime); // z goes from 0 (oldest) to 1 (youngest)
			if (!redrawing) {
				addSpike(ev);
			}

		}
		gl.glPushMatrix();
		// draw time axis
		gl.glColor3f(1, 1, 1);
		gl.glLineWidth(2f);
		gl.glBegin(GL.GL_LINES);
		gl.glVertex2f(0, 0);
		gl.glVertex2f(maxTime, 0);
		gl.glEnd();
		{
			String s = String.format("%ss", engFmt.format((maxTime / AEConstants.TICK_DEFAULT_US) * 1e-6f));
			final float scale = 0.2f;
			gl.glPushMatrix();
			gl.glLoadIdentity();
			gl.glLineWidth(.3f);
			float wid = glut.glutStrokeLengthf(GLUT.STROKE_ROMAN, s);
			gl.glTranslatef(drawable.getWidth() - 80, -30, 0);
			gl.glTranslatef(0, 0, 0);
			gl.glScalef(scale, scale, 1);
			glut.glutStrokeString(GLUT.STROKE_ROMAN, s);
			gl.glPopMatrix();
		}
		gl.glPopMatrix();
		gl.glFlush();
		getChipCanvas().checkGLError(gl, glu, "after display drawing");
	}
	EngineeringFormat engFmt = new EngineeringFormat();

	private GL2 setupMyGL(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
		gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
		gl.glOrtho(-BORDER, drawable.getWidth() + BORDER, -BORDER, drawable.getHeight() + BORDER, 10000, -10000);
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
		return gl;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals("fileopen")) {
			log.info("file was opened, adding property change listener to aefileinputstream for rewind events");
			clearScreenEnabled = true;
			ChipCanvas c = getChipCanvas();
			Chip2D chip = c.getChip();
			AEChip aeChip = (AEChip) chip;
			PropertyChangeSupport support = aeChip.getAeViewer().getAePlayer().getAEInputStream().getSupport();
			if (!support.hasListeners("rewind")) {
				support.addPropertyChangeListener(this);
			}
		} else if (evt.getPropertyName().equals("rewind")) {
			clearScreenEnabled = true;
		}
		//        else if (evt.getPropertyName().equals("paused")) {
		//            try {
		//                Boolean b = (Boolean) evt.getNewValue();
		//                paused = b.booleanValue();
		//            } catch (Exception e) {
		//                log.warning("caught " + e.toString() + " on event from AEViewer");
		//            }
		//
		//        }
	}

	@Override
	public void update(Observable o, Object arg) {
		if (addedPropertyChangeListener) {
			return;
		}
		try {
			ChipCanvas c = getChipCanvas();
			Chip2D chip = c.getChip();
			AEChip aeChip = (AEChip) chip;
			aeChip.getAeViewer().getAePlayer().getSupport().addPropertyChangeListener(this);
			addedPropertyChangeListener = true;
			log.info("added property change listener to AEViewer.AEPlayer");
		} catch (Exception e) {
			//            log.warning("caught when adding property change listener for rewinds: " + e.toString());
		}
	}

	@Override
	public void dispose(GLAutoDrawable arg0) {
		// TODO Auto-generated method stub

	}
}

