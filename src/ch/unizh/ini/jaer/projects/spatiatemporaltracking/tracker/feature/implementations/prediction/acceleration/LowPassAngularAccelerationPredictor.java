/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.acceleration;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Matrix;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.velocity.VelocityExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.velocity.VelocityPredictor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 *
 * @author matthias
 * 
 * This approach rotates the velocity that they are aligned to the x-axis. By
 * comparing the magnitude and the angle of the two velocity vectors the 
 * needed acceleration of the magnitude and the angle can be estimated.
 */
public class LowPassAngularAccelerationPredictor extends AbstractRotatedAccelerationPredictor {
    
    /**
     * Stores the low pass filter for each direction.
     */
    private LowpassFilter[] filters;
    
    /**
     * The timestamp of the last measured velocity.
     */
    private int last;
    
    /**
     * Indicates whether the predictor was allready used.
     */
    private boolean isVirgin;
    
    /**
     * Creates a new LowPassAngularAccelerationPredictor.
     */
    public LowPassAngularAccelerationPredictor(ParameterManager parameters, 
                                               FeatureManager features, 
                                               AEChip chip) {
        super(Features.Velocity, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.filters = new LowpassFilter[2];
        for (int i = 0; i < this.filters.length; i++) this.filters[i] = new LowpassFilter();
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.parameterUpdate();
        
        for (int i = 0; i < this.filters.length; i++) this.filters[i].reset();
        
        this.last = 0;
        this.isVirgin = true;
    }
    
    /**
     * Rotates the velocity that they are aligned to the x-axis. By comparing 
     * the magnitude and the angle of the two velocity vectors the needed 
     * acceleration of the magnitude and the angle can be estimated.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        if (this.isVirgin) {
            this.isVirgin = false;
        }
        else {
            Vector predictedVelocity = ((VelocityPredictor)this.features.get(Features.VelocityPredictor)).getReferenceVelocity();
            Vector measuredVelocity = ((VelocityExtractor)this.features.get(Features.Velocity)).getVelocity().copy();
            
            if (predictedVelocity.normalizedDot(measuredVelocity) <= -20.7) {
                this.acceleration.reset();
            }
            else {
                /**
                 * determine angle between 2d-velocity vector and x-axis
                 */
                float rotation = this.angle(predictedVelocity);
                
                /**
                 * rotate 2d-velocity vector and align it to x-axis
                 */
                Vector forward = Matrix.getRotation2D(rotation).multiply(predictedVelocity.copy().redimension(2));
                
                /**
                 * rotate the new velocity with the same angle
                 */
                Vector target = Matrix.getRotation2D(rotation).multiply(measuredVelocity.copy().redimension(2));
                
                /**
                 * Decides whether the magnitude of the acceleration has to
                 * be adapted.
                 */
                float m = (float)(target.norm() - forward.norm());
                this.acceleration.set(0, this.filters[0].filter(m / 100000, timestamp));
                
                /**
                 * Decides wheter the direction of the acceleration has to
                 * be adapted.
                 */
                float a = 0;
                if (target.norm() > 0) {
                    a = 1 - target.normalizedDot(Vector.getXAxis(2));
                }
                this.acceleration.set(1, this.filters[1].filter(a / 10000, timestamp));
                
            }
            this.features.getNotifier().notify(this.feature, timestamp);
        }
    }

    @Override
    public Vector computeVelocity(Vector velocity, int delta) {
        /**
         * determine angle between 2d-velocity vector and x-axis
         */
        float rotation = this.angle(velocity);

        /**
         * rotate 2d-velocity vector and align it to x-axis
         */
        Vector forward = Matrix.getRotation2D(rotation).multiply(velocity.copy().redimension(2));
        
        /**
         * Compute new forward velocity.
         */
        float m = (float)forward.norm() + this.acceleration.get(0) * delta;
        float a = this.acceleration.get(1) * delta;
        
        forward.set(0, m * (float) Math.cos(Math.abs(a)));
        forward.set(1, m * (float)(Math.sin(Math.abs(a))) * Math.signum(a));
        
        /**
         * rotate velocity vector back
         */
        return Matrix.getRotation2D((float)(2 * Math.PI - rotation)).multiply(forward).redimension(3);
    }
}
