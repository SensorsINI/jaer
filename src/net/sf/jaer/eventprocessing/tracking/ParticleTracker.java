/*
 * ParticleTracker.java
 *
 * Created on 30. april 2007, 16:18
 *
 */

package net.sf.jaer.eventprocessing.tracking;
//import ch.unizh.ini.caviar.aemonitor.AEConstants;
import net.sf.jaer.chip.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.*;
import java.awt.*;
//import ch.unizh.ini.caviar.util.PreferencesEditor;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;


/**
 *
 * @author Philipp <hafliger@ifi.uio.no>
 */
public class ParticleTracker extends EventFilter2D implements FrameAnnotater, Observer{

    private static Preferences prefs=Preferences.userNodeForPackage(ParticleTracker.class);
    
    private java.util.List<Cluster> clusters=new LinkedList<Cluster>();
    protected AEChip chip;
    private AEChipRenderer renderer;
    private int[][] lastCluster= new int[128][128];
    private int[][] lastEvent= new int[128][128];
    private int next_cluster_id=1;
    protected Random random=new Random();
    private PrintStream logStream=null;
    
    //Variables that will be set by the tracker parameter pop-up window: 
    //------------------------------------------------------------------
    //private final int CLUSTER_UNSUPPORTED_LIFETIME=50000;
    int surround=2;//neighbourhood that is searched for recent events that are considered to belong to the same cluster
    int clusterUnsupportedLifetime= prefs.getInt("ParticleTracker.clusterUnsupportedLifetime",50000);
    //private final int CLUSTER_MINLIFEFORCE_4_DISPLAY=10;
    float clusterMinMass4Display= prefs.getFloat("ParticleTracker.clusterMinMass4Display",10);
    boolean OnPolarityOnly=prefs.getBoolean("ParticleTracker.OnPolarityOnly",false);
    float displayVelocityScaling=prefs.getFloat("ParticleTracker.DisplayVelocityScaling",1000.0f);
    int logFrameLength=prefs.getInt("Particletracker.LogFrameLength",0);
    int logFrameNumber=0;


   
    /** Creates a new instance of ParticleTracker */
    public ParticleTracker(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
//        chip.getRenderer().addAnnotator(this); // to draw the clusters
//        chip.getCanvas().addAnnotator(this);
        chip.addObserver(this);
//        prefs.addPreferenceChangeListener(this);
//        Cluster newCluster=new Cluster(40,60,-1,-1);
//        clusters.add(newCluster);
        initFilter();
    }
    
// All callback functions for the tracker parameter pop-up:
// -----------------------------------------------------------
    public void setDisplayVelocityScaling(float x){
        this.displayVelocityScaling=x;
        prefs.putFloat("ParticleTracker.displayVelocityScaling", displayVelocityScaling);
    }
    public final float getDisplayVelocityScaling(){
        return(displayVelocityScaling);
    }
    public void setLogFrameLength(int x){
        if(x>0){
           //log.warning("I think I am opening a file here *************************");
            openLog();
        }else{
            closeLog();
        }
        this.logFrameLength=x;
        prefs.putInt("ParticleTracker.logFrameLength", logFrameLength);
    }
    public final int getLogFrameLength(){
        return(logFrameLength);
    }
    public void setClusterUnsupportedLifetime(int x){
        this.clusterUnsupportedLifetime=x;
        prefs.putInt("ParticleTracker.clusterUnsupportedLifetime", clusterUnsupportedLifetime);
    }
    public final int getClusterUnsupportedLifetime(){
        return(clusterUnsupportedLifetime);
    }
    public void setOnPolarityOnly(boolean b){
        this.OnPolarityOnly=b;
        prefs.putBoolean("ParticleTracker.OnPolarityOnly", OnPolarityOnly);
    }
    public final boolean getOnPolarityOnly(){
        return(OnPolarityOnly);
    }
    public void setClusterMinMass4Display(float x){
        this.clusterMinMass4Display=x;
        prefs.putFloat("ParticleTracker.clusterMinMass4Display", clusterMinMass4Display);
    }
    public final float getClusterMinMass4Display(){
        return(clusterMinMass4Display);
    }
    synchronized public void setFilterEnabled(boolean enabled) {
        super.setFilterEnabled(enabled);
        initFilter();
        if (!enabled){
            closeLog();
        }
    } 
// all default values for the tracker parameter pop-up
    private void initDefaults(){
        initDefault("ParticleTracker.clusterUnsupportedLifetime","50000");
        initDefault("ParticleTracker.ParticleTracker.clusterMinMass4Display","10");
        initDefault("ParticleTracker.displayVelocityScaling","1000.0f");
        initDefault("ParticleTracker.OnPolarityOnly","false");
        initDefault("ParticleTracker.LogFrameRate","0");
        
//        initDefault("ClassTracker.","");
    }
    
    private void initDefault(String key, String value){
        if(prefs.get(key,null)==null) prefs.put(key,value);
    }
    
    private void closeLog(){
        if(logStream!=null){
           //log.warning("I think I am closing a file here *************************");
                logStream.println("otherwise");
                logStream.println("particles=[];");
                //logStream.println("cla;");
                //logStream.println("set(gca,'xlim',xlim,'ylim',ylim)");
                logStream.println("end; %switch");
                logStream.flush();
                logStream.close();
                logStream=null;
        }
    }
    
    private void openLog(){
            if(logStream==null){
                try{
                    logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File("ParticleTrackerLog.m"))));
                    //logStream.println("function [particles]=ParticleTrackerLog(frameN,time_shift,xshift,yshift,xscaling,yscaling,xlim,ylim)");
                    logStream.println("function [particles]=ParticleTrackerLog(frameN)");
                    logStream.println("% lasttimestamp x y u v");
                    //logStream.println("switch (frameN+time_shift)");
                    logStream.println("switch (frameN)");
                }catch(Exception e){
                    e.printStackTrace();
                }
            }        
    }
    
    private void printClusterLog(PrintStream log, LinkedList<Cluster> cl, int frameNumber, int now){

       ListIterator listScanner=cl.listIterator();
       Cluster c=null;
       int time_limit;
       
       if(logStream==null) openLog();
       time_limit=now-clusterUnsupportedLifetime;
       logStream.println(String.format("case %d", frameNumber));
       logStream.println(String.format("particles=["));
       while (listScanner.hasNext()){ 
           c=(Cluster)listScanner.next();
           if ((c.last < time_limit)||(c.last > now)){ //check if cluster is dead or if time has moved backwards
                listScanner.remove();
           }else{
                if ((c.mass*weighEvent((float)c.last,(float)now))>clusterMinMass4Display){
                    logStream.println(String.format("%d %e %e %e %e", c.last,c.location.x,c.location.y,c.velocity.x,c.velocity.y));
                }
           }
       }
       logStream.println("];");
       //logStream.println("if (~isempty(particles))");
       //logStream.println("plot(xscaling*particles(:,2)-xshift,yscaling*particles(:,3)-yshift,'o')");
       //logStream.println("set(gca,'xlim',xlim,'ylim',ylim)");
       //logStream.println("end; %if");
    }
    
    public float weighEvent(float t_ev, float now){
        float result;
        
        result=(float)Math.exp(-(now-t_ev)/(float)clusterUnsupportedLifetime);
        //result=1f-(now-t_ev)/(float)clusterUnsupportedLifetime;
        return(result);
    }

    // the method that actually does the tracking
    synchronized private void track(EventPacket<BasicEvent> ae){
        int n=ae.getSize();
        //int surround=2;
        if(n==0) return;
        int l,k,i,ir,il,j,jr,jl;
        //int most_recent;
        //LinkedList<Cluster> pruneList=new LinkedList<Cluster>();
        int[] cluster_ids=new int[9];
        Cluster thisCluster=null;
        int[] merged_ids=null;
        float thisClusterWeight;
        float thatClusterWeight;
        ListIterator listScanner;
        Cluster c=null;
        //int maxNumClusters=getMaxNumClusters();
        
        // for each event, see which cluster it is closest to and add it to this cluster.
        // if its too far from any cluster, make a new cluster if we can
//        for(int i=0;i<n;i++){
        for(BasicEvent ev:ae){
            // check for if off-polarity is to be ignored
            if(  !( OnPolarityOnly && (ev instanceof TypedEvent)&&(((TypedEvent)ev).type==0) )  ){
// *****************
                if(logFrameLength>0){
                    if ((ev.timestamp/logFrameLength)>logFrameNumber){
                        logFrameNumber=ev.timestamp/logFrameLength;
                        printClusterLog(logStream, (LinkedList<Cluster>)clusters, logFrameNumber, ev.timestamp);
                    }
                }
                // check if any neigbours are assigned to a cluster already
                il=-surround;
                if ((ev.x+il)<0) il=il-(ev.x+il);
                ir=surround;
                if ((ev.x+ir)>127) ir=ir-(ev.x+ir-127);
                jl=-surround;
                if ((ev.y+jl)<0) jl=jl-(ev.y+jl);
                jr=surround;
                if ((ev.y+jr)>127) jr=jr-(ev.y+jr-127);
                //if (ev.x==0){il=0;}else{il=-1;}
                //if (ev.x==127){ir=0;}else{ir=1;}
                //if (ev.y==0){jl=0;}else{jl=-1;}
                //if (ev.y==127){jr=0;}else{jr=1;}
                //most_recent=-1;
                k=0;
                for (i=il;i<=ir;i++){
                    for(j=jl;j<=jr;j++){
                        if (lastEvent[ev.x+i][ev.y+j] != -1){
                            //if (lastEvent[ev.x+i][ev.y+j]>most_recent){
                                //most_recent=lastEvent[ev.x+i][ev.y+j];
                                //lastCluster[ev.x][ev.y]=lastCluster[ev.x+i][ev.y+j];
                            //}
                            if (lastEvent[ev.x+i][ev.y+j] >= ev.timestamp-clusterUnsupportedLifetime){
                                lastCluster[ev.x][ev.y]=lastCluster[ev.x+i][ev.y+j];
                                cluster_ids[k]=lastCluster[ev.x+i][ev.y+j]; // an existing cluster id at or around the event
                                k++;
                                for (l=0;l<(k-1);l++){ // checking if its a doublicate
                                    if (cluster_ids[k-1]==cluster_ids[l]){
                                        k--;
                                        break;
                                    } 
                                }
                            }
                        }
                    }
                }
                lastEvent[ev.x][ev.y]=ev.timestamp;
/***************************************************************************************************************/
                if (k==0){// new cluster
                    //if (next_cluster_id<200){
                        lastCluster[ev.x][ev.y]=next_cluster_id;
                        thisCluster=new Cluster(ev.x,ev.y,ev.timestamp);
                        clusters.add(thisCluster);
                    //}
/***************************************************************************************************************/
                }else{// existing cluster: new event of one or several existing cluster
                    listScanner=clusters.listIterator();
                    while (listScanner.hasNext()){ // add new event to cluster
                        c=(Cluster)listScanner.next();
                        if ((c.last < ev.timestamp- clusterUnsupportedLifetime)||(c.last > ev.timestamp)){ //check if cluster is dead or if time has moved backwards
                            listScanner.remove();
                        }else{ //if cluster is still alive
                            for (l=0;l<c.id.length;l++){
                                if (c.id[l]==lastCluster[ev.x][ev.y]){// check if this event belongs to this cluster
                                    c.addEvent(ev);
                                    thisCluster=c;
                                    //break;
                                }
                            }
                        }
                    }
/***************************************************************************************************************/
                    if (k>1){ //merge clusters if there has been more alive clusters in neighbourhood
                        mergeClusters(thisCluster, cluster_ids, k, ev.timestamp);
                    }
                    /************************************/
                    //clusters.removeAll(pruneList);
                    //pruneList.clear();
                }
            }
        }
    }
    
/**************************************************************************************************************************************/
    
    public int mergeClusters(Cluster thisCluster, int[] cluster_ids, int n_ids, int now){
        ListIterator listScanner;
        Cluster c=null;
        int i,j,l;
        int c_count=1;
        int[] merged_ids;
        float thisClusterWeight,thatClusterWeight;

                        if (thisCluster!=null){
                        listScanner=clusters.listIterator();
                        while (listScanner.hasNext()){ // look for the clusters to be merged
                            c=(Cluster)listScanner.next();
                            if (c!=thisCluster){
                                for (i=0;i<c.id.length;i++){
                                    for (j=0;j<n_ids;j++){
                                        if (c.id[i]== cluster_ids[j]){
                                            c_count++;
                                            merged_ids=new int[c.id.length + thisCluster.id.length];
                                            //System.out.println("******cluster merging: "+(c.id.length+ thisCluster.id.length));
                                            for (l=0;l<thisCluster.id.length;l++){
                                                merged_ids[l]=thisCluster.id[l];
                                            }
                                            for (l=0;l<(c.id.length);l++){
                                                merged_ids[l+thisCluster.id.length]= c.id[l];
                                            }
                                            //for (l=0;l<(c.id.length+ thisCluster.id.length);l++){
                                                //System.out.print(" "+merged_ids[l]);
                                            //}
                                            //System.out.println();
                                            thisCluster.id=merged_ids;
                                            c.mass= c.mass*weighEvent((float)c.last,(float)now);
                                            c.lifeForce= c.lifeForce*(float)Math.exp(-(float)(now- c.last)/clusterUnsupportedLifetime);
                                            thisClusterWeight=thisCluster.mass/ (thisCluster.mass + c.mass);
                                            thatClusterWeight=1-thisClusterWeight;
                                            thisCluster.location.x=thisClusterWeight*thisCluster.location.x+thatClusterWeight*c.location.x;
                                            thisCluster.location.y=thisClusterWeight*thisCluster.location.y+thatClusterWeight*c.location.y;
                                            thisCluster.velocity.x=thisClusterWeight*thisCluster.velocity.x+thatClusterWeight*c.velocity.x;
                                            thisCluster.velocity.y=thisClusterWeight*thisCluster.velocity.y+thatClusterWeight*c.velocity.y;
                                            if (thisCluster.mass<c.mass){
                                                thisCluster.color=c.color;
                                            }
                                            thisCluster.mass=thisCluster.mass + c.mass;
                                            thisCluster.lifeForce=thisCluster.lifeForce + c.lifeForce;
                                            j=n_ids;
                                            i=c.id.length;
                                            listScanner.remove();
                                        }
                                    }
                                }
                            }
                        }
                    }else{
                        log.warning("null thisCluster in ParticleTracker.mergeClusters");
                    }
        return(c_count);

    }
/**************************************************************************************************************************************/
    public class DiffusedCluster{
        int t;
        Point2D.Float location = new Point2D.Float();
        int n=0;
        float mass=0;
        
        public DiffusedCluster(){
        }

        public DiffusedCluster(int x, int y, int ti){
            t=ti;
            //t=-1;
            location.x=x;
            location.y=y;
            n=0;
            mass=0;
        }
    }
/**************************************************************************************************************************************/
    
    public DiffusedCluster diffuseCluster(int x, int y, int id, int time_limit,int lowest_id, DiffusedCluster c){
        int i,j,t,most_recent;
        float new_event_weight;
        //int surround=2;
        
        if ((x>=0)&&(x<128)&&(y>=0)&&(y<128)&&(lastCluster[x][y]<lowest_id)&&(lastEvent[x][y]>=time_limit)){
            if (c == null){
                c = new DiffusedCluster(0,0,lastEvent[x][y]);
            }
            lastCluster[x][y]=id;
            c.n++;
            //new_event_weight = (float)Math.exp( ((float)(lastEvent[x][y]-time_limit)) / (float)clusterUnsupportedLifetime - 1f);
            new_event_weight = weighEvent((float)lastEvent[x][y],(float)(time_limit+clusterUnsupportedLifetime));
            c.location.x= (c.location.x*c.mass + x*new_event_weight)/(c.mass + new_event_weight);
            c.location.y= (c.location.y*c.mass + y*new_event_weight)/(c.mass + new_event_weight);
            c.mass= c.mass +  new_event_weight;
            if (c.t < lastEvent[x][y]) c.t= lastEvent[x][y];
            for (i=-surround;i<=surround;i++){
                for(j=-surround;j<=surround;j++){
                    c=diffuseCluster(x+i,y+j,id,time_limit,lowest_id,c);
                }
            }
        }
        return(c);
    }
/**************************************************************************************************************************************/
    
    public int splitClusters(){
        int split_count=0;
        float max_new_mass=0f;
        int local_split_count=0;
        ListIterator clusterScanner,old_new_id_scanner;
        Cluster c,new_c;
        int now=-1;
        int new_clusters_from;
        DiffusedCluster dc=null;
        int x,y,time_limit,i/*,new_id*/,old_id;
        float first_x=0;
        float first_y=0;
        //int[] old_cluster_id = new int[1];
        int[] old_ids;
        //int[] new_ids;
        class OldNewId{
            int o;
            int n;
            DiffusedCluster c; //last event timestamp in this cluster, estimated location and mass
        }
        java.util.List<OldNewId> old_new_id_list= new java.util.LinkedList<OldNewId>();
        OldNewId old_new_id;
        int this_pixel_old_id;
        boolean position_correction_necessary=false;
        float old_mass;
        
        clusterScanner=clusters.listIterator();
        while (clusterScanner.hasNext()){ // check for the most recent event timestamp
            c=(Cluster)clusterScanner.next();
            if (c.last> now) now=c.last;
        }
        time_limit=now-clusterUnsupportedLifetime;
        if (time_limit<0) time_limit=0;
        new_clusters_from=next_cluster_id; 
        // the next foor loop builds up a list of clusters based on all connected alive events in the pixels
        // and assignes new cluster ids in the lastCluster array. This new id, the old id and the cluster are stored ina temporary list
        for (x=0;x<128;x++){
            for (y=0;y<128;y++){
                this_pixel_old_id=lastCluster[x][y];
                try{
                dc=diffuseCluster(x,y,next_cluster_id,time_limit,new_clusters_from,null);
                }catch(java.lang.StackOverflowError e){
                    log.warning(e.getMessage());
                    log.warning("Probably a stack overflow: resetting ParticleTracker");
                    dc=null;
                    initFilter();
                }
                if (dc != null){
                    //dc.mass = dc.mass * (float)Math.exp((float)(now-dc.t)/clusterUnsupportedLifetime); 
                    // the above is not necessary since already right from the difuseCluster function
                    old_new_id= new OldNewId();
                    old_new_id.o = this_pixel_old_id;
                    old_new_id.n = next_cluster_id;
                    old_new_id.c = dc;
                    old_new_id_list.add(old_new_id);
                    next_cluster_id++;                    
                }else{
                    //lastCluster[x][y]=-1;
                }
            }
        }
        clusterScanner=clusters.listIterator();
        while (clusterScanner.hasNext()){ // get all old clusters, assign new ids and split if necessary
            c=(Cluster)clusterScanner.next();
            if ((c.last < time_limit)||(c.last > now)){ //check if cluster is dead or if time has moved backwards
                clusterScanner.remove();
            }else{
                old_mass=c.mass;
                old_ids=c.id;
                max_new_mass=0;
                local_split_count=0;
                //new_id=new_clusters_from;
                first_x=-1; // dummy initialization : this variable should always be initialized in if (local_split_count >0){}else{...}
                first_y=-1;
                old_new_id_scanner=old_new_id_list.listIterator();
                while (old_new_id_scanner.hasNext()){ // checking for all new clusters if they have been part of this old cluster
                    old_new_id= (OldNewId)old_new_id_scanner.next();
                    for (i=0;i<old_ids.length;i++){
                        if (old_ids[i]==old_new_id.o){
                            //position_correction_necessary=
                                     //(old_mass > 1000*old_new_id.c.mass);
                                     // //((Math.abs(old_new_id.c.location.x-c.location.x) > 10)||
                                     // //(Math.abs(old_new_id.c.location.y-c.location.y)>10 ));
                            if(local_split_count>0){// cluster has been split
                                new_c = new Cluster(old_new_id.n, c.location.x, c.location.y, c.velocity.x, c.velocity.y, old_new_id.c.t);
                                if (old_new_id.c.mass>max_new_mass){
                                    max_new_mass=old_new_id.c.mass;
                                    c.mass=0f;
                                    new_c.mass=old_mass;
                                }else{
                                    new_c.mass = 0f;
                                }
                                //new_c.mass = old_new_id.c.mass; // not so good this, since various events in the same pixel can have contributed
                                new_c.color=c.color;
                                clusterScanner.add(new_c);
                            }else{ // found first old cluster and assigning the new id
                                max_new_mass=old_new_id.c.mass;
                                c.id=new int[1];
                                c.id[0]= old_new_id.n;
                                c.last = old_new_id.c.t;
                                new_c = c; 
                            }
                            //if (position_correction_necessary){
                                // //System.out.println("correcting position"
                                // //        +" "+(old_new_id.c.location.x-new_c.location.x)
                                // //        +" "+(old_new_id.c.location.y-new_c.location.y)
                                // //        +" "+(old_mass)
                                // //        +" "+(old_new_id.c.mass)
                                // //        );
                                //new_c.location.x= old_new_id.c.location.x;
                                //new_c.location.y= old_new_id.c.location.y;
                                //new_c.mass=0;
                            //}
                            local_split_count++;
                        }
                    }
                }
                if (local_split_count<1){ 
                    log.warning("could not associate an existing cluster with one of the still valid diffused clusters");
                    clusterScanner.remove();
                }
                split_count+=(local_split_count-1);
            }
        }
        return(split_count);
    }
    
/**************************************************************************************************************************************/
    
    public class Cluster{
        public Point2D.Float location=new Point2D.Float(); // location in chip pixels        
        public Point2D.Float lastPixel=new Point2D.Float(); // location of last active pixel belonging to cluster        
        /** velocity of cluster in pixels/tick, where tick is timestamp tick (usually microseconds) */
        public Point2D.Float velocity=new Point2D.Float(); // velocity in chip pixels/sec
        //protected float distanceToLastEvent=Float.POSITIVE_INFINITY;
        public int[] id=null;
        int last=-1;
        public float mass=(float)0;
        public float lifeForce=0f;
        public Color color=null;
        public boolean em=false; //emphazise when drawing;

//        public Cluster(){
            
//            location.x=(float)20.5;
//            location.y=(float)10.9;
//            id=-1;
//        }

        public Cluster(float x, float y, int first_event_time){
            //System.out.println("**** constructed "+this);
            
            location.x=x;
            location.y=y;
            lastPixel.x=x;
            lastPixel.y=y;
            id=new int[1];            
            id[0]=next_cluster_id++;
            last=first_event_time;
            velocity.x=(float)0.0;
            velocity.y=(float)0.0;
            lifeForce=1;
            mass=1;
            //float hue=random.nextFloat();
            color=Color.getHSBColor(random.nextFloat(),1f,1f);

        }
        public Cluster(int identity, float x, float y, float vx, float vy, int first_event_time){
            location.x=x;
            location.y=y;
            id=new int[1];            
            id[0]=identity;
            last=first_event_time;
            velocity.x=vx;
            velocity.y=vy;
            lifeForce=1;
            mass=1;
            //float hue=random.nextFloat();
            color=Color.getHSBColor(random.nextFloat(),1f,1f);
            
        }
       
        public void addEvent(BasicEvent ev){
                                
                                int interval;
                                

                                interval=ev.timestamp - this.last;
                                if (interval==0) interval=1;
                                this.last= ev.timestamp;
                                this.lastPixel.x= ev.x;
                                this.lastPixel.y= ev.y;
                                this.lifeForce= this.lifeForce * (float)Math.exp(-(float)interval/clusterUnsupportedLifetime) +1;
                                //this.mass= this.mass * (float)Math.exp(-(float)interval/clusterUnsupportedLifetime) +1;
                                this.mass= this.mass * weighEvent(this.last-interval,this.last)+1;
                                //float event_weight=(float)interval/clusterUnsupportedLifetime;
                                //if (event_weight>1) event_weight=1;
                                //float event_weight=(float)1/(this.mass);
                                float predicted_x=this.location.x + this.velocity.x * (interval);
                                float predicted_y=this.location.y + this.velocity.y * (interval);
                                //float new_x=(1-event_weight)*predicted_x + event_weight*ev.x ;
                                //float new_x=(1-1/(this.mass))*predicted_x + 1/(this.mass)*ev.x ;
                                float new_x=(1-1/(this.mass))*this.location.x + 1/(this.mass)*ev.x ;
                                //float new_y=(1-event_weight)*predicted_y + event_weight*ev.y ;
                                //float new_y=(1-1/(this.mass))*predicted_y + 1/(this.mass)*ev.y ;
                                float new_y=(1-1/(this.mass))*this.location.y + 1/(this.mass)*ev.y ;
                                //this.velocity.x= (1-event_weight)*this.velocity.x + event_weight*(new_x - this.location.x)/interval;
                                this.velocity.x= (1-1/(this.mass))*this.velocity.x + 1/(this.mass)*(new_x - this.location.x)/(float)interval;
                                //this.velocity.x= (new_x - this.location.x)/interval;
                                //this.velocity.y= (1-event_weight)*this.velocity.y + event_weight*(new_y - this.location.y)/interval;
                                this.velocity.y= (1-1/(this.mass))*this.velocity.y + 1/(this.mass)*(new_y - this.location.y)/(float)interval;
                                //this.velocity.y= (new_y - this.location.y)/interval;
                                this.location.x= new_x;
                                this.location.y= new_y;
        }
        
        public Point2D.Float getLocation() {
            return location;
        }
        public void setLocation(Point2D.Float l){
            this.location = l;
        }

    }

    public java.util.List<ParticleTracker.Cluster> getClusters() {
        return this.clusters;
    }
    
//    private LinkedList<ParticleTracker.Cluster> getPruneList(){
//        return this.pruneList;
//    }

    private final void drawCluster(final Cluster c, float[][][] fr){
        int x=(int)c.getLocation().x;
        int y=(int)c.getLocation().y;
        int i;
        if(y<0 || y>fr.length-1 || x<0 || x>fr[0].length-1) return;
//        for (x=20;x<(fr[0].length-20);x++){
//            for(y=20;y<(fr[1].length-20);y++){
                fr[x][y][0]=(float)0.2;
                fr[x][y][1]=(float)1.0;
                fr[x][y][2]=(float)1.0;
//            }
//        }
    }
    
    public void initFilter() {
        initDefaults();
        resetFilter();
//        defaultClusterRadius=(int)Math.max(chip.getSizeX(),chip.getSizeY())*getClusterSize();
        if ((logFrameLength>0)&&(logStream==null)){
            openLog();
        }
    }
    

    synchronized public void resetFilter() {
        int i,j;
        clusters.clear();
        next_cluster_id=1;
        for (i=0;i<128;i++){
            for (j=0;j<128;j++){
                lastCluster[i][j]=-1;
                lastEvent[i][j]=-1;
            }
        }
    }
    
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }

    
    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        track(in);
        return in;
    }

    public void update(Observable o, Object arg) {
        initFilter();
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
        final float BOX_LINE_WIDTH=5f; // in pixels
        final float PATH_LINE_WIDTH=3f;
        float[] rgb;
        int sx,sy;

        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in ClassTracker.annotate");
            return;
        }
        gl.glPushMatrix();
        splitClusters();
        for(Cluster c:clusters){
            rgb=c.color.getRGBComponents(null);
            if (c.mass>clusterMinMass4Display){
                sx=4;
                sy=4;
                if (c.em){
                    gl.glLineWidth((float)2);
                }else{
                    gl.glLineWidth((float)1);                    
                }
                gl.glColor3fv(rgb,0);
                //gl.glColor3f(.5f,.7f,.1f);
            }else{
                sx=(int)(4.0*c.mass/clusterMinMass4Display);
                sy=(int)(4.0*c.mass/clusterMinMass4Display);
                gl.glLineWidth((float).2);
                //gl.glColor3fv(rgb,0);
                gl.glColor3f(.1f,.2f,.1f);
            }
            drawBox(gl,(int)c.location.x,(int)c.location.y,sx,sy);
            gl.glBegin(GL.GL_LINES);
              {
                  gl.glVertex2i((int)c.location.x,(int)c.location.y);
                  gl.glVertex2f((int)(c.location.x+c.velocity.x*displayVelocityScaling),(int)(c.location.y+c.velocity.y*displayVelocityScaling));
              }
            gl.glEnd();
        }
        gl.glPopMatrix();
    }
    
        /** annotate the rendered retina frame to show locations of clusters */
    synchronized public void annotate(float[][][] frame) {
        //System.out.println("******drawing ouooo yeah!");
        if(!isFilterEnabled()) return;
        // disable for now TODO
        if(chip.getCanvas().isOpenGLEnabled()) return; // done by open gl annotator
        //System.out.println("******really ouooo yeah!");
        for(Cluster c:clusters){
        //    if(c.isVisible()){
                //System.out.println("******really true!");
                drawCluster(c, frame);
//            }
        }
    }


}
