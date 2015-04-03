/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.awt.geom.Point2D;
import java.util.LinkedList;
import java.util.ListIterator;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLLightingFunc;

/**
 * OpenGL display of race track
 * @author Michael Pfeiffer
 */
public class RaceDisplay extends GLCanvas implements GLEventListener {

	// The race track to display
	SlotcarTrack raceTrack;

	// The car object
	Slotcar myCar = null;

	// Step size of track
	float stepsize = 0.01f;

	public RaceDisplay(GLCapabilities caps) {
		super(caps);

		raceTrack = null;

		setLocale(java.util.Locale.US); // to avoid problems with other language support in JOGL

		// this.setSize(300,200);
		setVisible(true);

		addGLEventListener(this);

	}

	public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
		System.out.println("displayChanged");
	}

	@Override
	public synchronized void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		System.out.println("reshape");
	}

	@Override
	public synchronized void display(GLAutoDrawable drawable) {
		if (raceTrack != null) {
			LinkedList<Point2D.Float> allpoints = raceTrack.getSmoothPoints(stepsize);
			ListIterator<Point2D.Float> it = allpoints.listIterator();

			GL2 gl = getGL().getGL2();
			gl.glClear(GL.GL_COLOR_BUFFER_BIT);

			gl.glLineWidth(1.0f);
			gl.glColor3f(0,1,0);
			gl.glBegin(GL.GL_LINE_LOOP);


			while (it.hasNext()) {
				Point2D p = it.next();
				gl.glVertex2d((2*p.getX())-1, (2*p.getY())-1);
			}
			gl.glEnd();

			if (myCar != null) {
				// Move car
				myCar.drive();
				// Display car
				myCar.displayCar(gl);
			} else {
				System.out.println("No car defined!");
			}

			gl.glFlush();
		}

	}
	@Override
	public void init(GLAutoDrawable drawable) {

		System.out.println("init");

		GL2 gl = getGL().getGL2();

		gl.setSwapInterval(1);
		gl.glShadeModel(GLLightingFunc.GL_FLAT);

		gl.glClearColor(0, 0, 0, 0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		gl.glLoadIdentity();

		gl.glRasterPos3f(0, 0, 0);
		gl.glColor3f(1, 1, 1);
	}

	/**
	 * Sets a new track for the race.
	 * @param newTrack The new race track
	 */
	public void setTrack(SlotcarTrack newTrack, float stepsize) {
		raceTrack = newTrack;
		this.stepsize = stepsize;
	}


	/**
	 * Defines the driver and car for this race
	 * @param myCar The new car.
	 */
	public void setCar(Slotcar myCar) {
		this.myCar = myCar;
		myCar.setGL(getGL().getGL2());
	}

	@Override
	public void dispose(GLAutoDrawable arg0) {
		// TODO Auto-generated method stub

	}
}
