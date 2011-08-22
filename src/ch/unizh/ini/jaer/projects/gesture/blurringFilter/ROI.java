/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.blurringFilter;

import java.awt.geom.Point2D;

/**
 *
 * @author Jun Haeng Lee
 */
/**
 * Region of interest
 * ROI can be used as an attentional region in which events are processed by the filter.
 */
public class ROI{
    public int id;
    public Point2D.Float center = new Point2D.Float(0, 0);
    public float radius = 0;
    public int timestamp = 0;

    public ROI(int id, int timestamp){
        this.id = id;
        this.timestamp = timestamp;
                }

    public ROI(int id, int timestamp, Point2D.Float center, float radius){
        this(id, timestamp);
        this.center.setLocation(center);
        this.radius = radius;
    }

    public int getID(){
        return id;
    }

    public int getTimestamp(){
        return timestamp;
    }

    public Point2D.Float getCenter(){
        return center;        }

    public float getRadius(){
        return radius;
    }

    public void setTimestamp(int timestamp){
        this.timestamp = timestamp;
    }

    public void setCenter(Point2D.Float c){
        center.setLocation(c);
    }

    public void setRadius(float radius){
        this.radius = radius;
    }

    public void update(int timestamp, Point2D.Float c, float radius){
        this.timestamp = timestamp;
        center.setLocation(c);
        this.radius = radius;
    }

    public boolean contains(Point2D.Float pos){
        boolean ret = false;
        if(center.distance(pos) <= radius)
            ret = true;

        return ret;
    }

    public boolean contains(int x, int y){
        boolean ret = false;
        if(center.distance(x, y) <= radius)
            ret = true;

        return ret;
    }

    public boolean contains(float x, float y){
        boolean ret = false;
        if(center.distance(x, y) <= radius)
            ret = true;

        return ret;
    }
}
