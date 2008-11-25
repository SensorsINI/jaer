/* FaceTrack.java
 *
 * Created on 14. april 2008, 18.03
 *
 * @Author: Alexander Tureczek
 * 
 * This class is controlling the flow of the filter, and initializing all 
 * variables and setting the GUI values. Also used to collect the data from the 
 * silicon retina.
 * 
 */


package ch.unizh.ini.jaer.projects.facetracker;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.*;
import java.lang.Math.*;
import net.sf.jaer.graphics.FrameAnnotater;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;


public class FaceTrack extends EventFilter2D implements FrameAnnotater, Observer {

    //final int NEVENTS = 1000;
    protected int NEVENTS = getPrefs().getInt("NEVENTS", 1000);
    {setPropertyTooltip("NEVENTS","Number of events to buffer before updating the model");}
        
    protected int LEDBOX = getPrefs().getInt("LEDBOX",5);
    {setPropertyTooltip("LEDBOX","Sets the size of the allowed movement of LED");}
    
    //Collecting data in two vectors x and y. 
    int[] x_data = new int[NEVENTS];
    int[] y_data = new int[NEVENTS];
    float[] LED = new float[5];
    float[] LEDold = new float[5];
    //float[][] hori_meanS = new float[8][2]; 
    float f_dist;
    float[][] new_hori = new float[20][2];
    float[][] pre_mouth = new float[7][2];
    float[][] mouth_boundary = new float[5][2];
    int face_end = 7; //Face has 8 landmarks but JAVA counts from 0.

    //Constructor.
    public FaceTrack(AEChip chip) {
        super(chip);        
        initFilter();
        resetFilter();   
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    //Resetting the filter, is only done when the filter is initialized.
    @Override
    public void resetFilter() {
        //Defining variables used by the filter
        
        //initializing the face model.
        HorizontalFaceModel model = new HorizontalFaceModel();
        float[][] face =(float[][]) model.InitialFace();

        //calculating the size of the face model, used for scaling to the LED.    
        HorizontalFaceModel dist = new HorizontalFaceModel();
        f_dist =(float) dist.FACEdist(face);
       
        //initial guess of the LED, calculated in matlab.    
        int[] LED1 = {35, 80};
        int[] LED2 = {105, 80};
                
        //Calculating the distance between the LED
        int x = (int) ((LED2[0] - LED1[0]) * (LED2[0] - LED1[0]));
        int y = (int) ((LED2[1] - LED1[1]) * (LED2[1] - LED1[1]));
        //calculation of distance using standard foormula. 
        float LEDdist = (float) Math.sqrt(x+ y);
        
        LEDold[0] = LED1[0];
        LEDold[1] = LED1[1];
        LEDold[2] = LED2[0];
        LEDold[3] = LED2[1];
        LEDold[4] = (float) LEDdist;

        //Defining the boundary of the mouth. the boundary points are surrounding 
        //the mouth, that is the 7 last observations in hori_meanS.java
        float minx = face[8][0];
        float miny = face[8][1];
        float maxx = face[8][0];
        float maxy = face[8][1];

        for (int i = 8; i < face.length; i++) {
            //finding minimum for the bounadary box around the emouth. 
            if (face[i][0] < minx) {
                minx = face[i][0];
            }
            if (face[i][1] < miny) {
                miny = face[i][1];
            }
            //finding maximum for the bounadary box around the emouth. 
            if (face[i][0] > maxx) {
                maxx = face[i][0];
            }
            if (face[i][1] > maxy) {
                maxy = face[i][1];
            }
        }

// ---------------------------------------------------------------------
//
//      Check disse udregninger igen, jeg kan ikke gennemskue dem 
//
// ---------------------------------------------------------------------
        float x_add = (float) (minx - 0.12);
        float y_add = (float) (miny + 0.42);
        

        //X-coordinates of the boundary box
        mouth_boundary[0][0] = minx - x_add;
        mouth_boundary[1][0] = maxx + x_add;
        mouth_boundary[2][0] = maxx + x_add;
        mouth_boundary[3][0] = minx - x_add;
        mouth_boundary[4][0] = minx - x_add;

        //Y-coordinates of the boundary box
        mouth_boundary[0][1] = miny - y_add;
        mouth_boundary[1][1] = miny - y_add;
        mouth_boundary[2][1] = maxy + y_add;
        mouth_boundary[3][1] = maxy + y_add;
        mouth_boundary[4][1] = miny - y_add;

//----------------------------------------------------------------------
//                      
//          Creating initial mouth and face
//
//----------------------------------------------------------------------

        //Creating combined face, mouth and boundary vector
        for (int i = (int) 0; i < face.length + mouth_boundary.length; i++) {
            if (i < face.length) 
            {
                new_hori[i][0] = face[i][0];
                new_hori[i][1] = face[i][1];
            } 
            else 
            {
                new_hori[i][0] = mouth_boundary[i - face.length][0];
                new_hori[i][1] = mouth_boundary[i - face.length][1];
            }
            if (i > face_end && i < face.length) 
            {
                pre_mouth[i - face_end - 1][0] = face[i][0];
                pre_mouth[i - face_end - 1][1] = face[i][1];
            }
        }

        //Setting the end point equal starting point to make sure the mouth is 
        //closed
        pre_mouth[6][0] = pre_mouth[0][0];
        pre_mouth[6][1] = pre_mouth[0][1];
        
        for (int index=0; index<new_hori.length; index++)
        {
            new_hori[index][0]=new_hori[index][0]+LEDold[0];
            new_hori[index][1]=new_hori[index][1]+LEDold[1];
        }
        
    }

    //@Override
    public void initFilter() {
        resetFilter();
    }
    
    //Parameter setting in the GUI, Setting NEVENTS and LEDBOX
    public int getNEVENTS() {
        return this.NEVENTS;
    }
    
    //Setting the NEVENTS parameters in the parameter box in the GUI.
    public void setNEVENTS(final int NEVENTS) {
        getPrefs().putInt("FaceTracker.NEVENTS",NEVENTS);
        support.firePropertyChange("NEVENTS",this.NEVENTS,NEVENTS);
        this.NEVENTS = NEVENTS;
    }
    
    //Getting the LEDBOX parameters in the parameter box in the GUI. 
    public int getLEDBOX() {
        return this.LEDBOX;
    }
    
    //Setting the LEDBOX parameters in the parameter box in the GUI.
    public void setLEDBOX(final int LEDBOX) {
        getPrefs().putInt("FaceTracker.LEDBOX",LEDBOX);
        support.firePropertyChange("LEDBOX",this.LEDBOX,LEDBOX);
        this.LEDBOX = LEDBOX;
    }

    public void update(Observable o, Object arg) {
        initFilter();
    }
    int numEventsCollected = 0;
    
    //Method for collecting data from the retina. 
    //@Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {

        if (!filterEnabled) {
            return in;
        } //Check to avoid always running filter.
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }

        for (Object e : in) {
            BasicEvent i = (BasicEvent) e;
            x_data[numEventsCollected] = (int)i.x;
            y_data[numEventsCollected] = (int)i.y;
            numEventsCollected++;
            if (numEventsCollected == NEVENTS) {
                processEvents();
                numEventsCollected = 0;
            }
        }
        
        return in;
    }

    private void processEvents() {

        //Calculating the updated estimates of the LED positions, and the distance between the LED.
        //initializing the followLED class, used for updating the LED position 
        //shape and size of the face. 
        FollowLED shapeUpdate = new FollowLED();
        HorizontalFaceModel fdist = new HorizontalFaceModel();
        Mund mouth = new Mund();
        
        //LED is an array of 5 elements with information about the Position of the LED and distance between them. 
        LED = shapeUpdate.movLED(LEDold, x_data, y_data, LEDBOX);
        
        //Updating the shape of the model. 
        new_hori = shapeUpdate.rotator(LED, f_dist, new_hori, LEDold);
  
        //Updating the FACEdist. 
        f_dist = fdist.FACEdist(new_hori);
        
        //Updating the mouth 
        pre_mouth = mouth.rotateMouth(x_data, y_data, new_hori, pre_mouth, mouth_boundary);
        
        //keeping track of the previous LED.
        LEDold=LED;
    }
    
    //Plotting of the vectors, new_hori(0:7), pre_mouth and LED.
    public void annotate(Graphics2D g) {}
       
    //Method for annotating arrays in the GUI, using openGl
    synchronized public void annotate(GLAutoDrawable drawable) {
        final float LINE_WIDTH=6f; // in pixels
        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in Face_Track.annotate");
            return;
        }

        float[] rgb=new float[4];
        rgb[0]=1;
        gl.glPushMatrix();
        
        gl.glColor3fv(rgb,0);
        gl.glLineWidth(LINE_WIDTH);
        gl.glPointSize(LINE_WIDTH);
        gl.glBegin(GL.GL_POINTS);
        for (int index=0; index<face_end+1; index++)
        {
            gl.glVertex2f(new_hori[index][0],new_hori[index][1]);
        }
        for (int index=0; index<pre_mouth.length-1; index++)
        {
            gl.glVertex2f(pre_mouth[index][0],pre_mouth[index][1]);
        }
        
        gl.glEnd();
        gl.glPopMatrix();
        
    }
    
    //Empty method for plotting, needs to implemented but not used.
    public void annotate(float[][][] frame) {}
   
    //Used for standardizing arrays removing the LED1 influence.
    public void standard(float[][]new_hori)
        {
            for (int index=0; index<new_hori.length; index++)
            {
                new_hori[index][0]=new_hori[index][0]-LED[0];
                new_hori[index][1]=new_hori[index][1]-LED[1];
            }
    
    }
}