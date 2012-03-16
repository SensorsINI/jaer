/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.brainfair;

import javax.media.opengl.*;

/**
 * Displays the current firing rate of every pixel.
 * @author Michael Pfeiffer
 */
public class PixelDisplay extends GLCanvas implements GLEventListener {
    
    StatisticsCalculator statistics = null;
    
    public PixelDisplay(GLCapabilities caps) {
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

            int[] image = statistics.getPixelHistogram();
            int maxValue = statistics.getMaxPixelBin();
            
            float dx = 2.0f / 128.0f;
            int idx = 0;
            for (int i=0; i<128; i++) {
                float y = -1+2.0f*i/128.0f;
                
                for (int j=0; j<128; j++) {
                    float x = -1+2.0f*j/128.0f;
                    
                    double c = Math.sqrt((double) image[idx] / (double) maxValue);
                    // System.out.println(c + " / " + image[idx]);
                    idx++;
                    
                    gl.glBegin(GL.GL_POLYGON);
                    gl.glColor3d(c,c,c);
                    gl.glVertex2f(x,y);
                    gl.glVertex2f(x+dx,y);
                    gl.glVertex2f(x+dx, y+dx);
                    gl.glVertex2f(x, y+dx);
                    gl.glEnd();
                }
                
                // System.out.println(bins[i] + " / " + maxValue);
            }

            gl.glFlush();
        }

    }
    
    public void resetDisplay() {
        
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
