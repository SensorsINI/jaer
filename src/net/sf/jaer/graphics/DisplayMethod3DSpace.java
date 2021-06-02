/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import ch.unizh.ini.jaer.chip.multicamera.MultiDavisCameraChip;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import java.util.Iterator;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraApsDvsEvent;

/**
 * Displays events in a 3D space
 * @author Gemma
 */

public class DisplayMethod3DSpace extends DisplayMethod implements DisplayMethod3D {
    
    int numCam=1;
    
    public DisplayMethod3DSpace(ChipCanvas chipCanvas) {
        super(chipCanvas);
    }

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
        
        if(chip instanceof MultiDavisCameraChip){
            numCam=((MultiDavisCameraChip) chip).getNumCameras();
        }
        
        gl.glTranslatef(chip.getSizeX()/numCam, 0.0f, -chip.getSizeX());
        gl.glPushMatrix();
        gl.glClearColor(0.1f, 0.1f, 0.1f, 0);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        
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
            gl.glVertex3f(chip.getSizeX()/numCam, 0, 0);
            gl.glVertex3f(chip.getSizeX()/numCam, chip.getSizeY(), 0);
            gl.glVertex3f(0, chip.getSizeY(), 0);
            gl.glVertex3f(0, 0, 0);
            gl.glEnd();

            // Second face
            gl.glColor3f(1, 0, 0);
            gl.glLineWidth(2.0f);
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex3f(0, 0, 0);
            gl.glVertex3f(0, 0, chip.getSizeX()/numCam);
            gl.glVertex3f(0, chip.getSizeY(), chip.getSizeX()/numCam);
            gl.glVertex3f(0, chip.getSizeY(), 0);
            gl.glVertex3f(0, 0, 0);
            gl.glEnd();

            // Floor
            gl.glBegin(GL2.GL_QUADS);
            gl.glColor3f(0.2f, 0.2f, 0.2f);
            gl.glVertex3f(0.0f, 0.0f, 0.0f);
            gl.glVertex3f(0.0f, 0.0f, chip.getSizeX()/numCam);
            gl.glVertex3f(chip.getSizeX()/numCam, 0.0f, chip.getSizeX()/numCam);
            gl.glVertex3f(chip.getSizeX()/numCam, 0.0f, 0.0f);
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
        
        float x, y, z;
        while (evItr.hasNext()) {
            Object e = evItr.next();
            if(e instanceof  MultiCameraApsDvsEvent){
                MultiCameraApsDvsEvent ev = (MultiCameraApsDvsEvent) e;

                gl.glPushMatrix();

                // Estimate the 3D point position
                


                gl.glPopMatrix();
            }
        }
        
        
    }
    
}
