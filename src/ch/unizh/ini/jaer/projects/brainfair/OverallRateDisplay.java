/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.brainfair;

import java.awt.geom.Point2D;
import java.util.LinkedList;
import java.util.ListIterator;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.fixedfunc.GLLightingFunc;

/**
 * Displays the overall firing rate over time.
 * @author Michael Pfeiffer
 */
public class OverallRateDisplay extends GLCanvas implements GLEventListener {

	StatisticsCalculator statistics = null;

	private double maxOverallRate;

	public OverallRateDisplay(GLCapabilities caps) {
		super(caps);

		statistics = null;

		setLocale(java.util.Locale.US); // to avoid problems with other language support in JOGL

		this.setSize(300,300);
		setVisible(true);

		addGLEventListener(this);

		maxOverallRate = 0.0;

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

		if (statistics != null) {

			// System.out.println("In Display!");
			GL2 gl = getGL().getGL2();

			gl.glClear(GL2.GL_COLOR_BUFFER_BIT);

			gl.glLineWidth(1.0f);
			gl.glColor3f(0,1,0);


			LinkedList rateHistory = statistics.getOverallRate();

			ListIterator it = rateHistory.listIterator();

			double minTime = Integer.MAX_VALUE;
			double maxTime = Integer.MIN_VALUE;
			double minRate = Float.MAX_VALUE;
			double maxRate = Float.MIN_VALUE;

			// Plot firing rate curve
			int numP = 0;
			while (it.hasNext()) {

				Point2D p = (Point2D) it.next();
				double t = p.getX();
				double y = p.getY();
				if (t<minTime) {
					minTime = t;
				}
				if (t>maxTime) {
					maxTime = t;
				}
				if (y < minRate) {
					minRate = y;
				}
				if (y > maxRate) {
					maxRate = y;
				}

				numP++;
			}

			if (maxRate > maxOverallRate) {
				maxOverallRate = maxRate;
			}

			//System.out.println("Num Data Points: " + numP);
			//            System.out.println("XRange: " + "[" + minTime/1000.0 + ", " + maxTime/1000.0 + "]");
			//            System.out.println("YRange: " + "[" + minRate + ", " + maxRate + "]");
			//gl.glViewport((int) (minTime/1000.0), 0, (int)((maxTime-minTime)/1000.0), (int) (maxRate));
			//            gl.glViewport((int) (minTime/1000.0), (int) minRate, (int)((maxTime-minTime)/1000.0), (int) (maxRate));

			it = rateHistory.listIterator();
			double tRange = maxTime-minTime;
			// double yRange = maxOverallRate-minRate;
			double yRange = maxOverallRate;
			gl.glBegin(GL2.GL_LINE_STRIP);

			// System.out.println("yRange: " + yRange + "[" + minRate + " / " + maxRate + "]");

			while (it.hasNext()) {

				Point2D p = (Point2D) it.next();
				double t = p.getX();
				double y = p.getY();
				gl.glVertex2d(-1+((2.0*(t-minTime))/(tRange)),
					-1.0+((2.0*(y-minRate))/yRange));
				gl.glVertex2d(-1+((2.0*(t-minTime))/(tRange)),
					-1.0+((2.0*(y))/yRange));

				/*System.out.println(-1+2.0*(t-minTime)/(tRange) + " / " +
                        -1.0+2.0*(y-minRate)/yRange); */

                        numP++;
			}

			//gl.glVertex2d(minTime/1000.0, minRate);
			// gl.glVertex2d(maxTime/1000.0, maxRate);

			gl.glEnd();


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
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
		gl.glLoadIdentity();

		gl.glRasterPos3f(0, 0, 0);
		gl.glColor3f(1, 1, 1);
	}

	/**
	 * Sets a new data source
	 */
	public void setDataSource(StatisticsCalculator statistics) {
		this.statistics = statistics;
	}

	public void resetDisplay() {
		maxOverallRate = 0.0;
	}

	@Override
	public void dispose(GLAutoDrawable arg0) {
		// TODO Auto-generated method stub

	}

}
