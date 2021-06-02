/*
 * EyeTarget.java
 *
 * Created on September 5, 2006, 9:55 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright September 5, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.graphics;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLLightingFunc;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.geom.Point2D;
import java.util.Random;
import java.util.logging.Logger;


import net.sf.jaer.util.filter.LowpassFilter;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * A randomly moving and jumping target for test users to watch while having their eyes tracked. This can be used for calibration.
 * @author tobi
 */
public class EyeTarget extends GLCanvas implements GLEventListener {

	private static Logger log=Logger.getLogger("EyeTarget");

	final int SIZE=15;
	Target target;
	GLUquadric eyeQuad;

	// design capabilities of opengl canvas
	static final GLCapabilities caps=new GLCapabilities(null);
	static {
		caps.setDoubleBuffered(true);
		caps.setHardwareAccelerated(true);
		caps.setAlphaBits(8);
		caps.setRedBits(8);
		caps.setGreenBits(8);
		caps.setBlueBits(8);
	}

	public float getTargetX(){
		return target.x();
	}

	public float getTargetY(){
		return target.y();
	}

	public void setTargetSpeed(float speed){
		target.scale=speed;
	}

	public float getTargetSpeed(){
		return target.scale;
	}

	@Override
	public void repaint() {
	}

	class Target{
		Point2D.Float position=new Point2D.Float();
		float scale=300;
		long minSaccadeIntervalMs=500;
		float saccadeProb=.04f;
		LowpassFilter filterx=new LowpassFilter(),filtery=new LowpassFilter();
		Target(){
			position.x=getWidth()/2;
			position.y=getHeight()/2;
			filterx.setTauMs(1000);
			filtery.setTauMs(1000);
			filterx.setInternalValue(position.x);
			filtery.setInternalValue(position.y);

		}
		final float radius=0.05f;
		Random random=new Random();
		long lastSaccadeTimeMs=System.currentTimeMillis();
		void update(){
			long tMs=System.currentTimeMillis();
			int tus=(int)(tMs*1000);
			position.x=clip(filterx.filter(position.x+(scale*(random.nextFloat()-.5f)),tus),getWidth());
			position.y=clip(filtery.filter(position.y+(scale*(random.nextFloat()-.5f)),tus),getHeight());
			if(((tMs-lastSaccadeTimeMs)>minSaccadeIntervalMs) && (random.nextFloat()<saccadeProb)){
				position.x=random.nextFloat()*getWidth();
				position.y=random.nextFloat()*getHeight();
				filterx.setInternalValue(position.x);
				filtery.setInternalValue(position.y);
				log.info("saccade after "+(tMs-lastSaccadeTimeMs)+" ms");
				lastSaccadeTimeMs=tMs;
			}
			return;
		}
		float x(){
			return position.x;
		}
		float y(){
			return position.y;
		}
		float clip(float v, int lim){
			if(v<0) {
				v=0;
			}
			else if(v>lim) {
				v=lim;
			}
			return v;
		}
	}

	GLU glu;
	GLUT glut;
	/** Creates a new instance of EyeTarget */
	public EyeTarget() {
		super(caps);
		addGLEventListener(this);
		setAutoSwapBufferMode(true);
		glu=new GLU();
		glut=new GLUT();
		Dimension ss=Toolkit.getDefaultToolkit().getScreenSize();
		ss.setSize(ss.width/2,ss.height/2);
		setSize(ss);
		target=new Target();
	}

	/** Called by the drawable immediately after the OpenGL context is
     initialized. Can be used to perform one-time OpenGL
     initialization such as setup of lights and display lists. Note
     that this method may be called more than once if the underlying
     OpenGL context for the GLAutoDrawable is destroyed and
     recreated, for example if a GLCanvas is removed from the widget
     hierarchy and later added again.
	 */
	@Override
	public void init(GLAutoDrawable drawable){
		GL2 gl = drawable.getGL().getGL2();

		gl.setSwapInterval(1);
		gl.glShadeModel(GLLightingFunc.GL_FLAT);

		gl.glClearColor(0,0,0,0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		gl.glLoadIdentity();

		gl.glRasterPos3f(0,0,0);
		gl.glColor3f(1,1,1);
		glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,"Initialized display");

		reval();

	};

	/** Called by the drawable to initiate OpenGL rendering by the
     client. After all GLEventListeners have been notified of a
     display event, the drawable will swap its buffers if {@link
     GLAutoDrawable#setAutoSwapBufferMode setAutoSwapBufferMode} is
     enabled. */
	@Override
	public void display(GLAutoDrawable drawable){
		target.update();
		GL2 gl=drawable.getGL().getGL2();
		gl.glClearColor(0,0,0,0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		gl.glPushMatrix();
		gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
		gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
		gl.glOrtho(0,drawable.getSurfaceWidth() ,0,drawable.getSurfaceHeight(),10000,-10000);
		gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
		gl.glPopMatrix();
		gl.glPushMatrix();
		gl.glColor3f(1,1,1);
		gl.glRectf(target.x()-SIZE, target.y()-SIZE, target.x()+SIZE, target.y()+SIZE);
		//        gl.glColor3f(1,1,1);
		//        gl.glTranslatef(target.x(),target.y(),0);
		//        if(eyeQuad==null) eyeQuad = glu.gluNewQuadric();
		//        glu.gluQuadricDrawStyle(eyeQuad,GLU.GLU_FILL);
		//        glu.gluDisk(eyeQuad,0,5,16,1);
		gl.glPopMatrix();
	};

	/** Called by the drawable during the first repaint after the
     component has been resized. The client can update the viewport
     and view volume of the window appropriately, for example by a
     call to {@link com.jogamp.opengl.GL#glViewport}; note that for
     convenience the component has already called <code>glViewport(x,
     y, width, height)</code> when this method is called, so the
     client may not have to do anything in this method.
	 */
	/** called on reshape of canvas. Sets the scaling for drawing pixels to the screen. */
	@Override
	public synchronized void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		GL2 gl = drawable.getGL().getGL2();
		gl.glLoadIdentity();
		gl.glViewport(0,0,width,height);
	}

	void reval(){
		if (getParent() != null){
			invalidate();
			getParent().validate();
		}
	}

	/** Called by the drawable when the display mode or the display device
     associated with the GLAutoDrawable has changed. The two boolean parameters
     indicate the types of change(s) that have occurred. (<b> !!! CURRENTLY
     UNIMPLEMENTED !!! </b>)
     <P>

     An example of a display <i>mode</i> change is when the bit depth changes (e.g.,
     from 32-bit to 16-bit color) on monitor upon which the GLAutoDrawable is
     currently being displayed. <p>

     An example of a display <i>device</i> change is when the user drags the
     window containing the GLAutoDrawable from one monitor to another in a
     multiple-monitor setup. <p>

     The reason that this function handles both types of changes (instead of
     handling mode and device changes in separate methods) is so that
     applications have the opportunity to respond to display changes the most
     efficient manner. For example, the application may need make fewer
     adjustments to compensate for a device change if it knows that the mode
     on the new device is identical the previous mode.
	 */
	public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged){

	}

	@Override
	public void dispose(GLAutoDrawable arg0) {
		// TODO Auto-generated method stub

	};


}
