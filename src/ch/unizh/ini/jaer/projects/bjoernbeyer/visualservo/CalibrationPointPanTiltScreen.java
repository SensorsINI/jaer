
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import java.awt.geom.Point2D;
import java.io.Serializable;

/** A single pan tilt screen calibration point.
 * @author bjoern */
public class CalibrationPointPanTiltScreen implements Serializable{

    /** Retina coordinates */
    Point2D.Float ret;
    /** Pan tilt coordinates */
    Point2D.Float pt;
    /** Screen coordinates in GUI relative to the center of the panel*/
    Point2D.Float screen;

    public CalibrationPointPanTiltScreen(Point2D.Float ret, Point2D.Float pt, Point2D.Float screen) {
        this.ret    = ret;
        this.pt     = pt;
        this.screen = screen;
    }
    
    public CalibrationPointPanTiltScreen(float retX, float retY, float ptX, float ptY, float screenX, float screenY) {
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
    
    @Override
    public String toString(){
        return "retina: "+ret.x+","+ret.y+" | panTilt: "+pt.x+","+pt.y+" | screen: "+screen.x+","+screen.y;
    }
}