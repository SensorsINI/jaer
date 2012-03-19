/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.boundary;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.moment.MomentExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * This BoundaryExtractor computes the boundary of the observed object using
 * the moments of the observed object.
 */
public class MomentBoundaryExtractor extends AbstractBoundaryExtractor {

    /**
     * Creates a new instance of a MomentBoundaryExtractor.
     */
    public MomentBoundaryExtractor(ParameterManager parameters, 
                                   FeatureManager features, 
                                   AEChip chip) {
        super(Features.Packet, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
    }
    
    /**
     * Computes the boundary of the observed object using the moments of the o
     * bserved object.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        this.timestamp = timestamp;
        
        MomentExtractor me = (MomentExtractor)this.features.get(Features.Moment);
        
        float m00 = me.getMoment(0, 0);
        float m10 = me.getMoment(1, 0);
        float m01 = me.getMoment(0, 1);
        float m11 = me.getMoment(1, 1);
        float m20 = me.getMoment(2, 0);
        float m02 = me.getMoment(0, 2);
        
        float x = m10 / m00;
        float y = m01 / m00;
        
        float du20 = m20 / m00 - x*x;
        float du02 = m02 / m00 - y*y;
        float du11 = m11 / m00 - x*y;
        
        this.majorLength = (float)Math.max(1, Math.sqrt(m20 - x*m10) / Math.sqrt(m00) * 2);
        this.minorLength = (float)Math.max(1, Math.sqrt(m02 - y*m01) / Math.sqrt(m00) * 2);
        
        this.angle = (float)(0.5 * Math.atan((2 * du11) / (du20 - du02)));
        
        this.features.getNotifier().notify(this.feature, timestamp);
    }
}
