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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTrackerEvent;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.ParticleFilter.DynamicEvaluator;
import net.sf.jaer.util.filter.ParticleFilter.MeasurmentEvaluator;
import net.sf.jaer.util.filter.ParticleFilter.ParticleFilter;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.AEViewer;
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
    
    private boolean Useframe = false;
    private boolean UseClustersFrametime = false;
    private boolean UseClustersRealtime = false;
    private boolean filterEventsEnabled = getBoolean("filterEventsEnabled", false); // enables filtering events so
    private float threshold = getFloat("threshold", 2);
    private int decay = getInt("decay", 1);
    private float noise = getFloat("noise", 5);
    private int startPositionX = getInt("x", 0);
    private int startPositionY = getInt("y", 0);
    private int particlesCount = getInt("particlesCount", 1000);
    private boolean UsePureEvents = getBoolean("UsePureEvents", false);
    private boolean displayParticles = getBoolean("displayParticles", false);
    private int eventsNumToProcess = getInt("eventsNumToProcess", 10);

    private boolean addedViewerPropertyChangeListener = false; // TODO promote these to base EventFilter class
    private boolean addTimeStampsResetPropertyChangeListener = false;

    FilterChain trackingFilterChain;
    private RectangularClusterTracker tracker;
    private HeatMapCNN heatMapCNN;
    private String outputFilename; 
    private double outputX, outputY;
    private List<Float> measurementLocationsX = new ArrayList<Float>(), measurementLocationsY = new ArrayList<Float>();
    private List<Boolean> enableFlg = new ArrayList<Boolean>(); // enable flag for the measurement
    private List<Double> measurementWeight = new ArrayList<Double>();

    // private final AEFrameChipRenderer renderer;

    
    public ParticleFilterTracking(AEChip chip) {
        super(chip);
        
        outputX = getStartPositionX();
        outputY = getStartPositionY();
        
        dynamic = new DynamicEvaluator(noise);
        measurement = new MeasurmentEvaluator();
        average = new AverageEvaluator();

        tracker = new RectangularClusterTracker(chip);
        heatMapCNN = new HeatMapCNN(chip);
        trackingFilterChain = new FilterChain(chip);
        trackingFilterChain.add(tracker);
        trackingFilterChain.add(heatMapCNN);
        tracker.setFilterEnabled(false);
        tracker.setEnclosed(true, this);        
        this.heatMapCNN.setTracker(this);
        heatMapCNN.getSupport().addPropertyChangeListener(HeatMapCNN.OUTPUT_AVAILBLE, this);
        setEnclosedFilterChain(trackingFilterChain);
        
        // Save the result to the file
        Format formatter = new SimpleDateFormat("YYYY-MM-dd_hh-mm-ss");
        // Instantiate a Date object
        Date date = new Date();
         
        outputFilename = "PF_Output_Location" + formatter.format(date);

        
        setPropertyTooltip("colorClustersDifferentlyEnabled", 
                "each cluster gets assigned a random color, otherwise color indicates ages.");
        setPropertyTooltip("threshold", "The resample threshold");
        setPropertyTooltip("displayParticles", "Display all the particles");
        setPropertyTooltip("startPositionX", "Particles start position x");
        setPropertyTooltip("startPositionY", "Particles start position y");
        setPropertyTooltip("UsePureEvents", "Only use events");
        setPropertyTooltip("eventsNumToProcess", "The events in the packet will be processed");
        // setPropertyTooltip("filterEventsEnabled", "Just for test");      
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!filterEnabled) {
                return in;
        }
        if(in.size == 0) {  // Empty packet
            return in;
        }
        EventPacket filtered = getEnclosedFilterChain().filterPacket(in);

        if (in instanceof ApsDvsEventPacket) {
                checkOutputPacketEventType(in); // make sure memory is allocated to avoid leak
        }
        else if (isFilterEventsEnabled()) {
                checkOutputPacketEventType(RectangularClusterTrackerEvent.class);
        }

        maybeAddListeners(chip); // Add listeners for the AEChip's Events (such as REWIND)

        // updated at every income event, notice: compuatation very expensive.
        if(UsePureEvents) {
            // Clear the measurement list first
            measurementLocationsX.removeAll(measurementLocationsX);
            measurementLocationsY.removeAll(measurementLocationsY);
            enableFlg.removeAll(enableFlg);      
            measurementWeight.removeAll(measurementWeight);
            int numProcessEvents = eventsNumToProcess;
            if(eventsNumToProcess > in.getSize()) {
                numProcessEvents = in.getSize();
            }
            for(int nCnt = 0; nCnt < numProcessEvents; nCnt++) {
                if(measurementLocationsX.size() <= 0) {
                    measurementLocationsX.add(0, (float)in.getEvent(nCnt).getX());
                    measurementLocationsY.add(0, (float)in.getEvent(nCnt).getY());
                    enableFlg.add(0, true);                    
                    measurementWeight.add(0, 1.0);
                } else {
                    measurementLocationsX.set(0, (float)in.getEvent(nCnt).getX());
                    measurementLocationsY.set(0, (float)in.getEvent(nCnt).getY());
                    enableFlg.set(0, true);   
                    measurementWeight.set(0, 1.0);
                }      
                Random r = new Random();

                filterProcess();

                outputX = filter.getAverageX();
                outputY = filter.getAverageY();
                /* If particles are outside, then they will be reset to the center point. */
                if(outputX > 240 || outputY > 180 || outputX < 0 || outputY < 0) {
                    for(int i = 0; i < filter.getParticleCount(); i++) {
                        filter.get(i).setX(120 + 50 * (r.nextDouble() * 2 - 1));
                        filter.get(i).setY(90 + 50 * (r.nextDouble() * 2 - 1));
                    }
                }                
            }    
            
            return in;
        } 

        int i = 0, visibleCnt = 0;
        int numMeasure = 0;
        if(tracker.isFilterEnabled()) {
            for (RectangularClusterTracker.Cluster c : tracker.getClusters()) {
                if(measurementLocationsX.size() <= i) {
                    measurementLocationsX.add(i, c.location.x);
                    measurementLocationsY.add(i, c.location.y);
                    enableFlg.add(i, c.isVisible()); 
                    measurementWeight.add(1.0);
                } else {
                    measurementLocationsX.set(i, c.location.x);
                    measurementLocationsY.set(i, c.location.y);
                    enableFlg.set(i, c.isVisible());
                }

                i = i + 1;
                if(c.isVisible()) {
                    visibleCnt = visibleCnt + 1;                
                }     
            }
            numMeasure = tracker.getMaxNumClusters();
        } else {
            numMeasure = 0;
        }
        
        // If heatMap is enabled, then the number of the measurement should add 1.
        if(heatMapCNN.isFilterEnabled()) { 
            if(measurementLocationsX.size() == numMeasure + 1) {
                measurementWeight.set(measurementWeight.size() - 1, measurementWeight.get(numMeasure)*decay);     
                numMeasure = numMeasure + 1;
            }
        }

        if(measurementLocationsX.size() > numMeasure) { 

            // We should make the size of the measurement always equal to the clusters number
            for(int removeCnt = measurementLocationsX.size() - 1; removeCnt >= numMeasure; removeCnt--) {
                measurementLocationsX.remove(removeCnt);
                measurementLocationsY.remove(removeCnt);
                enableFlg.remove(removeCnt);
                measurementWeight.remove(removeCnt);
            }             
        }
        
        Random r = new Random();
        filterProcess();
   
        outputX = filter.getAverageX();
        outputY = filter.getAverageY();
        /* If particles are outside, then they will be reset to the center point. */
        if(outputX > 240 || outputY > 180 || outputX < 0 || outputY < 0) {
            for(i = 0; i < filter.getParticleCount(); i++) {
                filter.get(i).setX(120 + 50 * (r.nextDouble() * 2 - 1));
                filter.get(i).setY(90 + 50 * (r.nextDouble() * 2 - 1));
            }
        }                 

        try (FileWriter outFile = new FileWriter(outputFilename,true)) {
            outFile.write(String.format(in.getFirstEvent().getTimestamp() + " " + (int)outputX + " " + (int)outputY + "\n"));
            outFile.close();
        } catch (IOException ex) {
            Logger.getLogger(ParticleFilter.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception e) {
            log.warning("Caught " + e + ". See following stack trace.");
            e.printStackTrace();
        }
        
        return in;
    }

    /* The rewind event will revoke this function to init the filter and the particles.
       TODO: The content of this function should be moved to a new function, such as SetupFilter(); 
    */
    @Override
    public void resetFilter() {
        filter = new ParticleFilter(dynamic, measurement, average);
        
        Random r = new Random();
        for(int i = 0; i < particlesCount; i++) {
//                double x = (chip.getSizeX()/2) * (r.nextDouble()*2 - 1) + chip.getSizeX()/2;
//                double y = (chip.getSizeX()/2) * (r.nextDouble()*2 - 1) + chip.getSizeX()/2;
                double x = r.nextGaussian() + startPositionX;
                double y = r.nextGaussian() + startPositionY;
                filter.addParticle(new SimpleParticle(x, y));
        }    
    }

    @Override
    public void initFilter() {
        List<Float> xArray = new ArrayList<Float>();
        List<Float> yArray = new ArrayList<Float>();

        for(int i = 0; i < tracker.getMaxNumClusters(); i ++) {
            xArray.add((float)0);
            yArray.add((float)0);
        }

        measurement.setMu(xArray, yArray);
    }

    @Override
    public synchronized void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(HeatMapCNN.OUTPUT_AVAILBLE)) {
            float[] map = this.heatMapCNN.getHeatMap();
            int clustersNum = 0;            
            if(tracker.isFilterEnabled()) {
                clustersNum = tracker.getMaxNumClusters();
            } else {
                measurementLocationsX.removeAll(measurementLocationsX);
                measurementLocationsY.removeAll(measurementLocationsY);
                enableFlg.removeAll(enableFlg);
                measurementWeight.removeAll(measurementWeight);
                clustersNum = 0;
            }
            if(measurementLocationsX.size() <= clustersNum) {
                measurementLocationsX.add((float)heatMapCNN.getOutputX());
                measurementLocationsY.add((float)heatMapCNN.getOutputY());    
                enableFlg.add(true);
                measurementWeight.add(1.0);
            } else {
                measurementLocationsX.set(clustersNum, (float)heatMapCNN.getOutputX());
                measurementLocationsY.set(clustersNum, (float)heatMapCNN.getOutputY());
                enableFlg.set(clustersNum, true);
                measurementWeight.set(clustersNum, 1.0);
            }          
        }
        
        if(evt.getPropertyName().equals(AEInputStream.EVENT_REWIND)) {
            resetFilter();
        }
    }

    public final void maybeAddListeners(AEChip chip) {
        if (chip.getAeViewer() != null) {
            if (!addedViewerPropertyChangeListener) {
                chip.getAeViewer().addPropertyChangeListener(this);
                addedViewerPropertyChangeListener = true;
            }
            if (!addTimeStampsResetPropertyChangeListener) {
                chip.getAeViewer().addPropertyChangeListener(AEViewer.EVENT_TIMESTAMPS_RESET, this);
                addTimeStampsResetPropertyChangeListener = true;
            }
        }
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
        gl.glColor4f(1f, .1f, .1f, .25f);
        gl.glLineWidth(1f);
        
        if(displayParticles) {
            gl.glColor4f(.1f, 1f, .1f, .25f);

            for(int i = 0; i < filter.getParticleCount(); i ++) {            
                gl.glRectd(filter.get(i).getX() - 0.5, filter.get(i).getY() - 0.5, filter.get(i).getX() + 0.5, filter.get(i).getY() + 0.5);
            }            
        }
        
        gl.glColor4f(.1f, .1f, 1f, .25f);

        gl.glRectf((int)outputX - 10, (int)outputY - 10, (int)outputX + 12, (int)outputY + 12);

        // }    
    }   
    
    public void filterProcess() {
        Random r = new Random();

        measurement.setMu(measurementLocationsX, measurementLocationsY);
        measurement.setMeasurementWeight(measurementWeight);
        
        double originSum = 0;
        double effectiveNum = 0;
        // if(visibleCnt != 0) {
            measurement.setVisibleCluster(enableFlg);
            filter.evaluateStrength();            
            originSum = filter.normalize(); // The sum value before normalize
            effectiveNum = filter.calculateNeff();
            if(originSum > threshold /* && effectiveNum < filter.getParticleCount() * 0.75*/) {
                filter.resample(r);   
            } else {
                filter.updateWeight();
            }
        // }
    }

    public double getOutputX() {
        return outputX;
    }

    public double getOutputY() {
        return outputY;
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
        super.setFilterEnabled(false);
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
        public float getUseframe() {
        return threshold;
    }

    /**
     * @param Useframe the Useframe to set
     */
    public boolean isUseframe() {
        return Useframe;
    }

    /**
     * @param Useframe the Useframe to set
     */
    public void setUseframe(boolean Useframe) {
        this.Useframe = Useframe;
    }

    /**
     * @return the UseClustersFrametime
     */
    public boolean isUseClustersFrametime() {
        return UseClustersFrametime;
    }

    /**
     * @param UseClustersFrametime the UseClustersFrametime to set
     */
    public void setUseClustersFrametime(boolean UseClustersFrametime) {
        this.UseClustersFrametime = UseClustersFrametime;
    }

    /**
     * @return the UseClustersRealtime
     */
    public boolean isUseClustersRealtime() {
        return UseClustersRealtime;
    }

    /**
     * @param UseClustersRealtime the UseClustersRealtime to set
     */
    public void setUseClustersRealtime(boolean UseClustersRealtime) {
        this.UseClustersRealtime = UseClustersRealtime;
    }

    /**
     * @return the particlesCount
     */
    public int getParticlesCount() {
        return particlesCount;
    }

    /**
     * @param particlesCount the particlesCount to set
     */
    public void setParticlesCount(int particlesCount) {
        this.particlesCount = particlesCount;
        putInt("particlesCount", particlesCount);
    }

    /**
     * @return the UsePureEvents
     */
    public boolean isUsePureEvents() {
        return UsePureEvents;
    }

    /**
     * @param UsePureEvents the UsePureEvents to set
     */
    public void setUsePureEvents(boolean UsePureEvents) {
        this.UsePureEvents = UsePureEvents;
        putBoolean("UsePureEvents", UsePureEvents);
    }

    /**
     * @return the displayParticles
     */
    public boolean isDisplayParticles() {
        return displayParticles;
    }

    /**
     * @param displayParticles the displayParticles to set
     */
    public void setDisplayParticles(boolean displayParticles) {
        this.displayParticles = displayParticles;
        putBoolean("displayParticles", displayParticles);
    }

    /**
     * @return the decay
    */
    public int getDecay() {
        return decay;
    }

    /**
     * @param decay the decay to set
    */
    public void setDecay(int decay) {
        this.decay = decay;
        putInt("decaytest", decay);
    }
    
    /**
     * @return the eventsNumToProcess
     */
    public int getEventsNumToProcess() {
        return eventsNumToProcess;
    }

    /**
     * @param eventsNumToProcess the eventsNumToProcess to set
     */
    public void setEventsNumToProcess(int eventsNumToProcess) {
        this.eventsNumToProcess = eventsNumToProcess;
        putInt("eventsNumToProcess", eventsNumToProcess);
    }

    /**
     * @return the dynamicModelNoise
     */
    public float getNoise() {
        return noise;
    }

    /**
     * @param dynamicModelNoise the dynamicModelNoise to set
     */
    public void setNoise(float noise) {
        this.noise = noise;
        putFloat("noise", noise);
    }


 
}
