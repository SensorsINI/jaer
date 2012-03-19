/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.math;

/**
 *
 * @author matthias
 * 
 * This class stores a lookup table with the precomputed values of the
 * distribution function for the standard normal distribution.
 */
public class LookupStandardNormalDistribution {
    
    /**
     * Stores the lookup table of the distribution function of the standard
     * normal distribution.
     */
    private float [][] values = {{0.50000f, 0.50399f, 0.50798f, 0.51197f, 0.51595f, 0.51994f, 0.52392f, 0.52790f, 0.53188f, 0.53586f},
                                 {0.53983f, 0.54380f, 0.54776f, 0.55172f, 0.55567f, 0.55962f, 0.56356f, 0.56749f, 0.57142f, 0.57535f},
                                 {0.57926f, 0.58317f, 0.58706f, 0.59095f, 0.59483f, 0.59871f, 0.60257f, 0.60642f, 0.61026f, 0.61409f},
                                 {0.61791f, 0.62172f, 0.62552f, 0.62930f, 0.63307f, 0.63683f, 0.64058f, 0.64431f, 0.64803f, 0.65173f},
                                 {0.65542f, 0.65910f, 0.66276f, 0.66640f, 0.67003f, 0.67364f, 0.67724f, 0.68082f, 0.68439f, 0.68793f},
                                 {0.69146f, 0.69497f, 0.69847f, 0.70194f, 0.70540f, 0.70884f, 0.71226f, 0.71566f, 0.71904f, 0.72240f},
                                 {0.72575f, 0.72907f, 0.73237f, 0.73565f, 0.73891f, 0.74215f, 0.74537f, 0.74857f, 0.75175f, 0.75490f},
                                 {0.75804f, 0.76115f, 0.76424f, 0.76730f, 0.77035f, 0.77337f, 0.77637f, 0.77935f, 0.78230f, 0.78524f},
                                 {0.78814f, 0.79103f, 0.79389f, 0.79673f, 0.79955f, 0.80234f, 0.80511f, 0.80785f, 0.81057f, 0.81327f},
                                 {0.81594f, 0.81859f, 0.82121f, 0.82381f, 0.82639f, 0.82894f, 0.83147f, 0.83398f, 0.83646f, 0.83891f},
                                 {0.84134f, 0.84375f, 0.84614f, 0.84849f, 0.85083f, 0.85314f, 0.85543f, 0.85769f, 0.85993f, 0.86214f},
                                 {0.86433f, 0.86650f, 0.86864f, 0.87076f, 0.87286f, 0.87493f, 0.87698f, 0.87900f, 0.88100f, 0.88298f},
                                 {0.88493f, 0.88686f, 0.88877f, 0.89065f, 0.89251f, 0.89435f, 0.89617f, 0.89796f, 0.89973f, 0.90147f},
                                 {0.90320f, 0.90490f, 0.90658f, 0.90824f, 0.90988f, 0.91149f, 0.91309f, 0.91466f, 0.91621f, 0.91774f},
                                 {0.91924f, 0.92073f, 0.92220f, 0.92364f, 0.92507f, 0.92647f, 0.92785f, 0.92922f, 0.93056f, 0.93189f},
                                 {0.93319f, 0.93448f, 0.93574f, 0.93699f, 0.93822f, 0.93943f, 0.94062f, 0.94179f, 0.94295f, 0.94408f},
                                 {0.94520f, 0.94630f, 0.94738f, 0.94845f, 0.94950f, 0.95053f, 0.95154f, 0.95254f, 0.95352f, 0.95449f},
                                 {0.95543f, 0.95637f, 0.95728f, 0.95818f, 0.95907f, 0.95994f, 0.96080f, 0.96164f, 0.96246f, 0.96327f},
                                 {0.96407f, 0.96485f, 0.96562f, 0.96638f, 0.96712f, 0.96784f, 0.96856f, 0.96926f, 0.96995f, 0.97062f},
                                 {0.97128f, 0.97193f, 0.97257f, 0.97320f, 0.97381f, 0.97441f, 0.97500f, 0.97558f, 0.97615f, 0.97670f},
                                 {0.97725f, 0.97778f, 0.97831f, 0.97882f, 0.97932f, 0.97982f, 0.98030f, 0.98077f, 0.98124f, 0.98169f},
                                 {0.98214f, 0.98257f, 0.98300f, 0.98341f, 0.98382f, 0.98422f, 0.98461f, 0.98500f, 0.98537f, 0.98574f},
                                 {0.98610f, 0.98645f, 0.98679f, 0.98713f, 0.98745f, 0.98778f, 0.98809f, 0.98840f, 0.98870f, 0.98899f},
                                 {0.98928f, 0.98956f, 0.98983f, 0.99010f, 0.99036f, 0.99061f, 0.99086f, 0.99111f, 0.99134f, 0.99158f},
                                 {0.99180f, 0.99202f, 0.99224f, 0.99245f, 0.99266f, 0.99286f, 0.99305f, 0.99324f, 0.99343f, 0.99361f},
                                 {0.99379f, 0.99396f, 0.99413f, 0.99430f, 0.99446f, 0.99461f, 0.99477f, 0.99492f, 0.99506f, 0.99520f},
                                 {0.99534f, 0.99547f, 0.99560f, 0.99573f, 0.99585f, 0.99598f, 0.99609f, 0.99621f, 0.99632f, 0.99643f},
                                 {0.99653f, 0.99664f, 0.99674f, 0.99683f, 0.99693f, 0.99702f, 0.99711f, 0.99720f, 0.99728f, 0.99736f},
                                 {0.99744f, 0.99752f, 0.99760f, 0.99767f, 0.99774f, 0.99781f, 0.99788f, 0.99795f, 0.99801f, 0.99807f},
                                 {0.99813f, 0.99819f, 0.99825f, 0.99831f, 0.99836f, 0.99841f, 0.99846f, 0.99851f, 0.99856f, 0.99861f},
                                 {0.99865f, 0.99869f, 0.99874f, 0.99878f, 0.99882f, 0.99886f, 0.99889f, 0.99893f, 0.99896f, 0.99900f},
                                 {0.99903f, 0.99906f, 0.99910f, 0.99913f, 0.99916f, 0.99918f, 0.99921f, 0.99924f, 0.99926f, 0.99929f},
                                 {0.99931f, 0.99934f, 0.99936f, 0.99938f, 0.99940f, 0.99942f, 0.99944f, 0.99946f, 0.99948f, 0.99950f},
                                 {0.99952f, 0.99953f, 0.99955f, 0.99957f, 0.99958f, 0.99960f, 0.99961f, 0.99962f, 0.99964f, 0.99965f},
                                 {0.99966f, 0.99968f, 0.99969f, 0.99970f, 0.99971f, 0.99972f, 0.99973f, 0.99974f, 0.99975f, 0.99976f},
                                 {0.99977f, 0.99978f, 0.99978f, 0.99979f, 0.99980f, 0.99981f, 0.99981f, 0.99982f, 0.99983f, 0.99983f},
                                 {0.99984f, 0.99985f, 0.99985f, 0.99986f, 0.99986f, 0.99987f, 0.99987f, 0.99988f, 0.99988f, 0.99989f},
                                 {0.99989f, 0.99990f, 0.99990f, 0.99990f, 0.99991f, 0.99991f, 0.99992f, 0.99992f, 0.99992f, 0.99992f},
                                 {0.99993f, 0.99993f, 0.99993f, 0.99994f, 0.99994f, 0.99994f, 0.99994f, 0.99995f, 0.99995f, 0.99995f},
                                 {0.99995f, 0.99995f, 0.99996f, 0.99996f, 0.99996f, 0.99996f, 0.99996f, 0.99996f, 0.99997f, 0.99997f},
                                 {0.99997f, 0.99997f, 0.99997f, 0.99997f, 0.99997f, 0.99997f, 0.99998f, 0.99998f, 0.99998f, 0.99998f}};
    
    /** Stores the instance of the class. */
    private static LookupStandardNormalDistribution instance = null;
    
    /**
     * Creates a new instance of the class LookupStandardNormalDistribution.
     */
    private LookupStandardNormalDistribution() {
        
    }
    
    /**
     * Gets the value of the distribution function of the normal distribution
     * with mean u and standard deviation o.
     * 
     * @param v The parameter for the distribution function.
     * @param u The mean of the normal distribution.
     * @param o The standard deviation of the normal distribution.
     * 
     * @return The value of the distribution function.
     */
    public float getValue(float v, float u, float o) {
        return this.getValue((v - u) / o);
    }
    
    /**
     * Gets the value of the distribution function of the standard normal
     * distribution.
     * 
     * @param v The parameter for the distribution function.
     * @return The value of the distribution function.
     */
    public float getValue(float v) {
        int index = Math.round(v * 100);
        index = Math.min(index, this.values.length * this.values[0].length - 1);
        
        return this.values[index / 10][index % 10];
    }
    
    /**
     * Gets the current instance of the class.
     * 
     * @return The instance of the class.
     */
    public static LookupStandardNormalDistribution getInstance() {
        if (instance == null) instance = new LookupStandardNormalDistribution();
        
        return instance;
    }
    
    private static void check(float v, float e) {
        if (getInstance().getValue(v) != e) System.out.println("wrong value for '" + v + "': " + getInstance().getValue(v));
    }
    
    public static void main(String [] args) {
        check(0f, 0.5f);
        check(0.04f, 0.51595f);
        check(0.09f, 0.53586f);
        check(0.10f, 0.53983f);
        check(0.11f, 0.54380f);
        check(0.19f, 0.57535f);
        check(1.60f, 0.94520f);
        check(1.73f, 0.95818f);
        check(4.00f, 0.99997f);
        check(4.09f, 0.99998f);
        check(4.10f, 0.99998f);
        check(4.11f, 0.99998f);
        check(5.11f, 0.99998f);
        check(100.11f, 0.99998f);
        
    }
}
