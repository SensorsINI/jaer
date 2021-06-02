/*
 * Bins.java
 *
 * Created on 3. Dezember 2007, 12:48
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead;

import java.util.Vector;

/**
 *
 * @author Administrator
 */
public class Bins {

    static int shiftSize;
    static int binSize;
    static int numberOfPairs;    // sets the number of correlated Spike Pairs used to determine Correlation
    static int numOfBins;

    static Vector usedPairs;
    static public int[] bins; // here is where the corrs are saved
    static public int[] lags; // corresponding lags for bins
    static public int[] lower; // limits of the bins
    static public int[] upper;


    /** Creates a new instance of Bins */
    //public Bins(int shiftSize, int binSize, int numberOfPairs) {

      //  genBins(int shiftSize, int binSize, int numberOfPairs);

    //}

    public void genBins(int shiftSize, int binSize, int numberOfPairs){
        int numOfBins = ((2*shiftSize)/binSize);
        bins = new int[numOfBins];
        lags = new int[numOfBins];
        lower = new int[numOfBins];
        upper = new int[numOfBins];
        for (int i = 0; i<numOfBins; i++ ){
            lags[i]=(i*binSize)-shiftSize;

            //System.out.println(lags[i]);

            lower[i]=lags[i]-(binSize/2);
            upper[i]=lags[i]+(binSize/2);
        }

        Bins.binSize=binSize;
        Bins.shiftSize=shiftSize;
        Bins.numberOfPairs=numberOfPairs;
        Bins.numOfBins=numOfBins;
        Bins.usedPairs = new Vector(numberOfPairs,10);
    }

    public void addToBin(int diff){
        //System.out.println(diff);
        int diffIndex=0;            // find in which Bin it belongs
        for (int i=0;i<lower.length;i++){
            if ((lower[i]<=diff) & (upper[i]>diff)) {
				diffIndex= i;
			}
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
    public static int getSumOfBins(){
        int sum=0;
        for (int bin : bins) {
            sum=sum+bin;
        }
        return sum;
    }
    public static void dispBins(){
        for (int bin : bins) {
            System.out.print(bin+" ");
        }
        System.out.print("  ==>  "+getSumOfBins()+"\n");
    }


}
