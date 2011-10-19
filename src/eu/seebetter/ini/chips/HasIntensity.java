/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package eu.seebetter.ini.chips;


/**
 * Interface for something that has an "intensity" value.
 * @author tobi
 */
public interface HasIntensity {

    /** Returns normalized "intensity" value, which should be a value from 0 to 1.
     *
     * @return 0-1 value which gets rendered. This specification may change to make intensity have real units.
     */
    public float getIntensity();
    
    public void setIntensity(float f);

}
