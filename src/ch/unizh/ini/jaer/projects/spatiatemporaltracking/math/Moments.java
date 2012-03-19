/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.math;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import java.util.List;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 */
public class Moments {
    
    /**
     * Computes the i,j image moment of the given events.
     * 
     * @param i Specifies which moment has to be computed.
     * @param j Specifies which moment has to be computed.
     * @param events The events used to compute the moment.
     * @return The computed moment.
     */
    public static double getMoment(int i, int j, List<TypedEvent> events) {
        double s = 0;
        for (TypedEvent e : events) {
            s += Math.pow(e.x, i) * Math.pow(e.y, j);
        }
        return s;
    }
    
    /**
     * Computes the spatial mean of the given events using the central 
     * moments of image analysis.
     * 
     * @param events The events from which the spatial mean has to be computed.
     * 
     * @return The spatial mean of the given events.
     */
    public static Vector getSpatialMean(List<TypedEvent> events) {
        Vector v = Vector.getDefault(2);
        
        double m00 = Moments.getMoment(0, 0, events);
        double m10 = Moments.getMoment(1, 0, events);
        double m01 = Moments.getMoment(0, 1, events);
        
        v.set(0, (float)(m10 / m00));
        v.set(1, (float)(m01 / m00));
        
        return v;
    }
}
