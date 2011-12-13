/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.acceleration;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Provides some basic methods used by implementations of the interface
 * AccelerationPredictor using rotated velocity vectors.
 */
public abstract class AbstractRotatedAccelerationPredictor extends AbstractAccelerationPredictor {
    
    /**
     * Creates a new RotatedAngularAccelerationPredictor.
     */
    public AbstractRotatedAccelerationPredictor(Features interrupt,
                                                ParameterManager parameters, 
                                                FeatureManager features,
                                                AEChip chip) {
        super(interrupt, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
    }
    
    @Override
    public void reset() {
        super.reset();
    }
    
    /**
     * Computes the angle between the vector and the x-axis. This angle can
     * directly be used for the rotation to the axis.
     * 
     * @param vector The vector from which the angle has to be known.
     * @return The angle between the given vector and the x-axis.
     */
    public float angle(Vector vector) {
        /**
         * determine angle between 2d-velocity vector and x-axis
         */
        Vector projected = vector.copy().redimension(2);
        float angle = 0;
        if (projected.norm() > 0) {
            angle = (float)(Math.acos(projected.normalizedDot(Vector.getXAxis(2))));
        }
        
        /**
         * check orientation to x-axis and correct the angle
         */
        if (projected.get(1) > 0) {
            angle = (float)(2 * Math.PI - angle);
        }
        return angle;
    }
}
