/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.brainfair;

import javax.media.opengl.*;

/**
 * Displays the orientation statistics
 * @author Michael Pfeiffer
 */
public class OrientationDisplay extends GLCanvas implements GLEventListener {
    

    StatisticsCalculator statistics = null;

    public OrientationDisplay(GLCapabilities caps) {
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


        float sumHist = 0.0f;
        float[] orientHistory = statistics.getOrientHistory();
        int i;

        for (i=0; i<4; i++)
            sumHist+=orientHistory[i];
        
            // Draw orientation pictograms
            // Horizontal
            gl.glColor3f(1, 0, 0);
            gl.glLineWidth(5.0f);
            gl.glBegin(GL.GL_LINE);
            gl.glVertex2f(-0.75f, 0.85f);
            gl.glVertex2f(-0.45f, 0.85f);
            gl.glEnd();

            // Vertical
            gl.glColor3f(0, 0, 1);
            gl.glBegin(GL.GL_LINE);
            gl.glVertex2f(0.2f, 0.99f);
            gl.glVertex2f(0.2f, 0.70f);
            gl.glEnd();
            
            // 45 degrees
            gl.glColor3f(0, 1, 0);
            gl.glBegin(GL.GL_LINE);
            gl.glVertex2f(-0.3f, 0.7f);
            gl.glVertex2f(-0.1f, 0.99f);
            gl.glEnd();

            // 135 degrees
            gl.glColor3f(1, 0, 1);
            gl.glBegin(GL.GL_LINE);
            gl.glVertex2f(0.7f, 0.7f);
            gl.glVertex2f(0.5f, 0.99f);
            gl.glEnd();
            
            gl.glColor3f(1,1,1);
            gl.glLineWidth(1.0f);
         
            float x, y;
            float histWidth = 0.4f;
            float histHeight = 1.6f;
            for (i=0; i<4; i++) {
                gl.glBegin(GL.GL_LINE_LOOP);
                    x = -0.8f + histWidth*i;
                    y = -0.8f+histHeight * ((float) orientHistory[i] / (float) sumHist);
                    gl.glVertex2f(x,-0.8f);
                    gl.glVertex2f(x,y);
                    gl.glVertex2f(x+histWidth, y);
                    gl.glVertex2f(x+histWidth, -0.8f);
                    gl.glEnd();
            }
  
            
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
