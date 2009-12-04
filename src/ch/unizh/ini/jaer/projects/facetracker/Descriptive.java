/* Descriptive.java
 *
 * Created on 14. april 2008, 14:42
 *
 * @author: Alexander Tureczek
 * 
 * This class implements descriptive statistics. takes as input a list and 
 * returns a float number, containing the median of the list.
 */

package ch.unizh.ini.jaer.projects.facetracker;

public class Descriptive {
    
    public Descriptive(){}
    
    //this is used to calculate the median of a list. 
    public float Median(float[] list) 
    {
        float median = list.length;
        short index=0;
        
        
        //if the list is even length
        if (median%2==0)
        {
            median = (float) list[(list.length/2)-1];
            median = (float) list[(list.length/2)]+median;
            median = median/2;
        }
        //if the list is odd length    
        else
        {
            index = (short) ((list.length + 1) / 2 - 1);
            median = (short) list[index];   
        }
    
        return (float) median;
    }  
}
