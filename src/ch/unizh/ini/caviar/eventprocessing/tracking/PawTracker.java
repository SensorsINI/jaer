/*
 * PawTracker.java
 *
 * Created on December 5, 2005, 3:49 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.eventprocessing.tracking;
import ch.unizh.ini.caviar.aemonitor.AEConstants;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.graphics.*;
import com.sun.opengl.util.*;
import java.awt.*;
//import ch.unizh.ini.caviar.util.PreferencesEditor;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

/**
 * Tracks Rat's Paw (too fill)
 *<p>
 A single cluster tracks each object but the cluster simultaneously maintains several hypotheses about the size of the object.
 A cluster is moved by the presently-dominant hypothesis. The present hypothesis is highlighted in annotation.
 The dominant hypothesis for object size is the one that has most support per pixel. A small object with large hypothesis has low average support
 but a large object with small hypotesis will have more support from the large hypothesis (usually).
 *
 * @author tobi
 */
public class PawTracker extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
//    private static Preferences prefs=Preferences.userNodeForPackage(PawTracker.class);
 
    private java.util.List<Cluster> clusters=new LinkedList<Cluster>();
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    /** the number of classes of objects */
    private final int NUM_CLASSES=2;
    
    private int nbFingers = 0; //number of fingers tracked, maybe put it somewhere else
    
    
    /** scaling can't make cluster bigger or smaller than this ratio to default cluster size */
  //  public static final float MAX_SCALE_RATIO=2;  
    
 //   private float classSizeRatio=prefs.getFloat("PawTracker.classSizeRatio",2);
 //   private boolean sizeClassificationEnabled=prefs.getBoolean("PawTracker.sizeClassificationEnabled",true);
    
    
     private int entry_xa=getPrefs().getInt("PawTracker.entry_xa",30);
     private int entry_xb=getPrefs().getInt("PawTracker.entry_xb",76);
     private int entry_ya=getPrefs().getInt("PawTracker.entry_ya",80);
     private int entry_yb=getPrefs().getInt("PawTracker.entry_yb",102);
     private int wrist_xa=getPrefs().getInt("PawTracker.wrist_xa",0);
     private int wrist_xb=getPrefs().getInt("PawTracker.wrist_xb",127);
     private int wrist_ya=getPrefs().getInt("PawTracker.wrist_ya",110);
     private int wrist_yb=getPrefs().getInt("PawTracker.wrist_yb",127);
     
     private int paw_tip_size=getPrefs().getInt("PawTracker.paw_tip_size",8);
     private int pellet_size=getPrefs().getInt("PawTracker.pellet_size",24);

     private int entryZone_threshold=getPrefs().getInt("PawTracker.entryZone_threshold",25000);
     private int entryZone_pixel_threshold=getPrefs().getInt("PawTracker.entryZone_pixel_threshold",0);
     private int max_fingers=getPrefs().getInt("PawTracker.max_fingers",4);

     private float change_ratio=getPrefs().getFloat("PawTracker.change_ratio",0.5f); //% of opposite type events above which cluster change to opp type
     
     private int min_events_per_cluster=getPrefs().getInt("PawTracker.min_events_per_cluster",1);

    private boolean showClusters = getPrefs().getBoolean("PawTracker.showClusters",true);
    private boolean showFingers = getPrefs().getBoolean("PawTracker.showFingers",true);
    
    
    protected float defaultClusterRadius;
    protected float mixingFactor=getPrefs().getFloat("PawTracker.mixingFactor",0.01f); // amount each event moves COM of cluster towards itself
    protected float velocityMixingFactor=getPrefs().getFloat("PawTracker.velocityMixingFactor",0.01f); // mixing factor for velocity computation
    
    private float surround=getPrefs().getFloat("PawTracker.surround",2f);
    private boolean dynamicSizeEnabled=getPrefs().getBoolean("PawTracker.dynamicSizeEnabled", false);
    private boolean dynamicAspectRatioEnabled=getPrefs().getBoolean("PawTracker.dynamicAspectRatioEnabled",false); 
    private boolean pathsEnabled=getPrefs().getBoolean("PawTracker.pathsEnabled", true);
    private boolean colorClustersDifferentlyEnabled=getPrefs().getBoolean("PawTracker.colorClustersDifferentlyEnabled",false);
    private boolean useOnPolarityOnlyEnabled=getPrefs().getBoolean("PawTracker.useOnPolarityOnlyEnabled",false);
    private boolean useOffPolarityOnlyEnabled=getPrefs().getBoolean("PawTracker.useOffPolarityOnlyEnabled",false);
    private float aspectRatio=getPrefs().getFloat("PawTracker.aspectRatio",1f);
    private float clusterSize=getPrefs().getFloat("PawTracker.clusterSize",.2f);
    protected boolean growMergedSizeEnabled=getPrefs().getBoolean("PawTracker.growMergedSizeEnabled",false);
    private boolean measureVelocity=getPrefs().getBoolean("PawTracker.showVelocity",true); // enabling this enables both computation and rendering of cluster velocities
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
    private boolean classifierEnabled=getPrefs().getBoolean("PawTracker.classifierEnabled",false);
    private float classifierThreshold=getPrefs().getFloat("PawTracker.classifierThreshold",0.2f);
    private boolean showAllClusters=getPrefs().getBoolean("PawTracker.showAllClusters",false);
    private boolean useNearestCluster=getPrefs().getBoolean("PawTracker.useNearestCluster",false); // use the nearest cluster to an event, not the first containing it
    
    
    
    
    
    private final float VELOCITY_VECTOR_SCALING=1e5f; // to scale rendering of cluster velocity vector
    private int predictiveVelocityFactor=1;// making this M=10, for example, will cause cluster to substantially lead the events, then slow down, speed up, etc.
    
    /** maximum and minimum allowed dynamic aspect ratio */
    public static final float ASPECT_RATIO_MAX=5, ASPECT_RATIO_MIN=0.2f;
    
    protected boolean pawIsDetected = false;
    private EntryZone entryZone = new EntryZone(128,128);
    private EntryZone wristZone = new EntryZone(128,128);
    Cluster wristCluster = null;
    Cluster pelletCluster = null;
    /** Creates a new instance of PawTracker */
    public PawTracker(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        initFilter();
        resetPawTracker();
        chip.addObserver(this);
//        prefs.addPreferenceChangeListener(this);
        
        
    }
    
    public void initFilter() {
        initDefaults();
        defaultClusterRadius=(int)Math.max(chip.getSizeX(),chip.getSizeY())*getClusterSize();
    }
    
    private void initDefaults(){
        initDefault("PawTracker.clusterLifetimeWithoutSupport","10000");
        initDefault("PawTracker.maxNumClusters","10");
        initDefault("PawTracker.clusterSize","0.15f");
        initDefault("PawTracker.numEventsStoredInCluster","100");
        initDefault("PawTracker.thresholdEventsForVisibleCluster","30");
        initDefault("PawTracker.thresholdISIForVisibleCluster","2.0f");
        
//        initDefault("PawTracker.","");
    }
    
     private void resetPawTracker(){
        pawIsDetected = false;
        //empty clusters
        clusters.clear();
        entryZone.reset();
        entryZone.typeMatters=true;
        entryZone.setCoordinates(entry_xa,entry_xb,entry_ya,entry_yb);
        wristZone.reset();
        wristZone.setCoordinates(wrist_xa,wrist_xb,wrist_ya,wrist_yb);
        pelletCluster=null;
        wristCluster=null;
        nbFingers = 0;
        setResetPawTracking(false);
    }
    
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
    
//    ArrayList<Cluster> pruneList=new ArrayList<Cluster>(1);
    protected LinkedList<Cluster> pruneList=new LinkedList<Cluster>();
    
    // the method that actually does the tracking
    synchronized private void track(EventPacket<TypedEvent> ae){
        
        
        if(isResetPawTracking()){
            // reset
            resetPawTracker();
           
            return;
        }
        
        int n=ae.getSize();
        if(n==0) return;
        int maxNumClusters=getMaxNumClusters();
        
        /******
        // if paw is not yet detected, accumulate events of entry zone in time-window buffer
        // if enough 'white' events in entry zone buffer (white because paw clearer than background)
        // then sweep (left to right) for finger tips, return kernel numbers and gravity center
        // set paw detected to true
        
        // if paw already detected
        // according to paw direction (forth:white or back:black)
        // for each add events of relevant type in fingertip defined zones to fingertip clusters
        
        // every timesteps (and not events)
        
        // move clusters, compute speed of fingertip clusters and globally of paw.
        // if high proportion of events inside fingertip cluster are of opposite type : check for direction change!
        // if direction change, swap event type accepted for relevant cluster
        // if number of fingertip below max (==4) then look for appearance of extra fingertip and/or for separation of clusters
        // possibly look also for merging of clusters
        // display clusters position and speed and record to file
         *
        ******/
        
        // later add pellet detection
        
        
        // for each event, see which cluster it is closest to and add it to this cluster.
        // if its too far from any cluster, make a new cluster if we can
//        for(int i=0;i<n;i++){
        for(TypedEvent ev:ae){
           
           
            
            // if paw is detected, add each event to cluster if falls into fingerzone
           if(pawIsDetected){
                boolean addtoPellet=false;
                // look for wrist 
                wristZone.findWrist(ev);
               
                //if pellet add to pellet
                if(pelletCluster!=null){
                    if(ev.x>=(pelletCluster.getLocation().x-pellet_size/2)
                            &&ev.x<(pelletCluster.getLocation().x+pellet_size/2)
                            &&ev.y>=(pelletCluster.getLocation().y-pellet_size/2)
                            &&ev.y<(pelletCluster.getLocation().y+pellet_size/2)){
                        
                        pelletCluster.addEvent(ev);
                        addtoPellet=true;
                    }
                } 
               
               
                if(!addtoPellet){
               
                    Cluster closest=null;
                    closest=getNearestCluster(ev);

                    if( closest!=null ){
                        closest.addEvent(ev);
                    } else {
                        // add to entryZone so that we can find fingers even after start
                        entryZone.addEvent(ev);
                    }
                }
            
           } else {
           // else if paw not detected, add events to entry zone buffer
               entryZone.addEvent(ev);
                          
           } //end if paw detected
            
            
        }
        
        
        // all events treated, check for this timestep:
        
        if(pawIsDetected){
            entryZone.typeMatters=false;
            entryZone.setCoordinates(0,127,0,127);//for further finding finger in all screen
            
            // find pellet
             entryZone.findPellet(0,2,4,3);//pixel,empty,full,sucess thresholds
            
          //  to do here
        // if paw previously detected:       
        // if high proportion of events inside fingertip cluster are of opposite type : check for direction change!
            // for each clusters
            pruneList.clear();
            for(Cluster c:clusters){
                if(c.nbOppositeEvents>c.nbEvents*change_ratio){
                    if(c.type==1){
                        //c.setTypeAndColor(0);
                        c.setType(0);
                    } else { 
                        //c.setTypeAndColor(1);
                        c.setType(1);
                    } 
                }
                // remove too small clusters
                float keps=1e3f / (c.getAvgISI()*AEConstants.TICK_DEFAULT_US);
                //if(c.nbEvents<min_events_per_cluster){
                if(keps<min_events_per_cluster){
                    pruneList.add(c);
                    nbFingers--;
                    
                }
            }
            clusters.removeAll(pruneList);
       
        // if number of fingertip below max (==4) then look for appearance of extra fingertip and/or for separation of clusters
            if(nbFingers<max_fingers){
                
                nbFingers = entryZone.findFingers(nbFingers,entryZone_threshold,entryZone_pixel_threshold);
                
            }
          
           
            
        // possibly look also for merging of clusters
        // log data
        
        } else { // if paw is not detected
            entryZone.typeMatters=true;
            entryZone.setCoordinates(entry_xa,entry_xb,entry_ya,entry_yb);//restricting to entry zone
            
            nbFingers = entryZone.findFingers(nbFingers,entryZone_threshold,entryZone_pixel_threshold);
            if(nbFingers>0) pawIsDetected = true;
        // if paw not detected :
       
        // if found at least one fingertip cluster : paw is detected
        }
        // reset entryzone buffer and cluster count of events and opposite events
        entryZone.reset();
        wristZone.reset();
        for(Cluster c:clusters){
            c.resetNbEvents();        
        }
        // prune out old clusters that don't have support : not here : we remove clusters only if end of grasp detected or merging
//        pruneList.clear();
//        for(Cluster c:clusters){
//            int t0=c.getLastEventTimestamp();
//            int t1=ae.getLastTimestamp();
//            int timeSinceSupport=t1-t0;
//            if(t0>t1 || timeSinceSupport>clusterLifetimeWithoutSupport || timeSinceSupport<0){
//                // ordinarily, we discard the cluster if it hasn't gotten any support for a while, but we also discard it if there
//                // is something funny about the timestamps
//                pruneList.add(c);
//            }
////            if(t0>t1){
////                log.warning("last cluster timestamp is later than last packet timestamp");
////            }
//        }
//        clusters.removeAll(pruneList);
        
        
        
        // merge clusters that are too close to each other : no, now we want to merge only if close and similar direction, or?
        // this must be done interatively, because merging 4 or more clusters feedforward can result in more clusters than
        // you start with. each time we merge two clusters, we start over, until there are no more merges on iteration.
        
        // for each cluster, if it is close to another cluster then merge them and start over.
        
//        int beforeMergeCount=clusters.size();
//        boolean mergePending;
//        Cluster c1=null,c2=null;
//        do{
//            mergePending=false;
//            int nc=clusters.size();
//            outer:
//                for(int i=0;i<nc;i++){
//                    c1=clusters.get(i);
//                    for(int j=i+1;j<nc;j++){
//                        c2=clusters.get(j); // get the other cluster
//                        if(c1.type==c2.type){
//                            if(c1.distanceTo(c2)<(c1.getRadius()+c2.getRadius())) { // if distance is less than sum of radii merge them
//                                // if cluster is close to another cluster, merge them
//                                mergePending=true;
//                                break outer; // break out of the outer loop
//                            }
//                        }
//                    }
//                }
//                if(mergePending && c1!=null && c2!=null){
//                    pruneList.add(c1);
//                    pruneList.add(c2);
//                    clusters.remove(c1);
//                    clusters.remove(c2);
//                    clusters.add(new Cluster(c1,c2));
//                }
//        }while(mergePending);
        
      
        
        // update paths of clusters
        for(Cluster c:clusters) c.updatePath();
        
//        if(clusters.size()>beforeMergeCount) throw new RuntimeException("more clusters after merge than before");
        if(isLogDataEnabled() && getNumClusters()>0){
            if(logStream!=null) {
                for(Cluster c:clusters){
                    if(!c.isVisible()) continue;
                    logStream.println(String.format("%d %d %f %f %f", c.getClusterNumber(), c.lastTimestamp,c.location.x,c.location.y, c.averageEventDistance));
                    if(logStream.checkError()) log.warning("eroror logging data");
                }
            }
        }
    }
        
    public int getNumClusters(){
        return clusters.size();
    }
    
    public String toString(){
        String s="PawTracker with "+clusters.size()+" clusters ";
        return s;
    }
    
    /**
     * Method that given event, returns closest cluster and distance to it. The actual computation returns the first cluster that is within the
     minDistance of the event, which reduces the computation at the cost of reduced precision.
     * @param event the event
     * @return closest cluster object (a cluster with a distance - that distance is the distance between the given event and the returned cluster).
     */
    private Cluster getNearestCluster(TypedEvent event){
        float minDistance=Float.MAX_VALUE;
        Cluster closest=null;
        float currentDistance=0;
        for(Cluster c:clusters){
        
//            float rX=c.radiusX;
//            float rY=c.radiusY; // this is surround region for purposes of dynamicSize scaling of cluster size or aspect ratio
            
             float rX=paw_tip_size+surround;
             float rY=paw_tip_size+surround; 
             
            float dx,dy;
            // test wether events is in zone of interest of cluster
            // here for the moment only test if within radius or surround
            // add directionnality based on current cluster movement? or?
            if((dx=c.distanceToX(event))<rX && (dy=c.distanceToY(event))<rY){
                currentDistance=dx+dy;
                if(currentDistance<minDistance){
                   
                    closest=c;
                    minDistance=currentDistance;
                    c.distanceToLastEvent=minDistance;
                }
                
            }
          
        }
        if(closest!=null){
            closest.incNbEvents();
            if(closest.type!=event.type){ // paul: added test on type so that cluster have only one polarity. should make it optionnal
             closest.incNbOppositeEvents();
             closest = null;
            }//end if
        }
           
        return closest;
    }
    
    /** Given AE, returns first (thus oldest) cluster that event is within.
     The radius of the cluster here depends on whether {@link #setdynamicSizeEnabled scaling} is enabled.
     * @param event the event
     * @return cluster that contains event within the cluster's radius, modfied by aspect ratio. null is returned if no cluster is close enough.
     */
//    private Cluster getFirstContainingCluster(TypedEvent event){
//        float minDistance=Float.MAX_VALUE;
//        Cluster closest=null;
//        float currentDistance=0;
//        for(Cluster c:clusters){
//            float rX=c.radiusX;
//            float rY=c.radiusY; // this is surround region for purposes of dynamicSize scaling of cluster size or aspect ratio
//            if(dynamicSizeEnabled) {
//                rX*=surround;
//                rY*=surround; // the event is captured even when it is in "invisible surround"
//            }
//            float dx,dy;
//            if((dx=c.distanceToX(event))<rX && (dy=c.distanceToY(event))<rY){
//                if(c.type==event.type){
//                     currentDistance=dx+dy;
//                     closest=c;
//                     minDistance=currentDistance;
//                     c.distanceToLastEvent=minDistance;
//                     break;
//                }
//            }
//        }
//        return closest;
//    }
    
    
     /** Represents a single tracked object */
    public class EntryZone{
        public Point2D.Float location=new Point2D.Float(); // location in chip pixels
        protected int type; // polarity: On or Off
        protected int numEvents;
        protected int timestamp;
        protected int xa=0,xb=0,ya=0,yb=0;
        protected int maxX=128,maxY=128;
        // here add an array of coordinates
        int eventsArray[][];
        int eventsTypeArray[][];
        public boolean typeMatters=true;
        
        public EntryZone(){
         
          reset();
          type=1; // white?
          
        }
        
        public EntryZone(int maxX, int maxY){
          this.maxX = maxX;
          this.maxY = maxY;
          reset();
          type=1; // white?
          
        }
         
        
      
        
        public void reset(){ 
            
            eventsArray = new int[maxX][maxY];
            eventsTypeArray = new int[maxX][maxY];   
            numEvents = 0;
      
        }
        
        public void setCoordinates( int xa, int xb, int ya, int yb){
            this.xa = xa;
            this.xb = xb;
            this.ya = ya;
            this.yb = yb;
        }
        
        
        
        public void addEvent(TypedEvent event){
            if(typeMatters){
                if(type!=event.type){
                    return;
                }
            }
            
            if(event.x>=xa&&event.x<xb&&event.y>=ya&&event.y<yb){
                eventsArray[event.x-xa][event.y-ya]++;
                if(event.type==0){
                  eventsTypeArray[event.x-xa][event.y-ya]--;
                } else {  
                  eventsTypeArray[event.x-xa][event.y-ya]++;                 
                }
                timestamp = event.timestamp;
                numEvents++;
            }
            
            
        }
        
         // if entryzonebuffer.nbevents > threshold
        // findFingerTip
        //        while not found maxnbfingers or not visited all coordinates in entryzonebuffer:
        //        loop through coordinates of entryzonebuffer starting leftmost
        //           when events at coordinates, check for neighbouring events
        //           if it matchs tip template (or enough of them) create cluster there
        //           and remove cluster events from entryzonebuffer
        //         continue until maxfinger reached or last coordinate reach
        // end findFingerTip
      
        
        public int findFingers(int nbFingers, int threshold, int pixel_threshold){
           
             
              boolean lastNoVisited = true;
              if(numEvents>threshold){  // above which fingers are likely present in entry zone
                  //System.out.println("findFingers: enough events: "+numEvents);
                 
                 while(nbFingers<max_fingers&&lastNoVisited){
                      
                      
                     int x=0,y=0; //coordinates of cluster center when found
                     int xi=0,yj=0; //coordinates of event from where cluster is found
                     int nEvents=0;
                     boolean fingerFound = false;
                     for(int i=xa;i<xb;i++){
                         for(int j=ya;j<yb;j++){
                             
                             if(eventsArray[i-xa][j-ya]>pixel_threshold){
                                 // one event at this coordinate
                                // look for fingerTip template matching
                                 fingerFound = true; //true unless proved false
                                 int k=paw_tip_size;
                                 for(int n=0;n<k-1;n++){
                                     for(int m=0;n<k-1;n++){
                                         if((i+n>=xb)||(j+m>=yb)){ 
                                            fingerFound=false;//too close to border 
                                         } else {
                                            int nb = eventsArray[i+n-xa][j+m-ya];
                                            if(nb<pixel_threshold+1) {
                                                fingerFound=false;
                                                n=k; m=k; // to end loop  
                                                nEvents=0;
                                            } else {
                                                nEvents+=nb;
                                            }
                                         }
                                     } // end for m:j
                                 } // end for n:i
                                 
                                 // in all cases remove curent point from array
                                 eventsArray[i-xa][j-ya]=0;
                                
                                 if(fingerFound){
                                    x = i+paw_tip_size/2;
                                    y = j+paw_tip_size/2;
                                    xi = i;
                                    yj = j;
                                     // end bigger loop 
                                    j=yb+1;
                                    i=xb+1; 
                                     
                                 }
                                
                             } // end if event at x,y
                             
                             if(i==xb-1&&j==yb-1){
                               lastNoVisited=false;      // end if reached last coordinates                      
                             }
                            // System.out.println("findFingers loop: i:"+i+"<xb:"+xb+" j:"+j+"<yb:"+yb+" visited:"+lastNoVisited);
                         } // end for j:y
                     }//end for i:x
                             
                     if(fingerFound){
                         // create new cluster with center at gc
                         if(clusters.size()<maxNumClusters){ // start a new cluster : not here, we ignoer events outsite finger tip zoi
                             Cluster newCluster=new Cluster(x,y,1,nEvents,timestamp,paw_tip_size);//add type
                             // add cluster to global array of clusters
                             clusters.add(newCluster);
                         }

                         
                         // remove points from array //should optimize that
                         int k=paw_tip_size;
//                         int i = x-paw_tip_size/2;
//                         int j = y-paw_tip_size/2;
                         for(int n=0;n<k-1;n++){
                            for(int m=0;n<k-1;n++){
                                if((xi+n<=xb)&&(yj+m<=yb)){
                                  eventsArray[xi+n-xa][yj+m-ya]=0;                                             
                                }
                            }
                         }
                         nbFingers++;

                     }//end if fingerfound
                     
                       
                 } // end while fingers
                  
              } // end if enough events        
              return nbFingers;
          } //end findFingers
          
        
        public void findWrist(TypedEvent event){
            
                if(event.x>=xa&&event.x<xb&&event.y>=ya&&event.y<yb){
                    if(wristCluster!=null){
                         wristCluster.addEvent(event);
                    } else {
                       wristCluster=new Cluster(event);
                       clusters.add(wristCluster);
                    }           
                }        
        }
        
        // to do: change constants to variables
         public boolean findPellet(int pixel_threshold, int empty_threshold, int full_threshold, int success_threshold){
             int success=0;
             // look at all point in zone
             
             for(int i=xa;i<xb;i++){
                 for(int j=ya;j<yb;j++){
                    if(eventsArray[i-xa][j-ya]>pixel_threshold){
                        success=0;
                       
                        // look first at central box that must be empty
                        if(pixelTypedEventCount(i+8,i+16,j-4,j+3)>empty_threshold){
                            continue;
                            
                        }
                         // inside zone is empty, look at 6 points and their opposite
                        if (compareTwoEventsZones(i,i+4,j-2,j+1,i+20,i+24,j-2,j+1,full_threshold)){
                            success++;
                        }
                        if (compareTwoEventsZones(i+2,i+6,j+2,j+5,i+18,i+22,j-6,j-3,full_threshold)){
                            success++;
                        }
                        if (compareTwoEventsZones(i+6,i+10,j+6,j+9,i+14,i+18,j-10,j-7,full_threshold)){
                            success++;
                        }
                        if (compareTwoEventsZones(i+10,i+14,j+8,j+11,i+10,i+14,j-12,j-9,full_threshold)){
                            success++;
                        }
                        if (compareTwoEventsZones(i+2,i+6,j-6,j-3,i+18,i+22,j+2,j+5,full_threshold)){
                            success++;
                        }
                        if (compareTwoEventsZones(i+6,i+10,j-10,j-7,i+14,i+18,j+6,j+9,full_threshold)){
                            success++;
                        }
                        
                        if(success>success_threshold){
                            pelletCluster = new Cluster(i+12,j,0,1,timestamp,pellet_size);
                            clusters.add(pelletCluster);
                            return true;
                        }
                        
                        
                        
                        
                        
                    }   // end if > pixel_threshold                                 
                 }//end for y                        
             }//end for x
             return false;
         }
        
         
         private boolean compareTwoEventsZones(int xa1, int xb1, int ya1, int yb1, int xa2, int xb2, int ya2, int yb2, int threshold){
            int n = pixelTypedEventCount(xa1,xb1,ya1,yb1);
            
            int t=1;
            if (n<0){
                t=0;
                n=-1;                
            } 
            if(n<=threshold) return false;
            n = pixelTypedEventCount(xa2,xb2,ya2,yb2);
            if(n==0) return false;
            int t2=1;
            if (n<0){
                t2=0;
                n=-n;
            } 
            if(n<=threshold) return false;
            if(t!=t2){
                return true;
            }
            return false;
         }
         
         
         // return number of events in box, *-1 if main type is 0
         private int pixelTypedEventCount(int x, int sx, int y, int sy){
             int n=0;
             int t=0;
             for(int i=x;i<sx;i++){
                 for(int j=y;j<sy;j++){
                     if(eventsArray[i-x][j-y]>0){
                         n++;
                     }
                     t+=eventsTypeArray[i-x][j-y];
                 }
             }
             // return positive value if type ==1, negative if type==0
             if(t<0)n=-n;
             return n;
         }
        
        
    } // end class EntryZone
    
    
    
    
    protected int clusterCounter=0; // keeps track of absolute cluster number
    
    /** Represents a single tracked object */
    public class Cluster{
        /** location of cluster in pixels */
        public Point2D.Float location=new Point2D.Float(); // location in chip pixels
        
        /** velocity of cluster in pixels/tick, where tick is timestamp tick (usually microseconds) */
        public Point2D.Float velocity=new Point2D.Float(); // velocity in chip pixels/sec
        
//        public float tauMsVelocity=50; // LP filter time constant for velocity change
        
//        private LowpassFilter velocityFilter=new LowpassFilter();
        
        private float radius; // in chip chip pixels
//        private float mass; // a cluster has a mass correspoding to its support - the higher the mass, the harder it is to change its velocity
        private float aspectRatio, radiusX, radiusY;
        
        protected final int MAX_PATH_LENGTH=100;
        protected ArrayList<Point2D.Float> path=new ArrayList<Point2D.Float>(MAX_PATH_LENGTH);
        protected Color color=null;
        
        
        protected int type; // polarity: On or Off
        protected int numEvents;
//        ArrayList<EventXYType> events=new ArrayList<EventXYType>();
        protected int lastTimestamp, firstTimestamp;
        protected float instantaneousEventRate; // in events/tick
        private float avgEventRate = 0;
        protected float instantaneousISI; // ticks/event
        private float avgISI;
        private int clusterNumber; // assigned to be the absolute number of the cluster that has been created
        private float averageEventDistance; // average (mixed) distance of events from cluster center, a measure of actual cluster size
        protected float distanceToLastEvent=Float.POSITIVE_INFINITY;
        
        private int nbEvents = 0;
        private int nbOppositeEvents = 0;
        private int prevNbEvents = 0;
        
        public Cluster(){
            setRadius(defaultClusterRadius);
            float hue=.1f;//random.nextFloat(); // not random  color          
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
            setClusterNumber(clusterCounter++);
            setAspectRatio(PawTracker.this.getAspectRatio());
        }
        
        public Cluster(TypedEvent ev){
            this();
            type = ev.type;
            float hue=.3f;//random.nextFloat(); // not random  color but according to polarity of cluster
            if(type==1) hue=.8f;
           
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
            
            
            location.x=ev.x;
            location.y=ev.y;
            lastTimestamp=ev.timestamp;
            firstTimestamp=lastTimestamp;
            numEvents=1;
            setRadius(defaultClusterRadius);
//            System.out.println("constructed "+this);
        }
        
        public Cluster(int x, int y, int type, int numEvents, int timestamp, int radius){
            this();
            this.type = type;
            float hue=.3f;//random.nextFloat(); // not random  color but according to polarity of cluster
            if(type==1) hue=.8f;
           
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
            
            
            location.x=x;
            location.y=y;
            lastTimestamp=timestamp;
            firstTimestamp=lastTimestamp;
            this.numEvents=numEvents;
            setRadius(radius);
//            System.out.println("constructed "+this);
        }
        
        
        
        public void resetNbEvents(){
            prevNbEvents = nbEvents;
            nbEvents = 0;         
            nbOppositeEvents = 0;
        }
        
        
        /**
         Computes a geometrical scale factor based on location of a point relative to the vanishing point.
         If a pixel has been selected (we ask the renderer) then we compute the perspective from this vanishing point, otherwise
         it is the top middle pixel.
         @param p a point with 0,0 at lower left corner
         @return scale factor, which grows linearly to 1 at botton of scene
         */
        final float getPerspectiveScaleFactor(Point2D.Float p){
            if(!renderer.isPixelSelected()){
                float yfrac=1f-(p.y/chip.getSizeY()); // yfrac grows to 1 at bottom of image
                return yfrac;
            }else{
                // scale is 0 at vanishing point and grows linearly to 1 at max size of chip
                int size=chip.getMaxSize();
                float d=(float)p.distance(renderer.getXsel(),renderer.getYsel());
                float scale=d/size;
                return scale;
            }
        }
        
        /** Constructs a cluster by merging two clusters. All parameters of the resulting cluster should be reasonable combinations of the
         source cluster parameters. For example, the merged location values are weighted by the number of events that have supported each 
         source cluster, so that older clusters weigh more heavily in the resulting cluster location. Subtle bugs or poor performance can result
         from not properly handling the merging of parameters.
         
         @param one the first cluster
         @param two the second cluster
         */
        public Cluster(Cluster one, Cluster two){
            this();
            if(one.type!=two.type) return; // don't want to merge clusters of different polarity, hum hum paul
            
            // merge locations by just averaging
//            location.x=(one.location.x+two.location.x)/2;
//            location.y=(one.location.y+two.location.y)/2;
            
            Cluster older=one.firstTimestamp<two.firstTimestamp? one:two;
//            Cluster older=one.numEvents>two.numEvents? one:two;

            // merge locations by average weighted by number of events supporting cluster
            int sumEvents=one.numEvents+two.numEvents;
            location.x=(one.location.x*one.numEvents+two.location.x*two.numEvents)/(sumEvents);
            location.y=(one.location.y*one.numEvents+two.location.y*two.numEvents)/(sumEvents);
            averageEventDistance=( one.averageEventDistance*one.numEvents + two.averageEventDistance*two.numEvents )/sumEvents;
            lastTimestamp=(one.lastTimestamp+two.lastTimestamp)/2;
            numEvents=sumEvents;
            firstTimestamp=older.firstTimestamp; // make lifetime the oldest src cluster
            lastTimestamp=older.lastTimestamp;
            path=older.path;
            velocity.x=older.velocity.x;
            velocity.y=older.velocity.y;
            avgEventRate=older.avgEventRate;
            avgISI=older.avgISI;
            setAspectRatio(older.getAspectRatio());
            
//            Color c1=one.getColor(), c2=two.getColor();
            setColor(older.getColor());
//            System.out.println("merged "+one+" with "+two);
            //the radius should increase
//            setRadius((one.getRadius()+two.getRadius())/2);
            if(growMergedSizeEnabled){
                float R = (one.getRadius()+two.getRadius())/2;
                setRadius(R + getMixingFactor()*R);
            }else{
                setRadius(older.getRadius());
            }
            
        }
        
        public int getLastEventTimestamp(){
//            EventXYType ev=events.get(events.size()-1);
//            return ev.timestamp;
            return lastTimestamp;
        }
        
        public void addEvent(BasicEvent event){
            
            
            // save location for computing velocity
            float oldx=location.x, oldy=location.y;
            
            float m=mixingFactor,m1=1-m;;
            
            float dt=event.timestamp-lastTimestamp; // this timestamp may be bogus if it goes backwards in time, we need to check it later
            
            // if measureVelocity is enabled, first update the location using the measured estimate of velocity.
            // this will give predictor characteristic to cluster because cluster will move ahead to the predicted location of 
            // the present event
            if(measureVelocity && dt>0){
                location.x=location.x+getPredictiveVelocityFactor()*dt*velocity.x;
                location.y=location.y+getPredictiveVelocityFactor()*dt*velocity.y;
            }
            
            // compute new cluster location by mixing old location with event location by using
            // mixing factor
            
            location.x=(m1*location.x+m*event.x);
            location.y=(m1*location.y+m*event.y);
            
            if(measureVelocity && dt>0){
                // update velocity vector using old and new position only if valid dt
                // and update it by the mixing factors
                    float oldvelx=velocity.x;
                    float oldvely=velocity.y;
                    
                    float velx=(location.x-oldx)/dt; // instantaneous velocity for this event in pixels/tick (pixels/us)
                    float vely=(location.y-oldy)/dt;
                    
                    float vm1=1-velocityMixingFactor;
                    velocity.x=vm1*oldvelx+velocityMixingFactor*velx;
                    velocity.y=vm1*oldvely+velocityMixingFactor*vely;
            }
            
            int prevLastTimestamp=lastTimestamp;
            lastTimestamp=event.timestamp;
            numEvents++;
            instantaneousISI=lastTimestamp-prevLastTimestamp;
            if(instantaneousISI<=0) instantaneousISI=1;
            avgISI=m1*avgISI+m*instantaneousISI;
            instantaneousEventRate=1f/instantaneousEventRate;
            avgEventRate=m1*avgEventRate+m*instantaneousEventRate;
            
            averageEventDistance=m1*averageEventDistance+m*distanceToLastEvent;
            
            // if scaling is enabled, now scale the cluster size
            //scale(event);
            
        }
        
        /** sets the cluster radius according to distance of event from cluster center, but only if dynamicSizeEnabled or dynamicAspectRatioEnabled.
         @param event the event to scale with
         */
//        private final void scale(BasicEvent event){
//            if(!dynamicSizeEnabled && !dynamicAspectRatioEnabled) return;
//            if(dynamicSizeEnabled){
//                float dist=distanceTo(event);
//                float oldr = radius;
//                float newr=(1-mixingFactor)*oldr+dist*mixingFactor;
//                float f;
//                if(newr>(f=defaultClusterRadius*MAX_SCALE_RATIO)) 
//                    newr=f; 
//                else if(newr<(f=defaultClusterRadius/MAX_SCALE_RATIO)) 
//                    newr=f;
//                setRadius(newr);
//            }
//            if(dynamicAspectRatioEnabled){
//                float dx=(location.x-event.x);
//                float dy=(location.y-event.y);
//                float oldAspectRatio=getAspectRatio();
//                float newAspectRatio=Math.abs(dy/dx/2);
//                if(newAspectRatio>ASPECT_RATIO_MAX) newAspectRatio=ASPECT_RATIO_MAX; else if(newAspectRatio<ASPECT_RATIO_MIN) newAspectRatio=ASPECT_RATIO_MIN;
//                setAspectRatio((1-mixingFactor)*oldAspectRatio+mixingFactor*newAspectRatio);
//            }
//        }
        
        /** @return distance of this cluster to the event in manhatten (cheap) metric (sum of abs values of x and y distance */
        private float distanceTo(BasicEvent event){
            final float dx=event.x-location.x;
            final float dy=event.y-location.y;
//            return Math.abs(dx)+Math.abs(dy);
            return distanceMetric(dx,dy);
//            dx*=dx;
//            dy*=dy;
//            float distance=(float)Math.sqrt(dx+dy);
//            return distance;
        }
        
        public float distanceMetric(float dx,float dy){
            return ((dx>0)?dx:-dx)+((dy>0)?dy:-dy);
        }
        
        /** @return distance in x direction of this cluster to the event */
        private float distanceToX(BasicEvent event){
            float distance=Math.abs(event.x-location.x);
            return distance;
        }
        
        /** @return distance in y direction of this cluster to the event */
        private float distanceToY(BasicEvent event){
            float distance=Math.abs(event.y-location.y);
            return distance;
        }
        
        /** @return distance of this cluster to the other cluster */
        protected final float distanceTo(Cluster c){
            float dx=c.location.x-location.x;
            float dy=c.location.y-location.y;
            return distanceMetric(dx,dy);
//            if(dx<0)dx=-dx;
//            if(dy<0)dy=-dy;
//            dx*=dx;
//            dy*=dy;
//            float distance=(float)Math.sqrt(dx+dy);
//            distance=dx+dy;
//            return distance;
        }
        
        /** @return the absolute size of the cluster after perspective correction, i.e., a large cluster at the bottom
         of the scene is the same absolute size as a smaller cluster higher up in the scene.
         */
        public float getRadiusCorrectedForPerspective(){
            float scale=1/getPerspectiveScaleFactor(location);
            return radius*scale;
        }
        
        public final float getRadius(){
            return radius;
        }
        
        /** the radius of a cluster is the distance in pixels from the cluster center that is the putative model size.
         If highwayPerspectiveEnabled is true, then the radius is set to a fixed size depending on the defaultClusterRadius and the perspective
         location of the cluster and r is ignored. The aspect ratio parameters of the cluster are also set.
         @param r the radius in pixels
         */
        public void setRadius(float r){
            if(!isHighwayPerspectiveEnabled())
                radius=r;
            else{
                radius=defaultClusterRadius*getPerspectiveScaleFactor(location);
            }
            radiusX=radius/aspectRatio;
            radiusY=radius*aspectRatio;
        }
        
        final public Point2D.Float getLocation() {
            return location;
        }
        public void setLocation(Point2D.Float l){
            this.location = l;
        }
        
        /** @return true if cluster has enough support */
        final public boolean isVisible(){
            boolean ret=true;
            if(numEvents<getThresholdEventsForVisibleCluster()) ret=false;
            if(avgISI<getThresholdISIForVisibleCluster()) {
               // float t = 1e3f / (avgISI*AEConstants.TICK_DEFAULT_US);
               // if(ret) System.out.println("avgisi: "+t);
                ret=false;
            }
            
            return ret;
        }
        
        /** @return lifetime of cluster in timestamp ticks */
        final public int getLifetime(){
            return lastTimestamp-firstTimestamp;
        }
        
        final public void updatePath(){
            if(!pathsEnabled) return;
            path.add(new Point2D.Float(location.x,location.y));
            if(path.size()>MAX_PATH_LENGTH) path.remove(path.get(0));
        }
        
        public String toString(){
            return String.format("Cluster #%d with %d events near x,y=%d,%d of absRadius=%.1f, visible=%s",
                    getClusterNumber(),                     numEvents,
                    (int)location.x,
                    (int)location.y,
                    getRadiusCorrectedForPerspective(),
                    isVisible()
                    );
        }
        
        public ArrayList<Point2D.Float> getPath() {
            return path;
        }
        
        public Color getColor() {
            return color;
        }
        
        public void setColor(Color color) {
            this.color = color;
        }
        
        
        public int getType() {
            return type;
        }
         
        public void setType(int type) {           
            this.type = type;            
        }
        
        public void setTypeAndColor(int type) {
            
            float hue=.3f;
            if(type==1) hue=.8f;
           
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
            this.type = type;
            
        }
        
        /** @return averaged velocity of cluster in pixels per second. The velocity is instantaneously
         computed from the movement of the cluster caused by the last event, then this velocity is mixed
         with the the old velocity by the mixing factor. Thus the mixing factor is appplied twice: once for moving
         the cluster and again for changing the velocity.
         */
        public Point2D.Float getVelocity() {
            return velocity;
        }
        
        /** @return average (mixed by {@link #mixingFactor}) distance from events to cluster center
         */
        public float getAverageEventDistance() {
            return averageEventDistance;
        }
        
        /** @see #getAverageEventDistance */
        public void setAverageEventDistance(float averageEventDistance) {
            this.averageEventDistance = averageEventDistance;
        }
        
        /** Computes the size of the cluster based on average event distance and adjusted for perpective scaling.
         A large cluster at botton of screen is the same size as a smaller cluster closer to horizon
         @return size of cluster in pizels
         */
        public float getMeasuredSizeCorrectedByPerspective(){
            float scale=getPerspectiveScaleFactor(location);
            if(scale<=0) return averageEventDistance;
            return averageEventDistance/scale;
        }
        
        /** Sets color according to measured cluster size */
        public void setColorAccordingToSize(){
            float s=getMeasuredSizeCorrectedByPerspective();
            float hue=2*s/chip.getMaxSize();
            if(hue>1) hue=1;
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
        }
        
        /** Sets color according to age of cluster */
        public void setColorAccordingToAge(){
            float brightness=(float)Math.max(0f,Math.min(1f,getLifetime()/fullbrightnessLifetime));
            Color color=Color.getHSBColor(.5f,1f,brightness);
            setColor(color);
        }
        
        public void setColorAccordingToClass() {
            float s=getMeasuredSizeCorrectedByPerspective();
            float hue=0.5f;
            if(s>getClassifierThreshold()) hue=.3f; else hue=.8f;
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
        }
        
        public void setColorAutomatically() {
            if(isColorClustersDifferentlyEnabled()){
                // color is set on object creation, don't change it
            }else if(!isClassifierEnabled()){
                setColorAccordingToSize(); // sets color according to measured cluster size, corrected by perspective, if this is enabled
                // setColorAccordingToAge(); // sets color according to how long the cluster has existed
            }else{ // classifier enabled
                setColorAccordingToClass();
            }
        }
        
        public int getClusterNumber() {
            return clusterNumber;
        }
        
        public void setClusterNumber(int clusterNumber) {
            this.clusterNumber = clusterNumber;
        }
        
        /** @return average ISI for this cluster in timestamp ticks. Average is computed using cluster location mising factor.
         */
        public float getAvgISI() {
            return avgISI;
        }
        
        public void setAvgISI(float avgISI) {
            this.avgISI = avgISI;
        }
        
        /** @return coordinates of entry zone
         */
         public int getEntryZoneXa(){
                return entry_xa;
         }
          public int getEntryZoneXb(){
                return entry_xb;
         }
         public int getEntryZoneYa(){
                return entry_ya;
         }
          public int getEntryZoneYb(){
                return entry_yb;
         }
          
         /** @return number of events in this cluster for the current timestep.
         */
        public int getNbEvents() {
            return nbEvents;
        }
        
        public void setNbEvents(int nbEvents) {
            this.nbEvents = nbEvents;
        }
         public void incNbEvents() {
            nbEvents++;
        }
         
         /** @return number of events of opposite type in this cluster for the current timestep
          *  so that we can compute change of direction when high proportion of events if from the other type
         */
        public int getNbOppositeEvents() {
            return nbOppositeEvents;
        }
        
        public void setNbOppositeEvents(int nbOppositeEvents) {
            this.nbOppositeEvents = nbOppositeEvents;
        }
        
        public void incNbOppositeEvents() {
            nbOppositeEvents++;
        }
        
        /** @return average event rate in spikes per timestamp tick. Average is computed using location mixing factor. Note that this measure
         emphasizes the high spike rates because a few events in rapid succession can rapidly push up the average rate.
         */
        public float getAvgEventRate() {
            return avgEventRate;
        }
        
        public void setAvgEventRate(float avgEventRate) {
            this.avgEventRate = avgEventRate;
        }

        public float getAspectRatio() {
            return aspectRatio;
        }
        
        public void setAspectRatio(float aspectRatio) {
            this.aspectRatio = aspectRatio;
            float radiusX=radius/aspectRatio, radiusY=radius*aspectRatio;
        }
        
    }
    
    public java.util.List<PawTracker.Cluster> getClusters() {
        return this.clusters;
    }
    
    private LinkedList<PawTracker.Cluster> getPruneList(){
        return this.pruneList;
    }
    
    
    protected static final float fullbrightnessLifetime=1000000;
    
    
    protected Random random=new Random();
    
    private final void drawCluster(final Cluster c, float[][][] fr){
        int x=(int)c.getLocation().x;
        int y=(int)c.getLocation().y;
        
        
        int sy=(int)c.getRadius(); // sx sy are (half) size of rectangle
        int sx=sy;
        int ix, iy;
        int mn,mx;
        
        if(isColorClustersDifferentlyEnabled()){
        }else{
            c.setColorAccordingToSize();
        }
        
        Color color=c.getColor();
        if(true){ // draw boxes
            iy=y-sy;    // line under center
            mn=x-sx;
            mx=x+sx;
            for(ix=mn;ix<=mx;ix++){
                colorPixel(ix,iy,fr,clusterColorChannel,color);
            }
            iy=y+sy;    // line over center
            for(ix=mn;ix<=mx;ix++){
                colorPixel(ix,iy,fr,clusterColorChannel,color);
            }
            ix=x-sx;        // line to left
            mn=y-sy;
            mx=y+sy;
            for(iy=mn;iy<=mx;iy++){
                colorPixel(ix,iy,fr,clusterColorChannel,color);
            }
            ix=x+sx;    // to right
            for(iy=mn;iy<=mx;iy++){
                colorPixel(ix,iy,fr,clusterColorChannel,color);
            }
        }else{ // draw diamond reflecting manhatten distance measure doesn't look very nice because not antialiased at all
            iy=y-sy;    // line up right from bot
            ix=x;
            mx=x+sx;
            while(ix<mx){
                colorPixel(ix++,iy++,fr,clusterColorChannel,color);
            }
            mx=x+sx;
            ix=x;
            iy=y+sy;    // line down right from top
            while(ix<mx){
                colorPixel(ix++,iy--,fr,clusterColorChannel,color);
            }
            ix=x;        // line from top down left
            iy=y+sy;
            while(iy>=y){
                colorPixel(ix--,iy--,fr,clusterColorChannel,color);
            }
            ix=x;
            iy=y-sy;
            while(iy<y){
                colorPixel(ix--,iy++,fr,clusterColorChannel,color);
            }
        }
        
        ArrayList<Point2D.Float> points=c.getPath();
        for(Point2D.Float p:points){
            colorPixel(Math.round(p.x),Math.round(p.y),fr,clusterColorChannel,color);
        }
        
    }
    
    private static final int clusterColorChannel=2;
    
    /** @param x x location of pixel
     *@param y y location
     *@param fr the frame data
     *@param channel the RGB channel number 0-2
     *@param brightness the brightness 0-1
     */
    private final void colorPixel(final int x, final int y, final float[][][] fr, int channel, Color color){
        if(y<0 || y>fr.length-1 || x<0 || x>fr[0].length-1) return;
        float[] rgb=color.getRGBColorComponents(null);
        float[] f=fr[y][x];
        for(int i=0;i<3;i++){
            f[i]=rgb[i];
        }
//        fr[y][x][channel]=brightness;
////        if(brightness<1){
//        for(int i=0;i<3;i++){
//            if(i!=channel) fr[y][x][i]=0;
//        }
////        }
    }
    
    private int clusterLifetimeWithoutSupport=getPrefs().getInt("PawTracker.clusterLifetimeWithoutSupport",10000);
    
    /** lifetime of cluster in ms without support */
    public final int getClusterLifetimeWithoutSupportUs() {
        return clusterLifetimeWithoutSupport;
    }
    
    /** lifetime of cluster in ms without support */
    public void setClusterLifetimeWithoutSupportUs(final int clusterLifetimeWithoutSupport) {
        this.clusterLifetimeWithoutSupport=clusterLifetimeWithoutSupport;
        getPrefs().putInt("PawTracker.clusterLifetimeWithoutSupport", clusterLifetimeWithoutSupport);
    }
    
    /** max distance from cluster to event as fraction of size of array */
    public final float getClusterSize() {
        return clusterSize;
    }
    
    /** sets max distance from cluster center to event as fraction of maximum size of chip pixel array.
     e.g. clusterSize=0.5 and 128x64 array means cluster has radius of 0.5*128=64 pixels.
     
     @param clusterSize
     */
    public void setClusterSize(float clusterSize) {
        if(clusterSize>1f) clusterSize=1f;
        if(clusterSize<0) clusterSize=0;
        defaultClusterRadius=(int)Math.max(chip.getSizeX(),chip.getSizeY())*clusterSize;
        this.clusterSize=clusterSize;
        for(Cluster c:clusters){
            c.setRadius(defaultClusterRadius);
        }
        getPrefs().putFloat("PawTracker.clusterSize", clusterSize);
    }
    
    private int maxNumClusters=getPrefs().getInt("PawTracker.maxNumClusters",10);
    
    /** max number of clusters */
    public final int getMaxNumClusters() {
        return maxNumClusters;
    }
    
    /** max number of clusters */
    public void setMaxNumClusters(final int maxNumClusters) {
        this.maxNumClusters=maxNumClusters;
        getPrefs().putInt("PawTracker.maxNumClusters", maxNumClusters);
    }
    
//    /** number of events to store for a cluster */
//    public int getNumEventsStoredInCluster() {
//        return prefs.getInt("PawTracker.numEventsStoredInCluster",10);
//    }
//
//    /** number of events to store for a cluster */
//    public void setNumEventsStoredInCluster(final int numEventsStoredInCluster) {
//        prefs.putInt("PawTracker.numEventsStoredInCluster", numEventsStoredInCluster);
//    }
    
    private int thresholdEventsForVisibleCluster=getPrefs().getInt("PawTracker.thresholdEventsForVisibleCluster",10);
    
    /** number of events to make a potential cluster visible */
    public final int getThresholdEventsForVisibleCluster() {
        return thresholdEventsForVisibleCluster;
    }
    
    /** number of events to make a potential cluster visible */
    public void setThresholdEventsForVisibleCluster(final int thresholdEventsForVisibleCluster) {
        this.thresholdEventsForVisibleCluster=thresholdEventsForVisibleCluster;
        getPrefs().putInt("PawTracker.thresholdEventsForVisibleCluster", thresholdEventsForVisibleCluster);
    }
    
     private float thresholdISIForVisibleCluster=getPrefs().getFloat("PawTracker.thresholdISIForVisibleCluster",0.2f);
    
    /** number of events to make a potential cluster visible */
    public final float getThresholdISIForVisibleCluster() {
        return thresholdISIForVisibleCluster;
    }
    
    /** number of events to make a potential cluster visible */
    public void setThresholdISIForVisibleCluster(final float thresholdISIForVisibleCluster) {
        this.thresholdISIForVisibleCluster=thresholdISIForVisibleCluster;
        getPrefs().putFloat("PawTracker.thresholdISIForVisibleCluster", thresholdISIForVisibleCluster);
    }
    
    
    
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }
    
    synchronized public void resetFilter() {
        clusters.clear();
    }
    
    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        track(in);
        return in;
    }
    
    
    
    private boolean highwayPerspectiveEnabled=getPrefs().getBoolean("PawTracker.highwayPerspectiveEnabled",false);
    
    public boolean isHighwayPerspectiveEnabled() {
        return highwayPerspectiveEnabled;
    }
    
    public void setHighwayPerspectiveEnabled(boolean highwayPerspectiveEnabled) {
        this.highwayPerspectiveEnabled = highwayPerspectiveEnabled;
        getPrefs().putBoolean("PawTracker.highwayPerspectiveEnabled",highwayPerspectiveEnabled);
    }
    
    public float getMixingFactor() {
        return mixingFactor;
    }
    
    public void setMixingFactor(float mixingFactor) {
        if(mixingFactor<0) mixingFactor=0; if(mixingFactor>1) mixingFactor=1f;
        this.mixingFactor = mixingFactor;
        getPrefs().putFloat("PawTracker.mixingFactor",mixingFactor);
    }
    
    /** @see #setSurround */
    public float getSurround() {
        return surround;
    }
    
    /** sets scale factor of radius that events outside the cluster size can affect the size of the cluster if
     {@link #setDynamicSizeEnabled scaling} is enabled.
     @param surround the scale factor, constrained >1 by setter. radius is multiplied by this to determine if event is within surround.
     */
    public void setSurround(float surround){
        if(surround < 1) surround = 1;
        this.surround = surround;
        getPrefs().putFloat("PawTracker.surround",surround);
    }
    
    /** @see #setPathsEnabled
     */
    public boolean isPathsEnabled() {
        return pathsEnabled;
    }
    
    /** @param pathsEnabled true to show the history of the cluster locations on each packet */
    public void setPathsEnabled(boolean pathsEnabled) {
        this.pathsEnabled = pathsEnabled;
        getPrefs().putBoolean("PawTracker.pathsEnabled",pathsEnabled);
    }
    
    /** @see #setDynamicSizeEnabled
     */
    public boolean getDynamicSizeEnabled(){
        return dynamicSizeEnabled;
    }
    
    /**
     Enables cluster size scaling. The clusters are dynamically resized by the distances of the events from the cluster center. If most events
     are far from the cluster then the cluster size is increased, but if most events are close to the cluster center than the cluster size is
     decreased. The size change for each event comes from mixing the old size with a the event distance from the center using the mixing factor.
     @param dynamicSizeEnabled true to enable scaling of cluster size
     */
    public void setDynamicSizeEnabled(boolean dynamicSizeEnabled){
        this.dynamicSizeEnabled = dynamicSizeEnabled;
        getPrefs().putBoolean("PawTracker.dynamicSizeEnabled",dynamicSizeEnabled);
    }
    
    /**@see #setColorClustersDifferentlyEnabled */
    public boolean isColorClustersDifferentlyEnabled() {
        return colorClustersDifferentlyEnabled;
    }
    
    /** @param colorClustersDifferentlyEnabled true to color each cluster a different color. false to color each cluster
     by its age
     */
    public void setColorClustersDifferentlyEnabled(boolean colorClustersDifferentlyEnabled) {
        this.colorClustersDifferentlyEnabled = colorClustersDifferentlyEnabled;
        getPrefs().putBoolean("PawTracker.colorClustersDifferentlyEnabled",colorClustersDifferentlyEnabled);
    }
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    public boolean isUseOnePolarityOnlyEnabled() {
        return useOnPolarityOnlyEnabled;
    }
    
    public void setUseOnePolarityOnlyEnabled(boolean useOnePolarityOnlyEnabled) {
        this.useOnPolarityOnlyEnabled = useOnPolarityOnlyEnabled;
        getPrefs().putBoolean("PawTracker.useOnePolarityOnlyEnabled",useOnePolarityOnlyEnabled);
    }
    
    public boolean isUseOffPolarityOnlyEnabled() {
        return useOffPolarityOnlyEnabled;
    }
    
    public void setUseOffPolarityOnlyEnabled(boolean useOffPolarityOnlyEnabled) {
        this.useOffPolarityOnlyEnabled = useOffPolarityOnlyEnabled;
        getPrefs().putBoolean("PawTracker.useOffPolarityOnlyEnabled",useOffPolarityOnlyEnabled);
    }
    
    public void annotate(Graphics2D g) {
    }
    
    protected void drawBox(GL gl, int x, int y, int sx, int sy){
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2i(x-sx,y-sy);
            gl.glVertex2i(x+sx,y-sy);
            gl.glVertex2i(x+sx,y+sy);
            gl.glVertex2i(x-sx,y+sy);
        }
        gl.glEnd();
    }
    
    synchronized public void annotate(GLAutoDrawable drawable) {
        final float LINE_WIDTH=5f; // in pixels
        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in PawTracker.annotate");
            return;
        }
        float[] rgb=new float[4];
        gl.glPushMatrix();
        try{
            {
                for(Cluster c:clusters){
                    if(showAllClusters || c.isVisible()){
                        int x=(int)c.getLocation().x;
                        int y=(int)c.getLocation().y;
                        
                        
                        int sy=(int)c.radiusY; // sx sy are (half) size of rectangle
                        int sx=(int)c.radiusX;
                        
                        // set color and line width of cluster annotation
                        c.setColorAutomatically();
                        c.getColor().getRGBComponents(rgb);
                        if(c.isVisible()){
                            gl.glColor3fv(rgb,0);
                            gl.glLineWidth(LINE_WIDTH);
                        }else{
                            gl.glColor3f(.3f,.3f,.3f);
                            gl.glLineWidth(.5f);
                        }
                        if(showClusters){
                            drawBox(gl,x,y,sx,sy);
                        }
                        // draw path points
                        gl.glPointSize(LINE_WIDTH);
                        gl.glBegin(GL.GL_POINTS);
                        {
                            ArrayList<Point2D.Float> points=c.getPath();
                            for(Point2D.Float p:points){
                                gl.glVertex2f(p.x,p.y);
                            }
                        }
                        gl.glEnd();
                        
                        // now draw velocity vector
                        if(measureVelocity){
                            gl.glBegin(GL.GL_LINES);
                            {
                                gl.glVertex2i(x,y);
                                gl.glVertex2f(x+c.velocity.x*VELOCITY_VECTOR_SCALING,y+c.velocity.y*VELOCITY_VECTOR_SCALING);
                            }
                            gl.glEnd();
                        }
                        
                        // paul: draw finger lines experimantal
                        if(showFingers){
                            if(c!=pelletCluster){
                                if(wristCluster!=null){
                                     int xpaw=(int)wristCluster.getLocation().x;
                                     int ypaw=(int)wristCluster.getLocation().y;

                                     gl.glBegin(GL.GL_LINES);
                                        {
                                            gl.glVertex2i(x,y);
                                            gl.glVertex2f(xpaw,ypaw);
                                        }
                                     gl.glEnd();
                                }
                            }
                        }
                        
                                                
//                        // draw text size of cluster corrected for perspective
//                            // text for cluster
//                            int font = GLUT.BITMAP_HELVETICA_12;
//                            gl.glColor3f(1,1,1);
//                            gl.glRasterPos3f(c.location.x,c.location.y,0);
//                            chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.1f", c.getRadiusCorrectedForPerspective()));
                        
                        // draw text avgEventRate
                        int font = GLUT.BITMAP_HELVETICA_12;
                        gl.glColor3f(1,0,0);
                        gl.glRasterPos3f(c.location.x,c.location.y,0);
                        // annotate the cluster with the event rate computed as 1/(avg ISI) in keps
                        float keps=1e3f / (c.getAvgISI()*AEConstants.TICK_DEFAULT_US);
                        //chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.0fkeps", keps ));
                       
                        chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.0f", c.prevNbEvents/keps ));
                        
                    }
                }
            }
        }catch(java.util.ConcurrentModificationException e){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
    }
    
//    void drawGLCluster(int x1, int y1, int x2, int y2)
    
    /** annotate the rendered retina frame to show locations of clusters */
    synchronized public void annotate(float[][][] frame) {
        if(!isFilterEnabled()) return;
        // disable for now TODO
        if(chip.getCanvas().isOpenGLEnabled()) return; // done by open gl annotator
        for(Cluster c:clusters){
            if(c.isVisible()){
                drawCluster(c, frame);
            }
        }
    }
    
//    public void preferenceChange(PreferenceChangeEvent evt) {
//        mixingFactor=prefs.getFloat("PawTracker.mixingFactor",0.1f); // amount each event moves COM of cluster towards itself
//        pathsEnabled=prefs.getBoolean("PawTracker.pathsEnabled", true);
//        colorClustersDifferentlyEnabled=prefs.getBoolean("PawTracker.colorClustersDifferentlyEnabled",false);
//        useOnePolarityOnlyEnabled=prefs.getBoolean("PawTracker.useOnePolarityOnlyEnabled",false);
//        useOffPolarityOnlyEnabled=prefs.getBoolean("PawTracker.useOffPolarityOnlyEnabled",false);
//        aspectRatio=prefs.getFloat("PawTracker.aspectRatio",1f);
//    }
    
    public boolean isGrowMergedSizeEnabled() {
        return growMergedSizeEnabled;
    }
    
    public void setGrowMergedSizeEnabled(boolean growMergedSizeEnabled) {
        this.growMergedSizeEnabled = growMergedSizeEnabled;
        getPrefs().putBoolean("PawTracker.growMergedSizeEnabled",growMergedSizeEnabled);
    }
    
    public float getVelocityMixingFactor() {
        return velocityMixingFactor;
    }
    
    public void setVelocityMixingFactor(float velocityMixingFactor) {
        if(velocityMixingFactor<0) velocityMixingFactor=0; if(velocityMixingFactor>1) velocityMixingFactor=1f;
        this.velocityMixingFactor = velocityMixingFactor;
        getPrefs().putFloat("PawTracker.velocityMixingFactor",velocityMixingFactor);
    }
    
    public void setShowVelocity(boolean showVelocity){
        this.measureVelocity = showVelocity;
        getPrefs().putBoolean("PawTracker.showVelocity",showVelocity);
    }
    public boolean isShowVelocity(){
        return measureVelocity;
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
    
    public float getAspectRatio() {
        return aspectRatio;
    }
    
    public void setAspectRatio(float aspectRatio) {
        if(aspectRatio<0) aspectRatio=0; else if(aspectRatio>4) aspectRatio=4;
        this.aspectRatio = aspectRatio;
        getPrefs().putFloat("PawTracker.aspectRatio",aspectRatio);
        
    }
    
    public boolean isClassifierEnabled() {
        return classifierEnabled;
    }
    
    /** Sets whether classifier is enabled.
     @param classifierEnabled true to enable classifier
     */
    public void setClassifierEnabled(boolean classifierEnabled) {
        this.classifierEnabled = classifierEnabled;
        getPrefs().putBoolean("PawTracker.classifierEnabled",classifierEnabled);
    }
    
    public float getClassifierThreshold() {
        return classifierThreshold;
    }
    
    public void setClassifierThreshold(float classifierThreshold) {
        this.classifierThreshold = classifierThreshold;
        getPrefs().putFloat("PawTracker.classifierThreshold",classifierThreshold);
    }
    
    public boolean isShowAllClusters() {
        return showAllClusters;
    }
    
    /**Sets annotation visibility of clusters that are not "visible"
     @param showAllClusters true to show all clusters even if there are not "visible"
     */
    public void setShowAllClusters(boolean showAllClusters) {
        this.showAllClusters = showAllClusters;
        getPrefs().putBoolean("PawTracker.showAllClusters",showAllClusters);
    }

    public boolean isDynamicAspectRatioEnabled() {
        return dynamicAspectRatioEnabled;
    }

    public void setDynamicAspectRatioEnabled(boolean dynamicAspectRatioEnabled) {
        this.dynamicAspectRatioEnabled = dynamicAspectRatioEnabled;
        getPrefs().putBoolean("PawTracker.dynamicAspectRatioEnabled",dynamicAspectRatioEnabled);
    }

//    public boolean isUseNearestCluster() {
//        return useNearestCluster;
//    }
//
//    public void setUseNearestCluster(boolean useNearestCluster) {
//        this.useNearestCluster = useNearestCluster;
//        prefs.putBoolean("PawTracker.useNearestCluster",useNearestCluster);
//    }

    public int getPredictiveVelocityFactor() {
        return predictiveVelocityFactor;
    }

    public void setPredictiveVelocityFactor(int predictiveVelocityFactor) {
        this.predictiveVelocityFactor = predictiveVelocityFactor;
    }
    
    
    
    public void setEntry_xa(int entry_xa) {
        this.entry_xa = entry_xa;
        getPrefs().putInt("PawTracker.entry_xa",entry_xa);
    }
    
      public int getEntry_xa() {
        return entry_xa;
    }
    
    public void setEntry_xb(int entry_xb) {
        this.entry_xb = entry_xb;
        getPrefs().putInt("PawTracker.entry_xb",entry_xb);
    }
      public int getEntry_xb() {
        return entry_xb;
    }
    
    public void setEntry_ya(int entry_ya) {
        this.entry_ya = entry_ya;
        getPrefs().putInt("PawTracker.entry_ya",entry_ya);
    }
      public int getEntry_ya() {
        return entry_ya;
    }
    
    public void setEntry_yb(int entry_yb) {
        this.entry_yb = entry_yb;
        getPrefs().putInt("PawTracker.entry_yb",entry_yb);
    }
      public int getEntry_yb() {
        return entry_yb;
    }
    
    public void setWrist_xa(int wrist_xa) {
        this.wrist_xa = wrist_xa;
        getPrefs().putInt("PawTracker.wrist_xa",wrist_xa);
    }
    
      public int getWrist_xa() {
        return wrist_xa;
    }
    
    public void setWrist_xb(int wrist_xb) {
        this.wrist_xb = wrist_xb;
        getPrefs().putInt("PawTracker.wrist_xb",wrist_xb);
    }
      public int getWrist_xb() {
        return wrist_xb;
    }
    
    public void setWrist_ya(int wrist_ya) {
        this.wrist_ya = wrist_ya;
        getPrefs().putInt("PawTracker.wrist_ya",wrist_ya);
    }
      public int getWrist_ya() {
        return wrist_ya;
    }
    
    public void setWrist_yb(int wrist_yb) {
        this.wrist_yb = wrist_yb;
        getPrefs().putInt("PawTracker.wrist_yb",wrist_yb);
    }
      public int getWrist_yb() {
        return wrist_yb;
    }
      
    public void setPaw_tip_size(int paw_tip_size) {
        this.paw_tip_size = paw_tip_size;
        getPrefs().putInt("PawTracker.paw_tip_size",paw_tip_size);
    }
      public int getPaw_tip_size() {
        return paw_tip_size;
    }
    
    public void setPellet_size(int pellet_size) {
        this.pellet_size = pellet_size;
        getPrefs().putInt("PawTracker.pellet_size",pellet_size);
    }
      public int getPellet_size() {
        return pellet_size;
    }
    
    public void setEntryZone_threshold(int entryZone_threshold) {
        this.entryZone_threshold = entryZone_threshold;
        getPrefs().putInt("PawTracker.entryZone_threshold",entryZone_threshold);
    }
    
    public int getEntryZone_threshold() {
        return entryZone_threshold;
    }
    
    public void setEntryZone_pixel_threshold(int entryZone_pixel_threshold) {
        this.entryZone_pixel_threshold = entryZone_pixel_threshold;
        getPrefs().putInt("PawTracker.entryZone_pixel_threshold",entryZone_pixel_threshold);
    }
    
    public int getEntryZone_pixel_threshold() {
        return entryZone_pixel_threshold;
    }
    
    public int getMax_fingers() {
        return max_fingers;
    }
    
    public void setMax_fingers(int max_fingers) {
        this.max_fingers = max_fingers;
        
    }
    
    private boolean resetPawTracking=getPrefs().getBoolean("PawTracker.resetPawTracking",false);

    
    public boolean isResetPawTracking() {
        return resetPawTracking;
    }
    public void setResetPawTracking(boolean resetPawTracking) {
        this.resetPawTracking = resetPawTracking;
        getPrefs().putBoolean("PawTracker.resetPawTracking",resetPawTracking);
       
    }
    
    public float getChange_ratio() {
        return change_ratio;
    }
    
    public void setChange_ratio(float change_ratio) {
        this.change_ratio = change_ratio;
        getPrefs().putFloat("PawTracker.change_ratio",change_ratio);
    }
     
    public int getMin_events_per_cluster() {
        return min_events_per_cluster;
    }
    
    public void setMin_events_per_cluster(int min_events_per_cluster) {
        this.min_events_per_cluster = min_events_per_cluster;
        getPrefs().putInt("PawTracker.min_events_per_cluster",min_events_per_cluster);
    }
    
    public void setShowClusters(boolean showClusters){
        this.showClusters = showClusters;
        getPrefs().putBoolean("PawTracker.showClusters",showClusters);
    }
    public boolean isShowClusters(){
        return showClusters;
    }
    
    public void setShowFingers(boolean showFingers){
        this.showFingers = showFingers;
        getPrefs().putBoolean("PawTracker.showfingers",showFingers);
    }
    public boolean isShowFingers(){
        return showFingers;
    }
    
}
