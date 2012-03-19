/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.kernel;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.FloatSummedCircularList;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * This class uses different rectangular kernels to convolve the incoming 
 * stream of events with them. The results of the convolution are then 
 * normalized by the size of the kernels.
 * This corresponds then to the number of events per time.
 */
public class EventDrivenRectangularKernelExtractor extends AbstractKernelExtractor {

    /** The coarse window used to find the maximas for the threshold. */
    public int coarseWindow = 400;
    
    /** Defines the window to find the maximas for possible transitions. */
    public int detailWindow = 100;
    
    /** Stores the number of events in the coarse time window. */
    private FloatSummedCircularList[] coarse;
    
    /** Stores the number of events in the detail time window. */
    private FloatSummedCircularList[] detail;
    
    /** 
     * Stores for each type the timestamp of the last event to the coarse 
     * kernel. 
     */
    private int[] lastCoarse;
    
    /** 
     * Stores for each type the timestamp of the last event to the detail 
     * kernel. 
     */
    private int[] lastDetail;
    
    /** Indicates whether the algorithm is allready used for each type*/
    private boolean[] isVirgin;
    
    /** 
     * The list stores the requested coarse kernels. The algorithm has to 
     * search in this list to know whether the result of the kernel is required 
     * at a particular timestamp.
     */
    private List<List<Storage>> requestedCoarse;
    
    /** The list stores the requested detailed kernels. */
    private List<List<Storage>> requestedDetail;
    
    /**
     * Creates a new instance of a EventDrivenRectangularKernelExtractor.
     */
    public EventDrivenRectangularKernelExtractor(ParameterManager parameters, 
                                                 FeatureManager features, 
                                                 AEChip chip) {
        super(Features.Event, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.coarse = new FloatSummedCircularList[2];
        for (int i = 0; i < this.coarse.length; i++) this.coarse[i] = new FloatSummedCircularList(2 * this.coarseWindow + 1);
        
        this.detail = new FloatSummedCircularList[2];
        for (int i = 0; i < this.detail.length; i++) this.detail[i] = new FloatSummedCircularList(2* this.detailWindow + 1);
        
        this.requestedCoarse = new ArrayList<List<Storage>>();
        for (int i = 0; i < 2; i++) this.requestedCoarse.add(new ArrayList<Storage>());
        
        this.requestedDetail = new ArrayList<List<Storage>>();
        for (int i = 0; i < 2; i++) this.requestedDetail.add(new ArrayList<Storage>());
        
        this.lastCoarse = new int[2];
        this.lastDetail = new int[2];
        this.isVirgin = new boolean[2];
    }
    
    @Override
    public void reset() {
        super.reset();
        
        for (int i = 0; i < this.coarse.length; i++) this.coarse[i].reset();
        for (int i = 0; i < this.detail.length; i++) this.detail[i].reset();
        for (int i = 0; i < this.requestedCoarse.size(); i++) this.requestedCoarse.get(i).clear();
        for (int i = 0; i < this.requestedDetail.size(); i++) this.requestedDetail.get(i).clear();
        
        Arrays.fill(this.isVirgin, true);
    }
    
    /**
     * Uses different rectangular kernels to convolve the incoming stream of 
     * events with them. The results of the convolution are then normalized by 
     * the size of the kernels.
     * This corresponds then to the number of events per time.
     * 
     * @param timestamp The timestamp of the algorithm.
     */
    @Override
    public void update(int timestamp) {
        TypedEvent event = this.features.getEvent();
        
        if (this.isVirgin[event.type]) {
            this.lastCoarse[event.type] = event.timestamp;
            this.lastDetail[event.type] = event.timestamp;

            this.isVirgin[event.type] = false;
        }
        else {
            /*
             * add request to the queue
             */
            this.requestedCoarse.get(event.type).add(new Storage(event.timestamp, 2));
            this.requestedDetail.get(event.type).add(this.requestedCoarse.get(event.type).get(this.requestedCoarse.get(event.type).size() - 1));

            /*
             * check whether there are requested results from the detail
             * kernel.
             */
            while (!this.requestedDetail.get(event.type).isEmpty() &&
                    this.requestedDetail.get(event.type).get(0).timestamp + this.detailWindow <= event.timestamp) {

                Storage s = this.requestedDetail.get(event.type).remove(0);

                this.detail[event.type].add(s.timestamp + this.detailWindow - this.lastDetail[event.type], 0);
                this.lastDetail[event.type] = s.timestamp + this.detailWindow;

                s.absolute[0] = this.detail[event.type].getSum();
                s.size[0] = this.detail[event.type].getCapacity();
                s.state[0] = true;
            }

            /*
             * move detailed kernel forward.
             */
            int changeDetail = event.timestamp - this.lastDetail[event.type];
            if (changeDetail >= 0) {
                this.detail[event.type].add(changeDetail, 1);
                this.lastDetail[event.type] = event.timestamp;
            }

            /*
             * check whether there are requested results from the coarse
             * kernel.
             */
            while (!this.requestedCoarse.get(event.type).isEmpty() &&
                    this.requestedCoarse.get(event.type).get(0).timestamp + this.coarseWindow <= event.timestamp) {

                Storage s = this.requestedCoarse.get(event.type).remove(0);

                this.coarse[event.type].add(s.timestamp + this.coarseWindow - this.lastCoarse[event.type], 0);
                this.lastCoarse[event.type] = s.timestamp + this.coarseWindow;

                s.absolute[1] = this.coarse[event.type].getSum();
                s.size[1] = this.coarse[event.type].getCapacity();
                s.state[1] = true;

                this.storage[event.type] = s;
                this.visualization.get(event.type).add(this.storage[event.type]);
                
                /*
                 * notify extractors about the new kernel result
                 */
                this.features.getNotifier().notify(this.feature, timestamp);
            }

            /*
             * move coarse kernel forward.
             */
            int changeCoarse = event.timestamp - this.lastCoarse[event.type];
            if (changeCoarse >= 0) {
                this.coarse[event.type].add(changeCoarse, 1);
                this.lastCoarse[event.type] = event.timestamp;
            }
        }
    }
}
