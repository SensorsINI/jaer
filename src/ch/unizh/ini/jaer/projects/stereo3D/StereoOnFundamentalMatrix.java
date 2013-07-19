/*
 * StereoOnFundamentalMatrix.java
 * Stereo matching using small time window and no epipolar rectif but
 * directly distance to epipolar lines
 *
 * This filters need to load the file fundamental.txt in order to perform
 * This file is obtain from calibration, it contains the fundametal matrix
 * Paul Rogister, Created on June, 2008
 *
 */


package ch.unizh.ini.jaer.projects.stereo3D;
//import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match;
import java.awt.Graphics2D;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularDisparityEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * StereoMatcherOnTime:
 * combine streams of events from two cameras into one single stream of 3D event
 *
 * @author rogister
 */
public class StereoOnFundamentalMatrix extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
    protected final int RIGHT = 1;
    protected final int LEFT = 0;
//    protected final int NODISPARITY = -999;

  //  int minDiff = 14; // miniumpossible synchrony is 15 us
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
    // Parameters appearing in the GUI
 //   private int brightness=getPrefs().getInt("StereoOnFundamentalMatrix.brightness",2);

//    private int minDiff=getPrefs().getInt("StereoOnFundamentalMatrix.minDiff",14);
//    {setPropertyTooltip("minDiff","[microsec (us)] minimum delta between matching events");}


    private int realTimeBin=getPrefs().getInt("StereoOnFundamentalMatrix.realTimeBin",20);
    {setPropertyTooltip("realTimeBin","time bin for real time, after it events are dropped");}



    private int decayTimeLimit=getPrefs().getInt("StereoOnFundamentalMatrix.decayTimeLimit",10000);
    {setPropertyTooltip("decayTimeLimit","[microsec (us)] for decaying accumulated events");}

     private int deltaTime=getPrefs().getInt("StereoOnFundamentalMatrix.deltaTime",1000);
    {setPropertyTooltip("deltaTime","[microsec (us)] max allowed difference between matching events");}

//    private float mix=getPrefs().getFloat("StereoOnFundamentalMatrix.mix",0.5f);
//    {setPropertyTooltip("mix","mix==1 : only new disparity matters, mix==0, only previous, can be set between 0-1");}
//
//     private int disparityMin = getPrefs().getInt("StereoOnFundamentalMatrix.disparityMin",20);
//    {setPropertyTooltip("disparityMin","disparity range constraint");}
//     private int disparityMax = getPrefs().getInt("StereoOnFundamentalMatrix.disparityMax",20);
//    {setPropertyTooltip("disparityMax","disparity range constraint");}
//
//     private int coherenceRadius = getPrefs().getInt("StereoOnFundamentalMatrix.coherenceRadius",2);
//    {setPropertyTooltip("coherenceRadius","radiius to check coherence toward neighboring disparities");}
//    private float coherenceThreshold=getPrefs().getFloat("StereoOnFundamentalMatrix.coherenceThreshold",3.0f);
//    {setPropertyTooltip("coherenceThreshold","threshold on neighboring disparity coherence");}



  //  private boolean decayEveryFrame = getPrefs().getBoolean("StereoOnFundamentalMatrix.decayEveryFrame",false);
    //private boolean initGray = getPrefs().getBoolean("StereoOnFundamentalMatrix.initGray",false);

  //  private float threshold=getPrefs().getFloat("StereoOnFundamentalMatrix.threshold",0.4f);
  //  {setPropertyTooltip("threshold","threshold on acc values for generating new events");}

     private float maxDistance=getPrefs().getFloat("StereoOnFundamentalMatrix.maxDistance",4f);

    
     //private boolean scaleAcc = getPrefs().getBoolean("StereoOnFundamentalMatrix.scaleAcc",false);
  //  {setPropertyTooltip("scaleAcc","when true: accumulated value cannot go below zero");}
   
      

    private double[] fmatrix = new double[9];



    // do not forget to add a set and a getString/is method for each new parameter, at the end of this .java file
    
    
    // global variables
    
   private int retinaSize=128;//getPrefs().getInt("GravityCentersImageDumper.retinaSize",128);
    
   
 //  DPoint accLeftPoints[][] = new DPoint[retinaSize][retinaSize];
 //  DPoint accRightPoints[][] = new DPoint[retinaSize][retinaSize];

   int nbEventsRight[][] = new int[retinaSize][retinaSize];
   long start;
  // float step = event_strength / (colorScale + 1);
//   float step = 0.33334f;
   
   boolean firstRun = true;
   
//   protected int colorScale = 2;
//   protected float grayValue = 0.5f;
//   protected int currentTime;
   protected OutputEventIterator outItr;




   Vector<BinocularEvent> rightEventList = new Vector<BinocularEvent>();
   Vector<BinocularEvent> leftEventList = new Vector<BinocularEvent>();
    
    /** Creates a new instance of GravityCentersImageDumper */
    public StereoOnFundamentalMatrix(AEChip chip) {
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

        int n=ae.getSize();
        if(n==0) return;
                            
//        int tempcurrentTime = ae.getLastTimestamp();
//        if(tempcurrentTime!=0){
//            currentTime = tempcurrentTime; // for avoid wrong timing to corrupt data
//        }
      //  int nbloops = 0;
      //  System.out.println("ae.size= "+ae.getSize());
        Vector<BinocularEvent> rightEventList = new Vector<BinocularEvent>();
        Vector<BinocularEvent> leftEventList = new Vector<BinocularEvent>();
        nbEventsRight = new int[retinaSize][retinaSize];



        for(BinocularEvent e:ae){
            

           int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;



          if(eye==RIGHT){

               nbEventsRight[e.x][e.y]++;
               rightEventList.add(e);

               // pass all right events
               BinocularDisparityEvent oe=(BinocularDisparityEvent) outItr.nextOutput();
               oe.copyFrom(e);

          } else {
               leftEventList.add(e);
                
          }

        }
        long now;
        for(BinocularEvent e:leftEventList){

            now = System.currentTimeMillis();
            if(now-start<realTimeBin){
                processEvent(e,rightEventList);
            }
        }

//        // try with double loop, like having delayed response?
//        for(BinocularEvent e:ae){
//
//           generateEvent(e);
//
//        }
   
                
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

        checkOutputPacketEventType(BinocularDisparityEvent.class);
        outItr=out.outputIterator();

      //  checkLeftDisplayFrame();
      //  checkRightDisplayFrame();

      //  nbevents = 0;
        track(in);

    //    if (showWindow) leftdisplayCanvas.repaint();
    //    if (showWindow) rightdisplayCanvas.repaint();
    
       // System.out.println("nbevents: "+nbevents+" in.size: "+in.getSize());
        return out;
        
    }
    
   
    
  
     

    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    protected void processEvent(BinocularEvent e, Vector<BinocularEvent> righte){
    
           //   int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1; //be sure if left is same as here
           int sign = e.polarity == BinocularEvent.Polarity.Off ? 0 : 1;

           // find points a minimum distance to epipolar line
           double[] epipolarLine = computeEpipolarLineTranspose(e.x, e.y);
           // find closest point to epipolar line

           BinocularEvent me = findClosestEvent(righte, sign, e.timestamp, deltaTime, epipolarLine);

           if (me != null && e.x < me.x && nbEventsRight[me.x][me.y] > 1) {
               // accLeftPoints[e.x][e.y].d = me.x - e.x;
               generateEvent(e, me.x - e.x);
           }
    }


 


  protected BinocularEvent findClosestEvent( Vector<BinocularEvent> recentEvents, int sign, int time, int delta, double[] d){
      BinocularEvent closest = null;

      double mindist = maxDistance;
      double dist = 0;
      for (BinocularEvent e:recentEvents){

          int type = e.polarity == BinocularEvent.Polarity.Off ? 0 : 1;



          if (type == sign) { //with polarity check //eye==side&& useless here as only right events checked

            //  if (Math.abs(time - e.timestamp) < delta) {
                  dist = Math.abs(d[0] * e.x + d[1] * e.y + d[2]) / Math.sqrt(d[0] * d[0] + d[1] * d[1]);
                  if (dist < mindist) {

                      // add ordering and disparity coherence here
                      mindist = dist;
                      closest = e;
                  }
            //  }

          }     

      }

      return closest;
  }




    // d2=F'*[events_0(1,i)+1;events_0(2,i)+1;1];
    protected double[] computeEpipolarLine( int x, int y ){
        double line[] = new double[3];

        line[0] = fmatrix[0]*x + fmatrix[1]*y + fmatrix[2];
        line[1] = fmatrix[3]*x + fmatrix[4]*y + fmatrix[5];
        line[2] = fmatrix[6]*x + fmatrix[7]*y + fmatrix[8];

      

        return line;
    }

    protected double[] computeEpipolarLineTranspose( int x, int y ){
        double line[] = new double[3];

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
  


     protected void forceGenerateEvent(BinocularEvent e){
   

       generateEvent(e,0);


    }


  

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
                double[] data = new double[9];
                int d=0;
                while((line = input.readLine()) != null) {
                    String[] result = line.split("\\s");
                    //System.out.println("parsing  = "+line);
                    for (int i = 0; i < result.length; i++) {
                        // store variables
                      //  System.out.println("parsing input: "+i+" = "+result[i]);
                        data[d] = Double.parseDouble(result[i]);
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


    protected void generateEvent(BinocularEvent e, int d){
        
       // System.out.println("incoming event "+e.x+" "+e.y+" "+e.polarity+" "+e.timestamp+" "+e.eye+" "+e.type);

        BinocularDisparityEvent oe=(BinocularDisparityEvent) outItr.nextOutput();
        oe.copyFrom(e);
        oe.disparity =(byte)d;

       // System.out.println("incoming event "+oe.x+" "+oe.y+" "+oe.polarity+" "+oe.timestamp+" "+oe.eye+" "+oe.type+" "+oe.d);

             
    }

 

  


   

    protected float decayedEvent( int time ){
        float res=1;

        if(time<0){ //problem due to play back when restarting, some events are in future
            return 0;
        }

        float dt = (float)time/(float)decayTimeLimit;
        if(dt<0)dt = 0;
        //if(dt<1){
            res = 1 - (0.1f * dt);
       // }
        if(res<0.4){ //threshold){
            res = 0;
        } else {
            res = 1;
        }
        return res;
    }

    protected float decayedValue( float value, int time ){
        float res=value;

        if(time<0){ //problem due to play back when restarting, some events are in future
            return 0;
        }
        float dt = (float)time/(float)decayTimeLimit;
        if(dt<0)dt = 0;
        //if(dt<1){
            res = value - (0.1f * dt);
       // }
        if(res<0){ //important
           res = 0;
        }
        return res;
    }
    protected float decayedValue( float value, int time, float reference ){
        float res=value;

        if(time<0){ //problem due to play back when restarting, some events are in future
            return 0;
        }

        float dt = (float)time/(float)decayTimeLimit;
        if(dt<0)dt = 0;



        //if(dt<1){
        if(value>reference){ // converge toward reference
            res = value - (0.1f * dt);
            if(res<0.5f) res = 0.5f;
            if(res>value) res = value;

        } else if(value<reference){
            res = value + (0.1f * dt);
            if(res>0.5f) res = 0.5f;
            if(res<value) res = value;
        }
       // }
        return res;
    }

 
    
    /***********************************************************************************
     * // drawing on player window
     ********************************************************************************/
    
    public void annotate(Graphics2D g) {
    }
    
    protected void drawBoxCentered(GL2 gl, int x, int y, int sx, int sy){
        gl.glBegin(GL2.GL_LINE_LOOP);
        {
            gl.glVertex2i(x-sx,y-sy);
            gl.glVertex2i(x+sx+1,y-sy);
            gl.glVertex2i(x+sx+1,y+sy+1);
            gl.glVertex2i(x-sx,y+sy+1);
        }
        gl.glEnd();
    }
    
    protected void drawBox(GL2 gl, int x, int x2, int y, int y2){
        gl.glBegin(GL2.GL_LINE_LOOP);
        {
            gl.glVertex2i(x,y);
            gl.glVertex2i(x2,y);
            gl.glVertex2i(x2,y2);
            gl.glVertex2i(x,y2);
        }
        gl.glEnd();
    }

       protected void drawLine(GL2 gl, int x1, int y1, int x2, int y2){
        gl.glBegin(GL2.GL_LINE_LOOP);
        {
            gl.glVertex2i(x1,y1);
            gl.glVertex2i(x2,y2);

        }
        gl.glEnd();
    }
    
    synchronized public void annotate(GLAutoDrawable drawable) {
        final float LINE_WIDTH=5f; // in pixels
        if(!isFilterEnabled()) return;
        
        
        GL2 gl=drawable.getGL().getGL2(); // when we getString this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in GravityCentersImageDumper.annotate");
            return;
        }

          gl.glColor3f(1, 0, 1);
                  double[] line = computeEpipolarLineTranspose(45,27);
                    // line = computeEpipolarLineTranspose(63,63);
                   int iy1 = (int)Math.round(- (line[2])/line[1]);
                   int iy2 = (int)Math.round(- (line[0]*127 + line[2])/line[1]);
                   drawLine(gl,0,iy1,127,iy2);

     
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
    
    



   public void setRealTimeBin(int realTimeBin) {
        this.realTimeBin = realTimeBin;

        getPrefs().putInt("StereoOnFundamentalMatrix.realTimeBin",realTimeBin);
    }
    public int getRealTimeBin() {
        return realTimeBin;
    }
 
    public void setDeltaTime(int deltaTime) {
        this.deltaTime = deltaTime;

        getPrefs().putInt("StereoOnFundamentalMatrix.deltaTime",deltaTime);
    }
    public int getDeltaTime() {
        return deltaTime;
    }
    
  
//    public float getThreshold() {
//        return threshold;
//    }
//
//    public void setThreshold(float threshold) {
//        this.threshold = threshold;
//
//        getPrefs().putFloat("StereoOnFundamentalMatrix.threshold",threshold);
//    }

    



//    public void setBrightness(int brightness) {
//        this.brightness = brightness;
//
//        getPrefs().putInt("StereoOnFundamentalMatrix.brightness",brightness);
//    }

//     public void setDisparityMin(int disparityMin) {
//        this.disparityMin = disparityMin;
//
//        getPrefs().putInt("StereoOnFundamentalMatrix.disparityMin",disparityMin);
//    }
//    public int getDisparityMin() {
//        return disparityMin;
//    }
//
//    public void setDisparityMax(int disparityMax) {
//        this.disparityMax = disparityMax;
//
//        getPrefs().putInt("StereoOnFundamentalMatrix.disparityMax",disparityMax);
//    }
//    public int getDisparityMax() {
//        return disparityMax;
//    }
//
//    public void setMinDiff(int minDiff) {
//        this.minDiff = minDiff;
//
//        getPrefs().putInt("StereoOnFundamentalMatrix.minDiff",minDiff);
//    }
//    public int getMinDiff() {
//        return minDiff;
//    }
    
//      public void setCoherenceRadius(int coherenceRadius) {
//        this.coherenceRadius = coherenceRadius;
//
//        getPrefs().putInt("StereoOnFundamentalMatrix.coherenceRadius",coherenceRadius);
//    }
//    public int getCoherenceRadius() {
//        return coherenceRadius;
//    }
//
//     public float getCoherenceThreshold() {
//        return coherenceThreshold;
//    }
//
//    public void setCoherenceThreshold(float coherenceThreshold) {
//        this.coherenceThreshold = coherenceThreshold;
//
//        getPrefs().putFloat("StereoOnFundamentalMatrix.coherenceThreshold",coherenceThreshold);
//    }
//
//     public double getF1() {
//        return f1;
//    }
//
//    public void setF1(double f1) {
//        this.f1 = f1;
//
//        getPrefs().putDouble("StereoOnFundamentalMatrix.f1",f1);
//    }
//
//      public double getF2() {
//        return f2;
//    }
//
//    public void setF2(double f2) {
//        this.f2 = f2;
//
//        getPrefs().putDouble("StereoOnFundamentalMatrix.f2",f2);
//    }
//
//      public double getF3() {
//        return f3;
//    }
//
//    public void setF3(double f3) {
//        this.f3 = f3;
//
//        getPrefs().putDouble("StereoOnFundamentalMatrix.f3",f3);
//    }
//
//      public double getF4() {
//        return f4;
//    }
//
//    public void setF4(double f4) {
//        this.f4 = f4;
//
//        getPrefs().putDouble("StereoOnFundamentalMatrix.f4",f4);
//    }
//
//      public double getF5() {
//        return f5;
//    }
//
//    public void setF5(double f5) {
//        this.f5 = f5;
//
//        getPrefs().putDouble("StereoOnFundamentalMatrix.f5",f5);
//    }
//
//      public double getF6() {
//        return f6;
//    }
//
//    public void setF6(double f6) {
//        this.f6 = f6;
//
//        getPrefs().putDouble("StereoOnFundamentalMatrix.f6",f6);
//    }
//
//      public double getF7() {
//        return f7;
//    }
//
//    public void setF7(double f7) {
//        this.f7 = f7;
//
//        getPrefs().putDouble("StereoOnFundamentalMatrix.f7",f7);
//    }
//
//      public double getF8() {
//        return f8;
//    }
//
//    public void setF8(double f8) {
//        this.f8 = f8;
//
//        getPrefs().putDouble("StereoOnFundamentalMatrix.f8",f8);
//    }
//
//      public double getF9() {
//        return f9;
//    }
//
//    public void setF9(double f9) {
//        this.f9 = f9;
//
//        getPrefs().putDouble("StereoOnFundamentalMatrix.f9",f9);
//    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(float maxDistance) {
        this.maxDistance = maxDistance;

        getPrefs().putDouble("StereoOnFundamentalMatrix.maxDistance",maxDistance);
    }



}
