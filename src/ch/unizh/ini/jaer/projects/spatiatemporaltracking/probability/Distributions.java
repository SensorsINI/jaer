/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.probability;

/**
 *
 * @author matthias
 * 
 * Provies a set of distribution.
 */
public class Distributions {
    
    
    
    /**
     * This class provides helper methods to compute the probabilities based
     * on an uniform distribution.
     */
    public static class UniformDistribution {
        
        /**
         * Computes the value of the probability density function using an 
         * uniform distribution on the interval [a, b].
         * 
         * @param a The start of the interval [a, b].
         * @param b The end of the interval [a, b].
         * @param x The value used as parameter for the probability density
         * function.
         * 
         * @return The value of the probability density function.
         */
        public static float getProbabilityDensityFunction(float a, float b, float x) {
            if (x < a) return 0;
            if (x > b) return 0;
            return 1 / (b - a);
        }

        /**
         * Computes the value of the cumulative distribution function using an 
         * uniform distribution on the interval [a, b].
         * 
         * @param a The start of the interval [a, b].
         * @param b The end of the interval [a, b].
         * @param x The value used as parameter for the cumulative distribution 
         * function.
         * 
         * @return The value of the cumulative distribution function.
         */
        public static float getCumulativeDistributionFunction(float a, float b, float x) {
            if (x < a) return 0;
            if (x > b) return 1;
            return (x - a) / (b - a);
        }
    }
    /**
     * This class provides helper methods to compute the probabilities based
     * on a normal distribution.
     */
    public static class NormalDistribution {
        
        /**
         * Computes the value of the probability density function using an 
         * normal distribution with mean u and standard deviation o.
         * 
         * @param u The mean of the normal distribution.
         * @param o The standard deviation of the normal distribution.
         * @param x The value used as parameter for the probability density
         * function.
         * 
         * @return The value of the probability density function.
         */
        public static float getProbabilityDensityFunction(float u, float o, float x) {
            return (float)(1 / Math.sqrt(2*Math.PI) * Math.exp(-Math.pow((x-u) / o, 2.0)));
        }

        /**
         * Computes the value of the probability density function using an 
         * normal distribution with mean u and standard deviation o.
         * 
         * @param u The mean of the normal distribution.
         * @param o The standard deviation of the normal distribution.
         * @param x The value used as parameter for the cumulative distribution 
         * function.
         * 
         * @return The value of the cumulative distribution function.
         */
        public static float getCumulativeDistributionFunction(float u, float o, float x) {
            return LookupStandardNormalDistribution.getInstance().getValue(x, u, o);
        }
    }
    
    
    /**
     * This class provides helper methods to compute the probabilities based
     * on a exponential distribution.
     */
    public static class ExponentialDistribution {
        
        /**
         * Computes the value of the probability density function using an 
         * exponential distribution with parameter l.
         * 
         * @param l The parameter of the exponential distribution.
         * @param x The value used as parameter for the probability density
         * function.
         * 
         * @return The value of the probability density function.
         */
        public static float getProbabilityDensityFunction(float l, float x) {
            if (x < 0) return 0;
            return (float)(l * Math.exp(-l*x));
        }

        /**
         * Computes the value of the cumulative distribution function using an 
         * exponential distribution with parameter l.
         * 
         * @param l The parameter of the exponential distribution.
         * @param x The value used as parameter for the cumulative distribution 
         * function.
         * 
         * @return The value of the cumulative distribution function.
         */
        public static float getCumulativeDistributionFunction(float l, float x) {
            if (x < 0) return 0;
            float r = (float)(1 - Math.exp(-x * l));
            return r;
        }
    }
}
