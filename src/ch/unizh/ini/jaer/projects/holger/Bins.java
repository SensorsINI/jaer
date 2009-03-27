/*
 * Bins.java
 *
 * Created on 3. Dezember 2007, 12:48
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.holger;

import java.util.Vector;
import java.lang.Math.*;
import java.util.logging.Logger;
/**
 *
 * @author jaeckeld/Holger
 */
public class Bins {
    private Logger log = Logger.getLogger("JAERITDViewer");
    public int shiftSize;
    public int binSize;
    public int numberOfPairs;    // sets the number of correlated Spike Pairs used to determine Correlation
    public int numOfBins;
    
    public Vector usedPairs;
    public int[] bins; // here is where the corrs are saved
    public int[] lags; // corresponding lags for bins
    public int[] lower; // limits of the bins
    public int[] upper;
    
    /** Creates a new instance of Bins */
    //public Bins(int shiftSize, int binSize, int numberOfPairs) {
        
    //  genBins(int shiftSize, int binSize, int numberOfPairs);
    //}
    public void genBins(int shiftSize, int binSize, int numberOfPairs)
    {
            numOfBins = (2 * shiftSize / binSize);
            bins = new int[numOfBins];
            lags = new int[numOfBins];
            lower = new int[numOfBins];
            upper = new int[numOfBins];
        for (int i = 0; i < numOfBins; i++) {
            lags[i] = i * binSize - shiftSize;

            lower[i] = lags[i] - binSize / 2;
            upper[i] = lags[i] + binSize / 2;
        }
        try {
            this.binSize = binSize;
            this.shiftSize = shiftSize;
            this.numberOfPairs = numberOfPairs;
            this.usedPairs = new Vector(numberOfPairs, 10);
        } catch (Exception e) {
            log.warning("while creating variables caught exception " + e);
            e.printStackTrace();
        }
    }

    public void addToBin(int diff){
        //System.out.println(diff);
        int diffIndex=0;            // find in which Bin it belongs
        for (int i=0;i<lower.length;i++){
            if (lower[i]<=diff & upper[i]>diff)
                 diffIndex= i;
        }
        bins[diffIndex]=bins[diffIndex]+1;  // add Value to Bins
        
        usedPairs.add(new Integer(diffIndex));
        int overload=usedPairs.size()-numberOfPairs;
        if (overload>0){
            for (int j=0;j<overload;j++){
                int val = ((Integer)usedPairs.elementAt(1)).intValue();
                bins[val]=bins[val]-1;    // remove value from Bins
                usedPairs.remove(1);
            }
        }
       
    }

    public void resetBins(){
        
    }
    /**public int getBinSize(){
        return this.binSize;
    }
    public int getBinSize(){
        return this.binSize;
    }*/
    public int getITD(){
        int maximum= bins[0];           // find maximum in Bins
        int maxInd=shiftSize/binSize;
        for (int i=0;i<bins.length;i++){
            if (bins[i]>maximum){
                maximum=bins[i];
                maxInd=i;
            }
        }
        return lags[maxInd];
    }

    public int getSumOfBins(){
        int sum=0;
        for (int j=0;j<bins.length;j++){
            sum=sum+bins[j];
        }
        return sum;
    }

    public void dispBins(){
        for (int i=0; i<bins.length; i++){
            System.out.print(bins[i]+" ");
        }
        System.out.print("  ==>  "+getSumOfBins()+"\n");
    }
}
