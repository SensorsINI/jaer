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
import java.util.ArrayList;
import java.util.Arrays;
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
    private float threshold = getFloat("threshold", 100);
    private int startPositionX = getInt("x", 0);
    private int startPositionY = getInt("y", 0);

    FilterChain trackingFilterChain;
    private RectangularClusterTracker tracker;
    private HeatMapCNN heatMapCNN;
    
    private double outputX, outputY;
    private double[] measurementLocationsX = new double[4], measurementLocationsY = new double[4];
    
    // private final AEFrameChipRenderer renderer;

    
    public ParticleFilterTracking(AEChip chip) {
        super(chip);
        
        this.outputX = 0;
        this.outputY = 0;
        
        dynamic = new DynamicEvaluator();
        measurement = new MeasurmentEvaluator();
        average = new AverageEvaluator();
        filter = new ParticleFilter(dynamic, measurement, average);
        
        Random r = new Random();
        for(int i = 0; i < 1000; i++) {
                double x = 10 * r.nextGaussian() + getStartPositionX();
                double y = 10 * r.nextGaussian() + getStartPositionY();
                filter.addParticle(new SimpleParticle(x, y));
        }

        tracker = new RectangularClusterTracker(chip);
        heatMapCNN = new HeatMapCNN(chip);
        trackingFilterChain = new FilterChain(chip);
        trackingFilterChain.add(tracker);
        trackingFilterChain.add(heatMapCNN);
        tracker.setFilterEnabled(false);
        tracker.setEnclosed(true, this);        
        heatMapCNN.getSupport().addPropertyChangeListener(HeatMapCNN.OUTPUT_AVAILBLE, this);
        setEnclosedFilterChain(trackingFilterChain);

        setPropertyTooltip("colorClustersDifferentlyEnabled", 
                "each cluster gets assigned a random color, otherwise color indicates ages");
        setPropertyTooltip("filterEventsEnabled", "Just for test");      
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
        
           
        // RectangularClusterTracker.Cluster robot = getRobotCluster();
        
        int i = 0, visibleCnt = 0;
        boolean[] visibleFlg = new boolean[3];
        for (RectangularClusterTracker.Cluster c : tracker.getClusters()) {
            measurementLocationsX[i] = c.location.x;
            measurementLocationsY[i] = c.location.y;
            visibleFlg[i] = c.isVisible();
            i = i + 1;
            if(c.isVisible()) {
                visibleCnt = visibleCnt + 1;                
            }     
        }
 
        
        Random r = new Random();
//        measurementLocationsX[3] = heatMapCNN.getOutputX();
//        measurementLocationsY[3] = heatMapCNN.getOutputY();
        measurement.setMu(measurementLocationsX, measurementLocationsY);
        double originSum = 0;
        double effectiveNum = 0;
        if(visibleCnt != 0) {
            measurement.setVisibleCluster(visibleFlg);
            filter.evaluateStrength();            
            originSum = filter.normalize(); // The sum value before normalize
            effectiveNum = filter.calculateNeff();
            if(originSum > threshold /* && effectiveNum < filter.getParticleCount() * 0.75*/) {
                filter.resample(r);   
            } else {
                // filter.resample(r);
            }
        }
   
        outputX = filter.getAverageX();
        outputY = filter.getAverageY();
        if(outputX > 240 || outputY > 180 || outputX < 0 || outputY < 0) {
            for(i = 0; i < filter.getParticleCount(); i++) {
                filter.get(i).setX(120 + 50 * (r.nextDouble() * 2 - 1));
                filter.get(i).setY(90 + 50 * (r.nextDouble() * 2 - 1));
            }
        }
        
        return in;
    }

    @Override
    public void resetFilter() {
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void initFilter() {
        double[] xArray = new double[4];
        double[] yArray = new double[4];
        Arrays.fill(xArray, 0);
        Arrays.fill(yArray, 0);

        measurement.setMu(xArray, yArray);
    }

    @Override
    public synchronized void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(HeatMapCNN.OUTPUT_AVAILBLE)) {
            float[] map = this.heatMapCNN.getHeatMap();
            measurementLocationsX[3] = heatMapCNN.getOutputX();
            measurementLocationsY[3] = heatMapCNN.getOutputY();
            heatMapCNN.getOutputProbVal();
        }
        
//        Random r = new Random();
//
//        measurement.setMu(measurementLocationsX, measurementLocationsY);
//        double originSum = 0;
//        double effectiveNum = 0;
//
//        filter.evaluateStrength();            
//        originSum = filter.normalize(); // The sum value before normalize
//        effectiveNum = filter.calculateNeff();
//        if(originSum > threshold /* && effectiveNum < filter.getParticleCount() * 0.75*/) {
//            filter.resample(r);   
//        } else {
//            filter.resample(r);
//        }
//        
//   
//        outputX = filter.getAverageX();
//        outputY = filter.getAverageY();
//        if(outputX > 240 || outputY > 180 || outputX < 0 || outputY < 0) {
//            for(int i = 0; i < filter.getParticleCount(); i++) {
//                filter.get(i).setX(120 + 50 * (r.nextDouble() * 2 - 1));
//                filter.get(i).setY(90 + 50 * (r.nextDouble() * 2 - 1));
//            }
//        }
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
        for(int i = 0; i < filter.getParticleCount(); i ++) {            
            gl.glRectd(filter.get(i).getX() - 0.5, filter.get(i).getY() - 0.5, filter.get(i).getX() + 0.5, filter.get(i).getY() + 0.5);
        }
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

    /**
     * @return the startPositionX
     */
    public int getStartPositionX() {
        return startPositionX;
    }

    /**
     * @param startPositionX the startPositionX to set
     */
    public void setStartPositionX(int startPositionX) {
        putInt("x", startPositionX);
        this.startPositionX = startPositionX;
    }

    /**
     * @return the startPositionY
     */
    public int getStartPositionY() {
        return startPositionY;
    }

    /**
     * @param startPositionY the startPositionY to set
     */
    public void setStartPositionY(int startPositionY) {
        putInt("y", startPositionY);
        this.startPositionY = startPositionY;
    }

    /**
     * @return the threshold
     */
    public float getThreshold() {
        return threshold;
    }

    /**
     * @param threshold the threshold to set
     */
    public void setThreshold(float threshold) {
        putFloat("threshold", threshold);
        this.threshold = threshold;
    }
}
