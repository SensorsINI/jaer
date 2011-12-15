/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.occurance;

import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.cluster.SimpleFeatureCluster;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.data.signal.SimpleSignal;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.extractable.ConcreteFeatureExtractable;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.extractable.FeatureExtractable;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.extractor.Extractors;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.extractor.FeatureExtractor;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.extractor.FeatureExtractorFactory;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.extractor.identification.phase.AbstractPhaseExtractor;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.extractor.identification.phase.PhaseExtractor;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.extractor.identification.signal.AbstractSignalExtractor;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.extractor.identification.signal.SignalExtractor;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.predictable.ConcreteFeaturePredictable;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.predictable.FeaturePredictable;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.predictor.ConcreteFeaturePredictorFactory;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.feature.predictor.Predictors;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.parameter.ParameterListener;
import ch.unizh.ini.jaer.projects.spatioTemporalLEDTracker.tracker.parameter.ParameterManager;
import java.util.List;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 */
public class TestOccurancePredictor {
    
    private static Signal signal;
    private static int phase;
    
    public static void main(String [] args) {
        /**
         * dummy parameter manager.
         */
        ParameterManager manager = new ParameterManager() {

            @Override
            public void add(ParameterListener listener) {}

            @Override
            public void remove(ParameterListener listener) {}
            
            @Override
            public void updateListeners() {}
        };
        
        FeatureExtractable extractable = new ConcreteFeatureExtractable(new DummyFeatureExtractorFactory(manager));
        FeaturePredictable predictable = new ConcreteFeaturePredictable(extractable,
                                                                        ConcreteFeaturePredictorFactory.getInstance(null, manager));
        FeatureCluster cluster = new SimpleFeatureCluster(extractable, predictable, manager);
        
        
        /*
         * setup first test case with phase = 0 and transitions at 10000 and 20000
         */
        TestOccurancePredictor.phase = 0;
        
        TestOccurancePredictor.signal = new SimpleSignal();
        TestOccurancePredictor.signal.setPhase(0);
        TestOccurancePredictor.signal.add(10000, 1);
        TestOccurancePredictor.signal.add(20000, 0);
        TestOccurancePredictor.signal.update();
        
        init(cluster);
        for (int i = 0; i < 100000; i ++) {
            int timestamp = (int)(Math.random() * Math.pow(10, 8));
            
            int rel = timestamp % 20000;
            int expected = Math.min(rel, 20000 - rel);
            
            check(cluster, timestamp, (byte)0, expected);
        }
        
        for (int i = 0; i < 100000; i ++) {
            int timestamp = (int)(Math.random() * Math.pow(10, 8));
            
            int rel = timestamp % 20000;
            int expected = Math.abs(10000 - rel);
            
            check(cluster, timestamp, (byte)1, expected);
        }
        
        /*
         * change phase to 857306
         */
        TestOccurancePredictor.phase = 857306;
        
        init(cluster);
        
        for (int i = 0; i < 100000; i ++) {
            int timestamp = (int)(Math.random() * Math.pow(10, 8)) + phase;
            
            int rel = (timestamp - phase) % 20000;
            int expected = Math.min(rel, 20000 - rel);
            
            check(cluster, timestamp, (byte)0, expected);
        }
        
        for (int i = 0; i < 100000; i ++) {
            int timestamp = (int)(Math.random() * Math.pow(10, 8)) + phase;
            
            int rel = (timestamp - phase) % 20000;
            int expected = Math.abs(10000 - rel);
            
            check(cluster, timestamp, (byte)1, expected);
        }
    }
    
    public static void init(FeatureCluster cluster) {
        cluster.clear();
        
        cluster.getFeaturePredictable().add(Predictors.Occurance);
        
        /*
         * check used extractors
         */
        int nExtractors = -1;
        while (nExtractors != cluster.getFeatureExtractable().getFeatures().size()) {
            nExtractors = cluster.getFeatureExtractable().getFeatures().size();
            cluster.update(0);
        }
        
        System.out.println("test case uses the following extractors...");
        for (FeatureExtractor fe : cluster.getFeatureExtractable().getFeatures()) {
            System.out.println("\t" + fe.getFeature().name());
        }
    }
    
    private static void check(FeatureCluster cluster, int relative, byte type, int expected) {
        int measured = ((OccurancePredictor)cluster.getFeaturePredictable().getFeature(Predictors.Occurance)).getDistance(type, relative);
        
        if (measured != expected) {
            System.out.println("FATAL ERROR: wrong measured value for '" + relative + "'. Expected '" + expected + "' and measured '" + measured + "'.");
        }
        
    }
    
    /*
     * dummy extractor factory
     */
    public static class DummyFeatureExtractorFactory implements FeatureExtractorFactory {
        private ParameterManager manager;
        
        public DummyFeatureExtractorFactory(ParameterManager manager) {
            this.manager = manager;
        }
        
        @Override
        public void addFeature(FeatureExtractable cluster, Extractors feature) {
            switch(feature) {
                case Phase:
                    cluster.add(new DummyPhaseExtractor(this.manager, null, cluster));
                    break;
                case Signal:
                    cluster.add(new DummySignalExtractor(this.manager, null, cluster));
                    break;
            }
        }
    }
    
    /*
     * dummy signal extractor
     */
    public static class DummySignalExtractor extends AbstractSignalExtractor implements SignalExtractor {

        public DummySignalExtractor(ParameterManager manager, AEChip chip, FeatureExtractable source) {
            super(manager, chip, source);
        }
        
        @Override
        public void update(List<TypedEvent> events, int timestamp) { }
        
        @Override
        public void update(TypedEvent event) { }
        
        @Override
        public Signal getSignal() {
            return TestOccurancePredictor.signal;
        }
        
        @Override
        public boolean isStable() {
            return true;
        }
    }

    /*
     * dummy phase extractor.
     */
    public static class DummyPhaseExtractor extends AbstractPhaseExtractor implements PhaseExtractor {

        public DummyPhaseExtractor(ParameterManager manager, AEChip chip, FeatureExtractable source) {
            super(manager, chip, source);
        }
        
        @Override
        public void update(List<TypedEvent> events, int timestamp) { }
        
        @Override
        public void update(TypedEvent event) { }
        
        @Override
        public int getPhase() {
            return TestOccurancePredictor.phase;
        }
        
        @Override
        public boolean isFound() {
            return true;
        }
    }

}
