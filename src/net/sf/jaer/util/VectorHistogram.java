package net.sf.jaer.util;

import java.awt.geom.Point2D;


/** 
 * For histogramming results of a vector computation like orientation or direction selectivity.
 Computes various useful derivatives.
 */
public class VectorHistogram{
    
    /** @param numTypes the number of histogram bins, i.e., the number of directions */
    public VectorHistogram(int numTypes) {
        this.numTypes=numTypes;
        counts=new int[numTypes];
    }
    
    int[] counts;
    boolean eventAdded = false;
    int numTypes=0;
    private int totalCounts=0;
    
    public int[] getCounts(){ return counts;}
    
    /** resets all counts to zero */
    public void reset(){
        for (int i = 0; i<counts.length; i++){
            counts[i]=0;
        }
        eventAdded=false;
        totalCounts=0;
    }
    
    /** add a new value to histogram
     *@param val the count value, must be in ranage 0 to numTypes-1 
     */
    public void add(int val){
        counts[val]++;
        eventAdded=true;
        totalCounts++;
    }
    
    /** @return histogram values normalized to 1 for max value, or 0 if there are no counts */
    public float[] getNormalized(){
        float[] norm = new float[numTypes];
        if(!eventAdded) return norm;
        int max = 0;
        for (int i = 0; i < numTypes; i++){
            if(counts[i]>max) max=counts[i];
        }
        if (max>0){
            for (int i = 0; i < numTypes; i++){
                norm[i] = (float) counts[i] / max;
            }
        }
        return norm;
    }
    
    /** @return histogram values normalized by multiplying each element by a factor
     * @param factor the multiplier
     */
    public float[] getNormalizedByFactor(float factor){
        float[] norm = new float[numTypes];
            for (int i = 0; i < numTypes; i++){
                norm[i] = counts[i] * factor;
            }
        return norm;
    }
    
    /** @return the average direction vector based on counts.
     theta increases CCW and starts up y axis
     */
    public Point2D.Float getAverageDir(){
        java.awt.geom.Point2D.Float p = new Point2D.Float();
        for (int i = 0; i < numTypes; i++){
            double theta = (2*Math.PI*(double)i/numTypes)-Math.PI/2; // theta starts vertical up, type 0 is for vertical ori
            float wx = -(float)Math.cos(theta);
            float wy = -(float)Math.sin(theta);
            p.x+=counts[i]*wx;
            p.y+=counts[i]*wy;
        }
        return p;
    }
    
    //        float[] getAverageDir(){
    //            float[] d=new float[2]; // dx,dy
    //            for(int i=0;i<numTypes;i++){
    //
    //            }
    //        }
    
    public String toString(){
        StringBuffer s = new StringBuffer();
        for (int i = 0; i < numTypes; i++){
            s.append(counts[i]+" ");
        }
        return s.toString();
    }

    public int getTotalCounts() {
        return totalCounts;
    }
}