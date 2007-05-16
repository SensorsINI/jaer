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
// edit by Philipp to add another filter
import ch.unizh.ini.caviar.eventprocessing.tracking.ParticleTracker;
// end edit philipp
import ch.unizh.ini.caviar.eventprocessing.tracking.ClusterTracker;
import ch.unizh.ini.caviar.eventprocessing.tracking.EyeTracker;
import ch.unizh.ini.caviar.eventprocessing.tracking.MedianTracker;
import ch.unizh.ini.caviar.eventprocessing.tracking.WingTracker;
import ch.unizh.ini.caviar.graphics.ChipCanvas;
import ch.unizh.ini.caviar.graphics.RetinaCanvas;
import ch.unizh.ini.caviar.graphics.RetinaRenderer;
import ch.unizh.ini.caviar.eventprocessing.tracking.HoughEyeTracker;
import ch.unizh.ini.tobi.rccar.*;
import java.util.ArrayList;

/**
 * A superclass for retina chips, with renderers and event filters.
 *
 * @author tobi
 */
abstract public class AERetina extends AEChip{
    
    /** Creates a new instance of AERetina */
    public AERetina() {
        setEventClass(PolarityEvent.class);
        
        // these are subclasses of ChipRenderer and ChipCanvas
        // these need to be added *before* the filters are made or the filters will not annotate the results!!!
        setRenderer(new RetinaRenderer(this));
        
        addDefaultEventFilter(SimpleOrientationFilter.class);
        addDefaultEventFilter(DirectionSelectiveFilter.class);
        addDefaultEventFilter(ClassTracker.class);
// edit by Philipp to add another filter; in preparation to add below fliter
        // not needed now, just add by using FilterFrame/View/Customize - tobi
//        filterChain.add(new ParticleTracker(this));
// end edit Philipp
    }
}
