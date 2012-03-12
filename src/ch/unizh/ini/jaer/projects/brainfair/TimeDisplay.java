/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.brainfair;

import javax.media.opengl.*;

/**
 * Displays the last firing time of every pixel.
 * @author Michael Pfeiffer
 */
public class TimeDisplay extends GLCanvas implements GLEventListener {
    StatisticsCalculator statistics = null;
    
    public TimeDisplay(GLCapabilities caps) {
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

    /** Converts from HSV to RGB  (assuming */
    public static float[] HSV2RGB(float hue, float saturation, float value) {
        int h1 = (int) Math.floor(hue / 60.0f);
        float f = (hue/60.0f - h1);
        float p = value*(1-saturation);
        float q = value*(1-saturation*f);
        float t = value*(1-saturation*(1-f));

        float[] tmp = new float[3];
        switch (h1) {
            case 0: tmp[0]=value; tmp[1]=t; tmp[2]=p; break;
            case 6: tmp[0]=value; tmp[1]=t; tmp[2]=p; break;
            case 1: tmp[0]=q; tmp[1]=value; tmp[2]=p; break;
            case 2: tmp[0]=p; tmp[1]=value; tmp[2]=t; break;
            case 3: tmp[0]=p; tmp[1]=q; tmp[2]=value; break;
            case 4: tmp[0]=t; tmp[1]=p; tmp[2]=value; break;
            case 5: tmp[0]=value; tmp[1]=p; tmp[2]=q; break;
        }

        return tmp;
    }

    
    
    public synchronized void display(GLAutoDrawable drawable) {
        
        if (statistics != null) {

            // System.out.println("In Display!");
            GL gl = this.getGL();
            
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        
            gl.glLineWidth(1.0f);
            gl.glColor3f(0,1,0);

            int[] image = statistics.getPixelTime();
            int maxValue = statistics.getMaxPixelTime();
            int range = statistics.getTimeWindowWidth();
            
            float dx = 2.0f / 128.0f;
            int idx = 0;
            for (int i=0; i<128; i++) {
                float y = -1+2.0f*i/128.0f;
                
                for (int j=0; j<128; j++) {
                    float x = -1+2.0f*j/128.0f;
                    
                    float c = ((float) (maxValue-image[idx])) / ((float) range*1000f);
                    float[] rgbColor;
                    
                    if (c <= 1) {
                        rgbColor = HSV2RGB(c*360.0f,1.0f,1.0f);
                    } else {
                        rgbColor = new float[3];
                        rgbColor[0] = rgbColor[1] = rgbColor[2] = 0.0f;
                    }
                    
                    // System.out.println(rgbColor[0] + " / " + rgbColor[1] + " / " + rgbColor[2]);
                    
                    // double c = Math.sqrt((double) image[idx] / (double) maxValue);
                    // System.out.println(c + " / " + image[idx]);
                    idx++;
                    
                    gl.glBegin(GL.GL_POLYGON);
                    gl.glColor3d(rgbColor[0],rgbColor[1],rgbColor[2]);
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
