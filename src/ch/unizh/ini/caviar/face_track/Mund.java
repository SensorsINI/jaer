/*  Mund.java
 *
 * Created on 13. juni 2008, 14:15
 *
 * @Author: Alexander Tureczek
 * 
 * Governs the mouth and the deformation of it. It takes as input the data 
 * vector and the model. Though rotation and scaling the model is deformed to 
 * fit the data.
 */

package ch.unizh.ini.caviar.face_track;

import java.lang.Math.*;
import java.util.ArrayList;

public class Mund 
{
    
    //empty constructor
    public Mund(){}
    
    public float[][] rotateMouth(int[] x, int[] y, float[][] hori_meanS, float[][] pre_mouth, float[][] mouth_boundary)
    {
        //identifying the boundary box.
        int count=0;        
        float[][] box = new float[5][2];
        
        for (int index=15; index<hori_meanS.length; index++)
        {
            box[count][0]=hori_meanS[index][0];
            box[count][1]=hori_meanS[index][1];        
            count++;
        }
        //calculating the tilt of the mouth_boundary box.

        float box_slope = (box[2][1]-box[3][1])/(box[2][0]-box[3][0]);
        
        float[] r = new float[5];
        float[] ang = new float[5];
        float[] new_ang = new float[5];
        float[][] new_box = new float[5][2];
        
        for (int index=0; index<box.length; index++)
        {
            //calculating the argument of every point. using normal a^2+b^2 = c^2
            
            r[index] = (float) Math.sqrt(Math.pow(box[index][0],2)+Math.pow(box[index][1],2));
            
            //Calculating the modulo of the boundary.
            
            ang[index] = (float) Math.acos(box[index][0]/r[index]);
            
            //Subracting the difference between the angle and the slope
            
            new_ang[index] = ang[index]-box_slope;
            
            //calculating the final horisontal box. 
            
            new_box[index][0] = (float) (r[index]*Math.cos(new_ang[index]));
            new_box[index][1] = (float) (r[index]*Math.sin(new_ang[index]));
        }
        
//  --------------------------------------------------------------------
//
//              Finding relevant data and doing Rotation.
//
//  --------------------------------------------------------------------
        
        //Finding the relevant data.  -> data within the mouth_boundary
        
        float[] mx = new float[x.length];
        float[] my = new float[x.length];
        count=0;
        
        //finding the smallest and largest x and y, to find a box that surrounds the mouth.
        float minx = box[0][0];
        float miny = box[0][1];
        float maxx = box[0][0];
        float maxy = box[0][1];

        for (int index=0; index < box.length; index++) {
            //finding minimum for the bounadary box around the emouth. 
            if (box[index][0] < minx) 
            {
                minx = box[index][0];
            }
            if (box[index][1] < miny) 
            {
                miny = box[index][1];
            }
            //finding maximum for the bounadary box around the emouth. 
            if (box[index][0] > maxx) 
            {
                maxx = box[index][0];
            }
            if (box[index][1] > maxy) 
            {
                maxy = box[index][1];
            }
        }
        //finding all relevant data, e.g. data inside the boundary box.
        count=0;
        for (int index=0; index<x.length; index++)
        {
            if ((x[index]>=minx) && (x[index]<=maxx) && (y[index]>=miny) && (y[index]<=maxy))
            {
                    mx[count]=x[index];
                    my[count]=y[index];
                    count++;
            }
        }        
        
        //if relevant data is found, we analyse this.
        if (count>0)
        {
            //calculating the angle for all data. Used to rotate
            
            //Modulo for data
            float[] rd = new float[count];
            // Argument for data
            float[] ang_d = new float[count];
            float[][] new_data = new float[count][2]; 
            float[] new_ang_d = new float[count];           
            
            
            for (int index=0; index<count; index++)
            {
                rd[index]=(float)(Math.sqrt(Math.pow(mx[index],2)+Math.pow(my[index],2)));
                
                ang_d[index]=(float)(Math.acos(mx[index]/rd[index]));

                //Here is not included check for solution due to float solution. 
            
                //Subtracting the difference in angles
                new_ang_d[index] = ang_d[index]-box_slope;
            
                //calculating the final horisontal data. 
                    new_data[index][0] = (float)(rd[index]*Math.cos(new_ang_d[index]));
                    new_data[index][1] = (float)(rd[index]*Math.sin(new_ang_d[index]));
            }
            
            
            
                
//--------------------------------------------------------------------------
//
//              Identification of # obs in each cell.
//
//--------------------------------------------------------------------------
        
        //Test using 1/4 equidistant.

        //Width of the box. 
        float width=new_box[1][0]-new_box[0][0];
        float height=new_box[3][1]-new_box[0][1];
        
        int count1L=0;
        int count2L=0;  
        int count3L=0;
        int count4L=0;

        int count1U=0;
        int count2U=0;
        int count3U=0;
        int count4U=0;
        
        //This is initiating each cell in the boundary (c.f report for cell division)
        
        //this for-loop is used to determine the number of observations in each cell. 
        //this is used in the next for-loop to define the vector for each cell.
         for (int index=0; index<new_data.length; index++)
        {
            //Left cell calculation. First finding the x-data and then finding the y-data.
            if (new_data[index][0]>new_box[0][0] && new_data[index][0]<=new_box[0][0]+width/4)
            {
                if (new_data[index][1]>new_box[0][1] && new_data[index][1]<=new_box[0][1]+height/2)
                {
                    count1L++;
                }
                if (new_data[index][1]>new_box[0][1]+height/2 && new_data[index][1]<=new_box[3][1])
                {
                    count1U++;
                }
            }
            //Cell 2 from the left. 
             if (new_data[index][0]>new_box[0][0]+width/4 && new_data[index][0]<=new_box[0][0]+2*width/4)
            {
                if (new_data[index][1]>new_box[0][1] && new_data[index][1]<=new_box[0][1]+height/2)
                {
                    count2L++;
                }
                if (new_data[index][1]>new_box[0][1]+height/2 && new_data[index][1]<=new_box[3][1])
                {
                    count2U++;
                }
            }
            //third cell from the left. 
            if (new_data[index][0]>new_box[0][0]+2*width/4 && new_data[index][0]<=new_box[0][0]+3*width/4)
            {
                if (new_data[index][1]>new_box[0][1] && new_data[index][1]<=new_box[0][1]+height/2)
                {
                    count3L++;
                }
                if (new_data[index][1]>new_box[0][1]+height/2 && new_data[index][1]<=new_box[3][1])
                {
                    count3U++;
                }
            }
            //Right cell 
            if (new_data[index][0]>new_box[0][0]+3*width/4 && new_data[index][0]<=new_box[0][0]+width)
            {
                if (new_data[index][1]>new_box[0][1] && new_data[index][1]<=new_box[0][1]+height/2)
                {
                    count4L++;
                }
                if (new_data[index][1]>new_box[0][1]+height/2 && new_data[index][1]<=new_box[3][1])
                {
                    count4U++;
                }
            }
        }
        
        //knowing the number of observations in each cell, we define the arrays for each cell.
        float[][] part1L = new float[count1L][2];
        float[][] part1U = new float[count1U][2];
        float[][] part2L = new float[count2L][2];
        float[][] part2U = new float[count2U][2];
        float[][] part3L = new float[count3L][2];
        float[][] part3U = new float[count3U][2];
        float[][] part4L = new float[count4L][2];
        float[][] part4U = new float[count4U][2];
        
        //Resetting the counting.
        count1L=0;
        count2L=0;  
        count3L=0;
        count4L=0;

        count1U=0;
        count2U=0;
        count3U=0;
        count4U=0;
        

        for (int index=0; index<new_data.length; index++)
        {
            //Left cell calculation. First finding the x-data and then finding the y-data.
            if (new_data[index][0]>new_box[0][0] && new_data[index][0]<=new_box[0][0]+width/4)
            {
                if (new_data[index][1]>new_box[0][1] && new_data[index][1]<=new_box[0][1]+height/2)
                {
                    part1L[count1L][0] = new_data[index][0];
                    part1L[count1L][1] = new_data[index][1];
                    count1L++;
                }
                if (new_data[index][1]>new_box[0][1]+height/2 && new_data[index][1]<=new_box[3][1])
                {
                    part1U[count1U][0] = new_data[index][0];
                    part1U[count1U][1] = new_data[index][1];
                    count1U++;
                }
            }
            //Cell 2 from the left. 
             if (new_data[index][0]>new_box[0][0]+width/4 && new_data[index][0]<=new_box[0][0]+2*width/4)
            {
                if (new_data[index][1]>new_box[0][1] && new_data[index][1]<=new_box[0][1]+height/2)
                {
                    part2L[count2L][0] = new_data[index][0];
                    part2L[count2L][1] = new_data[index][1];
                    count2L++;
                }
                if (new_data[index][1]>new_box[0][1]+height/2 && new_data[index][1]<=new_box[3][1])
                {
                    part2U[count2U][0] = new_data[index][0];
                    part2U[count2U][1] = new_data[index][1];
                    count2U++;
                }
            }
            //third cell from the left. 
            if (new_data[index][0]>new_box[0][0]+2*width/4 && new_data[index][0]<=new_box[0][0]+3*width/4)
            {
                if (new_data[index][1]>new_box[0][1] && new_data[index][1]<=new_box[0][1]+height/2)
                {
                    part3L[count3L][0] = new_data[index][0];
                    part3L[count3L][1] = new_data[index][1];
                    count3L++;
                }
                if (new_data[index][1]>new_box[0][1]+height/2 && new_data[index][1]<=new_box[3][1])
                {
                    part3U[count3U][0] = new_data[index][0];
                    part3U[count3U][1] = new_data[index][1];
                    count3U++;
                }
            }
            //Right cell 
            if (new_data[index][0]>new_box[0][0]+3*width/4 && new_data[index][0]<=new_box[0][0]+width)
            {
                if (new_data[index][1]>new_box[0][1] && new_data[index][1]<=new_box[0][1]+height/2)
                {
                    part4L[count4L][0] = new_data[index][0];
                    part4L[count4L][1] = new_data[index][1];
                    count4L++;
                }
                if (new_data[index][1]>new_box[0][1]+height/2 && new_data[index][1]<=new_box[3][1])
                {
                    part4U[count4U][0] = new_data[index][0];
                    part4U[count4U][1] = new_data[index][1];
                    count4U++;
                }
            }
        }
            
//  --------------------------------------------------------------------
//
//                  Calculating the mean in each cell
//
//  --------------------------------------------------------------------
        
        //Initializing the sorting and median methods.
        Sorting heap = new Sorting();
        Descriptive median = new Descriptive();
        
        //Calculating median x and y in cell 2L and 2U
        part2L =(float[][]) heap.medianHeap(part2L);
        part2U =(float[][]) heap.medianHeap(part2U);
        
        //putting the median x value from cell 2L and 2U in part2x
        float part2x = (part2L[0][0]+part2U[0][0])/2;
        
        //Calculating median x and y in cell 3L and 3U
        part3L = (float[][]) heap.medianHeap(part3L);
        part3U = (float[][]) heap.medianHeap(part3U);
        
        //putting the median x value from cell 3L and 3U in part3x
        float part3x = (float)(part3L[0][0]+part3U[0][0])/2;
        
        
        //Averaging over 2L and 3L 
        float part23L = (float)(part2L[0][1]+part3L[0][1])/2;
        float part23U = (float)(part2U[0][1]+part3U[0][1])/2;
        
        //final estimate of 2L and 3L
        part2L[0][0] = part2x;
        part2L[0][1] = part23L;
        
        part3L[0][0] = part3x;
        part3L[0][1] = part23L;
        
        //final estimate of 2U and 3U
        part2U[0][0] = part2x;
        part2U[0][1] = part23U;
        
        part3L[0][0] = part3x;
        part3L[0][1] = part23U;
                
        
        //Estimating 1L and 1U
        part1L = heap.medianHeap(part1L);
        part1U = heap.medianHeap(part1U);
        
        float part1x = (float)(part1L[0][0]+part1U[0][0])/2;
        float part1y = (float)(part1L[0][1]+part1U[0][1])/2;
        
        part1L[0][0]=part1x;
        part1U[0][0]=part1x;
        
        part1L[0][1]=part1y;
        part1U[0][1]=part1y;
        

        //Estimating 4L and 4U
        part4L = (float[][]) (heap.medianHeap(part4L));
        part4U = (float[][]) (heap.medianHeap(part4U));
        
        float part4x = (float)(part4L[0][0]+part4U[0][0])/2;
        float part4y = (float)(part4L[0][1]+part4U[0][1])/2;
        
        part4L[0][0]=part4x;
        part4U[0][0]=part4x;
        
        part4L[0][1]=part4y;
        part4U[0][1]=part4y;
        
        // collecting all estimates in 1 array.
        float[][] new_mouth = new float[7][2];
        new_mouth[0][0] = part1x;
        new_mouth[1][0] = part2x;
        new_mouth[2][0] = part3x;
        new_mouth[3][0] = part4x;
        new_mouth[4][0] = part3x;
        new_mouth[5][0] = part2x;
        new_mouth[6][0] = part1x;

        new_mouth[0][1] = part1y;
        new_mouth[1][1] = part23L;
        new_mouth[2][1] = part23L;
        new_mouth[3][1] = part4y;
        new_mouth[4][1] = part23U;
        new_mouth[5][1] = part23U;
        new_mouth[6][1] = part1y;


// ---------------------------------------------------------------------
//
//              Rotating the estimate bock to original tilt
//
// ---------------------------------------------------------------------

        //finding the slope of every point. 
        //calculating the angle for all data. Used to rotate
            
        //Modulo for data
        float[] rm = new float[new_mouth.length];
        // Argument for data
        float[] new_ang_m = new float[new_mouth.length];
        
        //float[][] pre_mouth = new float[new_mouth.length][2]; 
        float[] new_m = new float[new_mouth.length]; 
        float[] ang_m = new float[new_mouth.length];
            
            
        for (int index=0; index<new_mouth.length; index++)
        {
            rm[index]=(float)(Math.sqrt(Math.pow(new_mouth[index][0],2)+Math.pow(new_mouth[index][1],2)));

            ang_m[index]=(float)(Math.acos(new_mouth[index][0]/rm[index]));

            //Subtracting the difference in angles
            new_ang_m[index] =(float) ang_m[index]+box_slope;

            //calculating the final horisontal data. 
            new_mouth[index][0] = (float)(rm[index]*Math.cos(new_ang_m[index]));
            new_mouth[index][1] = (float)(rm[index]*Math.sin(new_ang_m[index]));
        }
        
        if (count1L==0 || count1U==0)
        {
            new_mouth[0][0] = pre_mouth[0][0];
            new_mouth[0][1] = pre_mouth[0][1];
        
            new_mouth[6][0] = pre_mouth[0][0];
            new_mouth[6][1] = pre_mouth[0][1];
        }
        
        if (count2L==0)
        {
            new_mouth[5][0] = pre_mouth[5][0];
            new_mouth[5][1] = pre_mouth[5][1];
        }
        
        if (count3L==0)
        {
            new_mouth[4][0] = pre_mouth[4][0];
            new_mouth[4][1] = pre_mouth[4][1];
        }
        
        if (count4L==0 || count4U==0)
        {
            new_mouth[3][0] = pre_mouth[3][0];
            new_mouth[3][1] = pre_mouth[3][1];
        }
        
        if (count3U==0)
        {
            new_mouth[2][0] = pre_mouth[2][0];
            new_mouth[2][1] = pre_mouth[2][1];
        }
                
        if (count2U==0)
        {
            new_mouth[1][0] = pre_mouth[1][0];
            new_mouth[1][1] = pre_mouth[1][1];
        }
        
        return new_mouth; 
        }
        
    return pre_mouth;
    }
}
