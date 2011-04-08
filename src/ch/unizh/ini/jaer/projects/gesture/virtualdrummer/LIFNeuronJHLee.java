/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

import java.awt.geom.Point2D;
import net.sf.jaer.event.BasicEvent;

/**
 *
 * @author Jun Haeng Lee
 */


/**
 * Definition of leaky integrate and fire (LIF) neuron.
 * The receptive field is a partial area of events-occuring space.
 * Events within the receptive field of a neuron are considered strongly correalated.
 * Spacing of the receptive field of two adjacent LIF neurons is decided to the half of side length of the receptive field to increase the spatial resolution.
 * Thus, each neuron shares half area of the receptive field with its neighbor.
 */
public class LIFNeuronJHLee {
    /**
     * id for this neuron
     */
    protected int cellNumber;

    /**
     *  spatial location of a neuron in chip pixels
     */
    public Point2D.Float location = new Point2D.Float();

    /** The "membranePotential" of the neuron.
     * The membranePotential decays over time (i.e., leaky) and is incremented by one by each collected event.
     * The membranePotential decays with a first order time constant of tauMP in us.
     * The membranePotential dreases by the amount of MPJumpAfterFiring after firing an event.
     */
    protected float membranePotential = 0;

    /**
     * This is the last in timestamp ticks that the neuron was updated, by an event
     */
    protected int lastEventTimestamp;

    /**
     * size of the receptive field.
     */
    protected int receptiveFieldSize;

    /**
     * maximum time constant of membrane potentail
     */
    protected float tauMP;

    /**
     * threshold
     */
    protected float thresholdMP;

    /**
     * amount of membrane potential that decreases after firing
     */
    protected float MPDecreaseArterFiringPercentTh;

    /**
     * number of spikes fired since
     */
    protected int numSpikes;

    /**
     * refractory period
     */
    protected float RefractoryPeriod = 0.0f;

    /**
     * control parameter for refractory period
     */
    protected float adaptationParam = 0.0f;

    /**
     * last timestamp that the MP if LIF neuron went above the threshold
     * The neuron might fired or not at this timing depending on the refractory period
     */
    protected int lastAboveThresholdTimestamp = -1;

    /**
     * timestamp of the last spike
     */
    protected int lastSpikeTimestamp = 0;

    /**
     * if true, uses Adaptation.
     */
    protected boolean enableAdaptation = false;

    /**
     * type of adaptation
     */
    protected ADAPTATION_TYPE adaptationType = ADAPTATION_TYPE.REFRACTORY_PERIOD;

    /**
     * time constant of LIF neuron's adaptation parameter
     */
    protected float adaptationParamTauMs = 10.0f;

    /**
     * max value of the control parameter for LIF neuron's adaptation parameter
     */
    protected float adaptationParamMax = 100.0f;

    /**
     * delta of the control parameter for LIF neuron's adaptation parameter
     */
    protected float adaptationParamDelta = 0.1f;

    /**
     * slope of the control parameter for LIF neuron's adaptation parameter
     */
    protected float adaptationParamSlop = 30.0f;

    /**
     * maximum value of LIF neuron's refractory period
     */
    protected float RefractoryPeriodMaxMs = 1.0f;




    /**
     * type of adaptation
     * TODO : adds more adaptation types
     */
    public static enum ADAPTATION_TYPE {
        /**
         * adapts the refractory period
         */
        REFRACTORY_PERIOD,
    }
    
    
    /**
     * Construct an LIF neuron with index.
     *
     * @param cellNumber : cell number
     * @param index : cell index
     * @param location : location on DVS pixels (x,y)
     * @param receptiveFieldSize : size of the receptive field
     * @param tauMP : RC time constant of the membrane potential
     * @param thresholdMP : threshold of the membrane potential to fire a spike
     * @param MPDecreaseArterFiringPercentTh : membrane potential jump after the spike in the percents of thresholdMP
     */
    public LIFNeuronJHLee(int cellNumber, Point2D.Float location, int receptiveFieldSize, float tauMP, float thresholdMP, float MPDecreaseArterFiringPercentTh) {
        // sets invariable parameters
        this.cellNumber = cellNumber;
        this.location.setLocation(location);
        this.receptiveFieldSize = receptiveFieldSize;
        this.tauMP = tauMP;
        this.thresholdMP = thresholdMP;
        this.MPDecreaseArterFiringPercentTh = MPDecreaseArterFiringPercentTh;

        // resets initially variable parameters
        membranePotential = 0;
        numSpikes = 0;
        lastEventTimestamp = 0;
        RefractoryPeriod = 0;
        adaptationParam = 0;
        lastAboveThresholdTimestamp = -1;
        lastSpikeTimestamp = 0;
    }

    /**
     * Resets a neuron with initial values
     */
    public void reset() {
        membranePotential = 0;
        numSpikes = 0;
        lastEventTimestamp = 0;
        RefractoryPeriod = 0;
        adaptationParam = 0;
        lastAboveThresholdTimestamp = -1;
        lastSpikeTimestamp = 0;
    }

    /**
     * set numSpikes
     *
     * @param n
     */
    public final void setNumSpikes(int n) {
        numSpikes = n;
    }

    /**
     * returns numSpikes
     * @return
     */
    public int getNumSpikes(){
        return numSpikes;
    }

    /**
     * updates a neuron with an additional event.
     *
     * @param event
     * @param weight
     */
    public void addEvent(BasicEvent event,float weight) {
        incrementMP(event.timestamp, weight);
        lastEventTimestamp = event.timestamp;

        if(enableAdaptation){
            if(lastAboveThresholdTimestamp == -1){
                adaptationParam = 0;
            }else{
                float deltaMs = 0.001f * (lastEventTimestamp - lastAboveThresholdTimestamp);
                adaptationParam = (adaptationParam + adaptationParamDelta)*((float) Math.exp((double) (-deltaMs/adaptationParamTauMs)));
            }

            switch(adaptationType){
                case REFRACTORY_PERIOD:
                    if(MPDecreaseArterFiringPercentTh > 0 && membranePotential >= thresholdMP){
                        // calculates refractory period
                        if(adaptationParam < adaptationParamMax){
                            RefractoryPeriod = RefractoryPeriodMaxMs*(1.0f - (float)Math.exp((double) -adaptationParam/adaptationParamSlop));

                            // if it's not constrained by the refractory period
                            if(lastEventTimestamp > lastSpikeTimestamp + (int)(RefractoryPeriod*1000f)){
                                // fires a spike
                                numSpikes++;
                                // decreases MP by MPJumpAfterFiring after firing
                                reduceMPafterFiring();
                                // spike timing
                                lastSpikeTimestamp = lastEventTimestamp;
                            } else {
                                membranePotential = thresholdMP;
                            }
                        } else {
                            membranePotential = thresholdMP;
                        }
                    }
                    break;
                default:
                    break;
            }
        } else {
            if(MPDecreaseArterFiringPercentTh > 0 && membranePotential >= thresholdMP){
                // fires a spike
                numSpikes++;
                // decreases MP by MPJumpAfterFiring after firing
                reduceMPafterFiring();
            }
        }

        lastAboveThresholdTimestamp = lastEventTimestamp;
    }

    /**
     * Computes and returns membranePotential at time t, using the last time an event hit this neuron
     * and the tauMP. Does not change the membranePotential itself.
     *
     * @param t timestamp now.
     * @return the membranePotential.
     */
    protected float getMPNow(int t) {
        float m = membranePotential * (float) Math.exp(((float) (lastEventTimestamp - t)) / tauMP);
        if(m < 1e-3f)
            m = 1e-3f;

        return m;
    }

    /**
     * returns the membranePotential without considering the current time.
     *
     * @return membranePotential
     */
    public float getMP() {
        return membranePotential;
    }

    /**
     * sets membranePotential
     * @param membranePotential
     */
    public void setMP(float membranePotential){
        this.membranePotential = membranePotential;
    }

    /**
     * Increments membranePotential of the neuron by amount of weight after decaying it away since the lastEventTimestamp according
     * to exponential decay with time constant tauMP.
     *
     * @param timeStamp
     * @param weight
     */
    public void incrementMP(int timeStamp, float weight) {
        float timeDiff = (float) lastEventTimestamp - timeStamp;
        if(timeDiff > 0)
            membranePotential = 0;
        else{
            membranePotential = weight + membranePotential * (float) Math.exp(timeDiff / tauMP);
            if(membranePotential < 0f)
                membranePotential = 0f;
        }
    }

    /**
     * returns the neuron's location in pixels.
     *
     * @return
     */
    final public Point2D.Float getLocation() {
        return location;
    }

    /**
     * decreases MP by MPJumpAfterFiring after firing
     */
    public void reduceMPafterFiring(){
        float MPjump = thresholdMP*MPDecreaseArterFiringPercentTh/100.0f;
        if(MPjump < 1.0f)
            MPjump = 1.0f;

        membranePotential -= MPjump;
    }

    /**
     * returns the cell number of a neuron
     *
     * @return cell number
     */
    public int getNeuronNumber() {
        return cellNumber;
    }

    /**
     * returns the last event timestamp
     *
     * @return timestamp of the last event collected by the neuron
     */
    public int getLastEventTimestamp() {
        return lastEventTimestamp;
    }

    /**
     * sets the last event timestamp
     *
     * @param lastEventTimestamp
     */
    public void setLastEventTimestamp(int lastEventTimestamp) {
        this.lastEventTimestamp = lastEventTimestamp;
    }

    /**
     * returns receptiveFieldSize
     *
     * @return
     */
    public int getReceptiveFieldSize() {
        return receptiveFieldSize;
    }

    /**
     * returns the current control parameter value for refractory period
     * @return
     */
    public float getRPControlParam() {
        return adaptationParam;
    }

    /**
     * returns MPDecreaseArterFiringPercentTh
     * @return
     */
    public float getMPDecreaseArterFiringPercentTh() {
        return MPDecreaseArterFiringPercentTh;
    }

    /**
     * sets MPDecreaseArterFiringPercentTh
     * @param MPDecreaseArterFiringPercentTh
     */
    public void setMPDecreaseArterFiringPercentTh(float MPDecreaseArterFiringPercentTh) {
        this.MPDecreaseArterFiringPercentTh = MPDecreaseArterFiringPercentTh;
    }

    /**
     * returns adaptationParam
     * @return
     */
    public float getAdaptationParam() {
        return adaptationParam;
    }

    /**
     * returns tauMP
     * @return
     */
    public float getTauMP() {
        return tauMP;
    }

    /**
     * sets tauMP
     * @param tauMP
     */
    public void setTauMP(float tauMP) {
        this.tauMP = tauMP;
    }

    /**
     * returns thresholdMP
     * @return
     */
    public float getThresholdMP() {
        return thresholdMP;
    }

    /**
     * sets thresholdMP
     * @param thresholdMP
     */
    public void setThresholdMP(float thresholdMP) {
        this.thresholdMP = thresholdMP;
    }

    /**
     * returns the refractory period
     * @return
     */
    public float getRefractoryPeriod() {
        return RefractoryPeriod;
    }

    /**
     * sets the refractory period
     * @param RefractoryPeriod
     */
    public void setRefractoryPeriod(float RefractoryPeriod) {
        this.RefractoryPeriod = RefractoryPeriod;
    }

    /**
     * returns lastAboveThresholdTimestamp
     * @return
     */
    public int getLastAboveThresholdTimestamp() {
        return lastAboveThresholdTimestamp;
    }

    /**
     * sets lastAboveThresholdTimestamp
     * @param lastAboveThresholdTimestamp
     */
    public void setLastAboveThresholdTimestamp(int lastAboveThresholdTimestamp) {
        this.lastAboveThresholdTimestamp = lastAboveThresholdTimestamp;
    }
    
    /**
     * returns enableAdaptation
     * @return
     */
    public boolean isEnableAdaptation() {
        return enableAdaptation;
    }

    /**
     * sets enableAdaptation
     * @param enableAdaptation
     */
    public void setEnableAdaptation(boolean enableAdaptation) {
        this.enableAdaptation = enableAdaptation;
    }

    /**
     * returns adaptationType
     * @return
     */
    public ADAPTATION_TYPE getAdaptationType() {
        return adaptationType;
    }

    /**
     * sets adaptationType
     *
     * @param adaptationType
     */
    public void setAdaptationType(ADAPTATION_TYPE adaptationType) {
        this.adaptationType = adaptationType;
    }


    /**
     * returns adaptationParamDelta
     * @return
     */
    public float getAdaptationParamDelta() {
        return adaptationParamDelta;
    }

    /**
     * sets adaptationParamDelta
     *
     * @param adaptationParamDelta
     */
    public void setAdaptationParamDelta(float adaptationParamDelta) {
        this.adaptationParamDelta = adaptationParamDelta;
    }

    /**
     * returns adaptationParamMax
     * @return
     */
    public float getAdaptationParamMax() {
        return adaptationParamMax;
    }

    /**
     * sets adaptationParamMax
     * @param adaptationParamMax
     */
    public void setAdaptationParamMax(float adaptationParamMax) {
        this.adaptationParamMax = adaptationParamMax;
    }

    /**
     * returns adaptationParamTauMs
     *
     * @return
     */
    public float getAdaptationParamTauMs() {
        return adaptationParamTauMs;
    }

    /**
     * sets adaptationParamTauMs
     *
     * @param adaptationParamTauMs
     */
    public void setAdaptationParamTauMs(float adaptationParamTauMs) {
        this.adaptationParamTauMs = adaptationParamTauMs;
    }

    /**
     * returns adaptationParamSlop
     *
     * @return
     */
    public float getAdaptationParamSlop() {
        return adaptationParamSlop;
    }

    /**
     * sets adaptationParamSlop
     *
     * @param adaptationParamSlop
     */
    public void setAdaptationParamSlop(float adaptationParamSlop) {
        this.adaptationParamSlop = adaptationParamSlop;
    }

    /**
     * returns RefractoryPeriodMaxMs
     *
     * @return
     */
    public float getRefractoryPeriodMaxMs() {
        return RefractoryPeriodMaxMs;
    }

    /**
     * sets RefractoryPeriodMaxMs
     * 
     * @param RefractoryPeriodMaxMs
     */
    public void setRefractoryPeriodMaxMs(float RefractoryPeriodMaxMs) {
        this.RefractoryPeriodMaxMs = RefractoryPeriodMaxMs;
    }
} // End of class LIFNeuron

