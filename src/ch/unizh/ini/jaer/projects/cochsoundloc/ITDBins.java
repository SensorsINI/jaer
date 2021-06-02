/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.util.logging.Logger;

/**
 * Saves the histogram of the interaural time differences and has functions for decay of old ITDs.
 *
 * @author Holger
 */
public class ITDBins {

    private ITDCalibrationGaussians calibration = null;
    private Logger log = Logger.getLogger("ITDBins");
    private float AveragingDecay;
    private int maxITD;
    private int timestamp = 0;
    private int NumLoopMean;
    private float[] bins;
    private float ITDConfidence = 0;
    private boolean useCalibration;

    public ITDBins(float AveragingDecay, int NumLoopMean, int maxITD, int numOfBins) {
        useCalibration = false;
        this.AveragingDecay = AveragingDecay; //in us
        this.NumLoopMean = NumLoopMean;
        this.maxITD = maxITD;
        this.timestamp = 0;
        this.bins = new float[numOfBins];
        for (int i = 0; i < numOfBins; i++) {
            bins[i] = 0;
        }
    }

    public ITDBins(float AveragingDecay, int NumLoopMean, ITDCalibrationGaussians calibration) {
        useCalibration = true;
        this.calibration = calibration;
        this.AveragingDecay = AveragingDecay;
        this.NumLoopMean = NumLoopMean;
        this.maxITD = calibration.getMaxITD();
        this.timestamp = 0;
        int numOfBins = calibration.getNumOfBins();
        this.bins = new float[numOfBins];
        for (int i = 0; i < numOfBins; i++) {
            bins[i] = 0;
        }
    }

    // if normValue == 0 then use averagingDecay;
    public void addITD(int ITD, int timestamp, int channel, float weight, int normValue) {
        updateTime(normValue, timestamp);
        if (useCalibration == false) {
            int index = ((ITD + this.maxITD) * bins.length) / (2 * this.maxITD);
            //log.info("index="+index+" -> adding ITD="+ITD+"  maxITD="+maxITD+"  bins.length="+bins.length);
            //check for errors:
//            if (index > bins.length - 1) {
//                index = bins.length - 1;
//                log.warning("index was too high");
//            }
//            if (index < 0) {
//                index = 0;
//                log.warning("index was too low");
//            }

            bins[index] = bins[index] + weight;
        } else {
            double[] addThis = new double[getNumOfBins()];
            addThis = getCalibration().convertITD(channel, ITD);
            double sum = 0;
            for (int k = 0; k < getNumOfBins(); k++) {
                bins[k] += (float) addThis[k] * weight;
                sum += addThis[k];
                if (!(addThis[k] >= 0 && addThis[k] < 1.1)) {
                    log.info("addToBins[k] is out of good range!! addToBins[k]=" + addThis[k]);
                }
            }
            if (sum != 0 && !(sum > 0.9 && sum < 1.1)) {
                log.info("sum of addToBins=" + sum);
            }
        }
        //this.timestamp = timestamp;
    }

    public float convertITD2BIN(int ITD) {
        float binIndex = ((ITD + this.maxITD) * bins.length) / (2 * this.maxITD);
        return binIndex;
    }

    public void clear() {
        timestamp = 0;
        ITDConfidence = 0;
        for (int i = 0; i < bins.length; i++) {
            bins[i] = 0;
        }
    }

    public void loadCalibrationFile(String calibrationFilePath) {
        if (getCalibration() == null) {
            calibration = new ITDCalibrationGaussians();
        }
        getCalibration().loadCalibrationFile(calibrationFilePath);
    }

    public int getITDMean() {
        float sum2 = 0;
        ITDConfidence = 0;
        //Compute the Center of Mass:
        for (int i = 0; i < bins.length; i++) {
            sum2 = sum2 + bins[i] * i;
            ITDConfidence = ITDConfidence + bins[i];
        }

        //Check if no data:
        if (ITDConfidence == 0) {
            return 0;
        }

        float ITDIndex = sum2 / ITDConfidence + 0.5f; //is between 0.5 and 15.5 (if default)
        int ITD = (int) ((2 * this.maxITD * ITDIndex) / bins.length - this.maxITD);

        //Redo with new boundarys to avoid biasing:
        for (int loop = 1; loop < NumLoopMean; loop++) {
            int firstIndex;
            int lastIndex;
            if (2 * ITDIndex > bins.length) {
                firstIndex = java.lang.Math.round(2 * ITDIndex) - bins.length; // between 1 and 15
                lastIndex = bins.length - 1; //15
            } else {
                firstIndex = 0;
                lastIndex = java.lang.Math.round(2 * ITDIndex) - 1; // between 0 and 14
            }

            sum2 = 0;
            float sum3 = 0;
            //Compute the Center of Mass:
            for (int i = firstIndex; i <= lastIndex; i++) {
                sum2 = sum2 + bins[i] * i;
                sum3 = sum3 + bins[i];
            }
            ITDIndex = sum2 / sum3 + 0.5f; //is between 0.5 and 15.5 (if default)
            ITD = (int) ((2 * this.maxITD * ITDIndex) / bins.length - this.maxITD);
        }

        return ITD;
    }

    public int getITDMedian() {
        //Compute Confidence:
        ITDConfidence = 0;
        for (int i = 0; i < bins.length; i++) {
            ITDConfidence += bins[i];
        }

        //Check if no data:
        if (ITDConfidence == 0) {
            return 0;
        }

        //Compute the Median:
        float lower = bins[0];
        int bin = 1;
        while (lower < ITDConfidence / 2) {
            lower += bins[bin];
            bin++;
        }
        float ITDIndex = bin - (lower - ITDConfidence / 2) / bins[bin - 1]; //is between 0.5 and 15.5 (if default)
        int ITD = (int) ((2 * this.maxITD * ITDIndex) / bins.length - this.maxITD);

        //Redo with new boundarys to avoid biasing:
        for (int loop = 1; loop < NumLoopMean; loop++) {
            int firstIndex;
            int lastIndex;
            if (2 * ITDIndex > bins.length) {
                firstIndex = java.lang.Math.round(2 * ITDIndex) - bins.length; // between 1 and 15
                lastIndex = bins.length - 1; //15
            } else {
                firstIndex = 0;
                lastIndex = java.lang.Math.round(2 * ITDIndex) - 1; // between 0 and 14
            }
            float sum2 = 0;
            for (int i = firstIndex; i <= lastIndex; i++) {
                sum2 += bins[i];
            }
            //Check if no data:
            if (ITDConfidence == 0) {
                break;
            }
            lower = bins[firstIndex];
            bin = firstIndex + 1;
            while (lower < sum2 / 2) {
                lower += bins[bin];
                bin++;
            }
            ITDIndex = (bin - (lower - sum2 / 2) / bins[bin - 1]);
            ITD = (int) ((2 * this.maxITD * ITDIndex) / (bins.length) - this.maxITD);
        }

        return ITD;
    }

    /** Returns the ITD in us of peak of histogram
     * 
     * @return the ITD in us; can be positive or negative up to masITD.
     */
    public int getITDMax() {
        ITDConfidence = 0;
        int max = 0;
        //Compute the Max:
        for (int i = 0; i < bins.length; i++) {
            if (bins[i] > bins[max]) {
                max = i;
            }
            ITDConfidence = ITDConfidence + bins[i];
        }
        if (bins[max] == 0) {
            return 0;
        } else {
            return (int) ((2 * this.maxITD * (max + 0.5)) / bins.length - this.maxITD);
        }
    }

    public int getITDMaxIndex() {
        ITDConfidence = 0;
        int max = 0;
        //Compute the Max:
        for (int i = 0; i < bins.length; i++) {
            if (bins[i] > bins[max]) {
                max = i;
            }
            ITDConfidence = ITDConfidence + bins[i];
        }
        return max;
    }

    public float getITDConfidence() {
        return ITDConfidence;
    }

    public float getBin(int index) {
        return bins[index];
    }

    public int getNumOfBins() {
        return bins.length;
    }

    /**
     * @return the calibration
     */
    public ITDCalibrationGaussians getCalibration() {
        return calibration;
    }

    /**
     * @return the AveragingDecay
     */
    public float getAveragingDecay() {
        return AveragingDecay;
    }

    /**
     * @param AveragingDecay the AveragingDecay to set
     */
    public void setAveragingDecay(float AveragingDecay) {
        this.AveragingDecay = AveragingDecay;
    }

    /**
     * @return the maxITD
     */
    public int getMaxITD() {
        return maxITD;
    }

    /**
     * @return the bins
     */
    public float[] getBins() {
        return bins;
    }

    @Override
    public String toString() {
        String strBins = "";
        for (int i = 0; i < bins.length; i++) {
            strBins = strBins + Float.toString(bins[i]) + "\t";
        }
        return strBins;
    }

    /**
     * @return the NumLoopMean
     */
    public int getNumLoopMean() {
        return NumLoopMean;
    }

    /**
     * @param NumLoopMean the NumLoopMean to set
     */
    public void setNumLoopMean(int NumLoopMean) {
        this.NumLoopMean = NumLoopMean;
    }

    public void normToValue(int confidenceThreshold) {
        ITDConfidence = 0;
        for (int i = 0; i < bins.length; i++) {
            ITDConfidence = ITDConfidence + bins[i];
        }
        //if (ITDConfidence != 0) {
        if (ITDConfidence > confidenceThreshold) {
            float normConst = confidenceThreshold / ITDConfidence;
            for (int i = 0; i < bins.length; i++) {
                bins[i] = bins[i] * normConst;
            }
        }

    }

    public void updateTime(int normValue, int timestamp) {
        if (normValue == 0) {
            if (AveragingDecay != 0 && timestamp>this.getTimestamp()) {
                float decayconstant = (float) java.lang.Math.exp(-(timestamp - this.timestamp) / AveragingDecay);
                //log.info("exp=" + decayconstant + " thistime=" + timestamp + " lasttime="+ this.timestamp);
                for (int i = 0; i < bins.length; i++) {
                    bins[i] = (float) (bins[i] * decayconstant);
                }
            }
        } else {
            normToValue(normValue);
        }
        this.timestamp = timestamp;
    }

    /**
     * @return the timestamp
     */
    public int getTimestamp() {
        return timestamp;
    }
}
