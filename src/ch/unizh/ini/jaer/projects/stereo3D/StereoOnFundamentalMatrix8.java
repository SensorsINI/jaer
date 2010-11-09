/*
 * StereoOnFundamentalMatrix8.java fast version
 * Stereo matching using small time window and no epipolar rectif but
 * directly distance to epipolar lines
 *
 * based on StereoOnFundamentalMatrix3 but generating BinocularXYDisparityEvent events
 *
 * This filters need to load the file fundamental.txt in order to perform
 * This file is obtain from calibration, it contains the fundametal matrix
 * Paul Rogister, Created on October, 2010
 *
 */


package ch.unizh.ini.jaer.projects.stereo3D;
import net.sf.jaer.chip.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.*;
import com.sun.opengl.util.*;
//import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

import javax.imageio.*;
import java.awt.image.*;
import java.nio.ByteBuffer;

import java.io.*;
import java.util.*;
import javax.media.opengl.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.*;
import javax.media.opengl.glu.GLU;

import java.util.List;

import java.text.*;

/**
 * StereoOnFundamentalMatrix8:
 * disparity matching using fundamental matrix
 *
 * @author rogister
 */
public class StereoOnFundamentalMatrix8 extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
    protected final int RIGHT = 1;
    protected final int LEFT = 0;
//    protected final int NODISPARITY = -999;

  //  int minDiff = 14; // miniumpossible synchrony is 15 us
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
   

    private int avgTimeBin=getPrefs().getInt("StereoOnFundamentalMatrix8.avgTimeBin",20);
    {setPropertyTooltip("avgTimeBin","time window size for averaging disparities");}


   
     private int deltaTime=getPrefs().getInt("StereoOnFundamentalMatrix8.deltaTime",1000);
    {setPropertyTooltip("deltaTime","[microsec (us)] max allowed difference between matching events");}


 
    //private boolean checkOrdering = getPrefs().getBoolean("StereoOnFundamentalMatrix8.checkOrdering",false);




  //  private boolean decayEveryFrame = getPrefs().getBoolean("StereoOnFundamentalMatrix8.decayEveryFrame",false);
    //private boolean initGray = getPrefs().getBoolean("StereoOnFundamentalMatrix8.initGray",false);

    private int threshold=getPrefs().getInt("StereoOnFundamentalMatrix8.threshold",0);
    {setPropertyTooltip("threshold","threshold on max for generating new events");}

     private float maxDistance=getPrefs().getFloat("StereoOnFundamentalMatrix8.maxDistance",4f);
  //   private float maxDiff=getPrefs().getFloat("StereoOnFundamentalMatrix8.maxDiff",10f);



     //private boolean scaleAcc = getPrefs().getBoolean("StereoOnFundamentalMatrix8.scaleAcc",false);
  //  {setPropertyTooltip("scaleAcc","when true: accumulated value cannot go below zero");}
   
      

    private float[] fmatrix = new float[9];



    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
    
    
    // global variables
    
   private int retinaSize=128;//getPrefs().getInt("GravityCentersImageDumper.retinaSize",128);
    
   
 //  DPoint accLeftPoints[][] = new DPoint[retinaSize][retinaSize];
 //  DPoint accRightPoints[][] = new DPoint[retinaSize][retinaSize];

  
   long start;
  // float step = event_strength / (colorScale + 1);
//   float step = 0.33334f;
   
   boolean firstRun = true;
   
//   protected int colorScale = 2;
//   protected float grayValue = 0.5f;
//   protected int currentTime;
   protected OutputEventIterator outItr;


    // do not use vectors...

    BinocularXYDisparityEvent rightEventList[];
    BinocularXYDisparityEvent leftEventList[];
    //private int[][] disparityCount = new int[retinaSize*retinaSize][retinaSize*retinaSize];
    private HashMap[] disparityCount = new HashMap[retinaSize*retinaSize];

    int nbEventsLeft[][] = new int[retinaSize][retinaSize];
    int nbEventsRight[][] = new int[retinaSize][retinaSize];

  // counters to keep track of how many events we have in arrays this time, need be global
    int nbRights;
    int nbLefts;

    
    boolean debug = false;
    int xdebug = 103;
    int ydebug = 77;

    /** Creates a new instance of GravityCentersImageDumper */
    public StereoOnFundamentalMatrix8(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
       
        initFilter();
       
        chip.addObserver(this);
               
    }
    
    public void initFilter() {
        
    }
            
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
     
     // the method that actually does the tracking
    synchronized private void track(EventPacket<BinocularEvent> ae){

        if(firstRun){
            firstRun = false;
            resetArrays();
        }

        int packetSize = ae.getSize();
        if(packetSize==0) return;
                            
//        int tempcurrentTime = ae.getLastTimestamp();
//        if(tempcurrentTime!=0){
//            currentTime = tempcurrentTime; // for avoid wrong timing to corrupt data
//        }
      //  int nbloops = 0;
      //  System.out.println("ae.size= "+ae.getSize());
   //     Vector<BinocularEvent> rightEventList = new Vector<BinocularEvent>();
   //     Vector<BinocularEvent> leftEventList = new Vector<BinocularEvent>();
       
        nbEventsRight = new int[retinaSize][retinaSize];
        rightEventList = new BinocularXYDisparityEvent[packetSize];
        leftEventList = new BinocularXYDisparityEvent[packetSize];
        nbRights = 0;
        nbLefts = 0;

        // store all events in left and right array for processing
        for(int index=0;index<ae.getSize();index++){
          BinocularEvent e = ae.getEvent(index);
          int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;
          int type = e.polarity == BinocularEvent.Polarity.Off ? -1 : 1;
          if(eye==RIGHT){                       
               // pass all right events
               BinocularXYDisparityEvent oe=(BinocularXYDisparityEvent) outItr.nextOutput();
               oe.copyFrom(e);
               oe.disparity = 0; //uses disparity to indicate if there is a disparity!

               // store events in array
               rightEventList[nbRights] = oe;
               nbRights++;
               nbEventsRight[e.x][e.y]++;
            } else {                            
               // store events in array
               BinocularXYDisparityEvent le= new BinocularXYDisparityEvent();
              if(debug) System.out.println("     >>>>   store e"+e);
               le.copyFrom(e);
               le.disparity = 0;
               leftEventList[nbLefts] = le;
               if(debug) System.out.println("    <<<<<   stored in "+nbLefts+" as le"+le);

               nbLefts++;

            }
        }

        if(debug) System.out.println("total nbLefts = "+nbLefts);

        
        long avgEndTime = ae.getFirstTimestamp()+avgTimeBin;
        // process all left events (main camera events)
        for(int index=0;index<nbLefts;index++){

                
                if (leftEventList[index].timestamp > avgEndTime) {
                   // end computation of max
                   // generate events based on max

                   generateEventsFromMax();
                   // reset array
                   //disparityCount = new int[retinaSize*retinaSize][retinaSize*retinaSize];

                   resetMaps();

                   // reset end time
                   avgEndTime = leftEventList[index].timestamp+avgTimeBin;
                }

                if(debug)System.out.println("processing nbLefts["+index+"]");
                processEvent(leftEventList[index], rightEventList);
            

        }

        // end of last avg time window
        // if big enough, generate events or generate anyway or trash events?
        generateEventsFromMax();
        resetMaps();

//        // try with float loop, like having delayed response?
//        for(BinocularEvent e:ae){
//
//           generateEvent(e);
//
//        }
   
                
    }

   void createMaps(){
        for (int i=0;i<retinaSize*retinaSize;i++){
            disparityCount[i] = new HashMap();
        }
    }

    void resetMaps(){
        for (int i=0;i<retinaSize*retinaSize;i++){
            disparityCount[i].clear();
        }
        nbEventsLeft = new int[retinaSize][retinaSize];
      //  nbEventsRight = new int[retinaSize][retinaSize];

        //debug 
        if(debug)System.out.println("------------------------------------------ resetMaps");

    }

    private void generateEventsFromMax(){
        int i,j=0;
        // heavy
        // for all pixels of left, find most often corresponding right pixel
        for (i=0;i<retinaSize*retinaSize;i++){
            
            // debug
            int y = i/128;
            int x= i - (y*128);
            
           if(debug)if(nbEventsLeft[x][y]>0) System.out.println(" nbEventsLeft["+x+"]["+y+"] "+nbEventsLeft[x][y]);

            generateEventsFrom(x,y,disparityCount[i]);
        }
    }

    private void generateEventsFrom( int x1, int y1, HashMap disparityLine ){
        // disparityLine is 128*128
        int sizex = retinaSize;

        int imax = findIndexOfMax(x1,y1,disparityLine);


        if(imax!=-1){
          //  int y1 = (int)Math.floor((float)index/(float)sizex);
            


          //  int y2 = (int)Math.floor((float)imax/(float)sizex);
            int y2 = imax/sizex;
            int x2 = imax - (y2*sizex);

            //System.out.println("index="+index+" x1="+x1+" y1="+y1);
            // if(debug)
                 if(x1==xdebug&&y1==ydebug) System.out.println("evenement ["+x1+","+y1+"] matches  ["+x2+","+y2+"]");


            // generate events for pair x1,y1 <-> x2,y2
            for(int i=0;i<nbLefts;i++){

                if(leftEventList[i].x==x1&&leftEventList[i].y==y1){
                    generateEvent(leftEventList[i], 1, x2-x1, y2-y1);
                }
            }

        }

    }

    private int findIndexOfMax( int x, int y, HashMap disparityLine ){
        int max = 0;
        int imax = -1;
        Iterator it = disparityLine.entrySet().iterator();
         while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            Integer val = (Integer)pair.getValue();
            if(val>max){
                imax = ((Integer)pair.getKey()).intValue();
                max = val.intValue();
            }

        }


//         for (int i=0;i<retinaSize*retinaSize;i++){
//            if(disparityLine[i] >max){
//                imax=i;
//                max = disparityLine[i];
//            }
//        }

       //if(debug)
           if(x==xdebug&&y==ydebug)if(max>0)System.out.println("findIndexOfMax ["+x+"]["+y+"] max = "+max);
        if(max<threshold)imax=-1;



        return imax;
    }
    
    public String toString(){
        String s="StereoMatcherOnTime";
        return s;
    }
    
    
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }

    synchronized public void resetFilter() {
        if(!firstRun){
            // resetArrays();
    
        
        }
    }

    synchronized public void resetArrays() {
        if(!firstRun){

          createMaps();

          //   System.out.println ("StereoMatcherOnTime resetArrays ");
          //  accLeftPoints = new DPoint[retinaSize][retinaSize];
          //  accRightPoints = new DPoint[retinaSize][retinaSize];
          loadFundamentalFromTextFile("fundamental.txt");

        }
    }



    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularEvent)) {
            // System.out.println("not a binocular event!");
            return in;
        }

        start = System.currentTimeMillis();

        checkOutputPacketEventType(BinocularXYDisparityEvent.class);
        outItr=out.outputIterator();

      // main function
        track(in);

        return out;
        
    }
    
   
    
  
     

    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    protected void processEvent(BinocularXYDisparityEvent e, BinocularXYDisparityEvent[] in){

           //if(debug)
               if(e.x==xdebug&&e.y==ydebug) System.out.println("processEvent "+e);

           //   int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1; //be sure if left is same as here
           int sign = e.polarity == BinocularEvent.Polarity.Off ? 0 : 1;

           // find points a minimum distance to epipolar line
           float[] epipolarLine = computeEpipolarLineTranspose(e.x, e.y);
           // find closest point to epipolar line
           int ori = 0;
           if (e instanceof BinocularOrientationEvent){
                BinocularOrientationEvent oe = (BinocularOrientationEvent)e;
                ori = oe.orientation;

           }

           nbEventsLeft[e.x][e.y]++;

           BinocularXYDisparityEvent[] allme = findClosestEvents(in, e.x, e.y, sign, e.timestamp, deltaTime, epipolarLine,ori);

          // BinocularXYDisparityEvent me = findClosestEvent(in, e.x, e.y, sign, e.timestamp, deltaTime, epipolarLine,ori);

         //  if (me != null) {
          for (int k = 0; k < allme.length; k++) {

            if (allme[k] != null) {
                // accLeftPoints[e.x][e.y].d = me.x - e.x;
                // Integer key = new Integer(me.y*retinaSize+me.x);

                //if(debug)
                    if(e.x==xdebug&&e.y==ydebug)System.out.println("left["+e.x+","+e.y+"]("+e.timestamp+") all closest["+k+"] = ["+allme[k].x+","+allme[k].y+"]("+allme[k].timestamp+")");

                Integer key = new Integer(allme[k].y * retinaSize + allme[k].x);
                Integer prevValue = (Integer) disparityCount[e.y * retinaSize + e.x].get(key);
                Integer newValue = 1;
                if (prevValue != null) {
                    newValue += prevValue;
                }
                disparityCount[e.y * retinaSize + e.x].put(key, newValue);

            } else { // end it
                k = allme.length;
            }
        }
           
    }


 


  protected BinocularXYDisparityEvent findClosestEvent( BinocularXYDisparityEvent[] in, int x, int y,  int sign, int time, int delta, float[] d, int orientation){
      BinocularXYDisparityEvent closest = null;
                
      float mindist = maxDistance;
      float dist = 0;

      for(int index=0;index<nbRights;index++){
          BinocularXYDisparityEvent e = in[index];
          int type = e.polarity == BinocularEvent.Polarity.Off ? 0 : 1;
     //     int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;
          int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;
          
            //    eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;
          boolean sameOrientation = false;

          if (eye == RIGHT) {

              if (e instanceof BinocularOrientationEvent){
                BinocularOrientationEvent oe = (BinocularOrientationEvent)e;
                if(oe.orientation==orientation){
                    sameOrientation = true;
                }

              } else {
                  sameOrientation = true;
              }
               if (sameOrientation&&(type == sign)){//&&(e.x<x)){ //||!usePolarity) { //with polarity check

                 if (Math.abs(time - e.timestamp) < delta) {

                   dist = (float)Math.abs(d[0] * e.x + d[1] * e.y + d[2]) / (float)Math.sqrt(d[0] * d[0] + d[1] * d[1]);
                   if (dist < mindist) {
                      if( e.disparity!=-1){ //&& e.x > x   // use d==-1 to tell if an event is already matched
                          
                               // add ordering and disparity coherence here
                               mindist = dist;

                               closest = e;

                              
                       }

                    }
                 }

               }
          }

      } // end for
      
   

      if(closest!=null){
            closest.disparity = -1;
      }
      return closest;
  }


 // alternatively



  protected BinocularXYDisparityEvent[] findClosestEvents( BinocularXYDisparityEvent[] in, int x, int y,  int sign, int time, int delta, float[] d, int orientation){

      int max = 200;
      BinocularXYDisparityEvent allClosests[] = new BinocularXYDisparityEvent[max];
      float allClosestDists[] = new float[max];

      int ic = 0;

      float mindist = maxDistance;
      float dist = 0;
       for(int index=0;index<nbRights;index++){
          BinocularDisparityEvent e = in[index];
          int type = e.polarity == BinocularEvent.Polarity.Off ? 0 : 1;
          int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;

          boolean sameOrientation = false;

          if (eye == RIGHT) {
              if (e instanceof BinocularOrientationEvent){
                BinocularOrientationEvent oe = (BinocularOrientationEvent)e;
                if(oe.orientation==orientation){
                    sameOrientation = true;
                }
              } else {
                  sameOrientation = true;
              }
               if (sameOrientation&&(type == sign)&&(e.x<x)){ //||!usePolarity) { //with polarity check

                 if (Math.abs(time - e.timestamp) < delta) {

                   dist = (float)Math.abs(d[0] * e.x + d[1] * e.y + d[2]) / (float)Math.sqrt(d[0] * d[0] + d[1] * d[1]);
                   if (dist < mindist) {
                      if( e.disparity!=-1){ // use d==-1 to tell if an event is already matched
                            if(x==xdebug&&y==ydebug){
                                int timediff = Math.abs(time - e.timestamp);
                                int disp = e.x-x;
                                System.out.println("candidate is ["+e.x+","+e.y+"], dist="+dist+" timediff="+timediff+" disp="+disp);
                            }
                               // add ordering and disparity coherence here
                               //mindist = dist;
                               ic = addClosestTo(e,dist,allClosests,allClosestDists,ic,max);
                       }
                    }
                 }
               }
          }
      }

      if(allClosests[0]!=null){
        allClosests[0].disparity = -1;
      }


      return allClosests;
  }


  int addClosestTo( BinocularDisparityEvent e, float dist, BinocularDisparityEvent[] allClosests, float[] allClosestDists, int ic, int max){
      int nic = ic;
      boolean added = false;
      if(ic==0){
          allClosestDists[ic] = dist;
          allClosests[ic] = e;
          return 1;
      } else {
          boolean continuing = true;

          int i = ic-1;
          while(continuing){
              if(dist < allClosestDists[i]){
                  // shift
                  if(i+1<max){
                      allClosestDists[i + 1] = allClosestDists[i];
                      allClosests[i + 1] = allClosests[i];
                      if(i+1==ic) added = true;
                  }
                  i--;
                  if(i<0){
                      continuing = false;
                      allClosestDists[0] = dist;
                      allClosests[0] = e;
                  }
              } else {
                  if (i + 1 < max) {
                      allClosestDists[i + 1] = dist;
                      allClosests[i + 1] = e;
                      if(i+1==ic) added = true;
                  }
                  continuing = false;
              }
          }
      }

      if(added) nic++;

      return nic;

  }




    // d2=F'*[events_0(1,i)+1;events_0(2,i)+1;1];
    protected float[] computeEpipolarLine( int x, int y ){
        float line[] = new float[3];

        line[0] = fmatrix[0]*x + fmatrix[1]*y + fmatrix[2];
        line[1] = fmatrix[3]*x + fmatrix[4]*y + fmatrix[5];
        line[2] = fmatrix[6]*x + fmatrix[7]*y + fmatrix[8];

      

        return line;
    }

    protected float[] computeEpipolarLineTranspose( int x, int y ){
        float line[] = new float[3];

     //   line[0] = fmatrix[0]*x + fmatrix[1]*y + fmatrix[2];
   //     line[1] = fmatrix[3]*x + fmatrix[4]*y + fmatrix[5];
   //     line[2] = fmatrix[6]*x + fmatrix[7]*y + fmatrix[8];

        // maybe transpose

        line[0] = fmatrix[0]*x + fmatrix[3]*y + fmatrix[6];
        line[1] = fmatrix[1]*x + fmatrix[4]*y + fmatrix[7];
        line[2] = fmatrix[2]*x + fmatrix[5]*y + fmatrix[8];


        return line;
    }

 /*
    protected boolean isCoherentDisparityNeighbor(int x, int y, float d){

        float averageNeighborDisparities = computeAvgDispAround(x,y,coherenceRadius);

        if(Math.abs((float)d-averageNeighborDisparities)<coherenceThreshold){
            return true;
        }

        return false;
    }
*/
/*
    protected float computeAvgDispAround( int x, int y, int radius){
        float res = 0;
        float totalD = 0;
        int n = 0;
        for (int i = x - radius; i < x + radius - 1; i++) {
            if (i >= 0 && i < retinaSize) {
                for (int j = y - radius; j < y + radius - 1; j++) {
                    if (j >= 0 && j < retinaSize) {
                        if(accLeftPoints[i][j]!=null){
                            // maybe add decay later to remove old values

                            if(accLeftPoints[i][j].d!=NODISPARITY) { 

                                totalD += accLeftPoints[i][j].d;
                                n++;
                            }
                        }


                    }
                }
            }
        }
        if(n>0){
            res = totalD/(float)n;
        }
        return res;

    }

*/
  


    

  

    // load fundamental matrix

    private void loadFundamentalFromTextFile(String filename ) {
        try {
            //use buffering, reading one line at a time
            //FileReader always assumes default encoding is OK!
            BufferedReader input = new BufferedReader(new FileReader(filename));
            try {
                String line = null; //not declared within while loop

               // int x = 0;
               // int y = 0;
                float[] data = new float[9];
                int d=0;
                while((line = input.readLine()) != null) {
                    String[] result = line.split("\\s");
                    //System.out.println("parsing  = "+line);
                    for (int i = 0; i < result.length; i++) {
                        // store variables
                      //  System.out.println("parsing input: "+i+" = "+result[i]);
                        data[d] = Float.parseFloat(result[i]);
                        d++;
                    }
                }

                // store
               fmatrix = data;

            } finally {
                input.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }



  // replace by if at least two element far enough from grey level 0.5f
    protected float diffToGrey( float[][] farray ){
         float res = 0;

        // sum of elements
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                res += Math.abs(farray[i][j]-0.5f);
            }
        }

        return res;
    }


    protected void generateEvent(BinocularEvent e, int valid, int dx, int dy){
        
       // System.out.println("incoming event "+e.x+" "+e.y+" "+e.polarity+" "+e.timestamp+" "+e.eye+" "+e.type);

        BinocularXYDisparityEvent oe=(BinocularXYDisparityEvent) outItr.nextOutput();
        oe.copyFrom(e);

        oe.disparity = (byte)valid; //to indicate there is a disparity (abuse)
        oe.xdisparity =(byte)dx;
        oe.ydisparity =(byte)dy;


       // System.out.println("incoming event "+oe.x+" "+oe.y+" "+oe.polarity+" "+oe.timestamp+" "+oe.eye+" "+oe.type+" "+oe.d);

             
    }

 

  


 
    
    /***********************************************************************************
     * // drawing on player window
     ********************************************************************************/
    
    public void annotate(Graphics2D g) {
    }
    
    protected void drawBoxCentered(GL gl, int x, int y, int sx, int sy){
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2i(x-sx,y-sy);
            gl.glVertex2i(x+sx+1,y-sy);
            gl.glVertex2i(x+sx+1,y+sy+1);
            gl.glVertex2i(x-sx,y+sy+1);
        }
        gl.glEnd();
    }
    
    protected void drawBox(GL gl, int x, int x2, int y, int y2){
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2i(x,y);
            gl.glVertex2i(x2,y);
            gl.glVertex2i(x2,y2);
            gl.glVertex2i(x,y2);
        }
        gl.glEnd();
    }

       protected void drawLine(GL gl, int x1, int y1, int x2, int y2){
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2i(x1,y1);
            gl.glVertex2i(x2,y2);

        }
        gl.glEnd();
    }
    
    synchronized public void annotate(GLAutoDrawable drawable) {
        final float LINE_WIDTH=5f; // in pixels
        if(!isFilterEnabled()) return;
        
        
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in GravityCentersImageDumper.annotate");
            return;
        }

          gl.glColor3f(1, 0, 1);
                 // float[] line = computeEpipolarLineTranspose(45,27);
                    // line = computeEpipolarLineTranspose(63,63);
                 //  int iy1 = (int)Math.round(- (line[2])/line[1]);
                 //  int iy2 = (int)Math.round(- (line[0]*127 + line[2])/line[1]);
                 //  drawLine(gl,0,iy1,127,iy2);

     
        gl.glPushMatrix();
     
        gl.glPopMatrix();
    }
    
//    void drawGLCluster(int x1, int y1, int x2, int y2)
    
    /** annotate the rendered retina frame to show locations of clusters */
    synchronized public void annotate(float[][][] frame) {
        if(!isFilterEnabled()) return;
        // disable for now TODO
        if(chip.getCanvas().isOpenGLEnabled()) return; // done by open gl annotator
        
    }
    
    public synchronized boolean isLogDataEnabled() {
        return logDataEnabled;
    }
    
    public synchronized void setLogDataEnabled(boolean logDataEnabled) {
        this.logDataEnabled = logDataEnabled;
        if(!logDataEnabled) {
            logStream.flush();
            logStream.close();
            logStream=null;
        }else{
            try{
                logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File("PawTrackerData.txt"))));
                logStream.println("# clusterNumber lasttimestamp x y avergeEventDistance");
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    
    



   public void setavgTimeBin(int avgTimeBin) {
        this.avgTimeBin = avgTimeBin;

        getPrefs().putInt("StereoOnFundamentalMatrix8.avgTimeBin",avgTimeBin);
    }
    public int getavgTimeBin() {
        return avgTimeBin;
    }
 
    public void setDeltaTime(int deltaTime) {
        this.deltaTime = deltaTime;

        getPrefs().putInt("StereoOnFundamentalMatrix8.deltaTime",deltaTime);
    }
    public int getDeltaTime() {
        return deltaTime;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;

        getPrefs().putInt("StereoOnFundamentalMatrix8.threshold",threshold);
    }
    public int getThreshold() {
        return threshold;
    }
  

    public float getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(float maxDistance) {
        this.maxDistance = maxDistance;

        getPrefs().putDouble("StereoOnFundamentalMatrix8.maxDistance",maxDistance);
    }

   

//    public void setCheckOrdering(boolean checkOrdering){
//        this.checkOrdering = checkOrdering;
//
//        getPrefs().putBoolean("StereoOnFundamentalMatrix8.checkOrdering",checkOrdering);
//    }
//    public boolean isCheckOrdering(){
//        return checkOrdering;
//    }




}
