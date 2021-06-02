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

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;

import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraEvent;
import net.sf.jaer.util.EngineeringFormat;

import com.jogamp.opengl.util.gl2.GLUT;
import net.sf.jaer.event.MultiCameraApsDvsEvent;

/**
 * Displays events in space time
 * @author tobi
 */
public class TwoCamera3DDisplayMethod extends DisplayMethod implements DisplayMethod3D {

    EngineeringFormat engFmt = new EngineeringFormat();

    /**
     * Creates a new instance of SpaceTimeEventDisplayMethod
     */
    public TwoCamera3DDisplayMethod(ChipCanvas chipCanvas) {
        super(chipCanvas);
    }
    boolean spikeListCreated = false;
    int spikeList = 1;
    GLUT glut = null;
    GLU glu = null;
    final boolean useCubeEnabled = false; // true is too false or uses GPU improperly

    @Override
	public void display(GLAutoDrawable drawable) {
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
            gl.glTranslatef(0.0f, 0.0f, -chip.getSizeX());
            gl.glPushMatrix();
            gl.glClearColor(0.1f, 0.1f, 0.1f, 0);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);

            if (useCubeEnabled) {
                if (!spikeListCreated) {
                    spikeList = gl.glGenLists(1);
                    gl.glNewList(spikeList, GL2.GL_COMPILE);
                    {
                        //gl.glScalef(1, 1, .1f);
                        //gl.glScalef(1.0d/128, 1.0/128, .1f);
                        glut.glutSolidCube(1);
                        spikeListCreated=true;
                    }
                    gl.glEndList();
                }
            }
            // rotate and align viewpoint
            gl.glRotatef(getChipCanvas().getAngley(), 0, 1, 0); // rotate viewpoint by angle deg around the upvector
            gl.glRotatef(getChipCanvas().getAnglex(), 1, 0, 0); // rotate viewpoint by angle deg around the upvector
            gl.glTranslatef(getChipCanvas().getOrigin3dx(), getChipCanvas().getOrigin3dy(), 0);

            // draw 3d axes
            {
                // First face
                gl.glColor3f(0, 0, 1);
                gl.glLineWidth(2.0f);
                gl.glBegin(GL.GL_LINE_LOOP);
                gl.glVertex3f(0, 0, 0);
                gl.glVertex3f(chip.getSizeX(), 0, 0);
                gl.glVertex3f(chip.getSizeX(), chip.getSizeY(), 0);
                gl.glVertex3f(0, chip.getSizeY(), 0);
                gl.glVertex3f(0, 0, 0);
                gl.glEnd();

                // Second face
                gl.glColor3f(1, 0, 0);
                gl.glLineWidth(2.0f);
                gl.glBegin(GL.GL_LINE_LOOP);
                gl.glVertex3f(0, 0, 0);
                gl.glVertex3f(0, 0, chip.getSizeX());
                gl.glVertex3f(0, chip.getSizeY(), chip.getSizeX());
                gl.glVertex3f(0, chip.getSizeY(), 0);
                gl.glVertex3f(0, 0, 0);
                gl.glEnd();

                // Floor
                gl.glBegin(GL2.GL_QUADS);
                    gl.glColor3f(0.2f, 0.2f, 0.2f);
                    gl.glVertex3f(0.0f, 0.0f, 0.0f);
                    gl.glVertex3f(0.0f, 0.0f, chip.getSizeX());
                    gl.glVertex3f(chip.getSizeX(), 0.0f, chip.getSizeX());
                    gl.glVertex3f(chip.getSizeX(), 0.0f, 0.0f);
                gl.glEnd();
            }


            // render events
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

            Iterator evItr = packet.iterator();
            if(packet instanceof ApsDvsEventPacket){
                ApsDvsEventPacket apsPacket = (ApsDvsEventPacket) packet;
                evItr = apsPacket.fullIterator();
            }

            float x, y, z;
            while (evItr.hasNext()) {
                Object e = evItr.next();
                if(e instanceof MultiCameraEvent){
                    MultiCameraEvent ev = (MultiCameraEvent) e;
                    {
                        gl.glPushMatrix();

                        // Assign X-Y or Y-Z based on camera
                        if(ev.getCamera() == 0){
                            x = ev.x;
                            y = ev.y;
                            z = 0.0f;
                        }
                        else {
                            x = 0;
                            y = ev.y;
                            z = ev.x;
                        }
                        computeRGBFromZ(z);
                        gl.glColor3fv(rgb, 0);
                        if (useCubeEnabled) {
                            gl.glTranslatef(x, y, z);
                            gl.glCallList(spikeList);
                        } else {
                            gl.glTranslatef(x, y, z);
                            gl.glRectf(0 - .5f, 0 - .5f, 0 + .5f, 0 + .5f);
                        }
                        gl.glPopMatrix();
                    }
                }
                //chiedere a luca come sistemarlo in modo piú elegante!!!!!!!!!!!!!!!!!!!!!
                if(e instanceof MultiCameraApsDvsEvent){
                    MultiCameraApsDvsEvent ev = (MultiCameraApsDvsEvent) e;
                    {
                        gl.glPushMatrix();

                        // Assign X-Y or Y-Z based on camera
                        if(ev.getCamera() == 0){
                            x = ev.x;
                            y = ev.y;
                            z = 0.0f;
                        }
                        else {
                            x = 0;
                            y = ev.y;
                            z = ev.x;
                        }
                        computeRGBFromZ(z);
                        gl.glColor3fv(rgb, 0);
                        if (useCubeEnabled) {
                            gl.glTranslatef(x, y, z);
                            gl.glCallList(spikeList);
                        } else {
                            gl.glTranslatef(x, y, z);
                            gl.glRectf(0 - .5f, 0 - .5f, 0 + .5f, 0 + .5f);
                        }
                        gl.glPopMatrix();
                    }
                }
            }

            // draw axes labels x,y,z. See tutorial at http://jerome.jouvie.free.fr/OpenGl/Tutorials/Tutorial18.php
            int font = GLUT.BITMAP_HELVETICA_18;
            {
                gl.glPushMatrix();
                final int FS = 1; // distance in pixels of text from endZoom of axis
                gl.glRasterPos3f(chip.getSizeX() + FS, 0, 0);
                glut.glutBitmapString(font, "x=" + chip.getSizeX());
                gl.glRasterPos3f(0, chip.getSizeY() + FS, 0);
                glut.glutBitmapString(font, "y=" + chip.getSizeY());
                // label time end value
                gl.glRasterPos3f(0, 0, chip.getMaxSize() + FS);
                glut.glutBitmapString(font, "z=" + chip.getSizeX());
                gl.glPopMatrix();
            }
            checkGLError(gl);
            gl.glPopMatrix();
        }

    }

    void checkGLError(GL gl) {
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