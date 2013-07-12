/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

/**
 *
 * @author Christian
 */
public class EdgeSegment extends Line2D.Float {
	public float phi;
	public Edge edge;
	public int evidence;
	public int timestamp;
	public Corner c1,c2;

	private Point2D closePoint;

	public EdgeSegment(EdgeFragments.Snakelet snakelet, Edge edg){
		phi = snakelet.phi;
		c1 = new Corner(snakelet.line.getP1(), snakelet, edg.constructor);
		c2 = new Corner(snakelet.line.getP2(), snakelet, edg.constructor);
		this.setLine(snakelet.line.getP1(), snakelet.line.getP2());
		evidence = 1;
		timestamp = snakelet.timestamp;
	}

	public EdgeSegment(Corner c1, Corner c2){
		this.c1 = c1;
		this.c2 = c2;
		this.setLine(c1, c2);
		evidence = 1;
		calculatePhi();
	}

	public boolean touches(EdgeSegment segment, float distTolerance){
		float dS1, dS2;
		dS1 = (float)ptSegDist(segment.getP1());
		dS2 = (float)ptSegDist(segment.getP2());
		if(dS1 < dS2){
			closePoint = segment.getP1();
		} else {
			closePoint = segment.getP2();
		}
		if((dS1<distTolerance) || (dS2<distTolerance)){
			return true;
		}else{
			return false;
		}
	}

	public boolean aligns(EdgeSegment segment, float oriTolerance){
		float dPhi;
		dPhi = getAngleDiff(phi, segment.phi);
		if(dPhi<oriTolerance){
			return true;
		}else{
			return false;
		}
	}

	public void merge(EdgeSegment segment){
		float dL = (float)ptLineDist(closePoint);
		int dir = relativeCCW(closePoint);
		float theta = (float)(((phi-dir)*Math.PI)/2.0);
		translateSegment((float)(Math.sin(theta)*dL), (float)(Math.cos(theta)*dL));
		phi = calculatePhi();
		evidence++;
		timestamp = segment.timestamp;
	}

	void translateSegment(float dX, float dY){
		if(edge == null){
			x1 = x1+dX;
			y1 = y1+dY;
			x2 = x2+dX;
			y2 = y2+dY;
		}else{
			edge.translate(dX, dY);
		}
	}

	float calculatePhi(){
		return (float)Math.atan2((x1-x2),(y1-y2));
	}

	float getAngleDiff(float angle1, float angle2){
		float diff = Math.abs(angle1-angle2);
		if(diff > Math.PI){
			diff = (float)(2*Math.PI)-diff;
		}
		return diff;
	}

	public void setEdge(Edge edg){
		edge = edg;
	}

	public void draw(GLAutoDrawable drawable){
		GL2 gl=drawable.getGL().getGL2();
		if(edge == null){
			gl.glLineWidth(2.0f);
			float s = evidence/5.0f;
			gl.glColor3f(s*1.0f,s*0.5f,s*0.5f);
		}else{
			gl.glLineWidth(3.0f);
			gl.glColor3f(0.5f+(0.5f*edge.color[0]),0.5f+(0.5f*edge.color[1]),0.5f+(0.5f*edge.color[2]));
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2d(x1,y1);
			gl.glVertex2d(x2,y2);
			gl.glEnd();
		}

	}

	@Override
	public Corner getP1(){
		return c1;
	}

	@Override
	public Corner getP2(){
		return c2;
	}
}
