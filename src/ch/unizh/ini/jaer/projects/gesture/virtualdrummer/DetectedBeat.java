/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;


import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.geom.Point2D;


import net.sf.jaer.eventprocessing.tracking.ClusterInterface;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * A detected drum beat. Includes countdown counter for rendering and method to render indication of beat.
 *
 * @author tobi
 */
public class DetectedBeat {

	private static GLUT glut = new GLUT();
	private long sysTimeDetected = System.currentTimeMillis();
	private ClusterInterface cluster;
	final int FRAMES_TO_RENDER = 30;
	private int framesLeftToRender = FRAMES_TO_RENDER;
	private Point2D.Float location;
	String string;

	private float force; // to hold force of beat


	public DetectedBeat(ClusterInterface cluster, String s) {
		this.cluster = cluster;
		location=(Point2D.Float)cluster.getLocation().clone();
		string=s;
	}

	public boolean isDoneRendering() {
		return framesLeftToRender <= 0;
	}

	/** Draws the beat on the cluster location.
	 *
	 * @param drawable the GL drawable to draw on.
	 */
	public void draw(GLAutoDrawable drawable) {
		if (isDoneRendering()) {
			return;
		}
		GL2 gl = drawable.getGL().getGL2(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
		gl.glPushMatrix();
		gl.glColor3f(0, 1, 0); // green
		gl.glLineWidth(3); // will get scaled
		final int pos = 10;
		final String beatString = string;

		// render string at location of cluster. size of string is sized to match cluster size

		float sw = glut.glutStrokeLength(GLUT.STROKE_ROMAN, beatString); // length in model space
		float cw = cluster.getRadius() ; // cluster size (actually radius, to make string half total width)
		float scale = cw / sw; // scaling to make string come out size of cluster /2

		// set origin to put string centered on cluster
		gl.glTranslatef(location.x - (cw/2), location.y, 0);
		gl.glScalef(scale, scale, 1); // scale transform to make string right size

		glut.glutStrokeString(GLUT.STROKE_ROMAN, beatString); // stroke the string
		gl.glPopMatrix();
		framesLeftToRender--;  // decrease counter for rendering
	}
}
