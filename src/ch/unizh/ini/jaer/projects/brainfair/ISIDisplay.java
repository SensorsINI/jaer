/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.brainfair;

import javax.media.opengl.*;

/**
 * Displays the current Interspike-Interval distribution. Using code from ISIHistogrammer.
 * @author Michael Pfeiffer
 */
public class ISIDisplay extends GLCanvas implements GLEventListener {

    StatisticsCalculator statistics = null;
    
    public ISIDisplay(GLCapabilities caps) {
        super(caps);

        statistics = null;

        this.setLocale(java.util.Locale.US); // to avoid problems with other language support in JOGL

        this.setSize(300,300);
        this.setVisible(true);

        addGLEventListener(this);
        
    }
    
    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
            System.out.println("displayChanged");
    }

    public synchronized void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        System.out.println("reshape");
    }

    public synchronized void display(GLAutoDrawable drawable) {
        
        if (statistics != null) {

            // System.out.println("In Display!");
            GL gl = this.getGL();
            
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        
            gl.glLineWidth(1.0f);
            gl.glColor3f(0,1,0);

            int[] bins = statistics.getISIBins();
            int nBins = statistics.getNumISIBins();
            int maxValue = statistics.getMaxBin();
            
            gl.glBegin(GL.GL_LINE_STRIP);
            
            for (int i=0; i<nBins; i++) {
                gl.glVertex2f(-1.0f+(2.0f*i)/nBins, -1.0f+2.0f*((float) bins[i])/maxValue);
                // System.out.println(bins[i] + " / " + maxValue);
            }
            gl.glEnd();

            gl.glFlush();
        }

    }
    public void init(GLAutoDrawable drawable) {

        System.out.println("init");

        GL gl = getGL();

        gl.setSwapInterval(1);
        gl.glShadeModel(GL.GL_FLAT);

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

    
}
