/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.factory;

import java.util.HashMap;
import java.util.Map;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.FeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.activity.SimpleActivityExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.assigned.SimpleAssignedExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.boundary.MomentBoundaryExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.lifetime.SimpleLifetimeExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.moment.SimpleMomentExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.path.ConstantTimeDistancePathExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.MomentPositionExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.velocity.SimpleVelocityExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.event.packet.SimplePacketEventExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.event.single.SimpleSingleEventExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.information.EventInformationExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.information.InterruptedPositionInformationExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.information.PathInformationExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.information.SignalInformationExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.information.VelocityInformationExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.interrupt.ExternalInterruptExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.acceleration.LowPassAngularAccelerationPredictor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.occurance.TemporalPatternOccurancePredictor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.position.DiscreteHeunPositionPredictor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.velocity.SyntheticVelocityPredictor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.autocorrelation.SimpleAutoCorrelationExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.correlation.SimpleSignalCorrelationExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.improver.PhaseLockedSignaImproverlExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.kernel.EventDrivenRectangularKernelExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.period.MultipleAutoCorrelationPeriodExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.phase.CorrelationPhaseExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.signal.TransitionBasedSignalExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.transition.PeakKernelTransitionHistory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;

/**
 *
 * @author matthias
 * 
 * This factory is used to create new FeatureExtractors.
 */
public class ConcreteFeatureExtractorFactory implements FeatureExtractorFactory {
    
    /** The instances of the factory. */
    private static Map<ParameterManager, FeatureExtractorFactory> instances = new HashMap<ParameterManager, FeatureExtractorFactory>();
    
    private AEChip chip;
    
    /** The instance of the ParameterManager. */
    private ParameterManager manager;
    
    /**
     * Creates a new ConcreteFeatureExtractorFactory.
     */
    private ConcreteFeatureExtractorFactory(ParameterManager manager, AEChip chip) {
        this.manager = manager;
        this.chip = chip;
    }
    
    @Override
    public FeatureExtractor addFeature(FeatureManager features, Features feature) {
        switch (feature) {
            case Event:
                return new SimpleSingleEventExtractor(this.manager, features, this.chip);
            case Packet:
                return new SimplePacketEventExtractor(this.manager, features, this.chip);
                
            case Lifetime:
                return new SimpleLifetimeExtractor(this.manager, features, this.chip);
            case Activity:
                return new SimpleActivityExtractor(this.manager, features, this.chip);
            case Assigned:
                return new SimpleAssignedExtractor(this.manager, features, this.chip);
                
            case Moment:
                return new SimpleMomentExtractor(this.manager, features, this.chip);
            case Position:
                return new MomentPositionExtractor(this.manager, features, this.chip);
            case Boundary:
                return new MomentBoundaryExtractor(this.manager, features, this.chip);
            case Path:
                //return new ConstantTimePathExtractor(this.manager, features, this.chip);
                return new ConstantTimeDistancePathExtractor(this.manager, features, this.chip);
            case Velocity:
                return new SimpleVelocityExtractor(this.manager, features, this.chip);
                
            case Kernel:
                return new EventDrivenRectangularKernelExtractor(this.manager, features, this.chip);
            case Transition:
                return new PeakKernelTransitionHistory(this.manager, features, this.chip);
            case AutoCorrelation:
                return new SimpleAutoCorrelationExtractor(this.manager, features, this.chip);
            case Period:
                return new MultipleAutoCorrelationPeriodExtractor(this.manager, features, this.chip);
            case Signal:
                return new TransitionBasedSignalExtractor(this.manager, features, this.chip);
            case Correlation:
                return new SimpleSignalCorrelationExtractor(this.manager, features, this.chip);
            case Phase:
                return new CorrelationPhaseExtractor(this.manager, features, this.chip);
            case Improver:
                return new PhaseLockedSignaImproverlExtractor(this.manager, features, this.chip);
                
            case AccelerationPredictor:
                return new LowPassAngularAccelerationPredictor(this.manager, features, this.chip);
            case VelocityPredictor:
                return new SyntheticVelocityPredictor(this.manager, features, this.chip);
            case PositionPredictor:
                return new DiscreteHeunPositionPredictor(this.manager, features, this.chip);
            case Occurance:
                return new TemporalPatternOccurancePredictor(this.manager, features, this.chip);
                
            case Interrupt:
                return new ExternalInterruptExtractor(this.manager, features, this.chip);
                
            case InformationSignal:
                return new SignalInformationExtractor(this.manager, features, this.chip);
            case InformationPosition:
                return new InterruptedPositionInformationExtractor(this.manager, features, this.chip);
            case InformationPath:
                return new PathInformationExtractor(this.manager, features, this.chip);
            case InformationVelocity:
                return new VelocityInformationExtractor(this.manager, features, this.chip);
            case InformationEvent:
                return new EventInformationExtractor(this.manager, features, this.chip);
            
        }
        return null;
    }
    
    public static FeatureExtractorFactory getInstance(ParameterManager manager, AEChip chip) {
        if (!instances.containsKey(manager)) {
            instances.put(manager, new ConcreteFeatureExtractorFactory(manager, chip));
        }
        return instances.get(manager);
    }
}
