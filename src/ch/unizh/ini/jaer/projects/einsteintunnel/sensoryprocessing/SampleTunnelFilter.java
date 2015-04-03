/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFrame;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *A demonstration sample for the Einstein Tunnel project on pedestrian traffic controlled LED panels
 * @author braendch
 */
@Description("A demonstration sample for the Einstein Tunnel project on pedestrian traffic controlled LED panels")
public class SampleTunnelFilter extends EventFilter2D implements Observer {

	public int csx, maxHistogramX;
	public int dsx = 504;
	public int dsy = 80;
	public int[] xHistogram;
	public double decayFactor = 0.9;

	private boolean histogramEnabled = getPrefs().getBoolean("HistogramFilter.histogramEnabled",true);

	public SampleTunnelFilter(AEChip chip) {
		super(chip);

		final String f = "Filters";
		setPropertyTooltip(f,"histogramEnabled","A simple histogram display filter");

		initFilter();
	}

	@Override
	public void initFilter() {
		resetFilter();
	}

	@Override
	synchronized public void resetFilter() {
		if(chip!=null){
			csx = chip.getSizeX();
			xHistogram = new int[dsx];
		}
		maxHistogramX = 1; // not 0 to avoid division by 0
	}

	@Override
	synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {

		if(!isFilterEnabled()) {
			return in;
		}
		if(getEnclosedFilter()!=null) {
			in=getEnclosedFilter().filterPacket(in);
		}
		if(getEnclosedFilterChain()!=null) {
			in=getEnclosedFilterChain().filterPacket(in);
		}

		for(BasicEvent e:in){
			xHistogram[(e.x*dsx)/csx] += 1;
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
		if((histogramFrame==null) || ((histogramFrame!=null) && !histogramFrame.isVisible())){
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
			@Override
			public void init(GLAutoDrawable drawable) {
			}

			@Override
			synchronized public void display(GLAutoDrawable drawable) {
				GL2 gl=drawable.getGL().getGL2();
				gl.glLoadIdentity();
				gl.glClearColor(0,0,0,0);
				gl.glClear(GL.GL_COLOR_BUFFER_BIT);
				//iteration through the xHistogram
				for(int i = 0; i<dsx; i++){
					if(xHistogram[i]>maxHistogramX) {
						maxHistogramX = xHistogram[i];
					}
					gl.glColor3f(1,0,0);
					gl.glRectf(i,0,i+1,(xHistogram[i]*dsy)/maxHistogramX);
					//System.out.println("DSX: "+dsx/csx*i);
					//System.out.println("histogram X: "+xHistogram[i]);
				}
				int error=gl.glGetError();
				if(error!=GL.GL_NO_ERROR){
					if(glu==null) {
						glu=new GLU();
					}
					log.warning("GL error number "+error+" "+glu.gluErrorString(error));
				}
			}

			@Override
			synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
				GL2 gl=drawable.getGL().getGL2();
				final int B=10;
				gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
				gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
				gl.glOrtho(-B,drawable.getSurfaceWidth()+B,-B,drawable.getSurfaceHeight()+B,10000,-10000);
				gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
				gl.glViewport(0,0,width,height);
			}

			@Override
			public void dispose(GLAutoDrawable arg0) {
				// TODO Auto-generated method stub

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

	@Override
	public void update(Observable o, Object arg){
		resetFilter();
	}
}
