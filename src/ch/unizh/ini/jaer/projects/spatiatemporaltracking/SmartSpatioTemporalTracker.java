/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.EventTracker;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.SimpleEventTracker;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.Parameters;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern.TemporalPatternStorage;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author matthias
 */
public class SmartSpatioTemporalTracker extends EventFilter2D implements Observer, FrameAnnotater {
    
    private EventTracker tracker;
    
    /**
     * Creates a new instance of the class LEDTracker.
     * 
     * @param chip The chip of the camera.
     */
    public SmartSpatioTemporalTracker(AEChip chip) {
        super(chip);
        
        this.initFilter();
        this.resetFilter();
    }
    
    @Override
    public void initFilter() {
        this.tracker = new SimpleEventTracker();
    }

    @Override
    public void resetFilter() {
        this.tracker.reset();
    }
    
    @Override
    public void update(Observable o, Object arg) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        this.tracker.track(in);
        
        return in;
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        this.tracker.draw(drawable);
    }
    
    /***************************************************************************
     * 
     */
    
    protected float clusterassignmentthreshold = getPrefs().getFloat("cluster.assignment.clusterassignmentthreshold", Parameters.getInstance().getAsFloat(Parameters.CLUSTER_ASSIGNMENT_THRESHOLD));
    {setPropertyTooltip("cluster.assignment","clusterassignmentthreshold","Defines the threshold for the assignment of clusters to a temporal pattern.");}

    protected float clusterdeletethreshold = getPrefs().getFloat("cluster.assignment.clusterdeletethreshold", Parameters.getInstance().getAsFloat(Parameters.CLUSTER_DELETION_THRESHOLD));
    {setPropertyTooltip("cluster.assignment","clusterdeletethreshold","Defines the threshold for the delete an unused cluster.");}

    protected float clustermergethreshold = getPrefs().getFloat("cluster.assignment.clustermergethreshold", Parameters.getInstance().getAsFloat(Parameters.CLUSTER_MERGE_THRESHOLD));
    {setPropertyTooltip("cluster.assignment","clustermergethreshold","Defines the threshold for the merge of clusters that are too similar.");}

    protected float eventassignmentchance = getPrefs().getFloat("event.assignment.eventassignmentchance", Parameters.getInstance().getAsFloat(Parameters.EVENT_ASSINGMENT_CHANCE));
    {setPropertyTooltip("event.assignment","eventassignmentchance","Defines the threshold for the fast rejection of events.");}

    protected float eventassignmentthreshold = getPrefs().getFloat("event.assignment.eventassignmentthreshold", Parameters.getInstance().getAsFloat(Parameters.EVENT_ASSINGMENT_THRESHOLD));
    {setPropertyTooltip("event.assignment","eventassignmentthreshold","Defines the threshold for the assignment of events.");}

    protected float spatialsharpness = getPrefs().getFloat("event.assignment.spatialsharpness", Parameters.getInstance().getAsFloat(Parameters.EVENT_ASSINGMENT_SPATIAL_SHARPNESS));
    {setPropertyTooltip("event.assignment","spatialsharpness","Defines the sharpness of the spatial cost function.");}

    protected float temporalsharpness = getPrefs().getFloat("event.assignment.temporalsharpness", Parameters.getInstance().getAsFloat(Parameters.EVENT_ASSINGMENT_TEMPORAL_SHARPNESS));
    {setPropertyTooltip("event.assignment","temporalsharpness","Defines the sharpness of the temporal cost function.");}

    protected float difference = getPrefs().getFloat("event.assignmnet.difference", Parameters.getInstance().getAsFloat(Parameters.EVENT_ASSINGMENT_DIFFERENCE));
    {setPropertyTooltip("event.assignmnet","difference","Defines the threshold used if two or more clusters are valid ones for the assignment.");}

    protected String extractorperiod = getPrefs().get("extractor.extractorperiod", Parameters.getInstance().getAsString(Parameters.EXTRACTOR_PERIOD));
    {setPropertyTooltip("extractor","extractorperiod","Defines which type of extractor has to be used to find the period of the signal.");}

    protected String extractorsignal = getPrefs().get("extractor.extractorsignal", Parameters.getInstance().getAsString(Parameters.EXTRACTOR_SIGNAL));
    {setPropertyTooltip("extractor","extractorsignal","Defines which type of extractor has to be used to find the signal.");}

    protected int candidateN = getPrefs().getInt("general.candidateN", Parameters.getInstance().getAsInteger(Parameters.GENERAL_CANDIDATE_N));
    {setPropertyTooltip("general","candidateN","Defines the maximal number of candidate clusters used by the tracker.");}

    protected int clusterN = getPrefs().getInt("general.clusterN", Parameters.getInstance().getAsInteger(Parameters.GENERAL_CLUSTER_N));
    {setPropertyTooltip("general","clusterN","Defines the maximal number of clusters used by the tracker.");}

    protected boolean debug = getPrefs().getBoolean("general.debug", Parameters.getInstance().getAsBoolean(Parameters.DEBUG_MODE));
    {setPropertyTooltip("general","debug","Visualizes all information available if activated.");}

    protected int method = getPrefs().getInt("general.method", Parameters.getInstance().getAsInteger(Parameters.METHOD));
    {setPropertyTooltip("general","method","no longer used...");}

    protected float accelerationforward = getPrefs().getFloat("predictor.accelerationforward", Parameters.getInstance().getAsFloat(Parameters.PREDICTOR_ACCELERATION_FORWARD));
    {setPropertyTooltip("predictor","accelerationforward","Defines the maximal forward acceleration of the object.");}

    protected float accelerationsideway = getPrefs().getFloat("predictor.accelerationsideway", Parameters.getInstance().getAsFloat(Parameters.PREDICTOR_ACCELERATION_SIDEWAY));
    {setPropertyTooltip("predictor","accelerationsideway","Defines the maximal sideway acceleration of the object.");}

    protected String accelerationtype = getPrefs().get("predictor.accelerationtype", Parameters.getInstance().getAsString(Parameters.PREDICTOR_ACCELERATION_TYPE));
    {setPropertyTooltip("predictor","accelerationtype","Defines the type of predictor used for the acceleration.");}

    protected int signalcorrelation = getPrefs().getInt("predictor.signalcorrelation", Parameters.getInstance().getAsInteger(Parameters.PREDICTOR_SINGAL_CORRELATION));
    {setPropertyTooltip("predictor","signalcorrelation","Defines the time interval after which the signal correlation has to be recomputed.");}

    protected String temporaltype = getPrefs().get("predictor.temporaltype", Parameters.getInstance().getAsString(Parameters.PREDICTOR_TEMPORAL_TYPE));
    {setPropertyTooltip("predictor","temporaltype","Defines the type of predictor used for the temporal prediction.");}

    protected int noiseduration = getPrefs().getInt("signal.noiseduration", Parameters.getInstance().getAsInteger(Parameters.NOISE_DURATION));
    {setPropertyTooltip("signal","noiseduration","Defines the level of noise within the tracker.");}

    protected float quality = getPrefs().getFloat("signal.quality", Parameters.getInstance().getAsFloat(Parameters.SIGNAL_QUALITY));
    {setPropertyTooltip("signal","quality","Defines the required quality of the extracted signal to be accepted.");}

    protected int temporalobservations = getPrefs().getInt("signal.temporalobservations", Parameters.getInstance().getAsInteger(Parameters.SIGNAL_TEMPORAL_OBSERVATIONS));
    {setPropertyTooltip("signal","temporalobservations","Defines the number of observations stored by the tracker.");}

    protected int temporalresolution = getPrefs().getInt("signal.temporalresolution", Parameters.getInstance().getAsInteger(Parameters.SIGNAL_TEMPORAL_RESOLUTION));
    {setPropertyTooltip("signal","temporalresolution","Defines the temporal resolution used by the tracker.");}

    protected float transitionpercentage = getPrefs().getFloat("signal.transitionpercentage", Parameters.getInstance().getAsFloat(Parameters.SIGNAL_TRANSITION_PERCENTAGE));
    {setPropertyTooltip("signal","transitionpercentage","Defines the number of events used to create a transition.");}

    protected float deviation = getPrefs().getFloat("transitionhistory.deviation", Parameters.getInstance().getAsFloat(Parameters.TRANSITION_HISTORY_DEVIATION));
    {setPropertyTooltip("transitionhistory","deviation","Defines allowed deviation in the number of elements of a possible transition compared to the maxima found.");}

    protected int distribution = getPrefs().getInt("transitionhistory.distribution", Parameters.getInstance().getAsInteger(Parameters.TRANSITION_HISTORY_DISTRIBUTION));
    {setPropertyTooltip("transitionhistory","distribution","Defines the average distribution of events belonging to the same transition.");}

    protected String kernelmethod = getPrefs().get("transitionhistory.kernelmethod", Parameters.getInstance().getAsString(Parameters.TRANSITION_HISTORY_KERNEL_METHODE));
    {setPropertyTooltip("transitionhistory","kernelmethod","Defines the method to convolve the signal with a kernel.");}

    protected int maxresolution = getPrefs().getInt("transitionhistory.maxresolution", Parameters.getInstance().getAsInteger(Parameters.TRANSITION_HISTORY_MAX_RESOLUTION));
    {setPropertyTooltip("transitionhistory","maxresolution","Defines the temporal resolution with which the local maximas are stored.");}

    protected int maxwindow = getPrefs().getInt("transitionhistory.maxwindow", Parameters.getInstance().getAsInteger(Parameters.TRANSITION_HISTORY_MAX_WINDOW));
    {setPropertyTooltip("transitionhistory","maxwindow","Defines the size of the window to search local maximas.");}




    public float getClusterassignmentthreshold() { 
        return this.clusterassignmentthreshold; 
    } 

    public void setClusterassignmentthreshold(final float clusterassignmentthreshold) { 
        getPrefs().putFloat("cluster.assignment.clusterassignmentthreshold", clusterassignmentthreshold); 
        this.clusterassignmentthreshold = clusterassignmentthreshold; 
        
        Parameters.getInstance().add(Parameters.CLUSTER_ASSIGNMENT_THRESHOLD, clusterassignmentthreshold); 
        this.tracker.updateListeners(); 
    } 

    public float getClusterdeletethreshold() { 
        return this.clusterdeletethreshold; 
    } 

    public void setClusterdeletethreshold(final float clusterdeletethreshold) { 
        getPrefs().putFloat("cluster.assignment.clusterdeletethreshold", clusterdeletethreshold); 
        this.clusterdeletethreshold = clusterdeletethreshold; 
        
        Parameters.getInstance().add(Parameters.CLUSTER_DELETION_THRESHOLD, clusterdeletethreshold); 
        this.tracker.updateListeners(); 
    } 

    public float getClustermergethreshold() { 
        return this.clustermergethreshold; 
    } 

    public void setClustermergethreshold(final float clustermergethreshold) { 
        getPrefs().putFloat("cluster.assignment.clustermergethreshold", clustermergethreshold); 
        this.clustermergethreshold = clustermergethreshold; 
        
        Parameters.getInstance().add(Parameters.CLUSTER_MERGE_THRESHOLD, clustermergethreshold); 
        this.tracker.updateListeners(); 
    } 

    public float getEventassignmentchance() { 
        return this.eventassignmentchance; 
    } 

    public void setEventassignmentchance(final float eventassignmentchance) { 
        getPrefs().putFloat("event.assignment.eventassignmentchance", eventassignmentchance); 
        this.eventassignmentchance = eventassignmentchance; 
        
        Parameters.getInstance().add(Parameters.EVENT_ASSINGMENT_CHANCE, eventassignmentchance); 
        this.tracker.updateListeners(); 
    } 

    public float getEventassignmentthreshold() { 
        return this.eventassignmentthreshold; 
    } 

    public void setEventassignmentthreshold(final float eventassignmentthreshold) { 
        getPrefs().putFloat("event.assignment.eventassignmentthreshold", eventassignmentthreshold); 
        this.eventassignmentthreshold = eventassignmentthreshold; 
        
        Parameters.getInstance().add(Parameters.EVENT_ASSINGMENT_THRESHOLD, eventassignmentthreshold); 
        this.tracker.updateListeners(); 
    } 

    public float getSpatialsharpness() { 
        return this.spatialsharpness; 
    } 

    public void setSpatialsharpness(final float spatialsharpness) { 
        getPrefs().putFloat("event.assignment.spatialsharpness", spatialsharpness); 
        this.spatialsharpness = spatialsharpness; 
        
        Parameters.getInstance().add(Parameters.EVENT_ASSINGMENT_SPATIAL_SHARPNESS, spatialsharpness); 
        this.tracker.updateListeners(); 
    } 

    public float getTemporalsharpness() { 
        return this.temporalsharpness; 
    } 

    public void setTemporalsharpness(final float temporalsharpness) { 
        getPrefs().putFloat("event.assignment.temporalsharpness", temporalsharpness); 
        this.temporalsharpness = temporalsharpness; 
        
        Parameters.getInstance().add(Parameters.EVENT_ASSINGMENT_TEMPORAL_SHARPNESS, temporalsharpness); 
        this.tracker.updateListeners(); 
    } 

    public float getDifference() { 
        return this.difference; 
    } 

    public void setDifference(final float difference) { 
        getPrefs().putFloat("event.assignmnet.difference", difference); 
        this.difference = difference; 
        
        Parameters.getInstance().add(Parameters.EVENT_ASSINGMENT_DIFFERENCE, difference); 
        this.tracker.updateListeners(); 
    } 

    public String getExtractorperiod() { 
        return this.extractorperiod; 
    } 

    public void setExtractorperiod(final String extractorperiod) { 
        getPrefs().put("extractor.extractorperiod", extractorperiod); 
        this.extractorperiod = extractorperiod; 
        
        Parameters.getInstance().add(Parameters.EXTRACTOR_PERIOD, extractorperiod); 
        this.tracker.updateListeners(); 
        
        this.resetFilter(); 
    } 

    public String getExtractorsignal() { 
        return this.extractorsignal; 
    } 

    public void setExtractorsignal(final String extractorsignal) { 
        getPrefs().put("extractor.extractorsignal", extractorsignal); 
        this.extractorsignal = extractorsignal; 
        
        Parameters.getInstance().add(Parameters.EXTRACTOR_SIGNAL, extractorsignal); 
        this.tracker.updateListeners(); 
        
        this.resetFilter(); 
    } 

    public int getCandidaten() { 
        return this.candidateN; 
    } 

    public void setCandidaten(final int candidateN) { 
        getPrefs().putInt("general.candidateN", candidateN); 
        this.candidateN = candidateN; 
        
        Parameters.getInstance().add(Parameters.GENERAL_CANDIDATE_N, candidateN); 
        this.tracker.updateListeners(); 
    } 

    public int getClustern() { 
        return this.clusterN; 
    } 

    public void setClustern(final int clusterN) { 
        getPrefs().putInt("general.clusterN", clusterN); 
        this.clusterN = clusterN; 
        
        Parameters.getInstance().add(Parameters.GENERAL_CLUSTER_N, clusterN); 
        this.tracker.updateListeners(); 
    } 

    public boolean getDebug() { 
        return this.debug; 
    } 

    public void setDebug(final boolean debug) { 
        getPrefs().putBoolean("general.debug", debug); 
        this.debug = debug; 
        
        Parameters.getInstance().add(Parameters.DEBUG_MODE, debug); 
        this.tracker.updateListeners(); 
    } 

    public int getMethod() { 
        return this.method; 
    } 

    public void setMethod(final int method) { 
        getPrefs().putInt("general.method", method); 
        this.method = method; 
        
        Parameters.getInstance().add(Parameters.METHOD, method); 
        this.tracker.updateListeners(); 
        
        this.resetFilter(); 
    } 

    public float getAccelerationforward() { 
        return this.accelerationforward; 
    } 

    public void setAccelerationforward(final float accelerationforward) { 
        getPrefs().putFloat("predictor.accelerationforward", accelerationforward); 
        this.accelerationforward = accelerationforward; 
        
        Parameters.getInstance().add(Parameters.PREDICTOR_ACCELERATION_FORWARD, accelerationforward); 
        this.tracker.updateListeners(); 
    } 

    public float getAccelerationsideway() { 
        return this.accelerationsideway; 
    } 

    public void setAccelerationsideway(final float accelerationsideway) { 
        getPrefs().putFloat("predictor.accelerationsideway", accelerationsideway); 
        this.accelerationsideway = accelerationsideway; 
        
        Parameters.getInstance().add(Parameters.PREDICTOR_ACCELERATION_SIDEWAY, accelerationsideway); 
        this.tracker.updateListeners(); 
    } 

    public String getAccelerationtype() { 
        return this.accelerationtype; 
    } 

    public void setAccelerationtype(final String accelerationtype) { 
        getPrefs().put("predictor.accelerationtype", accelerationtype); 
        this.accelerationtype = accelerationtype; 
        
        Parameters.getInstance().add(Parameters.PREDICTOR_ACCELERATION_TYPE, accelerationtype); 
        this.tracker.updateListeners(); 
    } 

    public int getSignalcorrelation() { 
        return this.signalcorrelation; 
    } 

    public void setSignalcorrelation(final int signalcorrelation) { 
        getPrefs().putInt("predictor.signalcorrelation", signalcorrelation); 
        this.signalcorrelation = signalcorrelation; 
        
        Parameters.getInstance().add(Parameters.PREDICTOR_SINGAL_CORRELATION, signalcorrelation); 
        this.tracker.updateListeners(); 
    } 

    public String getTemporaltype() { 
        return this.temporaltype; 
    } 

    public void setTemporaltype(final String temporaltype) { 
        getPrefs().put("predictor.temporaltype", temporaltype); 
        this.temporaltype = temporaltype; 
        
        Parameters.getInstance().add(Parameters.PREDICTOR_TEMPORAL_TYPE, temporaltype); 
        this.tracker.updateListeners(); 
    } 

    public int getNoiseduration() { 
        return this.noiseduration; 
    } 

    public void setNoiseduration(final int noiseduration) { 
        getPrefs().putInt("signal.noiseduration", noiseduration); 
        this.noiseduration = noiseduration; 
        
        Parameters.getInstance().add(Parameters.NOISE_DURATION, noiseduration); 
        this.tracker.updateListeners(); 
    } 

    public float getQuality() { 
        return this.quality; 
    } 

    public void setQuality(final float quality) { 
        getPrefs().putFloat("signal.quality", quality); 
        this.quality = quality; 
        
        Parameters.getInstance().add(Parameters.SIGNAL_QUALITY, quality); 
        this.tracker.updateListeners(); 
    } 

    public int getTemporalobservations() { 
        return this.temporalobservations; 
    } 

    public void setTemporalobservations(final int temporalobservations) { 
        getPrefs().putInt("signal.temporalobservations", temporalobservations); 
        this.temporalobservations = temporalobservations; 
        
        Parameters.getInstance().add(Parameters.SIGNAL_TEMPORAL_OBSERVATIONS, temporalobservations); 
        this.tracker.updateListeners(); 
    } 

    public int getTemporalresolution() { 
        return this.temporalresolution; 
    } 

    public void setTemporalresolution(final int temporalresolution) { 
        getPrefs().putInt("signal.temporalresolution", temporalresolution); 
        this.temporalresolution = temporalresolution; 
        
        Parameters.getInstance().add(Parameters.SIGNAL_TEMPORAL_RESOLUTION, temporalresolution); 
        this.tracker.updateListeners(); 
    } 

    public float getTransitionpercentage() { 
        return this.transitionpercentage; 
    } 

    public void setTransitionpercentage(final float transitionpercentage) { 
        getPrefs().putFloat("signal.transitionpercentage", transitionpercentage); 
        this.transitionpercentage = transitionpercentage; 
        
        Parameters.getInstance().add(Parameters.SIGNAL_TRANSITION_PERCENTAGE, transitionpercentage); 
        this.tracker.updateListeners(); 
    } 

    public float getDeviation() { 
        return this.deviation; 
    } 

    public void setDeviation(final float deviation) { 
        getPrefs().putFloat("transitionhistory.deviation", deviation); 
        this.deviation = deviation; 
        
        Parameters.getInstance().add(Parameters.TRANSITION_HISTORY_DEVIATION, deviation); 
        this.tracker.updateListeners(); 
    } 

    public int getDistribution() { 
        return this.distribution; 
    } 

    public void setDistribution(final int distribution) { 
        getPrefs().putInt("transitionhistory.distribution", distribution); 
        this.distribution = distribution; 
        
        Parameters.getInstance().add(Parameters.TRANSITION_HISTORY_DISTRIBUTION, distribution); 
        this.tracker.updateListeners(); 
    } 

    public String getKernelmethod() { 
        return this.kernelmethod; 
    } 

    public void setKernelmethod(final String kernelmethod) { 
        getPrefs().put("transitionhistory.kernelmethod", kernelmethod); 
        this.kernelmethod = kernelmethod; 
        
        Parameters.getInstance().add(Parameters.TRANSITION_HISTORY_KERNEL_METHODE, kernelmethod); 
        this.tracker.updateListeners(); 
    } 

    public int getMaxresolution() { 
        return this.maxresolution; 
    } 

    public void setMaxresolution(final int maxresolution) { 
        getPrefs().putInt("transitionhistory.maxresolution", maxresolution); 
        this.maxresolution = maxresolution; 
        
        Parameters.getInstance().add(Parameters.TRANSITION_HISTORY_MAX_RESOLUTION, maxresolution); 
        this.tracker.updateListeners(); 
    } 

    public int getMaxwindow() { 
        return this.maxwindow; 
    } 

    public void setMaxwindow(final int maxwindow) { 
        getPrefs().putInt("transitionhistory.maxwindow", maxwindow); 
        this.maxwindow = maxwindow; 
        
        Parameters.getInstance().add(Parameters.TRANSITION_HISTORY_MAX_WINDOW, maxwindow); 
        this.tracker.updateListeners(); 
    } 


    
    /*
     * 
     **************************************************************************/
}
