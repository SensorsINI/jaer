/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.awt.geom.Point2D;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.media.opengl.GLAutoDrawable;

/**
 *
 * @author Christian
 */
public class Edge {
    public CopyOnWriteArrayList<EdgeSegment> segments;
    public CopyOnWriteArrayList<Point2D> points;
    public int timestamp, closeTimestamp;
    public float[] color;

    public Edge(EdgeSegment segment){
        segments = new CopyOnWriteArrayList<EdgeSegment>();
        segments.add(segment);
        points = new CopyOnWriteArrayList<Point2D>();
        points.add(segment.getP1());
        points.add(segment.getP2());
        color = new float[3];
        color[0] = (float)Math.random();
        color[1] = (float)Math.random();
        color[2] = (float)Math.random();
        segment.setEdge(this);
        timestamp = segment.timestamp;
        closeTimestamp = segment.timestamp;
    }

    public boolean checkOverlap(EdgeSegment newSgmt, float oriTol, float distTol){
        for(Object sgmt : segments){
            EdgeSegment segment = (EdgeSegment) sgmt;
            if(segment.touches(newSgmt, distTol)){
                closeTimestamp = newSgmt.timestamp;
                if(segment.aligns(segment, oriTol)){
                    segment.merge(newSgmt);
                    timestamp = newSgmt.timestamp;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkAge(){
        boolean pass = false;
        if(timestamp-closeTimestamp<1000)
            pass = true;
        return pass;
    }
    
    public boolean merge(Edge edge){
        boolean merged = false;
        
        return merged;
    }
    
    public void translate(float dX, float dY){
        for(Point2D point:points){
            double pX = point.getX();
            double pY = point.getY();
            point.setLocation(pX+dX, pY+dY);
        }
        updateSegments();
    }
    
    public void updateSegments(){
        int i = 0;
        for(EdgeSegment segment : segments){
            segment.setLine(points.get(i), points.get(i+1));
            i++;
        }
    }

    public void draw(GLAutoDrawable drawable){
        for(Object sgm : segments){
            EdgeSegment segment = (EdgeSegment) sgm;
            segment.draw(drawable);
        }
    }
}
