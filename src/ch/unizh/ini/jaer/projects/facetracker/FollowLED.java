//LED.java 
//
// Author Alexander Tureczek
//
// This class is used to detect the movement of the LED, the size of the scaled 
//face and the rotation of the face. 



//Description:
package ch.unizh.ini.jaer.projects.facetracker;


public class FollowLED
{
public FollowLED () {}

    public float[] movLED(float[] LED, int[] x, int[] y, int box_size)
    {
        int limit=(int) x.length;        
        int count1=0, count2=0;
        float est_LED1x, est_LED2x, est_LED1y, est_LED2y;
        //Arrays used for storing the usefull values for the LED updating.
        float[] LED1xi = new float[limit];
        float[] LED2xi = new float[limit];
        float[] LED1yi = new float[limit];
        float[] LED2yi = new float[limit];
        float[] LEDu = new float[5];

        int[] LED1 = new int[2]; 
        int[] LED2 = new int[2]; 
        LED1[0] = (int) LED[0]; 
        LED1[1] = (int) LED[1];
        LED2[0] = (int) LED[2];
        LED2[1] = (int) LED[3];
        
        //Instantiating descriptive object
        Descriptive simple = new Descriptive();
        Sorting sort = new Sorting();
        
        //Finding the "good" x's'
        for (int index=0; index<x.length; index++)
        {
            //Finding the observations in the allowed region for the first LED
            if (x[index]>=LED1[0]-box_size & x[index]<=LED1[0]+box_size & y[index]>=LED1[1]-box_size & y[index]<=LED1[1]+box_size)
            {
                //if (y[index]>=LED1[1]-box_size & y[index]<=LED1[1]+box_size)
                //{
                    LED1xi[count1]=x[index];
                    LED1yi[count1]=y[index];
                    count1++;
                //}
            }

            //Finding the observations in the allowed region for the second LED
            if (x[index]>=LED2[0]-box_size & x[index]<=LED2[0]+box_size & y[index]>=LED2[1]-box_size & y[index]<=LED2[1]+box_size)
            {
              //  if ()
                //{
                    LED2xi[count2]=x[index];
                    LED2yi[count2]=y[index];
                    count2++;
                //}
            }
        }

        // Knowing the number of observations in each of the LED boxes we start 
        // defining new array with the exact length so as to make it possible to
        // calculate the median. 
        float[] LED1x = new float[count1];
        float[] LED2x = new float[count2];
        float[] LED1y = new float[count1];
        float[] LED2y = new float[count2];

        for (int index=0; index<count1; index++)
        {
            LED1x[index]=LED1xi[index];
            LED1y[index]=LED1yi[index];
        }
        
        for (int index=0; index<count2; index++)
        {
            LED2x[index]=LED2xi[index];
            LED2y[index]=LED2yi[index];
        }
        
        
        //Calculating the median of each LED box by sorting 4 arrays and then 
        //finding the median
        LED1x=sort.heapSort(LED1x);
        est_LED1x=simple.Median(LED1x);
        LED2x=sort.heapSort(LED2x);
        est_LED2x=simple.Median(LED2x);
        LED1y=sort.heapSort(LED1y);
        est_LED1y=simple.Median(LED1y);
        LED2y=sort.heapSort(LED2y);
        est_LED2y=simple.Median(LED2y);


        //This creates the result array, where the elements 0 and 1 are the coordinates 
        //for LED1 and elements 2 and 3 are the coordinates for LED2 and element 4 is the 
        //distance between the two LED. 

        LEDu[0]=(float)est_LED1x;
        LEDu[1]=(float)est_LED1y;
        LEDu[2]=(float)est_LED2x;
        LEDu[3]=(float)est_LED2y;        
        LEDu[4]=(float)distance(LEDu);

        //Returning the LED positions in an array [5] where the last cell is the distance between the LED
        return LEDu;
        }

    public float distance(float[] LEDu)
    {
        //The distance between the two LED's
        float dist= (float) Math.sqrt(Math.pow(LEDu[2]-LEDu[0], 2)+Math.pow(LEDu[3]-LEDu[1], 2));
        return dist;
    }

    //Calculating the rotation of the face due to rotation in the LED.
    public float[][] rotator(float[] LEDu, float FACEdist, float[][] meanshape, float[] LEDold)
    {
        //Hax to remove the influence of the LED from previous iteration
        for (int index=0; index<meanshape.length; index++)
        {
            meanshape[index][0]=meanshape[index][0]-LEDold[0];
            meanshape[index][1]=meanshape[index][1]-LEDold[1];
        }
        
        //Calculating the slope between the LEDs and the slope of the model
        float x_LED, y_LED, x_model,y_model, LED_slope, MODEL_slope;
        float diff_slope, s_face;

        int size=meanshape.length;

        float[] mod_ang = new float[size];
        float[] new_ang = new float[size];
        float[] r = new float[size];
        float[] new_x = new float[size];
        float[] new_y = new float[size];

        float[][] shape_update = new float[size][2];

        //Calculating the tilt between the two LED
        y_LED=LEDu[3]-LEDu[1];
        x_LED=LEDu[2]-LEDu[0];
        
        //element 8(7) is the upper right ear, and is to be where LED two is to be estimated. 
        x_model=meanshape[7][0]-meanshape[0][0];
        y_model=meanshape[7][1]-meanshape[0][1];

        LED_slope=(float) Math.atan(y_LED/x_LED);
        MODEL_slope=(float) Math.atan(y_model/x_model);

        //Calculating the difference between the MODEL_slope and the LED_slope
        diff_slope=LED_slope-MODEL_slope;

        //Calculating the angle of each landmark of the model.
        for (int index=1; index<size; index++)
        {
            if (meanshape[index][0]<0 & meanshape[index][1]<0)
            {
                //This is the angle of the landmarks in the model.
                mod_ang[index]=(float)(Math.atan((meanshape[index][1]-meanshape[0][1])/(meanshape[index][0]-meanshape[0][0]))); //+ Math.PI));

                //This is the angle of the difference between the model from last iteration and the new data. 
                new_ang[index]=(float) mod_ang[index]+diff_slope;

                //This is the length of the vector pointing to each landmark.
                r[index]=(float) Math.sqrt(meanshape[index][0]*meanshape[index][0]+meanshape[index][1]*meanshape[index][1]);

            }
            else
            {
                //This is the angle of the landmarks in the model.
                mod_ang[index]=(float) Math.atan((meanshape[index][1]-meanshape[0][1])/(meanshape[index][0]-meanshape[0][0]));           

                //This is the angle of the difference between the model from last iteration and the new data. 
                new_ang[index]=mod_ang[index]+diff_slope;

                //This is the length of the vector pointing to each landmark.
                r[index]=(float) Math.sqrt(meanshape[index][0]*meanshape[index][0]+meanshape[index][1]*meanshape[index][1]);
            }
            new_x[index]=(float) (r[index]*Math.cos(new_ang[index]));
            new_y[index]=(float) (r[index]*Math.sin(new_ang[index]));

        }
        //Scaling the face.
        //This is not really necessary, as they should be defined as zero from the start. BUT it's safety I guess : ) 
        mod_ang[0]=0;
        new_ang[0]=0;
        r[0]=0;
        new_x[0]=0;
        new_y[0]=0;

        //Calculating the scaling factor, used to scale the face to the LED. 
        s_face=LEDu[4]/FACEdist;

        //A matrix of a new scaled face is constructed. 
        for (int index=0; index<new_x.length; index++)
        {
            shape_update[index][0]=s_face*new_x[index]+LEDu[0];
            shape_update[index][1]=s_face*new_y[index]+LEDu[1];
            
            //Hack to remove rounding errors resulting in wrong angles and hence
            //wrong landmarks positions. 
            if (index>0 && index<4)
            {
                if (shape_update[index][1]>shape_update[0][1])
                {
                    float diff=shape_update[index][1]-shape_update[0][1];
                    shape_update[index][1]=shape_update[index][1]-2*diff;
                }
            }
        }

        //The method returns an array with new coordinates of the landmarks in the model. 
        //The has been scaled. The face distance is calculated in the method FACEdist in hori_meanS.
        return shape_update;
        }
}

