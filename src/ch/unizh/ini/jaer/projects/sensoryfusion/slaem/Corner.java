/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.awt.geom.Point2D;
import java.util.ArrayList;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.sensoryfusion.slaem.EdgeFragments.Snakelet;

/**
 *
 * @author Christian
 */
public class Corner extends Point2D{

	boolean angled;
	double x,y;
	ArrayList<Snakelet> snakelets;
	EdgeConstructor constructor;
	Edge edge;

	public int blankScore = -10;

	public Corner(Point2D point, Snakelet snklet, EdgeConstructor constructor){
		x = point.getX();
		y = point.getY();
		snakelets = new ArrayList<Snakelet>();
		snakelets.add(snklet);
		angled = false;
		edge = null;
		this.constructor = constructor;
	}

	public boolean toAdd(Snakelet snakelet){
		boolean added = false;
		if(((snakelet.line.x1 == x) && (snakelet.line.y1 == y)) || ((snakelet.line.x2 == x) && (snakelet.line.y2 == y))){
			snakelets.add(snakelet);
			added = true;
			if(!angled) {
				setAngled();
			}
		}
		return added;
	}

	public boolean toRemove(Snakelet snakelet){
		if(snakelets.contains(snakelet)){
			snakelets.remove(snakelet);
		}
		if(snakelets.size()<2) {
			angled = false;
		}
		return snakelets.isEmpty();
	}

	public void merge(Corner other){
		x=other.x;
		y=other.y;
		snakelets = other.snakelets;
		other.clearSnakelets();
	}

	public void clearSnakelets(){
		snakelets.clear();
	}

	private void setAngled(){
		int maxScore = 0;
		Corner best = null;
		for(Corner crnr:constructor.corners){
			if(crnr.angled && (distance(crnr)<1.1)){
				crnr.merge(this);
			}
			if(crnr.angled){
				int score = 0;
				score = getScore(crnr);
				if(score>maxScore){
					maxScore = score;
					best = crnr;
				}
				//System.out.println(score);
			}
		}
		if(maxScore > 0) {
			if(best.hasEdge()){
				best.edge.addCorner(this, best);
			}else{
				constructor.newEdge(this, best);
			}
		}
		angled = true;
	}

	private int getScore(Corner other){
		int score=0;
		double dX = other.x-x;
		double dY = other.y-y;
		if((dX != 0) || (dY != 0)){
			if(Math.abs(dX)>Math.abs(dY)){
				for(int iX=0; Math.abs(iX)<=Math.abs(dX); iX+=1*Math.signum(dX)){
					int accum = constructor.snakelets.accumArray[(int)x+iX][(int)y+(int)Math.round(iX*(dY/dX))];
					if(accum>0){
						score+=accum;
					}else{
						score+=blankScore;
					}
				}
			}else{
				for(int iY=0; Math.abs(iY)<=Math.abs(dY); iY+=1*Math.signum(dY)){
					int accum= constructor.snakelets.accumArray[(int)x+(int)Math.round(iY*(dX/dY))][(int)y+iY];
					if(accum>0){
						score+=accum;
					}else{
						score+=blankScore;
					}
				}
			}
		}
		return score;
	}

	public void setEdge(Edge edg){
		edge = edg;
	}

	public boolean hasEdge(){
		return !(edge==null);
	}

	@Override
	public double getX() {
		return x;
	}

	@Override
	public double getY() {
		return y;
	}

	@Override
	public void setLocation(double x, double y) {
		this.x = x;
		this.y = y;
	}

	public void draw(GLAutoDrawable drawable){
		GL2 gl=drawable.getGL().getGL2();
		gl.glPointSize(6.0f);
		gl.glBegin(GL.GL_POINTS);
		if(hasEdge()){
			gl.glColor3f(0.0f,0.0f,1.0f);
			gl.glVertex2d(x,y);
		}else if(angled){
			gl.glColor3f(1.0f,0.0f,0.0f);
			gl.glVertex2d(x,y);
		}else{
			gl.glColor4f(0.5f,0.0f,0.0f,0.2f);
			//gl.glVertex2d(x,y);
		}
		gl.glEnd();
	}

}
