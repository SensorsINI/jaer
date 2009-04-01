/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.holger;

import java.lang.Math.*;
import java.util.logging.Logger;

/**
 *
 * @author Holger
 */
public class ITDBins {

    private Logger log = Logger.getLogger("ITDBins");
    private float AveragingDecay;
    private int maxITD;
    private int timestamp;
    private float[] bins;
    private float sum = 0;

    public ITDBins(float AveragingDecay, int maxITD, int numOfBins) {
        this.AveragingDecay = AveragingDecay;
        this.maxITD = maxITD;
        this.timestamp = 0;
        this.bins = new float[numOfBins];
        for (int i = 0; i < numOfBins; i++) {
            bins[i] = 0;
        }
    }

    public void addITD(int ITD, int timestamp) {
        for (int i = 0; i < bins.length; i++) {
            //log.info("bins["+i+"]="+bins[i]+" dt="+(timestamp-this.timestamp)+" -> bins["+i+"]="+(bins[i]-(timestamp-this.timestamp)/AveragingDecay));
            //bins[i] = bins[i]-(timestamp-this.timestamp)/AveragingDecay;
            bins[i] = bins[i]-(timestamp-this.timestamp)/AveragingDecay;
            if(bins[i]<0)
                bins[i]=0;
        }
        int index = ((ITD+maxITD)*bins.length)/(2*maxITD);
        //log.info("index="+index+" -> adding ITD="+ITD+"  maxITD="+maxITD+"  bins.length="+bins.length);
        //check for errors:
        if (index>bins.length-1)
        {
            index=bins.length-1;
            log.warning("index was too high");
        }
        if (index<0)
        {
            index=0;
            log.warning("index was too low");
        }

        bins[index]=bins[index]+1;
        this.timestamp = timestamp;
    }

    public void clear() {
        timestamp = 0;
        for (int i = 0; i < bins.length; i++) {
            bins[i] = 0;
        }
    }

    public int getMeanITD() {
        float sum2 = 0;
        sum = 0;
        //Compute the Center of Mass:
        for (int i = 0; i < bins.length; i++) {
            sum2 = sum2 + bins[i] * i;
            sum = sum + bins[i];
        }
        return (int) ((2 * maxITD * sum2) / (sum * bins.length) - maxITD);
    }

    public int getMedianITD() {
        //Compute Confidence:
        sum = 0;
        for (int i = 0; i < bins.length; i++) {
            sum = sum + bins[i];
        }

        //Check if no data:
        if (sum==0)
            return 0;

        //Compute the Median:
        float lower = bins[0];
        int bin = 1;
        while (lower < sum/2) {
            lower=lower+bins[bin];
            bin++;
        }

        //Check special (very unlikely) case:
//        if (lower==sum/2) {
//            //find middle between bins:
//            int i = bin;
//            while (bins[i] != 0) {
//                i++;
//            }
//            fltBin = (i+bin-1)/2f; //makes it more accurate
//        }
//        else {
//            fltBin = bin - (lower - sum/2)/bins[bin-1]; //makes it more accurate
//        }
        
        // This is not used, because above method is faster:
//        int lowerBin = 0;
//        int upperBin = bins.length-1;
//        float difference = 0;
//        while (lowerBin < upperBin) {
//            difference = bins[upperBin]-bins[lowerBin];
//            if ()...
//        }

        //log.info("fltBin="+fltBin+" bin="+bin+" lower="+lower+" sum="+sum+" bins[bin-1]="+bins[bin-1]);
        return (int) ((2 * maxITD * (bin - (lower - sum/2)/bins[bin-1])) / (bins.length) - maxITD);
    }

    public float getITDConfidence() {
        return sum;
    }

    public float getBin(int index) {
        return bins[index];
    }

    public int getNumOfBins()
    {
        return bins.length;
    }
}
