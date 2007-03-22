/*
 * AERetina.java
 *
 * Created on January 26, 2006, 11:12 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.chip.retina;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.chip.retina.sensorymotor.*;
import ch.unizh.ini.caviar.chip.retina.sensorymotor.ServoReaction;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventprocessing.filter.BackgroundActivityFilter;
import ch.unizh.ini.caviar.eventprocessing.filter.CircularConvolutionFilter;
import ch.unizh.ini.caviar.eventprocessing.filter.ProbFPNCorrectionFilter;
import ch.unizh.ini.caviar.eventprocessing.filter.RepetitiousFilter;
import ch.unizh.ini.caviar.eventprocessing.filter.SpatialBandpassFilter;
import ch.unizh.ini.caviar.eventprocessing.filter.SubSampler;
import ch.unizh.ini.caviar.eventprocessing.filter.SubSamplingBandpassFilter;
import ch.unizh.ini.caviar.eventprocessing.label.DirectionSelectiveFilter;
import ch.unizh.ini.caviar.eventprocessing.label.MotionCompensator;
import ch.unizh.ini.caviar.eventprocessing.label.NearestEventMotionComputer;
import ch.unizh.ini.caviar.eventprocessing.label.SimpleOrientationFilter;
import ch.unizh.ini.caviar.eventprocessing.label.TypeCoincidenceFilter;
import ch.unizh.ini.caviar.eventprocessing.tracking.*;
// [edit by Hans Kristian Otnes Berge hansbe@ifi.uio.no]
// import ch.unizh.ini.caviar.eventprocessing.tracking.ClassTracker2;
// Package not found
import ch.unizh.ini.caviar.eventprocessing.tracking.ClassTracker;
// [end of edit by Hans Kristian Otnes Berge hansbe@ifi.uio.no]
import ch.unizh.ini.caviar.eventprocessing.tracking.ClusterTracker;
import ch.unizh.ini.caviar.eventprocessing.tracking.EyeTracker;
import ch.unizh.ini.caviar.eventprocessing.tracking.MedianTracker;
import ch.unizh.ini.caviar.eventprocessing.tracking.WingTracker;
import ch.unizh.ini.caviar.graphics.ChipCanvas;
import ch.unizh.ini.caviar.graphics.RetinaCanvas;
import ch.unizh.ini.caviar.graphics.RetinaRenderer;
import ch.unizh.ini.caviar.eventprocessing.tracking.HoughEyeTracker;
import ch.unizh.ini.tobi.rccar.*;


/**
 * A superclass for retina chips, with renderers and event filters.
 *
 * @author tobi
 */
abstract public class AERetina extends AEChip{
    
    /** Creates a new instance of AERetina */
    public AERetina() {
        init();
    }
    
    /** override this to add more filters or change filterChain */
    protected void init(){
        
        setEventClass(PolarityEvent.class);
        
        // these are subclasses of ChipRenderer and ChipCanvas
        // these need to be added *before* the filters are made or the filters will not annotate the results!!!
        setRenderer(new RetinaRenderer(this));
        filterChain.add(new RepetitiousFilter(this));
//        filterChain.add(new HarmonicFilter(this));
        filterChain.add(new BackgroundActivityFilter(this));
        filterChain.add(new ProbFPNCorrectionFilter(this));
        filterChain.add(new SubSampler(this));
        filterChain.add(new SubSamplingBandpassFilter(this));
//        filterChain.add(new OverlappingSubSampler(this));
        filterChain.add(new SpatialBandpassFilter(this));
        filterChain.add(new SimpleOrientationFilter(this));
//        filterChain.add(new SimpleOrientationFilter_1(this));
        filterChain.add(new TypeCoincidenceFilter(this));
        filterChain.add(new CircularConvolutionFilter(this));
        filterChain.add( new DirectionSelectiveFilter(this));
        filterChain.add(new MotionCompensator(this));
        filterChain.add(new NearestEventMotionComputer(this));
        filterChain.add(new MedianTracker(this));
//        filterChain.add(new ClusterTracker(this));
        filterChain.add(new ClassTracker(this));
//        filterChain.add(new ServoReaction(this));
//        filterChain.add(new MedianTracker(this));
//        filterChain.add(new CircularConvolutionFilter(this));
//        filterChain.add(new SpikeSoundFilter(this));
        filterChain.add(new EyeTracker(this));
        filterChain.add(new WingTracker(this));
        filterChain.add(new Goalie(this));
        filterChain.add(new HoughEyeTracker(this));
        filterChain.add(new HoughLineTracker(this));
        filterChain.add(new Driver(this));
        
         filterChain.add(new PawTracker(this));
        
//        // real time filters here: these are automagically added to filterframe
//        realTimeFilterChain.add(new ServoReaction(this));

//        if(filterFrame!=null) filterFrame.dispose();
//        filterFrame=new FilterFrame(this);
    }
    
}
