
package ch.unizh.ini.jaer.projects.bjoernbeyer.pantiltscreencalibration;

import java.awt.geom.Point2D;
import java.io.Serializable;

/** A single pan tilt screen calibration point.
 * @author bjoern */
public class CalibrationPanTiltScreenListPoint implements Serializable{

    /** Retina coordinates */
    Point2D.Float ret;
    /** Pan tilt coordinates */
    Point2D.Float pt;
    /** Screen coordinates in GUI relative to the center of the panel*/
    Point2D.Float screen;

    public CalibrationPanTiltScreenListPoint(Point2D.Float ret, Point2D.Float pt, Point2D.Float screen) {
        this.ret    = ret;
        this.pt     = pt;
        this.screen = screen;
    }
    
    public CalibrationPanTiltScreenListPoint(float retX, float retY, float ptX, float ptY, float screenX, float screenY) {
        ret    = new Point2D.Float();
        pt     = new Point2D.Float();
        screen = new Point2D.Float();
        this.ret.x    = retX;
        this.ret.y    = retY;
        this.pt.x     = ptX;
        this.pt.y     = ptY;
        this.screen.x = screenX;
        this.screen.y = screenY;
    }
    
    @Override public String toString(){
        return "retina: " +String.format("%.2f", ret.x)   +","+String.format("%.2f", ret.y)+" | "
              +"panTilt: "+String.format("%.2f", pt.x)    +","+String.format("%.2f", pt.y) +" | "
              +"screen: " +String.format("%.2f", screen.x)+","+String.format("%.2f", screen.y);
    }
}