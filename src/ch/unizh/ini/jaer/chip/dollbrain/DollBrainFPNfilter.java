package ch.unizh.ini.jaer.chip.dollbrain;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Reduces fixed pattern noise in the dollbrain AER vision sensor.
 * @author Raphael Berner
 */
public class DollBrainFPNfilter extends EventFilter2D implements Observer  {
    
   
    float[][][] FPNmap;
    
    public DollBrainFPNfilter(AEChip chip){
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();
    }
    
    void allocateMaps(AEChip chip){
        FPNmap=new float[4][5][2];
    }
    
    
    float averageColor=0;
    double averageTimestamp=0;
    int numberOfEvents=0;
    int[][] EventsPerPixel;
    
    int frameStart=0;
    
    private void initRecordingFPN() {
        averageColor=0;
        averageTimestamp=0;
        numberOfEvents=0;
        EventsPerPixel= new int[4][5];
        
        frameStart=0;
        
        for(int i=0;i<EventsPerPixel.length;i++)
            Arrays.fill(EventsPerPixel[i],0);
        
        resetFPNmap();
    }
   
    private boolean recordingFPNinternal=false;
    
    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number put in
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        if(FPNmap==null) allocateMaps(chip);
          
        if (recordingFPN)
        {
            if (!this.recordingFPNinternal)
            {
                this.recordingFPNinternal=true;
                this.initRecordingFPN();
            }
            
            
            for(Object e:in){
                ColorEvent i=(ColorEvent)e;
                
                if (i.x==7 && i.y==3)
                {
                    frameStart=i.timestamp;
                    continue;
                }
                
                if (frameStart==0)
                    continue;
                
                try {
                    FPNmap[i.y][i.x][1] += i.color;
                    FPNmap[i.y][i.x][0] += (i.timestamp-frameStart);
                    averageColor+= i.color;
                    averageTimestamp+= (i.timestamp-frameStart);
                    numberOfEvents+=1;
                    EventsPerPixel[i.y][i.x]+=1;
                } catch (java.lang.IndexOutOfBoundsException er) // ignore framestart events
                {}
            }
            
            
        } else {
            if(this.recordingFPNinternal) {
                this.recordingFPNinternal=false;
                
                averageColor=averageColor/numberOfEvents;
                averageTimestamp=averageTimestamp/numberOfEvents;
                
                for (int i=0; i<4; i++)
                    for (int k=0;k<5;k++) {
                        FPNmap[i][k][1]=averageColor * EventsPerPixel[i][k] / FPNmap[i][k][1];
                        FPNmap[i][k][0]=(float) averageTimestamp * EventsPerPixel[i][k] / FPNmap[i][k][0];
                    }
            }
            
            for(Object e:in){
                ColorEvent i=(ColorEvent)e;
                
                if (i.x==7 && i.y==3)
                {
                    frameStart=i.timestamp;
                }
                
                try {
                    i.color= (short) (i.color*FPNmap[i.y][i.x][1]);
                    i.timestamp= (int) ( frameStart + (i.timestamp-frameStart)*FPNmap[i.y][i.x][0]);
                   // System.out.println("color " + i.color + " fpnmap " + FPNmap[i.y][i.x][1]);
                } catch (java.lang.IndexOutOfBoundsException er) // ignore framestart events
                {}
            }
        }
//        }catch(Exception e){
//            e.printStackTrace();
//        }
        return in;
    }
    
    boolean recordingFPN;
    
    public boolean getRecordingFPN()
    {
        return recordingFPN;
    }
    
    public void setRecordingFPN(boolean recordingFPN)
    {
        this.recordingFPN=recordingFPN;
    }
 
    
    public Object getFilterState() {
        return FPNmap;
    }
    
    void resetFPNmap(){
        for(int i=0;i<4;i++)
            for(int k=0;k<5;k++)
                for(int l=0;l<2;l++)
                    FPNmap[i][k][l]=1f;
    }
    
    synchronized public void resetFilter() {
        // set all FPNmap to max value so that any event is soon enough, guarenteed to be less than it
        resetFPNmap();
    }
    
    
    public void update(Observable o, Object arg) {
//        if(!isFilterEnabled()) return;
        initFilter();
    }
    
    public void initFilter() {
        allocateMaps(chip);
    }
    
    
}
