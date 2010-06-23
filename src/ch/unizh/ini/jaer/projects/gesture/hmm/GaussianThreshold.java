/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Inspired from Gaussian Mixture model.
 * But, instead of mixture-density, only one gaussian pdf is used to allow online learning of guestures.
 * @author Jun Haeng Lee
 */
public class GaussianThreshold implements Serializable {
    /**
     * type of feature values
     */
    public enum Type {

        /**
         * the range of feature values is open. (eg> x is from 0 to 10)
         */
        NONCIRCULATING,
        /**
         * CIRCULATING_X : the range of feature values is circulating. (eg> angle, 0~2pi)
         */
        CIRCULATING_ANGLE,
        /**
         * CIRCULATING_X : the range of feature values is circulating. (eg> angle, 0~2pi)
         */
        CIRCULATING_USER
    };
    Type type;

    /**
     * min and max values of the range of feature values when it's CIRCULATING_USER.
     */
    private double minValue, maxValue, range;

    /**
     * number of elements
     */
    int numElements;

    /**
     * Default value of GaussianParameter.sigma
     * This is used as an initial value and will be replaced with the real value if the real one is lager than this.
     */
    double defaultSigma;

    /** 
     * Number of samples trained ever.
     */
    int numSamples;

    /**
     * Parameters for Gaussian PDFs.
     */
    private ArrayList<GaussianParameter> gParam;

    /**
     * threshold value
     */
    private double thresholdProb;

    /**
     * true if update is done with the best matching delay
     * used for HiddenMarkovModel.ModelType.LRC_RANDOM or HiddenMarkovModel.ModelType.LRBC_RANDOM
     */
     private boolean doBestMatching;


    /**
     * constructor with the number of elements
     * @param numElements
     * @param defaultSigma
     * @param type
     * @param doBestMatching
     */
    public GaussianThreshold(int numElements, double defaultSigma, Type type, boolean doBestMatching) {
        this.numElements = numElements;
        this.defaultSigma = defaultSigma;
        this.type = type;
        this.doBestMatching = doBestMatching;

        gParam = new ArrayList<GaussianParameter>(numElements);

        for(int i=0; i<numElements; i++)
            gParam.add(new GaussianParameter(0, defaultSigma));

        numSamples = 0;
        thresholdProb = 0;

        if(type == Type.CIRCULATING_ANGLE){
            minValue = 0;
            maxValue = 2*Math.PI;
            range = 2*Math.PI;
        }
    }

    /**
     * resets a model
     */
    public void reset(){
        gParam.clear();

        for(int i=0; i<numElements; i++)
            gParam.add(new GaussianParameter(0, defaultSigma));

        numSamples = 0;
        thresholdProb = 0;
    }

    /**
     * sets the min and max values for the type of CIRCULATING_USER
     * @param min
     * @param max
     */
    public void setCirculatingTypeMinMax(double min, double max){
        minValue = min;
        maxValue = max;
        range = max - min;
    }

    /**
     * Adds a sample and updates.
     * On-line update is available.
     * @param sample
     */
    public void addSample(double[] sample){
        if(sample.length != numElements){
            System.err.println("Sample length is not identical to the number of elements of Gaussian threshold.");
            return;
        }

        int delay = 0;
        if(doBestMatching)
            delay = calBestMatchingDelay(sample);

        // update mu's and sigma's
        for(int i=delay; i<numElements; i++){
            updateGP(gParam.get(i - delay), sample[i]);
        }
        if(delay > 0){
            for(int i=0; i<delay; i++){
                updateGP(gParam.get(i + numElements - delay), sample[i]);
            }
        }
        System.out.println();

        numSamples++;

        thresholdProb = 1.0;
        for(int i=0; i<numElements; i++){
            thresholdProb *= getGaussianPDF(gParam.get(i).mu, gParam.get(i).sigma, gParam.get(i).mu+3.0*gParam.get(i).sigma);
        }

        System.out.println("Gth threshold = " + Math.log10(thresholdProb));
    }

    /**
     * updates Gaussian parameters
     * @param gp
     * @param newSample
     */
    private void updateGP(GaussianParameter gp, double newSample){
        if(newSample > gp.mu + Math.PI)
            newSample -= 2*Math.PI;
        else if(newSample < gp.mu - Math.PI)
            newSample += 2*Math.PI;

        double sum = gp.mu*numSamples + newSample;
        double sqSum;
        if(numSamples == 0)
            sqSum = (Math.pow(gp.sigma, 2.0) + Math.pow(gp.mu, 2.0)) + Math.pow(newSample, 2.0);
        else
            sqSum = (Math.pow(gp.sigma, 2.0) + Math.pow(gp.mu, 2.0))*numSamples + Math.pow(newSample, 2.0);

        gp.mu = sum/(numSamples+1.0);
        gp.sigma = Math.sqrt(sqSum/(numSamples+1.0) - Math.pow(gp.mu, 2.0));
        if(gp.sigma < defaultSigma)
            gp.sigma = defaultSigma;
    }

    /**
     * returns true of the sample sequence satisfies the threshold requirement.
     * @param sample
     * @return
     */
    public boolean isAboveThreshold(double[] sample){
        boolean ret = false;

        if(sample.length != numElements){
            System.err.println("Sample length is not identical to the number of elements of Gaussian threshold.");
            return ret;
        }

        double prob = 1.0;

        if(!doBestMatching){
            for(int i=0; i<numElements; i++){
                prob *= getGaussianPDF(gParam.get(i).mu, gParam.get(i).sigma, sample[i]);
            }
        } else {
            prob = calBestMatchingProb(sample);
        }

        if(prob >= thresholdProb)
            ret = true;

        return ret;
    }

    /**
     * calculates the best matching probability
     * @param sample
     * @return
     */
    private double calBestMatchingProb(double[] sample){
        double bestProb = 0;
        
        for(int delay = 0; delay<numElements; delay++){
            double prob = calProbWithDelay(sample, delay, false);
            
            if(prob > bestProb)
                bestProb = prob;
        }
        
        return bestProb;
    }

    /**
     * calculates the best matching delay
     * @param sample
     * @return
     */
    private int calBestMatchingDelay(double[] sample){
        double bestProb = 0;
        int bestDelay = 0;

        for(int delay = 0; delay<numElements; delay++){
            double prob = calProbWithDelay(sample, delay, true);

            if(prob > bestProb){
                bestProb = prob;
                bestDelay = delay;
            }
        }

        return bestDelay;
    }

    /**
     * returns the probability of the delayed sample sequence
     * @param sample
     * @return
     */
    private double calProbWithDelay(double[] sample, int delay, boolean forDelaySearch){
        double prob = 1.0;

        if(sample.length != numElements){
            System.err.println("Sample length is not identical to the number of elements of Gaussian threshold.");
            return prob;
        }

        if(delay > numElements || delay < 0){
            if(delay > 0){
                while(delay > numElements)
                    delay -= numElements;
            } else {
                while(delay < 0)
                    delay += numElements;
            }
        }

        for(int i=delay; i<numElements; i++){
            if(forDelaySearch)
                prob *= getGaussianPDF(gParam.get(i-delay).mu, range/10, sample[i]);
            else
                prob *= getGaussianPDF(gParam.get(i-delay).mu, gParam.get(i-delay).sigma, sample[i]);
        }
        for(int i=0; i<delay; i++){
            if(forDelaySearch)
                prob *= getGaussianPDF(gParam.get(i+numElements-delay).mu, range/10, sample[i]);
            else
                prob *= getGaussianPDF(gParam.get(i+numElements-delay).mu, gParam.get(i+numElements-delay).sigma, sample[i]);
        }

        return prob;
    }
    

    /**
     * returns pdf of Gaussian PDF
     * @param mu
     * @param sigma
     * @param pos
     * @return
     */
    private double getGaussianPDF(double mu, double sigma, double pos){
        double out = 1.0;

        if(sigma != 0){
            if(type == Type.NONCIRCULATING)
                out = Math.exp(-Math.pow((pos-mu)/sigma, 2.0)/2)/Math.sqrt(2*Math.PI)/sigma;
            else{
                refactorValue(pos);

                // in the case of circulating types, the PDF must be folded at both boundary
                out = Math.exp(-Math.pow((pos-mu)/sigma, 2.0)/2)/Math.sqrt(2*Math.PI)/sigma;
                mu += range;
                out += Math.exp(-Math.pow((pos-mu)/sigma, 2.0)/2)/Math.sqrt(2*Math.PI)/sigma;
                mu -= 2*range;
                out += Math.exp(-Math.pow((pos-mu)/sigma, 2.0)/2)/Math.sqrt(2*Math.PI)/sigma;
                
                return out;
            }
        }

        return out;
    }

    /**
     * makes value be between minValue and maxValue
     * @param val
     * @return
     */
    private double refactorValue(double val){
        while(val < minValue)
            val+=range;

        while(val >= maxValue)
            val-=range;

        return val;
    }

    /**
     * returns mu's in a list
     * @return
     */
    public double[] getMuToArray(){
        double[] mu = new double[numElements];

        for(int i=0; i<numElements; i++)
            mu[i] = gParam.get(i).mu;

        return mu;
    }

    /**
     * returns sigma's in a list
     * @return
     */
    public double[] getSigmaToArray(){
        double[] sigma = new double[numElements];

        for(int i=0; i<numElements; i++)
            sigma[i] = gParam.get(i).sigma;

        return sigma;
    }

    /**
     * returns mu of the specfied element
     * @param pos
     * @return
     */
    public double getMu(int pos){
        return gParam.get(pos).mu;
    }

    /**
     * returns sigma of the specfied element
     * @param pos
     * @return
     */
    public double getSigma(int pos){
        return gParam.get(pos).sigma;
    }

    /**
     * returns the threshold probability
     * @return
     */
    public double getThresholdProb(){
        return thresholdProb;
    }

    /**
     * returns the number of samples trained ever
     * @return
     */
    public int getNumSamples(){
        return numSamples;
    }


    /**
     * Inner class defining the parameters of Gaussian PDF
     */
    class GaussianParameter implements Serializable{
        /**
         * mean
         */
        protected double mu;

        /**
         * covariance
         */
        protected double sigma;

        /**
         * constructor
         * @param mu
         * @param sigma
         */
        public GaussianParameter(double mu, double sigma) {
            this.mu = mu;
            this.sigma = sigma;
        }
    }
}
