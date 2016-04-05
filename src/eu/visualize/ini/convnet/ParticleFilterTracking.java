/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import ch.unizh.ini.jaer.projects.util.ColorHelper;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Random;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTrackerEvent;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.ParticleFilter.DynamicEvaluator;
import net.sf.jaer.util.filter.ParticleFilter.MeasurmentEvaluator;
import net.sf.jaer.util.filter.ParticleFilter.ParticleFilter;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.util.filter.ParticleFilter.AverageEvaluator;
import net.sf.jaer.util.filter.ParticleFilter.Particle;
import net.sf.jaer.util.filter.ParticleFilter.SimpleParticle;

/**
 *
 * @author hongjie and liu min
 */

@Description("Particle Filter for tracking")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ParticleFilterTracking extends EventFilter2D implements PropertyChangeListener, FrameAnnotater {
    public static final String PROP_SURROUNDINHIBITIONCOST = "PROP_SURROUNDINHIBITIONCOST";
    private DynamicEvaluator dynamic;
    private MeasurmentEvaluator measurement;
    private AverageEvaluator average;

    private ParticleFilter filter;
    
    private boolean colorClustersDifferentlyEnabled = getBoolean("colorClustersDifferentlyEnabled", false);
    private boolean filterEventsEnabled = getBoolean("filterEventsEnabled", false); // enables filtering events so

    FilterChain trackingFilterChain;
    private RectangularClusterTracker tracker;
    
    private double locationX, locationY; 
    private double outputX, outputY;
    
    // private final AEFrameChipRenderer renderer;

    
    public ParticleFilterTracking(AEChip chip) {
        super(chip);
        this.locationX = 0;
        this.locationY = 0;
        
        this.outputX = 0;
        this.outputY = 0;
        
        dynamic = new DynamicEvaluator();
        measurement = new MeasurmentEvaluator();
        average = new AverageEvaluator();
        filter = new ParticleFilter(dynamic, measurement, average);
        
        Random r = new Random();
        for(int i = 0; i < 1000; i++) {
                double x = 10 * r.nextDouble() + 170;
                double y = 10 * r.nextDouble() + 102;
                filter.addParticle(new SimpleParticle(x, y));
        }

        tracker = new RectangularClusterTracker(chip);
        trackingFilterChain = new FilterChain(chip);
        trackingFilterChain.add(tracker);
        tracker.setEnclosed(true, this);        
        setEnclosedFilterChain(trackingFilterChain);

        setPropertyTooltip("colorClustersDifferentlyEnabled", 
                "each cluster gets assigned a random color, otherwise color indicates ages");
        setPropertyTooltip("filterEventsEnabled", "Just for test");
        
        // renderer = (AEFrameChipRenderer) chip.getRenderer();

    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!filterEnabled) {
                return in;
        }
        EventPacket filtered = getEnclosedFilterChain().filterPacket(in);

        // added so that packets don't use a zero length packet to set last
        // timestamps, etc, which can purge clusters for no reason
//        if (in.getSize() == 0) {
//                return in;
//        }
//
//        if (enclosedFilter != null) {
//                in = enclosedFilter.filterPacket(in);
//        }
//
        if (in instanceof ApsDvsEventPacket) {
                checkOutputPacketEventType(in); // make sure memory is allocated to avoid leak
        }
        else if (isFilterEventsEnabled()) {
                checkOutputPacketEventType(RectangularClusterTrackerEvent.class);
        }
//        
//        for (Object o : filtered) {
//            BasicEvent ev = (BasicEvent) o;
//        }
        
           
        RectangularClusterTracker.Cluster robot = getRobotCluster();
        
        if(robot == null) {
            return in;
        }
 
        locationX = robot.location.x;
        locationY = robot.location.y;
 
        Random r = new Random();
        measurement.setMu(locationX, locationY);
        filter.evaluateStrength();
        filter.resample(r);     
        outputX = filter.getAverageX();
        outputY = filter.getAverageY();
        
        float[] colors = new float[3];
        colors = ColorHelper.HSVtoRGB(3.0f, 1.0f, 1.0f);

        // filter.disperseDistribution(r, locationX);
        return in;
    }

    @Override
    public void resetFilter() {
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void initFilter() {
        measurement.setMu(0, 0);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        final GL2 gl = drawable.getGL().getGL2();
        try {
                gl.glEnable(GL.GL_BLEND);
                gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                gl.glBlendEquation(GL.GL_FUNC_ADD);
        }
        catch (final GLException e) {
                e.printStackTrace();
        }
        gl.glColor4f(.1f, .1f, 1f, .25f);
        gl.glLineWidth(1f);
        // for (final HotPixelFilter.HotPixel p : hotPixelSet) {
                gl.glRectf((int)outputX - 10, (int)outputY - 10, (int)outputX + 12, (int)outputY + 12);
        // }    
    } 
    
    public boolean isColorClustersDifferentlyEnabled() {
        return colorClustersDifferentlyEnabled;
    }
    
    /**
     * @param colorClustersDifferentlyEnabled
     *            true to color each cluster a
     *            different color. false to color each cluster by its age
     */
    public void setColorClustersDifferentlyEnabled(boolean colorClustersDifferentlyEnabled) {
            this.colorClustersDifferentlyEnabled = colorClustersDifferentlyEnabled;
            putBoolean("colorClustersDifferentlyEnabled", colorClustersDifferentlyEnabled);
    }   

    /**
     * @return the filterEventsEnabled
     */
    public boolean isFilterEventsEnabled() {
        return filterEventsEnabled;
    }

    /**
     * @param filterEventsEnabled the filterEventsEnabled to set
     */
    public void setFilterEventsEnabled(boolean filterEventsEnabled) {
        this.filterEventsEnabled = filterEventsEnabled;
        putBoolean("filterEventsEnabled", filterEventsEnabled);
    }

    /**
     * @return the tracker
     */
    public RectangularClusterTracker getTracker() {
        return tracker;
    }

    /**
     * @param tracker the tracker to set
     */
    public void setTracker(RectangularClusterTracker tracker) {
        this.tracker = tracker;
    }

    private RectangularClusterTracker.Cluster getRobotCluster() {
                if (tracker.getNumClusters() == 0) {
            return null;
        }
        float minDistance = Float.POSITIVE_INFINITY, f, minTimeToImpact = Float.POSITIVE_INFINITY;
        RectangularClusterTracker.Cluster closest = null, soonest = null;
        for (RectangularClusterTracker.Cluster c : tracker.getClusters()) {
                if (c.isVisible()) { // cluster must be visible
                    if ((f = c.location.y) < minDistance) {
                        // give closest ball unconditionally if not using
                        // ball velocityPPT
                        // but if using velocityPPT, then only give ball if
                        // it is moving towards goal
                        minDistance = f;
                        closest = c;
                        // will it hit earlier?
                } // visible
            }
        }

        return closest;
    }    
}
