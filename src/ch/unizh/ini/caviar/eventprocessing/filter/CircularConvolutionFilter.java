/*
 * CircularConvolutionFilter.java
 *
 * Created on 24.2.2006 Tobi
 *
 */

package ch.unizh.ini.caviar.eventprocessing.filter;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import java.util.*;
import java.util.logging.Level;

/**
 * Computes circular convolutions by splatting out events and checking receiving pixels to see if they exceed a threshold.
 * A behavioral model of Raphael/Bernabe convolution chip, but limited in that it presently only allows positive binary kernel weights, thus
 *the output events an be triggered by lots of input activity.
 *
 * @author tobi
 */
public class CircularConvolutionFilter extends EventFilter2D implements Observer {
    public boolean isGeneratingFilter(){ return true;}
    
    /** events must occur within this time along orientation in us to generate an event */
//    protected int maxDtThreshold=prefs.getInt("SimpleOrientationFilter.maxDtThreshold",Integer.MAX_VALUE);
//    protected int minDtThreshold=prefs.getInt("TypeCoincidenceFilter.minDtThreshold",10000);
    
    static final int NUM_INPUT_CELL_TYPES=1;
    
    /** the number of cell output types */
    public final int NUM_OUTPUT_TYPES=1; // we make it big so rendering is in color
    
    /** Creates a new instance of TypeCoincidenceFilter */
    public CircularConvolutionFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        resetFilter();
        setFilterEnabled(false);
    }
    
    public Object getFilterState() {
        return null;
    }
    
    synchronized public void resetFilter() {
        allocateMap();
    }
    
    final class Splatt{
        int x, y;
        float weight;
        Splatt(){
            x=0;
            y=0;
            weight=1f;
        }
        Splatt(int x, int y, float w){
            this.x=x;
            this.y=y;
            this.weight=w;
        }
        public String toString(){
            return "Splatt: "+x+","+y+","+String.format("%.1f",weight);
        }
        
    }
    
    private Splatt[] splatts;
    private int splattCircum;
    
    // computes the indices to splatt to from a source event
    // these are octagonal around a point to the neighboring pixels at a certain radius
    // eg, radius=0, impulse kernal=identity kernel
    // radius=1, nearest neighbors in 8 directions
    // radius=2 octagon?
    synchronized void computeSplattLookup(){
//        log.info("computing splatt");
        double circum=2*Math.PI*radius; // num pixels
        splattCircum=(int)circum;
        int n=(int)Math.round(circum);
        int xlast=-1, ylast=-1;
        ArrayList<Splatt> list=new ArrayList<Splatt>();
        for(int i=0;i<n;i++){
            double theta=2*Math.PI*i/circum;
            double x=Math.cos(theta)*radius;
            double y=Math.sin(theta)*radius;
            double xround=Math.round(x);
            double yround=Math.round(y);
            if(xlast!=xround || ylast!=yround){ // dont make multiple copies of the same splatt around the circle
                Splatt s=new Splatt();
                s.x=(int)xround;
                s.y=(int)yround;
                s.weight=1; //(float)(1-Math.sqrt((x-xround)*(x-xround)+(y-yround)*(y-yround)));
                xlast=s.x; ylast=s.y;
                list.add(s);
            }
        }
//        log.info("splatt has "+list.size()+" +1 elements");
        if(radius>2){
            // make negative outside ring, 1/2 weight
            xlast=-1; ylast=-1;
            for(int i=0;i<n;i++){
                double theta=2*Math.PI*i/circum;
                double x=Math.cos(theta)*radius+1;
                double y=Math.sin(theta)*radius+1;
                double xround=Math.round(x);
                double yround=Math.round(y);
                if(xlast!=xround || ylast!=yround){ // dont make multiple copies of the same splatt around the circle
                    Splatt s=new Splatt();
                    s.x=(int)xround;
                    s.y=(int)yround;
                    s.weight= -0.5f; //(float)(1-Math.sqrt((x-xround)*(x-xround)+(y-yround)*(y-yround)));
                    xlast=s.x; ylast=s.y;
                    list.add(s);
                }
            }
            xlast=-1; ylast=-1;
            for(int i=0;i<n;i++){
                double theta=2*Math.PI*i/circum;
                double x=Math.cos(theta)*radius+-1;
                double y=Math.sin(theta)*radius-1;
                double xround=Math.round(x);
                double yround=Math.round(y);
                if(xlast!=xround || ylast!=yround){ // dont make multiple copies of the same splatt around the circle
                    Splatt s=new Splatt();
                    s.x=(int)xround;
                    s.y=(int)yround;
                    s.weight= -0.5f; //(float)(1-Math.sqrt((x-xround)*(x-xround)+(y-yround)*(y-yround)));
                    xlast=s.x; ylast=s.y;
                    list.add(s);
                }
            }
        }
//        log.info("splatt has "+list.size()+" total elements");
        float sum=0;
        Object[] oa=list.toArray();
        splatts=new Splatt[oa.length];
        for(int i=0;i<oa.length;i++){
            splatts[i]=(Splatt)oa[i];
            sum+=splatts[i].weight;
        }
//        log.info("splatt total weight = "+sum);
        
    }
    
    int PADDING=0, P=0;
    float[][] convolutionVm;
    int[][] convolutionLastEventTime;
    
    private void allocateMap() {
        if(chip.getSizeX()==0 || chip.getSizeY()==0){
//            log.warning("tried to allocateMap in CircularConvolutionFilter but chip size is 0");
            return;
        }
//        PADDING=2*radius;
//        P=radius;
        convolutionVm=new float[chip.getSizeX()][chip.getSizeY()];
        convolutionLastEventTime=new int[chip.getSizeX()][chip.getSizeY()];
        computeSplattLookup();
    }
    
//    private int[] oriHist=new int[NUM_OUTPUT_TYPES];
    
//    int maxEvents=0;
//    int index=0;
    private short x,y;
    private byte type;
    private int ts;
    
    
    
//    public int getMinDtThreshold() {
//        return this.minDtThreshold;
//    }
//
//    public void setMinDtThreshold(final int minDtThreshold) {
//        this.minDtThreshold = minDtThreshold;
//        prefs.putInt("TypeCoincidenceFilter.minDtThreshold", minDtThreshold);
//    }
//
    public void initFilter() {
        resetFilter();
    }
    
    public void update(Observable o, Object arg) {
        if(!isFilterEnabled()) return;
        initFilter();
    }
    
    private int radius=getPrefs().getInt("CircularConvolutionFilter.radius",3);
    {setPropertyTooltip("radius","radius in pixels of kernal");}
    
    public int getRadius() {
        return radius;
    }
    
    synchronized public void setRadius(int radius) {
        if(radius<0) radius=0; else if(radius>chip.getMaxSize()) radius=chip.getMaxSize();
        if(radius!=this.radius) {
            this.radius = radius;
            getPrefs().putInt("CircularConvolutionFilter.radius",radius);
            resetFilter();
        }
    }
    
    
    private float tauMs=getPrefs().getFloat("CircularConvolutionFilter.tauMs",10f);
    {setPropertyTooltip("tauMs","time constant in ms of integrator neuron potential decay");}
    
    public float getTauMs() {
        return tauMs;
    }
    
    synchronized public void setTauMs(float tauMs) {
        if(tauMs<0) tauMs=0; else if(tauMs>10000) tauMs=10000f;
        this.tauMs = tauMs;
        getPrefs().putFloat("CircularConvolutionFilter.tauMs",tauMs);
    }
    
    private float threshold=getPrefs().getFloat("CircularConvolutionFilter.threshold",1f);
    {setPropertyTooltip("threshold","threahold on ms for firing output event from integrating neuron");}
    
    public float getThreshold() {
        return threshold;
    }
    
    synchronized public void setThreshold(float threshold) {
        if(threshold<0) threshold=0; else if(threshold>100) threshold=100;
        this.threshold = threshold;
        getPrefs().putFloat("CircularConvolutionFilter.threshold",threshold);
    }
    
   synchronized public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!isFilterEnabled()) return in;
//        if(enclosedFilter!=null) in=enclosedFilter.filter(in);
        checkOutputPacketEventType(in);
        int sizex=chip.getSizeX();
        int sizey=chip.getSizeY();
        int n=in.getSize();
        
        // for each event write out an event of an orientation type if there have also been events within past dt along this type's orientation of the
        // same retina polarity
        OutputEventIterator oi=out.outputIterator();
        for(Object o:in){
            PolarityEvent e=(PolarityEvent)o;
            x=e.x;
            y=e.y;
            type=(byte)e.getType();
            ts=e.timestamp;  // get event x,y,type,timestamp
            
            // get times to neighbors in 'type' at this pixel
            
            for(int j=0;j<splatts.length;j++){
                Splatt s=splatts[j];
                int xoff=x+s.x; if(xoff<0||xoff>sizex-1) continue; //precheck array access
                int yoff=y+s.y; if(yoff<0||yoff>sizey-1) continue;
                
                float dtMs=(ts-convolutionLastEventTime[xoff][yoff])/1000f;
                float vmold=convolutionVm[xoff][yoff];
                vmold=(float)(vmold*(Math.exp(-dtMs/tauMs)));
                float vm=vmold+s.weight;
                convolutionVm[xoff][yoff]=vm;
                convolutionLastEventTime[xoff][yoff]=ts;
                if( vm>threshold ){
                    PolarityEvent oe=(PolarityEvent)oi.nextOutput();
                    oe.copyFrom(e);
                    oe.x=(short)xoff;
                    oe.y=(short)yoff;
                    convolutionVm[xoff][yoff]=0;
                }
            }
        }
        return out;
    }

    
}
