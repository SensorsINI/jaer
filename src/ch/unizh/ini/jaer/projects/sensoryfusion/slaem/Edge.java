/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.util.concurrent.CopyOnWriteArrayList;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

/**
 *
 * @author Christian
 */
public class Edge {
	public CopyOnWriteArrayList<Corner> corners;
	public float[] color;
	public EdgeConstructor constructor;

	public Edge(Corner c1, Corner c2, EdgeConstructor cnstrctr){
		corners = new CopyOnWriteArrayList<Corner>();
		c1.setEdge(this);
		corners.add(c1);
		c2.setEdge(this);
		corners.add(c2);
		color = new float[3];
		color[0] = (float)Math.random();
		color[1] = (float)Math.random();
		color[2] = (float)Math.random();
		constructor = cnstrctr;
	}

	public void addCorner(Corner newCorner, Corner close){
		newCorner.setEdge(this);
		int idx = findIndex(newCorner);
		corners.add(idx, newCorner);
	}

	public boolean removeCorner(Corner toRemove){
		corners.remove(toRemove);
		return !(corners.size()>1);
	}

	public boolean checkValidity(){
		boolean pass = true;
		for(Corner corner:corners){
			if(!constructor.corners.contains(corner)){
				corners.remove(corner);
			}
		}
		if(corners.size()<2) {
			pass = false;
		}
		return pass;
	}

	public void remove(){
		for(Corner corner:corners){
			if(corner.hasEdge()) {
				corner.edge = null;
			}
		}
	}

	public void translate(float dX, float dY){
		for(Corner corner:corners){
			double pX = corner.getX();
			double pY = corner.getY();
			corner.setLocation(pX+dX, pY+dY);
		}
	}

	private int findIndex(Corner newCorner){
		int idx = 0;
		double minDist = 2*corners.get(0).distance(newCorner);
		for(int i=1; i<corners.size(); i++){
			double dist = corners.get(i-1).distance(newCorner)+corners.get(i).distance(newCorner);
			if(dist<minDist){
				minDist = dist;
				idx = i;
			}
		}
		if((2*corners.get(corners.size()-1).distance(newCorner))<minDist) {
			idx=corners.size();
		}
		return idx;
	}

	public void draw(GLAutoDrawable drawable){
		GL2 gl=drawable.getGL().getGL2();
		gl.glLineWidth(3.0f);
		gl.glColor3f(0.5f+(0.5f*color[0]),0.5f+(0.5f*color[1]),0.5f+(0.5f*color[2]));
		gl.glBegin(GL.GL_LINE_STRIP);
		for(Corner corner : corners){
			gl.glVertex2d(corner.x,corner.y);
		}
		gl.glEnd();
	}
}
