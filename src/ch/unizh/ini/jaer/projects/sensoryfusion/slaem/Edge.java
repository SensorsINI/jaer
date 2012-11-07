/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.util.concurrent.CopyOnWriteArrayList;
import javax.media.opengl.GLAutoDrawable;

/**
 *
 * @author Christian
 */
public class Edge {
    public CopyOnWriteArrayList<EdgeSegment> segments;
    public int timestamp;
    public boolean mature, matureSegment;
    public float[] color;

    public Edge(EdgeSegment segment){
        segments = new CopyOnWriteArrayList<EdgeSegment>();
        segments.add(segment);
        color = new float[3];
        color[0] = (float)Math.random();
        color[1] = (float)Math.random();
        color[2] = (float)Math.random();
    }

    public boolean contains(EdgeFragments.Snakelet snakelet, float oriTol, float distTol){
        for(Object sgm : segments){
            EdgeSegment segment = (EdgeSegment) sgm;
            if(segment.contains(snakelet, oriTol, distTol))return true;
        }
        return false;
    }

    public void addSegment(EdgeSegment segment){
        this.segments.add(segment);
    }

    public void checkOverlap(){

    }

    public boolean overlaps(EdgeSegment edgeSegment, float oriTol, float distTol){
        boolean overlap = true;
        for(Object sgmt:this.segments){
            EdgeSegment thisSegment = (EdgeSegment) sgmt;
            if(thisSegment.crosses(edgeSegment, oriTol, distTol)){
                overlap = true;
                break;
            }
        }
        return overlap;
    }

    public boolean checkAge(){
        boolean pass = false;
        for(Object sgm : this.segments){
            EdgeSegment segment = (EdgeSegment) sgm;
//            if(baFilter.lastTimestamps[(int)segment.line.x1][(int)segment.line.y1]-this.timestamp<baFilter.getDt())pass=true;
//            if(baFilter.lastTimestamps[(int)segment.line.x2][(int)segment.line.y2]-this.timestamp<baFilter.getDt())pass=true;
        }
        return pass;
    }

    public void draw(GLAutoDrawable drawable){
        for(Object sgm : segments){
            EdgeSegment segment = (EdgeSegment) sgm;
            segment.draw(drawable);
        }
    }

    public boolean isMature(){
        return mature;
    }

    public boolean hasMatureSegment(){
        return matureSegment;
    }

    public void setMature(boolean mature){
        this.mature = mature;
    }
}
