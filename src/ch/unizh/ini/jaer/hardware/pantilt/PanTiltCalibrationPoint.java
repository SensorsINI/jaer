/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.hardware.pantilt;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * A single pan tilt calibration point.
 * @author tobi
 */
public class PanTiltCalibrationPoint implements Serializable{

    /** Retina coordinates */
    Point2D.Float ret;
    /** Pan tilt coordinates */
    Point2D.Float pt;
    /** Mouse coordinates in GUI */
    Point mouse;

    public PanTiltCalibrationPoint(Point2D.Float ret, Point2D.Float pt, Point mouse) {
        this.ret = ret;
        this.pt = pt;
        this.mouse = mouse;
    }
    
    public String toString(){
        return "retina: "+ret.x+","+ret.y+" panTilt: "+pt.x+","+pt.y+" mouse: "+mouse.x+","+mouse.y;
    }
}
