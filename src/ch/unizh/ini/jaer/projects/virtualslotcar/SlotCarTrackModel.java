/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
/**
 * Model of the slot car track.  Coordinate system is same as sensor pixel coordinates.
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class SlotCarTrackModel{
    private SlotCarRacer slotCarRacer;
    private Logger log = Logger.getLogger("SlotCarTrackModel");
    private Preferences prefs;
    private ArrayList<Point2D.Float> points = new ArrayList();  // points along track, units are sensor pixels
    private float TRACK_WIDTH = 2f; // pixels
    private Rectangle2D.Float bounds = null;
    private Point2D.Float startPoint = null;

    public SlotCarTrackModel (SlotCarRacer racer){
        this.slotCarRacer = racer;
        this.prefs = racer.prefs();
        try{
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(prefs.getByteArray("SlotCarTrackModel.points",null)));
            points = (ArrayList<Point2D.Float>)in.readObject();
            in.close();
            computeBounds();
            log.info("loaded track model with " + points.size() + " points");
        } catch ( Exception e ){
            e.printStackTrace();
        }

    }

    void computeBounds (){
        if ( points == null || points.isEmpty() ){
            bounds = null;
            return;
        }
        float xmin = Float.MAX_VALUE, ymin = Float.MAX_VALUE, xmax = Float.MIN_VALUE, ymax = Float.MIN_VALUE;
        for ( Point2D.Float p:points ){
            if ( p.x < xmin ){
                xmin = p.x;
            } else if ( p.x > xmax ){
                xmax = p.x;
            }
            if ( p.y < ymin ){
                ymin = p.y;
            } else if ( p.y > ymax ){
                ymax = p.y;
            }
        }
        bounds = new Rectangle2D.Float(xmin,ymin,xmax - xmin,ymax - ymin);
    }

    static void pr(float x, float y){
        System.out.println(String.format("%.2f, %.2f",x,y));
    }
    static SlotCarTrackModel makeOvalTrack (SlotCarRacer outer){
        final float h = 1, w = 2;  // dimensions
        final int n = 10; // each segment
        SlotCarTrackModel oval = new SlotCarTrackModel(outer);
        oval.points.clear();
        for ( int i = 0 ; i < n ; i++ ){
            float y = -h / 2;
            float x = -w / 2 + w / n * i;
            if ( i == n / 2 ){
                oval.startPoint = new Point2D.Float(x,y);
                oval.points.add(oval.startPoint);
            } else{
                oval.points.add(new Point2D.Float(x,y));
            }
//            pr(x,y);
        }
//        System.out.println("");
        for ( int i = 0 ; i < n ; i++ ){
            float y = -h / 2 * ( (float)Math.cos(Math.PI * i / n) );
            float x = w / 2 * ( 1 + (float)Math.sin(Math.PI * i / n) );
//            pr(x,y);
             oval.points.add(new Point2D.Float(x,y));
        }
//        System.out.println("");
        for ( int i = 0 ; i < n ; i++ ){
            float y = +h / 2;
            float x = +w / 2 - w / n * i;
//           pr(x,y);
              oval.points.add(new Point2D.Float(x,y));
        }
//        System.out.println("");
        for ( int i = 0 ; i < n ; i++ ){
            float y = h / 2 * ( (float)Math.cos(Math.PI * i / n) );
            float x = -w / 2 * ( 1 + (float)Math.sin(Math.PI * i / n) );
//             pr(x,y);
            oval.points.add(new Point2D.Float(x,y));
        }
        oval.computeBounds();
        return oval;

    }

    public void draw (GL gl){
        if ( points == null ){
            return;
        }
        gl.glLineWidth(TRACK_WIDTH);
        gl.glColor3f(1f,1f,.1f);
        gl.glBegin(GL.GL_LINE_LOOP);
        for ( Point2D.Float p:points ){
            gl.glVertex2f(p.x,p.y);
        }
        gl.glEnd();
    }

    /**
     * @return the points
     */
    public ArrayList<Point2D.Float> getPoints (){
        return points;
    }

    /**
     * @param points the points to set
     */
    public void setPoints (ArrayList<Point2D.Float> points){
        this.points = points;
    }

    /**
     * @return the bounds
     */
    public Rectangle2D.Float getBounds (){
        return bounds;
    }

    /**
     * @param bounds the bounds to set
     */
    public void setBounds (Rectangle2D.Float bounds){
        this.bounds = bounds;
    }

    /**
     * @return the startPoint
     */
    public Point2D.Float getStartPoint (){
        return startPoint;
    }

    /**
     * @param startPoint the startPoint to set
     */
    public void setStartPoint (Point2D.Float startPoint){
        this.startPoint = startPoint;
    }
}
