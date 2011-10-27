/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import ch.unizh.ini.jaer.projects.gesture.blurringFilter.BlurringFilter2D;
import ch.unizh.ini.jaer.projects.gesture.blurringFilter.ROI;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventprocessing.FilterChain;

/** does blurringFiltering after the vegence of stereo images
 *
 * @author Jun Haeng Lee
 */
public class BlurringFilterStereo extends BlurringFilter2D{

    /**
     * enables left-right association
     */
    private boolean enableBinocluarAssociation = getPrefs().getBoolean("BlurringFilterStereo.enableBinocluarAssociation", true);

    /**
     * activate clustering and tracking in the non-overlaping side regions
     */
    private boolean activateNonoverlapingArea = getPrefs().getBoolean("BlurringFilterStereo.activateNonoverlapingArea", true);


    /**
     * mass threshold for left-right association in percents of MPThreshold
     */
    private float binocluarAssMassThresholdPercentTh = getPrefs().getFloat("BlurringFilterStereo.binocluarAssMassThresholdPercentTh", 10.0f);

    /**
     * RC time constant of the mass of binocular cells
     */
    private int cellMassTimeConstatUs = getPrefs().getInt("BlurringFilterStereo.cellMassTimeConstatUs", 3000);

    /**
     * Stereo vergence filter
     */
    public DisparityUpdater disparityUpdater;

    /**
     * mass of left cells for ON and OFF events
     */
    private ArrayList<Float> leftCellMassOnEvent, leftCellMassOffEvent;

    /**
     * mass of right cells for ON and OFF events
     */
    private ArrayList<Float> rightCellMassOnEvent, rightCellMassOffEvent;


    /**
     * defines the event occurance ration
     */
    public class EventRatio{
        /**
         * total number of events
         */
        public int totalNumOfEvents;
        /**
         * number of events from left eye
         * x: # of On events
         * y: # of Off events
         */
        public Point leftNumOfEvents;
        /**
         * number of events from right eye
         * x: # of On events
         * y: # of Off events
         */
        public Point rightNumOfEvents;
        /**
         * event ratio
         */
        public Point2D.Float leftEventRaio;
        /**
         * event ratio
         */
        public Point2D.Float rightEventRaio;

        EventRatio(){
            totalNumOfEvents = 0;
            leftNumOfEvents = new Point(0, 0);
            rightNumOfEvents = new Point(0, 0);
            leftEventRaio = new Point2D.Float(1, 1);
            rightEventRaio = new Point2D.Float(1, 1);
        }
    }

    /**
     * Constructor. A StereoVergenceFilter is inside a FilterChain that is enclosed in this.
     * @param chip
     */
    public BlurringFilterStereo(AEChip chip) {
        super(chip);
        disparityUpdater = new DisparityUpdater(chip);
        FilterChain fc=new FilterChain(chip);
        fc.add(disparityUpdater);
        setEnclosedFilterChain(fc);

        addObserver(disparityUpdater);

        setPropertyTooltip("Association", "enableBinocluarAssociation", "enables left-right association");
        setPropertyTooltip("Association", "activateNonoverlapingArea", "activate clustering and tracking in the non-overlaping side regions");
        setPropertyTooltip("Association", "binocluarAssMassThresholdPercentTh", "mass threshold for left-right association in percents of MPThreshold");
        setPropertyTooltip("Association", "cellMassTimeConstatUs", "RC time constant of the mass of binocular cells");
    }


    @Override
    protected  EventPacket<?> blurring(EventPacket<?> in) {
        if(in == null)
            return in;

        if (in.isEmpty()) {
            return in;
        }

        checkOutputPacketEventType(in);
        OutputEventIterator oei=out.outputIterator();

        EventRatio er = calculateEventRatio(in);
        for(int i=0; i<in.getSize(); i++){
            BinocularEvent ev = (BinocularEvent)in.getEvent(i);

            // updates lastTime
            lastTime = ev.timestamp;

            // updates event histograms in disparity updater
            disparityUpdater.addEvent(ev);

            // stereo vergence
            int evx = vergence(ev, (int) (disparityUpdater.getVergenceDisparity()/2), oei);
            if(evx == chip.getSizeX()) continue; // vergenced event falls on outside of DVS


            // checks ROI
            if(ROIactivated && !rois.isEmpty()){
                boolean goOn = false;
                for(ROI roi:rois.values()){
                    goOn |= roi.contains(ev.x, ev.y);
                    if(goOn)
                        break;
                }

                if(!goOn){
                    maybeCallUpdateObservers(in, lastTime);
                    continue;
                }
            }

            // add events to the corresponding LIF neurons
            addEvent(ev, evx, er);

            // periodic update
            maybeCallUpdateObservers(in, lastTime);
        }

        return out;
    }

    private EventRatio calculateEventRatio(EventPacket<?> in){
        EventRatio er = new EventRatio();
        
        for(int i=0; i<in.getSize(); i++){
            BinocularEvent ev = (BinocularEvent)in.getEvent(i);

            if(ev.eye == BinocularEvent.Eye.LEFT){
                if(ev.polarity == BinocularEvent.Polarity.On){
                    er.leftNumOfEvents.x++;
                } else {
                    er.leftNumOfEvents.y++;
                }
            } else {
                if(ev.polarity == BinocularEvent.Polarity.On){
                    er.rightNumOfEvents.x++;
                } else {
                    er.rightNumOfEvents.y++;
                }
            }
        }
        er.totalNumOfEvents = er.leftNumOfEvents.x + er.leftNumOfEvents.y + er.rightNumOfEvents.x + er.rightNumOfEvents.y;
        er.leftEventRaio.x = (float) er.leftNumOfEvents.x/er.totalNumOfEvents*4f;
        er.leftEventRaio.y = (float) er.leftNumOfEvents.y/er.totalNumOfEvents*4f;
        er.rightEventRaio.x = (float) er.rightNumOfEvents.x/er.totalNumOfEvents*4f;
        er.rightEventRaio.y = (float) er.rightNumOfEvents.y/er.totalNumOfEvents*4f;

        return er;
    }

    /**
     * stereo vergence
     * 
     * @param ev
     * @param dx
     * @param oei
     * @return
     */
    protected int vergence(BinocularEvent ev, int dx, OutputEventIterator oei){
        int x;

        if (ev.eye==BinocularEvent.Eye.LEFT)
            x = ev.x - dx;
        else
            x = ev.x + dx;

        if (x < 0 || x > chip.getSizeX() - 1)
            return chip.getSizeX();

        BinocularEvent oe=(BinocularEvent)oei.nextOutput();

        // to reduce computational cost... (oe.copyFrom() takes too much cost!)
        oe.address = ev.address;
        oe.eye = ev.eye;
        oe.polarity = ev.polarity;
        oe.timestamp = ev.timestamp;
        oe.type = ev.type;
        oe.x = (short) x;
        oe.y = ev.y;

        return x;
    }


    /**
     * add an event to the corresponding LIF neurons
     * 
     * @param ev
     * @return
     */
    private boolean addEvent(BinocularEvent ev, int evx, EventRatio er){
        int subIndexX = (int) (evx / halfReceptiveFieldSizePixels);
        if (subIndexX == numOfNeuronsX)
            subIndexX--;
        int subIndexY = (int) (ev.getY() / halfReceptiveFieldSizePixels);
        if (subIndexY == numOfNeuronsY)
            subIndexY--;

        if (subIndexX >= numOfNeuronsX && subIndexY >= numOfNeuronsY) {
            initFilter();
            return false;
        }

        // stereo association and blurring
        float weight;
        if (subIndexX != numOfNeuronsX && subIndexY != numOfNeuronsY) {
            weight = increaseBinocularMass(subIndexX + subIndexY * numOfNeuronsX, ev, er);
            lifNeurons.get(subIndexX + subIndexY * numOfNeuronsX).addEvent(ev, weight);
        }
        if (subIndexX != numOfNeuronsX && subIndexY != 0) {
            weight = increaseBinocularMass(subIndexX + (subIndexY - 1) * numOfNeuronsX, ev, er);
            lifNeurons.get(subIndexX + (subIndexY - 1) * numOfNeuronsX).addEvent(ev, weight);
        }
        if (subIndexX != 0 && subIndexY != numOfNeuronsY) {
            weight = increaseBinocularMass(subIndexX - 1 + subIndexY * numOfNeuronsX, ev, er);
            lifNeurons.get(subIndexX - 1 + subIndexY * numOfNeuronsX).addEvent(ev, weight);
        }
        if (subIndexY != 0 && subIndexX != 0) {
            weight = increaseBinocularMass(subIndexX - 1 + (subIndexY - 1) * numOfNeuronsX, ev, er);
            lifNeurons.get(subIndexX - 1 + (subIndexY - 1) * numOfNeuronsX).addEvent(ev, weight);
        }

        return true;
    }

    /**
     * increases mass of left or right eye based on a binocular event and returns synaptic weight for the event based on the cell mass of the other eye.
     * polarity of the event is considered here to suppress out-of-vergence regions
     *
     * @param index
     * @param ev
     * @return massWeight.
     */
    private float increaseBinocularMass(int index, BinocularEvent ev, EventRatio er){
        float retVal = 1f;
        
        ArrayList<Float> mainEye, secondaryEye;

        // checks eye and polarity of the event
        if(ev.eye == BinocularEvent.Eye.LEFT){
            if(ev.polarity == Polarity.On){
                mainEye = leftCellMassOnEvent;
                secondaryEye = rightCellMassOnEvent;
                retVal = (er.leftEventRaio.x + er.rightEventRaio.x)/2;
            } else {
                mainEye = leftCellMassOffEvent;
                secondaryEye = rightCellMassOffEvent;
                retVal = (er.leftEventRaio.y + er.rightEventRaio.y)/2;
            }
        } else {
            if(ev.polarity == Polarity.On){
                mainEye = rightCellMassOnEvent;
                secondaryEye = leftCellMassOnEvent;
                retVal = (er.leftEventRaio.x + er.rightEventRaio.x)/2;
            }else {
                mainEye = rightCellMassOffEvent;
                secondaryEye = leftCellMassOffEvent;
                retVal = (er.leftEventRaio.y + er.rightEventRaio.y)/2;
            }
        }

        if(!enableBinocluarAssociation)
            return retVal;

        // increases mass
        float nextMass = 0.0f;
        int timestampDiff = lifNeurons.get(index).getLastEventTimestamp() - ev.timestamp;
        if(timestampDiff <= 0)
            nextMass = mainEye.get(index)*(float) Math.exp((float)timestampDiff / cellMassTimeConstatUs) + 1.0f;
        mainEye.set(index, nextMass);

        // calcuates weight
        float binocluarAssociationMassThreshold = getMPThreshold() * binocluarAssMassThresholdPercentTh / 100.0f;
        if(!activateNonoverlapingArea)
            retVal *= (1- (float) Math.exp(-secondaryEye.get(index)/binocluarAssociationMassThreshold));
        else{
            int halfDisparity = (int) (disparityUpdater.getVergenceDisparity()/2);
            if(ev.x > halfDisparity && ev.x < chip.getSizeX() - halfDisparity)
                retVal *= (1- (float) Math.exp(-secondaryEye.get(index)/binocluarAssociationMassThreshold));
        }

        return retVal;
    }



    @Override
    public synchronized void initFilter() {
        super.initFilter();
        if(numOfNeuronsX*numOfNeuronsY > 0){
            leftCellMassOnEvent = new ArrayList<Float>(numOfNeuronsX*numOfNeuronsY);
            leftCellMassOffEvent = new ArrayList<Float>(numOfNeuronsX*numOfNeuronsY);
            rightCellMassOnEvent = new ArrayList<Float>(numOfNeuronsX*numOfNeuronsY);
            rightCellMassOffEvent = new ArrayList<Float>(numOfNeuronsX*numOfNeuronsY);
            for(int i=0; i<numOfNeuronsX*numOfNeuronsY; i++){
                leftCellMassOnEvent.add(0.0f);
                leftCellMassOffEvent.add(0.0f);
                rightCellMassOnEvent.add(0.0f);
                rightCellMassOffEvent.add(0.0f);
            }

            if(disparityUpdater != null)
                disparityUpdater.initFilter();
        }
    }


    @Override
    public void resetFilter() {
//        getEnclosedFilterChain().reset();
        super.resetFilter();
        for(int i=0; i<numOfNeuronsX*numOfNeuronsY; i++){
            leftCellMassOnEvent.set(i, 0.0f);
            rightCellMassOnEvent.set(i, 0.0f);
        }
    }


    /**
     * returns enableBinocluarAssociation
     * @return
     */
    public boolean isEnableBinocluarAssociation() {
        return enableBinocluarAssociation;
    }

    /**
     * sets enableBinocluarAssociation
     * @param enableBinocluarAssociation
     */
    public void setEnableBinocluarAssociation(boolean enableBinocluarAssociation) {
        this.enableBinocluarAssociation = enableBinocluarAssociation;
        getPrefs().putBoolean("BlurringFilterStereo.enableBinocluarAssociation", enableBinocluarAssociation);
    }

    /**
     * returns true if the non-overlaping area is activated
     * @return
     */
    public boolean isActivateNonoverlapingArea() {
        return activateNonoverlapingArea;
    }

    /**
     * sets activateNonoverlapingArea
     * 
     * @param activateNonoverlapingArea
     */
    public void setActivateNonoverlapingArea(boolean activateNonoverlapingArea) {
        this.activateNonoverlapingArea = activateNonoverlapingArea;
        getPrefs().putBoolean("BlurringFilterStereo.activateNonoverlapingArea", activateNonoverlapingArea);
    }


    /**
     * returns binocluarAssociationMassThreshold
     * @return
     */
    public float getBinocluarAssMassThresholdPercentTh() {
        return binocluarAssMassThresholdPercentTh;
    }

    /**
     * sets binocluarAssMassThresholdPercentTh
     * 
     * @param binocluarAssMassThresholdPercentTh
     */
    public void setBinocluarAssMassThresholdPercentTh(float binocluarAssMassThresholdPercentTh) {
        this.binocluarAssMassThresholdPercentTh = binocluarAssMassThresholdPercentTh;
        getPrefs().putFloat("BlurringFilterStereo.binocluarAssMassThresholdPercentTh", binocluarAssMassThresholdPercentTh);
    }

    /**
     * returns cellMassTimeConstatUs
     * @return
     */
    public int getCellMassTimeConstatUs() {
        return cellMassTimeConstatUs;
    }

    /**
     * sets cellMassTimeConstatUs
     * @param cellMassTimeConstatUs
     */
    public void setCellMassTimeConstatUs(int cellMassTimeConstatUs) {
        this.cellMassTimeConstatUs = cellMassTimeConstatUs;
        getPrefs().putInt("BlurringFilterStereo.cellMassTimeConstatUs", cellMassTimeConstatUs);
    }

    /**
     * sets disparityLimit
     *
     * @param disparityLimit
     * @param useLowLimit : sets low limit if true. otherwise, sets high limit
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public void setDisparityLimit(int disparityLimit, boolean useLowLimit, int id){
        disparityUpdater.setDisparityLimit(disparityLimit, useLowLimit, id);
    }

    /**
     * sets enableDisparityLimit
     * @param enableDisparityLimit
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public void setDisparityLimitEnabled(boolean enableDisparityLimit, int id){
        disparityUpdater.setDisparityLimitEnabled(enableDisparityLimit, id);
    }

    /**
     * returns true if the disparity limit is enabled
     * @param id : -1 for global disparity, cluster number for cluster disparity
     *
     * @return
     */
    public boolean isDisparityLimitEnabled(int id){
        return disparityUpdater.isDisparityLimitEnabled(id);
    }

    /**
     * sets the maximum change of the disparity change in vergence filter
     * @param maxDisparityChange
     * @param id : -1 for global disparity, cluster number for cluster disparity
     */
    public void setMaxDisparityChangePixels(int maxDisparityChange, int id){
        disparityUpdater.setMaxDisparityChangePixels(maxDisparityChange, id);
    }

}
