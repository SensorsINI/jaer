///*
// * Bins.java
// *
// * Created on 3. Dezember 2007, 12:48
// *
// * To change this template, choose Tools | Template Manager
// * and open the template in the editor.
// */
//
//package ch.unizh.ini.jaer.projects.holger;
//
//
//
//import java.util.logging.Logger;
//
//import java.util.Vector;
//import java.lang.Math.*;
//
///**
// *
// * @author Administrator
// */
//public class Binsbak {
//    private Logger log = Logger.getLogger("JAERAppletViewer");
//
//    static int shiftSize;
//    static int binSize;
//    static public int numberOfPairs;    // sets the number of correlated Spike Pairs used to determine Correlation
//    static public int numOfBins;
//
//    static public Vector usedPairs;
//    static public Vector usedPairsWeights;
//    static public int[] bins; // here is where the corrs are saved
//    static public int[] binsWeighted; // here is where the corrs are saved
//    static public int[] lags; // corresponding lags for bins
//    static public int[] lower; // limits of the bins
//    static public int[] upper;
//
//
//    /** Creates a new instance of Bins */
//    //public Bins(int shiftSize, int binSize, int numberOfPairs) {
//
//      //  genBins(int shiftSize, int binSize, int numberOfPairs);
//
//    //}
//    public Binsbak()
//    {
//        log.info("bins constructor");
//
//    }
//
//    public void genBins(int shiftSize, int binSize, int numberOfPairs){
//        int numOfBins = (2*shiftSize/binSize);
//        bins = new int[numOfBins];
//        binsWeighted = new int[numOfBins];
//        lags = new int[numOfBins];
//        lower = new int[numOfBins];
//        upper = new int[numOfBins];
//        for (int i = 0; i<numOfBins; i++ ){
//            lags[i]=i*binSize-shiftSize;
//
//            //System.out.println(lags[i]);
//
//            lower[i]=lags[i]-binSize/2;
//            upper[i]=lags[i]+binSize/2;
//        }
//        this.binSize=binSize;
//        this.shiftSize=shiftSize;
//        this.numberOfPairs=numberOfPairs;
//        this.numOfBins=numOfBins;
//        this.usedPairs = new Vector(numberOfPairs,10);
//        this.usedPairsWeights = new Vector(numberOfPairs,10);
//        //log.info("bins generated");
//    }
//
//    public void addToBin(int diff, int weight){
//        //System.out.println(diff);
//        int diffIndex=0;            // find in which Bin it belongs
//        for (int i=0;i<lower.length;i++){
//            if (lower[i]<=diff && upper[i]>diff)
//                 diffIndex= i;
//        }
//        bins[diffIndex]=bins[diffIndex]+1;  // add Value to Bins
//        binsWeighted[diffIndex]=binsWeighted[diffIndex]+weight;
//
//        usedPairs.add(new Integer(diffIndex));
//        usedPairsWeights.add(new Integer(weight));
//        int overload=usedPairs.size()-numberOfPairs;
//        if (overload>0){
//            for (int j=0;j<overload;j++){
//                int val = ((Integer)usedPairs.elementAt(1)).intValue();
//                bins[val]=bins[val]-1;    // remove value from Bins
//                binsWeighted[val]=binsWeighted[val]-(Integer)usedPairsWeights.elementAt(1);
//                usedPairs.remove(1);
//                usedPairsWeights.remove(1);
//            }
//        }
//
//    }
//    public void resetBins(){
//
//    }
//    /**public int getBinSize(){
//        return this.binSize;
//    }
//    public int getBinSize(){
//        return this.binSize;
//    }*/
//    public int getITD(){
//        int maximum= bins[0];           // find maximum in Bins
//        int maxInd=shiftSize/binSize;
//        for (int i=0;i<bins.length;i++){
//            if (bins[i]>maximum){
//                maximum=bins[i];
//                maxInd=i;
//            }
//        }
//
//        return lags[maxInd];
//    }
//
//    public int getWeightedITD(){
//        int maximum= binsWeighted[0];           // find maximum in Bins
//        int maxInd=shiftSize/binSize;
//        for (int i=0;i<binsWeighted.length;i++){
//            if (binsWeighted[i]>maximum){
//                maximum=binsWeighted[i];
//                maxInd=i;
//            }
//        }
//
//        return lags[maxInd];
//    }
//
//    public static int getSumOfBins(){
//        int sum=0;
//        for (int j=0;j<bins.length;j++){
//            sum=sum+bins[j];
//        }
//        return sum;
//    }
//
//    public void dispBins(){
////        for (int i=0; i<bins.length; i++){
////            System.out.print(bins[i]+" ");
////        }
////        System.out.print("  ==>  "+getSumOfBins()+"\n");
//
//
//    }
//
//
//}
