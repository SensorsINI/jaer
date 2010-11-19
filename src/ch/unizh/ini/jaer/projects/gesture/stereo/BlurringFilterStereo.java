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
import net.sf.jaer.eventprocessing.FilterChain;

/** does blurringFiltering after the vegence of stereo images
 *
 * @author Jun Haeng Lee
 */
public class BlurringFilterStereo extends BlurringFilter2D{

    private boolean enableBinocluarAssociation = getPrefs().getBoolean("BlurringFilterStereo.enableBinocluarAssociation", false);
    private boolean activateNonoverlapingArea = getPrefs().getBoolean("BlurringFilterStereo.activateNonoverlapingArea", false);
    private float binocluarAssociationMassThreshold = getPrefs().getFloat("BlurringFilterStereo.binocluarAssociationMassThreshold", 20.0f);

    /**
     * Stereo vergence filter
     */
    protected StereoVergenceFilter svf;

    /**
     * mass of left cells
     */
    private ArrayList<Float> leftCellMass;

    /**
     * mass of right cells
     */
    private ArrayList<Float> rightCellMass;

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
        setPropertyTooltip("Association", "activateNonoverlapingArea", "activate non-overlaping area");
        setPropertyTooltip("Association", "binocluarAssociationMassThreshold", "mass threshold for left-right association");
    }


    @Override
    protected synchronized EventPacket<?> blurring(EventPacket<?> in) {
        boolean updatedCells = false;

        if(in == null)
            return in;

        if (in.getSize() == 0) {
            return in;
        }

        try {
            // add events to the corresponding cell
            for(int i=0; i<in.getSize(); i++){
                BinocularEvent ev = (BinocularEvent)in.getEvent(i);

                int subIndexX = (int) (2.0f * ev.getX() / receptiveFieldSizePixels);
                int subIndexY = (int) (2.0f * ev.getY() / receptiveFieldSizePixels);

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
                updatedCells = maybeCallUpdateObservers(in, lastTime);

            }
        } catch (IndexOutOfBoundsException e) {
            initFilter();
            // this is in case cell list is modified by real time filter during updating cells
            log.warning(e.getMessage());
        }

        if (!updatedCells) {
            updateNeurons(lastTime); // at laest once per packet update list
        }

        return out;
    }

    /**
     * increases mass of left or right eye based on a binocular event.
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

        if(ev.eye == BinocularEvent.Eye.LEFT){
            mainEye = leftCellMass;
            secondaryEye = rightCellMass;
        } else {
            mainEye = rightCellMass;
            secondaryEye = leftCellMass;
        }

        // increases mass
        mainEye.set(index, mainEye.get(index)*(float) Math.exp(((float) lifNeurons.get(index).getLastEventTimestamp() - ev.timestamp) / MPTimeConstantUs) + 1.0f);

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
            leftCellMass = new ArrayList<Float>(numOfNeuronsX*numOfNeuronsY);
            rightCellMass = new ArrayList<Float>(numOfNeuronsX*numOfNeuronsY);
            for(int i=0; i<numOfNeuronsX*numOfNeuronsY; i++){
                leftCellMass.add(0.0f);
                rightCellMass.add(0.0f);
            }
        }
    }


    @Override
    public void resetFilter() {
        getEnclosedFilterChain().reset();
        super.resetFilter();
        for(int i=0; i<numOfNeuronsX*numOfNeuronsY; i++){
            leftCellMass.set(i, 0.0f);
            rightCellMass.set(i, 0.0f);
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
    public float getBinocluarAssociationMassThreshold() {
        return binocluarAssociationMassThreshold;
    }

    /**
     * sets binocluarAssociationMassThreshold
     * 
     * @param binocluarAssociationMassThreshold
     */
    public void setBinocluarAssociationMassThreshold(float binocluarAssociationMassThreshold) {
        this.binocluarAssociationMassThreshold = binocluarAssociationMassThreshold;
        getPrefs().putFloat("BlurringFilterStereo.binocluarAssociationMassThreshold", binocluarAssociationMassThreshold);
    }

    public void setDisparityLimit(int disparityLimit, boolean useLowLimit){
        svf.setDisparityLimit(disparityLimit, useLowLimit);
    }

    public void setEnableDisparityLimit(boolean enableDisparityLimit){
        svf.setEnableDisparityLimit(enableDisparityLimit);
    }
}
