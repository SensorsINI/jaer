/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.transition;

import java.util.ArrayList;
import java.util.List;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.MaxCircularList;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Transition;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.Parameters;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.kernel.KernelExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;

/**
 *
 * @author matthias
 * 
 * This type of transition history extractor computes the transition history
 * by using the results of a kernel function. For more information have a look 
 * at the implementations of the interface KernelExtractor.
 * By using these kernels the algorithm searchs for peaks in the number of
 * incoming events to find the position of possible transitions..
 */
public class PeakKernelTransitionHistory extends AbstractTransitionHistoryExtractor {

    /**
     * Defines the temporal resolution used by this extractor to collect the
     * maximas.
     */
    public int resolution = 1000;
    
    /**
     * Defines a window in which the algorithm has to find a global maxima. The
     * maxima in this window is then used as a reference to determine how many
     * events are needed to create a new transition.
     */
    public int globalWindow = 10000;
    
    /**
     * Defines a second window in which the algorithm has to search for a local
     * maxima. This maxima is then used as a candidate for a possible 
     * transition.
     */
    public int localWindow = 100;
    
    /**
     * Defines the allowed deviation between the global maxima and the local
     * one in order to create a new transition.
     */
    public float deviation;
    
    /** Stores the best candidate of the transition. */
    private KernelExtractor.Storage candidate;
    
    /** Stores the score of the best candidate found. */
    private float best;
    
    /** Stores the found global maximas. */
    private MaxCircularList[] maximas;
    
    /** Stores the timestamp of the last added transition. */
    private int last;
    
    /** Stores the state in which the algorithm has to change. */
    private int state;
    
    /** 
     * The list stores the candidates for transitions. The algorithm has to 
     * search in this list to know whether it has to look at a candidate to
     * decide if there is a valid transition.
     */
    private List<List<KernelExtractor.Storage>> requested;
    
    /**
     * Creates a new instance of the class PeakKernelTransitionHistory.
     */
    public PeakKernelTransitionHistory(ParameterManager parameters, 
                                              FeatureManager features, 
                                              AEChip chip) {
        super(Features.Kernel, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.maximas = new MaxCircularList[2];
        for (int i = 0; i < this.maximas.length; i++) this.maximas[i] = new MaxCircularList(2 * (this.globalWindow / this.resolution) + 1);
        
        this.requested = new ArrayList<List<KernelExtractor.Storage>>();
        for (int i = 0; i < 2; i++) this.requested.add(new ArrayList<KernelExtractor.Storage>());
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.parameterUpdate();
        
        for (int i = 0; i < this.maximas.length; i++) this.maximas[i].reset();
        
        for (int i = 0; i < this.requested.size(); i++) this.requested.get(i).clear();
        
        this.best = 0;
        this.last = 0;
        this.state = 0;
    }

    
    @Override
    public void update(int timestamp) {
        KernelExtractor.Storage[] storage = ((KernelExtractor)this.features.get(Features.Kernel)).getStorage();
        
        /*
         * store new data
         */
        for (int type = 0; type < storage.length; type++) {
            if (storage[type] != null) {
                this.requested.get(type).add(storage[type]);
                this.maximas[type].add(storage[type].timestamp / this.resolution, storage[type].absolute[0]);
            }
        }
        if (storage[this.state] == null) return;

        /*
         * check whether there are candidates to process
         */
        while (!this.requested.get(this.state).isEmpty() &&
                this.requested.get(this.state).get(0).timestamp + this.globalWindow <= storage[this.state].timestamp) {

            KernelExtractor.Storage c = this.requested.get(this.state).remove(0);

            /*
             * check whether the algorithm is ready to accept candidates
             */
            if (this.maximas[this.state].isFull()) {
                
                /*
                 * find best possible transition
                 */
                if (c.timestamp - this.last > this.localWindow &&
                        c.absolute[0] > this.maximas[this.state].getMax() * this.deviation &&
                        c.absolute[0] > this.best) {
                    this.best = c.absolute[0];
                    this.candidate = c;
                }
            }
        }
                
        /*
         * check whether there is a valid transition
         */
        if (this.candidate != null && 
                this.candidate.timestamp - this.last > this.localWindow &&
                storage[this.state].timestamp - this.candidate.timestamp > this.localWindow) {

            /*
             * add transition to history
             */
            this.transition = new Transition(this.candidate.timestamp, this.state);
            this.visualization.add(this.transition);

            /*
             * updates the desired state
             */
            this.state = (this.state + 1) % 2;
            this.last = this.candidate.timestamp;
            this.best = 0;
            
            /*
             * notify the other extractors about the change.
             */
            this.features.getNotifier().notify(this.feature, timestamp);
        }
    }

    @Override
    public void parameterUpdate() {
        super.parameterUpdate();
        
        if (Parameters.getInstance().hasKey(Parameters.TRANSITION_HISTORY_MAX_WINDOW)) this.globalWindow = Parameters.getInstance().getAsInteger(Parameters.TRANSITION_HISTORY_MAX_WINDOW);
        if (Parameters.getInstance().hasKey(Parameters.TRANSITION_HISTORY_MAX_RESOLUTION)) this.resolution = Parameters.getInstance().getAsInteger(Parameters.TRANSITION_HISTORY_MAX_RESOLUTION);
        if (Parameters.getInstance().hasKey(Parameters.TRANSITION_HISTORY_DISTRIBUTION)) this.localWindow = Parameters.getInstance().getAsInteger(Parameters.TRANSITION_HISTORY_DISTRIBUTION);
        if (Parameters.getInstance().hasKey(Parameters.TRANSITION_HISTORY_DEVIATION)) this.deviation = Parameters.getInstance().getAsFloat(Parameters.TRANSITION_HISTORY_DEVIATION);
    }
}
