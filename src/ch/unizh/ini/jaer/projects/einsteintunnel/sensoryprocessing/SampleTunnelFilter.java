/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;
import javax.swing.*;
import java.awt.*;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.util.*;
import java.util.Observable;
import java.util.Observer;

/**
 *
 * @author braendch
 */
public class SampleTunnelFilter extends EventFilter2D implements Observer {

    public int csx, maxHistogramX;
    public int dsx = 504;
    public int dsy = 80;
    public int[] xHistogram;
    public double decayFactor = 0.9;

    public static String getDescription (){
        return "A demonstration sample for the Einstein Tunnel project on pedestrian traffic controlled LED panels";
    }
    private boolean histogramEnabled = getPrefs().getBoolean("HistogramFilter.histogramEnabled",true);

    public SampleTunnelFilter(AEChip chip) {
        super(chip);

        final String f = "Filters";
        setPropertyTooltip(f,"histogramEnabled","A simple histogram display filter");

        initFilter();
    }

    public void initFilter() {
        resetFilter();
    }

    synchronized public void resetFilter() {
        if(chip!=null){
            csx = chip.getSizeX();
            xHistogram = new int[dsx];
        }
        maxHistogramX = 1; // not 0 to avoid division by 0
        Arrays.fill(xHistogram,0);
    }

    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        if(!isFilterEnabled()) return in;
        if(getEnclosedFilter()!=null) in=getEnclosedFilter().filterPacket(in);
        if(getEnclosedFilterChain()!=null) in=getEnclosedFilterChain().filterPacket(in);

        for(BasicEvent e:in){
            xHistogram[e.x*dsx/csx] += 1;
        }
        if(histogramEnabled){
            checkHistogram();
            histogramCanvas.repaint();
            for(int i = 0; i<xHistogram.length; i++){
                xHistogram[i] = (int)(xHistogram[i]*decayFactor);
            }
        }

        return in;
    }

    GLU glu=null;
    JFrame histogramFrame=null;
    GLCanvas histogramCanvas=null;

    void checkHistogram(){
        if(histogramFrame==null || (histogramFrame!=null && !histogramFrame.isVisible())){
            createSimpleHistogram();
        }
    }

    void createSimpleHistogram(){
        histogramFrame=new JFrame("Histogram");
        Insets histogramInsets = histogramFrame.getInsets();
        histogramFrame.setSize(dsx+histogramInsets.left+histogramInsets.right, dsy+histogramInsets.bottom+histogramInsets.top);
        //histogramFrame.setSize(new Dimension(dsx,dsy));
        histogramFrame.setResizable(false);
        histogramFrame.setAlwaysOnTop(true);
        histogramFrame.setLocation(100, 100);
        histogramCanvas=new GLCanvas();
        histogramCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }

            synchronized public void display(GLAutoDrawable drawable) {
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                //iteration through the xHistogram
                for(int i = 0; i<dsx; i++){
                    if(xHistogram[i]>maxHistogramX) maxHistogramX = xHistogram[i];
                    gl.glColor3f(1,0,0);
                    gl.glRectf(i,0,i+1,xHistogram[i]*dsy/maxHistogramX);
                    //System.out.println("DSX: "+dsx/csx*i);
                    //System.out.println("histogram X: "+xHistogram[i]);
                }
                int error=gl.glGetError();
                if(error!=GL.GL_NO_ERROR){
                    if(glu==null) glu=new GLU();
                    log.warning("GL error number "+error+" "+glu.gluErrorString(error));
                }
            }

            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL gl=drawable.getGL();
                final int B=10;
                gl.glMatrixMode(GL.GL_PROJECTION);
                gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
                gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
                gl.glMatrixMode(GL.GL_MODELVIEW);
                gl.glViewport(0,0,width,height);
            }

            public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
            }
        });
        histogramFrame.getContentPane().add(histogramCanvas);
        //histogramFrame.pack();
        histogramFrame.setVisible(true);
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }
    
    public void update(Observable o, Object arg){
        resetFilter();
    }
}
