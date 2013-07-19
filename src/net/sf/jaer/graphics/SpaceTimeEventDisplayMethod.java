/*
 * ChipRendererDisplayMethod.java
 *
 * Created on May 4, 2006, 9:07 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 4, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.graphics;


import java.util.Iterator;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;

import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.util.EngineeringFormat;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Displays events in space time
 * @author tobi
 */
public class SpaceTimeEventDisplayMethod extends DisplayMethod implements DisplayMethod3D {

	EngineeringFormat engFmt = new EngineeringFormat();

	/**
	 * Creates a new instance of SpaceTimeEventDisplayMethod
	 */
	public SpaceTimeEventDisplayMethod(ChipCanvas chipCanvas) {
		super(chipCanvas);
	}
	boolean spikeListCreated = false;
	int spikeList = 1;
	GLUT glut = null;
	GLU glu = null;
	final boolean useCubeEnabled = false; // true is too false or uses GPU improperly

	// TODO it doesn't make sense to redraw all data for each mouse drag transform change. All we should have to do is to change the projection matrix.

	@Override
	public void display(GLAutoDrawable drawable) {
		//        GL2 gl=setupGL(drawable);
		//        AEChipRenderer renderer = (AEChipRenderer) (getChipCanvas().getRenderer());
		//        log.info("display");
		Chip2D chip = getChipCanvas().getChip();
		if (glut == null) {
			glut = new GLUT();
		}
		GL2 gl = drawable.getGL().getGL2();
		if (gl == null) {
			log.warning("null GL context - not displaying");
			return;
		}
		{
			gl.glPushMatrix();
			gl.glClearColor(0, 0, 0, 0);
			gl.glClear(GL.GL_COLOR_BUFFER_BIT);

			if (useCubeEnabled) {
				if (!spikeListCreated) {
					spikeList = gl.glGenLists(1);
					gl.glNewList(spikeList, GL2.GL_COMPILE);
					{
						gl.glScalef(1, 1, .1f);
						glut.glutSolidCube(1);
						spikeListCreated=true;
						//            gl.glRectf(.5f,.5f, .5f,.5f);
					}
					gl.glEndList();
				}
			}
			// rotate viewpoint

			gl.glRotatef(getChipCanvas().getAngley(), 0, 1, 0); // rotate viewpoint by angle deg around the upvector
			gl.glRotatef(getChipCanvas().getAnglex(), 1, 0, 0); // rotate viewpoint by angle deg around the upvector

			gl.glTranslatef(getChipCanvas().getOrigin3dx(), getChipCanvas().getOrigin3dy(), 0);

			// draw 3d axes
			gl.glColor3f(0, 0, 1);
			gl.glLineWidth(.4f);

			{
				gl.glBegin(GL2.GL_LINES);
				gl.glVertex3f(0, 0, 0);
				gl.glVertex3f(chip.getSizeX(), 0, 0);

				gl.glVertex3f(0, 0, 0);
				gl.glVertex3f(0, chip.getSizeY(), 0);

				gl.glVertex3f(0, 0, 0);
				gl.glVertex3f(0, 0, chip.getMaxSize());
				gl.glEnd();
			}


			// render events

			//        AEPacket2D ae = renderer.getAe();
			EventPacket packet = (EventPacket) chip.getLastData();
			if (packet == null) {
				log.warning("null packet to render");
				gl.glPopMatrix();
				return;
			}
			int n = packet.getSize();
			if (n == 0) {
				gl.glPopMatrix();
				return;
			}
			//        if(ae==null || ae.getNumEvents()==0) return;
			//        int n = ae.getNumEvents();
			int t0 = packet.getFirstTimestamp();
			int dt = packet.getDurationUs() + 1;
			//        int t0 = ae.getFirstTimestamp();
			//        int dt = ae.getLastTimestamp()-t0+1;
			float z;
			float zfac = chip.getMaxSize();
			//            int count=0;
			Iterator evItr = packet.iterator();
			if(packet instanceof ApsDvsEventPacket){
				ApsDvsEventPacket apsPacket = (ApsDvsEventPacket) packet;
				evItr = apsPacket.fullIterator();
			}


			while (evItr.hasNext()) {
				BasicEvent ev = (BasicEvent) evItr.next();
				//                if(count++%1000==0)log.info("1000 events");
				//        for (int i = 0; i<n; i++){
				//            EventXYType ev = ae.getEvent2D(i);
				z = (float) (ev.timestamp - t0) / dt; // z goes from 0 (oldest) to 1 (youngest)
				{
					gl.glPushMatrix();
					z = (float) (ev.timestamp - t0) / dt; // z goes from 0 (oldest) to 1 (youngest)
					computeRGBFromZ(z);
					gl.glColor3fv(rgb, 0);
					if (useCubeEnabled) {
						gl.glTranslatef(ev.x, ev.y, z * zfac);
						gl.glCallList(spikeList);
					} else {
						gl.glTranslatef(0, 0, z * zfac);
						gl.glRectf(ev.x - .5f, ev.y - .5f, ev.x + .5f, ev.y + .5f);
					}
					gl.glPopMatrix();
				}
			}
			// draw axes labels x,y,t. See tutorial at http://jerome.jouvie.free.fr/OpenGl/Tutorials/Tutorial18.php
			int font = GLUT.BITMAP_HELVETICA_18;
			{
				gl.glPushMatrix();
				final int FS = 1; // distance in pixels of text from endZoom of axis
				gl.glRasterPos3f(chip.getSizeX() + FS, 0, 0);
				glut.glutBitmapString(font, "x=" + chip.getSizeX());
				gl.glRasterPos3f(0, chip.getSizeY() + FS, 0);
				glut.glutBitmapString(font, "y=" + chip.getSizeY());
				// label time end value
				//            gl.glRasterPos3f(0, -2 , chip.getMaxSize() + FS);
				//            glut.glutBitmapCharacter(font, '0');
				gl.glRasterPos3f(0, 0, chip.getMaxSize() + FS);
				glut.glutBitmapString(font, "t=" + engFmt.format(dt * AEConstants.TICK_DEFAULT_US * 1e-6f) + "s");
				gl.glPopMatrix();
			}

			//            log.info("done rendering");
			checkGLError(gl);
			gl.glPopMatrix();
		}
	}

	void checkGLError(GL2 gl) {
		int error = gl.glGetError();
		int nerrors = 10;
		while ((error != GL.GL_NO_ERROR) && (nerrors-- != 0)) {
			if (glu == null) {
				glu = new GLU();
			}
			log.warning("GL error number " + error + " " + glu.gluErrorString(error));
			error = gl.glGetError();
		}
	}
	protected float[] rgb = new float[3];

	protected final void computeRGBFromZ(float z) {
		rgb[0] = z;
		rgb[1] = 1 - z;
		rgb[2] = 0;
	}
}
