/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.brainfair;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.fixedfunc.GLLightingFunc;

/**
 * Displays the current Interspike-Interval distribution. Using code from ISIHistogrammer.
 * @author Michael Pfeiffer
 */
public class ISIDisplay extends GLCanvas implements GLEventListener {

	StatisticsCalculator statistics = null;

	public ISIDisplay(GLCapabilities caps) {
		super(caps);

		statistics = null;

		setLocale(java.util.Locale.US); // to avoid problems with other language support in JOGL

		this.setSize(300,300);
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

		if (statistics != null) {

			// System.out.println("In Display!");
			GL2 gl = getGL().getGL2();

			gl.glClear(GL.GL_COLOR_BUFFER_BIT);

			gl.glLineWidth(1.0f);
			gl.glColor3f(0,1,0);

			int[] bins = statistics.getISIBins();
			int nBins = statistics.getNumISIBins();
			int maxValue = statistics.getMaxBin();

			gl.glBegin(GL.GL_LINE_STRIP);

			for (int i=0; i<nBins; i++) {
				gl.glVertex2f(-1.0f+((2.0f*i)/nBins), -1.0f+((2.0f*(bins[i]))/maxValue));
				// System.out.println(bins[i] + " / " + maxValue);
			}
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
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
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

	@Override
	public void dispose(GLAutoDrawable arg0) {
		// TODO Auto-generated method stub

	}


}
