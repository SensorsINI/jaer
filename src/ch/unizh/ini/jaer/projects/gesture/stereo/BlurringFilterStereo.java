/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2D;
import java.util.ArrayList;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
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
    private boolean enableBinocluarAssociation = getPrefs().getBoolean("BlurringFilterStereo.enableBinocluarAssociation", false);

    /**
     * activate clustering and tracking in the non-overlaping side regions
     */
    private boolean activateNonoverlapingArea = getPrefs().getBoolean("BlurringFilterStereo.activateNonoverlapingArea", false);


    /**
     * mass threshold for left-right association in percents of MPThreshold
     */
    private float binocluarAssMassThresholdPercentTh = getPrefs().getFloat("BlurringFilterStereo.binocluarAssMassThresholdPercentTh", 20.0f);

    /**
     * RC time constant of the mass of binocular cells
     */
    private int cellMassTimeConstatUs = getPrefs().getInt("BlurringFilterStereo.cellMassTimeConstatUs", 5000);

    /**
     * Stereo vergence filter
     */
    protected StereoVergenceFilter svf;

    /**
     * mass of left cells for ON and OFF events
     */
    private ArrayList<Float> leftCellMassOnEvent, leftCellMassOffEvent;

    /**
     * mass of right cells for ON and OFF events
     */
    private ArrayList<Float> rightCellMassOnEvent, rightCellMassOffEvent;

    /**
     * Constructor. A StereoVergenceFilter is inside a FilterChain that is enclosed in this.
     * @param chip
     */
    public BlurringFilterStereo(AEChip chip) {
        super(chip);
        svf = new StereoVergenceFilter(chip);
        FilterChain fc=new FilterChain(chip);
        fc.add(svf);
        setEnclosedFilterChain(fc);

        setPropertyTooltip("Association", "enableBinocluarAssociation", "enables left-right association");
        setPropertyTooltip("Association", "activateNonoverlapingArea", "activate clustering and tracking in the non-overlaping side regions");
        setPropertyTooltip("Association", "binocluarAssMassThresholdPercentTh", "mass threshold for left-right association in percents of MPThreshold");
        setPropertyTooltip("Association", "cellMassTimeConstatUs", "RC time constant of the mass of binocular cells");
    }


    @Override
    protected synchronized EventPacket<?> blurring(EventPacket<?> in) {
//        boolean updatedCells = false;

        if(in == null)
            return in;

        if (in.getSize() == 0) {
            return in;
        }

        try {
            // add events to the corresponding cell
            for(int i=0; i<in.getSize(); i++){
                BinocularEvent ev = (BinocularEvent)in.getEvent(i);

                int subIndexX = (int) (ev.getX() / halfReceptiveFieldSizePixels);
                int subIndexY = (int) (ev.getY() / halfReceptiveFieldSizePixels);

                if (subIndexX >= numOfNeuronsX && subIndexY >= numOfNeuronsY) {
                    initFilter();
                }

                float weight;
                if (subIndexX != numOfNeuronsX && subIndexY != numOfNeuronsY) {
                    weight = increaseBinocularMass(subIndexX + subIndexY * numOfNeuronsX, ev);
                    lifNeurons.get(subIndexX + subIndexY * numOfNeuronsX).addEvent(ev, weight);
                }
                if (subIndexX != numOfNeuronsX && subIndexY != 0) {
                    weight = increaseBinocularMass(subIndexX + (subIndexY - 1) * numOfNeuronsX, ev);
                    lifNeurons.get(subIndexX + (subIndexY - 1) * numOfNeuronsX).addEvent(ev, weight);
                }
                if (subIndexX != 0 && subIndexY != numOfNeuronsY) {
                    weight = increaseBinocularMass(subIndexX - 1 + subIndexY * numOfNeuronsX, ev);
                    lifNeurons.get(subIndexX - 1 + subIndexY * numOfNeuronsX).addEvent(ev, weight);
                }
                if (subIndexY != 0 && subIndexX != 0) {
                    weight = increaseBinocularMass(subIndexX - 1 + (subIndexY - 1) * numOfNeuronsX, ev);
                    lifNeurons.get(subIndexX - 1 + (subIndexY - 1) * numOfNeuronsX).addEvent(ev, weight);
                }


                lastTime = ev.getTimestamp();
                //updatedCells = maybeCallUpdateObservers(in, lastTime);
                maybeCallUpdateObservers(in, lastTime);

            }
        } catch (IndexOutOfBoundsException e) {
            initFilter();
            // this is in case cell list is modified by real time filter during updating cells
            log.warning(e.getMessage());
        }

//        if (!updatedCells) {
//            updateNeurons(lastTime); // at laest once per packet update list
//        }

        return out;
    }

    /**
     * increases mass of left or right eye based on a binocular event and returns synaptic weight for the event based on the cell mass of the other eye.
     * polarity of the event is considered here to suppress out-of-vergence regions
     *
     * @param index
     * @param ev
     * @return massWeight.
     */
    private float increaseBinocularMass(int index, BinocularEvent ev){
        float retVal = 1.0f;

        if(!enableBinocluarAssociation)
            return retVal;
        
        ArrayList<Float> mainEye, secondaryEye;

        // checks eye and polarity of the event
        if(ev.eye == BinocularEvent.Eye.LEFT){
            if(ev.polarity == Polarity.On){
                mainEye = leftCellMassOnEvent;
                secondaryEye = rightCellMassOnEvent;
            } else {
                mainEye = leftCellMassOffEvent;
                secondaryEye = rightCellMassOffEvent;
            }
        } else {
            if(ev.polarity == Polarity.On){
                mainEye = rightCellMassOnEvent;
                secondaryEye = leftCellMassOnEvent;
            }else {
                mainEye = rightCellMassOffEvent;
                secondaryEye = leftCellMassOffEvent;
            }
        }

        // increases mass
        float nextMass = 0.0f;
        int timestampDiff = lifNeurons.get(index).getLastEventTimestamp() - ev.timestamp;
        if(timestampDiff <= 0)
            nextMass = mainEye.get(index)*(float) Math.exp((float)timestampDiff / cellMassTimeConstatUs) + 1.0f;
        mainEye.set(index, nextMass);

        // calcuates weight
        float binocluarAssociationMassThreshold = getMPThreshold() * binocluarAssMassThresholdPercentTh / 100.0f;
        if(!activateNonoverlapingArea)
            retVal -= (float) Math.exp(-secondaryEye.get(index)/binocluarAssociationMassThreshold);
        else{
            int halfDisparity = svf.getGlobalDisparity()/2;
            if(ev.x > halfDisparity && ev.x < chip.getSizeX() - halfDisparity)
                retVal -= (float) Math.exp(-secondaryEye.get(index)/binocluarAssociationMassThreshold);
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
        }
    }


    @Override
    public void resetFilter() {
        getEnclosedFilterChain().reset();
        super.resetFilter();
        for(int i=0; i<numOfNeuronsX*numOfNeuronsY; i++){
            leftCellMassOnEvent.set(i, 0.0f);
            rightCellMassOnEvent.set(i, 0.0f);
        }
    }

    /** returns the global disparity between left and right eyes.
     * it returns prevDisparity rather than currentGlobalDisparity to cover the failure of disparity estimation.
     *
     * @return
     */
    synchronized public int getGlobalDisparity(){
        return svf.getGlobalDisparity();
    }

    /** returns true if the disparity of the specified position is properly updated.
     *
     * @param yPos
     * @return
     */
    public boolean isDisparityValid(int yPos){
        return svf.isDisparityValid(yPos);
    }

    /** returns the disparity at the specified position.
     * Disparity values obtained from mutiples sections are used to getString it.
     *
     * @param yPos
     * @return
     */
    public int getDisparity(int yPos){
        return svf.getDisparity(yPos);
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
     */
    public void setDisparityLimit(int disparityLimit, boolean useLowLimit){
        svf.setDisparityLimit(disparityLimit, useLowLimit);
    }

    /**
     * sets enableDisparityLimit
     * @param enableDisparityLimit
     */
    public void setEnableDisparityLimit(boolean enableDisparityLimit){
        svf.setEnableDisparityLimit(enableDisparityLimit);
    }
}
