/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.biasgen;

/**
 * Static methods for tweaking biases.
 *
 * @author tobi
 */
public class PotTweakerUtilities {
    private PotTweakerUtilities(){}

    /** Convenience method that computes ratio from slider position and tweakability.
     * @param sliderValue -1:1 float value from slider
     * @param maxRatio output ranges from 1/maxRatio to maxRatio
     *
     * @return ratio from 1/maxRatio to maxRatio.
     */
    public static float getRatioTweak(float sliderValue, float maxRatio) {
        float logratio = (float) Math.log(maxRatio);
        float ratio = (float) Math.exp(sliderValue * logratio);
        return ratio;
    }

    /** Convenience method that computes absolute change from slider position and tweakability.
     *
     * @return absolute change from -tweakability to tweakability.
     */
    public static float getAbsoluteTweak(float sliderValue, float maxTweak) {
        float value = maxTweak * sliderValue;
        return value;
    }

}
