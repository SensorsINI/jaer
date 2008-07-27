/* hori_meanS.java
 *
 * Created on 20. april 2008, 16:14
 *
 * Author: Alexander Tureczek
 *
/* This class is governing the model used in the facetracker. The face has been 
 meancentered and rotated to up-right position. It has been converted from complex 
 notation in MatLab xy notation in JAVA. The rotation and alligning methods has 
 not been implemented in JAVA and thus a new model has to be made in MatLab with 
 the functions meancent and rotation. The size of the model is recommended to be
 of an even number of landmarks.
 */

package ch.unizh.ini.caviar.face_track;
import java.lang.Math;


public class HorizontalFaceModel {
    
    public HorizontalFaceModel(){}
    
    //This contains the coordinates of the initial model, rotated but NOT scaled.
        public float[][] InitialFace() 
        {

            float[][] meanshape = new float[15][2];
            //This is the x-coordinates of the model
            meanshape[0][0]=0f;
            meanshape[1][0]=0.0208f;
            meanshape[2][0]=0.0581f;
            meanshape[3][0]=0.2440f;
            meanshape[4][0]=0.4264f;
            meanshape[5][0]=0.6118f;
            meanshape[6][0]=0.6474f;
            meanshape[7][0]=0.6605f;

            //Landmarks for the mouth, x-coordinates
            meanshape[8][0]=0.1823f;
            meanshape[9][0]=0.2728f;
            meanshape[10][0]=0.3530f;
            meanshape[11][0]=0.4551f;
            meanshape[12][0]=0.3694f;
            meanshape[13][0]=0.2615f;       
            meanshape[14][0]=0.1823f;       
                  
            //This is the y-coordinates of the model            
            meanshape[0][1]=-0f;
            meanshape[1][1]=-0.2156f;
            meanshape[2][1]=-0.3668f;
            meanshape[3][1]=-0.5600f;
            meanshape[4][1]=-0.5561f;
            meanshape[5][1]=-0.3774f;
            meanshape[6][1]=-0.2131f;
            meanshape[7][1]=-0f;
            //Landmarks for the mouth, y-coordinates            
            meanshape[8][1] =-0.3183f;
            meanshape[9][1] =-0.3044f;
            meanshape[10][1]=-0.3003f;
            meanshape[11][1]=-0.3176f;
            meanshape[12][1]=-0.3843f;
            meanshape[13][1]=-0.3843f;
            meanshape[14][1]=-0.3183f;
                 
            return (float[][]) meanshape;
    }

        
        
    //This method calculates the distance between the (1,1) and (8,1), this is 
    //used to calculate the scaling of the model to the LED. 
    public float FACEdist(float meanshape[][])
    {
        float x,y, dist; 
        
        x=(float) (meanshape[7][0]-meanshape[0][0])*(meanshape[7][0]-meanshape[0][0]);
        y=(float) (meanshape[7][1]-meanshape[0][1])*(meanshape[7][1]-meanshape[0][1]);
        
        //calculation of distance using standard foormula. 
        dist=(float) Math.sqrt(x+y);
        
        return dist;
    }
        
}
        